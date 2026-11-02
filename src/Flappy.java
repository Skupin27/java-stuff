import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Flappy: tap SPACE / Up / W / click to flap, fly through the gaps.
 */
public class Flappy extends JPanel implements Main.Game {

    static final int W = 480, H = 640, GROUND = H - 90;
    static final int BIRD_X = 130, BR = 15, PIPE_W = 72, GAP = 165, SPACING = 240;

    enum State { READY, PLAYING, OVER }

    static class Pipe {
        double x;
        int gapY;
        boolean passed;

        Pipe(double x, int gapY) {
            this.x = x;
            this.gapY = gapY;
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
    private final Preferences prefs = Preferences.userNodeForPackage(Flappy.class);
    private final ArrayList<Pipe> pipes = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();

    private State state = State.READY;
    private double by, bvy, speed = 2, scroll, phase;
    private int score, best, shake, deadTimer;
    private boolean newBest;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Flappy() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(112, 200, 245));
        setFocusable(true);
        best = prefs.getInt("flappy_best", 0);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W || k == KeyEvent.VK_ENTER) {
                    flapOrStart();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                flapOrStart();
            }
        });

        resetGame();
        loop.start();
    }

    @Override
    public void stop() {
        loop.stop();
    }

    // ---------- flow ----------
    private void resetGame() {
        pipes.clear();
        by = H / 2.0 - 60;
        bvy = 0;
        score = 0;
        speed = 2;
        newBest = false;
        state = State.READY;
    }

    private void flapOrStart() {
        switch (state) {
            case READY -> {
                state = State.PLAYING;
                speed = 3;
                flap();
            }
            case PLAYING -> flap();
            case OVER -> {
                if (deadTimer > 30) resetGame();
            }
        }
    }

    private void flap() {
        bvy = -7.6;
        for (int i = 0; i < 4; i++) {
            particles.add(new Particle(BIRD_X - 10, by + 6, -1 - rnd.nextDouble() * 2, rnd.nextDouble() * 2 - 0.5,
                    14 + rnd.nextInt(8), new Color(255, 255, 255, 220)));
        }
    }

    private void die() {
        state = State.OVER;
        shake = 12;
        deadTimer = 0;
        for (int i = 0; i < 22; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 1 + rnd.nextDouble() * 4;
            particles.add(new Particle(BIRD_X, by, Math.cos(a) * sp, Math.sin(a) * sp, 26 + rnd.nextInt(14),
                    rnd.nextBoolean() ? new Color(255, 214, 60) : new Color(255, 255, 255)));
        }
        if (score > best) {
            best = score;
            newBest = true;
            prefs.putInt("flappy_best", best);
        }
    }

    // ---------- update ----------
    private void update() {
        phase += 0.05;
        if (shake > 0) shake--;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.08;
            return --p.life <= 0;
        });

        if (state != State.OVER) scroll = (scroll + speed) % 48;

        if (state == State.READY) {
            by = H / 2.0 - 60 + Math.sin(phase * 4) * 9;
            return;
        }

        if (state == State.OVER) {
            deadTimer++;
            if (by < GROUND - BR) {
                bvy = Math.min(14, bvy + 0.7);
                by = Math.min(GROUND - BR, by + bvy);
            }
            return;
        }

        bvy = Math.min(11, bvy + 0.46);
        by += bvy;
        if (by < BR) {
            by = BR;
            bvy = 0;
        }

        for (Pipe p : pipes) p.x -= speed;
        if (pipes.isEmpty() || pipes.get(pipes.size() - 1).x < W - SPACING) {
            pipes.add(new Pipe(W + 20, 150 + rnd.nextInt(GROUND - 300)));
        }
        pipes.removeIf(p -> p.x + PIPE_W < -10);

        for (Pipe p : pipes) {
            if (!p.passed && p.x + PIPE_W < BIRD_X - BR) {
                p.passed = true;
                score++;
                speed = Math.min(5.2, 3 + score * 0.05);
            }
            int topH = p.gapY - GAP / 2, botY = p.gapY + GAP / 2;
            if (circleRect(BIRD_X, by, BR - 2, p.x, 0, PIPE_W, topH)
                    || circleRect(BIRD_X, by, BR - 2, p.x, botY, PIPE_W, H)) {
                die();
                return;
            }
        }
        if (by + BR >= GROUND) {
            by = GROUND - BR;
            die();
        }
    }

    private static boolean circleRect(double cx, double cy, double r, double rx, double ry, double rw, double rh) {
        double nx = Math.max(rx, Math.min(cx, rx + rw)), ny = Math.max(ry, Math.min(cy, ry + rh));
        double dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    // ---------- rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int sx = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        int sy = shake > 0 ? rnd.nextInt(shake + 1) - shake / 2 : 0;
        g2.translate(sx, sy);

        drawSky(g2);
        for (Pipe p : pipes) drawPipe(g2, p);
        drawGround(g2);
        drawBird(g2);

        for (Particle p : particles) {
            int a = Math.max(0, Math.min(255, p.color.getAlpha() * p.life / p.maxLife));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            g2.fillOval((int) p.x - 3, (int) p.y - 3, 6, 6);
        }

        if (state != State.READY) bigText(g2, String.valueOf(score), 80, 64);

        if (state == State.READY) {
            bigText(g2, "FLAPPY", 190, 64);
            centered(g2, "Press SPACE or click to flap", new Font("SansSerif", Font.BOLD, 20), Color.WHITE, 250);
            centered(g2, "Best: " + best, new Font("SansSerif", Font.PLAIN, 16), new Color(255, 245, 190), 280);
        } else if (state == State.OVER && deadTimer > 12) {
            drawGameOver(g2);
        }
        g2.translate(-sx, -sy);
    }

    private void drawSky(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, new Color(112, 200, 245), 0, GROUND, new Color(200, 238, 250)));
        g2.fillRect(-20, -20, W + 40, GROUND + 20);

        // clouds
        g2.setColor(new Color(255, 255, 255, 190));
        for (int i = 0; i < 5; i++) {
            double cx = ((i * 190 - phase * (6 + i * 2)) % (W + 240) + (W + 240)) % (W + 240) - 100;
            int cy = 70 + (i * 83) % 260;
            g2.fillOval((int) cx, cy, 90, 34);
            g2.fillOval((int) cx + 22, cy - 14, 54, 38);
            g2.fillOval((int) cx + 50, cy + 2, 70, 28);
        }

        // hills
        g2.setColor(new Color(140, 214, 150));
        for (int i = -1; i < 4; i++) {
            double hx = i * 220 - (scroll * 0.3) % 220;
            g2.fillOval((int) hx, GROUND - 90, 260, 180);
        }
        g2.setColor(new Color(110, 196, 126));
        for (int i = -1; i < 4; i++) {
            double hx = i * 170 + 60 - (scroll * 0.55) % 170;
            g2.fillOval((int) hx, GROUND - 50, 200, 120);
        }
    }

    private void drawPipe(Graphics2D g2, Pipe p) {
        int x = (int) p.x, topH = p.gapY - GAP / 2, botY = p.gapY + GAP / 2;
        pipeBody(g2, x, -10, topH + 10, true);
        pipeBody(g2, x, botY, GROUND - botY, false);
    }

    private void pipeBody(Graphics2D g2, int x, int y, int h, boolean capAtBottom) {
        g2.setPaint(new GradientPaint(x, 0, new Color(96, 190, 60), x + PIPE_W, 0, new Color(60, 140, 40)));
        g2.fillRect(x + 4, y, PIPE_W - 8, h);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillRect(x + 10, y, 8, h);
        g2.setColor(new Color(40, 100, 30));
        g2.drawRect(x + 4, y, PIPE_W - 9, h);

        int capY = capAtBottom ? y + h - 26 : y;
        g2.setPaint(new GradientPaint(x - 2, 0, new Color(110, 205, 70), x + PIPE_W + 2, 0, new Color(60, 140, 40)));
        g2.fillRoundRect(x - 2, capY, PIPE_W + 4, 26, 6, 6);
        g2.setColor(new Color(40, 100, 30));
        g2.drawRoundRect(x - 2, capY, PIPE_W + 3, 26, 6, 6);
    }

    private void drawGround(Graphics2D g2) {
        g2.setColor(new Color(222, 196, 120));
        g2.fillRect(-20, GROUND, W + 40, H - GROUND + 20);
        g2.setColor(new Color(120, 200, 70));
        g2.fillRect(-20, GROUND, W + 40, 16);
        g2.setColor(new Color(90, 160, 50));
        for (int x = -48; x < W + 48; x += 48) {
            int sx = (int) (x - scroll);
            g2.fillPolygon(new int[]{sx, sx + 24, sx + 36, sx + 12},
                    new int[]{GROUND, GROUND, GROUND + 16, GROUND + 16}, 4);
        }
        g2.setColor(new Color(180, 154, 90));
        g2.drawLine(-20, GROUND + 16, W + 20, GROUND + 16);
    }

    private void drawBird(Graphics2D g2) {
        AffineTransform old = g2.getTransform();
        double angle = state == State.READY ? 0 : Math.max(-0.5, Math.min(1.2, bvy / 12.0));
        g2.rotate(angle, BIRD_X, by);

        int x = BIRD_X, y = (int) by;
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillOval(x - BR + 2, y - BR + 3, BR * 2, BR * 2);
        g2.setColor(new Color(255, 214, 60));
        g2.fillOval(x - BR, y - BR, BR * 2, BR * 2);
        g2.setColor(new Color(255, 170, 40));
        g2.drawOval(x - BR, y - BR, BR * 2, BR * 2);

        // wing
        double flapOffset = state == State.OVER ? 0 : Math.sin(phase * 22) * 4;
        g2.setColor(new Color(255, 238, 170));
        g2.fillOval(x - 14, (int) (y - 2 + flapOffset), 18, 12);
        g2.setColor(new Color(220, 150, 30));
        g2.drawOval(x - 14, (int) (y - 2 + flapOffset), 18, 12);

        // eye
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 3, y - 10, 12, 12);
        g2.setColor(Color.BLACK);
        g2.fillOval(x + 8, y - 6, 5, 5);

        // beak
        g2.setColor(new Color(245, 110, 50));
        g2.fillPolygon(new int[]{x + 11, x + 25, x + 11}, new int[]{y - 1, y + 4, y + 9}, 3);

        g2.setTransform(old);
    }

    private void drawGameOver(Graphics2D g2) {
        int pw = 300, ph = 230, px = (W - pw) / 2, py = 190;
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(px + 4, py + 6, pw, ph, 24, 24);
        g2.setColor(new Color(236, 224, 176));
        g2.fillRoundRect(px, py, pw, ph, 24, 24);
        g2.setColor(new Color(120, 90, 50));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(px, py, pw, ph, 24, 24);
        g2.setStroke(new BasicStroke(1f));

        centeredOn(g2, "GAME OVER", new Font("SansSerif", Font.BOLD, 34), new Color(230, 90, 50), px + pw / 2, py + 48);

        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(new Color(130, 100, 60));
        g2.drawString("SCORE", px + 40, py + 90);
        g2.drawString("BEST", px + 40, py + 150);
        g2.setFont(new Font("SansSerif", Font.BOLD, 34));
        g2.setColor(Color.WHITE);
        outlined(g2, String.valueOf(score), px + 40, py + 124);
        outlined(g2, String.valueOf(best), px + 40, py + 184);
        if (newBest) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(new Color(230, 90, 50));
            g2.drawString("NEW!", px + 100, py + 180);
        }

        // medal
        Color medal = score >= 40 ? new Color(255, 205, 60) : score >= 25 ? new Color(210, 215, 225)
                : score >= 10 ? new Color(205, 127, 50) : null;
        int mx = px + pw - 90, my = py + 128;
        if (medal != null) {
            g2.setColor(medal);
            g2.fillOval(mx - 34, my - 34, 68, 68);
            g2.setColor(medal.darker());
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(mx - 34, my - 34, 68, 68);
            g2.drawOval(mx - 24, my - 24, 48, 48);
            g2.setStroke(new BasicStroke(1f));
        } else {
            g2.setColor(new Color(0, 0, 0, 30));
            g2.fillOval(mx - 34, my - 34, 68, 68);
        }

        if (deadTimer > 30) {
            centeredOn(g2, "Press SPACE to retry", new Font("SansSerif", Font.BOLD, 15), new Color(120, 90, 50),
                    px + pw / 2, py + ph - 14);
        }
    }

    private void outlined(Graphics2D g2, String s, int x, int y) {
        Color c = g2.getColor();
        g2.setColor(new Color(60, 40, 20));
        for (int dx = -2; dx <= 2; dx += 2) for (int dy = -2; dy <= 2; dy += 2) g2.drawString(s, x + dx, y + dy);
        g2.setColor(c);
        g2.drawString(s, x, y);
    }

    private void bigText(Graphics2D g2, String s, int y, int size) {
        g2.setFont(new Font("SansSerif", Font.BOLD, size));
        int x = (W - g2.getFontMetrics().stringWidth(s)) / 2;
        g2.setColor(new Color(60, 40, 20));
        for (int dx = -3; dx <= 3; dx += 3) for (int dy = -3; dy <= 3; dy += 3) g2.drawString(s, x + dx, y + dy);
        g2.setColor(Color.WHITE);
        g2.drawString(s, x, y);
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        centeredOn(g2, text, f, c, W / 2, y);
    }

    private void centeredOn(Graphics2D g2, String text, Font f, Color c, int cx, int y) {
        g2.setFont(f);
        int x = cx - g2.getFontMetrics().stringWidth(text) / 2;
        g2.setColor(new Color(0, 0, 0, 90));
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Flappy");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Flappy game = new Flappy();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
