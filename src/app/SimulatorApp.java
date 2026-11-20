package app;

import event.EventPanel;
import parking.ParkingPanel;
import stadium.StadiumPanel;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * One window for both simulations. Use the tabs to switch between the stadium entry (fans and
 * gate scanners) and the parking lots around the stadium (cars and lot attendants). Each tab has
 * its own simulation, so one can keep running while you look at the other.
 */
public class SimulatorApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createFrame().setVisible(true));
    }

    public static JFrame createFrame() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // the default look is fine
        }
        JFrame frame = new JFrame("Stadium Event Simulator - CSC 311 Producer-Consumer");
        StadiumPanel stadium = new StadiumPanel();
        ParkingPanel parking = new ParkingPanel();
        EventPanel event = new EventPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Stadium entry: fans and gates", stadium);
        tabs.addTab("Parking lots: cars and attendants", parking);
        tabs.addTab("Whole event: parking, then the gates", event);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stadium.shutdown();
                parking.shutdown();
                event.shutdown();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setContentPane(tabs);

        // The whole event tab shows two pictures side by side, so use as much of the screen as possible.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(new Dimension(Math.min(1900, screen.width - 20), Math.min(1000, screen.height - 20)));
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
