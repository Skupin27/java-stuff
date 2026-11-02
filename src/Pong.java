import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * Pong: play the CPU (3 difficulties) or a friend on the same keyboard.
 * Left player: W/S (or mouse in 1P). Right player: Up/Down. First to 7 wins.
 */
public class Pong extends JPanel implements Main.Game {

    static final int W = 720, H = 480, PW = 14, PH = 90, BR = 8, WIN = 7;
    static final int L_X = 24, R_X = W - 24 - PW;
    static final String[] OPTIONS = {"1 Player  -  Easy", "1 Player  -  Normal", "1 Player  -  Hard", "2 Players"};
    static final double[] AI_SPEED = {3.6, 5.0, 6.8};
    static final double[] AI_ERROR = {55, 30, 10};
    static final Color LEFT_COLOR = new Color(79, 195, 247);
    static final Color RIGHT_COLOR = new Color(255, 152, 67);

    enum State { MENU, SERVE, PLAYING, PAUSED, OVER }

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
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<double[]> trail = new ArrayList<>();

    private State state = State.MENU;
    private State pausedFrom = State.PLAYING;
    private int option = 1;
    private boolean twoPlayer;
    private int aiLevel = 1;

    private double p1y = H / 2.0 - PH / 2.0, p2y = H / 2.0 - PH / 2.0;
    private double bx, by, bvx, bvy, speed = 6;
    private int s1, s2, serveTimer, serveDir = 1, shake;
    private double aiOffset, phase;
    private boolean w, s, up, down;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Pong() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_UP) {
                    up = true;
                    if (state == State.MENU) option = (option + 3) % 4;
                } else if (k == KeyEvent.VK_DOWN) {
                    down = true;
                    if (state == State.MENU) option = (option + 1) % 4;
                } else if (k == KeyEvent.VK_W) {
                    w = true;
                    if (state == State.MENU) option = (option + 3) % 4;
                } else if (k == KeyEvent.VK_S) {
                    s = true;
                    if (state == State.MENU) option = (option + 1) % 4;
                } else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
                    if (state == State.MENU || state == State.OVER) startMatch();
                    else if (state == State.PAUSED) state = pausedFrom;
                } else if (k == KeyEvent.VK_P || k == KeyEvent.VK_ESCAPE) {
                    if (state == State.PLAYING || state == State.SERVE) {
                        pausedFrom = state;
                        state = State.PAUSED;
                    } else if (state == State.PAUSED) {
                        state = pausedFrom;
                    }
                } else if (k == KeyEvent.VK_Q) {
                    if (state == State.PAUSED || state == State.OVER) state = State.MENU;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_UP) up = false;
                else if (k == KeyEvent.VK_DOWN) down = false;
                else if (k == KeyEvent.VK_W) w = false;
                else if (k == KeyEvent.VK_S) s = false;
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if ((state == State.PLAYING || state == State.SERVE) && !twoPlayer) {
                    p1y = e.getY() - PH / 2.0;
                    clampPaddles();
                }
                if (state == State.MENU) {
                    int i = optionAt(e.getY());
                    if (i >= 0) option = i;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (state == State.MENU) {
                    int i = optionAt(e.getY());
                    if (i >= 0) {
                        option = i;
                        startMatch();
                    }
                } else if (state == State.OVER) {
                    startMatch();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        loop.start();
    }

    @Override
    public void stop() {
        loop.stop();
    }

    private int optionAt(int y) {
        for (int i = 0; i < OPTIONS.length; i++) {
            int base = 250 + i * 46;
            if (y >= base - 32 && y <= base + 12) return i;
        }
        return -1;
    }

    // ---------- flow ----------
    private void startMatch() {
        twoPlayer = option == 3;
        if (!twoPlayer) aiLevel = option;
        s1 = 0;
        s2 = 0;
        serveDir = rnd.nextBoolean() ? 1 : -1;
        p1y = p2y = H / 2.0 - PH / 2.0;
        resetServe();
    }

    private void resetServe() {
        bx = W / 2.0;
        by = H / 2.0;
        bvx = 0;
        bvy = 0;
        speed = 6;
        trail.clear();
        serveTimer = 72;
        state = State.SERVE;
    }

    private void launch() {
        double a = (rnd.nextDouble() - 0.5) * 0.8;
        bvx = serveDir * speed * Math.cos(a);
        bvy = speed * Math.sin(a);
    }

    private void point(int scorer) {
        if (scorer == 1) s1++;
        else s2++;
        shake = 10;
        burst(scorer == 1 ? W - 10 : 10, by, scorer == 1 ? LEFT_COLOR : RIGHT_COLOR, 30);
        if (s1 >= WIN || s2 >= WIN) {
            state = State.OVER;
        } else {
            serveDir = scorer == 1 ? 1 : -1; // serve toward the player who conceded
            resetServe();
        }
    }

    // ---------- update ----------
    private void clampPaddles() {
        p1y = Math.max(0, Math.min(H - PH, p1y));
        p2y = Math.max(0, Math.min(H - PH, p2y));
    }

    private void movePaddles() {
        if (w || (!twoPlayer && up)) p1y -= 7;
        if (s || (!twoPlayer && down)) p1y += 7;

        if (twoPlayer) {
            if (up) p2y -= 7;
            if (down) p2y += 7;
        } else {
            double target = (bvx > 0 && state == State.PLAYING) ? by + aiOffset : H / 2.0;
            double diff = target - (p2y + PH / 2.0);
            if (Math.abs(diff) > 4) {
                double sp = AI_SPEED[aiLevel];
                p2y += Math.max(-sp, Math.min(sp, diff));
            }
        }
        clampPaddles();
    }

    private void update() {
        phase += 0.05;
        if (shake > 0) shake--;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.96;
            p.vy *= 0.96;
            return --p.life <= 0;
        });

        if (state == State.SERVE) {
            movePaddles();
            if (--serveTimer <= 0) {
                launch();
                state = State.PLAYING;
            }
            return;
        }
        if (state != State.PLAYING) return;

        movePaddles();

        int steps = 3;
        for (int i = 0; i < steps; i++) {
            bx += bvx / steps;
            by += bvy / steps;

            if (by < BR) {
                by = BR;
                bvy = Math.abs(bvy);
            } else if (by > H - BR) {
                by = H - BR;
                bvy = -Math.abs(bvy);
            }

            if (bvx < 0 && bx - BR <= L_X + PW && bx + BR >= L_X
                    && by >= p1y - BR && by <= p1y + PH + BR) {
                bounce(p1y, 1, LEFT_COLOR);
            } else if (bvx > 0 && bx + BR >= R_X && bx - BR <= R_X + PW
                    && by >= p2y - BR && by <= p2y + PH + BR) {
                bounce(p2y, -1, RIGHT_COLOR);
            }
        }

        trail.add(new double[]{bx, by});
        if (trail.size() > 12) trail.remove(0);

        if (bx < -BR * 2) point(2);
        else if (bx > W + BR * 2) point(1);
    }

    private void bounce(double paddleY, int dir, Color c) {
        double rel = Math.max(-1, Math.min(1, (by - (paddleY + PH / 2.0)) / (PH / 2.0)));
        double ang = rel * Math.toRadians(55);
        speed = Math.min(14, speed + 0.4);
        bvx = dir * speed * Math.cos(ang);
        bvy = speed * Math.sin(ang);
        bx = dir > 0 ? L_X + PW + BR : R_X - BR;
        aiOffset = (rnd.nextDouble() - 0.5) * 2 * AI_ERROR[aiLevel];
        burst(bx, by, c, 10);
    }

    private void burst(double x, double y, Color c, int n) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double sp = 1 + rnd.nextDouble() * 4;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 18 + rnd.nextInt(14), c));
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

        // center line
        g2.setColor(new Color(255, 255, 255, 50));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{12f, 14f}, 0f));
        g2.drawLine(W / 2, 0, W / 2, H);
        g2.setStroke(new BasicStroke(1f));

        if (state != State.MENU) {
            // scores
            g2.setFont(new Font("SansSerif", Font.BOLD, 72));
            g2.setColor(new Color(255, 255, 255, 60));
            String a = String.valueOf(s1), b = String.valueOf(s2);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(a, W / 4 - fm.stringWidth(a) / 2, 100);
            g2.drawString(b, 3 * W / 4 - fm.stringWidth(b) / 2, 100);

            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(new Color(LEFT_COLOR.getRed(), LEFT_COLOR.getGreen(), LEFT_COLOR.getBlue(), 160));
            g2.drawString(twoPlayer ? "P1" : "YOU", L_X, H - 12);
            g2.setColor(new Color(RIGHT_COLOR.getRed(), RIGHT_COLOR.getGreen(), RIGHT_COLOR.getBlue(), 160));
            String right = twoPlayer ? "P2" : "CPU";
            g2.drawString(right, R_X + PW - g2.getFontMetrics().stringWidth(right), H - 12);

            // paddles
            drawPaddle(g2, L_X, p1y, LEFT_COLOR);
            drawPaddle(g2, R_X, p2y, RIGHT_COLOR);

            // ball + trail
            for (int i = 0; i < trail.size(); i++) {
                double[] t = trail.get(i);
                float f = (i + 1f) / trail.size();
                int r = (int) (BR * (0.35 + 0.65 * f));
                g2.setColor(new Color(255, 255, 255, (int) (70 * f)));
                g2.fillOval((int) (t[0] - r), (int) (t[1] - r), r * 2, r * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillOval((int) (bx - BR), (int) (by - BR), BR * 2, BR * 2);

            for (Particle p : particles) {
                int al = Math.max(0, Math.min(255, 255 * p.life / p.maxLife));
                g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), al));
                g2.fillOval((int) p.x - 2, (int) p.y - 2, 5, 5);
            }
        }

        switch (state) {
            case MENU -> drawMenu(g2);
            case SERVE -> centered(g2, String.valueOf(serveTimer / 24 + 1),
                    new Font("SansSerif", Font.BOLD, 80), new Color(255, 255, 255, 150), H / 2 + 28);
            case PAUSED -> overlay(g2, "PAUSED", "SPACE / P to resume   -   Q for menu");
            case OVER -> {
                String msg;
                if (s1 > s2) msg = twoPlayer ? "PLAYER 1 WINS!" : "YOU WIN!";
                else msg = twoPlayer ? "PLAYER 2 WINS!" : "CPU WINS";
                overlay(g2, msg, "Final score  " + s1 + " - " + s2 + "        SPACE: rematch   -   Q: menu");
            }
            default -> { }
        }
        g2.translate(-sx, -sy);
    }

    private void drawPaddle(Graphics2D g2, int x, double y, Color c) {
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
        g2.fillRoundRect(x - 4, (int) y - 4, PW + 8, PH + 8, 14, 14);
        g2.setPaint(new GradientPaint(x, 0, c.brighter(), x + PW, 0, c.darker()));
        g2.fillRoundRect(x, (int) y, PW, PH, 10, 10);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, W, H);

        g2.setFont(new Font("SansSerif", Font.BOLD, 80));
        int tw = g2.getFontMetrics().stringWidth("PONG");
        int tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, LEFT_COLOR, tx + tw, 0, RIGHT_COLOR));
        g2.drawString("PONG", tx, 150);

        centered(g2, "First to " + WIN + " wins", new Font("SansSerif", Font.PLAIN, 16),
                new Color(170, 175, 205), 188);

        for (int i = 0; i < OPTIONS.length; i++) {
            boolean sel = i == option;
            int y = 250 + i * 46;
            if (sel) {
                g2.setColor(new Color(255, 255, 255, 25));
                g2.fillRoundRect(W / 2 - 170, y - 32, 340, 44, 16, 16);
            }
            centered(g2, sel ? ">  " + OPTIONS[i] + "  <" : OPTIONS[i],
                    new Font("SansSerif", sel ? Font.BOLD : Font.PLAIN, sel ? 24 : 21),
                    sel ? Color.WHITE : new Color(130, 135, 165), y);
        }

        Font small = new Font("SansSerif", Font.PLAIN, 13);
        Color grey = new Color(150, 155, 185);
        centered(g2, "Up / Down + Enter, or click an option", small, grey, 440);
        centered(g2, "Left: W/S or mouse     Right (2P): Up/Down     Pause: P", small, grey, 460);
    }

    private void overlay(Graphics2D g2, String title, String sub) {
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRect(0, 0, W, H);
        centered(g2, title, new Font("SansSerif", Font.BOLD, 56), Color.WHITE, H / 2 - 5);
        centered(g2, sub, new Font("SansSerif", Font.PLAIN, 16), new Color(255, 213, 79), H / 2 + 35);
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
            JFrame frame = new JFrame("Pong");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Pong game = new Pong();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}