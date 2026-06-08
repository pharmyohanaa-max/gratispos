package hIMs.App;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import com.toedter.calendar.JDateChooser;
import java.util.prefs.Preferences;

public class Receiveproduct extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(Receiveproduct.class.getName());
    
    // Universal colors using UIManager (works on Linux & Windows)
    private Color getUniversalColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
    
    // Dynamic colors that adapt to system theme
    private Color PRIMARY_COLOR;
    private Color SUCCESS_COLOR;
    private Color DANGER_COLOR;
    private Color BACKGROUND;
    private Color CARD_BACKGROUND;
    private Color TEXT_PRIMARY;
    private Color TEXT_SECONDARY;
    private Color BORDER_COLOR;
    private Color INFO_COLOR;
    private Color WARNING_COLOR;
    
    // UI Components
    private JTextField searchField;
    private JComboBox<String> vendorCombo;
    private JTable searchTable;
    private DefaultTableModel searchTableModel;
    private JTable receiveTable;
    private DefaultTableModel receiveTableModel;
    private List<ReceiveEntry> receivedProducts;
    private String currentUser;
    
    // Statistics Labels
    private JLabel costOfGoodsLabel;
    private JLabel profitLabel;
    private JLabel entriesLabel;
    private JLabel quantityEntriesLabel;
    private JLabel vendorEntriesLabel;

    /**
     * Inner class to hold receive entry data
     */
    private static class ReceiveEntry {
        int productId;
        String productName;
        String category;
        BigDecimal costPrice;
        BigDecimal sellingPrice;
        int receivedQuantity;
        int oldStock;
        int newStock;
        LocalDate expiryDate;
        String batchNumber;
        String invoiceNumber;

        public ReceiveEntry(int productId, String productName, String category,
                      BigDecimal costPrice, BigDecimal sellingPrice,
                      int receivedQuantity, int oldStock, int newStock,
                      LocalDate expiryDate, String batchNumber, String invoiceNumber) {
            this.productId = productId;
            this.productName = productName;
            this.category = category;
            this.costPrice = costPrice;
            this.sellingPrice = sellingPrice;
            this.receivedQuantity = receivedQuantity;
            this.oldStock = oldStock;
            this.newStock = newStock;
            this.expiryDate = expiryDate;
            this.batchNumber = batchNumber;
            this.invoiceNumber = invoiceNumber;
        }
    }

    public Receiveproduct(Frame parent, String username) {
        super(parent, "Receive Products", false);
        this.currentUser = username;
        this.receivedProducts = new ArrayList<>();
        
        // Initialize universal colors based on system theme
        initializeUniversalColors();
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Make dialog resizable
        setResizable(true);
        
        // Auto-detect screen size and set appropriate dialog size (95% of screen)
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.95);
        int height = (int) (screenSize.height * 0.92);
        setSize(width, height);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(parent);
        
        // Add component listener for responsive behavior
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustLayoutForSize();
            }
        });
        
        initializeUI();
        loadReceiveListFromLocalStorage();
        loadVendorsIntoCombo();
        performSearch();
    }
    
    private void initializeUniversalColors() {
        // Get colors from UIManager (FlatLaf theme - works on both Linux and Windows)
        PRIMARY_COLOR = getUniversalColor("Component.accentColor", new Color(37, 99, 235));
        SUCCESS_COLOR = getUniversalColor("Component.successColor", new Color(34, 197, 94));
        DANGER_COLOR = getUniversalColor("Component.errorColor", new Color(239, 68, 68));
        BACKGROUND = getUniversalColor("Panel.background", new Color(249, 250, 251));
        CARD_BACKGROUND = getUniversalColor("Panel.background", Color.WHITE);
        TEXT_PRIMARY = getUniversalColor("Label.foreground", new Color(17, 24, 39));
        TEXT_SECONDARY = getUniversalColor("Label.disabledForeground", new Color(107, 114, 128));
        BORDER_COLOR = getUniversalColor("Separator.foreground", new Color(229, 231, 235));
        INFO_COLOR = new Color(6, 182, 212);
        WARNING_COLOR = new Color(245, 158, 11);
    }

    private void adjustLayoutForSize() {
        int width = getWidth();
        int height = getHeight();
        
        if (width < 900 || height < 700) {
            // Compact mode for small windows
            if (searchField != null) searchField.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            if (vendorCombo != null) vendorCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            if (searchTable != null) {
                searchTable.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                searchTable.setRowHeight(24);
            }
            if (receiveTable != null) {
                receiveTable.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                receiveTable.setRowHeight(28);
            }
        } else {
            // Normal mode
            if (searchField != null) searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            if (vendorCombo != null) vendorCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            if (searchTable != null) {
                searchTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                searchTable.setRowHeight(28);
            }
            if (receiveTable != null) {
                receiveTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                receiveTable.setRowHeight(35);
            }
        }
    }

    private void initializeUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.setBackground(BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(320);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.setBackground(BACKGROUND);
        
        splitPane.setTopComponent(createProductSearchPanel());
        splitPane.setBottomComponent(createReceiveListPanel());
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel titleLabel = new JLabel("Receive Products", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel userLabel = new JLabel("User: " + currentUser, SwingConstants.RIGHT);
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        userLabel.setForeground(Color.WHITE);

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(userLabel, BorderLayout.EAST);

        return headerPanel;
    }

    private JPanel createProductSearchPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
        mainPanel.setBackground(CARD_BACKGROUND);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            new EmptyBorder(10, 10, 10, 10)
        ));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchPanel.setOpaque(false);

        JLabel searchLabel = new JLabel("🔍 Search:");
        searchLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchLabel.setForeground(TEXT_PRIMARY);
        searchField = new JTextField(25);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            new EmptyBorder(6, 10, 6, 10)
        ));

        JLabel vendorLabel = new JLabel("Vendor:");
        vendorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        vendorLabel.setForeground(TEXT_PRIMARY);
        vendorCombo = new JComboBox<>();
        vendorCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        vendorCombo.setPreferredSize(new Dimension(180, 32));

        JButton btnManageVendor = createButton("Manage Vendors", PRIMARY_COLOR);
        btnManageVendor.setPreferredSize(new Dimension(130, 32));
        btnManageVendor.addActionListener(e -> openVendorManagementDialog());

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(vendorLabel);
        searchPanel.add(vendorCombo);
        searchPanel.add(btnManageVendor);

        mainPanel.add(searchPanel, BorderLayout.NORTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { performSearch(); }
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            public void changedUpdate(DocumentEvent e) { performSearch(); }
        });

        JLabel tableTitle = new JLabel("Available Products");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tableTitle.setForeground(TEXT_PRIMARY);
        tableTitle.setBorder(new EmptyBorder(3, 0, 3, 0));

        String[] columns = {"ID", "Product Name", "Category", "Vendor", "Cost Price", "Selling Price", "Current Stock"};
        searchTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        searchTable = createModernTable(searchTableModel);
        searchTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        searchTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        searchTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        searchTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        searchTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        searchTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        searchTable.getColumnModel().getColumn(6).setPreferredWidth(100);

        searchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && searchTable.getSelectedRow() != -1) {
                    openReceiveDialog();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(searchTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollPane.setPreferredSize(new Dimension(0, 227));

        JPanel tablePanel = new JPanel(new BorderLayout(0, 3));
        tablePanel.setOpaque(false);
        tablePanel.add(tableTitle, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(tablePanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createReceiveListPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
        mainPanel.setBackground(CARD_BACKGROUND);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            new EmptyBorder(10, 10, 10, 10)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("Receive List");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        JButton btnClearAll = createButton("🗑️ Clear All", DANGER_COLOR);
        btnClearAll.setPreferredSize(new Dimension(110, 30));
        btnClearAll.addActionListener(e -> clearAllReceiveEntries());
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(btnClearAll, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        String[] columns = {"Product", "Category", "Cost", "Sell Price", "Qty", "Old Stock", "New Stock", "Expiry", "Batch#", "Invoice#", "Actions"};
        receiveTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 10;
            }
        };

        receiveTable = createModernTable(receiveTableModel);
        receiveTable.setRowHeight(35);
        receiveTableModel.addTableModelListener(e -> autoSaveReceiveList());
        receiveTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        receiveTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        receiveTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        receiveTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        receiveTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        receiveTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        receiveTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        receiveTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        receiveTable.getColumnModel().getColumn(8).setPreferredWidth(80);
        receiveTable.getColumnModel().getColumn(9).setPreferredWidth(120);

        receiveTable.getColumnModel().getColumn(10).setCellRenderer(new ActionButtonRenderer());
        receiveTable.getColumnModel().getColumn(10).setCellEditor(new ActionButtonEditor());

        JScrollPane scrollPane = new JScrollPane(receiveTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollPane.setPreferredSize(new Dimension(0, 227));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createFooterPanel() {
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        footerPanel.setBackground(CARD_BACKGROUND);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            new EmptyBorder(12, 20, 12, 20)
        ));

        footerPanel.add(createStatCard("📦 Product Entries", "0", PRIMARY_COLOR, "entries"));
        footerPanel.add(createStatCard("💰 Total Buying Cost", "0", TEXT_SECONDARY, "cost"));
        footerPanel.add(createStatCard("🔢 Quantity Entries", "0", INFO_COLOR, "quantity"));
        footerPanel.add(createStatCard("📈 Total Profit", "0", SUCCESS_COLOR, "profit"));
        footerPanel.add(createActionCard("✓ Save & Receive", SUCCESS_COLOR, "save"));
        footerPanel.add(createActionCard("📊 History", TEXT_SECONDARY, "history"));

        return footerPanel;
    }

    private JPanel createStatCard(String label, String value, Color accentColor, String id) {
        JPanel card = new JPanel(new BorderLayout(6, 2));
        card.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 20));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        
        card.setPreferredSize(new Dimension(160, 65));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        labelComp.setForeground(TEXT_SECONDARY);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 12));
        valueComp.setForeground(accentColor.darker());

        card.add(labelComp, BorderLayout.NORTH);
        card.add(valueComp, BorderLayout.CENTER);

        switch(id) {
            case "entries": entriesLabel = valueComp; break;
            case "cost": costOfGoodsLabel = valueComp; break;
            case "quantity": quantityEntriesLabel = valueComp; break;
            case "profit": profitLabel = valueComp; break;
        }

        return card;
    }

    private JPanel createActionCard(String label, Color bgColor, String action) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createLineBorder(bgColor.darker(), 1));
        card.setPreferredSize(new Dimension(155, 50));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel labelComp = new JLabel(label, SwingConstants.CENTER);
        labelComp.setFont(new Font("Segoe UI", Font.BOLD, 12));
        labelComp.setForeground(Color.WHITE);

        card.add(labelComp, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(bgColor.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(bgColor);
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (action.equals("save")) {
                    saveProducts();
                } else if (action.equals("history")) {
                    showReceiveHistory();
                }
            }
        });

        return card;
    }

    private JButton createButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(10, 20, 10, 20));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });
        
        return button;
    }

    private JTable createModernTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(28);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(new Color(PRIMARY_COLOR.getRed(), PRIMARY_COLOR.getGreen(), PRIMARY_COLOR.getBlue(), 50));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setBackground(CARD_BACKGROUND);
        table.setForeground(TEXT_PRIMARY);
        
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 11));
        header.setBackground(BACKGROUND);
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_COLOR));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
        
        return table;
    }

    private class ActionButtonRenderer extends JPanel implements TableCellRenderer {
        public ActionButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 3));
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            removeAll();
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            
            JButton btnEdit = new JButton("✏️");
            JButton btnDelete = new JButton("🗑️");
            
            styleActionButton(btnEdit, PRIMARY_COLOR);
            styleActionButton(btnDelete, DANGER_COLOR);
            
            add(btnEdit);
            add(btnDelete);
            
            return this;
        }
        
        private void styleActionButton(JButton btn, Color color) {
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(35, 28));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
    }

    private class ActionButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JButton btnEdit;
        private JButton btnDelete;
        private int currentRow;

        public ActionButtonEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 3));
            
            btnEdit = new JButton("✏️");
            btnDelete = new JButton("🗑️");
            
            styleActionButton(btnEdit, PRIMARY_COLOR);
            styleActionButton(btnDelete, DANGER_COLOR);
            
            btnEdit.addActionListener(e -> {
                editReceiveEntry(currentRow);
                fireEditingStopped();
            });
            
            btnDelete.addActionListener(e -> {
                deleteReceiveEntry(currentRow);
                fireEditingStopped();
            });
            
            panel.add(btnEdit);
            panel.add(btnDelete);
        }

        private void styleActionButton(JButton btn, Color color) {
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(35, 28));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            currentRow = row;
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private void loadVendorsIntoCombo() {
        vendorCombo.removeAllItems();
        String currentClientId = Database.getCurrentClientId();
        try (java.sql.Connection conn = Database.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT name FROM vendors WHERE client_id = ? ORDER BY name")) {
            ps.setString(1, currentClientId);
            java.sql.ResultSet rs = ps.executeQuery();

            boolean hasVendors = false;
            while (rs.next()) {
                vendorCombo.addItem(rs.getString("name"));
                hasVendors = true;
            }
            if (!hasVendors) {
                vendorCombo.addItem("(No vendors available)");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading vendors", e);
            JOptionPane.showMessageDialog(this, "Error loading vendors: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void performSearch() {
        searchTableModel.setRowCount(0);
        
        String searchText = searchField.getText().trim().toLowerCase();

        try {
            String currentClientId = Database.getCurrentClientId();
            List<Database.Product> products = Database.getAllProducts();
            List<Database.Category> categories = Database.getAllCategoryObjects();

            products.removeIf(p -> {
                try {
                    return p.getClientId() != null && !p.getClientId().equals(currentClientId);
                } catch (Exception e) {
                    return false;
                }
            });

            for (Database.Product product : products) {
                if (!searchText.isEmpty() && 
                    !product.getName().toLowerCase().contains(searchText) &&
                    (product.getVendor() == null || !product.getVendor().toLowerCase().contains(searchText))) {
                    continue;
                }

                String categoryName = "Unknown";
                for (Database.Category category : categories) {
                    if (category.getId() == product.getCategoryId()) {
                        categoryName = category.getName();
                        break;
                    }
                }

                searchTableModel.addRow(new Object[]{
                    product.getId(),
                    product.getName(),
                    categoryName,
                    product.getVendor() != null ? product.getVendor() : "N/A",
                    String.format("%,d", product.getCostPrice().intValue()),
                    String.format("%,d", product.getSellingPrice().intValue()),
                    product.getStockQuantity()
                });
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error searching products", e);
            JOptionPane.showMessageDialog(this, "Error searching products: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openReceiveDialog() {
        int selectedRow = searchTable.getSelectedRow();
        if (selectedRow < 0) return;

        try {
            int productId = Integer.parseInt(searchTableModel.getValueAt(selectedRow, 0).toString());
            String name = searchTableModel.getValueAt(selectedRow, 1).toString();
            String category = searchTableModel.getValueAt(selectedRow, 2).toString();
            String costStr = searchTableModel.getValueAt(selectedRow, 4).toString().replace(",", "");
            String sellStr = searchTableModel.getValueAt(selectedRow, 5).toString().replace(",", "");
            BigDecimal costPrice = new BigDecimal(costStr);
            BigDecimal sellingPrice = new BigDecimal(sellStr);
            int oldStock = Integer.parseInt(searchTableModel.getValueAt(selectedRow, 6).toString());

            JDialog dialog = new JDialog(this, "Receive Product - " + name, true);
            dialog.setResizable(true);
            
            int dialogWidth = Math.min(550, getWidth() - 100);
            int dialogHeight = Math.min(550, getHeight() - 100);
            dialog.setSize(dialogWidth, dialogHeight);
            dialog.setMinimumSize(new Dimension(450, 450));
            dialog.setLocationRelativeTo(this);
            
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBackground(BACKGROUND);
            int padding = dialogWidth < 500 ? 10 : 20;
            mainPanel.setBorder(new EmptyBorder(padding, padding, padding, padding));

            JPanel infoCard = new JPanel(new GridLayout(2, 2, 8, 8));
            infoCard.setBackground(CARD_BACKGROUND);
            infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 12, 12, 12)
            ));
            
            addFormRow(infoCard, "Product:", name);
            addFormRow(infoCard, "Category:", category);
            addFormRow(infoCard, "Current Stock:", String.valueOf(oldStock) + " units");
            addFormRow(infoCard, "Cost Price:", String.format("%,d", costPrice.intValue()));

            JPanel inputCard = new JPanel(new GridLayout(7, 2, 8, 12));
            inputCard.setBackground(CARD_BACKGROUND);
            inputCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 12, 12, 12)
            ));

            JSpinner spCost = new JSpinner(new SpinnerNumberModel(costPrice.doubleValue(), 0.0, 10000000.0, 100.0));
            JSpinner spSell = new JSpinner(new SpinnerNumberModel(sellingPrice.doubleValue(), 0.0, 10000000.0, 100.0));
            JSpinner spQty = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
            
            JDateChooser dateChooser = new JDateChooser();
            dateChooser.setDateFormatString("yyyy-MM-dd");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.MONTH, 6);
            dateChooser.setDate(cal.getTime());
            dateChooser.setPreferredSize(new Dimension(150, 28));
            
            JTextField txtBatch = new JTextField("BATCH-" + System.currentTimeMillis());
            JTextField txtInvoice = new JTextField("INV-" + System.currentTimeMillis());

            inputCard.add(createLabel("Cost Price:"));
            inputCard.add(spCost);
            inputCard.add(createLabel("Selling Price:"));
            inputCard.add(spSell);
            inputCard.add(createLabel("Quantity:"));
            inputCard.add(spQty);
            inputCard.add(createLabel("Expiry Date:"));
            inputCard.add(dateChooser);
            inputCard.add(createLabel("Batch Number:"));
            inputCard.add(txtBatch);
            inputCard.add(createLabel("Invoice Number:"));
            inputCard.add(txtInvoice);
            
            JLabel totalLabel = new JLabel("Total Cost: 0", SwingConstants.RIGHT);
            totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            totalLabel.setForeground(PRIMARY_COLOR);
            
            inputCard.add(new JLabel());
            inputCard.add(totalLabel);

            spQty.addChangeListener(e -> {
                int qty = ((Number) spQty.getValue()).intValue();
                BigDecimal cost = new BigDecimal(spCost.getValue().toString());
                BigDecimal total = cost.multiply(new BigDecimal(qty));
                totalLabel.setText(String.format("Total Cost: %,d", total.intValue()));
            });
            spQty.setValue(1);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            buttonPanel.setOpaque(false);

            int buttonWidth = dialogWidth < 500 ? 100 : 120;
            JButton btnCancel = createButton("Cancel", TEXT_SECONDARY);
            btnCancel.setPreferredSize(new Dimension(buttonWidth, 38));
            
            JButton btnConfirm = createButton("✓ Confirm", SUCCESS_COLOR);
            btnConfirm.setPreferredSize(new Dimension(buttonWidth + 40, 38));

            btnCancel.addActionListener(e -> dialog.dispose());
            btnConfirm.addActionListener(e -> {
                try {
                    java.util.Date selectedDate = dateChooser.getDate();
                    if (selectedDate == null) {
                        JOptionPane.showMessageDialog(dialog, "Please select an expiry date", 
                            "Invalid Date", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    
                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    calendar.setTime(selectedDate);
                    LocalDate expiryDate = LocalDate.of(
                        calendar.get(java.util.Calendar.YEAR),
                        calendar.get(java.util.Calendar.MONTH) + 1,
                        calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    );
                    
                    int qty = ((Number) spQty.getValue()).intValue();
                    BigDecimal newCost = new BigDecimal(spCost.getValue().toString());
                    BigDecimal newSell = new BigDecimal(spSell.getValue().toString());
                    int newStock = oldStock + qty;

                    ReceiveEntry entry = new ReceiveEntry(
                        productId, name, category, newCost, newSell, qty, oldStock, newStock,
                        expiryDate, txtBatch.getText(), txtInvoice.getText()
                    );

                    receivedProducts.add(entry);
                    addReceiveEntryToTable(entry);
                    updateTotals();
                    dialog.dispose();
                    
                    JOptionPane.showMessageDialog(this, "Product added to receive list!", 
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            buttonPanel.add(btnCancel);
            buttonPanel.add(btnConfirm);

            mainPanel.add(infoCard, BorderLayout.NORTH);
            mainPanel.add(inputCard, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setContentPane(mainPanel);
            dialog.setVisible(true);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error opening receive dialog", e);
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addFormRow(JPanel panel, String label, String value) {
        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblLabel.setForeground(TEXT_SECONDARY);
        
        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblValue.setForeground(TEXT_PRIMARY);
        
        panel.add(lblLabel);
        panel.add(lblValue);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    private void addReceiveEntryToTable(ReceiveEntry entry) {
        receiveTableModel.addRow(new Object[]{
            entry.productName,
            entry.category,
            String.format("%,d", entry.costPrice.intValue()),
            String.format("%,d", entry.sellingPrice.intValue()),
            entry.receivedQuantity,
            entry.oldStock,
            entry.newStock,
            entry.expiryDate.toString(),
            entry.batchNumber,
            entry.invoiceNumber,
            ""
        });
        autoSaveReceiveList();
    }

    private void editReceiveEntry(int index) {
        if (index < 0 || index >= receivedProducts.size()) return;
        
        ReceiveEntry entry = receivedProducts.get(index);
        
        JDialog dialog = new JDialog(this, "Edit Receive Entry - " + entry.productName, true);
        dialog.setResizable(true);
        
        int dialogWidth = Math.min(550, getWidth() - 100);
        int dialogHeight = Math.min(550, getHeight() - 100);
        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setMinimumSize(new Dimension(450, 450));
        dialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(BACKGROUND);
        int padding = dialogWidth < 500 ? 10 : 20;
        mainPanel.setBorder(new EmptyBorder(padding, padding, padding, padding));

        JPanel inputCard = new JPanel(new GridLayout(7, 2, 8, 12));
        inputCard.setBackground(CARD_BACKGROUND);
        inputCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            new EmptyBorder(12, 12, 12, 12)
        ));

        JSpinner spCost = new JSpinner(new SpinnerNumberModel(entry.costPrice.doubleValue(), 0.0, 10000000.0, 100.0));
        JSpinner spSell = new JSpinner(new SpinnerNumberModel(entry.sellingPrice.doubleValue(), 0.0, 10000000.0, 100.0));
        JSpinner spQty = new JSpinner(new SpinnerNumberModel(entry.receivedQuantity, 1, 100000, 1));
        
        JDateChooser dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("yyyy-MM-dd");
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(entry.expiryDate.getYear(), entry.expiryDate.getMonthValue() - 1, entry.expiryDate.getDayOfMonth());
        dateChooser.setDate(cal.getTime());
        dateChooser.setPreferredSize(new Dimension(150, 28));
        
        JTextField txtBatch = new JTextField(entry.batchNumber);
        JTextField txtInvoice = new JTextField(entry.invoiceNumber != null ? entry.invoiceNumber : "INV-" + System.currentTimeMillis());

        inputCard.add(createLabel("Cost Price:"));
        inputCard.add(spCost);
        inputCard.add(createLabel("Selling Price:"));
        inputCard.add(spSell);
        inputCard.add(createLabel("Quantity:"));
        inputCard.add(spQty);
        inputCard.add(createLabel("Expiry Date:"));
        inputCard.add(dateChooser);
        inputCard.add(createLabel("Batch Number:"));
        inputCard.add(txtBatch);
        inputCard.add(createLabel("Invoice Number:"));
        inputCard.add(txtInvoice);

        JLabel totalLabel = new JLabel("", SwingConstants.RIGHT);
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        totalLabel.setForeground(PRIMARY_COLOR);
        inputCard.add(new JLabel());
        inputCard.add(totalLabel);

        spQty.addChangeListener(e -> {
            int qty = ((Number) spQty.getValue()).intValue();
            BigDecimal cost = new BigDecimal(spCost.getValue().toString());
            BigDecimal total = cost.multiply(new BigDecimal(qty));
            totalLabel.setText(String.format("Total Cost: %,d", total.intValue()));
        });
        spQty.setValue(entry.receivedQuantity);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);

        int buttonWidth = dialogWidth < 500 ? 100 : 120;
        JButton btnCancel = createButton("Cancel", TEXT_SECONDARY);
        btnCancel.setPreferredSize(new Dimension(buttonWidth, 38));
        
        JButton btnUpdate = createButton("✓ Update", SUCCESS_COLOR);
        btnUpdate.setPreferredSize(new Dimension(buttonWidth + 40, 38));

        btnCancel.addActionListener(e -> dialog.dispose());
        btnUpdate.addActionListener(e -> {
            try {
                java.util.Date selectedDate = dateChooser.getDate();
                if (selectedDate == null) {
                    JOptionPane.showMessageDialog(dialog, "Please select an expiry date", 
                        "Invalid Date", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.setTime(selectedDate);
                LocalDate expiryDate = LocalDate.of(
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH) + 1,
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                );
                
                int qty = ((Number) spQty.getValue()).intValue();
                BigDecimal newCost = new BigDecimal(spCost.getValue().toString());
                BigDecimal newSell = new BigDecimal(spSell.getValue().toString());
                int newStock = entry.oldStock + qty;

                entry.costPrice = newCost;
                entry.sellingPrice = newSell;
                entry.receivedQuantity = qty;
                entry.newStock = newStock;
                entry.expiryDate = expiryDate;
                entry.batchNumber = txtBatch.getText();
                entry.invoiceNumber = txtInvoice.getText();

                updateReceiveTableRow(index, entry);
                updateTotals();
                dialog.dispose();
                
                JOptionPane.showMessageDialog(this, "Entry updated successfully!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnUpdate);

        mainPanel.add(inputCard, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }

    private void updateReceiveTableRow(int index, ReceiveEntry entry) {
        receiveTableModel.setValueAt(String.format("%,d", entry.costPrice.intValue()), index, 2);
        receiveTableModel.setValueAt(String.format("%,d", entry.sellingPrice.intValue()), index, 3);
        receiveTableModel.setValueAt(entry.receivedQuantity, index, 4);
        receiveTableModel.setValueAt(entry.newStock, index, 6);
        receiveTableModel.setValueAt(entry.expiryDate.toString(), index, 7);
        receiveTableModel.setValueAt(entry.batchNumber, index, 8);
        receiveTableModel.setValueAt(entry.invoiceNumber, index, 9);
        autoSaveReceiveList();
    }

    private void deleteReceiveEntry(int index) {
        if (index < 0 || index >= receivedProducts.size()) return;
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to remove this entry?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            receivedProducts.remove(index);
            receiveTableModel.removeRow(index);
            updateTotals();
            autoSaveReceiveList();
            JOptionPane.showMessageDialog(this, "Entry removed successfully!", 
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateTotals() {
        double totalBuyingCost = 0;
        double totalSellingValue = 0;
        int totalQuantity = 0;

        for (ReceiveEntry entry : receivedProducts) {
            totalBuyingCost += entry.costPrice.doubleValue() * entry.receivedQuantity;
            totalSellingValue += entry.sellingPrice.doubleValue() * entry.receivedQuantity;
            totalQuantity += entry.receivedQuantity;
        }

        double totalProfit = totalSellingValue - totalBuyingCost;

        if (entriesLabel != null) entriesLabel.setText(String.valueOf(receivedProducts.size()));
        if (costOfGoodsLabel != null) costOfGoodsLabel.setText(String.format("%,.2f", totalBuyingCost));
        if (quantityEntriesLabel != null) quantityEntriesLabel.setText(String.valueOf(totalQuantity));
        if (profitLabel != null) profitLabel.setText(String.format("%,.2f", totalProfit));
        
        SwingUtilities.invokeLater(() -> {
            if (getContentPane() != null) {
                getContentPane().revalidate();
                getContentPane().repaint();
            }
        });
    }

    private void saveProducts() {
        if (receivedProducts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No products to save!", 
                "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String vendorName = (String) vendorCombo.getSelectedItem();
        if (vendorName == null || vendorName.isEmpty() || vendorName.contains("No vendors")) {
            JOptionPane.showMessageDialog(this, "Please select a vendor.",
                "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = showConfirmDialog();
        if (confirm != JOptionPane.YES_OPTION) return;

        JDialog progressDialog = showProgressDialog("Saving products...");
        boolean wasOnline = Database.isOnline();

        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    for (int i = 0; i < receivedProducts.size(); i++) {
                        ReceiveEntry entry = receivedProducts.get(i);
                        
                        Database.Product product = Database.getProductById(entry.productId);
                        if (product == null) {
                            throw new SQLException("Product ID " + entry.productId + " not found");
                        }

                        int categoryId = product.getCategoryId();
                        if (!Database.categoryExists(categoryId)) {
                            String categoryName = entry.category;
                            if (categoryName == null || categoryName.equals("Unknown")) {
                                categoryName = "GENERAL";
                            }
                            int newCategoryId = Database.autoCreateCategory(categoryName);
                            if (newCategoryId > 0) {
                                categoryId = newCategoryId;
                                LOGGER.info("Auto-created missing category: " + categoryName + " (ID=" + newCategoryId + ")");
                            } else {
                                LOGGER.warning("Could not auto-create category for product: " + entry.productName);
                            }
                        }

                        Database.updateProduct(
                            entry.productId, 
                            product.getName(), 
                            categoryId,
                            vendorName,
                            entry.costPrice, 
                            entry.sellingPrice, 
                            entry.newStock, 
                            product.getMinStockLevel(), 
                            entry.expiryDate
                        );

                        Database.insertReceivedProductFlow(
                            entry.productId, 
                            entry.productName, 
                            entry.receivedQuantity, 
                            entry.oldStock, 
                            entry.newStock, 
                            vendorName, 
                            currentUser
                        );
                        
                        Database.logInventoryAction(
                            entry.productId, 
                            "RECEIVED", 
                            entry.receivedQuantity, 
                            entry.oldStock, 
                            currentUser
                        );
                        
                        publish((i + 1) * 100 / receivedProducts.size());
                        Thread.sleep(50);
                    }
                } catch (Exception e) {
                    throw e;
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get();
                    
                    if (wasOnline) {
                        try {
                            LOGGER.info("Syncing received products to cloud...");
                            SyncEngine.fullSync();
                            LOGGER.info("✅ Products synced to cloud successfully!");
                            
                            JOptionPane.showMessageDialog(Receiveproduct.this, 
                                String.format("✅ Successfully received %d product entries!\n\n📦 Synced to Cloud\n🌐 Online backup complete", 
                                    receivedProducts.size()),
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception syncEx) {
                            LOGGER.log(Level.WARNING, "Products saved locally but cloud sync failed: " + syncEx.getMessage());
                            JOptionPane.showMessageDialog(Receiveproduct.this, 
                                String.format("⚠️ %d products saved locally but cloud sync failed.\n\nError: %s\n\nWill retry automatically when online.", 
                                    receivedProducts.size(), syncEx.getMessage()),
                                "Partial Success", JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(Receiveproduct.this, 
                            String.format("✅ %d products saved locally.\n\n📱 Offline Mode\n🔄 Will sync to cloud when connection is restored.", 
                                receivedProducts.size()),
                            "Success (Offline Mode)", JOptionPane.INFORMATION_MESSAGE);
                    }
                    
                    clearAllReceiveEntries(true);
                    performSearch();
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error saving products", e);
                    JOptionPane.showMessageDialog(Receiveproduct.this, 
                        "Error saving products: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void saveReceiveListToLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/ReceiveList");
            
            StringBuilder listBuilder = new StringBuilder();
            for (int i = 0; i < receivedProducts.size(); i++) {
                ReceiveEntry entry = receivedProducts.get(i);
                
                if (i > 0) listBuilder.append("###");
                listBuilder.append(entry.productId).append("|");
                listBuilder.append(entry.productName).append("|");
                listBuilder.append(entry.category).append("|");
                listBuilder.append(entry.costPrice.toString()).append("|");
                listBuilder.append(entry.sellingPrice.toString()).append("|");
                listBuilder.append(entry.receivedQuantity).append("|");
                listBuilder.append(entry.oldStock).append("|");
                listBuilder.append(entry.newStock).append("|");
                listBuilder.append(entry.expiryDate.toString()).append("|");
                listBuilder.append(entry.batchNumber).append("|");
                listBuilder.append(entry.invoiceNumber);
            }
            
            prefs.put("receiveList_" + currentUser, listBuilder.toString());
            prefs.putLong("lastSaved_" + currentUser, System.currentTimeMillis());
            
            LOGGER.info("Receive list saved locally for user: " + currentUser + " - " + receivedProducts.size() + " items");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save receive list locally", e);
        }
    }

    private void loadReceiveListFromLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/ReceiveList");
            
            String savedList = prefs.get("receiveList_" + currentUser, "");
            
            if (savedList.isEmpty()) {
                LOGGER.info("No saved receive list found for user: " + currentUser);
                return;
            }
            
            String[] items = savedList.split("###");
            
            for (String item : items) {
                String[] parts = item.split("\\|");
                if (parts.length == 11) {
                    int productId = Integer.parseInt(parts[0]);
                    String productName = parts[1];
                    String category = parts[2];
                    BigDecimal costPrice = new BigDecimal(parts[3]);
                    BigDecimal sellingPrice = new BigDecimal(parts[4]);
                    int receivedQuantity = Integer.parseInt(parts[5]);
                    int oldStock = Integer.parseInt(parts[6]);
                    int newStock = Integer.parseInt(parts[7]);
                    LocalDate expiryDate = LocalDate.parse(parts[8]);
                    String batchNumber = parts[9];
                    String invoiceNumber = parts[10];
                    
                    boolean productExists = false;
                    try {
                        Database.Product product = Database.getProductById(productId);
                        if (product != null) productExists = true;
                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING, "Product ID " + productId + " may no longer exist", e);
                    }
                    
                    if (productExists) {
                        ReceiveEntry entry = new ReceiveEntry(
                            productId, productName, category, costPrice, sellingPrice,
                            receivedQuantity, oldStock, newStock, expiryDate, batchNumber, invoiceNumber
                        );
                        receivedProducts.add(entry);
                        addReceiveEntryToTable(entry);
                    } else {
                        LOGGER.warning("Skipped loading product ID " + productId + " - no longer exists in database");
                    }
                }
            }
            
            if (receivedProducts.size() > 0) {
                updateTotals();
                LOGGER.info("Receive list loaded locally for user: " + currentUser + " - " + receivedProducts.size() + " items");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load receive list locally", e);
        }
    }

    private void autoSaveReceiveList() {
        SwingUtilities.invokeLater(() -> saveReceiveListToLocalStorage());
    }

    private void clearReceiveListFromLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/ReceiveList");
            prefs.remove("receiveList_" + currentUser);
            LOGGER.info("Receive list cleared locally for user: " + currentUser);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clear receive list locally", e);
        }
    }

    private int showConfirmDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JLabel message = new JLabel("Confirm receiving the following products:");
        message.setFont(new Font("Segoe UI", Font.BOLD, 14));
        message.setForeground(TEXT_PRIMARY);
        
        JTextArea summary = new JTextArea(8, 40);
        summary.setEditable(false);
        summary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        summary.setBackground(CARD_BACKGROUND);
        summary.setForeground(TEXT_PRIMARY);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-30s %10s %15s\n", "Product", "Quantity", "Total Cost"));
        sb.append("-".repeat(60)).append("\n");
        
        for (ReceiveEntry entry : receivedProducts) {
            BigDecimal total = entry.costPrice.multiply(new BigDecimal(entry.receivedQuantity));
            sb.append(String.format("%-30s %10d %,15d\n", 
                entry.productName.length() > 28 ? entry.productName.substring(0, 28) + ".." : entry.productName,
                entry.receivedQuantity, 
                total.intValue()));
        }
        
        summary.setText(sb.toString());
        JScrollPane scrollPane = new JScrollPane(summary);
        
        panel.add(message, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return JOptionPane.showConfirmDialog(this, panel, "Confirm Receive", 
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
    }

    private JDialog showProgressDialog(String message) {
        JDialog dialog = new JDialog(this, "Processing", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setSize(350, 120);
        dialog.setLocationRelativeTo(this);
        dialog.setUndecorated(true);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY_COLOR, 2),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setForeground(TEXT_PRIMARY);
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(300, 25));
        
        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        
        dialog.setContentPane(panel);
        
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        
        return dialog;
    }

    private void clearAllReceiveEntries() {
        clearAllReceiveEntries(false);
    }

    private void clearAllReceiveEntries(boolean skipConfirmation) {
        if (!receivedProducts.isEmpty()) {
            if (skipConfirmation) {
                receivedProducts.clear();
                receiveTableModel.setRowCount(0);
                updateTotals();
                clearReceiveListFromLocalStorage();
            } else {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Clear all receive entries?",
                    "Confirm Clear",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                    
                if (confirm == JOptionPane.YES_OPTION) {
                    receivedProducts.clear();
                    receiveTableModel.setRowCount(0);
                    updateTotals();
                    clearReceiveListFromLocalStorage();
                }
            }
        }
    }

    private void showReceiveHistory() {
        JDialog historyDialog = new JDialog(this, "Receive History", true);
        historyDialog.setResizable(true);
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.75);
        int height = (int) (screenSize.height * 0.75);
        historyDialog.setSize(width, height);
        historyDialog.setMinimumSize(new Dimension(700, 500));
        historyDialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("📦 Receive History", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        datePanel.setOpaque(false);
        datePanel.add(new JLabel("Date:") {{
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
        }});
        
        JDateChooser historyDateChooser = new JDateChooser();
        historyDateChooser.setDateFormatString("yyyy-MM-dd");
        historyDateChooser.setDate(new java.util.Date());
        historyDateChooser.setPreferredSize(new Dimension(150, 30));
        datePanel.add(historyDateChooser);
        
        JButton loadBtn = createButton("Load", Color.WHITE);
        loadBtn.setForeground(PRIMARY_COLOR);
        loadBtn.setPreferredSize(new Dimension(80, 30));
        datePanel.add(loadBtn);
        
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(datePanel, BorderLayout.EAST);
        
        String[] columns = {"Product", "Category", "Cost Price", "Selling Price", "Quantity", "Date"};
        DefaultTableModel historyModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable historyTable = createModernTable(historyModel);
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        
        JPanel summaryPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        summaryPanel.setBackground(CARD_BACKGROUND);
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            new EmptyBorder(15, 20, 15, 20)
        ));
        
        JLabel productEntriesLabel = new JLabel("📦 Product Entries: 0");
        productEntriesLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        productEntriesLabel.setForeground(PRIMARY_COLOR);
        
        JLabel totalCostLabel = new JLabel("💰 Total Buying Cost: 0");
        totalCostLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        totalCostLabel.setForeground(TEXT_SECONDARY);
        
        JLabel quantityLabel = new JLabel("🔢 Quantity Entries: 0");
        quantityLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        quantityLabel.setForeground(INFO_COLOR);
        
        JLabel profitLabelHistory = new JLabel("📈 Total Profit: 0");
        profitLabelHistory.setFont(new Font("Segoe UI", Font.BOLD, 13));
        profitLabelHistory.setForeground(SUCCESS_COLOR);
        
        summaryPanel.add(productEntriesLabel);
        summaryPanel.add(totalCostLabel);
        summaryPanel.add(quantityLabel);
        summaryPanel.add(profitLabelHistory);
        
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(CARD_BACKGROUND);
        bottomPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
        
        JButton closeBtn = createButton("Close", TEXT_SECONDARY);
        closeBtn.setPreferredSize(new Dimension(120, 40));
        closeBtn.addActionListener(e -> historyDialog.dispose());
        bottomPanel.add(closeBtn);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(summaryPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.PAGE_END);
        
        historyDialog.setContentPane(mainPanel);
        
        loadBtn.addActionListener(e -> {
            historyModel.setRowCount(0);
            try {
                java.util.Date selectedDate = historyDateChooser.getDate();
                if (selectedDate == null) {
                    JOptionPane.showMessageDialog(historyDialog, "Please select a date", 
                        "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                String dateStr = sdf.format(selectedDate);
                
                String sql = "SELECT p.name, c.name as category, p.cost_price, p.selling_price, if.quantity, DATE(if.flow_date) as receive_date " +
                            "FROM item_flows if " +
                            "JOIN products p ON if.product_id = p.id " +
                            "LEFT JOIN categories c ON p.category_id = c.id " +
                            "WHERE if.action = 'RECEIVED' AND DATE(if.flow_date) = ? AND if.client_id = ?";
                
                double totalCost = 0;
                int totalQuantity = 0;
                double totalSell = 0;
                int productCount = 0;
                
                try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
                    stmt.setString(1, dateStr);
                    stmt.setString(2, Database.getCurrentClientId());
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        int qty = rs.getInt("quantity");
                        double costPrice = rs.getDouble("cost_price");
                        double sellPrice = rs.getDouble("selling_price");
                        
                        historyModel.addRow(new Object[]{
                            rs.getString("name"),
                            rs.getString("category") != null ? rs.getString("category") : "Uncategorized",
                            String.format("%,.2f", costPrice),
                            String.format("%,.2f", sellPrice),
                            qty,
                            rs.getString("receive_date")
                        });
                        
                        productCount++;
                        totalCost += costPrice * qty;
                        totalQuantity += qty;
                        totalSell += sellPrice * qty;
                    }
                }
                
                double totalProfit = totalSell - totalCost;
                
                productEntriesLabel.setText("📦 Product Entries: " + productCount);
                totalCostLabel.setText(String.format("💰 Total Buying Cost: %,.2f", totalCost));
                quantityLabel.setText("🔢 Quantity Entries: " + totalQuantity);
                profitLabelHistory.setText(String.format("📈 Total Profit: %,.2f", totalProfit));
                
                if (productCount == 0) {
                    JOptionPane.showMessageDialog(historyDialog, 
                        "No receive records found for " + dateStr,
                        "No Data", JOptionPane.INFORMATION_MESSAGE);
                }
                
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error loading history", ex);
                JOptionPane.showMessageDialog(historyDialog, 
                    "Error loading history: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        loadBtn.doClick();
        historyDialog.setVisible(true);
    }

    private void openVendorManagementDialog() {
        try {
            Productcategories.VendorDialog vendorDialog = new Productcategories.VendorDialog(this);
            vendorDialog.setVisible(true);
            loadVendorsIntoCombo();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error opening vendor dialog", e);
            JOptionPane.showMessageDialog(this, "Could not open vendor management dialog",
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
