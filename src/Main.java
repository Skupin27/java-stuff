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
                    "Snake with levels, rocks,\ngolden fruit and slow-mo",
                    "Arrows/WASD   P: pause",
                    new Color(120, 230, 120),
                    Snek::new, Main::snekIcon),
            new Entry("Breakout",
                    "Brick breaker with combos,\npower-ups and steel blocks",
                    "Mouse/arrows   SPACE: launch",
                    new Color(255, 152, 67),
                    Breakout::new, Main::breakoutIcon),
            new Entry("Pong",
                    "Beat the CPU or challenge\na friend on one keyboard",
                    "W/S or mouse   Up/Down (2P)",
                    new Color(79, 195, 247),
                    Pong::new, Main::pongIcon),
            new Entry("Tetris",
                    "Stack, hold and hard-drop,\nwith ghost piece and levels",
                    "Arrows   Z/X rotate   C hold",
                    new Color(175, 80, 215),
                    Tetris::new, Main::tetrisIcon),
            new Entry("Minesweeper",
                    "Classic mine hunting with\n3 sizes and best times",
                    "Click / right-click to flag",
                    new Color(90, 165, 255),
                    Minesweeper::new, Main::minesweeperIcon),
            new Entry("2048",
                    "Slide and merge tiles to\nreach 2048. Has undo!",
                    "Arrows/WASD or swipe",
                    new Color(237, 194, 46),
                    Game2048::new, Main::game2048Icon),
            new Entry("Flappy",
                    "Tap to flap through pipes,\nchase medals and high score",
                    "SPACE / click to flap",
                    new Color(255, 214, 60),
                    Flappy::new, Main::flappyIcon),
            new Entry("Invaders",
                    "Pixel aliens, crumbling\nshields and a bonus UFO",
                    "Arrows/A,D   SPACE: fire",
                    new Color(255, 110, 200),
                    Invaders::new, Main::invadersIcon),
            new Entry("Memory",
                    "Flip cards, find every pair\nin as few moves as you can",
                    "Click cards   R: restart",
                    new Color(38, 198, 218),
                    Memory::new, Main::memoryIcon),
            new Entry("Connect 4",
                    "Four in a row vs a minimax\nAI or a friend (2 players)",
                    "Click a column / arrows",
                    new Color(231, 76, 60),
                    ConnectFour::new, Main::connect4Icon),
            new Entry("Gomoku",
                    "Five in a row on a 15x15\nboard vs AI or a friend",
                    "Click an intersection",
                    new Color(222, 184, 135),
                    Gomoku::new, Main::gomokuIcon),
            new Entry("Sudoku",
                    "Generated puzzles with notes,\nhints, undo and best times",
                    "Click/arrows  1-9  N: notes",
                    new Color(130, 170, 255),
                    Sudoku::new, Main::sudokuIcon)
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
    static final int W = 640, H = 640;
    static final int CARD_X = 20, CARD_Y = 165, CARD_W = 290, CARD_H = 112, CARD_GAP = 20, ROW_GAP = 14;

    static final int PER_PAGE = 6;

    private int selected = 0;

    private static int pageCount() {
        return (GAMES.length + PER_PAGE - 1) / PER_PAGE;
    }

    private void changePage(int delta) {
        int pages = pageCount();
        int pg = ((selected / PER_PAGE) + delta + pages) % pages;
        selected = pg * PER_PAGE;
    }

    private Rectangle prevArrow() {
        return new Rectangle(W / 2 - 96, 546, 36, 30);
    }

    private Rectangle nextArrow() {
        return new Rectangle(W / 2 + 60, 546, 36, 30);
    }
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
        setFocusTraversalKeysEnabled(false); // lets Tab switch pages

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
                int start = (selected / PER_PAGE) * PER_PAGE;
                int count = Math.min(PER_PAGE, n - start);
                int local = selected - start;
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    selected = (selected + n - 1) % n;
                } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    selected = (selected + 1) % n;
                } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    int next = local - 2;
                    if (next < 0) {
                        next = local;
                        while (next + 2 < count) next += 2;
                    }
                    selected = start + next;
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    int next = local + 2;
                    selected = start + (next >= count ? local % 2 : next);
                } else if (key == KeyEvent.VK_PAGE_UP) {
                    changePage(-1);
                } else if (key == KeyEvent.VK_PAGE_DOWN || key == KeyEvent.VK_TAB) {
                    changePage(1);
                } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                    launch(GAMES[selected]);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                } else if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_9) {
                    int idx = start + (key - KeyEvent.VK_1);
                    if (key - KeyEvent.VK_1 < PER_PAGE && idx < n) launch(GAMES[idx]);
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = cardAt(e.getPoint());
                if (i >= 0) selected = i;
                boolean arrow = pageCount() > 1 && (prevArrow().contains(e.getPoint()) || nextArrow().contains(e.getPoint()));
                setCursor(Cursor.getPredefinedCursor(i >= 0 || arrow ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (pageCount() > 1) {
                    if (prevArrow().contains(e.getPoint())) {
                        changePage(-1);
                        return;
                    }
                    if (nextArrow().contains(e.getPoint())) {
                        changePage(1);
                        return;
                    }
                }
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
        int local = i % PER_PAGE;
        return new Rectangle(CARD_X + (local % 2) * (CARD_W + CARD_GAP),
                CARD_Y + (local / 2) * (CARD_H + ROW_GAP), CARD_W, CARD_H);
    }

    private int cardAt(Point p) {
        int start = (selected / PER_PAGE) * PER_PAGE;
        for (int i = start; i < Math.min(GAMES.length, start + PER_PAGE); i++)
            if (cardRect(i).contains(p)) return i;
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
        int pageStart = (selected / PER_PAGE) * PER_PAGE;
        for (int i = pageStart; i < Math.min(GAMES.length, pageStart + PER_PAGE); i++) drawCard(g2, i);
        drawPager(g2);

        // footer
        Font small = new Font("SansSerif", Font.PLAIN, 13);
        Color grey = new Color(130, 135, 165);
        centered(g2, "Arrow keys + Enter, click a card, or press 1-" + Math.min(PER_PAGE, GAMES.length)
                + (pageCount() > 1 ? "      Tab: next page" : "") + "      Esc: quit", small, grey, H - 42);
        centered(g2, "Inside a game: Ctrl+M returns to this menu", small, grey, H - 22);
    }

    private void drawPager(Graphics2D g2) {
        int pages = pageCount();
        if (pages <= 1) return;
        int cur = selected / PER_PAGE;
        for (int j = 0; j < pages; j++) {
            int cx = (int) (W / 2 + (j - (pages - 1) / 2.0) * 22), cy = 561;
            g2.setColor(j == cur ? new Color(255, 255, 255, 230) : new Color(255, 255, 255, 70));
            int d = j == cur ? 10 : 7;
            g2.fillOval(cx - d / 2, cy - d / 2, d, d);
        }
        Rectangle[] arrows = {prevArrow(), nextArrow()};
        for (int a = 0; a < 2; a++) {
            Rectangle r = arrows[a];
            g2.setColor(new Color(40, 44, 72));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g2.setColor(new Color(90, 96, 140));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
            g2.setColor(Color.WHITE);
            int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
            if (a == 0) g2.fillPolygon(new int[]{cx + 4, cx + 4, cx - 5}, new int[]{cy - 6, cy + 6, cy}, 3);
            else g2.fillPolygon(new int[]{cx - 4, cx - 4, cx + 5}, new int[]{cy - 6, cy + 6, cy}, 3);
        }
    }

    private void drawCard(Graphics2D g2, int i) {
        Entry e = GAMES[i];
        Rectangle r = cardRect(i);
        boolean sel = i == selected;
        int lift = sel ? -2 : 0;

        if (sel) {
            g2.setColor(new Color(e.accent.getRed(), e.accent.getGreen(), e.accent.getBlue(),
                    40 + (int) (20 * Math.sin(phase * 4))));
            g2.fillRoundRect(r.x - 5, r.y - 5 + lift, r.width + 10, r.height + 10, 24, 24);
        }
        g2.setColor(sel ? new Color(44, 50, 84) : new Color(32, 36, 60));
        g2.fillRoundRect(r.x, r.y + lift, r.width, r.height, 18, 18);
        g2.setColor(sel ? e.accent : new Color(60, 66, 100));
        g2.setStroke(new BasicStroke(sel ? 2.5f : 1.5f));
        g2.drawRoundRect(r.x, r.y + lift, r.width, r.height, 18, 18);
        g2.setStroke(new BasicStroke(1f));

        // icon
        e.icon.paint(g2, r.x + 14, r.y + (r.height - 64) / 2 + lift, 64);

        // text
        int tx = r.x + 90;
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString(e.name, tx, r.y + 32 + lift);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(190, 195, 220));
        String[] lines = e.blurb.split("\n");
        for (int k = 0; k < lines.length; k++) {
            g2.drawString(lines[k], tx, r.y + 54 + k * 15 + lift);
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(sel ? e.accent : new Color(130, 135, 165));
        g2.drawString(e.controls, tx, r.y + 96 + lift);

        // number badge + play arrow
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(new Color(130, 135, 165));
        g2.drawString("[" + (i % PER_PAGE + 1) + "]", r.x + r.width - 28, r.y + 20 + lift);
        if (sel) {
            int ax = r.x + r.width - 34, ay = r.y + r.height - 26 + lift;
            int nudge = (int) (Math.sin(phase * 5) * 2);
            g2.setColor(e.accent);
            g2.fillPolygon(new int[]{ax + nudge, ax + 14 + nudge, ax + nudge},
                    new int[]{ay - 9, ay, ay + 9}, 3);
        }
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        g2.setFont(f);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    // ---------- icons ----------
    private static int k(int s, double v) {
        return (int) Math.round(v * s / 80.0);
    }

    private static void iconBg(Graphics2D g2, int x, int y, int s) {
        g2.setColor(new Color(20, 22, 34));
        g2.fillRoundRect(x, y, s, s, 14, 14);
    }

    private static void snekIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int c = s / 5;
        int[][] seg = {{0, 3}, {1, 3}, {2, 3}, {2, 2}, {2, 1}, {3, 1}}; // tail -> head
        for (int i = 0; i < seg.length; i++) {
            float t = (float) i / (seg.length - 1);
            g2.setColor(blend(new Color(30, 130, 80), new Color(130, 240, 130), t));
            g2.fillRoundRect(x + seg[i][0] * c + 1, y + seg[i][1] * c + 1, c - 2, c - 2, 6, 6);
        }
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 3 * c + c - 6, y + c + 2, 4, 4);
        g2.fillOval(x + 3 * c + c - 6, y + c + c - 6, 4, 4);
        g2.setColor(new Color(235, 70, 80));
        g2.fillOval(x + 4 * c + 2, y + c + 2, c - 4, c - 4);
    }

    private static void breakoutIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        Color[] cols = {new Color(239, 83, 80), new Color(255, 152, 67), new Color(255, 213, 79)};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                g2.setColor(cols[r]);
                g2.fillRoundRect(x + k(s, 6) + c * k(s, 17), y + k(s, 8) + r * k(s, 10), k(s, 15), k(s, 8), 3, 3);
            }
        }
        g2.setColor(Color.WHITE);
        g2.fillOval(x + k(s, 44), y + k(s, 48), k(s, 8), k(s, 8));
        g2.setColor(new Color(200, 210, 240));
        g2.fillRoundRect(x + k(s, 22), y + k(s, 66), k(s, 36), k(s, 6), 6, 6);
    }

    private static void pongIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        g2.setColor(new Color(255, 255, 255, 70));
        for (int yy = y + 4; yy < y + s - 6; yy += k(s, 10)) g2.fillRect(x + s / 2 - 1, yy, 2, k(s, 5));
        g2.setColor(new Color(79, 195, 247));
        g2.fillRoundRect(x + k(s, 8), y + k(s, 16), k(s, 7), k(s, 28), 4, 4);
        g2.setColor(new Color(255, 152, 67));
        g2.fillRoundRect(x + s - k(s, 15), y + k(s, 36), k(s, 7), k(s, 28), 4, 4);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + k(s, 40), y + k(s, 28), k(s, 9), k(s, 9));
    }

    private static void tetrisIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int q = s / 6;
        int ox = x + (s - q * 6) / 2, oy = y + (s - q * 6) / 2;
        // {col, row, color index}
        int[][] cells = {{0, 5, 0}, {1, 5, 0}, {2, 5, 0}, {3, 5, 0}, {4, 5, 4}, {5, 5, 5},
                {0, 4, 2}, {1, 4, 2}, {1, 3, 2}, {4, 4, 4}, {5, 4, 5}, {5, 3, 5},
                {3, 0, 1}, {4, 0, 1}, {3, 1, 1}, {4, 1, 1}};
        Color[] pal = {new Color(0, 220, 230), new Color(250, 220, 40), new Color(175, 80, 215),
                new Color(90, 215, 90), new Color(240, 70, 70), new Color(70, 110, 240)};
        for (int[] c : cells) {
            g2.setColor(pal[c[2]]);
            g2.fillRoundRect(ox + c[0] * q + 1, oy + c[1] * q + 1, q - 2, q - 2, 3, 3);
        }
    }

    private static void minesweeperIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int q = (s - 8) / 3;
        int ox = x + 4, oy = y + 4;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int cx = ox + c * q, cy = oy + r * q;
                boolean open = (r == 0 && c == 0) || (r == 0 && c == 1) || (r == 1 && c == 0);
                g2.setColor(open ? new Color(34, 38, 58) : new Color(70, 80, 120));
                g2.fillRoundRect(cx + 1, cy + 1, q - 2, q - 2, 4, 4);
            }
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, q * 2 / 3)));
        g2.setColor(new Color(90, 165, 255));
        g2.drawString("1", ox + q / 2 - 3, oy + q / 2 + 5);
        g2.setColor(new Color(95, 205, 115));
        g2.drawString("2", ox + q + q / 2 - 3, oy + q / 2 + 5);
        g2.setColor(new Color(255, 105, 105));
        g2.drawString("3", ox + q / 2 - 3, oy + q + q / 2 + 5);
        // flag
        int fx = ox + q + q / 2 - 4, fy = oy + q + 4;
        g2.setColor(new Color(235, 235, 240));
        g2.fillRect(fx + 2, fy, 2, q - 8);
        g2.setColor(new Color(240, 70, 70));
        g2.fillPolygon(new int[]{fx + 4, fx + 4, fx + 12}, new int[]{fy, fy + 7, fy + 3}, 3);
        // mine
        int mx = ox + 2 * q + q / 2, my = oy + 2 * q + q / 2;
        g2.setColor(new Color(235, 235, 240));
        g2.drawLine(mx - 7, my, mx + 7, my);
        g2.drawLine(mx, my - 7, mx, my + 7);
        g2.setColor(new Color(20, 20, 30));
        g2.fillOval(mx - 5, my - 5, 10, 10);
        g2.setColor(new Color(235, 235, 240));
        g2.drawOval(mx - 5, my - 5, 10, 10);
    }

    private static void game2048Icon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int q = (s - 12) / 2;
        int[] vals = {2, 4, 8, 16};
        Color[] cols = {new Color(238, 228, 218), new Color(237, 224, 200),
                new Color(242, 177, 121), new Color(245, 149, 99)};
        for (int i = 0; i < 4; i++) {
            int tx = x + 4 + (i % 2) * (q + 4), ty = y + 4 + (i / 2) * (q + 4);
            g2.setColor(cols[i]);
            g2.fillRoundRect(tx, ty, q, q, 8, 8);
            g2.setFont(new Font("SansSerif", Font.BOLD, vals[i] >= 10 ? q / 2 : q * 3 / 5));
            g2.setColor(vals[i] <= 4 ? new Color(119, 110, 101) : Color.WHITE);
            String t = String.valueOf(vals[i]);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, tx + (q - fm.stringWidth(t)) / 2, ty + (q + fm.getAscent()) / 2 - 2);
        }
    }

    private static void flappyIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        Shape oldClip = g2.getClip();
        g2.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, s, s, 14, 14));
        g2.setPaint(new GradientPaint(0, y, new Color(112, 200, 245), 0, y + s, new Color(200, 238, 250)));
        g2.fillRect(x, y, s, s);
        g2.setColor(new Color(222, 196, 120));
        g2.fillRect(x, y + k(s, 68), s, s);
        g2.setColor(new Color(120, 200, 70));
        g2.fillRect(x, y + k(s, 68), s, k(s, 5));
        g2.setColor(new Color(96, 190, 60));
        g2.fillRect(x + k(s, 52), y, k(s, 16), k(s, 26));
        g2.fillRect(x + k(s, 52), y + k(s, 50), k(s, 16), k(s, 24));
        g2.setColor(new Color(60, 140, 40));
        g2.fillRect(x + k(s, 49), y + k(s, 22), k(s, 22), k(s, 6));
        g2.fillRect(x + k(s, 49), y + k(s, 48), k(s, 22), k(s, 6));
        g2.setClip(oldClip);
        int bx = x + k(s, 28), by = y + k(s, 40), br = k(s, 11);
        g2.setColor(new Color(255, 214, 60));
        g2.fillOval(bx - br, by - br, br * 2, br * 2);
        g2.setColor(new Color(255, 238, 170));
        g2.fillOval(bx - br + 1, by - 1, br, br * 2 / 3);
        g2.setColor(Color.WHITE);
        g2.fillOval(bx + 1, by - br + 1, br * 2 / 3 + 1, br * 2 / 3 + 1);
        g2.setColor(Color.BLACK);
        g2.fillOval(bx + br / 3 + 2, by - br / 2 + 1, 3, 3);
        g2.setColor(new Color(245, 110, 50));
        g2.fillPolygon(new int[]{bx + br - 1, bx + br + k(s, 8), bx + br - 1},
                new int[]{by - 1, by + 2, by + 5}, 3);
    }

    private static void invadersIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int px = Math.max(2, s / 16);
        String[] a = Invaders.SPRITES[0][0];
        int ax = x + (s - 8 * px) / 2;
        g2.setColor(new Color(255, 110, 200));
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (a[r].charAt(c) == 'X') g2.fillRect(ax + c * px, y + k(s, 9) + r * px, px, px);
        g2.setColor(Color.WHITE);
        g2.fillRect(x + s / 2 - 1, y + k(s, 46), 2, k(s, 9));
        g2.setColor(new Color(110, 245, 130));
        g2.fillRect(x + s / 2 - k(s, 12), y + k(s, 66), k(s, 24), k(s, 6));
        g2.fillRect(x + s / 2 - k(s, 4), y + k(s, 61), k(s, 8), k(s, 6));
    }

    private static void memoryIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int q = (s - 14) / 2;
        for (int i = 0; i < 4; i++) {
            int cx = x + 5 + (i % 2) * (q + 4), cy = y + 5 + (i / 2) * (q + 4);
            boolean face = i == 1 || i == 2;
            if (face) {
                g2.setColor(new Color(244, 246, 252));
                g2.fillRoundRect(cx, cy, q, q, 8, 8);
                if (i == 1) {
                    g2.setColor(new Color(239, 83, 80));
                    g2.fillOval(cx + q / 4, cy + q / 4, q / 2, q / 2);
                } else {
                    g2.setColor(new Color(66, 165, 245));
                    g2.fillRoundRect(cx + q / 4, cy + q / 4, q / 2, q / 2, 4, 4);
                }
            } else {
                g2.setPaint(new GradientPaint(cx, cy, new Color(92, 80, 190), cx + q, cy + q, new Color(52, 60, 150)));
                g2.fillRoundRect(cx, cy, q, q, 8, 8);
                g2.setColor(new Color(255, 255, 255, 70));
                int d = q / 5;
                g2.fillPolygon(new int[]{cx + q / 2, cx + q / 2 + d, cx + q / 2, cx + q / 2 - d},
                        new int[]{cy + q / 2 - d, cy + q / 2, cy + q / 2 + d, cy + q / 2}, 4);
            }
        }
    }

    private static void connect4Icon(Graphics2D g2, int x, int y, int s) {
        g2.setColor(new Color(46, 92, 220));
        g2.fillRoundRect(x, y, s, s, 14, 14);
        int[][] pat = {{0, 0, 0, 0}, {0, 0, 2, 0}, {0, 1, 1, 2}, {1, 2, 1, 1}};
        int q = s / 4;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) {
                int d = q - 5;
                int cx = x + c * q + 2, cy = y + r * q + 2;
                g2.setColor(pat[r][c] == 0 ? new Color(20, 22, 38)
                        : pat[r][c] == 1 ? new Color(231, 76, 60) : new Color(244, 200, 30));
                g2.fillOval(cx, cy, d, d);
            }
    }

    private static void gomokuIcon(Graphics2D g2, int x, int y, int s) {
        g2.setPaint(new GradientPaint(x, y, new Color(228, 190, 132), x, y + s, new Color(202, 158, 98)));
        g2.fillRoundRect(x, y, s, s, 14, 14);
        g2.setColor(new Color(92, 64, 32));
        int m = s / 8, step = (s - 2 * m) / 4;
        for (int i = 0; i < 5; i++) {
            g2.drawLine(x + m, y + m + i * step, x + m + 4 * step, y + m + i * step);
            g2.drawLine(x + m + i * step, y + m, x + m + i * step, y + m + 4 * step);
        }
        // {col, row, colour} stones: black / white diagonal run
        int[][] stones = {{1, 1, 1}, {2, 1, 2}, {2, 2, 1}, {1, 2, 2}, {3, 3, 1}, {3, 2, 2}, {0, 0, 2}, {4, 4, 2}};
        int r = step * 2 / 5 + 1;
        for (int[] st : stones) {
            int cx = x + m + st[0] * step, cy = y + m + st[1] * step;
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(cx - r * 0.3f, cy - r * 0.4f), r * 1.6f,
                    new float[]{0f, 1f}, st[2] == 1
                    ? new Color[]{new Color(95, 95, 100), new Color(10, 10, 14)}
                    : new Color[]{Color.WHITE, new Color(190, 192, 200)}));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    private static void sudokuIcon(Graphics2D g2, int x, int y, int s) {
        iconBg(g2, x, y, s);
        int m = 4, cell = (s - 2 * m) / 3;
        int[][] digits = {{5, 3, 0}, {0, 7, 0}, {1, 0, 9}};
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                int cx = x + m + c * cell, cy = y + m + r * cell;
                g2.setColor(r == 1 && c == 1 ? new Color(74, 94, 160) : new Color(34, 38, 66));
                g2.fillRect(cx, cy, cell, cell);
                if (digits[r][c] != 0) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, cell * 3 / 5));
                    g2.setColor(r == 2 && c == 0 ? new Color(110, 180, 255) : Color.WHITE);
                    String t = String.valueOf(digits[r][c]);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(t, cx + (cell - fm.stringWidth(t)) / 2, cy + (cell + fm.getAscent()) / 2 - 2);
                }
            }
        g2.setColor(new Color(52, 56, 88));
        for (int i = 0; i <= 3; i++) {
            g2.drawLine(x + m, y + m + i * cell, x + m + 3 * cell, y + m + i * cell);
            g2.drawLine(x + m + i * cell, y + m, x + m + i * cell, y + m + 3 * cell);
        }
        g2.setColor(new Color(170, 176, 214));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(x + m, y + m, 3 * cell, 3 * cell);
        g2.setStroke(new BasicStroke(1f));
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