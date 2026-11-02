import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Tetris with 7-bag randomizer, ghost piece, hold, next queue, levels and line-clear flash.
 * Left/Right move, Down soft drop, Up/X rotate, Z rotate back, Space hard drop, C hold, P pause.
 */
public class Tetris extends JPanel implements Main.Game {

    static final int COLS = 10, ROWS = 20, CELL = 28, BX = 16, BY = 16;
    static final int W = 500, H = 592;
    static final int SIDE_X = BX + COLS * CELL + 22;

    static final String[][] SHAPES = {
            {"....", "XXXX", "....", "...."},   // I
            {"XX", "XX"},                       // O
            {".X.", "XXX", "..."},              // T
            {".XX", "XX.", "..."},              // S
            {"XX.", ".XX", "..."},              // Z
            {"X..", "XXX", "..."},              // J
            {"..X", "XXX", "..."}               // L
    };
    static final Color[] COLORS = {
            new Color(0, 220, 230), new Color(250, 220, 40), new Color(175, 80, 215),
            new Color(90, 215, 90), new Color(240, 70, 70), new Color(70, 110, 240),
            new Color(250, 150, 40)
    };
    static final int[] LINE_POINTS = {0, 100, 300, 500, 800};

    enum State { MENU, PLAYING, PAUSED, CLEARING, OVER }

    static class Piece {
        int type, x, y;
        int[][] m;
    }

    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Tetris.class);

    private final int[][] board = new int[ROWS][COLS];
    private final ArrayList<Integer> queue = new ArrayList<>();
    private final ArrayList<Integer> clearRows = new ArrayList<>();

    private Piece cur;
    private State state = State.MENU;
    private int holdType = -1;
    private boolean canHold = true;
    private int score, lines, level = 1, clearTimer, dropAccum;
    private boolean newRecord;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Tetris() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
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

    // ---------- input ----------
    private void handleKey(int k) {
        if (state == State.MENU || state == State.OVER) {
            if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) newGame();
            else if (k == KeyEvent.VK_Q || k == KeyEvent.VK_ESCAPE) state = State.MENU;
            return;
        }
        if (state == State.PAUSED) {
            if (k == KeyEvent.VK_P || k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_SPACE) state = State.PLAYING;
            else if (k == KeyEvent.VK_Q) state = State.MENU;
            return;
        }
        if (state != State.PLAYING) return;

        switch (k) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> tryMove(-1, 0);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> tryMove(1, 0);
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                if (tryMove(0, 1)) {
                    score += 1;
                    dropAccum = 0;
                }
            }
            case KeyEvent.VK_UP, KeyEvent.VK_X, KeyEvent.VK_W -> rotate(true);
            case KeyEvent.VK_Z -> rotate(false);
            case KeyEvent.VK_SPACE -> hardDrop();
            case KeyEvent.VK_C, KeyEvent.VK_SHIFT -> hold();
            case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> state = State.PAUSED;
        }
    }

    // ---------- pieces ----------
    private static int[][] shape(int type) {
        String[] rows = SHAPES[type];
        int n = rows.length;
        int[][] m = new int[n][n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                m[r][c] = rows[r].charAt(c) == 'X' ? 1 : 0;
        return m;
    }

    private static int[][] rot(int[][] m, boolean cw) {
        int n = m.length;
        int[][] o = new int[n][n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (cw) o[c][n - 1 - r] = m[r][c];
                else o[n - 1 - c][r] = m[r][c];
            }
        return o;
    }

    private boolean valid(int[][] m, int x, int y) {
        int n = m.length;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (m[r][c] == 0) continue;
                int bx = x + c, by = y + r;
                if (bx < 0 || bx >= COLS || by >= ROWS) return false;
                if (by >= 0 && board[by][bx] != 0) return false;
            }
        return true;
    }

    private void refillQueue() {
        while (queue.size() < 5) {
            ArrayList<Integer> bag = new ArrayList<>();
            for (int i = 0; i < 7; i++) bag.add(i);
            Collections.shuffle(bag, rnd);
            queue.addAll(bag);
        }
    }

    private void nextPiece() {
        refillQueue();
        spawn(queue.remove(0));
        refillQueue();
    }

    private void spawn(int type) {
        Piece p = new Piece();
        p.type = type;
        p.m = shape(type);
        p.x = (COLS - p.m.length) / 2;
        p.y = type == 0 ? -1 : 0;
        cur = p;
        dropAccum = 0;
        if (!valid(p.m, p.x, p.y)) gameOver();
    }

    private boolean tryMove(int dx, int dy) {
        if (cur != null && valid(cur.m, cur.x + dx, cur.y + dy)) {
            cur.x += dx;
            cur.y += dy;
            return true;
        }
        return false;
    }

    private void rotate(boolean cw) {
        int[][] nm = rot(cur.m, cw);
        int[][] kicks = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, -1}};
        for (int[] k : kicks) {
            if (valid(nm, cur.x + k[0], cur.y + k[1])) {
                cur.m = nm;
                cur.x += k[0];
                cur.y += k[1];
                return;
            }
        }
    }

    private void hardDrop() {
        while (valid(cur.m, cur.x, cur.y + 1)) {
            cur.y++;
            score += 2;
        }
        lock();
    }

    private void hold() {
        if (!canHold) return;
        int t = cur.type;
        if (holdType == -1) {
            holdType = t;
            nextPiece();
        } else {
            int h = holdType;
            holdType = t;
            spawn(h);
        }
        canHold = false;
    }

    private int ghostY() {
        int gy = cur.y;
        while (valid(cur.m, cur.x, gy + 1)) gy++;
        return gy;
    }

    // ---------- game flow ----------
    private int best() {
        return prefs.getInt("tetris_high", 0);
    }

    private void newGame() {
        for (int[] row : board) java.util.Arrays.fill(row, 0);
        queue.clear();
        clearRows.clear();
        score = 0;
        lines = 0;
        level = 1;
        holdType = -1;
        canHold = true;
        newRecord = false;
        state = State.PLAYING;
        nextPiece();
    }

    private void gameOver() {
        state = State.OVER;
        if (score > best()) {
            prefs.putInt("tetris_high", score);
            newRecord = true;
        }
    }

    private int interval() {
        return Math.max(70, 800 - (level - 1) * 65);
    }

    private void update() {
        phase += 0.05;
        if (state == State.CLEARING) {
            if (--clearTimer <= 0) finishClear();
            return;
        }
        if (state != State.PLAYING) return;

        dropAccum += 16;
        if (dropAccum >= interval()) {
            dropAccum = 0;
            if (!tryMove(0, 1)) lock();
        }
    }

    private void lock() {
        int n = cur.m.length;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (cur.m[r][c] == 0) continue;
                int by = cur.y + r, bx = cur.x + c;
                if (by < 0) {
                    gameOver();
                    return;
                }
                board[by][bx] = cur.type + 1;
            }
        cur = null;
        canHold = true;

        clearRows.clear();
        for (int r = 0; r < ROWS; r++) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) if (board[r][c] == 0) { full = false; break; }
            if (full) clearRows.add(r);
        }
        if (clearRows.isEmpty()) {
            nextPiece();
        } else {
            state = State.CLEARING;
            clearTimer = 12;
        }
    }

    private void finishClear() {
        int cleared = clearRows.size();
        for (int row : clearRows) {
            for (int r = row; r > 0; r--) board[r] = board[r - 1].clone();
            board[0] = new int[COLS];
        }
        lines += cleared;
        score += LINE_POINTS[Math.min(cleared, 4)] * level;
        level = 1 + lines / 10;
        clearRows.clear();
        state = State.PLAYING;
        nextPiece();
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

        // board background
        g2.setColor(new Color(10, 11, 20));
        g2.fillRoundRect(BX - 4, BY - 4, COLS * CELL + 8, ROWS * CELL + 8, 10, 10);
        g2.setColor(new Color(28, 30, 48));
        for (int c = 0; c <= COLS; c++) g2.drawLine(BX + c * CELL, BY, BX + c * CELL, BY + ROWS * CELL);
        for (int r = 0; r <= ROWS; r++) g2.drawLine(BX, BY + r * CELL, BX + COLS * CELL, BY + r * CELL);

        // locked blocks
        boolean flash = state == State.CLEARING && (clearTimer / 2) % 2 == 0;
        for (int r = 0; r < ROWS; r++) {
            boolean clearing = clearRows.contains(r);
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) continue;
                Color col = (clearing && flash) ? Color.WHITE : COLORS[board[r][c] - 1];
                drawBlock(g2, BX + c * CELL, BY + r * CELL, CELL, col, 255);
            }
        }

        // ghost + current piece
        if (cur != null && state != State.MENU) {
            int gy = ghostY();
            int n = cur.m.length;
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++) {
                    if (cur.m[r][c] == 0) continue;
                    int px = BX + (cur.x + c) * CELL;
                    if (gy + r >= 0) {
                        g2.setColor(new Color(COLORS[cur.type].getRed(), COLORS[cur.type].getGreen(),
                                COLORS[cur.type].getBlue(), 110));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRoundRect(px + 2, BY + (gy + r) * CELL + 2, CELL - 5, CELL - 5, 6, 6);
                        g2.setStroke(new BasicStroke(1f));
                    }
                    if (cur.y + r >= 0) drawBlock(g2, px, BY + (cur.y + r) * CELL, CELL, COLORS[cur.type], 255);
                }
        }

        drawSidebar(g2);

        switch (state) {
            case MENU -> menuOverlay(g2);
            case PAUSED -> overlay(g2, "PAUSED", "SPACE / P to resume   -   Q for menu", null);
            case OVER -> overlay(g2, "GAME OVER",
                    "Score " + score + (newRecord ? "   NEW RECORD!" : "   Best " + best()),
                    "SPACE to play again");
            default -> { }
        }
    }

    private void drawBlock(Graphics2D g2, int px, int py, int size, Color c, int alpha) {
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        g2.fillRoundRect(px + 1, py + 1, size - 2, size - 2, 6, 6);
        g2.setColor(new Color(255, 255, 255, Math.min(alpha, 90)));
        g2.fillRoundRect(px + 3, py + 3, size - 6, Math.max(2, (size - 6) / 3), 4, 4);
        g2.setColor(new Color(0, 0, 0, Math.min(alpha, 70)));
        g2.drawRoundRect(px + 1, py + 1, size - 3, size - 3, 6, 6);
    }

    private void drawSidebar(Graphics2D g2) {
        int x = SIDE_X;
        label(g2, "SCORE", x, 40);
        value(g2, String.valueOf(score), x, 66, Color.WHITE);
        label(g2, "LINES", x, 100);
        value(g2, String.valueOf(lines), x, 126, Color.WHITE);
        label(g2, "LEVEL", x, 160);
        value(g2, String.valueOf(level), x, 186, new Color(120, 230, 120));
        label(g2, "BEST", x, 220);
        value(g2, String.valueOf(Math.max(best(), score)), x, 246, new Color(255, 213, 79));

        label(g2, "NEXT", x, 288);
        for (int i = 0; i < 3 && i < queue.size(); i++) drawMini(g2, queue.get(i), x, 296 + i * 52, 18);

        label(g2, "HOLD", x, 466);
        if (holdType >= 0) drawMini(g2, holdType, x, 474, 18);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(130, 135, 165));
        g2.drawString("Z/X rotate    C hold", x, H - 34);
        g2.drawString("Space: hard drop    P: pause", x, H - 18);
    }

    private void drawMini(Graphics2D g2, int type, int x, int y, int size) {
        int[][] m = shape(type);
        int n = m.length, minR = n, maxR = -1, minC = n, maxC = -1;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (m[r][c] != 0) {
                    minR = Math.min(minR, r);
                    maxR = Math.max(maxR, r);
                    minC = Math.min(minC, c);
                    maxC = Math.max(maxC, c);
                }
        int bw = (maxC - minC + 1) * size, bh = (maxR - minR + 1) * size;
        int ox = x + (4 * size - bw) / 2, oy = y + (2 * size - bh) / 2;
        for (int r = minR; r <= maxR; r++)
            for (int c = minC; c <= maxC; c++)
                if (m[r][c] != 0) drawBlock(g2, ox + (c - minC) * size, oy + (r - minR) * size, size, COLORS[type], 255);
    }

    private void label(Graphics2D g2, String text, int x, int y) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(130, 135, 165));
        g2.drawString(text, x, y);
    }

    private void value(Graphics2D g2, String text, int x, int y, Color c) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.setColor(c);
        g2.drawString(text, x, y);
    }

    private void menuOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRect(0, 0, W, H);
        g2.setFont(new Font("SansSerif", Font.BOLD, 72));
        int tw = g2.getFontMetrics().stringWidth("TETRIS");
        int tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, COLORS[0], tx + tw, 0, COLORS[2]));
        g2.drawString("TETRIS", tx, 170);

        int a = (int) (170 + 85 * Math.sin(phase * 3));
        centered(g2, "Press SPACE to start", new Font("SansSerif", Font.BOLD, 22),
                new Color(255, 255, 255, Math.max(0, Math.min(255, a))), 240);
        centered(g2, "High score: " + best(), new Font("SansSerif", Font.PLAIN, 16),
                new Color(255, 213, 79), 275);

        Font small = new Font("SansSerif", Font.PLAIN, 15);
        Color grey = new Color(170, 175, 205);
        String[] help = {"Left / Right      move", "Down                 soft drop", "Up / X               rotate",
                "Z                      rotate back", "Space                hard drop", "C                      hold piece",
                "P                      pause"};
        for (int i = 0; i < help.length; i++) centered(g2, help[i], small, grey, 330 + i * 26);
    }

    private void overlay(Graphics2D g2, String title, String l1, String l2) {
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRect(0, 0, W, H);
        centered(g2, title, new Font("SansSerif", Font.BOLD, 50), Color.WHITE, H / 2 - 20);
        centered(g2, l1, new Font("SansSerif", Font.BOLD, 18), new Color(255, 213, 79), H / 2 + 20);
        if (l2 != null) centered(g2, l2, new Font("SansSerif", Font.PLAIN, 15), new Color(170, 175, 205), H / 2 + 50);
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
            JFrame frame = new JFrame("Tetris");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Tetris game = new Tetris();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}