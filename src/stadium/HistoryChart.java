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
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A small line chart of how many fans are waiting for a scanner (in lanes and outside them) over
 * the simulated time. When it holds too many points it keeps every second one, so a long event
 * still fits.
 */
public final class HistoryChart extends JPanel {

    private static final int MAX_POINTS = 400;

    private final List<double[]> points = new ArrayList<>();
    private final String title;
    private int stride = 1;
    private int counter = 0;

    public HistoryChart() {
        this("Fans waiting for a scanner");
    }

    public HistoryChart(String title) {
        this.title = title;
        setPreferredSize(new Dimension(300, 120));
        setBackground(Color.WHITE);
    }

    public void clear() {
        points.clear();
        stride = 1;
        counter = 0;
        repaint();
    }

    public void add(double simMinutes, int waiting) {
        counter++;
        if (counter % stride != 0) {
            return;
        }
        points.add(new double[] {simMinutes, waiting});
        if (points.size() > MAX_POINTS) {
            List<double[]> kept = new ArrayList<>();
            for (int i = 0; i < points.size(); i += 2) {
                kept.add(points.get(i));
            }
            points.clear();
            points.addAll(kept);
            stride *= 2;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int left = 40;
        int right = 8;
        int top = 22;
        int bottom = 18;

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.setColor(new Color(0x26323C));
        g.drawString(title, 6, 14);

        double maxY = 5;
        double maxX = 1;
        for (double[] p : points) {
            maxY = Math.max(maxY, p[1]);
            maxX = Math.max(maxX, p[0]);
        }
        maxY = niceCeiling(maxY);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0xD8DEE4));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 2; i++) {
            int y = top + (h - top - bottom) * i / 2;
            g.drawLine(left, y, w - right, y);
            String label = String.format("%,d", Math.round(maxY * (2 - i) / 2));
            g.setColor(new Color(0x5B6670));
            g.drawString(label, left - 4 - fm.stringWidth(label), y + 4);
            g.setColor(new Color(0xD8DEE4));
        }
        g.setColor(new Color(0x5B6670));
        g.drawString("0", left, h - 4);
        String xLabel = String.format("%.0f min", maxX);
        g.drawString(xLabel, w - right - fm.stringWidth(xLabel), h - 4);

        if (points.size() < 2) {
            g.dispose();
            return;
        }
        double plotW = w - left - right;
        double plotH = h - top - bottom;
        Path2D line = new Path2D.Double();
        Path2D area = new Path2D.Double();
        for (int i = 0; i < points.size(); i++) {
            double x = left + points.get(i)[0] / maxX * plotW;
            double y = top + plotH - points.get(i)[1] / maxY * plotH;
            if (i == 0) {
                line.moveTo(x, y);
                area.moveTo(x, top + plotH);
                area.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        double lastX = left + points.get(points.size() - 1)[0] / maxX * plotW;
        area.lineTo(lastX, top + plotH);
        area.closePath();
        g.setColor(new Color(StadiumView.OUTSIDE.getRed(), StadiumView.OUTSIDE.getGreen(), StadiumView.OUTSIDE.getBlue(), 60));
        g.fill(area);
        g.setColor(StadiumView.OUTSIDE);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(line);
        g.dispose();
    }

    private static double niceCeiling(double value) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        double scaled = value / magnitude;
        double nice = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10;
        return nice * magnitude;
    }
}
