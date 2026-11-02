import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Memory Match: flip two cards at a time and find all the pairs in as few moves as you can.
 * Click cards. R restarts, Esc returns to the size picker.
 */
public class Memory extends JPanel implements Main.Game {

    static final int W = 640, H = 640, HUD = 70;
    static final String[] NAMES = {"Easy", "Normal", "Hard"};
    static final int[][] SIZES = {{4, 3}, {4, 4}, {6, 4}};  // cols, rows

    static final Color[] ICON_COLORS = {
            new Color(239, 83, 80), new Color(66, 165, 245), new Color(102, 187, 106),
            new Color(255, 167, 38), new Color(255, 214, 64), new Color(171, 71, 188),
            new Color(240, 98, 146), new Color(38, 198, 218), new Color(255, 112, 67),
            new Color(255, 235, 59), new Color(159, 168, 218), new Color(139, 195, 74)
    };

    enum State { MENU, PLAYING, WON }

    static class Card {
        final int icon;
        boolean matched, faceUp;
        double flip;   // 0 = face down, 1 = face up
        double glow;

        Card(int icon) {
            this.icon = icon;
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
    private final Preferences prefs = Preferences.userNodeForPackage(Memory.class);
    private final ArrayList<Card> cards = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();

    private State state = State.MENU;
    private int diff = 1, cols, rows;
    private int first = -1, second = -1, lockTimer, moves, pairs, totalPairs;
    private long startTime, endTime;
    private boolean newBest;
    private int cardW, cardH, gx, gy, hover = -1;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Memory() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (state == State.MENU) {
                    if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) diff = (diff + 2) % 3;
                    else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) diff = (diff + 1) % 3;
                    else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startGame();
                    else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_3) {
                        diff = k - KeyEvent.VK_1;
                        startGame();
                    }
                } else {
                    if (k == KeyEvent.VK_R) startGame();
                    else if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_M) state = State.MENU;
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (state == State.MENU) {
                    int i = menuOptionAt(e.getX(), e.getY());
                    if (i >= 0) diff = i;
                } else {
                    hover = cardAt(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (state == State.MENU) {
                    int i = menuOptionAt(e.getX(), e.getY());
                    if (i >= 0) {
                        diff = i;
                        startGame();
                    }
                    return;
                }
                if (menuButton().contains(e.getPoint())) {
                    state = State.MENU;
                    return;
                }
                if (state == State.WON) {
                    startGame();
                    return;
                }
                int i = cardAt(e.getX(), e.getY());
                if (i >= 0) pick(i);
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

    // ---------- geometry ----------
    private Rectangle menuButton() {
        return new Rectangle(14, 18, 74, 34);
    }

    private int menuOptionAt(int x, int y) {
        for (int i = 0; i < 3; i++) {
            if (new Rectangle(W / 2 - 220, 220 + i * 84, 440, 64).contains(x, y)) return i;
        }
        return -1;
    }

    private void layoutCards() {
        int gap = 14;
        int availW = W - 60, availH = H - HUD - 50;
        cardW = Math.min(150, (availW - (cols - 1) * gap) / cols);
        cardH = Math.min(170, (availH - (rows - 1) * gap) / rows);
        gx = (W - (cols * cardW + (cols - 1) * gap)) / 2;
        gy = HUD + (availH - (rows * cardH + (rows - 1) * gap)) / 2 + 10;
    }

    private Rectangle cardRect(int i) {
        int gap = 14;
        int c = i % cols, r = i / cols;
        return new Rectangle(gx + c * (cardW + gap), gy + r * (cardH + gap), cardW, cardH);
    }

    private int cardAt(int x, int y) {
        for (int i = 0; i < cards.size(); i++) if (cardRect(i).contains(x, y)) return i;
        return -1;
    }

    // ---------- game logic ----------
    private void startGame() {
        cols = SIZES[diff][0];
        rows = SIZES[diff][1];
        totalPairs = cols * rows / 2;
        cards.clear();
        ArrayList<Integer> icons = new ArrayList<>();
        for (int i = 0; i < totalPairs; i++) {
            icons.add(i);
            icons.add(i);
        }
        Collections.shuffle(icons, rnd);
        for (int ic : icons) cards.add(new Card(ic));
        first = -1;
        second = -1;
        lockTimer = 0;
        moves = 0;
        pairs = 0;
        startTime = 0;
        endTime = 0;
        newBest = false;
        state = State.PLAYING;
        layoutCards();
    }

    private void pick(int i) {
        Card c = cards.get(i);
        if (state != State.PLAYING || lockTimer > 0 || c.matched || c.faceUp) return;
        if (startTime == 0) startTime = System.currentTimeMillis();

        c.faceUp = true;
        if (first < 0) {
            first = i;
            return;
        }
        second = i;
        moves++;
        Card a = cards.get(first), b = cards.get(second);
        if (a.icon == b.icon) {
            a.matched = true;
            b.matched = true;
            a.glow = 1;
            b.glow = 1;
            pairs++;
            burst(cardRect(first), ICON_COLORS[a.icon]);
            burst(cardRect(second), ICON_COLORS[a.icon]);
            first = -1;
            second = -1;
            if (pairs == totalPairs) win();
        } else {
            lockTimer = 52;
        }
    }

    private void win() {
        state = State.WON;
        endTime = System.currentTimeMillis();
        int best = best(diff);
        if (best == 0 || moves < best) {
            prefs.putInt("mem_best_" + diff, moves);
            newBest = true;
        }
        for (int i = 0; i < 60; i++) {
            particles.add(new Particle(rnd.nextInt(W), -10, rnd.nextDouble() * 2 - 1, 1 + rnd.nextDouble() * 3,
                    80 + rnd.nextInt(40), ICON_COLORS[rnd.nextInt(ICON_COLORS.length)]));
        }
    }

    private int best(int d) {
        return prefs.getInt("mem_best_" + d, 0);
    }

    private int elapsed() {
        if (startTime == 0) return 0;
        long end = state == State.WON ? endTime : System.currentTimeMillis();
        return (int) ((end - startTime) / 1000);
    }

    private void burst(Rectangle r, Color c) {
        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 1 + rnd.nextDouble() * 3.5;
            particles.add(new Particle(r.getCenterX(), r.getCenterY(), Math.cos(a) * sp, Math.sin(a) * sp,
                    24 + rnd.nextInt(16), c));
        }
    }

    private void update() {
        phase += 0.05;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.07;
            return --p.life <= 0;
        });
        for (Card c : cards) {
            double target = c.faceUp ? 1 : 0;
            if (c.flip < target) c.flip = Math.min(target, c.flip + 0.12);
            else if (c.flip > target) c.flip = Math.max(target, c.flip - 0.12);
            if (c.glow > 0) c.glow = Math.max(0, c.glow - 0.012);
        }
        if (lockTimer > 0 && --lockTimer == 0 && first >= 0 && second >= 0) {
            cards.get(first).faceUp = false;
            cards.get(second).faceUp = false;
            first = -1;
            second = -1;
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

        if (state == State.MENU) {
            drawMenu(g2);
            return;
        }
        drawHud(g2);
        for (int i = 0; i < cards.size(); i++) drawCard(g2, i);
        for (Particle p : particles) {
            int a = Math.max(0, Math.min(255, 255 * p.life / p.maxLife));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            g2.fillRect((int) p.x - 2, (int) p.y - 2, 5, 5);
        }
        if (state == State.WON) drawWin(g2);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(10, 10, 18, 230));
        g2.fillRect(0, 0, W, HUD);
        g2.setColor(new Color(60, 64, 90));
        g2.drawLine(0, HUD, W, HUD);

        Rectangle mb = menuButton();
        g2.setColor(new Color(40, 44, 72));
        g2.fillRoundRect(mb.x, mb.y, mb.width, mb.height, 10, 10);
        g2.setColor(new Color(90, 96, 140));
        g2.drawRoundRect(mb.x, mb.y, mb.width, mb.height, 10, 10);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("Menu", mb.x + 20, mb.y + 22);

        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("Moves: " + moves, 130, 34);
        g2.drawString("Pairs: " + pairs + "/" + totalPairs, 260, 34);
        g2.drawString(String.format("%d:%02d", elapsed() / 60, elapsed() % 60), 410, 34);
        g2.setColor(new Color(255, 213, 79));
        int b = best(diff);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.drawString(NAMES[diff] + "  -  Best: " + (b == 0 ? "--" : b + " moves"), 130, 56);
        g2.setColor(new Color(130, 135, 165));
        g2.drawString("R: restart", W - 90, 56);
    }

    private void drawCard(Graphics2D g2, int i) {
        Card c = cards.get(i);
        Rectangle r = cardRect(i);
        double sx = Math.abs(Math.cos(c.flip * Math.PI));
        double cx = r.getCenterX(), cy = r.getCenterY();
        boolean showFace = c.flip > 0.5;

        java.awt.geom.AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        double lift = (!c.matched && !c.faceUp && i == hover && state == State.PLAYING) ? 1.04 : 1.0;
        g2.scale(Math.max(0.02, sx) * lift, lift);
        int hw = r.width / 2, hh = r.height / 2;

        if (c.glow > 0) {
            g2.setColor(new Color(255, 255, 255, (int) (110 * c.glow)));
            g2.fillRoundRect(-hw - 6, -hh - 6, r.width + 12, r.height + 12, 22, 22);
        }

        if (showFace) {
            g2.setColor(c.matched ? new Color(236, 244, 255) : new Color(244, 246, 252));
            g2.fillRoundRect(-hw, -hh, r.width, r.height, 16, 16);
            g2.setColor(c.matched ? ICON_COLORS[c.icon] : new Color(150, 156, 190));
            g2.setStroke(new BasicStroke(c.matched ? 4f : 2f));
            g2.drawRoundRect(-hw, -hh, r.width, r.height, 16, 16);
            g2.setStroke(new BasicStroke(1f));
            drawIcon(g2, c.icon, 0, 0, Math.min(r.width, r.height) * 0.62);
        } else {
            g2.setPaint(new GradientPaint(-hw, -hh, new Color(92, 80, 190), hw, hh, new Color(52, 60, 150)));
            g2.fillRoundRect(-hw, -hh, r.width, r.height, 16, 16);
            g2.setColor(new Color(160, 150, 240, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(-hw + 8, -hh + 8, r.width - 16, r.height - 16, 10, 10);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(255, 255, 255, 60));
            int d = Math.min(r.width, r.height) / 5;
            g2.fillPolygon(new int[]{0, d, 0, -d}, new int[]{-d, 0, d, 0}, 4);
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fillRoundRect(-hw + 3, -hh + 3, r.width - 6, r.height / 3, 12, 12);
        }
        g2.setTransform(old);
    }

    private void drawIcon(Graphics2D g2, int idx, double cx, double cy, double size) {
        double s = size / 2;
        g2.setColor(ICON_COLORS[idx]);
        switch (idx) {
            case 0 -> g2.fill(new Ellipse2D.Double(cx - s, cy - s, s * 2, s * 2));
            case 1 -> g2.fill(new RoundRectangle2D.Double(cx - s * 0.85, cy - s * 0.85, s * 1.7, s * 1.7, s * 0.4, s * 0.4));
            case 2 -> poly(g2, cx, cy, new double[]{0, 1.05, -1.05}, new double[]{-1, 0.85, 0.85}, s);
            case 3 -> poly(g2, cx, cy, new double[]{0, 0.8, 0, -0.8}, new double[]{-1.05, 0, 1.05, 0}, s);
            case 4 -> {
                double[] xs = new double[10], ys = new double[10];
                for (int i = 0; i < 10; i++) {
                    double rad = i % 2 == 0 ? 1.1 : 0.45, a = -Math.PI / 2 + i * Math.PI / 5;
                    xs[i] = Math.cos(a) * rad;
                    ys[i] = Math.sin(a) * rad;
                }
                poly(g2, cx, cy, xs, ys, s);
            }
            case 5 -> {
                g2.fill(new RoundRectangle2D.Double(cx - s * 0.3, cy - s, s * 0.6, s * 2, s * 0.2, s * 0.2));
                g2.fill(new RoundRectangle2D.Double(cx - s, cy - s * 0.3, s * 2, s * 0.6, s * 0.2, s * 0.2));
            }
            case 6 -> {
                g2.fill(new Ellipse2D.Double(cx - s * 0.95, cy - s * 0.85, s * 1.0, s * 1.0));
                g2.fill(new Ellipse2D.Double(cx - s * 0.05, cy - s * 0.85, s * 1.0, s * 1.0));
                poly(g2, cx, cy, new double[]{-0.93, 0.93, 0}, new double[]{-0.1, -0.1, 1.0}, s);
            }
            case 7 -> {
                double[] xs = new double[6], ys = new double[6];
                for (int i = 0; i < 6; i++) {
                    xs[i] = Math.cos(i * Math.PI / 3) * 1.05;
                    ys[i] = Math.sin(i * Math.PI / 3) * 1.05;
                }
                poly(g2, cx, cy, xs, ys, s);
            }
            case 8 -> {
                Area ring = new Area(new Ellipse2D.Double(cx - s, cy - s, s * 2, s * 2));
                ring.subtract(new Area(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.5, s, s)));
                g2.fill(ring);
            }
            case 9 -> poly(g2, cx, cy,
                    new double[]{0.2, -0.65, -0.05, -0.2, 0.65, 0.05},
                    new double[]{-1.05, 0.12, 0.12, 1.05, -0.12, -0.12}, s);
            case 10 -> {
                Area moon = new Area(new Ellipse2D.Double(cx - s, cy - s, s * 2, s * 2));
                moon.subtract(new Area(new Ellipse2D.Double(cx - s * 0.35, cy - s * 1.05, s * 1.7, s * 1.7)));
                g2.fill(moon);
            }
            default -> {
                double r = s * 0.55;
                g2.fill(new Ellipse2D.Double(cx - r, cy - s, r * 2, r * 2));
                g2.fill(new Ellipse2D.Double(cx - r, cy, r * 2, r * 2 - s * 0.2));
                g2.fill(new Ellipse2D.Double(cx - s * 1.05, cy - s * 0.5, r * 2, r * 2));
                g2.fill(new Ellipse2D.Double(cx + s * 1.05 - r * 2, cy - s * 0.5, r * 2, r * 2));
            }
        }
    }

    private void poly(Graphics2D g2, double cx, double cy, double[] xs, double[] ys, double s) {
        int[] px = new int[xs.length], py = new int[xs.length];
        for (int i = 0; i < xs.length; i++) {
            px[i] = (int) Math.round(cx + xs[i] * s);
            py[i] = (int) Math.round(cy + ys[i] * s);
        }
        g2.fillPolygon(px, py, xs.length);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 66));
        int tw = g2.getFontMetrics().stringWidth("MEMORY"), tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, new Color(171, 71, 188), tx + tw, 0, new Color(38, 198, 218)));
        g2.drawString("MEMORY", tx, 130);
        centered(g2, "Find all the matching pairs", new Font("SansSerif", Font.PLAIN, 17), new Color(170, 175, 205), 172);

        // decorative icons
        for (int i = 0; i < 6; i++) drawIcon(g2, i * 2, W / 2 - 150 + i * 60, 196, 26);

        for (int i = 0; i < 3; i++) {
            boolean sel = i == diff;
            Rectangle r = new Rectangle(W / 2 - 220, 220 + i * 84, 440, 64);
            g2.setColor(sel ? new Color(44, 50, 84) : new Color(32, 36, 60));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setColor(sel ? new Color(38, 198, 218) : new Color(60, 66, 100));
            g2.setStroke(new BasicStroke(sel ? 2.5f : 1.5f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setStroke(new BasicStroke(1f));

            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2.setColor(Color.WHITE);
            g2.drawString(NAMES[i], r.x + 22, r.y + 29);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(new Color(170, 175, 205));
            g2.drawString(SIZES[i][0] + " x " + SIZES[i][1] + " cards  -  " + SIZES[i][0] * SIZES[i][1] / 2 + " pairs",
                    r.x + 22, r.y + 50);

            int b = best(i);
            String bt = b == 0 ? "No record" : "Best: " + b + " moves";
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(b == 0 ? new Color(130, 135, 165) : new Color(255, 213, 79));
            g2.drawString(bt, r.x + r.width - 22 - g2.getFontMetrics().stringWidth(bt), r.y + 38);
        }
        centered(g2, "Up / Down + Enter, press 1-3, or click", new Font("SansSerif", Font.PLAIN, 13),
                new Color(130, 135, 165), 505);
    }

    private void drawWin(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRect(0, HUD + 1, W, H);
        centered(g2, "YOU WIN!", new Font("SansSerif", Font.BOLD, 60), Color.WHITE, H / 2 - 20);
        centered(g2, moves + " moves  -  " + elapsed() + "s" + (newBest ? "   NEW BEST!" : ""),
                new Font("SansSerif", Font.BOLD, 20), new Color(255, 213, 79), H / 2 + 24);
        centered(g2, "Click or press R to play again   -   Esc for menu",
                new Font("SansSerif", Font.PLAIN, 15), new Color(170, 175, 205), H / 2 + 58);
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        g2.setFont(f);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Memory");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Memory game = new Memory();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
