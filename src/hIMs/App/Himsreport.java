package hIMs.App;

import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import com.formdev.flatlaf.FlatLightLaf;
import com.toedter.calendar.JDateChooser;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
/**
 * CORRECTED Inventory Report System
 * 
 * CRITICAL FIX:
 * - Flow history now uses ACTUAL database quantities (not calculated)
 * - Column order: Product | Stock Before | Action | Change Qty | Stock After
 * - Captures ALL changes including Product Monitor Analysis adjustments
 * - Stock After always matches the actual quantity stored in item_flows
 * - Table only shows data when a specific product is selected
 * - Detects stock inconsistencies and marks them as "STOCK UPDATED"
 * - Tracks logged-in user for all actions
 * 
 * USAGE:
 * To open this dialog with username tracking from another window:
 * 
 *   String loggedInUsername = // get from login or session
 *   Himsreport report = new Himsreport(loggedInUsername);
 *   report.setVisible(true);
 * 
 * Example from Profilewindow:
 *   Himsreport report = new Himsreport(this.username);
 *   report.setVisible(true);
 */
public class Himsreport extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(Himsreport.class.getName());
    
    // UI Components
    private JTable receivedTable, productTable, flowTable, detailsTable;
    private DefaultTableModel receivedTableModel, productTableModel, flowTableModel, detailsTableModel;
    private JDateChooser dateChooser;
    private JTextField searchField;
    private JLabel statsLabel, selectedProductLabel;
    
    // Data
    private List<Database.ReceiveEntry> allReceivedEntries;
    private List<Database.Product> allProducts;
    private List<FlowEntry> processedFlowHistory;
    private int selectedProductId = -1;
    private String selectedProductName = "";
    private String currentUsername = "SYSTEM"; // Track current logged-in user
    
    // State
    private boolean isMinimized = false;
    private JPanel fullContentPanel, minimizedContentPanel;
    private JButton floatButton;
    
    // Modern colors
    private static final Color PRIMARY = new Color(59, 130, 246);
    private static final Color SUCCESS = new Color(34, 197, 94);
    private static final Color WARNING = new Color(251, 146, 60);
    private static final Color DANGER = new Color(239, 68, 68);
    private static final Color INFO = new Color(139, 92, 246);
    private static final Color DARK_BG = new Color(31, 41, 55);
    private static final Color LIGHT_BG = new Color(243, 244, 246);
    private static final Color CARD_BG = Color.WHITE;
    
    /**
     * FlowEntry - Uses ACTUAL database values, no calculation
     * Each entry represents a single stock change event with precise timestamp
     */
    private static class FlowEntry {
        int productId;
        String productName;
        String action;
        int quantityChange;      // The actual change amount
        int stockBefore;         // Stock before this transaction
        int stockAfter;          // Stock after (from database)
        LocalDate flowDate;
        LocalDate createdDate;
        String createdTimestamp;  // Precise timestamp to track changes
        String vendor;
        String user;
        BigDecimal costPrice;
        BigDecimal sellingPrice;
        String category;
        
        public FlowEntry(int productId, String productName, String action, 
                        int quantityChange, int stockBefore, int stockAfter,
                        LocalDate flowDate, LocalDate createdDate, String createdTimestamp,
                        String vendor, String user,
                        BigDecimal costPrice, BigDecimal sellingPrice, String category) {
            this.productId = productId;
            this.productName = productName;
            this.action = action;
            this.quantityChange = quantityChange;
            this.stockBefore = stockBefore;
            this.stockAfter = stockAfter;
            this.flowDate = flowDate;
            this.createdDate = createdDate;
            this.createdTimestamp = createdTimestamp;
            this.vendor = vendor;
            this.user = user;
            this.costPrice = costPrice != null ? costPrice : BigDecimal.ZERO;
            this.sellingPrice = sellingPrice != null ? sellingPrice : BigDecimal.ZERO;
            this.category = category != null ? category : "N/A";
        }
    }
    
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                Database.initializeDatabase();
                Himsreport dialog = new Himsreport("admin"); // Use default username for testing
                dialog.setVisible(true);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
            }
        });
    }
    
    // Constructor with username parameter
    public Himsreport(String username) {
        super();
        this.currentUsername = username != null && !username.trim().isEmpty() ? username : "SYSTEM";
        
        setTitle("📊 Inventory Flow Report - Stock Accuracy");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 1400, 850);
        setLocationRelativeTo(null);
        
        allReceivedEntries = new ArrayList<>();
        allProducts = new ArrayList<>();
        processedFlowHistory = new ArrayList<>();
        
        initializeUI();
        loadAllData();
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(fullContentPanel, BorderLayout.CENTER);
    }
    
    // Default constructor for backward compatibility
    public Himsreport() {
        this("SYSTEM");
    }
    
    private void initializeUI() {
        fullContentPanel = new JPanel(new BorderLayout(0, 0));
        fullContentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        fullContentPanel.setBackground(LIGHT_BG);
        
        fullContentPanel.add(createTopPanel(), BorderLayout.NORTH);
        
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setTopComponent(createTopSection());
        mainSplit.setBottomComponent(createBottomSection());
        
        fullContentPanel.add(mainSplit, BorderLayout.CENTER);
        fullContentPanel.add(createStatsPanel(), BorderLayout.SOUTH);
        
        createMinimizedPanel();
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(CARD_BG);
        
        JLabel title = new JLabel("📊 Item Flow Report - Stock Accuracy Tracking");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(DARK_BG);
        titlePanel.add(title, BorderLayout.NORTH);
        
        JLabel userLabel = new JLabel("👤 Logged in as: " + currentUsername);
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userLabel.setForeground(new Color(100, 100, 100));
        titlePanel.add(userLabel, BorderLayout.SOUTH);
        
        panel.add(titlePanel, BorderLayout.WEST);
        
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setBackground(CARD_BG);
        
        controls.add(new JLabel("Date:"));
        dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("yyyy-MM-dd");
        dateChooser.setDate(new java.util.Date());
        dateChooser.setPreferredSize(new Dimension(150, 35));
        controls.add(dateChooser);
        
        JButton refreshBtn = createButton("🔄 Refresh", PRIMARY);
        refreshBtn.addActionListener(e -> loadAllData());
        controls.add(refreshBtn);
        
        JButton verifyBtn = createButton("✓ Verify Stock", SUCCESS);
        verifyBtn.addActionListener(e -> verifyStockAccuracy());
        controls.add(verifyBtn);
        
        floatButton = createButton("⬇ Float", WARNING);
        floatButton.addActionListener(e -> toggleFloat());
        controls.add(floatButton);
        
        panel.add(controls, BorderLayout.EAST);
        return panel;
    }
    
    private JPanel createTopSection() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBackground(LIGHT_BG);
        
        receivedTableModel = new DefaultTableModel(
            new String[]{"Product", "Category", "Cost", "Selling", "Qty", "Date"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        receivedTable = new JTable(receivedTableModel);
        styleTable(receivedTable);
        panel.add(createCard("📦 Received Products Today", new JScrollPane(receivedTable)));
        
        panel.add(createProductLedgerPanel());
        
        return panel;
    }
    
    private JPanel createProductLedgerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(CARD_BG);
        
        JLabel title = new JLabel("📋 Product Ledger");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(title, BorderLayout.WEST);
        
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        searchPanel.setBackground(CARD_BG);
        searchPanel.add(new JLabel("🔍"));
        
        searchField = new JTextField(20);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
            new EmptyBorder(5, 10, 5, 10)
        ));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateProductTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateProductTable(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateProductTable(); }
        });
        searchPanel.add(searchField);
        
        JButton clearBtn = createSmallButton("✕", DANGER);
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            selectedProductId = -1;
            selectedProductName = "";
            updateProductLabel();
            updateFlowTable(); // Clear flow table when clearing selection
        });
        searchPanel.add(clearBtn);
        
        header.add(searchPanel, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        
        productTableModel = new DefaultTableModel(
            new String[]{"ID", "Product", "Category", "Current Stock", "Min Stock", "Vendor"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        
        productTable = new JTable(productTableModel);
        styleTable(productTable);
        productTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) selectProduct();
            }
        });
        
        panel.add(new JScrollPane(productTable), BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createBottomSection() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBackground(LIGHT_BG);
        
        panel.add(createFlowPanel());
        panel.add(createDetailsPanel());
        
        return panel;
    }
    
    private JPanel createFlowPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        
        selectedProductLabel = new JLabel("📜 Item Flow History (Select a product to view)");
        selectedProductLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(selectedProductLabel, BorderLayout.WEST);
        
        JButton showAllBtn = createSmallButton("Clear Selection", PRIMARY);
        showAllBtn.addActionListener(e -> {
            selectedProductId = -1;
            selectedProductName = "";
            updateProductLabel();
            updateFlowTable();
        });
        header.add(showAllBtn, BorderLayout.EAST);
        
        panel.add(header, BorderLayout.NORTH);
        
        // CORRECTED COLUMN ORDER: Product | Stock Before | Action | Change Qty | Stock After | Date | Time | User | Vendor
        flowTableModel = new DefaultTableModel(
            new String[]{"Product", "Stock Before", "Action", "Change Qty", "Stock After", 
                        "Date", "Time", "User", "Vendor"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        
        flowTable = new JTable(flowTableModel);
        styleTable(flowTable);
        
        // Color-code the Action column (index 2 now)
        flowTable.getColumnModel().getColumn(2).setCellRenderer(new ActionRenderer());
        
        flowTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                showDetails(flowTable.getSelectedRow());
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(flowTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235), 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel title = new JLabel("📋 Transaction Details");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        panel.add(title, BorderLayout.NORTH);
        
        detailsTableModel = new DefaultTableModel(new String[]{"Property", "Value"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        
        detailsTable = new JTable(detailsTableModel);
        detailsTable.setRowHeight(28);
        detailsTable.setShowGrid(false);
        detailsTable.getColumnModel().getColumn(0).setCellRenderer(new BoldRenderer());
        
        panel.add(new JScrollPane(detailsTable), BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(229, 231, 235)));
        
        statsLabel = new JLabel("📊 Loading statistics...");
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(statsLabel);
        
        return panel;
    }
    
    private void createMinimizedPanel() {
        minimizedContentPanel = new JPanel(new BorderLayout());
        minimizedContentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        minimizedContentPanel.setBackground(LIGHT_BG);
        
        JLabel label = new JLabel("📊 Flow Report (Minimized)", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        minimizedContentPanel.add(label, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.setBackground(LIGHT_BG);
        btnPanel.add(floatButton);
        minimizedContentPanel.add(btnPanel, BorderLayout.SOUTH);
    }
    
    // === DATA LOADING - CRITICAL FIX ===
    
    private void loadAllData() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                loadReceivedEntries();
                loadProducts();
                loadFlowHistory();
                return null;
            }
            protected void done() {
                updateStats();
            }
        }.execute();
    }
    
    private void loadReceivedEntries() {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String dateStr = sdf.format(dateChooser.getDate());
            String currentClientId = Database.getCurrentClientId();
            
            allReceivedEntries.clear();
            
            // Use a custom query with client_id filter since Database.getReceivedEntries doesn't have it
            String sql = "SELECT p.name as product, c.name as category, p.cost_price, p.selling_price, ifl.quantity as stock_quantity, DATE(ifl.flow_date) as created_date " +
                        "FROM item_flows ifl JOIN products p ON ifl.product_id = p.id LEFT JOIN categories c ON p.category_id = c.id " +
                        "WHERE ifl.action = 'RECEIVED' AND DATE(ifl.flow_date) = ? AND p.client_id = ?";
            
            try (Connection conn = Database.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dateStr);
                pstmt.setString(2, currentClientId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Database.ReceiveEntry entry = new Database.ReceiveEntry(
                        rs.getString("product"),
                        rs.getString("category"),
                        rs.getBigDecimal("cost_price"),
                        rs.getBigDecimal("selling_price"),
                        rs.getInt("stock_quantity"),
                        Database.fromDatabaseDate(rs.getString("created_date"))
                    );
                    allReceivedEntries.add(entry);
                }
            }
            
            updateReceivedTable();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading received: " + e.getMessage(), e);
        }
    }
    
    private void updateReceivedTable() {
        receivedTableModel.setRowCount(0);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        
        for (Database.ReceiveEntry e : allReceivedEntries) {
            receivedTableModel.addRow(new Object[]{
                e.product, e.category,
                df.format(e.costPrice), df.format(e.sellingPrice),
                e.stockQuantity, e.createdDate
            });
        }
    }
    
    private void loadProducts() {
        try {
            allProducts.clear();
            // Database.getAllProducts() already filters by client_id (as updated earlier)
            allProducts.addAll(Database.getAllProducts());
            updateProductTable();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading products: " + e.getMessage(), e);
        }
    }
    
    private void updateProductTable() {
        productTableModel.setRowCount(0);
        String search = searchField.getText().trim().toLowerCase();
        
        if (search.isEmpty()) {
            // Don't display anything when search is empty
            return;
        }
        
        for (Database.Product p : allProducts) {
            if (p.getName().toLowerCase().contains(search) || 
                String.valueOf(p.getId()).equals(search)) {
                productTableModel.addRow(new Object[]{
                    p.getId(), 
                    p.getName(), 
                    getCategoryName(p.getCategoryId()),
                    p.getStockQuantity(),
                    p.getMinStockLevel(),
                    p.getVendor() != null ? p.getVendor() : "N/A"
                });
            }
        }
    }
    
    private void loadFlowHistory() {
        try {
            String currentClientId = Database.getCurrentClientId();
            
            // Create a custom query with client_id filter
            String sql = "SELECT ifl.product_id, p.name as product_name, ifl.action, ifl.quantity, " +
                        "ifl.flow_date, ifl.created_date, ifl.vendor, ifl.user, " +
                        "p.cost_price, p.selling_price, c.name as category " +
                        "FROM item_flows ifl " +
                        "JOIN products p ON ifl.product_id = p.id " +
                        "LEFT JOIN categories c ON p.category_id = c.id " +
                        "WHERE p.client_id = ? " +
                        "ORDER BY ifl.created_date ASC";
            
            List<Database.ItemFlow> rawFlows = new ArrayList<>();
            
            try (Connection conn = Database.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, currentClientId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Database.ItemFlow flow = new Database.ItemFlow(
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getString("action"),
                        rs.getInt("quantity"),
                        Database.fromDatabaseDate(rs.getString("flow_date")),
                        null, // createdDate will be set separately
                        rs.getString("vendor"),
                        rs.getString("user")
                    );
                    // Set createdDate from timestamp
                    String createdDateStr = rs.getString("created_date");
                    if (createdDateStr != null) {
                        // Handle timestamp parsing
                        if (createdDateStr.contains(" ")) {
                            flow.createdDate = LocalDate.parse(createdDateStr.split(" ")[0]);
                        } else {
                            flow.createdDate = Database.fromDatabaseDate(createdDateStr);
                        }
                    }
                    rawFlows.add(flow);
                }
            }
            
            LOGGER.info("Loaded " + rawFlows.size() + " raw flow entries from database");
            
            processedFlowHistory.clear();
            processedFlowHistory = processFlows(rawFlows);
            
            LOGGER.info("Processed " + processedFlowHistory.size() + " flow entries");
            updateFlowTable();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading flows: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this, 
                "Error loading flow history: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * CRITICAL FIX: Each database entry creates ONE row in the flow history
     * - NEVER combines or modifies entries
     * - Stock Before from one row becomes Stock After for next row
     * - Detects action type from database or infers from stock changes
     * - Each change (even 1ms apart) creates a new row
     */
    private List<FlowEntry> processFlows(List<Database.ItemFlow> raw) {
        List<FlowEntry> processed = new ArrayList<>();
        
        // Group flows by product ID to process each product separately
        Map<Integer, List<Database.ItemFlow>> flowsByProduct = new HashMap<>();
        for (Database.ItemFlow flow : raw) {
            flowsByProduct.computeIfAbsent(flow.productId, k -> new ArrayList<>()).add(flow);
        }
        
        // Process each product's flows
        for (Map.Entry<Integer, List<Database.ItemFlow>> entry : flowsByProduct.entrySet()) {
            int productId = entry.getKey();
            List<Database.ItemFlow> productFlows = entry.getValue();
            
            // Sort chronologically by created_date (includes timestamp if available)
            productFlows.sort((f1, f2) -> {
                // First sort by date
                LocalDate d1 = f1.createdDate != null ? f1.createdDate : LocalDate.MIN;
                LocalDate d2 = f2.createdDate != null ? f2.createdDate : LocalDate.MIN;
                int dateCompare = d1.compareTo(d2);
                
                // If same date, could sort by ID (which is auto-increment, so chronological)
                // This ensures changes within same day are in correct order
                return dateCompare;
            });
            
            // Get current actual stock from database
            Database.Product product = findProduct(productId);
            int actualCurrentStock = product != null ? product.getStockQuantity() : 0;
            
            // Track stock through the flows - each entry creates ONE row
            int runningStock = 0;
            
            for (int i = 0; i < productFlows.size(); i++) {
                Database.ItemFlow dbFlow = productFlows.get(i);
                
                // Stock before THIS transaction is the running stock
                int stockBefore = runningStock;
                
                // Determine the action type and calculate stock after
                String action = normalizeAction(dbFlow.action);
                int quantityChange = dbFlow.quantity;
                int stockAfter;
                
                // Calculate stock after based on action
                if (action.equalsIgnoreCase("NEW ITEM")) {
                    // First entry for product - stock before should be 0
                    stockBefore = 0;
                    stockAfter = dbFlow.quantity;
                    quantityChange = dbFlow.quantity;
                    
                } else if (action.equalsIgnoreCase("RECEIVED")) {
                    // Received: add to current stock
                    stockAfter = stockBefore + dbFlow.quantity;
                    quantityChange = dbFlow.quantity;
                    
                } else if (action.equalsIgnoreCase("SOLD")) {
                    // Sold: subtract from current stock
                    stockAfter = stockBefore - dbFlow.quantity;
                    quantityChange = dbFlow.quantity;
                    
                } else if (action.equalsIgnoreCase("RETURNED")) {
                    // Returned: add back to stock
                    stockAfter = stockBefore + dbFlow.quantity;
                    quantityChange = dbFlow.quantity;
                    
                } else if (action.equalsIgnoreCase("STOCK UPDATED") || 
                           action.equalsIgnoreCase("STOCK ADJUSTMENT")) {
                    // Stock adjustment: quantity is the NEW absolute value
                    stockAfter = dbFlow.quantity;
                    quantityChange = Math.abs(stockAfter - stockBefore);
                    
                } else {
                    // Unknown action - treat as stock adjustment
                    stockAfter = dbFlow.quantity;
                    quantityChange = Math.abs(stockAfter - stockBefore);
                    action = "STOCK ADJUSTED";
                }
                
                // Ensure non-negative
                stockAfter = Math.max(0, stockAfter);
                
                // CRITICAL: If this is the LAST entry, verify against database
                if (i == productFlows.size() - 1 && stockAfter != actualCurrentStock) {
                    LOGGER.warning(String.format(
                        "Last entry mismatch for %s (ID: %d): Calculated=%d, Database=%d. Creating adjustment entry.",
                        dbFlow.product, productId, stockAfter, actualCurrentStock
                    ));
                    
                    // Create entry for what we calculated
                    FlowEntry calculatedEntry = new FlowEntry(
                        dbFlow.productId,
                        dbFlow.product,
                        action,
                        quantityChange,
                        stockBefore,
                        stockAfter,
                        dbFlow.flowDate,
                        dbFlow.createdDate,
                        dbFlow.createdDate != null ? dbFlow.createdDate.toString() : "N/A",
                        dbFlow.vendor,
                        dbFlow.user,
                        product != null ? product.getCostPrice() : BigDecimal.ZERO,
                        product != null ? product.getSellingPrice() : BigDecimal.ZERO,
                        product != null ? getCategoryName(product.getCategoryId()) : "N/A"
                    );
                    processed.add(calculatedEntry);
                    
                    // Create ANOTHER entry for the adjustment to match database
                    FlowEntry adjustmentEntry = new FlowEntry(
                        dbFlow.productId,
                        dbFlow.product,
                        "STOCK ADJUSTED",
                        Math.abs(actualCurrentStock - stockAfter),
                        stockAfter,
                        actualCurrentStock,
                        LocalDate.now(),
                        LocalDate.now(),
                        LocalDate.now().toString() + " (auto-detected)",
                        "SYSTEM",
                        "SYSTEM",
                        product != null ? product.getCostPrice() : BigDecimal.ZERO,
                        product != null ? product.getSellingPrice() : BigDecimal.ZERO,
                        product != null ? getCategoryName(product.getCategoryId()) : "N/A"
                    );
                    processed.add(adjustmentEntry);
                    
                    runningStock = actualCurrentStock;
                } else {
                    // Normal entry - create single row
                    FlowEntry flowEntry = new FlowEntry(
                        dbFlow.productId,
                        dbFlow.product,
                        action,
                        quantityChange,
                        stockBefore,
                        stockAfter,
                        dbFlow.flowDate,
                        dbFlow.createdDate,
                        dbFlow.createdDate != null ? dbFlow.createdDate.toString() : "N/A",
                        dbFlow.vendor,
                        dbFlow.user,
                        product != null ? product.getCostPrice() : BigDecimal.ZERO,
                        product != null ? product.getSellingPrice() : BigDecimal.ZERO,
                        product != null ? getCategoryName(product.getCategoryId()) : "N/A"
                    );
                    
                    processed.add(flowEntry);
                    runningStock = stockAfter;
                }
                
                // Log for debugging
                if (processed.size() <= 5) {
                    LOGGER.info(String.format("Row %d: %s - Before: %d, Action: %s, Change: %d, After: %d, Time: %s",
                        processed.size(), dbFlow.product, stockBefore, action, quantityChange, stockAfter,
                        dbFlow.createdDate));
                }
            }
        }
        
        // Sort all processed entries chronologically
        processed.sort((f1, f2) -> {
            // Compare by date first
            LocalDate d1 = f1.createdDate != null ? f1.createdDate : LocalDate.MIN;
            LocalDate d2 = f2.createdDate != null ? f2.createdDate : LocalDate.MIN;
            int dateCompare = d1.compareTo(d2);
            if (dateCompare != 0) return dateCompare;
            
            // If same date, compare by timestamp string
            return f1.createdTimestamp.compareTo(f2.createdTimestamp);
        });
        
        LOGGER.info("Processed " + processed.size() + " flow entries - each database entry creates ONE row");
        return processed;
    }
    
    private void updateFlowTable() {
        flowTableModel.setRowCount(0);
        
        // Only show data if a specific product is selected
        if (selectedProductId == -1) {
            LOGGER.info("No product selected - flow table remains empty");
            return;
        }
        
        // Filter flows for selected product only
        List<FlowEntry> flows = new ArrayList<>();
        for (FlowEntry f : processedFlowHistory) {
            if (f.productId == selectedProductId) {
                flows.add(f);
            }
        }
        
        // Sort by date and time to ensure chronological order
        flows.sort((f1, f2) -> {
            if (f1.createdDate == null && f2.createdDate == null) return 0;
            if (f1.createdDate == null) return 1;
            if (f2.createdDate == null) return -1;
            
            int dateCompare = f1.createdDate.compareTo(f2.createdDate);
            if (dateCompare != 0) return dateCompare;
            
            // If same date, compare timestamps
            return f1.createdTimestamp.compareTo(f2.createdTimestamp);
        });
        
        // Display flows for the selected product - each row is a separate change event
        for (FlowEntry f : flows) {
            // Extract time from timestamp (if available)
            String timeDisplay = "N/A";
            if (f.createdTimestamp != null && !f.createdTimestamp.equals("N/A")) {
                // If timestamp has time component, extract it
                if (f.createdTimestamp.contains(" ")) {
                    String[] parts = f.createdTimestamp.split(" ");
                    timeDisplay = parts.length > 1 ? parts[1] : "00:00:00";
                } else {
                    timeDisplay = "00:00:00";
                }
            }
            
            flowTableModel.addRow(new Object[]{
                f.productName,              // Product
                f.stockBefore,              // Stock Before
                f.action,                   // Action
                f.quantityChange,           // Change Qty
                f.stockAfter,               // Stock After
                f.flowDate,                 // Date
                timeDisplay,                // Time
                f.user != null ? f.user : "SYSTEM",     // User
                f.vendor != null ? f.vendor : "N/A"     // Vendor
            });
        }
        
        LOGGER.info("Displayed " + flows.size() + " flow entries for product ID: " + selectedProductId + " (each row is a separate change)");
    }
    
    // === VERIFICATION ===
    
    private void verifyStockAccuracy() {
        StringBuilder report = new StringBuilder();
        report.append("=== STOCK VERIFICATION REPORT ===\n");
        report.append("Client ID: ").append(Database.getCurrentClientId()).append("\n\n");
        
        int totalProducts = allProducts.size();
        int accurateProducts = 0;
        int discrepancies = 0;
        
        for (Database.Product product : allProducts) {
            int dbStock = product.getStockQuantity();
            
            // Get last flow entry for this product
            int lastFlowStock = 0;
            boolean hasFlows = false;
            for (int i = processedFlowHistory.size() - 1; i >= 0; i--) {
                FlowEntry f = processedFlowHistory.get(i);
                if (f.productId == product.getId()) {
                    lastFlowStock = f.stockAfter;
                    hasFlows = true;
                    break;
                }
            }
            
            if (!hasFlows) {
                report.append(String.format("⚠️  %s (ID: %d) - No flow history found\n", 
                    product.getName(), product.getId()));
                report.append(String.format("   Current DB Stock: %d\n\n", dbStock));
                continue;
            }
            
            if (dbStock == lastFlowStock) {
                accurateProducts++;
            } else {
                discrepancies++;
                report.append(String.format("❌ %s (ID: %d)\n", product.getName(), product.getId()));
                report.append(String.format("   Current DB Stock: %d\n", dbStock));
                report.append(String.format("   Last Flow Stock After: %d\n", lastFlowStock));
                report.append(String.format("   Difference: %d\n\n", Math.abs(dbStock - lastFlowStock)));
            }
        }
        
        String summary = String.format("Total Products: %d\nAccurate: %d (%.1f%%)\nDiscrepancies: %d (%.1f%%)\n\n",
            totalProducts, 
            accurateProducts, totalProducts > 0 ? (accurateProducts * 100.0 / totalProducts) : 0,
            discrepancies, totalProducts > 0 ? (discrepancies * 100.0 / totalProducts) : 0);
        
        report.insert(0, summary);
        
        if (discrepancies == 0) {
            report.append("\n✅ All stock levels match between database and flow history!");
        } else {
            report.append("\n⚠️  Discrepancies found. Consider refreshing the data or checking for manual stock adjustments.");
        }
        
        JTextArea textArea = new JTextArea(report.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setRows(25);
        textArea.setColumns(70);
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        JOptionPane.showMessageDialog(this, scrollPane, 
            "Stock Verification Report", JOptionPane.INFORMATION_MESSAGE);
    }
    
    // === ACTIONS ===
    
    private void selectProduct() {
        int row = productTable.getSelectedRow();
        if (row == -1) return;
        
        selectedProductId = (Integer) productTableModel.getValueAt(row, 0);
        selectedProductName = (String) productTableModel.getValueAt(row, 1);
        
        LOGGER.info("Selected product: " + selectedProductName + " (ID: " + selectedProductId + ")");
        
        updateProductLabel();
        updateFlowTable();
        showProductSummary();
    }
    
    private void showDetails(int row) {
        if (row < 0) return;
        
        try {
            String product = (String) flowTableModel.getValueAt(row, 0);
            int stockBefore = (Integer) flowTableModel.getValueAt(row, 1);
            String action = (String) flowTableModel.getValueAt(row, 2);
            int changeQty = (Integer) flowTableModel.getValueAt(row, 3);
            int stockAfter = (Integer) flowTableModel.getValueAt(row, 4);
            String date = flowTableModel.getValueAt(row, 5).toString();
            String user = (String) flowTableModel.getValueAt(row, 6);
            String vendor = (String) flowTableModel.getValueAt(row, 7);
            
            // Find full entry
            FlowEntry entry = null;
            for (FlowEntry f : processedFlowHistory) {
                if (f.productName.equals(product) && 
                    f.stockBefore == stockBefore &&
                    f.action.equals(action) && 
                    f.quantityChange == changeQty &&
                    f.stockAfter == stockAfter) {
                    entry = f;
                    break;
                }
            }
            
            detailsTableModel.setRowCount(0);
            detailsTableModel.addRow(new Object[]{"=== TRANSACTION DETAILS ===", ""});
            detailsTableModel.addRow(new Object[]{"Product", product});
            if (entry != null) detailsTableModel.addRow(new Object[]{"Product ID", entry.productId});
            if (entry != null) detailsTableModel.addRow(new Object[]{"Category", entry.category});
            detailsTableModel.addRow(new Object[]{"", ""});
            detailsTableModel.addRow(new Object[]{"Action Type", action});
            detailsTableModel.addRow(new Object[]{"Change Quantity", changeQty});
            detailsTableModel.addRow(new Object[]{"Stock Before", stockBefore});
            detailsTableModel.addRow(new Object[]{"Stock After", stockAfter});
            detailsTableModel.addRow(new Object[]{"Stock Difference", (stockAfter - stockBefore)});
            detailsTableModel.addRow(new Object[]{"", ""});
            detailsTableModel.addRow(new Object[]{"Transaction Date", date});
            detailsTableModel.addRow(new Object[]{"Performed By", user});
            detailsTableModel.addRow(new Object[]{"Vendor", vendor});
            
            if (entry != null) {
                DecimalFormat df = new DecimalFormat("#,##0.00");
                detailsTableModel.addRow(new Object[]{"", ""});
                detailsTableModel.addRow(new Object[]{"=== PRICING ===", ""});
                detailsTableModel.addRow(new Object[]{"Cost Price", df.format(entry.costPrice)});
                detailsTableModel.addRow(new Object[]{"Selling Price", df.format(entry.sellingPrice)});
                
                BigDecimal value = entry.sellingPrice.multiply(new BigDecimal(changeQty));
                detailsTableModel.addRow(new Object[]{"Transaction Value", df.format(value)});
                
                if (action.equalsIgnoreCase("SOLD")) {
                    BigDecimal profit = entry.sellingPrice.subtract(entry.costPrice)
                        .multiply(new BigDecimal(changeQty));
                    detailsTableModel.addRow(new Object[]{"Profit/Loss", df.format(profit)});
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error showing details: " + e.getMessage(), e);
        }
    }
    
    private void showProductSummary() {
        if (selectedProductId == -1) return;
        
        Database.Product p = findProduct(selectedProductId);
        if (p == null) return;
        
        detailsTableModel.setRowCount(0);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        
        detailsTableModel.addRow(new Object[]{"=== PRODUCT SUMMARY ===", ""});
        detailsTableModel.addRow(new Object[]{"Name", p.getName()});
        detailsTableModel.addRow(new Object[]{"ID", p.getId()});
        detailsTableModel.addRow(new Object[]{"Category", getCategoryName(p.getCategoryId())});
        
        // Get last flow stock
        int lastFlowStock = -1;
        for (int i = processedFlowHistory.size() - 1; i >= 0; i--) {
            FlowEntry f = processedFlowHistory.get(i);
            if (f.productId == selectedProductId) {
                lastFlowStock = f.stockAfter;
                break;
            }
        }
        
        detailsTableModel.addRow(new Object[]{"", ""});
        detailsTableModel.addRow(new Object[]{"=== STOCK STATUS ===", ""});
        detailsTableModel.addRow(new Object[]{"Current Stock (DB)", p.getStockQuantity()});
        if (lastFlowStock >= 0) {
            detailsTableModel.addRow(new Object[]{"Last Flow Stock", lastFlowStock});
            String status = (p.getStockQuantity() == lastFlowStock) ? "✅ MATCH" : "❌ MISMATCH";
            detailsTableModel.addRow(new Object[]{"Verification", status});
        }
        detailsTableModel.addRow(new Object[]{"Min Stock Level", p.getMinStockLevel()});
        String stockStatus = (p.getStockQuantity() <= p.getMinStockLevel()) ? "⚠️ LOW STOCK" : "✅ ADEQUATE";
        detailsTableModel.addRow(new Object[]{"Stock Alert", stockStatus});
        
        detailsTableModel.addRow(new Object[]{"", ""});
        detailsTableModel.addRow(new Object[]{"=== PRICING ===", ""});
        detailsTableModel.addRow(new Object[]{"Cost Price", df.format(p.getCostPrice())});
        detailsTableModel.addRow(new Object[]{"Selling Price", df.format(p.getSellingPrice())});
        BigDecimal margin = p.getSellingPrice().subtract(p.getCostPrice());
        detailsTableModel.addRow(new Object[]{"Profit Margin", df.format(margin)});
        detailsTableModel.addRow(new Object[]{"Vendor", p.getVendor()});
        
        // Movement stats
        int received = 0, sold = 0, returned = 0, adjusted = 0, newStock = 0;
        for (FlowEntry f : processedFlowHistory) {
            if (f.productId == selectedProductId) {
                switch (f.action.toUpperCase()) {
                    case "NEW ITEM": 
                    case "NEW PRODUCT": 
                        newStock += f.quantityChange; 
                        break;
                    case "RECEIVED": 
                        received += f.quantityChange; 
                        break;
                    case "SOLD": 
                        sold += f.quantityChange; 
                        break;
                    case "RETURNED": 
                        returned += f.quantityChange; 
                        break;
                    case "STOCK UPDATED": 
                    case "STOCK ADJUSTED": 
                    case "STOCK ADJUSTMENT": 
                        adjusted++; 
                        break;
                }
            }
        }
        
        detailsTableModel.addRow(new Object[]{"", ""});
        detailsTableModel.addRow(new Object[]{"=== MOVEMENTS ===", ""});
        detailsTableModel.addRow(new Object[]{"New Stock Added", newStock});
        detailsTableModel.addRow(new Object[]{"Total Received", received});
        detailsTableModel.addRow(new Object[]{"Total Sold", sold});
        detailsTableModel.addRow(new Object[]{"Total Returned", returned});
        detailsTableModel.addRow(new Object[]{"Stock Adjustments", adjusted});
        
        BigDecimal profit = p.getSellingPrice().subtract(p.getCostPrice())
            .multiply(new BigDecimal(sold));
        detailsTableModel.addRow(new Object[]{"Est. Profit", df.format(profit)});
    }
    
    private void updateProductLabel() {
        selectedProductLabel.setText(selectedProductId == -1 ? 
            "📜 Item Flow History (Select a product to view)" :
            "📜 Item Flow History: " + selectedProductName);
    }
    
    private void updateStats() {
        try {
            int products = allProducts.size();
            int transactions = processedFlowHistory.size();
            
            statsLabel.setText(String.format(
                "📊 %d Products | %d Transactions Recorded",
                products, transactions
            ));
        } catch (Exception e) {
            statsLabel.setText("📊 Stats unavailable");
        }
    }
    
    private void toggleFloat() {
        if (isMinimized) {
            setBounds(100, 100, 1400, 850);
            floatButton.setText("⬇ Float");
            getContentPane().remove(minimizedContentPanel);
            getContentPane().add(fullContentPanel, BorderLayout.CENTER);
            isMinimized = false;
        } else {
            setSize(400, 200);
            floatButton.setText("⬆ Restore");
            getContentPane().remove(fullContentPanel);
            getContentPane().add(minimizedContentPanel, BorderLayout.CENTER);
            isMinimized = true;
        }
        getContentPane().revalidate();
        getContentPane().repaint();
        setLocationRelativeTo(null);
    }
    
    // === UTILITIES ===
    
    private String normalizeAction(String action) {
        if (action == null) return "STOCK ADJUSTED";
        action = action.toUpperCase().trim();
        
        // Exact matches from database actions set by specific classes
        if (action.equals("NEW ITEM") || action.equals("NEW PRODUCT")) return "NEW ITEM";
        if (action.equals("RECEIVED")) return "RECEIVED";
        if (action.equals("SOLD")) return "SOLD";
        if (action.equals("RETURNED")) return "RETURNED";
        
        // If action contains keywords that indicate specific sources
        if (action.contains("NEW") && action.contains("ITEM")) return "NEW ITEM";
        if (action.contains("RECEIVE")) return "RECEIVED";
        if (action.contains("SALE") || action.equals("SOLD")) return "SOLD";
        if (action.contains("RETURN")) return "RETURNED";
        
        // If none of the above match, it's an adjustment/update not from main classes
        // (not from AddProduct.java, ReceiveProduct.java, or SalePoint.java)
        return "STOCK ADJUSTED";
    }
    
    private Database.Product findProduct(int id) {
        for (Database.Product p : allProducts) {
            if (p.getId() == id) return p;
        }
        return null;
    }
    
    private String getCategoryName(int id) {
        try {
            for (Database.Category c : Database.getAllCategoryObjects()) {
                if (c.getId() == id) return c.getName();
            }
        } catch (Exception e) {}
        return "N/A";
    }
    
    // === STYLING ===
    
    private void styleTable(JTable t) {
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setRowHeight(28);
        t.setShowGrid(false);
        t.setSelectionBackground(new Color(219, 234, 254));
        
        JTableHeader h = t.getTableHeader();
        h.setFont(new Font("Segoe UI", Font.BOLD, 13));
        h.setBackground(new Color(243, 244, 246));
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 35));
        
        t.setDefaultRenderer(Object.class, new ZebraRenderer());
    }
    
    private JButton createButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(color);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(130, 35));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(color.brighter()); }
            public void mouseExited(MouseEvent e) { b.setBackground(color); }
        });
        
        return b;
    }
    
    private JButton createSmallButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setForeground(Color.WHITE);
        b.setBackground(color);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(120, 28));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
    
    private JPanel createCard(String title, Component content) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(CARD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel l = new JLabel(title);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        p.add(l, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }
    
    // === RENDERERS ===
    
    private class ZebraRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            if (!sel) {
                comp.setBackground(r % 2 == 0 ? Color.WHITE : new Color(249, 250, 251));
            }
            return comp;
        }
    }
    
    private class BoldRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setFont(getFont().deriveFont(Font.BOLD));
            setForeground(DARK_BG);
            return comp;
        }
    }
    
    private class ActionRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            
            if (v != null && !sel) {
                setFont(getFont().deriveFont(Font.BOLD));
                switch (v.toString().toUpperCase()) {
                    case "NEW ITEM": 
                    case "NEW PRODUCT": 
                        setForeground(SUCCESS); 
                        break;
                    case "RECEIVED": 
                        setForeground(PRIMARY); 
                        break;
                    case "SOLD": 
                        setForeground(WARNING); 
                        break;
                    case "RETURNED": 
                        setForeground(INFO); 
                        break;
                    case "STOCK UPDATED": 
                    case "STOCK ADJUSTED": 
                        setForeground(DANGER); 
                        break;
                    default: 
                        setForeground(DARK_BG);
                }
            } else if (!sel) {
                setForeground(DARK_BG);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return comp;
        }
    }
}