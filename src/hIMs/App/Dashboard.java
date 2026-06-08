package hIMs.App;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

public class Dashboard extends JFrame {

    // ── Color System ────────────────────────────────────────────────────────────
    private static final Color C_BG           = new Color(6,   7,  12);
    private static final Color C_SURFACE      = new Color(12,  14,  22, 240);
    private static final Color C_SURFACE_2    = new Color(18,  21,  34, 230);
    private static final Color C_GLASS        = new Color(255, 255, 255, 8);
    private static final Color C_BORDER       = new Color(255, 255, 255, 18);
    private static final Color C_BORDER_GLOW  = new Color(60,  130, 255, 55);
    private static final Color C_ACCENT_1     = new Color(50,  140, 255);
    private static final Color C_ACCENT_2     = new Color(120,  70, 255);
    private static final Color C_TEXT_PRI     = new Color(228, 232, 248);
    private static final Color C_TEXT_SEC     = new Color(130, 138, 168);
    private static final Color C_TEXT_DIM     = new Color(75,   82, 110);
    private static final Color C_ONLINE       = new Color(52,  211, 120);
    private static final Color C_OFFLINE      = new Color(245, 185,  50);
    private static final Color C_TIME_GREEN   = new Color(45,  220, 110);
    private static final Color C_SEP          = new Color(255, 255, 255, 14);
    private static final Color C_MENU_SEL_BG  = new Color(50,  120, 255, 22);
    private static final Color C_MENU_SEL_BD  = new Color(60,  140, 255, 130);
    private static final Color C_MENU_HOV     = new Color(255, 255, 255,  8);

    // ── Dimensions ──────────────────────────────────────────────────────────────
    private static final int W           = 1280;
    private static final int H           = 820;
    private static final int TOP_H       = 60;
    private static final int SEP_H       = 1;
    private static final int BODY_H      = H - TOP_H - SEP_H;
    private static final int AVATAR_D    = 80;
    private static final int SIDEBAR_W   = 220;

    // ── State ───────────────────────────────────────────────────────────────────
    private GratisData.User currentUser;
    private Point    dragPoint;
    private JPanel   mainPanel, contentSection;
    private JLabel   timeLabel, dateLabel, statusDot, statusText;
    private JPanel   avatarPanel;
    private String   selectedMenu = "Dashboard";
    private boolean  isMaximized  = false;
    private Rectangle normalBounds;
    private BufferedImage avatarImage = null;
    private String posTitle = "Gratis POS";

    // Role constants
    private static final String ROLE_CEO      = "CEO";
    private static final String ROLE_MANAGER  = "Manager";
    private static final String ROLE_SUPERVISOR = "Supervisor";
    private static final String ROLE_EMPLOYEE = "Employee";

    // Menu definitions: {label, icon, requiresAdmin, defaultVisible, panelClass}
    private static final Object[][] ALL_MENUS = {
        {"Dashboard",                    null, false, true, "DashboardPanel"},
        {"Admin",                        null, true,  true, "AdminPanel"},
        {"Products-New items",           null, false, true, "ProductPanel"},
        {"Purchases-Restock",            null, false, true, "PurchasePanel"},
        {"Customers-Retailers",          null, false, true, "CustomerPanel"},
        {"General-Staff",                null, false, true, "GeneralPanel"},
        {"Suppliers-Importers",          null, false, true, "SuppliersPanel"},
        {"Create Order",                 null, false, true, "OrderPanel"},
        {"Refund-Products",              null, false, true, "RefundPanel"},
        {"Product-Ledger",               null, false, true, "LedgerPanel"},
        {"Stock Adjustment & Inventory", null, false, true, "AdjustmentPanel"},
        {"Invoice Registry",             null, false, true, "InvoicePanel"},
        {"Receipts",                     null, false, true, "ReceiptsPanel"},
        {"Pending Order",                null, false, true, "OrderPanel"},
        {"Daily-User Reports",           null, false, true, "UserReportPanel"},
        {"Reports",                      null, false, true, "ReportsPanel"},
        {"Backup",                       null, false, true, null},
        {"Settings",                     null, false, true, null},
    };

    private java.util.Map<String, Boolean> menuVisibility = new java.util.HashMap<>();
    private java.util.List<JButton> menuButtons = new java.util.ArrayList<>();
    private java.util.Map<String, JPanel> panelCache = new java.util.HashMap<>();

    // ── Constructor ─────────────────────────────────────────────────────────────
    public Dashboard(GratisData.User user) {
        this.currentUser  = user;
        
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(W, screenSize.width - 100);
        int height = Math.min(H, screenSize.height - 100);
        
        setSize(width, height);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        mainPanel = buildMainPanel();
        mainPanel.setLayout(new BorderLayout(0, 0));
        initUI();
        setContentPane(mainPanel);
        
        int cornerRadius = Math.min(20, Math.min(width, height) / 20);
        setShape(new RoundRectangle2D.Double(0, 0, width, height, cornerRadius, cornerRadius));

        enableDrag();
        startClock();
        loadAvatar();
        checkNet();
    }

    private JPanel buildMainPanel() {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                int w = getWidth(), h = getHeight();

                g2.setColor(C_BG);
                g2.fillRoundRect(0, 0, w, h, 20, 20);

                paintRadial(g2, w * .15f, h * .06f, w * .65f,
                        new Color(20, 80, 220, 40), new Color(0, 0, 0, 0));
                paintRadial(g2, w * .88f, h * .94f, w * .55f,
                        new Color(90, 30, 200, 30), new Color(0, 0, 0, 0));

                GradientPaint sheen = new GradientPaint(
                        0, 0, new Color(255, 255, 255, 20),
                        0, h / 5f, new Color(255, 255, 255, 0));
                g2.setPaint(sheen);
                g2.fillRoundRect(4, 4, w - 8, h / 5, 18, 18);

                for (int i = 3; i >= 1; i--) {
                    g2.setColor(new Color(40, 110, 255, 8 * i));
                    g2.setStroke(new BasicStroke(i * 1.5f));
                    g2.drawRoundRect(i, i, w - 2*i - 1, h - 2*i - 1, 20 - i, 20 - i);
                }
                g2.setColor(C_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
                g2.dispose();
            }
        };
    }

    private void initUI() {
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(buildTopBar(), BorderLayout.NORTH);
        topSection.add(buildSep(),    BorderLayout.SOUTH);

        mainPanel.add(topSection,  BorderLayout.NORTH);
        mainPanel.add(buildBody(), BorderLayout.CENTER);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(new Color(8, 9, 16, 220));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(W, TOP_H));
        bar.setBorder(new EmptyBorder(0, 20, 0, 20));

        JPanel leftSpacer = new JPanel();
        leftSpacer.setOpaque(false);
        leftSpacer.setPreferredSize(new Dimension(100, TOP_H));

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerPanel.setOpaque(false);
        
        JLabel title = new JLabel(posTitle);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(C_TEXT_PRI);
        centerPanel.add(title);

        JPanel dtBox = new JPanel();
        dtBox.setLayout(new BoxLayout(dtBox, BoxLayout.Y_AXIS));
        dtBox.setOpaque(false);
        dtBox.setPreferredSize(new Dimension(200, TOP_H));

        timeLabel = new JLabel("00:00:00");
        timeLabel.setFont(new Font("Consolas", Font.BOLD, 18));
        timeLabel.setForeground(C_TIME_GREEN);
        timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        dateLabel = new JLabel("Mon 01 Jan 2025");
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dateLabel.setForeground(C_TIME_GREEN);
        dateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        dtBox.add(timeLabel);
        dtBox.add(Box.createVerticalStrut(2));
        dtBox.add(dateLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, (TOP_H - 40) / 2));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(200, TOP_H));
        rightPanel.add(dtBox);

        bar.add(leftSpacer, BorderLayout.WEST);
        bar.add(centerPanel, BorderLayout.CENTER);
        bar.add(rightPanel, BorderLayout.EAST);
        
        return bar;
    }

    private JPanel buildSep() {
        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(0, 0, 0, 0),
                        getWidth() * .15f, 0, new Color(50, 130, 255, 80));
                g2.setPaint(gp); g2.fillRect(0, 0, getWidth() / 2, SEP_H + 1);
                GradientPaint gp2 = new GradientPaint(
                        getWidth() / 2f, 0, new Color(50, 130, 255, 80),
                        getWidth() * .85f, 0, new Color(0, 0, 0, 0));
                g2.setPaint(gp2); g2.fillRect(getWidth() / 2, 0, getWidth() / 2, SEP_H + 1);
                g2.setColor(C_SEP);
                g2.fillRect(0, 0, getWidth(), SEP_H);
                g2.dispose();
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(W, SEP_H + 2));
        return sep;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                int x = SIDEBAR_W;
                GradientPaint vg = new GradientPaint(x, 0, new Color(50, 130, 255, 40),
                        x + 2, 0, new Color(0, 0, 0, 0));
                g2.setPaint(vg); g2.fillRect(x - 1, 0, 4, getHeight());
                g2.setColor(C_SEP);
                g2.fillRect(x, 0, 1, getHeight());
                g2.dispose();
            }
        };
        body.setOpaque(false);
        
        body.add(buildSidebar(), BorderLayout.WEST);
        body.add(buildContent(), BorderLayout.CENTER);
        return body;
    }

    private JPanel sidebarPanel;

    private JPanel buildSidebar() {
        sidebarPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(C_SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                paintRadial(g2, getWidth() / 2f, 0, getWidth() * 1.1f,
                        new Color(40, 100, 255, 18), new Color(0, 0, 0, 0));
                g2.dispose();
            }
        };
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setOpaque(false);
        sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_W, BODY_H));
        sidebarPanel.setMinimumSize(new Dimension(SIDEBAR_W, 0));
        sidebarPanel.setMaximumSize(new Dimension(SIDEBAR_W, Integer.MAX_VALUE));
        sidebarPanel.setBorder(new EmptyBorder(22, 12, 18, 12));

        populateSidebar();
        return sidebarPanel;
    }

    private void populateSidebar() {
        sidebarPanel.removeAll();
        menuButtons.clear();

        avatarPanel = buildAvatarPanel();
        sidebarPanel.add(avatarPanel);
        sidebarPanel.add(vgap(4));

        String roleDisplay = getUserRole();
        JLabel roleLbl = new JLabel(roleDisplay.toUpperCase());
        roleLbl.setFont(new Font("Segoe UI", Font.BOLD, 9));
        roleLbl.setForeground(C_ACCENT_1);
        roleLbl.setHorizontalAlignment(SwingConstants.CENTER);
        roleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarPanel.add(roleLbl);
        sidebarPanel.add(vgap(2));

        String firstName = currentUser.name != null && !currentUser.name.isBlank()
            ? currentUser.name.trim().split("\\s+")[0] : currentUser.email.split("@")[0];
        JLabel nameLbl = new JLabel(firstName);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLbl.setForeground(C_TEXT_PRI);
        nameLbl.setHorizontalAlignment(SwingConstants.CENTER);
        nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarPanel.add(nameLbl);
        sidebarPanel.add(vgap(3));

        JLabel emailLbl = new JLabel(truncate(currentUser.email, 22));
        emailLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        emailLbl.setForeground(C_TEXT_SEC);
        emailLbl.setHorizontalAlignment(SwingConstants.CENTER);
        emailLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarPanel.add(emailLbl);
        sidebarPanel.add(vgap(4));

        JPanel netRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        netRow.setOpaque(false);
        netRow.setMaximumSize(new Dimension(SIDEBAR_W - 24, 20));

        statusDot  = new JLabel("●");
        statusDot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusDot.setForeground(C_TEXT_DIM);

        statusText = new JLabel("checking…");
        statusText.setFont(new Font("Segoe UI", Font.BOLD, 10));
        statusText.setForeground(C_TEXT_DIM);

        netRow.add(statusDot);
        netRow.add(statusText);
        sidebarPanel.add(netRow);
        sidebarPanel.add(vgap(12));

        sidebarPanel.add(hLine(SIDEBAR_W - 24));
        sidebarPanel.add(vgap(12));

        boolean isAdminUser = isAdminRole();
        String role = getUserRole();
        String userMenuLabel = role + " " + firstName;

        for (Object[] m : ALL_MENUS) {
            String label       = (String)  m[0];
            boolean adminOnly  = (boolean) m[2];

            if (adminOnly) {
                String displayLabel = isAdminUser ? "Admin" : userMenuLabel;
                Icon icon = isAdminUser ? resolveIcon("Admin") : userBadgeIcon();
                JButton btn = menuBtn(displayLabel, icon);
                if (!isAdminUser) {
                    btn.addActionListener(e -> {
                        selectedMenu = displayLabel;
                        repaintAllButtons();
                        showPanelForMenu("User");
                    });
                }
                menuButtons.add(btn);
                sidebarPanel.add(btn);
                sidebarPanel.add(vgap(2));
                continue;
            }

            if (!isAdminUser && !isMenuVisible(label)) continue;

            Icon icon = resolveIcon(label);
            JButton btn = menuBtn(label, icon);
            menuButtons.add(btn);
            sidebarPanel.add(btn);
            sidebarPanel.add(vgap(2));
        }

        sidebarPanel.add(Box.createVerticalGlue());
        sidebarPanel.add(hLine(SIDEBAR_W - 24));
        sidebarPanel.add(vgap(8));
        sidebarPanel.add(menuBtn("Sign Out", signOutIcon()));
        sidebarPanel.add(vgap(8));

        sidebarPanel.revalidate();
        sidebarPanel.repaint();
    }

    private String getUserRole() {
        if (currentUser.email != null) {
            String e = currentUser.email.toLowerCase();
            if (e.contains("ceo") || e.contains("owner")) return ROLE_CEO;
        }
        try {
            LocalUserStore store = LocalUserStore.getInstance();
            LocalUserStore.LocalUser lu = store.getUser(currentUser.email);
            if (lu != null) {
                String rn = lu.role.name();
                if (rn.equalsIgnoreCase("DIRECTOR")) return ROLE_CEO;
                if (rn.equalsIgnoreCase("MANAGER"))  return ROLE_MANAGER;
                if (rn.equalsIgnoreCase("SUPERVISOR")) return ROLE_SUPERVISOR;
                return ROLE_EMPLOYEE;
            }
        } catch (Exception ignored) {}
        return ROLE_EMPLOYEE;
    }

    private boolean isAdminRole() {
        String r = getUserRole();
        return ROLE_CEO.equals(r) || ROLE_MANAGER.equals(r);
    }

    private boolean isMenuVisible(String label) {
        String emailKey = label + ":" + currentUser.email.toLowerCase();
        if (menuVisibility.containsKey(emailKey)) return menuVisibility.get(emailKey);
        String roleKey = label + ":ROLE_" + getUserRole();
        if (menuVisibility.containsKey(roleKey)) return menuVisibility.get(roleKey);
        return true;
    }

    private Icon resolveIcon(String label) {
        switch (label) {
            case "Dashboard": return dashboardIcon();
            case "Products-New items": return productIcon();
            case "Purchases-Restock": return purchaseIcon();
            case "Customers-Retailers": return customerIcon();
            case "General-Staff": return customerIcon();
            case "Suppliers-Importers": return supplierIcon();
            case "Reports": return reportIcon();
            case "Admin": return adminIcon();
            case "User": return userBadgeIcon();
            case "Create Order": return orderIcon();
            case "Refund-Products": return refundIcon();
            case "Product-Ledger": return ledgerIcon();
            case "Stock Adjustment & Inventory": return stockIcon();
            case "Invoice Registry": return invoiceIcon();
            case "Receipts": return receiptIcon();
            case "Backup": return backupIcon();
            case "Pending Order": return pendingIcon();
            case "Daily-User Reports": return dailyReportIcon();
            default: return settingsIcon();
        }
    }

    private JPanel buildAvatarPanel() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                int d = AVATAR_D, ox = cx - d/2, oy = cy - d/2;

                for (int i = 6; i >= 1; i--) {
                    float alpha = 0.06f * i;
                    g2.setColor(new Color(50, 130, 255, (int)(255 * alpha)));
                    g2.setStroke(new BasicStroke(i * 1.8f));
                    int pad = i * 2;
                    g2.drawOval(ox - pad, oy - pad, d + pad*2, d + pad*2);
                }

                Shape circle = new Ellipse2D.Float(ox, oy, d, d);
                g2.setColor(new Color(16, 20, 38));
                g2.fill(circle);

                g2.setClip(circle);
                if (avatarImage != null) {
                    g2.drawImage(avatarImage, ox, oy, d, d, null);
                } else {
                    GradientPaint gp = new GradientPaint(ox, oy,
                            new Color(20, 90, 200), ox + d, oy + d, new Color(100, 40, 220));
                    g2.setPaint(gp); g2.fill(circle);
                    g2.setClip(null); g2.setClip(circle);
                    String ini = initials(currentUser.name);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
                    g2.setColor(new Color(220, 232, 255));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(ini, cx - fm.stringWidth(ini)/2,
                            cy + fm.getAscent()/2 - 2);
                }
                g2.setClip(null);

                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(new Color(60, 140, 255, 200));
                g2.draw(circle);
                g2.dispose();
            }
        };
        int sz = AVATAR_D + 24;
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(SIDEBAR_W - 24, sz));
        p.setMaximumSize(new Dimension(SIDEBAR_W - 24, sz));
        p.setMinimumSize(new Dimension(SIDEBAR_W - 24, sz));
        return p;
    }

    private JButton menuBtn(String label, Icon icon) {
        JButton btn = new JButton() {
            boolean hov = false;
            { 
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    public void mouseExited(MouseEvent e) { hov = false; repaint(); }
                }); 
            }
            
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                boolean sel = label.equals(selectedMenu);
                int w = getWidth(), h = getHeight();
                
                if (sel) {
                    g2.setColor(C_MENU_SEL_BG);
                    g2.fillRoundRect(4, 2, w - 8, h - 4, 8, 8);
                    g2.setColor(C_MENU_SEL_BD);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(4, 2, w - 8, h - 4, 8, 8);
                } else if (hov) {
                    g2.setColor(C_MENU_HOV);
                    g2.fillRoundRect(4, 2, w - 8, h - 4, 8, 8);
                }

                if (icon != null) {
                    Graphics iconG = g2.create(12, (h - 18) / 2, 18, 18);
                    icon.paintIcon(this, iconG, 0, 0);
                    iconG.dispose();
                }

                g2.setFont(new Font("Segoe UI", sel ? Font.BOLD : Font.PLAIN, 13));
                g2.setColor(sel ? C_TEXT_PRI : (hov ? C_TEXT_PRI : C_TEXT_SEC));
                FontMetrics fm = g2.getFontMetrics();
                int textX = 38;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, textX, textY);
                
                g2.dispose();
            }
        };
        
        btn.setPreferredSize(new Dimension(SIDEBAR_W - 24, 42));
        btn.setMaximumSize(new Dimension(SIDEBAR_W - 24, 42));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        
        btn.addActionListener(e -> {
            if ("Sign Out".equals(label)) { 
                logout(); 
                return; 
            }
            selectedMenu = label;
            repaintAllButtons();
            showPanelForMenu(label);
        });
        
        return btn;
    }

    private void showPanelForMenu(String menuLabel) {
        contentSection.removeAll();

        if ("Dashboard".equals(menuLabel)) {
            renderDashboard();
        } else if ("User".equals(menuLabel)) {
            renderUserPanel();
        } else {
            String panelClassName = getPanelClassForMenu(menuLabel);
            if (panelClassName != null) {
                JPanel panel = getOrCreatePanel(panelClassName);
                if (panel != null) {
                    contentSection.add(panel, BorderLayout.CENTER);
                } else {
                    showComingSoon(menuLabel);
                }
            } else {
                showComingSoon(menuLabel);
            }
        }

        contentSection.revalidate();
        contentSection.repaint();
    }

    private String getPanelClassForMenu(String menuLabel) {
        for (Object[] m : ALL_MENUS) {
            if (((String) m[0]).equals(menuLabel)) {
                return (String) m[4];
            }
        }
        return null;
    }

    private JPanel getOrCreatePanel(String className) {
        if (panelCache.containsKey(className)) {
            return panelCache.get(className);
        }
        try {
            String fullClassName = "hIMs.App." + className;
            Class<?> clazz = Class.forName(fullClassName);
            java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(GratisData.User.class);
            JPanel panel = (JPanel) constructor.newInstance(currentUser);
            panelCache.put(className, panel);
            return panel;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showComingSoon(String menuLabel) {
        JPanel ph = new JPanel(new GridBagLayout());
        ph.setOpaque(false);
        JLabel lbl = new JLabel(menuLabel + " — Coming soon");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lbl.setForeground(C_TEXT_DIM);
        ph.add(lbl);
        contentSection.add(ph, BorderLayout.CENTER);
    }

    private JPanel buildContent() {
        contentSection = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(new Color(10, 12, 20));
                g2.fillRect(0, 0, getWidth(), getHeight());
                paintRadial(g2, getWidth() * .5f, getHeight() * .25f,
                        getWidth() * .7f, new Color(20, 60, 180, 14), new Color(0, 0, 0, 0));
                g2.dispose();
            }
        };
        contentSection.setOpaque(false);
        contentSection.setBorder(new EmptyBorder(30, 34, 30, 30));
        renderDashboard();
        return contentSection;
    }

    private void renderDashboard() {
        contentSection.removeAll();

        JPanel wrap = new JPanel(new BorderLayout(0, 24));
        wrap.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel pg = new JLabel("Dashboard") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                g2.setColor(new Color(50, 130, 255, 20));
                g2.drawString(t, 2, fm.getAscent() + 2);
                g2.setColor(C_TEXT_PRI);
                g2.drawString(t, 0, fm.getAscent());
                g2.dispose();
            }
        };
        pg.setFont(new Font("Segoe UI", Font.BOLD, 26));
        pg.setForeground(C_TEXT_PRI);

        JLabel sub = new JLabel("Welcome back, " + currentUser.name);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(C_TEXT_DIM);

        JPanel headWrap = new JPanel();
        headWrap.setLayout(new BoxLayout(headWrap, BoxLayout.Y_AXIS));
        headWrap.setOpaque(false);
        headWrap.add(pg); 
        headWrap.add(Box.createVerticalStrut(3)); 
        headWrap.add(sub);
        header.add(headWrap, BorderLayout.WEST);
        wrap.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 3, 16, 0));
        grid.setOpaque(false);
        grid.add(statCard("Today's Sales",  "$1,245",  "+12% vs yesterday",  C_ACCENT_1, salesIcon()));
        grid.add(statCard("Products",        "247",      "12 low in stock",    C_ACCENT_2, productIcon()));
        grid.add(statCard("Customers",       "1,892",    "34 new this week",   new Color(50, 200, 140), customerIcon()));
        wrap.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(C_SURFACE_2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(C_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
                g2.dispose();
            }
        };
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(16, 16, 16, 16));
        
        JLabel activityTitle = new JLabel("Recent Activity");
        activityTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        activityTitle.setForeground(C_TEXT_PRI);
        
        JPanel activityList = new JPanel();
        activityList.setLayout(new BoxLayout(activityList, BoxLayout.Y_AXIS));
        activityList.setOpaque(false);
        activityList.add(createActivityItem("New sale: $45.99 - Cash payment", "2 min ago"));
        activityList.add(Box.createVerticalStrut(8));
        activityList.add(createActivityItem("Product added: Paracetamol 500mg", "15 min ago"));
        activityList.add(Box.createVerticalStrut(8));
        activityList.add(createActivityItem("Customer registered: John Smith", "1 hour ago"));
        
        bottom.add(activityTitle, BorderLayout.NORTH);
        bottom.add(activityList, BorderLayout.CENTER);
        
        wrap.add(bottom, BorderLayout.SOUTH);

        contentSection.add(wrap, BorderLayout.CENTER);
        contentSection.revalidate();
        contentSection.repaint();
    }
    
    private JPanel createActivityItem(String text, String time) {
        JPanel item = new JPanel(new BorderLayout());
        item.setOpaque(false);
        
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(C_TEXT_SEC);
        
        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(C_TEXT_DIM);
        
        item.add(textLabel, BorderLayout.WEST);
        item.add(timeLabel, BorderLayout.EAST);
        
        return item;
    }

    private JPanel statCard(String label, String value, String sub, Color accent, Icon icon) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                int w = getWidth(), h = getHeight();

                g2.setColor(C_SURFACE_2);
                g2.fillRoundRect(0, 0, w, h, 14, 14);

                GradientPaint stripe = new GradientPaint(
                        0, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30),
                        w, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 5));
                g2.setPaint(stripe);
                g2.fillRoundRect(0, 0, w, h, 14, 14);

                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w-1, h-1, 14, 14);

                int cr = 22, cx = 20 + cr, cy = 20 + cr;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
                g2.fillOval(cx - cr, cy - cr, cr*2, cr*2);
                if (icon != null) {
                    Graphics ig = g2.create(cx - 10, cy - 10, 20, 20);
                    icon.paintIcon(null, ig, 0, 0);
                    ig.dispose();
                }
                g2.dispose();
            }
        };
        card.setLayout(null);
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(0, 130));

        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valLbl.setForeground(C_TEXT_PRI);
        valLbl.setBounds(20, 72, 200, 32);

        JLabel lLbl = new JLabel(label.toUpperCase());
        lLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lLbl.setForeground(C_TEXT_SEC);
        lLbl.setBounds(20, 55, 200, 16);

        JLabel subLbl = new JLabel(sub);
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        subLbl.setForeground(C_TEXT_DIM);
        subLbl.setBounds(20, 106, 200, 16);

        card.add(valLbl); card.add(lLbl); card.add(subLbl);
        return card;
    }

    private void renderUserPanel() {
        contentSection.removeAll();

        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);

        JLabel pg = new JLabel("User Profile");
        pg.setFont(new Font("Segoe UI", Font.BOLD, 26));
        pg.setForeground(C_TEXT_PRI);
        pg.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(pg);
        wrap.add(Box.createVerticalStrut(24));

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(C_SURFACE_2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(C_BORDER_GLOW);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(24, 28, 24, 28));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(420, 200));

        String role = getUserRole();
        String firstName = currentUser.name != null && !currentUser.name.isBlank()
            ? currentUser.name.trim().split("\\s+")[0] : currentUser.email.split("@")[0];

        JLabel rolePill = new JLabel(role) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                Color pill = ROLE_CEO.equals(role)       ? new Color(255, 200, 50)  :
                             ROLE_MANAGER.equals(role)   ? new Color(50, 180, 255)  :
                             ROLE_SUPERVISOR.equals(role)? new Color(120, 80, 255)  :
                                                           new Color(80, 200, 140);
                g2.setColor(new Color(pill.getRed(), pill.getGreen(), pill.getBlue(), 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(pill);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(pill);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        rolePill.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rolePill.setAlignmentX(Component.LEFT_ALIGNMENT);
        rolePill.setPreferredSize(new Dimension(100, 26));
        rolePill.setMaximumSize(new Dimension(100, 26));

        JLabel greeting = new JLabel(role + " " + firstName);
        greeting.setFont(new Font("Segoe UI", Font.BOLD, 16));
        greeting.setForeground(C_ACCENT_1);
        greeting.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fullNameLbl = new JLabel(currentUser.name != null ? currentUser.name : firstName);
        fullNameLbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        fullNameLbl.setForeground(C_TEXT_PRI);
        fullNameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel emailLbl2 = new JLabel(currentUser.email);
        emailLbl2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        emailLbl2.setForeground(C_TEXT_SEC);
        emailLbl2.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(rolePill);
        card.add(Box.createVerticalStrut(10));
        card.add(greeting);
        card.add(Box.createVerticalStrut(4));
        card.add(fullNameLbl);
        card.add(Box.createVerticalStrut(6));
        card.add(emailLbl2);

        wrap.add(card);
        contentSection.add(wrap, BorderLayout.CENTER);
        contentSection.revalidate();
        contentSection.repaint();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ICONS
    // ═══════════════════════════════════════════════════════════════════════════
    private static Icon makeIcon(java.util.function.Consumer<Graphics2D> painter) {
        return new Icon() {
            public int getIconWidth()  { return 18; }
            public int getIconHeight() { return 18; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create(x, y, 18, 18);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                painter.accept(g2);
                g2.dispose();
            }
        };
    }
    
    private static Icon dashboardIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(50, 140, 255));
            g.setStroke(new BasicStroke(1.5f));
            int[] xPoints = {9, 2, 2, 16, 16, 9};
            int[] yPoints = {2, 7, 15, 15, 7, 2};
            g.drawPolygon(xPoints, yPoints, 6);
            g.drawRect(6, 9, 6, 5);
        });
    }
    
    private static Icon productIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(80, 180, 255)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(2, 5, 14, 11); 
            g.drawLine(2, 9, 16, 9);
            g.drawLine(9, 5, 9, 16); 
            g.drawArc(5, 2, 8, 6, 0, 180);
        });
    }
    
    private static Icon purchaseIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(80, 200, 140)); 
            g.setStroke(new BasicStroke(1.4f));
            int[] cx = {1,4,4,14,16,13,3}, cy = {2,2,4,4,2,14,14};
            g.drawPolyline(cx, cy, 7);
            g.drawOval(5, 14, 4, 4); 
            g.drawOval(11, 14, 4, 4);
        });
    }
    
    private static Icon customerIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(80, 200, 160)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(4, 1, 10, 10);
            g.drawArc(1, 11, 16, 10, 0, 180);
        });
    }
    
    private static Icon supplierIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(255, 165, 50)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(1, 7, 11, 7); 
            g.drawRect(12, 10, 5, 4);
            g.drawLine(12, 7, 14, 7); 
            g.drawLine(14, 7, 17, 10);
            g.drawOval(2, 13, 4, 4); 
            g.drawOval(11, 13, 4, 4);
        });
    }
    
    private static Icon reportIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(180, 130, 255)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(3, 1, 12, 16);
            g.drawLine(6, 5, 12, 5); 
            g.drawLine(6, 8, 12, 8);
            g.drawLine(6, 11, 10, 11);
            g.fillRect(5, 12, 2, 3); 
            g.fillRect(8, 10, 2, 5); 
            g.fillRect(11, 8, 2, 7);
        });
    }
    
    private static Icon settingsIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(150, 150, 170));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(4, 4, 10, 10);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int x1 = (int)(9 + 10 * Math.cos(angle));
                int y1 = (int)(9 + 10 * Math.sin(angle));
                int x2 = (int)(9 + 14 * Math.cos(angle));
                int y2 = (int)(9 + 14 * Math.sin(angle));
                g.drawLine(x1, y1, x2, y2);
            }
        });
    }
    
    private static Icon signOutIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(220, 80, 80)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawArc(3, 3, 12, 12, 60, 240);
            g.drawLine(9, 1, 9, 9);
        });
    }
    
    private static Icon salesIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(80, 160, 255)); 
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(2, 14, 6, 8); 
            g.drawLine(6, 8, 10, 11);
            g.drawLine(10, 11, 16, 4);
            g.drawLine(13, 4, 16, 4); 
            g.drawLine(16, 4, 16, 7);
        });
    }

    private static Icon adminIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(255, 200, 50));
            g.setStroke(new BasicStroke(1.4f));
            int[] xs = {9,2,2,9,16,16};
            int[] ys = {1,4,11,17,11,4};
            g.drawPolygon(xs, ys, 6);
            g.drawLine(6, 8, 8, 11); g.drawLine(8, 11, 12, 6);
        });
    }

    private static Icon userBadgeIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(100, 180, 255));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(5, 1, 8, 8);
            g.drawArc(2, 10, 14, 8, 0, 180);
            g.drawRoundRect(10, 8, 7, 5, 3, 3);
        });
    }

    private static Icon orderIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(80, 220, 180));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(2, 3, 14, 13);
            g.drawLine(6, 7, 12, 7); g.drawLine(6, 10, 12, 10); g.drawLine(6, 13, 10, 13);
            g.drawLine(12, 0, 12, 6); g.drawLine(10, 2, 14, 2);
        });
    }

    private static Icon refundIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(255, 120, 100));
            g.setStroke(new BasicStroke(1.4f));
            g.drawArc(2, 2, 14, 14, 30, 300);
            int[] ax = {2,6,2}; int[] ay = {5,5,9};
            g.drawPolyline(ax, ay, 3);
            g.drawLine(9, 5, 9, 10); g.drawLine(7, 12, 11, 12);
        });
    }

    private static Icon ledgerIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(180, 220, 120));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(2, 1, 14, 16);
            g.drawLine(5, 5, 13, 5); g.drawLine(5, 8, 13, 8);
            g.drawLine(5, 11, 13, 11); g.drawLine(5, 14, 9, 14);
            g.drawLine(2, 4, 0, 4); g.drawLine(2, 8, 0, 8);
        });
    }

    private static Icon stockIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(255, 160, 80));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(1, 8, 7, 8); g.drawRect(10, 5, 7, 11);
            g.drawLine(9, 2, 9, 6); g.drawLine(7, 4, 9, 2); g.drawLine(11, 4, 9, 2);
        });
    }

    private static Icon invoiceIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(140, 200, 255));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(3, 1, 12, 16);
            g.drawLine(6, 5, 12, 5); g.drawLine(6, 8, 12, 8); g.drawLine(6, 11, 10, 11);
            g.drawLine(6, 14, 8, 14);
            g.fillOval(11, 12, 5, 5);
            g.setColor(new Color(10, 12, 20));
            g.drawLine(13, 13, 13, 16); g.drawLine(12, 15, 14, 15);
        });
    }

    private static Icon receiptIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(160, 255, 200));
            g.setStroke(new BasicStroke(1.4f));
            int[] rx = {2,2,4,6,8,10,12,14,16,16};
            int[] ry = {1,17,15,17,15,17,15,17,15,1};
            g.drawPolyline(rx, ry, 10);
            g.drawLine(2,1,16,1);
            g.drawLine(5,5,13,5); g.drawLine(5,8,13,8); g.drawLine(5,11,10,11);
        });
    }

    private static Icon backupIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(150, 150, 220));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 14, 14);
            g.drawLine(9, 5, 9, 13); g.drawLine(6, 10, 9, 13); g.drawLine(12, 10, 9, 13);
            g.drawArc(5, 5, 8, 8, 45, 270);
        });
    }

    private static Icon pendingIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(255, 200, 80));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 14, 14);
            g.drawLine(9, 5, 9, 9); g.drawLine(9, 9, 12, 12);
            g.drawLine(6, 1, 6, 3); g.drawLine(12, 1, 12, 3);
        });
    }

    private static Icon dailyReportIcon() {
        return makeIcon(g -> {
            g.setColor(new Color(100, 220, 255));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(1, 3, 16, 14);
            g.drawLine(1, 7, 17, 7);
            g.drawLine(6, 3, 6, 1); g.drawLine(12, 3, 12, 1);
            g.drawLine(4, 11, 7, 11); g.drawLine(4, 13, 8, 13);
            g.fillRect(10, 10, 2, 4); g.fillRect(13, 8, 2, 6);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    private Graphics2D prep(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,        RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,   RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    private void paintRadial(Graphics2D g2, float cx, float cy, float r, Color c0, Color c1) {
        RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Float(cx, cy), r, new float[]{0f, 1f}, new Color[]{c0, c1});
        g2.setPaint(rg);
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private Component vgap(int h) {
        return Box.createVerticalStrut(h);
    }

    private JPanel hLine(int width) {
        JPanel l = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = prep(g);
                g2.setColor(C_SEP); 
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        l.setOpaque(false);
        l.setMaximumSize(new Dimension(width, 1));
        l.setPreferredSize(new Dimension(width, 1));
        return l;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] p = name.trim().split("\\s+");
        return (p.length >= 2)
                ? ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase()
                : ("" + name.charAt(0)).toUpperCase();
    }

    private String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : (s != null ? s : "");
    }

    private void repaintAllButtons() {
        repaintIn(mainPanel);
    }

    private void repaintIn(Container c) {
        for (Component ch : c.getComponents()) {
            if (ch instanceof JButton) ch.repaint();
            if (ch instanceof Container) repaintIn((Container) ch);
        }
    }

    private void loadAvatar() {
        new Thread(() -> {
            BufferedImage img = null;

            img = tryFetchImage("https://lh3.googleusercontent.com/a/" + currentUser.email);

            if (img == null) {
                try {
                    String hash = md5(currentUser.email.trim().toLowerCase());
                    img = tryFetchImage("https://www.gravatar.com/avatar/" + hash + "?s=" + AVATAR_D + "&d=404");
                } catch (Exception ignored) {}
            }

            if (img == null) {
                try {
                    String name = currentUser.name.trim().replace(" ", "+");
                    img = tryFetchImage("https://ui-avatars.com/api/?name=" + name
                            + "&size=" + AVATAR_D
                            + "&background=1A5CCC&color=ffffff&bold=true&format=png");
                } catch (Exception ignored) {}
            }

            final BufferedImage finalImg = img;
            SwingUtilities.invokeLater(() -> {
                if (finalImg != null) {
                    avatarImage = finalImg;
                    if (avatarPanel != null) avatarPanel.repaint();
                }
            });
        }, "avatar-fetch").start();
    }

    private BufferedImage tryFetchImage(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "image/webp,image/png,image/*");

            int code = conn.getResponseCode();
            String contentType = conn.getContentType();

            if (code == 200 && contentType != null && contentType.startsWith("image/")) {
                BufferedImage img = ImageIO.read(conn.getInputStream());
                if (img != null && img.getWidth() > 1) return img;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return "0".repeat(32); }
    }

    private void checkNet() {
        new Thread(() -> {
            Runnable poll = () -> {
                boolean on = false;
                for (String h : new String[]{"8.8.8.8", "1.1.1.1", "142.250.200.46"}) {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(h, 443), 2500);
                        on = true; break;
                    } catch (Exception ignored) {}
                }
                final boolean isOn = on;
                SwingUtilities.invokeLater(() -> {
                    Color c = isOn ? C_ONLINE : C_OFFLINE;
                    statusDot.setForeground(c);
                    statusText.setForeground(c);
                    statusText.setText(isOn ? "Online" : "Offline");
                });
            };
            poll.run();
            new Timer(true).scheduleAtFixedRate(new TimerTask(){
                @Override public void run(){ poll.run(); }
            }, 30_000, 30_000);
        }, "net-check").start();
    }

    private void startClock() {
        new Timer(true).scheduleAtFixedRate(new TimerTask(){
            @Override public void run(){
                SwingUtilities.invokeLater(() -> {
                    timeLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date()));
                    dateLabel.setText(new SimpleDateFormat("EEE, dd MMM yyyy").format(new Date()));
                });
            }
        }, 0, 1000);
    }

    private void logout() {
        int c = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to sign out?", "Sign Out",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (c == JOptionPane.YES_OPTION) {
            dispose();
        }
    }

    private void enableDrag() {
        addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){
                if (e.getY() <= TOP_H) dragPoint = e.getPoint();
            }
            public void mouseReleased(MouseEvent e){ dragPoint = null; }
        });
        addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){
                if (dragPoint != null && !isMaximized){
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - dragPoint.x, p.y - dragPoint.y);
                }
            }
        });
    }
}