import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Gomoku (five in a row) on a 15x15 board, against an AI (3 levels) or a friend.
 * Click an intersection, or use the arrow keys + Space. U: undo, R: next round, Q/Esc: menu.
 * Black always moves first; colours swap every round. Five or more in a row wins.
 */
public class Gomoku extends JPanel implements Main.Game {

    static final int W = 640, H = 712, N = 15, CELL = 38;
    static final int BX = (W - (N - 1) * CELL) / 2, BY = 122;
    static final String[] OPTIONS = {"1 Player  -  Easy", "1 Player  -  Normal", "1 Player  -  Hard", "2 Players"};
    static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    enum State { MENU, PLAYING, OVER }

    private final Random rnd = new Random();
    private final int[][] b = new int[N][N];                 // 0 empty, 1 black, 2 white
    private final ArrayList<int[]> history = new ArrayList<>(); // {r, c, color}
    private ArrayList<int[]> winCells = new ArrayList<>();

    private State state = State.MENU;
    private int option = 1, level = 1;
    private boolean twoPlayer;
    private int turn = 1, humanColor = 1, winner, aiTimer, round;
    private int cursorR = N / 2, cursorC = N / 2;
    private int winsP1, winsP2, draws;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public Gomoku() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (state == State.MENU) {
                    if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) option = (option + 3) % 4;
                    else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) option = (option + 1) % 4;
                    else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startMatch();
                    else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_4) {
                        option = k - KeyEvent.VK_1;
                        startMatch();
                    }
                    return;
                }
                if (k == KeyEvent.VK_Q || k == KeyEvent.VK_ESCAPE) {
                    state = State.MENU;
                } else if (k == KeyEvent.VK_R) {
                    newRound();
                } else if (state == State.OVER) {
                    if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) newRound();
                    else if (k == KeyEvent.VK_U || k == KeyEvent.VK_Z) undo();
                } else {
                    if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) cursorC = Math.max(0, cursorC - 1);
                    else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) cursorC = Math.min(N - 1, cursorC + 1);
                    else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) cursorR = Math.max(0, cursorR - 1);
                    else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) cursorR = Math.min(N - 1, cursorR + 1);
                    else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) humanPlay(cursorR, cursorC);
                    else if (k == KeyEvent.VK_U || k == KeyEvent.VK_Z) undo();
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (state == State.MENU) {
                    int i = optionAt(e.getY());
                    if (i >= 0) option = i;
                    return;
                }
                int[] p = nearest(e.getX(), e.getY());
                if (p != null) {
                    cursorR = p[0];
                    cursorC = p[1];
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
                    newRound();
                } else {
                    int[] p = nearest(e.getX(), e.getY());
                    if (p != null) humanPlay(p[0], p[1]);
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
            int base = 300 + i * 56;
            if (y >= base - 36 && y <= base + 12) return i;
        }
        return -1;
    }

    private int[] nearest(int x, int y) {
        int c = (int) Math.round((x - BX) / (double) CELL), r = (int) Math.round((y - BY) / (double) CELL);
        if (r < 0 || r >= N || c < 0 || c >= N) return null;
        if (Math.hypot(x - (BX + c * CELL), y - (BY + r * CELL)) > CELL * 0.7) return null;
        return new int[]{r, c};
    }

    // ---------- flow ----------
    private void startMatch() {
        twoPlayer = option == 3;
        if (!twoPlayer) level = option;
        winsP1 = 0;
        winsP2 = 0;
        draws = 0;
        round = 0;
        newRound();
    }

    private void newRound() {
        for (int[] row : b) java.util.Arrays.fill(row, 0);
        history.clear();
        winCells = new ArrayList<>();
        winner = 0;
        round++;
        humanColor = round % 2 == 1 ? 1 : 2; // Player 1 / you play Black in odd rounds
        turn = 1;
        cursorR = N / 2;
        cursorC = N / 2;
        state = State.PLAYING;
        aiTimer = 28;
    }

    private boolean humanTurn() {
        return twoPlayer || turn == humanColor;
    }

    private void humanPlay(int r, int c) {
        if (state != State.PLAYING || !humanTurn() || b[r][c] != 0) return;
        placeStone(r, c);
    }

    private void placeStone(int r, int c) {
        b[r][c] = turn;
        history.add(new int[]{r, c, turn});
        ArrayList<int[]> w = findWin(r, c);
        if (w != null) {
            winCells = w;
            winner = turn;
            state = State.OVER;
            if (winner == humanColor) winsP1++;
            else winsP2++;
        } else if (history.size() == N * N) {
            winner = 0;
            state = State.OVER;
            draws++;
        } else {
            turn = 3 - turn;
            aiTimer = 18;
        }
    }

    private void undo() {
        if (history.isEmpty() || (state == State.PLAYING && !humanTurn())) return;
        // in 1P undo both the CPU reply and our own move
        int toUndo = twoPlayer ? 1 : 2;
        if (!twoPlayer && history.get(history.size() - 1)[2] == humanColor) toUndo = 1;
        for (int i = 0; i < toUndo && !history.isEmpty(); i++) {
            int[] m = history.remove(history.size() - 1);
            b[m[0]][m[1]] = 0;
            turn = m[2];
        }
        if (state == State.OVER) {
            // take back the round result
            if (winner != 0) {
                if (winner == humanColor) winsP1--;
                else winsP2--;
            } else if (draws > 0) draws--;
            winner = 0;
            winCells = new ArrayList<>();
            state = State.PLAYING;
        }
        aiTimer = 18;
    }

    private void update() {
        phase += 0.06;
        if (state == State.PLAYING && !twoPlayer && turn != humanColor && --aiTimer <= 0) {
            int[] m = chooseAi();
            placeStone(m[0], m[1]);
        }
    }

    // ---------- rules ----------
    private boolean in(int r, int c) {
        return r >= 0 && r < N && c >= 0 && c < N;
    }

    private ArrayList<int[]> findWin(int r, int c) {
        int color = b[r][c];
        for (int[] d : DIRS) {
            ArrayList<int[]> run = new ArrayList<>();
            run.add(new int[]{r, c});
            for (int s = -1; s <= 1; s += 2) {
                int rr = r + d[0] * s, cc = c + d[1] * s;
                while (in(rr, cc) && b[rr][cc] == color) {
                    run.add(new int[]{rr, cc});
                    rr += d[0] * s;
                    cc += d[1] * s;
                }
            }
            if (run.size() >= 5) return run;
        }
        return null;
    }

    // ---------- AI ----------
    private int patternScore(int count, int open) {
        if (count >= 5) return 1_000_000;
        if (open == 0) return 0;
        return switch (count) {
            case 4 -> open == 2 ? 100_000 : 12_000;
            case 3 -> open == 2 ? 5_000 : 400;
            case 2 -> open == 2 ? 300 : 30;
            default -> open == 2 ? 12 : 2;
        };
    }

    /** Score of placing `color` on the empty cell (r, c), summed over the four directions. */
    private int attack(int r, int c, int color) {
        int total = 0;
        for (int[] d : DIRS) {
            int count = 1, open = 0;
            for (int s = -1; s <= 1; s += 2) {
                int rr = r + d[0] * s, cc = c + d[1] * s;
                while (in(rr, cc) && b[rr][cc] == color) {
                    count++;
                    rr += d[0] * s;
                    cc += d[1] * s;
                }
                if (in(rr, cc) && b[rr][cc] == 0) open++;
            }
            total += patternScore(count, open);
        }
        return total;
    }

    private ArrayList<int[]> candidates() {
        boolean[][] mark = new boolean[N][N];
        ArrayList<int[]> list = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                if (b[r][c] == 0) continue;
                for (int dr = -2; dr <= 2; dr++)
                    for (int dc = -2; dc <= 2; dc++) {
                        int rr = r + dr, cc = c + dc;
                        if (in(rr, cc) && b[rr][cc] == 0 && !mark[rr][cc]) {
                            mark[rr][cc] = true;
                            list.add(new int[]{rr, cc});
                        }
                    }
            }
        return list;
    }

    private int[] chooseAi() {
        int me = 3 - humanColor, opp = humanColor;
        if (history.isEmpty()) return new int[]{N / 2, N / 2};
        if (history.size() == 1) {
            int[] f = history.get(0);
            int[][] opts = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int tries = 0; tries < 12; tries++) {
                int[] o = opts[rnd.nextInt(4)];
                if (in(f[0] + o[0], f[1] + o[1]) && b[f[0] + o[0]][f[1] + o[1]] == 0)
                    return new int[]{f[0] + o[0], f[1] + o[1]};
            }
        }

        ArrayList<int[]> cands = candidates();
        int[] block = null;
        for (int[] cd : cands) {
            if (attack(cd[0], cd[1], me) >= 1_000_000) return cd;           // win now
            if (attack(cd[0], cd[1], opp) >= 1_000_000) block = cd;        // must block
        }
        if (block != null && !(level == 0 && rnd.nextInt(100) < 30)) return block;

        double defWeight = level == 0 ? 0.6 : level == 1 ? 0.95 : 1.05;
        int n = cands.size();
        double[] val = new double[n];
        for (int i = 0; i < n; i++) {
            int[] cd = cands.get(i);
            val[i] = attack(cd[0], cd[1], me) + attack(cd[0], cd[1], opp) * defWeight;
            if (level == 0) val[i] *= 0.6 + rnd.nextDouble() * 0.8;
        }

        // sort candidate indices by value (descending)
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> Double.compare(val[y], val[x]));

        if (level == 2) {
            int top = Math.min(8, n);
            double best = -Double.MAX_VALUE;
            int[] bestMove = cands.get(order[0]);
            for (int i = 0; i < top; i++) {
                int[] cd = cands.get(order[i]);
                b[cd[0]][cd[1]] = me;
                double oppBest = 0;
                for (int[] c2 : candidates()) {
                    double v = attack(c2[0], c2[1], opp) + attack(c2[0], c2[1], me) * 0.9;
                    if (v > oppBest) oppBest = v;
                }
                b[cd[0]][cd[1]] = 0;
                double score = val[order[i]] - 0.55 * oppBest;
                if (score > best) {
                    best = score;
                    bestMove = cd;
                }
            }
            return bestMove;
        }
        if (level == 0 && n > 1 && rnd.nextInt(100) < 30) return cands.get(order[1]);
        return cands.get(order[0]);
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

        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(130, 135, 165));
        String hint = "Click or arrows + Space to place      U: undo   R: next round   Q: menu";
        g2.drawString(hint, (W - g2.getFontMetrics().stringWidth(hint)) / 2, H - 12);
    }

    private void drawBoard(Graphics2D g2) {
        int span = (N - 1) * CELL;
        // shadow + wood
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillRoundRect(BX - 28 + 5, BY - 28 + 7, span + 56, span + 56, 18, 18);
        g2.setPaint(new GradientPaint(0, BY - 28, new Color(228, 190, 132), 0, BY + span + 28, new Color(202, 158, 98)));
        g2.fillRoundRect(BX - 28, BY - 28, span + 56, span + 56, 18, 18);
        g2.setColor(new Color(150, 106, 56));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRoundRect(BX - 28, BY - 28, span + 56, span + 56, 18, 18);
        g2.setColor(new Color(160, 120, 70, 40));
        g2.setStroke(new BasicStroke(1f));
        for (int y = BY - 24; y < BY + span + 26; y += 7) g2.drawLine(BX - 26, y, BX + span + 26, y + (y % 3) - 1);

        // grid
        g2.setColor(new Color(92, 64, 32));
        g2.setStroke(new BasicStroke(1.3f));
        for (int i = 0; i < N; i++) {
            g2.drawLine(BX, BY + i * CELL, BX + span, BY + i * CELL);
            g2.drawLine(BX + i * CELL, BY, BX + i * CELL, BY + span);
        }
        g2.setStroke(new BasicStroke(2.4f));
        g2.drawRect(BX, BY, span, span);
        g2.setStroke(new BasicStroke(1f));
        for (int[] s : new int[][]{{3, 3}, {3, 11}, {11, 3}, {11, 11}, {7, 7}}) {
            g2.fillOval(BX + s[1] * CELL - 4, BY + s[0] * CELL - 4, 8, 8);
        }

        // coordinates
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(120, 84, 44));
        for (int i = 0; i < N; i++) {
            String col = String.valueOf((char) ('A' + i)), row = String.valueOf(i + 1);
            g2.drawString(col, BX + i * CELL - g2.getFontMetrics().stringWidth(col) / 2, BY - 11);
            g2.drawString(row, BX - 22 - g2.getFontMetrics().stringWidth(row) / 2 + 4, BY + i * CELL + 4);
        }

        // stones
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (b[r][c] != 0) drawStone(g2, BX + c * CELL, BY + r * CELL, b[r][c], 1f);

        // last move marker
        if (!history.isEmpty()) {
            int[] m = history.get(history.size() - 1);
            g2.setColor(m[2] == 1 ? new Color(255, 90, 80) : new Color(220, 50, 50));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(BX + m[1] * CELL - 5, BY + m[0] * CELL - 5, 10, 10);
            g2.setStroke(new BasicStroke(1f));
        }

        // hover ghost
        if (state == State.PLAYING && humanTurn() && b[cursorR][cursorC] == 0) {
            drawStone(g2, BX + cursorC * CELL, BY + cursorR * CELL, turn, 0.45f);
        }

        // winning line
        if (state == State.OVER && winner != 0 && !winCells.isEmpty()) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(phase * 6));
            g2.setStroke(new BasicStroke(4f));
            g2.setColor(new Color(255, 70, 70, (int) (255 * pulse)));
            for (int[] cell : winCells)
                g2.draw(new Ellipse2D.Double(BX + cell[1] * CELL - 18, BY + cell[0] * CELL - 18, 36, 36));
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawStone(Graphics2D g2, int cx, int cy, int color, float alpha) {
        int rad = CELL / 2 - 3;
        int a = (int) (255 * alpha);
        g2.setColor(new Color(0, 0, 0, (int) (70 * alpha)));
        g2.fill(new Ellipse2D.Double(cx - rad + 2, cy - rad + 3, rad * 2, rad * 2));
        Color c1 = color == 1 ? new Color(95, 95, 100, a) : new Color(255, 255, 255, a);
        Color c2 = color == 1 ? new Color(10, 10, 14, a) : new Color(190, 192, 200, a);
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx - rad * 0.35f, cy - rad * 0.4f), rad * 1.5f,
                new float[]{0f, 1f}, new Color[]{c1, c2}));
        g2.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
        g2.setColor(new Color(0, 0, 0, (int) (60 * alpha)));
        g2.draw(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
    }

    private void drawHud(Graphics2D g2) {
        String p1 = twoPlayer ? "P1" : "YOU", p2 = twoPlayer ? "P2" : "CPU";
        chip(g2, p1, winsP1, humanColor, 40, 14, state == State.PLAYING && turn == humanColor);
        chip(g2, p2, winsP2, 3 - humanColor, W - 40 - 170, 14, state == State.PLAYING && turn != humanColor);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(150, 155, 185));
        String mid = "Draws " + draws;
        g2.drawString(mid, (W - g2.getFontMetrics().stringWidth(mid)) / 2, 36);
        String mv = "Move " + history.size();
        g2.drawString(mv, (W - g2.getFontMetrics().stringWidth(mv)) / 2, 54);

        String msg;
        Color col = Color.WHITE;
        if (state == State.OVER) {
            if (winner == 0) msg = "It's a draw!";
            else {
                boolean p1won = winner == humanColor;
                msg = (twoPlayer ? (p1won ? "P1" : "P2") : (p1won ? "You" : "CPU")) + " (" + (winner == 1 ? "Black" : "White")
                        + ") win" + (!twoPlayer && p1won ? "!" : "s!");
                col = new Color(255, 213, 79);
            }
            msg += "   (click or Space for next round)";
        } else if (humanTurn()) {
            msg = twoPlayer ? (turn == humanColor ? "P1" : "P2") + " to move (" + (turn == 1 ? "Black" : "White") + ")"
                    : "Your move (" + (turn == 1 ? "Black" : "White") + ")";
        } else {
            msg = "CPU is thinking...";
            col = new Color(190, 195, 220);
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 19));
        int x = (W - g2.getFontMetrics().stringWidth(msg)) / 2;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(msg, x + 2, 90 + 2);
        g2.setColor(col);
        g2.drawString(msg, x, 90);
    }

    private void chip(Graphics2D g2, String name, int wins, int color, int x, int y, boolean active) {
        g2.setColor(active ? new Color(44, 50, 84) : new Color(30, 34, 58));
        g2.fillRoundRect(x, y, 170, 50, 16, 16);
        g2.setColor(active ? new Color(222, 184, 135) : new Color(60, 66, 100));
        g2.setStroke(new BasicStroke(active ? 2.5f : 1.5f));
        g2.drawRoundRect(x, y, 170, 50, 16, 16);
        g2.setStroke(new BasicStroke(1f));
        drawStone(g2, x + 25, y + 25, color, 1f);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString(name, x + 50, y + 31);
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        String w = String.valueOf(wins);
        g2.drawString(w, x + 158 - g2.getFontMetrics().stringWidth(w), y + 37);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 70));
        int tw = g2.getFontMetrics().stringWidth("GOMOKU"), tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, new Color(240, 214, 160), tx + tw, 0, new Color(200, 150, 90)));
        g2.drawString("GOMOKU", tx, 140);
        centered(g2, "Five in a row wins - horizontal, vertical or diagonal",
                new Font("SansSerif", Font.PLAIN, 15), new Color(170, 175, 205), 180);

        drawStone(g2, W / 2 - 60, 232, 1, 1f);
        drawStone(g2, W / 2 - 20, 232, 2, 1f);
        drawStone(g2, W / 2 + 20, 232, 1, 1f);
        drawStone(g2, W / 2 + 60, 232, 2, 1f);

        for (int i = 0; i < OPTIONS.length; i++) {
            boolean sel = i == option;
            int y = 300 + i * 56;
            if (sel) {
                g2.setColor(new Color(255, 255, 255, 25));
                g2.fillRoundRect(W / 2 - 190, y - 36, 380, 48, 16, 16);
            }
            centered(g2, sel ? ">  " + OPTIONS[i] + "  <" : OPTIONS[i],
                    new Font("SansSerif", sel ? Font.BOLD : Font.PLAIN, sel ? 26 : 22),
                    sel ? Color.WHITE : new Color(130, 135, 165), y);
        }
        centered(g2, "Up / Down + Enter, press 1-4, or click an option", new Font("SansSerif", Font.PLAIN, 13),
                new Color(150, 155, 185), 550);
        centered(g2, "Colours swap each round - Black always moves first", new Font("SansSerif", Font.PLAIN, 13),
                new Color(150, 155, 185), 572);
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
            JFrame frame = new JFrame("Gomoku");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Gomoku game = new Gomoku();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
