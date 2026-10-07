import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Breakout / Brick Breaker.
 * Mouse or Left/Right (A/D) to move, Click or SPACE to launch, P to pause.
 */
public class Breakout extends JPanel implements Main.Game {

    // ---------- constants ----------
    static final int W = 640, H = 560, TOP = 50;
    static final int COLS = 10, BW = 54, BH = 22, GAP = 4, MARGIN = 32, BRICK_TOP = 80;
    static final int PADDLE_Y = H - 42, PADDLE_H = 14, BALL_R = 7;
    static final int PADDLE_BASE = 96, PADDLE_WIDE = 152;

    static final Color[] ROW_COLORS = {
            new Color(239, 83, 80), new Color(255, 152, 67), new Color(255, 213, 79),
            new Color(129, 199, 132), new Color(79, 195, 247), new Color(149, 117, 205),
            new Color(240, 98, 146), new Color(77, 208, 225), new Color(174, 213, 129)
    };

    enum State { MENU, PLAYING, PAUSED, CLEAR, OVER }

    enum Type { WIDE, MULTI, SLOW, LIFE }

    static class Brick {
        final Rectangle r;
        final int maxHp;
        final Color color;
        final boolean steel;
        int hp;
        boolean alive = true;

        Brick(Rectangle r, int hp, Color color, boolean steel) {
            this.r = r;
            this.hp = hp;
            this.maxHp = hp;
            this.color = color;
            this.steel = steel;
        }
    }

    static class Ball {
        double x, y, vx, vy;
        boolean stuck = true;
        final ArrayList<double[]> trail = new ArrayList<>();
    }

    static class Drop {
        double x, y;
        final Type type;

        Drop(double x, double y, Type type) {
            this.x = x;
            this.y = y;
            this.type = type;
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

    // ---------- state ----------
    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Breakout.class);

    private final ArrayList<Brick> bricks = new ArrayList<>();
    private final ArrayList<Ball> balls = new ArrayList<>();
    private final ArrayList<Drop> drops = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();

    private State state = State.MENU;
    private double paddleX = W / 2.0, paddleW = PADDLE_BASE;
    private boolean left, right;
    private int score, lives = 3, level = 1, combo, wideTimer, slowTimer, shake, clearBonus;
    private boolean newRecord;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Breakout() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(14, 16, 28));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> left = true;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> right = true;
                    case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> action();
                    case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> {
                        if (state == State.PLAYING) state = State.PAUSED;
                        else if (state == State.PAUSED) state = State.PLAYING;
                    }
                    case KeyEvent.VK_Q -> {
                        if (state == State.PAUSED || state == State.OVER) toMenu();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> left = false;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> right = false;
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                moveMouse(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                moveMouse(e.getX());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                action();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        toMenu();
        loop.start();
    }

    /** Called by the Main menu when this game is closed. */
    @Override
    public void stop() {
        loop.stop();
    }

    private void moveMouse(int mx) {
        if (state == State.PLAYING) {
            paddleX = mx;
            clampPaddle();
        }
    }

    private void clampPaddle() {
        paddleX = Math.max(paddleW / 2, Math.min(W - paddleW / 2, paddleX));
    }

    // ---------- flow ----------
    private void toMenu() {
        state = State.MENU;
        level = 1;
        buildLevel();
        balls.clear();
        drops.clear();
    }

    private void action() {
        switch (state) {
            case MENU, OVER -> newGame();
            case PLAYING -> launch();
            case CLEAR -> nextLevel();
            case PAUSED -> state = State.PLAYING;
        }
    }

    private void newGame() {
        score = 0;
        lives = 3;
        level = 1;
        newRecord = false;
        buildLevel();
        resetBall();
        state = State.PLAYING;
    }

    private void nextLevel() {
        level++;
        buildLevel();
        resetBall();
        state = State.PLAYING;
    }

    private void resetBall() {
        balls.clear();
        drops.clear();
        Ball b = new Ball();
        balls.add(b);
        wideTimer = 0;
        slowTimer = 0;
        combo = 0;
        paddleW = PADDLE_BASE;
        paddleX = W / 2.0;
    }

    private void launch() {
        for (Ball b : balls) {
            if (b.stuck) {
                double a = (rnd.nextDouble() - 0.5) * 0.6;
                double sp = baseSpeed();
                b.vx = sp * Math.sin(a);
                b.vy = -sp * Math.cos(a);
                b.stuck = false;
            }
        }
    }

    private double baseSpeed() {
        return Math.min(9.0, 5.2 + 0.45 * (level - 1));
    }

    // ---------- level building ----------
    private void buildLevel() {
        bricks.clear();
        int rows = Math.min(6 + level / 2, 9);
        int pattern = (level - 1) % 4;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean place = switch (pattern) {
                    case 0 -> true;
                    case 1 -> (r + c) % 2 == 0;
                    case 2 -> Math.abs(c - 4.5) <= 0.5 + r * 0.75;
                    default -> c % 3 != 2;
                };
                boolean steel = false;
                if (pattern == 3 && c % 3 == 2 && (r == 2 || r == rows - 2)) {
                    place = true;
                    steel = true;
                }
                if (!place) continue;

                int hp = 1;
                if (level >= 2 && r < 2) hp = 2;
                if (level >= 4 && r == 0) hp = 3;

                Rectangle rect = new Rectangle(MARGIN + c * (BW + GAP), BRICK_TOP + r * (BH + GAP), BW, BH);
                bricks.add(new Brick(rect, steel ? 1 : hp, ROW_COLORS[r % ROW_COLORS.length], steel));
            }
        }
    }

    private int breakableLeft() {
        int n = 0;
        for (Brick b : bricks) if (b.alive && !b.steel) n++;
        return n;
    }

    // ---------- update ----------
    private void update() {
        phase += 0.06;
        if (shake > 0) shake--;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.12;
            p.vx *= 0.98;
            return --p.life <= 0;
        });

        if (state != State.PLAYING) return;

        // paddle
        if (left) paddleX -= 8;
        if (right) paddleX += 8;
        double targetW = wideTimer > 0 ? PADDLE_WIDE : PADDLE_BASE;
        paddleW += (targetW - paddleW) * 0.2;
        clampPaddle();
        if (wideTimer > 0) wideTimer--;
        if (slowTimer > 0) slowTimer--;

        // balls
        double factor = slowTimer > 0 ? 0.7 : 1.0;
        for (int i = balls.size() - 1; i >= 0; i--) {
            Ball b = balls.get(i);
            if (b.stuck) {
                b.x = paddleX;
                b.y = PADDLE_Y - BALL_R - 1;
                continue;
            }
            int steps = 3;
            for (int s = 0; s < steps; s++) {
                double mx = b.vx * factor / steps, my = b.vy * factor / steps;

                b.x += mx;
                if (b.x < BALL_R) {
                    b.x = BALL_R;
                    b.vx = Math.abs(b.vx);
                } else if (b.x > W - BALL_R) {
                    b.x = W - BALL_R;
                    b.vx = -Math.abs(b.vx);
                }
                Brick hit = brickAt(b);
                if (hit != null) {
                    b.x -= mx;
                    b.vx = -b.vx;
                    hitBrick(hit);
                }

                b.y += my;
                if (b.y < TOP + BALL_R) {
                    b.y = TOP + BALL_R;
                    b.vy = Math.abs(b.vy);
                }
                hit = brickAt(b);
                if (hit != null) {
                    b.y -= my;
                    b.vy = -b.vy;
                    hitBrick(hit);
                }

                if (b.vy > 0 && b.y < PADDLE_Y + PADDLE_H / 2.0 && hitsPaddle(b)) {
                    double rel = Math.max(-1, Math.min(1, (b.x - paddleX) / (paddleW / 2)));
                    double ang = rel * Math.toRadians(62);
                    double sp = baseSpeed();
                    b.vx = sp * Math.sin(ang);
                    b.vy = -sp * Math.cos(ang);
                    b.y = PADDLE_Y - BALL_R - 0.5;
                    combo = 0;
                }
            }
            b.trail.add(new double[]{b.x, b.y});
            if (b.trail.size() > 9) b.trail.remove(0);

            if (b.y - BALL_R > H) balls.remove(i);
        }

        // power-up drops
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            d.y += 2.6;
            double px = paddleX - paddleW / 2;
            if (d.y + 10 >= PADDLE_Y && d.y - 10 <= PADDLE_Y + PADDLE_H
                    && d.x >= px - 10 && d.x <= px + paddleW + 10) {
                applyPowerUp(d.type);
                burst(d.x, d.y, typeColor(d.type), 12);
                drops.remove(i);
            } else if (d.y > H + 20) {
                drops.remove(i);
            }
        }

        if (state == State.PLAYING && balls.isEmpty()) loseLife();
    }

    private boolean hitsPaddle(Ball b) {
        return circleRect(b.x, b.y, BALL_R, paddleX - paddleW / 2, PADDLE_Y, paddleW, PADDLE_H);
    }

    private Brick brickAt(Ball b) {
        for (Brick br : bricks) {
            if (!br.alive) continue;
            Rectangle r = br.r;
            if (circleRect(b.x, b.y, BALL_R, r.x, r.y, r.width, r.height)) return br;
        }
        return null;
    }

    private static boolean circleRect(double cx, double cy, double r, double rx, double ry, double rw, double rh) {
        double nx = Math.max(rx, Math.min(cx, rx + rw));
        double ny = Math.max(ry, Math.min(cy, ry + rh));
        double dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    private void hitBrick(Brick br) {
        double cx = br.r.getCenterX(), cy = br.r.getCenterY();
        if (br.steel) {
            burst(cx, cy, new Color(190, 195, 210), 3);
            return;
        }
        br.hp--;
        combo++;
        int mult = 1 + combo / 4;
        if (br.hp <= 0) {
            br.alive = false;
            score += 10 * br.maxHp * mult;
            burst(cx, cy, br.color, 14);
            if (rnd.nextDouble() < 0.2) drops.add(new Drop(cx, cy, randomType()));
            if (breakableLeft() == 0) levelClear();
        } else {
            score += 2 * mult;
            burst(cx, cy, Color.WHITE, 4);
        }
    }

    private Type randomType() {
        double r = rnd.nextDouble();
        if (r < 0.35) return Type.WIDE;
        if (r < 0.60) return Type.MULTI;
        if (r < 0.85) return Type.SLOW;
        return Type.LIFE;
    }

    private void applyPowerUp(Type t) {
        switch (t) {
            case WIDE -> wideTimer = 600;
            case SLOW -> slowTimer = 480;
            case LIFE -> lives = Math.min(lives + 1, 5);
            case MULTI -> {
                Ball src = null;
                for (Ball b : balls) if (!b.stuck) { src = b; break; }
                if (src == null) return;
                for (double rot : new double[]{0.35, -0.35}) {
                    Ball nb = new Ball();
                    nb.stuck = false;
                    nb.x = src.x;
                    nb.y = src.y;
                    nb.vx = src.vx * Math.cos(rot) - src.vy * Math.sin(rot);
                    nb.vy = src.vx * Math.sin(rot) + src.vy * Math.cos(rot);
                    balls.add(nb);
                }
            }
        }
    }

    private void levelClear() {
        state = State.CLEAR;
        clearBonus = 100 + 50 * lives;
        score += clearBonus;
        balls.clear();
        drops.clear();
        saveHigh();
    }

    private void loseLife() {
        lives--;
        shake = 14;
        if (lives <= 0) {
            state = State.OVER;
            saveHigh();
        } else {
            resetBall();
        }
    }

    private int high() {
        return prefs.getInt("breakout_high", 0);
    }

    private void saveHigh() {
        if (score > high()) {
            prefs.putInt("breakout_high", score);
            newRecord = true;
        }
    }

    private void burst(double x, double y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double sp = 0.8 + rnd.nextDouble() * 3.2;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp - 1, 20 + rnd.nextInt(16), c));
        }
    }

    // ---------- rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(18, 20, 40), 0, H, new Color(8, 8, 16)));
        g2.fillRect(0, 0, W, H);

        int sx = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        int sy = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        g2.translate(sx, sy);

        drawBricks(g2);
        if (state != State.MENU) {
            drawDrops(g2);
            drawPaddle(g2);
            drawBalls(g2);
        }
        drawParticles(g2);
        drawHud(g2);

        if (state == State.PLAYING) {
            for (Ball b : balls) {
                if (b.stuck) {
                    int a = (int) (140 + 100 * Math.sin(phase * 3));
                    drawCentered(g2, "Click or press SPACE to launch", new Font("SansSerif", Font.BOLD, 16),
                            new Color(255, 255, 255, Math.max(0, Math.min(255, a))), PADDLE_Y - 40);
                    break;
                }
            }
        }

        switch (state) {
            case MENU -> drawMenu(g2);
            case PAUSED -> overlay(g2, "PAUSED", "SPACE / P to resume   -   Q for menu", null);
            case CLEAR -> overlay(g2, "LEVEL " + level + " CLEAR!", "Bonus +" + clearBonus,
                    "Press SPACE for level " + (level + 1));
            case OVER -> overlay(g2, "GAME OVER",
                    "Score: " + score + (newRecord ? "    NEW RECORD!" : "    Best: " + high()),
                    "SPACE to retry   -   Q for menu");
            default -> { }
        }
        g2.translate(-sx, -sy);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(10, 10, 18, 230));
        g2.fillRect(0, 0, W, TOP);
        g2.setColor(new Color(60, 64, 90));
        g2.drawLine(0, TOP, W, TOP);

        g2.setFont(new Font("SansSerif", Font.BOLD, 17));
        g2.setColor(Color.WHITE);
        g2.drawString("Score " + score, 14, 31);
        g2.setColor(new Color(170, 175, 205));
        g2.drawString("Lv " + level, 160, 31);

        int mult = 1 + combo / 4;
        if (mult > 1 && state == State.PLAYING) {
            g2.setColor(new Color(255, 213, 79));
            g2.drawString("x" + mult, 226, 31);
        }

        // power-up timers
        int tx = 285;
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        if (wideTimer > 0) {
            g2.setColor(typeColor(Type.WIDE));
            g2.drawString("WIDE " + (wideTimer / 60 + 1) + "s", tx, 30);
            tx += 70;
        }
        if (slowTimer > 0) {
            g2.setColor(typeColor(Type.SLOW));
            g2.drawString("SLOW " + (slowTimer / 60 + 1) + "s", tx, 30);
        }

        // lives
        for (int i = 0; i < lives; i++) {
            g2.setColor(new Color(239, 83, 80));
            g2.fillOval(W - 22 - i * 22, 17, 14, 14);
            g2.setColor(new Color(255, 170, 170));
            g2.fillOval(W - 19 - i * 22, 20, 4, 4);
        }
    }

    private void drawBricks(Graphics2D g2) {
        for (Brick br : bricks) {
            if (!br.alive) continue;
            Rectangle r = br.r;
            if (br.steel) {
                g2.setPaint(new GradientPaint(r.x, r.y, new Color(170, 175, 190), r.x, r.y + r.height,
                        new Color(90, 95, 110)));
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
                g2.setColor(new Color(60, 64, 78));
                g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 6, 6);
                g2.drawLine(r.x + 6, r.y + 5, r.x + r.width - 6, r.y + r.height - 5);
                g2.drawLine(r.x + 6, r.y + r.height - 5, r.x + r.width - 6, r.y + 5);
                continue;
            }
            g2.setColor(br.color);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height / 2 - 2, 4, 4);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 6, 6);
            if (br.hp > 1) {
                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(br.hp >= 3 ? 3f : 2f));
                g2.drawRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, 5, 5);
                g2.setStroke(new BasicStroke(1f));
            }
        }
    }

    private void drawPaddle(Graphics2D g2) {
        int px = (int) (paddleX - paddleW / 2), pw = (int) paddleW;
        if (wideTimer > 0) {
            g2.setColor(new Color(66, 165, 245, 60));
            g2.fillRoundRect(px - 4, PADDLE_Y - 4, pw + 8, PADDLE_H + 8, 14, 14);
        }
        g2.setPaint(new GradientPaint(0, PADDLE_Y, new Color(235, 240, 255), 0, PADDLE_Y + PADDLE_H,
                new Color(110, 125, 175)));
        g2.fillRoundRect(px, PADDLE_Y, pw, PADDLE_H, 10, 10);
        g2.setColor(new Color(60, 70, 110));
        g2.drawRoundRect(px, PADDLE_Y, pw - 1, PADDLE_H - 1, 10, 10);
    }

    private void drawBalls(Graphics2D g2) {
        Color bc = slowTimer > 0 ? new Color(160, 255, 180) : Color.WHITE;
        for (Ball b : balls) {
            int n = b.trail.size();
            for (int i = 0; i < n; i++) {
                double[] t = b.trail.get(i);
                float f = (i + 1f) / n;
                g2.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), (int) (70 * f)));
                int r = (int) (BALL_R * (0.4 + 0.6 * f));
                g2.fillOval((int) (t[0] - r), (int) (t[1] - r), r * 2, r * 2);
            }
            g2.setColor(bc);
            g2.fillOval((int) (b.x - BALL_R), (int) (b.y - BALL_R), BALL_R * 2, BALL_R * 2);
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillOval((int) (b.x - 3), (int) (b.y - 4), 4, 4);
        }
    }

    private void drawDrops(Graphics2D g2) {
        for (Drop d : drops) drawDropIcon(g2, (int) d.x, (int) d.y, d.type);
    }

    private void drawDropIcon(Graphics2D g2, int cx, int cy, Type t) {
        g2.setColor(typeColor(t));
        g2.fillOval(cx - 11, cy - 11, 22, 22);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - 11, cy - 11, 22, 22);
        g2.setStroke(new BasicStroke(1f));
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        String s = typeLetter(t);
        g2.drawString(s, cx - g2.getFontMetrics().stringWidth(s) / 2, cy + 5);
    }

    private static Color typeColor(Type t) {
        return switch (t) {
            case WIDE -> new Color(66, 165, 245);
            case MULTI -> new Color(171, 71, 188);
            case SLOW -> new Color(76, 175, 80);
            case LIFE -> new Color(239, 83, 80);
        };
    }

    private static String typeLetter(Type t) {
        return switch (t) {
            case WIDE -> "W";
            case MULTI -> "M";
            case SLOW -> "S";
            case LIFE -> "+";
        };
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            int a = Math.max(0, Math.min(255, 255 * p.life / p.maxLife));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            g2.fillRect((int) p.x - 2, (int) p.y - 2, 4, 4);
        }
    }

    private void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 185));
        g2.fillRect(0, TOP, W, H - TOP);

        drawCentered(g2, "BREAKOUT", new Font("SansSerif", Font.BOLD, 62), new Color(255, 152, 67), 190);
        drawCentered(g2, "High score: " + high(), new Font("SansSerif", Font.PLAIN, 17),
                new Color(255, 213, 79), 230);

        int a = (int) (170 + 85 * Math.sin(phase * 3));
        drawCentered(g2, "Click or press SPACE to start", new Font("SansSerif", Font.BOLD, 22),
                new Color(255, 255, 255, Math.max(0, Math.min(255, a))), 290);

        Font small = new Font("SansSerif", Font.PLAIN, 14);
        Color grey = new Color(170, 175, 205);
        drawCentered(g2, "Move: Mouse or Left/Right (A/D)     Pause: P", small, grey, 335);

        Type[] types = Type.values();
        String[] desc = {"Wide paddle", "Multi-ball", "Slow ball", "Extra life"};
        int startX = 80;
        for (int i = 0; i < types.length; i++) {
            int x = startX + i * 135;
            drawDropIcon(g2, x, 390, types[i]);
            g2.setFont(small);
            g2.setColor(grey);
            g2.drawString(desc[i], x + 18, 395);
        }

        drawCentered(g2, "Chain hits without touching the paddle for a score multiplier!",
                small, new Color(255, 213, 79), 450);
        drawCentered(g2, "Grey bricks are indestructible", small, grey, 475);
    }

    private void overlay(Graphics2D g2, String title, String line1, String line2) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, TOP, W, H - TOP);
        drawCentered(g2, title, new Font("SansSerif", Font.BOLD, 52), Color.WHITE, H / 2 - 10);
        drawCentered(g2, line1, new Font("SansSerif", Font.BOLD, 19), new Color(255, 213, 79), H / 2 + 30);
        if (line2 != null) {
            drawCentered(g2, line2, new Font("SansSerif", Font.PLAIN, 15), new Color(170, 175, 205), H / 2 + 62);
        }
    }

    private void drawCentered(Graphics2D g2, String text, Font font, Color color, int y) {
        g2.setFont(font);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(color);
        g2.drawString(text, x, y);
    }

    // ---------- entry point ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Breakout");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Breakout game = new Breakout();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}