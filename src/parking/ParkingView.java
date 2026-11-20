package parking;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The top-down picture of the parking at the venue. It only reads the simulation, it never
 * changes it. There are two layouts, chosen with ParkingConfig.layout. They only change the
 * picture: in both, the lots are numbered from the one nearest the venue, and cars fill each lot
 * starting at the spaces closest to the venue.
 *
 * SURROUNDING looks like MetLife Stadium. The stadium is in the middle with a plaza and a ring
 * road around it, and one large lot fills the ground around it, split into the lots A, B, C and
 * so on. Cars arrive from the highway at the edge of the picture and queue on an access road to
 * their lot's entrance.
 *
 * BEHIND_VENUE looks like Jones Beach Theater. The seating fans out from a stage on the water, and
 * one big lot lies behind it, split into side by side strips. The lot closest to the middle is A.
 * Cars come in from the highway along the top.
 *
 * At the entrance attendants take the payment, then the car parks and the fans walk to the venue.
 * A lot with thousands of spaces cannot draw one square per space, so a square can stand for
 * several spaces.
 */
public final class ParkingView extends JPanel {

    static final Color DRIVING = new Color(0x7B8794);
    static final Color IN_LANE = new Color(0x2F6FDE);
    static final Color STREET = new Color(0xE8830C);
    static final Color PAYING = new Color(0x1E9E5A);
    static final Color PARKED = new Color(0x2A8C8C);
    static final Color WALKING = new Color(0xD4A600);
    static final Color TURNED_AWAY = new Color(0xC62828);

    private static final Color BACKGROUND = new Color(0xE6EAEE);
    private static final Color LOT_A = new Color(0xC6CCD3);
    private static final Color LOT_B = new Color(0xBEC5CD);
    private static final Color LOT_EDGE = new Color(0x8D97A2);
    private static final Color EMPTY_SPACE = new Color(0xDDE2E7);
    private static final Color PLAZA = new Color(0xE3DFD3);
    private static final Color CONCOURSE = new Color(0xD3CDBD);
    private static final Color ROAD = new Color(0x7D8791);
    private static final Color ACCESS_ROAD = new Color(0xB3BBC4);
    private static final Color BOWL_RIM = new Color(0x56616E);
    private static final Color BOWL = new Color(0xAEB8C2);
    private static final Color SEATS = new Color(0x4A5A70);
    private static final Color PITCH = new Color(0x5FA46B);
    private static final Color BAR = new Color(0x2D4A57);
    private static final Color WATER = new Color(0x86B5D6);
    private static final Color RED_SEATS = new Color(0xA9454B);
    private static final Color RED_SEATS_DARK = new Color(0x7F3035);
    private static final Color[] CAR_COLORS = {
            new Color(0x2A8C8C), new Color(0x3AA0A0), new Color(0x23797C), new Color(0x4BB0AA)};

    private static final double SMOOTHING = 0.35;
    private static final double PARK_SECONDS = 0.9;
    private static final double AWAY_SECONDS = 1.1;
    private static final int MAX_DRIVING_PER_LINE = 120;
    private static final int MAX_BOOTHS_DRAWN = 8;

    /** A block of slots laid out in rows, used for the cars in a lane and the cars on the street. */
    private static final class Area2 {
        final double ox, oy, ux, uy, vx, vy, spacing;
        final int cols;
        final int maxSlots;

        Area2(double ox, double oy, double ux, double uy, double vx, double vy,
              double spacing, double width, double length) {
            this.ox = ox;
            this.oy = oy;
            this.ux = ux;
            this.uy = uy;
            this.vx = vx;
            this.vy = vy;
            this.spacing = spacing;
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
    }

    private static final class Geo {
        // the entrance is on the outer edge of the lot, n points away from the venue
        double ex, ey, nx, ny, tx, ty;
        double laneW, laneLen, blobW, blobLen, approach;
        double spawnX, spawnY, spread;
        double labelX, labelY;
        Area2 lane;
        Area2 blob;
        // the parking spaces, closest to the venue first
        float[] cellX = new float[0];
        float[] cellY = new float[0];
    }

    private final Ellipse2D.Double dot = new Ellipse2D.Double();
    private final Rectangle2D.Double rect = new Rectangle2D.Double();
    private ParkingSimulation sim;
    private Map<Car, float[]> positions = new HashMap<>();

    private int layoutKey;
    private boolean behind;
    private double scale = 1;
    private double cellSize = 6;
    private Geo[] geo = new Geo[0];
    private BufferedImage background;

    // the field of parking
    private double fieldX, fieldY, fieldW, fieldH;
    // SURROUNDING: the stadium and the lots around it
    private double cx, cy, radiusA, radiusB, ringA, ringB, plazaW, roadW;
    private double firstAngle;
    private double[] sweep = new double[0];
    // BEHIND_VENUE: the stage, the seating and the strips
    private double stageX, stageY, seatIn, seatOut, stripW;
    private int[] stripOfLot = new int[0];
    // where the counter of parked cars is drawn
    private double counterX, counterY;

    public ParkingView() {
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(820, 700));
        setMinimumSize(new Dimension(400, 360));
    }

    public void setSimulation(ParkingSimulation simulation) {
        this.sim = simulation;
        positions = new HashMap<>();
        layoutKey = 0;
        repaint();
    }

    // ------------------------------------------------------------------ layout

    /** A point on the edge of the field, measured clockwise from the middle of the top edge. */
    private static double[] perimeterPoint(double s, double x0, double y0, double w, double h) {
        double perimeter = 2 * (w + h);
        double d = ((s % perimeter) + perimeter) % perimeter;
        if (d < w / 2) {
            return new double[] {x0 + w / 2 + d, y0, 0, -1};
        }
        d -= w / 2;
        if (d < h) {
            return new double[] {x0 + w, y0 + d, 1, 0};
        }
        d -= h;
        if (d < w) {
            return new double[] {x0 + w - d, y0 + h, 0, 1};
        }
        d -= w;
        if (d < h) {
            return new double[] {x0, y0 + h - d, -1, 0};
        }
        d -= h;
        return new double[] {x0 + d, y0, 0, -1};
    }

    /** Point on an ellipse around the stadium centre, in the direction of the given screen angle. */
    private double[] ellipsePoint(double theta, double a, double b) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);
        double r = 1 / Math.sqrt((c / a) * (c / a) + (s / b) * (s / b));
        return new double[] {cx + r * c, cy + r * s};
    }

    private static double mod2pi(double angle) {
        double full = 2 * Math.PI;
        return ((angle % full) + full) % full;
    }

    private Geo newGeo(ParkingConfig cfg, double ex, double ey, double nx, double ny, double laneW) {
        double approach = 4 * scale;
        double laneLen = 52 * scale;
        double blobLen = 34 * scale;
        double driveZone = 24 * scale;
        Geo ge = new Geo();
        ge.ex = ex;
        ge.ey = ey;
        ge.nx = nx;
        ge.ny = ny;
        ge.tx = -ny;
        ge.ty = nx;
        ge.laneW = laneW;
        ge.laneLen = laneLen;
        ge.blobW = laneW * 1.8;
        ge.blobLen = blobLen;
        ge.approach = approach;
        double spacing = Math.max(4.2, Math.min(7.5, Math.sqrt(laneW * laneLen / cfg.queueCapacity())));
        ge.lane = new Area2(ex + nx * approach, ey + ny * approach, nx, ny, ge.tx, ge.ty, spacing, laneW, laneLen);
        double blobStart = approach * 2 + laneLen;
        ge.blob = new Area2(ex + nx * blobStart, ey + ny * blobStart, nx, ny, ge.tx, ge.ty, spacing, ge.blobW, blobLen);
        double spawnDist = blobStart + blobLen + driveZone;
        ge.spawnX = ex + nx * spawnDist;
        ge.spawnY = ey + ny * spawnDist;
        ge.spread = Math.min(laneW * 3, 90 * scale);
        return ge;
    }

    /**
     * The size of one square of the grid. With few spaces the squares are big, one per space. With
     * thousands of spaces they get small and one square stands for several.
     */
    private double chooseCellSize(ParkingConfig cfg, double freeArea) {
        double target = Math.min((double) cfg.spacesPerLot() * cfg.lots(), 9000);
        return Math.max(5 * scale, Math.min(30 * scale, Math.sqrt(freeArea / target)));
    }

    /**
     * Stores a lot's spaces, sorted so the ones closest to the venue come first. A lot with fewer
     * spaces than the ground it has keeps only the squares closest to the venue.
     */
    private static void setCells(Geo ge, List<float[]> list, int spaces) {
        list.sort(Comparator.comparingDouble(c -> c[2]));
        if (list.size() > spaces) {
            list = list.subList(0, spaces);
        }
        ge.cellX = new float[list.size()];
        ge.cellY = new float[list.size()];
        double sumX = 0;
        double sumY = 0;
        for (int k = 0; k < list.size(); k++) {
            ge.cellX[k] = list.get(k)[0];
            ge.cellY[k] = list.get(k)[1];
            sumX += ge.cellX[k];
            sumY += ge.cellY[k];
        }
        if (list.isEmpty()) {
            ge.cellX = new float[] {(float) ge.ex};
            ge.cellY = new float[] {(float) ge.ey};
            ge.labelX = ge.ex;
            ge.labelY = ge.ey;
        } else {
            ge.labelX = sumX / list.size();
            ge.labelY = sumY / list.size();
        }
    }

    private void ensureLayout(ParkingConfig cfg, int w, int h) {
        int key = Objects.hash(w, h, cfg.lots(), cfg.queueCapacity(), cfg.layout());
        if (key == layoutKey && geo.length == cfg.lots() && background != null) {
            return;
        }
        layoutKey = key;
        behind = cfg.layout() == ParkingLayout.BEHIND_VENUE;
        scale = Math.max(0.6, Math.min(1.3, Math.min(w, h) / 800.0));
        if (behind) {
            buildBehind(cfg, w, h);
        } else {
            buildSurrounding(cfg, w, h);
        }
        background = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = background.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(BACKGROUND);
        bg.fillRect(0, 0, w, h);
        drawAccessRoads(bg, w, h);
        if (behind) {
            drawBackgroundBehind(bg, cfg, w, h);
        } else {
            drawBackgroundSurrounding(bg, cfg, w, h);
        }
        bg.dispose();
    }

    private double margin(int w, int h) {
        double needed = (4 * 2 + 52 + 34 + 24 + 6) * scale;
        return Math.min(needed, Math.min(w, h) * 0.22);
    }

    private void buildSurrounding(ParkingConfig cfg, int w, int h) {
        int lots = cfg.lots();
        double margin = margin(w, h);
        fieldX = margin;
        fieldY = margin;
        fieldW = w - 2 * margin;
        fieldH = h - 2 * margin;
        cx = w / 2.0;
        cy = h / 2.0;

        plazaW = 12 * scale;
        roadW = 15 * scale;
        radiusA = Math.max(30, fieldW * 0.24);
        radiusB = Math.max(24, fieldH * 0.23);
        // in a tall or wide window keep the stadium from stretching into a very long oval
        radiusB = Math.min(radiusB, radiusA * 1.4);
        radiusA = Math.min(radiusA, radiusB * 1.8);
        ringA = radiusA + plazaW + roadW;
        ringB = radiusB + plazaW + roadW;

        // Split the lot into sectors. Lot g is centred on the edge of the field at perimeter * g / lots.
        double perimeter = 2 * (fieldW + fieldH);
        double[] boundaryAngle = new double[lots + 1];
        for (int k = 0; k <= lots; k++) {
            double[] p = perimeterPoint(perimeter * (k - 0.5) / lots, fieldX, fieldY, fieldW, fieldH);
            boundaryAngle[k] = Math.atan2(p[1] - cy, p[0] - cx);
        }
        firstAngle = boundaryAngle[0];
        sweep = new double[lots + 1];
        for (int k = 1; k <= lots; k++) {
            sweep[k] = mod2pi(boundaryAngle[k] - firstAngle);
        }
        sweep[lots] = 2 * Math.PI;

        // parking spaces: a grid over the field, each square belongs to one lot
        cellSize = chooseCellSize(cfg, fieldW * fieldH - Math.PI * ringA * ringB);
        List<List<float[]>> cells = new ArrayList<>();
        for (int g = 0; g < lots; g++) {
            cells.add(new ArrayList<>());
        }
        for (double y = fieldY + cellSize * 0.75; y < fieldY + fieldH - cellSize * 0.4; y += cellSize) {
            for (double x = fieldX + cellSize * 0.75; x < fieldX + fieldW - cellSize * 0.4; x += cellSize) {
                double dx = x - cx;
                double dy = y - cy;
                double angle = mod2pi(Math.atan2(dy, dx) - firstAngle);
                int g = 0;
                while (g < lots - 1 && angle >= sweep[g + 1]) {
                    g++;
                }
                double inner = Math.hypot(dx / ringA, dy / ringB);
                if (inner < 1.0) {
                    continue;
                }
                cells.get(g).add(new float[] {(float) x, (float) y, (float) inner});
            }
        }

        double laneW = Math.max(8, Math.min(perimeter / lots * 0.4, 24 * scale));
        geo = new Geo[lots];
        for (int g = 0; g < lots; g++) {
            double[] e = perimeterPoint(perimeter * g / lots, fieldX, fieldY, fieldW, fieldH);
            geo[g] = newGeo(cfg, e[0], e[1], e[2], e[3], laneW);
            setCells(geo[g], cells.get(g), cfg.spacesPerLot());
        }
        counterX = cx;
        counterY = cy;
    }

    private void buildBehind(ParkingConfig cfg, int w, int h) {
        int lots = cfg.lots();
        double margin = margin(w, h);
        double side = 14 * scale;
        double venueH = Math.max(150 * scale, h * 0.38);
        fieldX = side;
        fieldY = margin;
        fieldW = w - 2 * side;
        fieldH = Math.max(80, h - margin - venueH);
        double fieldBottom = fieldY + fieldH;

        plazaW = 14 * scale;
        stageX = w / 2.0;
        stageY = h - venueH * 0.2;
        seatOut = Math.min(stageY - fieldBottom - plazaW, w / 2.0 - 10);
        seatOut = Math.max(40, seatOut);
        seatIn = seatOut * 0.42;

        // Strips run side by side. The strip in the middle is lot A and the lots go outward from it.
        stripW = fieldW / lots;
        Integer[] order = new Integer[lots];
        for (int s = 0; s < lots; s++) {
            order[s] = s;
        }
        Arrays.sort(order, Comparator.<Integer>comparingDouble(s -> Math.abs(fieldX + (s + 0.5) * stripW - stageX))
                .thenComparing(Comparator.<Integer>naturalOrder().reversed()));
        stripOfLot = new int[lots];
        int[] lotOfStrip = new int[lots];
        for (int g = 0; g < lots; g++) {
            stripOfLot[g] = order[g];
            lotOfStrip[order[g]] = g;
        }

        cellSize = chooseCellSize(cfg, fieldW * fieldH);
        List<List<float[]>> cells = new ArrayList<>();
        for (int g = 0; g < lots; g++) {
            cells.add(new ArrayList<>());
        }
        for (double y = fieldY + cellSize * 0.75; y < fieldBottom - cellSize * 0.4; y += cellSize) {
            for (double x = fieldX + cellSize * 0.75; x < fieldX + fieldW - cellSize * 0.4; x += cellSize) {
                int strip = Math.max(0, Math.min(lots - 1, (int) ((x - fieldX) / stripW)));
                double distance = Math.hypot(x - stageX, y - stageY);
                cells.get(lotOfStrip[strip]).add(new float[] {(float) x, (float) y, (float) distance});
            }
        }

        double laneW = Math.max(8, Math.min(stripW * 0.5, 24 * scale));
        geo = new Geo[lots];
        for (int g = 0; g < lots; g++) {
            double x = fieldX + (stripOfLot[g] + 0.5) * stripW;
            geo[g] = newGeo(cfg, x, fieldY, 0, -1, laneW);
            setCells(geo[g], cells.get(g), cfg.spacesPerLot());
        }
        counterX = w / 2.0;
        counterY = h - 18 * scale;
    }

    // ------------------------------------------------------------------ the parts that never change

    /** The roads from the highway at the edge of the picture to each lot entrance. */
    private void drawAccessRoads(Graphics2D g, int w, int h) {
        for (Geo ge : geo) {
            double edge = ge.nx != 0 ? (ge.nx > 0 ? w - ge.ex : ge.ex) : (ge.ny > 0 ? h - ge.ey : ge.ey);
            double half = ge.laneW / 2 + 3;
            Path2D road = new Path2D.Double();
            road.moveTo(ge.ex - ge.tx * half, ge.ey - ge.ty * half);
            road.lineTo(ge.ex + ge.tx * half, ge.ey + ge.ty * half);
            road.lineTo(ge.ex + ge.tx * half + ge.nx * edge, ge.ey + ge.ty * half + ge.ny * edge);
            road.lineTo(ge.ex - ge.tx * half + ge.nx * edge, ge.ey - ge.ty * half + ge.ny * edge);
            road.closePath();
            g.setColor(ACCESS_ROAD);
            g.fill(road);
        }
    }

    private void drawEmptySpaces(Graphics2D g, Geo ge) {
        double size = cellSize * 0.78;
        g.setColor(EMPTY_SPACE);
        for (int k = 0; k < ge.cellX.length; k++) {
            rect.setRect(ge.cellX[k] - size / 2, ge.cellY[k] - size / 2, size, size);
            g.fill(rect);
        }
    }

    private void drawBackgroundSurrounding(Graphics2D g, ParkingConfig cfg, int w, int h) {
        int lots = cfg.lots();
        double perimeter = 2 * (fieldW + fieldH);
        for (int g0 = 0; g0 < lots; g0++) {
            Path2D shape = new Path2D.Double();
            double s0 = perimeter * (g0 - 0.5) / lots;
            double s1 = perimeter * (g0 + 0.5) / lots;
            List<Double> samples = new ArrayList<>();
            for (double s = s0; s < s1; s += 6) {
                samples.add(s);
            }
            samples.add(s1);
            double[] corners = {fieldW / 2, fieldW / 2 + fieldH, fieldW / 2 + fieldH + fieldW,
                    fieldW / 2 + 2 * fieldH + fieldW};
            for (double corner : corners) {
                for (int shift = -1; shift <= 1; shift++) {
                    double s = corner + shift * perimeter;
                    if (s > s0 && s < s1) {
                        samples.add(s);
                    }
                }
            }
            samples.sort(Comparator.naturalOrder());
            boolean first = true;
            for (double s : samples) {
                double[] p = perimeterPoint(s, fieldX, fieldY, fieldW, fieldH);
                if (first) {
                    shape.moveTo(p[0], p[1]);
                    first = false;
                } else {
                    shape.lineTo(p[0], p[1]);
                }
            }
            double a0 = firstAngle + sweep[g0];
            double a1 = firstAngle + sweep[g0 + 1];
            int steps = Math.max(8, (int) ((a1 - a0) / 0.03));
            for (int k = steps; k >= 0; k--) {
                double[] p = ellipsePoint(a0 + (a1 - a0) * k / steps, ringA, ringB);
                shape.lineTo(p[0], p[1]);
            }
            shape.closePath();
            g.setColor(g0 % 2 == 0 ? LOT_A : LOT_B);
            g.fill(shape);
            drawEmptySpaces(g, geo[g0]);
            g.setColor(LOT_EDGE);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(shape);
        }

        // the plaza and the ring road around the stadium
        g.setColor(PLAZA);
        g.fill(new Ellipse2D.Double(cx - ringA, cy - ringB, 2 * ringA, 2 * ringB));
        double midA = radiusA + plazaW + roadW / 2;
        double midB = radiusB + plazaW + roadW / 2;
        g.setColor(ROAD);
        g.setStroke(new BasicStroke((float) roadW));
        g.draw(new Ellipse2D.Double(cx - midA, cy - midB, 2 * midA, 2 * midB));
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {7f, 6f}, 0f));
        g.draw(new Ellipse2D.Double(cx - midA, cy - midB, 2 * midA, 2 * midB));

        // the stadium
        double a = radiusA;
        double b = radiusB;
        g.setColor(BOWL);
        g.fill(new Ellipse2D.Double(cx - a, cy - b, 2 * a, 2 * b));
        g.setColor(SEATS);
        g.fill(new Ellipse2D.Double(cx - a * 0.9, cy - b * 0.9, 2 * a * 0.9, 2 * b * 0.9));
        g.setColor(BOWL_RIM);
        g.setStroke(new BasicStroke((float) (3 * scale)));
        g.draw(new Ellipse2D.Double(cx - a, cy - b, 2 * a, 2 * b));
        double pw = a * 0.58;
        double ph = b * 0.5;
        g.setColor(PITCH);
        g.fill(new RoundRectangle2D.Double(cx - pw, cy - ph, 2 * pw, 2 * ph, 14, 14));
        g.setColor(new Color(255, 255, 255, 190));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Double(cx - pw + 5, cy - ph + 5, 2 * pw - 10, 2 * ph - 10, 10, 10));
        g.draw(new Line2D.Double(cx, cy - ph + 5, cx, cy + ph - 5));
    }

    private void drawBackgroundBehind(Graphics2D g, ParkingConfig cfg, int w, int h) {
        int lots = cfg.lots();
        double fieldBottom = fieldY + fieldH;

        // the ground behind the seating, then the water
        g.setColor(PLAZA);
        g.fill(new Rectangle2D.Double(0, fieldBottom, w, h - fieldBottom));
        double waterTop = stageY - seatIn * 0.45;
        g.setColor(WATER);
        g.fill(new Rectangle2D.Double(0, waterTop, w, h - waterTop));

        // the big lot, split into strips
        for (int g0 = 0; g0 < lots; g0++) {
            double x0 = fieldX + stripOfLot[g0] * stripW;
            g.setColor(g0 % 2 == 0 ? LOT_A : LOT_B);
            g.fill(new Rectangle2D.Double(x0, fieldY, stripW, fieldH));
            drawEmptySpaces(g, geo[g0]);
            g.setColor(LOT_EDGE);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Rectangle2D.Double(x0, fieldY, stripW, fieldH));
        }

        // the promenade, then the seating in rows, fanning out from the stage
        Area promenade = new Area(new Ellipse2D.Double(stageX - seatOut - plazaW, stageY - seatOut - plazaW,
                2 * (seatOut + plazaW), 2 * (seatOut + plazaW)));
        promenade.intersect(new Area(new Rectangle2D.Double(0, 0, w, stageY)));
        g.setColor(CONCOURSE);
        g.fill(promenade);

        Area seats = new Area(new Ellipse2D.Double(stageX - seatOut, stageY - seatOut, 2 * seatOut, 2 * seatOut));
        seats.subtract(new Area(new Ellipse2D.Double(stageX - seatIn, stageY - seatIn, 2 * seatIn, 2 * seatIn)));
        seats.intersect(new Area(new Rectangle2D.Double(0, 0, w, stageY)));
        g.setColor(RED_SEATS);
        g.fill(seats);
        g.setColor(RED_SEATS_DARK);
        g.setStroke(new BasicStroke(1f));
        int rows = 10;
        for (int r = 0; r <= rows; r++) {
            double radius = seatIn + (seatOut - seatIn) * r / rows;
            g.draw(new Arc2D.Double(stageX - radius, stageY - radius, 2 * radius, 2 * radius, 0, 180, Arc2D.OPEN));
        }
        for (int deg = 0; deg <= 180; deg += 12) {
            double rad = Math.toRadians(deg);
            g.draw(new Line2D.Double(stageX + Math.cos(rad) * seatIn, stageY - Math.sin(rad) * seatIn,
                    stageX + Math.cos(rad) * seatOut, stageY - Math.sin(rad) * seatOut));
        }
        g.setColor(BOWL_RIM);
        g.setStroke(new BasicStroke((float) (2 * scale)));
        g.draw(new Arc2D.Double(stageX - seatOut, stageY - seatOut, 2 * seatOut, 2 * seatOut, 0, 180, Arc2D.OPEN));

        // the stage on the water
        double platformW = seatIn * 1.7;
        double platformH = seatIn * 1.0;
        g.setColor(new Color(0xB8B6AD));
        g.fill(new RoundRectangle2D.Double(stageX - platformW / 2, stageY - platformH * 0.75, platformW, platformH, 18, 18));
        double houseW = seatIn * 1.25;
        double houseH = seatIn * 0.62;
        g.setColor(new Color(0x2F56A5));
        g.fill(new RoundRectangle2D.Double(stageX - houseW / 2, stageY - platformH * 0.6, houseW, houseH, 8, 8));
        g.setColor(new Color(0xC9D3DE));
        g.fill(new RoundRectangle2D.Double(stageX - houseW / 2 - 4, stageY - platformH * 0.6 - houseH * 0.3, houseW + 8, houseH * 0.45, 12, 12));
    }

    // ------------------------------------------------------------------ geometry helpers for the moving parts

    private double boothX(Geo ge, int k, int booths) {
        return ge.ex - ge.nx * 8 * scale + ge.tx * ((k + 0.5) / booths - 0.5) * ge.laneW;
    }

    private double boothY(Geo ge, int k, int booths) {
        return ge.ey - ge.ny * 8 * scale + ge.ty * ((k + 0.5) / booths - 0.5) * ge.laneW;
    }

    /** The grid square a space number belongs to: a lot with more spaces than squares uses one square for several. */
    private static int cellFor(Geo ge, int space, int spaces) {
        int cells = ge.cellX.length;
        return Math.max(0, Math.min(cells - 1, (int) ((long) space * cells / spaces)));
    }

    /** Where a fan who parked at (x, y) enters the venue: on the edge of the stadium, or on the seating. */
    private double[] venueEntrance(double x, double y) {
        if (behind) {
            double dx = x - stageX;
            double dy = y - stageY;
            double length = Math.max(1e-6, Math.hypot(dx, dy));
            return new double[] {stageX + dx / length * seatOut, stageY + dy / length * seatOut};
        }
        double dx = x - cx;
        double dy = y - cy;
        double rim = 1 / Math.sqrt((dx / radiusA) * (dx / radiusA) + (dy / radiusB) * (dy / radiusB));
        return new double[] {cx + dx * rim, cy + dy * rim};
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

        ParkingSimulation s = sim;
        if (s == null) {
            g.dispose();
            return;
        }
        ParkingConfig cfg = s.getConfig();
        ensureLayout(cfg, w, h);
        g.drawImage(background, 0, 0, null);
        ParkingSnapshot snap = s.snapshot();
        double now = s.getClock().now();

        drawLots(g, s, snap);

        Map<Car, float[]> next = new HashMap<>(positions.size() * 2 + 16);
        drawLineCars(g, s, next);
        drawBoothCars(g, s, next);
        drawFinishedCars(g, s, now);
        positions = next;

        drawOverlays(g, s, snap, w);
        g.dispose();
    }

    private void drawLots(Graphics2D g, ParkingSimulation s, ParkingSnapshot snap) {
        ParkingConfig cfg = s.getConfig();
        Font label = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, (int) (13 * scale)));
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (11 * scale)));
        double size = cellSize * 0.78;
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            ParkingSnapshot.LotSnapshot ls = snap.lots().get(i);
            int cells = ge.cellX.length;
            int lit = (int) Math.min(cells, Math.ceil((double) ls.parked() * cells / ls.spaces()));

            // parked cars fill the lot starting next to the venue
            for (int shade = 0; shade < CAR_COLORS.length; shade++) {
                g.setColor(CAR_COLORS[shade]);
                for (int k = (shade + i) % CAR_COLORS.length; k < lit; k += CAR_COLORS.length) {
                    rect.setRect(ge.cellX[k] - size / 2, ge.cellY[k] - size / 2, size, size);
                    g.fill(rect);
                }
            }

            if (ls.outside() > 0) {
                g.setColor(new Color(STREET.getRed(), STREET.getGreen(), STREET.getBlue(), 40));
                double bx = ge.ex + ge.nx * (ge.approach * 2 + ge.laneLen);
                double by = ge.ey + ge.ny * (ge.approach * 2 + ge.laneLen);
                Path2D blob = new Path2D.Double();
                double half = ge.blobW / 2;
                blob.moveTo(bx - ge.tx * half, by - ge.ty * half);
                blob.lineTo(bx + ge.tx * half, by + ge.ty * half);
                blob.lineTo(bx + ge.tx * half + ge.nx * ge.blobLen, by + ge.ty * half + ge.ny * ge.blobLen);
                blob.lineTo(bx - ge.tx * half + ge.nx * ge.blobLen, by - ge.ty * half + ge.ny * ge.blobLen);
                blob.closePath();
                g.fill(blob);
            }

            // the barrier across the entrance: red once the lot is full
            double half = ge.laneW / 2;
            g.setColor(ls.free() == 0 ? TURNED_AWAY : BAR);
            g.setStroke(new BasicStroke((float) (4 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(ge.ex - ge.tx * half, ge.ey - ge.ty * half, ge.ex + ge.tx * half, ge.ey + ge.ty * half));

            // payment booths just inside the entrance: green when the attendant has a car
            Lot lot = s.getLots().get(i);
            int booths = Math.min(cfg.attendantsPerLot(), MAX_BOOTHS_DRAWN);
            double boothSize = Math.max(3, Math.min(7 * scale, ge.laneW / booths * 0.8));
            int lit2 = cfg.attendantsPerLot() <= MAX_BOOTHS_DRAWN ? -1
                    : (int) Math.round((double) ls.busyAttendants() / cfg.attendantsPerLot() * booths);
            for (int k = 0; k < booths; k++) {
                boolean busy = lit2 >= 0 ? k < lit2 : lot.getAttendants().get(k).getCurrent() != null;
                g.setColor(busy ? PAYING : new Color(0x9AA5AF));
                rect.setRect(boothX(ge, k, booths) - boothSize / 2, boothY(ge, k, booths) - boothSize / 2,
                        boothSize, boothSize);
                g.fill(rect);
            }

            drawLotLabel(g, ls, ge, label, small, cells);
        }
        drawCounter(g, snap);
    }

    private void drawLotLabel(Graphics2D g, ParkingSnapshot.LotSnapshot ls, Geo ge, Font label, Font small, int cells) {
        String name = ls.name();
        String letter = name.replace("Lot ", "");
        if (cells >= 160 && geo.length <= 12) {
            String detail = ls.parked() + "/" + ls.spaces();
            String walk = String.format("walk %.1f min", ls.walkSeconds() / 60.0);
            g.setFont(label);
            FontMetrics fm = g.getFontMetrics();
            g.setFont(small);
            FontMetrics sm = g.getFontMetrics();
            double boxW = Math.max(fm.stringWidth(name), Math.max(sm.stringWidth(detail), sm.stringWidth(walk))) + 12;
            double boxH = fm.getHeight() + sm.getHeight() * 2 + 4;
            g.setColor(new Color(255, 255, 255, 225));
            g.fill(new RoundRectangle2D.Double(ge.labelX - boxW / 2, ge.labelY - boxH / 2, boxW, boxH, 8, 8));
            g.setColor(new Color(0x26323C));
            g.setFont(label);
            double top = ge.labelY - boxH / 2;
            g.drawString(name, (float) (ge.labelX - fm.stringWidth(name) / 2.0), (float) (top + fm.getAscent() + 1));
            g.setFont(small);
            g.drawString(detail, (float) (ge.labelX - sm.stringWidth(detail) / 2.0),
                    (float) (top + fm.getHeight() + sm.getAscent() + 1));
            g.setColor(new Color(0x5B6670));
            g.drawString(walk, (float) (ge.labelX - sm.stringWidth(walk) / 2.0),
                    (float) (top + fm.getHeight() + sm.getHeight() + sm.getAscent() + 2));
        } else if (cells >= 30) {
            g.setFont(label);
            FontMetrics fm = g.getFontMetrics();
            double boxW = fm.stringWidth(letter) + 7;
            g.setColor(new Color(255, 255, 255, 225));
            g.fill(new RoundRectangle2D.Double(ge.labelX - boxW / 2, ge.labelY - fm.getHeight() / 2.0, boxW, fm.getHeight(), 6, 6));
            g.setColor(new Color(0x26323C));
            g.drawString(letter, (float) (ge.labelX - fm.stringWidth(letter) / 2.0), (float) (ge.labelY + fm.getAscent() / 2.0 - 1));
        }
    }

    /** The number of parked cars, on the pitch for the stadium and on a small box for the venue by the water. */
    private void drawCounter(Graphics2D g, ParkingSnapshot snap) {
        String top = String.format("%,d / %,d", snap.parked(), snap.totalCars());
        String bottom = "cars parked  |  " + clock(snap.simSeconds());
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, (int) (20 * scale)));
        Font mid = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (11 * scale)));
        g.setFont(big);
        FontMetrics fm = g.getFontMetrics();
        g.setFont(mid);
        FontMetrics sm = g.getFontMetrics();
        if (behind) {
            double boxW = Math.max(fm.stringWidth(top), sm.stringWidth(bottom)) + 24;
            double boxH = fm.getHeight() + sm.getHeight() + 8;
            double x = counterX;
            double y = counterY - boxH / 2;
            g.setColor(new Color(255, 255, 255, 220));
            g.fill(new RoundRectangle2D.Double(x - boxW / 2, y - boxH / 2, boxW, boxH, 10, 10));
            g.setColor(new Color(0x26323C));
            g.setFont(big);
            g.drawString(top, (float) (x - fm.stringWidth(top) / 2.0), (float) (y - boxH / 2 + fm.getAscent() + 3));
            g.setFont(mid);
            g.drawString(bottom, (float) (x - sm.stringWidth(bottom) / 2.0),
                    (float) (y - boxH / 2 + fm.getHeight() + sm.getAscent() + 3));
        } else {
            g.setColor(Color.WHITE);
            g.setFont(big);
            g.drawString(top, (float) (counterX - fm.stringWidth(top) / 2.0), (float) (counterY - 2));
            g.setFont(mid);
            g.drawString(bottom, (float) (counterX - sm.stringWidth(bottom) / 2.0), (float) (counterY + sm.getAscent() + 3));
        }
    }

    private double carSize(double spacing) {
        return Math.max(3.4, Math.min(5.6, spacing * 0.82)) * Math.min(1.2, Math.max(0.8, scale));
    }

    private void drawCar(Graphics2D g, Map<Car, float[]> next, Car car, double tx, double ty,
                         boolean snapTo, double size, Color color) {
        if (next.containsKey(car)) {
            return;
        }
        float[] p = positions.get(car);
        if (p == null || snapTo) {
            p = new float[] {(float) tx, (float) ty};
        } else {
            p[0] += (float) ((tx - p[0]) * SMOOTHING);
            p[1] += (float) ((ty - p[1]) * SMOOTHING);
        }
        next.put(car, p);
        g.setColor(color);
        rect.setRect(p[0] - size / 2, p[1] - size / 2, size, size);
        g.fill(rect);
    }

    private double driveProgress(Car car, double now) {
        double duration = car.getArriveAt() - car.getSpawnedAt();
        if (duration <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (now - car.getSpawnedAt()) / duration));
    }

    private double clampX(double x) {
        return Math.max(5, Math.min(getWidth() - 5, x));
    }

    private double clampY(double y) {
        return Math.max(5, Math.min(getHeight() - 5, y));
    }

    private void drawLineCars(Graphics2D g, ParkingSimulation s, Map<Car, float[]> next) {
        double now = s.getClock().now();
        List<Lot> lots = s.getLots();
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, (int) (10 * scale)));
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            CarLine line = lots.get(i).getLine();
            int capacity = line.capacity();
            double size = carSize(ge.lane.spacing);

            List<Car> lane = line.laneCars(ge.lane.maxSlots);
            for (int k = 0; k < lane.size(); k++) {
                drawCar(g, next, lane.get(k), ge.lane.x(k), ge.lane.y(k), false, size, IN_LANE);
            }
            List<Car> outside = line.outsideCars(ge.blob.maxSlots);
            for (int k = 0; k < outside.size(); k++) {
                drawCar(g, next, outside.get(k), ge.blob.x(k), ge.blob.y(k), false, size, STREET);
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
            for (Car car : line.drivingCars(MAX_DRIVING_PER_LINE)) {
                double fx = clampX(ge.spawnX + ge.tx * (car.getSpot() - 0.5) * ge.spread);
                double fy = clampY(ge.spawnY + ge.ty * (car.getSpot() - 0.5) * ge.spread);
                double p = driveProgress(car, now);
                drawCar(g, next, car, fx + (tx - fx) * p, fy + (ty - fy) * p, true, size, DRIVING);
            }

            if (geo.length <= 16) {
                int waiting = line.laneSize() + line.outsideSize();
                int hidden = Math.max(0, line.laneSize() - ge.lane.maxSlots)
                        + Math.max(0, line.outsideSize() - ge.blob.maxSlots);
                if (waiting > 0) {
                    String text = hidden > 0 ? waiting + " (+" + hidden + ")" : String.valueOf(waiting);
                    g.setFont(small);
                    FontMetrics fm = g.getFontMetrics();
                    double d = ge.approach * 2 + ge.laneLen + ge.blobLen + 11 * scale;
                    double shift = ge.blobW / 2 + fm.stringWidth(text) / 2.0 + 4;
                    double lx = ge.ex + ge.nx * d + ge.tx * shift;
                    double ly = ge.ey + ge.ny * d + ge.ty * shift;
                    g.setColor(new Color(0x33404B));
                    g.drawString(text, (float) (lx - fm.stringWidth(text) / 2.0), (float) (ly + fm.getAscent() / 2.0));
                }
            }
        }
    }

    private void drawBoothCars(Graphics2D g, ParkingSimulation s, Map<Car, float[]> next) {
        ParkingConfig cfg = s.getConfig();
        if (cfg.attendantsPerLot() > MAX_BOOTHS_DRAWN) {
            return;
        }
        int booths = cfg.attendantsPerLot();
        List<Lot> lots = s.getLots();
        for (int i = 0; i < geo.length; i++) {
            Geo ge = geo[i];
            double size = carSize(ge.lane.spacing) + 0.8;
            List<Attendant> attendants = lots.get(i).getAttendants();
            for (int k = 0; k < booths; k++) {
                Car car = attendants.get(k).getCurrent();
                if (car != null) {
                    drawCar(g, next, car, boothX(ge, k, booths), boothY(ge, k, booths), false, size, PAYING);
                }
            }
        }
    }

    /** Cars that just parked drive to their space, cars that were turned away drive off, and parked fans walk to the venue. */
    private void drawFinishedCars(Graphics2D g, ParkingSimulation s, double now) {
        long nowNanos = System.nanoTime();
        int shown = 0;
        int spaces = s.getConfig().spacesPerLot();
        for (Car car : s.getRecentCars().within(Long.MAX_VALUE)) {
            int li = car.getServedLot();
            if (li < 0 || li >= geo.length || shown > 900) {
                continue;
            }
            Geo ge = geo[li];
            double ageReal = (nowNanos - car.getFinishedNanos()) / 1e9;
            if (car.getState() == Car.State.PARKED) {
                int cell = cellFor(ge, car.getSpace(), spaces);
                double cellX = ge.cellX[cell];
                double cellY = ge.cellY[cell];
                if (ageReal >= 0 && ageReal < PARK_SECONDS) {
                    double size = carSize(ge.lane.spacing);
                    double e = 1 - (1 - ageReal / PARK_SECONDS) * (1 - ageReal / PARK_SECONDS);
                    double sx = ge.ex - ge.nx * 8 * scale;
                    double sy = ge.ey - ge.ny * 8 * scale;
                    g.setColor(PAYING);
                    rect.setRect(sx + (cellX - sx) * e - size / 2, sy + (cellY - sy) * e - size / 2, size, size);
                    g.fill(rect);
                    shown++;
                }
                double walk = s.getLots().get(li).getWalkSeconds();
                double since = now - car.getFinishedAt();
                if (since >= 0 && since < walk) {
                    // the fan walks in a straight line from the space to the venue
                    double[] target = venueEntrance(cellX, cellY);
                    double p = since / walk;
                    g.setColor(WALKING);
                    dot.setFrame(cellX + (target[0] - cellX) * p - 2.2, cellY + (target[1] - cellY) * p - 2.2, 4.4, 4.4);
                    g.fill(dot);
                    shown++;
                }
            } else if (car.getState() == Car.State.TURNED_AWAY && ageReal >= 0 && ageReal < AWAY_SECONDS) {
                double e = ageReal / AWAY_SECONDS;
                double x = ge.ex + ge.nx * (6 * scale + 46 * scale * e) + ge.tx * 6 * scale * e;
                double y = ge.ey + ge.ny * (6 * scale + 46 * scale * e) + ge.ty * 6 * scale * e;
                double fade = e < 0.6 ? 1 : 1 - (e - 0.6) / 0.4;
                double size = carSize(ge.lane.spacing) + 0.6;
                g.setColor(new Color(TURNED_AWAY.getRed(), TURNED_AWAY.getGreen(), TURNED_AWAY.getBlue(),
                        (int) Math.round(255 * Math.max(0, fade))));
                rect.setRect(x - size / 2, y - size / 2, size, size);
                g.fill(rect);
                shown++;
            }
        }
    }

    private void drawOverlays(Graphics2D g, ParkingSimulation s, ParkingSnapshot snap, int w) {
        Font legend = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g.setFont(legend);
        String[] names = {"Driving to a lot", "In the entrance lane", "Waiting on the street (lane full)",
                "At the payment booth", "Parked in a space", "Walking to the venue", "Turned away (lot full)"};
        Color[] colors = {DRIVING, IN_LANE, STREET, PAYING, PARKED, WALKING, TURNED_AWAY};
        g.setColor(new Color(255, 255, 255, 215));
        g.fill(new RoundRectangle2D.Double(8, 8, 238, 14 + names.length * 18, 10, 10));
        for (int i = 0; i < names.length; i++) {
            g.setColor(colors[i]);
            if (i == 5) {
                dot.setFrame(18, 20 + i * 18 - 4, 9, 9);
                g.fill(dot);
            } else {
                rect.setRect(18, 20 + i * 18 - 4, 9, 9);
                g.fill(rect);
            }
            g.setColor(new Color(0x26323C));
            g.drawString(names[i], 34, 20 + i * 18 + 5);
        }

        String state = switch (snap.state()) {
            case NEW -> "Press Start";
            case PAUSED -> "Paused";
            case FINISHED -> "All cars handled";
            default -> "";
        };
        if (!state.isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            if (behind) {
                g.setColor(new Color(0x26323C));
                g.drawString(state, (float) (counterX + 130 * scale), (float) (counterY + 5));
            } else {
                g.setColor(Color.WHITE);
                g.drawString(state, (float) (cx - fm.stringWidth(state) / 2.0), (float) (cy + radiusB * 0.5 + 24 * scale));
            }
        }

        g.setFont(legend);
        String info = s.getConfig().strategy() + "  |  " + s.getConfig().shape();
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(255, 255, 255, 215));
        g.fill(new RoundRectangle2D.Double(w - fm.stringWidth(info) - 24, getHeight() - 30, fm.stringWidth(info) + 16, 22, 8, 8));
        g.setColor(new Color(0x26323C));
        g.drawString(info, w - fm.stringWidth(info) - 16, getHeight() - 14);
    }

    public static String clock(double simSeconds) {
        int total = (int) Math.round(simSeconds);
        return String.format("%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
    }
}
