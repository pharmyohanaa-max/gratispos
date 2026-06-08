package hIMs.App;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Point;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.util.logging.Level;
import hIMs.App.Subscribe;
// Google OAuth imports
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.EmailAddress;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeParseException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLayeredPane;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
public class Pharmacy_Login extends JFrame {

    private static final long serialVersionUID = 1L;
    private Point dragStart;
    private JPanel contentPane;
    private JPasswordField password;
    private JTextField username;

    private static final Logger LOGGER = Logger.getLogger(Pharmacy_Login.class.getName());
    private static final String SAVED_EMAILS_FILE = System.getProperty("user.home") + File.separator + ".gratispos" + File.separator + "saved_emails.txt";
    private java.util.List<String> savedEmails = new java.util.ArrayList<>();
    private boolean isLoggingIn = false;

    // Security features
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCKOUT_MINUTES = 30;
    private static final int SESSION_TIMEOUT_MINUTES = 60;
    private static final int PASSWORD_EXPIRY_DAYS = 90;
    
    // Per-account attempt tracking
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> perAccountAttempts =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> perAccountLockout =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> perAccountLastAttempt =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MIN_ATTEMPT_INTERVAL_MS = 1000;
    
    // Session state
    private String currentSessionToken = null;
    private LocalDateTime sessionStartTime = null;
    
    // Active sessions tracking
    private static final java.util.concurrent.ConcurrentHashMap<String, ActiveSession> activeSessions =
        new java.util.concurrent.ConcurrentHashMap<>();
    
 // Instance tracking
    private static Pharmacy_Login instance;
    static Pharmacy_Login getInstance() { return instance; }
    { instance = this; }

    // Current user tracking fields
    private String currentClientId = "";
    private String currentUserEmail = "";
    private String currentUsername = "";
    
    // Sync timer
    private Timer syncTimer;
    private String currentSyncingEmail = null;
    private static final int SYNC_INTERVAL_MS = 300000; // 5 minutes
    
    // UI Colors
    private static final Color glassBackground = new Color(0, 0, 0, 250);
    private static final Color glassDarker = new Color(2, 2, 2, 235);
    private static final Color glassPanel = new Color(8, 8, 8, 220);
    private static final Color accentBlue = new Color(0, 140, 255, 255);
    private static final Color accentGreen = new Color(100, 255, 150, 220);
    private static final Color accentRed = new Color(255, 80, 80, 220);
    private static final Color accentYellow = new Color(255, 210, 60, 220);
    private static final Color accentPurple = new Color(140, 80, 255, 255);
    private static final Color textLight = new Color(255, 255, 255, 250);
    private static final Color textMuted = new Color(180, 180, 220, 180);
    private static final Color borderColor = new Color(80, 60, 255, 100);
    private static final Color highlightColor = new Color(140, 100, 255, 90);
    private static final String USER_PREFS_DIR = System.getProperty("user.home") + File.separator + ".gratispos" + File.separator + "prefs";
    
    // Google OAuth Constants
    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + File.separator + ".gratispos" + File.separator + "tokens";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/userinfo.profile email");

    // Inner classes
    private static class AuditLogEntry {
        String username;
        String action;
        String ipAddress;
        LocalDateTime timestamp;
        boolean success;
        String details;
        
        AuditLogEntry(String username, String action, String ipAddress, boolean success, String details) {
            this.username = username;
            this.action = action;
            this.ipAddress = ipAddress;
            this.timestamp = LocalDateTime.now();
            this.success = success;
            this.details = details;
        }
    }
    
    private static class LoginRecord {
        LocalDateTime timestamp;
        String ipAddress;
        boolean success;
        String deviceInfo;
        
        LoginRecord(String ipAddress, boolean success, String deviceInfo) {
            this.timestamp = LocalDateTime.now();
            this.ipAddress = ipAddress;
            this.success = success;
            this.deviceInfo = deviceInfo;
        }
    }
    
    private static class ActiveSession {
        String sessionToken;
        String username;
        LocalDateTime startTime;
        String ipAddress;
        String deviceInfo;
        
        ActiveSession(String token, String username, String ipAddress, String deviceInfo) {
            this.sessionToken = token;
            this.username = username;
            this.startTime = LocalDateTime.now();
            this.ipAddress = ipAddress;
            this.deviceInfo = deviceInfo;
        }
        
        boolean isExpired() {
            return LocalDateTime.now().isAfter(startTime.plusMinutes(SESSION_TIMEOUT_MINUTES));
        }
    }

    public Pharmacy_Login() {
        setTitle("Point Of Sale (PoS) Login Portal");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);
        setResizable(true);
        setBounds(100, 100, 580, 420);
        setBackground(Color.BLACK);
        
        cleanupExpiredSessions();
        
        contentPane = new JPanel() {
            @Override
            public boolean isOpaque() {
                return true;
            }
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth(), h = getHeight();

                g2d.setColor(new Color(0, 0, 0, 250));
                g2d.fillRoundRect(0, 0, w, h, 30, 30);

                g2d.setColor(new Color(0, 80, 255, 40));
                g2d.setStroke(new BasicStroke(6));
                g2d.drawRoundRect(3, 3, w - 7, h - 7, 28, 28);

                g2d.setColor(new Color(60, 40, 255, 50));
                g2d.setStroke(new BasicStroke(5));
                g2d.drawRoundRect(2, 2, w - 5, h - 5, 29, 29);

                g2d.setColor(new Color(100, 80, 255, 70));
                g2d.setStroke(new BasicStroke(4));
                g2d.drawRoundRect(1, 1, w - 3, h - 3, 30, 30);

                g2d.setColor(new Color(140, 100, 255, 90));
                g2d.setStroke(new BasicStroke(3));
                g2d.drawRoundRect(0, 0, w - 1, h - 1, 30, 30);

                java.awt.RadialGradientPaint cg1 = new java.awt.RadialGradientPaint(
                    new java.awt.Point(0, 0), 100,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{new Color(180, 100, 255, 180),
                                new Color(100, 50, 255, 60),
                                new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(cg1);
                g2d.fillRect(0, 0, 150, 150);

                java.awt.RadialGradientPaint cg2 = new java.awt.RadialGradientPaint(
                    new java.awt.Point(w, h), 120,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{new Color(140, 80, 255, 160),
                                new Color(80, 40, 255, 50),
                                new Color(0, 0, 0, 0)}
                );
                g2d.setPaint(cg2);
                g2d.fillRect(w - 150, h - 150, 150, 150);

                java.awt.GradientPaint reflection = new java.awt.GradientPaint(
                    0, 0, new Color(255, 255, 255, 40),
                    0, h / 3, new Color(255, 255, 255, 0)
                );
                g2d.setPaint(reflection);
                g2d.fillRoundRect(5, 5, w - 10, h / 4, 25, 25);

                java.util.Random rand = new java.util.Random(42);
                for (int i = 0; i < 14; i++) {
                    int alpha = 30 + rand.nextInt(50);
                    int size = 1 + rand.nextInt(3);
                    g2d.setColor(new Color(200, 180, 255, alpha));
                    g2d.fillOval(30 + rand.nextInt(w - 60), 30 + rand.nextInt(h - 60), size, size);
                }

                for (int i = 0; i < 3; i++) {
                    int off = i * 2;
                    g2d.setColor(new Color(100 + i * 40, 70 + i * 30, 255, 30 - i * 5));
                    g2d.setStroke(new BasicStroke(1));
                    g2d.drawRoundRect(5 + off, 5 + off, w - 10 - off * 2, h - 10 - off * 2, 22 - off, 22 - off);
                }

                g2d.setColor(new Color(180, 120, 255, 150));
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.drawRoundRect(1, 1, w - 3, h - 3, 29, 29);

                g2d.setColor(new Color(255, 255, 255, 60));
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRoundRect(2, 2, w - 5, h - 5, 27, 27);

                g2d.dispose();
            }
        };
        contentPane.setOpaque(true);
        contentPane.setLayout(null);
        setContentPane(contentPane);

        // Make window draggable
        contentPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }
        });
        contentPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point current = e.getLocationOnScreen();
                setLocation(current.x - dragStart.x, current.y - dragStart.y);
            }
        });

        JLabel lblTitle = new JLabel("Point Of Sale Management System");
        lblTitle.setFont(new Font("Serif", Font.BOLD, 20));
        lblTitle.setForeground(textLight);
        lblTitle.setBounds(73, 38, 402, 30);
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        contentPane.add(lblTitle);

        JLabel lblSubtitle = new JLabel("Secure Email Login");
        lblSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        lblSubtitle.setForeground(textMuted);
        lblSubtitle.setBounds(363, 80, 105, 20);
        contentPane.add(lblSubtitle);

        JLabel lblUsername = new JLabel("Email:");
        lblUsername.setFont(new Font("DialogInput", Font.BOLD, 13));
        lblUsername.setForeground(textLight);
        lblUsername.setBounds(240, 120, 83, 25);
        contentPane.add(lblUsername);

        username = createGlassTextField();
        username.setBounds(323, 117, 180, 30);
        contentPane.add(username);
        
        // User Profile Icon Button
        JButton userProfileBtn = new JButton("👤") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2d.setColor(new Color(140, 100, 255, 80));
                    g2d.fillOval(0, 0, 28, 28);
                } else {
                    g2d.setColor(new Color(60, 40, 255, 50));
                    g2d.fillOval(0, 0, 28, 28);
                }
                g2d.setColor(textLight);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("👤", (28 - fm.stringWidth("👤")) / 2,
                    (28 + fm.getAscent() - fm.getDescent()) / 2);
                g2d.dispose();
            }
        };
        userProfileBtn.setBounds(505, 117, 28, 30);
        userProfileBtn.setOpaque(false);
        userProfileBtn.setContentAreaFilled(false);
        userProfileBtn.setBorderPainted(false);
        userProfileBtn.setFocusPainted(false);
        userProfileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        userProfileBtn.addActionListener(e -> showUserPrivilegesDialog());
        contentPane.add(userProfileBtn);

        JLabel lblPassword = new JLabel("Password:");
        lblPassword.setFont(new Font("DialogInput", Font.BOLD, 14));
        lblPassword.setForeground(textLight);
        lblPassword.setBounds(240, 162, 87, 25);
        contentPane.add(lblPassword);

        password = createGlassPasswordField();
        password.setBounds(323, 159, 210, 30);
        contentPane.add(password);

        JCheckBox showPass = createGlassCheckBox("Show Password");
        showPass.setBounds(363, 197, 160, 20);
        contentPane.add(showPass);

        showPass.addActionListener(e ->
            password.setEchoChar(showPass.isSelected() ? (char) 0 : '•')
        );

        JButton btnLogin = createGlassButton("Login", accentGreen);
        btnLogin.setBounds(363, 225, 135, 36);
        contentPane.add(btnLogin);

        // Footer
        JLabel lblFooter = new JLabel("© Gratis- Point of Sale(PoS). All rights reserved. Created by Yohana");
        lblFooter.setFont(new Font("Dialog", Font.PLAIN, 8));
        lblFooter.setForeground(textMuted);
        lblFooter.setBounds(20, 400, 540, 12);
        contentPane.add(lblFooter);

        // Close button
        JButton closeBtn = new JButton("✕") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2d.setColor(new Color(180, 60, 60, 180));
                    g2d.fillOval(0, 0, 26, 26);
                    g2d.setColor(new Color(255, 100, 100, 220));
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawOval(0, 0, 25, 25);
                } else {
                    g2d.setColor(new Color(140, 100, 255, 80));
                    g2d.fillOval(0, 0, 26, 26);
                    g2d.setColor(new Color(140, 100, 255, 120));
                    g2d.setStroke(new BasicStroke(1f));
                    g2d.drawOval(0, 0, 25, 25);
                }
                g2d.setColor(new Color(255, 255, 255, 220));
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕",
                    (26 - fm.stringWidth("✕")) / 2,
                    ((26 - fm.getHeight()) / 2) + fm.getAscent());
                g2d.dispose();
            }
        };
        closeBtn.setBounds(543, 8, 26, 26);
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> System.exit(0));
        contentPane.add(closeBtn);

        // Glass panel for logo area
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(glassPanel);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setColor(borderColor);
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2d.dispose();
            }
        };
        logoPanel.setOpaque(false);
        logoPanel.setBounds(12, 80, 216, 212);
        logoPanel.setLayout(new BorderLayout());
        contentPane.add(logoPanel);

        JLabel lblNewLabel = new JLabel(new ImageIcon(Pharmacy_Login.class.getResource("/hIMs/App/resources/snow.png")));
        lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        lblNewLabel.setVerticalAlignment(SwingConstants.CENTER);
        logoPanel.add(lblNewLabel, BorderLayout.CENTER);

        JLabel logoname = new JLabel("");
        logoname.setBounds(144, 12, 216, 25);
        logoname.setHorizontalAlignment(SwingConstants.CENTER);
        logoname.setForeground(accentBlue);
        logoname.setFont(new Font("Serif", Font.BOLD, 22));
        contentPane.add(logoname);

        String pharmacyName = PharmacyInfoUtil.getPharmacyName();
        if (pharmacyName.isEmpty()) {
            pharmacyName = "Gratis - Pos";
        }
        logoname.setText(pharmacyName);
        adjustFontToFit(logoname);

        JButton newser = createGlassButton("Sign Up", accentBlue);
        newser.setFont(new Font("FreeMono", Font.ITALIC, 14));
        newser.setBounds(363, 265, 135, 28);
        contentPane.add(newser);

        // Forgot Password - transparent with green text
        JButton forgotPasswordBtn = new JButton("Forgot Password?") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                g2d.setColor(new Color(100, 255, 150, 230));
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
                g2d.dispose();
            }
        };
        forgotPasswordBtn.setFont(new Font("FreeMono", Font.ITALIC, 12));
        forgotPasswordBtn.setOpaque(false);
        forgotPasswordBtn.setContentAreaFilled(false);
        forgotPasswordBtn.setBorderPainted(false);
        forgotPasswordBtn.setFocusPainted(false);
        forgotPasswordBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        forgotPasswordBtn.setBounds(363, 300, 135, 22);
        contentPane.add(forgotPasswordBtn);
        forgotPasswordBtn.addActionListener(e -> showForgotPasswordDialog());

        password.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    btnLogin.doClick();
                }
            }
        });

        btnLogin.addActionListener(e -> performSecureLogin());
        newser.addActionListener(e -> showGoogleSignUpDialog());

        loadSavedEmails();
    }
    private LocalUserStore.Role convertRoleToLocalUserRole(String privileges) {
        if (privileges == null) return LocalUserStore.Role.USER;
        
        // Strip restriction suffix (e.g. "ADMIN:Sale,Inventory" -> "ADMIN")
        String role = privileges.contains(":") ? privileges.split(":")[0] : privileges;
        
        switch (role.toUpperCase().trim()) {
            case "CEO":        return LocalUserStore.Role.CEO;
            case "OWNER":      return LocalUserStore.Role.OWNER;
            case "SUPERADMIN": return LocalUserStore.Role.SUPERADMIN;
            case "ADMIN":      return LocalUserStore.Role.ADMIN;
            case "MANAGER":    return LocalUserStore.Role.MANAGER;
            case "DIRECTOR":   return LocalUserStore.Role.DIRECTOR;
            case "SUPERVISOR": return LocalUserStore.Role.SUPERVISOR;
            case "CASHIER":    return LocalUserStore.Role.CASHIER;
            case "EMPLOYEE":   return LocalUserStore.Role.EMPLOYEE;
            case "VIEWER":     return LocalUserStore.Role.VIEWER;
            default:           return LocalUserStore.Role.USER;
        }
    }

    // ==================== GOOGLE SIGN UP DIALOG ====================
    
    private void showGoogleSignUpDialog() {
        JDialog signUpDialog = new JDialog(this, "Sign Up with Google", true);
        signUpDialog.setUndecorated(true);
        signUpDialog.setSize(500, 200);
        signUpDialog.setLocationRelativeTo(this);
        signUpDialog.setBackground(new Color(0, 0, 0, 0));
        
        final Point[] dragPt = {null};
        
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(0, 0, 0, 250));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setColor(new Color(140, 100, 255, 90));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout());
        
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point cur = e.getLocationOnScreen();
                signUpDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
            }
        });
        
        // Header with close button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 20, 10, 15));
        
        JLabel titleLabel = new JLabel("Sign Up with Google");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 18));
        titleLabel.setForeground(accentBlue);
        
        JButton closeBtn = new JButton("✕") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getModel().isRollover() ? new Color(180, 60, 60, 180) : new Color(140, 100, 255, 80));
                g2d.fillOval(0, 0, 24, 24);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕", (24 - fm.stringWidth("✕")) / 2, ((24 - fm.getHeight()) / 2) + fm.getAscent());
                g2d.dispose();
            }
        };
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> signUpDialog.dispose());
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(closeBtn, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 30, 20, 30));
        
        // Google icon and text
        JLabel googleIcon = new JLabel("G", SwingConstants.CENTER);
        googleIcon.setFont(new Font("Segoe UI", Font.BOLD, 48));
        googleIcon.setForeground(new Color(219, 68, 55));
        googleIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(googleIcon);
        contentPanel.add(Box.createVerticalStrut(10));
        
        JLabel signUpLabel = new JLabel("Sign up with Google");
        signUpLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        signUpLabel.setForeground(textLight);
        signUpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(signUpLabel);
        contentPanel.add(Box.createVerticalStrut(15));
        
        JLabel infoLabel = new JLabel("Click the button below to sign up using your Google account");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoLabel.setForeground(textMuted);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(infoLabel);
        contentPanel.add(Box.createVerticalStrut(25));
        
        // Google Sign Up Button
        JButton googleSignUpBtn = createGlassButton("Continue with Google", new Color(219, 68, 55));
        googleSignUpBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        googleSignUpBtn.setMaximumSize(new Dimension(250, 45));
        googleSignUpBtn.addActionListener(e -> {
            signUpDialog.dispose();
            performGoogleSignUpWithAdminSetup();
        });
        contentPanel.add(googleSignUpBtn);
        contentPanel.add(Box.createVerticalStrut(15));
        
        JLabel termsLabel = new JLabel("By continuing, you agree to our Terms and Privacy Policy");
        termsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        termsLabel.setForeground(textMuted);
        termsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(termsLabel);
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        signUpDialog.add(mainPanel);
        signUpDialog.setVisible(true);
    }

    private void performGoogleSignUpWithAdminSetup() {
        try {
            if (!isInternetAvailable()) {
                JOptionPane.showMessageDialog(this,
                    "Internet connection required for Google Sign-In.",
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            InputStream in = null;
            String userDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                "/credentials.json",
                "/hIMs/App/resources/credentials.json",
                "resources/credentials.json",
                "hIMs/App/resources/credentials.json",
                "credentials.json",
                "./credentials.json",
                "src/hIMs/App/resources/credentials.json",
                userDir + "/credentials.json",
                userDir + "/src/hIMs/App/resources/credentials.json",
                System.getProperty("user.home") + "/credentials.json"
            };

            for (String path : possiblePaths) {
                try {
                    if (path.startsWith("/") && !new File(path).exists()) {
                        InputStream res = getClass().getResourceAsStream(path);
                        if (res != null) { in = res; break; }
                    } else {
                        File file = new File(path);
                        if (file.exists()) { in = new FileInputStream(file); break; }
                    }
                } catch (Exception ignored) {}
            }

            if (in == null) {
                JOptionPane.showMessageDialog(this,
                    "Cannot find credentials.json file.\nPlace it in: " + userDir,
                    "Configuration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            File tokenDirectory = new File(TOKENS_DIRECTORY_PATH);
            if (tokenDirectory.exists()) {
                File[] files = tokenDirectory.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().equals("StoredCredential")) { f.delete(); break; }
                    }
                }
            }

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory))
                .setAccessType("offline")
                .build();

            // Build auth URL with auto-close script injected via state param
            String authUrl = flow.newAuthorizationUrl()
                .set("prompt", "select_account")
                .setRedirectUri("http://localhost:8888/Callback")
                .build();

            java.awt.Desktop.getDesktop().browse(new java.net.URI(authUrl));

            // Start local server to receive callback
            com.sun.net.httpserver.HttpServer callbackServer =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(8888), 0);

            final String[] authCode = {null};
            final boolean[] received = {false};

            // HTML response that closes the tab instantly after success
            String successHtml = "<!DOCTYPE html><html><head><title>Authenticated</title>" +
                "<style>body{background:#0a0a0a;color:#fff;font-family:Arial;display:flex;" +
                "align-items:center;justify-content:center;height:100vh;margin:0;}" +
                ".box{text-align:center;padding:40px;border:1px solid #8c64ff;border-radius:16px;}" +
                "h2{color:#64ff96;margin-bottom:10px;}p{color:#b0b0cc;font-size:13px;}</style></head>" +
                "<body><div class='box'><h2>&#10003; Authenticated!</h2>" +
                "<p>Returning to application...</p></div>" +
                "<script>setTimeout(function(){window.close();},800);</script></body></html>";

            String failHtml = "<!DOCTYPE html><html><head><title>Failed</title>" +
                "<style>body{background:#0a0a0a;color:#fff;font-family:Arial;display:flex;" +
                "align-items:center;justify-content:center;height:100vh;margin:0;}</style></head>" +
                "<body><h2 style='color:#ff5050;'>&#10007; Authentication Failed. Please try again.</h2>" +
                "</body></html>";

            callbackServer.createContext("/Callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("code=")) {
                    authCode[0] = query.split("code=")[1].split("&")[0];
                    received[0] = true;
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, successHtml.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(successHtml.getBytes()); }
                } else {
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, failHtml.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(failHtml.getBytes()); }
                }
                callbackServer.stop(0);
            });

            callbackServer.start();

            long startTime = System.currentTimeMillis();
            while (!received[0] && (System.currentTimeMillis() - startTime) < 300000) {
                Thread.sleep(500);
            }

            if (authCode[0] == null) {
                JOptionPane.showMessageDialog(this,
                    "Google authentication timed out or was cancelled.",
                    "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            com.google.api.client.auth.oauth2.TokenResponse tokenResponse = flow.newTokenRequest(authCode[0])
                .setRedirectUri("http://localhost:8888/Callback")
                .execute();
            Credential credential = flow.createAndStoreCredential(tokenResponse, "user");

            PeopleService peopleService = new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName("Gratis POS").build();

            Person profile = peopleService.people().get("people/me")
                .setPersonFields("names,emailAddresses").execute();

            String googleName = "Google User";
            String email = "";

            List<Name> names = profile.getNames();
            if (names != null && !names.isEmpty()) googleName = names.get(0).getDisplayName();

            List<EmailAddress> emails = profile.getEmailAddresses();
            if (emails != null && !emails.isEmpty()) email = emails.get(0).getValue();

            // Small delay to allow browser tab to close before showing dialog
            Thread.sleep(900);

            final String finalEmail = email;
            final String finalGoogleName = googleName;

            // Check if user already exists in cloud
            SwingUtilities.invokeLater(() -> {
                try {
                    List<JsonObject> existingUsers = SQLiteCloudClient.execute(
    "SELECT email, username, privileges, business_name, " +
    "security_question1, security_question2 FROM users WHERE email = ?", finalEmail);

                    if (!existingUsers.isEmpty()) {
                        // User exists — pre-fill admin dialog with cloud data
                        showAdminSetupDialog(finalEmail, finalGoogleName, existingUsers.get(0));
                    } else {
                        // New user — open empty admin dialog
                        showAdminSetupDialog(finalEmail, finalGoogleName, null);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Could not check cloud user, opening empty dialog", ex);
                    showAdminSetupDialog(finalEmail, finalGoogleName, null);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Failed to sign up with Google: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showAdminSetupDialog(String email, String googleName, JsonObject existingData) {
        boolean isExisting = existingData != null;

        JDialog setupDialog = new JDialog(this, "Welcome to Gratis POS", true);
        setupDialog.setUndecorated(true);
        setupDialog.setSize(500, 750);
        setupDialog.setLocationRelativeTo(this);
        setupDialog.setBackground(new Color(0, 0, 0, 0));

        final Point[] dragPt = {null};

        JPanel mainPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(0, 0, 0, 250));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setColor(new Color(140, 100, 255, 90));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout());

        mainPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point cur = e.getLocationOnScreen();
                setupDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
            }
        });

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 20, 10, 15));

        JLabel titleLabel = new JLabel(isExisting ? "Update Your Profile" : "Welcome to Gratis POS");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 20));
        titleLabel.setForeground(accentBlue);

        JButton closeBtn = new JButton("✕") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getModel().isRollover() ? new Color(180, 60, 60, 180) : new Color(140, 100, 255, 80));
                g2d.fillOval(0, 0, 24, 24);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕", (24 - fm.stringWidth("✕")) / 2, ((24 - fm.getHeight()) / 2) + fm.getAscent());
                g2d.dispose();
            }
        };
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.setOpaque(false); closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false); closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> setupDialog.dispose());

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        titlePanel.add(closeBtn, BorderLayout.EAST);
        headerPanel.add(titlePanel, BorderLayout.NORTH);

        JLabel subtitleLabel = new JLabel(isExisting ? "Account found — data pre-filled from cloud" : "Complete Admin Privileges");
        subtitleLabel.setFont(new Font("Dialog", Font.ITALIC, 12));
        subtitleLabel.setForeground(isExisting ? accentGreen : accentYellow);
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Helper to safely get string from JsonObject
        java.util.function.BiFunction<String, String, String> getField = (key, fallback) -> {
            if (existingData != null && existingData.has(key) && !existingData.get(key).isJsonNull()) {
                return existingData.get(key).getAsString();
            }
            return fallback;
        };

        // Content
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 30, 20, 30));

        // Email (read-only)
        JLabel emailLabel = new JLabel("Valid Email:");
        emailLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        emailLabel.setForeground(textLight);
        emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(emailLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField emailField = createGlassTextField();
        emailField.setText(email);
        emailField.setEditable(false);
        emailField.setMaximumSize(new Dimension(400, 32));
        emailField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(emailField);
        contentPanel.add(Box.createVerticalStrut(15));

        // Username
        JLabel usernameLabel = new JLabel("Create Username:");
        usernameLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        usernameLabel.setForeground(textLight);
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(usernameLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField usernameField = createGlassTextField();
        String defaultUsername = getField.apply("username",
            googleName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase());
        if (defaultUsername.isEmpty()) defaultUsername = email.split("@")[0];
        usernameField.setText(defaultUsername);
        usernameField.setMaximumSize(new Dimension(400, 32));
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(usernameField);
        contentPanel.add(Box.createVerticalStrut(15));

        // Password
        JLabel passwordLabel = new JLabel(isExisting ? "New Password (leave blank to keep):" : "Create Password (min 8 characters):");
        passwordLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        passwordLabel.setForeground(textLight);
        passwordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(passwordLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JPasswordField passwordField = createGlassPasswordField();
        passwordField.setMaximumSize(new Dimension(400, 32));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(passwordField);
        contentPanel.add(Box.createVerticalStrut(15));

        // Confirm Password
        JLabel confirmLabel = new JLabel("Confirm Password:");
        confirmLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        confirmLabel.setForeground(textLight);
        confirmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(confirmLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JPasswordField confirmPasswordField = createGlassPasswordField();
        confirmPasswordField.setMaximumSize(new Dimension(400, 32));
        confirmPasswordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(confirmPasswordField);
        contentPanel.add(Box.createVerticalStrut(15));

        // Security Questions
        String[] predefinedQuestions = {
            "What was the name of your first pet?",
            "What is your mother's maiden name?",
            "What city were you born in?",
            "What was the name of your primary school?",
            "What is your oldest sibling's middle name?",
            "What street did you grow up on?",
            "What was the make of your first car?",
            "What is your favourite movie of all time?"
        };

        JLabel securityLabel = new JLabel("Security Questions:");
        securityLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        securityLabel.setForeground(accentYellow);
        securityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(securityLabel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Question 1
        JComboBox<String> question1Combo = new JComboBox<>(predefinedQuestions);
        String savedQ1 = getField.apply("security_question1", predefinedQuestions[0]);
        for (int i = 0; i < predefinedQuestions.length; i++) {
            if (predefinedQuestions[i].equals(savedQ1)) { question1Combo.setSelectedIndex(i); break; }
        }
        question1Combo.setMaximumSize(new Dimension(400, 32));
        question1Combo.setFont(new Font("Dialog", Font.PLAIN, 11));
        question1Combo.setBackground(new Color(10, 10, 20));
        question1Combo.setForeground(textLight);
        question1Combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(question1Combo);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField answer1Field = createGlassTextField();
        // Don't pre-fill hashed answers — security answers must be re-entered
        answer1Field.setMaximumSize(new Dimension(400, 32));
        answer1Field.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (isExisting) {
            answer1Field.setToolTipText("Re-enter your answer to keep security question");
            answer1Field.putClientProperty("JTextField.placeholderText", "Re-enter answer...");
        }
        contentPanel.add(answer1Field);
        contentPanel.add(Box.createVerticalStrut(10));

        // Question 2
        JComboBox<String> question2Combo = new JComboBox<>(predefinedQuestions);
        String savedQ2 = getField.apply("security_question2", predefinedQuestions[1]);
        for (int i = 0; i < predefinedQuestions.length; i++) {
            if (predefinedQuestions[i].equals(savedQ2)) { question2Combo.setSelectedIndex(i); break; }
        }
        question2Combo.setMaximumSize(new Dimension(400, 32));
        question2Combo.setFont(new Font("Dialog", Font.PLAIN, 11));
        question2Combo.setBackground(new Color(10, 10, 20));
        question2Combo.setForeground(textLight);
        question2Combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(question2Combo);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField answer2Field = createGlassTextField();
        answer2Field.setMaximumSize(new Dimension(400, 32));
        answer2Field.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(answer2Field);
        contentPanel.add(Box.createVerticalStrut(15));

        // Role
        JLabel roleLabel = new JLabel("Role:");
        roleLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        roleLabel.setForeground(textLight);
        roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(roleLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        String[] roles = {"SUPERADMIN", "ADMIN", "MANAGER", "CASHIER"};
        JComboBox<String> roleCombo = new JComboBox<>(roles);
        String savedRole = getField.apply("privileges", "SUPERADMIN");
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equalsIgnoreCase(savedRole)) { roleCombo.setSelectedIndex(i); break; }
        }
        roleCombo.setMaximumSize(new Dimension(400, 32));
        roleCombo.setFont(new Font("Dialog", Font.PLAIN, 12));
        roleCombo.setBackground(new Color(10, 10, 20));
        roleCombo.setForeground(textLight);
        roleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(roleCombo);
        contentPanel.add(Box.createVerticalStrut(15));

        // Business Name
        JLabel businessLabel = new JLabel("Business Name:");
        businessLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        businessLabel.setForeground(textLight);
        businessLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(businessLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField businessField = createGlassTextField();
        String defaultBusiness = getField.apply("business_name", googleName.split(" ")[0] + "'s Business");
        businessField.setText(defaultBusiness);
        businessField.setMaximumSize(new Dimension(400, 32));
        businessField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(businessField);
        contentPanel.add(Box.createVerticalStrut(25));

        final String finalEmail = email;
        final String finalGoogleName = googleName;

        JButton applyBtn = createGlassButton(isExisting ? "Update & Login" : "Apply Admin Privileges", accentGreen);
        applyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyBtn.setMaximumSize(new Dimension(300, 45));
        applyBtn.addActionListener(e -> {
            String usernameVal = usernameField.getText().trim();
            String passwordVal = new String(passwordField.getPassword()).trim();
            String confirmVal = new String(confirmPasswordField.getPassword()).trim();
            String businessName = businessField.getText().trim();
            String role = (String) roleCombo.getSelectedItem();
            String q1 = (String) question1Combo.getSelectedItem();
            String a1 = answer1Field.getText().trim();
            String q2 = (String) question2Combo.getSelectedItem();
            String a2 = answer2Field.getText().trim();

            if (usernameVal.isEmpty()) {
                showGlassMessage("Missing Field", "Please enter a username", JOptionPane.WARNING_MESSAGE); return;
            }
            if (businessName.isEmpty()) {
                showGlassMessage("Missing Field", "Please enter your business name", JOptionPane.WARNING_MESSAGE); return;
            }
            if (a1.isEmpty() || a2.isEmpty()) {
                showGlassMessage("Missing Answers", "Please answer both security questions", JOptionPane.WARNING_MESSAGE); return;
            }

            // For existing users, password is optional (blank = keep existing)
            if (!isExisting && passwordVal.length() < 8) {
                showGlassMessage("Weak Password", "Password must be at least 8 characters", JOptionPane.WARNING_MESSAGE); return;
            }
            if (!passwordVal.isEmpty() && !passwordVal.equals(confirmVal)) {
                showGlassMessage("Password Mismatch", "Passwords do not match", JOptionPane.WARNING_MESSAGE); return;
            }

            setupDialog.dispose();

            if (isExisting) {
                // Update existing user record, then login
                updateExistingCloudUser(finalEmail, usernameVal,
                    passwordVal.isEmpty() ? null : passwordVal,
                    businessName, role, q1, a1, q2, a2, finalGoogleName);
            } else {
                createAdminAccount(finalEmail, usernameVal, passwordVal, businessName, role, q1, a1, q2, a2, finalGoogleName);
            }
        });
        contentPanel.add(applyBtn);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        setupDialog.add(mainPanel);
        setupDialog.setVisible(true);
    }

    private void createAdminAccount(String email, String username, String password, String businessName,
            String role, String question1, String answer1, String question2, String answer2, String googleName) {

        JDialog progressDialog = new JDialog(this, "Creating Account", true);
        progressDialog.setUndecorated(true);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(new Color(0, 0, 0, 200));
        progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel progressLabel = new JLabel("Creating your admin account...", SwingConstants.CENTER);
        progressLabel.setForeground(textLight);
        progressPanel.add(progressLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressPanel.add(progressBar, BorderLayout.SOUTH);
        progressDialog.add(progressPanel);

        new Thread(() -> {
            try {
                boolean connected = Database.checkTursoConnection();

                // ── Offline: offer local-only creation ──
                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        int choice = JOptionPane.showConfirmDialog(Pharmacy_Login.this,
                            "No internet connection. Create account locally only?\n" +
                            "Data will sync when online.",
                            "Offline Mode", JOptionPane.YES_NO_OPTION);
                        if (choice == JOptionPane.YES_OPTION) {
                            createLocalAdminAccount(email, username, password, businessName,
                                role, question1, answer1, question2, answer2, googleName);
                        }
                    });
                    return;
                }

                // ── Check if email already exists ──
                List<JsonObject> existingUsers = SQLiteCloudClient.execute(
                    "SELECT email FROM users WHERE email = ?", email);
                if (!existingUsers.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        showGlassMessage("Email Exists",
                            "This email is already registered.", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // ── Check if username already exists ──
                List<JsonObject> existingUsernames = SQLiteCloudClient.execute(
                    "SELECT username FROM users WHERE username = ?", username);
                if (!existingUsernames.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        showGlassMessage("Username Exists",
                            "This username is already taken.", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // ── Hash credentials ──
                String hashedPassword = hashPassword(password);
                String hashedAnswer1  = hashAnswer(answer1.trim().toLowerCase());
                String hashedAnswer2  = hashAnswer(answer2.trim().toLowerCase());

                // ── Generate clientId (deterministic from email, NOT random UUID) ──
                // Using email-based MD5 so same email always gets same clientId
                // even if account is recreated offline then synced
                String clientId = generateDeterministicClientId(email);
                LOGGER.info("Generated clientId: " + clientId + " for email: " + email);

                // ── Fix 4: Insert into cloud with row-count verification ──
                int rowsInserted = 0;
                try {
                    rowsInserted = SQLiteCloudClient.executeUpdate(
                        "INSERT INTO users (email, username, password, privileges, business_name, " +
                        "client_id, created_date, password_last_changed, account_status, " +
                        "email_verified, security_question1, security_answer1, " +
                        "security_question2, security_answer2) " +
                        "VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), " +
                        "'ACTIVE', 1, ?, ?, ?, ?)",
                        email, username, hashedPassword, role, businessName, clientId,
                        question1, hashedAnswer1, question2, hashedAnswer2);

                    LOGGER.info("Cloud insert result: " + rowsInserted +
                                " row(s) for email=" + email);
                } catch (IOException cloudEx) {
                    LOGGER.severe("Cloud INSERT failed with exception: " + cloudEx.getMessage());
                    // Don't return — still create locally so user is not left with nothing
                    SwingUtilities.invokeLater(() ->
                        showGlassMessage("Cloud Warning",
                            "Account saved locally but cloud sync failed.\n" +
                            "It will retry when internet is available.",
                            JOptionPane.WARNING_MESSAGE));
                }

                if (rowsInserted < 1) {
                    // Insert returned 0 rows — log it but still continue with local setup
                    LOGGER.severe("Cloud INSERT returned 0 rows — user NOT in cloud! " +
                                  "email=" + email);
                    SwingUtilities.invokeLater(() ->
                        showGlassMessage("Cloud Warning",
                            "Account created locally but could not verify cloud save.\n" +
                            "Please check your internet connection.",
                            JOptionPane.WARNING_MESSAGE));
                }

                // ── Verify cloud insert by reading back ──
                try {
                    List<JsonObject> verify = SQLiteCloudClient.execute(
                        "SELECT email, client_id FROM users WHERE email = ?", email);
                    if (verify.isEmpty()) {
                        LOGGER.severe("VERIFICATION FAILED: user not found in cloud after insert! " +
                                      "email=" + email);
                    } else {
                        String cloudClientId = verify.get(0).has("client_id") &&
                            !verify.get(0).get("client_id").isJsonNull()
                            ? verify.get(0).get("client_id").getAsString() : "null";
                        LOGGER.info("Cloud verification OK: email=" + email +
                                    " clientId=" + cloudClientId);
                        // Use the verified clientId from cloud (in case it was modified)
                        if (!cloudClientId.equals("null") && !cloudClientId.isEmpty()) {
                            clientId = cloudClientId;
                        }
                    }
                } catch (IOException verifyEx) {
                    LOGGER.warning("Could not verify cloud insert: " + verifyEx.getMessage());
                }

                // ── Save business name ──
                PharmacyInfoUtil.savePharmacyName(businessName);

                // ── Set up local DB with the SAME clientId ──
                // CRITICAL: pass clientId explicitly, never call the 2-arg version here
                Database.setCurrentUser(email, username, clientId);
                SQLiteCloudClient.setCurrentUser(email, username, clientId);

                // ── Ensure user has a uid ──
                try {
                    Database.ensureUserUid(email);
                } catch (Exception uidEx) {
                    LOGGER.warning("ensureUserUid failed: " + uidEx.getMessage());
                }

                // ── Initialize local DB (pulls cloud data down) ──
                try {
                    Database.initializeUserDatabase(email, username);
                } catch (Exception dbEx) {
                    LOGGER.warning("initializeUserDatabase failed: " + dbEx.getMessage());
                }

                // ── Register in LocalUserStore for offline login ──
                try {
                    LocalUserStore.Role localRole = convertRoleStringToEnum(role);
                    LocalUserStore store = LocalUserStore.getInstance();
                    store.registerUser(email, username, localRole, password);
                    LOGGER.info("Registered in LocalUserStore: " + email + " role=" + localRole);
                } catch (Exception storeEx) {
                    LOGGER.warning("LocalUserStore registration failed: " + storeEx.getMessage());
                }

                // ── Also insert into local SQLite DB directly ──
                // This ensures offline login works immediately without a pull
                try (java.sql.Connection conn = Database.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR IGNORE INTO users (email, username, password, privileges, " +
                         "business_name, client_id, created_date, account_status, " +
                         "security_question1, security_answer1, " +
                         "security_question2, security_answer2) " +
                         "VALUES (?, ?, ?, ?, ?, ?, datetime('now','localtime'), " +
                         "'ACTIVE', ?, ?, ?, ?)")) {
                    ps.setString(1, email);
                    ps.setString(2, username);
                    ps.setString(3, hashedPassword);
                    ps.setString(4, role);
                    ps.setString(5, businessName);
                    ps.setString(6, clientId);
                    ps.setString(7, question1);
                    ps.setString(8, hashedAnswer1);
                    ps.setString(9, question2);
                    ps.setString(10, hashedAnswer2);
                    int localRows = ps.executeUpdate();
                    LOGGER.info("Local SQLite insert: " + localRows + " row(s) for " + email);
                } catch (java.sql.SQLException localEx) {
                    LOGGER.warning("Local SQLite insert failed: " + localEx.getMessage());
                }

                // ── Save email to history ──
                saveEmailToHistory(email);

                // ── Final clientId for lambda capture ──
                final String finalClientId = clientId;

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    showGlassMorphismSuccessDialog(email, username, role, businessName, googleName);
                });

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Account creation failed", e);
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    showGlassMessage("Creation Failed",
                        "Failed to create account: " + e.getMessage(),
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();

        progressDialog.setVisible(true);
    }

private void showGlassMorphismSuccessDialog(String email, String username, String role, String businessName, String googleName) {
JDialog successDialog = new JDialog(this, "Account Created", true);
successDialog.setUndecorated(true);
successDialog.setSize(450, 550);
successDialog.setLocationRelativeTo(this);
successDialog.setBackground(new Color(0, 0, 0, 0));

final Point[] dragPt = {null};

// Main glass panel
JPanel glassPanel = new JPanel() {
@Override
protected void paintComponent(Graphics g) {
Graphics2D g2d = (Graphics2D) g.create();
g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

// Dark glass background with blur effect
g2d.setColor(new Color(0, 0, 0, 240));
g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);

// Glass border glow
g2d.setColor(new Color(100, 255, 150, 180));
g2d.setStroke(new BasicStroke(2.5f));
g2d.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 28, 28);

// Inner glow border
g2d.setColor(new Color(100, 255, 150, 80));
g2d.setStroke(new BasicStroke(1.5f));
g2d.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 26, 26);

// Glass reflection effect
g2d.setColor(new Color(255, 255, 255, 30));
g2d.fillRoundRect(5, 5, getWidth() - 10, 80, 25, 25);

// Subtle gradient overlay
java.awt.GradientPaint gradient = new java.awt.GradientPaint(
0, 0, new Color(100, 255, 150, 20),
0, getHeight(), new Color(100, 255, 150, 5)
);
g2d.setPaint(gradient);
g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);

g2d.dispose();
}
};
glassPanel.setOpaque(false);
glassPanel.setLayout(new BorderLayout());

// Make dialog draggable
glassPanel.addMouseListener(new MouseAdapter() {
@Override
public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
});
glassPanel.addMouseMotionListener(new MouseMotionAdapter() {
@Override
public void mouseDragged(MouseEvent e) {
Point cur = e.getLocationOnScreen();
successDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
}
});

// Header with close button
JPanel headerPanel = new JPanel(new BorderLayout());
headerPanel.setOpaque(false);
headerPanel.setBorder(new EmptyBorder(20, 20, 10, 20));

JLabel titleLabel = new JLabel("✓ Account Created Successfully!");
titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
titleLabel.setForeground(new Color(100, 255, 150));
titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

JButton closeBtn = new JButton("✕") {
@Override
protected void paintComponent(Graphics g) {
Graphics2D g2d = (Graphics2D) g.create();
g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
if (getModel().isRollover()) {
g2d.setColor(new Color(180, 60, 60, 200));
g2d.fillOval(0, 0, 28, 28);
} else {
g2d.setColor(new Color(100, 255, 150, 100));
g2d.fillOval(0, 0, 28, 28);
}
g2d.setColor(Color.WHITE);
g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
FontMetrics fm = g2d.getFontMetrics();
g2d.drawString("✕", (28 - fm.stringWidth("✕")) / 2, ((28 - fm.getHeight()) / 2) + fm.getAscent());
g2d.dispose();
}
};
closeBtn.setPreferredSize(new Dimension(28, 28));
closeBtn.setOpaque(false);
closeBtn.setContentAreaFilled(false);
closeBtn.setBorderPainted(false);
closeBtn.setFocusPainted(false);
closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
closeBtn.addActionListener(e -> {
    successDialog.dispose();
    reenableLoginComponents();
    // Don't auto-login
});

headerPanel.add(titleLabel, BorderLayout.CENTER);
headerPanel.add(closeBtn, BorderLayout.EAST);
glassPanel.add(headerPanel, BorderLayout.NORTH);

// Content panel
JPanel contentPanel = new JPanel();
contentPanel.setOpaque(false);
contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
contentPanel.setBorder(new EmptyBorder(10, 40, 20, 40));

// Animated checkmark icon
JPanel iconPanel = new JPanel() {
@Override
protected void paintComponent(Graphics g) {
Graphics2D g2d = (Graphics2D) g.create();
g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

// Outer circle with pulse effect
g2d.setColor(new Color(100, 255, 150, 180));
g2d.setStroke(new BasicStroke(3));
g2d.drawOval(5, 5, 70, 70);

// Inner circle
g2d.setColor(new Color(100, 255, 150, 60));
g2d.fillOval(8, 8, 64, 64);

// Checkmark
g2d.setColor(new Color(100, 255, 150));
g2d.setStroke(new BasicStroke(4));
g2d.drawLine(25, 42, 38, 55);
g2d.drawLine(38, 55, 58, 32);

g2d.dispose();
}
};
iconPanel.setOpaque(false);
iconPanel.setPreferredSize(new Dimension(80, 80));
iconPanel.setMaximumSize(new Dimension(80, 80));
iconPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
contentPanel.add(iconPanel);
contentPanel.add(Box.createVerticalStrut(20));

// Welcome message
JLabel welcomeLabel = new JLabel("Welcome, " + googleName + "!");
welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
welcomeLabel.setForeground(new Color(220, 220, 255));
welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
contentPanel.add(welcomeLabel);
contentPanel.add(Box.createVerticalStrut(10));

JLabel successLabel = new JLabel("Your admin account has been created");
successLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
successLabel.setForeground(new Color(180, 180, 220));
successLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
contentPanel.add(successLabel);
contentPanel.add(Box.createVerticalStrut(25));

// Account details panel
JPanel detailsPanel = new JPanel() {
@Override
protected void paintComponent(Graphics g) {
Graphics2D g2d = (Graphics2D) g.create();
g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
g2d.setColor(new Color(20, 20, 40, 180));
g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
g2d.setColor(new Color(100, 255, 150, 80));
g2d.setStroke(new BasicStroke(1));
g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
g2d.dispose();
}
};
detailsPanel.setOpaque(false);
detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
detailsPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
detailsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
detailsPanel.setMaximumSize(new Dimension(350, 200));

// Account details with glassmorphism styling
addDetailRow(detailsPanel, "📧 Email:", email);
addDetailRow(detailsPanel, "👤 Username:", username);
addDetailRow(detailsPanel, "⭐ Role:", role);
addDetailRow(detailsPanel, "🏪 Business:", businessName);

contentPanel.add(detailsPanel);
contentPanel.add(Box.createVerticalStrut(25));

// Info message
JLabel infoLabel = new JLabel("You now have full admin privileges");
infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
infoLabel.setForeground(new Color(100, 255, 150, 200));
infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
contentPanel.add(infoLabel);
contentPanel.add(Box.createVerticalStrut(20));

// Continue button
JButton continueBtn = createGlassButton("Continue to Dashboard", accentGreen);
continueBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
continueBtn.setMaximumSize(new Dimension(250, 45));
continueBtn.addActionListener(e -> {
    successDialog.dispose();
    performEmailLogin(email, username);
    // The performEmailLogin will dispose the login window, so no need to re-enable here
});
contentPanel.add(continueBtn);

JScrollPane scrollPane = new JScrollPane(contentPanel);
scrollPane.setOpaque(false);
scrollPane.getViewport().setOpaque(false);
scrollPane.setBorder(null);
scrollPane.getVerticalScrollBar().setUnitIncrement(12);
glassPanel.add(scrollPane, BorderLayout.CENTER);

successDialog.add(glassPanel);
successDialog.setVisible(true);

// Auto-close timer (optional - 5 seconds)
Timer autoCloseTimer = new Timer(5000, e -> {
if (successDialog.isVisible()) {
successDialog.dispose();
performEmailLogin(email, username);
}
});
autoCloseTimer.setRepeats(false);
autoCloseTimer.start();
}

private void addDetailRow(JPanel panel, String label, String value) {
JPanel rowPanel = new JPanel(new BorderLayout());
rowPanel.setOpaque(false);
rowPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

JLabel labelLabel = new JLabel(label);
labelLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
labelLabel.setForeground(new Color(100, 255, 150));

JLabel valueLabel = new JLabel(value);
valueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
valueLabel.setForeground(new Color(220, 220, 255));

rowPanel.add(labelLabel, BorderLayout.WEST);
rowPanel.add(valueLabel, BorderLayout.EAST);

panel.add(rowPanel);
}
    

    private void createLocalAdminAccount(String email, String username, String password, String businessName, 
                                          String role, String question1, String answer1, String question2, String answer2, String googleName) {
        try {
            PharmacyInfoUtil.savePharmacyName(businessName);
            Database.setCurrentUser(email, username);
            Database.initializeUserDatabase(email, username);
            this.currentUserEmail = email;
            this.currentUsername = username;
            String cid = Database.getClientIdFromCloud(email);
            if (cid != null && !cid.isEmpty()) {
                this.currentClientId = cid;
            }
            
            LocalUserStore store = LocalUserStore.getInstance();
            store.registerUser(email, username, LocalUserStore.Role.valueOf(role), password);
            
            saveEmailToHistory(email);
            
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(Pharmacy_Login.this,
                    "✅ Local admin account created!\n\n" +
                    "Welcome, " + googleName + "!\n" +
                    "Email: " + email + "\n" +
                    "Username: " + username + "\n" +
                    "Role: " + role + "\n" +
                    "Business: " + businessName + "\n\n" +
                    "Data will sync to cloud when internet is available.",
                    "Local Account Created",
                    JOptionPane.INFORMATION_MESSAGE);
                
                performEmailLogin(email, username);
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Local account creation failed", e);
            showGlassMessage("Creation Failed", "Failed to create local account: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }
    private void updateExistingCloudUser(String email, String username, String newPassword,
            String businessName, String role,
            String question1, String answer1,
            String question2, String answer2,
            String googleName) {
        
        JDialog progressDialog = new JDialog(this, "Updating Account", true);
        progressDialog.setUndecorated(true);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(this);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(new Color(0, 0, 0, 200));
        progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel progressLabel = new JLabel("Updating your account...", SwingConstants.CENTER);
        progressLabel.setForeground(textLight);
        progressPanel.add(progressLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressPanel.add(progressBar, BorderLayout.SOUTH);
        progressDialog.add(progressPanel);

        new Thread(() -> {
            try {
                String hashedA1 = hashAnswer(answer1.trim().toLowerCase());
                String hashedA2 = hashAnswer(answer2.trim().toLowerCase());

                // Build SQL manually
                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE users SET ");
                sql.append("username = '").append(username.replace("'", "''")).append("', ");
                sql.append("privileges = '").append(role).append("', ");
                sql.append("business_name = '").append(businessName.replace("'", "''")).append("', ");
                sql.append("security_question1 = '").append(question1.replace("'", "''")).append("', ");
                sql.append("security_answer1 = '").append(hashedA1).append("', ");
                sql.append("security_question2 = '").append(question2.replace("'", "''")).append("', ");
                sql.append("security_answer2 = '").append(hashedA2).append("'");
                
                if (newPassword != null && !newPassword.isEmpty()) {
                    String hashedPassword = hashPassword(newPassword);
                    sql.append(", password = '").append(hashedPassword).append("'");
                    sql.append(", password_last_changed = datetime('now')");
                }
                
                sql.append(" WHERE email = '").append(email).append("'");
                
                String finalSql = sql.toString();
                LOGGER.info("Executing SQL: " + finalSql);
                
                // CHANGED: Use executeUpdateDirect instead of execute
                SQLiteCloudClient.executeUpdateDirect(finalSql);
                
                LOGGER.info("Update completed successfully");

                // Update local data regardless of cloud success
                PharmacyInfoUtil.savePharmacyName(businessName);
                Database.setCurrentUser(email, username);
                Database.initializeUserDatabase(email, username);

                LocalUserStore store = LocalUserStore.getInstance();
                if (store.getUser(email) == null) {
                    store.registerUser(email, username, LocalUserStore.Role.valueOf(role),
                        newPassword != null && !newPassword.isEmpty() ? newPassword : "");
                } else if (newPassword != null && !newPassword.isEmpty()) {
                    store.updateUserPassword(email, newPassword);
                }
                saveEmailToHistory(email);

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(Pharmacy_Login.this,
                        "✅ Account updated!\n\nWelcome back, " + googleName + "!\nEmail: " + email,
                        "Account Updated", JOptionPane.INFORMATION_MESSAGE);
                    performEmailLogin(email, username);
                });

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Account update failed", e);
                
                // CHANGED: Still update locally even if cloud fails
                try {
                    // Update local data only
                    PharmacyInfoUtil.savePharmacyName(businessName);
                    Database.setCurrentUser(email, username);
                    Database.initializeUserDatabase(email, username);

                    LocalUserStore store = LocalUserStore.getInstance();
                    if (store.getUser(email) == null) {
                        store.registerUser(email, username, LocalUserStore.Role.valueOf(role),
                            newPassword != null && !newPassword.isEmpty() ? newPassword : "");
                    } else if (newPassword != null && !newPassword.isEmpty()) {
                        store.updateUserPassword(email, newPassword);
                    }
                    saveEmailToHistory(email);
                    
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(Pharmacy_Login.this,
                            "⚠️ Account updated locally only!\n\nCloud sync failed, but your local data has been updated.\n\nWelcome back, " + googleName + "!\nEmail: " + email,
                            "Local Update Only", JOptionPane.WARNING_MESSAGE);
                        performEmailLogin(email, username);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        showGlassMessage("Update Failed", "Failed to update account: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
                    });
                }
            }
        }).start();

        progressDialog.setVisible(true);
    }
    
    private boolean isInternetAvailable() {
        try {
            try {
                java.net.URL url = new java.net.URL("https://www.google.com");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestMethod("HEAD");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 400) {
                    return true;
                }
            } catch (Exception e) {}
            
            try {
                java.net.InetAddress address = java.net.InetAddress.getByName("8.8.8.8");
                return address.isReachable(3000);
            } catch (Exception e) {}
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== USER PRIVILEGES METHODS WITH TABLE VIEW ====================
    
    private void showUserPrivilegesDialog() {
    loadSavedEmails();
    if (savedEmails.isEmpty()) {
        showGlassMessage("No Users", "No user accounts found. Please sign up first.", JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    JDialog privilegesDialog = new JDialog(this, "User Privileges", true);
    privilegesDialog.setUndecorated(true);
    privilegesDialog.setSize(550, 480);
    privilegesDialog.setLocationRelativeTo(this);
    privilegesDialog.setBackground(new Color(0, 0, 0, 0));

    final Point[] dragPt = {null};
    JPanel mainPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0, 0, 0, 245));
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2d.setColor(new Color(140, 100, 255, 90));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
            g2d.dispose();
        }
    };
    mainPanel.setOpaque(false);
    mainPanel.setLayout(new BorderLayout());
    
    mainPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
    });
    mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
            Point cur = e.getLocationOnScreen();
            privilegesDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
        }
    });

    // Header with close button
    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setOpaque(false);
    headerPanel.setBorder(new EmptyBorder(15, 20, 10, 15));
    
    JLabel titleLabel = new JLabel("User Privileges Settings");
    titleLabel.setFont(new Font("Serif", Font.BOLD, 18));
    titleLabel.setForeground(accentBlue);
    
    JButton closeBtn = new JButton("✕") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(getModel().isRollover() ? new Color(180, 60, 60, 180) : new Color(140, 100, 255, 80));
            g2d.fillOval(0, 0, 24, 24);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString("✕", (24 - fm.stringWidth("✕")) / 2, ((24 - fm.getHeight()) / 2) + fm.getAscent());
            g2d.dispose();
        }
    };
    closeBtn.setPreferredSize(new Dimension(24, 24));
    closeBtn.setOpaque(false);
    closeBtn.setContentAreaFilled(false);
    closeBtn.setBorderPainted(false);
    closeBtn.setFocusPainted(false);
    closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    closeBtn.addActionListener(e -> privilegesDialog.dispose());
    
    headerPanel.add(titleLabel, BorderLayout.WEST);
    headerPanel.add(closeBtn, BorderLayout.EAST);
    mainPanel.add(headerPanel, BorderLayout.NORTH);

    // Create table model for users - current user first
    String[] columnNames = {"Email", "Username", "Role", "Status"};
    List<Object[]> userRows = new ArrayList<>();
    
    // Get current user first (should be Owner/CEO)
    String currentEmail = currentUserEmail;
    String currentStatus = getCurrentUserStatus(currentEmail);
    userRows.add(new Object[]{currentEmail, currentUsername, "OWNER", currentStatus});
    
    // Add other users (excluding current)
    for (String email : savedEmails) {
        if (!email.equals(currentEmail)) {
            LocalUserStore.LocalUser user = LocalUserStore.getInstance().getUser(email);
            String role = user != null ? user.role.name() : "USER";
            String status = getUserStatus(email);
            userRows.add(new Object[]{email, user != null ? user.username : "Unknown", role, status});
        }
    }
    
    Object[][] data = userRows.toArray(new Object[0][]);
    DefaultTableModel tableModel = new DefaultTableModel(data, columnNames) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    
    JTable userTable = new JTable(tableModel);
    userTable.setBackground(new Color(10, 10, 20, 200));
    userTable.setForeground(textLight);
    userTable.setSelectionBackground(new Color(140, 100, 255, 100));
    userTable.setSelectionForeground(Color.WHITE);
    userTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    userTable.setRowHeight(30);
    userTable.getTableHeader().setBackground(new Color(20, 20, 35));
    userTable.getTableHeader().setForeground(accentBlue);
    userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    
    // Custom renderer for status column
    userTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = (String) value;
            if (status.startsWith("Online")) {
                c.setForeground(new Color(100, 255, 100));
            } else if (status.startsWith("Last")) {
                c.setForeground(new Color(255, 200, 100));
            } else {
                c.setForeground(new Color(150, 150, 150));
            }
            return c;
        }
    });
    
    JScrollPane tableScrollPane = new JScrollPane(userTable);
    tableScrollPane.setOpaque(false);
    tableScrollPane.getViewport().setOpaque(false);
    tableScrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 60, 255, 80), 1));
    
    // Button panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
    buttonPanel.setOpaque(false);
    
    JButton editUserBtn = createGlassButton("Edit Selected", accentBlue);
    JButton loginBtn = createGlassButton("Login", accentGreen);
    JButton removeBtn = createGlassButton("Remove", accentRed);
    
    buttonPanel.add(editUserBtn);
    buttonPanel.add(loginBtn);
    buttonPanel.add(removeBtn);
    
    // Main content panel
    JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
    contentPanel.setOpaque(false);
    contentPanel.setBorder(new EmptyBorder(10, 20, 10, 20));
    contentPanel.add(tableScrollPane, BorderLayout.CENTER);
    contentPanel.add(buttonPanel, BorderLayout.SOUTH);
    
    // Login button action
    loginBtn.addActionListener(e -> {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            String selectedEmail = (String) tableModel.getValueAt(selectedRow, 0);
            username.setText(selectedEmail);
            privilegesDialog.dispose();
            showGlassMessage("Email Selected", "Email '" + selectedEmail + "' has been added to login field.", JOptionPane.INFORMATION_MESSAGE);
        } else {
            showGlassMessage("No Selection", "Please select a user from the table first.", JOptionPane.WARNING_MESSAGE);
        }
    });
    
    editUserBtn.addActionListener(e -> {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            String selectedEmail = (String) tableModel.getValueAt(selectedRow, 0);
            // Only allow editing if not current user or if current user is Owner/CEO
            if (selectedEmail.equals(currentEmail)) {
                showGlassMessage("Cannot Edit", "You cannot edit your own account from here.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            showAuthorizationPrompt(selectedEmail, privilegesDialog);
        } else {
            showGlassMessage("No Selection", "Please select a user to edit.", JOptionPane.WARNING_MESSAGE);
        }
    });
    
    removeBtn.addActionListener(e -> {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            String selectedEmail = (String) tableModel.getValueAt(selectedRow, 0);
            if (selectedEmail.equals(currentEmail)) {
                showGlassMessage("Cannot Remove", "You cannot remove your own account.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(privilegesDialog, 
                "Are you sure you want to remove user: " + selectedEmail + "?\nThis action cannot be undone!",
                "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                removeUserAccount(selectedEmail);
                privilegesDialog.dispose();
                showGlassMessage("User Removed", "User account has been removed successfully.", JOptionPane.INFORMATION_MESSAGE);
                showUserPrivilegesDialog(); // Refresh
            }
        } else {
            showGlassMessage("No Selection", "Please select a user to remove.", JOptionPane.WARNING_MESSAGE);
        }
    });
    
    mainPanel.add(contentPanel, BorderLayout.CENTER);
    privilegesDialog.add(mainPanel);
    privilegesDialog.setVisible(true);
}

private String getCurrentUserStatus(String email) {
    // Check if this is the currently logged in user
    if (email.equals(currentUserEmail)) {
        return "Online (Current)";
    }
    return getUserStatus(email);
}

private String getUserStatus(String email) {
    // Check if user has an active session
    if (activeSessions.containsKey(email) && !activeSessions.get(email).isExpired()) {
        return "Online";
    }
    // Try to get last login time from database
    try {
        List<JsonObject> result = SQLiteCloudClient.execute(
            "SELECT last_login FROM users WHERE email = ?", email);
        if (!result.isEmpty() && result.get(0).has("last_login") && 
            !result.get(0).get("last_login").isJsonNull()) {
            String lastLogin = result.get(0).get("last_login").getAsString();
            // Format the time nicely
            if (lastLogin.contains(" ")) {
                String timePart = lastLogin.split(" ")[1];
                if (timePart.length() > 5) {
                    timePart = timePart.substring(0, 5);
                }
                return "Last Login: " + timePart;
            }
            return "Last Login: " + lastLogin;
        }
    } catch (IOException e) {
        LOGGER.log(Level.FINE, "Could not get last login", e);
    }
    return "Inactive";
}

    

private int getRoleCountFromCloud(String role) {
    try {
        List<JsonObject> result = SQLiteCloudClient.execute(
            "SELECT COUNT(*) as count FROM users WHERE privileges = ? AND client_id = ?", 
            role, currentClientId);
        if (!result.isEmpty() && result.get(0).has("count")) {
            return result.get(0).get("count").getAsInt();
        }
    } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Could not get role count", e);
    }
    return 0;
}
private void updateLinkedCloudUser(String email, String username, String newPassword,
        String role, String question1, String answer1, String question2, String answer2) {
    
    JDialog progressDialog = new JDialog(this, "Updating Linked Account", true);
    progressDialog.setUndecorated(true);
    progressDialog.setSize(350, 120);
    progressDialog.setLocationRelativeTo(this);

    JPanel progressPanel = new JPanel(new BorderLayout());
    progressPanel.setBackground(new Color(0, 0, 0, 200));
    progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

    JLabel progressLabel = new JLabel("Updating linked account...", SwingConstants.CENTER);
    progressLabel.setForeground(textLight);
    progressPanel.add(progressLabel, BorderLayout.CENTER);

    JProgressBar progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressPanel.add(progressBar, BorderLayout.SOUTH);
    progressDialog.add(progressPanel);

    new Thread(() -> {
        try {
            String hashedA1 = hashAnswer(answer1.trim().toLowerCase());
            String hashedA2 = hashAnswer(answer2.trim().toLowerCase());

            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE users SET ");
            sql.append("username = '").append(username.replace("'", "''")).append("', ");
            sql.append("privileges = '").append(role).append("', ");
            sql.append("security_question1 = '").append(question1.replace("'", "''")).append("', ");
            sql.append("security_answer1 = '").append(hashedA1).append("', ");
            sql.append("security_question2 = '").append(question2.replace("'", "''")).append("', ");
            sql.append("security_answer2 = '").append(hashedA2).append("'");
            
            if (newPassword != null && !newPassword.isEmpty()) {
                String hashedPassword = hashPassword(newPassword);
                sql.append(", password = '").append(hashedPassword).append("'");
                sql.append(", password_last_changed = datetime('now')");
            }
            
            sql.append(" WHERE email = '").append(email).append("'");
            
            SQLiteCloudClient.executeUpdateDirect(sql.toString());

            // Update local store
            LocalUserStore store = LocalUserStore.getInstance();
            LocalUserStore.Role localRole = convertRoleToLocalUserRole(role);
            if (store.getUser(email) == null) {
                store.registerUser(email, username, localRole, 
                    newPassword != null && !newPassword.isEmpty() ? newPassword : "");
            } else if (newPassword != null && !newPassword.isEmpty()) {
                store.updateUserPassword(email, newPassword);
            }
            
            // Update saved emails list
            if (!savedEmails.contains(email)) {
                savedEmails.add(email);
                saveEmailsToFile();
            }

            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                showGlassMessage("Success", "Linked account updated successfully!", JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Linked account update failed", e);
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                showGlassMessage("Update Failed", "Failed to update linked account: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            });
        }
    }).start();

    progressDialog.setVisible(true);
}
public void createLinkedUser(String email, String username, String password, String role,
        String question1, String answer1, String question2, String answer2) {
    
    JDialog progressDialog = new JDialog(this, "Creating Linked Account", true);
    progressDialog.setUndecorated(true);
    progressDialog.setSize(350, 120);
    progressDialog.setLocationRelativeTo(this);

    JPanel progressPanel = new JPanel(new BorderLayout());
    progressPanel.setBackground(new Color(0, 0, 0, 200));
    progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

    JLabel progressLabel = new JLabel("Creating linked account...", SwingConstants.CENTER);
    progressLabel.setForeground(textLight);
    progressPanel.add(progressLabel, BorderLayout.CENTER);

    JProgressBar progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressPanel.add(progressBar, BorderLayout.SOUTH);
    progressDialog.add(progressPanel);

    new Thread(() -> {
        try {
            boolean connected = Database.checkTursoConnection();
            
            // Hash the password using the same method as login
            String hashedPassword = hashPassword(password);
            String hashedAnswer1 = hashAnswer(answer1.trim().toLowerCase());
            String hashedAnswer2 = hashAnswer(answer2.trim().toLowerCase());
            
            // Use the SAME clientId as the current user (owner)
            String clientId = (currentClientId != null && !currentClientId.isEmpty())
                ? currentClientId
                : Database.getCurrentClientId();
            if (clientId == null || clientId.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    showGlassMessage("Error", "Cannot create linked user: owner clientId not loaded. Please re-login.", JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            String businessName = PharmacyInfoUtil.getPharmacyName();
            
            // ========== STEP 1: Insert into cloud SQLiteCloud ==========
            try {
                SQLiteCloudClient.executeUpdate(
                    "INSERT INTO users (email, username, password, privileges, business_name, client_id, created_date, " +
                    "password_last_changed, account_status, email_verified, " +
                    "security_question1, security_answer1, security_question2, security_answer2) " +
                    "VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'), ?, ?, ?, ?, ?, ?)",
                    email, username, hashedPassword, role, businessName, clientId, "ACTIVE", 1,
                    question1, hashedAnswer1, question2, hashedAnswer2);
                LOGGER.info("Linked user inserted into cloud: " + email);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Cloud insert failed, will sync later", ex);
            }
            
            // ========== STEP 2: Insert into local SQLite database ==========
            try (Connection conn = Database.getConnection();
                 PreparedStatement pst = conn.prepareStatement(
                     "INSERT OR REPLACE INTO users (email, username, password, privileges, business_name, client_id, created_date, " +
                     "password_last_changed, account_status, security_question1, security_answer1, security_question2, security_answer2) " +
                     "VALUES (?, ?, ?, ?, ?, ?, datetime('now','localtime'), datetime('now','localtime'), 'ACTIVE', ?, ?, ?, ?)")) {
                pst.setString(1, email);
                pst.setString(2, username);
                pst.setString(3, hashedPassword);
                pst.setString(4, role);
                pst.setString(5, businessName);
                pst.setString(6, clientId);
                pst.setString(7, question1);
                pst.setString(8, hashedAnswer1);
                pst.setString(9, question2);
                pst.setString(10, hashedAnswer2);
                pst.executeUpdate();
                LOGGER.info("Linked user inserted into local SQLite: " + email);
            }
            
            // ========== STEP 3: CRITICAL - Register in LocalUserStore for login ==========
            LocalUserStore store = LocalUserStore.getInstance();
            LocalUserStore.Role localRole = convertRoleToLocalUserRole(role);
            
            // This is the key line that was missing!
            LocalUserStore.LocalUser newUser = store.registerUser(email, username, localRole, password);
            
            if (newUser != null) {
                LOGGER.info("Linked user registered in LocalUserStore: " + email);
            } else {
                // Fallback: manually create and add
                LocalUserStore.LocalUser fallbackUser = new LocalUserStore.LocalUser(email, username, localRole, true, password);
                store.addUser(fallbackUser);
                LOGGER.info("Linked user manually added to LocalUserStore: " + email);
            }
            
            // ========== STEP 4: Add to saved emails list ==========
            if (!savedEmails.contains(email)) {
                savedEmails.add(email);
                saveEmailsToFile();
            }
            
            // ========== STEP 5: Force LocalUserStore to save ==========
            store.saveUsers();
            
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                showGlassMessage("Success", 
                    "✅ Linked account created successfully!\n\n" +
                    "Email: " + email + "\n" +
                    "Username: " + username + "\n" +
                    "Role: " + role + "\n\n" +
                    "This user can now login immediately.",
                    JOptionPane.INFORMATION_MESSAGE);
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Linked account creation failed", e);
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                showGlassMessage("Creation Failed", "Failed to create linked account: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            });
        }
    }).start();

    progressDialog.setVisible(true);
}
    private void showAuthorizationPrompt(String selectedEmail, JDialog parentDialog) {
        JDialog authDialog = new JDialog(parentDialog, "Authorized Account", true);
        authDialog.setUndecorated(true);
        authDialog.setSize(400, 260);
        authDialog.setLocationRelativeTo(parentDialog);
        authDialog.setBackground(new Color(0, 0, 0, 0));

        final Point[] dragPt = {null};

        JPanel mainPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(0, 0, 0, 248));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setColor(new Color(140, 100, 255, 90));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout());

        mainPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point cur = e.getLocationOnScreen();
                authDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
            }
        });

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 20, 8, 15));

        JLabel titleLabel = new JLabel("Authorized Account");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 17));
        titleLabel.setForeground(accentBlue);

        JButton closeBtn = new JButton("✕") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getModel().isRollover() ? new Color(180, 60, 60, 180) : new Color(140, 100, 255, 80));
                g2d.fillOval(0, 0, 24, 24);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕", (24 - fm.stringWidth("✕")) / 2, ((24 - fm.getHeight()) / 2) + fm.getAscent());
                g2d.dispose();
            }
        };
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.setOpaque(false); closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false); closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> authDialog.dispose());
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(closeBtn, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(5, 25, 20, 25));

        // Email row
        JLabel emailTitleLabel = new JLabel("Email");
        emailTitleLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        emailTitleLabel.setForeground(textMuted);
        emailTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(emailTitleLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JLabel emailValueLabel = new JLabel(selectedEmail);
        emailValueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        emailValueLabel.setForeground(accentBlue);
        emailValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(emailValueLabel);
        contentPanel.add(Box.createVerticalStrut(16));

        // Password row
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        passLabel.setForeground(textMuted);
        passLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(passLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        JPasswordField authPassField = createGlassPasswordField();
        authPassField.setMaximumSize(new Dimension(360, 34));
        authPassField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(authPassField);
        contentPanel.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);

        JButton confirmBtn = createGlassButton("Confirm", accentGreen);
        JButton cancelBtn = createGlassButton("Cancel", accentRed);
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        contentPanel.add(btnPanel);

        cancelBtn.addActionListener(e -> authDialog.dispose());

        confirmBtn.addActionListener(e -> {
            String enteredPassword = new String(authPassField.getPassword()).trim();
            if (enteredPassword.isEmpty()) {
                showGlassMessage("Missing Password", "Please enter the account password.", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Verify password against local store first, then cloud
            boolean verified = false;
            LocalUserStore.LocalUser localUser = LocalUserStore.getInstance().authenticate(selectedEmail, enteredPassword);
            if (localUser != null) {
                verified = true;
            } else {
                try {
                    List<JsonObject> users = SQLiteCloudClient.execute(
                        "SELECT password FROM users WHERE email = ?", selectedEmail);
                    if (!users.isEmpty()) {
                        String storedHash = users.get(0).get("password").getAsString();
                        verified = verifyPassword(enteredPassword, storedHash);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Cloud password verify failed", ex);
                }
            }

            if (!verified) {
                showGlassMessage("Incorrect Password", "The password entered is incorrect.", JOptionPane.ERROR_MESSAGE);
                return;
            }

            authDialog.dispose();
            showFullEditUserDialog(selectedEmail, parentDialog);
        });

        // Allow Enter key to confirm
        authPassField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) confirmBtn.doClick();
            }
        });

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        authDialog.add(mainPanel);
        authDialog.setVisible(true);
    }
    private void showFullEditUserDialog(String email, JDialog parentDialog) {
        // Load cloud data for pre-filling
        JsonObject[] cloudData = {null};
try {
    List<JsonObject> result = SQLiteCloudClient.execute(
        "SELECT email, username, privileges, business_name, " +
        "security_question1, security_question2 FROM users WHERE email = ?", email);
    if (!result.isEmpty()) cloudData[0] = result.get(0);
} catch (Exception ex) {
    LOGGER.log(Level.WARNING, "Could not load user data from cloud for editing", ex);
}
        JsonObject existingData = cloudData[0];

        java.util.function.BiFunction<String, String, String> getField = (key, fallback) -> {
            if (existingData != null && existingData.has(key) && !existingData.get(key).isJsonNull())
                return existingData.get(key).getAsString();
            return fallback;
        };

        JDialog editDialog = new JDialog(parentDialog, "Edit User Privileges", true);
        editDialog.setUndecorated(true);
        editDialog.setSize(500, 760);
        editDialog.setLocationRelativeTo(parentDialog);
        editDialog.setBackground(new Color(0, 0, 0, 0));

        final Point[] dragPt = {null};

        JPanel mainPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(0, 0, 0, 248));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setColor(new Color(140, 100, 255, 90));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout());

        mainPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
        });
        mainPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point cur = e.getLocationOnScreen();
                editDialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
            }
        });

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(15, 20, 8, 15));

        JLabel titleLabel = new JLabel("User Privileges Settings");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 18));
        titleLabel.setForeground(accentBlue);

        JButton closeBtn = new JButton("✕") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getModel().isRollover() ? new Color(180, 60, 60, 180) : new Color(140, 100, 255, 80));
                g2d.fillOval(0, 0, 24, 24);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕", (24 - fm.stringWidth("✕")) / 2, ((24 - fm.getHeight()) / 2) + fm.getAscent());
                g2d.dispose();
            }
        };
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.setOpaque(false); closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false); closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> editDialog.dispose());

        JLabel subtitleLabel = new JLabel("Editing: " + email);
        subtitleLabel.setFont(new Font("Dialog", Font.ITALIC, 11));
        subtitleLabel.setForeground(accentYellow);

        JPanel titleBlock = new JPanel(new BorderLayout());
        titleBlock.setOpaque(false);
        titleBlock.add(titleLabel, BorderLayout.CENTER);
        titleBlock.add(closeBtn, BorderLayout.EAST);
        headerPanel.add(titleBlock, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content
        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 28, 20, 28));

        // Email (read-only)
        addFormLabel(contentPanel, "Email:");
        JTextField emailField = createGlassTextField();
        emailField.setText(email);
        emailField.setEditable(false);
        emailField.setMaximumSize(new Dimension(430, 32));
        emailField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(emailField);
        contentPanel.add(Box.createVerticalStrut(13));

        // Username
        addFormLabel(contentPanel, "Username:");
        JTextField usernameField = createGlassTextField();
        usernameField.setText(getField.apply("username", ""));
        usernameField.setMaximumSize(new Dimension(430, 32));
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(usernameField);
        contentPanel.add(Box.createVerticalStrut(13));

        // New Password
        addFormLabel(contentPanel, "New Password (leave blank to keep current):");
        JPasswordField passField = createGlassPasswordField();
        passField.setMaximumSize(new Dimension(430, 32));
        passField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(passField);
        contentPanel.add(Box.createVerticalStrut(8));

        // Confirm Password
        addFormLabel(contentPanel, "Confirm New Password:");
        JPasswordField confirmPassField = createGlassPasswordField();
        confirmPassField.setMaximumSize(new Dimension(430, 32));
        confirmPassField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(confirmPassField);
        contentPanel.add(Box.createVerticalStrut(13));

        // Role
        addFormLabel(contentPanel, "Role:");
        String[] roles = {"SUPERADMIN", "ADMIN", "MANAGER", "CASHIER"};
        JComboBox<String> roleCombo = new JComboBox<>(roles);
        String savedRole = getField.apply("privileges", "ADMIN");
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equalsIgnoreCase(savedRole)) { roleCombo.setSelectedIndex(i); break; }
        }
        roleCombo.setMaximumSize(new Dimension(430, 32));
        roleCombo.setFont(new Font("Dialog", Font.PLAIN, 12));
        roleCombo.setBackground(new Color(10, 10, 20));
        roleCombo.setForeground(textLight);
        roleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(roleCombo);
        contentPanel.add(Box.createVerticalStrut(13));

        // Business Name
        addFormLabel(contentPanel, "Business Name:");
        JTextField businessField = createGlassTextField();
        businessField.setText(getField.apply("business_name", ""));
        businessField.setMaximumSize(new Dimension(430, 32));
        businessField.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(businessField);
        contentPanel.add(Box.createVerticalStrut(13));

        // Security Questions
        JLabel secLabel = new JLabel("Security Questions:");
        secLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        secLabel.setForeground(accentYellow);
        secLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(secLabel);
        contentPanel.add(Box.createVerticalStrut(8));

        String[] predefinedQuestions = {
            "What was the name of your first pet?",
            "What is your mother's maiden name?",
            "What city were you born in?",
            "What was the name of your primary school?",
            "What is your oldest sibling's middle name?",
            "What street did you grow up on?",
            "What was the make of your first car?",
            "What is your favourite movie of all time?"
        };

        JComboBox<String> q1Combo = new JComboBox<>(predefinedQuestions);
        String savedQ1 = getField.apply("security_question1", predefinedQuestions[0]);
        for (int i = 0; i < predefinedQuestions.length; i++) {
            if (predefinedQuestions[i].equals(savedQ1)) { q1Combo.setSelectedIndex(i); break; }
        }
        q1Combo.setMaximumSize(new Dimension(430, 32));
        q1Combo.setFont(new Font("Dialog", Font.PLAIN, 11));
        q1Combo.setBackground(new Color(10, 10, 20));
        q1Combo.setForeground(textLight);
        q1Combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(q1Combo);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField answer1Field = createGlassTextField();
        answer1Field.setMaximumSize(new Dimension(430, 32));
        answer1Field.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(answer1Field);
        contentPanel.add(Box.createVerticalStrut(10));

        JComboBox<String> q2Combo = new JComboBox<>(predefinedQuestions);
        String savedQ2 = getField.apply("security_question2", predefinedQuestions[1]);
        for (int i = 0; i < predefinedQuestions.length; i++) {
            if (predefinedQuestions[i].equals(savedQ2)) { q2Combo.setSelectedIndex(i); break; }
        }
        q2Combo.setMaximumSize(new Dimension(430, 32));
        q2Combo.setFont(new Font("Dialog", Font.PLAIN, 11));
        q2Combo.setBackground(new Color(10, 10, 20));
        q2Combo.setForeground(textLight);
        q2Combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(q2Combo);
        contentPanel.add(Box.createVerticalStrut(4));

        JTextField answer2Field = createGlassTextField();
        answer2Field.setMaximumSize(new Dimension(430, 32));
        answer2Field.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(answer2Field);
        contentPanel.add(Box.createVerticalStrut(15));

        // Auto-login checkboxes
        JCheckBox termsCheckBox = createGlassCheckBox("Accept terms and policy");
        termsCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(termsCheckBox);
        contentPanel.add(Box.createVerticalStrut(6));

        JCheckBox autoLoginCheckBox = createGlassCheckBox("Auto-login");
        autoLoginCheckBox.setEnabled(false);
        autoLoginCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(autoLoginCheckBox);
        contentPanel.add(Box.createVerticalStrut(20));

        passField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateAutoLogin(); }
            @Override public void removeUpdate(DocumentEvent e) { updateAutoLogin(); }
            @Override public void changedUpdate(DocumentEvent e) {}
            private void updateAutoLogin() {
                autoLoginCheckBox.setEnabled(termsCheckBox.isSelected() && passField.getPassword().length > 0);
            }
        });
        termsCheckBox.addActionListener(e ->
            autoLoginCheckBox.setEnabled(termsCheckBox.isSelected() && passField.getPassword().length > 0));

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        JButton removeUserBtn = createGlassButton("Remove User", accentRed);
        JButton applyBtn = createGlassButton("Apply Changes", accentGreen);
        buttonPanel.add(removeUserBtn);
        buttonPanel.add(applyBtn);
        contentPanel.add(buttonPanel);

        removeUserBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(editDialog,
                "Remove user: " + email + "?\nThis cannot be undone!",
                "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                removeUserAccount(email);
                editDialog.dispose();
                parentDialog.dispose();
                showGlassMessage("User Removed", "User account removed successfully.", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        applyBtn.addActionListener(e -> {
            String newUsername = usernameField.getText().trim();
            String newPassword = new String(passField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());
            String businessName = businessField.getText().trim();
            String role = (String) roleCombo.getSelectedItem();
            String q1 = (String) q1Combo.getSelectedItem();
            String a1 = answer1Field.getText().trim();
            String q2 = (String) q2Combo.getSelectedItem();
            String a2 = answer2Field.getText().trim();
            boolean autoLogin = autoLoginCheckBox.isSelected();

            if (newUsername.isEmpty()) {
                showGlassMessage("Missing Field", "Username cannot be empty.", JOptionPane.WARNING_MESSAGE); return;
            }
            if (businessName.isEmpty()) {
                showGlassMessage("Missing Field", "Business name cannot be empty.", JOptionPane.WARNING_MESSAGE); return;
            }
            if (!newPassword.isEmpty()) {
                if (newPassword.length() < 8) {
                    showGlassMessage("Weak Password", "Password must be at least 8 characters.", JOptionPane.WARNING_MESSAGE); return;
                }
                if (!newPassword.equals(confirmPass)) {
                    showGlassMessage("Password Mismatch", "Passwords do not match.", JOptionPane.WARNING_MESSAGE); return;
                }
            }
            if (a1.isEmpty() || a2.isEmpty()) {
                showGlassMessage("Missing Answers", "Please answer both security questions.", JOptionPane.WARNING_MESSAGE); return;
            }
            if (autoLogin && !termsCheckBox.isSelected()) {
                showGlassMessage("Terms Required", "Please accept terms for auto-login.", JOptionPane.WARNING_MESSAGE); return;
            }

            // Persist changes
            updateExistingCloudUser(email, newUsername,
                newPassword.isEmpty() ? null : newPassword,
                businessName, role, q1, a1, q2, a2, newUsername);

            if (autoLogin && !newPassword.isEmpty()) {
                saveAutoLoginPrefs(email, newPassword);
            } else {
                clearAutoLoginPrefs(email);
            }

            editDialog.dispose();
            parentDialog.dispose();

            if (autoLogin && !newPassword.isEmpty()) {
                username.setText(email);
                password.setText(newPassword);
                performSecureLogin();
            }
        });

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        editDialog.add(mainPanel);
        editDialog.setVisible(true);
    }

    // Helper to add consistent form labels
    private void addFormLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Dialog", Font.BOLD, 12));
        label.setForeground(textLight);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(4));
    }

    private void removeUserAccount(String email) {
        savedEmails.remove(email);
        saveEmailsToFile();
        
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        File prefsFile = new File(USER_PREFS_DIR, safeEmail + ".properties");
        if (prefsFile.exists()) {
            prefsFile.delete();
        }
        
        LocalUserStore.getInstance().removeUser(email);
        LOGGER.info("User removed: " + email);
    }

    private void updateLocalPassword(String email, String newPassword) {
        try {
            LocalUserStore.getInstance().updateUserPassword(email, newPassword);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update local password", e);
        }
    }

    private void saveAutoLoginPrefs(String email, String password) {
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        java.util.Properties props = new java.util.Properties();
        props.setProperty("autoLogin", "true");
        props.setProperty("password", password);
        props.setProperty("timestamp", String.valueOf(System.currentTimeMillis()));
        
        File dir = new File(USER_PREFS_DIR);
        if (!dir.exists()) dir.mkdirs();
        
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(
                new File(USER_PREFS_DIR, safeEmail + ".properties"))) {
            props.store(fos, "Auto-login preferences");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save auto-login prefs", e);
        }
    }

    private void clearAutoLoginPrefs(String email) {
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        File prefsFile = new File(USER_PREFS_DIR, safeEmail + ".properties");
        if (prefsFile.exists()) {
            prefsFile.delete();
        }
    }

    // ==================== SYNC METHODS ====================
    
    private void startPeriodicSync(String email) {
        if (syncTimer != null && syncTimer.isRunning()) {
            syncTimer.stop();
        }
        
        currentSyncingEmail = email;
        
        syncTimer = new Timer(SYNC_INTERVAL_MS, e -> {
            if (currentSyncingEmail != null && Database.checkTursoConnection()) {
                new Thread(() -> {
                    try {
                        Database.pullUserDataFromCloud(currentSyncingEmail);
                        LOGGER.info("Periodic sync completed for: " + currentSyncingEmail);
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Periodic sync failed", ex);
                    }
                }).start();
            }
        });
        syncTimer.start();
        LOGGER.info("Periodic sync started for: " + email);
    }
    
    private void stopPeriodicSync() {
        if (syncTimer != null && syncTimer.isRunning()) {
            syncTimer.stop();
            syncTimer = null;
        }
        currentSyncingEmail = null;
    }

    // ==================== SECURITY METHODS ====================

    private void performSecureLogin() {
        // Prevent multiple concurrent login attempts
        if (isLoggingIn) {
            showGlassMessage("Please Wait", "Login already in progress...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        isLoggingIn = true;
        
        // Disable login button and input fields
        Component[] components = contentPane.getComponents();
        for (Component comp : components) {
            if (comp instanceof JButton) {
                comp.setEnabled(false);
            }
        }
        username.setEnabled(false);
        password.setEnabled(false);
        
        String inputEmail = username.getText().trim().toLowerCase();
        String inputPassword = new String(password.getPassword()).trim();
        String clientIp = getClientIpAddress();
        String deviceInfo = getDeviceInfo();

        if (!checkRateLimit(inputEmail)) {
            reenableLoginComponents();
            isLoggingIn = false;
            return;
        }

        if (inputEmail.isEmpty() || inputPassword.isEmpty()) {
            logAudit(inputEmail, "LOGIN_FAILED_EMPTY_CREDENTIALS", clientIp, false, "Empty credentials");
            showGlassMessage("Missing Input", "Please enter email and password", JOptionPane.WARNING_MESSAGE);
            reenableLoginComponents();
            isLoggingIn = false;
            return;
        }

        if (!inputEmail.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            logAudit(inputEmail, "LOGIN_FAILED_INVALID_EMAIL", clientIp, false, "Invalid email format");
            showGlassMessage("Invalid Email", "Please enter a valid email address.", JOptionPane.WARNING_MESSAGE);
            reenableLoginComponents();
            isLoggingIn = false;
            return;
        }

        if (isAccountLocked(inputEmail)) {
            logAudit(inputEmail, "LOGIN_FAILED_ACCOUNT_LOCKED", clientIp, false, "Account locked");
            showGlassMessage("Account Locked", 
                "Your account has been locked due to multiple failed attempts.\nContact administrator to unlock.",
                JOptionPane.WARNING_MESSAGE);
            reenableLoginComponents();
            isLoggingIn = false;
            return;
        }

        if (isPasswordExpired(inputEmail)) {
            showPasswordChangeDialog(inputEmail);
            reenableLoginComponents();
            isLoggingIn = false;
            return;
        }

        authenticateUser(inputEmail, inputPassword, clientIp, deviceInfo);
    }
    private void reenableLoginComponents() {
        SwingUtilities.invokeLater(() -> {
            Component[] components = contentPane.getComponents();
            for (Component comp : components) {
                if (comp instanceof JButton) {
                    comp.setEnabled(true);
                }
            }
            username.setEnabled(true);
            password.setEnabled(true);
            isLoggingIn = false;
        });
    }

    private boolean checkRateLimit(String email) {
        long now = System.currentTimeMillis();

        long lastAttempt = perAccountLastAttempt.getOrDefault(email, 0L);
        if (now - lastAttempt < MIN_ATTEMPT_INTERVAL_MS) {
            return false;
        }

        long lockoutEnd = perAccountLockout.getOrDefault(email, 0L);
        if (lockoutEnd > now) {
            long remainingMs = lockoutEnd - now;
            long remainingMin = TimeUnit.MILLISECONDS.toMinutes(remainingMs) + 1;
            showGlassMessage("Account Locked",
                "Too many failed attempts.\nTry again in " + remainingMin + " minute(s).",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        int attempts = perAccountAttempts.getOrDefault(email, 0);
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            perAccountLockout.put(email, now + TimeUnit.MINUTES.toMillis(ACCOUNT_LOCKOUT_MINUTES));
            perAccountAttempts.put(email, 0);
            return false;
        }

        perAccountLastAttempt.put(email, now);
        return true;
    }

    private void resetLoginAttempts(String email) {
        perAccountAttempts.remove(email);
        perAccountLockout.remove(email);
        perAccountLastAttempt.remove(email);
    }

    private boolean isAccountLocked(String email) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT locked, lock_reason FROM users WHERE email = ?", email);
            if (!result.isEmpty()) {
                boolean locked = result.get(0).get("locked") != null && 
                                result.get(0).get("locked").getAsBoolean();
                if (locked) {
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not check lock status", e);
        }
        return false;
    }

    private boolean isPasswordExpired(String email) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT password_last_changed FROM users WHERE email = ?", email);
            if (!result.isEmpty() && result.get(0).has("password_last_changed") && 
                !result.get(0).get("password_last_changed").isJsonNull()) {
                String lastChanged = result.get(0).get("password_last_changed").getAsString();
                
                LocalDateTime lastChangedDate;
                if (lastChanged.contains(" ")) {
                    String datePart = lastChanged.substring(0, 10);
                    String timePart = lastChanged.substring(11);
                    lastChangedDate = LocalDateTime.parse(datePart + "T" + timePart);
                } else {
                    lastChangedDate = LocalDateTime.parse(lastChanged + "T00:00:00");
                }
                
                return lastChangedDate.plusDays(PASSWORD_EXPIRY_DAYS).isBefore(LocalDateTime.now());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not check password expiry", e);
        } catch (DateTimeParseException e) {
            LOGGER.log(Level.WARNING, "Could not parse date: " + e.getMessage(), e);
        }
        return false;
    }

    private void showPasswordChangeDialog(String email) {
        JDialog dialog = new JDialog(this, "Password Expired", true);
        dialog.setUndecorated(true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(20, 20, 30));
        
        JLabel warningLabel = new JLabel("⚠️ Your password has expired!");
        warningLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        warningLabel.setForeground(accentRed);
        warningLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel infoLabel = new JLabel("Please change your password to continue.");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(textLight);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JPasswordField newPassField = createGlassPasswordField();
        newPassField.setMaximumSize(new Dimension(300, 35));
        
        JPasswordField confirmPassField = createGlassPasswordField();
        confirmPassField.setMaximumSize(new Dimension(300, 35));
        
        JButton changeBtn = createGlassButton("Change Password", accentGreen);
        JButton cancelBtn = createGlassButton("Cancel", accentRed);
        
        panel.add(warningLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(new JLabel("New Password:") {{ setForeground(textLight); }});
        panel.add(newPassField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("Confirm Password:") {{ setForeground(textLight); }});
        panel.add(confirmPassField);
        panel.add(Box.createVerticalStrut(20));
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.add(changeBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel);
        
        changeBtn.addActionListener(e -> {
            String newPass = new String(newPassField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());
            
            if (newPass.length() < 8) {
                showGlassMessage("Weak Password", "Password must be at least 8 characters", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                showGlassMessage("Mismatch", "Passwords do not match", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            updatePassword(email, newPass);
            dialog.dispose();
            showGlassMessage("Success", "Password updated successfully! Please login again.", JOptionPane.INFORMATION_MESSAGE);
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void updatePassword(String email, String newPassword) {
    try {
        String hashedPassword = hashPassword(newPassword);
        SQLiteCloudClient.executeUpdate(
            "UPDATE users SET password = ?, password_last_changed = datetime('now') WHERE email = ?",
            hashedPassword, email);
        LOGGER.info("Password updated for: " + email);
    } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to update password", e);
    }
}

    private void showForgotPasswordDialog() {
        JDialog dialog = new JDialog(this, "User Password Reset", true);
        dialog.setUndecorated(true);
        dialog.setSize(480, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setResizable(true);

        final Point[] dragPt = {null};

        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(0,0,0,250));
                g2d.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
                g2d.setColor(new Color(140,100,255,90));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(1,1,getWidth()-3,getHeight()-3,20,20);
                g2d.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragPt[0] = e.getPoint(); }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point cur = e.getLocationOnScreen();
                dialog.setLocation(cur.x - dragPt[0].x, cur.y - dragPt[0].y);
            }
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(18, 20, 5, 15));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel dialogTitle = new JLabel("User Password Reset");
        dialogTitle.setFont(new Font("Serif", Font.BOLD, 19));
        dialogTitle.setForeground(accentBlue);
        dialogTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel dialogSub = new JLabel("Reset Your Password");
        dialogSub.setFont(new Font("Dialog", Font.PLAIN, 12));
        dialogSub.setForeground(textMuted);
        dialogSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBlock.add(dialogTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(dialogSub);

        JButton topClose = new JButton("✕") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2d.setColor(new Color(180,60,60,180));
                    g2d.fillOval(0,0,24,24);
                } else {
                    g2d.setColor(new Color(140,100,255,80));
                    g2d.fillOval(0,0,24,24);
                }
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("✕", (24-fm.stringWidth("✕"))/2, ((24-fm.getHeight())/2)+fm.getAscent());
                g2d.dispose();
            }
        };
        topClose.setPreferredSize(new Dimension(24,24));
        topClose.setOpaque(false);
        topClose.setContentAreaFilled(false);
        topClose.setBorderPainted(false);
        topClose.setFocusPainted(false);
        topClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        topClose.addActionListener(e -> dialog.dispose());

        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(topClose, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(5, 25, 10, 25));

        JLabel step1 = new JLabel("Step 1 — Enter your registered email");
        step1.setFont(new Font("Dialog", Font.BOLD, 12));
        step1.setForeground(textMuted);
        step1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField emailField = createGlassTextField();
        emailField.setMaximumSize(new Dimension(400, 34));
        emailField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel step2 = new JLabel("Step 2 — Answer your security questions");
        step2.setFont(new Font("Dialog", Font.BOLD, 12));
        step2.setForeground(textMuted);
        step2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel q1Label = new JLabel("Security Question 1:");
        q1Label.setFont(new Font("Dialog", Font.PLAIN, 12));
        q1Label.setForeground(textLight);
        q1Label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel q1Display = new JLabel("(verify email first)");
        q1Display.setFont(new Font("Dialog", Font.ITALIC, 12));
        q1Display.setForeground(textMuted);
        q1Display.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField answer1Field = createGlassTextField();
        answer1Field.setMaximumSize(new Dimension(400, 34));
        answer1Field.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel q2Label = new JLabel("Security Question 2:");
        q2Label.setFont(new Font("Dialog", Font.PLAIN, 12));
        q2Label.setForeground(textLight);
        q2Label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel q2Display = new JLabel("(verify email first)");
        q2Display.setFont(new Font("Dialog", Font.ITALIC, 12));
        q2Display.setForeground(textMuted);
        q2Display.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField answer2Field = createGlassTextField();
        answer2Field.setMaximumSize(new Dimension(400, 34));
        answer2Field.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel step3 = new JLabel("Step 3 — Set new password");
        step3.setFont(new Font("Dialog", Font.BOLD, 12));
        step3.setForeground(textMuted);
        step3.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField newPassField = createGlassPasswordField();
        newPassField.setMaximumSize(new Dimension(400, 34));
        newPassField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField confirmPassField = createGlassPasswordField();
        confirmPassField.setMaximumSize(new Dimension(400, 34));
        confirmPassField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton verifyBtn = createGlassButton("Verify Email", accentBlue);
        verifyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton applyBtn = createGlassButton("Apply Changes", accentGreen);
        applyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyBtn.setEnabled(false);

        emailField.getDocument().addDocumentListener(new DocumentListener() {
            private Timer autoVerifyTimer;
            @Override public void insertUpdate(DocumentEvent e) { scheduleAutoVerify(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleAutoVerify(); }
            @Override public void changedUpdate(DocumentEvent e) {}
            private void scheduleAutoVerify() {
                if (autoVerifyTimer != null && autoVerifyTimer.isRunning()) autoVerifyTimer.stop();
                autoVerifyTimer = new Timer(800, ev -> {
                    String em = emailField.getText().trim().toLowerCase();
                    if (em.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
                        verifyBtn.doClick();
                    }
                });
                autoVerifyTimer.setRepeats(false);
                autoVerifyTimer.start();
            }
        });

        verifyBtn.addActionListener(e -> {
            String em = emailField.getText().trim().toLowerCase();
            if (em.isEmpty() || !em.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
                showGlassMessage("Invalid Email", "Enter a valid email first.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                List<JsonObject> result = SQLiteCloudClient.execute(
                    "SELECT security_question1, security_question2 FROM users WHERE email = ?", em);
                if (result.isEmpty()) {
                    showGlassMessage("Not Found", "No account found with that email.", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String sq1 = result.get(0).get("security_question1").getAsString();
                String sq2 = result.get(0).get("security_question2").getAsString();
                q1Display.setText(sq1);
                q2Display.setText(sq2);
                applyBtn.setEnabled(true);
            } catch (IOException ex) {
                showGlassMessage("Error", "Could not verify: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        applyBtn.addActionListener(e -> {
            String em = emailField.getText().trim().toLowerCase();
            String ans1 = answer1Field.getText().trim();
            String ans2 = answer2Field.getText().trim();
            String newPass = new String(newPassField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());

            if (ans1.isEmpty() || ans2.isEmpty()) {
                showGlassMessage("Missing Answers", "Please answer both security questions.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (newPass.length() < 8) {
                showGlassMessage("Weak Password", "Password must be at least 8 characters.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                showGlassMessage("Mismatch", "Passwords do not match.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!verifySecurityAnswers(em, ans1, ans2)) {
                showGlassMessage("Wrong Answers", "Security answers do not match.", JOptionPane.ERROR_MESSAGE);
                return;
            }
            updatePassword(em, newPass);
            dialog.dispose();
            username.setText(em);
            password.setText(newPass);
            showGlassMessage("Success", "✅ Password reset! Logging you in...", JOptionPane.INFORMATION_MESSAGE);
            Timer autoLogin = new Timer(1200, ev -> performSecureLogin());
            autoLogin.setRepeats(false);
            autoLogin.start();
        });

        contentPanel.add(step1);
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(emailField);
        contentPanel.add(Box.createVerticalStrut(5));
        contentPanel.add(verifyBtn);
        contentPanel.add(Box.createVerticalStrut(14));
        contentPanel.add(step2);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(q1Label);
        contentPanel.add(q1Display);
        contentPanel.add(Box.createVerticalStrut(3));
        contentPanel.add(answer1Field);
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(q2Label);
        contentPanel.add(q2Display);
        contentPanel.add(Box.createVerticalStrut(3));
        contentPanel.add(answer2Field);
        contentPanel.add(Box.createVerticalStrut(14));
        contentPanel.add(step3);
        contentPanel.add(Box.createVerticalStrut(5));
        JLabel newPassLbl = new JLabel("New Password:");
        newPassLbl.setForeground(textLight);
        newPassLbl.setFont(new Font("Dialog", Font.PLAIN, 12));
        newPassLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(newPassLbl);
        contentPanel.add(newPassField);
        contentPanel.add(Box.createVerticalStrut(5));
        JLabel confLbl = new JLabel("Confirm New Password:");
        confLbl.setForeground(textLight);
        confLbl.setFont(new Font("Dialog", Font.PLAIN, 12));
        confLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(confLbl);
        contentPanel.add(confirmPassField);
        contentPanel.add(Box.createVerticalStrut(20));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(0, 0, 10, 0));
        bottomBar.add(applyBtn);
        JButton cancelBtn2 = createGlassButton("Cancel", accentRed);
        cancelBtn2.addActionListener(ev -> dialog.dispose());
        bottomBar.add(cancelBtn2);
        panel.add(bottomBar, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void authenticateUser(String email, String password, String clientIp, String deviceInfo) {
        username.setEnabled(false);
        this.password.setEnabled(false);

        new Thread(() -> {
            boolean authenticated = false;
            String uname = null;
            String clientId = null;
            String role = "USER";
            String bizName = "";

            // ── Step 1: Try cloud ──────────────────────────────────────────
            try {
                if (Database.checkTursoConnection()) {
                    List<JsonObject> users = SQLiteCloudClient.execute(
                        "SELECT username, password, privileges, client_id, business_name " +
                        "FROM users WHERE email = ?", email);

                    if (!users.isEmpty()) {
                        JsonObject row = users.get(0);

                        // Guard: password field must exist and not be null
                        if (!row.has("password") || row.get("password").isJsonNull()) {
                            LOGGER.warning("Cloud user has no password hash: " + email);
                        } else {
                            String storedHash = row.get("password").getAsString();
                            boolean ok = verifyPassword(password, storedHash);
                            if (!ok && password.equals(storedHash)) ok = true; // plain-text fallback

                            if (ok) {
                                authenticated = true;
                                uname = row.has("username") && !row.get("username").isJsonNull()
                                    ? row.get("username").getAsString()
                                    : email.split("@")[0];
                                clientId = row.has("client_id") && !row.get("client_id").isJsonNull()
                                    ? row.get("client_id").getAsString() : null;
                                role = row.has("privileges") && !row.get("privileges").isJsonNull()
                                    ? row.get("privileges").getAsString() : "USER";
                                bizName = row.has("business_name") && !row.get("business_name").isJsonNull()
                                    ? row.get("business_name").getAsString() : "";

                                LOGGER.info("Cloud auth OK: email=" + email +
                                            " clientId=" + clientId + " role=" + role);

                                // Sync to local store so future offline logins work
                                syncUserToLocalStore(email, uname, password, role, clientId);
                            } else {
                                LOGGER.fine("Cloud auth: password mismatch for " + email);
                            }
                        }
                    } else {
                        LOGGER.fine("Cloud auth: no user found for " + email);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cloud auth failed, falling through to local: "
                    + e.getMessage(), e);
            }

            // ── Step 2: Try LocalUserStore (offline / cached) ─────────────
            if (!authenticated) {
                try {
                    LocalUserStore.LocalUser localUser =
                        LocalUserStore.getInstance().authenticate(email, password);
                    if (localUser != null) {
                        authenticated = true;
                        uname     = localUser.username;
                        role      = localUser.role.name();
                        // clientId stays null here — resolved later in performEmailLogin
                        LOGGER.info("LocalUserStore auth OK: " + email);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "LocalUserStore auth failed: " + e.getMessage(), e);
                }
            }

            // ── Step 3: Try local SQLite DB ────────────────────────────────
            if (!authenticated) {
                // We need to figure out which DB file to open BEFORE connecting
                // Try the email-scoped DB first, then fall back to current DB_FILE
                String targetDb = email.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_") + ".db";
                java.io.File dbFile = new java.io.File(targetDb);

                // If email-scoped DB doesn't exist yet, try current DB_FILE
                if (!dbFile.exists()) {
                    String currentDbFile = Database.getDatabaseFile();
                    if (currentDbFile != null) {
                        targetDb = currentDbFile;
                        LOGGER.fine("Email-scoped DB not found, trying current DB: " + targetDb);
                    }
                }

                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException e) {
                    LOGGER.severe("SQLite JDBC driver not found");
                }

                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + targetDb);
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT username, password, privileges, client_id " +
                         "FROM users WHERE email = ? OR username = ? LIMIT 1")) {
                    ps.setString(1, email);
                    ps.setString(2, email);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        String storedHash = rs.getString("password");
                        boolean ok = false;

                        // Try PBKDF2 hash (primary method)
                        if (storedHash != null && !storedHash.isEmpty()) {
                            ok = verifyPassword(password, storedHash);
                        }

                        // Try plain-text fallback
                        if (!ok && password.equals(storedHash)) {
                            ok = true;
                            LOGGER.warning("Plain-text password match for: " + email +
                                           " — password should be rehashed");
                        }

                        // Try SHA-256 fallback (used by older LocalUserStore)
                        if (!ok && storedHash != null) {
                            try {
                                java.security.MessageDigest md =
                                    java.security.MessageDigest.getInstance("SHA-256");
                                byte[] hash = md.digest(
                                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                StringBuilder sb = new StringBuilder();
                                for (byte b : hash) sb.append(String.format("%02x", b));
                                ok = sb.toString().equals(storedHash);
                            } catch (Exception ignored) {}
                        }

                        if (ok) {
                            authenticated = true;
                            uname    = rs.getString("username");
                            clientId = rs.getString("client_id");
                            role     = rs.getString("privileges");
                            if (role == null || role.isEmpty()) role = "USER";

                            LOGGER.info("SQLite auth OK: email=" + email +
                                        " db=" + targetDb + " clientId=" + clientId);

                            // Sync to LocalUserStore so next login uses cache
                            LocalUserStore.Role storeRole = convertRoleStringToEnum(role);
                            LocalUserStore.getInstance().registerUser(email, uname, storeRole, password);
                        }
                    } else {
                        LOGGER.fine("SQLite auth: no user found in " + targetDb + " for " + email);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "SQLite auth failed for db=" +
                        targetDb + ": " + e.getMessage(), e);
                }
            }

            // ── Deliver result on EDT ──────────────────────────────────────
            final boolean finalAuth      = authenticated;
            final String  finalUname     = uname;
            final String  finalClientId  = clientId;
            final String  finalRole      = role;
            final String  finalBizName   = bizName;

            SwingUtilities.invokeLater(() -> {
                // Re-enable UI regardless of result
                username.setEnabled(true);
                this.password.setEnabled(true);
                for (Component comp : contentPane.getComponents()) {
                    if (comp instanceof JButton) comp.setEnabled(true);
                }
                isLoggingIn = false;

                if (finalAuth) {
                    resetLoginAttempts(email);
                    saveEmailToHistory(email);

                    // Update clientId in login screen state
                    // NOTE: Do NOT call Database.setCurrentUser here —
                    // performEmailLogin handles full DB setup
                    if (finalClientId != null && !finalClientId.isEmpty()) {
                        this.currentClientId = finalClientId;
                    }

                    // Save business name if we got it from cloud
                    if (finalBizName != null && !finalBizName.isEmpty()) {
                        PharmacyInfoUtil.savePharmacyName(finalBizName);
                    }

                    // Fire-and-forget audit tasks (non-blocking)
                    new Thread(() -> recordLoginHistory(email, clientIp, true, deviceInfo),
                        "audit-login").start();
                    new Thread(() -> logAudit(email, "LOGIN_SUCCESS", clientIp, true, "OK"),
                        "audit-log").start();
                    new Thread(() -> updateLastLogin(email, clientIp),
                        "audit-lastlogin").start();

                    generateSessionToken(email, clientIp, deviceInfo);

                    // performEmailLogin handles ALL DB setup — pass resolved username
                    performEmailLogin(email,
                        finalUname != null ? finalUname : email.split("@")[0]);

                } else {
                    new Thread(() -> recordLoginHistory(email, clientIp, false, deviceInfo),
                        "audit-fail").start();
                    new Thread(() -> logAudit(email, "LOGIN_FAILED", clientIp, false,
                        "Bad credentials"), "audit-fail-log").start();

                    perAccountAttempts.merge(email, 1, Integer::sum);
                    int attempts  = perAccountAttempts.getOrDefault(email, 0);
                    int remaining = MAX_LOGIN_ATTEMPTS - attempts;

                    String msg = "Incorrect email or password.";
                    if (remaining > 0 && remaining < MAX_LOGIN_ATTEMPTS) {
                        msg += "\nAttempts remaining: " + remaining;
                    } else if (remaining <= 0) {
                        msg += "\nAccount temporarily locked. Try again later.";
                    }
                    showGlassMessage("Login Failed", msg, JOptionPane.ERROR_MESSAGE);
                }
            });
        }, "auth-thread").start();
    }
    private LocalUserStore.Role convertRoleStringToEnum(String privileges) {
        if (privileges == null) return LocalUserStore.Role.USER;
        // Strip restriction suffix (e.g. "ADMIN:Sale,Inventory" -> "ADMIN")
        String role = privileges.contains(":") ? privileges.split(":")[0] : privileges;
        switch (role.toUpperCase().trim()) {
            case "CEO":        return LocalUserStore.Role.CEO;
            case "OWNER":      return LocalUserStore.Role.OWNER;
            case "SUPERADMIN": return LocalUserStore.Role.SUPERADMIN;
            case "ADMIN":      return LocalUserStore.Role.ADMIN;
            case "MANAGER":    return LocalUserStore.Role.MANAGER;
            case "DIRECTOR":   return LocalUserStore.Role.DIRECTOR;
            case "SUPERVISOR": return LocalUserStore.Role.SUPERVISOR;
            case "CASHIER":    return LocalUserStore.Role.CASHIER;
            case "EMPLOYEE":   return LocalUserStore.Role.EMPLOYEE;
            case "VIEWER":     return LocalUserStore.Role.VIEWER;
            default:           return LocalUserStore.Role.USER;
        }
    }
 // Sync a single user from SQLite to LocalUserStore
    private void syncUserToLocalStore(String email, String username, String password, String privileges, String clientId) {
        try {
            // Check if user already exists in LocalUserStore
            LocalUserStore.LocalUser existing = LocalUserStore.getInstance().getUser(email);
            if (existing == null) {
                // Map role string to LocalUserStore.Role enum
                LocalUserStore.Role role = mapStringToRole(privileges);
                
                // Register user in LocalUserStore
                LocalUserStore.getInstance().registerUser(email, username, role, password);
                LOGGER.info("Synced user to LocalUserStore: " + username + " (" + email + ")");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to sync user to LocalUserStore: " + e.getMessage(), e);
        }
    }

    // Sync all SQLite users to LocalUserStore (for bulk sync)
    public void syncAllSqliteUsersToLocalStore() {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT username, email, password, privileges, client_id FROM users")) {
            
            ResultSet rs = stmt.executeQuery();
            int syncedCount = 0;
            
            while (rs.next()) {
                String username = rs.getString("username");
                String email = rs.getString("email");
                String password = rs.getString("password");
                String privileges = rs.getString("privileges");
                
                // Skip if email is null
                if (email == null || email.isEmpty()) continue;
                
                // Check if user already exists in LocalUserStore
                LocalUserStore.LocalUser existing = LocalUserStore.getInstance().getUser(email);
                if (existing == null) {
                    LocalUserStore.Role role = mapStringToRole(privileges);
                    LocalUserStore.getInstance().registerUser(email, username, role, password);
                    syncedCount++;
                }
            }
            
            LOGGER.info("Synced " + syncedCount + " users from SQLite to LocalUserStore");
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to sync all users: " + e.getMessage(), e);
        }
    }

    // Map string role to LocalUserStore.Role enum
    private LocalUserStore.Role mapStringToRole(String privileges) {
        if (privileges == null) return LocalUserStore.Role.USER;
        
        String roleStr = privileges.toUpperCase();
        if (roleStr.contains("CEO")) return LocalUserStore.Role.CEO;
        if (roleStr.contains("OWNER")) return LocalUserStore.Role.OWNER;
        if (roleStr.contains("SUPERADMIN")) return LocalUserStore.Role.SUPERADMIN;
        if (roleStr.contains("ADMIN")) return LocalUserStore.Role.ADMIN;
        if (roleStr.contains("MANAGER")) return LocalUserStore.Role.MANAGER;
        if (roleStr.contains("DIRECTOR")) return LocalUserStore.Role.DIRECTOR;
        if (roleStr.contains("SUPERVISOR")) return LocalUserStore.Role.SUPERVISOR;
        if (roleStr.contains("CASHIER")) return LocalUserStore.Role.CASHIER;
        if (roleStr.contains("EMPLOYEE")) return LocalUserStore.Role.EMPLOYEE;
        if (roleStr.contains("VIEWER")) return LocalUserStore.Role.VIEWER;
        
        return LocalUserStore.Role.USER;
    }

    private void generateSessionToken(String email, String clientIp, String deviceInfo) {
        currentSessionToken = java.util.UUID.randomUUID().toString();
        sessionStartTime = LocalDateTime.now();
        activeSessions.put(email, new ActiveSession(currentSessionToken, email, clientIp, deviceInfo));
        
        Timer cleanupTimer = new Timer(SESSION_TIMEOUT_MINUTES * 60 * 1000, e -> {
            if (sessionStartTime != null && 
                LocalDateTime.now().isAfter(sessionStartTime.plusMinutes(SESSION_TIMEOUT_MINUTES))) {
                currentSessionToken = null;
                sessionStartTime = null;
                activeSessions.remove(email);
                logAudit(email, "SESSION_EXPIRED", clientIp, true, "Session timed out");
            }
        });
        cleanupTimer.setRepeats(false);
        cleanupTimer.start();
    }

    private void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void recordLoginHistory(String email, String ipAddress, boolean success, String deviceInfo) {
    try {
        SQLiteCloudClient.executeUpdate(
            "INSERT INTO login_history (email, ip_address, success, device_info, timestamp) VALUES (?, ?, ?, ?, datetime('now'))",
            email, ipAddress, success ? 1 : 0, deviceInfo);
    } catch (IOException e) {
        if (e.getMessage() != null && !e.getMessage().contains("no such table")) {
            LOGGER.log(Level.WARNING, "Could not record login history", e);
        }
    }
}

    private void updateLastLogin(String email, String ipAddress) {
        try {
            SQLiteCloudClient.executeUpdate(
                "UPDATE users SET last_login = datetime('now'), last_login_ip = ? WHERE email = ?",
                ipAddress, email);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not update last login", e);
        }
    }

    private void logAudit(String username, String action, String ipAddress, boolean success, String details) {
    try {
        SQLiteCloudClient.executeUpdate(
            "INSERT INTO audit_log (username, action, ip_address, success, details, timestamp) VALUES (?, ?, ?, ?, ?, datetime('now'))",
            username, action, ipAddress, success ? 1 : 0, details);
    } catch (IOException e) {
        if (e.getMessage() != null && !e.getMessage().contains("no such table")) {
            LOGGER.log(Level.WARNING, "Could not write audit log", e);
        }
    }
}

    private String getClientIpAddress() {
        try {
            URL whatismyip = new URL("https://api.ipify.org");
            try (java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(whatismyip.openStream()))) {
                return in.readLine();
            }
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }

    private String getDeviceInfo() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version") +
               " | Java " + System.getProperty("java.version") +
               " | " + System.getProperty("user.name");
    }
    private void performEmailLogin(String email, String username) {
        LOGGER.info("Performing email login for: " + email);
        try {
            // ── Step 1: Resolve clientId (cloud first, then local, then fallback) ──
            String clientId = null;

            if (Database.checkTursoConnection()) {
                clientId = Database.getClientIdFromCloud(email);
                if (clientId != null && !clientId.isEmpty()) {
                    LOGGER.info("Resolved clientId from cloud: " + clientId + " for " + email);
                }
            }

            if (clientId == null || clientId.isEmpty()) {
                clientId = loadClientIdFromLocalDb(email);
                if (clientId != null && !clientId.isEmpty()) {
                    LOGGER.info("Resolved clientId from local DB: " + clientId + " for " + email);
                }
            }

            if (clientId == null || clientId.isEmpty()) {
                clientId = SQLiteCloudClient.loadClientIdFromFile();
                if (clientId != null && !clientId.isEmpty()) {
                    LOGGER.info("Resolved clientId from file: " + clientId + " for " + email);
                }
            }

            if (clientId == null || clientId.isEmpty()) {
                clientId = generateDeterministicClientId(email);
                LOGGER.warning("Generated new deterministic clientId: " + clientId + " for " + email);
            }

            // ── Step 2: Detect if this is a linked user (shares owner's DB) ──
            boolean isLinkedUser = false;
            String ownerEmail = null;

            if (Database.checkTursoConnection()) {
                try {
                    List<JsonObject> ownerResult = SQLiteCloudClient.execute(
                        "SELECT email FROM users WHERE client_id = ? " +
                        "AND privileges IN ('SUPERADMIN','ADMIN','CEO','OWNER') " +
                        "AND email != ? ORDER BY id ASC LIMIT 1",
                        clientId, email);
                    if (!ownerResult.isEmpty()) {
                        ownerEmail = ownerResult.get(0).get("email").getAsString();
                        isLinkedUser = true;
                        LOGGER.info("Linked user detected: " + email +
                                    " shares DB with owner: " + ownerEmail);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Could not check owner for clientId: " + e.getMessage());
                }
            }

            // ── Step 3: Set the correct DB file ──
            if (isLinkedUser && ownerEmail != null) {
                String ownerDb = ownerEmail.toLowerCase()
                                           .replaceAll("[^a-zA-Z0-9]", "_") + ".db";
                LOGGER.info("Linked user " + email + " will use owner DB: " + ownerDb);
                Database.setDatabaseFile(ownerDb);
            }

            // ── Step 4: Set session state on Database + SQLiteCloudClient ──
            Database.setCurrentUser(email, username, clientId);
            SQLiteCloudClient.setCurrentUser(email, username, clientId);

            this.currentClientId = clientId;
            this.currentUserEmail = email;
            this.currentUsername = username;

            // ── Step 5: Initialize the database (online or offline) ──
            if (Database.checkTursoConnection()) {
                LOGGER.info("Online — initializing user database with cloud sync: " + email);
                Database.initializeUserDatabase(email, username);
            } else {
                LOGGER.info("Offline — initializing local database only: " + email);
                Database.initializeLocalOnly(email, username);
            }

            // ── Step 6: Ensure sync queue schema is ready ──
            SQLiteCloudClient.ensureSyncQueueSchema();

            // ── Step 7: Initialize notification database ──
            try {
                NotificationDatabase.initializeDatabase();
                LOGGER.info("NotificationDatabase initialized");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING,
                    "Failed to initialize NotificationDatabase: " + e.getMessage(), e);
            }

            // ── Step 8: Register user in LocalUserStore for offline login ──
            LocalUserStore store = LocalUserStore.getInstance();
            if (store.getUser(email) == null) {
                LocalUserStore.Role role = LocalUserStore.Role.USER;
                try {
                    if (Database.checkTursoConnection()) {
                        List<JsonObject> roleResult = SQLiteCloudClient.execute(
                            "SELECT privileges FROM users WHERE email = ?", email);
                        if (!roleResult.isEmpty()) {
                            String privileges = roleResult.get(0).get("privileges").getAsString();
                            role = convertRoleStringToEnum(privileges);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("Could not fetch role for LocalUserStore: " + e.getMessage());
                }

                String existingPasswordHash = getUserPasswordHashFromDb(email);
                if (existingPasswordHash != null) {
                    LocalUserStore.LocalUser existingUser = new LocalUserStore.LocalUser(
                        email, username, role, true, existingPasswordHash);
                    store.addUser(existingUser);
                    LOGGER.info("Registered user in LocalUserStore with hash: " + email);
                } else {
                    store.registerUser(email, username, role, null);
                    LOGGER.warning("Registered user in LocalUserStore WITHOUT hash " +
                                   "(offline login may fail): " + email);
                }
            }

         // ── Step 9: Subscription Check (UPDATED) ──
            Subscribe.init(email);

            // Force refresh to get latest status
            Subscribe.forceRefreshIfStale(); // Changed to public wrapper method
            Subscribe.SubStatus subStatus = Subscribe.getStatus();
            int daysLeft = Subscribe.getDaysLeft();

            LOGGER.info("Subscription status for " + email + ": " + subStatus + 
                       ", days left: " + daysLeft);

            // Handle subscription status
            if (subStatus == Subscribe.SubStatus.EXPIRED || 
                subStatus == Subscribe.SubStatus.NONE) {
                
                LOGGER.warning("Subscription expired or none for: " + email + 
                              ". Showing payment dialog.");
                
                // Show blocking payment dialog
                Subscribe.PaymentDialog.showBlocking(
                    (JFrame) SwingUtilities.getWindowAncestor(contentPane));
                
                // Check again after dialog closes - force refresh again
                Subscribe.forceRefreshIfStale();
                if (!Subscribe.isAccessAllowed()) {
                    LOGGER.warning("User " + email + " did not activate subscription. Login aborted.");
                    reenableLoginComponents();
                    return;
                }
                
                LOGGER.info("Subscription activated for: " + email);
                
            } else if (subStatus == Subscribe.SubStatus.GRACE) {
                showGlassMessage("Grace Period",
                        "Your subscription has lapsed.\nPlease renew within 4 days to avoid losing access.",
                        JOptionPane.WARNING_MESSAGE);
                        
            } else if (subStatus == Subscribe.SubStatus.TRIAL && daysLeft <= 7) {
                String message = daysLeft <= 0 
                    ? "Your trial has ended. Please upgrade to continue."
                    : "Your free trial ends in " + daysLeft + " day(s).\nUpgrade now to avoid interruption.";
                
                showGlassMessage("Trial Ending Soon", message,
                    daysLeft <= 0 ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    
            } else if (subStatus == Subscribe.SubStatus.ACTIVE && daysLeft <= 3) {
                showGlassMessage("Subscription Renewal",
                        "Your subscription expires in " + daysLeft + " day(s).\nPlease renew to continue uninterrupted.",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            // ── Step 10: Background post-login tasks (UPDATED) ──
            final String finalClientId = clientId;
            final String finalEmail = email;
            final String finalUsername = username;
            
            // Check if subscription allows cloud features
            final boolean isCloudAccessAllowed = (subStatus == Subscribe.SubStatus.ACTIVE || 
                                                  subStatus == Subscribe.SubStatus.TRIAL || 
                                                  subStatus == Subscribe.SubStatus.GRACE);

            new Thread(() -> {
                try {
                    ensureUserUidColumn();
                } catch (SQLException e) {
                    LOGGER.warning("ensureUserUidColumn failed: " + e.getMessage());
                }
                try {
                    Database.ensureUserUid(finalEmail);
                } catch (SQLException e) {
                    LOGGER.warning("ensureUserUid failed: " + e.getMessage());
                }
                
                SQLiteCloudClient.syncClientIdAcrossAllSources();
                
                // Only sync if cloud access is allowed and connection exists
                if (Database.checkTursoConnection() && isCloudAccessAllowed) {
                    try {
                        SQLiteCloudClient.fullSync();
                        LOGGER.info("Login full sync complete for: " + finalEmail);
                    } catch (Exception e) {
                        LOGGER.warning("Login full sync failed: " + e.getMessage());
                    }
                } else if (!isCloudAccessAllowed) {
                    LOGGER.info("Cloud sync skipped for " + finalEmail + 
                               " due to subscription status: " + subStatus);
                }
                
                syncAllSqliteUsersToLocalStore();
            }, "post-login-init-" + email).start();

            // ── Step 11: Start periodic background sync (only if subscription allows) ──
            if (isCloudAccessAllowed) {
                try {
                    startPeriodicSync(email);
                    LOGGER.info("Periodic sync started for: " + email);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to start periodic sync", e);
                }
            } else {
                LOGGER.info("Periodic sync not started for " + email + 
                           " due to subscription status: " + subStatus);
            }

            // ── Step 12: Show success and open dashboard ──
            showAutoCloseSuccessMessage(username);

            final String finalClientIdForProfile = clientId;

            Timer openTimer = new Timer(1200, e -> {
                try {
                    GratisData.User profileUser = null;

                    try {
                        if (Database.checkTursoConnection() && isCloudAccessAllowed) {
                            List<JsonObject> results = SQLiteCloudClient.execute(
                                "SELECT id, email, username, privileges, business_name, " +
                                "client_id, created_date, account_status " +
                                "FROM users WHERE email = ?", finalEmail);
                            if (!results.isEmpty()) {
                                JsonObject obj = results.get(0);
                                profileUser = new GratisData.User(
                                    obj.get("id").getAsInt(),
                                    obj.get("username").getAsString(),
                                    obj.get("email").getAsString(),
                                    "",
                                    obj.has("privileges") && !obj.get("privileges").isJsonNull()
                                        ? obj.get("privileges").getAsString() : "USER",
                                    null, null,
                                    obj.has("account_status") && !obj.get("account_status").isJsonNull()
                                        && obj.get("account_status").getAsString().equals("ACTIVE"),
                                    "cloud",
                                    obj.has("business_name") && !obj.get("business_name").isJsonNull()
                                        ? obj.get("business_name").getAsString() : "",
                                    obj.has("client_id") && !obj.get("client_id").isJsonNull()
                                        ? obj.get("client_id").getAsString() : finalClientIdForProfile,
                                    obj.has("created_date") && !obj.get("created_date").isJsonNull()
                                        ? obj.get("created_date").getAsString() : null,
                                    null,
                                    false
                                );
                            }
                        }
                    } catch (Exception cloudEx) {
                        LOGGER.warning("Could not fetch profile from cloud: " + cloudEx.getMessage());
                    }

                    if (profileUser == null) {
                        LOGGER.info("Using local fallback profile for: " + finalEmail);
                        profileUser = new GratisData.User(
                            0,
                            finalUsername,
                            finalEmail,
                            "",
                            "USER",
                            null, null,
                            true,
                            "local",
                            "",
                            finalClientIdForProfile,
                            null, null,
                            false
                        );
                    }

                    Profilewindow profileWindow = new Profilewindow(profileUser);
                    profileWindow.setLocationRelativeTo(null);
                    profileWindow.setVisible(true);
                    dispose();

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error opening Profilewindow", ex);
                    showGlassMessage("Error", "Could not open dashboard: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
                    reenableLoginComponents();
                }
            });
            openTimer.setRepeats(false);
            openTimer.start();

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Login error", ex);
            showGlassMessage("Error", "Login failed: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            reenableLoginComponents();
        }
    }
 // Add these to your Subscribe class if missing
    

    // Make refreshCache accessible (it's already there, just ensure it's not private if needed elsewhere)
    // If you need to call it from outside, change its visibility or add a wrapper method
    private String findOwnerEmailForClientId(String clientId) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT email FROM users WHERE client_id = ? AND privileges IN " +
                "('SUPERADMIN','ADMIN','CEO','OWNER') ORDER BY id ASC LIMIT 1", clientId);
            if (!result.isEmpty()) {
                return result.get(0).get("email").getAsString();
            }
        } catch (IOException e) {
            LOGGER.warning("Could not find owner for clientId: " + clientId);
        }
        return null;
    }

    // ── Helper: deterministic clientId from email (no random UUID) ──
    private String generateDeterministicClientId(String email) {
        try {
            java.security.MessageDigest md =
                java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(
                email.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 16);
        } catch (Exception e) {
            // Absolute last resort
            return email.toLowerCase()
                        .replaceAll("[^a-zA-Z0-9]", "")
                        .substring(0, Math.min(16,
                            email.replaceAll("[^a-zA-Z0-9]", "").length()));
        }
    }

    // Helper method to get password hash from database
    private String getUserPasswordHashFromDb(String email) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE email = ?")) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("password");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not fetch password hash: " + e.getMessage(), e);
        }
        return null;
    }
    private String loadClientIdFromLocalDb(String email) {
        String dbFile = email.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_") + ".db";
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                 PreparedStatement ps = c.prepareStatement("SELECT value FROM app_settings WHERE key = 'client_id' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Could not load client_id from local DB: " + e.getMessage());
        }
        return null;
    }
 // Add this method to pharmacy_login.java and call it during startup or after creating linked users
    private void syncSqliteUsersToLocalStore() {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT username, email, password, privileges, client_id FROM users WHERE client_id = ?")) {
            
            String clientId = Database.getCurrentClientId();
            stmt.setString(1, clientId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String username = rs.getString("username");
                String email = rs.getString("email");
                String password = rs.getString("password");
                String privileges = rs.getString("privileges");
                
                // Check if user already exists in LocalUserStore
                LocalUserStore.LocalUser existing = LocalUserStore.getInstance().getUser(email);
                if (existing == null) {
                    // Map role
                    LocalUserStore.Role role;
                    String roleStr = privileges != null ? privileges.toUpperCase() : "USER";
                    if (roleStr.contains("CEO")) role = LocalUserStore.Role.CEO;
                    else if (roleStr.contains("OWNER")) role = LocalUserStore.Role.OWNER;
                    else if (roleStr.contains("SUPERADMIN")) role = LocalUserStore.Role.SUPERADMIN;
                    else if (roleStr.contains("ADMIN")) role = LocalUserStore.Role.ADMIN;
                    else if (roleStr.contains("MANAGER")) role = LocalUserStore.Role.MANAGER;
                    else if (roleStr.contains("DIRECTOR")) role = LocalUserStore.Role.DIRECTOR;
                    else if (roleStr.contains("SUPERVISOR")) role = LocalUserStore.Role.SUPERVISOR;
                    else if (roleStr.contains("CASHIER")) role = LocalUserStore.Role.CASHIER;
                    else if (roleStr.contains("EMPLOYEE")) role = LocalUserStore.Role.EMPLOYEE;
                    else if (roleStr.contains("VIEWER")) role = LocalUserStore.Role.VIEWER;
                    else role = LocalUserStore.Role.USER;
                    
                    // Add to LocalUserStore
                    LocalUserStore.getInstance().registerUser(email, username, role, password);
                    System.out.println("Synced user to LocalUserStore: " + username + " (" + email + ")");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void showAutoCloseSuccessMessage(String username) {
        JDialog successDialog = new JDialog(this, "Login Success", false);
        successDialog.setUndecorated(true);
        successDialog.setSize(450, 180);
        successDialog.setLocationRelativeTo(this);
        successDialog.setBackground(new Color(0, 0, 0, 0));
        
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                
                int w = getWidth();
                int h = getHeight();
                
                // Dark glass background with opacity
                g2d.setColor(new Color(0, 0, 0, 240));
                g2d.fillRoundRect(0, 0, w, h, 25, 25);
                
                // Glass border glow
                g2d.setColor(new Color(100, 255, 150, 180));
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.drawRoundRect(2, 2, w - 5, h - 5, 23, 23);
                
                // Inner glow border
                g2d.setColor(new Color(100, 255, 150, 80));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(4, 4, w - 9, h - 9, 21, 21);
                
                // Glass reflection effect
                g2d.setColor(new Color(255, 255, 255, 30));
                g2d.fillRoundRect(5, 5, w - 10, 50, 20, 20);
                
                // Subtle gradient overlay
                java.awt.GradientPaint gradient = new java.awt.GradientPaint(
                    0, 0, new Color(100, 255, 150, 20),
                    0, h, new Color(100, 255, 150, 5)
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, w, h, 25, 25);
                
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BorderLayout(15, 10));
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        
        // Icon panel with checkmark
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        iconPanel.setOpaque(false);
        
        JPanel checkCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth();
                int h = getHeight();
                
                // Outer circle with glow
                g2d.setColor(new Color(100, 255, 150, 180));
                g2d.setStroke(new BasicStroke(3));
                g2d.drawOval(3, 3, w - 7, h - 7);
                
                // Inner circle
                g2d.setColor(new Color(100, 255, 150, 60));
                g2d.fillOval(5, 5, w - 11, h - 11);
                
                // Checkmark
                g2d.setColor(new Color(100, 255, 150));
                g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.drawLine(18, 28, 28, 38);
                g2d.drawLine(28, 38, 45, 18);
                
                g2d.dispose();
            }
        };
        checkCircle.setPreferredSize(new Dimension(65, 65));
        checkCircle.setOpaque(false);
        iconPanel.add(checkCircle);
        
        // Message panel
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setOpaque(false);
        
        JLabel welcomeLabel = new JLabel("Welcome, " + username + "!");
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        welcomeLabel.setForeground(new Color(255, 255, 255));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messagePanel.add(welcomeLabel);
        
        messagePanel.add(Box.createVerticalStrut(5));
        
        JLabel successLabel = new JLabel("Login Successful");
        successLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        successLabel.setForeground(new Color(180, 220, 180));
        successLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messagePanel.add(successLabel);
        
        messagePanel.add(Box.createVerticalStrut(8));
        
        JLabel redirectLabel = new JLabel("Redirecting to Dashboard...");
        redirectLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        redirectLabel.setForeground(new Color(150, 150, 170));
        redirectLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messagePanel.add(redirectLabel);
        
        mainPanel.add(iconPanel, BorderLayout.WEST);
        mainPanel.add(messagePanel, BorderLayout.CENTER);
        
        successDialog.add(mainPanel);
        successDialog.setVisible(true);
        
        // Auto-close timer
        Timer closeTimer = new Timer(2000, e -> successDialog.dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    // Helper method to get GratisData.User from email
    private GratisData.User getGratisUser(String email, String username) {
        try {
            // Try to fetch from cloud first
            List<JsonObject> results = SQLiteCloudClient.execute(
                "SELECT id, email, username, privileges, business_name, client_id, created_date, account_status FROM users WHERE email = ?", 
                email);
            
            if (!results.isEmpty()) {
                JsonObject obj = results.get(0);
                return new GratisData.User(
                    obj.get("id").getAsInt(),
                    obj.get("username").getAsString(),
                    obj.get("email").getAsString(),
                    "",
                    obj.get("privileges").getAsString(),
                    null, null,
                    obj.get("account_status").getAsString().equals("ACTIVE"),
                    "local",
                    obj.has("business_name") && !obj.get("business_name").isJsonNull() ? 
                        obj.get("business_name").getAsString() : "",
                    obj.has("client_id") && !obj.get("client_id").isJsonNull() ? 
                        obj.get("client_id").getAsString() : "",
                    obj.get("created_date").getAsString(),
                    null,
                    false
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not fetch user from cloud", e);
        }
        
        // Fallback to local user
        return new GratisData.User(
            0, 
            username, 
            email, 
            "", 
            "USER", 
            null, 
            null, 
            true, 
            "local",
            "",
            "",
            null,
            null,
            false
        );
    }


    // Show auto-close success message
    

    private void adjustFontToFit(JLabel label) {
        String text = label.getText();
        if (text == null || text.isEmpty()) return;
        
        Font labelFont = label.getFont();
        int stringWidth = label.getFontMetrics(labelFont).stringWidth(text);
        int componentWidth = label.getWidth();
        
        if (componentWidth <= 0) return;
        
        double widthRatio = (double) componentWidth / (double) stringWidth;
        int newFontSize = (int)(labelFont.getSize() * widthRatio * 0.9);
        int componentHeight = label.getHeight();
        
        newFontSize = Math.min(newFontSize, componentHeight);
        label.setFont(new Font(labelFont.getName(), labelFont.getStyle(), Math.max(newFontSize, 10)));
    }

    private void loadSavedEmails() {
        savedEmails.clear();
        File f = new File(SAVED_EMAILS_FILE);
        if (!f.exists()) return;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !savedEmails.contains(line)) savedEmails.add(line);
            }
        } catch (Exception ignored) {}
    }

    private void saveEmailToHistory(String email) {
        if (email == null || email.isEmpty()) return;
        loadSavedEmails();
        if (!savedEmails.contains(email)) savedEmails.add(0, email);
        saveEmailsToFile();
    }

    private void saveEmailsToFile() {
        try {
            new File(System.getProperty("user.home") + File.separator + ".gratispos").mkdirs();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(SAVED_EMAILS_FILE))) {
                for (String e : savedEmails) pw.println(e);
            }
        } catch (Exception ignored) {}
    }

    private java.util.Properties loadUserPrefs(String email) {
        java.util.Properties p = new java.util.Properties();
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        File f = new File(USER_PREFS_DIR, safeEmail + ".properties");
        if (f.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                p.load(fis);
            } catch (Exception ignored) {}
        }
        return p;
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                password.toCharArray(), salt, 310000, 256
            );
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return java.util.Base64.getEncoder().encodeToString(salt) + ":" +
                   java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Hashing failed", e);
            throw new RuntimeException("Password hashing failed");
        }
    }

    private boolean verifyPassword(String inputPassword, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 2) return false;
            byte[] salt = java.util.Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = java.util.Base64.getDecoder().decode(parts[1]);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                inputPassword.toCharArray(), salt, 310000, 256
            );
            byte[] actualHash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return java.security.MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifySecurityAnswers(String email, String answer1, String answer2) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT security_answer1, security_answer2 FROM users WHERE email = ?", email);
            if (result.isEmpty()) return false;
            String stored1 = result.get(0).get("security_answer1").getAsString();
            String stored2 = result.get(0).get("security_answer2").getAsString();
            String input1 = hashAnswer(answer1.trim().toLowerCase());
            String input2 = hashAnswer(answer2.trim().toLowerCase());
            return java.security.MessageDigest.isEqual(stored1.getBytes(), input1.getBytes()) &&
                   java.security.MessageDigest.isEqual(stored2.getBytes(), input2.getBytes());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not verify security answers", e);
            return false;
        }
    }

    private String hashAnswer(String answer) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(answer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return answer;
        }
    }

    private static JTextField createGlassTextField() {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (hasFocus()) {
                    for (int i = 1; i <= 3; i++) {
                        g2d.setColor(new Color(140, 100, 255, 50 - i * 10));
                        g2d.setStroke(new BasicStroke(i));
                        g2d.drawRoundRect(i - 1, i - 1, getWidth() - i * 2 + 1, getHeight() - i * 2 + 1, 15, 15);
                    }
                    g2d.setColor(new Color(10, 10, 20, 160));
                } else {
                    g2d.setColor(new Color(0, 0, 0, 140));
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2d.setColor(hasFocus() ? new Color(140, 100, 255, 200) : new Color(80, 60, 255, 100));
                g2d.setStroke(new BasicStroke(hasFocus() ? 2 : 1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setForeground(new Color(255, 255, 255, 250));
        field.setCaretColor(new Color(255, 255, 255, 250));
        field.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return field;
    }

    private static JPasswordField createGlassPasswordField() {
        JPasswordField field = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (hasFocus()) {
                    for (int i = 1; i <= 3; i++) {
                        g2d.setColor(new Color(140, 100, 255, 50 - i * 10));
                        g2d.setStroke(new BasicStroke(i));
                        g2d.drawRoundRect(i - 1, i - 1, getWidth() - i * 2 + 1, getHeight() - i * 2 + 1, 15, 15);
                    }
                    g2d.setColor(new Color(10, 10, 20, 160));
                } else {
                    g2d.setColor(new Color(0, 0, 0, 140));
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2d.setColor(hasFocus() ? new Color(140, 100, 255, 200) : new Color(80, 60, 255, 100));
                g2d.setStroke(new BasicStroke(hasFocus() ? 2 : 1));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setForeground(new Color(255, 255, 255, 250));
        field.setCaretColor(new Color(255, 255, 255, 250));
        field.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        field.setEchoChar('•');
        return field;
    }

    private static JButton createGlassButton(String text, Color baseColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    for (int i = 1; i <= 4; i++) {
                        g2d.setColor(new Color(140, 100, 255, 40 - i * 5));
                        g2d.setStroke(new BasicStroke(i));
                        g2d.drawRoundRect(i - 1, i - 1, getWidth() - i * 2 + 1, getHeight() - i * 2 + 1, 20, 20);
                    }
                }
                java.awt.LinearGradientPaint gradient = new java.awt.LinearGradientPaint(
                    new java.awt.geom.Point2D.Float(0, 0),
                    new java.awt.geom.Point2D.Float(getWidth(), getHeight()),
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{baseColor, new Color(140, 80, 255, 255), new Color(160, 100, 255, 200)}
                );
                g2d.setPaint(gradient);
                if (getModel().isPressed()) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
                }
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g2d.setColor(new Color(255, 255, 255, 50));
                g2d.fillRoundRect(2, 2, getWidth() - 4, getHeight() / 2, 18, 18);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                for (int i = 1; i <= 2; i++) {
                    g2d.setColor(new Color(255, 255, 255, 30));
                    g2d.drawString(text, x - i, y - i);
                }
                g2d.setColor(Color.WHITE);
                g2d.drawString(text, x, y);
                g2d.dispose();
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JCheckBox createGlassCheckBox(String text) {
        JCheckBox checkBox = new JCheckBox(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected()) {
                    g2d.setColor(new Color(140, 80, 255, 220));
                    g2d.fillRoundRect(0, 0, 15, 15, 5, 5);
                    g2d.setColor(new Color(180, 120, 255, 180));
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawRoundRect(0, 0, 14, 14, 5, 5);
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(new BasicStroke(2f));
                    g2d.drawLine(3, 7, 6, 11);
                    g2d.drawLine(6, 11, 12, 4);
                } else {
                    g2d.setColor(new Color(0, 0, 0, 140));
                    g2d.fillRoundRect(0, 0, 15, 15, 5, 5);
                    g2d.setColor(new Color(80, 60, 255, 100));
                    g2d.setStroke(new BasicStroke(1));
                    g2d.drawRoundRect(0, 0, 14, 14, 5, 5);
                }
                g2d.setColor(new Color(220, 220, 255, 220));
                g2d.setFont(getFont());
                g2d.drawString(text, 20, 12);
                g2d.dispose();
            }
        };
        checkBox.setOpaque(false);
        checkBox.setBackground(new Color(0, 0, 0, 0));
        checkBox.setForeground(new Color(220, 220, 255, 220));
        checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return checkBox;
    }

    private void showGlassMessage(String title, String message, int messageType) {
        JDialog msgDialog = new JDialog(this, title, true);
        msgDialog.setUndecorated(true);
        msgDialog.setSize(420, 220);
        msgDialog.setLocationRelativeTo(this);
        msgDialog.setBackground(Color.BLACK);

        Color accentColor = messageType == JOptionPane.ERROR_MESSAGE ? new Color(220, 60, 60) :
                            messageType == JOptionPane.WARNING_MESSAGE ? new Color(220, 160, 0) :
                            new Color(60, 180, 100);

        String iconText = messageType == JOptionPane.ERROR_MESSAGE ? "✕" :
                          messageType == JOptionPane.WARNING_MESSAGE ? "⚠" : "✓";

        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(10, 10, 15));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2d.setColor(accentColor);
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 18, 18);
                g2d.dispose();
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

        JPanel iconArea = new JPanel();
        iconArea.setOpaque(false);
        JLabel iconLabel = new JLabel(iconText);
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 38));
        iconLabel.setForeground(accentColor);
        iconArea.add(iconLabel);

        JPanel textArea = new JPanel();
        textArea.setOpaque(false);
        textArea.setLayout(new BoxLayout(textArea, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msgLabel = new JLabel("<html><center>" + message.replace("\n","<br>") + "</center></html>");
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        msgLabel.setForeground(new Color(200, 200, 210));
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        textArea.add(Box.createVerticalStrut(5));
        textArea.add(titleLabel);
        textArea.add(Box.createVerticalStrut(8));
        textArea.add(msgLabel);

        JPanel btnArea = new JPanel();
        btnArea.setOpaque(false);
        JButton okBtn = new JButton("OK") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getModel().isRollover() ? accentColor.brighter() : accentColor);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("OK", (getWidth()-fm.stringWidth("OK"))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2d.dispose();
            }
        };
        okBtn.setPreferredSize(new Dimension(100, 34));
        okBtn.setOpaque(false);
        okBtn.setContentAreaFilled(false);
        okBtn.setBorderPainted(false);
        okBtn.setFocusPainted(false);
        okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okBtn.addActionListener(e -> msgDialog.dispose());
        btnArea.add(okBtn);

        mainPanel.add(iconArea, BorderLayout.WEST);
        mainPanel.add(textArea, BorderLayout.CENTER);
        mainPanel.add(btnArea, BorderLayout.SOUTH);

        msgDialog.setContentPane(mainPanel);
        msgDialog.getRootPane().setOpaque(false);
        msgDialog.setVisible(true);
    }

    public static void clearAllCache() {
        perAccountAttempts.clear();
        perAccountLockout.clear();
        perAccountLastAttempt.clear();
        activeSessions.clear();
        LocalUserStore.getInstance().clearCache();
        Database.clearCurrentUser();
        LOGGER.info("All session cache cleared");
    }
    
    public static void signOut() {
        clearAllCache();
        if (instance != null && instance.syncTimer != null) {
            instance.stopPeriodicSync();
        }
        System.gc();
        SwingUtilities.invokeLater(() -> {
            Pharmacy_Login loginWindow = new Pharmacy_Login();
            loginWindow.setLocationRelativeTo(null);
            loginWindow.setVisible(true);
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SplashScreen splash = new SplashScreen();
            splash.setVisible(true);

            new Thread(() -> {
                try {
                    boolean online = Database.checkTursoConnection();
                    if (online) {
                        SQLiteCloudClient.initializeCloudTables();
                        LOGGER.info("Cloud tables verified");

                        try {
                            SQLiteCloudClient.initializeCloudMessagesTable();
                            LOGGER.info("Cloud messages table verified");
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING,
                                "Could not initialize cloud messages table: " + e.getMessage(), e);
                        }
                    } else {
                        LOGGER.warning("Starting in offline mode");
                    }

                    // NotificationDatabase is local-only, safe at startup
                    try {
                        NotificationDatabase.initializeDatabase();
                        LOGGER.info("NotificationDatabase initialized");
                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING,
                            "Could not initialize NotificationDatabase: " + e.getMessage(), e);
                    }

                    // ── REMOVED: ensureUserUidColumn() ──
                    // Needs a user DB file — runs in performEmailLogin() instead

                    // ── REMOVED: syncClientIdAcrossAllSources() ──
                    // Needs an active user session — runs in performEmailLogin() instead

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                        "Background DB check failed — continuing offline", e);
                    try {
                        NotificationDatabase.initializeDatabase();
                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE,
                            "Failed to initialize local databases", ex);
                    }
                }
            }, "db-init-thread").start();

            // Start Auth HTTP Server for Google OAuth
            try {
                AuthHttpServer.start();
                LOGGER.info("Auth HTTP Server started on port 8080");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to start AuthHttpServer", e);
            }

            // Start WebSocket Message Server for real-time chat
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    TenantAwareMessageHandler.startServer(8887);
                    LOGGER.info("WebSocket Message Server started on port 8887");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                        "Could not start WebSocket server: " + e.getMessage(), e);
                    LOGGER.info("Chat will fall back to database polling mode");
                }
            }, "websocket-server").start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                AuthHttpServer.stop();
                TenantAwareMessageHandler.stopServer();
                clearAllCache();
                Database.shutdownConnection();
                LOGGER.info("All services stopped, cache cleared, DB connection closed");
            }, "shutdown-hook"));
        });
    }

    // ── ensureUserUidColumn: now takes a Connection parameter ──
    // Called from performEmailLogin() post-login thread where DB is ready
    private static void ensureUserUidColumn() throws SQLException {
        // Guard: DB file must be set (user must be logged in)
        if (Database.getDatabaseFile() == null) {
            LOGGER.warning("ensureUserUidColumn skipped — no DB file set yet");
            return;
        }

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check local DB for user_uid column
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)");
            boolean hasUserUid = false;
            while (rs.next()) {
                if ("user_uid".equals(rs.getString("name"))) {
                    hasUserUid = true;
                    break;
                }
            }

            if (!hasUserUid) {
                stmt.execute("ALTER TABLE users ADD COLUMN user_uid TEXT");
                LOGGER.info("Added user_uid column to local users table");

                stmt.execute(
                    "UPDATE users SET user_uid = client_id || '_' || id " +
                    "WHERE user_uid IS NULL AND client_id IS NOT NULL");
                LOGGER.info("Populated user_uid for existing local users");

                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_users_user_uid ON users(user_uid)");
            }

        } catch (SQLException e) {
            // Log but don't crash — user_uid is not critical for core functionality
            LOGGER.warning("ensureUserUidColumn failed (non-fatal): " + e.getMessage());
        }
    }
    static class SplashScreen extends JDialog {
        private static final long serialVersionUID = 1L;
        private JProgressBar progressBar;
        private JLabel statusLabel;
        private Timer progressTimer;
        
        public SplashScreen() {
            setUndecorated(true);
            setSize(350, 120);
            setLocationRelativeTo(null);
            setBackground(new Color(0, 0, 0, 0));
            
            JPanel content = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0, 0, 0, 250));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                    g2.setColor(new Color(140, 100, 255, 90));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30);
                }
            };
            content.setOpaque(false);
            content.setBorder(new EmptyBorder(10, 10, 0, 10));
            content.setLayout(new BorderLayout());
            add(content);
            
            JLabel titleLabel = new JLabel("Point Of Sale Management System", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            titleLabel.setForeground(new Color(230, 230, 250));
            content.add(titleLabel, BorderLayout.NORTH);
            
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.setOpaque(false);
            
            statusLabel = new JLabel("Starting...", SwingConstants.CENTER);
            statusLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
            statusLabel.setForeground(new Color(180, 180, 210));
            statusLabel.setBorder(new EmptyBorder(15, 0, 15, 0));
            centerPanel.add(statusLabel, BorderLayout.CENTER);
            
            content.add(centerPanel, BorderLayout.CENTER);
            
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setForeground(new Color(52, 144, 220, 220));
            progressBar.setBackground(new Color(40, 40, 55, 150));
            progressBar.setBorderPainted(false);
            content.add(progressBar, BorderLayout.SOUTH);
            
            animateProgress();
        }
        
        
        private void animateProgress() {
    progressTimer = new Timer(60, null);
    progressTimer.addActionListener(new ActionListener() {
        int value = 0;
        @Override
        public void actionPerformed(ActionEvent e) {
            value++;
            progressBar.setValue(value);
            if (value == 20) statusLabel.setText("Initializing...");
            if (value == 50) statusLabel.setText("Loading modules...");
            if (value == 80) statusLabel.setText("Checking for updates...");  // <- changed
            if (value >= 100) {
                progressTimer.stop();

                // ── Check for update before showing login ──
                new Thread(() -> {
                    if (UpdateChecker.isUpdateAvailable()) {
                        String latest = UpdateChecker.getLatestVersion();
                        SwingUtilities.invokeLater(() -> {
                            showUpdateDialog(latest);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            dispose(); // close splash
                            Pharmacy_Login login = new Pharmacy_Login();
                            login.setLocationRelativeTo(null);
                            login.setVisible(true);
                        });
                    }
                }, "update-check-thread").start();
            }
        }
    });
    progressTimer.start();
}

private void showUpdateDialog(String latestVersion) {
    // Glass-style dialog matching your existing UI
    JDialog updateDialog = new JDialog();
    updateDialog.setUndecorated(true);
    updateDialog.setSize(420, 220);
    updateDialog.setLocationRelativeTo(null);
    updateDialog.setAlwaysOnTop(true);

    JPanel mainPanel = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0, 0, 0, 250));
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2d.setColor(new Color(0, 140, 255, 120));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 20, 20);
            g2d.dispose();
        }
    };
    mainPanel.setOpaque(false);
    mainPanel.setLayout(new BorderLayout(15, 10));
    mainPanel.setBorder(new EmptyBorder(20, 25, 20, 25));

    // Icon
    JLabel iconLabel = new JLabel("↑");
    iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 42));
    iconLabel.setForeground(new Color(0, 140, 255));
    iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
    iconLabel.setPreferredSize(new Dimension(60, 60));

    // Text area
    JPanel textPanel = new JPanel();
    textPanel.setOpaque(false);
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

    JLabel titleLbl = new JLabel("Update Available!");
    titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
    titleLbl.setForeground(Color.WHITE);

    JLabel versionLbl = new JLabel("v" + UpdateChecker.getCurrentVersion()
        + "  →  v" + latestVersion);
    versionLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    versionLbl.setForeground(new Color(180, 180, 210));

    JLabel infoLbl = new JLabel("Download to get bug fixes and improvements.");
    infoLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
    infoLbl.setForeground(new Color(130, 130, 160));

    textPanel.add(titleLbl);
    textPanel.add(Box.createVerticalStrut(6));
    textPanel.add(versionLbl);
    textPanel.add(Box.createVerticalStrut(4));
    textPanel.add(infoLbl);

    // Buttons
    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    btnPanel.setOpaque(false);

    JButton downloadBtn = createGlassButton("Download Now",
        new Color(0, 140, 255));
    JButton skipBtn = createGlassButton("Skip",
        new Color(80, 80, 100));

    downloadBtn.setPreferredSize(new Dimension(145, 36));
    skipBtn.setPreferredSize(new Dimension(90, 36));

    downloadBtn.addActionListener(ev -> {
        try {
            Desktop.getDesktop().browse(
                new URI(UpdateChecker.getDownloadUrl()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        updateDialog.dispose();
        dispose();
        Pharmacy_Login login = new Pharmacy_Login();
        login.setLocationRelativeTo(null);
        login.setVisible(true);
    });

    skipBtn.addActionListener(ev -> {
        updateDialog.dispose();
        dispose();
        Pharmacy_Login login = new Pharmacy_Login();
        login.setLocationRelativeTo(null);
        login.setVisible(true);
    });

    btnPanel.add(downloadBtn);
    btnPanel.add(skipBtn);

    mainPanel.add(iconLabel, BorderLayout.WEST);
    mainPanel.add(textPanel, BorderLayout.CENTER);
    mainPanel.add(btnPanel, BorderLayout.SOUTH);

    updateDialog.setContentPane(mainPanel);
    updateDialog.setVisible(true);
}
