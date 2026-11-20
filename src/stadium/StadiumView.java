package stadium;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The top-down picture of the stadium. It only reads the simulation, it never changes it.
 *
 * The bowl is an ellipse with the gates spaced evenly around its edge. Outside each gate a lane
 * runs outward (blue dots). Fans who found the lane full wait in the crowd beyond its end
 * (orange dots). Fans still walking toward the gate are gray, fans at a scanner are green, and
 * fans who just got in are gold and walk to a seat. With one shared line, the line is drawn in a
 * holding area under the stadium and fans are called from it to whichever scanner is free.
 *
 * Every fan has a drawn position that eases toward its place in line, so fans shuffle forward
 * smoothly instead of jumping.
 */
public final class StadiumView extends JPanel {

    static final Color WALKING = new Color(0x7B8794);
    static final Color IN_LANE = new Color(0x2F6FDE);
    static final Color OUTSIDE = new Color(0xE8830C);
    static final Color SCANNING = new Color(0x1E9E5A);
    static final Color GOING_IN = new Color(0xD4A600);

    private static final Color BACKGROUND = new Color(0xEEF1F4);
    private static final Color BOWL_RIM = new Color(0x56616E);
    private static final Color BOWL = new Color(0xB9C1CA);
    private static final Color SEATS = new Color(0xD3D9DF);
    private static final Color CROWD = new Color(0x3D7FE0);
    private static final Color PITCH = new Color(0x5FA46B);
    private static final Color DOOR = new Color(0x2D4A57);

    private static final double SMOOTHING = 0.35;
    private static final double ENTER_SECONDS = 1.3;
    private static final int MAX_WALKING_PER_LINE = 150;
    private static final int MAX_SHARED_WALKING = 400;
    private static final int MAX_BOOTHS_DRAWN = 12;

    private static final class Area {
        final double ox, oy, ux, uy, vx, vy, spacing, width, length;
        final int cols;
        final int maxSlots;

        Area(double ox, double oy, double ux, double uy, double vx, double vy,
             double spacing, double width, double length) {
            this.ox = ox;
            this.oy = oy;
            this.ux = ux;
            this.uy = uy;
            this.vx = vx;
            this.vy = vy;
            this.spacing = spacing;
            this.width = width;
            this.length = length;
            this.cols = Math.max(1, (int) (width / spacing));
            this.maxSlots = cols * Math.max(1, (int) (length / spacing));
        }

        double x(int slot) {
            int s = Math.min(slot, maxSlots - 1);
            return ox + ux * ((s / cols + 0.5) * spacing) + vx * ((s % cols - (cols - 1) / 2.0) * spacing);
        }

        double y(int slot) {
            int s = Math.min(slot, maxSlots - 1);
            return oy + uy * ((s / cols + 0.5) * spacing) + vy * ((s % cols - (cols - 1) / 2.0) * spacing);
        }

        Path2D outline() {
            double hw = width / 2;
            Path2D p = new Path2D.Double();
            p.moveTo(ox - vx * hw, oy - vy * hw);
            p.lineTo(ox + vx * hw, oy + vy * hw);
            p.lineTo(ox + vx * hw + ux * length, oy + vy * hw + uy * length);
            p.lineTo(ox - vx * hw + ux * length, oy - vy * hw + uy * length);
            p.closePath();
            return p;
        }
    }

    private static final class Geo {
        double px, py, nx, ny, tx, ty, laneW, spawnX, spawnY, spread;
        Area lane;
        Area blob;
    }

    private final Ellipse2D.Double dot = new Ellipse2D.Double();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private StadiumSimulation sim;
    private Map<Fan, float[]> positions = new HashMap<>();

    private int layoutKey;
    private double cx;
    private double cy;
    private double radiusA;
    private double radiusB;
    private double scale = 1;
    private Geo[] geo = new Geo[0];
    private Area pen;
    private double penSpawnY;

    public StadiumView() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(820, 700));
        setMinimumSize(new Dimension(400, 360));
    }

    public void setSimulation(StadiumSimulation simulation) {
        this.sim = simulation;
        positions = new HashMap<>();
        layoutKey = 0;
        repaint();
    }

    // ------------------------------------------------------------------ layout

    private void ensureLayout(StadiumConfig cfg, int w, int h) {
        int key = java.util.Objects.hash(w, h, cfg.gates(), cfg.strategy(), cfg.queueCapacity());
        if (key == layoutKey && geo.length == cfg.gates()) {
            return;
        }
        layoutKey = key;
        boolean shared = cfg.strategy() == QueueStrategy.SHARED_QUEUE;
        int gates = cfg.gates();
        scale = Math.max(0.6, Math.min(1.3, Math.min(w, h) / 800.0));

        double gateGap = 8 * scale;
        double laneLen = 76 * scale;
        double blobLen = 44 * scale;
        double walkZone = 24 * scale;
        double reach = shared ? 30 * scale : gateGap + laneLen + blobLen + walkZone;
        double bottomReserve = shared ? 110 * scale : 0;

        cx = w / 2.0;
        cy = (h - bottomReserve) / 2.0;
        radiusA = Math.max(60, w / 2.0 - reach - 8);
        radiusB = Math.max(40, (h - bottomReserve) / 2.0 - reach - 8);
        // in a tall or wide window keep the bowl from stretching into a very long oval
        radiusB = Math.min(radiusB, radiusA * 1.4);
        radiusA = Math.min(radiusA, radiusB * 1.8);

        // Spread the gates evenly by distance along the edge, starting at the top and going clockwise.
        int steps = 2880;
        double[] cumulative = new double[steps + 1];
        double prevX = radiusA * Math.cos(-Math.PI / 2);
        double prevY = radiusB * Math.sin(-Math.PI / 2);
        for (int k = 1; k <= steps; k++) {
            double t = -Math.PI / 2 + 2 * Math.PI * k / steps;
            double x = radiusA * Math.cos(t);
            double y = radiusB * Math.sin(t);
            cumulative[k] = cumulative[k - 1] + Math.hypot(x - prevX, y - prevY);
            prevX = x;
            prevY = y;
        }
        double perimeter = cumulative[steps];
        double gap = perimeter / gates;
        double laneW = Math.max(7, Math.min(38 * scale, gap * 0.82));

        geo = new Geo[gates];
        int capacity = cfg.queueCapacity();
        int k = 0;
        for (int g = 0; g < gates; g++) {
            double target = gap * (g + 0.5);
            while (k < steps && cumulative[k + 1] < target) {
                k++;
            }
            double t = -Math.PI / 2 + 2 * Math.PI * k / steps;
            Geo ge = new Geo();
            ge.px = cx + radiusA * Math.cos(t);
            ge.py = cy + radiusB * Math.sin(t);
            double nx = Math.cos(t) / radiusA;
            double ny = Math.sin(t) / radiusB;
            double len = Math.hypot(nx, ny);
            ge.nx = nx / len;
            ge.ny = ny / len;
            ge.tx = -ge.ny;
            ge.ty = ge.nx;
            ge.laneW = laneW;
            double spacing = Math.max(3.4, Math.min(7.0, Math.sqrt(laneW * laneLen / capacity)));
            ge.lane = new Area(ge.px + ge.nx * gateGap, ge.py + ge.ny * gateGap, ge.nx, ge.ny, ge.tx, ge.ty,
                    spacing, laneW, laneLen);
            double blobStart = gateGap + laneLen + 4 * scale;
            ge.blob = new Area(ge.px + ge.nx * blobStart, ge.py + ge.ny * blobStart, ge.nx, ge.ny, ge.tx, ge.ty,
                    spacing, laneW * 1.7, blobLen);
            double spawnDist = gateGap + laneLen + blobLen + walkZone;
            ge.spawnX = ge.px + ge.nx * spawnDist;
            ge.spawnY = ge.py + ge.ny * spawnDist;
            ge.spread = Math.min(laneW * 3, 90 * scale);
            geo[g] = ge;
        }

        if (shared) {
            double penW = Math.min(w * 0.72, 640);
            double penH = 84 * scale;
            double top = h - bottomReserve + 18 * scale;
            double total = (double) capacity * gates;
            double spacing = Math.max(3.2, Math.min(6.0, Math.sqrt(penW * penH / (total * 1.6))));
            pen = new Area(cx, top, 0, 1, 1, 0, spacing, penW, penH);
            penSpawnY = h + 8;
        } else {
            pen = null;
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, w, h);

        StadiumSimulation s = sim;
        if (s == null) {
            g.dispose();
            return;
        }
        StadiumConfig cfg = s.getConfig();
        ensureLayout(cfg, w, h);
        StadiumSnapshot snap = s.snapshot();
        boolean shared = pen != null;

        drawBowl(g, snap);
        drawLaneBackgrounds(g, s, shared);
        drawGates(g, s, cfg);

        Map<Fan, float[]> next = new HashMap<>(positions.size() * 2 + 16);
        if (shared) {
            drawSharedFans(g, s, next);
        } else {
            drawLineFans(g, s, next);
        }
        drawScanningFans(g, s, next);
        drawEnteringFans(g, s);
        positions = next;

        drawOverlays(g, s, snap, w, h);
        g.dispose();
    }

    private void drawBowl(Graphics2D g, StadiumSnapshot snap) {
        double a = radiusA;
        double b = radiusB;
        g.setColor(BOWL);
        g.fill(new Ellipse2D.Double(cx - a, cy - b, 2 * a, 2 * b));
        g.setColor(SEATS);
        g.fill(new Ellipse2D.Double(cx - a * 0.94, cy - b * 0.94, 2 * a * 0.94, 2 * b * 0.94));

        double filled = snap.maxFans() == 0 ? 0 : (double) snap.admitted() / snap.maxFans();
        int alpha = (int) Math.round(255 * (0.08 + 0.85 * filled));
        g.setColor(new Color(CROWD.getRed(), CROWD.getGreen(), CROWD.getBlue(), Math.min(255, alpha)));
        g.fill(new Ellipse2D.Double(cx - a * 0.94, cy - b * 0.94, 2 * a * 0.94, 2 * b * 0.94));

        g.setColor(BOWL_RIM);
        g.setStroke(new BasicStroke((float) (3 * scale)));
        g.draw(new Ellipse2D.Double(cx - a, cy - b, 2 * a, 2 * b));

        // pitch
        double pw = a * 0.6;
        double ph = b * 0.5;
        g.setColor(PITCH);
        g.fill(new RoundRectangle2D.Double(cx - pw, cy - ph, 2 * pw, 2 * ph, 18, 18));
        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(cx - pw + 6, cy - ph + 6, 2 * pw - 12, 2 * ph - 12, 12, 12));
        g.draw(new java.awt.geom.Line2D.Double(cx, cy - ph + 6, cx, cy + ph - 6));
        double ring = Math.min(pw, ph) * 0.35;
        g.draw(new Ellipse2D.Double(cx - ring, cy - ring, 2 * ring, 2 * ring));
    }

    private void drawLaneBackgrounds(Graphics2D g, StadiumSimulation s, boolean shared) {
        if (shared) {
            Path2D penShape = pen.outline();
            g.setColor(new Color(CROWD.getRed(), CROWD.getGreen(), CROWD.getBlue(), 28));
            g.fill(penShape);
            g.setColor(new Color(CROWD.getRed(), CROWD.getGreen(), CROWD.getBlue(), 120));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(penShape);
            return;
        }
        List<Gate> gates = s.getGates();
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            g.setColor(new Color(CROWD.getRed(), CROWD.getGreen(), CROWD.getBlue(), 26));
            g.fill(ge.lane.outline());
            g.setColor(new Color(CROWD.getRed(), CROWD.getGreen(), CROWD.getBlue(), 90));
            g.setStroke(new BasicStroke(1f));
            g.draw(ge.lane.outline());
            if (gates.get(i).getLine().outsideSize() > 0) {
                g.setColor(new Color(OUTSIDE.getRed(), OUTSIDE.getGreen(), OUTSIDE.getBlue(), 34));
                g.fill(ge.blob.outline());
            }
        }
    }

    private void drawGates(Graphics2D g, StadiumSimulation s, StadiumConfig cfg) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, (int) (11 * scale)));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            double half = ge.laneW / 2;
            g.setColor(DOOR);
            g.setStroke(new BasicStroke((float) (5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(ge.px - ge.tx * half, ge.py - ge.ty * half,
                    ge.px + ge.tx * half, ge.py + ge.ty * half));

            // scanner booths just inside the door: green when the scanner has a fan
            Gate gate = s.getGates().get(i);
            int booths = Math.min(cfg.scannersPerGate(), MAX_BOOTHS_DRAWN);
            double size = Math.max(3, Math.min(8 * scale, ge.laneW / booths * 0.8));
            int lit = cfg.scannersPerGate() <= MAX_BOOTHS_DRAWN ? -1
                    : (int) Math.round((double) gate.busyScanners() / cfg.scannersPerGate() * booths);
            for (int k = 0; k < booths; k++) {
                double bx = boothX(ge, k, booths);
                double by = boothY(ge, k, booths);
                boolean busy = lit >= 0 ? k < lit : gate.getScanners().get(k).getCurrent() != null;
                g.setColor(busy ? SCANNING : new Color(0x9AA5AF));
                rect.setRect(bx - size / 2, by - size / 2, size, size);
                g.fill(rect);
            }

            if (ge.laneW >= 14) {
                String label = String.valueOf(i + 1);
                double lx = ge.px - ge.nx * 22 * scale;
                double ly = ge.py - ge.ny * 22 * scale;
                g.setColor(BOWL_RIM);
                g.drawString(label, (float) (lx - fm.stringWidth(label) / 2.0), (float) (ly + fm.getAscent() / 2.0 - 1));
            }
        }
    }

    private double boothX(Geo ge, int k, int booths) {
        return ge.px - ge.nx * 9 * scale + ge.tx * ((k + 0.5) / booths - 0.5) * ge.laneW;
    }

    private double boothY(Geo ge, int k, int booths) {
        return ge.py - ge.ny * 9 * scale + ge.ty * ((k + 0.5) / booths - 0.5) * ge.laneW;
    }

    private double dotSize(double spacing) {
        return Math.max(2.4, Math.min(5.6, spacing * 0.85)) * Math.min(1.2, Math.max(0.8, scale));
    }

    private void drawFan(Graphics2D g, Map<Fan, float[]> next, Fan fan, double tx, double ty,
                         boolean snap, double size, Color color) {
        if (next.containsKey(fan)) {
            return;
        }
        float[] p = positions.get(fan);
        if (p == null || snap) {
            p = new float[] {(float) tx, (float) ty};
        } else {
            p[0] += (float) ((tx - p[0]) * SMOOTHING);
            p[1] += (float) ((ty - p[1]) * SMOOTHING);
        }
        next.put(fan, p);
        g.setColor(color);
        dot.setFrame(p[0] - size / 2, p[1] - size / 2, size, size);
        g.fill(dot);
    }

    private double walkProgress(Fan fan, double now) {
        double duration = fan.getArriveAt() - fan.getSpawnedAt();
        if (duration <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (now - fan.getSpawnedAt()) / duration));
    }

    private void drawLineFans(Graphics2D g, StadiumSimulation s, Map<Fan, float[]> next) {
        double now = s.getClock().now();
        List<Gate> gates = s.getGates();
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (10 * scale)));
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            FanLine line = gates.get(i).getLine();
            int capacity = line.capacity();
            double size = dotSize(ge.lane.spacing);

            List<Fan> lane = line.laneFans(ge.lane.maxSlots);
            for (int k = 0; k < lane.size(); k++) {
                drawFan(g, next, lane.get(k), ge.lane.x(k), ge.lane.y(k), false, size, IN_LANE);
            }
            List<Fan> outside = line.outsideFans(ge.blob.maxSlots);
            for (int k = 0; k < outside.size(); k++) {
                drawFan(g, next, outside.get(k), ge.blob.x(k), ge.blob.y(k), false, size, OUTSIDE);
            }

            int tail = line.laneSize() + line.outsideSize();
            double tx;
            double ty;
            if (tail < capacity) {
                tx = ge.lane.x(tail);
                ty = ge.lane.y(tail);
            } else {
                tx = ge.blob.x(tail - capacity);
                ty = ge.blob.y(tail - capacity);
            }
            for (Fan fan : line.walkingFans(MAX_WALKING_PER_LINE)) {
                double fx = ge.spawnX + ge.tx * (fan.getSpot() - 0.5) * ge.spread;
                double fy = ge.spawnY + ge.ty * (fan.getSpot() - 0.5) * ge.spread;
                double p = walkProgress(fan, now);
                drawFan(g, next, fan, clampX(fx) + (tx - clampX(fx)) * p, clampY(fy) + (ty - clampY(fy)) * p,
                        true, size, WALKING);
            }

            if (geo.length <= 20) {
                int waiting = line.laneSize() + line.outsideSize();
                int hidden = Math.max(0, line.laneSize() - ge.lane.maxSlots)
                        + Math.max(0, line.outsideSize() - ge.blob.maxSlots);
                if (waiting > 0) {
                    String text = hidden > 0 ? waiting + " (+" + hidden + ")" : String.valueOf(waiting);
                    g.setFont(small);
                    FontMetrics fm = g.getFontMetrics();
                    double d = 10 * scale + ge.lane.length + 4 * scale + ge.blob.length + 11 * scale;
                    double lx = ge.px + ge.nx * d;
                    double ly = ge.py + ge.ny * d;
                    g.setColor(new Color(0x33404B));
                    g.drawString(text, (float) (lx - fm.stringWidth(text) / 2.0), (float) (ly + fm.getAscent() / 2.0));
                }
            }
        }
    }

    private void drawSharedFans(Graphics2D g, StadiumSimulation s, Map<Fan, float[]> next) {
        double now = s.getClock().now();
        FanLine line = s.getLines().get(0);
        int capacity = line.capacity();
        double size = dotSize(pen.spacing);

        List<Fan> lane = line.laneFans(pen.maxSlots);
        for (int k = 0; k < lane.size(); k++) {
            drawFan(g, next, lane.get(k), pen.x(k), pen.y(k), false, size, IN_LANE);
        }
        int offset = capacity;
        List<Fan> outside = line.outsideFans(Math.max(0, pen.maxSlots - offset));
        for (int k = 0; k < outside.size(); k++) {
            drawFan(g, next, outside.get(k), pen.x(offset + k), pen.y(offset + k), false, size, OUTSIDE);
        }

        int tail = line.laneSize() + line.outsideSize();
        double tx = pen.x(tail);
        double ty = pen.y(tail);
        double spread = pen.width * 1.1;
        for (Fan fan : line.walkingFans(MAX_SHARED_WALKING)) {
            double fx = clampX(cx + (fan.getSpot() - 0.5) * spread);
            double fy = penSpawnY;
            double p = walkProgress(fan, now);
            drawFan(g, next, fan, fx + (tx - fx) * p, fy + (ty - fy) * p, true, size, WALKING);
        }
    }

    private void drawScanningFans(Graphics2D g, StadiumSimulation s, Map<Fan, float[]> next) {
        StadiumConfig cfg = s.getConfig();
        List<Gate> gates = s.getGates();
        int booths = Math.min(cfg.scannersPerGate(), MAX_BOOTHS_DRAWN);
        if (cfg.scannersPerGate() > MAX_BOOTHS_DRAWN) {
            return;
        }
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            double size = dotSize(ge.lane.spacing);
            List<GateScanner> scanners = gates.get(i).getScanners();
            for (int k = 0; k < booths; k++) {
                Fan fan = scanners.get(k).getCurrent();
                if (fan != null) {
                    drawFan(g, next, fan, boothX(ge, k, booths), boothY(ge, k, booths), false, size + 0.8, SCANNING);
                }
            }
        }
    }

    private void drawEnteringFans(Graphics2D g, StadiumSimulation s) {
        long window = (long) (ENTER_SECONDS * 1e9);
        long nowNanos = System.nanoTime();
        int drawn = 0;
        for (Fan fan : s.getRecentAdmissions().within(window)) {
            int gi = fan.getServedGate();
            if (gi < 0 || gi >= geo.length || drawn++ > 700) {
                continue;
            }
            Geo ge = geo[gi];
            double age = Math.max(0, Math.min(1, (nowNanos - fan.getAdmittedNanos()) / 1e9 / ENTER_SECONDS));
            double ease = 1 - (1 - age) * (1 - age);
            double[] seat = seatFor(fan);
            double sx = ge.px - ge.nx * 9 * scale;
            double sy = ge.py - ge.ny * 9 * scale;
            double x = sx + (seat[0] - sx) * ease;
            double y = sy + (seat[1] - sy) * ease;
            double fade = age < 0.7 ? 1 : 1 - (age - 0.7) / 0.3;
            g.setColor(new Color(GOING_IN.getRed(), GOING_IN.getGreen(), GOING_IN.getBlue(),
                    (int) Math.round(255 * Math.max(0, fade))));
            double size = 4.2;
            dot.setFrame(x - size / 2, y - size / 2, size, size);
            g.fill(dot);
        }
    }

    /** A seat in the stands, chosen from the fan's number so the same fan always heads to the same seat. */
    private double[] seatFor(Fan fan) {
        long h = fan.getId() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double u1 = (h & 0xFFFFFF) / (double) 0x1000000;
        double u2 = ((h >>> 24) & 0xFFFFFF) / (double) 0x1000000;
        double t = u1 * 2 * Math.PI;
        double c = Math.cos(t);
        double sn = Math.sin(t);
        double edge = Math.min(Math.abs(c) < 1e-6 ? 99 : 0.62 / Math.abs(c), Math.abs(sn) < 1e-6 ? 99 : 0.52 / Math.abs(sn)) + 0.03;
        double r = edge + (0.91 - edge) * u2;
        return new double[] {cx + radiusA * r * c, cy + radiusB * r * sn};
    }

    private double clampX(double x) {
        return Math.max(5, Math.min(getWidth() - 5, x));
    }

    private double clampY(double y) {
        return Math.max(5, Math.min(getHeight() - 5, y));
    }

    private void drawOverlays(Graphics2D g, StadiumSimulation s, StadiumSnapshot snap, int w, int h) {
        // center of the pitch
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, (int) (26 * scale)));
        Font mid = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, (int) (13 * scale)));
        g.setColor(Color.WHITE);
        g.setFont(big);
        String inside = String.format("%,d / %,d", snap.admitted(), snap.totalFans());
        FontMetrics fm = g.getFontMetrics();
        g.drawString(inside, (float) (cx - fm.stringWidth(inside) / 2.0), (float) (cy - 2));
        g.setFont(mid);
        fm = g.getFontMetrics();
        String line2 = "fans inside   |   " + clock(snap.simSeconds());
        g.drawString(line2, (float) (cx - fm.stringWidth(line2) / 2.0), (float) (cy + fm.getAscent() + 4));

        if (snap.state() == StadiumSimulation.State.NEW) {
            String hint = "Press Start";
            g.setFont(mid);
            fm = g.getFontMetrics();
            g.drawString(hint, (float) (cx - fm.stringWidth(hint) / 2.0), (float) (cy + fm.getAscent() * 2 + 10));
        } else if (snap.state() == StadiumSimulation.State.PAUSED) {
            g.setFont(mid);
            fm = g.getFontMetrics();
            String hint = "Paused";
            g.drawString(hint, (float) (cx - fm.stringWidth(hint) / 2.0), (float) (cy + fm.getAscent() * 2 + 10));
        } else if (snap.state() == StadiumSimulation.State.FINISHED) {
            g.setFont(mid);
            fm = g.getFontMetrics();
            String hint = "Everyone is inside";
            g.drawString(hint, (float) (cx - fm.stringWidth(hint) / 2.0), (float) (cy + fm.getAscent() * 2 + 10));
        }

        if (pen != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (11 * scale))));
            g.setColor(new Color(0x33404B));
            String label = "Shared line: every gate's scanners call the next fan from here";
            FontMetrics f2 = g.getFontMetrics();
            g.drawString(label, (float) (cx - f2.stringWidth(label) / 2.0), (float) (pen.oy - 5));
            int hidden = Math.max(0, snap.waiting() - pen.maxSlots);
            if (hidden > 0) {
                String more = "+" + hidden + " more not drawn";
                g.drawString(more, (float) (cx + pen.width / 2 - f2.stringWidth(more)), (float) (pen.oy + pen.length + 14));
            }
        }

        // legend, top left
        Font legend = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g.setFont(legend);
        String[] names = {"Walking to the gate", "In the lane", "Waiting outside the lane (lane full)",
                "Being scanned", "Going in to a seat"};
        Color[] colors = {WALKING, IN_LANE, OUTSIDE, SCANNING, GOING_IN};
        g.setColor(new Color(255, 255, 255, 215));
        g.fill(new RoundRectangle2D.Double(8, 8, 244, 14 + names.length * 18, 10, 10));
        for (int i = 0; i < names.length; i++) {
            g.setColor(colors[i]);
            dot.setFrame(18, 20 + i * 18 - 4, 9, 9);
            g.fill(dot);
            g.setColor(new Color(0x26323C));
            g.drawString(names[i], 34, 20 + i * 18 + 5);
        }

        // top right: how fans pick a line
        g.setFont(legend);
        g.setColor(new Color(0x26323C));
        String info = s.getConfig().strategy() + "  |  " + s.getConfig().shape();
        fm = g.getFontMetrics();
        g.drawString(info, w - fm.stringWidth(info) - 12, 22);
    }

    static String clock(double simSeconds) {
        int total = (int) Math.round(simSeconds);
        return String.format("%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
    }
}
