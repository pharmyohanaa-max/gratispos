package hIMs.App;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.category.*;
import org.jfree.data.general.*;
import java.sql.PreparedStatement;
/**
 * Professional Stock Value & Business Analytics Dashboard
 * Enhanced with comprehensive reporting and modern UI design
 */
public class Stockvalue extends JDialog {

    // UI Components
    private JPanel contentPane;
    private JTabbedPane tabbedPane;
    
    // Stock Value Tab
    private JTable stockTable;
    private JComboBox<String> priceOptionBox;
    private JComboBox<String> currencyBox;
    private JComboBox<String> viewModeBox;
    private JLabel totalAmountLabel;
    private JLabel totalItemsLabel;
    private JLabel lowStockCountLabel;
    private JTextField totalValueField;
    
    // Analytics Tab
    private JTable analyticsTable;
    private JComboBox<String> dateRangeBox;
    private JPanel chartPanel;
    private JLabel totalRevenueLabel;
    private JLabel totalProfitLabel;
    private JLabel profitMarginLabel;
    
    // Inventory Health Tab
    private JTable inventoryHealthTable;
    private JProgressBar stockHealthBar;
    private JLabel expiringItemsLabel;
    private JLabel deadStockLabel;
    
    // Top Products Tab
    private JTable topProductsTable;
    private JComboBox<String> topProductsRangeBox;
    private JPanel topProductsChartPanel;
    
    // Financial Summary Tab
    private JTable incomeTable;
    private JComboBox<String> periodBox;
    private Map<String, JLabel> ratioLabels;
    private JLabel totalRevenueCardLabel;
    private JLabel netProfitCardLabel;
    private JLabel profitMarginCardLabel;
    private JLabel transactionCountLabel;
    private JLabel avgTicketLabel;
    private JTable topProductsSummaryTable;
    private JLabel bestDayLabel;
    private JLabel avgDailyLabel;
    private JLabel cogsSummaryLabel;
    private JLabel expenseSummaryLabel;
    
    // Data
    private ArrayList<StockItem> stockItems;
    private boolean isMinimized = false;
    private JButton floatButton;
    private JPanel fullContentPanel;
    private JPanel minimizedContentPanel;
    
    // Colors - Professional Palette
    private static final Color PRIMARY_COLOR = new Color(25, 118, 210);      // Blue
    private static final Color SECONDARY_COLOR = new Color(66, 66, 66);      // Dark Gray
    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);       // Green
    private static final Color WARNING_COLOR = new Color(237, 108, 2);       // Orange
    private static final Color DANGER_COLOR = new Color(211, 47, 47);        // Red
    private static final Color BACKGROUND_COLOR = new Color(250, 250, 250);  // Off-white
    private static final Color PANEL_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(224, 224, 224);
    
    // Fonts
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 28);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font SUBHEADER_FONT = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font DEFAULT_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font METRIC_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font CARD_FONT = new Font("Segoe UI", Font.BOLD, 20);

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                JFrame parent = new JFrame("Test Parent");
                parent.setSize(200, 200);
                parent.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                parent.setVisible(true);
                Stockvalue dialog = new Stockvalue(parent);
                dialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Stockvalue(JFrame parent) {
        super(parent, "Business Analytics Dashboard", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1400, 850);
        setLocationRelativeTo(parent);
        
        initializeComponents();
        loadDataFromDatabase();
        updateAllViews();
    }

    private void initializeComponents() {
        // Main content panel
        fullContentPanel = new JPanel(new BorderLayout(0, 0));
        fullContentPanel.setBackground(BACKGROUND_COLOR);
        
        // Header
        JPanel headerPanel = createHeaderPanel();
        fullContentPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Tabbed Pane
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(SUBHEADER_FONT);
        tabbedPane.setBackground(PANEL_COLOR);
        
        // Add tabs
        tabbedPane.addTab("📊 Stock Value", createStockValueTab());
        tabbedPane.addTab("📈 Business Analytics", createAnalyticsTab());
        tabbedPane.addTab("🏥 Inventory Health", createInventoryHealthTab());
        tabbedPane.addTab("🏆 Top Products", createTopProductsTab());
        tabbedPane.addTab("💰 Financial Summary", createFinancialSummaryTab());
        
        fullContentPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Minimized panel
        minimizedContentPanel = createMinimizedPanel();
        
        // Set content
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(fullContentPanel, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        
        // Title
        JLabel titleLabel = new JLabel("Pos Business Analytics Dashboard");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Date and controls
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        controlsPanel.setOpaque(false);
        
        JLabel dateLabel = new JLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
        dateLabel.setFont(DEFAULT_FONT);
        dateLabel.setForeground(Color.WHITE);
        controlsPanel.add(dateLabel);
        
        JButton refreshButton = createStyledButton("🔄 Refresh", PANEL_COLOR, PRIMARY_COLOR);
        refreshButton.addActionListener(e -> refreshAllData());
        controlsPanel.add(refreshButton);
        
        floatButton = createStyledButton("⬇ Minimize", PANEL_COLOR, PRIMARY_COLOR);
        floatButton.addActionListener(e -> toggleFloatMode());
        controlsPanel.add(floatButton);
        
        headerPanel.add(controlsPanel, BorderLayout.EAST);
        
        return headerPanel;
    }

    private JPanel createStockValueTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Top metrics panel
        JPanel metricsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        metricsPanel.setOpaque(false);
        
        totalAmountLabel = new JLabel("0.00");
        metricsPanel.add(createMetricCard("Total Stock Value", totalAmountLabel, PRIMARY_COLOR));
        
        totalItemsLabel = new JLabel("0");
        metricsPanel.add(createMetricCard("Total Items", totalItemsLabel, SUCCESS_COLOR));
        
        lowStockCountLabel = new JLabel("0");
        metricsPanel.add(createMetricCard("Low Stock Alerts", lowStockCountLabel, WARNING_COLOR));
        
        panel.add(metricsPanel, BorderLayout.NORTH);
        
        // Filters panel
        JPanel filtersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        filtersPanel.setBackground(PANEL_COLOR);
        filtersPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(10, 15, 10, 15)
        ));
        
        filtersPanel.add(createLabel("View:", SUBHEADER_FONT));
        viewModeBox = new JComboBox<>(new String[]{"By Category", "By Product", "Low Stock Only"});
        viewModeBox.setFont(DEFAULT_FONT);
        viewModeBox.addActionListener(e -> updateStockTable());
        filtersPanel.add(viewModeBox);
        
        filtersPanel.add(Box.createHorizontalStrut(20));
        
        filtersPanel.add(createLabel("Price:", SUBHEADER_FONT));
        priceOptionBox = new JComboBox<>(new String[]{"Selling Price", "Cost Price"});
        priceOptionBox.setFont(DEFAULT_FONT);
        priceOptionBox.addActionListener(e -> updateStockTable());
        filtersPanel.add(priceOptionBox);
        
        filtersPanel.add(Box.createHorizontalStrut(20));
        
        filtersPanel.add(createLabel("Currency:", SUBHEADER_FONT));
        currencyBox = new JComboBox<>(new String[]{
            "TZS - Tanzanian Shilling",
            "USD - US Dollar", 
            "EUR - Euro",
            "GBP - British Pound",
            "JPY - Japanese Yen",
            "CNY - Chinese Yuan",
            "KES - Kenyan Shilling",
            "UGX - Ugandan Shilling",
            "ZAR - South African Rand",
            "AED - UAE Dirham",
            "INR - Indian Rupee",
            "CHF - Swiss Franc"
        });
        currencyBox.setFont(DEFAULT_FONT);
        currencyBox.addActionListener(e -> updateStockTable());
        filtersPanel.add(currencyBox);

        filtersPanel.add(Box.createHorizontalStrut(30));

        // Total Value Display
        JLabel totalValueTitleLabel = new JLabel("Total Value:");
        totalValueTitleLabel.setFont(SUBHEADER_FONT);
        totalValueTitleLabel.setForeground(SUCCESS_COLOR);
        filtersPanel.add(totalValueTitleLabel);

        totalValueField = new JTextField(15);
        totalValueField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        totalValueField.setForeground(SUCCESS_COLOR);
        totalValueField.setEditable(false);
        totalValueField.setBackground(Color.WHITE);
        totalValueField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SUCCESS_COLOR, 2),
            new EmptyBorder(5, 10, 5, 10)
        ));
        totalValueField.setHorizontalAlignment(JTextField.RIGHT);
        filtersPanel.add(totalValueField);
        
        panel.add(filtersPanel, BorderLayout.BEFORE_FIRST_LINE);
        
        // Table
        stockTable = createStyledTable();
        JScrollPane scrollPane = new JScrollPane(stockTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Metrics panel
        JPanel metricsPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        metricsPanel.setOpaque(false);
        
        totalRevenueLabel = new JLabel("0.00");
        metricsPanel.add(createMetricCard("Total Revenue", totalRevenueLabel, PRIMARY_COLOR));
        
        totalProfitLabel = new JLabel("0.00");
        metricsPanel.add(createMetricCard("Total Profit", totalProfitLabel, SUCCESS_COLOR));
        
        profitMarginLabel = new JLabel("0%");
        metricsPanel.add(createMetricCard("Profit Margin", profitMarginLabel, WARNING_COLOR));
        
        panel.add(metricsPanel, BorderLayout.NORTH);
        
        // Center panel with chart and table
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        centerPanel.setOpaque(false);
        
        // Chart panel
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBackground(PANEL_COLOR);
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        JPanel chartHeaderPanel = new JPanel(new BorderLayout());
        chartHeaderPanel.setOpaque(false);
        chartHeaderPanel.add(createLabel("Sales Trend", HEADER_FONT), BorderLayout.WEST);
        
        dateRangeBox = new JComboBox<>(new String[]{"Last 7 Days", "Last 30 Days", "Last 90 Days", "This Year"});
        dateRangeBox.setFont(DEFAULT_FONT);
        dateRangeBox.addActionListener(e -> updateAnalyticsChart());
        chartHeaderPanel.add(dateRangeBox, BorderLayout.EAST);
        
        leftPanel.add(chartHeaderPanel, BorderLayout.NORTH);
        
        chartPanel = new JPanel(new BorderLayout());
        chartPanel.setBackground(Color.WHITE);
        chartPanel.setPreferredSize(new Dimension(400, 300));
        leftPanel.add(chartPanel, BorderLayout.CENTER);
        
        centerPanel.add(leftPanel);
        
        // Table panel
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(PANEL_COLOR);
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        rightPanel.add(createLabel("Recent Transactions", HEADER_FONT), BorderLayout.NORTH);
        
        analyticsTable = createStyledTable();
        JScrollPane analyticsScrollPane = new JScrollPane(analyticsTable);
        analyticsScrollPane.setBorder(null);
        rightPanel.add(analyticsScrollPane, BorderLayout.CENTER);
        
        centerPanel.add(rightPanel);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createInventoryHealthTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Health metrics
        JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        metricsPanel.setOpaque(false);
        
        JPanel healthCardPanel = new JPanel(new BorderLayout(10, 10));
        healthCardPanel.setBackground(PANEL_COLOR);
        healthCardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        healthCardPanel.add(createLabel("Overall Stock Health", HEADER_FONT), BorderLayout.NORTH);
        stockHealthBar = new JProgressBar(0, 100);
        stockHealthBar.setStringPainted(true);
        stockHealthBar.setFont(SUBHEADER_FONT);
        stockHealthBar.setPreferredSize(new Dimension(200, 40));
        healthCardPanel.add(stockHealthBar, BorderLayout.CENTER);
        
        metricsPanel.add(healthCardPanel);
        
        expiringItemsLabel = new JLabel("0");
        metricsPanel.add(createMetricCard("Expiring Soon", expiringItemsLabel, WARNING_COLOR));
        
        deadStockLabel = new JLabel("0");
        metricsPanel.add(createMetricCard("Dead Stock Items", deadStockLabel, DANGER_COLOR));
        
        JLabel turnoverLabel = new JLabel("0.0x");
        metricsPanel.add(createMetricCard("Inventory Turnover", turnoverLabel, PRIMARY_COLOR));
        
        panel.add(metricsPanel, BorderLayout.NORTH);
        
        // Inventory details table
        JPanel tablePanel = new JPanel(new BorderLayout(10, 10));
        tablePanel.setBackground(PANEL_COLOR);
        tablePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        tablePanel.add(createLabel("Inventory Status Details", HEADER_FONT), BorderLayout.NORTH);
        
        inventoryHealthTable = createStyledTable();
        JScrollPane scrollPane = new JScrollPane(inventoryHealthTable);
        scrollPane.setBorder(null);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        
        panel.add(tablePanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createTopProductsTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Header with filter
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setOpaque(false);
        
        headerPanel.add(createLabel("Top Performing Products", HEADER_FONT), BorderLayout.WEST);
        
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterPanel.setOpaque(false);
        filterPanel.add(createLabel("Period:", DEFAULT_FONT));
        topProductsRangeBox = new JComboBox<>(new String[]{"Today", "This Week", "This Month", "This Year"});
        topProductsRangeBox.setFont(DEFAULT_FONT);
        topProductsRangeBox.addActionListener(e -> updateTopProductsView());
        filterPanel.add(topProductsRangeBox);
        
        headerPanel.add(filterPanel, BorderLayout.EAST);
        
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Split panel for chart and table
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        
        // Chart
        JPanel chartContainerPanel = new JPanel(new BorderLayout());
        chartContainerPanel.setBackground(PANEL_COLOR);
        chartContainerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        topProductsChartPanel = new JPanel(new BorderLayout());
        topProductsChartPanel.setBackground(Color.WHITE);
        chartContainerPanel.add(topProductsChartPanel, BorderLayout.CENTER);
        
        splitPane.setTopComponent(chartContainerPanel);
        
        // Table
        JPanel tableContainerPanel = new JPanel(new BorderLayout(10, 10));
        tableContainerPanel.setBackground(PANEL_COLOR);
        tableContainerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        tableContainerPanel.add(createLabel("Detailed Rankings", SUBHEADER_FONT), BorderLayout.NORTH);
        
        topProductsTable = createStyledTable();
        topProductsTable.setSelectionBackground(new Color(200, 220, 240));
        topProductsTable.setSelectionForeground(Color.BLACK);
        JScrollPane scrollPane = new JScrollPane(topProductsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        tableContainerPanel.add(scrollPane, BorderLayout.CENTER);
        
        splitPane.setBottomComponent(tableContainerPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createFinancialSummaryTab() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Top Metrics Row - 5 cards
        JPanel topMetricsPanel = new JPanel(new GridLayout(1, 5, 15, 0));
        topMetricsPanel.setOpaque(false);
        topMetricsPanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        
        totalRevenueCardLabel = createValueLabel("0");
        topMetricsPanel.add(createMetricCard("💰 Total Revenue", totalRevenueCardLabel, PRIMARY_COLOR));
        
        netProfitCardLabel = createValueLabel("0");
        topMetricsPanel.add(createMetricCard("📈 Net Profit", netProfitCardLabel, SUCCESS_COLOR));
        
        profitMarginCardLabel = createValueLabel("0%");
        topMetricsPanel.add(createMetricCard("📊 Profit Margin", profitMarginCardLabel, WARNING_COLOR));
        
        transactionCountLabel = createValueLabel("0");
        topMetricsPanel.add(createMetricCard("🛒 Transactions", transactionCountLabel, new Color(156, 39, 176)));
        
        avgTicketLabel = createValueLabel("0");
        topMetricsPanel.add(createMetricCard("🎫 Avg Ticket", avgTicketLabel, new Color(0, 150, 136)));
        
        panel.add(topMetricsPanel, BorderLayout.NORTH);
        
        // Main content - 2 rows
        JPanel contentPanel = new JPanel(new GridLayout(2, 1, 15, 15));
        contentPanel.setOpaque(false);
        
        // Income Statement Panel
        JPanel incomePanel = new JPanel(new BorderLayout(10, 10));
        incomePanel.setBackground(PANEL_COLOR);
        incomePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        // Header with period selector and export
        JPanel incomeHeaderPanel = new JPanel(new BorderLayout());
        incomeHeaderPanel.setOpaque(false);
        incomeHeaderPanel.add(createLabel("Income Statement", HEADER_FONT), BorderLayout.WEST);
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlPanel.setOpaque(false);
        
        controlPanel.add(createLabel("Period:", DEFAULT_FONT));
        periodBox = new JComboBox<>(new String[]{"Today", "This Week", "This Month", "Last 30 Days", "This Quarter", "This Year"});
        periodBox.setFont(DEFAULT_FONT);
        periodBox.addActionListener(e -> loadFinancialSummary());
        controlPanel.add(periodBox);
        
        JButton exportBtn = createStyledButton("📎 Export", SUCCESS_COLOR, Color.WHITE);
        exportBtn.addActionListener(e -> exportFinancialReport());
        controlPanel.add(exportBtn);
        
        incomeHeaderPanel.add(controlPanel, BorderLayout.EAST);
        incomePanel.add(incomeHeaderPanel, BorderLayout.NORTH);
        
        incomeTable = createStyledTable();
        incomeTable.setSelectionBackground(new Color(200, 220, 240));
        incomeTable.setSelectionForeground(Color.BLACK);
        JScrollPane incomeScrollPane = new JScrollPane(incomeTable);
        incomeScrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        incomePanel.add(incomeScrollPane, BorderLayout.CENTER);
        
        contentPanel.add(incomePanel);
        
        // Bottom Panel - Ratios + Top Products + Stats
        JPanel bottomPanel = new JPanel(new GridLayout(1, 3, 15, 0));
        bottomPanel.setOpaque(false);
        
        // Key Financial Ratios Panel
        JPanel ratiosPanel = new JPanel(new BorderLayout(10, 10));
        ratiosPanel.setBackground(PANEL_COLOR);
        ratiosPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        ratiosPanel.add(createLabel("Key Ratios", SUBHEADER_FONT), BorderLayout.NORTH);
        
        JPanel ratiosGridPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        ratiosGridPanel.setOpaque(false);
        ratiosGridPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        ratioLabels = new HashMap<>();
        String[] ratioNames = {"Gross Margin", "Operating Margin", "Net Margin", "ROI", "Stock Turnover", "Days in Stock"};
        Color[] ratioColors = {SUCCESS_COLOR, PRIMARY_COLOR, WARNING_COLOR, SUCCESS_COLOR, PRIMARY_COLOR, WARNING_COLOR};
        
        for (int i = 0; i < ratioNames.length; i++) {
            JPanel card = createSmallRatioCard(ratioNames[i], "0", ratioColors[i]);
            ratiosGridPanel.add(card);
            for (Component comp : card.getComponents()) {
                if (comp instanceof JLabel && ((JLabel) comp).getFont().getSize() == 16) {
                    ratioLabels.put(ratioNames[i], (JLabel) comp);
                    break;
                }
            }
        }
        
        ratiosPanel.add(ratiosGridPanel, BorderLayout.CENTER);
        bottomPanel.add(ratiosPanel);
        
        // Top Products Summary Panel
        JPanel topProductsPanel = new JPanel(new BorderLayout(10, 10));
        topProductsPanel.setBackground(PANEL_COLOR);
        topProductsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        topProductsPanel.add(createLabel("🏆 Top 5 Products", SUBHEADER_FONT), BorderLayout.NORTH);
        
        topProductsSummaryTable = createStyledTable();
        topProductsSummaryTable.setRowHeight(28);
        JScrollPane topProductsScroll = new JScrollPane(topProductsSummaryTable);
        topProductsScroll.setBorder(null);
        topProductsPanel.add(topProductsScroll, BorderLayout.CENTER);
        
        bottomPanel.add(topProductsPanel);
        
        // Quick Stats Panel
        JPanel statsPanel = new JPanel(new BorderLayout(10, 10));
        statsPanel.setBackground(PANEL_COLOR);
        statsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        statsPanel.add(createLabel("Quick Stats", SUBHEADER_FONT), BorderLayout.NORTH);
        
        JPanel statsGrid = new JPanel(new GridLayout(4, 1, 5, 12));
        statsGrid.setOpaque(false);
        statsGrid.setBorder(new EmptyBorder(10, 10, 5, 10));
        
        bestDayLabel = new JLabel("Best Day: --");
        bestDayLabel.setFont(DEFAULT_FONT);
        avgDailyLabel = new JLabel("Avg Daily: --");
        avgDailyLabel.setFont(DEFAULT_FONT);
        cogsSummaryLabel = new JLabel("Total COGS: --");
        cogsSummaryLabel.setFont(DEFAULT_FONT);
        expenseSummaryLabel = new JLabel("Total Expenses: --");
        expenseSummaryLabel.setFont(DEFAULT_FONT);
        
        statsGrid.add(bestDayLabel);
        statsGrid.add(avgDailyLabel);
        statsGrid.add(cogsSummaryLabel);
        statsGrid.add(expenseSummaryLabel);
        
        statsPanel.add(statsGrid, BorderLayout.CENTER);
        bottomPanel.add(statsPanel);
        
        contentPanel.add(bottomPanel);
        panel.add(contentPanel, BorderLayout.CENTER);
        
        // Load initial data
        loadFinancialSummary();
        
        return panel;
    }

    private JLabel createValueLabel(String initialValue) {
        JLabel label = new JLabel(initialValue, SwingConstants.CENTER);
        label.setFont(METRIC_FONT);
        return label;
    }

    private JPanel createSmallRatioCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 3));
        card.setBackground(PANEL_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(8, 8, 8, 8)
        ));
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titleLabel.setForeground(SECONDARY_COLOR);
        card.add(titleLabel, BorderLayout.NORTH);
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        valueLabel.setForeground(color);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }

    private void loadFinancialSummary() {
        new Thread(() -> {
            try {
                String period = (String) periodBox.getSelectedItem();
                LocalDate endDate = LocalDate.now();
                LocalDate startDate;
                
                switch (period) {
                    case "Today": startDate = endDate; break;
                    case "This Week": startDate = endDate.minusDays(7); break;
                    case "This Month": startDate = endDate.minusDays(30); break;
                    case "Last 30 Days": startDate = endDate.minusDays(30); break;
                    case "This Quarter": startDate = endDate.minusMonths(3); break;
                    case "This Year": startDate = LocalDate.of(endDate.getYear(), 1, 1); break;
                    default: startDate = endDate.minusDays(30);
                }
                
                String startDateStr = Database.toDatabaseDate(startDate);
                String endDateStr = Database.toDatabaseDate(endDate);
                
                // Get financial data
                BigDecimal totalRevenue = Database.getTotalSales(startDateStr, endDateStr);
                BigDecimal totalDiscounts = Database.getTotalDiscounts(startDateStr, endDateStr);
                BigDecimal netSales = totalRevenue.subtract(totalDiscounts);
                BigDecimal cogs = Database.getCostOfGoods(startDateStr, endDateStr);
                BigDecimal totalExpenses = Database.getTotalExpenses(startDateStr, endDateStr);
                
                BigDecimal grossProfit = netSales.subtract(cogs);
                BigDecimal operatingProfit = grossProfit.subtract(totalExpenses);
                BigDecimal netProfit = operatingProfit;
                
                // Calculate margins
                BigDecimal grossMargin = netSales.compareTo(BigDecimal.ZERO) > 0 ? 
                    grossProfit.divide(netSales, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
                BigDecimal operatingMargin = netSales.compareTo(BigDecimal.ZERO) > 0 ? 
                    operatingProfit.divide(netSales, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
                BigDecimal netMargin = netSales.compareTo(BigDecimal.ZERO) > 0 ? 
                    netProfit.divide(netSales, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
                
                // Inventory metrics
                BigDecimal inventoryValue = getTotalInventoryValue();
                BigDecimal stockTurnover = inventoryValue.compareTo(BigDecimal.ZERO) > 0 && cogs.compareTo(BigDecimal.ZERO) > 0 ?
                    cogs.divide(inventoryValue, 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
                BigDecimal daysInStock = stockTurnover.compareTo(BigDecimal.ZERO) > 0 ?
                    BigDecimal.valueOf(365).divide(stockTurnover, 0, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
                
                // ROI
                BigDecimal totalAssets = inventoryValue.add(getTotalCash());
                BigDecimal roi = totalAssets.compareTo(BigDecimal.ZERO) > 0 && netProfit.compareTo(BigDecimal.ZERO) > 0 ?
                    netProfit.divide(totalAssets, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
                
                // Transaction count and average ticket
                int transactionCount = getTransactionCount(startDateStr, endDateStr);
                BigDecimal avgTicket = transactionCount > 0 ? netSales.divide(BigDecimal.valueOf(transactionCount), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
                
                // Best day and average daily
                String bestDay = getBestDay(startDateStr, endDateStr);
                BigDecimal avgDaily = getAverageDailyRevenue(startDateStr, endDateStr);
                
                String currency = ((String) currencyBox.getSelectedItem()).split(" - ")[0];
                
                final String currencyFinal = currency;
                final BigDecimal finalNetSales = netSales;
                final BigDecimal finalNetProfit = netProfit;
                final BigDecimal finalNetMargin = netMargin;
                final int finalTransactionCount = transactionCount;
                final BigDecimal finalAvgTicket = avgTicket;
                final String finalBestDay = bestDay;
                final BigDecimal finalAvgDaily = avgDaily;
                final BigDecimal finalCogs = cogs;
                final BigDecimal finalTotalExpenses = totalExpenses;
                
                SwingUtilities.invokeLater(() -> {
                    // Update top metric cards
                    totalRevenueCardLabel.setText(currencyFinal + " " + formatCurrency(finalNetSales));
                    netProfitCardLabel.setText(currencyFinal + " " + formatCurrency(finalNetProfit));
                    profitMarginCardLabel.setText(String.format("%.1f%%", finalNetMargin));
                    transactionCountLabel.setText(String.valueOf(finalTransactionCount));
                    avgTicketLabel.setText(currencyFinal + " " + formatCurrency(finalAvgTicket));
                    
                    // Update quick stats
                    bestDayLabel.setText("Best Day: " + finalBestDay);
                    avgDailyLabel.setText("Avg Daily: " + currencyFinal + " " + formatCurrency(finalAvgDaily));
                    cogsSummaryLabel.setText("Total COGS: " + currencyFinal + " " + formatCurrency(finalCogs));
                    expenseSummaryLabel.setText("Total Expenses: " + currencyFinal + " " + formatCurrency(finalTotalExpenses));
                    
                    // Update ratio labels
                    updateRatioLabel("Gross Margin", grossMargin, "%");
                    updateRatioLabel("Operating Margin", operatingMargin, "%");
                    updateRatioLabel("Net Margin", netMargin, "%");
                    updateRatioLabel("ROI", roi, "%");
                    updateRatioLabel("Stock Turnover", stockTurnover, "x");
                    updateRatioLabel("Days in Stock", daysInStock, "days");
                    
                    // Update income table
                    updateIncomeTable(totalRevenue, totalDiscounts, netSales, cogs, grossProfit, 
                                     totalExpenses, operatingProfit, netProfit, grossMargin, operatingMargin, netMargin, startDate, endDate);
                    
                    // Update top products table
                    try {
                        updateTopProductsSummaryTable(startDateStr, endDateStr, currencyFinal);
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                });
                
            } catch (SQLException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to load financial data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void updateIncomeTable(BigDecimal revenue, BigDecimal discounts, BigDecimal netSales,
                                    BigDecimal cogs, BigDecimal grossProfit, BigDecimal expenses, 
                                    BigDecimal operatingProfit, BigDecimal netProfit,
                                    BigDecimal grossMargin, BigDecimal operatingMargin, BigDecimal netMargin,
                                    LocalDate startDate, LocalDate endDate) {
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Account");
        model.addColumn("Amount");
        model.addColumn("% of Sales");
        
        String currencyFull = (String) currencyBox.getSelectedItem();
        String currency = currencyFull.split(" - ")[0];
        
        model.addRow(new Object[]{"Period: " + startDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) + " - " + endDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), "", ""});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"📊 REVENUE", "", ""});
        model.addRow(new Object[]{"  Gross Sales", currency + " " + formatCurrency(revenue), "100%"});
        model.addRow(new Object[]{"  (-) Discounts", currency + " " + formatCurrency(discounts), formatPercent(discounts, revenue)});
        model.addRow(new Object[]{"  Net Sales", currency + " " + formatCurrency(netSales), "100%"});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"📦 COST OF GOODS SOLD", "", ""});
        model.addRow(new Object[]{"  Cost of Goods Sold", currency + " " + formatCurrency(cogs), formatPercent(cogs, netSales)});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"💰 GROSS PROFIT", currency + " " + formatCurrency(grossProfit), formatPercent(grossProfit, netSales)});
        model.addRow(new Object[]{"  Gross Margin", "", String.format("%.1f%%", grossMargin)});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"📋 OPERATING EXPENSES", "", ""});
        model.addRow(new Object[]{"  Total Expenses", currency + " " + formatCurrency(expenses), formatPercent(expenses, netSales)});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"📈 OPERATING PROFIT", currency + " " + formatCurrency(operatingProfit), formatPercent(operatingProfit, netSales)});
        model.addRow(new Object[]{"  Operating Margin", "", String.format("%.1f%%", operatingMargin)});
        model.addRow(new Object[]{"", "", ""});
        model.addRow(new Object[]{"✅ NET PROFIT", currency + " " + formatCurrency(netProfit), formatPercent(netProfit, netSales)});
        model.addRow(new Object[]{"  Net Margin", "", String.format("%.1f%%", netMargin)});
        
        incomeTable.setModel(model);
        incomeTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        incomeTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        incomeTable.getColumnModel().getColumn(2).setPreferredWidth(120);
    }

    private void updateTopProductsSummaryTable(String startDate, String endDate, String currency) throws SQLException {
        List<Database.TopSellingProduct> topProducts = Database.getTopSellingProducts(startDate, endDate, 5);
        
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Product");
        model.addColumn("Qty");
        model.addColumn("Revenue");
        
        for (Database.TopSellingProduct p : topProducts) {
            model.addRow(new Object[]{
                p.productName.length() > 25 ? p.productName.substring(0, 22) + "..." : p.productName,
                p.totalQuantitySold,
                currency + " " + formatCurrency(p.totalRevenue)
            });
        }
        
        topProductsSummaryTable.setModel(model);
        topProductsSummaryTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        topProductsSummaryTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        topProductsSummaryTable.getColumnModel().getColumn(2).setPreferredWidth(100);
    }

    private int getTransactionCount(String startDate, String endDate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ?";
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String getBestDay(String startDate, String endDate) throws SQLException {
        String sql = "SELECT DATE(sale_date) as day, SUM(sales_total) as total FROM sales " +
                     "WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ? " +
                     "GROUP BY DATE(sale_date) ORDER BY total DESC LIMIT 1";
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String currency = ((String) currencyBox.getSelectedItem()).split(" - ")[0];
                return rs.getString("day") + " (" + currency + " " + formatCurrency(rs.getBigDecimal("total")) + ")";
            }
            return "No data";
        }
    }

    private BigDecimal getAverageDailyRevenue(String startDate, String endDate) throws SQLException {
        String sql = "SELECT AVG(daily_total) FROM (SELECT DATE(sale_date) as day, SUM(sales_total) as daily_total " +
                     "FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ? GROUP BY DATE(sale_date))";
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    private void exportFinancialReport() {
        JOptionPane.showMessageDialog(this, "Export feature coming soon!", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateRatioLabel(String name, BigDecimal value, String suffix) {
        JLabel label = ratioLabels.get(name);
        if (label != null) {
            if (suffix.equals("x")) {
                label.setText(String.format("%.2f", value) + suffix);
            } else if (suffix.equals("days")) {
                label.setText(String.format("%.0f", value) + " " + suffix);
            } else {
                label.setText(String.format("%.1f", value) + suffix);
            }
        }
    }

    private BigDecimal getTotalInventoryValue() throws SQLException {
        List<Database.Product> products = Database.getAllProducts();
        BigDecimal total = BigDecimal.ZERO;
        for (Database.Product p : products) {
            total = total.add(p.getSellingPrice().multiply(BigDecimal.valueOf(p.getStockQuantity())));
        }
        return total;
    }

    private BigDecimal getTotalCash() throws SQLException {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        return Database.getTotalSales(Database.toDatabaseDate(startDate), Database.toDatabaseDate(endDate));
    }

    private String formatCurrency(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private String formatPercent(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return "0%";
        BigDecimal percent = value.divide(total, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
        return String.format("%.1f%%", percent);
    }

    private JPanel createMinimizedPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        JLabel label = new JLabel("Analytics Dashboard (Minimized)", SwingConstants.CENTER);
        label.setFont(HEADER_FONT);
        panel.add(label, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        JButton restoreButton = createStyledButton("⬆ Restore", PRIMARY_COLOR, Color.WHITE);
        restoreButton.addActionListener(e -> toggleFloatMode());
        buttonPanel.add(restoreButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(PANEL_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 2),
            new EmptyBorder(20, 20, 20, 20)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(SUBHEADER_FONT);
        titleLabel.setForeground(SECONDARY_COLOR);
        card.add(titleLabel, BorderLayout.NORTH);
        
        valueLabel.setFont(METRIC_FONT);
        valueLabel.setForeground(accentColor);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }

    private JPanel createRatioCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(PANEL_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(15, 15, 15, 15)
        ));
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(DEFAULT_FONT);
        titleLabel.setForeground(SECONDARY_COLOR);
        card.add(titleLabel, BorderLayout.NORTH);
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(color);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }

    private JTable createStyledTable() {
        JTable table = new JTable();
        table.setFont(TABLE_FONT);
        table.setRowHeight(28);
        table.setGridColor(BORDER_COLOR);
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(227, 242, 253));
        table.setSelectionForeground(Color.BLACK);
        
        JTableHeader header = table.getTableHeader();
        header.setFont(SUBHEADER_FONT);
        header.setBackground(PRIMARY_COLOR);
        header.setForeground(Color.WHITE);
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 35));
        
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final Color evenColor = new Color(250, 250, 250);
            private final Color oddColor = Color.WHITE;
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, 
                    isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? evenColor : oddColor);
                }
                
                if (value instanceof Number || (value instanceof String && 
                    ((String)value).matches(".*\\d+.*"))) {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                }
                
                setBorder(new EmptyBorder(5, 10, 5, 10));
                
                return c;
            }
        });
        
        return table;
    }

    private JButton createStyledButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(DEFAULT_FONT);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 20, 8, 20));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(background.darker());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(background);
            }
        });
        
        return button;
    }

    private JLabel createLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(SECONDARY_COLOR);
        return label;
    }

    private void toggleFloatMode() {
        JFrame parent = (JFrame) getParent();
        if (parent != null) {
            parent.setVisible(true);
            parent.toFront();
        }
        
        if (isMinimized) {
            setSize(1400, 850);
            floatButton.setText("⬇ Minimize");
            getContentPane().removeAll();
            getContentPane().add(fullContentPanel, BorderLayout.CENTER);
            isMinimized = false;
        } else {
            setSize(400, 250);
            floatButton.setText("⬆ Restore");
            getContentPane().removeAll();
            getContentPane().add(minimizedContentPanel, BorderLayout.CENTER);
            isMinimized = true;
        }
        
        getContentPane().revalidate();
        getContentPane().repaint();
        setLocationRelativeTo(parent);
    }

    private void loadDataFromDatabase() {
        stockItems = new ArrayList<>();
        
        String currentClientId = Database.getCurrentClientId();
        String query = """
            SELECT p.id, p.name, c.name AS category, p.stock_quantity, 
                   p.selling_price, p.cost_price, p.min_stock_level,
                   p.expire_date, p.created_date, p.vendor
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.client_id = ?
            ORDER BY p.name
        """;
        
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, currentClientId);
            ResultSet rs = stmt.executeQuery();
              
            while (rs.next()) {
                StockItem item = new StockItem();
                item.id = rs.getInt("id");
                item.name = rs.getString("name");
                item.category = rs.getString("category");
                item.quantity = rs.getInt("stock_quantity");
                item.sellingPrice = rs.getBigDecimal("selling_price");
                item.costPrice = rs.getBigDecimal("cost_price");
                item.minStockLevel = rs.getInt("min_stock_level");
                item.expireDate = Database.fromDatabaseDate(rs.getString("expire_date"));
                item.createdDate = Database.fromDatabaseDate(rs.getString("created_date"));
                item.vendor = rs.getString("vendor");
                
                stockItems.add(item);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Failed to load data: " + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateAllViews() {
        updateStockTable();
        updateAnalyticsView();
        updateInventoryHealthView();
        updateTopProductsView();
    }

    private void updateStockTable() {
        String viewMode = (String) viewModeBox.getSelectedItem();
        boolean useSellingPrice = priceOptionBox.getSelectedIndex() == 0;
        String currencyFull = (String) currencyBox.getSelectedItem();
        String currency = currencyFull.split(" - ")[0];
        
        DefaultTableModel model = new DefaultTableModel();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int totalItems = 0;
        int lowStockCount = 0;
        
        if ("By Category".equals(viewMode)) {
            model.addColumn("Category");
            model.addColumn("Items");
            model.addColumn("Total Quantity");
            model.addColumn("Total Value");
            model.addColumn("Avg. Unit Value");
            
            Map<String, CategoryData> categoryMap = new HashMap<>();
            
            for (StockItem item : stockItems) {
                String category = item.category != null ? item.category : "Uncategorized";
                BigDecimal price = useSellingPrice ? item.sellingPrice : item.costPrice;
                BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(item.quantity));
                
                CategoryData data = categoryMap.getOrDefault(category, new CategoryData());
                data.itemCount++;
                data.totalQuantity += item.quantity;
                data.totalValue = data.totalValue.add(itemTotal);
                categoryMap.put(category, data);
                
                if (item.quantity <= item.minStockLevel) {
                    lowStockCount++;
                }
            }
            
            for (Map.Entry<String, CategoryData> entry : categoryMap.entrySet()) {
                CategoryData data = entry.getValue();
                BigDecimal avgValue = data.totalQuantity > 0 ? 
                    data.totalValue.divide(BigDecimal.valueOf(data.totalQuantity), 2, BigDecimal.ROUND_HALF_UP) : 
                    BigDecimal.ZERO;
                
                model.addRow(new Object[]{
                    entry.getKey(),
                    data.itemCount,
                    data.totalQuantity,
                    currency + " " + String.format("%,.2f", data.totalValue),
                    currency + " " + String.format("%,.2f", avgValue)
                });
                
                grandTotal = grandTotal.add(data.totalValue);
                totalItems += data.itemCount;
            }
            
        } else if ("By Product".equals(viewMode)) {
            model.addColumn("Product");
            model.addColumn("Category");
            model.addColumn("Quantity");
            model.addColumn("Unit Price");
            model.addColumn("Total Value");
            model.addColumn("Status");
            
            for (StockItem item : stockItems) {
                BigDecimal price = useSellingPrice ? item.sellingPrice : item.costPrice;
                BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(item.quantity));
                
                String status = item.quantity <= item.minStockLevel ? "⚠ Low Stock" : "✓ OK";
                if (item.quantity <= item.minStockLevel) {
                    lowStockCount++;
                }
                
                model.addRow(new Object[]{
                    item.name,
                    item.category,
                    item.quantity,
                    currency + " " + String.format("%,.2f", price),
                    currency + " " + String.format("%,.2f", itemTotal),
                    status
                });
                
                grandTotal = grandTotal.add(itemTotal);
                totalItems++;
            }
            
        } else {
            model.addColumn("Product");
            model.addColumn("Category");
            model.addColumn("Current Stock");
            model.addColumn("Min Level");
            model.addColumn("Shortage");
            model.addColumn("Value Impact");
            
            for (StockItem item : stockItems) {
                if (item.quantity <= item.minStockLevel) {
                    BigDecimal price = useSellingPrice ? item.sellingPrice : item.costPrice;
                    int shortage = item.minStockLevel - item.quantity;
                    BigDecimal valueImpact = price.multiply(BigDecimal.valueOf(shortage));
                    
                    model.addRow(new Object[]{
                        item.name,
                        item.category,
                        item.quantity,
                        item.minStockLevel,
                        shortage,
                        currency + " " + String.format("%,.2f", valueImpact)
                    });
                    
                    lowStockCount++;
                }
            }
        }
        
        stockTable.setModel(model);
        totalAmountLabel.setText(currency + " " + String.format("%,.2f", grandTotal));
        totalItemsLabel.setText(String.valueOf(totalItems));
        lowStockCountLabel.setText(String.valueOf(lowStockCount));

        if (totalValueField != null) {
            totalValueField.setText(currency + " " + String.format("%,.2f", grandTotal));
        }

        for (int i = 0; i < stockTable.getColumnCount(); i++) {
            stockTable.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
    }
    
    private void updateAnalyticsView() {
        String range = (String) dateRangeBox.getSelectedItem();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        
        switch (range) {
            case "Last 7 Days":
                startDate = endDate.minusDays(7);
                break;
            case "Last 30 Days":
                startDate = endDate.minusDays(30);
                break;
            case "Last 90 Days":
                startDate = endDate.minusDays(90);
                break;
            default:
                startDate = LocalDate.of(endDate.getYear(), 1, 1);
        }
        
        try {
            List<Database.SalesData> salesData = Database.getSalesData(
                Database.toDatabaseDate(startDate),
                Database.toDatabaseDate(endDate)
            );
            
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalProfit = BigDecimal.ZERO;
            
            for (Database.SalesData data : salesData) {
                totalRevenue = totalRevenue.add(data.getTotal());
                totalProfit = totalProfit.add(data.getProfit());
            }
            
            BigDecimal profitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                totalProfit.divide(totalRevenue, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;
            
            String currencyFull = (String) currencyBox.getSelectedItem();
            String currency = currencyFull.split(" - ")[0];
            totalRevenueLabel.setText(currency + " " + String.format("%,.2f", totalRevenue));
            totalProfitLabel.setText(currency + " " + String.format("%,.2f", totalProfit));
            profitMarginLabel.setText(String.format("%.1f%%", profitMargin));
            
            updateAnalyticsChart();
            updateRecentTransactionsTable(startDate, endDate);
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateAnalyticsChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        try {
            String range = (String) dateRangeBox.getSelectedItem();
            LocalDate endDate = LocalDate.now();
            LocalDate startDate;
            
            switch (range) {
                case "Last 7 Days":
                    startDate = endDate.minusDays(7);
                    break;
                case "Last 30 Days":
                    startDate = endDate.minusDays(30);
                    break;
                case "Last 90 Days":
                    startDate = endDate.minusDays(90);
                    break;
                default:
                    startDate = LocalDate.of(endDate.getYear(), 1, 1);
            }
            
            List<Database.SalesData> salesData = Database.getSalesData(
                Database.toDatabaseDate(startDate),
                Database.toDatabaseDate(endDate)
            );
            
            for (Database.SalesData data : salesData) {
                String dateLabel = data.getDate().format(DateTimeFormatter.ofPattern("MMM dd"));
                dataset.addValue(data.getTotal(), "Sales", dateLabel);
                dataset.addValue(data.getProfit(), "Profit", dateLabel);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        JFreeChart chart = ChartFactory.createLineChart(
            "",
            "Date",
            "Amount",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(BORDER_COLOR);
        
        ChartPanel chartPanelComponent = new ChartPanel(chart);
        chartPanelComponent.setPreferredSize(new Dimension(400, 250));
        
        chartPanel.removeAll();
        chartPanel.add(chartPanelComponent, BorderLayout.CENTER);
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    private void updateRecentTransactionsTable(LocalDate startDate, LocalDate endDate) {
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Date");
        model.addColumn("Total Sales");
        model.addColumn("Profit");
        model.addColumn("Margin %");
        
        try {
            List<Database.SalesData> salesData = Database.getSalesData(
                Database.toDatabaseDate(startDate),
                Database.toDatabaseDate(endDate)
            );
            
            String currencyFull = (String) currencyBox.getSelectedItem();
            String currency = currencyFull.split(" - ")[0];
            
            for (Database.SalesData data : salesData) {
                BigDecimal margin = data.getTotal().compareTo(BigDecimal.ZERO) > 0 ?
                    data.getProfit().divide(data.getTotal(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                model.addRow(new Object[]{
                    data.getDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                    currency + " " + String.format("%,.2f", data.getTotal()),
                    currency + " " + String.format("%,.2f", data.getProfit()),
                    String.format("%.1f%%", margin)
                });
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        analyticsTable.setModel(model);
    }

    private void updateInventoryHealthView() {
        int totalProducts = stockItems.size();
        int lowStockCount = 0;
        int expiringCount = 0;
        int deadStockCount = 0;
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);
        
        for (StockItem item : stockItems) {
            if (item.quantity <= item.minStockLevel) {
                lowStockCount++;
            }
            
            if (item.expireDate != null && item.expireDate.isBefore(thirtyDaysFromNow)) {
                expiringCount++;
            }
            
            if (item.quantity == 0) {
                deadStockCount++;
            }
        }
        
        int healthScore = totalProducts > 0 ? 
            (int)((double)(totalProducts - lowStockCount - expiringCount) / totalProducts * 100) : 
            100;
        
        stockHealthBar.setValue(healthScore);
        stockHealthBar.setString(healthScore + "% Healthy");
        
        if (healthScore >= 80) {
            stockHealthBar.setForeground(SUCCESS_COLOR);
        } else if (healthScore >= 50) {
            stockHealthBar.setForeground(WARNING_COLOR);
        } else {
            stockHealthBar.setForeground(DANGER_COLOR);
        }
        
        expiringItemsLabel.setText(String.valueOf(expiringCount));
        deadStockLabel.setText(String.valueOf(deadStockCount));
        
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Product");
        model.addColumn("Category");
        model.addColumn("Stock");
        model.addColumn("Status");
        model.addColumn("Days Until Expiry");
        model.addColumn("Action Required");
        
        for (StockItem item : stockItems) {
            String status = "✓ Normal";
            String action = "None";
            long daysUntilExpiry = -1;
            
            if (item.quantity <= item.minStockLevel) {
                status = "⚠ Low Stock";
                action = "Reorder";
            }
            
            if (item.expireDate != null) {
                daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, item.expireDate);
                if (daysUntilExpiry < 30 && daysUntilExpiry >= 0) {
                    status = "⚠ Expiring Soon";
                    action = "Promote/Discount";
                } else if (daysUntilExpiry < 0) {
                    status = "❌ Expired";
                    action = "Remove";
                }
            }
            
            if (item.quantity == 0) {
                status = "📦 Out of Stock";
                action = "Urgent Reorder";
            }
            
            if (!status.equals("✓ Normal")) {
                model.addRow(new Object[]{
                    item.name,
                    item.category,
                    item.quantity,
                    status,
                    daysUntilExpiry >= 0 ? daysUntilExpiry + " days" : "N/A",
                    action
                });
            }
        }
        
        inventoryHealthTable.setModel(model);
    }

    private void updateTopProductsView() {
        String period = (String) topProductsRangeBox.getSelectedItem();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        
        switch (period) {
            case "Today":
                startDate = endDate;
                break;
            case "This Week":
                startDate = endDate.minusDays(7);
                break;
            case "This Month":
                startDate = endDate.minusDays(30);
                break;
            default:
                startDate = LocalDate.of(endDate.getYear(), 1, 1);
        }
        
        try {
            List<Database.TopSellingProduct> topProducts = Database.getTopSellingProducts(
                Database.toDatabaseDate(startDate),
                Database.toDatabaseDate(endDate),
                10
            );
            
            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("Rank");
            model.addColumn("Product");
            model.addColumn("Units Sold");
            model.addColumn("Revenue");
            model.addColumn("Market Share %");
            
            String currencyFull = (String) currencyBox.getSelectedItem();
            String currency = currencyFull.split(" - ")[0];
            BigDecimal totalRevenue = BigDecimal.ZERO;
            
            for (Database.TopSellingProduct product : topProducts) {
                totalRevenue = totalRevenue.add(product.totalRevenue);
            }
            
            int rank = 1;
            for (Database.TopSellingProduct product : topProducts) {
                BigDecimal marketShare = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                    product.totalRevenue.divide(totalRevenue, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO;
                
                model.addRow(new Object[]{
                    rank++,
                    product.productName,
                    product.totalQuantitySold,
                    currency + " " + String.format("%,.2f", product.totalRevenue),
                    String.format("%.1f%%", marketShare)
                });
            }
            
            topProductsTable.setModel(model);
            
            DefaultPieDataset dataset = new DefaultPieDataset();
            int count = 0;
            for (Database.TopSellingProduct product : topProducts) {
                if (count < 5) {
                    dataset.setValue(product.productName, product.totalRevenue);
                }
                count++;
            }
            
            JFreeChart chart = ChartFactory.createPieChart(
                "Top 5 Products by Revenue",
                dataset,
                true,
                true,
                false
            );
            
            chart.setBackgroundPaint(Color.WHITE);
            PiePlot plot = (PiePlot) chart.getPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setOutlineVisible(false);
            
            ChartPanel chartPanelComponent = new ChartPanel(chart);
            
            topProductsChartPanel.removeAll();
            topProductsChartPanel.add(chartPanelComponent, BorderLayout.CENTER);
            topProductsChartPanel.revalidate();
            topProductsChartPanel.repaint();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshAllData() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loadDataFromDatabase();
        updateAllViews();
        loadFinancialSummary();
        setCursor(Cursor.getDefaultCursor());
        JOptionPane.showMessageDialog(this, 
            "Dashboard refreshed successfully!", 
            "Refresh Complete", 
            JOptionPane.INFORMATION_MESSAGE);
    }

    static class StockItem {
        int id;
        String name;
        String category;
        int quantity;
        BigDecimal sellingPrice;
        BigDecimal costPrice;
        int minStockLevel;
        LocalDate expireDate;
        LocalDate createdDate;
        String vendor;
    }

    static class CategoryData {
        int itemCount = 0;
        int totalQuantity = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
    }
}