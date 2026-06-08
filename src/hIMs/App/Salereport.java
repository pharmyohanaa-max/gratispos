package hIMs.App;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.Timer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import com.formdev.flatlaf.FlatLightLaf;
import com.toedter.calendar.JDateChooser;

public class Salereport extends JDialog {
    
    // Color Palette - Glass Morphism Style
    private static final Color PRIMARY = new Color(59, 130, 246);
    private static final Color PRIMARY_DARK = new Color(37, 99, 235);
    private static final Color SECONDARY = new Color(139, 92, 246);
    private static final Color SUCCESS = new Color(16, 185, 129);
    private static final Color WARNING = new Color(245, 158, 11);
    private static final Color DANGER = new Color(239, 68, 68);
    private static final Color INFO = new Color(6, 182, 212);
    private static final Color DARK = new Color(30, 41, 59);
    private static final Color GLASS_BG = new Color(255, 255, 255, 230);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 100);
    private static final Color BACKGROUND = new Color(240, 242, 245);
    private static final Color TEXT_SECONDARY = new Color(107, 114, 128);
    
    // Formatters
    private static final DecimalFormat MONEY = new DecimalFormat("TZS #,##0.00");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.0%");
    private static final DecimalFormat NUMBER = new DecimalFormat("#,###");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    // Data Models
    private SalesAnalytics analytics = new SalesAnalytics();
    private List<ProductSalesData> productSalesData = new ArrayList<>();
    private List<HourlySalesData> hourlyData = new ArrayList<>();
    private List<DailySalesData> dailyData = new ArrayList<>();
    private List<SalesRepData> repData = new ArrayList<>();
    private List<VendorSalesData> vendorData = new ArrayList<>();
    
    // UI Components
    private JTabbedPane tabbedPane;
    private JLabel lblGrossSale, lblTotalDiscount, lblTotalUsers;
    private JTable repLeaderboardTable, vendorTable, productVelocityTable;
    private JComboBox<String> periodCombo, categoryCombo, repFilterCombo;
    private JTextField txtItemSearch;
    private JSpinner discountSpinner;
    private JButton btnApplyDiscount;
    private JLabel lblStockWarning;
    private JPanel trendsPanel;
    private Timer refreshTimer;
    
    // Data
    private String currentUser;
    private LocalDate startDate, endDate;
    private BigDecimal annualQuota = new BigDecimal("100000000");
    
    // Parent frame reference
    private JFrame parentFrame;
    
    // Inner Classes for Data Structures
    static class SalesAnalytics {
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal profitMargin = BigDecimal.ZERO;
        int totalTransactions = 0;
        int totalItemsSold = 0;
        BigDecimal averageTransactionValue = BigDecimal.ZERO;
        int totalUsers = 0;
    }
    
    static class ProductSalesData {
        int productId;
        String name;
        String category;
        String vendor;
        int quantitySold;
        BigDecimal revenue;
        BigDecimal profit;
        double velocity;
        int currentStock;
        int reorderLevel;
    }
    
    static class HourlySalesData {
        int hour;
        BigDecimal revenue;
        int transactions;
        String timeRange;
        
        HourlySalesData(int hour, BigDecimal revenue, int transactions, String timeRange) {
            this.hour = hour;
            this.revenue = revenue;
            this.transactions = transactions;
            this.timeRange = timeRange;
        }
    }
    
    static class DailySalesData {
        LocalDate date;
        String dayName;
        BigDecimal revenue;
        int transactions;
        BigDecimal profit;
    }
    
    static class SalesRepData {
        String username;
        BigDecimal totalSales;
        int transactions;
        BigDecimal averageSale;
        double growth;
        int rank;
    }
    
    static class VendorSalesData {
        String vendorName;
        BigDecimal revenue;
        int unitsSold;
        BigDecimal profit;
        double velocity;
    }
    
    // Constructor
    public Salereport(JFrame parent, String username) {
        super(parent, "Sale Report Panel", true);
        this.parentFrame = parent;
        this.currentUser = username != null ? username : "Admin";
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        initUI();
        loadData();
        startAutoRefresh();
        
        setSize(1600, 1000);
        setLocationRelativeTo(parent);
    }
    
    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND);
        
        add(createHeader(), BorderLayout.NORTH);
        
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabbedPane.setBackground(BACKGROUND);
        
        tabbedPane.addTab("📊 Overview", createOverviewPanel());
        tabbedPane.addTab("🏆 Sales Rep Leaderboard", createLeaderboardPanel());
        tabbedPane.addTab("📈 Trends & Analytics", createTrendsPanel());
        tabbedPane.addTab("🏭 Vendor Performance", createVendorPanel());
        tabbedPane.addTab("📋 Sales View", createSalesViewPanel());
        tabbedPane.addTab("🔍 Product Search", createSearchPanel());
        tabbedPane.addTab("💰 Discount Manager", createDiscountPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, PRIMARY, getWidth(), 0, SECONDARY);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(0, 100));
        header.setBorder(new EmptyBorder(15, 25, 15, 25));
        
        JLabel titleLabel = new JLabel("📊  Sales Report Panel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        
        JPanel periodPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        periodPanel.setOpaque(false);
        
        String[] periods = {"Today", "This Week", "This Month", "Last 30 Days", "This Quarter", "This Year"};
        periodCombo = new JComboBox<>(periods);
        periodCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        periodCombo.setBackground(new Color(255, 255, 255, 200));
        periodCombo.setPreferredSize(new Dimension(150, 35));
        periodCombo.addActionListener(e -> loadData());
        
        JButton btnRefresh = new JButton("🔄 Refresh");
        btnRefresh.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRefresh.setBackground(new Color(255, 255, 255, 200));
        btnRefresh.setForeground(DARK);
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        btnRefresh.addActionListener(e -> loadData());
        
        JButton btnClose = new JButton("✗ Close");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnClose.setBackground(new Color(255, 255, 255, 200));
        btnClose.setForeground(DANGER);
        btnClose.setFocusPainted(false);
        btnClose.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        btnClose.addActionListener(e -> dispose());
        
        JLabel lblUser = new JLabel("👤 " + currentUser);
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblUser.setForeground(Color.WHITE);
        
        periodPanel.add(new JLabel("📅 Period:"));
        periodPanel.add(periodCombo);
        periodPanel.add(btnRefresh);
        periodPanel.add(btnClose);
        periodPanel.add(Box.createHorizontalStrut(20));
        periodPanel.add(lblUser);
        
        header.add(titleLabel, BorderLayout.WEST);
        header.add(periodPanel, BorderLayout.EAST);
        
        return header;
    }
    
    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Only 3 KPI Cards
        JPanel kpiPanel = new JPanel(new GridLayout(1, 3, 15, 15));
        kpiPanel.setBackground(BACKGROUND);
        
        lblGrossSale = createKPICardValue("💰 Total Gross Sale", "TZS 0", SUCCESS);
        lblTotalDiscount = createKPICardValue("🎯 Total Discount", "TZS 0", WARNING);
        lblTotalUsers = createKPICardValue("👥 Total User Entries", "0", PRIMARY);
        
        kpiPanel.add(createGlassKPICardWithLabel(lblGrossSale, "💰 Total Gross Sale", SUCCESS));
        kpiPanel.add(createGlassKPICardWithLabel(lblTotalDiscount, "🎯 Total Discount", WARNING));
        kpiPanel.add(createGlassKPICardWithLabel(lblTotalUsers, "👥 Total User Entries", PRIMARY));
        
        panel.add(kpiPanel, BorderLayout.NORTH);
        
        // Inventory Integration Panel
        JPanel inventoryPanel = createGlassPanel("📊 Inventory Integration - Stock vs Sales Trends");
        inventoryPanel.setLayout(new BorderLayout());
        inventoryPanel.setPreferredSize(new Dimension(0, 250));
        
        JTable stockTable = createStockIntegrationTable();
        JScrollPane stockScroll = new JScrollPane(stockTable);
        inventoryPanel.add(stockScroll, BorderLayout.CENTER);
        
        panel.add(inventoryPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JLabel createKPICardValue(String title, String value, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", Font.BOLD, 24));
        label.setForeground(color);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
    
    private JPanel createGlassKPICardWithLabel(JLabel valueLabel, String title, Color accent) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(8, 5));
        card.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(TEXT_SECONDARY);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(valueLabel);
        
        card.add(contentPanel, BorderLayout.CENTER);
        
        return card;
    }
    
    private JTable createStockIntegrationTable() {
        String[] columns = {"Product", "Current Stock", "Min Stock Level", "30-Day Sales", "Projected Days Left", "Risk Level"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(35);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(PRIMARY);
        table.getTableHeader().setForeground(Color.WHITE);
        
        table.getColumnModel().getColumn(5).setCellRenderer(new RiskRenderer());
        
        return table;
    }
    
    private JPanel createLeaderboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        filterPanel.setBackground(BACKGROUND);
        filterPanel.add(new JLabel("Filter by Period:"));
        
        String[] repPeriods = {"All Time", "This Month", "This Week", "Today"};
        repFilterCombo = new JComboBox<>(repPeriods);
        repFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        repFilterCombo.setPreferredSize(new Dimension(150, 30));
        repFilterCombo.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    loadRepData();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        });
        
        filterPanel.add(repFilterCombo);
        filterPanel.add(Box.createHorizontalStrut(20));
        filterPanel.add(new JLabel("🏆 Top Performers will be highlighted"));
        
        panel.add(filterPanel, BorderLayout.NORTH);
        
        String[] columns = {"Rank", "👤 Sales Rep", "💰 Total Sales", "🛒 Transactions", "💵 Avg Sale", "📈 Growth", "🏅 Badge"};
        repLeaderboardTable = createStyledTable(columns);
        repLeaderboardTable.setRowHeight(45);
        
        repLeaderboardTable.getColumnModel().getColumn(0).setCellRenderer(new RankRenderer());
        repLeaderboardTable.getColumnModel().getColumn(6).setCellRenderer(new BadgeRenderer());
        
        JScrollPane scrollPane = new JScrollPane(repLeaderboardTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createTrendsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        trendsPanel = new JPanel();
        trendsPanel.setLayout(new BoxLayout(trendsPanel, BoxLayout.Y_AXIS));
        trendsPanel.setBackground(BACKGROUND);
        
        JScrollPane scrollPane = new JScrollPane(trendsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void updateTrendsPanel() {
        trendsPanel.removeAll();
        
        // Daily Sales Summary
        JPanel dailySummaryPanel = createGlassPanel("📅 Daily Sales Summary");
        dailySummaryPanel.setLayout(new GridLayout(0, 4, 10, 10));
        
        for (DailySalesData d : dailyData) {
            JPanel dayCard = new JPanel();
            dayCard.setLayout(new BoxLayout(dayCard, BoxLayout.Y_AXIS));
            dayCard.setBackground(new Color(255, 255, 255, 200));
            dayCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            
            JLabel dayLabel = new JLabel(d.dayName + " - " + d.date.format(DateTimeFormatter.ofPattern("MMM dd")));
            dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            dayLabel.setForeground(PRIMARY);
            
            JLabel revenueLabel = new JLabel("Revenue: " + MONEY.format(d.revenue));
            revenueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            
            JLabel transLabel = new JLabel("Transactions: " + d.transactions);
            transLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            
            JLabel profitLabel = new JLabel("Profit: " + MONEY.format(d.profit));
            profitLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            profitLabel.setForeground(d.profit.compareTo(BigDecimal.ZERO) >= 0 ? SUCCESS : DANGER);
            
            dayCard.add(dayLabel);
            dayCard.add(Box.createVerticalStrut(5));
            dayCard.add(revenueLabel);
            dayCard.add(transLabel);
            dayCard.add(profitLabel);
            
            dailySummaryPanel.add(dayCard);
        }
        
        trendsPanel.add(dailySummaryPanel);
        trendsPanel.add(Box.createVerticalStrut(15));
        
        // Weekday Performance
        JPanel weekdayPanel = createGlassPanel("📊 Day-of-Week Performance");
        weekdayPanel.setLayout(new BorderLayout());
        weekdayPanel.add(new WeekdayChartComponent(dailyData), BorderLayout.CENTER);
        trendsPanel.add(weekdayPanel);
        trendsPanel.add(Box.createVerticalStrut(15));
        
        // Hourly Sales Trends
        JPanel hourlyPanel = createGlassPanel("⏰ Hourly Sales Trends");
        hourlyPanel.setLayout(new BorderLayout());
        hourlyPanel.add(new HourlyChartComponent(hourlyData), BorderLayout.CENTER);
        trendsPanel.add(hourlyPanel);
        
        trendsPanel.revalidate();
        trendsPanel.repaint();
    }
    
    private JPanel createVendorPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        String[] columns = {"Vendor", "Revenue", "Units Sold", "Profit", "Velocity (units/day)", "Market Share", "Trend"};
        vendorTable = createStyledTable(columns);
        
        JScrollPane scrollPane = new JScrollPane(vendorTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createSalesViewPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JButton btnOpenSalesView = new JButton("📋 Open Sales View");
        btnOpenSalesView.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnOpenSalesView.setBackground(PRIMARY);
        btnOpenSalesView.setForeground(Color.WHITE);
        btnOpenSalesView.setFocusPainted(false);
        btnOpenSalesView.setPreferredSize(new Dimension(200, 50));
        btnOpenSalesView.addActionListener(e -> openSalesViewDialog());
        
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(BACKGROUND);
        centerPanel.add(btnOpenSalesView);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void openSalesViewDialog() {
        JDialog salesDialog = new JDialog(this, "Sales View", true);
        salesDialog.setSize(1200, 700);
        salesDialog.setLocationRelativeTo(this);
        salesDialog.setLayout(new BorderLayout(10, 10));
        
        // Date Range Panel
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        datePanel.setBackground(Color.WHITE);
        datePanel.setBorder(BorderFactory.createTitledBorder("Select Date Range"));
        
        JDateChooser fromDateChooser = new JDateChooser();
        fromDateChooser.setDateFormatString("yyyy-MM-dd");
        fromDateChooser.setDate(java.sql.Date.valueOf(LocalDate.now().minusDays(30)));
        fromDateChooser.setPreferredSize(new Dimension(150, 35));
        
        JDateChooser toDateChooser = new JDateChooser();
        toDateChooser.setDateFormatString("yyyy-MM-dd");
        toDateChooser.setDate(new java.util.Date());
        toDateChooser.setPreferredSize(new Dimension(150, 35));
        
        JButton btnLoad = new JButton("🔍 Load Sales");
        btnLoad.setBackground(PRIMARY);
        btnLoad.setForeground(Color.WHITE);
        btnLoad.setFocusPainted(false);
        
        datePanel.add(new JLabel("From:"));
        datePanel.add(fromDateChooser);
        datePanel.add(new JLabel("To:"));
        datePanel.add(toDateChooser);
        datePanel.add(btnLoad);
        
        salesDialog.add(datePanel, BorderLayout.NORTH);
        
        // Table
        String[] columns = {"Product Name", "Unit Price (TZS)", "Quantity Sold", "Sub Total (TZS)"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        JTable salesTable = new JTable(model);
        salesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        salesTable.setRowHeight(35);
        salesTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        salesTable.getTableHeader().setBackground(PRIMARY);
        salesTable.getTableHeader().setForeground(Color.WHITE);
        
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        salesTable.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        salesTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        salesTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        
        JScrollPane scrollPane = new JScrollPane(salesTable);
        salesDialog.add(scrollPane, BorderLayout.CENTER);
        
        // Footer Panel
        JPanel footerPanel = new JPanel(new GridLayout(1, 3, 15, 15));
        footerPanel.setBackground(new Color(30, 41, 59));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel lblTotalEntries = new JLabel("Total Entries: 0");
        lblTotalEntries.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTotalEntries.setForeground(Color.WHITE);
        
        JLabel lblGrossTotal = new JLabel("Gross Total: TZS 0");
        lblGrossTotal.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblGrossTotal.setForeground(Color.WHITE);
        
        JLabel lblNetTotal = new JLabel("Net Total: TZS 0");
        lblNetTotal.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblNetTotal.setForeground(Color.WHITE);
        
        footerPanel.add(lblTotalEntries);
        footerPanel.add(lblGrossTotal);
        footerPanel.add(lblNetTotal);
        
        salesDialog.add(footerPanel, BorderLayout.SOUTH);
        
        // Load data action
        btnLoad.addActionListener(e -> {
            model.setRowCount(0);
            
            java.util.Date fromDate = fromDateChooser.getDate();
            java.util.Date toDate = toDateChooser.getDate();
            
            if (fromDate == null || toDate == null) {
                JOptionPane.showMessageDialog(salesDialog, "Please select both dates", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            LocalDate start = fromDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate end = toDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            
            if (start.isAfter(end)) {
                JOptionPane.showMessageDialog(salesDialog, "Start date cannot be after end date", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            loadSalesViewData(model, lblTotalEntries, lblGrossTotal, lblNetTotal, start, end);
        });
        
        // Initial load
        btnLoad.doClick();
        
        salesDialog.setVisible(true);
    }
    
    private void loadSalesViewData(DefaultTableModel model, JLabel lblTotalEntries, JLabel lblGrossTotal, JLabel lblNetTotal, LocalDate start, LocalDate end) {
        String currentClientId = Database.getCurrentClientId();
        String startStr = start.toString();
        String endStr = end.toString();
        
        String sql = """
            SELECT 
                p.name as product_name,
                p.selling_price as unit_price,
                SUM(si.quantity) as quantity_sold,
                SUM(si.total_price) as sub_total,
                COALESCE(SUM(s.discount), 0) as total_discount
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.id
            JOIN products p ON si.product_id = p.id
            WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?
            GROUP BY p.id, p.name, p.selling_price
            ORDER BY quantity_sold DESC
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startStr);
            stmt.setString(2, endStr);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            BigDecimal grossTotal = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            int rowCount = 0;
            
            while (rs.next()) {
                String productName = rs.getString("product_name");
                BigDecimal unitPrice = rs.getBigDecimal("unit_price");
                int quantity = rs.getInt("quantity_sold");
                BigDecimal subTotal = rs.getBigDecimal("sub_total");
                BigDecimal discount = rs.getBigDecimal("total_discount");
                
                grossTotal = grossTotal.add(subTotal);
                totalDiscount = totalDiscount.add(discount);
                rowCount++;
                
                model.addRow(new Object[]{
                    productName,
                    MONEY.format(unitPrice),
                    quantity,
                    MONEY.format(subTotal)
                });
            }
            
            BigDecimal netTotal = grossTotal.subtract(totalDiscount);
            
            lblTotalEntries.setText("Total Entries: " + rowCount);
            lblGrossTotal.setText("Gross Total: " + MONEY.format(grossTotal));
            lblNetTotal.setText("Net Total: " + MONEY.format(netTotal));
            
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error loading sales data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Search Bar
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        searchPanel.setBackground(BACKGROUND);
        searchPanel.add(new JLabel("🔍 Search Item:"));
        
        txtItemSearch = new JTextField(30);
        txtItemSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtItemSearch.setPreferredSize(new Dimension(300, 38));
        
        searchPanel.add(txtItemSearch);
        
        panel.add(searchPanel, BorderLayout.NORTH);
        
        // Results Table
        String[] columns = {"Product ID", "Product Name", "Category", "Vendor", "Price", "Stock", "Monthly Sales", "Velocity"};
        JTable searchResultsTable = createStyledTable(columns);
        searchResultsTable.setRowHeight(40);
        
        // Add double-click listener
        searchResultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = searchResultsTable.getSelectedRow();
                    if (row != -1) {
                        int productId = (Integer) searchResultsTable.getValueAt(row, 0);
                        String productName = (String) searchResultsTable.getValueAt(row, 1);
                        showProductDetailsDialog(productId, productName);
                    }
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(searchResultsTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Real-time search
        txtItemSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(searchResultsTable, txtItemSearch.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(searchResultsTable, txtItemSearch.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(searchResultsTable, txtItemSearch.getText()); }
        });
        
        return panel;
    }
    
    private void showProductDetailsDialog(int productId, String productName) {
        JDialog detailDialog = new JDialog(this, "Product Details: " + productName, true);
        detailDialog.setSize(500, 450);
        detailDialog.setLocationRelativeTo(this);
        detailDialog.setLayout(new BorderLayout(10, 10));
        
        JPanel detailPanel = new JPanel(new GridBagLayout());
        detailPanel.setBackground(Color.WHITE);
        detailPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        
        try {
            String currentClientId = Database.getCurrentClientId();
            
            // Get product details
            String productSql = """
                SELECT p.name, c.name as category, p.vendor, p.selling_price, p.stock_quantity,
                       COALESCE(SUM(si.quantity), 0) as monthly_sales
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN sale_items si ON p.id = si.product_id
                LEFT JOIN sales s ON si.sale_id = s.id AND DATE(s.sale_date) >= DATE('now', '-30 days')
                WHERE p.id = ? AND p.client_id = ?
                GROUP BY p.id
            """;
            
            PreparedStatement stmt = Database.getConnection().prepareStatement(productSql);
            stmt.setInt(1, productId);
            stmt.setString(2, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String name = rs.getString("name");
                String category = rs.getString("category");
                String vendor = rs.getString("vendor");
                BigDecimal price = rs.getBigDecimal("selling_price");
                int stock = rs.getInt("stock_quantity");
                int monthlySales = rs.getInt("monthly_sales");
                double velocity = monthlySales / 30.0;
                
                addDetailRow(detailPanel, gbc, 0, "🏷️ Product Name:", name != null ? name : "N/A");
                addDetailRow(detailPanel, gbc, 1, "📂 Category:", category != null ? category : "Uncategorized");
                addDetailRow(detailPanel, gbc, 2, "🏭 Vendor:", vendor != null ? vendor : "N/A");
                addDetailRow(detailPanel, gbc, 3, "💰 Price:", MONEY.format(price));
                addDetailRow(detailPanel, gbc, 4, "📦 Available Stock:", String.valueOf(stock));
                addDetailRow(detailPanel, gbc, 5, "📊 Monthly Sales:", String.valueOf(monthlySales) + " units");
                addDetailRow(detailPanel, gbc, 6, "⚡ Velocity:", String.format("%.1f units/day", velocity));
                
                // Add stock status
                String stockStatus;
                Color statusColor;
                if (stock <= 10) {
                    stockStatus = "🔴 CRITICAL - Reorder Now!";
                    statusColor = DANGER;
                } else if (stock <= 50) {
                    stockStatus = "🟡 LOW STOCK - Monitor";
                    statusColor = WARNING;
                } else {
                    stockStatus = "🟢 GOOD STOCK";
                    statusColor = SUCCESS;
                }
                addDetailRow(detailPanel, gbc, 7, "⚠️ Stock Status:", stockStatus, statusColor);
            }
            stmt.close();
            
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(detailDialog, "Error loading product details: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        
        JScrollPane scrollPane = new JScrollPane(detailPanel);
        detailDialog.add(scrollPane, BorderLayout.CENTER);
        
        JButton btnClose = new JButton("Close");
        btnClose.setBackground(PRIMARY);
        btnClose.setForeground(Color.WHITE);
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(e -> detailDialog.dispose());
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(btnClose);
        detailDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        detailDialog.setVisible(true);
    }
    
    private void addDetailRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        addDetailRow(panel, gbc, row, label, value, DARK);
    }
    
    private void addDetailRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value, Color valueColor) {
        gbc.gridx = 0;
        gbc.gridy = row;
        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(lblLabel, gbc);
        
        gbc.gridx = 1;
        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblValue.setForeground(valueColor);
        panel.add(lblValue, gbc);
    }
    
    private JPanel createDiscountPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JPanel discountControlPanel = createGlassPanel("💰 Apply Discount to Selected Items");
        discountControlPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        discountControlPanel.add(new JLabel("Discount Type:"), gbc);
        
        JComboBox<String> discountTypeCombo = new JComboBox<>(new String[]{"Percentage (%)", "Fixed Amount (TZS)"});
        discountTypeCombo.setPreferredSize(new Dimension(180, 35));
        gbc.gridx = 1;
        discountControlPanel.add(discountTypeCombo, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0;
        discountControlPanel.add(new JLabel("Value:"), gbc);
        
        discountSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 1.0));
        discountSpinner.setPreferredSize(new Dimension(120, 35));
        gbc.gridx = 3;
        discountControlPanel.add(discountSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        discountControlPanel.add(new JLabel("Apply to Product:"), gbc);
        
        JComboBox<String> productCombo = new JComboBox<>();
        loadProducts(productCombo);
        productCombo.setPreferredSize(new Dimension(250, 35));
        gbc.gridx = 1; gbc.gridwidth = 3;
        discountControlPanel.add(productCombo, gbc);
        
        btnApplyDiscount = new JButton("💾 Apply Discount");
        btnApplyDiscount.setBackground(SUCCESS);
        btnApplyDiscount.setForeground(Color.WHITE);
        btnApplyDiscount.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnApplyDiscount.setPreferredSize(new Dimension(200, 45));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        discountControlPanel.add(btnApplyDiscount, gbc);
        
        JTextArea discountResultArea = new JTextArea();
        discountResultArea.setEditable(false);
        discountResultArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        discountResultArea.setBackground(new Color(240, 250, 240));
        
        JScrollPane resultScroll = new JScrollPane(discountResultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Discount Application Results"));
        
        btnApplyDiscount.addActionListener(e -> applyDiscount(
            (String) productCombo.getSelectedItem(),
            (Double) discountSpinner.getValue(),
            discountTypeCombo.getSelectedIndex() == 0 ? "PERCENT" : "FIXED",
            discountResultArea
        ));
        
        panel.add(discountControlPanel, BorderLayout.NORTH);
        panel.add(resultScroll, BorderLayout.CENTER);
        
        return panel;
    }
    
    // ============ DATA LOADING METHODS ============
    
    private void loadData() {
        setPeriodDates();
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    loadSalesAnalytics();
                    loadHourlySales();
                    loadDailySales();
                    loadRepData();
                    loadVendorData();
                    loadProductVelocity();
                    loadInventoryValuation();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return null;
            }
            
            @Override
            protected void done() {
                updateUI();
            }
        };
        worker.execute();
    }
    
    private void setPeriodDates() {
        LocalDate today = LocalDate.now();
        String period = (String) periodCombo.getSelectedItem();
        
        switch (period) {
            case "Today":
                startDate = today;
                endDate = today;
                break;
            case "This Week":
                startDate = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                endDate = today;
                break;
            case "This Month":
                startDate = today.withDayOfMonth(1);
                endDate = today;
                break;
            case "Last 30 Days":
                startDate = today.minusDays(30);
                endDate = today;
                break;
            case "This Quarter":
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                startDate = today.withMonth(quarterMonth).withDayOfMonth(1);
                endDate = today;
                break;
            case "This Year":
                startDate = today.withDayOfYear(1);
                endDate = today;
                break;
            default:
                startDate = today.minusDays(30);
                endDate = today;
        }
    }
    
    private void loadSalesAnalytics() throws SQLException {
        String currentClientId = Database.getCurrentClientId();
        String startStr = startDate.toString();
        String endStr = endDate.toString();
        
        String sql = """
            SELECT 
                COALESCE(SUM(s.sales_total), 0) as revenue,
                COALESCE(SUM(s.discount), 0) as discounts,
                COUNT(DISTINCT s.user) as total_users
            FROM sales s
            WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startStr);
            stmt.setString(2, endStr);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                analytics.totalRevenue = rs.getBigDecimal("revenue");
                analytics.totalDiscount = rs.getBigDecimal("discounts");
                analytics.totalUsers = rs.getInt("total_users");
            }
        }
    }
    
    private void loadHourlySales() throws SQLException {
        hourlyData.clear();
        String currentClientId = Database.getCurrentClientId();
        
        String sql = """
            SELECT 
                CAST(strftime('%%H', s.sale_date) AS INTEGER) as hour,
                COALESCE(SUM(s.sales_total), 0) as revenue,
                COUNT(DISTINCT s.id) as transactions
            FROM sales s
            WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?
            GROUP BY hour
            ORDER BY hour
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            for (int h = 0; h < 24; h++) {
                hourlyData.add(new HourlySalesData(h, BigDecimal.ZERO, 0, 
                    String.format("%02d:00 - %02d:00", h, (h+1) % 24)));
            }
            
            while (rs.next()) {
                int hour = rs.getInt("hour");
                if (hour >= 0 && hour < 24) {
                    hourlyData.get(hour).revenue = rs.getBigDecimal("revenue");
                    hourlyData.get(hour).transactions = rs.getInt("transactions");
                }
            }
        }
    }
    
    private void loadDailySales() throws SQLException {
        dailyData.clear();
        String currentClientId = Database.getCurrentClientId();
        
        String sql = """
            SELECT 
                DATE(s.sale_date) as sale_date,
                COALESCE(SUM(s.sales_total), 0) as revenue,
                COUNT(DISTINCT s.id) as transactions,
                COALESCE(SUM(si.quantity * p.cost_price), 0) as cost
            FROM sales s
            LEFT JOIN sale_items si ON s.id = si.sale_id
            LEFT JOIN products p ON si.product_id = p.id
            WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?
            GROUP BY DATE(s.sale_date)
            ORDER BY sale_date
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                LocalDate date = LocalDate.parse(rs.getString("sale_date"));
                DailySalesData data = new DailySalesData();
                data.date = date;
                data.dayName = date.getDayOfWeek().toString().substring(0, 3);
                data.revenue = rs.getBigDecimal("revenue");
                data.transactions = rs.getInt("transactions");
                data.profit = data.revenue.subtract(rs.getBigDecimal("cost"));
                dailyData.add(data);
            }
        }
    }
    
    private void loadRepData() {
        repData.clear();
        String currentClientId = Database.getCurrentClientId();
        String filterPeriod = (String) repFilterCombo.getSelectedItem();
        
        String dateCondition = "";
        switch (filterPeriod) {
            case "Today":
                dateCondition = "DATE(s.sale_date) = DATE('now')";
                break;
            case "This Week":
                dateCondition = "DATE(s.sale_date) >= DATE('now', 'weekday 0', '-6 days')";
                break;
            case "This Month":
                dateCondition = "strftime('%Y-%m', s.sale_date) = strftime('%Y-%m', 'now')";
                break;
            default:
                dateCondition = "1=1";
        }
        
        String sql = String.format("""
            SELECT 
                s.user,
                COALESCE(SUM(s.sales_total), 0) as total_sales,
                COUNT(DISTINCT s.id) as transactions,
                COALESCE(AVG(s.sales_total), 0) as avg_sale
            FROM sales s
            WHERE %s AND s.client_id = ?
            GROUP BY s.user
            ORDER BY total_sales DESC
        """, dateCondition);
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            int rank = 1;
            while (rs.next()) {
                SalesRepData rep = new SalesRepData();
                rep.username = rs.getString("user");
                rep.totalSales = rs.getBigDecimal("total_sales");
                rep.transactions = rs.getInt("transactions");
                rep.averageSale = rs.getBigDecimal("avg_sale");
                rep.rank = rank++;
                rep.growth = calculateRepGrowth(rep.username);
                repData.add(rep);
            }
            
            updateLeaderboardTable();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error loading sales rep data: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private double calculateRepGrowth(String username) throws SQLException {
        String currentClientId = Database.getCurrentClientId();
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate previousStart = startDate.minusDays(daysDiff);
        LocalDate previousEnd = startDate.minusDays(1);
        
        String sql = """
            SELECT COALESCE(SUM(sales_total), 0) as total
            FROM sales
            WHERE user = ? AND DATE(sale_date) BETWEEN ? AND ? AND client_id = ?
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, previousStart.toString());
            stmt.setString(3, previousEnd.toString());
            stmt.setString(4, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            BigDecimal previousTotal = rs.next() ? rs.getBigDecimal("total") : BigDecimal.ZERO;
            SalesRepData current = repData.stream().filter(r -> r.username.equals(username)).findFirst().orElse(null);
            
            if (current != null && previousTotal.compareTo(BigDecimal.ZERO) > 0) {
                return current.totalSales.subtract(previousTotal)
                    .divide(previousTotal, 4, RoundingMode.HALF_UP)
                    .doubleValue();
            }
            return 0.0;
        }
    }
    
    private void loadVendorData() throws SQLException {
        vendorData.clear();
        String currentClientId = Database.getCurrentClientId();
        
        String sql = """
            SELECT 
                p.vendor,
                COALESCE(SUM(si.quantity * p.selling_price), 0) as revenue,
                COALESCE(SUM(si.quantity), 0) as units_sold,
                COALESCE(SUM(si.quantity * (p.selling_price - p.cost_price)), 0) as profit
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.id
            JOIN products p ON si.product_id = p.id
            WHERE DATE(s.sale_date) BETWEEN ? AND ? 
                AND s.client_id = ?
                AND p.vendor IS NOT NULL AND p.vendor != ''
            GROUP BY p.vendor
            ORDER BY revenue DESC
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            List<VendorSalesData> tempList = new ArrayList<>();
            
            while (rs.next()) {
                VendorSalesData vendor = new VendorSalesData();
                vendor.vendorName = rs.getString("vendor");
                vendor.revenue = rs.getBigDecimal("revenue");
                vendor.unitsSold = rs.getInt("units_sold");
                vendor.profit = rs.getBigDecimal("profit");
                vendor.velocity = vendor.unitsSold / (double) daysBetween;
                tempList.add(vendor);
                totalRevenue = totalRevenue.add(vendor.revenue);
            }
            
            for (VendorSalesData vendor : tempList) {
                vendorData.add(vendor);
            }
            
            updateVendorTable();
        }
    }
    
    private void loadProductVelocity() throws SQLException {
        productSalesData.clear();
        String currentClientId = Database.getCurrentClientId();
        
        String sql = """
            SELECT 
                p.id, p.name, p.category_id, p.vendor, p.stock_quantity, p.min_stock_level,
                COALESCE(SUM(si.quantity), 0) as units_sold,
                COALESCE(SUM(si.quantity * p.selling_price), 0) as revenue,
                COALESCE(SUM(si.quantity * (p.selling_price - p.cost_price)), 0) as profit
            FROM products p
            LEFT JOIN sale_items si ON p.id = si.product_id
            LEFT JOIN sales s ON si.sale_id = s.id AND DATE(s.sale_date) BETWEEN ? AND ?
            WHERE p.client_id = ?
            GROUP BY p.id
            ORDER BY units_sold DESC
            LIMIT 50
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
            
            while (rs.next()) {
                ProductSalesData p = new ProductSalesData();
                p.productId = rs.getInt("id");
                p.name = rs.getString("name");
                p.vendor = rs.getString("vendor");
                p.quantitySold = rs.getInt("units_sold");
                p.revenue = rs.getBigDecimal("revenue");
                p.profit = rs.getBigDecimal("profit");
                p.currentStock = rs.getInt("stock_quantity");
                p.reorderLevel = rs.getInt("min_stock_level");
                p.velocity = p.quantitySold / (double) daysBetween;
                
                int categoryId = rs.getInt("category_id");
                if (categoryId > 0) {
                    p.category = getCategoryName(categoryId);
                } else {
                    p.category = "Uncategorized";
                }
                
                productSalesData.add(p);
            }
            
            updateVelocityTable();
            updateStockIntegrationTable();
        }
    }
    
    private void updateStockIntegrationTable() {
        Component[] components = ((JPanel) tabbedPane.getComponentAt(0)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                if (panel.getComponent(1) instanceof JPanel) {
                    JPanel inventoryPanel = (JPanel) panel.getComponent(1);
                    if (inventoryPanel.getComponent(0) instanceof JScrollPane) {
                        JScrollPane scroll = (JScrollPane) inventoryPanel.getComponent(0);
                        if (scroll.getViewport().getView() instanceof JTable) {
                            JTable table = (JTable) scroll.getViewport().getView();
                            DefaultTableModel model = (DefaultTableModel) table.getModel();
                            model.setRowCount(0);
                            
                            for (ProductSalesData p : productSalesData) {
                                int projectedDays = p.velocity > 0 ? (int) (p.currentStock / p.velocity) : 999;
                                String riskLevel;
                                if (projectedDays <= 7) riskLevel = "🔴 CRITICAL - Reorder Now";
                                else if (projectedDays <= 30) riskLevel = "🟡 HIGH RISK - Monitor";
                                else if (projectedDays <= 90) riskLevel = "🟠 MEDIUM RISK";
                                else riskLevel = "🟢 LOW RISK - Good Stock";
                                
                                model.addRow(new Object[]{
                                    p.name,
                                    p.currentStock,
                                    p.reorderLevel,
                                    p.quantitySold,
                                    projectedDays,
                                    riskLevel
                                });
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void loadInventoryValuation() throws SQLException {
        String currentClientId = Database.getCurrentClientId();
        
        String sql = """
            SELECT 
                COUNT(CASE WHEN stock_quantity <= min_stock_level THEN 1 END) as low_stock_count
            FROM products
            WHERE client_id = ?
        """;
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, currentClientId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int lowStockCount = rs.getInt("low_stock_count");
                lblStockWarning.setText(String.format("⚠️ %d product(s) below reorder level", lowStockCount));
            }
        }
    }
    
    private String getCategoryName(int categoryId) throws SQLException {
        String sql = "SELECT name FROM categories WHERE id = ?";
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, categoryId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("name");
        }
        return "Unknown";
    }
    
    private void loadProducts(JComboBox<String> combo) {
        combo.removeAllItems();
        try {
            List<Database.Product> products = Database.getAllProducts();
            for (Database.Product p : products) {
                combo.addItem(p.getName());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void performSearch(JTable table, String keyword) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        
        try {
            String currentClientId = Database.getCurrentClientId();
            String sql = """
                SELECT p.id, p.name, c.name as category, p.vendor, p.selling_price, p.stock_quantity,
                       COALESCE(SUM(si.quantity), 0) as monthly_sales
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN sale_items si ON p.id = si.product_id
                LEFT JOIN sales s ON si.sale_id = s.id AND DATE(s.sale_date) >= DATE('now', '-30 days')
                WHERE p.client_id = ?
                GROUP BY p.id
            """;
            
            if (!keyword.isEmpty()) {
                sql += " AND (p.name LIKE ? OR p.vendor LIKE ?)";
            }
            
            PreparedStatement stmt = Database.getConnection().prepareStatement(sql);
            int idx = 1;
            stmt.setString(idx++, currentClientId);
            
            if (!keyword.isEmpty()) {
                String searchPattern = "%" + keyword + "%";
                stmt.setString(idx++, searchPattern);
                stmt.setString(idx++, searchPattern);
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                double velocity = rs.getInt("monthly_sales") / 30.0;
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("category") != null ? rs.getString("category") : "Uncategorized",
                    rs.getString("vendor") != null ? rs.getString("vendor") : "N/A",
                    MONEY.format(rs.getBigDecimal("selling_price")),
                    rs.getInt("stock_quantity"),
                    rs.getInt("monthly_sales"),
                    String.format("%.1f units/day", velocity)
                });
            }
            
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void applyDiscount(String productName, double discountValue, String type, JTextArea resultArea) {
        if (productName == null || productName.isEmpty()) {
            resultArea.setText("❌ Please select a product");
            return;
        }
        
        resultArea.setText("");
        StringBuilder results = new StringBuilder();
        results.append("🔄 APPLYING DISCOUNT...\n");
        results.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        try {
            int productId = -1;
            BigDecimal currentPrice = BigDecimal.ZERO;
            List<Database.Product> products = Database.getAllProducts();
            
            for (Database.Product p : products) {
                if (p.getName().equals(productName)) {
                    productId = p.getId();
                    currentPrice = p.getSellingPrice();
                    break;
                }
            }
            
            if (productId == -1) {
                resultArea.setText("❌ Product not found");
                return;
            }
            
            BigDecimal discountAmt = BigDecimal.valueOf(discountValue);
            BigDecimal newPrice;
            
            if (type.equals("PERCENT")) {
                BigDecimal discountPercent = discountAmt.divide(BigDecimal.valueOf(100));
                newPrice = currentPrice.subtract(currentPrice.multiply(discountPercent));
                results.append(String.format("📊 Discount Type: Percentage (%.1f%%)\n", discountValue));
            } else {
                newPrice = currentPrice.subtract(discountAmt);
                results.append(String.format("💰 Discount Type: Fixed Amount (%s)\n", MONEY.format(discountAmt)));
            }
            
            if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
                resultArea.setText("❌ Discount cannot exceed product price");
                return;
            }
            
            results.append(String.format("🏷️ Product: %s\n", productName));
            results.append(String.format("📉 Original Price: %s\n", MONEY.format(currentPrice)));
            results.append(String.format("✨ New Price: %s\n", MONEY.format(newPrice)));
            results.append(String.format("💸 Customer Savings: %s\n", MONEY.format(currentPrice.subtract(newPrice))));
            
            String updateSql = "UPDATE products SET selling_price = ? WHERE id = ? AND client_id = ?";
            try (PreparedStatement stmt = Database.getConnection().prepareStatement(updateSql)) {
                stmt.setBigDecimal(1, newPrice);
                stmt.setInt(2, productId);
                stmt.setString(3, Database.getCurrentClientId());
                int updated = stmt.executeUpdate();
                
                if (updated > 0) {
                    results.append("\n✅ DISCOUNT APPLIED SUCCESSFULLY!\n");
                    results.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    results.append("💡 Tip: The discounted price will be used for all future sales until changed.");
                } else {
                    results.append("\n❌ Failed to apply discount");
                }
            }
            
            resultArea.setText(results.toString());
            resultArea.setCaretPosition(0);
            loadProductVelocity();
            
        } catch (SQLException e) {
            resultArea.setText("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ============ UI UPDATE METHODS ============
    
    private void updateUI() {
        // Update KPI cards
        lblGrossSale.setText(MONEY.format(analytics.totalRevenue));
        lblTotalDiscount.setText(MONEY.format(analytics.totalDiscount));
        lblTotalUsers.setText(String.valueOf(analytics.totalUsers));
        
        // Update trends panel
        updateTrendsPanel();
        
        try {
            loadInventoryValuation();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void updateLeaderboardTable() {
        DefaultTableModel model = (DefaultTableModel) repLeaderboardTable.getModel();
        model.setRowCount(0);
        
        for (SalesRepData rep : repData) {
            String badge = getBadgeForRank(rep.rank);
            String growthStr = rep.growth > 0 ? String.format("↑ %.1f%%", rep.growth * 100) : 
                               (rep.growth < 0 ? String.format("↓ %.1f%%", Math.abs(rep.growth * 100)) : "0%");
            
            model.addRow(new Object[]{
                rep.rank,
                rep.username,
                MONEY.format(rep.totalSales),
                rep.transactions,
                MONEY.format(rep.averageSale),
                growthStr,
                badge
            });
        }
    }
    
    private String getBadgeForRank(int rank) {
        switch (rank) {
            case 1: return "🏆 GOLD MEDAL";
            case 2: return "🥈 SILVER MEDAL";
            case 3: return "🥉 BRONZE MEDAL";
            default: return "📈 TOP PERFORMER";
        }
    }
    
    private void updateVendorTable() {
        DefaultTableModel model = (DefaultTableModel) vendorTable.getModel();
        model.setRowCount(0);
        
        BigDecimal totalRevenue = vendorData.stream().map(v -> v.revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        for (VendorSalesData vendor : vendorData) {
            double marketShare = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                vendor.revenue.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
            String trend = vendor.velocity > 10 ? "📈 FAST" : (vendor.velocity > 3 ? "📊 STEADY" : "🐢 SLOW");
            
            model.addRow(new Object[]{
                vendor.vendorName,
                MONEY.format(vendor.revenue),
                NUMBER.format(vendor.unitsSold),
                MONEY.format(vendor.profit),
                String.format("%.1f units/day", vendor.velocity),
                String.format("%.1f%%", marketShare),
                trend
            });
        }
    }
    private void updateVelocityTable() {
        DefaultTableModel model = (DefaultTableModel) productVelocityTable.getModel();
        model.setRowCount(0);
        
        for (ProductSalesData p : productSalesData) {
            String status;
            if (p.currentStock <= p.reorderLevel) {
                status = "🔴 CRITICAL - Reorder!";
            } else if (p.currentStock <= p.reorderLevel * 2) {
                status = "🟡 LOW STOCK";
            } else {
                status = "🟢 GOOD";
            }
            
            model.addRow(new Object[]{
                p.name,
                p.category,
                p.vendor != null ? p.vendor : "N/A",
                p.quantitySold,
                String.format("%.1f units/day", p.velocity),
                p.currentStock,
                status
            });
        }
    }
    
    // ============ CHART COMPONENTS ============
    
    class HourlyChartComponent extends JPanel {
        private List<HourlySalesData> data;
        
        public HourlyChartComponent(List<HourlySalesData> data) {
            this.data = data;
            setPreferredSize(new Dimension(0, 280));
            setBackground(Color.WHITE);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            int leftMargin = 50;
            int rightMargin = 30;
            int topMargin = 30;
            int bottomMargin = 50;
            int chartWidth = width - leftMargin - rightMargin;
            int chartHeight = height - topMargin - bottomMargin;
            
            BigDecimal maxRevenue = data.stream().map(d -> d.revenue).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
            double maxVal = maxRevenue.doubleValue();
            if (maxVal <= 0) maxVal = 1;
            
            int barWidth = chartWidth / 24;
            for (int i = 0; i < data.size(); i++) {
                HourlySalesData hour = data.get(i);
                int barHeight = (int) ((hour.revenue.doubleValue() / maxVal) * chartHeight);
                int x = leftMargin + (i * barWidth);
                int y = topMargin + chartHeight - barHeight;
                
                GradientPaint gp = new GradientPaint(x, y, PRIMARY, x, topMargin + chartHeight, PRIMARY_DARK);
                g2.setPaint(gp);
                g2.fillRect(x, y, barWidth - 1, barHeight);
                
                g2.setColor(DARK);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                String hourLabel = String.format("%02d:00", i);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hourLabel, x + (barWidth - fm.stringWidth(hourLabel)) / 2, topMargin + chartHeight + 15);
            }
            
            int peakHour = 0;
            BigDecimal peakRevenue = BigDecimal.ZERO;
            for (HourlySalesData h : data) {
                if (h.revenue.compareTo(peakRevenue) > 0) {
                    peakRevenue = h.revenue;
                    peakHour = h.hour;
                }
            }
            
            g2.setColor(WARNING);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String peakMsg = String.format("⚡ Peak: %02d:00 - %s", peakHour, MONEY.format(peakRevenue));
            g2.drawString(peakMsg, leftMargin + 10, topMargin - 10);
        }
    }
    
    class WeekdayChartComponent extends JPanel {
        private List<DailySalesData> data;
        
        public WeekdayChartComponent(List<DailySalesData> data) {
            this.data = data;
            setPreferredSize(new Dimension(0, 280));
            setBackground(Color.WHITE);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Map<String, BigDecimal> weekdayRevenue = new LinkedHashMap<>();
            String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
            for (String day : days) weekdayRevenue.put(day, BigDecimal.ZERO);
            
            for (DailySalesData d : data) {
                String day = d.dayName.toUpperCase();
                if (weekdayRevenue.containsKey(day)) {
                    weekdayRevenue.put(day, weekdayRevenue.get(day).add(d.revenue));
                }
            }
            
            int width = getWidth();
            int height = getHeight();
            int leftMargin = 60;
            int rightMargin = 30;
            int topMargin = 30;
            int bottomMargin = 50;
            int chartWidth = width - leftMargin - rightMargin;
            int chartHeight = height - topMargin - bottomMargin;
            
            BigDecimal maxRevenue = weekdayRevenue.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
            double maxVal = maxRevenue.doubleValue();
            if (maxVal <= 0) maxVal = 1;
            
            int barWidth = chartWidth / 7;
            int i = 0;
            for (Map.Entry<String, BigDecimal> entry : weekdayRevenue.entrySet()) {
                int barHeight = (int) ((entry.getValue().doubleValue() / maxVal) * chartHeight);
                int x = leftMargin + (i * barWidth);
                int y = topMargin + chartHeight - barHeight;
                
                Color barColor = (entry.getKey().equals("SAT") || entry.getKey().equals("SUN")) ? WARNING : PRIMARY;
                GradientPaint gp = new GradientPaint(x, y, barColor, x, topMargin + chartHeight, barColor.darker());
                g2.setPaint(gp);
                g2.fillRect(x, y, barWidth - 2, barHeight);
                
                g2.setColor(DARK);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                String dayLabel = entry.getKey();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(dayLabel, x + (barWidth - fm.stringWidth(dayLabel)) / 2, topMargin + chartHeight + 20);
                
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                String valueStr = MONEY.format(entry.getValue());
                g2.drawString(valueStr, x + (barWidth - fm.stringWidth(valueStr)) / 2, y - 5);
                
                i++;
            }
        }
    }
    
    // ============ CUSTOM RENDERERS ============
    
    class RankRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(CENTER);
            
            int rank = (Integer) value;
            if (rank == 1) {
                setBackground(new Color(255, 215, 0, 80));
                setFont(getFont().deriveFont(Font.BOLD, 14));
            } else if (rank == 2) {
                setBackground(new Color(192, 192, 192, 80));
            } else if (rank == 3) {
                setBackground(new Color(205, 127, 50, 80));
            } else {
                setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
            }
            return this;
        }
    }
    
    class BadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = new JLabel((String) value);
            label.setHorizontalAlignment(CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 11));
            
            String badge = (String) value;
            if (badge.contains("GOLD")) {
                label.setBackground(new Color(255, 215, 0));
                label.setForeground(Color.BLACK);
                label.setOpaque(true);
            } else if (badge.contains("SILVER")) {
                label.setBackground(new Color(192, 192, 192));
                label.setForeground(Color.BLACK);
                label.setOpaque(true);
            } else if (badge.contains("BRONZE")) {
                label.setBackground(new Color(205, 127, 50));
                label.setForeground(Color.WHITE);
                label.setOpaque(true);
            } else {
                label.setOpaque(false);
            }
            return label;
        }
    }
    
    class RiskRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String risk = (String) value;
            
            if (risk.contains("CRITICAL")) {
                setBackground(DANGER);
                setForeground(Color.WHITE);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (risk.contains("HIGH RISK")) {
                setBackground(WARNING);
                setForeground(Color.WHITE);
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (risk.contains("MEDIUM RISK")) {
                setBackground(INFO);
                setForeground(Color.WHITE);
            } else {
                setBackground(SUCCESS);
                setForeground(Color.WHITE);
            }
            return this;
        }
    }
    
    // ============ HELPER METHODS ============
    
    private JPanel createGlassPanel(String title) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(GLASS_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEmptyBorder(10, 15, 15, 15),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 14),
            PRIMARY
        ));
        return panel;
    }
    
    private JTable createStyledTable(String[] columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(59, 130, 246, 50));
        
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(PRIMARY);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 42));
        
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        
        return table;
    }
    
    private JPanel createFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        footer.setBackground(new Color(30, 41, 59));
        footer.setPreferredSize(new Dimension(0, 45));
        
        JLabel statusLabel = new JLabel("✅ Advanced Analytics Active | Real-time Data | Auto-refresh Every 5 Minutes");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Color.WHITE);
        
        lblStockWarning = new JLabel("📊 Inventory Status: Loading...");
        lblStockWarning.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStockWarning.setForeground(new Color(200, 200, 200));
        
        JLabel timeLabel = new JLabel("Last Updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(200, 200, 200));
        
        footer.add(statusLabel);
        footer.add(new JSeparator(SwingConstants.VERTICAL));
        footer.add(lblStockWarning);
        footer.add(new JSeparator(SwingConstants.VERTICAL));
        footer.add(timeLabel);
        
        return footer;
    }
    
    private void startAutoRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> loadData());
            }
        }, 300000, 300000);
    }
    
    // No-argument constructor for compatibility
    public Salereport() {
        this(null, "Admin");
    }
    
    public static void showDialog(JFrame parent, String username) {
        Salereport dialog = new Salereport(parent, username);
        dialog.setVisible(true);
    }
}
    