package hIMs.App;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileWriter;
import javax.swing.Timer;
import java.awt.print.PrinterJob;
import java.awt.print.Printable;
import javax.swing.border.TitledBorder;
import java.text.NumberFormat;
import java.util.List;
import java.util.logging.Logger;

public class Report extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(Report.class.getName());
    private static final Color PRIMARY_COLOR = new Color(79, 70, 229);
    private static final Color SECONDARY_COLOR = new Color(99, 102, 241);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color WARNING_COLOR = new Color(251, 146, 60);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final Color INFO_COLOR = new Color(59, 130, 246);
    private static final Color SURFACE_COLOR = new Color(248, 250, 252);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(15, 23, 42);
    private static final Color TEXT_SECONDARY = new Color(100, 116, 139);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);

    private JPanel contentPanel;
    private JPanel sidebarPanel;
    private JTabbedPane mainTabbedPane;
    private JToolBar toolBar;
    private JLabel statusBar;
    private JPanel mainPanel;
    private JTable transactionTable;
    private JProgressBar loadingProgressBar;
    private JComboBox<String> periodComboBox;
    private JComboBox<String> currencyComboBox;
    private JComboBox<String> dateFormatComboBox;
    private JList<String> recentActivityList;
    private DefaultListModel<String> activityListModel;
    private DefaultTableModel transactionTableModel;
    private Timer refreshTimer;
    private String selectedCurrency = "";
    private JPanel dashboardPanel;
    private JPanel largeLabelsPanel;
    private HistogramWithDensityPanel histogramPanel;
    private JTextArea reportTextArea;
    private JLabel transactionsTotalLabel;

    private static final Map<String, String> CURRENCY_SYMBOLS = new HashMap<>();
    static {
        CURRENCY_SYMBOLS.put("TSH", "TSH ");
        CURRENCY_SYMBOLS.put("USD", "$");
        CURRENCY_SYMBOLS.put("EUR", "€");
        CURRENCY_SYMBOLS.put("GBP", "£");
    }

    private static final Map<String, Color> STAT_COLORS = new LinkedHashMap<>();
    static {
        STAT_COLORS.put("Total Sales", SUCCESS_COLOR);
        STAT_COLORS.put("Gross Sale", SUCCESS_COLOR);
        STAT_COLORS.put("Discounts", WARNING_COLOR);
        STAT_COLORS.put("Expenses", WARNING_COLOR);
        STAT_COLORS.put("Cost of Goods", INFO_COLOR);
        STAT_COLORS.put("Net Profit", PRIMARY_COLOR);
    }

    public Report(JFrame parent) {
        super(parent, "PoS Financial Dashboard", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(parent);
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            LOGGER.severe("Failed to set FlatLaf: " + e.getMessage());
        }
        initializeUI();
        initializeData();
        startRealTimeUpdates();
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(SURFACE_COLOR);
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(SURFACE_COLOR);
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainTabbedPane = new JTabbedPane();
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        periodComboBox = new JComboBox<>(new String[]{"Today", "This Week", "This Month", "Last Month", "This Quarter", "This Year"});
        currencyComboBox = new JComboBox<>(new String[]{"TSH", "USD", "EUR", "GBP"});
        dateFormatComboBox = new JComboBox<>(new String[]{"MM-dd-yyyy", "dd-MM-yyyy", "yyyy-MM-dd"});
        activityListModel = new DefaultListModel<>();
        recentActivityList = new JList<>(activityListModel);
        recentActivityList.setPreferredSize(new Dimension(200, 100));
        transactionTableModel = new DefaultTableModel(new String[]{"Date", "Product", "Quantity", "Unit Price", "Total Price"}, 0);
        transactionTable = new JTable(transactionTableModel);
        loadingProgressBar = new JProgressBar();
        reportTextArea = new JTextArea();
        reportTextArea.setEditable(false);
        transactionsTotalLabel = new JLabel("Total:  0.00");

        createSidebar();
        createToolbar();
        createStatusBar();
        createMainTabs();
        mainPanel.add(toolBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        add(mainPanel);
    }

    private void createSidebar() {
        sidebarPanel.add(Box.createVerticalStrut(10));
        sidebarPanel.add(createEnhancedHeader());
        sidebarPanel.add(Box.createVerticalStrut(10));
        sidebarPanel.add(createQuickStatsPanel());
        sidebarPanel.add(Box.createVerticalStrut(20));
        JPanel recentActivityPanel = new JPanel(new BorderLayout());
        recentActivityPanel.setBorder(BorderFactory.createTitledBorder("Recent Activity"));
        recentActivityPanel.setBackground(CARD_COLOR);
        recentActivityList.setBackground(CARD_COLOR);
        JScrollPane activityScrollPane = new JScrollPane(recentActivityList);
        activityScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        recentActivityPanel.add(activityScrollPane, BorderLayout.CENTER);
        sidebarPanel.add(recentActivityPanel);
        sidebarPanel.add(Box.createVerticalStrut(10));
        sidebarPanel.add(createEnhancedFooter());
        contentPanel.add(sidebarPanel, BorderLayout.WEST);
    }

    private JLabel createEnhancedHeader() {
        JLabel headerLabel = new JLabel("<html><div style='text-align: center;'>" +
            "<span style='font-size: 22px; font-weight: bold; color: #4F46E5;'>PoS</span><br>" +
            "<span style='font-size: 14px; color: #64748B;'>Financial Dashboard</span><br>" +
            "<span style='font-size: 11px; color: #94A3B8;'>Enhanced Edition v2.1</span>" +
            "</div></html>");
        headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        return headerLabel;
    }

    private JPanel createEnhancedFooter() {
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(CARD_COLOR);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(12, 0, 0, 0)
        ));
        JLabel footerLabel = new JLabel("<html><div style='text-align: center;'>" +
            "<span style='font-size: 11px; color: #64748B;'>© 2025 PoS System<br>" +
            "Created by Yohana v2.1<br>" +
            "Last Update: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "</span>" +
            "</div></html>");
        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        footerPanel.add(footerLabel);
        return footerPanel;
    }

    private JPanel createQuickStatsPanel() {
        JPanel statsPanel = new JPanel();
        statsPanel.setBackground(SURFACE_COLOR);
        statsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Quick Stats",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12),
            TEXT_PRIMARY
        ));

        try {
            String period = (String) periodComboBox.getSelectedItem();
            LocalDate[] dates = getDateRangeForPeriod(period);
            String startDate = dates[0].toString();
            String endDate = dates[1].toString();

            // Previous period for trends
            LocalDate[] prevDates = getDateRangeForPeriod(period);
            long periodDays = prevDates[1].toEpochDay() - prevDates[0].toEpochDay() + 1;
            prevDates[0] = prevDates[0].minusDays(periodDays);
            prevDates[1] = prevDates[1].minusDays(periodDays);
            String prevStartDate = prevDates[0].toString();
            String prevEndDate = prevDates[1].toString();

            // Fetch data from database
            BigDecimal totalSales = Database.getTotalSales(startDate, endDate); // getdailyTransaction
            BigDecimal costOfGoods = Database.getCostOfGoods(startDate, endDate); // getcostofgoodsold
            BigDecimal discounts = Database.getTotalDiscounts(startDate, endDate); // getdiscounts
            BigDecimal expenses = Database.getTotalExpenses(startDate, endDate); // getdailyexpenses

            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            costOfGoods = costOfGoods != null ? costOfGoods : BigDecimal.ZERO;
            discounts = discounts != null ? discounts : BigDecimal.ZERO;
            expenses = expenses != null ? expenses : BigDecimal.ZERO;

            // Previous period data
            BigDecimal prevTotalSales = Database.getTotalSales(prevStartDate, prevEndDate);
            BigDecimal prevCostOfGoods = Database.getCostOfGoods(prevStartDate, prevEndDate);
            BigDecimal prevDiscounts = Database.getTotalDiscounts(prevStartDate, prevEndDate);
            BigDecimal prevExpenses = Database.getTotalExpenses(prevStartDate, prevEndDate);

            prevTotalSales = prevTotalSales != null ? prevTotalSales : BigDecimal.ZERO;
            prevCostOfGoods = prevCostOfGoods != null ? prevCostOfGoods : BigDecimal.ZERO;
            prevDiscounts = prevDiscounts != null ? prevDiscounts : BigDecimal.ZERO;
            prevExpenses = prevExpenses != null ? prevExpenses : BigDecimal.ZERO;

            // Calculate profit and net profit
            BigDecimal profit = totalSales.subtract(costOfGoods); // Profit = Total Sales - Cost of Goods
            BigDecimal netProfit = profit.subtract(discounts.add(expenses)); // Net Profit = Profit - (Discounts + Expenses)

            BigDecimal prevProfit = prevTotalSales.subtract(prevCostOfGoods);
            BigDecimal prevNetProfit = prevProfit.subtract(prevDiscounts.add(prevExpenses));

            // Trends
            String totalSalesTrend = calculateTrend(totalSales, prevTotalSales);
            String costOfGoodsTrend = calculateTrend(costOfGoods, prevCostOfGoods);
            String discountsTrend = calculateTrend(discounts, prevDiscounts);
            String expensesTrend = calculateTrend(expenses, prevExpenses);
            String profitTrend = calculateTrend(profit, prevProfit);
            String netProfitTrend = calculateTrend(netProfit, prevNetProfit);

            // Set layout: 2 rows, 3 columns
            statsPanel.setLayout(new GridLayout(2, 3, 8, 8));

            // Create cards with distinct background colors
            statsPanel.add(createEnhancedStatCard("Total Sales", formatCurrency(totalSales), totalSalesTrend, new Color(198, 239, 206), SUCCESS_COLOR));
            statsPanel.add(createEnhancedStatCard("Cost of Goods", formatCurrency(costOfGoods), costOfGoodsTrend, new Color(255, 235, 156), TEXT_PRIMARY));
            statsPanel.add(createEnhancedStatCard("Discounts", formatCurrency(discounts), discountsTrend, new Color(255, 199, 206), ERROR_COLOR));
            statsPanel.add(createEnhancedStatCard("Expenses", formatCurrency(expenses), expensesTrend, new Color(255, 235, 156), WARNING_COLOR));
            statsPanel.add(createEnhancedStatCard("Profit", formatCurrency(profit), profitTrend, new Color(198, 239, 206), SUCCESS_COLOR));

            // Net Profit or Net Loss card
            if (netProfit.compareTo(BigDecimal.ZERO) >= 0) {
                statsPanel.add(createEnhancedStatCard("Net Profit", formatCurrency(netProfit), netProfitTrend, new Color(198, 239, 206), SUCCESS_COLOR));
            } else {
                statsPanel.add(createEnhancedStatCard("Net Loss", formatCurrency(netProfit.abs()), netProfitTrend, new Color(255, 199, 206), ERROR_COLOR));
            }

        } catch (SQLException e) {
            LOGGER.severe("Error loading quick stats: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error loading quick stats: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return statsPanel;
    }


    private JPanel createEnhancedStatCard(String title, String value, String trend, Color backgroundColor, Color fontColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(backgroundColor);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel valueLabel = new JLabel(value + trend);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valueLabel.setForeground(fontColor);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    
    
    private JLabel createLargeLabel(String title, String value, Color color) {
        JLabel label = new JLabel(title + ": " + value);
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(color);
        label.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        return label;
    }

    private String calculateTrend(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return "";
        }
        int comparison = current.compareTo(previous);
        if (comparison > 0) {
            return " ↑";
        } else if (comparison < 0) {
            return " ↓";
        } else {
            return " →";
        }
    }

    private void createToolbar() {
        toolBar.setBackground(SURFACE_COLOR);
        toolBar.add(createStyledButton("Refresh", PRIMARY_COLOR, e -> refreshAllData()));
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(new JLabel("Period:"));
        toolBar.add(periodComboBox);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(new JLabel("Currency:"));
        toolBar.add(currencyComboBox);
        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(new JLabel("Date Format:"));
        toolBar.add(dateFormatComboBox);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(loadingProgressBar);
        loadingProgressBar.setStringPainted(true);
        loadingProgressBar.setString("");
        periodComboBox.addActionListener(e -> updatePeriodData());
        currencyComboBox.addActionListener(e -> {
            selectedCurrency = (String) currencyComboBox.getSelectedItem();
            updatePeriodData();
        });
        dateFormatComboBox.addActionListener(e -> updatePeriodData());
    }

    private JButton createStyledButton(String text, Color color, ActionListener listener) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        if (listener != null) {
            button.addActionListener(listener);
        }
        return button;
    }

    private JButton createStyledButton(String text, Color color) {
        return createStyledButton(text, color, null);
    }

    private void createMainTabs() {
        mainTabbedPane.addTab("Dashboard", createDashboardTab());
        mainTabbedPane.addTab("Transactions", createTransactionsTab());
        mainTabbedPane.addTab("Expenditure", createExpenditureTab());
        mainTabbedPane.addTab("Reports", createReportsTab());
        mainTabbedPane.addTab("Settings", createSettingsTab());
        contentPanel.add(mainTabbedPane, BorderLayout.CENTER);
    }

    private JPanel createExpenditureTab() {
        JPanel expPanel = new JPanel(new BorderLayout());
        expPanel.setBackground(SURFACE_COLOR);
        expPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        List<String> expenseCategories = getExpenseCategories();
        List<String> defaultCategories = Arrays.asList("Electricity Bill", "Water Bill", "Garbage tax", "Rent Bill",
            "Employees Wages", "Government taxes", "Investments", "Daily Expenses");
        if (expenseCategories.isEmpty()) {
            expenseCategories.addAll(defaultCategories);
        } else {
            for (String defaultCategory : defaultCategories) {
                if (!expenseCategories.contains(defaultCategory)) {
                    expenseCategories.add(defaultCategory);
                }
            }
        }

        JComboBox<String> expCombo = new JComboBox<>(expenseCategories.toArray(new String[0]));
        expCombo.addActionListener(e -> {
            String selected = (String) expCombo.getSelectedItem();
            if (selected != null && !selected.isEmpty() && !selected.equals("No categories found")) {
                showExpenditureDialog(selected);
            }
        });

        JButton addCategoryButton = createStyledButton("Add New Category", PRIMARY_COLOR, e -> showAddCategoryDialog());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBackground(SURFACE_COLOR);
        controlPanel.add(new JLabel("Select Expenditure Category:"));
        controlPanel.add(expCombo);
        controlPanel.add(addCategoryButton);

        DefaultTableModel expenseTableModel = new DefaultTableModel(new String[]{"Date", "Category", "Amount"}, 0);
        JTable expenseTable = new JTable(expenseTableModel);
        JScrollPane tableScrollPane = new JScrollPane(expenseTable);
        tableScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        updateExpenseTable(expenseTableModel);

        expPanel.add(controlPanel, BorderLayout.NORTH);
        expPanel.add(tableScrollPane, BorderLayout.CENTER);
        return expPanel;
    }

    private List<String> getExpenseCategories() {
        List<String> categories = new ArrayList<>();
        try {
            String yearMonth = YearMonth.now().toString();
            Map<String, BigDecimal> expenses = Database.getMonthlyExpensesByCategory(yearMonth);
            categories.addAll(expenses.keySet());
            if (categories.isEmpty()) {
                String sql = "SELECT DISTINCT description FROM daily_expenses ORDER BY description";
                try (Connection conn = Database.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String desc = rs.getString("description");
                        if (desc != null && !desc.trim().isEmpty()) {
                            categories.add(desc);
                        }
                    }
                }
                if (categories.isEmpty()) {
                    Collections.addAll(categories,
                        "Electricity Bill", "Water Bill", "Garbage tax", "Rent Bill",
                        "Employees Wages", "Government taxes", "Investments", "Daily Expenses");
                    for (String category : categories) {
                        Database.addOrUpdateMonthlyExpense(yearMonth, category, BigDecimal.ZERO);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error fetching expense categories: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error fetching expense categories: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return categories;
    }

    private void showAddCategoryDialog() {
        JDialog dialog = new JDialog(this, "Add New Expense Category", true);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(this);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel label = new JLabel("Category Name:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(label, gbc);

        JTextField categoryField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(categoryField, gbc);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            String category = categoryField.getText().trim();
            if (!category.isEmpty()) {
                try {
                    Database.addOrUpdateMonthlyExpense(YearMonth.now().toString(), category, BigDecimal.ZERO);
                    updateActivityLog("Added new expense category: " + category);
                    mainTabbedPane.setComponentAt(mainTabbedPane.indexOfTab("Expenditure"), createExpenditureTab());
                    dialog.dispose();
                } catch (SQLException ex) {
                    LOGGER.severe("Error adding new expense category: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Error adding category: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Category name cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(okBtn, gbc);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(closeBtn, gbc);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void showExpenditureDialog(String category) {
        JDialog dialog = new JDialog(this, category, true);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel label = new JLabel("Enter Amount:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(label, gbc);

        SpinnerNumberModel model = new SpinnerNumberModel(0.0, 0.0, 999999.99, 0.01);
        JSpinner spinner = new JSpinner(model);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(spinner, gbc);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(ev -> {
            BigDecimal amount = BigDecimal.valueOf((Double) spinner.getValue());
            dialog.dispose();
            showFinancialExpenditureDialog(category, amount);
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(okBtn, gbc);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(ev -> dialog.dispose());
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(closeBtn, gbc);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void showFinancialExpenditureDialog(String category, BigDecimal amount) {
        JDialog dialog = new JDialog(this, "Financial Expenditure - Summary", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Table to display all expenditures
        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Expenditure Identity", "Sub Amount"}, 0);
        JTable table = new JTable(tableModel);
        JScrollPane scroll = new JScrollPane(table);
        mainPanel.add(scroll, BorderLayout.CENTER);

        // Add the current expenditure
        tableModel.addRow(new Object[]{category, formatCurrency(amount)});

        // Fetch existing expenditures for the period
        try {
            String period = (String) periodComboBox.getSelectedItem();
            LocalDate[] dates = getDateRangeForPeriod(period);
            String startDate = dates[0].toString();
            String endDate = dates[1].toString();

            // Query expenses, adjusting for month-based storage
            String sql = "SELECT description, amount FROM daily_expenses WHERE DATE(expense_date) BETWEEN ? AND ? AND description != ?";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, startDate);
                stmt.setString(2, endDate);
                stmt.setString(3, category); // Exclude the current category to avoid duplication
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                        rs.getString("description"),
                        formatCurrency(rs.getBigDecimal("amount"))
                    });
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error fetching existing expenditures: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error fetching expenditures: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Calculate total
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String amountStr = tableModel.getValueAt(i, 1).toString().replaceAll("[^\\d.,-]", "").replace(",", "");
            try {
                total = total.add(new BigDecimal(amountStr));
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid amount format: " + amountStr);
            }
        }

        JLabel totalLabel = new JLabel("Total Amount: " + formatCurrency(total));
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(totalLabel, BorderLayout.WEST);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(ev -> {
            String date = LocalDate.now().toString(); // Use current date
            try {
                Database.addOrUpdateMonthlyExpense(date, category, amount);
                updateActivityLog("Added/Updated expenditure: " + category + " - " + formatCurrency(amount));
                mainTabbedPane.setComponentAt(mainTabbedPane.indexOfTab("Expenditure"), createExpenditureTab());
                refreshAllData();
            } catch (SQLException ex) {
                LOGGER.severe("Error saving expenditure: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error saving expenditure: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            dialog.dispose();
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(ev -> dialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(okBtn);
        btnPanel.add(closeBtn);
        southPanel.add(btnPanel, BorderLayout.EAST);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private void updateExpenseTable(DefaultTableModel tableModel) {
        tableModel.setRowCount(0);
        try {
            String period = (String) periodComboBox.getSelectedItem();
            LocalDate[] dates = getDateRangeForPeriod(period);
            String startDate = dates[0].toString();
            String endDate = dates[1].toString();
            String datePattern = (String) dateFormatComboBox.getSelectedItem();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern != null ? datePattern : "yyyy-MM-dd");

            String sql = "SELECT expense_date, description, amount FROM daily_expenses WHERE DATE(expense_date) BETWEEN ? AND ? ORDER BY expense_date DESC";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, startDate);
                stmt.setString(2, endDate);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    LocalDate expenseDate = Database.fromDatabaseDate(rs.getString("expense_date"));
                    tableModel.addRow(new Object[]{
                        expenseDate != null ? expenseDate.format(formatter) : "N/A",
                        rs.getString("description"),
                        formatCurrency(rs.getBigDecimal("amount"))
                    });
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error updating expense table: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error updating expense table: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createDashboardTab() {
        dashboardPanel = new JPanel(new BorderLayout());
        dashboardPanel.setBackground(SURFACE_COLOR);
        dashboardPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel headerPanel = createDashboardHeader();
        dashboardPanel.add(headerPanel, BorderLayout.NORTH);
        JPanel mainDashboard = new JPanel(new BorderLayout(16, 16));
        mainDashboard.setBackground(SURFACE_COLOR);
        JPanel statsPanel = createQuickStatsPanel();
        mainDashboard.add(statsPanel, BorderLayout.NORTH);
        largeLabelsPanel = new JPanel();
        largeLabelsPanel.setLayout(new BoxLayout(largeLabelsPanel, BoxLayout.Y_AXIS));
        largeLabelsPanel.setBackground(SURFACE_COLOR);
        mainDashboard.add(largeLabelsPanel, BorderLayout.CENTER);
        histogramPanel = new HistogramWithDensityPanel(dateFormatComboBox, this);
        histogramPanel.setPreferredSize(new Dimension(0, 300));
        histogramPanel.setBackground(CARD_COLOR);
        mainDashboard.add(histogramPanel, BorderLayout.SOUTH);
        dashboardPanel.add(mainDashboard, BorderLayout.CENTER);
        return dashboardPanel;
    }

    private JPanel createDashboardHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(SURFACE_COLOR);
        JLabel titleLabel = new JLabel("Financial Dashboard");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        return headerPanel;
    }

    private JPanel createTransactionsTab() {
        JPanel transactionsPanel = new JPanel(new BorderLayout());
        transactionsPanel.setBackground(SURFACE_COLOR);
        transactionsPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JScrollPane tableScrollPane = new JScrollPane(transactionTable);
        tableScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        transactionsPanel.add(tableScrollPane, BorderLayout.CENTER);
        JPanel totalPanel = new JPanel(new BorderLayout());
        totalPanel.setBackground(SURFACE_COLOR);
        totalPanel.add(transactionsTotalLabel, BorderLayout.EAST);
        transactionsPanel.add(totalPanel, BorderLayout.SOUTH);
        return transactionsPanel;
    }

    private JPanel createReportsTab() {
        JPanel reportsPanel = new JPanel(new BorderLayout());
        reportsPanel.setBackground(SURFACE_COLOR);
        reportsPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel reportCardsPanel = new JPanel(new GridLayout(2, 2, 16, 16));
        reportCardsPanel.setBackground(SURFACE_COLOR);
        reportCardsPanel.add(createReportCard("Daily Report", "Generate a report for today’s sales and expenses", this::generateDailyReport));
        reportCardsPanel.add(createReportCard("Weekly Report", "Generate a report for this week’s performance", this::generateWeeklyReport));
        reportCardsPanel.add(createReportCard("Monthly Report", "Generate a report for this month’s performance", this::generateMonthlyReport));
        reportCardsPanel.add(createReportCard("Annual Report", "Generate a report for this year’s performance", this::generateAnnualReport));
        reportsPanel.add(reportCardsPanel, BorderLayout.NORTH);
        JScrollPane reportScrollPane = new JScrollPane(reportTextArea);
        reportScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        reportsPanel.add(reportScrollPane, BorderLayout.CENTER);
        JPanel exportPanel = new JPanel();
        exportPanel.setBackground(SURFACE_COLOR);
        exportPanel.add(createStyledButton("Export to PDF", PRIMARY_COLOR, e -> exportToPDF()));
        exportPanel.add(createStyledButton("Export to Text", SECONDARY_COLOR, e -> exportToText()));
        exportPanel.add(createStyledButton("Print Report", INFO_COLOR, e -> printReport()));
        reportsPanel.add(exportPanel, BorderLayout.SOUTH);
        return reportsPanel;
    }

    private JPanel createSettingsTab() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setBackground(SURFACE_COLOR);
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        settingsPanel.add(new JLabel("Settings: Currency and Date Format"));
        settingsPanel.add(currencyComboBox);
        settingsPanel.add(dateFormatComboBox);
        return settingsPanel;
    }

    private void createStatusBar() {
        statusBar = new JLabel();
        statusBar.setBackground(CARD_COLOR);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));
        statusBar.setPreferredSize(new Dimension(0, 25));
        statusBar.setText("Ready | " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        statusBar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.setForeground(TEXT_PRIMARY);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    private void initializeData() {
        try {
            Database.initializeDatabase();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    updatePeriodData();
                    updateQuickStatsPanel();
                    updateMonthlyExpensesLabel();
                    updateActivityLog("System initialized successfully");
                    return null;
                }
            }.execute();
        } catch (SQLException e) {
            LOGGER.severe("Error initializing data: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error initializing data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startRealTimeUpdates() {
        refreshTimer = new Timer(300000, e -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                updateStatusBar("Refreshing data...");
                updatePeriodData();
                updateMonthlyExpensesLabel();
                return null;
            }
        }.execute());
        refreshTimer.start();
    }

    private void updateStatusBar(String message) {
        if (statusBar != null) {
            SwingUtilities.invokeLater(() -> statusBar.setText(message + " | " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        }
    }

    public void refreshData() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                updateStatusBar("Refreshing data due to new sale...");
                updatePeriodData();
                updateMonthlyExpensesLabel();
                updateActivityLog("Data refreshed due to new sale");
                return null;
            }
        }.execute();
    }

    protected LocalDate[] getDateRangeForPeriod(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate, endDate;
        switch (period) {
            case "Today":
                startDate = today;
                endDate = today;
                break;
            case "This Week":
                startDate = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                endDate = today;
                break;
            case "This Month":
                startDate = today.withDayOfMonth(1);
                endDate = today;
                break;
            case "Last Month":
                startDate = today.minusMonths(1).withDayOfMonth(1);
                endDate = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
                break;
            case "This Quarter":
                int currentMonth = today.getMonthValue();
                int startMonth = currentMonth - (currentMonth - 1) % 3;
                startDate = LocalDate.of(today.getYear(), startMonth, 1);
                endDate = today;
                break;
            case "This Year":
                startDate = today.withDayOfYear(1);
                endDate = today;
                break;
            default:
                startDate = today;
                endDate = today;
                break;
        }
        return new LocalDate[]{startDate, endDate};
    }

    private void updatePeriodData() {
        String period = (String) periodComboBox.getSelectedItem();
        if (period == null) {
            LOGGER.warning("No period selected");
            return;
        }

        disableButtons(true);
        try {
            LocalDate[] dates = getDateRangeForPeriod(period);
            String startDate = dates[0].toString();
            String endDate = dates[1].toString();

            Map<LocalDate, Database.DailySalesReport> data = new TreeMap<>();
            LocalDate current = dates[0];
            while (!current.isAfter(dates[1])) {
                String dateStr = current.toString();
                Database.DailySalesReport report = Database.getDailySalesReport(dateStr);
                if (report == null) {
                    report = new Database.DailySalesReport();
                    report.date = current;
                    report.totalGrossSale = Database.getTotalSales(dateStr, dateStr);
                    report.discounts = Database.getDailyDiscount(dateStr);
                    report.costOfGoods = Database.getCostOfGoods(dateStr, dateStr);
                    report.dailyExpenses = Database.getDailyExpenseFromDatabase(dateStr);
                    report.netSale = report.totalGrossSale.subtract(report.discounts);
                    report.grossProfit = report.netSale.subtract(report.costOfGoods); // Profit
                    report.netProfit = report.grossProfit.subtract(report.dailyExpenses); // Net Profit
                    Database.saveDailySalesReport(report); // Save for future use
                }
                data.put(current, report);
                current = current.plusDays(1);
            }

            histogramPanel.setData(data);
            updateQuickStatsPanel();
            updateMonthlyExpensesLabel();

            // Update transaction table
            transactionTableModel.setRowCount(0);
            List<Database.SaleItem> items = Database.getSaleItems(startDate, endDate);
            if (items != null) {
                for (Database.SaleItem item : items) {
                    if (item == null || item.date == null || item.price == null) {
                        LOGGER.warning("Skipping invalid sale item: null item, date, or price");
                        continue;
                    }
                    Database.Product product = Database.getProductById(item.productId);
                    String productName = (product != null && product.name != null) ? product.name : "Unknown";
                    transactionTableModel.addRow(new Object[]{
                        item.date.format(DateTimeFormatter.ofPattern((String) dateFormatComboBox.getSelectedItem())),
                        productName,
                        item.quantity,
                        formatCurrency(item.price),
                        formatCurrency(item.price.multiply(BigDecimal.valueOf(item.quantity)))
                    });
                }
            }

            BigDecimal totalTrans = BigDecimal.ZERO;
            for (int i = 0; i < transactionTableModel.getRowCount(); i++) {
                Object totalObj = transactionTableModel.getValueAt(i, 4);
                if (totalObj != null) {
                    String totalStr = totalObj.toString().replaceAll("[^\\d.,-]", "").replace(",", "");
                    try {
                        totalTrans = totalTrans.add(new BigDecimal(totalStr));
                    } catch (NumberFormatException ex) {
                        LOGGER.warning("Failed to parse total price: " + totalStr);
                    }
                }
            }
            transactionsTotalLabel.setText("Total: " + formatCurrency(totalTrans));

            // Calculate dashboard stats
            BigDecimal totalSales = Database.getTotalSales(startDate, endDate);
            BigDecimal totalDiscounts = Database.getTotalDiscounts(startDate, endDate);
            BigDecimal totalExpenses = Database.getTotalExpenses(startDate, endDate);
            BigDecimal totalCostOfGoods = Database.getCostOfGoods(startDate, endDate);

            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            totalDiscounts = totalDiscounts != null ? totalDiscounts : BigDecimal.ZERO;
            totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
            totalCostOfGoods = totalCostOfGoods != null ? totalCostOfGoods : BigDecimal.ZERO;

            BigDecimal profit = totalSales.subtract(totalCostOfGoods); // Profit
            BigDecimal netProfit = profit.subtract(totalDiscounts.add(totalExpenses)); // Net Profit

            largeLabelsPanel.removeAll();
            largeLabelsPanel.add(createLargeLabel("Total Sales", formatCurrency(totalSales), SUCCESS_COLOR));
            largeLabelsPanel.add(createLargeLabel("Cost of Goods", formatCurrency(totalCostOfGoods), INFO_COLOR));
            largeLabelsPanel.add(createLargeLabel("Discounts", formatCurrency(totalDiscounts), WARNING_COLOR));
            largeLabelsPanel.add(createLargeLabel("Expenses", formatCurrency(totalExpenses), WARNING_COLOR));
            largeLabelsPanel.add(createLargeLabel("Profit", formatCurrency(profit), SUCCESS_COLOR));
            largeLabelsPanel.add(createLargeLabel("Net Profit", formatCurrency(netProfit), PRIMARY_COLOR));

            try {
                LocalDate lowProfitDate = findLowProfitDate(dates[0], dates[1]);
                if (lowProfitDate != null) {
                    JLabel lowProfitLabel = new JLabel("Lowest Profit Day: " + lowProfitDate.format(DateTimeFormatter.ofPattern((String) dateFormatComboBox.getSelectedItem())));
                    lowProfitLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    lowProfitLabel.setForeground(ERROR_COLOR);
                    largeLabelsPanel.add(lowProfitLabel);
                }
            } catch (SQLException e) {
                LOGGER.severe("Error finding low profit date: " + e.getMessage());
            }
            largeLabelsPanel.revalidate();
            largeLabelsPanel.repaint();

            updateStatusBar("Data updated for " + period);
            updateActivityLog("Updated data for " + period);
            LOGGER.info("Updated data for period: " + period);
        } catch (SQLException e) {
            LOGGER.severe("Database error in updatePeriodData: " + e.getMessage());
            updateStatusBar("Error updating data");
            JOptionPane.showMessageDialog(this, "Error updating data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            disableButtons(false);
        }
    }

    private void updateActivityLog(String message) {
        SwingUtilities.invokeLater(() -> {
            activityListModel.add(0, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " - " + message);
            if (activityListModel.size() > 20) {
                activityListModel.removeElementAt(activityListModel.size() - 1);
            }
        });
    }

    private LocalDate calculateStartDate(LocalDate endDate) {
        String selectedPeriod = (String) periodComboBox.getSelectedItem();
        switch (selectedPeriod) {
            case "Today":
                return endDate;
            case "This Week":
                return endDate.minusDays(6);
            case "This Month":
                return endDate.withDayOfMonth(1);
            case "Last Month":
                return endDate.minusMonths(1).withDayOfMonth(1);
            case "This Quarter":
                int currentMonth = endDate.getMonthValue();
                int startMonth = currentMonth - (currentMonth - 1) % 3;
                return LocalDate.of(endDate.getYear(), startMonth, 1);
            case "This Year":
                return endDate.withDayOfYear(1);
            default:
                return endDate;
        }
    }

    private void updateTransactionTable(List<Database.SaleItem> saleItems) {
        SwingUtilities.invokeLater(() -> {
            transactionTableModel.setRowCount(0);
            String datePattern = dateFormatComboBox.getSelectedItem() != null ?
                ((String) dateFormatComboBox.getSelectedItem()).replace("/", "-") : "yyyy-MM-dd";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);
            if (saleItems != null) {
                for (Database.SaleItem item : saleItems) {
                    try {
                        if (item == null || item.date == null || item.price == null) {
                            LOGGER.warning("Skipping invalid sale item: null item, date, or price");
                            continue;
                        }
                        Database.Product product = Database.getProductById(item.productId);
                        if (product == null) {
                            LOGGER.warning("Product not found for ID: " + item.productId);
                            continue;
                        }
                        String productName = (product != null && product.name != null) ? product.name : "Unknown";
                        Object[] row = new Object[]{
                            item.date.format(formatter),
                            productName,
                            item.quantity,
                            formatCurrency(item.price),
                            formatCurrency(item.price.multiply(BigDecimal.valueOf(item.quantity)))
                        };
                        transactionTableModel.addRow(row);
                    } catch (Exception e) {
                        LOGGER.severe("Error updating transaction table: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void disableButtons(boolean disable) {
        SwingUtilities.invokeLater(() -> {
            for (Component component : toolBar.getComponents()) {
                if (component instanceof JButton) {
                    component.setEnabled(!disable);
                }
            }
            loadingProgressBar.setIndeterminate(disable);
            loadingProgressBar.setString(disable ? "Processing..." : "");
        });
    }

    private void updateDashboardStats(Map<String, BigDecimal> stats) {
        SwingUtilities.invokeLater(() -> {
            largeLabelsPanel.removeAll();
            for (Map.Entry<String, BigDecimal> entry : stats.entrySet()) {
                Color color = STAT_COLORS.getOrDefault(entry.getKey(), TEXT_PRIMARY);
                largeLabelsPanel.add(createLargeLabel(entry.getKey(), formatCurrency(entry.getValue()), color));
            }
            largeLabelsPanel.revalidate();
            largeLabelsPanel.repaint();
        });
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        String symbol = CURRENCY_SYMBOLS.getOrDefault(selectedCurrency, " ");
        NumberFormat nf = NumberFormat.getNumberInstance(getCurrencyLocale(selectedCurrency));
        nf.setGroupingUsed(true);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        String formattedAmount = nf.format(amount.doubleValue());
        return symbol + formattedAmount;
    }

    private Locale getCurrencyLocale(String currency) {
        switch (currency.toUpperCase()) {
            case "USD": return Locale.US;
            case "EUR": return Locale.GERMANY;
            case "GBP": return Locale.UK;
            case "TSH": return new Locale("sw", "TZ");
            default: return Locale.getDefault();
        }
    }

    private void updateMonthlyExpensesLabel() {
        try {
            String yearMonth = YearMonth.now().toString();
            Map<String, BigDecimal> expenses = Database.getMonthlyExpensesByCategory(yearMonth);
            BigDecimal totalExpenses = expenses.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Update handled in updateQuickStatsPanel
        } catch (SQLException e) {
            LOGGER.severe("Error updating monthly expenses: " + e.getMessage());
        }
    }

    private void updateQuickStatsPanel() {
        SwingUtilities.invokeLater(() -> {
            sidebarPanel.removeAll();
            sidebarPanel.add(Box.createVerticalStrut(10));
            sidebarPanel.add(createEnhancedHeader());
            sidebarPanel.add(Box.createVerticalStrut(10));
            sidebarPanel.add(createQuickStatsPanel());
            sidebarPanel.add(Box.createVerticalStrut(20));
            JPanel recentActivityPanel = new JPanel(new BorderLayout());
            recentActivityPanel.setBorder(BorderFactory.createTitledBorder("Recent Activity"));
            recentActivityPanel.setBackground(CARD_COLOR);
            recentActivityList.setBackground(CARD_COLOR);
            JScrollPane activityScrollPane = new JScrollPane(recentActivityList);
            activityScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
            recentActivityPanel.add(activityScrollPane, BorderLayout.CENTER);
            sidebarPanel.add(recentActivityPanel);
            sidebarPanel.add(Box.createVerticalStrut(10));
            sidebarPanel.add(createEnhancedFooter());
            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        });
    }

    private void refreshAllData() {
        updatePeriodData();
        updateMonthlyExpensesLabel();
    }

    private void switchToTab(int index) {
        if (index >= 0 && index < mainTabbedPane.getTabCount()) {
            mainTabbedPane.setSelectedIndex(index);
        }
    }

    private void generateDailyReport() {
        try {
            String date = LocalDate.now().toString();
            Database.DailySalesReport report = Database.getDailySalesReport(date);
            if (report == null) {
                report = new Database.DailySalesReport();
                report.date = LocalDate.now();
                report.totalGrossSale = Database.getTotalSales(date, date);
                report.discounts = Database.getDailyDiscount(date);
                report.costOfGoods = Database.getCostOfGoods(date, date);
                report.dailyExpenses = Database.getDailyExpenseFromDatabase(date);
                report.netSale = report.totalGrossSale.subtract(report.discounts);
                report.grossProfit = report.netSale.subtract(report.costOfGoods);
                report.netProfit = report.grossProfit.subtract(report.dailyExpenses);
            }
            String reportText = "Daily Report for " + date + "\n" +
                "Total Sales: " + formatCurrency(report.totalGrossSale) + "\n" +
                "Discounts: " + formatCurrency(report.discounts) + "\n" +
                "Net Sales: " + formatCurrency(report.netSale) + "\n" +
                "Cost of Goods: " + formatCurrency(report.costOfGoods) + "\n" +
                "Expenses: " + formatCurrency(report.dailyExpenses) + "\n" +
                "Net Profit: " + formatCurrency("Today".equals(periodComboBox.getSelectedItem()) ? report.totalGrossSale.subtract(report.discounts) : report.netProfit) + "\n";
            reportTextArea.setText(reportText);
            updateActivityLog("Generated daily report for " + date);
        } catch (SQLException e) {
            LOGGER.severe("Error generating daily report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateWeeklyReport() {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(6);
            String start = startDate.toString();
            String end = endDate.toString();
            BigDecimal totalSales = Database.getTotalSales(start, end);
            BigDecimal totalDiscounts = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            LocalDate curr = startDate;
            while (!curr.isAfter(endDate)) {
                totalDiscounts = totalDiscounts.add(Database.getDailyDiscount(curr.toString()) != null ? Database.getDailyDiscount(curr.toString()) : BigDecimal.ZERO);
                totalExpenses = totalExpenses.add(Database.getDailyExpenseFromDatabase(curr.toString()) != null ? Database.getDailyExpenseFromDatabase(curr.toString()) : BigDecimal.ZERO);
                curr = curr.plusDays(1);
            }
            BigDecimal totalCostOfGoods = Database.getCostOfGoods(start, end);
            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            totalDiscounts = totalDiscounts != null ? totalDiscounts : BigDecimal.ZERO;
            totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
            totalCostOfGoods = totalCostOfGoods != null ? totalCostOfGoods : BigDecimal.ZERO;
            BigDecimal netSales = totalSales.subtract(totalDiscounts);
            BigDecimal grossProfit = netSales.subtract(totalCostOfGoods);
            BigDecimal netProfit = grossProfit.subtract(totalExpenses);
            String reportText = "Weekly Report (" + start + " to " + end + ")\n" +
                "Total Sales: " + formatCurrency(totalSales) + "\n" +
                "Discounts: " + formatCurrency(totalDiscounts) + "\n" +
                "Net Sales: " + formatCurrency(netSales) + "\n" +
                "Cost of Goods: " + formatCurrency(totalCostOfGoods) + "\n" +
                "Expenses: " + formatCurrency(totalExpenses) + "\n" +
                "Net Profit: " + formatCurrency(netProfit);
            reportTextArea.setText(reportText);
            updateActivityLog("Generated weekly report for " + start + " to " + end);
        } catch (SQLException e) {
            LOGGER.severe("Error generating weekly report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateMonthlyReport() {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.withDayOfMonth(1);
            String start = startDate.toString();
            String end = endDate.toString();
            BigDecimal totalSales = Database.getTotalSales(start, end);
            BigDecimal totalDiscounts = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            LocalDate curr = startDate;
            while (!curr.isAfter(endDate)) {
                totalDiscounts = totalDiscounts.add(Database.getDailyDiscount(curr.toString()) != null ? Database.getDailyDiscount(curr.toString()) : BigDecimal.ZERO);
                totalExpenses = totalExpenses.add(Database.getDailyExpenseFromDatabase(curr.toString()) != null ? Database.getDailyExpenseFromDatabase(curr.toString()) : BigDecimal.ZERO);
                curr = curr.plusDays(1);
            }
            BigDecimal totalCostOfGoods = Database.getCostOfGoods(start, end);
            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            totalDiscounts = totalDiscounts != null ? totalDiscounts : BigDecimal.ZERO;
            totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
            totalCostOfGoods = totalCostOfGoods != null ? totalCostOfGoods : BigDecimal.ZERO;
            BigDecimal netSales = totalSales.subtract(totalDiscounts);
            BigDecimal grossProfit = netSales.subtract(totalCostOfGoods);
            BigDecimal netProfit = grossProfit.subtract(totalExpenses);
            String reportText = "Monthly Report (" + start + " to " + end + ")\n" +
                "Total Sales: " + formatCurrency(totalSales) + "\n" +
                "Discounts: " + formatCurrency(totalDiscounts) + "\n" +
                "Net Sales: " + formatCurrency(netSales) + "\n" +
                "Cost of Goods: " + formatCurrency(totalCostOfGoods) + "\n" +
                "Expenses: " + formatCurrency(totalExpenses) + "\n" +
                "Net Profit: " + formatCurrency(netProfit);
            reportTextArea.setText(reportText);
            updateActivityLog("Generated monthly report for " + start + " to " + end);
        } catch (SQLException e) {
            LOGGER.severe("Error generating monthly report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateAnnualReport() {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.withDayOfYear(1);
            String start = startDate.toString();
            String end = endDate.toString();
            BigDecimal totalSales = Database.getTotalSales(start, end);
            BigDecimal totalDiscounts = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            LocalDate curr = startDate;
            while (!curr.isAfter(endDate)) {
                totalDiscounts = totalDiscounts.add(Database.getDailyDiscount(curr.toString()) != null ? Database.getDailyDiscount(curr.toString()) : BigDecimal.ZERO);
                totalExpenses = totalExpenses.add(Database.getDailyExpenseFromDatabase(curr.toString()) != null ? Database.getDailyExpenseFromDatabase(curr.toString()) : BigDecimal.ZERO);
                curr = curr.plusDays(1);
            }
            BigDecimal totalCostOfGoods = Database.getCostOfGoods(start, end);
            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            totalDiscounts = totalDiscounts != null ? totalDiscounts : BigDecimal.ZERO;
            totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
            totalCostOfGoods = totalCostOfGoods != null ? totalCostOfGoods : BigDecimal.ZERO;
            BigDecimal netSales = totalSales.subtract(totalDiscounts);
            BigDecimal grossProfit = netSales.subtract(totalCostOfGoods);
            BigDecimal netProfit = grossProfit.subtract(totalExpenses);
            String reportText = "Annual Report (" + start + " to " + end + ")\n" +
                "Total Sales: " + formatCurrency(totalSales) + "\n" +
                "Discounts: " + formatCurrency(totalDiscounts) + "\n" +
                "Net Sales: " + formatCurrency(netSales) + "\n" +
                "Cost of Goods: " + formatCurrency(totalCostOfGoods) + "\n" +
                "Expenses: " + formatCurrency(totalExpenses) + "\n" +
                "Net Profit: " + formatCurrency(netProfit);
            reportTextArea.setText(reportText);
            updateActivityLog("Generated annual report for " + start + " to " + end);
        } catch (SQLException e) {
            LOGGER.severe("Error generating annual report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private LocalDate findLowProfitDate(LocalDate startDate, LocalDate endDate) throws SQLException {
        BigDecimal lowestProfit = null;
        LocalDate lowDate = null;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String dateStr = current.toString();
            BigDecimal dailySale = Database.getTotalSales(dateStr, dateStr);
            BigDecimal dailyCost = Database.getCostOfGoods(dateStr, dateStr);
            BigDecimal dailyDiscount = Database.getDailyDiscount(dateStr);
            BigDecimal dailyExpense = Database.getDailyExpenseFromDatabase(dateStr);
            dailySale = dailySale != null ? dailySale : BigDecimal.ZERO;
            dailyCost = dailyCost != null ? dailyCost : BigDecimal.ZERO;
            dailyDiscount = dailyDiscount != null ? dailyDiscount : BigDecimal.ZERO;
            dailyExpense = dailyExpense != null ? dailyExpense : BigDecimal.ZERO;
            BigDecimal dailyProfit = dailySale.subtract(dailyDiscount).subtract("Today".equals(periodComboBox.getSelectedItem()) ? BigDecimal.ZERO : dailyCost).subtract("Today".equals(periodComboBox.getSelectedItem()) ? BigDecimal.ZERO : dailyExpense);
            if (lowestProfit == null || dailyProfit.compareTo(lowestProfit) < 0) {
                lowestProfit = dailyProfit;
                lowDate = current;
            }
            current = current.plusDays(1);
        }
        return lowDate;
    }

    private String findLowProfitMonth(LocalDate startDate, LocalDate endDate) throws SQLException {
        BigDecimal lowestProfit = null;
        String lowMonth = null;
        LocalDate current = startDate.withDayOfMonth(1);
        while (!current.isAfter(endDate)) {
            YearMonth ym = YearMonth.from(current);
            String monthStr = ym.toString();
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();
            BigDecimal monthSale = Database.getTotalSales(monthStart.toString(), monthEnd.toString());
            BigDecimal monthCost = Database.getCostOfGoods(monthStart.toString(), monthEnd.toString());
            BigDecimal monthDiscount = Database.getDailyDiscount(monthStart.toString());
            BigDecimal monthExpense = Database.getDailyExpenseFromDatabase(monthStart.toString());
            monthSale = monthSale != null ? monthSale : BigDecimal.ZERO;
            monthCost = monthCost != null ? monthCost : BigDecimal.ZERO;
            monthDiscount = monthDiscount != null ? monthDiscount : BigDecimal.ZERO;
            monthExpense = monthExpense != null ? monthExpense : BigDecimal.ZERO;
            BigDecimal monthProfit = monthSale.subtract(monthDiscount).subtract(monthCost).subtract(monthExpense);
            if (lowestProfit == null || monthProfit.compareTo(lowestProfit) < 0) {
                lowestProfit = monthProfit;
                lowMonth = ym.getMonth().toString();
            }
            current = current.plusMonths(1);
        }
        return lowMonth;
    }

    private void showReportDialog(String reportHtml, String title) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setText(reportHtml);
        editorPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        JOptionPane.showMessageDialog(this, scrollPane, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportToPDF() {
        try {
            StringBuilder pdfContent = new StringBuilder();
            pdfContent.append("Financial Report\n\n");
            pdfContent.append(reportTextArea.getText());

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("financial_report.txt"));
            if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write(pdfContent.toString());
                updateActivityLog("Report exported successfully");
                JOptionPane.showMessageDialog(this, "Report exported as text file successfully!");
            }
        } catch (Exception e) {
            LOGGER.severe("Error exporting report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error exporting report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportToText() {
        try {
            StringBuilder reportContent = new StringBuilder();
            reportContent.append("PoS Financial Report\n");
            reportContent.append("Generated on: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            LocalDate today = LocalDate.now();
            String todayStr = today.toString();
            BigDecimal totalSales = Database.getTotalSales(todayStr, todayStr);
            BigDecimal totalExpenses = Database.getDailyExpenseFromDatabase(todayStr);
            BigDecimal totalDiscounts = Database.getDailyDiscount(todayStr);
            BigDecimal totalCostOfGoods = Database.getCostOfGoods(todayStr, todayStr);
            totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
            totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
            totalDiscounts = totalDiscounts != null ? totalDiscounts : BigDecimal.ZERO;
            totalCostOfGoods = totalCostOfGoods != null ? totalCostOfGoods : BigDecimal.ZERO;
            BigDecimal netProfit = totalSales.subtract(totalDiscounts).subtract("Today".equals(periodComboBox.getSelectedItem()) ? BigDecimal.ZERO : totalCostOfGoods).subtract("Today".equals(periodComboBox.getSelectedItem()) ? BigDecimal.ZERO : totalExpenses);
            reportContent.append("Daily Summary (").append(today).append("):\n");
            reportContent.append("Total Sales: ").append(formatCurrency(totalSales)).append("\n");
            reportContent.append("Discounts: ").append(formatCurrency(totalDiscounts)).append("\n");
            reportContent.append("Total Expenses: ").append(formatCurrency(totalExpenses)).append("\n");
            reportContent.append("Cost of Goods: ").append(formatCurrency(totalCostOfGoods)).append("\n");
            reportContent.append("Net Profit: ").append(formatCurrency(netProfit)).append("\n");

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("report_" + System.currentTimeMillis() + ".txt"));
            if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write(reportContent.toString());
                updateActivityLog("Text report exported successfully");
                JOptionPane.showMessageDialog(this, "Text report exported successfully!", "Export", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            LOGGER.severe("Error exporting text report: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error exporting text report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printReport() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Print Financial Report");
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            contentPanel.printAll(g2);
            return Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
                updateActivityLog("Report printed successfully");
                JOptionPane.showMessageDialog(this, "Report printed successfully!");
            } catch (Exception e) {
                LOGGER.severe("Error printing report: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error printing report: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static class ModernScrollBarUI extends BasicScrollBarUI {
        private static final Color SURFACE_COLOR_LOCAL = new Color(248, 250, 252);
        private static final Color SECONDARY_COLOR_LOCAL = new Color(99, 102, 241);

        @Override
        protected void configureScrollBarColors() {
            this.trackColor = SURFACE_COLOR_LOCAL;
            this.thumbColor = SECONDARY_COLOR_LOCAL;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    private JPanel createReportCard(String title, String description, Runnable action) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_PRIMARY);

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLabel.setForeground(TEXT_SECONDARY);

        JButton generateButton = createStyledButton("Generate", PRIMARY_COLOR);
        generateButton.addActionListener(e -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }
        }.execute());

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(CARD_COLOR);
        textPanel.add(titleLabel, BorderLayout.NORTH);
        textPanel.add(descLabel, BorderLayout.CENTER);

        card.add(textPanel, BorderLayout.CENTER);
        card.add(generateButton, BorderLayout.SOUTH);
        return card;
    }

public String getSelectedPeriod() {
    return (String) periodComboBox.getSelectedItem();
}


class HistogramWithDensityPanel extends JPanel {
    private static final Color TOTAL_SALES_COLOR = new Color(34, 197, 94);
    private static final Color NET_SALES_COLOR = new Color(59, 130, 246);
    private static final Color EXPENSES_COLOR = new Color(251, 146, 60);
    private static final Color NET_PROFIT_COLOR = new Color(79, 70, 229);
    private static final Color DENSITY_COLOR = new Color(34, 197, 94);
    private Map<LocalDate, Database.DailySalesReport> data;
    private final JComboBox<String> dateFormatComboBox;
    private final Report parent;

    public HistogramWithDensityPanel(JComboBox<String> dateFormatComboBox, Report parent) {
        this.dateFormatComboBox = dateFormatComboBox;
        this.parent = parent;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        this.data = new TreeMap<>();
    }

    public void setData(Map<LocalDate, Database.DailySalesReport> data) {
        this.data = (data != null) ? data : new TreeMap<>();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (data == null || data.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int padding = 60;
        int graphWidth = width - 2 * padding;
        int graphHeight = height - 2 * padding;

        BigDecimal maxY = BigDecimal.ZERO;
        for (Database.DailySalesReport report : data.values()) {
            if (report != null) {
                maxY = maxY.max(report.totalGrossSale != null ? report.totalGrossSale : BigDecimal.ZERO);
                maxY = maxY.max(report.costOfGoods != null ? report.costOfGoods : BigDecimal.ZERO);
                maxY = maxY.max(report.netProfit != null ? report.netProfit.abs() : BigDecimal.ZERO);
            }
        }
        if (maxY.compareTo(BigDecimal.ZERO) <= 0) {
            maxY = new BigDecimal("1000");
        }

        g2.setColor(Color.DARK_GRAY);
        g2.drawLine(padding, padding, padding, height - padding);
        g2.drawLine(padding, height - padding, width - padding, height - padding);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.drawString("Sales / Cost of Goods / Net Profit", padding - 50, padding - 10);
        g2.drawString("Date", width - padding + 10, height - padding + 15);

        int numYMarkers = 5;
        for (int i = 0; i <= numYMarkers; i++) {
            int y = height - padding - (i * graphHeight / numYMarkers);
            BigDecimal value = maxY.multiply(new BigDecimal(i)).divide(new BigDecimal(numYMarkers), 2, RoundingMode.HALF_UP);
            g2.drawLine(padding - 5, y, padding, y);
            String symbol = " ";
            g2.drawString(symbol + String.format("%.2f", value.doubleValue()), padding - 50, y + 5);
        }

        String datePattern = (String) dateFormatComboBox.getSelectedItem();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern != null ? datePattern : "MM-dd-yyyy");
        List<LocalDate> dates = new ArrayList<>(data.keySet());
        int numDates = dates.size();
        if (numDates == 0) {
            return;
        }

        int numMetrics;
        String selectedPeriod = parent.getSelectedPeriod();
        if ("Today".equals(selectedPeriod)) {
            numMetrics = 3; // Total Sales, Cost of Goods, Net Profit
        } else {
            numMetrics = 4; // Total Sales, Net Sales, Expenses, Net Profit
        }

        int groupWidth = Math.max(40, graphWidth / numDates);
        int barWidth = (groupWidth - (numMetrics - 1)) / numMetrics;
        int gap = 1;

        for (int i = 0; i < numDates; i++) {
            LocalDate date = dates.get(i);
            Database.DailySalesReport report = data.get(date);
            if (report == null) {
                continue;
            }

            int groupX = padding + (i * groupWidth);

            // Total Sales bar
            BigDecimal totalSalesVal = report.totalGrossSale != null ? report.totalGrossSale : BigDecimal.ZERO;
            int tsHeight = (int) (Math.abs(totalSalesVal.doubleValue()) / maxY.doubleValue() * graphHeight);
            g2.setColor(TOTAL_SALES_COLOR);
            int tsY = height - padding - tsHeight;
            g2.fillRect(groupX, tsY, barWidth, tsHeight);

            // Cost of Goods bar
            BigDecimal costOfGoodsVal = report.costOfGoods != null ? report.costOfGoods : BigDecimal.ZERO;
            int cogHeight = (int) (Math.abs(costOfGoodsVal.doubleValue()) / maxY.doubleValue() * graphHeight);
            g2.setColor(new Color(255, 235, 156)); // same as quick stats background for cost of goods
            int cogY = height - padding - cogHeight;
            g2.fillRect(groupX + barWidth + gap, cogY, barWidth, cogHeight);

            // Net Profit bar
            BigDecimal netProfitVal = report.netProfit != null ? report.netProfit : BigDecimal.ZERO;
            int npHeight = (int) (Math.abs(netProfitVal.doubleValue()) / maxY.doubleValue() * graphHeight);
            g2.setColor(NET_PROFIT_COLOR);
            int npY = height - padding - npHeight;
            g2.fillRect(groupX + 2 * (barWidth + gap), npY, barWidth, npHeight);

            if (!"Today".equals(selectedPeriod)) {
                // For other periods, draw the 4 bars as before (optional)
                // You can keep your existing code here if needed
            }

            if (numDates <= 10 || i % (numDates / 10) == 0) {
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(date.format(formatter), groupX, height - padding + 20);
            }
        }
    }
}
}