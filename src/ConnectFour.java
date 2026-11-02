import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Connect Four against a minimax AI (3 difficulties) or a friend on the same screen.
 * Click a column (or Left/Right + Down/Space) to drop a disc. R: next round, Q/Esc: menu.
 */
public class ConnectFour extends JPanel implements Main.Game {

    static final int W = 640, H = 680, COLS = 7, ROWS = 6, CELL = 80;
    static final int BX = (W - COLS * CELL) / 2, BY = 150;
    static final String[] OPTIONS = {"1 Player  -  Easy", "1 Player  -  Normal", "1 Player  -  Hard", "2 Players"};
    static final int[] DEPTH = {2, 4, 6};
    static final int[] ORDER = {3, 2, 4, 1, 5, 0, 6};
    static final Color RED = new Color(231, 76, 60);
    static final Color YELLOW = new Color(244, 200, 30);

    enum State { MENU, PLAYING, OVER }

    private final Random rnd = new Random();
    private final int[][] board = new int[ROWS][COLS];

    private State state = State.MENU;
    private int option = 1, aiLevel = 1;
    private boolean twoPlayer;
    private int turn = 1, starter = 1, winner, hoverCol = 3, aiTimer;
    private int redWins, yellowWins, draws;
    private ArrayList<int[]> winCells = new ArrayList<>();

    // falling disc animation
    private boolean dropping;
    private int dCol, dRow, dPlayer;
    private double dY, dVy;
    private double phase;

    private final Timer loop = new Timer(16, e -> {
        update();
        repaint();
    });

    public ConnectFour() {
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
                } else if (state == State.PLAYING) {
                    if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) hoverCol = Math.max(0, hoverCol - 1);
                    else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) hoverCol = Math.min(COLS - 1, hoverCol + 1);
                    else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) humanDrop(hoverCol);
                } else if (state == State.OVER && (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER)) {
                    newRound();
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (state == State.MENU) {
                    int i = optionAt(e.getY());
                    if (i >= 0) option = i;
                } else {
                    int c = (e.getX() - BX) / CELL;
                    if (e.getX() >= BX && c >= 0 && c < COLS) hoverCol = c;
                }
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
                    int c = (e.getX() - BX) / CELL;
                    if (e.getX() >= BX && c >= 0 && c < COLS) humanDrop(c);
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
            int base = 290 + i * 56;
            if (y >= base - 36 && y <= base + 12) return i;
        }
        return -1;
    }

    // ---------- flow ----------
    private void startMatch() {
        twoPlayer = option == 3;
        if (!twoPlayer) aiLevel = option;
        redWins = 0;
        yellowWins = 0;
        draws = 0;
        starter = 2; // flipped to 1 by newRound
        newRound();
    }

    private void newRound() {
        for (int[] row : board) java.util.Arrays.fill(row, 0);
        winCells.clear();
        winner = 0;
        dropping = false;
        starter = 3 - starter;
        turn = starter;
        state = State.PLAYING;
        aiTimer = 30;
    }

    private boolean humanTurn() {
        return twoPlayer || turn == 1;
    }

    private void humanDrop(int col) {
        if (state != State.PLAYING || dropping || !humanTurn()) return;
        startDrop(col);
    }

    private void startDrop(int col) {
        int row = dropRow(board, col);
        if (row < 0) return;
        dropping = true;
        dCol = col;
        dRow = row;
        dPlayer = turn;
        dY = BY - CELL;
        dVy = 0;
    }

    private void landDisc() {
        dropping = false;
        board[dRow][dCol] = dPlayer;
        ArrayList<int[]> w = findWin(board);
        if (w != null) {
            winCells = w;
            winner = dPlayer;
            state = State.OVER;
            if (winner == 1) redWins++;
            else yellowWins++;
        } else if (isFull(board)) {
            winner = 0;
            state = State.OVER;
            draws++;
        } else {
            turn = 3 - turn;
            aiTimer = 22;
        }
    }

    private void update() {
        phase += 0.06;
        if (dropping) {
            dVy += 0.9;
            dY += dVy;
            double target = BY + dRow * CELL;
            if (dY >= target) {
                dY = target;
                if (dVy > 3.5) { // small bounce
                    dVy = -dVy * 0.28;
                    dY = target - 1;
                } else {
                    landDisc();
                }
            }
            return;
        }
        if (state == State.PLAYING && !twoPlayer && turn == 2 && --aiTimer <= 0) {
            startDrop(chooseAi());
        }
    }

    // ---------- board logic ----------
    private static int dropRow(int[][] b, int col) {
        for (int r = ROWS - 1; r >= 0; r--) if (b[r][col] == 0) return r;
        return -1;
    }

    private static boolean isFull(int[][] b) {
        for (int c = 0; c < COLS; c++) if (b[0][c] == 0) return false;
        return true;
    }

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private static ArrayList<int[]> findWin(int[][] b) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int p = b[r][c];
                if (p == 0) continue;
                for (int[] d : DIRS) {
                    int er = r + 3 * d[0], ec = c + 3 * d[1];
                    if (er < 0 || er >= ROWS || ec < 0 || ec >= COLS) continue;
                    boolean ok = true;
                    for (int k = 1; k < 4 && ok; k++) ok = b[r + k * d[0]][c + k * d[1]] == p;
                    if (ok) {
                        ArrayList<int[]> cells = new ArrayList<>();
                        for (int k = 0; k < 4; k++) cells.add(new int[]{r + k * d[0], c + k * d[1]});
                        return cells;
                    }
                }
            }
        return null;
    }

    private static int winnerOf(int[][] b) {
        ArrayList<int[]> w = findWin(b);
        return w == null ? 0 : b[w.get(0)[0]][w.get(0)[1]];
    }

    // ---------- AI ----------
    private int chooseAi() {
        ArrayList<Integer> valid = new ArrayList<>();
        for (int c : ORDER) if (dropRow(board, c) >= 0) valid.add(c);
        if (aiLevel == 0 && rnd.nextInt(100) < 30) return valid.get(rnd.nextInt(valid.size()));

        int[][] b = new int[ROWS][];
        for (int r = 0; r < ROWS; r++) b[r] = board[r].clone();

        int bestCol = valid.get(0), bestScore = Integer.MIN_VALUE;
        for (int c : valid) {
            int r = dropRow(b, c);
            b[r][c] = 2;
            int s = minimax(b, DEPTH[aiLevel] - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            b[r][c] = 0;
            if (s > bestScore) {
                bestScore = s;
                bestCol = c;
            }
        }
        return bestCol;
    }

    private int minimax(int[][] b, int depth, int alpha, int beta, boolean maximizing) {
        int w = winnerOf(b);
        if (w == 2) return 1_000_000 + depth;
        if (w == 1) return -1_000_000 - depth;
        if (isFull(b)) return 0;
        if (depth == 0) return evaluate(b);

        if (maximizing) {
            int best = Integer.MIN_VALUE;
            for (int c : ORDER) {
                int r = dropRow(b, c);
                if (r < 0) continue;
                b[r][c] = 2;
                best = Math.max(best, minimax(b, depth - 1, alpha, beta, false));
                b[r][c] = 0;
                alpha = Math.max(alpha, best);
                if (alpha >= beta) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int c : ORDER) {
                int r = dropRow(b, c);
                if (r < 0) continue;
                b[r][c] = 1;
                best = Math.min(best, minimax(b, depth - 1, alpha, beta, true));
                b[r][c] = 0;
                beta = Math.min(beta, best);
                if (alpha >= beta) break;
            }
            return best;
        }
    }

    private int evaluate(int[][] b) {
        int score = 0;
        for (int r = 0; r < ROWS; r++) if (b[r][3] == 2) score += 3; else if (b[r][3] == 1) score -= 3;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                for (int[] d : DIRS) {
                    int er = r + 3 * d[0], ec = c + 3 * d[1];
                    if (er < 0 || er >= ROWS || ec < 0 || ec >= COLS) continue;
                    int ai = 0, hu = 0;
                    for (int k = 0; k < 4; k++) {
                        int v = b[r + k * d[0]][c + k * d[1]];
                        if (v == 2) ai++;
                        else if (v == 1) hu++;
                    }
                    if (hu == 0) score += ai == 3 ? 50 : ai == 2 ? 10 : 0;
                    if (ai == 0) score -= hu == 3 ? 60 : hu == 2 ? 10 : 0;
                }
        return score;
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

        // placed discs (behind the board)
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c] != 0) drawDisc(g2, BX + c * CELL + CELL / 2.0, BY + r * CELL + CELL / 2.0, board[r][c], 1f);
        if (dropping) drawDisc(g2, BX + dCol * CELL + CELL / 2.0, dY + CELL / 2.0, dPlayer, 1f);

        drawBoard(g2);

        // hover disc above the board
        if (state == State.PLAYING && !dropping && humanTurn()) {
            drawDisc(g2, BX + hoverCol * CELL + CELL / 2.0, BY - 36, turn, 0.75f);
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRoundRect(BX + hoverCol * CELL + 4, BY, CELL - 8, ROWS * CELL, 18, 18);
        }

        // winning line
        if (state == State.OVER && winner != 0) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(phase * 6));
            g2.setStroke(new BasicStroke(5f));
            for (int[] cell : winCells) {
                double cx = BX + cell[1] * CELL + CELL / 2.0, cy = BY + cell[0] * CELL + CELL / 2.0;
                g2.setColor(new Color(1f, 1f, 1f, pulse));
                g2.draw(new Ellipse2D.Double(cx - 33, cy - 33, 66, 66));
            }
            g2.setStroke(new BasicStroke(1f));
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(130, 135, 165));
        String hint = "Click a column  -  or Left/Right + Down/Space      R: next round   Q: menu";
        g2.drawString(hint, (W - g2.getFontMetrics().stringWidth(hint)) / 2, H - 14);
    }

    private void drawDisc(Graphics2D g2, double cx, double cy, int player, float alpha) {
        Color base = player == 1 ? RED : YELLOW;
        int a = (int) (255 * alpha);
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), a));
        g2.fill(new Ellipse2D.Double(cx - 33, cy - 33, 66, 66));
        Color dark = base.darker();
        g2.setColor(new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), a));
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new Ellipse2D.Double(cx - 26, cy - 26, 52, 52));
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, (int) (70 * alpha)));
        g2.fill(new Ellipse2D.Double(cx - 18, cy - 24, 22, 12));
    }

    private void drawBoard(Graphics2D g2) {
        Area boardShape = new Area(new RoundRectangle2D.Double(BX - 10, BY - 10, COLS * CELL + 20, ROWS * CELL + 20, 34, 34));
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                boardShape.subtract(new Area(new Ellipse2D.Double(BX + c * CELL + CELL / 2.0 - 33,
                        BY + r * CELL + CELL / 2.0 - 33, 66, 66)));
        g2.setPaint(new GradientPaint(0, BY, new Color(46, 92, 220), 0, BY + ROWS * CELL, new Color(26, 56, 150)));
        g2.fill(boardShape);
        g2.setColor(new Color(16, 34, 100));
        g2.setStroke(new BasicStroke(3f));
        g2.draw(boardShape);
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawHud(Graphics2D g2) {
        // scoreboard
        String p1 = "RED", p2 = twoPlayer ? "YELLOW" : "CPU";
        scoreChip(g2, p1, redWins, RED, 40, 20, turn == 1 && state == State.PLAYING);
        scoreChip(g2, p2, yellowWins, YELLOW, W - 40 - 170, 20, turn == 2 && state == State.PLAYING);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(150, 155, 185));
        String d = "Draws " + draws;
        g2.drawString(d, (W - g2.getFontMetrics().stringWidth(d)) / 2, 42);

        // status line
        String msg;
        Color col;
        if (state == State.OVER) {
            if (winner == 0) {
                msg = "It's a draw!";
                col = Color.WHITE;
            } else {
                msg = (winner == 1 ? (twoPlayer ? "Red" : "You") : (twoPlayer ? "Yellow" : "CPU")) + " win" + (winner == 1 && !twoPlayer ? "!" : "s!");
                col = winner == 1 ? RED : YELLOW;
            }
            msg += "   (click or R for next round)";
        } else if (!twoPlayer && turn == 2) {
            msg = "CPU is thinking...";
            col = YELLOW;
        } else {
            msg = (twoPlayer ? (turn == 1 ? "Red" : "Yellow") : "Your") + (twoPlayer ? "'s turn" : " turn");
            col = turn == 1 ? RED : YELLOW;
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(msg, (W - g2.getFontMetrics().stringWidth(msg)) / 2 + 2, 98 + 2);
        g2.setColor(col);
        g2.drawString(msg, (W - g2.getFontMetrics().stringWidth(msg)) / 2, 98);
    }

    private void scoreChip(Graphics2D g2, String name, int wins, Color c, int x, int y, boolean active) {
        g2.setColor(active ? new Color(44, 50, 84) : new Color(30, 34, 58));
        g2.fillRoundRect(x, y, 170, 50, 16, 16);
        g2.setColor(active ? c : new Color(60, 66, 100));
        g2.setStroke(new BasicStroke(active ? 2.5f : 1.5f));
        g2.drawRoundRect(x, y, 170, 50, 16, 16);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(c);
        g2.fillOval(x + 12, y + 12, 26, 26);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString(name, x + 48, y + 31);
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        String w = String.valueOf(wins);
        g2.drawString(w, x + 158 - g2.getFontMetrics().stringWidth(w), y + 37);
    }

    private void drawMenu(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 62));
        int tw = g2.getFontMetrics().stringWidth("CONNECT 4"), tx = (W - tw) / 2;
        g2.setPaint(new GradientPaint(tx, 0, RED, tx + tw, 0, YELLOW));
        g2.drawString("CONNECT 4", tx, 140);
        centered(g2, "Get four in a row - horizontally, vertically or diagonally",
                new Font("SansSerif", Font.PLAIN, 15), new Color(170, 175, 205), 180);

        // mini discs
        for (int i = 0; i < 4; i++) {
            drawDisc(g2, W / 2 - 105 + i * 70, 225, i % 2 == 0 ? 1 : 2, 1f);
        }

        for (int i = 0; i < OPTIONS.length; i++) {
            boolean sel = i == option;
            int y = 290 + i * 56;
            if (sel) {
                g2.setColor(new Color(255, 255, 255, 25));
                g2.fillRoundRect(W / 2 - 190, y - 36, 380, 48, 16, 16);
            }
            centered(g2, sel ? ">  " + OPTIONS[i] + "  <" : OPTIONS[i],
                    new Font("SansSerif", sel ? Font.BOLD : Font.PLAIN, sel ? 26 : 22),
                    sel ? Color.WHITE : new Color(130, 135, 165), y);
        }
        centered(g2, "Up / Down + Enter, press 1-4, or click an option", new Font("SansSerif", Font.PLAIN, 13),
                new Color(150, 155, 185), 540);
        centered(g2, "Hard uses a deep minimax search - good luck!", new Font("SansSerif", Font.PLAIN, 13),
                new Color(150, 155, 185), 562);
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
            JFrame frame = new JFrame("Connect 4");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            ConnectFour game = new ConnectFour();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
