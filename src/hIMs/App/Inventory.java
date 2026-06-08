package hIMs.App;

import com.toedter.calendar.JCalendar;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Inventory.java — Full Inventory Management Panel
 * Matches Database.java schema 100%
 * Uses JDateChooser (JCalendar library) for all date inputs.
 */
public class Inventory extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(Inventory.class.getName());

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(0xF7F5F0);
    private static final Color PANEL_BG = new Color(0xFFFEFC);
    private static final Color ACCENT   = new Color(0x2D4A3E);
    private static final Color ACCENT2  = new Color(0xC8963E);
    private static final Color TEXT_DARK  = new Color(0x1A1A1A);
    private static final Color TEXT_MID   = new Color(0x4A4A4A);
    private static final Color TEXT_LIGHT = new Color(0x888888);
    private static final Color BORDER_COL = new Color(0xDDD9D0);
    private static final Color ROW_ALT    = new Color(0xF5F3EE);
    private static final Color SEL_ROW    = new Color(0xD6E8DF);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font TITLE_FONT    = new Font("Georgia", Font.BOLD, 26);
    private static final Font SUBTITLE_FONT = new Font("Palatino Linotype", Font.BOLD, 13);
    private static final Font TABLE_FONT    = new Font("Courier New", Font.PLAIN, 12);
    private static final Font LABEL_FONT    = new Font("Palatino Linotype", Font.PLAIN, 13);

    // ── Card colors ───────────────────────────────────────────────────────────
    private static final Color[] CARD_COLORS = {
        new Color(0x2D4A3E), new Color(0x7B3F20), new Color(0x1A3A5C),
        new Color(0x5C3A1E), new Color(0x3B2A5C), new Color(0x2C5F2E),
        new Color(0x4A1942), new Color(0x1C3557),
    };
    private static final String[] CARD_FONTS = {
        "Georgia", "Palatino Linotype", "Book Antiqua", "Garamond",
        "Times New Roman", "Century", "Copperplate Gothic Bold", "Cambria",
    };
    private static final String[] CARD_LABELS = {
        "Product View", "Perform Audit", "Audit History",
        "Sale Excess Monitor", "Profit/Loss Audit",
        "Expire Date Updater", "Export PDF", "Settings"
    };
    private static final String[] CARD_ICONS = {
        "\uD83D\uDCE6", "\uD83D\uDD0D", "\uD83D\uDCCB",
        "\uD83D\uDCCA", "\uD83D\uDCB9", "\uD83D\uDCC5",
        "\uD83D\uDCC4", "\u2699"
    };
    private static final String[] CARD_KEYS = {
        "ProductView", "PerformAudit", "AuditHistory",
        "SaleExcess", "ProfitLoss", "ExpireDate", "ExportPdf", "Settings"
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private final String loggedInUser;
    private String businessName = "Gratis Point of Sale";
    private JTextField quickSearchField;
    private JPanel contentArea;
    private CardLayout cardLayout;
    private int selectedCard = 0;
    private JPanel cardStripPanel; // kept for refreshStrip

    // Product view fields
    private JTable productTable;
    private DefaultTableModel productTableModel;
    private JComboBox<String> filterCategoryCombo;
    private JComboBox<String> filterStockCombo;
    private JTextField filterMinPrice, filterMaxPrice;
    private JComboBox<String> sortCombo;

    // Audit summary labels (used across perform-audit dialog)
    private JLabel totalAuditedLbl, totalLossLbl, totalBalancedLbl, totalAddedLbl;

    // Settings fields
    private final Map<String, JTextField> settingFields = new LinkedHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────────────
    public Inventory(String loggedInUser) {
        this.loggedInUser = loggedInUser;
        loadBusinessName();
        initUI();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD BUSINESS NAME
    // ─────────────────────────────────────────────────────────────────────────
    private void loadBusinessName() {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT business_name FROM users WHERE username = ? LIMIT 1")) {
            ps.setString(1, loggedInUser);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String bn = rs.getString("business_name");
                if (bn != null && !bn.trim().isEmpty()) businessName = bn.trim();
            }
        } catch (SQLException ignored) {}
        // fallback: app_settings
        if ("Gratis Point of Sale".equals(businessName)) {
            String v = getSetting("business_name", "");
            if (!v.isEmpty()) businessName = v;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN UI
    // ─────────────────────────────────────────────────────────────────────────
    private void initUI() {
        setTitle("Inventory Management — " + businessName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1240, 780);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));

        cardStripPanel = buildCardStrip();
        body.add(cardStripPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(BG);
        contentArea.add(buildProductViewPanel(), "ProductView");
        contentArea.add(buildPerformAuditPanel(), "PerformAudit");
        contentArea.add(buildAuditHistoryPanel(), "AuditHistory");
        contentArea.add(buildSaleExcessPanel(), "SaleExcess");
        contentArea.add(buildProfitLossPanel(), "ProfitLoss");
        contentArea.add(buildExpireDatePanel(), "ExpireDate");
        contentArea.add(buildExportPdfPanel(), "ExportPdf");
        contentArea.add(buildSettingsPanel(), "Settings");

        body.add(contentArea, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        setVisible(true);
        cardLayout.show(contentArea, "ProductView");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HEADER
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(18, 24, 14, 24));

        JLabel titleLbl = new JLabel(businessName, SwingConstants.CENTER);
        titleLbl.setFont(TITLE_FONT);
        titleLbl.setForeground(new Color(0xF5E6C8));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(titleLbl);
        header.add(Box.createVerticalStrut(4));

        JLabel subLbl = new JLabel("Inventory Management Panel", SwingConstants.CENTER);
        subLbl.setFont(SUBTITLE_FONT);
        subLbl.setForeground(new Color(0xB8C9C0));
        subLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(subLbl);
        header.add(Box.createVerticalStrut(12));

        // Quick search — types here filter Product View immediately
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        searchRow.setBackground(ACCENT);

        JLabel searchIcon = new JLabel("\uD83D\uDD0D");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        searchIcon.setForeground(ACCENT2);

        JLabel searchLbl = new JLabel("Quick Search");
        searchLbl.setFont(new Font("Palatino Linotype", Font.BOLD, 13));
        searchLbl.setForeground(new Color(0xC8C8C8));

        quickSearchField = new JTextField(30);
        quickSearchField.setFont(new Font("Courier New", Font.PLAIN, 13));
        quickSearchField.setBackground(new Color(0x1E3530));
        quickSearchField.setForeground(new Color(0xF0EAD6));
        quickSearchField.setCaretColor(ACCENT2);
        quickSearchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x4A7A68), 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        // Live filter: switch to ProductView and reload on every keystroke
        quickSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onQuickSearch(); }
            public void removeUpdate(DocumentEvent e)  { onQuickSearch(); }
            public void changedUpdate(DocumentEvent e) { onQuickSearch(); }
        });

        searchRow.add(searchIcon);
        searchRow.add(searchLbl);
        searchRow.add(quickSearchField);
        header.add(searchRow);
        return header;
    }

    private void onQuickSearch() {
        // Switch to ProductView and reload with the typed query
        selectedCard = 0;
        cardLayout.show(contentArea, "ProductView");
        refreshStrip(cardStripPanel);
        loadProducts(); // loadProducts() already reads quickSearchField
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARD STRIP
    // ─────────────────────────────────────────────────────────────────────────
    private JPanel buildCardStrip() {
        JPanel strip = new JPanel(new GridLayout(1, CARD_LABELS.length, 8, 0));
        strip.setBackground(BG);
        strip.setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
        for (int i = 0; i < CARD_LABELS.length; i++) {
            final int idx = i;
            JPanel card = createMiniCard(idx);
            card.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectedCard = idx;
                    cardLayout.show(contentArea, CARD_KEYS[idx]);
                    refreshStrip(strip);
                    handleCardClick(idx);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    if (selectedCard != idx) card.setBackground(CARD_COLORS[idx].brighter());
                }
                @Override public void mouseExited(MouseEvent e) {
                    card.setBackground(selectedCard == idx ? CARD_COLORS[idx] : new Color(0xEAE6DE));
                }
            });
            strip.add(card);
        }
        return strip;
    }

    private JPanel createMiniCard(int idx) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(idx == selectedCard ? CARD_COLORS[idx] : new Color(0xEAE6DE));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL, 1),
            BorderFactory.createEmptyBorder(8, 4, 8, 4)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel(CARD_ICONS[idx], SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 17));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lbl = new JLabel("<html><center>" + CARD_LABELS[idx] + "</center></html>", SwingConstants.CENTER);
        lbl.setFont(new Font(CARD_FONTS[idx], Font.BOLD, 10));
        lbl.setForeground(idx == selectedCard ? Color.WHITE : CARD_COLORS[idx]);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(icon);
        card.add(Box.createVerticalStrut(3));
        card.add(lbl);
        return card;
    }

    private void refreshStrip(JPanel strip) {
        Component[] comps = strip.getComponents();
        for (int i = 0; i < comps.length; i++) {
            JPanel c = (JPanel) comps[i];
            c.setBackground(i == selectedCard ? CARD_COLORS[i] : new Color(0xEAE6DE));
            JLabel lbl = (JLabel) c.getComponent(2);
            lbl.setForeground(i == selectedCard ? Color.WHITE : CARD_COLORS[i]);
        }
        strip.repaint();
    }

    private void handleCardClick(int idx) {
        if (idx == 1) showPerformAuditDialog();
        else if (idx == 2) showAuditHistoryDialog();
        // Export PDF (6): panel shows three option-cards, no extra dialog needed
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  PRODUCT VIEW PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildProductViewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        // ── Filter bar ──
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterRow.setBackground(PANEL_BG);
        filterRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        filterRow.add(boldLabel("Category:"));
        filterCategoryCombo = new JComboBox<>(new String[]{"All"});
        filterCategoryCombo.setFont(LABEL_FONT);
        // AUTO-FILTER on change (no Apply needed)
        filterCategoryCombo.addActionListener(e -> loadProducts());
        filterRow.add(filterCategoryCombo);

        filterRow.add(boldLabel("Stock:"));
        filterStockCombo = new JComboBox<>(new String[]{"All", "In Stock", "Low Stock", "Out of Stock"});
        filterStockCombo.setFont(LABEL_FONT);
        // AUTO-FILTER on change
        filterStockCombo.addActionListener(e -> loadProducts());
        filterRow.add(filterStockCombo);

        filterRow.add(boldLabel("Min Price:"));
        filterMinPrice = styledTextField(6);
        filterMinPrice.getDocument().addDocumentListener(docListener(() -> loadProducts()));
        filterRow.add(filterMinPrice);

        filterRow.add(boldLabel("Max Price:"));
        filterMaxPrice = styledTextField(6);
        filterMaxPrice.getDocument().addDocumentListener(docListener(() -> loadProducts()));
        filterRow.add(filterMaxPrice);

        filterRow.add(boldLabel("Sort:"));
        sortCombo = new JComboBox<>(new String[]{"Name", "Selling Price", "Stock Qty", "Date Added"});
        sortCombo.setFont(LABEL_FONT);
        sortCombo.addActionListener(e -> loadProducts());
        filterRow.add(sortCombo);

        JButton dupeBtn = accentButton("Find Duplicates", new Color(0x8B4513));
        dupeBtn.addActionListener(e -> showDuplicatesDialog());
        filterRow.add(dupeBtn);

        JButton valuationBtn = accentButton("Stock Valuation", new Color(0x1A3A5C));
        valuationBtn.addActionListener(e -> showStockValuationDialog());
        filterRow.add(valuationBtn);

        panel.add(filterRow, BorderLayout.NORTH);

        // ── Main tabbed area: All Products + Audit status tabs ──
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Palatino Linotype", Font.BOLD, 12));

        // TAB 0: All Products
        String[] cols = {"ID", "Product Name", "Category", "Vendor",
                         "Cost Price", "Selling Price", "Qty", "Min Stock", "Expire Date", "Added"};
        productTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        productTable = new JTable(productTableModel);
        styleTable(productTable);
        productTable.getColumnModel().getColumn(0).setMaxWidth(50);
        tabs.addTab("All Products", new JScrollPane(productTable));

        // TAB 1: Audited Products
        String[] auditedCols = {"ID", "Product Name", "Category", "Available Stock", "Physical Stock", "Status", "Qty Diff", "Audit Date"};
        DefaultTableModel auditedModel = new DefaultTableModel(auditedCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable auditedTable = new JTable(auditedModel);
        styleTable(auditedTable);
        tabs.addTab("Audited Products", new JScrollPane(auditedTable));

        // TAB 2: Non-Audited Products
        String[] nonAuditedCols = {"ID", "Product Name", "Category", "Stock Qty", "Expire Date"};
        DefaultTableModel nonAuditedModel = new DefaultTableModel(nonAuditedCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable nonAuditedTable = new JTable(nonAuditedModel);
        styleTable(nonAuditedTable);
        tabs.addTab("Non-Audited", new JScrollPane(nonAuditedTable));

        // TAB 3: Balanced Products (audited, no loss)
        String[] balancedCols = {"ID", "Product Name", "Category", "Stock Qty", "Audit Date"};
        DefaultTableModel balancedModel = new DefaultTableModel(balancedCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable balancedTable = new JTable(balancedModel);
        styleTable(balancedTable);
        tabs.addTab("Balanced", new JScrollPane(balancedTable));

        // Populate audit-status tabs on selection
        tabs.addChangeListener(e -> {
            int sel = tabs.getSelectedIndex();
            if (sel == 1) loadAuditedProducts(auditedModel);
            else if (sel == 2) loadNonAuditedProducts(nonAuditedModel);
            else if (sel == 3) loadBalancedProducts(balancedModel);
        });

        panel.add(tabs, BorderLayout.CENTER);

        // Load on build
        loadCategoriesToCombo();
        loadProducts();
        return panel;
    }

    // ── DocumentListener helper ───────────────────────────────────────────────
    private DocumentListener docListener(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { r.run(); }
            public void removeUpdate(DocumentEvent e)  { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    private void loadCategoriesToCombo() {
        try {
            String selected = (String) filterCategoryCombo.getSelectedItem();
            filterCategoryCombo.removeAllItems();
            filterCategoryCombo.addItem("All");
            for (String c : Database.getAllCategories()) filterCategoryCombo.addItem(c);
            if (selected != null) filterCategoryCombo.setSelectedItem(selected);
        } catch (SQLException e) {
            LOGGER.warning("loadCategories: " + e.getMessage());
        }
    }

    private void loadProducts() {
        if (productTableModel == null) return;
        productTableModel.setRowCount(0);
        String catFilter   = (String) filterCategoryCombo.getSelectedItem();
        String stockFilter = (String) filterStockCombo.getSelectedItem();
        String sortBy      = (String) sortCombo.getSelectedItem();
        String minP = filterMinPrice.getText().trim();
        String maxP = filterMaxPrice.getText().trim();
        String quickQ = quickSearchField != null ? quickSearchField.getText().trim().toLowerCase() : "";

        try {
            List<Database.Product> products = Database.getAllProducts();
            List<Database.Category> categories = Database.getAllCategoryObjects();
            Map<Integer, String> catMap = new HashMap<>();
            for (Database.Category c : categories) catMap.put(c.getId(), c.getName());

            // Sort
            switch (sortBy != null ? sortBy : "Name") {
                case "Selling Price":
                    products.sort(Comparator.comparing(p -> p.getSellingPrice() != null ? p.getSellingPrice() : BigDecimal.ZERO));
                    break;
                case "Stock Qty":
                    products.sort(Comparator.comparingInt(Database.Product::getStockQuantity));
                    break;
                case "Date Added":
                    products.sort((a, b) -> {
                        if (a.getCreatedDate() == null) return 1;
                        if (b.getCreatedDate() == null) return -1;
                        return b.getCreatedDate().compareTo(a.getCreatedDate());
                    });
                    break;
                default:
                    products.sort(Comparator.comparing(p -> p.getName().toLowerCase()));
            }

            BigDecimal minPrice = parseBD(minP);
            BigDecimal maxPrice = parseBD(maxP);

            for (Database.Product p : products) {
                String catName = catMap.getOrDefault(p.getCategoryId(), "");

                if (!quickQ.isEmpty()
                    && !p.getName().toLowerCase().contains(quickQ)
                    && !catName.toLowerCase().contains(quickQ)) continue;

                if (!"All".equals(catFilter) && !catName.equalsIgnoreCase(catFilter)) continue;

                if ("In Stock".equals(stockFilter) && p.getStockQuantity() <= 0) continue;
                if ("Out of Stock".equals(stockFilter) && p.getStockQuantity() > 0) continue;
                if ("Low Stock".equals(stockFilter) && p.getStockQuantity() > p.getMinStockLevel()) continue;

                if (minPrice != null && p.getSellingPrice() != null && p.getSellingPrice().compareTo(minPrice) < 0) continue;
                if (maxPrice != null && p.getSellingPrice() != null && p.getSellingPrice().compareTo(maxPrice) > 0) continue;

                productTableModel.addRow(new Object[]{
                    p.getId(), p.getName(), catName,
                    p.getVendor() != null ? p.getVendor() : "",
                    p.getCostPrice(), p.getSellingPrice(),
                    p.getStockQuantity(), p.getMinStockLevel(),
                    p.getExpireDate() != null ? p.getExpireDate().toString() : "",
                    p.getCreatedDate() != null ? p.getCreatedDate().toString() : ""
                });
            }
        } catch (SQLException e) {
            showError("Error loading products: " + e.getMessage());
        }
    }

    // ── Audited Products tab ──────────────────────────────────────────────────
    private void loadAuditedProducts(DefaultTableModel model) {
        model.setRowCount(0);
        String sql = "SELECT p.id, p.name, c.name as cat, il.previous_quantity, " +
                     "il.previous_quantity - il.quantity as physical, il.action, il.quantity, il.date " +
                     "FROM inventory_log il " +
                     "JOIN products p ON il.product_id = p.id " +
                     "LEFT JOIN categories c ON p.category_id = c.id " +
                     "WHERE il.action LIKE 'AUDIT%' AND il.client_id = ? " +
                     "ORDER BY il.date DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Database.getCurrentClientId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String action = rs.getString("action");
                String status = action.contains("LOSS") ? "LOSS"
                    : action.contains("ADDITION") ? "ADDITION" : "BALANCED";
                model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                    rs.getInt("previous_quantity"), rs.getInt("physical"),
                    status, rs.getInt("quantity"), rs.getString("date")
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ── Non-Audited Products tab ──────────────────────────────────────────────
    private void loadNonAuditedProducts(DefaultTableModel model) {
        model.setRowCount(0);
        String sql = "SELECT p.id, p.name, c.name as cat, p.stock_quantity, p.expire_date " +
                     "FROM products p LEFT JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.client_id = ? " +
                     "AND p.id NOT IN (" +
                     "  SELECT DISTINCT product_id FROM inventory_log " +
                     "  WHERE action LIKE 'AUDIT%' AND client_id = ?) " +
                     "ORDER BY p.name";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Database.getCurrentClientId());
            ps.setString(2, Database.getCurrentClientId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                    rs.getInt("stock_quantity"),
                    rs.getString("expire_date") != null ? rs.getString("expire_date") : "N/A"
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ── Balanced Products tab ─────────────────────────────────────────────────
    private void loadBalancedProducts(DefaultTableModel model) {
        model.setRowCount(0);
        String sql = "SELECT p.id, p.name, c.name as cat, p.stock_quantity, il.date " +
                     "FROM inventory_log il " +
                     "JOIN products p ON il.product_id = p.id " +
                     "LEFT JOIN categories c ON p.category_id = c.id " +
                     "WHERE il.action LIKE 'AUDIT BALANCED%' AND il.client_id = ? " +
                     "ORDER BY il.date DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Database.getCurrentClientId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                    rs.getInt("stock_quantity"), rs.getString("date")
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ── Duplicate Detection Dialog ────────────────────────────────────────────
    private void showDuplicatesDialog() {
        JDialog dlg = styledDialog(this, "Duplicate Product Detection", 720, 440);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        p.add(new JLabel("Products with identical names (case-insensitive):"), BorderLayout.NORTH);

        String[] cols = {"ID", "Name", "Category", "Stock", "Cost Price", "Selling Price"};
        DefaultTableModel dtm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(dtm);
        styleTable(t);

        try {
            String sql = "SELECT p.id, p.name, c.name as cat, p.stock_quantity, p.cost_price, p.selling_price " +
                         "FROM products p LEFT JOIN categories c ON p.category_id = c.id " +
                         "WHERE LOWER(p.name) IN (" +
                         "  SELECT LOWER(name) FROM products WHERE client_id = ? " +
                         "  GROUP BY LOWER(name) HAVING COUNT(*) > 1) AND p.client_id = ? " +
                         "ORDER BY LOWER(p.name)";
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, Database.getCurrentClientId());
                ps.setString(2, Database.getCurrentClientId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    dtm.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                        rs.getInt("stock_quantity"), rs.getBigDecimal("cost_price"), rs.getBigDecimal("selling_price")});
                }
            }
        } catch (SQLException e) { showError(e.getMessage()); }

        p.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.setBackground(BG);

        JButton delBtn = accentButton("Delete Selected", new Color(0xA00000));
        delBtn.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(dlg, "Select a row first."); return; }
            int id = (int) dtm.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(dlg, "Delete product ID " + id + " from database?",
                "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                try {
                    Database.deleteProductCompletely(id);
                    dtm.removeRow(row);
                    loadProducts();
                } catch (SQLException ex) { showError(ex.getMessage()); }
            }
        });
        btnRow.add(delBtn);
        btnRow.add(accentButton("Close", TEXT_MID)).addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { dlg.dispose(); }
        });
        p.add(btnRow, BorderLayout.SOUTH);
        dlg.add(p);
        dlg.setVisible(true);
    }

    // ── Stock Valuation Dialog ────────────────────────────────────────────────
    private void showStockValuationDialog() {
        JDialog dlg = styledDialog(this, "Stock Valuation (FIFO/LIFO)", 480, 280);
        JPanel p = new JPanel(new GridLayout(0, 2, 12, 10));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        try {
            String sql = "SELECT COALESCE(SUM(stock_quantity * cost_price),0) as cost_val, " +
                         "COALESCE(SUM(stock_quantity * selling_price),0) as sell_val, " +
                         "COUNT(*) as total_products, COALESCE(SUM(stock_quantity),0) as total_units " +
                         "FROM products WHERE client_id = ?";
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, Database.getCurrentClientId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    BigDecimal costVal = rs.getBigDecimal("cost_val");
                    BigDecimal sellVal = rs.getBigDecimal("sell_val");
                    addValRow(p, "Total Products:", rs.getString("total_products"));
                    addValRow(p, "Total Units in Stock:", rs.getString("total_units"));
                    addValRow(p, "FIFO/LIFO Cost Basis:", costVal.toPlainString());
                    addValRow(p, "Potential Revenue (Selling):", sellVal.toPlainString());
                    addValRow(p, "Gross Margin Potential:", sellVal.subtract(costVal).toPlainString());
                }
            }
        } catch (SQLException e) { showError(e.getMessage()); }
        dlg.add(p);
        dlg.setVisible(true);
    }

    private void addValRow(JPanel p, String label, String val) {
        JLabel l = boldLabel(label);
        JLabel v = new JLabel(val);
        v.setFont(new Font("Courier New", Font.PLAIN, 13));
        v.setForeground(ACCENT);
        p.add(l); p.add(v);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  PERFORM AUDIT PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildPerformAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        JButton openBtn = accentButton("Open Stock Audit Panel", CARD_COLORS[1]);
        openBtn.setFont(new Font("Georgia", Font.BOLD, 15));
        openBtn.addActionListener(e -> showPerformAuditDialog());
        JPanel c = new JPanel(new FlowLayout(FlowLayout.CENTER));
        c.setBackground(BG);
        c.add(openBtn);
        panel.add(c, BorderLayout.CENTER);
        return panel;
    }

    private DefaultTableModel auditResultModel;

    private void showPerformAuditDialog() {
        JDialog dlg = styledDialog(this, "Perform Stock Audit Panel", 940, 700);
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // Titles
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBackground(BG);
        titleBlock.add(centeredBoldLabel("Perform Stock Audit Panel", 20, CARD_COLORS[1]));
        titleBlock.add(centeredBoldLabel("Monitor Products Physically Against The System", 12, TEXT_MID));
        main.add(titleBlock, BorderLayout.NORTH);

        // Search row
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        searchRow.setBackground(BG);
        searchRow.add(new JLabel("\uD83D\uDD0D  Product Search:"));
        JTextField auditSearchField = styledTextField(26);
        searchRow.add(auditSearchField);

        // Table 1: search results
        String[] t1Cols = {"ID", "Product Name", "Category", "Available Stock", "Cost Price", "Selling Price"};
        DefaultTableModel t1Model = new DefaultTableModel(t1Cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table1 = new JTable(t1Model);
        styleTable(table1);

        // Audit result table
        String[] resCols = {"Product Name", "Category", "Available Stock", "Physical Stock", "Status", "Qty Difference"};
        auditResultModel = new DefaultTableModel(resCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable auditTable = new JTable(auditResultModel);
        styleTable(auditTable);
        // Color-code status column
        auditTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if ("LOSS".equals(val)) setForeground(new Color(0xA00000));
                else if ("ADDITION".equals(val)) setForeground(new Color(0x1A6B2A));
                else setForeground(new Color(0x1A3A5C));
                return this;
            }
        });

        // Summary labels
        JPanel summaryRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 4));
        summaryRow.setBackground(new Color(0xF0EBE0));
        summaryRow.setBorder(BorderFactory.createLineBorder(BORDER_COL));
        totalAuditedLbl  = summaryLabel("Audited: 0");
        totalLossLbl     = summaryLabel("Loss: 0");
        totalBalancedLbl = summaryLabel("Balanced: 0");
        totalAddedLbl    = summaryLabel("Added: 0");
        totalLossLbl.setForeground(new Color(0xA00000));
        totalAddedLbl.setForeground(new Color(0x1A6B2A));
        summaryRow.add(totalAuditedLbl);
        summaryRow.add(totalLossLbl);
        summaryRow.add(totalBalancedLbl);
        summaryRow.add(totalAddedLbl);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table1), new JScrollPane(auditTable));
        split.setResizeWeight(0.45);
        split.setBackground(BG);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setBackground(BG);
        center.add(searchRow, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        center.add(summaryRow, BorderLayout.SOUTH);
        main.add(center, BorderLayout.CENTER);

        // Live search
        auditSearchField.getDocument().addDocumentListener(docListener(() -> {
            String q = auditSearchField.getText().trim();
            t1Model.setRowCount(0);
            if (q.isEmpty()) return;
            try {
                String sql = "SELECT p.id, p.name, c.name as cat, p.stock_quantity, p.cost_price, p.selling_price " +
                             "FROM products p LEFT JOIN categories c ON p.category_id = c.id " +
                             "WHERE LOWER(p.name) LIKE ? AND p.client_id = ? ORDER BY p.name";
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "%" + q.toLowerCase() + "%");
                    ps.setString(2, Database.getCurrentClientId());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        t1Model.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                            rs.getInt("stock_quantity"), rs.getBigDecimal("cost_price"), rs.getBigDecimal("selling_price")});
                    }
                }
            } catch (SQLException ex) { showError(ex.getMessage()); }
        }));

        // Double-click → audit dialog
        table1.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table1.getSelectedRow();
                    if (row < 0) return;
                    showAuditProductDialog(dlg,
                        (int) t1Model.getValueAt(row, 0),
                        (String) t1Model.getValueAt(row, 1),
                        t1Model.getValueAt(row, 2) != null ? (String) t1Model.getValueAt(row, 2) : "",
                        (int) t1Model.getValueAt(row, 3),
                        auditResultModel);
                }
            }
        });

        dlg.add(main);
        dlg.setVisible(true);
    }

    private void showAuditProductDialog(JDialog parent, int productId, String productName,
                                         String category, int availableStock,
                                         DefaultTableModel auditModel) {
        JDialog dlg = styledDialog(parent, "Audit Panel", 420, 300);
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        addFormRow(p, "Product Name:", productName);
        addFormRow(p, "Category:", category);
        addFormRow(p, "Available Stock:", String.valueOf(availableStock));

        p.add(boldLabel("Physical Stock:"));
        SpinnerNumberModel spinModel = new SpinnerNumberModel(availableStock, 0, 99999, 1);
        JSpinner physSpinner = new JSpinner(spinModel);
        physSpinner.setFont(new Font("Courier New", Font.PLAIN, 13));
        p.add(physSpinner);

        JButton saveBtn = new JButton("Save Changes");
        saveBtn.setBackground(new Color(0x2E7D32));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(new Font("Georgia", Font.BOLD, 13));
        saveBtn.setFocusPainted(false);
        saveBtn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));

        saveBtn.addActionListener(e -> {
            int physical = (int) physSpinner.getValue();
            int diff = physical - availableStock;
            String status = diff < 0 ? "LOSS" : diff > 0 ? "ADDITION" : "BALANCED";
            try {
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND client_id = ?")) {
                    ps.setInt(1, physical);
                    ps.setInt(2, productId);
                    ps.setString(3, Database.getCurrentClientId());
                    ps.executeUpdate();
                }
                Database.logInventoryAction(productId, "AUDIT " + status, Math.abs(diff), availableStock, loggedInUser);
                auditModel.addRow(new Object[]{productName, category, availableStock, physical, status, diff});
                updateAuditSummary(auditModel);
                loadProducts();
                dlg.dispose();
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        JPanel btnPnl = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPnl.setBackground(BG);
        btnPnl.add(saveBtn);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(p, BorderLayout.CENTER);
        wrapper.add(btnPnl, BorderLayout.SOUTH);
        dlg.add(wrapper);
        dlg.setVisible(true);
    }

    private void updateAuditSummary(DefaultTableModel model) {
        int audited = model.getRowCount(), loss = 0, balanced = 0, added = 0;
        for (int i = 0; i < audited; i++) {
            String s = (String) model.getValueAt(i, 4);
            if ("LOSS".equals(s)) loss++;
            else if ("BALANCED".equals(s)) balanced++;
            else if ("ADDITION".equals(s)) added++;
        }
        if (totalAuditedLbl != null) totalAuditedLbl.setText("Audited: " + audited);
        if (totalLossLbl != null) totalLossLbl.setText("Loss: " + loss);
        if (totalBalancedLbl != null) totalBalancedLbl.setText("Balanced: " + balanced);
        if (totalAddedLbl != null) totalAddedLbl.setText("Added: " + added);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  AUDIT HISTORY PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildAuditHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        JButton btn = accentButton("Open Audit Report History", CARD_COLORS[2]);
        btn.addActionListener(e -> showAuditHistoryDialog());
        JPanel c = new JPanel(new FlowLayout(FlowLayout.CENTER));
        c.setBackground(BG);
        c.add(btn);
        panel.add(c, BorderLayout.CENTER);
        return panel;
    }

    private void showAuditHistoryDialog() {
        JDialog dlg = styledDialog(this, "Audit Report History", 920, 600);
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        main.add(centeredBoldLabel("Audit Report History", 18, CARD_COLORS[2]), BorderLayout.NORTH);

        // ── JDateChooser for date selection ──
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        topRow.setBackground(BG);
        topRow.add(boldLabel("Select Date:"));
        JDateChooser datePicker = new JDateChooser();
        datePicker.setDate(new Date());
        datePicker.setDateFormatString("yyyy-MM-dd");
        datePicker.setPreferredSize(new Dimension(140, 28));
        topRow.add(datePicker);

        JComboBox<String> viewMode = new JComboBox<>(new String[]{"Selling Price", "Buying Price", "Loss (Selling - Buying)"});
        viewMode.setFont(LABEL_FONT);
        topRow.add(boldLabel("View total as:"));
        topRow.add(viewMode);

        JButton loadBtn = accentButton("Load Audit Data", CARD_COLORS[2]);
        topRow.add(loadBtn);

        // Tables
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Palatino Linotype", Font.BOLD, 12));

        DefaultTableModel lossModel = new DefaultTableModel(
            new String[]{"Product Name", "Category", "Available Stock", "Physical Stock", "Loss Qty", "Buying Price", "Selling Price"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable lossTable = new JTable(lossModel);
        styleTable(lossTable);

        DefaultTableModel sumModel = new DefaultTableModel(new String[]{"Metric", "Value"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable sumTable = new JTable(sumModel);
        styleTable(sumTable);

        tabs.addTab("View Loss", new JScrollPane(lossTable));
        tabs.addTab("Summary", new JScrollPane(sumTable));

        loadBtn.addActionListener(e -> {
            Date chosen = datePicker.getDate();
            if (chosen == null) { JOptionPane.showMessageDialog(dlg, "Please select a date."); return; }
            String dateStr = LocalDate.ofInstant(chosen.toInstant(), ZoneId.systemDefault()).toString();
            lossModel.setRowCount(0); sumModel.setRowCount(0);
            try {
                String sql = "SELECT p.name, c.name as cat, il.previous_quantity, il.quantity, p.cost_price, p.selling_price " +
                             "FROM inventory_log il JOIN products p ON il.product_id = p.id " +
                             "LEFT JOIN categories c ON p.category_id = c.id " +
                             "WHERE il.action LIKE 'AUDIT LOSS%' AND DATE(il.date) = ? AND il.client_id = ?";
                BigDecimal totalAmt = BigDecimal.ZERO;
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, dateStr);
                    ps.setString(2, Database.getCurrentClientId());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int avail = rs.getInt("previous_quantity");
                        int diff  = rs.getInt("quantity");
                        int phys  = avail - diff;
                        lossModel.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                            avail, phys, diff, rs.getBigDecimal("cost_price"), rs.getBigDecimal("selling_price")});
                        String mode = (String) viewMode.getSelectedItem();
                        BigDecimal price;
                        if ("Buying Price".equals(mode)) price = rs.getBigDecimal("cost_price");
                        else if ("Loss (Selling - Buying)".equals(mode))
                            price = rs.getBigDecimal("selling_price").subtract(rs.getBigDecimal("cost_price"));
                        else price = rs.getBigDecimal("selling_price");
                        totalAmt = totalAmt.add(price.multiply(BigDecimal.valueOf(diff)));
                    }
                }
                sumModel.addRow(new Object[]{"Date", dateStr});
                sumModel.addRow(new Object[]{"Total Loss Amount", totalAmt.toPlainString()});
                sumModel.addRow(new Object[]{"View Mode", viewMode.getSelectedItem()});
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        main.add(topRow, BorderLayout.BEFORE_FIRST_LINE);
        main.add(tabs, BorderLayout.CENTER);
        dlg.add(main);
        dlg.setVisible(true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  SALE EXCESS MONITOR PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildSaleExcessPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.add(centeredBoldLabel("Sale Excess Monitor", 18, CARD_COLORS[3]), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setBackground(BG);
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        form.add(boldLabel("Entry Type:"));
        JComboBox<String> entryType = new JComboBox<>(new String[]{"Daily", "Weekly"});
        entryType.setFont(LABEL_FONT);
        form.add(entryType);

        form.add(boldLabel("Start Date:"));
        JDateChooser startDateChooser = new JDateChooser();
        startDateChooser.setDate(toDate(LocalDate.now().minusDays(30)));
        startDateChooser.setDateFormatString("yyyy-MM-dd");
        form.add(startDateChooser);

        form.add(boldLabel("End Date:"));
        JDateChooser endDateChooser = new JDateChooser();
        endDateChooser.setDate(toDate(LocalDate.now()));
        endDateChooser.setDateFormatString("yyyy-MM-dd");
        form.add(endDateChooser);

        form.add(boldLabel("Total Excess Amount (manual):"));
        JTextField excessField = styledTextField(14);
        form.add(excessField);

        JPanel resultArea = new JPanel(new GridLayout(0, 2, 10, 8));
        resultArea.setBackground(new Color(0xF0EBE0));
        resultArea.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel totalLossOut   = resultLabel("—");
        JLabel excessOut      = resultLabel("—");
        JLabel actualLossOut  = resultLabel("—");
        resultArea.add(boldLabel("Total Loss (from Audit):")); resultArea.add(totalLossOut);
        resultArea.add(boldLabel("Total Excess:"));             resultArea.add(excessOut);
        resultArea.add(boldLabel("Actual Net Loss:"));          resultArea.add(actualLossOut);

        JButton calcBtn = accentButton("Calculate", CARD_COLORS[3]);
        calcBtn.addActionListener(e -> {
            Date sd = startDateChooser.getDate(), ed = endDateChooser.getDate();
            if (sd == null || ed == null) { JOptionPane.showMessageDialog(panel, "Select both dates."); return; }
            String from = toLocalDate(sd).toString();
            String to   = toLocalDate(ed).toString();
            try {
                BigDecimal excess = parseBD(excessField.getText().trim());
                if (excess == null) excess = BigDecimal.ZERO;
                String sql = "SELECT COALESCE(SUM(il.quantity * p.cost_price), 0) as total_loss " +
                             "FROM inventory_log il JOIN products p ON il.product_id = p.id " +
                             "WHERE il.action LIKE 'AUDIT LOSS%' AND DATE(il.date) BETWEEN ? AND ? AND il.client_id = ?";
                BigDecimal totalLoss = BigDecimal.ZERO;
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, from); ps.setString(2, to); ps.setString(3, Database.getCurrentClientId());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalLoss = rs.getBigDecimal("total_loss");
                }
                BigDecimal actual = totalLoss.subtract(excess);
                totalLossOut.setText(totalLoss.toPlainString());
                excessOut.setText(excess.toPlainString());
                actualLossOut.setText(actual.toPlainString());
                actualLossOut.setForeground(actual.compareTo(BigDecimal.ZERO) < 0 ? new Color(0xA00000) : new Color(0x1A6B2A));
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG);
        center.add(form, BorderLayout.NORTH);
        center.add(resultArea, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRow.setBackground(BG);
        btnRow.add(calcBtn);
        center.add(btnRow, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  PROFIT / LOSS AUDIT PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildProfitLossPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.add(centeredBoldLabel("Profit / Loss Audit", 18, CARD_COLORS[4]), BorderLayout.NORTH);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        dateRow.setBackground(BG);
        dateRow.add(boldLabel("From:"));
        JDateChooser fromChooser = new JDateChooser();
        fromChooser.setDate(toDate(LocalDate.now().withDayOfMonth(1)));
        fromChooser.setDateFormatString("yyyy-MM-dd");
        fromChooser.setPreferredSize(new Dimension(140, 28));
        dateRow.add(fromChooser);

        dateRow.add(boldLabel("To:"));
        JDateChooser toChooser = new JDateChooser();
        toChooser.setDate(toDate(LocalDate.now()));
        toChooser.setDateFormatString("yyyy-MM-dd");
        toChooser.setPreferredSize(new Dimension(140, 28));
        dateRow.add(toChooser);

        JButton runBtn = accentButton("Generate Report", CARD_COLORS[4]);
        dateRow.add(runBtn);

        DefaultTableModel model = new DefaultTableModel(new String[]{"Metric", "Amount"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        styleTable(table);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                if (val != null) {
                    try {
                        BigDecimal bd = new BigDecimal(val.toString());
                        setForeground(bd.compareTo(BigDecimal.ZERO) < 0 ? new Color(0xA00000) : new Color(0x1A6B2A));
                    } catch (Exception ignored) { setForeground(TEXT_DARK); }
                }
                return this;
            }
        });

        runBtn.addActionListener(e -> {
            Date fd = fromChooser.getDate(), td = toChooser.getDate();
            if (fd == null || td == null) { JOptionPane.showMessageDialog(panel, "Select both dates."); return; }
            String f = toLocalDate(fd).toString(), t = toLocalDate(td).toString();
            model.setRowCount(0);
            try {
                BigDecimal grossSales  = Database.getTotalSales(f, t);
                BigDecimal discounts   = Database.getTotalDiscounts(f, t);
                BigDecimal cogs        = Database.getCostOfGoods(f, t);
                BigDecimal expenses    = Database.getTotalExpenses(f, t);
                BigDecimal netSales    = grossSales.subtract(discounts);
                BigDecimal grossProfit = netSales.subtract(cogs);
                BigDecimal netProfit   = grossProfit.subtract(expenses);

                String lossSQL = "SELECT COALESCE(SUM(il.quantity * p.cost_price),0) as loss " +
                                 "FROM inventory_log il JOIN products p ON il.product_id = p.id " +
                                 "WHERE il.action LIKE 'AUDIT LOSS%' AND DATE(il.date) BETWEEN ? AND ? AND il.client_id = ?";
                BigDecimal auditLoss = BigDecimal.ZERO;
                try (Connection conn = Database.getConnection();
                     PreparedStatement ps = conn.prepareStatement(lossSQL)) {
                    ps.setString(1, f); ps.setString(2, t); ps.setString(3, Database.getCurrentClientId());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) auditLoss = rs.getBigDecimal("loss");
                }

                model.addRow(new Object[]{"Gross Sales",                grossSales});
                model.addRow(new Object[]{"Discounts",                  discounts.negate()});
                model.addRow(new Object[]{"Net Sales",                  netSales});
                model.addRow(new Object[]{"Cost of Goods Sold (COGS)", cogs.negate()});
                model.addRow(new Object[]{"Gross Profit",               grossProfit});
                model.addRow(new Object[]{"Expenses",                   expenses.negate()});
                model.addRow(new Object[]{"Audit Loss (Inventory)",     auditLoss.negate()});
                model.addRow(new Object[]{"NET PROFIT / LOSS",         netProfit.subtract(auditLoss)});
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        panel.add(dateRow, BorderLayout.BEFORE_FIRST_LINE);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  EXPIRE DATE UPDATER PANEL  — uses JCalendar only
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildExpireDatePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 40, 14, 40));
        panel.add(centeredBoldLabel("Expire Date Updater", 18, CARD_COLORS[5]), BorderLayout.NORTH);

        LocalDate minAllowed = LocalDate.now().plusMonths(9);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG);

        // Instructions
        JLabel infoLbl = new JLabel(
            "<html><center>Select a new expire date using the calendar below.<br>" +
            "The date must be <b>at least 9 months</b> from today " +
            "(<b>" + minAllowed + "</b>).<br>" +
            "Clicking <i>Apply to ALL Products</i> will update every product at once.</center></html>",
            SwingConstants.CENTER);
        infoLbl.setFont(new Font("Palatino Linotype", Font.PLAIN, 13));
        infoLbl.setForeground(TEXT_MID);
        center.add(infoLbl, BorderLayout.NORTH);

        // JCalendar — single calendar widget
        JCalendar calendar = new JCalendar();
        calendar.setMinSelectableDate(toDate(minAllowed));
        // Style the calendar slightly
        calendar.setBackground(PANEL_BG);
        calendar.setBorder(BorderFactory.createLineBorder(BORDER_COL, 1));

        JPanel calWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        calWrap.setBackground(BG);
        calWrap.add(calendar);
        center.add(calWrap, BorderLayout.CENTER);

        JLabel warningLbl = new JLabel(" ", SwingConstants.CENTER);
        warningLbl.setFont(LABEL_FONT);
        warningLbl.setForeground(new Color(0xA00000));

        JButton applyBtn = new JButton("Apply to ALL Products");
        applyBtn.setBackground(new Color(0x2C5F2E));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setFont(new Font("Georgia", Font.BOLD, 14));
        applyBtn.setFocusPainted(false);
        applyBtn.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));

        applyBtn.addActionListener(e -> {
            warningLbl.setText(" ");
            Date selectedDate = calendar.getDate();
            if (selectedDate == null) {
                warningLbl.setText("Please select a date on the calendar.");
                return;
            }
            LocalDate chosen = toLocalDate(selectedDate);
            if (chosen.isBefore(minAllowed)) {
                warningLbl.setText("⚠ Date must be at least 9 months from today (" + minAllowed + ")");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(panel,
                "Update ALL products' expire date to " + chosen + "?",
                "Confirm Bulk Update", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE products SET expire_date = ?, updated_at = CURRENT_TIMESTAMP WHERE client_id = ?")) {
                ps.setString(1, chosen.toString());
                ps.setString(2, Database.getCurrentClientId());
                int rows = ps.executeUpdate();
                JOptionPane.showMessageDialog(panel, rows + " products updated to expire on " + chosen,
                    "Done", JOptionPane.INFORMATION_MESSAGE);
                loadProducts();
                warningLbl.setText("✔ " + rows + " products updated.");
                warningLbl.setForeground(new Color(0x1A6B2A));
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBackground(BG);
        south.add(warningLbl);
        south.add(Box.createVerticalStrut(8));
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrap.setBackground(BG);
        btnWrap.add(applyBtn);
        south.add(btnWrap);

        panel.add(center, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  EXPORT PDF PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildExportPdfPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(centeredBoldLabel("Export PDF", 18, CARD_COLORS[6]), BorderLayout.NORTH);

        JPanel options = new JPanel(new GridLayout(1, 3, 18, 0));
        options.setBackground(BG);
        options.add(buildPdfOptionCard("Buying Price List",
            "Product Name, Category\nBuying Price, Qty, Expire Date",
            new Color(0x4A1942), () -> showExportPdfDialog("buying")));
        options.add(buildPdfOptionCard("Product Inventory",
            "Product Name, Category\nBuying Price, Selling Price\nAvailable Qty, Vendor, Expire Date",
            new Color(0x1A3A5C), () -> showExportPdfDialog("inventory")));
        options.add(buildPdfOptionCard("Selling Price List",
            "Product Name, Category\nSelling Price, Qty, Expire Date",
            new Color(0x2C5F2E), () -> showExportPdfDialog("selling")));
        panel.add(options, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPdfOptionCard(String title, String description, Color color, Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(color);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker()),
            BorderFactory.createEmptyBorder(20, 16, 20, 16)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Palatino Linotype", Font.BOLD, 14));
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel descLbl = new JLabel("<html><center>" + description.replace("\n","<br>") + "</center></html>", SwingConstants.CENTER);
        descLbl.setFont(new Font("Courier New", Font.PLAIN, 11));
        descLbl.setForeground(new Color(0xDDDDDD));
        descLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btn = new JButton("Generate PDF");
        btn.setBackground(Color.WHITE);
        btn.setForeground(color);
        btn.setFont(new Font("Georgia", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.addActionListener(e -> action.run());

        card.add(titleLbl);
        card.add(Box.createVerticalStrut(10));
        card.add(descLbl);
        card.add(Box.createVerticalStrut(14));
        card.add(btn);
        return card;
    }

    private void showExportPdfDialog(String type) {
        JDialog dlg = styledDialog(this, "Export PDF — " + type.toUpperCase(), 780, 620);
        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // ── PDF Header section (always visible with labels) ──
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCCCCCC)),
            BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        // Business name — centered, bold
        JLabel bizLbl = new JLabel(businessName, SwingConstants.CENTER);
        bizLbl.setFont(new Font("Georgia", Font.BOLD, 16));
        bizLbl.setForeground(TEXT_DARK);
        bizLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(bizLbl);
        header.add(Box.createVerticalStrut(6));

        // Load address fields — always display labels even if values empty
        String poBox   = getSetting("po_box",   "N/A");
        String plotNo  = getSetting("plot_no",  "N/A");
        String blockNo = getSetting("block_no", "N/A");
        String street  = getSetting("street",   "N/A");
        String province = getSetting("province","N/A");
        String phone   = getSetting("phone",    "N/A");
        String email   = getSetting("email",    "N/A");
        String country = getSetting("country",  "");
        if (country.isEmpty()) {
            // derive from system timezone
            String tz = TimeZone.getDefault().getID();
            country = tz.contains("/") ? tz.split("/")[0] : tz;
        }

        // Row 1: P.O.Box | Plot No. | Block No.
        addPdfHeaderRow(header, new String[][]{
            {"P.O.Box", poBox}, {"Plot No.", plotNo}, {"Block No.", blockNo}
        });
        // Row 2: Street | Province
        addPdfHeaderRow(header, new String[][]{
            {"Street", street}, {"Province", province}
        });
        // Row 3: Phone | Email
        addPdfHeaderRow(header, new String[][]{
            {"Phone", phone}, {"Email", email}
        });
        // Row 4: Country
        addPdfHeaderRow(header, new String[][]{
            {"Country", country}
        });

        header.add(Box.createVerticalStrut(4));
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_COL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(sep);
        header.add(Box.createVerticalStrut(4));

        // Document title line
        String docTitle = type.equals("buying") ? "Buying Price List"
            : type.equals("selling") ? "Selling Price List" : "Product Inventory";
        JLabel docTitleLbl = new JLabel(docTitle, SwingConstants.CENTER);
        docTitleLbl.setFont(new Font("Palatino Linotype", Font.BOLD, 13));
        docTitleLbl.setForeground(CARD_COLORS[6]);
        docTitleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(docTitleLbl);

        main.add(header, BorderLayout.NORTH);

        // ── Product table ──
        String[] cols;
        switch (type) {
            case "buying":
                cols = new String[]{"Product Name", "Category", "Buying Price", "Qty", "Expire Date"};
                break;
            case "selling":
                cols = new String[]{"Product Name", "Category", "Selling Price", "Qty", "Expire Date"};
                break;
            default:
                cols = new String[]{"Product Name", "Category", "Buying Price", "Selling Price", "Qty", "Vendor", "Expire Date"};
        }
        DefaultTableModel dtm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable previewTable = new JTable(dtm);
        styleTable(previewTable);

        try {
            List<Database.Product> products = Database.getAllProducts();
            List<Database.Category> categories = Database.getAllCategoryObjects();
            Map<Integer, String> catMap = new HashMap<>();
            for (Database.Category c : categories) catMap.put(c.getId(), c.getName());
            for (Database.Product p : products) {
                String cat = catMap.getOrDefault(p.getCategoryId(), "");
                String exp = p.getExpireDate() != null ? p.getExpireDate().toString() : "N/A";
                switch (type) {
                    case "buying":
                        dtm.addRow(new Object[]{p.getName(), cat, p.getCostPrice(), p.getStockQuantity(), exp});
                        break;
                    case "selling":
                        dtm.addRow(new Object[]{p.getName(), cat, p.getSellingPrice(), p.getStockQuantity(), exp});
                        break;
                    default:
                        dtm.addRow(new Object[]{p.getName(), cat, p.getCostPrice(), p.getSellingPrice(),
                            p.getStockQuantity(), p.getVendor() != null ? p.getVendor() : "", exp});
                }
            }
        } catch (SQLException e) { showError(e.getMessage()); }

        main.add(new JScrollPane(previewTable), BorderLayout.CENTER);

        // ── Footer ──
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        String printDate = "Print Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        JLabel leftFoot = new JLabel("Printed by: " + loggedInUser);
        leftFoot.setFont(new Font("Courier New", Font.ITALIC, 11));
        JLabel rightFoot = new JLabel(printDate);
        rightFoot.setFont(new Font("Courier New", Font.ITALIC, 11));
        footer.add(leftFoot, BorderLayout.WEST);
        footer.add(rightFoot, BorderLayout.EAST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.setBackground(BG);
        JButton printBtn = accentButton("Print / Save PDF", CARD_COLORS[6]);
        printBtn.addActionListener(e -> {
            try { previewTable.print(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Print failed: " + ex.getMessage()); }
        });
        JButton closeBtn = accentButton("Close", TEXT_MID);
        closeBtn.addActionListener(e -> dlg.dispose());
        btnRow.add(printBtn);
        btnRow.add(closeBtn);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(BG);
        southPanel.add(footer, BorderLayout.NORTH);
        southPanel.add(btnRow, BorderLayout.SOUTH);
        main.add(southPanel, BorderLayout.SOUTH);

        dlg.add(main);
        dlg.setVisible(true);
    }

    /**
     * Adds a row of label:value pairs to the PDF header.
     * Labels are ALWAYS shown even when values are N/A.
     */
    private void addPdfHeaderRow(JPanel header, String[][] pairs) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 2));
        row.setBackground(Color.WHITE);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (String[] pair : pairs) {
            JLabel lbl = new JLabel(pair[0] + ":");
            lbl.setFont(new Font("Palatino Linotype", Font.BOLD, 11));
            lbl.setForeground(TEXT_MID);
            JLabel val = new JLabel(pair[1]);
            val.setFont(new Font("Courier New", Font.PLAIN, 11));
            val.setForeground(TEXT_DARK);
            row.add(lbl);
            row.add(val);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        header.add(row);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ██  SETTINGS PANEL
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 30, 14, 30));
        panel.add(centeredBoldLabel("Settings", 18, CARD_COLORS[7]), BorderLayout.NORTH);

        String[][] builtIns = {
            {"business_name", "Business Name"},
            {"po_box",        "P.O. Box"},
            {"plot_no",       "Plot No."},
            {"block_no",      "Block No."},
            {"street",        "Street"},
            {"province",      "Province"},
            {"country",       "Country"},
            {"phone",         "Phone"},
            {"email",         "Email"},
        };

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        fieldsPanel.setBackground(BG);
        for (String[] kv : builtIns) {
            fieldsPanel.add(boldLabel(kv[1] + ":"));
            JTextField tf = styledTextField(22);
            tf.setText(getSetting(kv[0], ""));
            fieldsPanel.add(tf);
            settingFields.put(kv[0], tf);
        }

        JPanel customArea = new JPanel();
        customArea.setLayout(new BoxLayout(customArea, BoxLayout.Y_AXIS));
        customArea.setBackground(BG);

        JButton addFieldBtn = accentButton("+ Add Custom Field", new Color(0x1C3557));
        addFieldBtn.addActionListener(e -> {
            String fieldName = JOptionPane.showInputDialog(panel, "Enter field label:");
            if (fieldName == null || fieldName.trim().isEmpty()) return;
            JTextField tf = styledTextField(22);
            JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
            row.setBackground(BG);
            row.add(boldLabel(fieldName.trim() + ":"));
            row.add(tf);
            customArea.add(row);
            customArea.revalidate();
            settingFields.put(fieldName.trim().toLowerCase().replace(" ", "_"), tf);
        });

        JButton saveBtn = new JButton("Save Settings");
        saveBtn.setBackground(ACCENT);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(new Font("Georgia", Font.BOLD, 13));
        saveBtn.setFocusPainted(false);
        saveBtn.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        saveBtn.addActionListener(e -> saveSettings());

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setBackground(BG);
        center.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);
        center.add(customArea, BorderLayout.SOUTH);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setBackground(BG);
        btnRow.add(addFieldBtn);
        btnRow.add(saveBtn);

        panel.add(center, BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    private String getSetting(String key, String def) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT value FROM app_settings WHERE key = ? LIMIT 1")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String v = rs.getString("value");
                return (v != null && !v.isEmpty()) ? v : def;
            }
        } catch (SQLException ignored) {}
        return def;
    }

    private void saveSettings() {
        try (Connection conn = Database.getConnection()) {
            for (Map.Entry<String, JTextField> entry : settingFields.entrySet()) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue().getText().trim());
                    ps.executeUpdate();
                }
            }
            String newBn = settingFields.containsKey("business_name")
                ? settingFields.get("business_name").getText().trim() : "";
            if (!newBn.isEmpty()) { businessName = newBn; setTitle("Inventory Management — " + businessName); }
            JOptionPane.showMessageDialog(this, "Settings saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTIL: STYLED COMPONENTS
    // ─────────────────────────────────────────────────────────────────────────
    private void styleTable(JTable t) {
        t.setFont(TABLE_FONT);
        t.setRowHeight(24);
        t.setGridColor(new Color(0xDDD9D0));
        t.setShowVerticalLines(false);
        t.setSelectionBackground(SEL_ROW);
        t.setSelectionForeground(TEXT_DARK);
        t.setBackground(PANEL_BG);
        t.getTableHeader().setFont(new Font("Palatino Linotype", Font.BOLD, 12));
        t.getTableHeader().setBackground(ACCENT);
        t.getTableHeader().setForeground(Color.WHITE);
        t.getTableHeader().setBorder(BorderFactory.createEmptyBorder());
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object val, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                setBackground(sel ? SEL_ROW : (r % 2 == 0 ? PANEL_BG : ROW_ALT));
                setForeground(TEXT_DARK);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                return this;
            }
        });
    }

    private JButton accentButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Palatino Linotype", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTextField styledTextField(int cols) {
        JTextField tf = new JTextField(cols);
        tf.setFont(new Font("Courier New", Font.PLAIN, 13));
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_DARK);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COL),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return tf;
    }

    private JDialog styledDialog(Window parent, String title, int w, int h) {
        JDialog dlg = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(w, h);
        dlg.setLocationRelativeTo(parent);
        dlg.setBackground(BG);
        dlg.getContentPane().setBackground(BG);
        return dlg;
    }

    private JLabel centeredBoldLabel(String text, int size, Color color) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Palatino Linotype", Font.BOLD, size));
        lbl.setForeground(color);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private JLabel boldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Palatino Linotype", Font.BOLD, 12));
        lbl.setForeground(TEXT_DARK);
        return lbl;
    }

    private JLabel summaryLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Palatino Linotype", Font.BOLD, 12));
        lbl.setForeground(ACCENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        return lbl;
    }

    private JLabel resultLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Courier New", Font.BOLD, 13));
        lbl.setForeground(ACCENT);
        return lbl;
    }

    private void addFormRow(JPanel p, String label, String value) {
        p.add(boldLabel(label));
        JLabel val = new JLabel(value);
        val.setFont(new Font("Courier New", Font.PLAIN, 13));
        val.setForeground(ACCENT);
        p.add(val);
    }

    private BigDecimal parseBD(String s) {
        try { return (s != null && !s.isEmpty()) ? new BigDecimal(s) : null; }
        catch (Exception e) { return null; }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new Inventory("admin");
        });
    }
}
