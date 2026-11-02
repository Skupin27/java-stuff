import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Space Invaders: pixel-art aliens, destructible shields, a bonus UFO and endless waves.
 * Left/Right (A/D) move, SPACE fires (hold to auto-fire), P pauses.
 */
public class Invaders extends JPanel implements Main.Game {

    static final int W = 640, H = 640;
    static final int ROWS = 5, COLS = 10, SPX = 46, SPY = 36, PX = 3; // PX = sprite pixel size
    static final int PLAYER_Y = H - 62, SHIELD_Y = H - 170, CELL = 4, SH_COLS = 18, SH_ROWS = 12;

    // [type][frame][row]
    static final String[][][] SPRITES = {
            {{"...XX...", "..XXXX..", ".XXXXXX.", "XX.XX.XX", "XXXXXXXX", "..X..X..", ".X.XX.X.", "X.X..X.X"},
                    {"...XX...", "..XXXX..", ".XXXXXX.", "XX.XX.XX", "XXXXXXXX", ".X.XX.X.", "X......X", ".X....X."}},
            {{"X..XX..X", "X.XXXX.X", "XXXXXXXX", "XX.XX.XX", "XXXXXXXX", ".XXXXXX.", "..X..X..", ".X....X."},
                    {"..X..X..", "X.XXXX.X", "XXXXXXXX", "XX.XX.XX", "XXXXXXXX", ".XXXXXX.", ".X....X.", "..X..X.."}},
            {{"..XXXX..", ".XXXXXX.", "XXXXXXXX", "XX.XX.XX", "XXXXXXXX", "..X..X..", ".XX..XX.", "XX....XX"},
                    {"..XXXX..", ".XXXXXX.", "XXXXXXXX", "XX.XX.XX", "XXXXXXXX", ".X.XX.X.", "X.X..X.X", ".X....X."}}
    };
    static final int[] ROW_TYPE = {0, 1, 1, 2, 2};
    static final int[] ROW_POINTS = {30, 20, 20, 10, 10};
    static final Color[] ROW_COLORS = {
            new Color(255, 110, 200), new Color(120, 200, 255), new Color(120, 200, 255),
            new Color(130, 240, 130), new Color(130, 240, 130)
    };

    enum State { MENU, PLAYING, PAUSED, OVER }

    static class Bullet {
        double x, y;

        Bullet(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Particle {
        double x, y, vx, vy;
        int life;
        final int maxLife;
        final Color color;

        Particle(double x, double y, double vx, double vy, int life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.color = color;
        }
    }

    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Invaders.class);

    private final boolean[][] alive = new boolean[ROWS][COLS];
    private final boolean[][][] shields = new boolean[4][SH_ROWS][SH_COLS];
    private final ArrayList<Bullet> enemyBullets = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final double[] starX = new double[70], starY = new double[70], starS = new double[70];

    private Bullet shot;
    private State state = State.MENU;
    private double ox, oy, playerX = W / 2.0;
    private int dir = 1, stepTimer, frame, fireCooldown, enemyFireTimer, respawnTimer, waveTimer;
    private int score, lives = 3, level = 1, best, shake;
    private double ufoX = -100, ufoDir = 1;
    private boolean ufoActive;
    private int ufoTimer = 600, ufoFlash;
    private String popup = "";
    private int popupX, popupY, popupLife;
    private boolean left, right, fireHeld, newRecord;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Invaders() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(6, 6, 16));
        setFocusable(true);
        best = prefs.getInt("invaders_best", 0);

        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextDouble() * W;
            starY[i] = rnd.nextDouble() * H;
            starS[i] = 0.5 + rnd.nextDouble() * 1.8;
        }

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = true;
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
                else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) {
                    fireHeld = true;
                    if (state == State.MENU || state == State.OVER) newGame();
                    else if (state == State.PAUSED) state = State.PLAYING;
                } else if (k == KeyEvent.VK_P || k == KeyEvent.VK_ESCAPE) {
                    if (state == State.PLAYING) state = State.PAUSED;
                    else if (state == State.PAUSED) state = State.PLAYING;
                } else if (k == KeyEvent.VK_Q) {
                    if (state == State.PAUSED || state == State.OVER) state = State.MENU;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) left = false;
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
                else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) fireHeld = false;
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (state == State.MENU || state == State.OVER) newGame();
            }
        });
        loop.start();
    }

    @Override
    public void stop() {
        loop.stop();
    }

    // ---------- setup ----------
    private void newGame() {
        score = 0;
        lives = 3;
        level = 1;
        newRecord = false;
        buildWave();
        buildShields();
        playerX = W / 2.0;
        respawnTimer = 0;
        state = State.PLAYING;
    }

    private void buildWave() {
        for (boolean[] row : alive) java.util.Arrays.fill(row, true);
        ox = 60;
        oy = 90 + Math.min(level - 1, 5) * 14;
        dir = 1;
        stepTimer = 0;
        enemyBullets.clear();
        shot = null;
        enemyFireTimer = 60;
        waveTimer = 100;
        ufoActive = false;
        ufoTimer = 500 + rnd.nextInt(500);
    }

    private void buildShields() {
        for (boolean[][] s : shields)
            for (int r = 0; r < SH_ROWS; r++)
                for (int c = 0; c < SH_COLS; c++) {
                    boolean solid = true;
                    if (r < 2 && (c < 2 || c >= SH_COLS - 2)) solid = false;
                    if (r < 1 && (c < 3 || c >= SH_COLS - 3)) solid = false;
                    if (r >= 8 && c >= 6 && c < 12) solid = false;
                    s[r][c] = solid;
                }
    }

    private int shieldX(int i) {
        return 60 + i * 149;
    }

    // ---------- helpers ----------
    private int aliveCount() {
        int n = 0;
        for (boolean[] row : alive) for (boolean a : row) if (a) n++;
        return n;
    }

    private double invX(int col) {
        return ox + col * SPX;
    }

    private double invY(int row) {
        return oy + row * SPY;
    }

    private void burst(double x, double y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 0.8 + rnd.nextDouble() * 3.5;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 16 + rnd.nextInt(16), c));
        }
    }

    private boolean hitShield(Bullet b, boolean fromBelow) {
        for (int i = 0; i < 4; i++) {
            int sx = shieldX(i);
            if (b.x < sx || b.x >= sx + SH_COLS * CELL || b.y < SHIELD_Y || b.y >= SHIELD_Y + SH_ROWS * CELL) continue;
            int c = (int) ((b.x - sx) / CELL), r = (int) ((b.y - SHIELD_Y) / CELL);
            if (!shields[i][r][c]) continue;
            // carve a small crater
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++) {
                    int rr = r + dr, cc = c + dc;
                    if (rr < 0 || rr >= SH_ROWS || cc < 0 || cc >= SH_COLS) continue;
                    if ((Math.abs(dr) + Math.abs(dc) <= 1) || rnd.nextInt(3) == 0) shields[i][rr][cc] = false;
                }
            burst(b.x, b.y, new Color(90, 220, 110), 5);
            return true;
        }
        return false;
    }

    // ---------- update ----------
    private void update() {
        phase += 0.03;
        for (int i = 0; i < starX.length; i++) {
            starY[i] += starS[i] * 0.3;
            if (starY[i] > H) {
                starY[i] = 0;
                starX[i] = rnd.nextDouble() * W;
            }
        }
        if (shake > 0) shake--;
        if (popupLife > 0) popupLife--;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.97;
            p.vy *= 0.97;
            return --p.life <= 0;
        });
        if (ufoFlash > 0) ufoFlash--;

        if (state != State.PLAYING) return;

        // player
        if (respawnTimer > 0) {
            respawnTimer--;
        } else {
            if (left) playerX -= 5;
            if (right) playerX += 5;
            playerX = Math.max(26, Math.min(W - 26, playerX));
            if (fireCooldown > 0) fireCooldown--;
            if (fireHeld && shot == null && fireCooldown == 0) {
                shot = new Bullet(playerX, PLAYER_Y - 6);
                fireCooldown = 12;
            }
        }

        if (waveTimer > 0) {
            waveTimer--;
            moveBullets();
            return;
        }

        stepInvaders();
        moveBullets();
        updateUfo();
        enemyShooting();

        if (aliveCount() == 0) {
            score += 100 * level;
            level++;
            buildWave();
            buildShields();
        }
    }

    private void stepInvaders() {
        int n = aliveCount();
        double interval = 4 + n * 0.8;
        interval *= Math.max(0.45, 1 - 0.07 * (level - 1));
        if (++stepTimer < interval) return;
        stepTimer = 0;
        frame ^= 1;

        // find bounds of living invaders
        int minC = COLS, maxC = -1, maxR = -1;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (alive[r][c]) {
                    minC = Math.min(minC, c);
                    maxC = Math.max(maxC, c);
                    maxR = Math.max(maxR, r);
                }
        if (maxC < 0) return;

        double dx = dir * 9;
        double left = ox + minC * SPX + dx, right = ox + maxC * SPX + 8 * PX + dx;
        if (left < 12 || right > W - 12) {
            dir = -dir;
            oy += 16;
        } else {
            ox += dx;
        }

        if (invY(maxR) + 8 * PX >= PLAYER_Y - 6) gameOver();
    }

    private void moveBullets() {
        // player bullet
        if (shot != null) {
            shot.y -= 11;
            boolean gone = shot.y < 40;

            if (!gone && ufoActive && shot.x > ufoX && shot.x < ufoX + 44 && shot.y > 52 && shot.y < 74) {
                int[] pts = {50, 100, 150, 300};
                int p = pts[rnd.nextInt(pts.length)];
                score += p;
                popup = "+" + p;
                popupX = (int) ufoX;
                popupY = 66;
                popupLife = 50;
                burst(ufoX + 22, 64, new Color(255, 90, 90), 24);
                ufoActive = false;
                gone = true;
            }
            if (!gone) {
                outer:
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++) {
                        if (!alive[r][c]) continue;
                        double ix = invX(c), iy = invY(r);
                        if (shot.x >= ix && shot.x <= ix + 8 * PX && shot.y >= iy && shot.y <= iy + 8 * PX) {
                            alive[r][c] = false;
                            score += ROW_POINTS[r];
                            burst(ix + 12, iy + 12, ROW_COLORS[r], 14);
                            gone = true;
                            break outer;
                        }
                    }
            }
            if (!gone && hitShield(shot, true)) gone = true;
            if (gone) shot = null;
        }

        // enemy bullets
        for (int i = enemyBullets.size() - 1; i >= 0; i--) {
            Bullet b = enemyBullets.get(i);
            b.y += 4 + Math.min(level, 6) * 0.25;
            boolean gone = b.y > H;
            if (!gone && hitShield(b, false)) gone = true;
            if (!gone && respawnTimer == 0 && Math.abs(b.x - playerX) < 20 && b.y > PLAYER_Y && b.y < PLAYER_Y + 20) {
                playerHit(); // clears all enemy bullets
                break;
            }
            if (gone) enemyBullets.remove(i);
        }
    }

    private void enemyShooting() {
        if (--enemyFireTimer > 0) return;
        enemyFireTimer = Math.max(18, 60 - level * 5 + rnd.nextInt(30));
        if (enemyBullets.size() >= 3 + Math.min(level, 4)) return;

        // pick a random column and fire from its lowest living invader
        int c = rnd.nextInt(COLS);
        for (int tries = 0; tries < COLS; tries++, c = (c + 1) % COLS) {
            for (int r = ROWS - 1; r >= 0; r--) {
                if (alive[r][c]) {
                    enemyBullets.add(new Bullet(invX(c) + 4 * PX, invY(r) + 8 * PX));
                    return;
                }
            }
        }
    }

    private void updateUfo() {
        if (ufoActive) {
            ufoX += ufoDir * 2.4;
            if (ufoX < -60 || ufoX > W + 20) ufoActive = false;
        } else if (--ufoTimer <= 0) {
            ufoActive = true;
            ufoDir = rnd.nextBoolean() ? 1 : -1;
            ufoX = ufoDir > 0 ? -50 : W + 10;
            ufoTimer = 700 + rnd.nextInt(800);
        }
    }

    private void playerHit() {
        lives--;
        shake = 14;
        burst(playerX, PLAYER_Y + 8, new Color(120, 255, 140), 36);
        enemyBullets.clear();
        shot = null;
        if (lives <= 0) gameOver();
        else respawnTimer = 80;
    }

    private void gameOver() {
        state = State.OVER;
        shake = 14;
        if (score > best) {
            best = score;
            newRecord = true;
            prefs.putInt("invaders_best", best);
        }
    }

    // ---------- rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(8, 8, 24), 0, H, new Color(2, 2, 8)));
        g2.fillRect(0, 0, W, H);
        for (int i = 0; i < starX.length; i++) {
            int a = (int) (80 + 90 * Math.abs(Math.sin(phase * 3 + i)));
            g2.setColor(new Color(255, 255, 255, Math.min(255, a)));
            g2.fillOval((int) starX[i], (int) starY[i], (int) Math.ceil(starS[i]), (int) Math.ceil(starS[i]));
        }

        int sx = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        int sy = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        g2.translate(sx, sy);

        if (state != State.MENU) {
            drawShields(g2);
            drawInvaders(g2);
            drawUfo(g2);
            drawPlayer(g2);
            g2.setColor(new Color(255, 255, 255));
            if (shot != null) g2.fillRect((int) shot.x - 1, (int) shot.y - 8, 3, 12);
            g2.setColor(new Color(255, 120, 90));
            for (Bullet b : enemyBullets) {
                int zig = (int) (Math.sin(b.y * 0.4) * 2);
                g2.fillRect((int) b.x - 1 + zig, (int) b.y - 6, 3, 12);
            }
        }

        for (Particle p : particles) {
            int a = Math.max(0, Math.min(255, 255 * p.life / p.maxLife));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            g2.fillRect((int) p.x - 1, (int) p.y - 1, 3, 3);
        }
        if (popupLife > 0) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.setColor(new Color(255, 230, 90, Math.min(255, popupLife * 8)));
            g2.drawString(popup, popupX, popupY - (50 - popupLife) / 2);
        }

        // ground line
        g2.setColor(new Color(60, 200, 90));
        g2.fillRect(0, H - 30, W, 2);

        drawHud(g2);
        g2.translate(-sx, -sy);

        if (state == State.PLAYING && waveTimer > 0) {
            centered(g2, "WAVE " + level, new Font("SansSerif", Font.BOLD, 44), new Color(255, 255, 255, 220), H / 2 + 40);
        }
        switch (state) {
            case MENU -> drawMenu(g2);
            case PAUSED -> overlay(g2, "PAUSED", "SPACE / P to resume   -   Q for menu");
            case OVER -> overlay(g2, "GAME OVER", "Score " + score + (newRecord ? "   NEW RECORD!" : "   Best " + best)
                    + "      SPACE: retry   Q: menu");
            default -> { }
        }
    }

    private void drawSprite(Graphics2D g2, String[] rows, int x, int y, int px) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == 'X') g2.fillRect(x + c * px, y + r * px, px, px);
    }

    private void drawInvaders(Graphics2D g2) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (!alive[r][c]) continue;
                g2.setColor(ROW_COLORS[r]);
                drawSprite(g2, SPRITES[ROW_TYPE[r]][frame], (int) invX(c), (int) invY(r), PX);
            }
    }

    private void drawUfo(Graphics2D g2) {
        if (!ufoActive) return;
        int x = (int) ufoX, y = 56;
        g2.setColor(new Color(255, 70, 90));
        g2.fillOval(x, y + 6, 44, 16);
        g2.setColor(new Color(255, 150, 160));
        g2.fillOval(x + 12, y, 20, 14);
        g2.setColor(new Color(255, 230, 120));
        for (int i = 0; i < 4; i++) g2.fillOval(x + 6 + i * 10, y + 12, 5, 5);
    }

    private void drawPlayer(Graphics2D g2) {
        if (respawnTimer > 0 && (respawnTimer / 5) % 2 == 0) return;
        int x = (int) playerX, y = PLAYER_Y;
        g2.setColor(new Color(110, 245, 130));
        g2.fillRect(x - 22, y + 8, 44, 12);
        g2.fillRect(x - 17, y + 4, 34, 6);
        g2.fillRect(x - 5, y - 4, 10, 10);
        g2.fillRect(x - 2, y - 9, 4, 6);
    }

    private void drawShields(Graphics2D g2) {
        g2.setColor(new Color(80, 215, 105));
        for (int i = 0; i < 4; i++) {
            int sx = shieldX(i);
            for (int r = 0; r < SH_ROWS; r++)
                for (int c = 0; c < SH_COLS; c++)
                    if (shields[i][r][c]) g2.fillRect(sx + c * CELL, SHIELD_Y + r * CELL, CELL, CELL);
        }
    }

    private void drawHud(Graphics2D g2) {
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("SCORE " + score, 16, 28);
        g2.setColor(new Color(255, 213, 79));
        g2.drawString("BEST " + Math.max(best, score), 190, 28);
        g2.setColor(new Color(170, 175, 205));
        g2.drawString("WAVE " + level, 360, 28);

        g2.setColor(new Color(110, 245, 130));
        for (int i = 0; i < Math.max(0, lives); i++) {
            int x = W - 40 - i * 34, y = 14;
            g2.fillRect(x, y + 8, 22, 6);
            g2.fillRect(x + 8, y + 2, 6, 8);
        }
    }

    private void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, W, H);

        g2.setFont(new Font("SansSerif", Font.BOLD, 62));
        String t = "SPACE INVADERS";
        g2.setFont(new Font("SansSerif", Font.BOLD, 54));
        int tw = g2.getFontMetrics().stringWidth(t), tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, new Color(130, 240, 130), tx + tw, 0, new Color(255, 110, 200)));
        g2.drawString(t, tx, 170);

        // showcase sprites
        for (int i = 0; i < 3; i++) {
            g2.setColor(i == 0 ? ROW_COLORS[0] : i == 1 ? ROW_COLORS[1] : ROW_COLORS[3]);
            drawSprite(g2, SPRITES[i][(int) (phase * 4) % 2], W / 2 - 150 + i * 110, 215, 4);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(new Color(200, 205, 230));
            g2.drawString("= " + new int[]{30, 20, 10}[i], W / 2 - 150 + i * 110 + 12, 270);
        }

        int a = (int) (170 + 85 * Math.sin(phase * 8));
        centered(g2, "Press SPACE or click to start", new Font("SansSerif", Font.BOLD, 22),
                new Color(255, 255, 255, Math.max(0, Math.min(255, a))), 350);
        centered(g2, "High score: " + best, new Font("SansSerif", Font.PLAIN, 17), new Color(255, 213, 79), 385);

        Font small = new Font("SansSerif", Font.PLAIN, 14);
        Color grey = new Color(170, 175, 205);
        centered(g2, "Left / Right (A/D): move      SPACE: fire (hold to auto-fire)", small, grey, 440);
        centered(g2, "P: pause      Shoot the red UFO for bonus points", small, grey, 464);
        centered(g2, "Shields crumble from both sides - use them wisely", small, grey, 488);
    }

    private void overlay(Graphics2D g2, String title, String sub) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, W, H);
        centered(g2, title, new Font("SansSerif", Font.BOLD, 56), Color.WHITE, H / 2 - 10);
        centered(g2, sub, new Font("SansSerif", Font.BOLD, 16), new Color(255, 213, 79), H / 2 + 30);
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        g2.setFont(f);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(new Color(0, 0, 0, 140));
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Space Invaders");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Invaders game = new Invaders();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
