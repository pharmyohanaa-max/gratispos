package hIMs.App;

import com.toedter.calendar.JDateChooser;
import java.sql.SQLException;
import com.formdev.flatlaf.FlatIntelliJLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import java.io.IOException;

import java.util.Set;

public class Addproduct extends JDialog {

    private JTextField txtProductName;
    private JComboBox<String> cmbVendor;
    private JComboBox<String> cmbCategory;
    private JLabel lblCurrencyDisplay;
    private JLabel lblExpiryDate;
    private JLabel lblSalesTotal;
    private JLabel lblTotalEntries;
    private JLabel lblInvoiceNumber;
    private String selectedCurrency = "TSh";
    private JTable table;
    private DefaultTableModel tableModel;
    private HashSet<String> productNames = new HashSet<>();
    private Date expiryDate;
    private JSpinner spnCostPrice;
    private JSpinner spnSellingPrice;
    private JSpinner spnQuantity;
    private JSpinner spnMinStock;
    private JSpinner spnNewStock;
    private Map<String, Integer> categoryMap = new HashMap<>();
    private JButton floatButton;
    private boolean isFloating = false;
    private Dimension originalSize;
    private Dimension floatSize;
    private final Dimension fieldSize = new Dimension(180, 30);
    private JDateChooser dateChooser;
    private JLabel lblSelectedDate;
    private String currentInvoiceNumber;
    private static int invoiceCounter = 1;
    
    private static final Logger LOGGER = Logger.getLogger(Addproduct.class.getName());

    // ===== DYNAMIC SCREEN SIZE METHODS =====
    
    /**
     * Gets the recommended dialog size based on screen resolution
     * Uses responsive percentages for optimal display on any screen
     */
    private Dimension getRecommendedDialogSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        // Base percentages for different screen categories
        double widthPercentage;
        double heightPercentage;
        
        if (screenSize.width >= 2560) { // 4K and ultra-wide
            widthPercentage = 0.45;
            heightPercentage = 0.55;
        } else if (screenSize.width >= 1920) { // Full HD
            widthPercentage = 0.55;
            heightPercentage = 0.65;
        } else if (screenSize.width >= 1366) { // Standard laptop
            widthPercentage = 0.70;
            heightPercentage = 0.75;
        } else if (screenSize.width >= 1024) { // Small laptop/tablet
            widthPercentage = 0.85;
            heightPercentage = 0.85;
        } else { // Mobile/small screens
            widthPercentage = 0.95;
            heightPercentage = 0.90;
        }
        
        int recommendedWidth = (int)(screenSize.width * widthPercentage);
        int recommendedHeight = (int)(screenSize.height * heightPercentage);
        
        // Set absolute minimum and maximum constraints
        recommendedWidth = Math.max(recommendedWidth, 650);
        recommendedWidth = Math.min(recommendedWidth, 1400);
        recommendedHeight = Math.max(recommendedHeight, 500);
        recommendedHeight = Math.min(recommendedHeight, 900);
        
        LOGGER.info("Screen: " + screenSize.width + "x" + screenSize.height + 
                   " → Recommended dialog: " + recommendedWidth + "x" + recommendedHeight);
        
        return new Dimension(recommendedWidth, recommendedHeight);
    }
    
    /**
     * Gets recommended floating mode size based on screen
     */
    private Dimension getRecommendedFloatSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        double widthPercentage;
        double heightPercentage;
        
        if (screenSize.width >= 1920) {
            widthPercentage = 0.25;
            heightPercentage = 0.35;
        } else if (screenSize.width >= 1366) {
            widthPercentage = 0.35;
            heightPercentage = 0.40;
        } else if (screenSize.width >= 1024) {
            widthPercentage = 0.45;
            heightPercentage = 0.45;
        } else {
            widthPercentage = 0.60;
            heightPercentage = 0.50;
        }
        
        int floatWidth = (int)(screenSize.width * widthPercentage);
        int floatHeight = (int)(screenSize.height * heightPercentage);
        
        floatWidth = Math.max(floatWidth, 380);
        floatWidth = Math.min(floatWidth, 600);
        floatHeight = Math.max(floatHeight, 320);
        floatHeight = Math.min(floatHeight, 500);
        
        return new Dimension(floatWidth, floatHeight);
    }
    
    /**
     * Gets field size adapted to screen resolution
     */
    private Dimension getAdaptiveFieldSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        if (screenSize.width >= 1920) {
            return new Dimension(220, 35);
        } else if (screenSize.width >= 1366) {
            return new Dimension(180, 30);
        } else {
            return new Dimension(150, 28);
        }
    }
    
    /**
     * Gets adaptive font size based on screen
     */
    private int getAdaptiveFontSize() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        if (screenSize.width >= 1920) {
            return 16;
        } else if (screenSize.width >= 1366) {
            return 14;
        } else {
            return 12;
        }
    }
    
    /**
     * Gets adaptive split pane divider location
     */
    private int getAdaptiveDividerLocation() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        if (screenSize.width >= 1920) {
            return 450;
        } else if (screenSize.width >= 1366) {
            return 350;
        } else {
            return 300;
        }
    }

    // Helper method to get universal colors from UIManager
    private Color getUniversalColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    public static void main(String[] args) {
        try {
            FlatIntelliJLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            Database.initializeDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        EventQueue.invokeLater(() -> {
            try {
                GratisData.User currentUser = GratisData.getInstance().getCurrentUser();
                
                if (currentUser == null) {
                    currentUser = new GratisData.User(
                        1, "Administrator", "admin@gratispos.com", "", "SUPERADMIN",
                        null, null, true, "local", "Gratis Pharmacy",
                        "", null, null, false
                    );
                    GratisData.getInstance().setCurrentUser(currentUser);
                }
                
                Profilewindow parent = new Profilewindow(currentUser);
                parent.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                parent.setVisible(true);
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error launching application: " + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }

    public Addproduct(JFrame parent) {
        super(parent, "Product Registry Point", false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        // Calculate adaptive sizes using recommended dimensions
        originalSize = getRecommendedDialogSize();
        floatSize = getRecommendedFloatSize();
        
        // Make dialog resizable
        setResizable(true);
        setSize(originalSize);
        setMinimumSize(new Dimension(550, 450));
        setLocationRelativeTo(parent);
        
        generateInvoiceNumber();
        initialize();
        loadCategories();
        loadVendorsIntoCombo();
        
        // Add component listener for dynamic resize handling
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Adjust internal components on resize
                adjustComponentsForResize();
            }
        });
    }
    
    /**
     * Adjusts internal components when dialog is resized
     */
    private void adjustComponentsForResize() {
        // Get current width
        int currentWidth = getWidth();
        
        // Adjust split pane divider if it exists
        Component[] components = ((JPanel)getContentPane().getComponent(0)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) comp;
                int newDividerLocation = (int)(currentWidth * 0.35);
                newDividerLocation = Math.max(newDividerLocation, 280);
                newDividerLocation = Math.min(newDividerLocation, 500);
                splitPane.setDividerLocation(newDividerLocation);
                break;
            }
        }
        
        revalidate();
        repaint();
    }

    private void generateInvoiceNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", (int)(Math.random() * 10000));
        currentInvoiceNumber = "INV-" + datePart + "-" + randomPart;
    }

    public Addproduct() {
        this(null);
    }

    private void initialize() {
        // Get adaptive values
        Dimension adaptiveFieldSize = getAdaptiveFieldSize();
        int fontSize = getAdaptiveFontSize();
        int dividerLocation = getAdaptiveDividerLocation();
        
        // Get universal colors
        Color panelBg = getUniversalColor("Panel.background", new Color(60, 60, 70));
        Color accentColor = getUniversalColor("Component.accentColor", new Color(0, 200, 150));
        Color textColor = getUniversalColor("Label.foreground", Color.WHITE);
        Color borderColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80);
        
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 200);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        getContentPane().add(mainPanel);

        JPanel topHeaderPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 150);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(borderColor);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        topHeaderPanel.setOpaque(false);
        topHeaderPanel.setLayout(new BoxLayout(topHeaderPanel, BoxLayout.Y_AXIS));

        JLabel lblTitle = new JLabel("Product Registry Point");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, Math.min(28, fontSize + 10)));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTitle.setForeground(accentColor);
        topHeaderPanel.add(lblTitle);
        topHeaderPanel.add(Box.createVerticalStrut(12));

        JPanel midControlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        midControlsPanel.setOpaque(false);

        JButton btnCurrency = new JButton("Select Currency");
        btnCurrency.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        lblCurrencyDisplay = new JLabel("Currency: " + selectedCurrency);
        lblCurrencyDisplay.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        lblCurrencyDisplay.setForeground(accentColor);

        JButton btnVendor = new JButton("Manage Vendors");
        btnVendor.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        btnVendor.addActionListener(e -> openVendorManagementDialog());

        cmbVendor = new JComboBox<>();
        cmbVendor.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        cmbVendor.setToolTipText("Select vendor");
        cmbVendor.setPreferredSize(adaptiveFieldSize);
        cmbVendor.setMaximumRowCount(10);

        floatButton = new JButton("Float");
        floatButton.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        floatButton.addActionListener(e -> toggleFloatMode());

        midControlsPanel.add(btnCurrency);
        midControlsPanel.add(lblCurrencyDisplay);
        midControlsPanel.add(Box.createHorizontalStrut(10));
        midControlsPanel.add(btnVendor);
        midControlsPanel.add(cmbVendor);
        midControlsPanel.add(Box.createHorizontalStrut(10));
        midControlsPanel.add(floatButton);

        topHeaderPanel.add(midControlsPanel);
        
        // Invoice and Date Panel
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        infoPanel.setOpaque(false);
        
        lblInvoiceNumber = new JLabel("Invoice #: " + currentInvoiceNumber);
        lblInvoiceNumber.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        lblInvoiceNumber.setForeground(new Color(255, 200, 0));
        
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        datePanel.setOpaque(false);
        
        JLabel dateLabel = new JLabel("Sale Date:");
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, fontSize - 1));
        dateLabel.setForeground(textColor);
        
        dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("yyyy-MM-dd");
        dateChooser.setDate(new Date());
        dateChooser.setPreferredSize(new Dimension(110, 28));
        
        lblSelectedDate = new JLabel("(Today)");
        lblSelectedDate.setFont(new Font("Segoe UI", Font.ITALIC, fontSize - 3));
        lblSelectedDate.setForeground(new Color(150, 255, 150));
        
        dateChooser.addPropertyChangeListener("date", evt -> {
            Date selected = dateChooser.getDate();
            if (selected != null) {
                LocalDate localDate = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if (localDate.equals(LocalDate.now())) {
                    lblSelectedDate.setText("(Today)");
                } else {
                    lblSelectedDate.setText("(Custom: " + localDate.toString() + ")");
                }
            }
        });
        
        datePanel.add(dateLabel);
        datePanel.add(dateChooser);
        datePanel.add(lblSelectedDate);
        
        infoPanel.add(lblInvoiceNumber);
        infoPanel.add(datePanel);
        
        topHeaderPanel.add(infoPanel);
        mainPanel.add(topHeaderPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(dividerLocation);
        splitPane.setResizeWeight(0.3);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        JPanel leftPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 140);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(borderColor);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        txtProductName = new JTextField();
        txtProductName.setPreferredSize(adaptiveFieldSize);
        txtProductName.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        cmbCategory = new JComboBox<>();
        cmbCategory.setPreferredSize(adaptiveFieldSize);
        cmbCategory.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        spnCostPrice = createDecimalSpinner(fontSize);
        spnSellingPrice = createDecimalSpinner(fontSize);
        spnQuantity = createIntegerSpinner(fontSize);
        spnMinStock = createIntegerSpinner(fontSize);
        spnNewStock = createIntegerSpinner(fontSize);
        spnNewStock.setEnabled(false);

        leftPanel.add(createLabeledField("Product Name:", txtProductName, "Enter product name", adaptiveFieldSize, fontSize));
        leftPanel.add(Box.createVerticalStrut(12));
        leftPanel.add(createLabeledField("Category:", cmbCategory, "Select product category", adaptiveFieldSize, fontSize));
        leftPanel.add(createLabeledField("Cost Price:", spnCostPrice, "Enter cost price", fontSize));
        leftPanel.add(createLabeledField("Selling Price:", spnSellingPrice, "Enter selling price", fontSize));
        leftPanel.add(createLabeledField("Quantity:", spnQuantity, "Enter quantity", fontSize));
        leftPanel.add(createLabeledField("Min Stock Level:", spnMinStock, "Enter minimum stock level", fontSize));
        leftPanel.add(createLabeledField("New Stock:", spnNewStock, "Automatically updated", fontSize));

        JPanel expiryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        expiryPanel.setOpaque(false);

        JButton btnExpiryDate = new JButton("Select Expiry Date");
        btnExpiryDate.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        lblExpiryDate = new JLabel("No Date Selected");
        lblExpiryDate.setFont(new Font("Segoe UI", Font.ITALIC, fontSize));
        lblExpiryDate.setForeground(new Color(200, 255, 220));

        expiryPanel.add(btnExpiryDate);
        expiryPanel.add(lblExpiryDate);
        leftPanel.add(expiryPanel);
        leftPanel.add(Box.createVerticalGlue());

        splitPane.setLeftComponent(leftPanel);

        JPanel rightPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 140);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(borderColor);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rightPanel.setOpaque(false);

        // Table without Vendor column - using universal colors
        tableModel = new DefaultTableModel(new Object[]{"Product", "Category", "Cost", "Sell", "Qty", "Min", "Expiry"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        table.setRowHeight(fontSize + 14);
        table.setShowGrid(true);
        table.setFillsViewportHeight(true);
        
        Color tableBg = getUniversalColor("Table.background", new Color(50, 50, 55));
        Color tableFg = getUniversalColor("Table.foreground", Color.WHITE);
        Color headerBg = getUniversalColor("TableHeader.background", new Color(0, 100, 80));
        Color headerFg = getUniversalColor("TableHeader.foreground", Color.WHITE);
        
        table.setBackground(tableBg);
        table.setForeground(tableFg);

        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        table.getTableHeader().setBackground(headerBg);
        table.getTableHeader().setForeground(headerFg);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(tableBg);
                    c.setForeground(tableFg);
                }
                ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom info panel with total entries and grand total
        JPanel infoBottomPanel = new JPanel(new GridLayout(2, 1, 10, 3));
        infoBottomPanel.setOpaque(false);
        infoBottomPanel.setBorder(new EmptyBorder(8, 12, 5, 12));
        
        lblTotalEntries = new JLabel("Total Entries: 0");
        lblTotalEntries.setFont(new Font("Segoe UI", Font.BOLD, fontSize - 1));
        lblTotalEntries.setForeground(accentColor);
        
        lblSalesTotal = new JLabel("Grand Total: " + selectedCurrency + " 0.00");
        lblSalesTotal.setFont(new Font("Segoe UI", Font.BOLD, fontSize - 1));
        lblSalesTotal.setForeground(accentColor);
        
        infoBottomPanel.add(lblTotalEntries);
        infoBottomPanel.add(lblSalesTotal);
        
        JPanel tableActionPanel = new JPanel(new BorderLayout(10, 10));
        tableActionPanel.setOpaque(false);

        JButton btnDelete = new JButton("Delete Selected Row");
        btnDelete.setFont(new Font("Segoe UI", Font.PLAIN, fontSize - 1));

        JPanel deletePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        deletePanel.setOpaque(false);
        deletePanel.add(btnDelete);

        tableActionPanel.add(infoBottomPanel, BorderLayout.NORTH);
        tableActionPanel.add(deletePanel, BorderLayout.SOUTH);

        rightPanel.add(tableActionPanel, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        bottomPanel.setOpaque(false);

        JButton btnSave = new JButton("Save");
        btnSave.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        JButton btnClear = new JButton("Clear All");
        btnClear.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));

        bottomPanel.add(btnSave);
        bottomPanel.add(btnClear);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        btnCurrency.addActionListener(e -> {
            String currency = JOptionPane.showInputDialog(Addproduct.this, "Enter Currency Symbol:", selectedCurrency);
            if (currency != null && !currency.trim().isEmpty()) {
                selectedCurrency = currency.trim();
                lblCurrencyDisplay.setText("Currency: " + selectedCurrency);
                updateSalesTotal();
            }
        });

        btnExpiryDate.addActionListener(e -> {
            JDateChooser expiryChooser = new JDateChooser();
            expiryChooser.setDateFormatString("yyyy-MM-dd");
            expiryChooser.setDate(new Date());

            JPanel panel = new JPanel();
            panel.add(expiryChooser);

            int option = JOptionPane.showConfirmDialog(Addproduct.this, panel, "Pick Expiry Date", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                Date selected = expiryChooser.getDate();
                if (selected == null) {
                    showGlassMessageDialog("Please select a valid date.", "Invalid Date", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                LocalDate localSelected = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                if (localSelected.isAfter(LocalDate.now().plusMonths(6))) {
                    expiryDate = selected;
                    lblExpiryDate.setText("Expires: " + localSelected.toString());
                    tryAutoInsertRow();
                } else {
                    showGlassMessageDialog("Date must be at least 6 months ahead.", "Invalid Date", JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        KeyAdapter tryInsertListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                tryAutoInsertRow();
            }
        };

        txtProductName.addKeyListener(tryInsertListener);
        cmbCategory.addActionListener(e -> tryAutoInsertRow());
        spnCostPrice.addChangeListener(e -> tryAutoInsertRow());
        spnSellingPrice.addChangeListener(e -> tryAutoInsertRow());
        spnQuantity.addChangeListener(e -> {
            spnNewStock.setValue(spnQuantity.getValue());
            tryAutoInsertRow();
        });
        spnMinStock.addChangeListener(e -> tryAutoInsertRow());

        btnSave.addActionListener(e -> saveProductsToDatabase());
        btnClear.addActionListener(e -> {
            tableModel.setRowCount(0);
            productNames.clear();
            updateSalesTotal();
            updateTotalEntries();
            generateInvoiceNumber();
            lblInvoiceNumber.setText("Invoice #: " + currentInvoiceNumber);
        });

        btnDelete.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                productNames.remove(tableModel.getValueAt(selectedRow, 0).toString());
                tableModel.removeRow(selectedRow);
                updateSalesTotal();
                updateTotalEntries();
            }
        });
    }

    private JPanel createLabeledField(String labelText, JComponent field, String tooltip, Dimension size, int fontSize) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setOpaque(false);
        
        Color textColor = getUniversalColor("Label.foreground", Color.WHITE);
        
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(130, 28));
        label.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        label.setForeground(textColor);
        
        field.setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        field.setToolTipText(tooltip);
        field.setPreferredSize(size);
        
        panel.add(label);
        panel.add(field);
        return panel;
    }

    private JPanel createLabeledField(String labelText, JComponent field, String tooltip, int fontSize) {
        Dimension fieldSize = getAdaptiveFieldSize();
        return createLabeledField(labelText, field, tooltip, fieldSize, fontSize);
    }

    private JSpinner createDecimalSpinner(int fontSize) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 0.01));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#,##0.00");
        spinner.setEditor(editor);
        spinner.setPreferredSize(new Dimension(130, fontSize + 16));
        
        // Set editor font
        editor.getTextField().setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        return spinner;
    }

    private JSpinner createIntegerSpinner(int fontSize) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
        spinner.setEditor(editor);
        spinner.setPreferredSize(new Dimension(130, fontSize + 16));
        
        editor.getTextField().setFont(new Font("Segoe UI", Font.PLAIN, fontSize));
        return spinner;
    }

    private void loadVendorsIntoCombo() {
        cmbVendor.removeAllItems();
        String clientId = Database.getCurrentClientId();
        String userEmail = Database.getCurrentUserEmail();

        try (Connection conn = Database.getConnection()) {
            PreparedStatement stmt;
            if (clientId != null && !clientId.isEmpty()) {
                stmt = conn.prepareStatement("SELECT name FROM vendors WHERE client_id = ? ORDER BY name");
                stmt.setString(1, clientId);
            } else {
                stmt = conn.prepareStatement("SELECT name FROM vendors WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name");
                stmt.setString(1, userEmail);
            }

            ResultSet rs = stmt.executeQuery();
            boolean hasVendors = false;
            while (rs.next()) {
                cmbVendor.addItem(rs.getString("name"));
                hasVendors = true;
            }
            rs.close();
            stmt.close();

            if (!hasVendors) {
                cmbVendor.addItem("(No vendors available)");
            } else {
                cmbVendor.setSelectedIndex(0);
            }

        } catch (SQLException e) {
            showGlassMessageDialog("Error loading vendors: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openVendorManagementDialog() {
        Productcategories.VendorDialog vendorDialog = new Productcategories.VendorDialog(this);
        vendorDialog.setVisible(true);
        loadVendorsIntoCombo();
    }

    private void toggleFloatMode() {
        Window parent = getOwner();
        
        if (isFloating) {
            setSize(originalSize);
            floatButton.setText("Float");
            setAlwaysOnTop(false);
            setUndecorated(false);
            setResizable(true);
            if (parent != null) {
                setLocationRelativeTo(parent);
            }
            setModal(false);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            revalidate();
            repaint();
        } else {
            setSize(floatSize);
            floatButton.setText("Dock");
            setAlwaysOnTop(true);
            setUndecorated(true);
            setResizable(true);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Point location = new Point(
                screenSize.width - floatSize.width - 20,
                screenSize.height - floatSize.height - 50
            );
            setLocation(location);
            setModal(false);
            setDefaultCloseOperation(HIDE_ON_CLOSE);
        }
        
        isFloating = !isFloating;
        setVisible(true);
        
        if (isFloating) {
            makeWindowDraggable();
        }
    }

    private void makeWindowDraggable() {
        final Point[] mouseOffset = {null};
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseOffset[0] = new Point(e.getX(), e.getY());
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (mouseOffset[0] != null) {
                    Point newLocation = new Point(
                        getLocation().x + e.getX() - mouseOffset[0].x,
                        getLocation().y + e.getY() - mouseOffset[0].y
                    );
                    setLocation(newLocation);
                }
            }
        });
    }

    private void loadCategories() {
        cmbCategory.removeAllItems();
        categoryMap.clear();

        String clientId = Database.getCurrentClientId();
        String userEmail = Database.getCurrentUserEmail();

        try (Connection conn = Database.getConnection()) {
            ensureCategoriesFromProducts(conn, clientId, userEmail);
            
            String sql = "SELECT id, name FROM categories WHERE client_id = ? ORDER BY name";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, clientId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    cmbCategory.addItem(name);
                    categoryMap.put(name, id);
                    System.out.println("Loaded category: " + name + " (ID: " + id + ")");
                }
            }
            
            if (cmbCategory.getItemCount() == 0 && Database.isOnline()) {
                try {
                    List<JsonObject> cloudCats = SQLiteCloudClient.execute(
                        "SELECT id, name FROM categories WHERE client_id = ?", clientId);
                    for (JsonObject cat : cloudCats) {
                        if (cat.has("name") && !cat.get("name").isJsonNull()) {
                            String name = cat.get("name").getAsString();
                            int id = cat.has("id") ? cat.get("id").getAsInt() : -1;
                            cmbCategory.addItem(name);
                            if (id > 0) categoryMap.put(name, id);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("Could not load cloud categories: " + e.getMessage());
                }
            }

            if (cmbCategory.getItemCount() == 0) {
                cmbCategory.addItem("GENERAL");
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT OR IGNORE INTO categories (name, client_id, user_email) VALUES ('GENERAL', ?, ?)")) {
                    stmt.setString(1, clientId);
                    stmt.setString(2, userEmail);
                    stmt.executeUpdate();
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM categories WHERE name = 'GENERAL' AND client_id = ?")) {
                        ps.setString(1, clientId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            categoryMap.put("GENERAL", rs.getInt("id"));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading categories", e);
            showGlassMessageDialog("Error loading categories: " + e.getMessage(), 
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensureCategoriesFromProducts(Connection conn, String clientId, String userEmail) {
        try {
            Set<String> categoryNamesFromProducts = new HashSet<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String catName = tableModel.getValueAt(i, 1).toString();
                if (catName != null && !catName.isEmpty() && !catName.startsWith("(No categories")) {
                    categoryNamesFromProducts.add(catName.toUpperCase().trim());
                }
            }
            
            for (String catName : categoryNamesFromProducts) {
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM categories WHERE UPPER(name) = UPPER(?) AND client_id = ?")) {
                    ps.setString(1, catName);
                    ps.setString(2, clientId);
                    ResultSet rs = ps.executeQuery();
                    exists = rs.next();
                }
                
                if (!exists) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO categories (name, client_id, user_email) VALUES (?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, catName.toUpperCase());
                        ps.setString(2, clientId);
                        ps.setString(3, userEmail);
                        ps.executeUpdate();
                        
                        ResultSet rs = ps.getGeneratedKeys();
                        if (rs.next()) {
                            int newId = rs.getInt(1);
                            categoryMap.put(catName.toUpperCase(), newId);
                            LOGGER.info("Auto-created category: " + catName);
                            
                            if (Database.isOnline()) {
                                try {
                                    JsonObject catData = new JsonObject();
                                    catData.addProperty("id", newId);
                                    catData.addProperty("name", catName.toUpperCase());
                                    catData.addProperty("client_id", clientId);
                                    catData.addProperty("user_email", userEmail);
                                    SQLiteCloudClient.queueSyncOperation("categories", newId, "INSERT", catData);
                                } catch (Exception ex) {
                                    LOGGER.warning("Failed to queue category sync: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("ensureCategoriesFromProducts error: " + e.getMessage());
        }
    }

    private void showGlassMessageDialog(String message, String title, int messageType) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        
        Color panelBg = getUniversalColor("Panel.background", new Color(50, 50, 60));
        Color accentColor = getUniversalColor("Component.accentColor", new Color(0, 200, 150));
        
        JPanel glassPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 220);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 150));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        glassPanel.setOpaque(false);
        glassPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        
        String icon = messageType == JOptionPane.ERROR_MESSAGE ? "❌" :
                      messageType == JOptionPane.WARNING_MESSAGE ? "⚠️" :
                      messageType == JOptionPane.QUESTION_MESSAGE ? "❓" : "ℹ️";
        
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 40));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        Color textColor = getUniversalColor("Label.foreground", Color.WHITE);
        
        JLabel messageLabel = new JLabel("<html><div style='text-align: center; width: 300px;'>" + message + "</div></html>");
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageLabel.setForeground(textColor);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JButton okButton = new JButton("OK");
        okButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        okButton.setForeground(accentColor);
        okButton.setBackground(panelBg);
        okButton.setBorder(BorderFactory.createLineBorder(accentColor, 1));
        okButton.setFocusPainted(false);
        okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okButton.addActionListener(e -> dialog.dispose());
        
        JPanel centerPanel = new JPanel(new BorderLayout(10, 15));
        centerPanel.setOpaque(false);
        centerPanel.add(iconLabel, BorderLayout.NORTH);
        centerPanel.add(messageLabel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setOpaque(false);
        buttonPanel.add(okButton);
        
        glassPanel.add(centerPanel, BorderLayout.CENTER);
        glassPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(glassPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void saveProductsToDatabase() {
        if (tableModel.getRowCount() == 0) {
            showGlassMessageDialog("No products to save!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Date selectedSaleDate = dateChooser.getDate();
        LocalDate saleDate = selectedSaleDate != null ? 
            selectedSaleDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
        final String saleDateStr = saleDate.toString();

        String vendor = (String) cmbVendor.getSelectedItem();
        final String finalVendor = (vendor == null || vendor.equals("(No vendors available)")) ? "" : vendor;

        JDialog progressDialog = createProgressDialog("Saving products...");
        JProgressBar progressBar = (JProgressBar) ((JPanel) progressDialog.getContentPane().getComponent(0)).getComponent(1);
        JLabel statusLabel = (JLabel) ((JPanel) progressDialog.getContentPane().getComponent(0)).getComponent(0);
        
        final String invoiceNumber = currentInvoiceNumber;
        
        SwingWorker<Boolean, Integer> worker = new SwingWorker<Boolean, Integer>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                String clientId = Database.getCurrentClientId();
                String userEmail = Database.getCurrentUserEmail();
                List<JsonObject> savedProducts = new ArrayList<>();
                
                try (Connection conn = Database.getConnection()) {
                    conn.setAutoCommit(false);
                    
                    Map<String, Integer> categoryIdMap = new HashMap<>();
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        String categoryName = tableModel.getValueAt(i, 1).toString();
                        if (!categoryIdMap.containsKey(categoryName)) {
                            int catId = getOrCreateCategoryId(conn, categoryName, clientId, userEmail);
                            if (catId > 0) {
                                categoryIdMap.put(categoryName, catId);
                            }
                        }
                    }
                    
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        String name = tableModel.getValueAt(i, 0).toString().trim();
                        String categoryName = tableModel.getValueAt(i, 1).toString();
                        Integer categoryId = categoryIdMap.get(categoryName);
                        
                        if (categoryId == null || categoryId == -1) {
                            LOGGER.warning("Skipping product " + name + " - category not found: " + categoryName);
                            continue;
                        }
                        
                        String costStr = tableModel.getValueAt(i, 2).toString()
                            .replace(selectedCurrency, "").replace(",", "").trim();
                        String sellStr = tableModel.getValueAt(i, 3).toString()
                            .replace(selectedCurrency, "").replace(",", "").trim();
                        int qty = Integer.parseInt(tableModel.getValueAt(i, 4).toString());
                        int min = Integer.parseInt(tableModel.getValueAt(i, 5).toString());
                        String expiryStr = tableModel.getValueAt(i, 6).toString();
                        
                        LocalDate expiryLocalDate;
                        try {
                            expiryLocalDate = LocalDate.parse(expiryStr);
                        } catch (Exception e) {
                            expiryLocalDate = LocalDate.now().plusYears(1);
                        }
                        
                        String sql = "INSERT INTO products (name, category_id, vendor, cost_price, selling_price, " +
                                     "stock_quantity, min_stock_level, expire_date, created_date, client_id, user_email) " +
                                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                        
                        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setString(1, name);
                            stmt.setInt(2, categoryId);
                            stmt.setString(3, finalVendor.isEmpty() ? null : finalVendor);
                            stmt.setBigDecimal(4, new BigDecimal(costStr));
                            stmt.setBigDecimal(5, new BigDecimal(sellStr));
                            stmt.setInt(6, qty);
                            stmt.setInt(7, min);
                            stmt.setString(8, Database.toDatabaseDate(expiryLocalDate));
                            stmt.setString(9, saleDateStr);
                            stmt.setString(10, clientId);
                            stmt.setString(11, userEmail);
                            stmt.executeUpdate();
                            
                            ResultSet rs = stmt.getGeneratedKeys();
                            if (rs.next()) {
                                int productId = rs.getInt(1);
                                
                                JsonObject productData = new JsonObject();
                                productData.addProperty("id", productId);
                                productData.addProperty("name", name);
                                productData.addProperty("category_id", categoryId);
                                productData.addProperty("vendor", finalVendor);
                                productData.addProperty("cost_price", costStr);
                                productData.addProperty("selling_price", sellStr);
                                productData.addProperty("stock_quantity", qty);
                                productData.addProperty("min_stock_level", min);
                                productData.addProperty("expire_date", expiryStr);
                                productData.addProperty("created_date", saleDateStr);
                                productData.addProperty("client_id", clientId);
                                productData.addProperty("user_email", userEmail);
                                savedProducts.add(productData);
                                
                                String flowSql = "INSERT INTO item_flows " +
                                    "(product_id, action, quantity, flow_date, created_date, vendor, user, client_id, user_email) " +
                                    "VALUES (?, 'NEW ITEM', ?, ?, ?, ?, 'SYSTEM', ?, ?)";
                                try (PreparedStatement flowStmt = conn.prepareStatement(flowSql)) {
                                    flowStmt.setInt(1, productId);
                                    flowStmt.setInt(2, qty);
                                    flowStmt.setString(3, saleDateStr);
                                    flowStmt.setString(4, saleDateStr);
                                    flowStmt.setString(5, finalVendor.isEmpty() ? null : finalVendor);
                                    flowStmt.setString(6, clientId);
                                    flowStmt.setString(7, userEmail);
                                    flowStmt.executeUpdate();
                                }
                            }
                        }
                        publish(i + 1);
                    }
                    
                    conn.commit();
                    
                    for (JsonObject productData : savedProducts) {
                        SQLiteCloudClient.queueSyncOperation("products",
                            productData.get("id").getAsInt(), "INSERT", productData);
                    }
                    
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Error saving products", e);
                    return false;
                }
                return true;
            }
            
            @Override
            protected void process(List<Integer> chunks) {
                int latest = chunks.get(chunks.size() - 1);
                progressBar.setValue(latest);
                statusLabel.setText("Saved " + latest + " of " + tableModel.getRowCount() + " products");
            }
            
            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    if (get()) {
                        int savedCount = tableModel.getRowCount();
                        tableModel.setRowCount(0);
                        productNames.clear();
                        updateSalesTotal();
                        updateTotalEntries();
                        
                        showGlassMessageDialog(
                            String.format("✅ %d products saved successfully!\nInvoice: %s\nSale Date: %s", 
                                savedCount, invoiceNumber, saleDateStr),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                        
                        generateInvoiceNumber();
                        lblInvoiceNumber.setText("Invoice #: " + currentInvoiceNumber);
                        dateChooser.setDate(new Date());
                        lblSelectedDate.setText("(Today)");
                    } else {
                        showGlassMessageDialog("Error saving products. Check database connection.", 
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Save failed", ex);
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }

    private int getOrCreateCategoryId(Connection conn, String categoryName, String clientId, String userEmail) throws SQLException {
        if (categoryName == null || categoryName.isEmpty()) return -1;
        
        String selectSql = "SELECT id FROM categories WHERE UPPER(name) = UPPER(?) AND client_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, categoryName);
            ps.setString(2, clientId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        
        String insertSql = "INSERT INTO categories (name, client_id, user_email) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, categoryName.toUpperCase());
            ps.setString(2, clientId);
            ps.setString(3, userEmail);
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int newId = rs.getInt(1);
                LOGGER.info("Auto-created category: " + categoryName + " with ID: " + newId);
                
                if (Database.isOnline()) {
                    try {
                        JsonObject catData = new JsonObject();
                        catData.addProperty("id", newId);
                        catData.addProperty("name", categoryName.toUpperCase());
                        catData.addProperty("client_id", clientId);
                        catData.addProperty("user_email", userEmail);
                        SQLiteCloudClient.queueSyncOperation("categories", newId, "INSERT", catData);
                    } catch (Exception ex) {
                        LOGGER.warning("Failed to queue category sync: " + ex.getMessage());
                    }
                }
                return newId;
            }
        }
        
        return -1;
    }
    
    private JDialog createProgressDialog(String message) {
        JDialog dialog = new JDialog(this, "Saving Products", true);
        dialog.setUndecorated(true);
        dialog.setSize(400, 130);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        Color panelBg = getUniversalColor("Panel.background", new Color(50, 50, 60));
        Color accentColor = getUniversalColor("Component.accentColor", new Color(0, 200, 150));
        Color textColor = getUniversalColor("Label.foreground", Color.WHITE);
        
        JPanel glassPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgWithAlpha = new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 220);
                g2.setColor(bgWithAlpha);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 150));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        glassPanel.setOpaque(false);
        glassPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        JLabel statusLabel = new JLabel(message);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(textColor);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(accentColor);
        progressBar.setBackground(new Color(panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue(), 255));
        
        glassPanel.add(statusLabel, BorderLayout.CENTER);
        glassPanel.add(progressBar, BorderLayout.SOUTH);
        
        dialog.add(glassPanel);
        return dialog;
    }

    private void tryAutoInsertRow() {
        String name = txtProductName.getText().trim();
        
        if (name.isEmpty() || productNames.contains(name) || expiryDate == null) {
            return;
        }

        String category = (String) cmbCategory.getSelectedItem();
        if (category == null || category.isEmpty() || category.startsWith("(No categories")) {
            showGlassMessageDialog("Please select a valid category before adding product.", 
                "Category Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Integer categoryId = categoryMap.get(category);
        if (categoryId == null || categoryId == -1) {
            showGlassMessageDialog("Invalid category selected. Please refresh categories.", 
                "Category Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double cost = ((Number) spnCostPrice.getValue()).doubleValue();
        double sell = ((Number) spnSellingPrice.getValue()).doubleValue();
        int qty = ((Number) spnQuantity.getValue()).intValue();
        int min = ((Number) spnMinStock.getValue()).intValue();

        DecimalFormat df = new DecimalFormat("#,###.00");
        tableModel.addRow(new Object[]{
                name,
                category,
                selectedCurrency + " " + df.format(cost),
                selectedCurrency + " " + df.format(sell),
                qty,
                min,
                expiryDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString()
        });

        productNames.add(name);
        updateSalesTotal();
        updateTotalEntries();
        clearFields();
    }

    private void updateSalesTotal() {
        double total = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            try {
                String costStr = tableModel.getValueAt(i, 2).toString().replace(selectedCurrency, "").replace(",", "").trim();
                int qty = Integer.parseInt(tableModel.getValueAt(i, 4).toString());
                total += Double.parseDouble(costStr) * qty;
            } catch (Exception ignored) {
            }
        }
        lblSalesTotal.setText("Grand Total: " + selectedCurrency + " " + new DecimalFormat("#,###.00").format(total));
    }

    private void updateTotalEntries() {
        lblTotalEntries.setText("Total Entries: " + tableModel.getRowCount());
    }

    private void clearFields() {
        txtProductName.setText("");
        expiryDate = null;
        lblExpiryDate.setText("Select Date to Save");
        spnCostPrice.setValue(0.0);
        spnSellingPrice.setValue(0.0);
        spnQuantity.setValue(0);
        spnMinStock.setValue(0);
        spnNewStock.setValue(0);
    }
}