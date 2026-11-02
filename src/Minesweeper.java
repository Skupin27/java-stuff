import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Minesweeper. Left-click reveals, right-click flags, clicking a satisfied number
 * (or middle-click) reveals its neighbours. The first click is always safe.
 * R restarts, Esc returns to the size picker.
 */
public class Minesweeper extends JPanel implements Main.Game {

    static final int W = 720, H = 640, HUD = 64;
    static final String[] NAMES = {"Beginner", "Intermediate", "Expert"};
    static final int[][] LEVELS = {{9, 9, 10}, {16, 16, 40}, {30, 16, 99}}; // cols, rows, mines
    static final Color[] NUM_COLORS = {
            null, new Color(90, 165, 255), new Color(95, 205, 115), new Color(255, 105, 105),
            new Color(160, 130, 255), new Color(255, 155, 95), new Color(85, 215, 215),
            new Color(235, 235, 240), new Color(165, 165, 175)
    };

    enum State { MENU, PLAYING, WON, LOST }

    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Minesweeper.class);

    private State state = State.MENU;
    private int diff = 0, rows, cols, mines;
    private boolean[][] mine, revealed, flagged;
    private int[][] adj;
    private boolean first;
    private int revealedCount, flagCount, explR = -1, explC = -1;
    private long startTime, endTime;
    private boolean newBest;

    private int cell, ox, oy;
    private int hoverR = -1, hoverC = -1;
    private int menuHover = -1;

    private final Timer loop = new Timer(50, e -> repaint());

    public Minesweeper() {
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
                    menuHover = menuOptionAt(e.getX(), e.getY());
                    if (menuHover >= 0) diff = menuHover;
                } else {
                    hoverR = rowAt(e.getY());
                    hoverC = colAt(e.getX());
                    if (hoverR < 0 || hoverC < 0) {
                        hoverR = -1;
                        hoverC = -1;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverR = -1;
                hoverC = -1;
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
                if (faceDistance(e.getX(), e.getY()) <= 22) {
                    startGame();
                    return;
                }
                int r = rowAt(e.getY()), c = colAt(e.getX());
                if (r < 0 || c < 0) return;
                if (SwingUtilities.isRightMouseButton(e)) toggleFlag(r, c);
                else if (SwingUtilities.isMiddleMouseButton(e)) chord(r, c);
                else if (SwingUtilities.isLeftMouseButton(e)) leftClick(r, c);
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
        return new Rectangle(14, 16, 74, 32);
    }

    private double faceDistance(int x, int y) {
        return Math.hypot(x - W / 2.0, y - HUD / 2.0);
    }

    private int colAt(int x) {
        int c = Math.floorDiv(x - ox, Math.max(1, cell));
        return (x >= ox && c >= 0 && c < cols) ? c : -1;
    }

    private int rowAt(int y) {
        int r = Math.floorDiv(y - oy, Math.max(1, cell));
        return (y >= oy && r >= 0 && r < rows) ? r : -1;
    }

    private int menuOptionAt(int x, int y) {
        for (int i = 0; i < 3; i++) {
            Rectangle r = new Rectangle(W / 2 - 230, 210 + i * 80, 460, 62);
            if (r.contains(x, y)) return i;
        }
        return -1;
    }

    private void layoutBoard() {
        cell = Math.min((W - 40) / cols, (H - HUD - 30) / rows);
        ox = (W - cell * cols) / 2;
        oy = HUD + (H - HUD - 30 - cell * rows) / 2 + 8;
    }

    // ---------- game logic ----------
    private void startGame() {
        cols = LEVELS[diff][0];
        rows = LEVELS[diff][1];
        mines = LEVELS[diff][2];
        mine = new boolean[rows][cols];
        revealed = new boolean[rows][cols];
        flagged = new boolean[rows][cols];
        adj = new int[rows][cols];
        first = true;
        revealedCount = 0;
        flagCount = 0;
        explR = -1;
        explC = -1;
        newBest = false;
        startTime = 0;
        endTime = 0;
        state = State.PLAYING;
        layoutBoard();
    }

    private void placeMines(int sr, int sc) {
        ArrayList<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (Math.abs(r - sr) > 1 || Math.abs(c - sc) > 1) candidates.add(new int[]{r, c});
        Collections.shuffle(candidates, rnd);
        for (int i = 0; i < mines; i++) {
            int[] p = candidates.get(i);
            mine[p[0]][p[1]] = true;
        }
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int n = 0;
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++) {
                        int rr = r + dr, cc = c + dc;
                        if ((dr != 0 || dc != 0) && inBounds(rr, cc) && mine[rr][cc]) n++;
                    }
                adj[r][c] = n;
            }
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    private void leftClick(int r, int c) {
        if (state != State.PLAYING || flagged[r][c]) return;
        if (revealed[r][c]) {
            chord(r, c);
            return;
        }
        if (first) {
            placeMines(r, c);
            startTime = System.currentTimeMillis();
            first = false;
        }
        if (mine[r][c]) {
            lose(r, c);
            return;
        }
        reveal(r, c);
        checkWin();
    }

    private void reveal(int sr, int sc) {
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sr, sc});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int r = p[0], c = p[1];
            if (!inBounds(r, c) || revealed[r][c] || flagged[r][c]) continue;
            revealed[r][c] = true;
            revealedCount++;
            if (adj[r][c] == 0) {
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++)
                        if (dr != 0 || dc != 0) stack.push(new int[]{r + dr, c + dc});
            }
        }
    }

    private void chord(int r, int c) {
        if (state != State.PLAYING || !revealed[r][c] || adj[r][c] == 0) return;
        int f = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++)
                if ((dr != 0 || dc != 0) && inBounds(r + dr, c + dc) && flagged[r + dr][c + dc]) f++;
        if (f != adj[r][c]) return;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {
                int rr = r + dr, cc = c + dc;
                if ((dr == 0 && dc == 0) || !inBounds(rr, cc) || flagged[rr][cc] || revealed[rr][cc]) continue;
                if (mine[rr][cc]) {
                    lose(rr, cc);
                    return;
                }
                reveal(rr, cc);
            }
        checkWin();
    }

    private void toggleFlag(int r, int c) {
        if (state != State.PLAYING || revealed[r][c]) return;
        flagged[r][c] = !flagged[r][c];
        flagCount += flagged[r][c] ? 1 : -1;
    }

    private void lose(int r, int c) {
        state = State.LOST;
        explR = r;
        explC = c;
        endTime = System.currentTimeMillis();
    }

    private void checkWin() {
        if (revealedCount != rows * cols - mines) return;
        state = State.WON;
        endTime = System.currentTimeMillis();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) if (mine[r][c]) flagged[r][c] = true;
        flagCount = mines;
        int secs = Math.max(1, (int) ((endTime - startTime) / 1000));
        int best = bestTime(diff);
        if (best == 0 || secs < best) {
            prefs.putInt("ms_best_" + diff, secs);
            newBest = true;
        }
    }

    private int bestTime(int d) {
        return prefs.getInt("ms_best_" + d, 0);
    }

    private int elapsedSeconds() {
        if (first || startTime == 0) return 0;
        long end = state == State.PLAYING ? System.currentTimeMillis() : endTime;
        return (int) Math.min(999, (end - startTime) / 1000);
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
        drawBoard(g2);
        drawFooter(g2);
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
        g2.drawString("Menu", mb.x + 20, mb.y + 21);

        led(g2, mines - flagCount, 110, 14);
        led(g2, elapsedSeconds(), W - 110 - 86, 14);

        // face
        int fx = W / 2, fy = HUD / 2;
        g2.setColor(new Color(255, 214, 80));
        g2.fillOval(fx - 20, fy - 20, 40, 40);
        g2.setColor(new Color(150, 110, 20));
        g2.drawOval(fx - 20, fy - 20, 40, 40);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        if (state == State.LOST) {
            g2.drawLine(fx - 11, fy - 9, fx - 5, fy - 3);
            g2.drawLine(fx - 5, fy - 9, fx - 11, fy - 3);
            g2.drawLine(fx + 5, fy - 9, fx + 11, fy - 3);
            g2.drawLine(fx + 11, fy - 9, fx + 5, fy - 3);
            g2.drawArc(fx - 8, fy + 4, 16, 12, 20, 140);
        } else if (state == State.WON) {
            g2.fillRoundRect(fx - 14, fy - 10, 12, 8, 4, 4);
            g2.fillRoundRect(fx + 2, fy - 10, 12, 8, 4, 4);
            g2.drawLine(fx - 2, fy - 7, fx + 2, fy - 7);
            g2.drawArc(fx - 9, fy - 2, 18, 14, 200, 140);
        } else {
            g2.fillOval(fx - 10, fy - 9, 5, 6);
            g2.fillOval(fx + 5, fy - 9, 5, 6);
            g2.drawArc(fx - 9, fy - 3, 18, 14, 200, 140);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    private void led(Graphics2D g2, int value, int x, int y) {
        g2.setColor(new Color(20, 6, 8));
        g2.fillRoundRect(x, y, 86, 36, 8, 8);
        g2.setColor(new Color(90, 30, 30));
        g2.drawRoundRect(x, y, 86, 36, 8, 8);
        String text = value < 0 ? String.format("-%02d", Math.min(99, -value)) : String.format("%03d", Math.min(999, value));
        g2.setFont(new Font("Monospaced", Font.BOLD, 28));
        g2.setColor(new Color(255, 70, 60));
        g2.drawString(text, x + 12, y + 28);
    }

    private void drawBoard(Graphics2D g2) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = ox + c * cell, y = oy + r * cell;
                boolean showMine = state == State.LOST && mine[r][c] && !flagged[r][c];
                if (revealed[r][c] || showMine) {
                    boolean boom = r == explR && c == explC;
                    g2.setColor(boom ? new Color(170, 40, 40) : new Color(24, 26, 42));
                    g2.fillRect(x, y, cell, cell);
                    g2.setColor(new Color(40, 44, 68));
                    g2.drawRect(x, y, cell, cell);
                    if (mine[r][c]) drawMine(g2, x, y);
                    else if (adj[r][c] > 0) {
                        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, cell * 5 / 9)));
                        g2.setColor(NUM_COLORS[adj[r][c]]);
                        String s = String.valueOf(adj[r][c]);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(s, x + (cell - fm.stringWidth(s)) / 2, y + (cell + fm.getAscent()) / 2 - 2);
                    }
                } else {
                    boolean hover = state == State.PLAYING && r == hoverR && c == hoverC;
                    Color base = hover ? new Color(88, 100, 150) : new Color(70, 80, 120);
                    g2.setColor(base);
                    g2.fillRect(x, y, cell, cell);
                    g2.setColor(new Color(120, 132, 180));
                    g2.drawLine(x, y, x + cell - 1, y);
                    g2.drawLine(x, y, x, y + cell - 1);
                    g2.setColor(new Color(38, 44, 74));
                    g2.drawLine(x, y + cell - 1, x + cell - 1, y + cell - 1);
                    g2.drawLine(x + cell - 1, y, x + cell - 1, y + cell - 1);
                    if (flagged[r][c]) {
                        drawFlag(g2, x, y);
                        if (state == State.LOST && !mine[r][c]) {
                            g2.setColor(new Color(255, 80, 80));
                            g2.setStroke(new BasicStroke(2f));
                            g2.drawLine(x + 4, y + 4, x + cell - 5, y + cell - 5);
                            g2.drawLine(x + cell - 5, y + 4, x + 4, y + cell - 5);
                            g2.setStroke(new BasicStroke(1f));
                        }
                    }
                }
            }
        }
    }

    private void drawFlag(Graphics2D g2, int x, int y) {
        int u = cell;
        g2.setColor(new Color(235, 235, 240));
        g2.fillRect(x + u * 45 / 100, y + u / 5, Math.max(2, u / 12), u * 11 / 20);
        g2.fillRect(x + u / 3, y + u * 3 / 4 - 1, u / 3, Math.max(2, u / 10));
        g2.setColor(new Color(240, 70, 70));
        g2.fillPolygon(new int[]{x + u * 45 / 100 + 1, x + u * 45 / 100 + 1, x + u * 3 / 4},
                new int[]{y + u / 5, y + u * 11 / 20, y + u * 7 / 20}, 3);
    }

    private void drawMine(Graphics2D g2, int x, int y) {
        int cx = x + cell / 2, cy = y + cell / 2, r = Math.max(4, cell / 4);
        g2.setColor(new Color(235, 235, 240));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(cx - r - 3, cy, cx + r + 3, cy);
        g2.drawLine(cx, cy - r - 3, cx, cy + r + 3);
        g2.drawLine(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1);
        g2.drawLine(cx - r - 1, cy + r + 1, cx + r + 1, cy - r - 1);
        g2.setStroke(new BasicStroke(1f));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(new Color(20, 20, 30));
        g2.fillOval(cx - r + 1, cy - r + 1, r * 2 - 2, r * 2 - 2);
        g2.setColor(Color.WHITE);
        g2.fillOval(cx - r / 2, cy - r / 2, Math.max(2, r / 2), Math.max(2, r / 2));
    }

    private void drawFooter(Graphics2D g2) {
        String msg;
        Color col;
        if (state == State.WON) {
            msg = "Cleared in " + elapsedSeconds() + "s" + (newBest ? " - new best time!" : "") + "   (R: play again)";
            col = new Color(120, 230, 120);
        } else if (state == State.LOST) {
            msg = "Boom! You hit a mine   (R: try again)";
            col = new Color(255, 110, 110);
        } else {
            msg = "Left-click: reveal   Right-click: flag   R: restart   Esc: menu";
            col = new Color(130, 135, 165);
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int x = (W - g2.getFontMetrics().stringWidth(msg)) / 2;
        g2.setColor(col);
        g2.drawString(msg, x, H - 10);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 64));
        int tw = g2.getFontMetrics().stringWidth("MINESWEEPER");
        int tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, new Color(90, 165, 255), tx + tw, 0, new Color(255, 105, 105)));
        g2.drawString("MINESWEEPER", tx, 130);
        centered(g2, "Choose a board size", new Font("SansSerif", Font.PLAIN, 17), new Color(170, 175, 205), 172);

        for (int i = 0; i < 3; i++) {
            boolean sel = i == diff;
            Rectangle r = new Rectangle(W / 2 - 230, 210 + i * 80, 460, 62);
            g2.setColor(sel ? new Color(44, 50, 84) : new Color(32, 36, 60));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setColor(sel ? new Color(90, 165, 255) : new Color(60, 66, 100));
            g2.setStroke(new BasicStroke(sel ? 2.5f : 1.5f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setStroke(new BasicStroke(1f));

            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2.setColor(Color.WHITE);
            g2.drawString(NAMES[i], r.x + 22, r.y + 28);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(new Color(170, 175, 205));
            g2.drawString(LEVELS[i][0] + " x " + LEVELS[i][1] + "   -   " + LEVELS[i][2] + " mines", r.x + 22, r.y + 49);

            int best = bestTime(i);
            String bt = best == 0 ? "No record" : "Best: " + best + "s";
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(best == 0 ? new Color(130, 135, 165) : new Color(255, 213, 79));
            g2.drawString(bt, r.x + r.width - 22 - g2.getFontMetrics().stringWidth(bt), r.y + 36);
        }

        Font small = new Font("SansSerif", Font.PLAIN, 13);
        Color grey = new Color(130, 135, 165);
        centered(g2, "Up / Down + Enter, press 1-3, or click   -   first click is always safe", small, grey, 505);
        centered(g2, "Tip: click a number whose flags are all placed to reveal the rest around it", small, grey, 527);
    }

    private void centered(Graphics2D g2, String text, Font f, Color c, int y) {
        g2.setFont(f);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Minesweeper");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Minesweeper game = new Minesweeper();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}