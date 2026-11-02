import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.prefs.Preferences;

/**
 * 2048 with animated tiles, one-step undo, swipe/drag support and a saved best score.
 * Arrows / WASD to move, N new game, U or Z undo.
 */
public class Game2048 extends JPanel implements Main.Game {

    static final int N = 4, CELL = 100, GAP = 12;
    static final int BOARD = N * CELL + (N + 1) * GAP;
    static final int W = 520, H = 680, BX = (W - BOARD) / 2, BY = 180;

    enum State { PLAYING, WON, OVER }

    static class Tile {
        int value, r, c;
        double x, y, scale = 1;
        boolean dead, merged;

        Tile(int value, int r, int c) {
            this.value = value;
            this.r = r;
            this.c = c;
        }
    }

    private final Random rnd = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Game2048.class);

    private final ArrayList<Tile> tiles = new ArrayList<>();
    private final Tile[][] grid = new Tile[N][N];
    private int[][] prevValues = new int[N][N];
    private int prevScore;
    private boolean canUndo, continued;

    private State state = State.PLAYING;
    private int score, best;
    private Point pressPoint;

    private final Rectangle newBtn = new Rectangle(BX, 118, 120, 38);
    private final Rectangle undoBtn = new Rectangle(BX + 132, 118, 90, 38);

    private final Timer loop = new Timer(16, e -> {
        animate();
        repaint();
    });

    public Game2048() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(12, 14, 26));
        setFocusable(true);
        best = prefs.getInt("g2048_best", 0);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) slide(0, -1);
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) slide(0, 1);
                else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) slide(-1, 0);
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) slide(1, 0);
                else if (k == KeyEvent.VK_N) newGame();
                else if (k == KeyEvent.VK_U || k == KeyEvent.VK_Z) undo();
                else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) {
                    if (state == State.WON) {
                        state = State.PLAYING;
                        continued = true;
                    } else if (state == State.OVER) newGame();
                }
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                pressPoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressPoint == null) return;
                int dx = e.getX() - pressPoint.x, dy = e.getY() - pressPoint.y;
                if (Math.abs(dx) < 12 && Math.abs(dy) < 12) {
                    if (newBtn.contains(e.getPoint())) newGame();
                    else if (undoBtn.contains(e.getPoint())) undo();
                } else if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > 30) slide(0, dx > 0 ? 1 : -1);
                } else if (Math.abs(dy) > 30) {
                    slide(dy > 0 ? 1 : -1, 0);
                }
                pressPoint = null;
            }
        };
        addMouseListener(mouse);

        newGame();
        loop.start();
    }

    @Override
    public void stop() {
        loop.stop();
    }

    // ---------- helpers ----------
    private static int px(int c) {
        return BX + GAP + c * (CELL + GAP);
    }

    private static int py(int r) {
        return BY + GAP + r * (CELL + GAP);
    }

    private void clearGrid() {
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) grid[r][c] = null;
    }

    private Tile spawnTile(int value, int r, int c, boolean pop) {
        Tile t = new Tile(value, r, c);
        t.x = px(c);
        t.y = py(r);
        if (pop) t.scale = 0.3;
        tiles.add(t);
        grid[r][c] = t;
        return t;
    }

    private void addRandom() {
        ArrayList<int[]> empty = new ArrayList<>();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) if (grid[r][c] == null) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return;
        int[] p = empty.get(rnd.nextInt(empty.size()));
        spawnTile(rnd.nextDouble() < 0.9 ? 2 : 4, p[0], p[1], true);
    }

    private void newGame() {
        tiles.clear();
        clearGrid();
        score = 0;
        state = State.PLAYING;
        continued = false;
        canUndo = false;
        addRandom();
        addRandom();
    }

    // ---------- moving ----------
    private void slide(int dr, int dc) {
        if (state != State.PLAYING) return;

        tiles.removeIf(t -> t.dead);
        for (Tile t : tiles) t.merged = false;

        int[][] snapshot = new int[N][N];
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) snapshot[r][c] = grid[r][c] == null ? 0 : grid[r][c].value;
        int scoreBefore = score;

        boolean moved = false;
        for (int i = 0; i < N; i++) {
            Tile prev = null;
            int target = 0;
            for (int k = 0; k < N; k++) {
                int r, c;
                if (dr != 0) {
                    r = dr < 0 ? k : N - 1 - k;
                    c = i;
                } else {
                    r = i;
                    c = dc < 0 ? k : N - 1 - k;
                }
                Tile t = grid[r][c];
                if (t == null) continue;

                if (prev != null && !prev.merged && prev.value == t.value) {
                    prev.value *= 2;
                    prev.merged = true;
                    prev.scale = 1.18;
                    t.dead = true;
                    t.r = prev.r;
                    t.c = prev.c;
                    score += prev.value;
                    moved = true;
                } else {
                    int nr = dr != 0 ? (dr < 0 ? target : N - 1 - target) : i;
                    int nc = dr != 0 ? i : (dc < 0 ? target : N - 1 - target);
                    if (nr != t.r || nc != t.c) moved = true;
                    t.r = nr;
                    t.c = nc;
                    target++;
                    prev = t;
                }
            }
        }

        clearGrid();
        for (Tile t : tiles) if (!t.dead) grid[t.r][t.c] = t;

        if (!moved) return;

        prevValues = snapshot;
        prevScore = scoreBefore;
        canUndo = true;
        addRandom();

        if (score > best) {
            best = score;
            prefs.putInt("g2048_best", best);
        }
        if (!continued) {
            for (Tile t : tiles) if (!t.dead && t.value >= 2048) state = State.WON;
        }
        if (state == State.PLAYING && !hasMoves()) state = State.OVER;
    }

    private boolean hasMoves() {
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                if (grid[r][c] == null) return true;
                int v = grid[r][c].value;
                if (c + 1 < N && grid[r][c + 1] != null && grid[r][c + 1].value == v) return true;
                if (r + 1 < N && grid[r + 1][c] != null && grid[r + 1][c].value == v) return true;
            }
        return false;
    }

    private void undo() {
        if (!canUndo) return;
        tiles.clear();
        clearGrid();
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) if (prevValues[r][c] > 0) spawnTile(prevValues[r][c], r, c, false);
        score = prevScore;
        canUndo = false;
        state = State.PLAYING;
    }

    private void animate() {
        for (Tile t : tiles) {
            double tx = px(t.c), ty = py(t.r);
            t.x += (tx - t.x) * 0.35;
            t.y += (ty - t.y) * 0.35;
            if (Math.abs(tx - t.x) < 0.5) t.x = tx;
            if (Math.abs(ty - t.y) < 0.5) t.y = ty;
            t.scale += (1 - t.scale) * 0.25;
            if (Math.abs(1 - t.scale) < 0.01) t.scale = 1;
        }
        // drop merged-away tiles once they have arrived
        tiles.removeIf(t -> t.dead && t.x == px(t.c) && t.y == py(t.r));
    }

    // ---------- rendering ----------
    private static Color tileColor(int v) {
        return switch (v) {
            case 2 -> new Color(238, 228, 218);
            case 4 -> new Color(237, 224, 200);
            case 8 -> new Color(242, 177, 121);
            case 16 -> new Color(245, 149, 99);
            case 32 -> new Color(246, 124, 95);
            case 64 -> new Color(246, 94, 59);
            case 128 -> new Color(237, 207, 114);
            case 256 -> new Color(237, 204, 97);
            case 512 -> new Color(237, 200, 80);
            case 1024 -> new Color(237, 197, 63);
            case 2048 -> new Color(237, 194, 46);
            default -> new Color(90, 70, 160);
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(18, 20, 40), 0, H, new Color(8, 8, 16)));
        g2.fillRect(0, 0, W, H);

        // header
        g2.setFont(new Font("SansSerif", Font.BOLD, 68));
        g2.setColor(new Color(237, 194, 46));
        g2.drawString("2048", BX, 84);
        scoreBox(g2, "SCORE", score, W - BX - 214, 30);
        scoreBox(g2, "BEST", best, W - BX - 104, 30);

        button(g2, newBtn, "New Game", true);
        button(g2, undoBtn, "Undo", canUndo);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(130, 135, 165));
        String hint = "Arrows / WASD / swipe";
        g2.drawString(hint, W - BX - g2.getFontMetrics().stringWidth(hint), 142);
        g2.drawString("Join the tiles, get to 2048!", BX, 174);

        // board
        g2.setColor(new Color(52, 56, 86));
        g2.fillRoundRect(BX, BY, BOARD, BOARD, 18, 18);
        g2.setColor(new Color(36, 40, 64));
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) g2.fillRoundRect(px(c), py(r), CELL, CELL, 12, 12);

        // dead (merging) tiles first so survivors sit on top
        for (Tile t : tiles) if (t.dead) drawTile(g2, t);
        for (Tile t : tiles) if (!t.dead) drawTile(g2, t);

        if (state == State.WON) {
            overlay(g2, new Color(237, 194, 46, 170), "You made 2048!", "SPACE: keep going     N: new game");
        } else if (state == State.OVER) {
            overlay(g2, new Color(10, 10, 20, 190), "Game over", "Press N or SPACE to try again");
        }
    }

    private void drawTile(Graphics2D g2, Tile t) {
        double s = t.scale;
        int size = (int) (CELL * s);
        int x = (int) t.x + (CELL - size) / 2, y = (int) t.y + (CELL - size) / 2;
        g2.setColor(tileColor(t.value));
        g2.fillRoundRect(x, y, size, size, 12, 12);

        String text = String.valueOf(t.value);
        int fs = text.length() <= 2 ? 44 : text.length() == 3 ? 38 : 30;
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (fs * s)));
        g2.setColor(t.value <= 4 ? new Color(119, 110, 101) : Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + (size - fm.stringWidth(text)) / 2, y + (size + fm.getAscent()) / 2 - 4);
    }

    private void scoreBox(Graphics2D g2, String label, int value, int x, int y) {
        g2.setColor(new Color(36, 40, 64));
        g2.fillRoundRect(x, y, 100, 62, 12, 12);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(150, 155, 185));
        g2.drawString(label, x + (100 - g2.getFontMetrics().stringWidth(label)) / 2, y + 20);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        String v = String.valueOf(value);
        g2.drawString(v, x + (100 - g2.getFontMetrics().stringWidth(v)) / 2, y + 48);
    }

    private void button(Graphics2D g2, Rectangle r, String text, boolean enabled) {
        g2.setColor(enabled ? new Color(237, 194, 46) : new Color(52, 56, 86));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(enabled ? new Color(40, 34, 20) : new Color(110, 115, 145));
        g2.drawString(text, r.x + (r.width - g2.getFontMetrics().stringWidth(text)) / 2, r.y + 24);
    }

    private void overlay(Graphics2D g2, Color bg, String title, String sub) {
        g2.setColor(bg);
        g2.fillRoundRect(BX, BY, BOARD, BOARD, 18, 18);
        g2.setFont(new Font("SansSerif", Font.BOLD, 48));
        FontMetrics fm = g2.getFontMetrics();
        int cx = BX + BOARD / 2;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(title, cx - fm.stringWidth(title) / 2 + 2, BY + BOARD / 2 - 6);
        g2.setColor(Color.WHITE);
        g2.drawString(title, cx - fm.stringWidth(title) / 2, BY + BOARD / 2 - 8);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString(sub, cx - g2.getFontMetrics().stringWidth(sub) / 2, BY + BOARD / 2 + 28);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("2048");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            Game2048 game = new Game2048();
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}