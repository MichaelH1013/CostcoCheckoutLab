package event;

import parking.LotStrategy;
import parking.ParkingConfig;
import parking.ParkingLayout;
import parking.ParkingSimulation;
import parking.ParkingSnapshot;
import parking.ParkingView;
import stadium.ArrivalShape;
import stadium.HistoryChart;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumSnapshot;
import stadium.StadiumView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * The whole event in one tab: the parking lots on the left of the picture, the stadium gates on
 * the right, and the controls and numbers for both. Fans who park walk from their lot to the
 * stadium and join the line at a gate, so a slow lot entrance starves the gates and a slow gate
 * backs fans up in the parking lots.
 *
 * A Swing timer redraws both pictures about 30 times a second and refreshes the numbers about
 * four times a second. The simulation threads never touch Swing; the timer only reads their
 * thread-safe counters.
 */
public final class EventPanel extends JPanel {

    private static final String CUSTOM = "Custom";

    private final ParkingView parkingView = new ParkingView();
    private final StadiumView stadiumView = new StadiumView();
    private final HistoryChart parkingChart = new HistoryChart("Cars waiting at the lot entrances");
    private final HistoryChart gateChart = new HistoryChart("Fans waiting at the gates");

    private final JComboBox<Object> presetBox = new JComboBox<>();
    // parking
    private final JSpinner lotsSpinner = intSpinner(6, 1, 100, 1);
    private final JSpinner spacesSpinner = intSpinner(300, 1, 100_000, 50);
    private final JSpinner attendantsSpinner = intSpinner(2, 1, 50, 1);
    private final JSpinner paySpinner = decimalSpinner(15, 0.5, 600, 0.5);
    private final JSpinner lotLaneSpinner = intSpinner(25, 1, 10_000, 5);
    private final JSpinner lotBiasSpinner = decimalSpinner(1, 0, 10, 0.5);
    private final JComboBox<LotStrategy> lotStrategyBox = new JComboBox<>(LotStrategy.values());
    private final JComboBox<ParkingLayout> layoutBox = new JComboBox<>(ParkingLayout.values());
    // stadium
    private final JSpinner gatesSpinner = intSpinner(8, 1, 200, 1);
    private final JSpinner scannersSpinner = intSpinner(2, 1, 100, 1);
    private final JSpinner scanSpinner = decimalSpinner(7, 0.5, 600, 0.5);
    private final JSpinner gateLaneSpinner = intSpinner(40, 1, 10_000, 5);
    private final JSpinner gateBiasSpinner = decimalSpinner(1, 0, 10, 0.5);
    private final JComboBox<QueueStrategy> gateStrategyBox = new JComboBox<>(QueueStrategy.values());
    // cars and fans
    private final JSpinner carsSpinner = intSpinner(1700, 1, 1_000_000, 100);
    private final JSpinner fansPerCarSpinner = intSpinner(3, 1, EventConfig.MAX_FANS_PER_CAR, 1);
    private final JSpinner minutesSpinner = decimalSpinner(90, 1, 1000, 5);
    private final JComboBox<ArrivalShape> shapeBox = new JComboBox<>(ArrivalShape.values());
    private final JSpinner driveSpinner = decimalSpinner(40, 0, 3600, 5);
    private final JSpinner lotWalkSpinner = decimalSpinner(120, 0, 3600, 10);
    private final JSpinner gateWalkSpinner = decimalSpinner(45, 0, 3600, 5);
    private final JSlider speedSlider = new JSlider(0, 100, 50);
    private final JLabel speedLabel = new JLabel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resetButton = new JButton("Reset");
    private final JLabel statusLabel = new JLabel();

    private final JLabel timeValue = valueLabel();
    private final JLabel insideValue = valueLabel();
    private final JLabel longerValue = valueLabel();
    private final JLabel parkedValue = valueLabel();
    private final JLabel awayValue = valueLabel();
    private final JLabel parkWaitValue = valueLabel();
    private final JLabel parkLongestValue = valueLabel();
    private final JLabel carsWaitingValue = valueLabel();
    private final JLabel walkingValue = valueLabel();
    private final JLabel gateLaneValue = valueLabel();
    private final JLabel gateOutsideValue = valueLabel();
    private final JLabel scanningValue = valueLabel();
    private final JLabel gateWaitValue = valueLabel();
    private final JLabel gateLongestValue = valueLabel();
    private final JLabel feesValue = valueLabel();
    private final JLabel lostValue = valueLabel();
    private final JLabel costValue = valueLabel();

    private final ChangeListener parameterListener = e -> onParameterChanged();
    private final Timer timer;
    private EventSimulation sim;
    private String configError;
    private boolean loading = false;
    private int ticks = 0;
    private EventSimulation.State shownState;

    public EventPanel() {
        super(new BorderLayout());
        presetBox.addItem(CUSTOM);
        for (EventPreset preset : EventPreset.values()) {
            presetBox.addItem(preset);
        }
        for (JSpinner spinner : new JSpinner[] {lotsSpinner, spacesSpinner, attendantsSpinner, paySpinner,
                lotLaneSpinner, lotBiasSpinner, gatesSpinner, scannersSpinner, scanSpinner, gateLaneSpinner,
                gateBiasSpinner, carsSpinner, fansPerCarSpinner, minutesSpinner, driveSpinner, lotWalkSpinner,
                gateWalkSpinner}) {
            spinner.addChangeListener(parameterListener);
        }
        lotStrategyBox.addActionListener(e -> onParameterChanged());
        layoutBox.addActionListener(e -> onParameterChanged());
        gateStrategyBox.addActionListener(e -> onParameterChanged());
        shapeBox.addActionListener(e -> onParameterChanged());
        presetBox.addActionListener(e -> onPresetChosen());
        speedSlider.addChangeListener(e -> onSpeedChanged());

        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> togglePause());
        resetButton.addActionListener(e -> reset());

        JScrollPane controls = new JScrollPane(buildControls(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controls.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xC9D0D6)));
        controls.setPreferredSize(new Dimension(300, 600));

        JPanel pictures = new JPanel(new GridLayout(1, 2, 4, 0));
        pictures.add(titled("1. Parking lots: cars park, fans walk to the stadium", parkingView));
        pictures.add(titled("2. Stadium gates: fans line up and go in", stadiumView));

        add(controls, BorderLayout.WEST);
        add(pictures, BorderLayout.CENTER);
        add(buildStats(), BorderLayout.EAST);

        timer = new Timer(33, e -> tick());
        loading = true;
        presetBox.setSelectedItem(EventPreset.FOOTBALL);
        loading = false;
        loadConfig(EventPreset.FOOTBALL.config());
        timer.start();
    }

    private static JPanel titled(String title, JComponent view) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        panel.add(label, BorderLayout.NORTH);
        view.setPreferredSize(new Dimension(300, 300));
        panel.add(view, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------ building the window

    /** A panel that is as wide as the scroll pane's view, so the controls never run off the right edge. */
    private static final class ControlsPanel extends JPanel implements Scrollable {

        ControlsPanel() {
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

    private JPanel buildControls() {
        JPanel panel = new ControlsPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int y = 0;
        y = section(panel, y, "Event");
        y = wideRow(panel, y, "Preset", presetBox, "Fills in all the numbers below for a typical event.");

        y = section(panel, y, "1. Parking lots");
        y = wideRow(panel, y, "Lot strategy", lotStrategyBox,
                "Assigned lot: each car picks a lot at random (nearer lots are busier). Shortest line and "
                        + "most free spaces skip full lots.");
        y = wideRow(panel, y, "Lot layout", layoutBox, "Only changes the picture.");
        y = row(panel, y, "Lots", lotsSpinner, "Number of parking lots.");
        y = row(panel, y, "Spaces per lot", spacesSpinner, "A full lot turns cars away.");
        y = row(panel, y, "Attendants per lot", attendantsSpinner, "Attendant threads at each lot entrance.");
        y = row(panel, y, "Pay time (sec)", paySpinner, "Average time to take payment or check a pass.");
        y = row(panel, y, "Lane capacity", lotLaneSpinner, "Cars that fit in one entrance lane. Extra cars wait on the street.");
        y = row(panel, y, "Lot bias", lotBiasSpinner, "1 = the nearest lot draws twice as many cars as the farthest.");

        y = section(panel, y, "2. Stadium gates");
        y = wideRow(panel, y, "Gate strategy", gateStrategyBox,
                "Assigned gate, shortest line, or one shared line for every gate.");
        y = row(panel, y, "Gates", gatesSpinner, "Number of entry points.");
        y = row(panel, y, "Scanners per gate", scannersSpinner, "Scanner threads at each gate.");
        y = row(panel, y, "Scan time (sec)", scanSpinner, "Average time to scan a fan.");
        y = row(panel, y, "Lane capacity", gateLaneSpinner, "Fans that fit in one gate lane. Extra fans wait outside it.");
        y = row(panel, y, "Gate bias", gateBiasSpinner, "1 = gate 1 draws twice as many fans as the last gate.");

        y = section(panel, y, "Cars and fans");
        y = row(panel, y, "Cars", carsSpinner, "How many cars arrive in total.");
        y = row(panel, y, "Fans per car", fansPerCarSpinner,
                "Each car that parks lets this many fans out. They walk to the stadium together.");
        y = row(panel, y, "Arrival window (min)", minutesSpinner, "Simulated minutes over which the cars arrive.");
        y = wideRow(panel, y, "Arrival pattern", shapeBox, "When cars show up during the arrival window.");
        y = row(panel, y, "Drive time (sec)", driveSpinner, "From the main road to the back of the line.");
        y = row(panel, y, "Lot walk (sec)", lotWalkSpinner,
                "Walk from the nearest lot to the stadium. The farthest lot is 3 times as far.");
        y = row(panel, y, "Gate walk (sec)", gateWalkSpinner, "Walk from the stadium approach to the gate line.");

        y = section(panel, y, "Speed");
        speedSlider.setToolTipText("How fast simulated time runs. You can change it while the event runs.");
        GridBagConstraints c = fullWidth(y++);
        panel.add(speedSlider, c);
        c = fullWidth(y++);
        speedLabel.setFont(speedLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(speedLabel, c);

        c = fullWidth(y++);
        c.insets = new Insets(14, 0, 0, 0);
        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        buttons.add(startButton);
        buttons.add(pauseButton);
        buttons.add(resetButton);
        panel.add(buttons, c);

        c = fullWidth(y++);
        c.insets = new Insets(10, 0, 0, 0);
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setPreferredSize(new Dimension(250, 64));
        panel.add(statusLabel, c);

        c = fullWidth(y);
        c.weighty = 1;
        c.fill = GridBagConstraints.VERTICAL;
        panel.add(new JPanel(), c);
        return panel;
    }

    private int section(JPanel panel, int y, String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setBorder(BorderFactory.createEmptyBorder(y == 0 ? 0 : 12, 0, 4, 0));
        panel.add(label, fullWidth(y));
        return y + 1;
    }

    private int row(JPanel panel, int y, String label, JComponent field, String tip) {
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
    private int wideRow(JPanel panel, int y, String label, JComponent field, String tip) {
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

    private GridBagConstraints fullWidth(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }

    private JPanel buildStats() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xC9D0D6)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        panel.setPreferredSize(new Dimension(330, 600));

        addSection(panel, "Whole event",
                new String[] {"Simulated time", "Fans inside", "Fans wait longer at"},
                new JLabel[] {timeValue, insideValue, longerValue});
        addSection(panel, "1. Parking lots",
                new String[] {"Cars parked", "Turned away (lot full)", "Cars waiting at entrances",
                        "Average wait", "Longest wait"},
                new JLabel[] {parkedValue, awayValue, carsWaitingValue, parkWaitValue, parkLongestValue});
        addSection(panel, "On the way",
                new String[] {"Fans walking to the stadium"}, new JLabel[] {walkingValue});
        addSection(panel, "2. Stadium gates",
                new String[] {"Fans in gate lanes", "Waiting outside lanes", "Being scanned", "Average wait",
                        "Longest wait"},
                new JLabel[] {gateLaneValue, gateOutsideValue, scanningValue, gateWaitValue, gateLongestValue});
        addSection(panel, "Money",
                new String[] {"Parking fees so far", "Fees lost (turned away)", "Staff cost so far"},
                new JLabel[] {feesValue, lostValue, costValue});

        panel.add(Box.createVerticalStrut(8));
        panel.add(parkingChart);
        panel.add(Box.createVerticalStrut(6));
        panel.add(gateChart);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void addSection(JPanel panel, String title, String[] names, JLabel[] values) {
        JLabel header = new JLabel(title);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createEmptyBorder(6, 0, 3, 0));
        panel.add(header);
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < names.length; i++) {
            JLabel label = new JLabel(names[i]);
            label.setFont(label.getFont().deriveFont(12f));
            grid.add(label);
            grid.add(values[i]);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, names.length * 20));
        panel.add(grid);
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-", SwingConstants.RIGHT);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        return label;
    }

    private static JSpinner intSpinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JSpinner decimalSpinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    // ------------------------------------------------------------------ controls to config and back

    private EventConfig readConfig() {
        ParkingConfig parking = ParkingConfig.builder()
                .lots(intOf(lotsSpinner))
                .attendantsPerLot(intOf(attendantsSpinner))
                .cars(intOf(carsSpinner))
                .arrivalMinutes(doubleOf(minutesSpinner))
                .payingSeconds(doubleOf(paySpinner))
                .spacesPerLot(intOf(spacesSpinner))
                .queueCapacity(intOf(lotLaneSpinner))
                .strategy((LotStrategy) lotStrategyBox.getSelectedItem())
                .shape((ArrivalShape) shapeBox.getSelectedItem())
                .producerThreads(2)
                .driveSeconds(doubleOf(driveSpinner))
                .walkSeconds(doubleOf(lotWalkSpinner))
                .lotBias(doubleOf(lotBiasSpinner))
                .speed(speedFromSlider())
                .seed(311)
                .layout((ParkingLayout) layoutBox.getSelectedItem())
                .build();
        StadiumConfig stadium = StadiumConfig.builder()
                .gates(intOf(gatesSpinner))
                .scannersPerGate(intOf(scannersSpinner))
                .serviceSeconds(doubleOf(scanSpinner))
                .queueCapacity(intOf(gateLaneSpinner))
                .strategy((QueueStrategy) gateStrategyBox.getSelectedItem())
                .producerThreads(2)
                .walkSeconds(doubleOf(gateWalkSpinner))
                .gateBias(doubleOf(gateBiasSpinner))
                .speed(speedFromSlider())
                .seed(311)
                .build();
        return EventConfig.of(parking, stadium, intOf(fansPerCarSpinner));
    }

    private static int intOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static double doubleOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private void loadConfig(EventConfig c) {
        loading = true;
        ParkingConfig p = c.parking();
        StadiumConfig s = c.stadium();
        lotsSpinner.setValue(p.lots());
        spacesSpinner.setValue(p.spacesPerLot());
        attendantsSpinner.setValue(p.attendantsPerLot());
        paySpinner.setValue(p.payingSeconds());
        lotLaneSpinner.setValue(p.queueCapacity());
        lotBiasSpinner.setValue(p.lotBias());
        lotStrategyBox.setSelectedItem(p.strategy());
        layoutBox.setSelectedItem(p.layout());
        gatesSpinner.setValue(s.gates());
        scannersSpinner.setValue(s.scannersPerGate());
        scanSpinner.setValue(s.serviceSeconds());
        gateLaneSpinner.setValue(s.queueCapacity());
        gateBiasSpinner.setValue(s.gateBias());
        gateStrategyBox.setSelectedItem(s.strategy());
        carsSpinner.setValue(p.cars());
        fansPerCarSpinner.setValue(c.fansPerCar());
        minutesSpinner.setValue(p.arrivalMinutes());
        shapeBox.setSelectedItem(p.shape());
        driveSpinner.setValue(p.driveSeconds());
        lotWalkSpinner.setValue(p.walkSeconds());
        gateWalkSpinner.setValue(s.walkSeconds());
        speedSlider.setValue(sliderFromSpeed(p.speed()));
        updateSpeedLabel(speedFromSlider());
        loading = false;
        rebuildPreview();
    }

    /** The slider is logarithmic: 10 to about 5,000 simulated seconds per real second. */
    private double speedFromSlider() {
        return Math.round(10 * Math.pow(10, speedSlider.getValue() / 100.0 * 2.7));
    }

    private static int sliderFromSpeed(double speed) {
        double position = Math.log10(Math.max(10, speed) / 10.0) / 2.7 * 100;
        return (int) Math.max(0, Math.min(100, Math.round(position)));
    }

    private void updateSpeedLabel(double speed) {
        speedLabel.setText(String.format("%,.0fx: 1 real second = %.1f simulated minutes", speed, speed / 60.0));
    }

    // ------------------------------------------------------------------ reacting to the user

    private boolean canEditParameters() {
        return sim == null || sim.getState() == EventSimulation.State.NEW;
    }

    private void onPresetChosen() {
        if (loading || !(presetBox.getSelectedItem() instanceof EventPreset preset) || !canEditParameters()) {
            return;
        }
        loadConfig(preset.config());
    }

    private void onParameterChanged() {
        if (loading || !canEditParameters()) {
            return;
        }
        loading = true;
        presetBox.setSelectedItem(CUSTOM);
        loading = false;
        rebuildPreview();
    }

    private void onSpeedChanged() {
        double speed = speedFromSlider();
        updateSpeedLabel(speed);
        if (loading) {
            return;
        }
        if (sim != null && sim.getState() != EventSimulation.State.NEW) {
            sim.setSpeed(speed);
        } else {
            rebuildPreview();
        }
    }

    /** Builds an idle event from the current numbers so both pictures show the lots and the gates before Start. */
    private void rebuildPreview() {
        EventConfig config;
        try {
            config = readConfig();
        } catch (IllegalArgumentException e) {
            configError = e.getMessage();
            showState();
            return;
        }
        configError = null;
        if (sim != null) {
            sim.stop();
        }
        sim = new EventSimulation(config);
        parkingView.setSimulation(sim.getParking());
        stadiumView.setSimulation(sim.getStadium());
        parkingChart.clear();
        gateChart.clear();
        shownState = null;
        refreshStats();
        showState();
    }

    private void start() {
        if (sim == null || sim.getState() != EventSimulation.State.NEW) {
            return;
        }
        sim.start();
        showState();
    }

    private void togglePause() {
        if (sim == null) {
            return;
        }
        if (sim.getState() == EventSimulation.State.RUNNING) {
            sim.pause();
        } else if (sim.getState() == EventSimulation.State.PAUSED) {
            sim.resume();
        }
        showState();
    }

    private void reset() {
        if (sim != null) {
            sim.stop();
            sim = null;
        }
        rebuildPreview();
    }

    /** Called when the window closes. */
    public void shutdown() {
        timer.stop();
        if (sim != null) {
            sim.stop();
        }
    }

    // ------------------------------------------------------------------ updating the display

    private void tick() {
        parkingView.repaint();
        stadiumView.repaint();
        ticks++;
        if (sim != null && (ticks % 8 == 0 || sim.getState() != shownState)) {
            refreshStats();
            showState();
        }
    }

    private void showState() {
        EventSimulation.State state = sim == null ? EventSimulation.State.NEW : sim.getState();
        shownState = state;
        boolean editable = state == EventSimulation.State.NEW;
        for (JComponent c : new JComponent[] {presetBox, lotStrategyBox, layoutBox, gateStrategyBox, shapeBox,
                lotsSpinner, spacesSpinner, attendantsSpinner, paySpinner, lotLaneSpinner, lotBiasSpinner,
                gatesSpinner, scannersSpinner, scanSpinner, gateLaneSpinner, gateBiasSpinner, carsSpinner,
                fansPerCarSpinner, minutesSpinner, driveSpinner, lotWalkSpinner, gateWalkSpinner}) {
            c.setEnabled(editable);
        }
        startButton.setEnabled(state == EventSimulation.State.NEW && configError == null);
        pauseButton.setEnabled(state == EventSimulation.State.RUNNING || state == EventSimulation.State.PAUSED);
        pauseButton.setText(state == EventSimulation.State.PAUSED ? "Resume" : "Pause");
        if (configError != null) {
            setStatus("#B3261E", configError);
            return;
        }
        switch (state) {
            case NEW -> setStatus("#26323C", "Ready. Change the numbers if you like, then press Start.");
            case RUNNING -> setStatus("#1E7B46", "Running. Cars park, then their fans walk to the stadium and go in.");
            case PAUSED -> setStatus("#8A5A00", "Paused.");
            case FINISHED -> {
                ParkingSnapshot ps = sim.getParking().snapshot();
                StadiumSnapshot ss = sim.getStadium().snapshot();
                setStatus("#1E7B46", String.format(
                        "Finished after %s: %,d cars parked, %,d fans inside. Press Reset to run again.",
                        ParkingView.clock(sim.getFinishedAt()), ps.parked(), ss.admitted()));
            }
            case STOPPED -> setStatus("#26323C", "Stopped.");
        }
    }

    private void setStatus(String color, String text) {
        statusLabel.setText("<html><body style='width:240px;color:" + color + "'>" + text + "</body></html>");
    }

    private void refreshStats() {
        if (sim == null) {
            return;
        }
        ParkingSnapshot ps = sim.getParking().snapshot();
        StadiumSnapshot ss = sim.getStadium().snapshot();
        EventConfig c = sim.getConfig();
        double now = sim.getState() == EventSimulation.State.FINISHED ? sim.getFinishedAt() : sim.getClock().now();
        double minutes = now / 60.0;

        timeValue.setText(ParkingView.clock(now));
        insideValue.setText(String.format("%,d / %,d", ss.admitted(), ss.totalFans()));
        if (ps.averageWaitMinutes() == 0 && ss.averageWaitMinutes() == 0) {
            longerValue.setText("-");
        } else {
            longerValue.setText(ps.averageWaitMinutes() >= ss.averageWaitMinutes() ? "parking" : "the gates");
        }
        parkedValue.setText(String.format("%,d", ps.parked()));
        awayValue.setText(String.format("%,d", ps.turnedAway()));
        carsWaitingValue.setText(String.format("%,d", ps.waiting() + ps.outside()));
        parkWaitValue.setText(String.format("%.1f min", ps.averageWaitMinutes()));
        parkLongestValue.setText(String.format("%.1f min", ps.longestWaitMinutes()));
        walkingValue.setText(String.format("%,d", sim.getFansWalking()));
        gateLaneValue.setText(String.format("%,d", ss.waiting()));
        gateOutsideValue.setText(String.format("%,d", ss.outside()));
        scanningValue.setText(String.format("%,d", ss.scanning()));
        gateWaitValue.setText(String.format("%.1f min", ss.averageWaitMinutes()));
        gateLongestValue.setText(String.format("%.1f min", ss.longestWaitMinutes()));
        feesValue.setText(String.format("$%,.0f", ps.parked() * ParkingConfig.FEE_PER_CAR));
        lostValue.setText(String.format("$%,.0f", ps.turnedAway() * ParkingConfig.FEE_PER_CAR));
        double parkingMinutes = Double.isNaN(sim.getParking().getFinishedAt()) ? minutes : sim.getParking().getFinishedAt() / 60.0;
        double staff = c.parking().totalAttendants() * parkingMinutes + c.stadium().totalScanners() * minutes;
        costValue.setText(String.format("$%,.2f", staff * ParkingConfig.WAGE_PER_HOUR / 60));

        if (sim.getState() == EventSimulation.State.RUNNING || sim.getState() == EventSimulation.State.FINISHED) {
            parkingChart.add(minutes, ps.waiting() + ps.outside());
            gateChart.add(minutes, ss.waiting() + ss.outside());
        }
    }
}
