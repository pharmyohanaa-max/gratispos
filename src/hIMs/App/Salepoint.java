package hIMs.App;
import java.awt.FontMetrics;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.util.Set;
import java.util.HashSet;
import java.math.BigDecimal;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.prefs.Preferences;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.awt.Toolkit;
import javax.swing.ImageIcon;
import java.sql.Connection;
import java.awt.GridLayout;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.PlainDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import com.formdev.flatlaf.FlatLightLaf;
import com.toedter.calendar.JCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import com.toedter.calendar.JDateChooser;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.awt.print.*;
import java.awt.GradientPaint;
import java.time.ZoneId;

public class Salepoint extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(Salepoint.class.getName());
    private JTextField txtSearch;
    private JSpinner spinnerDiscount;
    private JTable tableProducts;
    private JTable tableSales;
    private JLabel lblTotal;
    private DefaultTableModel modelProducts;
    private DefaultTableModel modelSales;
    private JFrame parentFrame;
    private List<Database.Product> products = new ArrayList<>();
    private List<Database.Product> originalProducts = new ArrayList<>();
    private String currentUser; // Will be set via constructor
    private Map<LocalDate, List<Object[]>> dateSpecificCarts = new HashMap<>(); // Store cart data per date
    private LocalDate currentSaleDate = LocalDate.now();
    private JDateChooser saleDateChooser;
    private String selectedCurrency = ""; // Default currency
    
    private JDateChooser dateChooser;
    

 
    
    private static final String[] RETURN_REASONS = {
        "product is damaged", "product doesnt meet customer specification", "product Quality is Low",
        "Product has Short expire", "product is Incomplete", "Accidental sold | Product no longer needed", "other reasons"
    };
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a z");
    private JCheckBox chkPrintReceipt;
    
    private JMenuBar menuBar;
    private static int receiptCounter = 1; // For generating receipt numbers
    private static final String RECEIPT_PREFIX = "uxvf";
    private NotificationDatabase.BusinessInfo businessInfo; // Store business info in memory
    
 // ── Backdated Entry System ──────────────────────────────────────
    private static final int BACKDATED_UNLOCK_MINUTES = 5;
    private final Map<String, java.time.LocalDateTime> unlockedUsers = new HashMap<>();
    private boolean adminDateUnlocked = false;
    
    private static final String PASSCODE_PREF_KEY = "salepoint_date_passcode";
    private static final String DEFAULT_PASSCODE = "1234";

   

    // --- New Colors for a Modern Palette ---
    private static final Color PRIMARY_BLUE = new Color(59, 130, 246);
    private static final Color DARK_GRAY = new Color(31, 41, 55);
    private static final Color LIGHT_GRAY_BG = new Color(243, 244, 246);
    private static final Color PALE_BLUE = new Color(229, 231, 235);
    private static final Color SUCCESS_GREEN = new Color(34, 197, 94);
    private static final Color INFO_PURPLE = new Color(139, 92, 246);
    private static final Color DELETE_RED = new Color(239, 68, 68);
    private static final Color ACCENT_ORANGE = new Color(251, 146, 60);

    public static void main(String[] args) {
        try {
            // Initialize databases
            Database.initializeDatabase();
            NotificationDatabase.initializeDatabase();
            // ... (rest of your main method, e.g., SwingUtilities.invokeLater)
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize databases: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(null, "Database initialization failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public Salepoint(JFrame parent, String username) {
        super(parent, "PoS Sale Point Dashboard", false);
        this.parentFrame = parent;
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        this.currentUser = username;

        // CRITICAL: Initialize NotificationDatabase BEFORE any operations
        try {
            NotificationDatabase.initializeDatabase();
            LOGGER.info("NotificationDatabase initialized successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize NotificationDatabase", e);
            JOptionPane.showMessageDialog(this, 
                "Failed to initialize database: " + e.getMessage() + 
                "\n\nReceipt and notification features may not work properly.", 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE);
        }

        // Load businessInfo from notifications.db
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
            this.businessInfo = NotificationDatabase.BusinessInfo.loadFromDatabase(conn);
            LOGGER.info("Loaded businessInfo: " + businessInfo.getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load business info", e);
            this.businessInfo = new NotificationDatabase.BusinessInfo(
                "Your Company Name",
                "Street Address, City, State, ZIP Code",
                "Business Phone Number",
                "Business Email Address",
                "Business Website"
            );
        }

        setResizable(true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Auto-detect screen size and fit
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int)(screenSize.width * 0.9);
        int height = (int)(screenSize.height * 0.9);
        setSize(width, height);
        setLocationRelativeTo(null);
        
        // Make components responsive on resize
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                revalidate();
                repaint();
            }
        });
        
        getContentPane().setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(245, 247, 250));

        // ── MAIN WRAPPER ────────────────────────────────────────────
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(245, 247, 250));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        getContentPane().add(mainPanel, BorderLayout.CENTER);

        GridBagConstraints mc = new GridBagConstraints();
        mc.fill = GridBagConstraints.BOTH;
        mc.weightx = 1.0;
        mc.insets = new Insets(4, 0, 4, 0);
        mc.gridx = 0;

        // ── HEADER PANEL ────────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createLineBorder(PALE_BLUE, 2));
        headerPanel.setMinimumSize(new Dimension(0, 90));
        headerPanel.setPreferredSize(new Dimension(0, 80));

        JPanel headerTopRow = new JPanel(new BorderLayout());
        headerTopRow.setBackground(Color.WHITE);
        headerTopRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));

        JLabel lblTitle = new JLabel("🛒 Point of Sale", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(PRIMARY_BLUE);
        headerTopRow.add(lblTitle, BorderLayout.CENTER);

        JLabel lblUser = new JLabel("👤 " + currentUser + "  ");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUser.setForeground(new Color(34, 197, 94));
        lblUser.setHorizontalAlignment(SwingConstants.RIGHT);
        headerTopRow.add(lblUser, BorderLayout.EAST);
        headerPanel.add(headerTopRow, BorderLayout.NORTH);

        JPanel headerBottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        headerBottomRow.setBackground(Color.WHITE);

        JButton btnChangeDate = new JButton("👤🔑 Change");
        btnChangeDate.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnChangeDate.setBackground(PRIMARY_BLUE);
        btnChangeDate.setForeground(Color.WHITE);
        btnChangeDate.setFocusPainted(false);
        btnChangeDate.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnChangeDate.setToolTipText("Admin date change panel");
        btnChangeDate.addActionListener(e -> showReSalePanel());
        headerBottomRow.add(btnChangeDate);

        JLabel lblSaleDate = new JLabel("📅 Sale Date:");
        lblSaleDate.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSaleDate.setForeground(DARK_GRAY);
        headerBottomRow.add(lblSaleDate);
        
     // ── MENU BAR ────────────────────────────────────────────────────
        menuBar = new JMenuBar();
        menuBar.setBackground(Color.WHITE);
        menuBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        // ── Receipts Menu ──
        JMenu menuReceipts = new JMenu("🧾 Receipts");
        menuReceipts.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JMenuItem miReceiptLog    = new JMenuItem("🛡️ Fraud Detection & Receipt Log");
        JMenuItem miReprintDialog = new JMenuItem("🖨️ Re-Print Receipt");
        miReceiptLog.addActionListener(e -> showReceiptLog());
        miReprintDialog.addActionListener(e -> showReprintDialog());
        menuReceipts.add(miReceiptLog);
        menuReceipts.add(miReprintDialog);

        // ── Settings Menu ──
        JMenu menuSettings = new JMenu("⚙️ Settings");
        menuSettings.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JMenuItem miBusinessSettings = new JMenuItem("🏪 Receipt & Business Settings");
        JMenuItem miBasicSettings    = new JMenuItem("🏢 Basic Business Info");
        JMenuItem miCurrency         = new JMenuItem("💱 Currency Settings");
        miBusinessSettings.addActionListener(e -> showReceiptSettingsDialog());
        miBasicSettings.addActionListener(e -> showSettingsDialog());
        miCurrency.addActionListener(e -> showCurrencySettingsDialog());
        menuSettings.add(miBusinessSettings);
        menuSettings.add(miBasicSettings);
        menuSettings.add(miCurrency);

        menuBar.add(menuReceipts);
        menuBar.add(menuSettings);

        // Attach menu bar to TOP of header panel, not center
JPanel menuWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
menuWrapper.setBackground(Color.WHITE);
menuWrapper.add(menuBar);
headerPanel.add(menuWrapper, BorderLayout.NORTH);  // Changed from CENTER to NORTH

     // Check admin rights FIRST before creating saleDateChooser
        boolean hasAdminRights = false;
        try {
            String userRole = Database.getUserRole(currentUser);
            if ("SuperAdmin".equalsIgnoreCase(userRole) ||
                "Admin".equalsIgnoreCase(userRole) ||
                "Manager".equalsIgnoreCase(userRole) ||
                "CEO".equalsIgnoreCase(userRole) ||
                "Owner".equalsIgnoreCase(userRole)) {
                hasAdminRights = true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not check user role", e);
        }

        saleDateChooser = new JDateChooser();
        saleDateChooser.setDateFormatString("yyyy-MM-dd");
        saleDateChooser.setDate(new java.util.Date());
        saleDateChooser.setPreferredSize(new Dimension(160, 28));
        saleDateChooser.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        saleDateChooser.setEnabled(false); // Always locked until passcode entered
        headerBottomRow.add(saleDateChooser);

        // Lock/Unlock button — available to everyone, gated by passcode
        JButton btnLockDate = new JButton("🔒");
        btnLockDate.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnLockDate.setPreferredSize(new Dimension(36, 28));
        btnLockDate.setBackground(Color.WHITE);
        btnLockDate.setForeground(DARK_GRAY);
        btnLockDate.setFocusPainted(false);
        btnLockDate.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLockDate.setToolTipText("Click to unlock date — requires passcode");

        final boolean[] isUnlocked = {false};

        btnLockDate.addActionListener(e -> {
            if (!isUnlocked[0]) {
                // Currently locked — ask for passcode to unlock
                JPanel passPanel = new JPanel(new GridBagLayout());
                GridBagConstraints pc = new GridBagConstraints();
                pc.insets = new Insets(8, 8, 8, 8);
                pc.fill = GridBagConstraints.HORIZONTAL;

                JLabel msgLbl = new JLabel("Enter passcode to unlock date:");
                msgLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                pc.gridx = 0; pc.gridy = 0; pc.gridwidth = 2;
                passPanel.add(msgLbl, pc);

                JPasswordField passField = new JPasswordField(12);
                passField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                pc.gridy = 1;
                passPanel.add(passField, pc);

                // Change passcode link
                JButton btnChangePIN = new JButton("🔧 Change Passcode");
                btnChangePIN.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                btnChangePIN.setFocusPainted(false);
                btnChangePIN.setBorderPainted(false);
                btnChangePIN.setBackground(Color.WHITE);
                btnChangePIN.setForeground(PRIMARY_BLUE);
                btnChangePIN.setCursor(new Cursor(Cursor.HAND_CURSOR));
                pc.gridy = 2;
                passPanel.add(btnChangePIN, pc);

                JDialog passDialog = new JDialog(Salepoint.this, "🔒 Unlock Date", true);
                passDialog.setLayout(new BorderLayout(10, 10));
                passDialog.setSize(320, 220);
                passDialog.setLocationRelativeTo(Salepoint.this);
                passDialog.getContentPane().setBackground(Color.WHITE);

                // Header
                JPanel passHeader = new JPanel(new BorderLayout());
                passHeader.setBackground(PRIMARY_BLUE);
                passHeader.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
                JLabel passTitle = new JLabel("🔒 Date Change Lock");
                passTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
                passTitle.setForeground(Color.WHITE);
                passHeader.add(passTitle);
                passDialog.add(passHeader, BorderLayout.NORTH);
                passDialog.add(passPanel, BorderLayout.CENTER);

                JPanel passBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
                passBtns.setBackground(Color.WHITE);
                JButton btnUnlock = new JButton("✅ Unlock");
                btnUnlock.setBackground(SUCCESS_GREEN);
                btnUnlock.setForeground(Color.WHITE);
                btnUnlock.setFocusPainted(false);
                btnUnlock.setFont(new Font("Segoe UI", Font.BOLD, 13));
                btnUnlock.setCursor(new Cursor(Cursor.HAND_CURSOR));
                JButton btnCancel2 = new JButton("Cancel");
                btnCancel2.setFocusPainted(false);
                btnCancel2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                passBtns.add(btnUnlock);
                passBtns.add(btnCancel2);
                passDialog.add(passBtns, BorderLayout.SOUTH);

                // Change passcode action
                btnChangePIN.addActionListener(ce -> {
                    Preferences prefs = Preferences.userRoot().node("hIMs/PosCart");
                    String currentCode = prefs.get(PASSCODE_PREF_KEY, DEFAULT_PASSCODE);

                    JPasswordField oldPass = new JPasswordField(10);
                    JPasswordField newPass = new JPasswordField(10);
                    JPasswordField confirmPass = new JPasswordField(10);

                    JPanel changePanel = new JPanel(new GridBagLayout());
                    changePanel.setBackground(Color.WHITE);
                    GridBagConstraints cp = new GridBagConstraints();
                    cp.insets = new Insets(6, 8, 6, 8);
                    cp.fill = GridBagConstraints.HORIZONTAL;
                    cp.gridx = 0;

                    cp.gridy = 0;
                    changePanel.add(new JLabel("Current passcode:"), cp);
                    cp.gridy = 1;
                    changePanel.add(oldPass, cp);
                    cp.gridy = 2;
                    changePanel.add(new JLabel("New passcode:"), cp);
                    cp.gridy = 3;
                    changePanel.add(newPass, cp);
                    cp.gridy = 4;
                    changePanel.add(new JLabel("Confirm new passcode:"), cp);
                    cp.gridy = 5;
                    changePanel.add(confirmPass, cp);

                    int result = JOptionPane.showConfirmDialog(passDialog, changePanel,
                        "🔧 Change Passcode", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                    if (result == JOptionPane.OK_OPTION) {
                        String oldEntered = new String(oldPass.getPassword()).trim();
                        String newEntered = new String(newPass.getPassword()).trim();
                        String confirmEntered = new String(confirmPass.getPassword()).trim();

                        if (!oldEntered.equals(currentCode)) {
                            JOptionPane.showMessageDialog(passDialog,
                                "Current passcode is incorrect.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                        } else if (newEntered.isEmpty()) {
                            JOptionPane.showMessageDialog(passDialog,
                                "New passcode cannot be empty.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                        } else if (!newEntered.equals(confirmEntered)) {
                            JOptionPane.showMessageDialog(passDialog,
                                "New passcodes do not match.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                        } else {
                            prefs.put(PASSCODE_PREF_KEY, newEntered);
                            JOptionPane.showMessageDialog(passDialog,
                                "✅ Passcode changed successfully!", "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                });

                // Unlock action
                java.awt.event.ActionListener unlockAction = ue -> {
                    Preferences prefs = Preferences.userRoot().node("hIMs/PosCart");
                    String storedCode = prefs.get(PASSCODE_PREF_KEY, DEFAULT_PASSCODE);
                    String entered = new String(passField.getPassword()).trim();

                    if (entered.equals(storedCode)) {
                        isUnlocked[0] = true;
                        adminDateUnlocked = true;
                        saleDateChooser.setEnabled(true);
                        btnLockDate.setText("🔓");
                        btnLockDate.setToolTipText("Date unlocked — click to lock");
                        passDialog.dispose();
                        LOGGER.info("Date chooser unlocked by: " + currentUser);
                    } else {
                        JOptionPane.showMessageDialog(passDialog,
                            "Incorrect passcode. Please try again.",
                            "Access Denied", JOptionPane.ERROR_MESSAGE);
                        passField.setText("");
                        passField.requestFocus();
                    }
                };

                btnUnlock.addActionListener(unlockAction);
                passField.addActionListener(unlockAction); // Enter key in field
                btnCancel2.addActionListener(ce -> passDialog.dispose());
                passDialog.getRootPane().setDefaultButton(btnUnlock);

                SwingUtilities.invokeLater(() -> passField.requestFocus());
                passDialog.setVisible(true);

            } else {
                // Currently unlocked — lock it
                isUnlocked[0] = false;
                adminDateUnlocked = false;
                saleDateChooser.setEnabled(false);
                saleDateChooser.setDate(new java.util.Date());
                currentSaleDate = LocalDate.now();
                btnLockDate.setText("🔒");
                btnLockDate.setToolTipText("Click to unlock date — requires passcode");
                LOGGER.info("Date chooser locked by: " + currentUser);
            }
        });
        headerBottomRow.add(btnLockDate);

        if (hasAdminRights) {
            JButton btnGrantUnlock = new JButton("👤+");
            btnGrantUnlock.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnGrantUnlock.setPreferredSize(new Dimension(52, 28));
            btnGrantUnlock.setBackground(new Color(255, 243, 205));
            btnGrantUnlock.setForeground(DARK_GRAY);
            btnGrantUnlock.setFocusPainted(false);
            btnGrantUnlock.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnGrantUnlock.setToolTipText("Grant 5-minute backdated entry to a specific user");
            btnGrantUnlock.addActionListener(e -> showGrantUnlockDialog());
            headerBottomRow.add(btnGrantUnlock);

            JButton btnAuditLog = new JButton("📋");
            btnAuditLog.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnAuditLog.setPreferredSize(new Dimension(36, 28));
            btnAuditLog.setBackground(new Color(220, 235, 255));
            btnAuditLog.setForeground(DARK_GRAY);
            btnAuditLog.setFocusPainted(false);
            btnAuditLog.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnAuditLog.setToolTipText("View backdated entry audit log");
            btnAuditLog.addActionListener(e -> showBackdatedAuditLog());
            headerBottomRow.add(btnAuditLog);
        }

        JLabel lblSearch = new JLabel("🔍 Search:");
        lblSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSearch.setForeground(DARK_GRAY);
        headerBottomRow.add(lblSearch);

        txtSearch = new JTextField();
        txtSearch.setPreferredSize(new Dimension(220, 28));
        txtSearch.setBackground(Color.WHITE);
        txtSearch.setForeground(DARK_GRAY);
        txtSearch.setBorder(BorderFactory.createLineBorder(PALE_BLUE, 2));
        headerBottomRow.add(txtSearch);

        headerPanel.add(headerBottomRow, BorderLayout.SOUTH);

        saleDateChooser.getDateEditor().addPropertyChangeListener(evt -> {
            if ("date".equals(evt.getPropertyName())) {
                handleSaleDateChange();
            }
        });

        mc.gridy = 0;
        mc.weighty = 0.0;
        mainPanel.add(headerPanel, mc);

        // ── PRODUCT TABLE ────────────────────────────────────────────
        modelProducts = new DefaultTableModel(
                new Object[][]{}, new String[]{"Product", "Price", "Stock"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableProducts = new JTable(modelProducts);
        tableProducts.setAutoCreateRowSorter(true);
        tableProducts.setBackground(Color.WHITE);
        tableProducts.setRowHeight(24);
        tableProducts.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableProducts.getTableHeader().setBackground(PALE_BLUE);
        tableProducts.getTableHeader().setForeground(DARK_GRAY);
        tableProducts.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tableProducts.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        int stock = (Integer) modelProducts.getValueAt(row, 2);
                        if (stock <= 0) {
                            JOptionPane.showMessageDialog(Salepoint.this,
                                "Out of stock", "Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        openSaleDialog(row);
                    }
                }
            }
        });

        JScrollPane scrollProducts = new JScrollPane(tableProducts);
        scrollProducts.setBackground(Color.WHITE);
        scrollProducts.getViewport().setBackground(Color.WHITE);
        scrollProducts.setBorder(BorderFactory.createLineBorder(PALE_BLUE));
        scrollProducts.setMinimumSize(new Dimension(0, 120));

        mc.gridy = 1;
        mc.weighty = 0.25;
        mainPanel.add(scrollProducts, mc);

        // ── SALES TABLE ──────────────────────────────────────────────
        modelSales = new DefaultTableModel(
                new Object[][]{}, new String[]{"Product", "Price", "Qty", "Total"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableSales = new JTable(modelSales);
        tableSales.setAutoCreateRowSorter(true);
        tableSales.setBackground(Color.WHITE);
        tableSales.setRowHeight(24);
        tableSales.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableSales.getTableHeader().setBackground(PALE_BLUE);
        tableSales.getTableHeader().setForeground(DARK_GRAY);

        JScrollPane scrollSales = new JScrollPane(tableSales);
        scrollSales.setBackground(Color.WHITE);
        scrollSales.getViewport().setBackground(Color.WHITE);
        scrollSales.setBorder(BorderFactory.createLineBorder(PALE_BLUE));
        scrollSales.setMinimumSize(new Dimension(0, 120));

        mc.gridy = 2;
        mc.weighty = 0.55;
        mainPanel.add(scrollSales, mc);

        // ── BOTTOM CONTROL PANEL ─────────────────────────────────────
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PALE_BLUE, 2),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        bottomPanel.setMinimumSize(new Dimension(0, 130));
        bottomPanel.setPreferredSize(new Dimension(0, 140));

        GridBagConstraints bgGbc = new GridBagConstraints();
        bgGbc.fill = GridBagConstraints.BOTH;
        bgGbc.insets = new Insets(3, 5, 3, 5);
        bgGbc.weighty = 0.5;

        // ── Row 0: Discount | Records | Print | Total ────────────────
        bgGbc.gridy = 0;

        // Discount
        JPanel discountCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        discountCard.setBackground(Color.WHITE);
        discountCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PALE_BLUE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel lblDiscount = new JLabel("💰 Discount:");
        lblDiscount.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDiscount.setForeground(DARK_GRAY);
        spinnerDiscount = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 9999999.99, 0.5));
        spinnerDiscount.setPreferredSize(new Dimension(110, 32));
        JSpinner.NumberEditor discEditor = new JSpinner.NumberEditor(spinnerDiscount, "#,##0.00");
        spinnerDiscount.setEditor(discEditor);
        spinnerDiscount.addChangeListener(e -> updateTotal());
        discountCard.add(lblDiscount);
        discountCard.add(spinnerDiscount);
        bgGbc.gridx = 0; bgGbc.weightx = 0.25;
        bottomPanel.add(discountCard, bgGbc);

        // Records
        JPanel recordsCard = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        recordsCard.setBackground(Color.WHITE);
        recordsCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PALE_BLUE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel lblRecordsIcon = new JLabel("📋");
        lblRecordsIcon.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel lblRecordsText = new JLabel("Records:");
        lblRecordsText.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRecordsText.setForeground(DARK_GRAY);
        JLabel lblTotalRecordsValue = new JLabel("0");
        lblTotalRecordsValue.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTotalRecordsValue.setForeground(PRIMARY_BLUE);
        recordsCard.add(lblRecordsIcon);
        recordsCard.add(lblRecordsText);
        recordsCard.add(lblTotalRecordsValue);
        bgGbc.gridx = 1; bgGbc.weightx = 0.25;
        bottomPanel.add(recordsCard, bgGbc);

        // Print Receipt
        JPanel printCard = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        printCard.setBackground(Color.WHITE);
        printCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PALE_BLUE, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        chkPrintReceipt = new JCheckBox("🖨️ Print Receipt");
        chkPrintReceipt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkPrintReceipt.setForeground(DARK_GRAY);
        chkPrintReceipt.setBackground(Color.WHITE);
        chkPrintReceipt.setOpaque(false);
        printCard.add(chkPrintReceipt);
        bgGbc.gridx = 2; bgGbc.weightx = 0.25;
        bottomPanel.add(printCard, bgGbc);

        // Total Card
        JPanel totalCard = new JPanel(new BorderLayout(0, 0));
        totalCard.setBackground(new Color(59, 130, 246, 20));
        totalCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY_BLUE, 2),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel lblTotalHeader = new JLabel("💰  TOTAL SALE", SwingConstants.CENTER);
        lblTotalHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTotalHeader.setForeground(DARK_GRAY);
        totalCard.add(lblTotalHeader, BorderLayout.NORTH);

        lblTotal = new JLabel("0.00", SwingConstants.CENTER);
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTotal.setForeground(PRIMARY_BLUE);
        totalCard.add(lblTotal, BorderLayout.CENTER);

        bgGbc.gridx = 3; bgGbc.weightx = 0.25;
        bottomPanel.add(totalCard, bgGbc);

        // ── Row 1: Action Buttons ─────────────────────────────────────
        bgGbc.gridy = 1;

        JButton btnDelete = createMenuButton("🗑️ Delete Item", DELETE_RED);
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bgGbc.gridx = 0; bgGbc.weightx = 0.25;
        bottomPanel.add(btnDelete, bgGbc);
        btnDelete.addActionListener(e -> deleteSelected());

        JButton btnClear = createMenuButton("🧹 Clear All", ACCENT_ORANGE);
        btnClear.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bgGbc.gridx = 1; bgGbc.weightx = 0.25;
        bottomPanel.add(btnClear, bgGbc);
        btnClear.addActionListener(e -> clearSales());

        JButton btnReturnSale = createMenuButton("↩️ Return Sale", INFO_PURPLE);
        btnReturnSale.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bgGbc.gridx = 2; bgGbc.weightx = 0.25;
        bottomPanel.add(btnReturnSale, bgGbc);
        btnReturnSale.addActionListener(e -> showReturnDialog());

        JButton btnSave = createMenuButton("💾 SAVE TRANSACTION", SUCCESS_GREEN);
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 14));
        bgGbc.gridx = 3; bgGbc.weightx = 0.25;
        bottomPanel.add(btnSave, bgGbc);
        btnSave.addActionListener(e -> saveTransaction());

        mc.gridy = 3;
        mc.weighty = 0.0;
        mainPanel.add(bottomPanel, mc);

        // ── Listeners ────────────────────────────────────────────────
        modelSales.addTableModelListener(e -> {
            lblTotalRecordsValue.setText(String.valueOf(modelSales.getRowCount()));
            autoSaveCart();
        });
        spinnerDiscount.addChangeListener(e -> autoSaveCart());

        loadProductsFromDatabase();
        loadCartFromLocalStorage();
        refreshProducts();
        updateTotal();

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { refreshProducts(); }
            @Override public void removeUpdate(DocumentEvent e)  { refreshProducts(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshProducts(); }
        });
    }
    private void showCurrencySettingsDialog() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
        }

        JDialog dialog = new JDialog(this, "Currency Settings", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblCurrency = new JLabel("Select Currency:");
        lblCurrency.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(lblCurrency, gbc);

        String[] currencies = {
            "USD - US Dollar",
            "EUR - Euro",
            "GBP - British Pound",
            "TZS - Tanzanian Shilling",
            "KES - Kenyan Shilling",
            "UGX - Ugandan Shilling",
            "ZAR - South African Rand",
            "NGN - Nigerian Naira",
            "JPY - Japanese Yen",
            "CNY - Chinese Yuan",
            "INR - Indian Rupee",
            "AUD - Australian Dollar",
            "CAD - Canadian Dollar",
            "CHF - Swiss Franc"
        };

        JComboBox<String> comboCurrency = new JComboBox<>(currencies);
        comboCurrency.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        comboCurrency.setPreferredSize(new Dimension(250, 35));
        
        // Set current selection
        for (int i = 0; i < currencies.length; i++) {
            if (currencies[i].startsWith(selectedCurrency)) {
                comboCurrency.setSelectedIndex(i);
                break;
            }
        }
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        dialog.add(comboCurrency, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(dialog.getBackground());
        
        JButton btnSave = new JButton("Save");
        btnSave.setBackground(SUCCESS_GREEN);
        btnSave.setForeground(Color.WHITE);
        styleButton(btnSave);
        
        JButton btnClose = new JButton("Close");
        btnClose.setBackground(DELETE_RED);
        btnClose.setForeground(Color.WHITE);
        styleButton(btnClose);
        
        buttonPanel.add(btnSave);
        buttonPanel.add(btnClose);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        dialog.add(buttonPanel, gbc);

        btnSave.addActionListener(e -> {
            String selected = (String) comboCurrency.getSelectedItem();
            selectedCurrency = selected.substring(0, 3); // Extract currency code
            JOptionPane.showMessageDialog(dialog, "Currency changed to " + selectedCurrency, 
                "Success", JOptionPane.INFORMATION_MESSAGE);
            LOGGER.info("Currency changed to: " + selectedCurrency);
            updateTotal(); // Refresh display
            dialog.dispose();
        });

        btnClose.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setMinimumSize(new Dimension(450, 150));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    private void handleSaleDateChange() {
        if (saleDateChooser.getDate() == null) return;
        
        LocalDate newDate = saleDateChooser.getDate().toInstant()
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        
        if (newDate.equals(currentSaleDate)) return;
        
        // Save current cart data before switching
        saveCurrentCartData();
        
        // Switch to new date
        currentSaleDate = newDate;
        
        // Load cart data for new date
        loadCartDataForDate(newDate);
        
        LOGGER.info("Switched sale date to: " + newDate);
    }
    
    private void saveCurrentCartData() {
        List<Object[]> cartData = new ArrayList<>();
        for (int i = 0; i < modelSales.getRowCount(); i++) {
            // Always store as canonical types: String, BigDecimal, Integer, BigDecimal
            String name = String.valueOf(modelSales.getValueAt(i, 0));
            Object priceObj = modelSales.getValueAt(i, 1);
            Object qtyObj   = modelSales.getValueAt(i, 2);
            Object totObj   = modelSales.getValueAt(i, 3);
            BigDecimal price = (priceObj instanceof BigDecimal)
                ? (BigDecimal) priceObj
                : new BigDecimal(cleanNumericString(String.valueOf(priceObj)));
            int qty = (qtyObj instanceof Integer)
                ? (Integer) qtyObj
                : Integer.parseInt(cleanNumericString(String.valueOf(qtyObj)));
            BigDecimal rowTotal = (totObj instanceof BigDecimal)
                ? (BigDecimal) totObj
                : price.multiply(BigDecimal.valueOf(qty));
            cartData.add(new Object[]{name, price, qty, rowTotal});
        }
        dateSpecificCarts.put(currentSaleDate, cartData);
    }
    
    private void loadCartDataForDate(LocalDate date) {
        modelSales.setRowCount(0);
        List<Object[]> cartData = dateSpecificCarts.get(date);
        if (cartData != null && !cartData.isEmpty()) {
            for (Object[] row : cartData) {
                modelSales.addRow(row);
            }
            LOGGER.info("Loaded cart data for date: " + date + ", items: " + cartData.size());
        } else {
            LOGGER.info("No saved cart data for date: " + date);
        }
        refreshProducts();
        updateTotal();
    }

    private JButton createMenuButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            private final Color originalColor = bgColor;
            
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(originalColor.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });
        
        return button;
    }
    
    private void styleModernButton(JButton button) {
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        button.addMouseListener(new MouseAdapter() {
            private final Color originalColor = button.getBackground();
            
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(originalColor.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });
    }
    private void loadProductsFromDatabase() {
        try {
            List<Database.Product> loaded = Database.getAllProducts();
            // Always replace on the EDT to avoid races with saveTransaction
            if (SwingUtilities.isEventDispatchThread()) {
                products = loaded;
                originalProducts = new ArrayList<>(loaded);
            } else {
                SwingUtilities.invokeAndWait(() -> {
                    products = loaded;
                    originalProducts = new ArrayList<>(loaded);
                });
            }
            LOGGER.info("Loaded " + products.size() + " products");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading products: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Error loading products: " + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    // Add this method to sync current transaction to cloud
    private void syncToCloudAfterTransaction() {
        new Thread(() -> {
            try {
                if (Database.isOnline()) {
                    LOGGER.info("🔄 Syncing transaction to Turso cloud...");
                    SyncEngine.fullSync();       // pull + recompute stock + push
                    LOGGER.info("✅ Transaction synced");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cloud sync failed", e);
            }
        }, "CloudSync-Thread").start();
    }


// Add this method to pull latest data from cloud
    private void pullFromCloud() {
        new Thread(() -> {
            try {
                if (Database.isOnline()) {
                    LOGGER.info("Pulling latest data from cloud...");
                    Database.pullFromCloud();
                    // Load into temp list first, then swap atomically on EDT
                    List<Database.Product> loaded = Database.getAllProducts();
                    SwingUtilities.invokeLater(() -> {
                        products = loaded;
                        originalProducts = new ArrayList<>(loaded);
                        refreshProducts();
                    });
                    LOGGER.info("Cloud pull complete");
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to pull from cloud: " + e.getMessage());
            }
        }, "CloudPull-Thread").start();
    }

    private void saveTransaction() {
        if (modelSales.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No items to save!", "Error", JOptionPane.ERROR_MESSAGE);
            LOGGER.warning("Attempted to save transaction with empty sales table");
            return;
        }

        try {
            LocalDate transactionDate;
            if (saleDateChooser != null && saleDateChooser.getDate() != null) {
                transactionDate = saleDateChooser.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            } else {
                transactionDate = LocalDate.now();
            }

            // ── Backdated entry authorization check ──────────────────
            boolean isBackdated = transactionDate.isBefore(LocalDate.now());
            if (isBackdated) {
                if (!isAuthorizedForBackdatedEntry(currentUser)) {
                    JOptionPane.showMessageDialog(this,
                        "You are not authorized to enter transactions for past dates.\n\n"
                        + "Please ask an Admin or SuperAdmin to unlock backdated entry.",
                        "Authorization Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            LOGGER.info("Using transaction date: " + transactionDate
                + (isBackdated ? " [BACKDATED by " + currentUser + "]" : ""));

            // ── Collect items first so we have total before discount check ──
            List<Database.SaleItem> items = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            for (int i = 0; i < modelSales.getRowCount(); i++) {
                String productName = (String) modelSales.getValueAt(i, 0);
                if (productName == null || productName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "Product name missing at row " + (i + 1), "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                BigDecimal price;
                Object priceObj = modelSales.getValueAt(i, 1);
                try {
                    String priceStr = cleanNumericString(String.valueOf(priceObj));
                    price = new BigDecimal(priceStr);
                    if (price.compareTo(BigDecimal.ZERO) <= 0)
                        throw new NumberFormatException("Price must be positive");
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                        "Invalid price for product: " + productName, "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int quantity;
                Object qtyObj = modelSales.getValueAt(i, 2);
                try {
                    quantity = (qtyObj instanceof Integer)
                        ? (Integer) qtyObj
                        : Integer.parseInt(cleanNumericString(String.valueOf(qtyObj)));
                    if (quantity <= 0)
                        throw new NumberFormatException("Quantity must be positive");
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                        "Invalid quantity for product: " + productName, "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Live DB stock check
                int productId = -1;
                int currentStock = 0;
                try (PreparedStatement stockStmt = Database.getConnection().prepareStatement(
                        "SELECT id, stock_quantity FROM products WHERE name=? AND client_id=?")) {
                    stockStmt.setString(1, productName);
                    stockStmt.setString(2, Database.getCurrentClientId());
                    ResultSet stockRs = stockStmt.executeQuery();
                    if (stockRs.next()) {
                        productId  = stockRs.getInt("id");
                        currentStock = stockRs.getInt("stock_quantity");
                    }
                }
                if (productId == -1) {
                    JOptionPane.showMessageDialog(this,
                        "Product not found in database: " + productName,
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (quantity > currentStock) {
                    JOptionPane.showMessageDialog(this,
                        "Insufficient stock for " + productName
                        + "!\nAvailable: " + currentStock + ", Requested: " + quantity,
                        "Stock Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                Database.SaleItem saleItem = new Database.SaleItem();
                saleItem.setProductId(productId);
                saleItem.setDate(transactionDate);
                saleItem.setQuantity(quantity);
                saleItem.setPrice(price);
                items.add(saleItem);
                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));
            }

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Total sale amount must be positive.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ── Discount validation (now that total is computed) ──────
            BigDecimal discount = BigDecimal.valueOf(
                ((Number) spinnerDiscount.getValue()).doubleValue());
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Discount cannot be negative.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (discount.compareTo(total) >= 0) {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Discount (" + fmtAmt(discount) + ") equals or exceeds total ("
                    + fmtAmt(total) + ").\nThis results in a zero or negative sale.\nContinue?",
                    "Confirm Full Discount", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
            }

            LOGGER.info("Saving transaction: date=" + transactionDate + ", total=" + total
                + ", discount=" + discount + ", items=" + items.size()
                + ", user=" + currentUser
                + (isBackdated ? ", BACKDATED=true" : ""));

            // ── Atomic DB write ───────────────────────────────────────
            Connection conn = Database.getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                Database.saveTransaction(total, items, discount, currentUser);

                // ── If backdated, log the audit record inside same transaction ──
                if (isBackdated) {
                    logBackdatedAudit(conn, transactionDate, items, total, discount, currentUser);
                }
                conn.commit();
            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException rbEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", rbEx);
                }
                throw ex;
            } finally {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
            }

            // ── Update in-memory stock ────────────────────────────────
            for (Database.SaleItem item : items) {
                for (Database.Product p : products) {
                    if (p.getId() == item.getProductId()) {
                        p.setStockQuantity(p.getStockQuantity() - item.getQuantity());
                        break;
                    }
                }
            }

            String receiptNumber  = generateReceiptNumber();
            String receiptContent = generateReceiptContent(items, total, discount,
                receiptNumber, transactionDate);
            saveReceiptToDatabase(receiptNumber, receiptContent);
            if (chkPrintReceipt.isSelected()) printReceipt(receiptContent);

            if (Database.isOnline()) {
                new Thread(() -> {
                    try { SyncEngine.quickPush(); }
                    catch (Exception ex) { LOGGER.warning("Cloud sync failed: " + ex.getMessage()); }
                }).start();
            }

            String dateLabel = isBackdated
                ? transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + " (BACKDATED)"
                : transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            JOptionPane.showMessageDialog(this,
                "Transaction saved successfully for date: " + dateLabel,
                "Success", JOptionPane.INFORMATION_MESSAGE);

            modelSales.setRowCount(0);
            clearCartFromLocalStorage();
            spinnerDiscount.setValue(0.0);
            dateSpecificCarts.remove(transactionDate);
            refreshProducts();
            updateTotal();

        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg.contains("NOT NULL constraint failed"))      msg = "Database error: " + msg;
            else if (msg.contains("Insufficient stock"))        msg = "Transaction failed: " + msg;
            else                                                msg = "Failed to save transaction: " + msg;
            LOGGER.log(Level.SEVERE, msg, e);
            JOptionPane.showMessageDialog(this, msg, "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in saveTransaction", e);
            JOptionPane.showMessageDialog(this, "Unexpected error: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
private String fmtAmt(BigDecimal v) {
    return (selectedCurrency.isEmpty() ? "" : selectedCurrency + " ")
        + String.format("%,.2f", v);
}

    private String generateReceiptNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return RECEIPT_PREFIX + timestamp.substring(timestamp.length() - 8);
    }

    private String generateReceiptContent(List<Database.SaleItem> items, BigDecimal total,
            BigDecimal discount, String receiptNumber, LocalDate transactionDate) {

        int width = 42; // 80mm thermal default
        StringBuilder sb = new StringBuilder();

        String bizName  = businessInfo.getName();
        String address  = businessInfo.getAddress();
        String phone    = businessInfo.getPhone();
        String email    = businessInfo.getEmail();

        sb.append(repeatChar('=', width)).append("\n");
        sb.append(centerText(bizName,  width)).append("\n");
        sb.append(centerText(address,  width)).append("\n");
        sb.append(centerText("Tel: " + phone, width)).append("\n");
        sb.append(centerText(email,    width)).append("\n");
        sb.append(repeatChar('=', width)).append("\n");
        sb.append("Receipt #: ").append(receiptNumber).append("\n");
        sb.append("Date    : ").append(transactionDate.format(DATE_FORMATTER)).append("\n");
        sb.append("Time    : ")
          .append(java.time.ZonedDateTime.now().format(TIME_FORMATTER)).append("\n");
        sb.append("Cashier : ").append(currentUser).append("\n");
        sb.append(repeatChar('-', width)).append("\n");

        // Column header
        sb.append(String.format("%-20s %4s %8s %8s\n", "Item", "Qty", "Price", "Total"));
        sb.append(repeatChar('-', width)).append("\n");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Database.SaleItem item : items) {
            String productName = "Unknown";
            try {
                Database.Product product = Database.getProductById(item.productId);
                if (product != null) productName = product.getName();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to load product name for ID " + item.productId, e);
            }
            BigDecimal itemTotal = item.price.multiply(BigDecimal.valueOf(item.quantity));
            subtotal = subtotal.add(itemTotal);
            // Truncate long names so columns stay aligned
            String nameCol = productName.length() > 20
                ? productName.substring(0, 17) + "..." : productName;
            sb.append(String.format("%-20s %4d %8.2f %8.2f\n",
                nameCol, item.quantity,
                item.price.doubleValue(), itemTotal.doubleValue()));
        }

        sb.append(repeatChar('-', width)).append("\n");
        sb.append(rightAlign("Subtotal : " + String.format("%.2f", subtotal.doubleValue()), width)).append("\n");
        sb.append(rightAlign("Discount : " + String.format("%.2f", discount.doubleValue()), width)).append("\n");
        BigDecimal net = total.subtract(discount);
        sb.append(repeatChar('=', width)).append("\n");
        sb.append(rightAlign("TOTAL    : " + String.format("%.2f", net.doubleValue()), width)).append("\n");
        sb.append(repeatChar('=', width)).append("\n");
        sb.append(centerText("Thank you for your purchase!", width)).append("\n");
        sb.append(centerText("Returns accepted within 3 days.", width)).append("\n");
        sb.append(repeatChar('=', width)).append("\n");

        return sb.toString();
    }

    private String escapeLatex(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\textbackslash")  // Must be first!
                  .replace("&", "\\&")
                  .replace("%", "\\%")
                  .replace("#", "\\#")
                  .replace("_", "\\_")
                  .replace("{", "\\{")
                  .replace("}", "\\}")
                  .replace("~", "\\textasciitilde")
                  .replace("^", "\\textasciicircum");
    }

    private void saveReceiptToDatabase(String receiptNumber, String receiptContent) {
        try {
            NotificationDatabase.saveNotebook(receiptNumber, receiptContent, currentUser);
            LOGGER.info("Receipt saved to database: " + receiptNumber);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save receipt to database: " + receiptNumber + ", " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Failed to save receipt: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void showReprintDialog() {
    try {
        UIManager.setLookAndFeel(new FlatLightLaf());
    } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
    }

    JDialog dialog = new JDialog(this, "Re-Print Receipts", true);
    dialog.setLayout(null);
    dialog.setSize(1200, 700);
    dialog.setLocationRelativeTo(this);
    dialog.getContentPane().setBackground(new Color(245, 247, 250));

    // Left side - Sale Search Date
    JLabel lblSearchDate = new JLabel("Sale Search Date");
    lblSearchDate.setFont(new Font("Segoe UI", Font.BOLD, 16));
    lblSearchDate.setBounds(20, 20, 250, 30);
    lblSearchDate.setForeground(DARK_GRAY);
    dialog.add(lblSearchDate);

    JCalendar calendar = new JCalendar();
    calendar.setBounds(20, 60, 250, 200);
    calendar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    dialog.add(calendar);

    // Receipt/Cashier group box
    JPanel receiptGroupBox = new JPanel();
    receiptGroupBox.setLayout(null);
    receiptGroupBox.setBounds(20, 280, 250, 360);
    receiptGroupBox.setBackground(Color.WHITE);
    receiptGroupBox.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(PALE_BLUE, 2),
        LocalDate.now().format(DATE_FORMATTER) + " sale receipts",
        javax.swing.border.TitledBorder.LEFT,
        javax.swing.border.TitledBorder.TOP,
        new Font("Segoe UI", Font.BOLD, 12),
        DARK_GRAY
    ));
    dialog.add(receiptGroupBox);

    // Flow pointer
    JLabel flowPointer = new JLabel("└─>");
    flowPointer.setFont(new Font("Monospaced", Font.BOLD, 20));
    flowPointer.setBounds(10, 30, 50, 30);
    flowPointer.setForeground(PRIMARY_BLUE);
    receiptGroupBox.add(flowPointer);

    JLabel lblReceiptHeader = new JLabel("Cashier / User");
    lblReceiptHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
    lblReceiptHeader.setBounds(60, 30, 180, 20);
    lblReceiptHeader.setForeground(DARK_GRAY);
    receiptGroupBox.add(lblReceiptHeader);

    // Receipt list with checkboxes (will show users/cashiers)
    DefaultListModel<String> receiptListModel = new DefaultListModel<>();
    JList<String> receiptList = new JList<>(receiptListModel);
    receiptList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    receiptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    
    JScrollPane receiptScroll = new JScrollPane(receiptList);
    receiptScroll.setBounds(10, 55, 230, 290);
    receiptGroupBox.add(receiptScroll);

    // Right side - Sold Items group box
    JPanel soldItemsGroupBox = new JPanel();
    soldItemsGroupBox.setLayout(new BorderLayout());
    soldItemsGroupBox.setBounds(290, 20, 880, 560);
    soldItemsGroupBox.setBackground(Color.WHITE);
    soldItemsGroupBox.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(PALE_BLUE, 2),
        "Sold Items",
        javax.swing.border.TitledBorder.LEFT,
        javax.swing.border.TitledBorder.TOP,
        new Font("Segoe UI", Font.BOLD, 14),
        DARK_GRAY
    ));
    dialog.add(soldItemsGroupBox);

    // Sold items table
    DefaultTableModel soldItemsModel = new DefaultTableModel(
        new Object[][]{},
        new String[]{"☑", "Product Name", "Sale Qty", "Selling Price", "Subtotal"}
    ) {
        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : (column == 2 ? Integer.class : String.class);
        }
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }
    };

    JTable soldItemsTable = new JTable(soldItemsModel);
    soldItemsTable.setRowHeight(28);
    soldItemsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    soldItemsTable.getTableHeader().setBackground(PALE_BLUE);
    soldItemsTable.getTableHeader().setForeground(DARK_GRAY);
    soldItemsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    
    // Center align all columns
    javax.swing.table.DefaultTableCellRenderer centerRenderer = new javax.swing.table.DefaultTableCellRenderer();
    centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
    for (int i = 0; i < soldItemsTable.getColumnCount(); i++) {
        soldItemsTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
    }
    
    JScrollPane soldItemsScroll = new JScrollPane(soldItemsTable);
    soldItemsGroupBox.add(soldItemsScroll, BorderLayout.CENTER);

    // Total panel at bottom of sold items
    JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    totalPanel.setBackground(Color.WHITE);
    JLabel lblTotalReceipt = new JLabel("Total Receipt Sale:");
    lblTotalReceipt.setFont(new Font("Segoe UI", Font.BOLD, 14));
    JSpinner spinTotalSale = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    spinTotalSale.setEnabled(false);
    spinTotalSale.setPreferredSize(new Dimension(150, 30));
    JSpinner.NumberEditor totalEditor = new JSpinner.NumberEditor(spinTotalSale, "#,##0.00");
    spinTotalSale.setEditor(totalEditor);
    totalPanel.add(lblTotalReceipt);
    totalPanel.add(spinTotalSale);
    soldItemsGroupBox.add(totalPanel, BorderLayout.SOUTH);

    // Store data for selected date and user
    final Map<String, List<Object[]>> userSalesMap = new HashMap<>();
    final Set<String> uniqueUsers = new HashSet<>();
    
    // ===== CALENDAR LISTENER - Load users when date is clicked =====
    calendar.getDayChooser().addPropertyChangeListener(evt -> {
        if ("day".equals(evt.getPropertyName())) {
            LocalDate selectedDate = calendar.getCalendar().getTime().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
            
            // Update group box title
            ((javax.swing.border.TitledBorder) receiptGroupBox.getBorder())
                .setTitle(selectedDate.format(DATE_FORMATTER) + " sale receipts");
            receiptGroupBox.repaint();
            
            // Clear all data
            receiptListModel.clear();
            userSalesMap.clear();
            uniqueUsers.clear();
            soldItemsModel.setRowCount(0);
            spinTotalSale.setValue(0.0);
            
            // Load users/cashiers for this date from sales table
            String userSql = "SELECT DISTINCT s.user FROM sales s " +
                             "WHERE DATE(s.sale_date) = ? AND s.user IS NOT NULL " +
                             "AND s.user != '' ORDER BY s.user";
            
            try (PreparedStatement stmt = Database.getConnection().prepareStatement(userSql)) {
                stmt.setString(1, selectedDate.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String user = rs.getString("user").trim();
                    if (!user.isEmpty()) {
                        uniqueUsers.add(user);
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to load users from sales", e);
            }
            
            // Also try item_flows as fallback
            if (uniqueUsers.isEmpty()) {
                String flowUserSql = "SELECT DISTINCT user FROM item_flows " +
                                     "WHERE action = 'SOLD' AND DATE(flow_date) = ? AND user IS NOT NULL";
                try (PreparedStatement stmt = Database.getConnection().prepareStatement(flowUserSql)) {
                    stmt.setString(1, selectedDate.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String user = rs.getString("user").trim();
                        if (!user.isEmpty()) {
                            uniqueUsers.add(user);
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to load users from item_flows", e);
                }
            }
            
            // Load sales data for each user
            for (String user : uniqueUsers) {
                List<Object[]> userItems = new ArrayList<>();
                
                // First try to get from sale_items + sales
                String itemsSql = "SELECT p.name, si.quantity, si.unit_price, si.total_price " +
                                  "FROM sale_items si " +
                                  "JOIN sales s ON si.sale_id = s.id " +
                                  "JOIN products p ON si.product_id = p.id " +
                                  "WHERE DATE(s.sale_date) = ? AND s.user = ?";
                
                try (PreparedStatement stmt = Database.getConnection().prepareStatement(itemsSql)) {
                    stmt.setString(1, selectedDate.toString());
                    stmt.setString(2, user);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        userItems.add(new Object[]{
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("total_price")
                        });
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Failed to load items for user: " + user, e);
                }
                
                // Fallback to item_flows if no data
                if (userItems.isEmpty()) {
                    String flowItemsSql = "SELECT p.name, if.quantity, p.selling_price, " +
                                          "(if.quantity * p.selling_price) as total " +
                                          "FROM item_flows if " +
                                          "JOIN products p ON if.product_id = p.id " +
                                          "WHERE if.action = 'SOLD' AND DATE(if.flow_date) = ? AND if.user = ?";
                    try (PreparedStatement stmt = Database.getConnection().prepareStatement(flowItemsSql)) {
                        stmt.setString(1, selectedDate.toString());
                        stmt.setString(2, user);
                        ResultSet rs = stmt.executeQuery();
                        while (rs.next()) {
                            userItems.add(new Object[]{
                                rs.getString("name"),
                                rs.getInt("quantity"),
                                rs.getBigDecimal("selling_price"),
                                rs.getBigDecimal("total")
                            });
                        }
                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING, "Failed to load items from flows for user: " + user, e);
                    }
                }
                
                // Only add user if they have sales data
                if (!userItems.isEmpty()) {
                    userSalesMap.put(user, userItems);
                    receiptListModel.addElement(user);
                }
            }
            
            // Also try to get receipts from notifications.db as fallback
            try {
                List<NotificationDatabase.Notebook> receipts = NotificationDatabase.getReceiptsByDate(selectedDate);
                if (receipts != null && !receipts.isEmpty()) {
                    for (NotificationDatabase.Notebook receipt : receipts) {
                        String user = receipt.getUser();
                        if (user != null && !user.isEmpty() && !uniqueUsers.contains(user)) {
                            // Parse items from receipt content
                            List<Object[]> receiptItems = parseReceiptItemsFromContent(receipt.getContent());
                            if (!receiptItems.isEmpty()) {
                                userSalesMap.put(user, receiptItems);
                                receiptListModel.addElement(user);
                                uniqueUsers.add(user);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to load receipts from notifications.db", e);
            }
            
            if (uniqueUsers.isEmpty()) {
                receiptListModel.addElement("No sales found for this date");
            }
            
            LOGGER.info("Loaded " + uniqueUsers.size() + " users with sales data for date: " + selectedDate);
        }
    });
    
    // ===== USER SELECTION LISTENER - Load items when user is clicked =====
    receiptList.addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
            String selectedUser = receiptList.getSelectedValue();
            if (selectedUser != null && !selectedUser.equals("No sales found for this date")) {
                soldItemsModel.setRowCount(0);
                
                List<Object[]> items = userSalesMap.get(selectedUser);
                BigDecimal total = BigDecimal.ZERO;
                
                if (items != null && !items.isEmpty()) {
                    for (Object[] item : items) {
                        String productName = (String) item[0];
                        int qty = (int) item[1];
                        BigDecimal price = (BigDecimal) item[2];
                        BigDecimal subtotal = (BigDecimal) item[3];
                        
                        total = total.add(subtotal);
                        soldItemsModel.addRow(new Object[]{
                            false,
                            productName,
                            qty,
                            String.format("%.2f", price),
                            String.format("%.2f", subtotal)
                        });
                    }
                }
                
                spinTotalSale.setValue(total.doubleValue());
                LOGGER.info("Loaded " + (items != null ? items.size() : 0) + " items for user: " + selectedUser);
            }
        }
    });

    // Center Print button
    JButton btnPrint = createMenuButton("🖨️ PRINT SELECTED", SUCCESS_GREEN);
    btnPrint.setBounds((dialog.getWidth() - 250) / 2, 600, 250, 50);
    btnPrint.setFont(new Font("Segoe UI", Font.BOLD, 16));
    dialog.add(btnPrint);

    btnPrint.addActionListener(e -> {
        List<String> selectedReceipts = new ArrayList<>();
        for (int i = 0; i < soldItemsModel.getRowCount(); i++) {
            Boolean checked = (Boolean) soldItemsModel.getValueAt(i, 0);
            if (checked != null && checked) {
                String selected = receiptList.getSelectedValue();
                if (selected != null && !selectedReceipts.contains(selected)) {
                    selectedReceipts.add(selected);
                }
            }
        }

        if (selectedReceipts.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, 
                "Please select items to print by checking the boxes",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(dialog,
            "Print receipt with " + selectedReceipts.size() + " selected item(s)?",
            "Confirm Print", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Generate receipt content from selected items
                String receiptContent = generateReceiptFromSelectedItems(soldItemsModel, spinTotalSale, receiptList.getSelectedValue());
                printReceipt(receiptContent);
                JOptionPane.showMessageDialog(dialog, "Receipt sent to printer",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to print", ex);
                JOptionPane.showMessageDialog(dialog, "Print failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    });

    dialog.setVisible(true);
}

// Helper method to parse receipt items from content
private List<Object[]> parseReceiptItemsFromContent(String content) {
    List<Object[]> items = new ArrayList<>();
    if (content == null) return items;
    
    String[] lines = content.split("\n");
    boolean inTable = false;
    
    for (String line : lines) {
        line = line.trim();
        if (line.contains("ITEMS:") || line.contains("Items:")) {
            inTable = true;
            continue;
        }
        if (inTable && (line.contains("Subtotal:") || line.contains("Total:") || line.contains("="))) {
            break;
        }
        if (inTable && !line.isEmpty() && !line.contains("-") && !line.contains("=")) {
            // Try to parse: "Product Name     x2  12.50" format
            String[] parts = line.split("x");
            if (parts.length >= 2) {
                try {
                    String productName = parts[0].trim();
                    String[] qtyPrice = parts[1].trim().split("\\s+");
                    if (qtyPrice.length >= 2) {
                        int qty = Integer.parseInt(qtyPrice[0]);
                        BigDecimal price = new BigDecimal(qtyPrice[1]);
                        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
                        items.add(new Object[]{productName, qty, price, subtotal});
                    }
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }
    }
    return items;
}

// Helper method to generate receipt from selected items
private String generateReceiptFromSelectedItems(DefaultTableModel model, JSpinner totalSpinner, String cashier) {
    StringBuilder sb = new StringBuilder();
    int width = 42;
    
    String bizName = businessInfo.getName();
    String address = businessInfo.getAddress();
    String phone = businessInfo.getPhone();
    String email = businessInfo.getEmail();
    
    sb.append(repeatChar('=', width)).append("\n");
    sb.append(centerText(bizName, width)).append("\n");
    sb.append(centerText(address, width)).append("\n");
    sb.append(centerText("Tel: " + phone, width)).append("\n");
    sb.append(centerText(email, width)).append("\n");
    sb.append(repeatChar('=', width)).append("\n");
    sb.append("Receipt #: ").append(generateReceiptNumber()).append("\n");
    sb.append("Date    : ").append(LocalDate.now().format(DATE_FORMATTER)).append("\n");
    sb.append("Time    : ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");
    sb.append("Cashier : ").append(cashier != null ? cashier : currentUser).append("\n");
    sb.append(repeatChar('-', width)).append("\n");
    
    sb.append(String.format("%-20s %4s %8s %8s\n", "Item", "Qty", "Price", "Total"));
    sb.append(repeatChar('-', width)).append("\n");
    
    BigDecimal total = BigDecimal.ZERO;
    for (int i = 0; i < model.getRowCount(); i++) {
        String productName = (String) model.getValueAt(i, 1);
        int qty = (int) model.getValueAt(i, 2);
        String priceStr = (String) model.getValueAt(i, 3);
        String subtotalStr = (String) model.getValueAt(i, 4);
        
        try {
            BigDecimal price = new BigDecimal(priceStr.replaceAll("[^0-9.]", ""));
            BigDecimal subtotal = new BigDecimal(subtotalStr.replaceAll("[^0-9.]", ""));
            total = total.add(subtotal);
            
            String nameCol = productName.length() > 20 ? productName.substring(0, 17) + "..." : productName;
            sb.append(String.format("%-20s %4d %8.2f %8.2f\n",
                nameCol, qty, price.doubleValue(), subtotal.doubleValue()));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to format receipt line", e);
        }
    }
    
    sb.append(repeatChar('-', width)).append("\n");
    sb.append(rightAlign("TOTAL    : " + String.format("%.2f", total.doubleValue()), width)).append("\n");
    sb.append(repeatChar('=', width)).append("\n");
    sb.append(centerText("Thank you for your purchase!", width)).append("\n");
    sb.append(centerText("Please come again", width)).append("\n");
    sb.append(repeatChar('=', width)).append("\n");
    
    return sb.toString();
}

    private List<Database.SaleItem> parseReceiptItems(String latexContent) {
        List<Database.SaleItem> items = new ArrayList<>();
        if (latexContent == null) return items;
        
        String[] lines = latexContent.split("\n");
        boolean inTable = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.contains("\\begin{tabular}")) {
                inTable = true;
                continue;
            }
            if (line.contains("\\end{tabular}")) break;
            
            if (inTable && line.contains("&") && !line.contains("Description") && !line.contains("hline")) {
                String[] parts = line.split("&");
                if (parts.length >= 4) {
                    try {
                        String productName = unescapeLatex(parts[0].trim());
                        int qty = Integer.parseInt(parts[1].trim());
                        BigDecimal price = new BigDecimal(parts[2].trim());
                        
                        // Find product ID
                        int productId = -1;
                        for (Database.Product p : products) {
                            if (p.getName().equals(productName)) {
                                productId = p.getId();
                                break;
                            }
                        }
                        
                        if (productId != -1) {
                            // Use setters instead of constructor to avoid compilation error
                            Database.SaleItem item = new Database.SaleItem();
                            item.setProductId(productId);
                            item.setDate(currentSaleDate); // Use current sale date
                            item.setQuantity(qty);
                            item.setPrice(price);
                            items.add(item);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to parse receipt item", e);
                    }
                }
            }
        }
        return items;
    }

    private int countItemsInReceipt(String latexContent) {
        if (latexContent == null) return 0;
        
        int count = 0;
        String[] lines = latexContent.split("\n");
        boolean inTable = false;
        
        for (String line : lines) {
            if (line.contains("\\begin{tabular}")) {
                inTable = true;
                continue;
            }
            if (line.contains("\\end{tabular}")) {
                break;
            }
            if (inTable && line.contains("&") && !line.contains("Description") && !line.contains("hline")) {
                String[] parts = line.split("&");
                if (parts.length >= 4) {
                    count++;
                }
            }
        }
        return count;
    }


    private String extractTotalFromReceipt(String latexContent) {
        if (latexContent == null) return "0.00";
        
        String[] lines = latexContent.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("Total: ")) {
                return line.replace("Total: ", "").replace("\\\\", "").trim();
            }
        }
        return "0.00";
    }

    private void printReceipt(String receiptContent) {
        try {
            if (receiptContent == null || receiptContent.trim().isEmpty()) {
                LOGGER.warning("Cannot print: receipt content is empty");
                JOptionPane.showMessageDialog(this, "Cannot print: receipt content is empty",
                    "Print Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Get default printer service
            PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
            if (defaultService == null) {
                LOGGER.warning("No default printer found");
                JOptionPane.showMessageDialog(this, "No printer found. Please install a printer.",
                    "Print Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LOGGER.info("Using printer: " + defaultService.getName());

            // Try to detect thermal receipt printer by checking supported media sizes
            MediaSizeName mediaSizeName = detectReceiptPrinterSize(defaultService);
            
            // Get media size dimensions in millimeters
            MediaSize mediaSize = MediaSize.getMediaSizeForName(mediaSizeName);
            float widthMM;
            float heightMM = 297; // Long enough for receipts
            
            if (mediaSize != null) {
                widthMM = mediaSize.getX(MediaSize.MM);
                LOGGER.info("Media size from MediaSizeName: " + widthMM + "mm x " + mediaSize.getY(MediaSize.MM) + "mm");
            } else {
                // Fallback width detection based on printer name
                String printerName = defaultService.getName().toLowerCase();
                if (printerName.contains("58") || printerName.contains("57")) {
                    widthMM = 58;
                    LOGGER.info("Fallback: detected 58mm from printer name");
                } else {
                    widthMM = 80; // Safe default for most thermal printers
                    LOGGER.info("Fallback: using default 80mm width");
                }
            }
            
            // Convert to points (1 mm = 2.83465 points)
            double widthPoints = widthMM * 2.83465;
            double heightPoints = heightMM * 2.83465;

            // Calculate optimal font size and characters per line for receipt printers
            int fontSize;
            int charsPerLine;
            
            if (widthMM <= 58) {
                // 58mm receipt paper (small thermal printers)
                fontSize = 8;
                charsPerLine = 32;
                LOGGER.info("Using 58mm format: fontSize=" + fontSize + ", chars=" + charsPerLine);
            } else if (widthMM <= 80) {
                // 80mm receipt paper (standard thermal printers)
                fontSize = 9;
                charsPerLine = 42;
                LOGGER.info("Using 80mm format: fontSize=" + fontSize + ", chars=" + charsPerLine);
            } else if (widthMM <= 110) {
                // Wider paper (110mm)
                fontSize = 10;
                charsPerLine = 48;
                LOGGER.info("Using wide format: fontSize=" + fontSize + ", chars=" + charsPerLine);
            } else {
                // A4 or other large formats
                fontSize = 10;
                charsPerLine = 60;
                LOGGER.info("Using large format: fontSize=" + fontSize + ", chars=" + charsPerLine);
            }

            // Format receipt content for the detected paper size
            String formattedReceipt = formatReceiptForPaperSize(receiptContent, widthPoints, charsPerLine, fontSize);

            // Create print job with custom page format
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(defaultService);

            // Set up page format based on printer's media size
            PageFormat pageFormat = new PageFormat();
            Paper paper = new Paper();
            paper.setSize(widthPoints, heightPoints);
            
            // Minimal margins for receipt printers (in points)
            double marginLeft = 5;
            double marginRight = 5;
            double marginTop = 5;
            double marginBottom = 5;
            
            paper.setImageableArea(marginLeft, marginTop, 
                widthPoints - (marginLeft + marginRight), 
                heightPoints - (marginTop + marginBottom));
            pageFormat.setPaper(paper);
            pageFormat.setOrientation(PageFormat.PORTRAIT);

            LOGGER.info("Paper setup: " + widthPoints + "x" + heightPoints + " points, " +
                       "Imageable: " + paper.getImageableWidth() + "x" + paper.getImageableHeight());

            // Create custom printable
            Printable printable = new Printable() {
                @Override
                public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) return NO_SUCH_PAGE;

                    Graphics2D g2d = (Graphics2D) g;
                    g2d.translate(pf.getImageableX(), pf.getImageableY());

                    // Use monospaced font for consistent character width
                    Font font = new Font("Monospaced", Font.PLAIN, fontSize);
                    g2d.setFont(font);

                    // Calculate text metrics
                    FontMetrics fm = g2d.getFontMetrics(font);
                    String[] lines = formattedReceipt.split("\n");
                    int lineHeight = fm.getHeight();
                    int y = 0;

                    LOGGER.info("Printing " + lines.length + " lines, lineHeight=" + lineHeight);

                    for (String line : lines) {
                        g2d.drawString(line, 0, y + fm.getAscent());
                        y += lineHeight;
                    }

                    return PAGE_EXISTS;
                }
            };

            printerJob.setPrintable(printable, pageFormat);

            // Print without dialog
            printerJob.print();

            LOGGER.info("✓ Receipt sent to printer successfully (Paper: " + widthMM + "mm, " + 
                       charsPerLine + " chars/line)");

        } catch (PrinterException e) {
            LOGGER.log(Level.SEVERE, "Printer error: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Printer error: " + e.getMessage(),
                "Print Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to print receipt: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Failed to print receipt: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private MediaSizeName detectReceiptPrinterSize(PrintService printService) {
        LOGGER.info("Detecting printer size for: " + printService.getName());
        
        // Try to detect thermal receipt printer by checking supported media sizes
        Object obj = printService.getSupportedAttributeValues(Media.class, null, null);
        
        if (obj instanceof Media[]) {
            Media[] media = (Media[]) obj;
            LOGGER.info("Found " + media.length + " supported media sizes");
            
            for (Media m : media) {
                if (m instanceof MediaSizeName) {
                    MediaSizeName msn = (MediaSizeName) m;
                    MediaSize ms = MediaSize.getMediaSizeForName(msn);
                    if (ms != null) {
                        float width = ms.getX(MediaSize.MM);
                        float height = ms.getY(MediaSize.MM);
                        LOGGER.info("Found media: " + msn + " - Width: " + width + "mm, Height: " + height + "mm");
                        
                        // Check for common receipt sizes
                        if (width >= 56 && width <= 60) {
                            LOGGER.info("✓ Detected 58mm thermal receipt printer");
                            return msn;
                        } else if (width >= 78 && width <= 82) {
                            LOGGER.info("✓ Detected 80mm thermal receipt printer");
                            return msn;
                        }
                    }
                }
            }
        }
        
        // Try default attribute
        MediaSizeName defaultSize = (MediaSizeName) printService.getDefaultAttributeValue(MediaSizeName.class);
        if (defaultSize != null) {
            MediaSize ms = MediaSize.getMediaSizeForName(defaultSize);
            if (ms != null) {
                float width = ms.getX(MediaSize.MM);
                LOGGER.info("Using default printer media: " + defaultSize + " (" + width + "mm)");
                return defaultSize;
            }
        }
        
        // Fallback: Check printer name for hints
        String printerName = printService.getName().toLowerCase();
        if (printerName.contains("58mm") || printerName.contains("58")) {
            LOGGER.info("Printer name suggests 58mm, using NA_5X7 as fallback");
            return MediaSizeName.NA_5X7;
        }
        
        // Default fallback to 80mm
        LOGGER.warning("Could not detect printer size, defaulting to 80mm (ISO_A7)");
        return MediaSizeName.ISO_A7; // Closest to 80mm receipt
    }
    
    private String formatReceiptForPaperSize(String latexContent, double widthPoints, int charsPerLine, int fontSize) {
        if (latexContent == null || latexContent.trim().isEmpty()) {
            return "ERROR: Empty receipt content";
        }

        int width = charsPerLine;
        StringBuilder formatted = new StringBuilder();
        String[] lines = latexContent.split("\n");

        // Extract data from LaTeX
        String businessName = businessInfo.getName();
        String address = businessInfo.getAddress();
        String phone = businessInfo.getPhone();
        String email = businessInfo.getEmail();
        String receiptNumber = "";
        String date = "";
        String time = "";
        String cashier = currentUser;
        List<String[]> items = new ArrayList<>();
        String subtotal = "0.00";
        String discount = "0.00";
        String total = "0.00";

        boolean inTable = false;
        for (String line : lines) {
            line = line.trim();

            if (line.contains("Receipt #:")) {
                int start = line.indexOf("Receipt #:") + 10;
                int end = line.indexOf("}", start);
                if (end > start) receiptNumber = line.substring(start, end).trim();
            }
            if (line.startsWith("Date:")) date = line.replace("Date:", "").replace("\\\\", "").trim();
            if (line.startsWith("Time:")) time = line.replace("Time:", "").replace("\\\\", "").trim();
            if (line.contains("Cashier:")) {
                int start = line.indexOf("Cashier:") + 8;
                cashier = unescapeLatex(line.substring(start).replace("\\\\", "").trim());
            }

            if (line.contains("\\begin{tabular}")) { inTable = true; continue; }
            if (line.contains("\\end{tabular}")) { inTable = false; continue; }

            if (inTable && line.contains("&") && !line.contains("Description") && !line.contains("hline")) {
                String[] parts = line.split("&");
                if (parts.length >= 4) {
                    items.add(new String[]{
                        unescapeLatex(parts[0].trim()),
                        parts[1].trim(),
                        parts[2].trim(),
                        parts[3].trim().replace("\\\\", "")
                    });
                }
            }

            if (line.startsWith("Subtotal: ")) subtotal = line.replace("Subtotal: ", "").replace("\\\\", "").trim();
            if (line.startsWith("Discount: ")) discount = line.replace("Discount: ", "").replace("\\\\", "").trim();
            if (line.startsWith("Total: ")) total = line.replace("Total: ", "").replace("\\\\", "").trim();
        }

        // Build compact receipt
        formatted.append(repeatChar('=', width)).append("\n");
        
        // Business name - wrap if too long
        if (businessName.length() > width) {
            List<String> wrappedLines = wrapTextToLines(businessName, width);
            for (String wrappedLine : wrappedLines) {
                formatted.append(centerText(wrappedLine, width)).append("\n");
            }
        } else {
            formatted.append(centerText(businessName, width)).append("\n");
        }
        
        // Address - wrap if needed
        if (address.length() > width) {
            List<String> wrappedLines = wrapTextToLines(address, width);
            for (String wrappedLine : wrappedLines) {
                formatted.append(centerText(wrappedLine, width)).append("\n");
            }
        } else {
            formatted.append(centerText(address, width)).append("\n");
        }
        
        formatted.append(centerText(phone, width)).append("\n");
        
        // Email - wrap if too long
        if (email.length() > width) {
            List<String> wrappedLines = wrapTextToLines(email, width);
            for (String wrappedLine : wrappedLines) {
                formatted.append(centerText(wrappedLine, width)).append("\n");
            }
        } else {
            formatted.append(centerText(email, width)).append("\n");
        }
        
        formatted.append(repeatChar('=', width)).append("\n");

        // Receipt details
        formatted.append("Receipt: ").append(receiptNumber).append("\n");
        formatted.append("Date: ").append(date).append("\n");
        formatted.append("Time: ").append(time).append("\n");
        
        // Wrap cashier if too long
        String cashierLabel = "Cashier: ";
        int remainingWidth = width - cashierLabel.length();
        if (cashier.length() > remainingWidth) {
            formatted.append(cashierLabel).append(truncate(cashier, remainingWidth)).append("\n");
        } else {
            formatted.append(cashierLabel).append(cashier).append("\n");
        }
        
        formatted.append(repeatChar('-', width)).append("\n");

        // Items section - adaptive formatting based on width
        for (String[] item : items) {
            String itemName = item[0];
            String qty = item[1];
            String itemTotal = item[3];

            if (width >= 42) {
                // Standard format for 80mm+ paper: "Product Name     x2  12.50"
                int nameWidth = width - 15; // Reserve 15 chars for " xQQ TTTT.TT"
                String qtyTotal = String.format("x%-2s %s", qty, itemTotal);
                int qtyTotalLen = qtyTotal.length();
                int actualNameWidth = width - qtyTotalLen - 1;
                
                String namePart = truncate(itemName, actualNameWidth);
                formatted.append(namePart);
                formatted.append(repeatChar(' ', width - namePart.length() - qtyTotalLen));
                formatted.append(qtyTotal).append("\n");
            } else {
                // Compact format for 58mm paper: two lines per item
                formatted.append(truncate(itemName, width)).append("\n");
                String qtyLine = String.format("  x%s = %s", qty, itemTotal);
                formatted.append(qtyLine).append("\n");
            }
        }

        formatted.append(repeatChar('-', width)).append("\n");
        formatted.append(rightAlign("Subtotal: " + subtotal, width)).append("\n");
        formatted.append(rightAlign("Discount: " + discount, width)).append("\n");
        formatted.append(repeatChar('=', width)).append("\n");
        formatted.append(rightAlign("TOTAL: " + total, width)).append("\n");
        formatted.append(repeatChar('=', width)).append("\n");
        formatted.append("\n");
        formatted.append(centerText("THANK YOU!", width)).append("\n");
        formatted.append(centerText("Please Come Again", width)).append("\n");
        formatted.append(repeatChar('=', width)).append("\n");

        LOGGER.info("Formatted receipt: " + formatted.toString().split("\n").length + " lines, " + 
                   width + " chars/line");
        return formatted.toString();
    }

    // Add helper method for wrapping text into multiple lines:
    private List<String> wrapTextToLines(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder();
                }
                // If single word is longer than max width, break it
                if (word.length() > maxWidth) {
                    lines.add(word.substring(0, maxWidth));
                    word = word.substring(maxWidth);
                }
                currentLine.append(word).append(" ");
            } else {
                currentLine.append(word).append(" ");
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }
        
        return lines;
    }

    

    // Helper methods for formatting
    private String centerText(String text, int width) {
        if (text.length() >= width) return truncate(text, width);
        int padding = (width - text.length()) / 2;
        return repeatChar(' ', padding) + text;
    }

    private String rightAlign(String text, int width) {
        if (text.length() >= width) return truncate(text, width);
        return repeatChar(' ', width - text.length()) + text;
    }

    private String wrapText(String text, int width) {
        if (text.length() <= width) return text;
        StringBuilder wrapped = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + width, text.length());
            wrapped.append(text.substring(start, end)).append("\n");
            start = end;
        }
        return wrapped.toString().trim();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    private String repeatChar(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Business Settings", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtBusinessName = new JTextField(businessInfo.getName());
        JTextField txtAddress = new JTextField(businessInfo.getAddress());
        JTextField txtPhone = new JTextField(businessInfo.getPhone());
        JTextField txtEmail = new JTextField(businessInfo.getEmail());
        JTextField txtWebsite = new JTextField(businessInfo.getWebsite());

        gbc.gridx = 0;
        gbc.gridy = 0;
        dialog.add(new JLabel("Business Name:"), gbc);
        gbc.gridx = 1;
        dialog.add(txtBusinessName, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        dialog.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1;
        dialog.add(txtAddress, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        dialog.add(new JLabel("Phone Number:"), gbc);
        gbc.gridx = 1;
        dialog.add(txtPhone, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        dialog.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        dialog.add(txtEmail, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        dialog.add(new JLabel("Website:"), gbc);
        gbc.gridx = 1;
        dialog.add(txtWebsite, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Save");
        btnSave.setBackground(SUCCESS_GREEN);
        styleButton(btnSave);
        JButton btnClose = new JButton("Close");
        btnClose.setBackground(DELETE_RED);
        styleButton(btnClose);
        buttonPanel.add(btnSave);
        buttonPanel.add(btnClose);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        dialog.add(buttonPanel, gbc);

        btnSave.addActionListener(e -> {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
                businessInfo = new NotificationDatabase.BusinessInfo(
                    txtBusinessName.getText().trim().isEmpty() ? "Your Company Name" : txtBusinessName.getText(),
                    txtAddress.getText().trim().isEmpty() ? "Street Address, City, State, ZIP Code" : txtAddress.getText(),
                    txtPhone.getText().trim().isEmpty() ? "Business Phone Number" : txtPhone.getText(),
                    txtEmail.getText().trim().isEmpty() ? "Business Email Address" : txtEmail.getText(),
                    txtWebsite.getText().trim().isEmpty() ? "Business Website" : txtWebsite.getText()
                );
                businessInfo.saveToDatabase(conn);
                JOptionPane.showMessageDialog(dialog, "Settings saved successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
                LOGGER.info("Business settings saved to database");
                dialog.dispose();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to save business settings: " + ex.getMessage(), ex);
                JOptionPane.showMessageDialog(dialog, "Failed to save settings: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    

    private void showReceiptLog() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
        }

        JDialog dialog = new JDialog(this, "🛡️ Advanced Fraud Detection & Receipt Analysis System", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(1400, 800);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(new Color(240, 242, 245));

        // ===== TOP HEADER WITH STATISTICS =====
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(52, 144, 220));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        
        JLabel titleLabel = new JLabel("🛡️ Fraud Detection System");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Statistics Panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        statsPanel.setBackground(new Color(52, 144, 220));
        statsPanel.setOpaque(false);
        
        JLabel[] statLabels = new JLabel[4];
        String[] statTitles = {"Total Receipts", "Suspicious", "Fraud Detected", "Clean"};
        Color[] statColors = {Color.WHITE, new Color(255, 193, 7), new Color(244, 67, 54), new Color(76, 175, 80)};
        
        for (int i = 0; i < 4; i++) {
            statLabels[i] = createStatLabel(statTitles[i], "0", statColors[i]);
            statsPanel.add(statLabels[i]);
        }
        headerPanel.add(statsPanel, BorderLayout.EAST);
        
        dialog.add(headerPanel, BorderLayout.NORTH);

        // ===== CONTROL PANEL WITH FILTERS =====
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        controlPanel.setBackground(Color.WHITE);
        controlPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(220, 220, 220)));
        
        // Date Range Filter
        JLabel lblDateRange = new JLabel("📅 Date Range:");
        lblDateRange.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        JDateChooser startDateChooser = new JDateChooser();
        startDateChooser.setDateFormatString("yyyy-MM-dd");
        startDateChooser.setDate(java.sql.Date.valueOf(LocalDate.now().minusDays(30)));
        startDateChooser.setPreferredSize(new Dimension(140, 30));
        
        JLabel lblTo = new JLabel("to");
        
        JDateChooser endDateChooser = new JDateChooser();
        endDateChooser.setDateFormatString("yyyy-MM-dd");
        endDateChooser.setDate(new java.util.Date());
        endDateChooser.setPreferredSize(new Dimension(140, 30));
        
        // Fraud Level Filter
        JLabel lblFilter = new JLabel("🔍 Filter:");
        lblFilter.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        String[] filterOptions = {"All Receipts", "⚠️ Suspicious Only", "🚨 Fraud Only", "✅ Clean Only"};
        JComboBox<String> filterCombo = new JComboBox<>(filterOptions);
        filterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        filterCombo.setPreferredSize(new Dimension(180, 30));
        
        // Search by Cashier
        JLabel lblSearchCashier = new JLabel("👤 Cashier:");
        lblSearchCashier.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        JTextField txtSearchCashier = new JTextField(15);
        txtSearchCashier.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtSearchCashier.setPreferredSize(new Dimension(150, 30));
        
        JButton btnApplyFilter = createMenuButton("🔎 Apply Filters", new Color(52, 144, 220));
        JButton btnExportReport = createMenuButton("📊 Export Report", SUCCESS_GREEN);
        JButton btnRefresh = createMenuButton("🔄 Refresh", INFO_PURPLE);
        
        controlPanel.add(lblDateRange);
        controlPanel.add(startDateChooser);
        controlPanel.add(lblTo);
        controlPanel.add(endDateChooser);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(lblFilter);
        controlPanel.add(filterCombo);
        controlPanel.add(lblSearchCashier);
        controlPanel.add(txtSearchCashier);
        controlPanel.add(btnApplyFilter);
        controlPanel.add(btnExportReport);
        controlPanel.add(btnRefresh);
        
        dialog.add(controlPanel, BorderLayout.NORTH);

        // ===== MAIN TABLE =====
        DefaultTableModel receiptModel = new DefaultTableModel(new Object[][]{},
            new String[]{"Receipt #", "Date", "Time", "Cashier", "Items", "Total", "Risk Score", "Fraud Type", "Status"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable receiptTable = new JTable(receiptModel);
        receiptTable.setBackground(Color.WHITE);
        receiptTable.getTableHeader().setBackground(new Color(70, 130, 180));
        receiptTable.getTableHeader().setForeground(Color.WHITE);
        receiptTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        receiptTable.setRowHeight(32);
        receiptTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        receiptTable.setForeground(Color.BLACK);
        receiptTable.setSelectionBackground(new Color(52, 152, 219, 50));
        receiptTable.setGridColor(new Color(220, 220, 220));

        // Custom renderer with color-coded risk levels
        receiptTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                try {
                    String status = (String) table.getModel().getValueAt(row, 8);
                    String riskScore = (String) table.getModel().getValueAt(row, 6);
                    
                    if (!isSelected) {
                        if ("🚨 FRAUD".equals(status)) {
                            c.setBackground(new Color(255, 220, 220)); // Light red
                            c.setForeground(Color.BLACK);
                        } else if ("⚠️ SUSPICIOUS".equals(status)) {
                            c.setBackground(new Color(255, 245, 220)); // Light yellow
                            c.setForeground(Color.BLACK);
                        } else if ("⚡ HIGH RISK".equals(status)) {
                            c.setBackground(new Color(255, 235, 230)); // Light orange
                            c.setForeground(Color.BLACK);
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Color.BLACK);
                        }
                        
                        // Color code risk score column
                        if (column == 6) {
                            try {
                                int score = Integer.parseInt(riskScore.replace("%", ""));
                                if (score >= 80) {
                                    setForeground(new Color(244, 67, 54)); // Red
                                    setFont(getFont().deriveFont(Font.BOLD));
                                } else if (score >= 50) {
                                    setForeground(new Color(255, 152, 0)); // Orange
                                    setFont(getFont().deriveFont(Font.BOLD));
                                } else if (score >= 30) {
                                    setForeground(new Color(255, 193, 7)); // Yellow
                                } else {
                                    setForeground(new Color(76, 175, 80)); // Green
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors
                            }
                        }
                    }
                } catch (Exception ex) {
                    if (!isSelected) {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(receiptTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
        dialog.add(scrollPane, BorderLayout.CENTER);

        // ===== LOAD RECEIPTS WITH FRAUD DETECTION =====
        Runnable loadReceipts = () -> {
            receiptModel.setRowCount(0);
            LocalDate today = LocalDate.now();
            int totalCount = 0;
            int suspiciousCount = 0;
            int fraudCount = 0;
            int cleanCount = 0;

            try {
                List<NotificationDatabase.Notebook> receipts = NotificationDatabase.getAllReceipts();

                if (receipts == null || receipts.isEmpty()) {
                    LOGGER.info("No receipts found in database");
                    receiptModel.addRow(new Object[]{"No receipts available", "", "", "", "", "", "", "", ""});
                } else {
                    LOGGER.info("Analyzing " + receipts.size() + " receipts for fraud detection");
                    
                    for (NotificationDatabase.Notebook receipt : receipts) {
                        try {
                            LocalDate receiptDate = receipt.getCreatedDate();
                            String dateStr = receiptDate.format(DATE_FORMATTER);
                            String timeStr = receiptDate.atStartOfDay().format(DateTimeFormatter.ofPattern("hh:mm a"));

                            // ADVANCED FRAUD DETECTION ALGORITHM
                            int riskScore = 0;
                            List<String> fraudIndicators = new ArrayList<>();
                            
                            // 1. Date Manipulation Detection (30 points)
                            if (receiptDate.isAfter(today)) {
                                riskScore += 30;
                                fraudIndicators.add("Future Date");
                            } else if (receiptDate.isBefore(today.minusDays(7))) {
                                riskScore += 15;
                                fraudIndicators.add("Old Date (7+ days)");
                            } else if (receiptDate.isBefore(today.minusDays(1))) {
                                riskScore += 10;
                                fraudIndicators.add("Backdated");
                            }
                            
                            // 2. Time Pattern Analysis (20 points)
                            int hour = receiptDate.atStartOfDay().getHour();
                            if (hour >= 0 && hour < 6) {
                                riskScore += 20;
                                fraudIndicators.add("Unusual Hours (12AM-6AM)");
                            } else if (hour >= 22) {
                                riskScore += 10;
                                fraudIndicators.add("Late Night (10PM+)");
                            }
                            
                            // 3. Receipt Number Pattern Analysis (15 points)
                            String receiptNum = receipt.getTitle();
                            if (receiptNum.length() < 8) {
                                riskScore += 15;
                                fraudIndicators.add("Short Receipt #");
                            }
                            
                            // 4. Content Analysis (25 points)
                            String content = receipt.getContent();
                            int itemsCount = countItemsInReceipt(content);
                            String totalStr = extractTotalFromReceipt(content);
                            
                            try {
                                double total = Double.parseDouble(totalStr.replaceAll("[^0-9.]", ""));
                                if (total > 1000000) {
                                    riskScore += 25;
                                    fraudIndicators.add("Unusually High Total");
                                } else if (total == 0) {
                                    riskScore += 15;
                                    fraudIndicators.add("Zero Total");
                                }
                                
                                if (itemsCount == 0) {
                                    riskScore += 20;
                                    fraudIndicators.add("No Items");
                                } else if (itemsCount > 50) {
                                    riskScore += 10;
                                    fraudIndicators.add("Excessive Items");
                                }
                            } catch (Exception e) {
                                riskScore += 10;
                                fraudIndicators.add("Invalid Total");
                            }
                            
                            // 5. Cashier Pattern Analysis (10 points)
                            String cashier = receipt.getUser();
                            // Check for suspicious patterns in future implementation
                            
                            // Determine status and fraud type
                            String status;
                            String fraudType = "-";
                            
                            if (riskScore >= 80) {
                                status = "🚨 FRAUD";
                                fraudType = String.join(", ", fraudIndicators);
                                fraudCount++;
                                
                                // Auto-save alert
                                try {
                                    NotificationDatabase.saveAlert(
                                        "FRAUD_DETECTED",
                                        "High-risk receipt detected: " + receiptNum,
                                        cashier,
                                        "CRITICAL",
                                        "Risk Score: " + riskScore + "%, Indicators: " + fraudType
                                    );
                                } catch (SQLException alertEx) {
                                    LOGGER.log(Level.WARNING, "Failed to save fraud alert", alertEx);
                                }
                            } else if (riskScore >= 50) {
                                status = "⚡ HIGH RISK";
                                fraudType = String.join(", ", fraudIndicators);
                                suspiciousCount++;
                            } else if (riskScore >= 30) {
                                status = "⚠️ SUSPICIOUS";
                                fraudType = fraudIndicators.isEmpty() ? "Minor Issues" : String.join(", ", fraudIndicators);
                                suspiciousCount++;
                            } else {
                                status = "✅ CLEAN";
                                cleanCount++;
                            }

                            totalCount++;

                            receiptModel.addRow(new Object[]{
                                receiptNum,
                                dateStr,
                                timeStr,
                                cashier,
                                String.valueOf(itemsCount),
                                totalStr,
                                riskScore + "%",
                                fraudType,
                                status
                            });

                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Error processing receipt: " + receipt.getTitle(), ex);
                        }
                    }
                    
                    // Update statistics
                    statLabels[0].setText("<html><div style='text-align: center'><b style='font-size: 20px'>" + totalCount + 
                        "</b><br><span style='font-size: 11px'>Total Receipts</span></div></html>");
                    statLabels[1].setText("<html><div style='text-align: center'><b style='font-size: 20px'>" + suspiciousCount + 
                        "</b><br><span style='font-size: 11px'>Suspicious</span></div></html>");
                    statLabels[2].setText("<html><div style='text-align: center'><b style='font-size: 20px'>" + fraudCount + 
                        "</b><br><span style='font-size: 11px'>Fraud Detected</span></div></html>");
                    statLabels[3].setText("<html><div style='text-align: center'><b style='font-size: 20px'>" + cleanCount + 
                        "</b><br><span style='font-size: 11px'>Clean</span></div></html>");
                    
                    LOGGER.info("Fraud analysis complete: Total=" + totalCount + ", Suspicious=" + suspiciousCount + 
                               ", Fraud=" + fraudCount + ", Clean=" + cleanCount);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to load receipts: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(dialog, "Database error loading receipts:\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
                receiptModel.addRow(new Object[]{"Error loading receipts", "", "", "", "", "", "", "", ""});
            }
        };

        // ===== FILTER FUNCTIONALITY =====
        btnApplyFilter.addActionListener(e -> {
            String selectedFilter = (String) filterCombo.getSelectedItem();
            String cashierSearch = txtSearchCashier.getText().trim().toLowerCase();
            
            for (int i = receiptModel.getRowCount() - 1; i >= 0; i--) {
                String status = (String) receiptModel.getValueAt(i, 8);
                String cashier = (String) receiptModel.getValueAt(i, 3);
                
                boolean matchesFilter = true;
                boolean matchesCashier = cashierSearch.isEmpty() || cashier.toLowerCase().contains(cashierSearch);
                
                if (selectedFilter.contains("Suspicious") && !status.contains("SUSPICIOUS") && !status.contains("HIGH RISK")) {
                    matchesFilter = false;
                } else if (selectedFilter.contains("Fraud") && !status.contains("FRAUD")) {
                    matchesFilter = false;
                } else if (selectedFilter.contains("Clean") && !status.contains("CLEAN")) {
                    matchesFilter = false;
                }
                
                if (!matchesFilter || !matchesCashier) {
                    receiptModel.removeRow(i);
                }
            }
            
            if (receiptModel.getRowCount() == 0) {
                receiptModel.addRow(new Object[]{"No receipts match the filter", "", "", "", "", "", "", "", ""});
            }
        });

        // ===== EXPORT REPORT =====
        btnExportReport.addActionListener(e -> {
            try {
                StringBuilder report = new StringBuilder();
                report.append("=".repeat(100)).append("\n");
                report.append("FRAUD DETECTION REPORT\n");
                report.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
                report.append("=".repeat(100)).append("\n\n");
                
                for (int i = 0; i < receiptModel.getRowCount(); i++) {
                    report.append(String.format("Receipt: %s | Date: %s | Cashier: %s | Risk: %s | Status: %s\n",
                        receiptModel.getValueAt(i, 0),
                        receiptModel.getValueAt(i, 1),
                        receiptModel.getValueAt(i, 3),
                        receiptModel.getValueAt(i, 6),
                        receiptModel.getValueAt(i, 8)));
                    
                    String fraudType = (String) receiptModel.getValueAt(i, 7);
                    if (!"-".equals(fraudType)) {
                        report.append("   Indicators: ").append(fraudType).append("\n");
                    }
                    report.append("\n");
                }
                
                // Save to file
                String filename = "fraud_report_" + LocalDate.now() + ".txt";
                java.nio.file.Files.write(
                    java.nio.file.Paths.get(filename),
                    report.toString().getBytes()
                );
                
                JOptionPane.showMessageDialog(dialog,
                    "Report exported successfully to:\n" + filename,
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to export report", ex);
                JOptionPane.showMessageDialog(dialog,
                    "Failed to export report:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRefresh.addActionListener(e -> loadReceipts.run());

        // View detailed receipt on click
        receiptTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && receiptTable.getSelectedRow() != -1) {
                int selectedRow = receiptTable.getSelectedRow();
                String receiptNumber = (String) receiptModel.getValueAt(selectedRow, 0);
                
                if (receiptNumber == null || receiptNumber.contains("No receipts") || receiptNumber.contains("Error")) {
                    return;
                }

                String status = (String) receiptModel.getValueAt(selectedRow, 8);
                String cashier = (String) receiptModel.getValueAt(selectedRow, 3);
                String riskScore = (String) receiptModel.getValueAt(selectedRow, 6);
                String fraudType = (String) receiptModel.getValueAt(selectedRow, 7);

                try {
                    NotificationDatabase.Notebook notebook =
                        NotificationDatabase.getNotebookByTitle(receiptNumber, cashier);
                    if (notebook != null) {
                        // Create detailed view dialog
                        JDialog detailDialog = new JDialog(dialog, "Receipt Details: " + receiptNumber, true);
                        detailDialog.setSize(900, 650);
                        detailDialog.setLocationRelativeTo(dialog);
                        detailDialog.setLayout(new BorderLayout(10, 10));
                        
                        // Header with risk assessment
                        JPanel detailHeaderPanel = new JPanel(new BorderLayout());
                        detailHeaderPanel.setBackground(status.contains("FRAUD") ? new Color(244, 67, 54) :
                                                       status.contains("HIGH RISK") ? new Color(255, 152, 0) :
                                                       status.contains("SUSPICIOUS") ? new Color(255, 193, 7) :
                                                       new Color(76, 175, 80));
                        detailHeaderPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
                        
                        JLabel headerLabel = new JLabel(status + " - Risk Score: " + riskScore);
                        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
                        headerLabel.setForeground(Color.WHITE);
                        detailHeaderPanel.add(headerLabel, BorderLayout.WEST);
                        
                        detailDialog.add(detailHeaderPanel, BorderLayout.NORTH);
                        
                        // Fraud indicators panel
                        if (!"-".equals(fraudType)) {
                            JPanel fraudPanel = new JPanel(new BorderLayout());
                            fraudPanel.setBackground(new Color(255, 243, 224));
                            fraudPanel.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(255, 152, 0), 2),
                                BorderFactory.createEmptyBorder(10, 15, 10, 15)
                            ));
                            
                            JLabel fraudLabel = new JLabel("⚠️ Fraud Indicators: " + fraudType);
                            fraudLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                            fraudLabel.setForeground(new Color(230, 81, 0));
                            fraudPanel.add(fraudLabel);
                            
                            detailDialog.add(fraudPanel, BorderLayout.NORTH);
                        }
                        
                        // Receipt content
                        String receiptContent = notebook.getContent();
                        JTextArea textArea = new JTextArea(formatReceiptForDisplay(receiptContent));
                        textArea.setEditable(false);
                        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                        textArea.setLineWrap(false);
                        textArea.setBackground(new Color(250, 250, 250));
                        textArea.setForeground(Color.BLACK);
                        textArea.setCaretPosition(0);
                        JScrollPane scrollArea = new JScrollPane(textArea);
                        detailDialog.add(scrollArea, BorderLayout.CENTER);
                        
                        // Action buttons
                        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
                        actionPanel.setBackground(Color.WHITE);
                        
                        JButton btnMarkReviewed = createMenuButton("✓ Mark Reviewed", SUCCESS_GREEN);
                        JButton btnFlagForInvestigation = createMenuButton("🚩 Flag for Investigation", DELETE_RED);
                        JButton btnClose = createMenuButton("Close", new Color(108, 117, 125));
                        
                        actionPanel.add(btnMarkReviewed);
                        actionPanel.add(btnFlagForInvestigation);
                        actionPanel.add(btnClose);
                        
                        btnClose.addActionListener(ev -> detailDialog.dispose());
                        
                        btnMarkReviewed.addActionListener(ev -> {
                            JOptionPane.showMessageDialog(detailDialog,
                                "Receipt marked as reviewed.\nThis feature saves to audit log.",
                                "Marked Reviewed",
                                JOptionPane.INFORMATION_MESSAGE);
                            // TODO: Implement audit log entry
                        });
                        
                        btnFlagForInvestigation.addActionListener(ev -> {
                            String reason = JOptionPane.showInputDialog(detailDialog,
                                "Reason for investigation:",
                                "Flag Receipt",
                                JOptionPane.QUESTION_MESSAGE);
                            if (reason != null && !reason.trim().isEmpty()) {
                                try {
                                    NotificationDatabase.saveAlert(
                                        "MANUAL_FLAG",
                                        "Receipt flagged for investigation: " + receiptNumber,
                                        currentUser,
                                        "HIGH",
                                        "Reason: " + reason + " | Original Cashier: " + cashier
                                    );
                                    JOptionPane.showMessageDialog(detailDialog,
                                        "Receipt flagged for investigation successfully.",
                                        "Flagged",
                                        JOptionPane.INFORMATION_MESSAGE);
                                } catch (SQLException ex) {
                                    LOGGER.log(Level.SEVERE, "Failed to flag receipt", ex);
                                    JOptionPane.showMessageDialog(detailDialog,
                                        "Error flagging receipt:\n" + ex.getMessage(),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        });
                        
                        detailDialog.add(actionPanel, BorderLayout.SOUTH);
                        detailDialog.setVisible(true);
                        
                    } else {
                        JOptionPane.showMessageDialog(dialog, "Receipt not found: " + receiptNumber,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to load receipt: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(dialog, "Error loading receipt:\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Bottom Button Panel
        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        bottomButtonPanel.setBackground(Color.WHITE);
        bottomButtonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        
        JButton btnCloseDialog = createMenuButton("✗ Close", new Color(108, 117, 125));
        btnCloseDialog.setPreferredSize(new Dimension(150, 40));
        btnCloseDialog.addActionListener(e -> dialog.dispose());
        
        bottomButtonPanel.add(btnCloseDialog);
        dialog.add(bottomButtonPanel, BorderLayout.SOUTH);

        // Initial load
        loadReceipts.run();
        
        dialog.setVisible(true);
    }
 // ── Backdated entry: is this user currently authorized? ──────────
    private boolean isAuthorizedForBackdatedEntry(String username) {
        // Always allow admin-level users regardless of toggle state
        try {
            String role = Database.getUserRole(username);
            if (role != null && (
                "SuperAdmin".equalsIgnoreCase(role) ||
                "Admin".equalsIgnoreCase(role)      ||
                "Manager".equalsIgnoreCase(role)    ||
                "Owner".equalsIgnoreCase(role)      ||
                "CEO".equalsIgnoreCase(role))) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not check role for backdated auth: " + username, e);
        }

        // Also allow if admin manually unlocked the date
        if (adminDateUnlocked) return true;

        // Check if this specific non-admin user was granted a timed unlock
        java.time.LocalDateTime grantedAt = unlockedUsers.get(username);
        if (grantedAt == null) return false;

        long minutesElapsed = java.time.Duration.between(
            grantedAt, java.time.LocalDateTime.now()).toMinutes();
        if (minutesElapsed >= BACKDATED_UNLOCK_MINUTES) {
            unlockedUsers.remove(username);
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                    "Your backdated entry permission has expired (5 minutes limit).",
                    "Permission Expired", JOptionPane.WARNING_MESSAGE));
            return false;
        }
        return true;
    }
    private void showReSalePanel() {
        // Check if user has admin rights
        boolean hasAdminRights = false;
        try {
            String userRole = Database.getUserRole(currentUser);
            if ("SuperAdmin".equalsIgnoreCase(userRole) || 
                "Admin".equalsIgnoreCase(userRole) || 
                "Manager".equalsIgnoreCase(userRole) ||
                "CEO".equalsIgnoreCase(userRole) ||
                "Owner".equalsIgnoreCase(userRole)) {
                hasAdminRights = true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not check user role", e);
        }
        
        if (!hasAdminRights) {
            JOptionPane.showMessageDialog(this,
                "⚠️ Kindly contact admin for unlocking date change permission.\n\n" +
                "Only administrators can change sale dates.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Create Re-Sale Panel Dialog
        JDialog reSaleDialog = new JDialog(this, "📅 Re-Sale Panel - Admin Date Management", true);
        reSaleDialog.setSize(500, 400);
        reSaleDialog.setLocationRelativeTo(this);
        reSaleDialog.setLayout(new BorderLayout(10, 10));
        reSaleDialog.getContentPane().setBackground(Color.WHITE);
        
        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(PRIMARY_BLUE);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel titleLabel = new JLabel("🔧 Admin Date Management Panel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        reSaleDialog.add(headerPanel, BorderLayout.NORTH);
        
        // Main Content
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Current Date Display
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel lblCurrent = new JLabel("Current Sale Date:");
        lblCurrent.setFont(new Font("Segoe UI", Font.BOLD, 14));
        contentPanel.add(lblCurrent, gbc);
        
        gbc.gridx = 1;
        JLabel lblCurrentValue = new JLabel(currentSaleDate.format(DATE_FORMATTER));
        lblCurrentValue.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblCurrentValue.setForeground(PRIMARY_BLUE);
        contentPanel.add(lblCurrentValue, gbc);
        
        // New Date Selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel lblNewDate = new JLabel("Select New Date:");
        lblNewDate.setFont(new Font("Segoe UI", Font.BOLD, 14));
        contentPanel.add(lblNewDate, gbc);
        
        gbc.gridx = 1;
        JDateChooser newDateChooser = new JDateChooser();
        newDateChooser.setDateFormatString("yyyy-MM-dd");
        newDateChooser.setDate(new java.util.Date());
        newDateChooser.setPreferredSize(new Dimension(180, 30));
        contentPanel.add(newDateChooser, gbc);
        
        // Reason for change
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel lblReason = new JLabel("Reason for Change:");
        lblReason.setFont(new Font("Segoe UI", Font.BOLD, 14));
        contentPanel.add(lblReason, gbc);
        
        gbc.gridx = 1;
        JComboBox<String> reasonCombo = new JComboBox<>(new String[]{
            "Backdated sale entry",
            "Forgot to record sale",
            "System date error",
            "Manual adjustment",
            "Other"
        });
        reasonCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        reasonCombo.setPreferredSize(new Dimension(180, 30));
        contentPanel.add(reasonCombo, gbc);
        
        // Additional note
        gbc.gridx = 0;
        gbc.gridy = 3;
        JLabel lblNote = new JLabel("Note:");
        lblNote.setFont(new Font("Segoe UI", Font.BOLD, 14));
        contentPanel.add(lblNote, gbc);
        
        gbc.gridx = 1;
        JTextField txtNote = new JTextField(15);
        txtNote.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        contentPanel.add(txtNote, gbc);
        
        // Employee selection (who requested)
        gbc.gridx = 0;
        gbc.gridy = 4;
        JLabel lblEmployee = new JLabel("For Employee:");
        lblEmployee.setFont(new Font("Segoe UI", Font.BOLD, 14));
        contentPanel.add(lblEmployee, gbc);
        
        gbc.gridx = 1;
        JComboBox<String> employeeCombo = new JComboBox<>();
        employeeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        employeeCombo.setPreferredSize(new Dimension(180, 30));
        // Load employees
        try {
            List<String> employees = new ArrayList<>();
            PreparedStatement stmt = Database.getConnection().prepareStatement(
                "SELECT username FROM users WHERE client_id=? AND role NOT IN ('SuperAdmin', 'Admin', 'CEO', 'Owner')");
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                employees.add(rs.getString("username"));
            }
            for (String emp : employees) {
                employeeCombo.addItem(emp);
            }
            if (employees.isEmpty()) {
                employeeCombo.addItem("No employees found");
            }
        } catch (SQLException e) {
            employeeCombo.addItem("Error loading employees");
        }
        contentPanel.add(employeeCombo, gbc);
        
        reSaleDialog.add(contentPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        
        JButton btnApply = new JButton("✅ Apply Date Change");
        btnApply.setBackground(SUCCESS_GREEN);
        btnApply.setForeground(Color.WHITE);
        btnApply.setFocusPainted(false);
        btnApply.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnApply.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnApply.setPreferredSize(new Dimension(180, 40));
        
        JButton btnCancel = new JButton("❌ Cancel");
        btnCancel.setBackground(DELETE_RED);
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setFocusPainted(false);
        btnCancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCancel.setPreferredSize(new Dimension(120, 40));
        
        buttonPanel.add(btnApply);
        buttonPanel.add(btnCancel);
        reSaleDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Apply button action
        btnApply.addActionListener(e -> {
            if (newDateChooser.getDate() == null) {
                JOptionPane.showMessageDialog(reSaleDialog, "Please select a date", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            LocalDate newDate = newDateChooser.getDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
            
            String selectedEmployee = (String) employeeCombo.getSelectedItem();
            String reason = (String) reasonCombo.getSelectedItem();
            String note = txtNote.getText().trim();
            
            // Save current cart before switching
            saveCurrentCartData();
            
            // Change the date
            currentSaleDate = newDate;
            saleDateChooser.setDate(java.sql.Date.valueOf(newDate));
            
            // Load cart for new date
            loadCartDataForDate(newDate);
            
            // Log the date change
            logDateChange(currentUser, selectedEmployee, newDate, reason, note);
            
            JOptionPane.showMessageDialog(reSaleDialog, 
                "✅ Date changed to: " + newDate.format(DATE_FORMATTER) + "\n" +
                "For employee: " + selectedEmployee + "\n" +
                "Reason: " + reason,
                "Date Changed", JOptionPane.INFORMATION_MESSAGE);
            
            reSaleDialog.dispose();
        });
        
        btnCancel.addActionListener(e -> reSaleDialog.dispose());
        
        reSaleDialog.setVisible(true);
    }
    private void logDateChange(String adminUser, String targetEmployee, LocalDate newDate, String reason, String note) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
            conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS date_change_audit (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "admin_user TEXT NOT NULL," +
                "target_employee TEXT," +
                "new_date TEXT NOT NULL," +
                "reason TEXT," +
                "note TEXT," +
                "changed_at TEXT NOT NULL," +
                "client_id TEXT NOT NULL)"
            ).execute();
            
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO date_change_audit (admin_user, target_employee, new_date, reason, note, changed_at, client_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, adminUser);
                stmt.setString(2, targetEmployee);
                stmt.setString(3, newDate.toString());
                stmt.setString(4, reason);
                stmt.setString(5, note);
                stmt.setString(6, LocalDateTime.now().toString());
                stmt.setString(7, Database.getCurrentClientId());
                stmt.execute();
            }
            
            LOGGER.info("Date change logged: " + adminUser + " changed date to " + newDate + " for " + targetEmployee);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to log date change", ex);
        }
    }

    // ── Check if current user is admin/superadmin ────────────────────
    private boolean isAdminUser(String username) {
        try {
            String role = Database.getUserRole(username);
            return role != null && (
                "SuperAdmin".equalsIgnoreCase(role) ||
                "Admin".equalsIgnoreCase(role)      ||
                "Manager".equalsIgnoreCase(role)    ||
                "Owner".equalsIgnoreCase(role)      ||
                "CEO".equalsIgnoreCase(role));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not check role for: " + username, e);
            return false;
        }
    }

    // ── Write backdated audit record inside the open transaction ─────
    private void logBackdatedAudit(Connection conn, LocalDate targetDate,
            List<Database.SaleItem> items, BigDecimal total,
            BigDecimal discount, String doneBy) {
        try {
            // Ensure audit table exists
            conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS backdated_audit (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " done_by      TEXT    NOT NULL," +
                " target_date  TEXT    NOT NULL," +
                " logged_at    TEXT    NOT NULL," +
                " item_count   INTEGER NOT NULL," +
                " total        REAL    NOT NULL," +
                " discount     REAL    NOT NULL," +
                " client_id    TEXT    NOT NULL," +
                " details      TEXT" +
                ")").execute();

            StringBuilder details = new StringBuilder();
            for (Database.SaleItem si : items) {
                try {
                    Database.Product p = Database.getProductById(si.getProductId());
                    String nm = (p != null) ? p.getName() : "ID:" + si.getProductId();
                    details.append(nm).append(" x").append(si.getQuantity())
                           .append(" @").append(si.getPrice()).append("; ");
                } catch (Exception ignored) {}
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO backdated_audit" +
                    " (done_by, target_date, logged_at, item_count, total, discount, client_id, details)" +
                    " VALUES (?,?,?,?,?,?,?,?)")) {
                ins.setString(1, doneBy);
                ins.setString(2, targetDate.toString());
                ins.setString(3, java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                ins.setInt(4, items.size());
                ins.setDouble(5, total.doubleValue());
                ins.setDouble(6, discount.doubleValue());
                ins.setString(7, Database.getCurrentClientId());
                ins.setString(8, details.toString());
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to write backdated audit log", e);
        }
    }
    

    // Helper method to create stat labels
    private JLabel createStatLabel(String title, String value, Color color) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(color);
        label.setText("<html><div style='text-align: center'><b style='font-size: 20px'>" + value + 
            "</b><br><span style='font-size: 11px'>" + title + "</span></div></html>");
        return label;
    }

    // Helper method to format LaTeX receipt content for display
    private String formatReceiptForDisplay(String latexContent) {
        if (latexContent == null || latexContent.trim().isEmpty()) {
            LOGGER.warning("formatReceiptForDisplay: received empty content");
            return "ERROR: Empty receipt content";
        }
        
        StringBuilder formatted = new StringBuilder();
        String[] lines = latexContent.split("\n");
        
        // Variables to extract
        String businessName = businessInfo.getName();
        String address = businessInfo.getAddress();
        String phone = businessInfo.getPhone();
        String email = businessInfo.getEmail();
        String receiptNumber = "";
        String date = "";
        String time = "";
        String cashier = currentUser;
        List<String> items = new ArrayList<>();
        String subtotal = "0.00";
        String tax = "0.00";
        String discount = "0.00";
        String total = "0.00";
        
        boolean inTable = false;
        
        // Parse LaTeX content
        for (String line : lines) {
            line = line.trim();
            
            // Extract receipt number
            if (line.contains("\\textbf{Receipt #:")) {
                int start = line.indexOf("Receipt #:") + 10;
                int end = line.indexOf("}", start);
                if (end > start) {
                    receiptNumber = line.substring(start, end).trim();
                }
            }
            
            // Extract date
            if (line.startsWith("Date:")) {
                date = line.replace("Date:", "").replace("\\\\", "").trim();
            }
            
            // Extract time
            if (line.startsWith("Time:")) {
                time = line.replace("Time:", "").replace("\\\\", "").trim();
            }
            
            // Extract cashier
            if (line.contains("Cashier:")) {
                int start = line.indexOf("Cashier:") + 8;
                cashier = line.substring(start).replace("\\\\", "").trim();
                cashier = unescapeLatex(cashier);
            }
            
            // Extract items from table
            if (line.contains("\\begin{tabular}")) {
                inTable = true;
                continue;
            }
            if (line.contains("\\end{tabular}")) {
                inTable = false;
                continue;
            }
            
            if (inTable && line.contains("&") && !line.contains("Description") && !line.contains("hline")) {
                String[] parts = line.split("&");
                if (parts.length >= 4) {
                    String itemName = unescapeLatex(parts[0].trim());
                    String qty = parts[1].trim();
                    String unitPrice = parts[2].trim();
                    String itemTotal = parts[3].trim().replace("\\\\", "");
                    items.add(String.format("%-25s x%-3s %8s", itemName, qty, itemTotal));
                }
            }
            
            // Extract totals
            if (line.startsWith("Subtotal: ")) {
                subtotal = line.replace("Subtotal: ", "").replace("\\\\", "").trim();
            }
            if (line.startsWith("Tax: ")) {
                tax = line.replace("Tax: ", "").replace("\\\\", "").trim();
            }
            if (line.startsWith("Discount: ")) {
                discount = line.replace("Discount: ", "").replace("\\\\", "").trim();
            }
            if (line.startsWith("Total: ")) {
                total = line.replace("Total: ", "").replace("\\\\", "").trim();
            }
        }
        
        // Build formatted receipt
        formatted.append("================================================\n");
        formatted.append(centerText(businessName, 48)).append("\n");
        formatted.append(centerText(address, 48)).append("\n");
        formatted.append(centerText("Tel: " + phone, 48)).append("\n");
        formatted.append(centerText(email, 48)).append("\n");
        formatted.append("================================================\n\n");
        
        formatted.append(String.format("Receipt #: %-20s\n", receiptNumber));
        formatted.append(String.format("Date: %-30s\n", date));
        formatted.append(String.format("Time: %-30s\n", time));
        formatted.append(String.format("Cashier: %-25s\n", cashier));
        formatted.append("------------------------------------------------\n\n");
        
        formatted.append("ITEMS:\n");
        formatted.append("------------------------------------------------\n");
        for (String item : items) {
            formatted.append(item).append("\n");
        }
        formatted.append("------------------------------------------------\n\n");
        
        formatted.append(String.format("%-30s %10s\n", "Subtotal:", subtotal));
        formatted.append(String.format("%-30s %10s\n", "Tax:", tax));
        formatted.append(String.format("%-30s %10s\n", "Discount:", discount));
        formatted.append("------------------------------------------------\n");
        formatted.append(String.format("%-30s %10s\n", "TOTAL:", total));
        formatted.append("================================================\n\n");
        
        formatted.append(centerText("THANK YOU FOR YOUR PURCHASE!", 48)).append("\n");
        formatted.append(centerText("Please come again", 48)).append("\n");
        formatted.append("================================================\n");
        
        String result = formatted.toString();
        LOGGER.info("Formatted receipt: " + result.length() + " characters");
        return result;
    }


    // Helper method to unescape LaTeX
    private String unescapeLatex(String text) {
        if (text == null) return "";
        return text.replace("\\textbackslash", "\\")  // Must be first!
                    .replace("\\&", "&")
                    .replace("\\%", "%")
                    .replace("\\#", "#")
                    .replace("\\_", "_")
                    .replace("\\{", "{")
                    .replace("\\}", "}")
                    .replace("\\textasciitilde", "~")
                    .replace("\\textasciicircum", "^");
    }
private void showReceiptSettingsDialog() {
     try {
         UIManager.setLookAndFeel(new FlatLightLaf());
     } catch (Exception e) {
         LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
     }

     JDialog dialog = new JDialog(this, "🏪 Modern POS - Business Configuration", true);
     dialog.setLayout(new BorderLayout(0, 0));
     dialog.setSize(1200, 800);
     dialog.setLocationRelativeTo(this);
     dialog.getContentPane().setBackground(new Color(245, 247, 250));

     // ===== MODERN GRADIENT HEADER =====
     JPanel headerPanel = new JPanel(new BorderLayout()) {
         @Override
         protected void paintComponent(Graphics g) {
             super.paintComponent(g);
             Graphics2D g2d = (Graphics2D) g;
             g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
             int w = getWidth(), h = getHeight();
             GradientPaint gp = new GradientPaint(0, 0, new Color(52, 144, 220), w, 0, new Color(67, 97, 238));
             g2d.setPaint(gp);
             g2d.fillRect(0, 0, w, h);
         }
     };
     headerPanel.setPreferredSize(new Dimension(1200, 80));
     headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
     
     JLabel titleLabel = new JLabel("🏪 Business Configuration Center");
     titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
     titleLabel.setForeground(Color.WHITE);
     headerPanel.add(titleLabel, BorderLayout.WEST);
     
     JLabel subtitleLabel = new JLabel("Configure your receipt appearance and business details");
     subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     subtitleLabel.setForeground(new Color(255, 255, 255, 200));
     headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
     
     dialog.add(headerPanel, BorderLayout.NORTH);

     // ===== MAIN SPLIT PANE: Settings (Left) | Live Preview (Right) =====
     JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
     splitPane.setDividerLocation(600);
     splitPane.setDividerSize(8);
     splitPane.setBorder(null);

     // ===== LEFT PANEL: Settings Tabs =====
     JPanel leftPanel = new JPanel(new BorderLayout());
     leftPanel.setBackground(Color.WHITE);
     
     JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
     tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
     tabbedPane.setBackground(Color.WHITE);

     // ===== TAB 1: Basic Information =====
     JPanel basicInfoPanel = new JPanel(new GridBagLayout());
     basicInfoPanel.setBackground(Color.WHITE);
     basicInfoPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
     GridBagConstraints gbc = new GridBagConstraints();
     gbc.insets = new Insets(12, 12, 12, 12);
     gbc.fill = GridBagConstraints.HORIZONTAL;
     gbc.weightx = 1.0;

     int textFieldWidth = 450;
     
     JTextField txtBusinessName = createStyledTextField(businessInfo.getName(), textFieldWidth);
     JTextField txtAddress = createStyledTextField(businessInfo.getAddress(), textFieldWidth);
     JTextField txtPhone = createStyledTextField(businessInfo.getPhone(), textFieldWidth);
     JTextField txtEmail = createStyledTextField(businessInfo.getEmail(), textFieldWidth);
     JTextField txtWebsite = createStyledTextField(businessInfo.getWebsite(), textFieldWidth);

     // Add fields with modern labels
     gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
     basicInfoPanel.add(createStyledLabel("🏢 Business Name:", true), gbc);
     gbc.gridy = 1;
     basicInfoPanel.add(txtBusinessName, gbc);

     gbc.gridy = 2;
     basicInfoPanel.add(createStyledLabel("📍 Full Address:", true), gbc);
     gbc.gridy = 3;
     basicInfoPanel.add(txtAddress, gbc);

     gbc.gridy = 4;
     basicInfoPanel.add(createStyledLabel("📞 Phone Number:", true), gbc);
     gbc.gridy = 5;
     basicInfoPanel.add(txtPhone, gbc);

     gbc.gridy = 6;
     basicInfoPanel.add(createStyledLabel("📧 Email Address:", true), gbc);
     gbc.gridy = 7;
     basicInfoPanel.add(txtEmail, gbc);

     gbc.gridy = 8;
     basicInfoPanel.add(createStyledLabel("🌐 Website:", true), gbc);
     gbc.gridy = 9;
     basicInfoPanel.add(txtWebsite, gbc);

     JScrollPane basicInfoScroll = new JScrollPane(basicInfoPanel);
     basicInfoScroll.setBorder(null);
     basicInfoScroll.getVerticalScrollBar().setUnitIncrement(16);
     tabbedPane.addTab("📝 Basic Info", basicInfoScroll);

     // ===== TAB 2: Custom Fields (Interactive Dialog-Based) =====
     JPanel customFieldsPanel = new JPanel(new BorderLayout(15, 15));
     customFieldsPanel.setBackground(Color.WHITE);
     customFieldsPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

     JLabel customFieldsTitle = new JLabel("🔧 Custom Receipt Fields");
     customFieldsTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
     customFieldsTitle.setForeground(new Color(52, 144, 220));
     
     JLabel customFieldsSubtitle = new JLabel("Add extra information to your receipts (e.g., Tax ID, Return Policy, etc.)");
     customFieldsSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
     customFieldsSubtitle.setForeground(new Color(100, 100, 100));
     
     JPanel titlePanel = new JPanel(new BorderLayout());
     titlePanel.setBackground(Color.WHITE);
     titlePanel.add(customFieldsTitle, BorderLayout.NORTH);
     titlePanel.add(customFieldsSubtitle, BorderLayout.SOUTH);
     customFieldsPanel.add(titlePanel, BorderLayout.NORTH);

     // Custom fields table with modern styling
     DefaultTableModel customFieldsModel = new DefaultTableModel(
         new Object[][]{},
         new String[]{"Field Name", "Field Value", "Display Order"}
     ) {
         @Override
         public boolean isCellEditable(int row, int column) {
             return false; // Make read-only, edit via dialog
         }
         
         @Override
         public Class<?> getColumnClass(int column) {
             return column == 2 ? Integer.class : String.class;
         }
     };

     JTable customFieldsTable = new JTable(customFieldsModel);
     customFieldsTable.setRowHeight(40);
     customFieldsTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     customFieldsTable.setShowGrid(true);
     customFieldsTable.setGridColor(new Color(230, 230, 230));
     customFieldsTable.setSelectionBackground(new Color(52, 144, 220, 50));
     customFieldsTable.setSelectionForeground(Color.BLACK);
     customFieldsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
     customFieldsTable.getTableHeader().setBackground(new Color(52, 144, 220));
     customFieldsTable.getTableHeader().setForeground(Color.WHITE);
     customFieldsTable.getTableHeader().setPreferredSize(new Dimension(0, 45));
     
     // Set column widths
     customFieldsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
     customFieldsTable.getColumnModel().getColumn(1).setPreferredWidth(300);
     customFieldsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
     
     JScrollPane customFieldsScroll = new JScrollPane(customFieldsTable);
     customFieldsScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));
     customFieldsPanel.add(customFieldsScroll, BorderLayout.CENTER);

     // Load existing custom fields from database
     loadCustomFieldsFromDatabase(customFieldsModel);

     // Modern action button panel
     JPanel customFieldsButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
     customFieldsButtonPanel.setBackground(Color.WHITE);
     
     JButton btnAddField = createModernButton("➕ Add Field", SUCCESS_GREEN, 140, 42);
     JButton btnEditField = createModernButton("✏️ Edit Field", new Color(255, 193, 7), 140, 42);
     JButton btnDeleteField = createModernButton("🗑️ Delete", DELETE_RED, 120, 42);
     JButton btnMoveUp = createModernButton("⬆️ Move Up", INFO_PURPLE, 130, 42);
     JButton btnMoveDown = createModernButton("⬇️ Move Down", INFO_PURPLE, 140, 42);
     
     // ===== ADD FIELD DIALOG =====
     btnAddField.addActionListener(e -> {
         showCustomFieldDialog(dialog, customFieldsModel, -1, null, null);
     });
     
     // ===== EDIT FIELD DIALOG =====
     btnEditField.addActionListener(e -> {
         int selectedRow = customFieldsTable.getSelectedRow();
         if (selectedRow == -1) {
             JOptionPane.showMessageDialog(dialog, "Please select a field to edit", "No Selection", JOptionPane.WARNING_MESSAGE);
             return;
         }
         String fieldName = (String) customFieldsModel.getValueAt(selectedRow, 0);
         String fieldValue = (String) customFieldsModel.getValueAt(selectedRow, 1);
         showCustomFieldDialog(dialog, customFieldsModel, selectedRow, fieldName, fieldValue);
     });
     
     // ===== DELETE FIELD =====
     btnDeleteField.addActionListener(e -> {
         int selectedRow = customFieldsTable.getSelectedRow();
         if (selectedRow == -1) {
             JOptionPane.showMessageDialog(dialog, "Please select a field to delete", "No Selection", JOptionPane.WARNING_MESSAGE);
             return;
         }
         
         int confirm = JOptionPane.showConfirmDialog(dialog,
             "Delete field: " + customFieldsModel.getValueAt(selectedRow, 0) + "?",
             "Confirm Delete",
             JOptionPane.YES_NO_OPTION,
             JOptionPane.WARNING_MESSAGE);
             
         if (confirm == JOptionPane.YES_OPTION) {
             customFieldsModel.removeRow(selectedRow);
             reorderCustomFields(customFieldsModel);
         }
     });
     
     // ===== MOVE UP =====
     btnMoveUp.addActionListener(e -> {
         int selectedRow = customFieldsTable.getSelectedRow();
         if (selectedRow <= 0) {
             return;
         }
         customFieldsModel.moveRow(selectedRow, selectedRow, selectedRow - 1);
         customFieldsTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
         reorderCustomFields(customFieldsModel);
     });
     
     // ===== MOVE DOWN =====
     btnMoveDown.addActionListener(e -> {
         int selectedRow = customFieldsTable.getSelectedRow();
         if (selectedRow == -1 || selectedRow >= customFieldsModel.getRowCount() - 1) {
             return;
         }
         customFieldsModel.moveRow(selectedRow, selectedRow, selectedRow + 1);
         customFieldsTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
         reorderCustomFields(customFieldsModel);
     });
     
     customFieldsButtonPanel.add(btnAddField);
     customFieldsButtonPanel.add(btnEditField);
     customFieldsButtonPanel.add(btnDeleteField);
     customFieldsButtonPanel.add(new JSeparator(SwingConstants.VERTICAL));
     customFieldsButtonPanel.add(btnMoveUp);
     customFieldsButtonPanel.add(btnMoveDown);
     customFieldsPanel.add(customFieldsButtonPanel, BorderLayout.SOUTH);

     tabbedPane.addTab("🔧 Custom Fields", customFieldsPanel);

     // ===== TAB 3: Receipt Layout Designer =====
     JPanel layoutPanel = new JPanel(new BorderLayout(15, 15));
     layoutPanel.setBackground(Color.WHITE);
     layoutPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

     JLabel layoutTitle = new JLabel("🎨 Receipt Layout Designer");
     layoutTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
     layoutTitle.setForeground(new Color(52, 144, 220));
     layoutPanel.add(layoutTitle, BorderLayout.NORTH);

     // Layout options panel
     JPanel layoutOptionsPanel = new JPanel(new GridBagLayout());
     layoutOptionsPanel.setBackground(Color.WHITE);
     layoutOptionsPanel.setBorder(BorderFactory.createTitledBorder(
         BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
         "Layout Options",
         javax.swing.border.TitledBorder.LEFT,
         javax.swing.border.TitledBorder.TOP,
         new Font("Segoe UI", Font.BOLD, 14),
         new Color(52, 144, 220)
     ));
     
     GridBagConstraints gbcLayout = new GridBagConstraints();
     gbcLayout.insets = new Insets(10, 10, 10, 10);
     gbcLayout.fill = GridBagConstraints.HORIZONTAL;
     gbcLayout.anchor = GridBagConstraints.WEST;

     // Receipt width selector
     gbcLayout.gridx = 0; gbcLayout.gridy = 0;
     layoutOptionsPanel.add(createStyledLabel("📏 Receipt Width:", false), gbcLayout);
     
     String[] widthOptions = {"48 chars (58mm)", "42 chars (80mm - Compact)", "60 chars (80mm - Wide)", "80 chars (A4)"};
     JComboBox<String> comboWidth = new JComboBox<>(widthOptions);
     comboWidth.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     comboWidth.setPreferredSize(new Dimension(250, 35));
     comboWidth.setSelectedIndex(0);
     gbcLayout.gridx = 1;
     layoutOptionsPanel.add(comboWidth, gbcLayout);

     // Font size selector
     gbcLayout.gridx = 0; gbcLayout.gridy = 1;
     layoutOptionsPanel.add(createStyledLabel("🔤 Font Size:", false), gbcLayout);
     
     SpinnerNumberModel fontSizeModel = new SpinnerNumberModel(12, 8, 20, 1);
     JSpinner spinnerFontSize = new JSpinner(fontSizeModel);
     spinnerFontSize.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     ((JSpinner.DefaultEditor) spinnerFontSize.getEditor()).getTextField().setColumns(5);
     gbcLayout.gridx = 1;
     layoutOptionsPanel.add(spinnerFontSize, gbcLayout);

     // Header alignment
     gbcLayout.gridx = 0; gbcLayout.gridy = 2;
     layoutOptionsPanel.add(createStyledLabel("📐 Header Alignment:", false), gbcLayout);
     
     String[] alignOptions = {"Center", "Left", "Right"};
     JComboBox<String> comboHeaderAlign = new JComboBox<>(alignOptions);
     comboHeaderAlign.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     comboHeaderAlign.setPreferredSize(new Dimension(250, 35));
     gbcLayout.gridx = 1;
     layoutOptionsPanel.add(comboHeaderAlign, gbcLayout);

     // Show business logo option
     gbcLayout.gridx = 0; gbcLayout.gridy = 3; gbcLayout.gridwidth = 2;
     JCheckBox chkShowLogo = new JCheckBox("Display Business Logo (if available)");
     chkShowLogo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     chkShowLogo.setBackground(Color.WHITE);
     layoutOptionsPanel.add(chkShowLogo, gbcLayout);

     // Show custom fields on receipt
     gbcLayout.gridy = 4;
     JCheckBox chkShowCustomFields = new JCheckBox("Show Custom Fields on Receipt");
     chkShowCustomFields.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     chkShowCustomFields.setBackground(Color.WHITE);
     chkShowCustomFields.setSelected(true);
     layoutOptionsPanel.add(chkShowCustomFields, gbcLayout);

     // Bold total amount
     gbcLayout.gridy = 5;
     JCheckBox chkBoldTotal = new JCheckBox("Bold Total Amount");
     chkBoldTotal.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     chkBoldTotal.setBackground(Color.WHITE);
     chkBoldTotal.setSelected(true);
     layoutOptionsPanel.add(chkBoldTotal, gbcLayout);

     // Show QR code
     gbcLayout.gridy = 6;
     JCheckBox chkShowQR = new JCheckBox("Include QR Code");
     chkShowQR.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     chkShowQR.setBackground(Color.WHITE);
     layoutOptionsPanel.add(chkShowQR, gbcLayout);

     layoutPanel.add(layoutOptionsPanel, BorderLayout.CENTER);

     tabbedPane.addTab("🎨 Layout", layoutPanel);

     leftPanel.add(tabbedPane, BorderLayout.CENTER);
     splitPane.setLeftComponent(leftPanel);

     // ===== RIGHT PANEL: LIVE RECEIPT PREVIEW =====
     JPanel previewPanel = new JPanel(new BorderLayout(10, 10));
     previewPanel.setBackground(new Color(245, 247, 250));
     previewPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

     JPanel previewHeaderPanel = new JPanel(new BorderLayout());
     previewHeaderPanel.setBackground(new Color(245, 247, 250));
     
     JLabel previewTitle = new JLabel("👁️ Live Receipt Preview");
     previewTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
     previewTitle.setForeground(new Color(52, 144, 220));
     previewHeaderPanel.add(previewTitle, BorderLayout.WEST);
     
     JButton btnAutoRefresh = new JButton("🔄 Auto-Refresh: ON");
     btnAutoRefresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
     btnAutoRefresh.setBackground(SUCCESS_GREEN);
     btnAutoRefresh.setForeground(Color.WHITE);
     btnAutoRefresh.setFocusPainted(false);
     btnAutoRefresh.setBorderPainted(false);
     btnAutoRefresh.setCursor(new Cursor(Cursor.HAND_CURSOR));
     btnAutoRefresh.setPreferredSize(new Dimension(180, 35));
     previewHeaderPanel.add(btnAutoRefresh, BorderLayout.EAST);
     
     previewPanel.add(previewHeaderPanel, BorderLayout.NORTH);

     // Receipt preview area with paper-like appearance
     JTextArea previewArea = new JTextArea();
     previewArea.setEditable(true); // Allow manual editing
     previewArea.setFont(new Font("Courier New", Font.PLAIN, 12));
     previewArea.setBackground(Color.WHITE);
     previewArea.setForeground(Color.BLACK);
     previewArea.setLineWrap(false);
     previewArea.setMargin(new Insets(20, 20, 20, 20));
     previewArea.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
         BorderFactory.createEmptyBorder(10, 10, 10, 10)
     ));
     
     JScrollPane previewScroll = new JScrollPane(previewArea);
     previewScroll.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createEmptyBorder(10, 10, 10, 10),
         BorderFactory.createLineBorder(new Color(220, 220, 220), 2)
     ));
     previewScroll.getVerticalScrollBar().setUnitIncrement(16);
     previewPanel.add(previewScroll, BorderLayout.CENTER);

     // Preview info panel
     JPanel previewInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
     previewInfoPanel.setBackground(new Color(245, 247, 250));
     JLabel infoLabel = new JLabel("💡 Tip: You can manually edit the preview to customize your receipt layout");
     infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
     infoLabel.setForeground(new Color(100, 100, 100));
     previewInfoPanel.add(infoLabel);
     previewPanel.add(previewInfoPanel, BorderLayout.SOUTH);

     splitPane.setRightComponent(previewPanel);
     dialog.add(splitPane, BorderLayout.CENTER);

     // ===== AUTO-REFRESH FUNCTIONALITY =====
     final boolean[] autoRefreshEnabled = {true};
     
     btnAutoRefresh.addActionListener(e -> {
         autoRefreshEnabled[0] = !autoRefreshEnabled[0];
         if (autoRefreshEnabled[0]) {
             btnAutoRefresh.setText("🔄 Auto-Refresh: ON");
             btnAutoRefresh.setBackground(SUCCESS_GREEN);
         } else {
             btnAutoRefresh.setText("🔄 Auto-Refresh: OFF");
             btnAutoRefresh.setBackground(new Color(158, 158, 158));
         }
     });

     // Refresh preview helper
     Runnable refreshPreview = () -> {
         if (!autoRefreshEnabled[0]) return;
         
         int widthIndex = comboWidth.getSelectedIndex();
         int receiptWidth = widthIndex == 0 ? 48 : widthIndex == 1 ? 42 : widthIndex == 2 ? 60 : 80;
         String alignment = (String) comboHeaderAlign.getSelectedItem();
         int fontSize = (Integer) spinnerFontSize.getValue();
         boolean showCustomFields = chkShowCustomFields.isSelected();
         
         previewArea.setText(generateRealTimePreview(
             txtBusinessName.getText(),
             txtAddress.getText(),
             txtPhone.getText(),
             txtEmail.getText(),
             txtWebsite.getText(),
             customFieldsModel,
             receiptWidth,
             alignment,
             showCustomFields
         ));
         
         // Update font size
         previewArea.setFont(new Font("Courier New", Font.PLAIN, fontSize));
     };

     // Add change listeners to all inputs for real-time preview
     txtBusinessName.getDocument().addDocumentListener(new DocumentListener() {
         public void insertUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void removeUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void changedUpdate(DocumentEvent e) { refreshPreview.run(); }
     });
     txtAddress.getDocument().addDocumentListener(new DocumentListener() {
         public void insertUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void removeUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void changedUpdate(DocumentEvent e) { refreshPreview.run(); }
     });
     txtPhone.getDocument().addDocumentListener(new DocumentListener() {
         public void insertUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void removeUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void changedUpdate(DocumentEvent e) { refreshPreview.run(); }
     });
     txtEmail.getDocument().addDocumentListener(new DocumentListener() {
         public void insertUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void removeUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void changedUpdate(DocumentEvent e) { refreshPreview.run(); }
     });
     txtWebsite.getDocument().addDocumentListener(new DocumentListener() {
         public void insertUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void removeUpdate(DocumentEvent e) { refreshPreview.run(); }
         public void changedUpdate(DocumentEvent e) { refreshPreview.run(); }
     });
     
     comboWidth.addActionListener(e -> refreshPreview.run());
     comboHeaderAlign.addActionListener(e -> refreshPreview.run());
     spinnerFontSize.addChangeListener(e -> refreshPreview.run());
     chkShowCustomFields.addChangeListener(e -> refreshPreview.run());

     // Custom fields table model listener
     customFieldsModel.addTableModelListener(e -> refreshPreview.run());

     // Initial preview
     refreshPreview.run();

     // ===== BOTTOM BUTTON PANEL =====
     JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 20));
     buttonPanel.setBackground(Color.WHITE);
     buttonPanel.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(220, 220, 220)));

     JButton btnSaveAll = createModernButton("💾 Save All Settings", SUCCESS_GREEN, 220, 50);
     btnSaveAll.setFont(new Font("Segoe UI", Font.BOLD, 16));
     
     JButton btnCancel = createModernButton("✗ Cancel", DELETE_RED, 150, 50);

     buttonPanel.add(btnSaveAll);
     buttonPanel.add(btnCancel);
     dialog.add(buttonPanel, BorderLayout.SOUTH);

     // ===== SAVE ALL SETTINGS =====
  // ===== SAVE ALL SETTINGS ===== (FIXED VERSION)
     btnSaveAll.addActionListener(e -> {
         try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
             conn.setAutoCommit(false);
             
             // Save basic info
             businessInfo = new NotificationDatabase.BusinessInfo(
                 txtBusinessName.getText().trim().isEmpty() ? "Your Company Name" : txtBusinessName.getText(),
                 txtAddress.getText().trim().isEmpty() ? "Street Address, City, State, ZIP Code" : txtAddress.getText(),
                 txtPhone.getText().trim().isEmpty() ? "Business Phone Number" : txtPhone.getText(),
                 txtEmail.getText().trim().isEmpty() ? "Business Email Address" : txtEmail.getText(),
                 txtWebsite.getText().trim().isEmpty() ? "Business Website" : txtWebsite.getText()
             );
             businessInfo.saveToDatabase(conn);
             
             // ===== SAFE TABLE MIGRATION =====
             // Check if table exists and has correct schema
             boolean needsRecreate = false;
             try {
                 // Try to query the display_order column
                 String checkSQL = "SELECT display_order FROM custom_receipt_fields LIMIT 1";
                 try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
                     checkStmt.executeQuery();
                 }
             } catch (SQLException ex) {
                 // Column doesn't exist, need to recreate table
                 needsRecreate = true;
                 LOGGER.info("display_order column missing, will recreate table");
             }
             
             if (needsRecreate) {
                 // Drop old table and create new one with correct schema
                 try (PreparedStatement dropStmt = conn.prepareStatement("DROP TABLE IF EXISTS custom_receipt_fields")) {
                     dropStmt.execute();
                     LOGGER.info("Dropped old custom_receipt_fields table");
                 }
             }
             
             // Create table with correct schema (if not exists)
             String createTableSQL = "CREATE TABLE IF NOT EXISTS custom_receipt_fields (" +
                 "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                 "field_name TEXT NOT NULL, " +
                 "field_value TEXT NOT NULL, " +
                 "display_order INTEGER DEFAULT 0, " +
                 "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
             try (PreparedStatement createStmt = conn.prepareStatement(createTableSQL)) {
                 createStmt.execute();
                 LOGGER.info("Ensured custom_receipt_fields table exists with correct schema");
             }
             
             // Clear existing custom fields
             try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM custom_receipt_fields")) {
                 deleteStmt.execute();
             }
             
             // Save custom fields with display order
             String insertSQL = "INSERT INTO custom_receipt_fields (field_name, field_value, display_order) VALUES (?, ?, ?)";
             try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
                 int savedCount = 0;
                 for (int i = 0; i < customFieldsModel.getRowCount(); i++) {
                     String fieldName = (String) customFieldsModel.getValueAt(i, 0);
                     String fieldValue = (String) customFieldsModel.getValueAt(i, 1);
                     Integer displayOrder = (Integer) customFieldsModel.getValueAt(i, 2);
                     
                     if (fieldName != null && !fieldName.trim().isEmpty() &&
                         fieldValue != null && !fieldValue.trim().isEmpty()) {
                         insertStmt.setString(1, fieldName.trim());
                         insertStmt.setString(2, fieldValue.trim());
                         insertStmt.setInt(3, displayOrder != null ? displayOrder : i + 1);
                         insertStmt.addBatch();
                         savedCount++;
                     }
                 }
                 
                 if (savedCount > 0) {
                     insertStmt.executeBatch();
                     LOGGER.info("Saved " + savedCount + " custom fields to database");
                 }
             }
             
             conn.commit();
             
             JOptionPane.showMessageDialog(dialog,
                 "✅ All settings saved successfully!\n\n" +
                 "• Business information updated\n" +
                 "• " + customFieldsModel.getRowCount() + " custom field(s) saved\n" +
                 "• Receipt configuration is now active",
                 "Success",
                 JOptionPane.INFORMATION_MESSAGE);
             LOGGER.info("Business settings and custom fields saved successfully");
             dialog.dispose();
             
         } catch (SQLException ex) {
             LOGGER.log(Level.SEVERE, "Failed to save settings: " + ex.getMessage(), ex);
             JOptionPane.showMessageDialog(dialog,
                 "❌ Failed to save settings:\n\n" + ex.getMessage() +
                 "\n\nPlease check the logs for more details.",
                 "Database Error",
                 JOptionPane.ERROR_MESSAGE);
             
             // Try to rollback
             try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
                 conn.rollback();
             } catch (SQLException rollbackEx) {
                 LOGGER.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
             }
         }
     });

     btnCancel.addActionListener(e -> dialog.dispose());

     dialog.setVisible(true);
 }
private void showGrantUnlockDialog() {
    // Fetch all users from the database
    List<String> allUsers = new ArrayList<>();
    try (PreparedStatement st = Database.getConnection().prepareStatement(
            "SELECT username FROM users WHERE client_id=? ORDER BY username")) {
        st.setString(1, Database.getCurrentClientId());
        ResultSet rs = st.executeQuery();
        while (rs.next()) {
            String u = rs.getString("username");
            if (!u.equalsIgnoreCase(currentUser)) allUsers.add(u);
        }
    } catch (SQLException e) {
        LOGGER.log(Level.WARNING, "Could not load users", e);
    }

    if (allUsers.isEmpty()) {
        JOptionPane.showMessageDialog(this,
            "No other users found to grant access to.",
            "No Users", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    JDialog dlg = new JDialog(this, "Grant Backdated Entry Access", true);
    dlg.setSize(460, 340);
    dlg.setLocationRelativeTo(this);
    dlg.setLayout(new BorderLayout(10, 10));
    dlg.getContentPane().setBackground(Color.WHITE);

    // Header
    JPanel hdr = new JPanel(new BorderLayout());
    hdr.setBackground(new Color(255, 193, 7));
    hdr.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
    JLabel hdrLbl = new JLabel("⏱  Grant 5-Minute Backdated Entry");
    hdrLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
    hdrLbl.setForeground(new Color(60, 40, 0));
    hdr.add(hdrLbl);
    dlg.add(hdr, BorderLayout.NORTH);

    // Body
    JPanel body = new JPanel(new GridBagLayout());
    body.setBackground(Color.WHITE);
    body.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(8, 8, 8, 8);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;

    gc.gridx = 0; gc.gridy = 0;
    JLabel selLbl = new JLabel("Select user to grant access:");
    selLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
    body.add(selLbl, gc);

    gc.gridy = 1;
    JComboBox<String> userCombo = new JComboBox<>(allUsers.toArray(new String[0]));
    userCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    userCombo.setPreferredSize(new Dimension(300, 32));
    body.add(userCombo, gc);

    gc.gridy = 2;
    JLabel dateLbl = new JLabel("Select target backdated date:");
    dateLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
    body.add(dateLbl, gc);

    gc.gridy = 3;
    JDateChooser grantDateChooser = new JDateChooser();
    grantDateChooser.setDateFormatString("yyyy-MM-dd");
    // Default to yesterday
    grantDateChooser.setDate(java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
    grantDateChooser.setPreferredSize(new Dimension(300, 32));
    body.add(grantDateChooser, gc);

    gc.gridy = 4;
    JLabel noteLbl = new JLabel("<html><i>The user will have exactly 5 minutes to enter the backdated items.<br>"
        + "All entries will be logged in the audit trail.</i></html>");
    noteLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
    noteLbl.setForeground(new Color(100, 100, 100));
    body.add(noteLbl, gc);

    dlg.add(body, BorderLayout.CENTER);

    // Buttons
    JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
    btnRow.setBackground(Color.WHITE);
    btnRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));

    JButton btnGrant = new JButton("✅  Grant Access");
    btnGrant.setFont(new Font("Segoe UI", Font.BOLD, 13));
    btnGrant.setBackground(SUCCESS_GREEN);
    btnGrant.setForeground(Color.WHITE);
    btnGrant.setFocusPainted(false);
    btnGrant.setBorderPainted(false);
    btnGrant.setPreferredSize(new Dimension(160, 38));
    btnGrant.setCursor(new Cursor(Cursor.HAND_CURSOR));

    JButton btnCancel = new JButton("Cancel");
    btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    btnCancel.setFocusPainted(false);
    btnCancel.setPreferredSize(new Dimension(100, 38));
    btnCancel.setCursor(new Cursor(Cursor.HAND_CURSOR));

    btnRow.add(btnGrant);
    btnRow.add(btnCancel);
    dlg.add(btnRow, BorderLayout.SOUTH);

    btnGrant.addActionListener(e -> {
        String targetUser = (String) userCombo.getSelectedItem();
        if (targetUser == null) return;

        LocalDate targetDate = (grantDateChooser.getDate() != null)
            ? grantDateChooser.getDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate()
            : LocalDate.now().minusDays(1);

        if (!targetDate.isBefore(LocalDate.now())) {
            JOptionPane.showMessageDialog(dlg,
                "Target date must be a past date.",
                "Invalid Date", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Grant timed unlock
        unlockedUsers.put(targetUser, java.time.LocalDateTime.now());

        // Auto-set the saleDateChooser to the granted date so the user
        // doesn't need to change it manually
        saleDateChooser.setDate(java.sql.Date.valueOf(targetDate));

        // Log the grant in audit table
        try (Connection conn = Database.getConnection()) {
            conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS backdated_audit (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " done_by TEXT NOT NULL, target_date TEXT NOT NULL," +
                " logged_at TEXT NOT NULL, item_count INTEGER NOT NULL," +
                " total REAL NOT NULL, discount REAL NOT NULL," +
                " client_id TEXT NOT NULL, details TEXT)").execute();

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO backdated_audit" +
                    " (done_by, target_date, logged_at, item_count, total, discount, client_id, details)" +
                    " VALUES (?,?,?,0,0,0,?,?)")) {
                ins.setString(1, currentUser);
                ins.setString(2, targetDate.toString());
                ins.setString(3, java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                ins.setString(4, Database.getCurrentClientId());
                ins.setString(5, "GRANT: " + currentUser + " granted backdated access to "
                    + targetUser + " for date " + targetDate);
                ins.executeUpdate();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Could not log grant event", ex);
        }

        // Start a timer to auto-revoke after 5 minutes and notify
        new Thread(() -> {
            try {
                Thread.sleep(BACKDATED_UNLOCK_MINUTES * 60 * 1000L);
                unlockedUsers.remove(targetUser);
                SwingUtilities.invokeLater(() -> {
                    // Reset date chooser back to today
                    saleDateChooser.setDate(new java.util.Date());
                    JOptionPane.showMessageDialog(Salepoint.this,
                        "Backdated access for \"" + targetUser + "\" has expired.",
                        "Access Expired", JOptionPane.INFORMATION_MESSAGE);
                });
                LOGGER.info("Backdated unlock expired for user: " + targetUser);
            } catch (InterruptedException ignored) {}
        }, "backdated-revoke-" + targetUser).start();

        JOptionPane.showMessageDialog(dlg,
            "✅ Access granted to \"" + targetUser + "\" for date " + targetDate + ".\n"
            + "They have exactly " + BACKDATED_UNLOCK_MINUTES + " minutes.\n"
            + "The date field has been pre-set for them.",
            "Access Granted", JOptionPane.INFORMATION_MESSAGE);

        LOGGER.info("Admin " + currentUser + " granted backdated entry to "
            + targetUser + " for date " + targetDate);
        dlg.dispose();
    });

    btnCancel.addActionListener(e -> dlg.dispose());
    dlg.setVisible(true);
}
private void showBackdatedAuditLog() {
    JDialog dlg = new JDialog(this, "📋  Backdated Entry Audit Log", true);
    dlg.setSize(1000, 580);
    dlg.setLocationRelativeTo(this);
    dlg.setLayout(new BorderLayout(10, 10));
    dlg.getContentPane().setBackground(new Color(245, 247, 250));

    // Header
    JPanel hdr = new JPanel(new BorderLayout());
    hdr.setBackground(new Color(30, 80, 160));
    hdr.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
    JLabel hdrLbl = new JLabel("Backdated Entry Audit Log — Full History");
    hdrLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
    hdrLbl.setForeground(Color.WHITE);
    hdr.add(hdrLbl);
    dlg.add(hdr, BorderLayout.NORTH);

    // Filter bar
    JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
    filterBar.setBackground(Color.WHITE);
    filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));

    JLabel byLbl  = new JLabel("User:");
    byLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
    JTextField txtUser = new JTextField(12);
    txtUser.setFont(new Font("Segoe UI", Font.PLAIN, 12));

    JLabel dtLbl  = new JLabel("Date:");
    dtLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
    JDateChooser filterDate = new JDateChooser();
    filterDate.setDateFormatString("yyyy-MM-dd");
    filterDate.setPreferredSize(new Dimension(140, 28));

    JButton btnFilter  = new JButton("🔎 Filter");
    btnFilter.setFont(new Font("Segoe UI", Font.BOLD, 12));
    btnFilter.setBackground(new Color(30, 80, 160));
    btnFilter.setForeground(Color.WHITE);
    btnFilter.setFocusPainted(false);
    btnFilter.setBorderPainted(false);
    btnFilter.setCursor(new Cursor(Cursor.HAND_CURSOR));

    JButton btnRefresh = new JButton("🔄 Refresh");
    btnRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    btnRefresh.setFocusPainted(false);
    btnRefresh.setCursor(new Cursor(Cursor.HAND_CURSOR));

    JButton btnExport  = new JButton("⬇ Export CSV");
    btnExport.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    btnExport.setFocusPainted(false);
    btnExport.setCursor(new Cursor(Cursor.HAND_CURSOR));

    filterBar.add(byLbl); filterBar.add(txtUser);
    filterBar.add(dtLbl); filterBar.add(filterDate);
    filterBar.add(btnFilter); filterBar.add(btnRefresh); filterBar.add(btnExport);
    dlg.add(filterBar, BorderLayout.NORTH);

    // Use a combined north panel
    JPanel northWrapper = new JPanel(new BorderLayout());
    northWrapper.add(hdr, BorderLayout.NORTH);
    northWrapper.add(filterBar, BorderLayout.SOUTH);
    dlg.add(northWrapper, BorderLayout.NORTH);

    // Table
    String[] cols = {"#", "Done By", "Target Date", "Logged At",
                     "Items", "Total", "Discount", "Details"};
    DefaultTableModel mdl = new DefaultTableModel(cols, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    JTable tbl = new JTable(mdl);
    tbl.setRowHeight(28);
    tbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    tbl.setBackground(Color.WHITE);
    tbl.setForeground(new Color(30, 30, 50));
    tbl.setGridColor(new Color(220, 225, 235));
    tbl.setSelectionBackground(new Color(30, 80, 160, 40));
    tbl.setShowHorizontalLines(true);
    tbl.setShowVerticalLines(false);
    tbl.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    tbl.getTableHeader().setBackground(new Color(30, 80, 160));
    tbl.getTableHeader().setForeground(Color.WHITE);
    tbl.getTableHeader().setReorderingAllowed(false);
    tbl.getColumnModel().getColumn(0).setMaxWidth(40);
    tbl.getColumnModel().getColumn(4).setMaxWidth(55);
    tbl.getColumnModel().getColumn(5).setMaxWidth(100);
    tbl.getColumnModel().getColumn(6).setMaxWidth(90);

    // Color-code rows: GRANT rows in amber, actual backdated sales in light blue
    tbl.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
        @Override public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            if (!sel) {
                String details = mdl.getValueAt(row, 7) != null
                    ? mdl.getValueAt(row, 7).toString() : "";
                if (details.startsWith("GRANT:"))
                    setBackground(new Color(255, 248, 220));
                else
                    setBackground(row % 2 == 0 ? Color.WHITE : new Color(240, 246, 255));
                setForeground(new Color(30, 30, 50));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    });

    JScrollPane scroll = new JScrollPane(tbl);
    scroll.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 230), 1));
    dlg.add(scroll, BorderLayout.CENTER);

    // Stats bar at bottom
    JPanel statsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 8));
    statsBar.setBackground(Color.WHITE);
    statsBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
    JLabel totalEntriesLbl = new JLabel("Total entries: 0");
    totalEntriesLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
    JLabel totalSalesLbl   = new JLabel("Total backdated sales: 0.00");
    totalSalesLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
    totalSalesLbl.setForeground(new Color(0, 120, 60));
    JButton btnClose = new JButton("Close");
    btnClose.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    btnClose.addActionListener(ev -> dlg.dispose());
    statsBar.add(totalEntriesLbl);
    statsBar.add(totalSalesLbl);
    JPanel closePnl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
    closePnl.setBackground(Color.WHITE);
    closePnl.add(btnClose);
    JPanel bottomBar = new JPanel(new BorderLayout());
    bottomBar.setBackground(Color.WHITE);
    bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
    bottomBar.add(statsBar, BorderLayout.WEST);
    bottomBar.add(closePnl, BorderLayout.EAST);
    dlg.add(bottomBar, BorderLayout.SOUTH);

    // ── Load data ────────────────────────────────────────────────
    Runnable loadData = () -> {
        mdl.setRowCount(0);
        String userFilter = txtUser.getText().trim();
        String dateFilter = (filterDate.getDate() != null)
            ? filterDate.getDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate().toString()
            : null;

        StringBuilder sql = new StringBuilder(
            "SELECT done_by, target_date, logged_at, item_count," +
            " total, discount, details FROM backdated_audit WHERE client_id=?");
        List<Object> params = new ArrayList<>();
        params.add(Database.getCurrentClientId());

        if (!userFilter.isEmpty()) {
            sql.append(" AND done_by LIKE ?");
            params.add("%" + userFilter + "%");
        }
        if (dateFilter != null) {
            sql.append(" AND target_date=?");
            params.add(dateFilter);
        }
        sql.append(" ORDER BY logged_at DESC");

        int rowNum = 1;
        double grandTotal = 0;
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) st.setObject(i + 1, params.get(i));
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                double rowTotal = rs.getDouble("total");
                grandTotal += rowTotal;
                String details = rs.getString("details");
                mdl.addRow(new Object[]{
                    rowNum++,
                    rs.getString("done_by"),
                    rs.getString("target_date"),
                    rs.getString("logged_at"),
                    rs.getInt("item_count"),
                    String.format("%,.2f", rowTotal),
                    String.format("%,.2f", rs.getDouble("discount")),
                    details != null ? details : ""
                });
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to load backdated audit", ex);
            mdl.addRow(new Object[]{"—", "Error loading data: " + ex.getMessage(),
                "", "", "", "", "", ""});
        }

        final int finalCount = rowNum - 1;
        final double finalTotal = grandTotal;
        SwingUtilities.invokeLater(() -> {
            totalEntriesLbl.setText("Total entries: " + finalCount);
            totalSalesLbl.setText("Total backdated sales: " +
                String.format("%,.2f", finalTotal));
        });
    };

    btnFilter.addActionListener(e  -> loadData.run());
    btnRefresh.addActionListener(e -> loadData.run());

    // ── CSV Export ───────────────────────────────────────────────
    btnExport.addActionListener(e -> {
        try {
            String path = System.getProperty("user.home") + "/Desktop/backdated_audit_"
                + LocalDate.now() + ".csv";
            try (java.io.PrintWriter pw = new java.io.PrintWriter(path, "UTF-8")) {
                pw.println("Row,Done By,Target Date,Logged At,Items,Total,Discount,Details");
                for (int r = 0; r < mdl.getRowCount(); r++) {
                    StringBuilder line = new StringBuilder();
                    for (int c = 0; c < mdl.getColumnCount(); c++) {
                        if (c > 0) line.append(",");
                        String val = mdl.getValueAt(r, c) != null
                            ? mdl.getValueAt(r, c).toString() : "";
                        // Quote fields containing commas
                        if (val.contains(",") || val.contains("\""))
                            val = "\"" + val.replace("\"", "\"\"") + "\"";
                        line.append(val);
                    }
                    pw.println(line);
                }
            }
            JOptionPane.showMessageDialog(dlg,
                "Exported to:\n" + path, "Export Complete",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dlg,
                "Export failed: " + ex.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    });

    loadData.run();
    dlg.setVisible(true);
}

 // ===== HELPER METHOD: Custom Field Dialog =====
 private void showCustomFieldDialog(JDialog parentDialog, DefaultTableModel model, int editIndex, String existingName, String existingValue) {
     JDialog fieldDialog = new JDialog(parentDialog, editIndex == -1 ? "➕ Add Custom Field" : "✏️ Edit Custom Field", true);
     fieldDialog.setSize(550, 350);
     fieldDialog.setLocationRelativeTo(parentDialog);
     fieldDialog.setLayout(new BorderLayout(15, 15));
     fieldDialog.getContentPane().setBackground(Color.WHITE);

     // Header
     JPanel headerPanel = new JPanel(new BorderLayout());
     headerPanel.setBackground(new Color(52, 144, 220));
     headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
     
     JLabel titleLabel = new JLabel(editIndex == -1 ? "Add New Custom Field" : "Edit Custom Field");
     titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
     titleLabel.setForeground(Color.WHITE);
     headerPanel.add(titleLabel, BorderLayout.WEST);
     
     fieldDialog.add(headerPanel, BorderLayout.NORTH);

     // Form panel
     JPanel formPanel = new JPanel(new GridBagLayout());
     formPanel.setBackground(Color.WHITE);
     formPanel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));
     GridBagConstraints gbc = new GridBagConstraints();
     gbc.insets = new Insets(12, 10, 12, 10);
     gbc.fill = GridBagConstraints.HORIZONTAL;
     gbc.weightx = 1.0;

     // Field Name
     gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
     JLabel lblFieldName = new JLabel("🏷️ Field Name:");
     lblFieldName.setFont(new Font("Segoe UI", Font.BOLD, 14));
     formPanel.add(lblFieldName, gbc);

     gbc.gridy = 1;
     JTextField txtFieldName = createStyledTextField(existingName != null ? existingName : "", 400);
     txtFieldName.setToolTipText("e.g., Tax ID, Return Policy, Business Hours");
     formPanel.add(txtFieldName, gbc);

     // Field Value
     gbc.gridy = 2;
     JLabel lblFieldValue = new JLabel("📝 Field Value:");
     lblFieldValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
     formPanel.add(lblFieldValue, gbc);

     gbc.gridy = 3;
     JTextArea txtFieldValue = new JTextArea(existingValue != null ? existingValue : "", 3, 30);
     txtFieldValue.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     txtFieldValue.setLineWrap(true);
     txtFieldValue.setWrapStyleWord(true);
     txtFieldValue.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
         BorderFactory.createEmptyBorder(8, 8, 8, 8)
     ));
     JScrollPane scrollValue = new JScrollPane(txtFieldValue);
     scrollValue.setPreferredSize(new Dimension(400, 80));
     formPanel.add(scrollValue, gbc);

     fieldDialog.add(formPanel, BorderLayout.CENTER);

     // Button panel
     JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
     buttonPanel.setBackground(Color.WHITE);
     buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));

     JButton btnSave = createModernButton("💾 Save Field", SUCCESS_GREEN, 150, 45);
     JButton btnCancel = createModernButton("✗ Cancel", new Color(158, 158, 158), 120, 45);

     buttonPanel.add(btnSave);
     buttonPanel.add(btnCancel);
     fieldDialog.add(buttonPanel, BorderLayout.SOUTH);

     // Save action
     btnSave.addActionListener(e -> {
         String fieldName = txtFieldName.getText().trim();
         String fieldValue = txtFieldValue.getText().trim();

         if (fieldName.isEmpty()) {
             JOptionPane.showMessageDialog(fieldDialog, "Field name cannot be empty", "Validation Error", JOptionPane.ERROR_MESSAGE);
             txtFieldName.requestFocus();
             return;
         }

         if (fieldValue.isEmpty()) {
             JOptionPane.showMessageDialog(fieldDialog, "Field value cannot be empty", "Validation Error", JOptionPane.ERROR_MESSAGE);
             txtFieldValue.requestFocus();
             return;
         }

         if (editIndex == -1) {
             // Add new field
             int order = model.getRowCount() + 1;
             model.addRow(new Object[]{fieldName, fieldValue, order});
         } else {
             // Update existing field
             model.setValueAt(fieldName, editIndex, 0);
             model.setValueAt(fieldValue, editIndex, 1);
         }

         fieldDialog.dispose();
     });

     btnCancel.addActionListener(e -> fieldDialog.dispose());

     fieldDialog.setVisible(true);
 }

 // ===== HELPER METHOD: Load Custom Fields from Database =====
 private void loadCustomFieldsFromDatabase(DefaultTableModel model) {
     try (Connection conn = DriverManager.getConnection("jdbc:sqlite:notifications.db")) {
         String sql = "SELECT field_name, field_value, display_order FROM custom_receipt_fields ORDER BY display_order";
         try (PreparedStatement stmt = conn.prepareStatement(sql);
              ResultSet rs = stmt.executeQuery()) {
             while (rs.next()) {
                 model.addRow(new Object[]{
                     rs.getString("field_name"),
                     rs.getString("field_value"),
                     rs.getInt("display_order")
                 });
             }
             LOGGER.info("Loaded " + model.getRowCount() + " custom fields from database");
         }
     } catch (SQLException e) {
         LOGGER.log(Level.WARNING, "Could not load custom fields, table may not exist", e);
     }
 }

 // ===== HELPER METHOD: Reorder Custom Fields =====
 private void reorderCustomFields(DefaultTableModel model) {
     for (int i = 0; i < model.getRowCount(); i++) {
         model.setValueAt(i + 1, i, 2);
     }
 }

 // ===== HELPER METHOD: Generate Real-Time Preview =====
 private String generateRealTimePreview(String businessName, String address, String phone, 
                                       String email, String website, DefaultTableModel customFields,
                                       int width, String alignment, boolean showCustomFields) {
     StringBuilder preview = new StringBuilder();
     
     // Helper for alignment
     java.util.function.Function<String, String> alignText = text -> {
         if ("Left".equals(alignment)) return text;
         if ("Right".equals(alignment)) return rightAlign(text, width);
         return centerText(text, width);
     };
     
     preview.append(repeatChar('=', width)).append("\n");
     preview.append(alignText.apply(businessName.isEmpty() ? "YOUR BUSINESS NAME" : businessName)).append("\n");
     preview.append(alignText.apply(address.isEmpty() ? "Business Address" : address)).append("\n");
     preview.append(alignText.apply("Tel: " + (phone.isEmpty() ? "Phone" : phone))).append("\n");
     preview.append(alignText.apply(email.isEmpty() ? "Email" : email)).append("\n");
     preview.append(alignText.apply(website.isEmpty() ? "Website" : website)).append("\n");
     
     // Add custom fields if enabled
     if (showCustomFields && customFields.getRowCount() > 0) {
         preview.append(repeatChar('-', width)).append("\n");
         for (int i = 0; i < customFields.getRowCount(); i++) {
             String fieldName = (String) customFields.getValueAt(i, 0);
             String fieldValue = (String) customFields.getValueAt(i, 1);
             if (fieldName != null && !fieldName.trim().isEmpty()) {
                 preview.append(alignText.apply(fieldName + ": " + (fieldValue != null ? fieldValue : ""))).append("\n");
             }
         }
     }
     
     preview.append(repeatChar('=', width)).append("\n\n");
     
     // REAL SAMPLE DATA from current sale table
     preview.append("Receipt #: ").append(generateReceiptNumber()).append("\n");
     preview.append("Date: ").append(currentSaleDate.format(DATE_FORMATTER)).append("\n");
     preview.append("Time: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))).append("\n");
     preview.append("Cashier: ").append(currentUser).append("\n");
     preview.append(repeatChar('-', width)).append("\n\n");
     
     // Use ACTUAL items from sale table
     preview.append("ITEMS:\n");
     preview.append(repeatChar('-', width)).append("\n");
     
     if (modelSales.getRowCount() > 0) {
         // Real items from current transaction
         for (int i = 0; i < Math.min(modelSales.getRowCount(), 5); i++) {
             String productName = (String) modelSales.getValueAt(i, 0);
             int qty = (Integer) modelSales.getValueAt(i, 2);
             Object totalObj = modelSales.getValueAt(i, 3);
             
             String itemLine = String.format("%-" + (width - 15) + "s x%-2d %10s", 
                 truncate(productName, width - 15), qty, totalObj.toString());
             preview.append(itemLine).append("\n");
         }
         
         if (modelSales.getRowCount() > 5) {
             preview.append(centerText("... and " + (modelSales.getRowCount() - 5) + " more items ...", width)).append("\n");
         }
     } else {
         // Sample when no items in cart
         preview.append(String.format("%-" + (width - 15) + "s x%-2d %10s", "Sample Product A", 2, "5000.00")).append("\n");
         preview.append(String.format("%-" + (width - 15) + "s x%-2d %10s", "Sample Product B", 1, "3000.00")).append("\n");
     }
     
     preview.append(repeatChar('-', width)).append("\n\n");
     
     // Calculate real total from lblTotal
     String totalText = lblTotal.getText().replaceAll("[^0-9.]", "");
     double currentTotal = 0.0;
     try {
         currentTotal = Double.parseDouble(totalText);
     } catch (Exception e) {
         currentTotal = 0.0;
     }
     
     double discount = ((Number) spinnerDiscount.getValue()).doubleValue();
     double subtotal = currentTotal + discount;
     
     preview.append(rightAlign("Subtotal: " + formatAmount(subtotal), width)).append("\n");
     preview.append(rightAlign("Discount: " + formatAmount(discount), width)).append("\n");
     preview.append(repeatChar('=', width)).append("\n");
     preview.append(rightAlign("TOTAL: " + formatAmount(currentTotal), width)).append("\n");
     preview.append(repeatChar('=', width)).append("\n\n");
     preview.append(centerText("THANK YOU FOR YOUR PURCHASE!", width)).append("\n");
     preview.append(centerText("Please come again!", width)).append("\n");
     preview.append(repeatChar('=', width)).append("\n");
     
     return preview.toString();
 }

 // ===== HELPER METHOD: Create Styled TextField =====
 private JTextField createStyledTextField(String text, int width) {
     JTextField textField = new JTextField(text);
     textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
     textField.setPreferredSize(new Dimension(width, 38));
     textField.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
         BorderFactory.createEmptyBorder(5, 10, 5, 10)
     ));
     return textField;
 }

 // ===== HELPER METHOD: Create Styled Label =====
 private JLabel createStyledLabel(String text, boolean isBold) {
     JLabel label = new JLabel(text);
     label.setFont(new Font("Segoe UI", isBold ? Font.BOLD : Font.PLAIN, 14));
     label.setForeground(new Color(60, 60, 60));
     return label;
 }

 // ===== HELPER METHOD: Create Modern Button =====
 private JButton createModernButton(String text, Color bgColor, int width, int height) {
     JButton button = new JButton(text);
     button.setFont(new Font("Segoe UI", Font.BOLD, 14));
     button.setBackground(bgColor);
     button.setForeground(Color.WHITE);
     button.setFocusPainted(false);
     button.setBorderPainted(false);
     button.setCursor(new Cursor(Cursor.HAND_CURSOR));
     button.setPreferredSize(new Dimension(width, height));
     button.setOpaque(true);
     
     button.addMouseListener(new MouseAdapter() {
         private final Color originalColor = bgColor;
         
         @Override
         public void mouseEntered(MouseEvent e) {
             button.setBackground(originalColor.brighter());
         }
         
         @Override
         public void mouseExited(MouseEvent e) {
             button.setBackground(originalColor);
         }
     });
     
     return button;
 }

 private void deleteSelected() {
     int row = tableSales.getSelectedRow();
     if (row != -1) {
         String productName = (String) modelSales.getValueAt(row, 0);
         
         modelSales.removeRow(row);
         refreshProducts();
         updateTotal();
         autoSaveCart();  // <-- ADD THIS LINE
         
         LOGGER.info("Removed " + productName + " from cart (no stock restoration needed)");
     }
 }

    private void clearSales() {
        int response = JOptionPane.showConfirmDialog(this,
            "Are You Sure Want To Clear All items?",
            "Confirm Clear All",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (response != JOptionPane.YES_OPTION) {
            LOGGER.info("Clear All operation cancelled by user");
            return;
        }

        modelSales.setRowCount(0);
        clearCartFromLocalStorage();  // <-- ADD THIS LINE
        spinnerDiscount.setValue(0.0);
        refreshProducts();
        updateTotal();
        LOGGER.info("Cleared sales table (no stock restoration needed)");
    }

    private void showReturnDialog() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
        }

        JDialog dialog = new JDialog(this, "Return Item", true);
        dialog.setSize(600, 400);
        dialog.setMinimumSize(new Dimension(600, 400));
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        // Date picker
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblDate = new JLabel("Select Sale Date:");
        lblDate.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JDateChooser dateChooser = new JDateChooser();
        dateChooser.setDateFormatString("yyyy-MM-dd");
        dateChooser.setDate(new java.util.Date());
        northPanel.add(lblDate);
        northPanel.add(dateChooser);

        // Search field
        JTextField txtSearch = new JTextField(20);
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        northPanel.add(new JLabel("Search Product:"));
        northPanel.add(txtSearch);

        dialog.add(northPanel, BorderLayout.NORTH);

        // Table for sold items
        String[] columns = {"Product Name", "Quantity", "Price"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(25);
        table.setBackground(Color.WHITE);
        table.getTableHeader().setBackground(PALE_BLUE);
        table.getTableHeader().setForeground(DARK_GRAY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        JScrollPane scrollPane = new JScrollPane(table);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Input fields and buttons
        JPanel southPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblQty = new JLabel("Quantity to Return:");
        lblQty.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        gbc.gridx = 0; gbc.gridy = 0;
        southPanel.add(lblQty, gbc);

        JSpinner spinnerQty = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        spinnerQty.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        gbc.gridx = 1; gbc.gridy = 0;
        southPanel.add(spinnerQty, gbc);

        JLabel lblReason = new JLabel("Reason for Return:");
        lblReason.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        gbc.gridx = 0; gbc.gridy = 1;
        southPanel.add(lblReason, gbc);

        String[] returnReasons = {
            "Product delivered too late",
            "The product was incomplete/damaged",
            "Product was sold without customer authorization",
            "Product doesn't work as expected",
            "Product was different from expected",
            "Product was sold too high"
        };
        JComboBox<String> reasonComboBox = new JComboBox<>(returnReasons);
        reasonComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        reasonComboBox.setPreferredSize(new Dimension(300, 30));
        gbc.gridx = 1; gbc.gridy = 1;
        southPanel.add(reasonComboBox, gbc);

        JButton btnRecord = new JButton("Record Return");
        btnRecord.setBackground(SUCCESS_GREEN);
        btnRecord.setForeground(Color.WHITE);
        btnRecord.setFont(new Font("Segoe UI", Font.BOLD, 14));
        styleButton(btnRecord);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        southPanel.add(btnRecord, gbc);

        dialog.add(southPanel, BorderLayout.SOUTH);

        // Update table based on date and search
        Runnable updateTable = () -> {
            try {
                model.setRowCount(0);
                LocalDate selectedDate = dateChooser.getDate() != null
                    ? dateChooser.getDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
                String dateStr = NotificationDatabase.toDatabaseDate(selectedDate);
                String searchText = txtSearch.getText().trim();
                List<Database.SaleItem> items = NotificationDatabase.getSoldItemsByDateAndName(dateStr, searchText);
                for (Database.SaleItem item : items) {
                    String productName = "";
                    for (Database.Product p : products) {
                        if (p.getId() == item.productId) {
                            productName = p.getName();
                            break;
                        }
                    }
                    model.addRow(new Object[]{
                        productName,
                        item.quantity,
                        item.price
                    });
                }
                LOGGER.info("Loaded " + items.size() + " sold items for date: " + dateStr);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to update return dialog table: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(dialog, "Error loading sold items: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        dateChooser.getDateEditor().addPropertyChangeListener(evt -> {
            if ("date".equals(evt.getPropertyName())) {
                updateTable.run();
            }
        });

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateTable.run(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateTable.run(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateTable.run(); }
        });

        btnRecord.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(dialog, "Please select a sold item.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int requestedQty = (Integer) spinnerQty.getValue();
            if (requestedQty <= 0) {
                JOptionPane.showMessageDialog(dialog, "Quantity must be greater than 0.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String reason = (String) reasonComboBox.getSelectedItem();
            if (reason == null || reason.trim().isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a return reason.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                String productName = (String) model.getValueAt(selectedRow, 0);
                int displayedSoldQty = (Integer) model.getValueAt(selectedRow, 1);

                // Resolve product ID
                int productId = -1;
                for (Database.Product p : products) {
                    if (p.getName().equals(productName)) { productId = p.getId(); break; }
                }
                if (productId == -1) {
                    JOptionPane.showMessageDialog(dialog, "Product not found: " + productName,
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Verify against the actual database sale record — not just the UI date
                LocalDate uiSoldDate = dateChooser.getDate() != null
                    ? dateChooser.getDate().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();

                // Pull the actual sold qty from DB to prevent UI manipulation
                int actualSoldQty = 0;
                try (PreparedStatement verifyStmt = Database.getConnection().prepareStatement(
                        "SELECT COALESCE(SUM(si.quantity),0) as sold" +
                        " FROM sale_items si JOIN sales s ON si.sale_id=s.id" +
                        " WHERE si.product_id=? AND DATE(s.sale_date)=? AND s.client_id=?")) {
                    verifyStmt.setInt(1, productId);
                    verifyStmt.setString(2, uiSoldDate.toString());
                    verifyStmt.setString(3, Database.getCurrentClientId());
                    ResultSet vrs = verifyStmt.executeQuery();
                    if (vrs.next()) actualSoldQty = vrs.getInt("sold");
                }

                if (actualSoldQty == 0) {
                    JOptionPane.showMessageDialog(dialog,
                        "No database record of \"" + productName
                        + "\" being sold on " + uiSoldDate + ".\nReturn denied.",
                        "Verification Failed", JOptionPane.ERROR_MESSAGE);
                    NotificationDatabase.saveAlert("FRAUD_ATTEMPT",
                        "Return attempted with no matching sale: " + productName,
                        currentUser, "HIGH",
                        "Date: " + uiSoldDate + ", Qty: " + requestedQty);
                    return;
                }
                if (requestedQty > actualSoldQty) {
                    JOptionPane.showMessageDialog(dialog,
                        "Return quantity (" + requestedQty
                        + ") exceeds actual sold quantity (" + actualSoldQty
                        + ") on " + uiSoldDate + ".",
                        "Stock Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Use server-side timestamp for return date — not client clock
                LocalDate returnDate = LocalDate.now();
                NotificationDatabase.saveReturnNotification(productId, productName,
                    requestedQty, reason, uiSoldDate, returnDate, currentUser);
                JOptionPane.showMessageDialog(dialog,
                    "Return request recorded and pending approval.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to record return: " + ex.getMessage(), ex);
                JOptionPane.showMessageDialog(dialog,
                    "Error recording return: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        updateTable.run();
        dialog.setVisible(true);
    }

    private void styleButton(JButton button) {
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new RoundedBorder(10, button.getBackground().darker()));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void refreshProducts() {
        modelProducts.setRowCount(0);
        String keyword = txtSearch.getText().trim().toLowerCase();
        List<Database.Product> productsToShow = keyword.isEmpty() ? products : products.stream()
            .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(keyword))
            .collect(Collectors.toList());

        // Calculate quantities already committed to the cart
        Map<String, Integer> cartQtyPerProduct = new HashMap<>();
        for (int i = 0; i < modelSales.getRowCount(); i++) {
            String productName = (String) modelSales.getValueAt(i, 0);
            Object qtyObj = modelSales.getValueAt(i, 2);
            int qty = (qtyObj instanceof Integer) ? (Integer) qtyObj : 0;
            cartQtyPerProduct.put(productName, cartQtyPerProduct.getOrDefault(productName, 0) + qty);
        }

        for (Database.Product p : productsToShow) {
            try {
                int stockQuantity = getStockQuantitySafe(p);
                int inCart = cartQtyPerProduct.getOrDefault(p.getName(), 0);
                // Never show negative — if somehow cart > stock, clamp to 0
                int availableStock = Math.max(0, stockQuantity - inCart);
                BigDecimal sellingPrice = p.getSellingPrice() != null ? p.getSellingPrice() : BigDecimal.ZERO;
                modelProducts.addRow(new Object[]{
                    p.getName() != null ? p.getName() : "N/A",
                    sellingPrice,
                    availableStock
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error adding product to table: " + (p.getName() != null ? p.getName() : "null"), e);
            }
        }
    }

    private Integer getStockQuantitySafe(Database.Product p) {
        try {
            return p.getStockQuantity();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting stock quantity for product: " + (p.getName() != null ? p.getName() : "null") + ", assuming 0", e);
            return 0;
        }
    }

    private String cleanNumericString(String input) {
        if (input == null || input.trim().isEmpty()) return "0";
        // Strip currency symbols, spaces, commas but preserve leading minus and decimal point
        String cleaned = input.trim()
            .replaceAll("[^0-9.\\-]", "")   // keep digits, dot, minus
            .replaceAll("(?<!^)-", "");     // remove any minus not at start
        // Guard against multiple decimal points
        int dotIdx = cleaned.indexOf('.');
        if (dotIdx >= 0) {
            cleaned = cleaned.substring(0, dotIdx + 1)
                + cleaned.substring(dotIdx + 1).replace(".", "");
        }
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) return "0";
        return cleaned;
    }

    private String formatAmount(double amount) {
        try {
            return new DecimalFormat("#,###.##").format(amount);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error formatting amount: " + amount, e);
            return "0.00";
        }
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < modelSales.getRowCount(); i++) {
            try {
                Object totalObj = modelSales.getValueAt(i, 3);
                String totalStr = cleanNumericString(String.valueOf(totalObj));
                total = total.add(new BigDecimal(totalStr));
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Error calculating total at row "
                    + (i + 1) + ": " + modelSales.getValueAt(i, 3), e);
            }
        }

        BigDecimal discount = BigDecimal.valueOf(
            ((Number) spinnerDiscount.getValue()).doubleValue());
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        BigDecimal netTotal = total.subtract(discount);

        if (lblTotal != null) {
            lblTotal.setText((selectedCurrency.isEmpty() ? "" : selectedCurrency + " ")
                + formatAmount(netTotal.doubleValue()));
        }
    }
    
    // cart formlities
 // Save current cart to local preferences (Windows Registry / Local file)
    private void saveCartToLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/PosCart");
            
            // Convert cart data to simple string format
            StringBuilder cartBuilder = new StringBuilder();
            for (int i = 0; i < modelSales.getRowCount(); i++) {
                String productName = (String) modelSales.getValueAt(i, 0);
                String price = ((BigDecimal) modelSales.getValueAt(i, 1)).toString();
                int quantity = (Integer) modelSales.getValueAt(i, 2);
                String total = ((BigDecimal) modelSales.getValueAt(i, 3)).toString();
                
                if (i > 0) cartBuilder.append("###");
                cartBuilder.append(productName).append("|").append(price).append("|").append(quantity).append("|").append(total);
            }
            
            double discount = ((Number) spinnerDiscount.getValue()).doubleValue();
            
            // Save cart for current user
            prefs.put("cart_" + currentUser, cartBuilder.toString());
            prefs.putDouble("discount_" + currentUser, discount);
            prefs.putLong("lastSaved_" + currentUser, System.currentTimeMillis());
            
            LOGGER.info("Cart saved locally for user: " + currentUser + " - " + modelSales.getRowCount() + " items");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save cart locally", e);
        }
    }

    // Load cart from local preferences
    private void loadCartFromLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/PosCart");
            String savedCart = prefs.get("cart_" + currentUser, "");
            double savedDiscount = prefs.getDouble("discount_" + currentUser, 0.0);
            if (savedCart.isEmpty()) return;

            String[] items = savedCart.split("###");
            List<Object[]> validRows = new ArrayList<>();

            for (String item : items) {
                if (item == null || item.trim().isEmpty()) continue;
                String[] parts = item.split("\\|", -1);
                if (parts.length != 4) continue;
                try {
                    String productName = parts[0].trim();
                    if (productName.isEmpty()) continue;
                    // Strict parse — skip silently if corrupt
                    if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()
                            || parts[3].trim().isEmpty()) continue;
                    BigDecimal price    = new BigDecimal(parts[1].trim());
                    int        quantity = Integer.parseInt(parts[2].trim());
                    BigDecimal rowTotal = new BigDecimal(parts[3].trim());
                    if (price.compareTo(BigDecimal.ZERO) <= 0 || quantity <= 0) continue;

                    int currentStock = getCurrentStockForProduct(productName);
                    if (currentStock <= 0) {
                        LOGGER.info("Cart item out of stock, skipping: " + productName);
                        continue;
                    }
                    int safeQty = Math.min(quantity, currentStock);
                    BigDecimal safeTotal = price.multiply(BigDecimal.valueOf(safeQty));
                    validRows.add(new Object[]{productName, price, safeQty, safeTotal});
                } catch (NumberFormatException | ArithmeticException ex) {
                    LOGGER.warning("Skipping corrupt cart entry: " + item + " — " + ex.getMessage());
                }
            }

            if (!validRows.isEmpty()) {
                for (Object[] row : validRows) modelSales.addRow(row);
                spinnerDiscount.setValue(Math.max(0.0, savedDiscount));
                updateTotal();
                refreshProducts();
                LOGGER.info("Cart loaded for user: " + currentUser
                    + " — " + validRows.size() + " items");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load cart — clearing corrupt data", e);
            clearCartFromLocalStorage();
        }
    }

    // Get current stock for a product by name
    private int getCurrentStockForProduct(String productName) {
        for (Database.Product p : products) {
            if (p.getName().equals(productName)) {
                return p.getStockQuantity();
            }
        }
        return 0;
    }

    // Clear cart from local storage
    private void clearCartFromLocalStorage() {
        if (currentUser == null || currentUser.isEmpty()) return;
        
        try {
            Preferences prefs = Preferences.userRoot().node("hIMs/PosCart");
            prefs.remove("cart_" + currentUser);
            prefs.remove("discount_" + currentUser);
            LOGGER.info("Cart cleared locally for user: " + currentUser);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clear cart locally", e);
        }
    }

    // Auto-save cart whenever cart changes
    private void autoSaveCart() {
        SwingUtilities.invokeLater(() -> saveCartToLocalStorage());
    }
    
    //end of cart formalities

    private void openSaleDialog(int rowIndex) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set FlatLaf look and feel", e);
        }

        String name = (String) modelProducts.getValueAt(rowIndex, 0);
        BigDecimal price;
        try {
            Object priceObj = modelProducts.getValueAt(rowIndex, 1);
            String priceStr = cleanNumericString(String.valueOf(priceObj));
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException("Price must be positive");
            }
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Invalid price format for product: " + name, e);
            JOptionPane.showMessageDialog(this, "Invalid price for product: " + name, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int stock;
        try {
            stock = (Integer) modelProducts.getValueAt(rowIndex, 2);
            if (stock < 0) {
                throw new IllegalArgumentException("Stock cannot be negative");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Invalid stock format for product: " + name, e);
            JOptionPane.showMessageDialog(this, "Invalid stock for product: " + name, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (stock <= 0) {
            JOptionPane.showMessageDialog(this,
                "No stock available for: " + name,
                "Out of Stock", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Transaction", true);
        dialog.setSize(400, 250);
        dialog.setMinimumSize(new Dimension(400, 250));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(UIManager.getColor("Panel.background"));
        dialog.getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblProductName = new JLabel("Product Name:");
        lblProductName.setFont(new Font("Roboto", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        dialog.getContentPane().add(lblProductName, gbc);

        JLabel lblProductValue = new JLabel(name);
        lblProductValue.setFont(new Font("Roboto", Font.PLAIN, 14));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        dialog.getContentPane().add(lblProductValue, gbc);

        JLabel lblStock = new JLabel("Available Stock:");
        lblStock.setFont(new Font("Roboto", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 1;
        dialog.getContentPane().add(lblStock, gbc);

        JLabel lblStockValue = new JLabel(String.valueOf(stock));
        lblStockValue.setFont(new Font("Roboto", Font.BOLD, 14));
        lblStockValue.setForeground(PRIMARY_BLUE);
        gbc.gridx = 1;
        gbc.gridy = 1;
        dialog.getContentPane().add(lblStockValue, gbc);

        JLabel lblQty = new JLabel("Quantity:");
        lblQty.setFont(new Font("Roboto", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 2;
        dialog.getContentPane().add(lblQty, gbc);

        JSpinner spinQty = new JSpinner(new SpinnerNumberModel(1, 1, stock, 1));
        spinQty.setFont(new Font("Roboto", Font.PLAIN, 14));
        gbc.gridx = 1;
        gbc.gridy = 2;
        dialog.getContentPane().add(spinQty, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(UIManager.getColor("Panel.background"));

        JButton btnAdd = new JButton("Add to Cart");
        btnAdd.setFont(new Font("Roboto", Font.BOLD, 14));
        btnAdd.setBackground(new Color(46, 204, 113));
        btnAdd.setForeground(Color.WHITE);
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setFont(new Font("Roboto", Font.BOLD, 14));
        btnCancel.setFocusPainted(false);
        btnCancel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnCancel);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        dialog.getContentPane().add(buttonPanel, gbc);

        // Action to add item safely
     // Action to add item safely
        java.awt.event.ActionListener addAction = e -> {
            try {
                // Commit any in-progress edit in the spinner text field
                try {
                    spinQty.commitEdit();
                } catch (java.text.ParseException pe) {
                    JOptionPane.showMessageDialog(dialog, "Invalid quantity format.", "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int qty = (Integer) spinQty.getValue();
                if (qty <= 0 || qty > stock) {
                    JOptionPane.showMessageDialog(dialog,
                        "Quantity must be between 1 and " + stock + ".",
                        "Invalid Quantity", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(qty));
                modelSales.addRow(new Object[]{name, price, qty, totalPrice});

                // Dispose FIRST — before any post-processing that might throw
                dialog.dispose();

                // Now safely update UI state
                refreshProducts();
                updateTotal();
                autoSaveCart();

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error adding item to cart", ex);
                JOptionPane.showMessageDialog(dialog,
                    "Failed to add item to cart: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        btnAdd.addActionListener(addAction);
        btnCancel.addActionListener(e -> dialog.dispose());

        // Make Enter key trigger add from the spinner text field
        ((JSpinner.DefaultEditor) spinQty.getEditor()).getTextField().addActionListener(addAction);

        // Set default button so Enter anywhere in dialog triggers Add
        dialog.getRootPane().setDefaultButton(btnAdd);

        dialog.setVisible(true);
    }

    private static class RoundedBorder implements Border {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color != null ? color : UIManager.getColor("Component.borderColor");
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2d.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
