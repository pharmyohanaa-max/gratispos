package hIMs.App;

import javax.swing.table.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.event.DocumentListener;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.util.prefs.Preferences;

/**
 * Advanced Inventory Management & Purchase Order System
 * Features:
 * - Smart Budget Allocation
 * - Vendor/Supplier Management
 * - Priority-Based Ordering
 * - Order History & Tracking
 * - Reorder Point Calculations
 * - Lead Time Management
 * - Multi-Currency Support
 */
public class Outstock extends JDialog {
    private JPanel contentPane;
    private JTable tableOutOfStock;
    private JTable tableBelowMin;
    private JTable tableFastMoving;
    private JTable tableSlowMoving;
    private JButton floatButton;
    private boolean isMinimized = false;
    private JPanel fullContentPanel;
    private JPanel minimizedContentPanel;
    
    // Purchase cart data
    private List<CartItem> purchaseCart = new ArrayList<>();
    private String currentUser;
    private Map<String, ProductInfo> productInfoMap = new HashMap<>();
    
    // Claymorphism Dark Theme Colors (FlatLaf compatible)
    private static final Color CLAY_BG = new Color(28, 30, 36);
    private static final Color CLAY_SURFACE = new Color(38, 40, 46);
    private static final Color CLAY_ELEVATED = new Color(48, 50, 56);
    private static final Color CLAY_BORDER = new Color(58, 60, 66);
    private static final Color CLAY_PRIMARY = new Color(0, 161, 255);
    private static final Color CLAY_SUCCESS = new Color(0, 200, 117);
    private static final Color CLAY_WARNING = new Color(255, 184, 0);
    private static final Color CLAY_DANGER = new Color(255, 69, 58);
    private static final Color CLAY_TEXT = new Color(235, 236, 240);
    private static final Color CLAY_TEXT_SECONDARY = new Color(158, 161, 170);
    private static final Color CLAY_SHADOW = new Color(0, 0, 0, 80);

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                // Use FlatLaf Dark Theme for claymorphism effect
                UIManager.setLookAndFeel(new FlatDarkLaf());
                
                // Custom claymorphism overrides
                UIManager.put("Button.arc", 15);
                UIManager.put("Component.arc", 12);
                UIManager.put("TextComponent.arc", 10);
                UIManager.put("Panel.background", CLAY_BG);
                UIManager.put("Table.background", CLAY_SURFACE);
                UIManager.put("Table.foreground", CLAY_TEXT);
                UIManager.put("Table.selectionBackground", new Color(0, 161, 255, 60));
                UIManager.put("TableHeader.background", CLAY_ELEVATED);
                UIManager.put("TableHeader.foreground", CLAY_TEXT);
                UIManager.put("TextField.background", CLAY_SURFACE);
                UIManager.put("TextField.foreground", CLAY_TEXT);
                UIManager.put("ComboBox.background", CLAY_SURFACE);
                UIManager.put("ComboBox.foreground", CLAY_TEXT);
                UIManager.put("Label.foreground", CLAY_TEXT);
                
                JFrame parent = new JFrame("Test Parent");
                parent.setSize(200, 200);
                parent.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                parent.setVisible(true);
                Outstock dialog = new Outstock(parent, "TestUser");
                dialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Outstock(JFrame parent, String username) {
        super(parent, "Advanced Inventory Management & Purchase Orders", false);
        this.currentUser = username;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        // Make dialog resizable
        setResizable(true);
        
        // Dynamic screen size detection (92% of screen, but max 1600x900)
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int targetWidth = (int) (screenSize.width * 0.92);
        int targetHeight = (int) (screenSize.height * 0.88);
        
        // Cap at reasonable max sizes
        targetWidth = Math.min(targetWidth, 1600);
        targetHeight = Math.min(targetHeight, 900);
        
        // Ensure minimum size
        targetWidth = Math.max(targetWidth, 1000);
        targetHeight = Math.max(targetHeight, 700);
        
        setSize(targetWidth, targetHeight);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(parent);

        contentPane = new ClaymorphismPanel();
        contentPane.setBorder(new EmptyBorder(20, 20, 20, 20));

        initializeComponents();
        loadProductInfo();
        loadAllStockData();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(fullContentPanel, BorderLayout.CENTER);
        
        // Add component listener for responsive adjustments
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustFontSizes();
            }
        });
    }
    
    private void adjustFontSizes() {
        int width = getWidth();
        int baseFontSize = width < 1000 ? 11 : 13;
        
        Component[] components = getContentPane().getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                adjustPanelFonts((JPanel) comp, baseFontSize);
            }
        }
    }
    
    private void adjustPanelFonts(JPanel panel, int fontSize) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JLabel) {
                Font f = ((JLabel) comp).getFont();
                ((JLabel) comp).setFont(f.deriveFont((float) fontSize));
            } else if (comp instanceof JButton) {
                Font f = ((JButton) comp).getFont();
                ((JButton) comp).setFont(f.deriveFont((float) fontSize));
            } else if (comp instanceof JPanel) {
                adjustPanelFonts((JPanel) comp, fontSize);
            }
        }
    }

    private void initializeComponents() {
        fullContentPanel = new JPanel(new BorderLayout(10, 10));
        fullContentPanel.setBackground(CLAY_BG);
        fullContentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel with action buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        topPanel.setBackground(CLAY_BG);
        
        // Order History
        JButton btnHistory = createClayButton("📜 Order History", CLAY_PRIMARY);
        btnHistory.addActionListener(e -> showOrderHistory());
        topPanel.add(btnHistory);

        // View Cart Button
        JButton btnViewOrder = createClayButton("🛒 View Cart (" + purchaseCart.size() + ")", CLAY_SUCCESS);
        btnViewOrder.addActionListener(e -> {
            showViewOrderDialog();
            btnViewOrder.setText("🛒 View Cart (" + purchaseCart.size() + ")");
        });
        topPanel.add(btnViewOrder);

        // Smart Auto Order Button
        JButton btnAutoOrder = createClayButton("🤖 Smart Auto Order", CLAY_PRIMARY);
        btnAutoOrder.addActionListener(e -> showSmartAutoOrderDialog());
        topPanel.add(btnAutoOrder);

        // Bulk Order Button
        JButton btnBulkOrder = createClayButton("📦 Bulk Order", CLAY_WARNING);
        btnBulkOrder.addActionListener(e -> showBulkOrderDialog());
        topPanel.add(btnBulkOrder);
        
        floatButton = createClayButton("Float", CLAY_ELEVATED);
        floatButton.addActionListener(e -> toggleFloatMode());
        topPanel.add(floatButton);
        
        fullContentPanel.add(topPanel, BorderLayout.NORTH);

        // Main content with tabs
        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        mainPanel.setBackground(CLAY_BG);

        tableOutOfStock = createClayTable();
        tableBelowMin = createClayTable();
        tableFastMoving = createClayTable();
        tableSlowMoving = createClayTable();
        
        // Add double-click listeners
        addTableDoubleClickListener(tableOutOfStock);
        addTableDoubleClickListener(tableBelowMin);
        addTableDoubleClickListener(tableFastMoving);
        addTableDoubleClickListener(tableSlowMoving);

        mainPanel.add(createClayTablePanel(tableOutOfStock, "🚫 Out of Stock (Critical)", CLAY_DANGER));
        mainPanel.add(createClayTablePanel(tableBelowMin, "⚠️ Below Minimum Level", CLAY_WARNING));
        mainPanel.add(createClayTablePanel(tableFastMoving, "🔥 Fast Moving Products", CLAY_SUCCESS));
        mainPanel.add(createClayTablePanel(tableSlowMoving, "🐌 Slow Moving Products", new Color(108, 117, 125)));

        fullContentPanel.add(mainPanel, BorderLayout.CENTER);

        // Bottom panel with summary
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomPanel.setBackground(CLAY_BG);
        
        JLabel lblInfo = new JLabel("💡 Tip: Double-click any product to add to cart");
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblInfo.setForeground(CLAY_TEXT_SECONDARY);
        bottomPanel.add(lblInfo);
        
        JButton btnBack = createClayButton("Back to Dashboard", CLAY_ELEVATED);
        btnBack.addActionListener((ActionEvent e) -> dispose());
        bottomPanel.add(btnBack);
        
        fullContentPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Minimized panel
        minimizedContentPanel = new JPanel(new BorderLayout());
        minimizedContentPanel.setBackground(CLAY_BG);
        minimizedContentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel minimizedLabel = new JLabel("Inventory Management (Minimized)");
        minimizedLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        minimizedLabel.setForeground(CLAY_TEXT);
        minimizedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        minimizedContentPanel.add(minimizedLabel, BorderLayout.CENTER);
        JPanel minimizedButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        minimizedButtonPanel.setBackground(CLAY_BG);
        minimizedButtonPanel.add(floatButton);
        minimizedContentPanel.add(minimizedButtonPanel, BorderLayout.SOUTH);
    }
    
    private JButton createClayButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(bgColor);
        button.setForeground(CLAY_TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
    
    private JTable createClayTable() {
        JTable table = new JTable();
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(CLAY_SURFACE);
        table.setForeground(CLAY_TEXT);
        table.setSelectionBackground(new Color(0, 161, 255, 60));
        table.setSelectionForeground(CLAY_TEXT);
        table.setGridColor(CLAY_BORDER);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(CLAY_ELEVATED);
        header.setForeground(CLAY_TEXT);
        header.setOpaque(true);
        
        return table;
    }
    
    private JPanel createClayTablePanel(JTable table, String title, Color accentColor) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(CLAY_SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 2),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(accentColor);
        label.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(label, BorderLayout.NORTH);
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(CLAY_SURFACE);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private void addTableDoubleClickListener(JTable table) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String productName = (String) table.getValueAt(row, 0);
                        showAddToCartDialog(productName);
                    }
                }
            }
        });
    }

    private void showAddToCartDialog(String productName) {
        ProductInfo info = productInfoMap.get(productName);
        if (info == null) {
            JOptionPane.showMessageDialog(this, 
                "Product information not found for: " + productName, 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JDialog dialog = new JDialog(this, "Add to Purchase Cart", true);
        dialog.setResizable(true);
        
        // Dynamic sizing for dialog
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int dialogWidth = Math.min(550, (int)(screenSize.width * 0.5));
        int dialogHeight = Math.min(450, (int)(screenSize.height * 0.5));
        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setMinimumSize(new Dimension(450, 400));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.setBackground(CLAY_SURFACE);
        
        // Product Info Card
        JPanel infoCard = createClayInfoCard();
        infoCard.add(createClayInfoRow("Product:", productName, true));
        infoCard.add(createClayInfoRow("Current Stock:", String.valueOf(info.currentStock), false));
        infoCard.add(createClayInfoRow("Min Level:", String.valueOf(info.minStockLevel), false));
        infoCard.add(createClayInfoRow("Suggested Order:", String.valueOf(Math.max(info.minStockLevel - info.currentStock, 0)), false));
        infoCard.add(createClayInfoRow("Unit Cost:", "TSh " + info.buyingPrice, false));
        
        contentPanel.add(infoCard);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // Quantity Selection
        JPanel qtyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        qtyPanel.setBackground(CLAY_SURFACE);
        
        JLabel lblQty = new JLabel("Order Quantity:");
        lblQty.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblQty.setForeground(CLAY_TEXT);
        
        int suggestedQty = Math.max(info.minStockLevel - info.currentStock, 1);
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(suggestedQty, 1, 10000, 1);
        JSpinner spinnerQty = new JSpinner(spinnerModel);
        spinnerQty.setPreferredSize(new Dimension(120, 35));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinnerQty.getEditor();
        editor.getTextField().setHorizontalAlignment(JTextField.CENTER);
        editor.getTextField().setFont(new Font("Segoe UI", Font.BOLD, 16));
        editor.getTextField().setBackground(CLAY_ELEVATED);
        editor.getTextField().setForeground(CLAY_TEXT);
        
        JLabel lblSubtotal = new JLabel();
        lblSubtotal.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSubtotal.setForeground(CLAY_SUCCESS);
        
        // Update subtotal when quantity changes
        Runnable updateSubtotal = () -> {
            int qty = (Integer) spinnerQty.getValue();
            BigDecimal subtotal = info.buyingPrice.multiply(BigDecimal.valueOf(qty));
            lblSubtotal.setText("Subtotal: TSh " + subtotal);
        };
        updateSubtotal.run();
        
        spinnerQty.addChangeListener(e -> updateSubtotal.run());
        
        qtyPanel.add(lblQty);
        qtyPanel.add(spinnerQty);
        qtyPanel.add(lblSubtotal);
        
        contentPanel.add(qtyPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        // Add to Cart Button
        JButton btnAddToCart = createClayButton("✓ Add to Cart", CLAY_SUCCESS);
        btnAddToCart.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnAddToCart.setPreferredSize(new Dimension(200, 45));
        
        btnAddToCart.addActionListener(e -> {
            int qty = (Integer) spinnerQty.getValue();
            addToCart(productName, qty);
            
            JOptionPane.showMessageDialog(dialog, 
                String.format("✓ Added to cart:\n%s (Qty: %d)\nSubtotal: TSh %s", 
                    productName, qty, info.buyingPrice.multiply(BigDecimal.valueOf(qty))), 
                "Added Successfully", 
                JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(CLAY_SURFACE);
        buttonPanel.add(btnAddToCart);
        contentPanel.add(buttonPanel);
        
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }
    
    private JPanel createClayInfoCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CLAY_ELEVATED);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            new EmptyBorder(15, 15, 15, 15)
        ));
        return card;
    }
    
    private JPanel createClayInfoRow(String label, String value, boolean isTitle) {
        JPanel row = new JPanel(new BorderLayout(10, 5));
        row.setBackground(CLAY_ELEVATED);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        JLabel lblKey = new JLabel(label);
        lblKey.setFont(new Font("Segoe UI", isTitle ? Font.BOLD : Font.PLAIN, isTitle ? 16 : 14));
        lblKey.setForeground(isTitle ? CLAY_PRIMARY : CLAY_TEXT_SECONDARY);
        
        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, isTitle ? 16 : 14));
        lblValue.setForeground(isTitle ? CLAY_PRIMARY : CLAY_TEXT);
        
        row.add(lblKey, BorderLayout.WEST);
        row.add(lblValue, BorderLayout.EAST);
        
        return row;
    }

    private void addToCart(String productName, int quantity) {
        ProductInfo info = productInfoMap.get(productName);
        if (info == null) {
            JOptionPane.showMessageDialog(this, 
                "Product information not found for: " + productName, 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        boolean found = false;
        for (CartItem item : purchaseCart) {
            if (item.productName.equals(productName)) {
                item.quantity += quantity;
                found = true;
                break;
            }
        }
        
        if (!found) {
            CartItem item = new CartItem(productName, quantity, info.buyingPrice, info.vendor);
            purchaseCart.add(item);
        }
    }

    private void showViewOrderDialog() {
        if (purchaseCart.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Your cart is empty.\nDouble-click products to add them to cart.",
                "Empty Cart",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JDialog dialog = new JDialog(this, "Purchase Cart - " + purchaseCart.size() + " items", false);
        dialog.setResizable(true);
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1000, (int)(screenSize.width * 0.85));
        int height = Math.min(700, (int)(screenSize.height * 0.8));
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(800, 500));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        // Title
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(CLAY_PRIMARY);
        titlePanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("🛒 Purchase Cart List");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel itemCount = new JLabel(purchaseCart.size() + " items");
        itemCount.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        itemCount.setForeground(Color.WHITE);
        
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(itemCount, BorderLayout.EAST);
        
        dialog.add(titlePanel, BorderLayout.NORTH);
        
        // Table
        String[] columns = {"Product", "Vendor", "Order Qty", "Unit Price", "Sub Total", "Actions"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }
        };
        
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (CartItem item : purchaseCart) {
            BigDecimal subPrice = item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity));
            model.addRow(new Object[]{
                item.productName,
                item.vendor != null ? item.vendor : "N/A",
                item.quantity,
                "TSh " + item.buyingPrice,
                "TSh " + subPrice,
                item.quantity
            });
            totalPrice = totalPrice.add(subPrice);
        }
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(40);
        table.setBackground(CLAY_SURFACE);
        table.setForeground(CLAY_TEXT);
        table.setSelectionBackground(new Color(0, 161, 255, 60));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(CLAY_ELEVATED);
        table.getTableHeader().setForeground(CLAY_TEXT);
        
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        
        table.getColumn("Actions").setCellRenderer(new ClayButtonRenderer());
        table.getColumn("Actions").setCellEditor(new ClayButtonEditor(new JCheckBox(), table, purchaseCart, model));
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
        scrollPane.getViewport().setBackground(CLAY_SURFACE);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        bottomPanel.setBackground(CLAY_SURFACE);
        
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        infoPanel.setBackground(CLAY_SURFACE);
        
        JLabel lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(CLAY_SUCCESS);
        
        JLabel lblItems = new JLabel();
        lblItems.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblItems.setForeground(CLAY_TEXT);
        
        JLabel lblOrderedBy = new JLabel("Ordered by: " + currentUser + " | Date: " + LocalDate.now());
        lblOrderedBy.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblOrderedBy.setForeground(CLAY_TEXT_SECONDARY);
        
        Runnable updateTotals = () -> {
            BigDecimal total = BigDecimal.ZERO;
            int totalQty = 0;
            for (CartItem item : purchaseCart) {
                total = total.add(item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity)));
                totalQty += item.quantity;
            }
            lblTotal.setText("Total Amount: TSh " + String.format("%,.2f", total));
            lblItems.setText("Total Items: " + purchaseCart.size() + " (Qty: " + totalQty + ")");
            itemCount.setText(purchaseCart.size() + " items");
        };
        
        updateTotals.run();
        model.addTableModelListener(e -> updateTotals.run());
        
        infoPanel.add(lblTotal);
        infoPanel.add(lblItems);
        infoPanel.add(lblOrderedBy);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(CLAY_SURFACE);
        
        JButton btnClearCart = createClayButton("🗑 Clear Cart", CLAY_DANGER);
        btnClearCart.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                "Clear all items from cart?",
                "Confirm",
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                purchaseCart.clear();
                dialog.dispose();
                showViewOrderDialog();
            }
        });
        
        JButton btnSave = createClayButton("✓ Save Order", CLAY_PRIMARY);
        btnSave.addActionListener(e -> {
            BigDecimal finalTotal = BigDecimal.ZERO;
            for (CartItem item : purchaseCart) {
                finalTotal = finalTotal.add(item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity)));
            }
            savePurchaseOrder("MANUAL", finalTotal);
            JOptionPane.showMessageDialog(dialog, 
                "✓ Purchase order saved successfully!\n" +
                "Total Amount: TSh " + String.format("%,.2f", finalTotal) + "\n" +
                "Check Notifications for details.", 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
            purchaseCart.clear();
            dialog.dispose();
        });
        
        buttonPanel.add(btnClearCart);
        buttonPanel.add(btnSave);
        
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(CLAY_SURFACE);
        bottomContainer.add(infoPanel, BorderLayout.WEST);
        bottomContainer.add(buttonPanel, BorderLayout.EAST);
        
        bottomPanel.add(bottomContainer, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    // Claymorphism Button Renderer for table
    class ClayButtonRenderer extends JPanel implements TableCellRenderer {
        private JButton btnMinus;
        private JTextField txtQuantity;
        private JButton btnPlus;
        
        public ClayButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
            setOpaque(true);
            setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnMinus.setPreferredSize(new Dimension(45, 28));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            btnMinus.setBorderPainted(false);
            
            txtQuantity = new JTextField("0");
            txtQuantity.setFont(new Font("Segoe UI", Font.BOLD, 13));
            txtQuantity.setPreferredSize(new Dimension(50, 28));
            txtQuantity.setHorizontalAlignment(JTextField.CENTER);
            txtQuantity.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            txtQuantity.setBackground(CLAY_ELEVATED);
            txtQuantity.setForeground(CLAY_TEXT);
            txtQuantity.setEditable(false);
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnPlus.setPreferredSize(new Dimension(45, 28));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            btnPlus.setBorderPainted(false);
            
            add(btnMinus);
            add(txtQuantity);
            add(btnPlus);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            txtQuantity.setText(String.valueOf(value));
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(CLAY_SURFACE);
            }
            return this;
        }
    }

    // Claymorphism Button Editor for table
    class ClayButtonEditor extends DefaultCellEditor {
        private JPanel panel;
        private JButton btnMinus;
        private JTextField txtQuantity;
        private JButton btnPlus;
        private int currentRow;
        private JTable table;
        private List<CartItem> cart;
        private DefaultTableModel model;

        public ClayButtonEditor(JCheckBox checkBox, JTable table, List<CartItem> cart, DefaultTableModel model) {
            super(checkBox);
            this.table = table;
            this.cart = cart;
            this.model = model;
            
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
            panel.setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnMinus.setPreferredSize(new Dimension(45, 28));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            btnMinus.setBorderPainted(false);
            btnMinus.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            txtQuantity = new JTextField("0");
            txtQuantity.setFont(new Font("Segoe UI", Font.BOLD, 13));
            txtQuantity.setPreferredSize(new Dimension(50, 28));
            txtQuantity.setHorizontalAlignment(JTextField.CENTER);
            txtQuantity.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            txtQuantity.setBackground(CLAY_ELEVATED);
            txtQuantity.setForeground(CLAY_TEXT);
            
            txtQuantity.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    e.consume();
                    txtQuantity.requestFocusInWindow();
                }
            });
            
            txtQuantity.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(() -> txtQuantity.selectAll());
                }
            });
            
            txtQuantity.addActionListener(e -> {
                try {
                    String text = txtQuantity.getText().trim();
                    if (!text.isEmpty()) {
                        int value = Integer.parseInt(text);
                        if (value > 0 && value < 100000) {
                            if (currentRow >= 0 && currentRow < cart.size()) {
                                CartItem item = cart.get(currentRow);
                                item.quantity = value;
                                updateRow();
                                fireEditingStopped();
                            }
                        }
                    }
                } catch (NumberFormatException ex) {
                    if (currentRow >= 0 && currentRow < cart.size()) {
                        txtQuantity.setText(String.valueOf(cart.get(currentRow).quantity));
                    }
                }
            });
            
            txtQuantity.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    try {
                        String text = txtQuantity.getText().trim();
                        if (!text.isEmpty()) {
                            int value = Integer.parseInt(text);
                            if (value > 0 && value < 100000) {
                                if (currentRow >= 0 && currentRow < cart.size()) {
                                    CartItem item = cart.get(currentRow);
                                    item.quantity = value;
                                    updateRow();
                                }
                            }
                        }
                    } catch (NumberFormatException ex) {
                        if (currentRow >= 0 && currentRow < cart.size()) {
                            txtQuantity.setText(String.valueOf(cart.get(currentRow).quantity));
                        }
                    }
                }
            });
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnPlus.setPreferredSize(new Dimension(45, 28));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            btnPlus.setBorderPainted(false);
            btnPlus.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            btnMinus.addActionListener(e -> {
                if (currentRow >= 0 && currentRow < cart.size()) {
                    CartItem item = cart.get(currentRow);
                    if (item.quantity > 1) {
                        item.quantity--;
                        txtQuantity.setText(String.valueOf(item.quantity));
                        updateRow();
                    } else {
                        int confirm = JOptionPane.showConfirmDialog(
                            SwingUtilities.getWindowAncestor(panel),
                            "Remove this item from cart?",
                            "Confirm Removal",
                            JOptionPane.YES_NO_OPTION
                        );
                        if (confirm == JOptionPane.YES_OPTION) {
                            cart.remove(currentRow);
                            model.removeRow(currentRow);
                        }
                    }
                }
                fireEditingStopped();
            });
            
            btnPlus.addActionListener(e -> {
                if (currentRow >= 0 && currentRow < cart.size()) {
                    CartItem item = cart.get(currentRow);
                    item.quantity++;
                    txtQuantity.setText(String.valueOf(item.quantity));
                    updateRow();
                }
                fireEditingStopped();
            });
            
            panel.add(btnMinus);
            panel.add(txtQuantity);
            panel.add(btnPlus);
        }
        
        private void updateRow() {
            if (currentRow >= 0 && currentRow < cart.size()) {
                CartItem item = cart.get(currentRow);
                
                SwingUtilities.invokeLater(() -> {
                    BigDecimal subtotal = item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity));
                    model.setValueAt(item.quantity, currentRow, 2);
                    model.setValueAt("TSh " + subtotal, currentRow, 4);
                });
            }
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            this.currentRow = row;
            
            if (row >= 0 && row < cart.size()) {
                txtQuantity.setText(String.valueOf(cart.get(row).quantity));
            }
            
            SwingUtilities.invokeLater(() -> txtQuantity.requestFocusInWindow());
            
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return currentRow;
        }
    }

    private void showSmartAutoOrderDialog() {
        String[] options = {"Fixed Budget", "Range Budget", "Reorder Point", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
            "Select auto-ordering method:\n\n" +
            "• Fixed Budget: Order within exact budget\n" +
            "• Range Budget: Order within min-max range\n" +
            "• Reorder Point: Smart ordering based on demand",
            "Smart Auto Order",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        
        switch(choice) {
            case 0:
                showFixedBudgetDialog();
                break;
            case 1:
                showRangeBudgetDialog();
                break;
            case 2:
                showReorderPointDialog();
                break;
        }
    }

    private void showFixedBudgetDialog() {
        JDialog dialog = new JDialog(this, "Fixed Budget Order", true);
        dialog.setResizable(true);
        dialog.setSize(450, 250);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(15, 15));
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        contentPanel.setBackground(CLAY_SURFACE);
        
        JLabel titleLabel = new JLabel("💰 Fixed Budget Order");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(CLAY_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JLabel lblBudget = new JLabel("Enter your exact budget amount (e.g., 1,000 or 100,000):");
        lblBudget.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblBudget.setForeground(CLAY_TEXT);
        lblBudget.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lblBudget);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        
        JTextField txtBudget = new JTextField();
        txtBudget.setFont(new Font("Segoe UI", Font.BOLD, 16));
        txtBudget.setHorizontalAlignment(JTextField.CENTER);
        txtBudget.setMaximumSize(new Dimension(300, 40));
        txtBudget.setAlignmentX(Component.CENTER_ALIGNMENT);
        txtBudget.setBackground(CLAY_ELEVATED);
        txtBudget.setForeground(CLAY_TEXT);
        txtBudget.setCaretColor(CLAY_PRIMARY);
        
        // Add document listener to format with commas
        txtBudget.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private boolean isFormatting = false;
            
            public void changedUpdate(javax.swing.event.DocumentEvent e) { format(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { format(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { format(); }
            
            private void format() {
                if (isFormatting) return;
                
                SwingUtilities.invokeLater(() -> {
                    isFormatting = true;
                    try {
                        String text = txtBudget.getText().replaceAll("[^0-9]", "");
                        if (!text.isEmpty() && text.length() < 19) {
                            long number = Long.parseLong(text);
                            int caretPos = txtBudget.getCaretPosition();
                            String formatted = String.format("%,d", number);
                            txtBudget.setText(formatted);
                            try {
                                txtBudget.setCaretPosition(Math.min(caretPos, formatted.length()));
                            } catch (IllegalArgumentException ex) {
                                txtBudget.setCaretPosition(formatted.length());
                            }
                        }
                    } catch (NumberFormatException ex) {
                        // Silently ignore invalid input
                    } finally {
                        isFormatting = false;
                    }
                });
            }
        });
        
        contentPanel.add(txtBudget);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        
        JButton btnGenerate = createClayButton("Generate Order", CLAY_PRIMARY);
        btnGenerate.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        btnGenerate.addActionListener(e -> {
            try {
                String cleanText = txtBudget.getText().replaceAll("[^0-9]", "");
                BigDecimal budget = new BigDecimal(cleanText);
                if (budget.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Budget must be greater than zero", "Invalid Budget", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                dialog.dispose();
                generateFixedBudgetOrderOptimized(budget);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid number", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        contentPanel.add(btnGenerate);
        dialog.add(contentPanel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void generateFixedBudgetOrderOptimized(BigDecimal budget) {
        List<CartItem> autoCart = new ArrayList<>();
        BigDecimal currentTotal = BigDecimal.ZERO;
        BigDecimal remainingBudget = budget;
        
        // Priority list: Out of stock first, then below minimum, then fast moving
        List<ProductInfo> prioritizedProducts = new ArrayList<>();
        Set<String> addedProducts = new HashSet<>();
        
        // Priority 1: Out of stock items (CRITICAL)
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor " +
                 "FROM products p " +
                 "WHERE p.stock_quantity = 0 " +
                 "ORDER BY p.min_stock_level DESC")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ProductInfo info = new ProductInfo();
                info.name = rs.getString("name");
                info.buyingPrice = rs.getBigDecimal("cost_price");
                info.sellingPrice = rs.getBigDecimal("selling_price");
                info.currentStock = rs.getInt("stock_quantity");
                info.minStockLevel = rs.getInt("min_stock_level");
                info.vendor = rs.getString("vendor");
                prioritizedProducts.add(info);
                addedProducts.add(info.name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Priority 2: Below minimum stock (HIGH)
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor " +
                 "FROM products p " +
                 "WHERE p.stock_quantity > 0 AND p.stock_quantity < p.min_stock_level " +
                 "ORDER BY (p.min_stock_level - p.stock_quantity) DESC")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String productName = rs.getString("name");
                if (!addedProducts.contains(productName)) {
                    ProductInfo info = new ProductInfo();
                    info.name = productName;
                    info.buyingPrice = rs.getBigDecimal("cost_price");
                    info.sellingPrice = rs.getBigDecimal("selling_price");
                    info.currentStock = rs.getInt("stock_quantity");
                    info.minStockLevel = rs.getInt("min_stock_level");
                    info.vendor = rs.getString("vendor");
                    prioritizedProducts.add(info);
                    addedProducts.add(productName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Priority 3: Fast moving items (MEDIUM)
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor, " +
                 "COUNT(DISTINCT DATE(s.sale_date)) AS days_sold " +
                 "FROM products p " +
                 "INNER JOIN sale_items si ON p.id = si.product_id " +
                 "INNER JOIN sales s ON si.sale_id = s.id " +
                 "WHERE s.sale_date >= DATE_SUB(CURDATE(), INTERVAL 1 WEEK) " +
                 "GROUP BY p.id, p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor " +
                 "HAVING days_sold >= 3 " +
                 "ORDER BY days_sold DESC")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String productName = rs.getString("name");
                if (!addedProducts.contains(productName)) {
                    ProductInfo info = new ProductInfo();
                    info.name = productName;
                    info.buyingPrice = rs.getBigDecimal("cost_price");
                    info.sellingPrice = rs.getBigDecimal("selling_price");
                    info.currentStock = rs.getInt("stock_quantity");
                    info.minStockLevel = rs.getInt("min_stock_level");
                    info.vendor = rs.getString("vendor");
                    prioritizedProducts.add(info);
                    addedProducts.add(productName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Smart allocation algorithm
        for (ProductInfo product : prioritizedProducts) {
            // Skip if we can't afford even one unit
            if (remainingBudget.compareTo(product.buyingPrice) < 0) {
                continue;
            }
            
            // Calculate needed quantity
            int neededQty;
            if (product.currentStock == 0) {
                // Out of stock: order minimum stock level
                neededQty = Math.max(product.minStockLevel, 1);
            } else if (product.currentStock < product.minStockLevel) {
                // Below minimum: order deficit + 20% buffer
                int deficit = product.minStockLevel - product.currentStock;
                neededQty = (int) Math.ceil(deficit * 1.2);
            } else {
                // Fast moving but sufficient stock: order 50% of min level as buffer
                neededQty = Math.max((int) (product.minStockLevel * 0.5), 5);
            }
            
            // Calculate how many we can afford
            int affordableQty = remainingBudget.divide(product.buyingPrice, 0, RoundingMode.DOWN).intValue();
            
            // Order the minimum of needed and affordable
            int orderQty = Math.min(neededQty, affordableQty);
            
            if (orderQty > 0) {
                BigDecimal cost = product.buyingPrice.multiply(BigDecimal.valueOf(orderQty));
                autoCart.add(new CartItem(product.name, orderQty, product.buyingPrice, product.vendor));
                currentTotal = currentTotal.add(cost);
                remainingBudget = remainingBudget.subtract(cost);
            }
            
            // Stop if budget is exhausted
            if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        
        if (autoCart.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Unable to generate order with budget: TSh " + String.format("%,d", budget.longValue()) + "\n\n" +
                "Possible reasons:\n" +
                "• Budget too low for any products\n" +
                "• No products meet ordering criteria\n" +
                "• All stock levels are adequate\n\n" +
                "Try increasing the budget or check inventory status.",
                "Order Generation Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Show success message with details
        String budgetInfo = String.format("FIXED BUDGET: TSh %,d (Used: TSh %,d, Remaining: TSh %,d)", 
            budget.longValue(), 
            currentTotal.longValue(), 
            remainingBudget.longValue());
        
        showAutoGeneratedCart(autoCart, currentTotal, budget, budgetInfo);
    }

    private void showRangeBudgetDialog() {
        JDialog dialog = new JDialog(this, "Range Budget Order", true);
        dialog.setResizable(true);
        dialog.setSize(450, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 15));
        inputPanel.setBorder(new EmptyBorder(30, 30, 20, 30));
        inputPanel.setBackground(CLAY_SURFACE);
        
        JLabel titleLabel = new JLabel("📊 Range Budget Order", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(CLAY_PRIMARY);
        
        JLabel lblMin = new JLabel("Minimum Amount:");
        lblMin.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblMin.setForeground(CLAY_TEXT);
        JTextField txtMin = new JTextField();
        txtMin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtMin.setBackground(CLAY_ELEVATED);
        txtMin.setForeground(CLAY_TEXT);
        txtMin.setCaretColor(CLAY_PRIMARY);
        
        JLabel lblMax = new JLabel("Maximum Amount:");
        lblMax.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblMax.setForeground(CLAY_TEXT);
        JTextField txtMax = new JTextField();
        txtMax.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtMax.setBackground(CLAY_ELEVATED);
        txtMax.setForeground(CLAY_TEXT);
        txtMax.setCaretColor(CLAY_PRIMARY);
        
        inputPanel.add(lblMin);
        inputPanel.add(txtMin);
        inputPanel.add(lblMax);
        inputPanel.add(txtMax);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(CLAY_SURFACE);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        
        dialog.add(mainPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 15));
        buttonPanel.setBackground(CLAY_SURFACE);
        
        JButton btnGenerate = createClayButton("Generate Order", CLAY_SUCCESS);
        
        btnGenerate.addActionListener(e -> {
            try {
                BigDecimal minAmount = new BigDecimal(txtMin.getText().trim());
                BigDecimal maxAmount = new BigDecimal(txtMax.getText().trim());
                
                if (minAmount.compareTo(maxAmount) >= 0) {
                    JOptionPane.showMessageDialog(dialog, 
                        "Minimum amount must be less than maximum amount.", 
                        "Invalid Range", 
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                dialog.dispose();
                generateRangeBudgetOrder(minAmount, maxAmount);
                
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, 
                    "Please enter valid numbers for both amounts.", 
                    "Invalid Input", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        
        buttonPanel.add(btnGenerate);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    private void generateRangeBudgetOrder(BigDecimal minAmount, BigDecimal maxAmount) {
        List<CartItem> autoCart = new ArrayList<>();
        BigDecimal currentTotal = BigDecimal.ZERO;
        
        List<ProductInfo> allProducts = new ArrayList<>();
        Set<String> added = new HashSet<>();
        
        for (ProductInfo p : getLowStockProducts()) {
            if (p.currentStock == 0) {
                allProducts.add(p);
                added.add(p.name);
            }
        }
        
        for (ProductInfo p : getLowStockProducts()) {
            if (!added.contains(p.name)) {
                allProducts.add(p);
                added.add(p.name);
            }
        }
        
        for (ProductInfo p : getFastMovingProducts()) {
            if (!added.contains(p.name)) {
                allProducts.add(p);
                added.add(p.name);
            }
        }
        
        BigDecimal targetAmount = minAmount.add(maxAmount).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        
        for (ProductInfo product : allProducts) {
            if (currentTotal.compareTo(maxAmount) >= 0) {
                break;
            }
            
            int neededQty = Math.max(product.minStockLevel - product.currentStock, 5);
            BigDecimal cost = product.buyingPrice.multiply(BigDecimal.valueOf(neededQty));
            
            if (currentTotal.add(cost).compareTo(maxAmount) <= 0) {
                autoCart.add(new CartItem(product.name, neededQty, product.buyingPrice, product.vendor));
                currentTotal = currentTotal.add(cost);
            } else {
                BigDecimal remaining = maxAmount.subtract(currentTotal);
                int reducedQty = remaining.divide(product.buyingPrice, 0, RoundingMode.DOWN).intValue();
                
                if (reducedQty > 0) {
                    BigDecimal reducedCost = product.buyingPrice.multiply(BigDecimal.valueOf(reducedQty));
                    if (currentTotal.add(reducedCost).compareTo(minAmount) >= 0) {
                        autoCart.add(new CartItem(product.name, reducedQty, product.buyingPrice, product.vendor));
                        currentTotal = currentTotal.add(reducedCost);
                        break;
                    }
                }
            }
        }
        
        if (currentTotal.compareTo(minAmount) < 0) {
            JOptionPane.showMessageDialog(this, 
                String.format("Unable to generate order within range.\nCurrent total: TSh %s\nMinimum: TSh %s\n\nTry adjusting the range.", 
                    currentTotal, minAmount), 
                "Out of Range", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        showAutoGeneratedCart(autoCart, currentTotal, maxAmount, 
            String.format("RANGE: TSh %s - TSh %s", minAmount, maxAmount));
    }

    private void showReorderPointDialog() {
        List<CartItem> autoCart = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        
        List<ProductInfo> products = new ArrayList<>();
        products.addAll(getLowStockProducts());
        products.addAll(getFastMovingProducts());
        
        Set<String> seen = new HashSet<>();
        List<ProductInfo> uniqueProducts = new ArrayList<>();
        for (ProductInfo p : products) {
            if (seen.add(p.name)) {
                uniqueProducts.add(p);
            }
        }
        
        for (ProductInfo product : uniqueProducts) {
            int orderQty = calculateReorderQuantity(product);
            if (orderQty > 0) {
                BigDecimal cost = product.buyingPrice.multiply(BigDecimal.valueOf(orderQty));
                autoCart.add(new CartItem(product.name, orderQty, product.buyingPrice, product.vendor));
                totalCost = totalCost.add(cost);
            }
        }
        
        if (autoCart.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No products need reordering at this time.\nAll stock levels are adequate.",
                "No Reorder Needed",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        showAutoGeneratedCart(autoCart, totalCost, totalCost, "SMART REORDER (Demand-Based)");
    }

    private int calculateReorderQuantity(ProductInfo product) {
        int stockDeficit = product.minStockLevel - product.currentStock;
        
        if (stockDeficit <= 0) {
            return 0;
        }
        
        int safetyStock = (int) (product.minStockLevel * 0.25);
        
        return stockDeficit + safetyStock;
    }

    private void showAutoGeneratedCart(List<CartItem> autoCart, BigDecimal actualTotal, 
                                       BigDecimal budgetAmount, String budgetInfo) {
        JDialog dialog = new JDialog(this, "Auto-Generated Purchase Order", false);
        dialog.setResizable(true);
        dialog.setSize(900, 650);
        dialog.setMinimumSize(new Dimension(800, 500));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CLAY_PRIMARY);
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("🤖 Auto-Generated Purchase Order");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel budgetLabel = new JLabel(budgetInfo);
        budgetLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        budgetLabel.setForeground(Color.WHITE);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(budgetLabel, BorderLayout.EAST);
        
        dialog.add(headerPanel, BorderLayout.NORTH);
        
        String[] columns = {"Product", "Vendor", "Order Qty", "Unit Price", "Sub Total"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        for (CartItem item : autoCart) {
            BigDecimal subPrice = item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity));
            model.addRow(new Object[]{
                item.productName,
                item.vendor != null ? item.vendor : "N/A",
                item.quantity,
                "TSh " + item.buyingPrice,
                "TSh " + subPrice
            });
        }
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setBackground(CLAY_SURFACE);
        table.setForeground(CLAY_TEXT);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(CLAY_ELEVATED);
        table.getTableHeader().setForeground(CLAY_TEXT);
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
        scrollPane.getViewport().setBackground(CLAY_SURFACE);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        bottomPanel.setBackground(CLAY_SURFACE);
        
        JPanel statsPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        statsPanel.setBackground(CLAY_SURFACE);
        
        JLabel lblTotal = new JLabel("Total Amount: TSh " + actualTotal);
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTotal.setForeground(CLAY_SUCCESS);
        
        JLabel lblItems = new JLabel("Total Products: " + autoCart.size());
        lblItems.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblItems.setForeground(CLAY_TEXT);
        
        BigDecimal remaining = budgetAmount.subtract(actualTotal);
        JLabel lblRemaining = new JLabel("Budget Remaining: TSh " + remaining);
        lblRemaining.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblRemaining.setForeground(remaining.compareTo(BigDecimal.ZERO) >= 0 ? CLAY_SUCCESS : CLAY_DANGER);
        
        JLabel lblBy = new JLabel("Ordered by: " + currentUser + " | Generated: " + LocalDate.now());
        lblBy.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblBy.setForeground(CLAY_TEXT_SECONDARY);
        
        statsPanel.add(lblTotal);
        statsPanel.add(lblItems);
        statsPanel.add(lblRemaining);
        statsPanel.add(lblBy);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(CLAY_SURFACE);
        
        JButton btnAddToCart = createClayButton("➕ Add All to Cart", CLAY_WARNING);
        btnAddToCart.addActionListener(e -> {
            purchaseCart.addAll(autoCart);
            JOptionPane.showMessageDialog(dialog,
                autoCart.size() + " items added to cart!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });
        
        JButton btnSave = createClayButton("✓ Save Order", CLAY_SUCCESS);
        btnSave.addActionListener(e -> {
            saveAutoGeneratedOrder(autoCart, budgetInfo, actualTotal);
            JOptionPane.showMessageDialog(dialog, 
                "✓ Auto-generated order saved successfully!\n" +
                "Total: TSh " + actualTotal + "\n" +
                "Check Notifications for details.", 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });
        
        buttonPanel.add(btnAddToCart);
        buttonPanel.add(btnSave);
        
        bottomPanel.add(statsPanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showOrderHistory() {
        try {
            List<NotificationDatabase.Notebook> allOrders = NotificationDatabase.getAllReceipts();
            
            List<NotificationDatabase.Notebook> purchaseOrders = new ArrayList<>();
            for (NotificationDatabase.Notebook order : allOrders) {
                if (order.getTitle().toLowerCase().contains("purchase order")) {
                    purchaseOrders.add(order);
                }
            }
            
            if (purchaseOrders.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No purchase orders found in history.",
                    "Order History",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            JDialog dialog = new JDialog(this, "Order History", false);
            dialog.setResizable(true);
            
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int width = Math.min(900, (int)(screenSize.width * 0.8));
            int height = Math.min(600, (int)(screenSize.height * 0.7));
            dialog.setSize(width, height);
            dialog.setMinimumSize(new Dimension(700, 500));
            dialog.setLocationRelativeTo(this);
            dialog.setLayout(new BorderLayout(10, 10));
            
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(new Color(23, 162, 184));
            headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
            
            JLabel titleLabel = new JLabel("📜 Order History");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
            titleLabel.setForeground(Color.WHITE);
            
            JLabel countLabel = new JLabel(purchaseOrders.size() + " orders");
            countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            countLabel.setForeground(Color.WHITE);
            
            headerPanel.add(titleLabel, BorderLayout.WEST);
            headerPanel.add(countLabel, BorderLayout.EAST);
            
            dialog.add(headerPanel, BorderLayout.NORTH);
            
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBackground(CLAY_SURFACE);
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            
            for (NotificationDatabase.Notebook order : purchaseOrders) {
                JPanel orderCard = createOrderHistoryCard(order);
                mainPanel.add(orderCard);
                mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            }
            
            JScrollPane scrollPane = new JScrollPane(mainPanel);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(CLAY_SURFACE);
            dialog.add(scrollPane, BorderLayout.CENTER);
            
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            bottomPanel.setBackground(CLAY_SURFACE);
            bottomPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            
            JButton btnClose = createClayButton("Close", CLAY_ELEVATED);
            btnClose.setPreferredSize(new Dimension(150, 40));
            btnClose.addActionListener(e -> dialog.dispose());
            
            bottomPanel.add(btnClose);
            dialog.add(bottomPanel, BorderLayout.SOUTH);
            
            dialog.setVisible(true);
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading order history: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createOrderHistoryCard(NotificationDatabase.Notebook order) {
        JPanel card = new JPanel(new BorderLayout(15, 15));
        card.setBackground(CLAY_ELEVATED);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CLAY_BORDER, 1),
                new EmptyBorder(15, 20, 15, 20)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CLAY_ELEVATED);

        JLabel lblTitle = new JLabel(order.getTitle());
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(CLAY_PRIMARY);
        headerPanel.add(lblTitle, BorderLayout.WEST);

        LocalDate createdDate = order.getCreatedDate();
        String formattedDate = createdDate.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));

        JLabel lblDate = new JLabel(formattedDate);
        lblDate.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblDate.setForeground(CLAY_TEXT_SECONDARY);
        headerPanel.add(lblDate, BorderLayout.EAST);

        card.add(headerPanel, BorderLayout.NORTH);

        JTextArea txtContent = new JTextArea(order.getContent());
        txtContent.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtContent.setEditable(false);
        txtContent.setBackground(CLAY_ELEVATED);
        txtContent.setForeground(CLAY_TEXT);
        txtContent.setLineWrap(true);
        txtContent.setWrapStyleWord(true);
        txtContent.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane contentScroll = new JScrollPane(txtContent);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(contentScroll, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footerPanel.setBackground(CLAY_ELEVATED);

        String user = order.getUser() != null ? order.getUser() : "Unknown User";
        JLabel lblOrderedBy = new JLabel("Ordered by: " + user);
        lblOrderedBy.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblOrderedBy.setForeground(CLAY_TEXT_SECONDARY);

        footerPanel.add(lblOrderedBy);
        card.add(footerPanel, BorderLayout.SOUTH);

        return card;
    }

    private void showBulkOrderDialog() {
        JDialog dialog = new JDialog(this, "Purchase Order - Bulk Order", false);
        dialog.setResizable(true);
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1200, (int)(screenSize.width * 0.9));
        int height = Math.min(700, (int)(screenSize.height * 0.85));
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(900, 600));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBackground(CLAY_PRIMARY);
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("📦 Purchase Order");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchPanel.setBackground(CLAY_PRIMARY);
        
        JTextField txtSearch = new JTextField(20);
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtSearch.setPreferredSize(new Dimension(250, 35));
        txtSearch.setBackground(CLAY_SURFACE);
        txtSearch.setForeground(CLAY_TEXT);
        txtSearch.setCaretColor(CLAY_PRIMARY);
        
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        searchIcon.setForeground(Color.WHITE);
        
        searchPanel.add(searchIcon);
        searchPanel.add(txtSearch);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(searchPanel, BorderLayout.EAST);
        
        dialog.add(headerPanel, BorderLayout.NORTH);
        
        String[] columns = {"Product Name", "Category", "Available Qty", "Buying Price", "Order Qty"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 4) {
                    return Integer.class;
                }
                return Object.class;
            }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(45);
        table.setBackground(CLAY_SURFACE);
        table.setForeground(CLAY_TEXT);
        table.setSelectionBackground(new Color(0, 161, 255, 60));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(CLAY_ELEVATED);
        table.getTableHeader().setForeground(CLAY_TEXT);
        
        table.getColumn("Order Qty").setCellRenderer(new SpinnerCellRenderer());
        table.getColumn("Order Qty").setCellEditor(new SpinnerCellEditor());
        
        Map<Integer, BulkOrderItem> itemMap = new HashMap<>();
        
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, c.name AS category_name, p.stock_quantity, p.cost_price " +
                 "FROM products p " +
                 "LEFT JOIN categories c ON p.category_id = c.id " +
                 "ORDER BY p.name")) {
            
            ResultSet rs = stmt.executeQuery();
            int rowIndex = 0;
            while (rs.next()) {
                String productName = rs.getString("name");
                String category = rs.getString("category_name");
                int availableQty = rs.getInt("stock_quantity");
                BigDecimal buyingPrice = rs.getBigDecimal("cost_price");
                
                model.addRow(new Object[]{
                    productName,
                    category,
                    availableQty,
                    "TSh " + buyingPrice,
                    0
                });
                
                BulkOrderItem item = new BulkOrderItem();
                item.productName = productName;
                item.category = category;
                item.availableQty = availableQty;
                item.buyingPrice = buyingPrice;
                item.orderQty = 0;
                itemMap.put(rowIndex, item);
                
                rowIndex++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
        scrollPane.getViewport().setBackground(CLAY_SURFACE);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { search(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { search(); }
            
            public void search() {
                String searchText = txtSearch.getText().toLowerCase();
                if (searchText.isEmpty()) {
                    table.clearSelection();
                    return;
                }
                
                for (int row = 0; row < table.getRowCount(); row++) {
                    String productName = table.getValueAt(row, 0).toString().toLowerCase();
                    if (productName.contains(searchText)) {
                        table.setRowSelectionInterval(row, row);
                        table.scrollRectToVisible(table.getCellRect(row, 0, true));
                        return;
                    }
                }
            }
        });
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        bottomPanel.setBackground(CLAY_SURFACE);
        
        JPanel cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        cardsPanel.setBackground(CLAY_SURFACE);
        
        JLabel lblTotalEntries = new JLabel("0");
        lblTotalEntries.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTotalEntries.setForeground(CLAY_PRIMARY);
        
        JLabel lblTotalBuyingPrice = new JLabel("TSh 0.00");
        lblTotalBuyingPrice.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTotalBuyingPrice.setForeground(CLAY_SUCCESS);
        
        JButton btnViewCart = createClayButton("🛒 View Cart", CLAY_SUCCESS);
        btnViewCart.addActionListener(e -> {
            showBulkOrderCart(itemMap, table);
        });
        
        cardsPanel.add(createMiniCard("📊 Total Entries", lblTotalEntries));
        cardsPanel.add(createMiniCard("💰 Total Buying Price", lblTotalBuyingPrice));
        cardsPanel.add(btnViewCart);
        
        bottomPanel.add(cardsPanel, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        
        // Update totals when table changes
        model.addTableModelListener(e -> {
            int totalEntries = 0;
            int totalQuantity = 0;
            BigDecimal totalBuyingPrice = BigDecimal.ZERO;
            
            for (int row = 0; row < table.getRowCount(); row++) {
                Integer orderQty = (Integer) table.getValueAt(row, 4);
                if (orderQty != null && orderQty > 0) {
                    totalEntries++;
                    totalQuantity += orderQty;
                    BulkOrderItem item = itemMap.get(row);
                    if (item != null && item.buyingPrice != null) {
                        BigDecimal subtotal = item.buyingPrice.multiply(BigDecimal.valueOf(orderQty));
                        totalBuyingPrice = totalBuyingPrice.add(subtotal);
                        item.orderQty = orderQty;
                    }
                }
            }
            
            lblTotalEntries.setText(String.valueOf(totalQuantity));
            lblTotalBuyingPrice.setText("TSh " + String.format("%,.2f", totalBuyingPrice));
        });
        
        dialog.setVisible(true);
    }

    private JPanel createMiniCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(CLAY_ELEVATED);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            new EmptyBorder(10, 15, 10, 15)
        ));
        
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(CLAY_TEXT_SECONDARY);
        
        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }

    private void showBulkOrderCart(Map<Integer, BulkOrderItem> itemMap, JTable sourceTable) {
        List<BulkOrderItem> cartItems = new ArrayList<>();
        for (BulkOrderItem item : itemMap.values()) {
            if (item.orderQty > 0) {
                cartItems.add(item);
            }
        }
        
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No items in cart.\nPlease add quantities to products first.",
                "Empty Cart",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JDialog dialog = new JDialog(this, "Bulk Order Cart - " + cartItems.size() + " items", true);
        dialog.setResizable(true);
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1000, (int)(screenSize.width * 0.85));
        int height = Math.min(600, (int)(screenSize.height * 0.7));
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(800, 500));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(CLAY_SUCCESS);
        titlePanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("🛒 Bulk Order Cart");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel itemCount = new JLabel(cartItems.size() + " items");
        itemCount.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        itemCount.setForeground(Color.WHITE);
        
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(itemCount, BorderLayout.EAST);
        
        dialog.add(titlePanel, BorderLayout.NORTH);
        
        String[] columns = {"Product", "Category", "Unit Price", "Quantity", "Subtotal"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3;
            }
        };
        
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (BulkOrderItem item : cartItems) {
            BigDecimal subPrice = item.buyingPrice.multiply(BigDecimal.valueOf(item.orderQty));
            model.addRow(new Object[]{
                item.productName,
                item.category != null ? item.category : "N/A",
                "TSh " + item.buyingPrice,
                item.orderQty,
                "TSh " + subPrice
            });
            totalPrice = totalPrice.add(subPrice);
        }
        final BigDecimal finalTotalPrice = totalPrice;
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(45);
        table.setBackground(CLAY_SURFACE);
        table.setForeground(CLAY_TEXT);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(CLAY_ELEVATED);
        table.getTableHeader().setForeground(CLAY_TEXT);
        table.setCellSelectionEnabled(true);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        
        JLabel lblTotal = new JLabel("Total Amount: TSh " + finalTotalPrice);
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(CLAY_SUCCESS);
        
        table.getColumn("Quantity").setCellRenderer(new QuantityAdjustRenderer());
        table.getColumn("Quantity").setCellEditor(new QuantityAdjustEditor(cartItems, model, table, lblTotal));
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
        scrollPane.getViewport().setBackground(CLAY_SURFACE);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        bottomPanel.setBackground(CLAY_SURFACE);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(CLAY_SURFACE);
        
        JButton btnSave = createClayButton("✓ Save Bulk Order", CLAY_SUCCESS);
        btnSave.addActionListener(e -> {
            saveBulkOrder(cartItems);
            JOptionPane.showMessageDialog(dialog,
                "✓ Bulk order saved successfully!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
            
            for (int row = 0; row < sourceTable.getRowCount(); row++) {
                sourceTable.setValueAt(0, row, 4);
            }
        });
        
        buttonPanel.add(btnSave);
        
        bottomPanel.add(lblTotal, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void saveBulkOrder(List<BulkOrderItem> items) {
        try {
            StringBuilder orderDetails = new StringBuilder();
            orderDetails.append("=== BULK PURCHASE ORDER ===\n");
            orderDetails.append("Ordered by: ").append(currentUser).append("\n");
            orderDetails.append("Date: ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("\n");
            orderDetails.append("Total Items: ").append(items.size()).append("\n\n");
            orderDetails.append("ITEMS:\n");
            orderDetails.append(String.format("%-30s %-15s %-10s %-15s %-15s\n",
                "Product", "Category", "Qty", "Unit Price", "Subtotal"));
            orderDetails.append("-".repeat(85)).append("\n");
            
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (BulkOrderItem item : items) {
                BigDecimal subtotal = item.buyingPrice.multiply(BigDecimal.valueOf(item.orderQty));
                orderDetails.append(String.format("%-30s %-15s %-10d TSh %-12s TSh %-12s\n",
                    item.productName,
                    item.category,
                    item.orderQty,
                    item.buyingPrice,
                    subtotal));
                totalAmount = totalAmount.add(subtotal);
            }
            
            orderDetails.append("-".repeat(85)).append("\n");
            orderDetails.append(String.format("\n%-56s TSh %s\n", "TOTAL AMOUNT:", totalAmount));
            
            String title = "Bulk Purchase Order - " + LocalDate.now();
            NotificationDatabase.saveNotebook(title, orderDetails.toString(), currentUser);
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving bulk order: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    class SpinnerCellRenderer extends JPanel implements TableCellRenderer {
        private JButton btnMinus;
        private JLabel lblQuantity;
        private JButton btnPlus;
        
        public SpinnerCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
            setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnMinus.setPreferredSize(new Dimension(45, 32));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            btnMinus.setBorderPainted(false);
            
            lblQuantity = new JLabel("0", SwingConstants.CENTER);
            lblQuantity.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblQuantity.setPreferredSize(new Dimension(70, 32));
            lblQuantity.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            lblQuantity.setOpaque(true);
            lblQuantity.setBackground(CLAY_ELEVATED);
            lblQuantity.setForeground(CLAY_TEXT);
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnPlus.setPreferredSize(new Dimension(45, 32));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            btnPlus.setBorderPainted(false);
            
            add(btnMinus);
            add(lblQuantity);
            add(btnPlus);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            lblQuantity.setText(String.valueOf(value));
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                lblQuantity.setBackground(table.getSelectionBackground());
            } else {
                setBackground(row % 2 == 0 ? CLAY_ELEVATED : CLAY_SURFACE);
                lblQuantity.setBackground(CLAY_ELEVATED);
            }
            return this;
        }
    }

    class SpinnerCellEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JButton btnMinus;
        private JTextField txtQuantity;
        private JButton btnPlus;
        private int currentValue;
        private JTable table;
        private int editingRow;
        
        public SpinnerCellEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
            panel.setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnMinus.setPreferredSize(new Dimension(45, 32));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            btnMinus.setBorderPainted(false);
            btnMinus.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            txtQuantity = new JTextField("0");
            txtQuantity.setFont(new Font("Segoe UI", Font.BOLD, 14));
            txtQuantity.setPreferredSize(new Dimension(70, 32));
            txtQuantity.setHorizontalAlignment(JTextField.CENTER);
            txtQuantity.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            txtQuantity.setBackground(CLAY_ELEVATED);
            txtQuantity.setForeground(CLAY_TEXT);
            txtQuantity.setCaretColor(CLAY_PRIMARY);
            
            txtQuantity.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    e.consume();
                    txtQuantity.requestFocusInWindow();
                }
            });
            
            txtQuantity.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(() -> txtQuantity.selectAll());
                }
            });
            
            txtQuantity.addActionListener(e -> {
                try {
                    String text = txtQuantity.getText().trim();
                    if (!text.isEmpty()) {
                        int value = Integer.parseInt(text);
                        if (value >= 0 && value < 100000) {
                            currentValue = value;
                            updateTable();
                            fireEditingStopped();
                        }
                    }
                } catch (NumberFormatException ex) {
                    txtQuantity.setText(String.valueOf(currentValue));
                }
            });
            
            txtQuantity.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    try {
                        String text = txtQuantity.getText().trim();
                        if (!text.isEmpty()) {
                            int value = Integer.parseInt(text);
                            if (value >= 0 && value < 100000) {
                                currentValue = value;
                                updateTable();
                            }
                        }
                    } catch (NumberFormatException ex) {
                        txtQuantity.setText(String.valueOf(currentValue));
                    }
                }
            });
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnPlus.setPreferredSize(new Dimension(45, 32));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            btnPlus.setBorderPainted(false);
            btnPlus.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            btnMinus.addActionListener(e -> {
                if (currentValue > 0) {
                    currentValue--;
                    txtQuantity.setText(String.valueOf(currentValue));
                    updateTable();
                }
            });
            
            btnPlus.addActionListener(e -> {
                currentValue++;
                txtQuantity.setText(String.valueOf(currentValue));
                updateTable();
            });
            
            panel.add(btnMinus);
            panel.add(txtQuantity);
            panel.add(btnPlus);
        }
        
        private void updateTable() {
            if (table != null && editingRow >= 0) {
                SwingUtilities.invokeLater(() -> {
                    table.setValueAt(currentValue, editingRow, 4);
                });
            }
        }
        
        @Override
        public Object getCellEditorValue() {
            return currentValue;
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            this.table = table;
            this.editingRow = row;
            this.currentValue = value != null ? (Integer) value : 0;
            txtQuantity.setText(String.valueOf(currentValue));
            
            SwingUtilities.invokeLater(() -> txtQuantity.requestFocusInWindow());
            
            return panel;
        }
    }

    class QuantityAdjustRenderer extends JPanel implements TableCellRenderer {
        private JButton btnMinus;
        private JLabel lblQty;
        private JButton btnPlus;
        
        public QuantityAdjustRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
            setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnMinus.setPreferredSize(new Dimension(40, 30));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            
            lblQty = new JLabel("0");
            lblQty.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblQty.setPreferredSize(new Dimension(60, 30));
            lblQty.setHorizontalAlignment(SwingConstants.CENTER);
            lblQty.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            lblQty.setBackground(CLAY_ELEVATED);
            lblQty.setForeground(CLAY_TEXT);
            lblQty.setOpaque(true);
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnPlus.setPreferredSize(new Dimension(40, 30));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            
            add(btnMinus);
            add(lblQty);
            add(btnPlus);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            lblQty.setText(String.valueOf(value));
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                lblQty.setBackground(table.getSelectionBackground());
            } else {
                setBackground(CLAY_SURFACE);
                lblQty.setBackground(CLAY_ELEVATED);
            }
            return this;
        }
    }

    class QuantityAdjustEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JButton btnMinus;
        private JLabel lblQty;
        private JButton btnPlus;
        private int currentQty;
        private List<BulkOrderItem> items;
        private DefaultTableModel model;
        private JTable table;
        private JLabel lblTotal;
        
        public QuantityAdjustEditor(List<BulkOrderItem> items, DefaultTableModel model, JTable table, JLabel lblTotal) {
            this.items = items;
            this.model = model;
            this.table = table;
            this.lblTotal = lblTotal;
            
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
            panel.setBackground(CLAY_SURFACE);
            
            btnMinus = new JButton("-");
            btnMinus.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnMinus.setPreferredSize(new Dimension(40, 30));
            btnMinus.setBackground(CLAY_DANGER);
            btnMinus.setForeground(CLAY_TEXT);
            btnMinus.setFocusPainted(false);
            
            lblQty = new JLabel("0");
            lblQty.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblQty.setPreferredSize(new Dimension(60, 30));
            lblQty.setHorizontalAlignment(SwingConstants.CENTER);
            lblQty.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
            lblQty.setBackground(CLAY_ELEVATED);
            lblQty.setForeground(CLAY_TEXT);
            lblQty.setOpaque(true);
            
            btnPlus = new JButton("+");
            btnPlus.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnPlus.setPreferredSize(new Dimension(40, 30));
            btnPlus.setBackground(CLAY_SUCCESS);
            btnPlus.setForeground(CLAY_TEXT);
            btnPlus.setFocusPainted(false);
            
            btnPlus.addActionListener(e -> {
                currentQty++;
                lblQty.setText(String.valueOf(currentQty));
                updateRow();
            });
            
            btnMinus.addActionListener(e -> {
                if (currentQty > 0) {
                    currentQty--;
                    lblQty.setText(String.valueOf(currentQty));
                    updateRow();
                }
            });
            
            panel.add(btnMinus);
            panel.add(lblQty);
            panel.add(btnPlus);
        }
        
        private void updateRow() {
            int row = table.getEditingRow();
            if (row >= 0 && row < items.size()) {
                BulkOrderItem item = items.get(row);
                if (item != null && item.buyingPrice != null) {
                    item.orderQty = currentQty;
                    
                    BigDecimal subtotal = item.buyingPrice.multiply(BigDecimal.valueOf(currentQty));
                    model.setValueAt(currentQty, row, 3);
                    model.setValueAt("TSh " + subtotal, row, 4);
                    
                    BigDecimal newTotal = BigDecimal.ZERO;
                    for (BulkOrderItem cartItem : items) {
                        if (cartItem != null && cartItem.buyingPrice != null) {
                            newTotal = newTotal.add(cartItem.buyingPrice.multiply(BigDecimal.valueOf(cartItem.orderQty)));
                        }
                    }
                    lblTotal.setText("Total Amount: TSh " + newTotal);
                    
                    SwingUtilities.invokeLater(() -> fireEditingStopped());
                }
            }
        }

        @Override
        public Object getCellEditorValue() {
            return currentQty;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            currentQty = (Integer) value;
            lblQty.setText(String.valueOf(currentQty));
            return panel;
        }
    }

    static class BulkOrderItem {
        String productName;
        String category;
        int availableQty;
        BigDecimal buyingPrice;
        int orderQty;
    }

    private void savePurchaseOrder(String orderType, BigDecimal totalAmount) {
        try {
            StringBuilder orderDetails = new StringBuilder();
            orderDetails.append("=== PURCHASE ORDER ===\n");
            orderDetails.append("Order Type: ").append(orderType).append("\n");
            orderDetails.append("Ordered by: ").append(currentUser).append("\n");
            orderDetails.append("Date: ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("\n");
            orderDetails.append("Total Items: ").append(purchaseCart.size()).append("\n\n");
            orderDetails.append("ITEMS:\n");
            orderDetails.append(String.format("%-30s %-15s %-10s %-15s %-15s\n", 
                "Product", "Vendor", "Qty", "Unit Price", "Subtotal"));
            orderDetails.append("-".repeat(85)).append("\n");
            
            for (CartItem item : purchaseCart) {
                BigDecimal subPrice = item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity));
                orderDetails.append(String.format("%-30s %-15s %-10d TSh %-12s TSh %-12s\n", 
                    item.productName, 
                    item.vendor != null ? item.vendor : "N/A",
                    item.quantity, 
                    item.buyingPrice, 
                    subPrice));
            }
            
            orderDetails.append("-".repeat(85)).append("\n");
            orderDetails.append(String.format("\n%-56s TSh %s\n", "TOTAL AMOUNT:", totalAmount));
            
            String title = "Purchase Order - " + orderType + " - " + LocalDate.now();
            NotificationDatabase.saveNotebook(title, orderDetails.toString(), currentUser);
            
            purchaseCart.clear();
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error saving purchase order: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAutoGeneratedOrder(List<CartItem> autoCart, String budgetInfo, BigDecimal totalAmount) {
        try {
            StringBuilder orderDetails = new StringBuilder();
            orderDetails.append("=== AUTO-GENERATED PURCHASE ORDER ===\n");
            orderDetails.append("Budget: ").append(budgetInfo).append("\n");
            orderDetails.append("Ordered by: ").append(currentUser).append("\n");
            orderDetails.append("Generated: ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
            orderDetails.append("Total Items: ").append(autoCart.size()).append("\n\n");
            orderDetails.append("ITEMS:\n");
            orderDetails.append(String.format("%-30s %-15s %-10s %-15s %-15s\n", 
                "Product", "Vendor", "Qty", "Unit Price", "Subtotal"));
            orderDetails.append("-".repeat(85)).append("\n");
            
            for (CartItem item : autoCart) {
                BigDecimal subPrice = item.buyingPrice.multiply(BigDecimal.valueOf(item.quantity));
                orderDetails.append(String.format("%-30s %-15s %-10d TSh %-12s TSh %-12s\n", 
                    item.productName,
                    item.vendor != null ? item.vendor : "N/A",
                    item.quantity, 
                    item.buyingPrice, 
                    subPrice));
            }
            
            orderDetails.append("-".repeat(85)).append("\n");
            orderDetails.append(String.format("\n%-56s TSh %s\n", "TOTAL AMOUNT:", totalAmount));
            
            String title = "Auto Purchase Order - " + LocalDate.now();
            NotificationDatabase.saveNotebook(title, orderDetails.toString(), currentUser);
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error saving auto-generated order: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProductInfo() {
        try {
            String currentClientId = Database.getCurrentClientId();
            List<Database.Product> products = Database.getAllProducts();
            for (Database.Product product : products) {
                ProductInfo info = new ProductInfo();
                info.name = product.getName();
                info.buyingPrice = product.getCostPrice();
                info.sellingPrice = product.getSellingPrice();
                info.currentStock = product.getStockQuantity();
                info.minStockLevel = product.getMinStockLevel();
                info.vendor = product.getVendor();
                productInfoMap.put(product.getName(), info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ProductInfo> getFastMovingProducts() {
        List<ProductInfo> fastMoving = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor, " +
                 "COUNT(si.id) AS times_sold " +
                 "FROM products p " +
                 "INNER JOIN sale_items si ON p.id = si.product_id " +
                 "INNER JOIN sales s ON si.sale_id = s.id " +
                 "WHERE DATE(s.sale_date) >= DATE('now', '-5 days') " +
                 "AND DATE(s.sale_date) <= DATE('now') " +
                 "GROUP BY p.id, p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor " +
                 "HAVING COUNT(si.id) > 2 " +
                 "ORDER BY times_sold DESC")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ProductInfo info = new ProductInfo();
                info.name = rs.getString("name");
                info.buyingPrice = rs.getBigDecimal("cost_price");
                info.sellingPrice = rs.getBigDecimal("selling_price");
                info.currentStock = rs.getInt("stock_quantity");
                info.minStockLevel = rs.getInt("min_stock_level");
                info.vendor = rs.getString("vendor");
                fastMoving.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fastMoving;
    }

    private List<ProductInfo> getLowStockProducts() {
        List<ProductInfo> lowStock = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT p.name, p.cost_price, p.selling_price, p.stock_quantity, p.min_stock_level, p.vendor " +
                 "FROM products p " +
                 "WHERE p.stock_quantity <= p.min_stock_level " +
                 "ORDER BY p.stock_quantity ASC, (p.min_stock_level - p.stock_quantity) DESC")) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ProductInfo info = new ProductInfo();
                info.name = rs.getString("name");
                info.buyingPrice = rs.getBigDecimal("cost_price");
                info.sellingPrice = rs.getBigDecimal("selling_price");
                info.currentStock = rs.getInt("stock_quantity");
                info.minStockLevel = rs.getInt("min_stock_level");
                info.vendor = rs.getString("vendor");
                lowStock.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lowStock;
    }

    private void loadAllStockData() {
        loadOutOfStockData();
        loadBelowMinData();
        loadFastMovingData();
        loadSlowMovingData();
    }

    private void loadOutOfStockData() {
        DefaultTableModel model = createTableModel();
        model.setColumnIdentifiers(new Object[]{"Product Name", "Category", "Min Level", "Vendor"});

        String currentClientId = Database.getCurrentClientId();
        String sql = """
            SELECT p.name, c.name AS category_name, p.min_stock_level, p.vendor
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.stock_quantity = 0 AND p.client_id = ?
            ORDER BY p.min_stock_level DESC
        """;

        loadTableData(tableOutOfStock, model, sql, currentClientId);
    }

    private void loadBelowMinData() {
        DefaultTableModel model = createTableModel();
        model.setColumnIdentifiers(new Object[]{"Product Name", "Category", "Current Stock", "Min Level", "Deficit"});

        String currentClientId = Database.getCurrentClientId();
        String sql = """
            SELECT p.name, c.name AS category_name, p.stock_quantity, p.min_stock_level,
                   (p.min_stock_level - p.stock_quantity) as deficit
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.stock_quantity > 0 AND p.stock_quantity < p.min_stock_level AND p.client_id = ?
            ORDER BY deficit DESC
        """;

        loadTableData(tableBelowMin, model, sql, currentClientId);
    }

    private void loadFastMovingData() {
        DefaultTableModel model = createTableModel();
        model.setColumnIdentifiers(new Object[]{"Product Name", "Category", "Days Sold", "Total Qty Sold", "Avg/Day"});

        String sql = """
            SELECT 
                p.name, 
                c.name AS category_name,
                COUNT(DISTINCT DATE(s.sale_date)) AS days_sold,
                SUM(si.quantity) AS total_quantity,
                ROUND(CAST(SUM(si.quantity) AS REAL) / COUNT(DISTINCT DATE(s.sale_date)), 1) as avg_per_day,
                COUNT(si.id) AS times_sold
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            INNER JOIN sale_items si ON p.id = si.product_id
            INNER JOIN sales s ON si.sale_id = s.id
            WHERE DATE(s.sale_date) >= DATE('now', '-5 days')
              AND DATE(s.sale_date) <= DATE('now')
              AND p.client_id = ?
            GROUP BY p.id, p.name, c.name
            HAVING COUNT(si.id) > 2
            ORDER BY total_quantity DESC, days_sold DESC
        """;

        String currentClientId = Database.getCurrentClientId();
        loadTableData(tableFastMoving, model, sql, currentClientId);
    }

    private void loadSlowMovingData() {
        DefaultTableModel model = createTableModel();
        model.setColumnIdentifiers(new Object[]{"Product Name", "Category", "Times Sold", "Total Qty", "Stock"});

        String sql = """
            SELECT 
                p.name, 
                c.name AS category_name,
                COALESCE(sale_count.times_sold, 0) AS times_sold,
                COALESCE(sale_count.total_quantity, 0) AS total_quantity,
                p.stock_quantity
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN (
                SELECT 
                    si.product_id,
                    COUNT(si.id) AS times_sold,
                    SUM(si.quantity) AS total_quantity
                FROM sale_items si
                INNER JOIN sales s ON si.sale_id = s.id
                WHERE DATE(s.sale_date) >= DATE('now', '-5 days')
                  AND DATE(s.sale_date) <= DATE('now')
                GROUP BY si.product_id
                HAVING COUNT(si.id) <= 2
            ) AS sale_count ON p.id = sale_count.product_id
            WHERE p.client_id = ?
              AND p.id NOT IN (
                SELECT DISTINCT si2.product_id
                FROM sale_items si2
                INNER JOIN sales s2 ON si2.sale_id = s2.id
                WHERE DATE(s2.sale_date) >= DATE('now', '-5 days')
                  AND DATE(s2.sale_date) <= DATE('now')
                GROUP BY si2.product_id
                HAVING COUNT(si2.id) > 2
            )
            ORDER BY times_sold ASC, total_quantity ASC, p.stock_quantity DESC
        """;

        String currentClientId = Database.getCurrentClientId();
        loadTableData(tableSlowMoving, model, sql, currentClientId);
    }

    private DefaultTableModel createTableModel() {
        return new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void loadTableData(JTable table, DefaultTableModel model, String sql, String clientId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Object[] row = new Object[model.getColumnCount()];
                for (int i = 0; i < model.getColumnCount(); i++) {
                    row[i] = rs.getObject(i + 1);
                }
                model.addRow(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        table.setModel(model);
        table.setDefaultRenderer(Object.class, new StockCellRenderer(new DefaultTableCellRenderer()));
    }

    static class StockCellRenderer extends DefaultTableCellRenderer {
        private final DefaultTableCellRenderer delegate;

        public StockCellRenderer(DefaultTableCellRenderer delegate) {
            this.delegate = delegate;
            delegate.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int row, int column) {
            Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            c.setForeground(CLAY_TEXT);
            
            if (isSelected) {
                c.setBackground(new Color(0, 161, 255, 60));
            } else {
                c.setBackground(row % 2 == 0 ? CLAY_ELEVATED : CLAY_SURFACE);
            }

            return c;
        }
    }

    private void toggleFloatMode() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parent != null) {
            parent.setVisible(true);
            parent.toFront();
        }

        if (isMinimized) {
            // Restore to original size (screen-aware)
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int width = Math.min(1400, (int)(screenSize.width * 0.92));
            int height = Math.min(800, (int)(screenSize.height * 0.88));
            setSize(width, height);
            floatButton.setText("Float");
            getContentPane().remove(minimizedContentPanel);
            getContentPane().add(fullContentPanel, BorderLayout.CENTER);
            isMinimized = false;
        } else {
            setSize(450, 350);
            floatButton.setText("Dock");
            getContentPane().remove(fullContentPanel);
            getContentPane().add(minimizedContentPanel, BorderLayout.CENTER);
            isMinimized = true;
        }
        getContentPane().revalidate();
        getContentPane().repaint();
        setLocationRelativeTo(parent);
    }

    // Claymorphism Gradient Panel
    static class ClaymorphismPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();
            
            // Claymorphism gradient background
            Color color1 = new Color(28, 30, 36);
            Color color2 = new Color(38, 40, 46);
            GradientPaint gp = new GradientPaint(0, 0, color1, w, h, color2);
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, w, h);
            
            // Claymorphism subtle shadow effect
            g2d.setColor(new Color(0, 0, 0, 30));
            g2d.fillRoundRect(5, 5, w - 10, h - 10, 30, 30);
            
            g2d.dispose();
        }
    }

    static class CartItem {
        String productName;
        int quantity;
        BigDecimal buyingPrice;
        String vendor;

        CartItem(String productName, int quantity, BigDecimal buyingPrice, String vendor) {
            this.productName = productName;
            this.quantity = quantity;
            this.buyingPrice = buyingPrice;
            this.vendor = vendor;
        }
    }

    static class ProductInfo {
        String name;
        BigDecimal buyingPrice;
        BigDecimal sellingPrice;
        int currentStock;
        int minStockLevel;
        String vendor;
    }
}
