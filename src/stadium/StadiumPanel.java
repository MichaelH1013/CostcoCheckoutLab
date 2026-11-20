package stadium;

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
 * The whole window content: parameter controls on the left, the stadium in the middle, live
 * statistics on the right. A Swing timer redraws the stadium about 30 times a second and refreshes
 * the numbers about four times a second. The simulation threads never touch Swing; the timer only
 * reads their thread-safe counters.
 */
public final class StadiumPanel extends JPanel {

    private static final String CUSTOM = "Custom";

    private final StadiumView view = new StadiumView();
    private final HistoryChart chart = new HistoryChart();

    private final JComboBox<Object> presetBox = new JComboBox<>();
    private final JSpinner gatesSpinner = intSpinner(8, 1, 200, 1);
    private final JSpinner scannersSpinner = intSpinner(2, 1, 100, 1);
    private final JSpinner fansSpinner = intSpinner(4000, 1, 1_000_000, 100);
    private final JSpinner minutesSpinner = decimalSpinner(60, 1, 1000, 5);
    private final JSpinner serviceSpinner = decimalSpinner(7, 0.5, 600, 0.5);
    private final JSpinner queueSpinner = intSpinner(40, 1, 10_000, 5);
    private final JSpinner crowdSpinner = intSpinner(0, 0, 1_000_000, 10);
    private final JSpinner producersSpinner = intSpinner(2, 1, 32, 1);
    private final JSpinner walkSpinner = decimalSpinner(45, 0, 3600, 5);
    private final JSpinner biasSpinner = decimalSpinner(1, 0, 10, 0.5);
    private final JComboBox<QueueStrategy> strategyBox = new JComboBox<>(QueueStrategy.values());
    private final JComboBox<ArrivalShape> shapeBox = new JComboBox<>(ArrivalShape.values());
    private final JSlider speedSlider = new JSlider(0, 100, 50);
    private final JLabel speedLabel = new JLabel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resetButton = new JButton("Reset");
    private final JLabel statusLabel = new JLabel();

    private final JLabel timeValue = valueLabel();
    private final JLabel createdValue = valueLabel();
    private final JLabel walkingValue = valueLabel();
    private final JLabel laneValue = valueLabel();
    private final JLabel outsideValue = valueLabel();
    private final JLabel scanningValue = valueLabel();
    private final JLabel insideValue = valueLabel();
    private final JLabel averageValue = valueLabel();
    private final JLabel longestValue = valueLabel();
    private final JLabel peakValue = valueLabel();
    private final JLabel fullValue = valueLabel();
    private final JLabel gaveUpValue = valueLabel();
    private final JLabel rateValue = valueLabel();
    private final JLabel costValue = valueLabel();
    private final GateTableModel tableModel = new GateTableModel();

    private final ChangeListener parameterListener = e -> onParameterChanged();
    private final Timer timer;
    private StadiumSimulation sim;
    private String configError;
    private boolean loading = false;
    private int ticks = 0;
    private StadiumSimulation.State shownState;

    public StadiumPanel() {
        super(new BorderLayout());
        presetBox.addItem(CUSTOM);
        for (StadiumPreset preset : StadiumPreset.values()) {
            presetBox.addItem(preset);
        }
        for (JSpinner spinner : new JSpinner[] {gatesSpinner, scannersSpinner, fansSpinner, minutesSpinner,
                serviceSpinner, queueSpinner, crowdSpinner, producersSpinner, walkSpinner, biasSpinner}) {
            spinner.addChangeListener(parameterListener);
        }
        strategyBox.addActionListener(e -> onParameterChanged());
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
        add(controls, BorderLayout.WEST);
        add(view, BorderLayout.CENTER);
        add(buildStats(), BorderLayout.EAST);

        timer = new Timer(33, e -> tick());
        loading = true;
        presetBox.setSelectedItem(StadiumPreset.FOOTBALL);
        loading = false;
        loadConfig(StadiumPreset.FOOTBALL.config());
        timer.start();
    }

    // ------------------------------------------------------------------ building the window

    private JPanel buildControls() {
        JPanel panel = new ControlsPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int y = 0;
        y = section(panel, y, "Event");
        y = wideRow(panel, y, "Preset", presetBox, "Fills in all the numbers below for a typical event.");
        y = wideRow(panel, y, "Line strategy", strategyBox,
                "Assigned gate: each fan goes to a gate picked at random (popular gates get more fans). "
                        + "Shortest line: each fan picks the gate with the fewest people. "
                        + "Shared line: one line feeds every scanner.");
        y = wideRow(panel, y, "Arrival pattern", shapeBox, "When fans show up during the arrival window.");

        y = section(panel, y, "Stadium");
        y = row(panel, y, "Gates", gatesSpinner, "Number of entry points around the stadium.");
        y = row(panel, y, "Scanners per gate", scannersSpinner,
                "Scanner threads at each gate. Each one lets in one fan at a time.");
        y = row(panel, y, "Lane capacity", queueSpinner,
                "Fans that fit in one lane. Extra fans wait outside it. A shared line holds this times the gates.");
        y = row(panel, y, "Crowd limit (0 = none)", crowdSpinner,
                "Most fans allowed to wait outside a full lane at each gate. A fan who arrives when it is full "
                        + "gives up and is counted. 0 means no limit.");
        y = row(panel, y, "Scan time (sec)", serviceSpinner,
                "Average seconds to scan a ticket and check a bag. Each fan takes 0.5x to 1.5x of this.");

        y = section(panel, y, "Fans");
        y = row(panel, y, "Fans", fansSpinner, "How many fans arrive in total.");
        y = row(panel, y, "Arrival window (min)", minutesSpinner, "Simulated minutes over which the fans arrive.");
        y = row(panel, y, "Walk time (sec)", walkSpinner, "Simulated seconds a fan walks from the approach to the line.");
        y = row(panel, y, "Gate bias", biasSpinner,
                "0 = every gate is equally popular. 1 = gate 1 draws twice as many fans as the last gate. "
                        + "Only used by the assigned gate strategy.");
        y = row(panel, y, "Producer threads", producersSpinner,
                "Threads that create fans and walk them to the gates. A fan is not a thread, so a few are plenty.");

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
        addStat(numbers, "Fans created", createdValue);
        addStat(numbers, "Walking to gates", walkingValue);
        addStat(numbers, "In lanes", laneValue);
        addStat(numbers, "Waiting outside lanes", outsideValue);
        addStat(numbers, "Being scanned", scanningValue);
        addStat(numbers, "Inside the stadium", insideValue);
        addStat(numbers, "Average wait", averageValue);
        addStat(numbers, "Longest wait", longestValue);
        addStat(numbers, "Longest line", peakValue);
        addStat(numbers, "Found the lane full", fullValue);
        addStat(numbers, "Gave up (crowd full)", gaveUpValue);
        addStat(numbers, "Admitted per minute", rateValue);
        addStat(numbers, "Scanner cost so far", costValue);

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
        table.getColumnModel().getColumn(0).setMinWidth(58);
        table.getColumnModel().getColumn(0).setPreferredWidth(62);
        JScrollPane tableScroll = new JScrollPane(table);

        JTextArea key = new JTextArea("Per gate: Lane = fans in its lane, Out = fans waiting outside the full lane, "
                + "Busy = scanners with a fan, In = fans admitted, Wait = average wait in minutes, "
                + "/min = fans admitted per simulated minute.");
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

    private StadiumConfig readConfig() {
        return StadiumConfig.builder()
                .gates(intOf(gatesSpinner))
                .scannersPerGate(intOf(scannersSpinner))
                .fans(intOf(fansSpinner))
                .arrivalMinutes(doubleOf(minutesSpinner))
                .serviceSeconds(doubleOf(serviceSpinner))
                .queueCapacity(intOf(queueSpinner))
                .crowdLimit(intOf(crowdSpinner))
                .strategy((QueueStrategy) strategyBox.getSelectedItem())
                .shape((ArrivalShape) shapeBox.getSelectedItem())
                .producerThreads(intOf(producersSpinner))
                .walkSeconds(doubleOf(walkSpinner))
                .gateBias(doubleOf(biasSpinner))
                .speed(speedFromSlider())
                .seed(311)
                .build();
    }

    private static int intOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static double doubleOf(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private void loadConfig(StadiumConfig c) {
        loading = true;
        gatesSpinner.setValue(c.gates());
        scannersSpinner.setValue(c.scannersPerGate());
        fansSpinner.setValue(c.fans());
        minutesSpinner.setValue(c.arrivalMinutes());
        serviceSpinner.setValue(c.serviceSeconds());
        queueSpinner.setValue(c.queueCapacity());
        crowdSpinner.setValue(c.crowdLimit());
        producersSpinner.setValue(c.producerThreads());
        walkSpinner.setValue(c.walkSeconds());
        biasSpinner.setValue(c.gateBias());
        strategyBox.setSelectedItem(c.strategy());
        shapeBox.setSelectedItem(c.shape());
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
        return sim == null || sim.getState() == StadiumSimulation.State.NEW;
    }

    private void onPresetChosen() {
        if (loading || !(presetBox.getSelectedItem() instanceof StadiumPreset preset) || !canEditParameters()) {
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
        if (sim != null && sim.getState() != StadiumSimulation.State.NEW) {
            sim.setSpeed(speed);
        } else {
            rebuildPreview();
        }
    }

    /** Builds an idle simulation from the current numbers so the stadium shows the gates before Start. */
    private void rebuildPreview() {
        StadiumConfig config;
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
        sim = new StadiumSimulation(config);
        view.setSimulation(sim);
        chart.clear();
        shownState = null;
        refreshStats();
        showState();
    }

    private void start() {
        if (sim == null || sim.getState() != StadiumSimulation.State.NEW) {
            return;
        }
        sim.start();
        showState();
    }

    private void togglePause() {
        if (sim == null) {
            return;
        }
        if (sim.getState() == StadiumSimulation.State.RUNNING) {
            sim.pause();
        } else if (sim.getState() == StadiumSimulation.State.PAUSED) {
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
        StadiumSimulation.State state = sim == null ? StadiumSimulation.State.NEW : sim.getState();
        shownState = state;
        boolean editable = state == StadiumSimulation.State.NEW;
        for (JComponent c : new JComponent[] {presetBox, strategyBox, shapeBox, gatesSpinner, scannersSpinner,
                fansSpinner, minutesSpinner, serviceSpinner, queueSpinner, crowdSpinner, producersSpinner, walkSpinner, biasSpinner}) {
            c.setEnabled(editable);
        }
        startButton.setEnabled(state == StadiumSimulation.State.NEW && configError == null);
        pauseButton.setEnabled(state == StadiumSimulation.State.RUNNING || state == StadiumSimulation.State.PAUSED);
        pauseButton.setText(state == StadiumSimulation.State.PAUSED ? "Resume" : "Pause");
        if (configError != null) {
            setStatus("#B3261E", configError);
            return;
        }
        switch (state) {
            case NEW -> setStatus("#26323C", "Ready. Change the numbers if you like, then press Start.");
            case RUNNING -> setStatus("#1E7B46", "Running. You can change the speed or pause at any time.");
            case PAUSED -> setStatus("#8A5A00", "Paused.");
            case FINISHED -> setStatus("#1E7B46", String.format(
                    "Finished: all %,d fans are inside after %s. Press Reset to run again.",
                    sim.getConfig().fans(), StadiumView.clock(sim.getFinishedAt())));
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
        StadiumSnapshot s = sim.snapshot();
        StadiumConfig c = sim.getConfig();
        timeValue.setText(StadiumView.clock(s.simSeconds()));
        createdValue.setText(String.format("%,d / %,d", s.spawned(), s.totalFans()));
        walkingValue.setText(String.format("%,d", s.walking()));
        laneValue.setText(String.format("%,d", s.waiting()));
        outsideValue.setText(String.format("%,d", s.outside()));
        scanningValue.setText(String.format("%,d", s.scanning()));
        insideValue.setText(String.format("%,d", s.admitted()));
        averageValue.setText(String.format("%.1f min", s.averageWaitMinutes()));
        longestValue.setText(String.format("%.1f min", s.longestWaitMinutes()));
        peakValue.setText(String.format("%,d", s.peakLine()));
        fullValue.setText(String.format("%,d", s.foundFull()));
        gaveUpValue.setText(String.format("%,d", s.gaveUp()));
        double minutes = s.simMinutes();
        rateValue.setText(minutes > 0 ? String.format("%,.1f", s.admitted() / minutes) : "-");
        costValue.setText(String.format("$%,.2f", c.totalScanners() * StadiumConfig.WAGE_PER_HOUR * minutes / 60));
        tableModel.update(s);
        if (s.state() == StadiumSimulation.State.RUNNING || s.state() == StadiumSimulation.State.FINISHED) {
            chart.add(minutes, s.waiting() + s.outside());
        }
    }

    // ------------------------------------------------------------------ per-gate table

    private static final class GateTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Gate", "Lane", "Out", "Busy", "In", "Wait", "/min"};
        private StadiumSnapshot snapshot;

        void update(StadiumSnapshot snapshot) {
            boolean sizeChanged = this.snapshot == null || this.snapshot.gates().size() != snapshot.gates().size();
            this.snapshot = snapshot;
            if (sizeChanged) {
                fireTableDataChanged();
            } else {
                fireTableRowsUpdated(0, Math.max(0, snapshot.gates().size() - 1));
            }
        }

        @Override
        public int getRowCount() {
            return snapshot == null ? 0 : snapshot.gates().size();
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
            StadiumSnapshot.GateSnapshot gate = snapshot.gates().get(row);
            boolean ownLines = snapshot.lines().size() == snapshot.gates().size();
            StadiumSnapshot.LineSnapshot line = ownLines ? snapshot.lines().get(row) : null;
            return switch (column) {
                case 0 -> gate.name();
                case 1 -> line == null ? "-" : String.format("%,d", line.lane());
                case 2 -> line == null ? "-" : String.format("%,d", line.outside());
                case 3 -> gate.busyScanners() + "/" + gate.scanners();
                case 4 -> String.format("%,d", gate.admitted());
                case 5 -> String.format("%.1f m", gate.averageWaitMinutes());
                default -> String.format("%.1f", gate.perMinute());
            };
        }
    }
}
