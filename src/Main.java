import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Game launcher / arcade menu.
 *
 * To add a new game:
 *   1. Make a JPanel class that implements Main.Game (it needs a stop() method
 *      that stops its timers).
 *   2. Add one Entry to the GAMES array below.
 */
public class Main extends JPanel {

    /** Implemented by every game panel so the menu can shut it down. */
    public interface Game {
        void stop();
    }

    interface IconPainter {
        void paint(Graphics2D g2, int x, int y, int size);
    }

    static class Entry {
        final String name, blurb, controls;
        final Color accent;
        final Supplier<JPanel> factory;
        final IconPainter icon;

        Entry(String name, String blurb, String controls, Color accent,
              Supplier<JPanel> factory, IconPainter icon) {
            this.name = name;
            this.blurb = blurb;
            this.controls = controls;
            this.accent = accent;
            this.factory = factory;
            this.icon = icon;
        }
    }

    // ---------- register games here ----------
    static final Entry[] GAMES = {
            new Entry("Snek",
                    "Classic snake with difficulty levels,\nrocks, golden fruit and slow-mo orbs.",
                    "Arrows / WASD   -   P to pause",
                    new Color(120, 230, 120),
                    Snek::new, Main::snekIcon),
            new Entry("Breakout",
                    "Smash every brick with power-ups, combos\nand indestructible steel blocks.",
                    "Mouse or Left/Right   -   SPACE to launch",
                    new Color(255, 152, 67),
                    Breakout::new, Main::breakoutIcon)
    };

    // ---------- window handling ----------
    private static JFrame frame;
    private static Main menu;
    private static JMenuBar menuBar;
    private static JPanel current;

    private static void showMenu() {
        stopCurrent();
        frame.setJMenuBar(null);
        swapContent(menu);
    }

    private static void launch(Entry e) {
        stopCurrent();
        JPanel game = e.factory.get();
        current = game;
        frame.setJMenuBar(menuBar);
        frame.setTitle(e.name);
        swapContent(game);
    }

    private static void swapContent(JPanel panel) {
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.revalidate();
        frame.repaint();
        if (panel == menu) frame.setTitle("Game Arcade");
        SwingUtilities.invokeLater(panel::requestFocusInWindow);
    }

    private static void stopCurrent() {
        if (current instanceof Game g) g.stop();
        current = null;
    }

    private static void buildMenuBar() {
        menuBar = new JMenuBar();
        JMenu m = new JMenu("Arcade");
        m.setMnemonic(KeyEvent.VK_A);

        JMenuItem back = new JMenuItem("Back to menu");
        back.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
        back.addActionListener(e -> showMenu());

        JMenuItem quit = new JMenuItem("Quit");
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        quit.addActionListener(e -> System.exit(0));

        m.add(back);
        m.addSeparator();
        m.add(quit);
        menuBar.add(m);
    }

    // ---------- the menu panel ----------
    static final int W = 640, H = 560;
    static final int CARD_X = 60, CARD_Y = 170, CARD_W = 520, CARD_H = 104, CARD_GAP = 16;

    private int selected = 0;
    private double phase;
    private final Timer anim = new Timer(30, e -> {
        phase += 0.03;
        repaint();
    });

    private final double[] starX = new double[45], starY = new double[45],
            starSpeed = new double[45], starSize = new double[45];

    public Main() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(14, 16, 28));
        setFocusable(true);

        Random rnd = new Random();
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextDouble() * W;
            starY[i] = rnd.nextDouble() * H;
            starSpeed[i] = 6 + rnd.nextDouble() * 22;
            starSize[i] = 1 + rnd.nextDouble() * 2.2;
        }

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                int n = GAMES.length;
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selected = (selected + n - 1) % n;
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    selected = (selected + 1) % n;
                } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                    launch(GAMES[selected]);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                } else if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_9) {
                    int idx = key - KeyEvent.VK_1;
                    if (idx < n) launch(GAMES[idx]);
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = cardAt(e.getPoint());
                if (i >= 0) selected = i;
                setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int i = cardAt(e.getPoint());
                if (i >= 0) launch(GAMES[i]);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // run the background animation only while the menu is on screen
    @Override
    public void addNotify() {
        super.addNotify();
        anim.start();
    }

    @Override
    public void removeNotify() {
        anim.stop();
        super.removeNotify();
    }

    private Rectangle cardRect(int i) {
        return new Rectangle(CARD_X, CARD_Y + i * (CARD_H + CARD_GAP), CARD_W, CARD_H);
    }

    private int cardAt(Point p) {
        for (int i = 0; i < GAMES.length; i++) if (cardRect(i).contains(p)) return i;
        return -1;
    }

    // ---------- painting ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(20, 22, 44), 0, H, new Color(8, 8, 16)));
        g2.fillRect(0, 0, W, H);

        // drifting dots
        for (int i = 0; i < starX.length; i++) {
            double x = (starX[i] + phase * starSpeed[i] * 4) % W;
            g2.setColor(new Color(255, 255, 255, 40 + (int) (starSize[i] * 20)));
            g2.fill(new java.awt.geom.Ellipse2D.Double(x, starY[i], starSize[i], starSize[i]));
        }

        // title
        Font titleFont = new Font("SansSerif", Font.BOLD, 56);
        g2.setFont(titleFont);
        String title = "GAME ARCADE";
        int tw = g2.getFontMetrics().stringWidth(title);
        int tx = (W - tw) / 2, ty = 98 + (int) (Math.sin(phase * 2) * 3);
        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(title, tx + 3, ty + 3);
        g2.setPaint(new GradientPaint(tx, 0, new Color(120, 230, 120), tx + tw, 0, new Color(255, 152, 67)));
        g2.drawString(title, tx, ty);

        centered(g2, "Choose a game", new Font("SansSerif", Font.PLAIN, 17), new Color(170, 175, 205), 138);

        // cards
        for (int i = 0; i < GAMES.length; i++) drawCard(g2, i);

        // footer
        Font small = new Font("SansSerif", Font.PLAIN, 13);
        Color grey = new Color(130, 135, 165);
        centered(g2, "Up / Down + Enter, click a card, or press 1-" + GAMES.length + "      Esc: quit", small, grey, H - 42);
        centered(g2, "Inside a game: Ctrl+M returns to this menu", small, grey, H - 22);
    }

    private void drawCard(Graphics2D g2, int i) {
        Entry e = GAMES[i];
        Rectangle r = cardRect(i);
        boolean sel = i == selected;
        int lift = sel ? -2 : 0;

        if (sel) {
            g2.setColor(new Color(e.accent.getRed(), e.accent.getGreen(), e.accent.getBlue(),
                    40 + (int) (20 * Math.sin(phase * 4))));
            g2.fillRoundRect(r.x - 6, r.y - 6 + lift, r.width + 12, r.height + 12, 26, 26);
        }
        g2.setColor(sel ? new Color(44, 50, 84) : new Color(32, 36, 60));
        g2.fillRoundRect(r.x, r.y + lift, r.width, r.height, 20, 20);
        g2.setColor(sel ? e.accent : new Color(60, 66, 100));
        g2.setStroke(new BasicStroke(sel ? 2.5f : 1.5f));
        g2.drawRoundRect(r.x, r.y + lift, r.width, r.height, 20, 20);
        g2.setStroke(new BasicStroke(1f));

        // icon
        e.icon.paint(g2, r.x + 18, r.y + 12 + lift, 80);

        // text
        int tx = r.x + 118;
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2.setColor(Color.WHITE);
        g2.drawString(e.name, tx, r.y + 34 + lift);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(190, 195, 220));
        String[] lines = e.blurb.split("\n");
        for (int k = 0; k < lines.length; k++) {
            g2.drawString(lines[k], tx, r.y + 56 + k * 16 + lift);
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(sel ? e.accent : new Color(130, 135, 165));
        g2.drawString(e.controls, tx, r.y + 94 + lift);

        // number badge + play arrow
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(130, 135, 165));
        g2.drawString("[" + (i + 1) + "]", r.x + r.width - 34, r.y + 22 + lift);
        if (sel) {
            int ax = r.x + r.width - 40, ay = r.y + r.height / 2 + lift + 6;
            int nudge = (int) (Math.sin(phase * 5) * 3);
            g2.setColor(e.accent);
            g2.fillPolygon(new int[]{ax + nudge, ax + 18 + nudge, ax + nudge},
                    new int[]{ay - 12, ay, ay + 12}, 3);
        }
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        g2.setFont(f);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    // ---------- icons ----------
    private static void snekIcon(Graphics2D g2, int x, int y, int s) {
        g2.setColor(new Color(20, 22, 34));
        g2.fillRoundRect(x, y, s, s, 14, 14);
        int c = s / 5;
        int[][] seg = {{0, 3}, {1, 3}, {2, 3}, {2, 2}, {2, 1}, {3, 1}}; // tail -> head
        for (int i = 0; i < seg.length; i++) {
            float t = (float) i / (seg.length - 1);
            g2.setColor(blend(new Color(30, 130, 80), new Color(130, 240, 130), t));
            g2.fillRoundRect(x + seg[i][0] * c + 1, y + seg[i][1] * c + 1, c - 2, c - 2, 6, 6);
        }
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 3 * c + c - 7, y + c + 3, 4, 4);
        g2.fillOval(x + 3 * c + c - 7, y + c + c - 7, 4, 4);
        g2.setColor(new Color(235, 70, 80));
        g2.fillOval(x + 4 * c + 2, y + 1 * c + 2, c - 4, c - 4);
    }

    private static void breakoutIcon(Graphics2D g2, int x, int y, int s) {
        g2.setColor(new Color(20, 22, 34));
        g2.fillRoundRect(x, y, s, s, 14, 14);
        Color[] cols = {new Color(239, 83, 80), new Color(255, 152, 67), new Color(255, 213, 79)};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                g2.setColor(cols[r]);
                g2.fillRoundRect(x + 6 + c * 17, y + 8 + r * 10, 15, 8, 3, 3);
            }
        }
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 44, y + 48, 8, 8);
        g2.setColor(new Color(200, 210, 240));
        g2.fillRoundRect(x + 22, y + 66, 36, 6, 6, 6);
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ---------- entry point ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            frame = new JFrame("Game Arcade");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            menu = new Main();
            buildMenuBar();
            showMenu();
            frame.setVisible(true);
        });
    }
}