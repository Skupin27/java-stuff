import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Random;
import java.util.prefs.Preferences;

public class Main extends JPanel {

    // ---------- constants ----------
    static final int CELL = 25, COLS = 24, ROWS = 24, HUD = 40;
    static final int W = COLS * CELL, H = ROWS * CELL;
    static final int FOOD_PER_LEVEL = 5;

    enum State { MENU, PLAYING, PAUSED, OVER }

    enum Difficulty {
        //       label     delay  startRocks  rocksPerLevel
        EASY("Easy", 140, 0, 0),
        NORMAL("Normal", 105, 3, 1),
        HARD("Hard", 75, 6, 2);

        final String label;
        final int baseDelay, startRocks, rocksPerLevel;

        Difficulty(String label, int baseDelay, int startRocks, int rocksPerLevel) {
            this.label = label;
            this.baseDelay = baseDelay;
            this.startRocks = startRocks;
            this.rocksPerLevel = rocksPerLevel;
        }
    }

    enum Kind { FOOD, BONUS, SLOW }

    static class Item {
        final Point p;
        final Kind kind;
        int life;
        final int maxLife;

        Item(Point p, Kind kind, int life) {
            this.p = p;
            this.kind = kind;
            this.life = life;
            this.maxLife = life;
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

    // ---------- state ----------
    private final Random random = new Random();
    private final Preferences prefs = Preferences.userNodeForPackage(Main.class);

    private final ArrayList<Point> snake = new ArrayList<>();
    private final ArrayList<Point> rocks = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Deque<int[]> inputQueue = new ArrayDeque<>();

    private Item food, bonus, slow;
    private State state = State.MENU;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean wrapMode = false;

    private int dx = 1, dy = 0;
    private int score, eaten, level, slowTicks, shake;
    private boolean newRecord;
    private double phase;

    private final Timer gameTimer = new Timer(100, e -> step());
    private final Timer animTimer = new Timer(25, e -> animate());

    public Main() {
        setPreferredSize(new Dimension(W, H + HUD));
        setBackground(new Color(22, 22, 32));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
            }
        });
        animTimer.start();
    }

    // ---------- input ----------
    private void handleKey(int key) {
        switch (state) {
            case MENU -> {
                switch (key) {
                    case KeyEvent.VK_UP, KeyEvent.VK_W ->
                            difficulty = Difficulty.values()[(difficulty.ordinal() + 2) % 3];
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S ->
                            difficulty = Difficulty.values()[(difficulty.ordinal() + 1) % 3];
                    case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_M ->
                            wrapMode = !wrapMode;
                    case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> startGame();
                }
            }
            case PLAYING -> {
                switch (key) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> queueDir(-1, 0);
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> queueDir(1, 0);
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> queueDir(0, -1);
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S -> queueDir(0, 1);
                    case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> {
                        state = State.PAUSED;
                        gameTimer.stop();
                    }
                }
            }
            case PAUSED -> {
                switch (key) {
                    case KeyEvent.VK_P, KeyEvent.VK_ESCAPE, KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> {
                        state = State.PLAYING;
                        gameTimer.start();
                    }
                    case KeyEvent.VK_Q -> state = State.MENU;
                }
            }
            case OVER -> {
                switch (key) {
                    case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> startGame();
                    case KeyEvent.VK_ESCAPE, KeyEvent.VK_Q -> state = State.MENU;
                }
            }
        }
    }

    private void queueDir(int ndx, int ndy) {
        int[] last = inputQueue.isEmpty() ? new int[]{dx, dy} : inputQueue.peekLast();
        boolean opposite = last[0] == -ndx && last[1] == -ndy;
        boolean same = last[0] == ndx && last[1] == ndy;
        if (!opposite && !same && inputQueue.size() < 3) {
            inputQueue.addLast(new int[]{ndx, ndy});
        }
    }

    // ---------- game setup ----------
    private void startGame() {
        snake.clear();
        rocks.clear();
        particles.clear();
        inputQueue.clear();
        bonus = null;
        slow = null;
        food = null;

        int cx = COLS / 2, cy = ROWS / 2;
        for (int i = 0; i < 3; i++) snake.add(new Point(cx - i, cy));
        dx = 1;
        dy = 0;
        score = 0;
        eaten = 0;
        level = 1;
        slowTicks = 0;
        shake = 0;
        newRecord = false;

        for (int i = 0; i < difficulty.startRocks; i++) addRock();
        food = new Item(freeCell(0), Kind.FOOD, 0);

        state = State.PLAYING;
        gameTimer.setDelay(currentDelay());
        gameTimer.start();
    }

    private int currentDelay() {
        int d = Math.max(45, difficulty.baseDelay - (level - 1) * 6);
        return slowTicks > 0 ? (int) (d * 1.7) : d;
    }

    private boolean isOccupied(Point p) {
        if (snake.contains(p) || rocks.contains(p)) return true;
        if (food != null && food.p.equals(p)) return true;
        if (bonus != null && bonus.p.equals(p)) return true;
        return slow != null && slow.p.equals(p);
    }

    /** Random free cell that is at least minDist (Manhattan) away from the snake head. */
    private Point freeCell(int minDist) {
        Point head = snake.get(0);
        for (int tries = 0; tries < 1000; tries++) {
            Point p = new Point(random.nextInt(COLS), random.nextInt(ROWS));
            int dist = Math.abs(p.x - head.x) + Math.abs(p.y - head.y);
            if (!isOccupied(p) && dist >= minDist) return p;
        }
        return new Point(0, 0);
    }

    private void addRock() {
        if (rocks.size() < 40) rocks.add(freeCell(6));
    }

    // ---------- game logic ----------
    private void step() {
        if (state != State.PLAYING) return;

        if (!inputQueue.isEmpty()) {
            int[] d = inputQueue.pollFirst();
            dx = d[0];
            dy = d[1];
        }

        Point head = snake.get(0);
        int nx = head.x + dx, ny = head.y + dy;

        if (wrapMode) {
            nx = (nx + COLS) % COLS;
            ny = (ny + ROWS) % ROWS;
        } else if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
            die();
            return;
        }
        Point nh = new Point(nx, ny);

        boolean eatFood = food != null && food.p.equals(nh);
        boolean eatBonus = bonus != null && bonus.p.equals(nh);
        boolean eatSlow = slow != null && slow.p.equals(nh);
        boolean grow = eatFood || eatBonus;

        // self collision (tail moves away unless we are growing)
        int bodyLen = grow ? snake.size() : snake.size() - 1;
        for (int i = 0; i < bodyLen; i++) {
            if (snake.get(i).equals(nh)) {
                die();
                return;
            }
        }
        if (rocks.contains(nh)) {
            die();
            return;
        }

        snake.add(0, nh);
        if (!grow) snake.remove(snake.size() - 1);

        if (eatFood) {
            score += 10;
            onEat(nh, new Color(235, 70, 80), 12);
            food = new Item(freeCell(0), Kind.FOOD, 0);
        }
        if (eatBonus) {
            score += 50;
            onEat(nh, new Color(255, 205, 60), 30);
            bonus = null;
        }
        if (eatSlow) {
            slowTicks = 60;
            burst(nh, new Color(90, 170, 255), 20);
            slow = null;
        }

        // timed items and effects
        if (bonus != null && --bonus.life <= 0) bonus = null;
        if (slow != null && --slow.life <= 0) slow = null;
        if (slowTicks > 0) slowTicks--;

        // random spawns
        if (bonus == null && random.nextDouble() < 0.012) {
            bonus = new Item(freeCell(0), Kind.BONUS, 55);
        }
        if (slow == null && random.nextDouble() < 0.006) {
            slow = new Item(freeCell(0), Kind.SLOW, 70);
        }

        gameTimer.setDelay(currentDelay());
    }

    private void onEat(Point cell, Color color, int count) {
        burst(cell, color, count);
        eaten++;
        int newLevel = 1 + eaten / FOOD_PER_LEVEL;
        if (newLevel > level) {
            level = newLevel;
            for (int i = 0; i < difficulty.rocksPerLevel; i++) addRock();
        }
    }

    private String bestKey() {
        return "best_" + difficulty.name() + (wrapMode ? "_wrap" : "_walls");
    }

    private int best() {
        return prefs.getInt(bestKey(), 0);
    }

    private void die() {
        state = State.OVER;
        gameTimer.stop();
        shake = 14;
        burst(snake.get(0), new Color(255, 120, 120), 40);
        if (score > best()) {
            newRecord = score > 0;
            prefs.putInt(bestKey(), score);
        }
    }

    // ---------- effects ----------
    private void burst(Point cell, Color color, int count) {
        double cx = cell.x * CELL + CELL / 2.0, cy = cell.y * CELL + CELL / 2.0;
        for (int i = 0; i < count; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double speed = 1 + random.nextDouble() * 3.5;
            particles.add(new Particle(cx, cy, Math.cos(ang) * speed, Math.sin(ang) * speed,
                    18 + random.nextInt(14), color));
        }
    }

    private void animate() {
        phase += 0.12;
        particles.removeIf(p -> {
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.94;
            p.vy *= 0.94;
            return --p.life <= 0;
        });
        if (shake > 0) shake--;
        repaint();
    }

    // ---------- rendering ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawHud(g2);

        int sx = shake > 0 ? random.nextInt(shake + 1) - shake / 2 : 0;
        int sy = shake > 0 ? random.nextInt(shake + 1) - shake / 2 : 0;
        g2.translate(sx, HUD + sy);

        drawBoard(g2);
        if (state != State.MENU) {
            drawRocks(g2);
            drawItems(g2);
            drawSnake(g2);
            drawParticles(g2);
        }

        g2.setColor(wrapMode ? new Color(90, 160, 255) : new Color(200, 200, 220));
        g2.setStroke(new BasicStroke(wrapMode ? 2f : 3f,
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                wrapMode ? new float[]{8f, 6f} : null, 0f));
        g2.drawRect(0, 0, W - 1, H - 1);
        g2.setStroke(new BasicStroke(1f));

        switch (state) {
            case MENU -> drawMenu(g2);
            case PAUSED -> drawOverlay(g2, "PAUSED", "SPACE / P to resume  -  Q for menu", null);
            case OVER -> drawOverlay(g2, "GAME OVER",
                    "Score: " + score + (newRecord ? "   NEW RECORD!" : "   Best: " + best()),
                    "SPACE to retry  -  Q for menu");
            default -> { }
        }
        g2.translate(-sx, -(HUD + sy));
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(new Color(15, 15, 22));
        g2.fillRect(0, 0, W, HUD);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.setColor(Color.WHITE);
        g2.drawString("Score: " + score, 12, 26);
        g2.drawString("Level: " + level, 140, 26);
        g2.setColor(new Color(255, 205, 60));
        g2.drawString("Best: " + best(), 250, 26);
        g2.setColor(new Color(170, 170, 190));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.drawString(difficulty.label + " / " + (wrapMode ? "Wrap" : "Walls"), 360, 26);

        if (slowTicks > 0) {
            g2.setColor(new Color(90, 170, 255));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString("SLOW " + (slowTicks * currentDelay() / 1000 + 1) + "s", W - 80, 26);
        }
    }

    private void drawBoard(Graphics2D g2) {
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                g2.setColor((x + y) % 2 == 0 ? new Color(30, 30, 44) : new Color(26, 26, 38));
                g2.fillRect(x * CELL, y * CELL, CELL, CELL);
            }
        }
    }

    private void drawRocks(Graphics2D g2) {
        for (Point r : rocks) {
            g2.setColor(new Color(110, 110, 125));
            g2.fillRoundRect(r.x * CELL + 1, r.y * CELL + 1, CELL - 2, CELL - 2, 6, 6);
            g2.setColor(new Color(75, 75, 90));
            g2.drawRoundRect(r.x * CELL + 1, r.y * CELL + 1, CELL - 3, CELL - 3, 6, 6);
            g2.setColor(new Color(140, 140, 155));
            g2.fillRect(r.x * CELL + 5, r.y * CELL + 5, 6, 3);
        }
    }

    private void drawItems(Graphics2D g2) {
        double pulse = Math.sin(phase * 2) * 2;

        if (food != null) {
            int px = food.p.x * CELL, py = food.p.y * CELL;
            int s = (int) (CELL - 8 + pulse);
            int o = (CELL - s) / 2;
            g2.setColor(new Color(235, 70, 80));
            g2.fillOval(px + o, py + o, s, s);
            g2.setColor(new Color(255, 160, 160));
            g2.fillOval(px + o + 3, py + o + 3, 5, 5);
        }

        if (bonus != null) {
            int px = bonus.p.x * CELL, py = bonus.p.y * CELL;
            boolean blink = bonus.life > 15 || (bonus.life / 2) % 2 == 0;
            if (blink) {
                g2.setColor(new Color(255, 205, 60));
                g2.fillOval(px + 3, py + 3, CELL - 6, CELL - 6);
                g2.setColor(new Color(255, 245, 170));
                g2.fillOval(px + 8, py + 6, 6, 6);
                g2.setColor(new Color(255, 255, 255, 190));
                g2.setStroke(new BasicStroke(2f));
                g2.drawArc(px, py, CELL, CELL, 90, (int) (360.0 * bonus.life / bonus.maxLife));
                g2.setStroke(new BasicStroke(1f));
            }
        }

        if (slow != null) {
            int px = slow.p.x * CELL, py = slow.p.y * CELL;
            boolean blink = slow.life > 15 || (slow.life / 2) % 2 == 0;
            if (blink) {
                int c = CELL / 2;
                int r = (int) (9 + pulse / 2);
                Polygon d = new Polygon(
                        new int[]{px + c, px + c + r, px + c, px + c - r},
                        new int[]{py + c - r, py + c, py + c + r, py + c}, 4);
                g2.setColor(new Color(90, 170, 255));
                g2.fillPolygon(d);
                g2.setColor(Color.WHITE);
                g2.drawPolygon(d);
            }
        }
    }

    private void drawSnake(Graphics2D g2) {
        int n = snake.size();
        for (int i = n - 1; i >= 0; i--) {
            Point p = snake.get(i);
            float t = n > 1 ? (float) i / (n - 1) : 0f;
            Color c = state == State.OVER
                    ? new Color(150, 90, 90)
                    : blend(new Color(130, 240, 130), new Color(30, 130, 80), t);
            if (slowTicks > 0 && state != State.OVER) c = blend(c, new Color(90, 170, 255), 0.45f);
            g2.setColor(c);
            g2.fillRoundRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 2, CELL - 2, 9, 9);
        }

        // eyes
        Point h = snake.get(0);
        int cx = h.x * CELL + CELL / 2, cy = h.y * CELL + CELL / 2;
        int fx = dx * 5, fy = dy * 5;       // forward offset
        int sx = -dy * 5, sy = dx * 5;      // sideways offset
        g2.setColor(Color.WHITE);
        g2.fillOval(cx + fx + sx - 3, cy + fy + sy - 3, 6, 6);
        g2.fillOval(cx + fx - sx - 3, cy + fy - sy - 3, 6, 6);
        g2.setColor(Color.BLACK);
        g2.fillOval(cx + fx + sx + dx - 1, cy + fy + sy + dy - 1, 3, 3);
        g2.fillOval(cx + fx - sx + dx - 1, cy + fy - sy + dy - 1, 3, 3);
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            int alpha = Math.max(0, Math.min(255, 255 * p.life / p.maxLife));
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
            g2.fillOval((int) p.x - 2, (int) p.y - 2, 5, 5);
        }
    }

    private void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRect(0, 0, W, H);

        drawCentered(g2, "SNAKE", new Font("SansSerif", Font.BOLD, 64), new Color(120, 230, 120), 130);
        drawCentered(g2, "Select difficulty (Up / Down)", new Font("SansSerif", Font.PLAIN, 15),
                new Color(170, 170, 190), 195);

        Difficulty[] all = Difficulty.values();
        for (int i = 0; i < all.length; i++) {
            boolean sel = all[i] == difficulty;
            drawCentered(g2, sel ? "<  " + all[i].label + "  >" : all[i].label,
                    new Font("SansSerif", sel ? Font.BOLD : Font.PLAIN, sel ? 26 : 22),
                    sel ? Color.WHITE : new Color(120, 120, 140), 240 + i * 38);
        }

        drawCentered(g2, "Mode (Left / Right):  " + (wrapMode ? "WRAP - walls loop around" : "WALLS - walls are deadly"),
                new Font("SansSerif", Font.BOLD, 16), new Color(90, 170, 255), 395);
        drawCentered(g2, "High score: " + best(), new Font("SansSerif", Font.PLAIN, 16),
                new Color(255, 205, 60), 425);
        drawCentered(g2, "Press SPACE to start", new Font("SansSerif", Font.BOLD, 20), Color.WHITE, 475);

        Font small = new Font("SansSerif", Font.PLAIN, 13);
        Color grey = new Color(160, 160, 180);
        drawCentered(g2, "Move: Arrows / WASD     Pause: P", small, grey, 515);
        drawCentered(g2, "Red = +10     Gold = +50 (timed)     Blue = slow-motion     Grey = rocks", small, grey, 538);
    }

    private void drawOverlay(Graphics2D g2, String title, String line1, String line2) {
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRect(0, 0, W, H);
        drawCentered(g2, title, new Font("SansSerif", Font.BOLD, 52), Color.WHITE, H / 2 - 20);
        drawCentered(g2, line1, new Font("SansSerif", Font.BOLD, 18), new Color(255, 205, 60), H / 2 + 20);
        if (line2 != null) {
            drawCentered(g2, line2, new Font("SansSerif", Font.PLAIN, 15), new Color(170, 170, 190), H / 2 + 50);
        }
    }

    private void drawCentered(Graphics2D g2, String text, Font font, Color color, int y) {
        g2.setFont(font);
        int x = (W - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(text, x + 2, y + 2);
        g2.setColor(color);
        g2.drawString(text, x, y);
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ---------- entry point ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new Main());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
