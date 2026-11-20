package parking;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The Stadium Parking Simulator window on its own. To switch between the stadium entry and the
 * parking lots in one window, run app.SimulatorApp instead.
 */
public class ParkingApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createFrame().setVisible(true));
    }

    public static JFrame createFrame() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // the default look is fine
        }
        JFrame frame = new JFrame("Stadium Parking Simulator - CSC 311 Producer-Consumer");
        ParkingPanel panel = new ParkingPanel();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.shutdown();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setContentPane(panel);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(new Dimension(Math.min(1560, screen.width - 20), Math.min(920, screen.height - 20)));
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
