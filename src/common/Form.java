package common;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * Small helpers shared by the control panels of the stadium, the parking lots and the whole
 * event, so the forms are built the same way and the code is written once. The panels use them
 * with a static import.
 *
 * A form is a panel with a GridBagLayout. Each helper takes the panel and the next free row y
 * and returns the next free row after the rows it added.
 */
public final class Form {

    private Form() {
    }

    /** A panel that is as wide as the scroll pane's view, so the controls never run off the right edge. */
    public static final class ControlsPanel extends JPanel implements Scrollable {

        public ControlsPanel() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return 80;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public static int section(JPanel panel, int y, String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setBorder(BorderFactory.createEmptyBorder(y == 0 ? 0 : 12, 0, 4, 0));
        panel.add(label, fullWidth(y));
        return y + 1;
    }

    /** A label on the left and the field on the right. */
    public static int row(JPanel panel, int y, String label, JComponent field, String tip) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        JLabel text = new JLabel(label);
        text.setToolTipText(tip);
        panel.add(text, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        field.setToolTipText(tip);
        if (field instanceof JSpinner) {
            field.setPreferredSize(new Dimension(90, 24));
        }
        panel.add(field, c);
        return y + 1;
    }

    /** The label on its own line with the field under it, for fields with long text. */
    public static int wideRow(JPanel panel, int y, String label, JComponent field, String tip) {
        JLabel text = new JLabel(label);
        text.setToolTipText(tip);
        GridBagConstraints c = fullWidth(y);
        c.insets = new Insets(2, 0, 0, 0);
        panel.add(text, c);
        c = fullWidth(y + 1);
        c.insets = new Insets(1, 0, 4, 0);
        field.setToolTipText(tip);
        panel.add(field, c);
        return y + 2;
    }

    public static GridBagConstraints fullWidth(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    /** A right-aligned number in the statistics panel. */
    public static JLabel valueLabel() {
        JLabel label = new JLabel("-", SwingConstants.RIGHT);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        return label;
    }

    public static JSpinner intSpinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    public static JSpinner decimalSpinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    // The speed slider is logarithmic: its 0 to 100 stands for 10 to about 5,000 simulated seconds per real second.

    public static double speedFor(int sliderValue) {
        return Math.round(10 * Math.pow(10, sliderValue / 100.0 * 2.7));
    }

    public static int sliderFor(double speed) {
        double position = Math.log10(Math.max(10, speed) / 10.0) / 2.7 * 100;
        return (int) Math.max(0, Math.min(100, Math.round(position)));
    }

    public static String speedText(double speed) {
        return String.format("%,.0fx: 1 real second = %.1f simulated minutes", speed, speed / 60.0);
    }
}
