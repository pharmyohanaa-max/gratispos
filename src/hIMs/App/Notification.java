package hIMs.App;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.ui.FlatBorder;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Timer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;

public class Notification extends JDialog {
   
    private static final long serialVersionUID = 1L;
    
    // Claymorphism colors
    private final Color CLAY_BG = new Color(30, 30, 35);
    private final Color CLAY_CARD = new Color(38, 38, 43);
    private final Color CLAY_BORDER = new Color(50, 50, 55);
    private final Color CLAY_SHADOW = new Color(20, 20, 25);
    private final Color ACCENT_PRIMARY = new Color(98, 114, 255);
    private final Color ACCENT_SUCCESS = new Color(40, 200, 120);
    private final Color ACCENT_WARNING = new Color(255, 170, 50);
    private final Color ACCENT_DANGER = new Color(255, 70, 70);
    private final Color TEXT_PRIMARY = new Color(240, 240, 245);
    private final Color TEXT_SECONDARY = new Color(160, 160, 170);
    
    private JPanel mainContentPanel;
    private CardLayout cardLayout;
    private JPanel sidebar;
    private Map<String, JButton> navButtons;
    private String currentPanel = "overview";
    private Timer refreshTimer;
    
    static {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 10);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Notification(JFrame parent) {
        super(parent, "Notification Dashboard", false);
        setUndecorated(true);
        setBackground(new Color(30, 30, 35));
        
        setupUI();
        startAutoRefresh();
        
        setSize(1300, 850);
        setLocationRelativeTo(parent);
        setVisible(true);
    }
    
    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(CLAY_BG);
        
        // Title bar
        JPanel titleBar = createTitleBar();
        mainPanel.add(titleBar, BorderLayout.NORTH);
        
        // Content area
        JPanel contentArea = new JPanel(new BorderLayout());
        contentArea.setBackground(CLAY_BG);
        contentArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Sidebar
        sidebar = createSidebar();
        contentArea.add(sidebar, BorderLayout.WEST);
        
        // Main content with card layout
        cardLayout = new CardLayout();
        mainContentPanel = new JPanel(cardLayout);
        mainContentPanel.setBackground(CLAY_BG);
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
        
        // Initialize all panels
        mainContentPanel.add(createOverviewPanel(), "overview");
        mainContentPanel.add(createNewItemsPanel(), "new");
        mainContentPanel.add(createReceivedPanel(), "received");
        mainContentPanel.add(createSalesPanel(), "sales");
        mainContentPanel.add(createPurchaseOrdersPanel(), "purchase");
        mainContentPanel.add(createReturnPanel(), "return");
        mainContentPanel.add(createExpirePanel(), "expire");
        mainContentPanel.add(createLowStockPanel(), "level");
        
        contentArea.add(mainContentPanel, BorderLayout.CENTER);
        mainPanel.add(contentArea, BorderLayout.CENTER);
        
        add(mainPanel);
        makeDraggable(titleBar);
    }
    
    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(CLAY_BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        
        JLabel titleLabel = new JLabel("Notification Center");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_PRIMARY);
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setBackground(CLAY_BG);
        
        JButton refreshBtn = createIconButton("⟳", "Refresh");
        refreshBtn.addActionListener(e -> refreshCurrentPanel());
        
        JButton minimizeBtn = createIconButton("─", "Minimize");
        minimizeBtn.addActionListener(e -> setVisible(false));
        
        JButton closeBtn = createIconButton("✕", "Close");
        closeBtn.addActionListener(e -> {
            if (refreshTimer != null) refreshTimer.stop();
            dispose();
        });
        
        rightPanel.add(refreshBtn);
        rightPanel.add(minimizeBtn);
        rightPanel.add(closeBtn);
        
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(rightPanel, BorderLayout.EAST);
        
        return titleBar;
    }
    
    private JButton createIconButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setForeground(TEXT_SECONDARY);
        btn.setBackground(CLAY_BG);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(TEXT_PRIMARY); }
            public void mouseExited(MouseEvent e) { btn.setForeground(TEXT_SECONDARY); }
        });
        return btn;
    }
    
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(CLAY_CARD);
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            BorderFactory.createEmptyBorder(20, 15, 20, 15)
        ));
        
        // Logo
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logoPanel.setBackground(CLAY_CARD);
        logoPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 30, 10));
        
        JLabel logoIcon = new JLabel("📊");
        logoIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        
        JLabel logoText = new JLabel("Dashboard");
        logoText.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logoText.setForeground(TEXT_PRIMARY);
        
        logoPanel.add(logoIcon);
        logoPanel.add(Box.createHorizontalStrut(10));
        logoPanel.add(logoText);
        sidebar.add(logoPanel);
        
        navButtons = new HashMap<>();
        
        String[][] navItems = {
            {"🏠", "Overview", "overview"},
            {"🆕", "New Items", "new"},
            {"📦", "Received", "received"},
            {"💰", "Sales", "sales"},
            {"📋", "Purchase Orders", "purchase"},
            {"🔄", "Returns", "return"},
            {"⏰", "Expiring", "expire"},
            {"⚠️", "Low Stock", "level"}
        };
        
        for (String[] item : navItems) {
            JButton navBtn = createNavButton(item[0], item[1], item[2]);
            navButtons.put(item[2], navBtn);
            sidebar.add(navBtn);
            sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        
        sidebar.add(Box.createVerticalGlue());
        
        // User info
        JPanel userPanel = new JPanel();
        userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));
        userPanel.setBackground(CLAY_CARD);
        userPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10));
        
        JSeparator sep = new JSeparator();
        sep.setForeground(CLAY_BORDER);
        userPanel.add(sep);
        userPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        
        String userEmail = Database.getCurrentUserEmail();
        String userName = userEmail != null ? userEmail.split("@")[0] : "User";
        JLabel userLabel = new JLabel("👤 " + userName);
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userLabel.setForeground(TEXT_SECONDARY);
        userPanel.add(userLabel);
        
        sidebar.add(userPanel);
        
        return sidebar;
    }
    
    private JButton createNavButton(String icon, String text, String action) {
        JButton btn = new JButton(icon + "  " + text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(TEXT_SECONDARY);
        btn.setBackground(CLAY_CARD);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        btn.addActionListener(e -> {
            setActiveNavButton(action);
            cardLayout.show(mainContentPanel, action);
            currentPanel = action;
        });
        
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(50, 50, 58));
                btn.setForeground(TEXT_PRIMARY);
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(CLAY_CARD);
                btn.setForeground(TEXT_SECONDARY);
            }
        });
        
        return btn;
    }
    
    private void setActiveNavButton(String action) {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton btn = entry.getValue();
            if (entry.getKey().equals(action)) {
                btn.setBackground(new Color(98, 114, 255, 40));
                btn.setForeground(ACCENT_PRIMARY);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_PRIMARY),
                    BorderFactory.createEmptyBorder(12, 17, 12, 20)
                ));
            } else {
                btn.setBackground(CLAY_CARD);
                btn.setForeground(TEXT_SECONDARY);
                btn.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
            }
        }
    }
    
    // ==================== OVERVIEW PANEL ====================
    
    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel welcomeLabel = new JLabel("Welcome back, " + getGreetingName());
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcomeLabel.setForeground(TEXT_PRIMARY);
        content.add(welcomeLabel);
        
        JLabel subLabel = new JLabel("Here's what's happening with your business today");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subLabel.setForeground(TEXT_SECONDARY);
        content.add(subLabel);
        content.add(Box.createRigidArea(new Dimension(0, 25)));
        
        // Stats grid
        JPanel statsGrid = new JPanel(new GridLayout(2, 4, 15, 15));
        statsGrid.setBackground(CLAY_BG);
        
        try {
            String today = LocalDate.now().toString();
            BigDecimal totalSales = Database.getTotalSales(today, today);
            BigDecimal totalExpenses = Database.getDailyExpense(today);
            int lowStock = Database.getLowStockProducts().size();
            int expiring = countExpiringItems();
            List<NotificationDatabase.Notification> returns = NotificationDatabase.getAllReturnNotifications();
            long pendingReturns = returns.stream().filter(n -> "PENDING".equals(n.getStatus())).count();
            int totalProducts = Database.getTotalProductCount();
            BigDecimal profit = totalSales.subtract(totalExpenses);
            
            statsGrid.add(createStatCard("💰 Total Sales", formatNumber(totalSales), ACCENT_SUCCESS));
            statsGrid.add(createStatCard("📉 Expenses", formatNumber(totalExpenses), ACCENT_WARNING));
            statsGrid.add(createStatCard("⚠️ Low Stock", String.valueOf(lowStock), ACCENT_DANGER));
            statsGrid.add(createStatCard("⏰ Expiring", String.valueOf(expiring), ACCENT_PRIMARY));
            statsGrid.add(createStatCard("🔄 Pending Returns", String.valueOf(pendingReturns), ACCENT_WARNING));
            statsGrid.add(createStatCard("📦 Products", String.valueOf(totalProducts), ACCENT_PRIMARY));
            statsGrid.add(createStatCard("✅ Completed Returns", String.valueOf(returns.size() - pendingReturns), ACCENT_SUCCESS));
            statsGrid.add(createStatCard("📊 Today's Profit", formatNumber(profit), profit.compareTo(BigDecimal.ZERO) >= 0 ? ACCENT_SUCCESS : ACCENT_DANGER));
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        content.add(statsGrid);
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createStatCard(String title, String value, Color color) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CLAY_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            BorderFactory.createEmptyBorder(18, 20, 18, 20)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setForeground(TEXT_SECONDARY);
        
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(color);
        
        card.add(titleLabel);
        card.add(Box.createRigidArea(new Dimension(0, 8)));
        card.add(valueLabel);
        
        return card;
    }
    
    // ==================== NEW ITEMS PANEL ====================
    
    private JPanel createNewItemsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("🆕 New Items Added Today");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            List<Database.Product> products = Database.getAllProducts();
            LocalDate today = LocalDate.now();
            List<Database.Product> newProducts = new ArrayList<>();
            
            for (Database.Product p : products) {
                if (p.getCreatedDate() != null && p.getCreatedDate().equals(today)) {
                    newProducts.add(p);
                }
            }
            
            if (newProducts.isEmpty()) {
                content.add(createEmptyState("No new items added today", "New products will appear here"));
            } else {
                String[] columns = {"Product Name", "Category", "Stock", "Vendor"};
                DefaultTableModel model = new DefaultTableModel(columns, 0);
                
                for (Database.Product p : newProducts) {
                    String categoryName = getCategoryName(p.getCategoryId());
                    model.addRow(new Object[]{p.getName(), categoryName, p.getStockQuantity(), p.getVendor() != null ? p.getVendor() : "N/A"});
                }
                
                JTable table = createTable();
                table.setModel(model);
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
                scrollPane.setBackground(CLAY_CARD);
                content.add(scrollPane);
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ==================== RECEIVED PANEL ====================
    
    private JPanel createReceivedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("📦 Items Received Today");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            String today = LocalDate.now().toString();
            List<Database.ReceiveEntry> entries = Database.getReceivedEntries(today);
            
            if (entries == null || entries.isEmpty()) {
                content.add(createEmptyState("No items received today", "Received items will appear here"));
            } else {
                String[] columns = {"Product", "Category", "Quantity", "Date"};
                DefaultTableModel model = new DefaultTableModel(columns, 0);
                
                for (Database.ReceiveEntry entry : entries) {
                    model.addRow(new Object[]{entry.product, entry.category, entry.stockQuantity, entry.createdDate != null ? entry.createdDate.toString() : "N/A"});
                }
                
                JTable table = createTable();
                table.setModel(model);
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
                content.add(scrollPane);
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ==================== SALES PANEL ====================
    
    private JPanel createSalesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("💰 Today's Sales Overview");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            String today = LocalDate.now().toString();
            BigDecimal totalSales = Database.getTotalSales(today, today);
            BigDecimal totalDiscounts = Database.getDailyDiscount(today);
            BigDecimal costOfGoods = Database.getCostOfGoods(today);
            BigDecimal profit = totalSales.subtract(costOfGoods);
            
            // Summary cards
            JPanel summaryPanel = new JPanel(new GridLayout(1, 4, 15, 15));
            summaryPanel.setBackground(CLAY_BG);
            summaryPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
            
            summaryPanel.add(createSmallStatCard("Total Sales", formatNumber(totalSales), ACCENT_SUCCESS));
            summaryPanel.add(createSmallStatCard("Discounts", formatNumber(totalDiscounts), ACCENT_WARNING));
            summaryPanel.add(createSmallStatCard("Cost of Goods", formatNumber(costOfGoods), ACCENT_PRIMARY));
            summaryPanel.add(createSmallStatCard("Profit", formatNumber(profit), profit.compareTo(BigDecimal.ZERO) >= 0 ? ACCENT_SUCCESS : ACCENT_DANGER));
            
            content.add(summaryPanel);
            
            // Transactions table
            List<Database.TransactionDetail> transactions = Database.getDailyTransactionsList(today);
            
            if (transactions.isEmpty()) {
                content.add(createEmptyState("No sales recorded today", "Sales will appear here"));
            } else {
                String[] columns = {"Item", "Quantity", "Unit Price", "Total"};
                DefaultTableModel model = new DefaultTableModel(columns, 0);
                
                for (Database.TransactionDetail td : transactions) {
                    BigDecimal total = td.unitPrice.multiply(BigDecimal.valueOf(td.quantity));
                    model.addRow(new Object[]{td.itemName, td.quantity, formatNumber(td.unitPrice), formatNumber(total)});
                }
                
                JTable table = createTable();
                table.setModel(model);
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
                content.add(scrollPane);
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ==================== PURCHASE ORDERS PANEL ====================
    
    private JPanel createPurchaseOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("📋 Purchase Orders");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            List<NotificationDatabase.Notebook> receipts = NotificationDatabase.getAllReceipts();
            List<NotificationDatabase.Notebook> purchaseOrders = new ArrayList<>();
            
            for (NotificationDatabase.Notebook receipt : receipts) {
                String lowerTitle = receipt.getTitle().toLowerCase();
                if (lowerTitle.contains("purchase order") || lowerTitle.contains("auto purchase")) {
                    purchaseOrders.add(receipt);
                }
            }
            
            if (purchaseOrders.isEmpty()) {
                content.add(createEmptyState("No purchase orders found", "Create orders from the inventory screen"));
            } else {
                for (NotificationDatabase.Notebook order : purchaseOrders) {
                    content.add(createOrderCard(order));
                    content.add(Box.createRigidArea(new Dimension(0, 15)));
                }
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createOrderCard(NotificationDatabase.Notebook order) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(CLAY_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        
        JLabel titleLabel = new JLabel(order.getTitle());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(ACCENT_PRIMARY);
        
        JLabel dateLabel = new JLabel("📅 " + order.getCreatedDate().toString());
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dateLabel.setForeground(TEXT_SECONDARY);
        
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CLAY_CARD);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(dateLabel, BorderLayout.EAST);
        
        String[] lines = order.getContent().split("\\n");
        int previewLines = Math.min(3, lines.length);
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < previewLines; i++) {
            preview.append(lines[i]).append("\n");
        }
        if (lines.length > 3) preview.append("...");
        
        JTextArea contentArea = new JTextArea(preview.toString());
        contentArea.setEditable(false);
        contentArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        contentArea.setBackground(new Color(45, 45, 50));
        contentArea.setForeground(TEXT_SECONDARY);
        contentArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton viewBtn = createActionButton("View Details", ACCENT_PRIMARY);
        viewBtn.addActionListener(e -> showOrderDetails(order));
        
        card.add(header, BorderLayout.NORTH);
        card.add(contentArea, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(CLAY_CARD);
        btnPanel.add(viewBtn);
        card.add(btnPanel, BorderLayout.SOUTH);
        
        return card;
    }
    
    private void showOrderDetails(NotificationDatabase.Notebook order) {
        JDialog dialog = new JDialog(this, order.getTitle(), true);
        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JTextArea textArea = new JTextArea(order.getContent());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setBackground(CLAY_CARD);
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
        
        JButton closeBtn = createActionButton("Close", ACCENT_PRIMARY);
        closeBtn.addActionListener(e -> dialog.dispose());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(CLAY_BG);
        buttonPanel.add(closeBtn);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    // ==================== RETURNS PANEL ====================
    
    private JPanel createReturnPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("🔄 Return Requests");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        
        JLabel subtitle = new JLabel("Review and process customer return requests");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(TEXT_SECONDARY);
        content.add(subtitle);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            List<NotificationDatabase.Notification> allReturns = NotificationDatabase.getAllReturnNotifications();
            List<NotificationDatabase.Notification> pending = new ArrayList<>();
            List<NotificationDatabase.Notification> processed = new ArrayList<>();
            
            for (NotificationDatabase.Notification n : allReturns) {
                if ("PENDING".equals(n.getStatus())) {
                    pending.add(n);
                } else {
                    processed.add(n);
                }
            }
            
            if (pending.isEmpty() && processed.isEmpty()) {
                content.add(createEmptyState("No return requests", "Returns will appear here when requested"));
            } else {
                if (!pending.isEmpty()) {
                    JLabel pendingLabel = new JLabel("⚠️ Pending Approval (" + pending.size() + ")");
                    pendingLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
                    pendingLabel.setForeground(ACCENT_WARNING);
                    content.add(pendingLabel);
                    content.add(Box.createRigidArea(new Dimension(0, 15)));
                    
                    for (NotificationDatabase.Notification request : pending) {
                        content.add(createReturnCard(request, true));
                        content.add(Box.createRigidArea(new Dimension(0, 15)));
                    }
                }
                
                if (!processed.isEmpty()) {
                    content.add(Box.createRigidArea(new Dimension(0, 20)));
                    JLabel processedLabel = new JLabel("✅ Processed Returns (" + processed.size() + ")");
                    processedLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
                    processedLabel.setForeground(ACCENT_SUCCESS);
                    content.add(processedLabel);
                    content.add(Box.createRigidArea(new Dimension(0, 15)));
                    
                    for (NotificationDatabase.Notification request : processed) {
                        content.add(createReturnCard(request, false));
                        content.add(Box.createRigidArea(new Dimension(0, 15)));
                    }
                }
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createReturnCard(NotificationDatabase.Notification request, boolean isPending) {
        JPanel card = new JPanel(new BorderLayout(15, 15));
        card.setBackground(CLAY_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isPending ? ACCENT_WARNING : ACCENT_SUCCESS, 1),
            BorderFactory.createEmptyBorder(18, 20, 18, 20)
        ));
        
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(CLAY_CARD);
        
        JLabel productLabel = new JLabel(request.getProductName());
        productLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        productLabel.setForeground(TEXT_PRIMARY);
        
        JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        detailsPanel.setBackground(CLAY_CARD);
        detailsPanel.add(createChip("📦 Qty: " + request.getQuantity()));
        detailsPanel.add(createChip("📝 " + request.getReason()));
        
        JLabel byLabel = new JLabel("Requested by: " + request.getRequestedBy());
        byLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        byLabel.setForeground(TEXT_SECONDARY);
        
        infoPanel.add(productLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        infoPanel.add(detailsPanel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        infoPanel.add(byLabel);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(CLAY_CARD);
        
        if (isPending) {
            JButton approveBtn = createActionButton("Approve", ACCENT_SUCCESS);
            JButton denyBtn = createActionButton("Deny", ACCENT_DANGER);
            
            approveBtn.addActionListener(e -> approveReturn(request));
            denyBtn.addActionListener(e -> denyReturn(request));
            
            buttonPanel.add(approveBtn);
            buttonPanel.add(denyBtn);
        } else {
            String statusText = "APPROVED".equals(request.getStatus()) ? "✓ Approved" : "✗ Denied";
            Color statusColor = "APPROVED".equals(request.getStatus()) ? ACCENT_SUCCESS : ACCENT_DANGER;
            JLabel statusLabel = new JLabel(statusText);
            statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            statusLabel.setForeground(statusColor);
            buttonPanel.add(statusLabel);
        }
        
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(buttonPanel, BorderLayout.EAST);
        
        return card;
    }
    
    private JPanel createChip(String text) {
        JPanel chip = new JPanel();
        chip.setBackground(new Color(50, 50, 58));
        chip.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(TEXT_SECONDARY);
        
        chip.add(label);
        return chip;
    }
    
    private void approveReturn(NotificationDatabase.Notification request) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Approve return for " + request.getProductName() + "?\n" +
            "Quantity: " + request.getQuantity() + " units will be restored to inventory.\n" +
            "Sales total will be reduced by the refund amount.",
            "Confirm Approval",
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                int saleItemId = request.getSaleItemId();
                
                // If saleItemId is not stored, try to find it
                if (saleItemId <= 0) {
                    saleItemId = findSaleItemId(request.getProductId(), request.getSoldDate());
                }
                
                if (saleItemId <= 0) {
                    JOptionPane.showMessageDialog(this,
                        "Could not find the original sale item for this return.\n" +
                        "The sale item may have been already refunded or deleted.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Calculate refund amount
                BigDecimal refundAmount = request.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
                
                // Use the existing Database.processRefund method
                boolean success = Database.processRefund(
                    saleItemId,
                    request.getProductId(),
                    request.getQuantity(),
                    refundAmount,
                    Database.getCurrentUsername()
                );
                
                if (success) {
                    // Update notification status
                    NotificationDatabase.approveReturnNotification(request.getId());
                    
                    JOptionPane.showMessageDialog(this,
                        String.format("Return approved successfully!\n\n" +
                            "Product: %s\n" +
                            "Quantity returned: %d units\n" +
                            "Refund amount: %,.0f",
                            request.getProductName(),
                            request.getQuantity(),
                            refundAmount),
                        "Return Approved",
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    refreshCurrentPanel();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to process refund. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
                
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Database error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Helper method to find sale item ID (fallback)
    private int findSaleItemId(int productId, LocalDate soldDate) throws SQLException {
        String sql = "SELECT si.id FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "WHERE si.product_id = ? AND DATE(s.sale_date) = ? " +
                     "AND s.client_id = ? " +
                     "ORDER BY s.id DESC LIMIT 1";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, soldDate.toString());
            ps.setString(3, Database.getCurrentClientId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }
    
    private void denyReturn(NotificationDatabase.Notification request) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Deny return for " + request.getProductName() + "?",
            "Confirm Denial",
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                NotificationDatabase.rejectReturnNotification(request.getId());
                JOptionPane.showMessageDialog(this, "Return request denied.", "Request Denied", JOptionPane.INFORMATION_MESSAGE);
                refreshCurrentPanel();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    // ==================== EXPIRE PANEL ====================
    
    private JPanel createExpirePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("⏰ Items Expiring Within 30 Days");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            List<Database.Product> products = Database.getAllProducts();
            LocalDate today = LocalDate.now();
            LocalDate thirtyDaysFromNow = today.plusDays(30);
            List<Database.Product> expiringProducts = new ArrayList<>();
            
            for (Database.Product p : products) {
                if (p.getExpireDate() != null && p.getExpireDate().isBefore(thirtyDaysFromNow)) {
                    expiringProducts.add(p);
                }
            }
            
            if (expiringProducts.isEmpty()) {
                content.add(createEmptyState("No items expiring soon", "All products have valid expiry dates"));
            } else {
                String[] columns = {"Product Name", "Expire Date", "Days Left", "Stock", "Status"};
                DefaultTableModel model = new DefaultTableModel(columns, 0);
                
                for (Database.Product p : expiringProducts) {
                    long daysLeft = ChronoUnit.DAYS.between(today, p.getExpireDate());
                    String status;
                    if (daysLeft < 0) {
                        status = "EXPIRED";
                    } else if (daysLeft <= 7) {
                        status = "CRITICAL";
                    } else if (daysLeft <= 14) {
                        status = "WARNING";
                    } else {
                        status = "NOTICE";
                    }
                    model.addRow(new Object[]{p.getName(), p.getExpireDate().toString(), daysLeft + " days", p.getStockQuantity(), status});
                }
                
                JTable table = createTable();
                table.setModel(model);
                
                table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        String status = (String) table.getValueAt(row, 4);
                        if (!isSelected) {
                            if ("EXPIRED".equals(status)) c.setBackground(new Color(239, 68, 68, 30));
                            else if ("CRITICAL".equals(status)) c.setBackground(new Color(245, 158, 11, 30));
                            else if ("WARNING".equals(status)) c.setBackground(new Color(234, 179, 8, 30));
                            else c.setBackground(CLAY_CARD);
                            c.setForeground(TEXT_PRIMARY);
                        }
                        return c;
                    }
                });
                
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
                content.add(scrollPane);
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ==================== LOW STOCK PANEL ====================
    
    private JPanel createLowStockPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CLAY_BG);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CLAY_BG);
        
        JLabel title = new JLabel("⚠️ Low Stock Alert");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        content.add(title);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        
        try {
            List<Database.Product> lowStockProducts = Database.getLowStockProducts();
            
            if (lowStockProducts.isEmpty()) {
                content.add(createEmptyState("No low stock items", "All products are above minimum stock levels"));
            } else {
                String[] columns = {"Product Name", "Current Stock", "Minimum Level", "Status"};
                DefaultTableModel model = new DefaultTableModel(columns, 0);
                
                for (Database.Product p : lowStockProducts) {
                    String status = p.getStockQuantity() <= 0 ? "OUT OF STOCK" : "LOW STOCK";
                    model.addRow(new Object[]{p.getName(), p.getStockQuantity(), p.getMinStockLevel(), status});
                }
                
                JTable table = createTable();
                table.setModel(model);
                
                table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                        if (!isSelected) {
                            String status = (String) table.getValueAt(row, 3);
                            if ("OUT OF STOCK".equals(status)) {
                                c.setBackground(new Color(239, 68, 68, 30));
                            } else {
                                c.setBackground(new Color(245, 158, 11, 30));
                            }
                            c.setForeground(TEXT_PRIMARY);
                        }
                        return c;
                    }
                });
                
                JScrollPane scrollPane = new JScrollPane(table);
                scrollPane.setBorder(BorderFactory.createLineBorder(CLAY_BORDER, 1));
                content.add(scrollPane);
                
                content.add(Box.createRigidArea(new Dimension(0, 20)));
                
                JPanel recPanel = new JPanel();
                recPanel.setLayout(new BoxLayout(recPanel, BoxLayout.Y_AXIS));
                recPanel.setBackground(CLAY_CARD);
                recPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CLAY_BORDER, 1),
                    BorderFactory.createEmptyBorder(15, 15, 15, 15)
                ));
                
                JLabel recTitle = new JLabel("📋 Restock Recommendations");
                recTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
                recTitle.setForeground(TEXT_PRIMARY);
                recPanel.add(recTitle);
                recPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                
                for (Database.Product p : lowStockProducts) {
                    int needed = p.getMinStockLevel() - p.getStockQuantity() + 10;
                    if (needed > 0) {
                        JLabel recLabel = new JLabel("• " + p.getName() + ": Order " + needed + " units");
                        recLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                        recLabel.setForeground(TEXT_SECONDARY);
                        recPanel.add(recLabel);
                    }
                }
                
                content.add(recPanel);
            }
        } catch (SQLException e) {
            content.add(createErrorPanel("Error: " + e.getMessage()));
        }
        
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBackground(CLAY_BG);
        scrollPane.getViewport().setBackground(CLAY_BG);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ==================== HELPER METHODS ====================
    
    private JTable createTable() {
        JTable table = new JTable();
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(40);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(CLAY_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setForeground(TEXT_PRIMARY);
        table.getTableHeader().setBackground(new Color(45, 45, 50));
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(CLAY_BORDER));
        table.setSelectionBackground(new Color(98, 114, 255, 50));
        return table;
    }
    
    private JPanel createSmallStatCard(String title, String value, Color color) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CLAY_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CLAY_BORDER, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titleLabel.setForeground(TEXT_SECONDARY);
        
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valueLabel.setForeground(color);
        
        card.add(titleLabel);
        card.add(Box.createRigidArea(new Dimension(0, 5)));
        card.add(valueLabel);
        
        return card;
    }
    
    private JButton createActionButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(color);
        btn.setBackground(CLAY_CARD);
        btn.setBorder(BorderFactory.createLineBorder(color, 1));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 32));
        
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(color);
                btn.setForeground(Color.WHITE);
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(CLAY_CARD);
                btn.setForeground(color);
            }
        });
        
        return btn;
    }
    
    private JPanel createEmptyState(String title, String message) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CLAY_CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(60, 40, 60, 40));
        
        JLabel iconLabel = new JLabel("📭");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        msgLabel.setForeground(TEXT_SECONDARY);
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        panel.add(iconLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));
        panel.add(titleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(msgLabel);
        
        return panel;
    }
    
    private JPanel createErrorPanel(String message) {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(239, 68, 68, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel errorLabel = new JLabel("⚠️ " + message);
        errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        errorLabel.setForeground(ACCENT_DANGER);
        panel.add(errorLabel);
        
        return panel;
    }
    
    private String getGreetingName() {
        String email = Database.getCurrentUserEmail();
        if (email != null && email.contains("@")) {
            return email.split("@")[0];
        }
        return "User";
    }
    
    private String getCategoryName(int categoryId) {
        try {
            List<Database.Category> categories = Database.getAllCategoryObjects();
            for (Database.Category cat : categories) {
                if (cat.getId() == categoryId) return cat.getName();
            }
        } catch (SQLException e) {}
        return "Uncategorized";
    }
    
    private int countExpiringItems() {
        int count = 0;
        try {
            List<Database.Product> products = Database.getAllProducts();
            LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
            for (Database.Product p : products) {
                if (p.getExpireDate() != null && p.getExpireDate().isBefore(thirtyDaysFromNow)) {
                    count++;
                }
            }
        } catch (SQLException e) {}
        return count;
    }
    
    private String formatNumber(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%,.0f", value);
    }
    
    private void makeDraggable(JPanel component) {
        final Point[] dragStart = {null};
        component.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { dragStart[0] = e.getPoint(); }
            public void mouseReleased(MouseEvent e) { dragStart[0] = null; }
        });
        component.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] != null) {
                    Point current = getLocation();
                    setLocation(current.x + e.getX() - dragStart[0].x, 
                               current.y + e.getY() - dragStart[0].y);
                }
            }
        });
    }
    
    private void startAutoRefresh() {
        refreshTimer = new Timer(60000, e -> refreshCurrentPanel());
        refreshTimer.start();
    }
    
    private void refreshCurrentPanel() {
        SwingUtilities.invokeLater(() -> {
            mainContentPanel.removeAll();
            mainContentPanel.add(createOverviewPanel(), "overview");
            mainContentPanel.add(createNewItemsPanel(), "new");
            mainContentPanel.add(createReceivedPanel(), "received");
            mainContentPanel.add(createSalesPanel(), "sales");
            mainContentPanel.add(createPurchaseOrdersPanel(), "purchase");
            mainContentPanel.add(createReturnPanel(), "return");
            mainContentPanel.add(createExpirePanel(), "expire");
            mainContentPanel.add(createLowStockPanel(), "level");
            cardLayout.show(mainContentPanel, currentPanel);
            mainContentPanel.revalidate();
            mainContentPanel.repaint();
        });
    }
}
