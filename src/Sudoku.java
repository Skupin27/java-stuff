import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * Sudoku with generated puzzles (always a unique solution), pencil notes, hints, undo
 * and saved best times. Click or arrow keys to select, 1-9 to fill, N: notes, H: hint,
 * U/Z: undo, Backspace: erase, R: new puzzle, Esc: menu.
 */
public class Sudoku extends JPanel implements Main.Game {

    static final int W = 640, H = 700, CELL = 52, BX = (W - 9 * CELL) / 2, BY = 86;
    static final int PAD_Y = BY + 9 * CELL + 14, TOOL_Y = PAD_Y + 54;
    static final String[] NAMES = {"Easy", "Medium", "Hard", "Expert"};
    static final int[] GIVENS = {42, 34, 30, 26};

    enum State { MENU, PLAYING, WON }

    static class Snap {
        final int[][] values = new int[9][9], notes = new int[9][9];
        final int mistakes, hints;

        Snap(int[][] v, int[][] n, int mistakes, int hints) {
            for (int r = 0; r < 9; r++) {
                values[r] = v[r].clone();
                notes[r] = n[r].clone();
            }
            this.mistakes = mistakes;
            this.hints = hints;
        }
    }

    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Sudoku.class);

    private final int[][] solution = new int[9][9];
    private int[][] values = new int[9][9], notes = new int[9][9];
    private final boolean[][] given = new boolean[9][9];
    private final Deque<Snap> undoStack = new ArrayDeque<>();

    private State state = State.MENU;
    private int diff = 1, selR = 4, selC = 4, mistakes, hints;
    private boolean notesMode, newBest;
    private long startTime, endTime;
    private double phase;

    private final Timer loop = new Timer(50, e -> {
        phase += 0.05;
        repaint();
    });

    public Sudoku() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (state == State.MENU) {
                    int i = menuOptionAt(e.getX(), e.getY());
                    if (i >= 0) diff = i;
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handleClick(e.getX(), e.getY());
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

    // ---------- input ----------
    private void handleKey(int k) {
        if (state == State.MENU) {
            if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) diff = (diff + 3) % 4;
            else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) diff = (diff + 1) % 4;
            else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startGame();
            else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_4) {
                diff = k - KeyEvent.VK_1;
                startGame();
            }
            return;
        }
        if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_M) {
            state = State.MENU;
            return;
        }
        if (k == KeyEvent.VK_R) {
            startGame();
            return;
        }
        if (state != State.PLAYING) return;

        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) selC = Math.max(0, selC - 1);
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) selC = Math.min(8, selC + 1);
        else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) selR = Math.max(0, selR - 1);
        else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) selR = Math.min(8, selR + 1);
        else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9) placeNumber(k - KeyEvent.VK_0);
        else if (k >= KeyEvent.VK_NUMPAD1 && k <= KeyEvent.VK_NUMPAD9) placeNumber(k - KeyEvent.VK_NUMPAD0);
        else if (k == KeyEvent.VK_0 || k == KeyEvent.VK_BACK_SPACE || k == KeyEvent.VK_DELETE) erase();
        else if (k == KeyEvent.VK_N) notesMode = !notesMode;
        else if (k == KeyEvent.VK_H) giveHint();
        else if (k == KeyEvent.VK_U || k == KeyEvent.VK_Z) undo();
    }

    private Rectangle menuButton() {
        return new Rectangle(14, 16, 74, 32);
    }

    private Rectangle padButton(int i) {
        int w = 50, gap = 8, total = 9 * w + 8 * gap;
        return new Rectangle((W - total) / 2 + i * (w + gap), PAD_Y, w, 44);
    }

    private Rectangle toolButton(int i) {
        int w = 120, gap = 12, total = 4 * w + 3 * gap;
        return new Rectangle((W - total) / 2 + i * (w + gap), TOOL_Y, w, 36);
    }

    private int menuOptionAt(int x, int y) {
        for (int i = 0; i < 4; i++) {
            if (new Rectangle(W / 2 - 220, 195 + i * 78, 440, 60).contains(x, y)) return i;
        }
        return -1;
    }

    private void handleClick(int x, int y) {
        if (state == State.MENU) {
            int i = menuOptionAt(x, y);
            if (i >= 0) {
                diff = i;
                startGame();
            }
            return;
        }
        if (menuButton().contains(x, y)) {
            state = State.MENU;
            return;
        }
        if (state == State.WON) {
            startGame();
            return;
        }
        int c = Math.floorDiv(x - BX, CELL), r = Math.floorDiv(y - BY, CELL);
        if (x >= BX && y >= BY && c >= 0 && c < 9 && r >= 0 && r < 9) {
            selR = r;
            selC = c;
            return;
        }
        for (int i = 0; i < 9; i++) if (padButton(i).contains(x, y)) placeNumber(i + 1);
        if (toolButton(0).contains(x, y)) notesMode = !notesMode;
        else if (toolButton(1).contains(x, y)) erase();
        else if (toolButton(2).contains(x, y)) giveHint();
        else if (toolButton(3).contains(x, y)) undo();
    }

    // ---------- generation ----------
    private boolean allowed(int[][] g, int r, int c, int d) {
        for (int i = 0; i < 9; i++) {
            if (g[r][i] == d || g[i][c] == d) return false;
        }
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) if (g[br + i][bc + j] == d) return false;
        return true;
    }

    private boolean fillGrid(int[][] g, int pos) {
        if (pos == 81) return true;
        int r = pos / 9, c = pos % 9;
        ArrayList<Integer> digits = new ArrayList<>();
        for (int d = 1; d <= 9; d++) digits.add(d);
        Collections.shuffle(digits, rnd);
        for (int d : digits) {
            if (!allowed(g, r, c, d)) continue;
            g[r][c] = d;
            if (fillGrid(g, pos + 1)) return true;
            g[r][c] = 0;
        }
        return false;
    }

    private int countSolutions(int[][] g, int[] rowM, int[] colM, int[] boxM, int limit) {
        int bestR = -1, bestC = -1, bestMask = 0, bestCnt = 10;
        for (int r = 0; r < 9 && bestCnt > 1; r++)
            for (int c = 0; c < 9; c++) {
                if (g[r][c] != 0) continue;
                int mask = ~(rowM[r] | colM[c] | boxM[r / 3 * 3 + c / 3]) & 0x1FF;
                int cnt = Integer.bitCount(mask);
                if (cnt == 0) return 0;
                if (cnt < bestCnt) {
                    bestCnt = cnt;
                    bestR = r;
                    bestC = c;
                    bestMask = mask;
                    if (cnt == 1) break;
                }
            }
        if (bestR < 0) return 1;

        int total = 0, box = bestR / 3 * 3 + bestC / 3;
        for (int d = 0; d < 9; d++) {
            if ((bestMask >> d & 1) == 0) continue;
            int bit = 1 << d;
            g[bestR][bestC] = d + 1;
            rowM[bestR] |= bit;
            colM[bestC] |= bit;
            boxM[box] |= bit;
            total += countSolutions(g, rowM, colM, boxM, limit);
            g[bestR][bestC] = 0;
            rowM[bestR] &= ~bit;
            colM[bestC] &= ~bit;
            boxM[box] &= ~bit;
            if (total >= limit) return total;
        }
        return total;
    }

    private int uniqueCount(int[][] puzzle) {
        int[][] g = new int[9][];
        for (int r = 0; r < 9; r++) g[r] = puzzle[r].clone();
        int[] rowM = new int[9], colM = new int[9], boxM = new int[9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                if (g[r][c] != 0) {
                    int bit = 1 << (g[r][c] - 1);
                    rowM[r] |= bit;
                    colM[c] |= bit;
                    boxM[r / 3 * 3 + c / 3] |= bit;
                }
        return countSolutions(g, rowM, colM, boxM, 2);
    }

    private void generate() {
        int[][] full = new int[9][9];
        fillGrid(full, 0);
        for (int r = 0; r < 9; r++) solution[r] = full[r].clone();

        int[][] puzzle = new int[9][];
        for (int r = 0; r < 9; r++) puzzle[r] = full[r].clone();
        ArrayList<Integer> cells = new ArrayList<>();
        for (int i = 0; i < 81; i++) cells.add(i);
        Collections.shuffle(cells, rnd);

        int givens = 81;
        for (int idx : cells) {
            if (givens <= GIVENS[diff]) break;
            int r = idx / 9, c = idx % 9, keep = puzzle[r][c];
            puzzle[r][c] = 0;
            if (uniqueCount(puzzle) != 1) puzzle[r][c] = keep;
            else givens--;
        }

        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                values[r][c] = puzzle[r][c];
                given[r][c] = puzzle[r][c] != 0;
                notes[r][c] = 0;
            }
    }

    private void startGame() {
        generate();
        undoStack.clear();
        mistakes = 0;
        hints = 0;
        notesMode = false;
        newBest = false;
        selR = 4;
        selC = 4;
        startTime = System.currentTimeMillis();
        endTime = 0;
        state = State.PLAYING;
    }

    // ---------- gameplay ----------
    private void pushUndo() {
        undoStack.push(new Snap(values, notes, mistakes, hints));
        if (undoStack.size() > 300) undoStack.removeLast();
    }

    private void placeNumber(int n) {
        if (given[selR][selC]) return;
        pushUndo();
        if (notesMode) {
            if (values[selR][selC] == 0) notes[selR][selC] ^= 1 << (n - 1);
            return;
        }
        if (values[selR][selC] == n) {
            values[selR][selC] = 0;
            return;
        }
        values[selR][selC] = n;
        notes[selR][selC] = 0;
        if (n != solution[selR][selC]) mistakes++;
        clearPeerNotes(selR, selC, n);
        checkWin();
    }

    private void clearPeerNotes(int r, int c, int n) {
        int bit = ~(1 << (n - 1));
        for (int i = 0; i < 9; i++) {
            notes[r][i] &= bit;
            notes[i][c] &= bit;
        }
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) notes[br + i][bc + j] &= bit;
    }

    private void erase() {
        if (given[selR][selC] || (values[selR][selC] == 0 && notes[selR][selC] == 0)) return;
        pushUndo();
        values[selR][selC] = 0;
        notes[selR][selC] = 0;
    }

    private void giveHint() {
        if (given[selR][selC] || values[selR][selC] == solution[selR][selC]) return;
        pushUndo();
        values[selR][selC] = solution[selR][selC];
        notes[selR][selC] = 0;
        hints++;
        clearPeerNotes(selR, selC, values[selR][selC]);
        checkWin();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Snap s = undoStack.pop();
        for (int r = 0; r < 9; r++) {
            values[r] = s.values[r].clone();
            notes[r] = s.notes[r].clone();
        }
        mistakes = s.mistakes;
        hints = s.hints;
    }

    private boolean conflict(int r, int c) {
        int v = values[r][c];
        if (v == 0) return false;
        for (int i = 0; i < 9; i++) {
            if (i != c && values[r][i] == v) return true;
            if (i != r && values[i][c] == v) return true;
        }
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                int rr = br + i, cc = bc + j;
                if ((rr != r || cc != c) && values[rr][cc] == v) return true;
            }
        return false;
    }

    private void checkWin() {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) if (values[r][c] == 0 || conflict(r, c)) return;
        state = State.WON;
        endTime = System.currentTimeMillis();
        int secs = (int) ((endTime - startTime) / 1000);
        int best = bestTime(diff);
        if (hints == 0 && (best == 0 || secs < best)) {
            prefs.putInt("sudoku_best_" + diff, Math.max(1, secs));
            newBest = true;
        }
    }

    private int bestTime(int d) {
        return prefs.getInt("sudoku_best_" + d, 0);
    }

    private int elapsed() {
        if (startTime == 0) return 0;
        long end = state == State.WON ? endTime : System.currentTimeMillis();
        return (int) ((end - startTime) / 1000);
    }

    private int digitCount(int d) {
        int n = 0;
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) if (values[r][c] == d) n++;
        return n;
    }

    private static String fmt(int secs) {
        return String.format("%d:%02d", secs / 60, secs % 60);
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
        drawPad(g2);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(130, 135, 165));
        String hint = "1-9 fill   N notes   H hint   U undo   Backspace erase   R new puzzle   Esc menu";
        g2.drawString(hint, (W - g2.getFontMetrics().stringWidth(hint)) / 2, H - 14);

        if (state == State.WON) drawWin(g2);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(10, 10, 18, 230));
        g2.fillRect(0, 0, W, 72);
        g2.setColor(new Color(60, 64, 90));
        g2.drawLine(0, 72, W, 72);

        Rectangle mb = menuButton();
        g2.setColor(new Color(40, 44, 72));
        g2.fillRoundRect(mb.x, mb.y, mb.width, mb.height, 10, 10);
        g2.setColor(new Color(90, 96, 140));
        g2.drawRoundRect(mb.x, mb.y, mb.width, mb.height, 10, 10);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("Menu", mb.x + 20, mb.y + 21);

        g2.setFont(new Font("SansSerif", Font.BOLD, 28));
        String t = fmt(elapsed());
        g2.setColor(Color.WHITE);
        g2.drawString(t, (W - g2.getFontMetrics().stringWidth(t)) / 2, 38);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(255, 213, 79));
        String sub = NAMES[diff] + (bestTime(diff) > 0 ? "  -  Best " + fmt(bestTime(diff)) : "");
        g2.drawString(sub, (W - g2.getFontMetrics().stringWidth(sub)) / 2, 60);

        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(mistakes > 0 ? new Color(255, 110, 110) : new Color(170, 175, 205));
        g2.drawString("Mistakes: " + mistakes, W - 150, 32);
        g2.setColor(hints > 0 ? new Color(255, 213, 79) : new Color(170, 175, 205));
        g2.drawString("Hints: " + hints, W - 150, 54);
    }

    private void drawBoard(Graphics2D g2) {
        int selVal = values[selR][selC];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int x = BX + c * CELL, y = BY + r * CELL;
                boolean peer = r == selR || c == selC || (r / 3 == selR / 3 && c / 3 == selC / 3);
                Color bg = new Color(26, 29, 50);
                if (peer) bg = new Color(34, 38, 66);
                if (selVal != 0 && values[r][c] == selVal) bg = new Color(48, 68, 112);
                if (r == selR && c == selC) bg = new Color(74, 94, 160);
                g2.setColor(bg);
                g2.fillRect(x, y, CELL, CELL);

                boolean bad = conflict(r, c);
                if (bad) {
                    g2.setColor(new Color(150, 40, 50, 120));
                    g2.fillRect(x, y, CELL, CELL);
                }

                int v = values[r][c];
                if (v != 0) {
                    g2.setFont(new Font("SansSerif", given[r][c] ? Font.BOLD : Font.PLAIN, 28));
                    g2.setColor(bad ? new Color(255, 110, 110) : given[r][c] ? Color.WHITE : new Color(110, 180, 255));
                    String s = String.valueOf(v);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(s, x + (CELL - fm.stringWidth(s)) / 2, y + (CELL + fm.getAscent()) / 2 - 4);
                } else if (notes[r][c] != 0) {
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g2.setColor(new Color(160, 170, 210));
                    FontMetrics fm = g2.getFontMetrics();
                    for (int d = 0; d < 9; d++) {
                        if ((notes[r][c] >> d & 1) == 0) continue;
                        String s = String.valueOf(d + 1);
                        int cx = x + (d % 3) * CELL / 3 + CELL / 6, cy = y + (d / 3) * CELL / 3 + CELL / 6;
                        g2.drawString(s, cx - fm.stringWidth(s) / 2, cy + fm.getAscent() / 2 - 1);
                    }
                }
            }
        }

        // grid lines
        g2.setColor(new Color(52, 56, 88));
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 9; i++) {
            g2.drawLine(BX, BY + i * CELL, BX + 9 * CELL, BY + i * CELL);
            g2.drawLine(BX + i * CELL, BY, BX + i * CELL, BY + 9 * CELL);
        }
        g2.setColor(new Color(170, 176, 214));
        g2.setStroke(new BasicStroke(3f));
        for (int i = 0; i <= 9; i += 3) {
            g2.drawLine(BX, BY + i * CELL, BX + 9 * CELL, BY + i * CELL);
            g2.drawLine(BX + i * CELL, BY, BX + i * CELL, BY + 9 * CELL);
        }
        // selection outline
        g2.setColor(new Color(255, 213, 79));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRect(BX + selC * CELL + 1, BY + selR * CELL + 1, CELL - 2, CELL - 2);
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawPad(Graphics2D g2) {
        for (int i = 0; i < 9; i++) {
            Rectangle r = padButton(i);
            int left = 9 - digitCount(i + 1);
            boolean done = left <= 0;
            g2.setColor(done ? new Color(28, 31, 50) : notesMode ? new Color(60, 52, 100) : new Color(40, 46, 84));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            g2.setColor(done ? new Color(50, 54, 80) : new Color(90, 100, 160));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2.setColor(done ? new Color(80, 84, 110) : Color.WHITE);
            String s = String.valueOf(i + 1);
            g2.drawString(s, r.x + (r.width - g2.getFontMetrics().stringWidth(s)) / 2, r.y + 28);
            if (!done) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(150, 160, 205));
                String l = String.valueOf(left);
                g2.drawString(l, r.x + r.width - 4 - g2.getFontMetrics().stringWidth(l), r.y + 12);
            }
        }
        String[] labels = {notesMode ? "Notes: ON" : "Notes: off", "Erase", "Hint", "Undo"};
        for (int i = 0; i < 4; i++) {
            Rectangle r = toolButton(i);
            boolean active = i == 0 && notesMode;
            boolean dim = i == 3 && undoStack.isEmpty();
            g2.setColor(active ? new Color(255, 213, 79) : dim ? new Color(28, 31, 50) : new Color(40, 44, 72));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            g2.setColor(active ? new Color(255, 213, 79) : new Color(70, 76, 120));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(active ? new Color(40, 34, 16) : dim ? new Color(90, 94, 120) : Color.WHITE);
            g2.drawString(labels[i], r.x + (r.width - g2.getFontMetrics().stringWidth(labels[i])) / 2, r.y + 23);
        }
    }

    private void drawWin(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRect(BX, BY, 9 * CELL, 9 * CELL);
        centered(g2, "SOLVED!", new Font("SansSerif", Font.BOLD, 58), new Color(120, 230, 120), BY + 9 * CELL / 2 - 20);
        String line = fmt(elapsed()) + "   -   " + mistakes + " mistakes   -   " + hints + " hints";
        centered(g2, line, new Font("SansSerif", Font.BOLD, 17), new Color(255, 213, 79), BY + 9 * CELL / 2 + 20);
        if (newBest) centered(g2, "NEW BEST TIME!", new Font("SansSerif", Font.BOLD, 18), Color.WHITE, BY + 9 * CELL / 2 + 52);
        else if (hints > 0)
            centered(g2, "(best times only count without hints)", new Font("SansSerif", Font.PLAIN, 13),
                    new Color(170, 175, 205), BY + 9 * CELL / 2 + 52);
        centered(g2, "Click or press R for a new puzzle", new Font("SansSerif", Font.PLAIN, 14),
                new Color(170, 175, 205), BY + 9 * CELL / 2 + 82);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 70));
        int tw = g2.getFontMetrics().stringWidth("SUDOKU"), tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, new Color(130, 170, 255), tx + tw, 0, new Color(190, 140, 255)));
        g2.drawString("SUDOKU", tx, 130);
        centered(g2, "Fill every row, column and 3 x 3 box with 1-9",
                new Font("SansSerif", Font.PLAIN, 16), new Color(170, 175, 205), 170);

        for (int i = 0; i < 4; i++) {
            boolean sel = i == diff;
            Rectangle r = new Rectangle(W / 2 - 220, 195 + i * 78, 440, 60);
            g2.setColor(sel ? new Color(44, 50, 84) : new Color(32, 36, 60));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setColor(sel ? new Color(130, 170, 255) : new Color(60, 66, 100));
            g2.setStroke(new BasicStroke(sel ? 2.5f : 1.5f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g2.setStroke(new BasicStroke(1f));

            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2.setColor(Color.WHITE);
            g2.drawString(NAMES[i], r.x + 22, r.y + 27);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.setColor(new Color(170, 175, 205));
            g2.drawString(GIVENS[i] + " starting numbers", r.x + 22, r.y + 47);

            int best = bestTime(i);
            String bt = best == 0 ? "No record" : "Best: " + fmt(best);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(best == 0 ? new Color(130, 135, 165) : new Color(255, 213, 79));
            g2.drawString(bt, r.x + r.width - 22 - g2.getFontMetrics().stringWidth(bt), r.y + 36);
        }
        centered(g2, "Up / Down + Enter, press 1-4, or click   -   every puzzle has exactly one solution",
                new Font("SansSerif", Font.PLAIN, 13), new Color(130, 135, 165), 540);
        centered(g2, "Pencil notes, hints and unlimited undo included", new Font("SansSerif", Font.PLAIN, 13),
                new Color(130, 135, 165), 562);
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
            JFrame frame = new JFrame("Sudoku");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Sudoku game = new Sudoku();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
