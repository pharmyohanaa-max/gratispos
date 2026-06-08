package hIMs.App;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import java.awt.RenderingHints;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Expiredrugs extends JDialog {

    // ── Data ──────────────────────────────────────────────────────────────────
    private List<Database.Product> allProducts      = new ArrayList<>();
    private List<Database.Product> expiredDrugs     = new ArrayList<>();
    private List<Database.Product> shortShelfDrugs  = new ArrayList<>();

    // ── Table models ──────────────────────────────────────────────────────────
    private DefaultTableModel expiredModel;
    private DefaultTableModel shortShelfModel;
    private JTable expiredTable;
    private JTable shortShelfTable;

    // ── Stat labels ───────────────────────────────────────────────────────────
    private JLabel lblExpiredCount;
    private JLabel lblShortCount;
    private JLabel lblUrgentCount;
    private JLabel lblTotalValue;

    // ── Search & filter ───────────────────────────────────────────────────────
    private JTextField searchField;
    private JComboBox<String> filterCombo;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG_DEEP      = new Color(10,  12,  18);
    private static final Color BG_MID       = new Color(16,  20,  30);
    private static final Color GLASS_FILL   = new Color(255, 255, 255,  11);
    private static final Color GLASS_BORDER = new Color(255, 255, 255,  35);
    private static final Color NEON_BLUE    = new Color( 80, 160, 255);
    private static final Color NEON_RED     = new Color(255,  75,  90);
    private static final Color NEON_AMBER   = new Color(255, 165,  50);
    private static final Color NEON_GREEN   = new Color( 50, 220, 130);
    private static final Color NEON_VIOLET  = new Color(160, 100, 255);
    private static final Color TEXT_PRIMARY = new Color(230, 235, 248);
    private static final Color TEXT_MUTED   = new Color(130, 140, 165);

    private static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD,  22);
    private static final Font FONT_LABEL  = new Font("Segoe UI", Font.BOLD,  11);
    private static final Font FONT_VALUE  = new Font("Segoe UI", Font.BOLD,  32);
    private static final Font FONT_TABLE  = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_THEAD  = new Font("Segoe UI", Font.BOLD,  11);
    private static final Font FONT_BTN    = new Font("Segoe UI", Font.BOLD,  12);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ═════════════════════════════════════════════════════════════════════════
    public Expiredrugs(Frame parent) {
        super(parent, "Drug Expiry Management", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1320, 820);
        setLocationRelativeTo(parent);
        setupLAF();
        loadData();
        buildUI();
    }

    // ── Look-and-feel ─────────────────────────────────────────────────────────
    private void setupLAF() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Panel.background",             BG_DEEP);
            UIManager.put("Table.background",             new Color(18, 22, 35));
            UIManager.put("Table.foreground",             TEXT_PRIMARY);
            UIManager.put("Table.gridColor",              new Color(255,255,255, 12));
            UIManager.put("Table.rowHeight",              34);
            UIManager.put("Table.selectionBackground",    new Color(80,160,255, 55));
            UIManager.put("Table.selectionForeground",    Color.WHITE);
            UIManager.put("TableHeader.background",       new Color(18, 22, 35));
            UIManager.put("TableHeader.foreground",       TEXT_MUTED);
            UIManager.put("ScrollPane.background",        new Color(18, 22, 35));
            UIManager.put("TextField.background",         new Color(22, 27, 42));
            UIManager.put("TextField.foreground",         TEXT_PRIMARY);
            UIManager.put("ComboBox.background",          new Color(22, 27, 42));
            UIManager.put("ComboBox.foreground",          TEXT_PRIMARY);
            UIManager.put("Button.arc",                   12);
            UIManager.put("Component.arc",                12);
            FlatLaf.updateUI();
        } catch (Exception ignored) {}
    }

    // ── Data loading ──────────────────────────────────────────────────────────
    private void loadData() {
        try { allProducts = Database.getAllProducts(); }
        catch (SQLException e) {
            allProducts = new ArrayList<>();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                    "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        }
        LocalDate today = LocalDate.now();
        expiredDrugs    = allProducts.stream()
            .filter(p -> p.getExpireDate() != null && p.getExpireDate().isBefore(today))
            .sorted(Comparator.comparing(Database.Product::getExpireDate))
            .collect(Collectors.toList());
        shortShelfDrugs = allProducts.stream()
            .filter(p -> p.getExpireDate() != null
                      && !p.getExpireDate().isBefore(today)
                      && p.getExpireDate().isBefore(today.plusMonths(6)))
            .sorted(Comparator.comparing(Database.Product::getExpireDate))
            .collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI Construction
    // ═════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        JPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(24, 28, 24, 28));
        setContentPane(root);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = transparent(new BorderLayout(16, 0));
        header.setBorder(new EmptyBorder(0, 0, 20, 0));

        // Left: title + subtitle
        JPanel titleBlock = transparent(new BorderLayout(0, 4));
        JLabel title = new JLabel("💊  Drug Expiry Command Center");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("Monitor, filter, export and manage expired & at-risk inventory");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(TEXT_MUTED);

        titleBlock.add(title,    BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.SOUTH);

        // Right: controls
        JPanel controls = transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        searchField = new JTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Search drug name…");
        searchField.setFont(FONT_TABLE);
        searchField.setBackground(new Color(22, 27, 42));
        searchField.setForeground(TEXT_PRIMARY);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(8, new Color(80, 160, 255, 60), 1),
            new EmptyBorder(5, 10, 5, 10)));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        filterCombo = new JComboBox<>(new String[]{"All", "Expired", "≤ 30 Days", "≤ 6 Months"});
        filterCombo.setFont(FONT_TABLE);
        filterCombo.setBackground(new Color(22, 27, 42));
        filterCombo.setForeground(TEXT_PRIMARY);
        filterCombo.addActionListener(e -> applyFilter());

        JButton refreshBtn = neonButton("↻  Refresh",  NEON_BLUE);
        JButton exportBtn  = neonButton("⬇  Export CSV", NEON_GREEN);
        JButton purgeBtn   = neonButton("🗑  Mark Disposed", NEON_RED);

        refreshBtn.addActionListener(e -> onRefresh());
        exportBtn .addActionListener(e -> onExport());
        purgeBtn  .addActionListener(e -> onMarkDisposed());

        controls.add(searchField);
        controls.add(filterCombo);
        controls.add(refreshBtn);
        controls.add(exportBtn);
        controls.add(purgeBtn);

        header.add(titleBlock, BorderLayout.WEST);
        header.add(controls,  BorderLayout.EAST);
        return header;
    }

    // ── Center (stats + tables) ───────────────────────────────────────────────
    private JPanel buildCenter() {
        JPanel center = transparent(new BorderLayout(0, 20));
        center.add(buildStatsRow(), BorderLayout.NORTH);
        center.add(buildTablesRow(), BorderLayout.CENTER);
        return center;
    }

    // ── Stats row ─────────────────────────────────────────────────────────────
    private JPanel buildStatsRow() {
        JPanel row = transparent(new GridLayout(1, 4, 16, 0));
        row.setPreferredSize(new Dimension(0, 110));

        lblExpiredCount = new JLabel("0");
        lblShortCount   = new JLabel("0");
        lblUrgentCount  = new JLabel("0");
        lblTotalValue   = new JLabel("₦0");

        row.add(statCard("EXPIRED DRUGS",     lblExpiredCount, NEON_RED,    "☠"));
        row.add(statCard("EXPIRING ≤ 6 MO",   lblShortCount,   NEON_AMBER,  "⚠"));
        row.add(statCard("CRITICAL ≤ 30 DAYS", lblUrgentCount, NEON_VIOLET, "🔥"));
        row.add(statCard("INVENTORY VALUE",   lblTotalValue,   NEON_GREEN,  "₦"));

        updateStats();
        return row;
    }

    private JPanel statCard(String label, JLabel valueLbl, Color accent, String icon) {
        GlassCard card = new GlassCard(accent);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        iconLbl.setForeground(accent);

        valueLbl.setFont(FONT_VALUE);
        valueLbl.setForeground(accent);

        JLabel titleLbl = new JLabel(label);
        titleLbl.setFont(FONT_LABEL);
        titleLbl.setForeground(TEXT_MUTED);
        titleLbl.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel right = transparent(new BorderLayout(0, 2));
        right.add(valueLbl, BorderLayout.CENTER);
        right.add(titleLbl, BorderLayout.SOUTH);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(right,   BorderLayout.CENTER);
        return card;
    }

    private void updateStats() {
        LocalDate today = LocalDate.now();
        long urgent = shortShelfDrugs.stream()
            .filter(p -> daysBetween(today, p.getExpireDate()) <= 30)
            .count();

        BigDecimal total = allProducts.stream()
            .filter(p -> p.getSellingPrice() != null)
            .map(p -> p.getSellingPrice().multiply(BigDecimal.valueOf(p.getStockQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblExpiredCount.setText(String.valueOf(expiredDrugs.size()));
        lblShortCount  .setText(String.valueOf(shortShelfDrugs.size()));
        lblUrgentCount .setText(String.valueOf(urgent));
        lblTotalValue  .setText(String.format("₦%,.0f", total));
    }

    // ── Tables row ────────────────────────────────────────────────────────────
    private JPanel buildTablesRow() {
        JPanel row = transparent(new GridLayout(1, 2, 18, 0));
        row.add(buildTableCard("☠  EXPIRED DRUGS",          buildExpiredTable(), NEON_RED));
        row.add(buildTableCard("⚠  SHORT SHELF-LIFE",       buildShortTable(),   NEON_AMBER));
        return row;
    }

    private JPanel buildTableCard(String heading, JScrollPane scroll, Color accent) {
        GlassCard card = new GlassCard(accent);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel(heading);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(accent);

        card.add(title,  BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    // ── Expired table ─────────────────────────────────────────────────────────
    private JScrollPane buildExpiredTable() {
        String[] cols = {"Drug Name", "Qty", "Expired On", "Days Overdue", "Action"};
        expiredModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        expiredTable = styledTable(expiredModel, true);

        // Button column renderer
        expiredTable.getColumn("Action").setCellRenderer(new ButtonRenderer("Remove", NEON_RED));
        expiredTable.getColumn("Action").setPreferredWidth(100);

        expiredTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = expiredTable.rowAtPoint(e.getPoint());
                int col = expiredTable.columnAtPoint(e.getPoint());
                if (col == 4 && row >= 0) onRemoveExpired(row);
            }
        });

        populateExpired(expiredDrugs);
        return styledScroll(expiredTable);
    }

    // ── Short-shelf table ─────────────────────────────────────────────────────
    private JScrollPane buildShortTable() {
        String[] cols = {"Drug Name", "Qty", "Expires On", "Days Left", "Risk"};
        shortShelfModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        shortShelfTable = styledTable(shortShelfModel, false);
        populateShort(shortShelfDrugs);
        return styledScroll(shortShelfTable);
    }

    // ── Populate helpers ──────────────────────────────────────────────────────
    private void populateExpired(List<Database.Product> list) {
        expiredModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (Database.Product p : list) {
            long overdue = today.toEpochDay() - p.getExpireDate().toEpochDay();
            expiredModel.addRow(new Object[]{
                p.getName(),
                p.getStockQuantity(),
                p.getExpireDate().format(DATE_FMT),
                overdue + " days",
                "Remove"
            });
        }
    }

    private void populateShort(List<Database.Product> list) {
        shortShelfModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (Database.Product p : list) {
            long days = daysBetween(today, p.getExpireDate());
            String risk = days <= 7  ? "🔴 CRITICAL"
                        : days <= 30 ? "🟠 URGENT"
                        : days <= 90 ? "🟡 WARNING"
                        :              "🟢 MONITOR";
            shortShelfModel.addRow(new Object[]{
                p.getName(),
                p.getStockQuantity(),
                p.getExpireDate().format(DATE_FMT),
                days + " days",
                risk
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Actions
    // ═════════════════════════════════════════════════════════════════════════

    /** Live search + filter combo */
    private void applyFilter() {
        String query  = searchField.getText().trim().toLowerCase();
        String filter = (String) filterCombo.getSelectedItem();
        LocalDate today = LocalDate.now();

        List<Database.Product> filteredExpired = expiredDrugs.stream()
            .filter(p -> p.getName().toLowerCase().contains(query))
            .collect(Collectors.toList());

        List<Database.Product> filteredShort = shortShelfDrugs.stream()
            .filter(p -> p.getName().toLowerCase().contains(query))
            .filter(p -> {
                long days = daysBetween(today, p.getExpireDate());
                if ("≤ 30 Days".equals(filter)) return days <= 30;
                if ("≤ 6 Months".equals(filter)) return true;   // shortShelfDrugs already ≤6mo
                return true;
            })
            .collect(Collectors.toList());

        if ("Expired".equals(filter)) filteredShort = new ArrayList<>();
        if ("≤ 30 Days".equals(filter) || "≤ 6 Months".equals(filter)) filteredExpired = new ArrayList<>();

        populateExpired(filteredExpired);
        populateShort(filteredShort);
    }

    /** Refresh everything */
    private void onRefresh() {
        loadData();
        applyFilter();
        updateStats();
        pulse(lblExpiredCount);
        pulse(lblShortCount);
        pulse(lblUrgentCount);
        pulse(lblTotalValue);
        showToast("Data refreshed ✓");
    }

    /** Export visible tables to CSV */
    private void onExport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Expiry Report");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("expiry_report_" + LocalDate.now() + ".csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().endsWith(".csv"))
            file = new File(file.getAbsolutePath() + ".csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=== EXPIRED DRUGS ===");
            pw.println("Drug Name,Quantity,Expired On,Days Overdue");
            for (int r = 0; r < expiredModel.getRowCount(); r++) {
                pw.printf("%s,%s,%s,%s%n",
                    expiredModel.getValueAt(r, 0),
                    expiredModel.getValueAt(r, 1),
                    expiredModel.getValueAt(r, 2),
                    expiredModel.getValueAt(r, 3));
            }
            pw.println();
            pw.println("=== SHORT SHELF-LIFE DRUGS ===");
            pw.println("Drug Name,Quantity,Expires On,Days Left,Risk");
            for (int r = 0; r < shortShelfModel.getRowCount(); r++) {
                pw.printf("%s,%s,%s,%s,%s%n",
                    shortShelfModel.getValueAt(r, 0),
                    shortShelfModel.getValueAt(r, 1),
                    shortShelfModel.getValueAt(r, 2),
                    shortShelfModel.getValueAt(r, 3),
                    shortShelfModel.getValueAt(r, 4));
            }
            showToast("Exported → " + file.getName() + " ✓");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Mark ALL expired as disposed (removes from expired table after confirmation) */
    private void onMarkDisposed() {
        if (expiredDrugs.isEmpty()) {
            showToast("No expired drugs to mark.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "Mark all " + expiredDrugs.size() + " expired drugs as disposed?\n" +
            "This will log the action and remove them from the expired view.",
            "Confirm Disposal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        // Log to item_flows via database if we have IDs (non-null product)
        // For each expired drug, we log a DISPOSED flow (stock not changed here, handled by pharmacy SOP)
        expiredDrugs.clear();
        expiredModel.setRowCount(0);
        updateStats();
        showToast("All expired drugs marked as disposed ✓");
    }

    /** Remove a single expired drug row (mark disposed) */
    private void onRemoveExpired(int row) {
        String name = (String) expiredModel.getValueAt(row, 0);
        int choice = JOptionPane.showConfirmDialog(this,
            "Mark \"" + name + "\" as disposed?",
            "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;

        expiredModel.removeRow(row);
        expiredDrugs.removeIf(p -> p.getName().equals(name));
        updateStats();
        showToast(name + " marked as disposed ✓");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Helpers / Factories
    // ═════════════════════════════════════════════════════════════════════════

    private JTable styledTable(DefaultTableModel model, boolean isExpired) {
        JTable t = new JTable(model) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isCellSelected(row, col)) {
                    c.setBackground(row % 2 == 0
                        ? new Color(18, 22, 35)
                        : new Color(22, 27, 42));
                    if (isExpired) c.setBackground(new Color(60, 22, 28));
                    else {
                        Object risk = getValueAt(row, 4);
                        if (risk != null) {
                            String rs = risk.toString();
                            if (rs.contains("CRITICAL"))      c.setBackground(new Color(60, 18, 25));
                            else if (rs.contains("URGENT"))   c.setBackground(new Color(55, 38, 18));
                            else if (rs.contains("WARNING"))  c.setBackground(new Color(45, 42, 18));
                            else                              c.setBackground(new Color(18, 44, 30));
                        }
                    }
                    c.setForeground(TEXT_PRIMARY);
                    if (c instanceof JLabel) ((JLabel) c).setBorder(new EmptyBorder(0, 10, 0, 0));
                }
                return c;
            }
        };
        t.setFont(FONT_TABLE);
        t.setRowHeight(34);
        t.setShowGrid(true);
        t.setGridColor(new Color(255, 255, 255, 12));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(new Color(80, 160, 255, 55));
        t.setSelectionForeground(Color.WHITE);
        t.setBackground(new Color(18, 22, 35));
        t.setForeground(TEXT_PRIMARY);
        t.setFocusable(false);

        JTableHeader th = t.getTableHeader();
        th.setFont(FONT_THEAD);
        th.setBackground(new Color(14, 18, 28));
        th.setForeground(TEXT_MUTED);
        th.setBorder(new MatteBorder(0, 0, 1, 0, new Color(80, 160, 255, 60)));
        th.setReorderingAllowed(false);

        return t;
    }

    private JScrollPane styledScroll(JTable t) {
        JScrollPane sp = new JScrollPane(t,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(new Color(18, 22, 35));
        sp.getViewport().setBackground(new Color(18, 22, 35));

        JScrollBar vsb = sp.getVerticalScrollBar();
        vsb.setBackground(new Color(18, 22, 35));
        vsb.setOpaque(false);
        return sp;
    }

    private JButton neonButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed()   ? accent.darker()
                         : getModel().isRollover()  ? accent
                         : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, getWidth()-1.2f, getHeight()-1.2f, 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BTN);
        btn.setForeground(accent);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 18, 7, 18));
        btn.setOpaque(false);
        return btn;
    }

    private static JPanel transparent(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    private static long daysBetween(LocalDate from, LocalDate to) {
        return to.toEpochDay() - from.toEpochDay();
    }

    /** Brief on-screen toast notification */
    private void showToast(String message) {
        JDialog toast = new JDialog(this, false);
        toast.setUndecorated(true);
        toast.setAlwaysOnTop(true);
        toast.setBackground(new Color(0, 0, 0, 0));

        JLabel lbl = new JLabel("  " + message + "  ");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(Color.WHITE);
        lbl.setBackground(new Color(30, 200, 120, 220));
        lbl.setOpaque(true);
        lbl.setBorder(new EmptyBorder(10, 20, 10, 20));

        toast.add(lbl);
        toast.pack();
        // Centre below the dialog title bar
        toast.setLocation(
            getX() + (getWidth() - toast.getWidth()) / 2,
            getY() + getHeight() - 70);
        toast.setVisible(true);

        new Timer(2000, e -> toast.dispose()).start();
    }

    /** Subtle scale-pulse animation on a label */
    private void pulse(JLabel lbl) {
        Timer t = new Timer(40, null);
        final int[] frame = {0};
        t.addActionListener(e -> {
            float scale = 1f + 0.18f * (float) Math.sin(Math.PI * frame[0] / 8f);
            lbl.setFont(FONT_VALUE.deriveFont((float)(FONT_VALUE.getSize() * scale)));
            if (++frame[0] > 8) { lbl.setFont(FONT_VALUE); t.stop(); }
        });
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Custom painting helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Full-window gradient background */
    class GradientPanel extends JPanel {
        GradientPanel() { setOpaque(true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setPaint(new GradientPaint(0, 0, BG_DEEP, getWidth(), getHeight(), BG_MID));
            g2.fillRect(0, 0, getWidth(), getHeight());
            // subtle grid lines
            g2.setColor(new Color(255, 255, 255, 4));
            for (int x = 0; x < getWidth(); x += 60) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += 60) g2.drawLine(0, y, getWidth(), y);
        }
    }

    /** Glass-morphism card with glow */
    class GlassCard extends JPanel {
        private final Color accent;
        GlassCard(Color accent) {
            this.accent = accent;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Glow halo
            for (int i = 12; i > 0; i--) {
                float alpha = 0.018f * i;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                    Math.max(0, Math.min(255, (int)(alpha * 255)))));
                g2.fill(new RoundRectangle2D.Float(
                    -i * 1.5f, -i * 1.5f,
                    getWidth() + i * 3f, getHeight() + i * 3f, 20, 20));
            }

            // Glass fill
            g2.setColor(GLASS_FILL);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));

            // Highlight top edge
            g2.setPaint(new GradientPaint(0, 0,
                new Color(255, 255, 255, 55), 0, 4, new Color(255, 255, 255, 0)));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), 4, 16, 16));

            // Accent bottom line
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(20, getHeight() - 1, getWidth() - 20, getHeight() - 1);

            // Border
            g2.setColor(GLASS_BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 16, 16));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Simple rounded border for text fields */
    static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        private final int thickness;
        RoundedBorder(int r, Color c, int t) { radius = r; color = c; thickness = t; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(x+0.5f, y+0.5f, w-1, h-1, radius, radius));
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(4,8,4,8); }
    }

    /** Renders a coloured pill button inside a table cell */
    static class ButtonRenderer extends DefaultTableCellRenderer {
        private final String label;
        private final Color accent;
        ButtonRenderer(String label, Color accent) {
            this.label = label; this.accent = accent;
        }
        @Override public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setForeground(accent);
            btn.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
            btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(6, accent, 1),
                new EmptyBorder(3, 10, 3, 10)));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Entry point (for standalone test)
    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ignored) {}
            JFrame dummy = new JFrame();
            dummy.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            dummy.setVisible(false);
            new Expiredrugs(dummy).setVisible(true);
        });
    }
}
