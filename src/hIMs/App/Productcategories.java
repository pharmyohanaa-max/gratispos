package hIMs.App;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class Productcategories extends JDialog {
    private JList<String> categoryList;
    private DefaultListModel<String> categoryModel;
    private JTextPane itemDisplay;
    private Map<String, List<String[]>> categoryItems;
    private JButton addCategoryBtn, deleteCategoryBtn, viewAllBtn, floatBtn, createVendorBtn;
    private boolean isFloating = false;
    private final Dimension originalSize = new Dimension(900, 600);
    private final Dimension floatSize = new Dimension(350, 250);
    private static final Logger LOGGER = Logger.getLogger(Productcategories.class.getName());

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
                JFrame parent = new JFrame();
                parent.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                parent.setSize(400, 300);
                parent.setLocationRelativeTo(null);
                parent.setVisible(true);
                Productcategories dialog = new Productcategories(parent);
                dialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Productcategories(JFrame parent) {
        super(parent, "Point Of Sale Inventory System", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(originalSize);
        setLocationRelativeTo(parent);

        categoryModel = new DefaultListModel<>();
        categoryList = new JList<>(categoryModel);
        itemDisplay = new JTextPane();
        itemDisplay.setContentType("text/html");
        itemDisplay.setEditable(false);
        categoryItems = new HashMap<>();

        initializeUI();
        loadDataFromDatabase();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isFloating) toggleFloatMode();
            }
        });
    }

    private void initializeUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(UIManager.getColor("Panel.background"));

        JLabel headerLabel = new JLabel("PoS INVENTORY MANAGEMENT", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerLabel.setForeground(new Color(0x2C3E50));
        headerLabel.setBorder(new EmptyBorder(0, 0, 20, 0));
        mainPanel.add(headerLabel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0);
        splitPane.setBorder(null);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.setBackground(UIManager.getColor("Panel.background"));
        leftPanel.setBorder(new LineBorder(UIManager.getColor("Separator.foreground"), 1));

        JLabel categoriesLabel = new JLabel("Categories");
        categoriesLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        categoriesLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        categoriesLabel.setForeground(new Color(0x34495E));
        leftPanel.add(categoriesLabel, BorderLayout.NORTH);

        categoryList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFixedCellHeight(30);
        categoryList.setBackground(UIManager.getColor("TextField.background"));
        categoryList.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane listScroll = new JScrollPane(categoryList);
        listScroll.setBorder(null);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBackground(UIManager.getColor("Panel.background"));
        rightPanel.setBorder(new LineBorder(UIManager.getColor("Separator.foreground"), 1));

        JLabel inventoryLabel = new JLabel("Inventory Details");
        inventoryLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        inventoryLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inventoryLabel.setForeground(new Color(0x34495E));
        rightPanel.add(inventoryLabel, BorderLayout.NORTH);

        JScrollPane displayScroll = new JScrollPane(itemDisplay);
        displayScroll.setBorder(null);
        rightPanel.add(displayScroll, BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(UIManager.getColor("Panel.background"));

        addCategoryBtn = createButton("Add Category");
        deleteCategoryBtn = createButton("Delete Category");
        viewAllBtn = createButton("View All Items");
        floatBtn = createButton("Float");
        createVendorBtn = createButton("Create Vendor");
        createVendorBtn.setToolTipText("Create and name vendors where you purchased your goods");

        addCategoryBtn.addActionListener(e -> addCategory());
        
        viewAllBtn.addActionListener(e -> displayAllItems());
        floatBtn.addActionListener(e -> toggleFloatMode());
        createVendorBtn.addActionListener(e -> openVendorDialog());

        buttonPanel.add(addCategoryBtn);
        buttonPanel.add(deleteCategoryBtn);
        buttonPanel.add(viewAllBtn);
        buttonPanel.add(floatBtn);
        buttonPanel.add(createVendorBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) displayCategoryItems();
        });

        getContentPane().add(mainPanel);
    }

    private JButton createButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(140, 36));
        return b;
    }

    private void openVendorDialog() {
        new VendorDialog(this).setVisible(true);
        loadDataFromDatabase();
    }

    private void toggleFloatMode() {
        if (isFloating) {
            setSize(originalSize);
            floatBtn.setText("Float");
        } else {
            setSize(floatSize);
            floatBtn.setText("Dock");
            if (getParent() != null) {
                getParent().setVisible(true);
                ((JFrame) getParent()).toFront();
            }
        }
        isFloating = !isFloating;
        setLocationRelativeTo(getParent());
    }

    private void loadDataFromDatabase() {
        categoryModel.clear();
        categoryItems.clear();
        itemDisplay.setText("<html><body style='text-align:center;padding:50px;'>Loading categories and products...</body></html>");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Step 1: If online, pull categories directly from cloud
                if (Database.isOnline()) {
                    Database.restoreFromCloudVerifyTables();  // This now only pulls from categories table
                }
                // Step 2: Ensure all categories referenced by products exist
                try {
                    int created = Database.rebuildAllCategoriesFromProducts();
                    if (created > 0) {
                        LOGGER.info("Auto-created " + created + " missing categories before load");
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "rebuildAllCategoriesFromProducts failed", e);
                }
                return null;
            }

            @Override
            protected void done() {
                loadCategoriesAndProducts();
            }
        };
        worker.execute();
    }
    
    /**
     * CRITICAL: Ensures every category referenced by products actually exists
     * This handles the case when user deletes database and logs in again
     */
    
    
    private void loadCategoriesAndProducts() {
        try {
            List<Database.Category> categories = Database.getAllCategoryObjects();
            List<Database.Product> allProducts = Database.getAllProducts();

            // Build valid category ID set
            Set<Integer> validCatIds = new HashSet<>();
            for (Database.Category c : categories) validCatIds.add(c.getId());

            // Group products by category id — only for valid categories
            Map<Integer, List<String[]>> productsByCatId = new HashMap<>();
            for (Database.Product p : allProducts) {
                int catId = p.getCategoryId();
                if (validCatIds.contains(catId)) {
                    productsByCatId
                        .computeIfAbsent(catId, k -> new ArrayList<>())
                        .add(new String[]{p.getName(), String.valueOf(p.getStockQuantity())});
                }
            }

            SwingUtilities.invokeLater(() -> {
                categoryModel.clear();
                categoryItems.clear();

                for (Database.Category cat : categories) {
                    categoryModel.addElement(cat.getName());
                    categoryItems.put(cat.getName(),
                        productsByCatId.getOrDefault(cat.getId(), new ArrayList<>()));
                }

                if (!categories.isEmpty()) {
                    categoryList.setSelectedIndex(0);
                    displayCategoryItems();
                } else {
                    itemDisplay.setText("<html><body style='text-align:center;padding:50px;'>"
                        + "No categories found.<br>Click 'Add Category' to create one.</body></html>");
                }
            });

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading categories and products", e);
            itemDisplay.setText("<html><body style='text-align:center;padding:50px;color:red;'>Error loading data: "
                + e.getMessage() + "</body></html>");
        }
    }
    
    

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this, "Enter new category name:");
        if (name == null || (name = name.trim()).isEmpty()) return;
        name = name.toUpperCase();

        if (categoryModel.contains(name)) {
            JOptionPane.showMessageDialog(this, "Category already exists!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            boolean added = Database.addCategory(name, "");
            if (added) {
                // Sync new category to cloud immediately
                if (Database.isOnline()) {
                    int newId = Database.getCategoryIdByName(name);
                    if (newId > 0) {
                        try {
                            SQLiteCloudClient.executeUpdate(
                                "INSERT OR REPLACE INTO categories (id, name, client_id, user_email) VALUES (?, ?, ?, ?)",
                                newId, name, Database.getCurrentClientId(), Database.getCurrentUserEmail());
                        } catch (IOException ex) {
                            JsonObject catData = new JsonObject();
                            catData.addProperty("id", newId);
                            catData.addProperty("name", name);
                            catData.addProperty("client_id", Database.getCurrentClientId());
                            catData.addProperty("user_email", Database.getCurrentUserEmail());
                            SQLiteCloudClient.queueSyncOperation("categories", newId, "INSERT", catData);
                            LOGGER.warning("Category queued for sync: " + ex.getMessage());
                        }
                    }
                }

                categoryModel.addElement(name);
                categoryItems.put(name, new ArrayList<>());
                categoryList.setSelectedValue(name, true);
                JOptionPane.showMessageDialog(this, "Category '" + name + "' added successfully!");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                JOptionPane.showMessageDialog(this, "Category already exists!", "Duplicate", JOptionPane.WARNING_MESSAGE);
            } else {
                LOGGER.log(Level.SEVERE, "Failed to add category", e);
                JOptionPane.showMessageDialog(this, "Failed to add category: " + e.getMessage());
            }
        }
    }

   

    private void displayCategoryItems() {
        String cat = categoryList.getSelectedValue();
        if (cat == null) return;
        List<String[]> items = categoryItems.get(cat);
        if (items == null) return;

        StringBuilder html = new StringBuilder("<html><body style='font-family:Segoe UI; font-size:14px; color:#2C3E50;'>")
            .append("<h2 style='border-bottom:2px solid #2980B9;padding-bottom:5px;'>").append(cat).append("</h2>")
            .append("<table width='100%' cellspacing='0' cellpadding='8' style='border-collapse:collapse;'>")
            .append("<thead style='background:#2980B9;color:white;'>")
            .append("<th align='left'>Item Name</th><th align='center'>Stock</th>")
            .append("</thead><tbody>");

        if (items.isEmpty()) {
            html.append("<tr><td colspan='2' style='text-align:center;padding:30px;color:#95A5A6;'>No products in this category</td></tr>");
        } else {
            boolean odd = true;
            for (String[] item : items) {
                html.append("<tr style='background:").append(odd ? "#ecf0f1" : "white").append(";'>")
                    .append("<td>").append(item[0]).append("</td>")
                    .append("<td align='center'>").append(item[1]).append("</td>")
                    .append("</tr>");
                odd = !odd;
            }
        }
        html.append("</tbody></table></body></html>");
        itemDisplay.setText(html.toString());
    }

    private void displayAllItems() {
        StringBuilder html = new StringBuilder(
            "<html><body style='font-family:Segoe UI; font-size:14px; color:#2C3E50;'>")
            .append("<h1 style='text-align:center;border-bottom:3px solid #2980B9;padding-bottom:10px;'>FULL INVENTORY LIST</h1>");

        try {
            List<Database.Category> categories = Database.getAllCategoryObjects();
            List<Database.Product> allProducts = Database.getAllProducts();

            // Build valid category ID set and name map
            Map<Integer, String> catNames = new HashMap<>();
            for (Database.Category c : categories) catNames.put(c.getId(), c.getName());

            // Group products by category name — uncategorized products go to "UNCATEGORIZED"
            Map<String, List<Database.Product>> grouped = new LinkedHashMap<>();
            for (Database.Product p : allProducts) {
                int catId = p.getCategoryId();
                String catName = catNames.containsKey(catId) ? catNames.get(catId) : "UNCATEGORIZED";
                grouped.computeIfAbsent(catName, k -> new ArrayList<>()).add(p);
            }

            if (grouped.isEmpty()) {
                html.append("<p style='text-align:center;padding:50px;color:#95A5A6;'>No products found. Add products from the main dashboard.</p>");
            } else {
                // Sort entries alphabetically, but keep UNCATEGORIZED at the bottom
                List<Map.Entry<String, List<Database.Product>>> sortedEntries = new ArrayList<>(grouped.entrySet());
                sortedEntries.sort((a, b) -> {
                    if (a.getKey().equals("UNCATEGORIZED")) return 1;
                    if (b.getKey().equals("UNCATEGORIZED")) return -1;
                    return a.getKey().compareTo(b.getKey());
                });

                for (Map.Entry<String, List<Database.Product>> entry : sortedEntries) {
                    List<Database.Product> products = entry.getValue();

                    // Sort products alphabetically within each category
                    products.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                    html.append("<h2 style='border-bottom:2px solid #2980B9;padding-bottom:5px;margin-top:15px;'>")
                        .append(entry.getKey())
                        .append(" <span style='font-size:12px;color:#7F8C8D;font-weight:normal;'>(")
                        .append(products.size()).append(" item").append(products.size() == 1 ? "" : "s")
                        .append(")</span></h2>")
                        .append("<table width='100%' cellspacing='0' cellpadding='8' style='border-collapse:collapse;margin-bottom:10px;'>")
                        .append("<thead style='background:#2980B9;color:white;'>")
                        .append("<th align='left' style='padding:8px;'>Item</th>")
                        .append("<th align='center' style='padding:8px;'>Stock</th>")
                        .append("<th align='center' style='padding:8px;'>Status</th>")
                        .append("</thead><tbody>");

                    boolean odd = true;
                    for (Database.Product p : products) {
                        int stock = p.getStockQuantity();
                        int minStock = p.getMinStockLevel();

                        // Stock status indicator
                        String statusColor;
                        String statusText;
                        if (stock <= 0) {
                            statusColor = "#E74C3C";
                            statusText = "OUT OF STOCK";
                        } else if (stock <= minStock) {
                            statusColor = "#E67E22";
                            statusText = "LOW STOCK";
                        } else {
                            statusColor = "#27AE60";
                            statusText = "OK";
                        }

                        html.append("<tr style='background:").append(odd ? "#ecf0f1" : "white").append(";'>")
                            .append("<td style='padding:8px;'>").append(p.getName()).append("</td>")
                            .append("<td align='center' style='padding:8px;'>").append(stock).append("</td>")
                            .append("<td align='center' style='padding:8px;'>")
                            .append("<span style='color:white;background:").append(statusColor)
                            .append(";padding:2px 8px;border-radius:4px;font-size:11px;'>")
                            .append(statusText).append("</span>")
                            .append("</td></tr>");
                        odd = !odd;
                    }
                    html.append("</tbody></table>");
                }

                // Summary footer
                int totalProducts = allProducts.size();
                long outOfStock = allProducts.stream().filter(p -> p.getStockQuantity() <= 0).count();
                long lowStock = allProducts.stream()
                    .filter(p -> p.getStockQuantity() > 0 && p.getStockQuantity() <= p.getMinStockLevel())
                    .count();

                html.append("<hr style='border:1px solid #BDC3C7;margin-top:20px;'>")
                    .append("<table width='100%' style='margin-top:10px;'>")
                    .append("<tr>")
                    .append("<td align='center'><b>Total Products:</b> ").append(totalProducts).append("</td>")
                    .append("<td align='center' style='color:#E74C3C;'><b>Out of Stock:</b> ").append(outOfStock).append("</td>")
                    .append("<td align='center' style='color:#E67E22;'><b>Low Stock:</b> ").append(lowStock).append("</td>")
                    .append("</tr></table>");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading all items", e);
            html.append("<p style='color:red;padding:20px;'>Error loading inventory: ").append(e.getMessage()).append("</p>");
        }

        html.append("</body></html>");
        itemDisplay.setText(html.toString());
        itemDisplay.setCaretPosition(0);
    }

    // ================================================================
    // VENDOR DIALOG (Simplified - just the essentials)
    // ================================================================
    public static class VendorDialog extends JDialog {
        private JTextPane vendorDisplay;

        public VendorDialog(JDialog owner) {
            super(owner, "Vendor Management", true);
            setSize(720, 580);
            setLocationRelativeTo(owner);
            initUI();
            loadVendors();
        }

        private void initUI() {
            JPanel main = new JPanel(new BorderLayout(10, 10));
            main.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel title = new JLabel("Vendor List", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 20));
            main.add(title, BorderLayout.NORTH);

            vendorDisplay = new JTextPane();
            vendorDisplay.setContentType("text/html");
            vendorDisplay.setEditable(false);
            JScrollPane sp = new JScrollPane(vendorDisplay);
            main.add(sp, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

            JButton addBtn = new JButton("Add Vendor");
            addBtn.setPreferredSize(new Dimension(150, 38));
            addBtn.setBackground(new Color(46, 204, 113));
            addBtn.setForeground(Color.WHITE);
            addBtn.addActionListener(e -> showAddVendorDialog());

            JButton refreshBtn = new JButton("Refresh");
            refreshBtn.setPreferredSize(new Dimension(150, 38));
            refreshBtn.addActionListener(e -> loadVendors());

            btns.add(addBtn);
            btns.add(refreshBtn);

            main.add(btns, BorderLayout.SOUTH);
            add(main);
        }

        private void showAddVendorDialog() {
            String name = JOptionPane.showInputDialog(this, "Enter vendor name:");
            if (name == null || name.trim().isEmpty()) return;
            
            name = name.trim();
            String clientId = Database.getCurrentClientId();
            String userEmail = Database.getCurrentUserEmail();
            
            // Check for duplicate
            try (Connection conn = Database.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement(
                     "SELECT name FROM vendors WHERE UPPER(name) = UPPER(?) AND client_id = ?")) {
                checkStmt.setString(1, name);
                checkStmt.setString(2, clientId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Vendor already exists!", "Duplicate", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO vendors(name, client_id, user_email) VALUES(?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, clientId);
                ps.setString(3, userEmail);
                ps.executeUpdate();
                
                if (Database.isOnline()) {
                    try {
                        Database.syncToCloud();
                    } catch (SQLException ex) {
                        System.err.println("Cloud sync failed: " + ex.getMessage());
                    }
                }
                
                loadVendors();
                JOptionPane.showMessageDialog(this, "Vendor added successfully!");
                
            } catch (SQLException e) {
                if (e.getMessage().contains("UNIQUE")) {
                    JOptionPane.showMessageDialog(this, "Vendor already exists!", "Duplicate", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
                }
            }
        }

        private void loadVendors() {
            String clientId = Database.getCurrentClientId();
            
            StringBuilder html = new StringBuilder("<html><body style='font-family:Segoe UI;padding:15px;'>")
                .append("<table width='100%' cellspacing='0' cellpadding='12' style='border-collapse:collapse;'>")
                .append("<thead style='background:#3498DB;color:white;'>")
                .append("<th align='left'>Vendor Name</th>")
                .append("</thead><tbody>");

            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM vendors WHERE client_id = ? ORDER BY name")) {
                
                ps.setString(1, clientId);
                ResultSet rs = ps.executeQuery();

                boolean has = false;
                boolean odd = true;
                while (rs.next()) {
                    has = true;
                    String n = rs.getString("name");
                    html.append("<tr style='background:").append(odd ? "#ECF0F1" : "white").append(";'>")
                        .append("<td style='padding:12px;font-weight:500;'>").append(n).append("</td>")
                        .append("</tr>");
                    odd = !odd;
                }
                if (!has) {
                    html.append("<tr><td style='text-align:center;padding:40px;color:#95A5A6;font-style:italic;'>")
                        .append("No vendors yet. Click 'Add Vendor'</td></tr>");
                }
            } catch (SQLException e) {
                html.append("<tr><td style='text-align:center;padding:40px;color:#95A5A6;'>")
                    .append("Error loading vendors: ").append(e.getMessage()).append("</td></tr>");
            }
            html.append("</tbody></table></body></html>");
            vendorDisplay.setText(html.toString());
            vendorDisplay.setCaretPosition(0);
        }
    }
}