package parking;

import common.ArrivalShape;
import common.Form;
import common.HistoryChart;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Insets;

import static common.Form.*;

/**
 * The whole parking window content: parameter controls on the left, the lots around the stadium
 * in the middle, live statistics on the right. A Swing timer redraws the picture about 30 times a
 * second and refreshes the numbers about four times a second. The simulation threads never touch
 * Swing; the timer only reads their thread-safe counters.
 */
public final class ParkingPanel extends JPanel {

    private static final String CUSTOM = "Custom";

    private final ParkingView view = new ParkingView();
    private final HistoryChart chart = new HistoryChart("Cars waiting at the entrances");

    private final JComboBox<Object> presetBox = new JComboBox<>();
    private final JSpinner lotsSpinner = intSpinner(6, 1, 100, 1);
    private final JSpinner attendantsSpinner = intSpinner(2, 1, 50, 1);
    private final JSpinner spacesSpinner = intSpinner(300, 1, 100_000, 50);
    private final JSpinner queueSpinner = intSpinner(25, 1, 10_000, 5);
    private final JSpinner crowdSpinner = intSpinner(0, 0, 1_000_000, 10);
    private final JSpinner payingSpinner = decimalSpinner(15, 0.5, 600, 0.5);
    private final JSpinner carsSpinner = intSpinner(1700, 1, 1_000_000, 100);
    private final JSpinner minutesSpinner = decimalSpinner(90, 1, 1000, 5);
    private final JSpinner driveSpinner = decimalSpinner(40, 0, 3600, 5);
    private final JSpinner walkSpinner = decimalSpinner(120, 0, 3600, 10);
    private final JSpinner biasSpinner = decimalSpinner(1, 0, 10, 0.5);
    private final JSpinner producersSpinner = intSpinner(2, 1, 32, 1);
    private final JComboBox<LotStrategy> strategyBox = new JComboBox<>(LotStrategy.values());
    private final JComboBox<ArrivalShape> shapeBox = new JComboBox<>(ArrivalShape.values());
    private final JComboBox<ParkingLayout> layoutBox = new JComboBox<>(ParkingLayout.values());
    private final JSlider speedSlider = new JSlider(0, 100, 50);
    private final JLabel speedLabel = new JLabel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resetButton = new JButton("Reset");
    private final JLabel statusLabel = new JLabel();

    private final JLabel timeValue = valueLabel();
    private final JLabel createdValue = valueLabel();
    private final JLabel drivingValue = valueLabel();
    private final JLabel laneValue = valueLabel();
    private final JLabel streetValue = valueLabel();
    private final JLabel payingValue = valueLabel();
    private final JLabel parkedValue = valueLabel();
    private final JLabel awayValue = valueLabel();
    private final JLabel freeValue = valueLabel();
    private final JLabel averageValue = valueLabel();
    private final JLabel longestValue = valueLabel();
    private final JLabel peakValue = valueLabel();
    private final JLabel fullValue = valueLabel();
    private final JLabel gaveUpValue = valueLabel();
    private final JLabel feesValue = valueLabel();
    private final JLabel lostValue = valueLabel();
    private final JLabel costValue = valueLabel();
    private final LotTableModel tableModel = new LotTableModel();

    private final ChangeListener parameterListener = e -> onParameterChanged();
    private final Timer timer;
    private ParkingSimulation sim;
    private String configError;
    private boolean loading = false;
    private int ticks = 0;
    private ParkingSimulation.State shownState;

    public ParkingPanel() {
        super(new BorderLayout());
        presetBox.addItem(CUSTOM);
        for (ParkingPreset preset : ParkingPreset.values()) {
            presetBox.addItem(preset);
        }
        for (JSpinner spinner : new JSpinner[] {lotsSpinner, attendantsSpinner, spacesSpinner, queueSpinner,
                crowdSpinner, payingSpinner, carsSpinner, minutesSpinner, driveSpinner, walkSpinner, biasSpinner, producersSpinner}) {
            spinner.addChangeListener(parameterListener);
        }
        strategyBox.addActionListener(e -> onParameterChanged());
        shapeBox.addActionListener(e -> onParameterChanged());
        layoutBox.addActionListener(e -> onParameterChanged());
        presetBox.addActionListener(e -> onPresetChosen());
        speedSlider.addChangeListener(e -> onSpeedChanged());

        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> togglePause());
        resetButton.addActionListener(e -> reset());

        JScrollPane controls = new JScrollPane(buildControls(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controls.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xC9D0D6)));
        controls.setPreferredSize(new Dimension(300, 600));
        add(controls, BorderLayout.WEST);
        add(view, BorderLayout.CENTER);
        add(buildStats(), BorderLayout.EAST);

        timer = new Timer(33, e -> tick());
        loading = true;
        presetBox.setSelectedItem(ParkingPreset.FOOTBALL);
        loading = false;
        loadConfig(ParkingPreset.FOOTBALL.config());
        timer.start();
    }

    // ------------------------------------------------------------------ building the window

    private JPanel buildControls() {
        JPanel panel = new ControlsPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int y = 0;
        y = section(panel, y, "Event");
        y = wideRow(panel, y, "Preset", presetBox, "Fills in all the numbers below for a typical event.");
        y = wideRow(panel, y, "Lot strategy", strategyBox,
                "Assigned lot: each car picks a lot at random (nearer lots are more popular). "
                        + "Shortest line: each car picks the entrance with the fewest cars, skipping full lots. "
                        + "Most free spaces: each car picks the lot with the most room.");
        y = wideRow(panel, y, "Arrival pattern", shapeBox, "When cars show up during the arrival window.");
        y = wideRow(panel, y, "Lot layout", layoutBox,
                "Only changes the picture. Lots all around the stadium looks like MetLife. "
                        + "One big lot behind the venue looks like Jones Beach Theater.");

        y = section(panel, y, "Parking lots");
        y = row(panel, y, "Lots", lotsSpinner, "Number of parking lots around the stadium.");
        y = row(panel, y, "Spaces per lot", spacesSpinner, "Parking spaces in each lot. A full lot turns cars away.");
        y = row(panel, y, "Attendants per lot", attendantsSpinner,
                "Attendant threads at each lot entrance. Each one handles one car at a time.");
        y = row(panel, y, "Lane capacity", queueSpinner,
                "Cars that fit in one entrance lane. Extra cars wait on the street.");
        y = row(panel, y, "Street limit (0 = none)", crowdSpinner,
                "Most cars allowed to wait on the street behind a full lane at each lot. A car that arrives "
                        + "when it is full drives on and is counted. 0 means no limit.");
        y = row(panel, y, "Pay time (sec)", payingSpinner,
                "Average seconds to take payment or check a pass. Each car takes 0.5x to 1.5x of this.");

        y = section(panel, y, "Cars");
        y = row(panel, y, "Cars", carsSpinner, "How many cars arrive in total.");
        y = row(panel, y, "Arrival window (min)", minutesSpinner, "Simulated minutes over which the cars arrive.");
        y = row(panel, y, "Drive time (sec)", driveSpinner,
                "Simulated seconds a car drives from the main road to the back of the line.");
        y = row(panel, y, "Walk time (sec)", walkSpinner,
                "Walk from the nearest lot to the stadium. The farthest lot is 3 times as far.");
        y = row(panel, y, "Lot bias", biasSpinner,
                "0 = every lot is equally popular. 1 = the nearest lot draws twice as many cars as the farthest. "
                        + "Only used by the assigned lot strategy.");
        y = row(panel, y, "Producer threads", producersSpinner,
                "Threads that create cars and drive them to the lots. A car is not a thread, so a few are plenty.");

        y = section(panel, y, "Speed");
        speedSlider.setToolTipText("How fast simulated time runs. You can change it while the simulation runs.");
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

    private JPanel buildStats() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xC9D0D6)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        panel.setPreferredSize(new Dimension(370, 600));

        JPanel numbers = new JPanel(new GridLayout(0, 2, 8, 3));
        addStat(numbers, "Simulated time", timeValue);
        addStat(numbers, "Cars created", createdValue);
        addStat(numbers, "Driving to lots", drivingValue);
        addStat(numbers, "In entrance lanes", laneValue);
        addStat(numbers, "Waiting on the street", streetValue);
        addStat(numbers, "At a payment booth", payingValue);
        addStat(numbers, "Parked", parkedValue);
        addStat(numbers, "Turned away (lot full)", awayValue);
        addStat(numbers, "Free spaces left", freeValue);
        addStat(numbers, "Average wait", averageValue);
        addStat(numbers, "Longest wait", longestValue);
        addStat(numbers, "Longest line", peakValue);
        addStat(numbers, "Found the lane full", fullValue);
        addStat(numbers, "Drove on (street full)", gaveUpValue);
        addStat(numbers, "Parking fees so far", feesValue);
        addStat(numbers, "Fees lost (turned away)", lostValue);
        addStat(numbers, "Attendant cost so far", costValue);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        JLabel title = new JLabel("Live statistics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        top.add(title, BorderLayout.NORTH);
        top.add(numbers, BorderLayout.CENTER);
        top.add(chart, BorderLayout.SOUTH);

        JTable table = new JTable(tableModel);
        table.setRowHeight(19);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFont(table.getFont().deriveFont(11f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 11f));
        table.setEnabled(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 1; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(right);
        }
        table.getColumnModel().getColumn(0).setMinWidth(46);
        table.getColumnModel().getColumn(0).setPreferredWidth(52);
        JScrollPane tableScroll = new JScrollPane(table);

        JTextArea key = new JTextArea("Per lot: Parked = cars parked, Free = spaces left, Lane = cars in the "
                + "entrance lane, Out = cars waiting on the street, Away = cars turned away, "
                + "Wait = average wait in minutes.");
        key.setEditable(false);
        key.setFocusable(false);
        key.setLineWrap(true);
        key.setWrapStyleWord(true);
        key.setOpaque(false);
        key.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 11f));

        panel.add(top, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(key, BorderLayout.SOUTH);
        return panel;
    }

    private void addStat(JPanel panel, String name, JLabel value) {
        JLabel label = new JLabel(name);
        label.setFont(label.getFont().deriveFont(12f));
        panel.add(label);
        panel.add(value);
    }

    // ------------------------------------------------------------------ controls to config and back

    private ParkingConfig readConfig() {
        return ParkingConfig.builder()
                .lots(intOf(lotsSpinner))
                .attendantsPerLot(intOf(attendantsSpinner))
                .cars(intOf(carsSpinner))
                .arrivalMinutes(doubleOf(minutesSpinner))
                .payingSeconds(doubleOf(payingSpinner))
                .spacesPerLot(intOf(spacesSpinner))
                .queueCapacity(intOf(queueSpinner))
                .crowdLimit(intOf(crowdSpinner))
                .strategy((LotStrategy) strategyBox.getSelectedItem())
                .shape((ArrivalShape) shapeBox.getSelectedItem())
                .producerThreads(intOf(producersSpinner))
                .driveSeconds(doubleOf(driveSpinner))
                .walkSeconds(doubleOf(walkSpinner))
                .lotBias(doubleOf(biasSpinner))
                .speed(speedFromSlider())
                .seed(311)
                .layout((ParkingLayout) layoutBox.getSelectedItem())
                .build();
    }

    private static int intOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static double doubleOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private void loadConfig(ParkingConfig c) {
        loading = true;
        lotsSpinner.setValue(c.lots());
        attendantsSpinner.setValue(c.attendantsPerLot());
        spacesSpinner.setValue(c.spacesPerLot());
        queueSpinner.setValue(c.queueCapacity());
        crowdSpinner.setValue(c.crowdLimit());
        payingSpinner.setValue(c.payingSeconds());
        carsSpinner.setValue(c.cars());
        minutesSpinner.setValue(c.arrivalMinutes());
        driveSpinner.setValue(c.driveSeconds());
        walkSpinner.setValue(c.walkSeconds());
        biasSpinner.setValue(c.lotBias());
        producersSpinner.setValue(c.producerThreads());
        strategyBox.setSelectedItem(c.strategy());
        shapeBox.setSelectedItem(c.shape());
        layoutBox.setSelectedItem(c.layout());
        speedSlider.setValue(sliderFor(c.speed()));
        updateSpeedLabel(speedFromSlider());
        loading = false;
        rebuildPreview();
    }

    /** The slider is logarithmic: 10 to about 5,000 simulated seconds per real second. */
    private double speedFromSlider() {
        return Form.speedFor(speedSlider.getValue());
    }

    private void updateSpeedLabel(double speed) {
        speedLabel.setText(Form.speedText(speed));
    }

    // ------------------------------------------------------------------ reacting to the user

    private boolean canEditParameters() {
        return sim == null || sim.getState() == ParkingSimulation.State.NEW;
    }

    private void onPresetChosen() {
        if (loading || !(presetBox.getSelectedItem() instanceof ParkingPreset preset) || !canEditParameters()) {
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
        if (sim != null && sim.getState() != ParkingSimulation.State.NEW) {
            sim.setSpeed(speed);
        } else {
            rebuildPreview();
        }
    }

    /** Builds an idle simulation from the current numbers so the picture shows the lots before Start. */
    private void rebuildPreview() {
        ParkingConfig config;
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
        sim = new ParkingSimulation(config);
        view.setSimulation(sim);
        chart.clear();
        shownState = null;
        refreshStats();
        showState();
    }

    private void start() {
        if (sim == null || sim.getState() != ParkingSimulation.State.NEW) {
            return;
        }
        sim.start();
        showState();
    }

    private void togglePause() {
        if (sim == null) {
            return;
        }
        if (sim.getState() == ParkingSimulation.State.RUNNING) {
            sim.pause();
        } else if (sim.getState() == ParkingSimulation.State.PAUSED) {
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
        view.repaint();
        ticks++;
        if (sim != null && (ticks % 8 == 0 || sim.getState() != shownState)) {
            refreshStats();
            showState();
        }
    }

    private void showState() {
        ParkingSimulation.State state = sim == null ? ParkingSimulation.State.NEW : sim.getState();
        shownState = state;
        boolean editable = state == ParkingSimulation.State.NEW;
        for (JComponent c : new JComponent[] {presetBox, strategyBox, shapeBox, layoutBox, lotsSpinner, attendantsSpinner,
                spacesSpinner, queueSpinner, crowdSpinner, payingSpinner, carsSpinner, minutesSpinner, driveSpinner,
                walkSpinner, biasSpinner, producersSpinner}) {
            c.setEnabled(editable);
        }
        startButton.setEnabled(state == ParkingSimulation.State.NEW && configError == null);
        pauseButton.setEnabled(state == ParkingSimulation.State.RUNNING || state == ParkingSimulation.State.PAUSED);
        pauseButton.setText(state == ParkingSimulation.State.PAUSED ? "Resume" : "Pause");
        if (configError != null) {
            setStatus("#B3261E", configError);
            return;
        }
        switch (state) {
            case NEW -> setStatus("#26323C", "Ready. Change the numbers if you like, then press Start.");
            case RUNNING -> setStatus("#1E7B46", "Running. You can change the speed or pause at any time.");
            case PAUSED -> setStatus("#8A5A00", "Paused.");
            case FINISHED -> {
                ParkingSnapshot s = sim.snapshot();
                setStatus("#1E7B46", String.format(
                        "Finished after %s: %,d cars parked and %,d turned away. Press Reset to run again.",
                        ParkingView.clock(sim.getFinishedAt()), s.parked(), s.turnedAway()));
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
        ParkingSnapshot s = sim.snapshot();
        ParkingConfig c = sim.getConfig();
        double minutes = s.simMinutes();
        timeValue.setText(ParkingView.clock(s.simSeconds()));
        createdValue.setText(String.format("%,d / %,d", s.spawned(), s.totalCars()));
        drivingValue.setText(String.format("%,d", s.driving()));
        laneValue.setText(String.format("%,d", s.waiting()));
        streetValue.setText(String.format("%,d", s.outside()));
        payingValue.setText(String.format("%,d", s.paying()));
        parkedValue.setText(String.format("%,d", s.parked()));
        awayValue.setText(String.format("%,d", s.turnedAway()));
        freeValue.setText(String.format("%,d", s.totalSpaces() - s.parked()));
        averageValue.setText(String.format("%.1f min", s.averageWaitMinutes()));
        longestValue.setText(String.format("%.1f min", s.longestWaitMinutes()));
        peakValue.setText(String.format("%,d", s.peakLine()));
        fullValue.setText(String.format("%,d", s.foundFull()));
        gaveUpValue.setText(String.format("%,d", s.gaveUp()));
        feesValue.setText(String.format("$%,.0f", s.parked() * ParkingConfig.FEE_PER_CAR));
        lostValue.setText(String.format("$%,.0f", s.turnedAway() * ParkingConfig.FEE_PER_CAR));
        costValue.setText(String.format("$%,.2f", c.totalAttendants() * ParkingConfig.WAGE_PER_HOUR * minutes / 60));
        tableModel.update(s);
        if (s.state() == ParkingSimulation.State.RUNNING || s.state() == ParkingSimulation.State.FINISHED) {
            chart.add(minutes, s.waiting() + s.outside());
        }
    }

    // ------------------------------------------------------------------ per-lot table

    private static final class LotTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Lot", "Parked", "Free", "Lane", "Out", "Away", "Wait"};
        private ParkingSnapshot snapshot;

        void update(ParkingSnapshot snapshot) {
            boolean sizeChanged = this.snapshot == null || this.snapshot.lots().size() != snapshot.lots().size();
            this.snapshot = snapshot;
            if (sizeChanged) {
                fireTableDataChanged();
            } else {
                fireTableRowsUpdated(0, Math.max(0, snapshot.lots().size() - 1));
            }
        }

        @Override
        public int getRowCount() {
            return snapshot == null ? 0 : snapshot.lots().size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ParkingSnapshot.LotSnapshot lot = snapshot.lots().get(row);
            return switch (column) {
                case 0 -> lot.name();
                case 1 -> String.format("%,d", lot.parked());
                case 2 -> String.format("%,d", lot.free());
                case 3 -> String.format("%,d", lot.lane());
                case 4 -> String.format("%,d", lot.outside());
                case 5 -> String.format("%,d", lot.turnedAway());
                default -> String.format("%.1f m", lot.averageWaitMinutes());
            };
        }
    }
}
