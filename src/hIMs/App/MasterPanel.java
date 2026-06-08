package hIMs.App;

import com.toedter.calendar.JDateChooser;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.Rectangle;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.math.BigDecimal;
import java.awt.Font;
import java.util.prefs.Preferences;

public class MasterPanel extends JFrame {

    // ── Singleton: prevent opening dialog twice ──────────────────
    private static MasterPanel instance = null;
    private static JDialog masterDialog = null;

    public static void open() {
        if (instance != null && masterDialog != null && masterDialog.isVisible()) {
            masterDialog.toFront();
            masterDialog.requestFocus();
            return;
        }
        instance = new MasterPanel();
    }

    // ══════════════════════════════════════════════════════════════
    //  BREVO CREDENTIALS
    // ══════════════════════════════════════════════════════════════
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String BREVO_API_KEY = "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";
    private static final String FROM_EMAIL    = "gratispointofsale@gmail.com";
    private static final String FROM_NAME     = "Gratis POS";

    // ══════════════════════════════════════════════════════════════
    //  GLASSMORPHISM COLOR PALETTE
    // ══════════════════════════════════════════════════════════════
    private static final Color BG_BASE     = new Color(8,  10, 22);
    private static final Color BG_GLASS    = new Color(18, 22, 45, 210);
    private static final Color BG_GLASS2   = new Color(25, 30, 60, 180);
    private static final Color BG_INPUT    = new Color(30, 36, 70, 200);
    private static final Color ACCENT      = new Color(99,  179, 255);
    private static final Color ACCENT2     = new Color(138, 99,  255);
    private static final Color SUCCESS     = new Color(52,  211, 153);
    private static final Color DANGER      = new Color(255, 100, 100);
    private static final Color WARNING     = new Color(255, 198,  60);
    private static final Color TEXT_PRI    = new Color(230, 235, 255);
    private static final Color TEXT_SEC    = new Color(130, 145, 195);
    private static final Color BORDER_C    = new Color(60,  80, 140, 120);

    private static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD,  26);
    private static final Font FONT_HEAD   = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font FONT_BODY   = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SMALL  = new Font("Segoe UI", Font.PLAIN, 11);

    private static final Logger LOGGER = Logger.getLogger(MasterPanel.class.getName());

    // ── State ──────────────────────────────────────────────────────
    private JLabel totalProductsVal, todaySalesVal, lowStockVal, expiringSoonVal;
    private JTextField[] settingsFields;

    // Settings fields
    private String businessName = "", registrationNo = "", tinNo = "",
            mobileNo = "", emailAddr = "", website = "", address = "", currency = "";

    private DefaultListModel<String> recipientListModel;
    private JList<String> recipientList;
    private JCheckBox[] autoSendCheckboxes;
    private DefaultTableModel mailHistoryModel;
    private Timer autoSendTimer;
    private JComboBox<String> autoSendTimeCombo;
    private JCheckBox autoSendEnabledChk;

    // ── Send-now checkboxes (separate from auto-send) ────────────
    private JCheckBox[] manualReportChecks;
    private JDateChooser[] manualDateRange;

    // ── Offline queue for missed auto-sends ─────────────────────
    private static final String MISSED_SENDS_KEY = "missedAutoSends";

    private static final String[] REPORT_NAMES = {
        "Daily Sale Report", "Weekly Sale Report", "Monthly Sale Report",
        "Price List Report", "Fast Moving Report", "Dead Stock Report",
        "Purchase Received Report", "Out of Stock Report",
        "Near Expire Report", "Expired Stock Report", "Profit Analysis"
    };

    // ── Tab reference so we can close it ───────────────────────
    private JTabbedPane mainTabs;

    public MasterPanel() {
        loadSettings();
        SwingUtilities.invokeLater(this::initUI);
    }

    private void initUI() {
        masterDialog = new JDialog();
        masterDialog.setTitle("Gratis Report Master Panel");

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = (int)(screen.width  * 0.92);
        int h = (int)(screen.height * 0.92);
        masterDialog.setSize(w, h);
        masterDialog.setMinimumSize(new Dimension(1000, 650));
        masterDialog.setLocationRelativeTo(null);
        masterDialog.setModal(false);
        masterDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        masterDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAutoSendTimer();
                masterDialog.setVisible(false);
                masterDialog.dispose();
                masterDialog = null;
                instance = null;
                notifyParentPanelClosed();
            }
        });

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(6, 8, 20),
                        getWidth(), getHeight(), new Color(14, 20, 50));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                RadialGradientPaint rg = new RadialGradientPaint(
                        new java.awt.geom.Point2D.Float(200, 150), 400,
                        new float[]{0f, 1f},
                        new Color[]{new Color(60, 100, 200, 35), new Color(0, 0, 0, 0)});
                g2.setPaint(rg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        root.add(buildHeader(), BorderLayout.NORTH);
        mainTabs = buildStyledTabs();
        mainTabs.addTab("📊  Reports",        buildReportsTab());
        mainTabs.addTab("✉   Send Reports",   buildSendReportsTab());
        mainTabs.addTab("📜  Mail History",   buildMailHistoryTab());
        mainTabs.addTab("📝  Adjustment Log", buildAdjustmentLogTab());
        mainTabs.addTab("⚙️  Settings",       buildSettingsTab());
        root.add(mainTabs, BorderLayout.CENTER);

        masterDialog.setContentPane(root);
        masterDialog.setVisible(true);
        loadDashboardStats();
        flushMissedAutoSends();
        loadAutoSendPrefs();
    }

    private void notifyParentPanelClosed() {
        System.setProperty("masterPanel.closed", "true");
    }

    // ══════════════════════════════════════════════════════════════
    //  HEADER
    // ══════════════════════════════════════════════════════════════

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout(24, 0));
        h.setOpaque(false);
        h.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JPanel brand = new JPanel(new BorderLayout(0, 3));
        brand.setOpaque(false);
        JLabel title = new JLabel("GRATIS  MASTER  PANEL");
        title.setFont(FONT_TITLE);
        title.setForeground(ACCENT);
        JLabel sub = new JLabel("Gratis Sending 📈 Report Panel 🕐 Cloud Enabled");
        sub.setFont(FONT_SMALL);
        sub.setForeground(TEXT_SEC);
        brand.add(title, BorderLayout.NORTH);
        brand.add(sub,   BorderLayout.SOUTH);

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        totalProductsVal = addStatCard(stats, "📦  Total Products", "—", ACCENT);
        todaySalesVal    = addStatCard(stats, "💰  Today's Sales",  "—", SUCCESS);
        lowStockVal      = addStatCard(stats, "⚠️  Low Stock",      "—", DANGER);
        expiringSoonVal  = addStatCard(stats, "🕐  Expiring Soon",  "—", WARNING);

        h.add(brand, BorderLayout.WEST);
        h.add(stats, BorderLayout.CENTER);
        return h;
    }

    private JLabel addStatCard(JPanel parent, String labelText, String val, Color accent) {
        GlassPanel card = new GlassPanel(accent);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(TEXT_SEC);
        JLabel valLbl = new JLabel(val);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valLbl.setForeground(accent);
        valLbl.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(lbl,    BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        parent.add(card);
        return valLbl;
    }

    private void loadDashboardStats() {
        new Thread(() -> {
            try {
                int products = Database.getTotalProductCount();
                String today = LocalDate.now().toString();
                BigDecimal ts = Database.getTotalSales(today, today);
                int low = Database.getLowStockProducts().size();
                int exp = queryExpiringCount();
                SwingUtilities.invokeLater(() -> {
                    totalProductsVal.setText(String.valueOf(products));
                    String cur = currency.isEmpty() ? "" : currency + " ";
                    todaySalesVal.setText(cur + fmt(ts));
                    lowStockVal.setText(String.valueOf(low));
                    expiringSoonVal.setText(String.valueOf(exp));
                });
            } catch (SQLException ex) { LOGGER.log(Level.WARNING, "Dashboard stats", ex); }
        }, "dash-stats").start();
    }

    private int queryExpiringCount() {
        try (PreparedStatement s = Database.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM products WHERE client_id=? AND expire_date IS NOT NULL " +
                "AND julianday(expire_date)-julianday('now') BETWEEN 0 AND 30")) {
            s.setString(1, Database.getCurrentClientId());
            ResultSet r = s.executeQuery();
            return r.next() ? r.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0.00" : String.format("%,.2f", v);
    }

    private String fmtCurrency(BigDecimal v) {
        String cur = currency.isEmpty() ? "" : currency + " ";
        return cur + fmt(v);
    }

    // ══════════════════════════════════════════════════════════════
    //  GLASS PANEL
    // ══════════════════════════════════════════════════════════════

    static class GlassPanel extends JPanel {
        private final Color accentColor;
        GlassPanel(Color accent) {
            this.accentColor = accent;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 14;
            g2.setColor(BG_GLASS);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            GradientPaint sheen = new GradientPaint(0, 0, new Color(255, 255, 255, 18),
                    0, getHeight() / 2, new Color(255, 255, 255, 0));
            g2.setPaint(sheen);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() / 2, arc, arc);
            g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private GlassPanel glassPanel(LayoutManager lm) {
        GlassPanel p = new GlassPanel(BORDER_C);
        p.setLayout(lm);
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  REPORTS TAB (UI only - brief methods)
    // ══════════════════════════════════════════════════════════════

    private JPanel buildReportsTab() {
        JPanel outer = opaquePanel(new BorderLayout(0, 12));
        String[][] btns = {
            {"💰","Cash Sale","cash_sale"}, {"📋","Price List","price_list"},
            {"⚡","Fast Moving","fast_moving"}, {"💀","Dead Stock","dead_stock"},
            {"📥","Purchase Rcv","purchase_receive"}, {"📦","Out of Stock","out_of_stock"},
            {"🕐","Near Expire","near_expire"}, {"❌","Expired Stock","expired_stock"},
            {"📊","Profit Analysis","profit_analysis"}, {"📈","Top Products","top_products"}
        };
        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navRow.setOpaque(false);
        CardLayout cl = new CardLayout();
        JPanel cards = opaquePanel(cl);
        cards.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        ButtonGroup bg = new ButtonGroup();
        for (String[] b : btns) {
            JToggleButton tb = navToggle(b[0] + " " + b[1]);
            bg.add(tb);
            String key = b[2];
            tb.addActionListener(e -> cl.show(cards, key));
            navRow.add(tb);
        }
        cards.add(buildCashSalePanel(),        "cash_sale");
        cards.add(buildPriceListPanel(),       "price_list");
        cards.add(buildFastMovingPanel(),      "fast_moving");
        cards.add(buildDeadStockPanel(),       "dead_stock");
        cards.add(buildPurchaseReceivePanel(), "purchase_receive");
        cards.add(buildOutOfStockPanel(),      "out_of_stock");
        cards.add(buildNearExpirePanel(),      "near_expire");
        cards.add(buildExpiredStockPanel(),    "expired_stock");
        cards.add(buildProfitAnalysisPanel(),  "profit_analysis");
        cards.add(buildTopProductsPanel(),     "top_products");
        ((JToggleButton) navRow.getComponent(0)).setSelected(true);
        outer.add(navRow, BorderLayout.NORTH);
        outer.add(cards,  BorderLayout.CENTER);
        return outer;
    }

    private JPanel reportContainer(String title, JPanel filters, JTable table, JPanel summary) {
        GlassPanel p = new GlassPanel(ACCENT);
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel hdr = opaquePanel(new BorderLayout());
        JLabel lbl = new JLabel(title);
        lbl.setFont(FONT_HEAD);
        lbl.setForeground(ACCENT);
        JButton sendBtn   = emailButton("✉  Send Report");
        JButton exportBtn = actionButton("⬇  Export PDF");
        sendBtn.addActionListener(e -> {
            if (!settingsComplete()) {
                showError("Please complete all business settings before sending reports.");
                mainTabs.setSelectedIndex(4);
                return;
            }
            sendCurrentReportByEmail(table, title);
        });
        exportBtn.addActionListener(e -> exportTableToPDF(table, title.replaceAll("[^A-Za-z0-9_]", "_")));
        JPanel btnRow = opaquePanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.add(exportBtn);
        btnRow.add(sendBtn);
        hdr.add(lbl,    BorderLayout.WEST);
        hdr.add(btnRow, BorderLayout.EAST);
        JPanel body = opaquePanel(new BorderLayout(0, 8));
        if (filters != null) body.add(filters, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(new Color(15, 18, 40, 180));
        body.add(scroll, BorderLayout.CENTER);
        if (summary != null) body.add(summary, BorderLayout.SOUTH);
        p.add(hdr,  BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCashSalePanel() {
        String[] cols = {"Product","Category","Sell Price","Qty","Sub Total","Sold By","Date"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JLabel totLbl  = summaryLabel("Total: 0");
        JLabel discLbl = summaryLabel("Discount: 0");
        JLabel netLbl  = summaryLabel("Net: 0");
        JLabel txnLbl  = summaryLabel("Txns: 0");
        JPanel sumRow  = summaryRow(totLbl, discLbl, netLbl, txnLbl);
        JDateChooser[] dr = new JDateChooser[2];
        JPanel filters = dateRangeFilter(dr,
            () -> loadCashSale(fmtDate(dr[0]), fmtDate(dr[1]), mdl, totLbl, discLbl, netLbl, txnLbl));
        loadCashSale(defaultFrom(), defaultTo(), mdl, totLbl, discLbl, netLbl, txnLbl);
        return reportContainer("💰 Cash Sale Details", filters, tbl, sumRow);
    }

    private JPanel buildPriceListPanel() {
        String[] cols = {"Product","Category","Cost","Sell Price","Stock","Vendor","Margin %"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> catCmb = new JComboBox<>();
        catCmb.addItem("All Categories");
        try { for (String c : Database.getAllCategories()) catCmb.addItem(c); } catch (SQLException ignored) {}
        styleCombo(catCmb);
        JPanel f = filterRow(label("Category:"), catCmb,
            actionButton("🔄 Refresh", () -> loadPriceList(mdl, (String)catCmb.getSelectedItem())));
        loadPriceList(mdl, "All Categories");
        return reportContainer("📋 Price List Report", f, tbl, null);
    }

    private JPanel buildFastMovingPanel() {
        String[] cols = {"Product","Category","Qty Sold","Revenue","Last Sale","Rank"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> per = styledCombo("Last 7 Days","Last 30 Days","Last 90 Days");
        JPanel f = filterRow(label("Period:"), per,
            actionButton("🔄 Refresh", () -> loadFastMoving(mdl, (String)per.getSelectedItem())));
        loadFastMoving(mdl, "Last 30 Days");
        return reportContainer("⚡ Fast Moving Products", f, tbl, null);
    }

    private JPanel buildDeadStockPanel() {
        String[] cols = {"Product","Category","Stock","Cost Price","Total Value","Last Sale"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> per = styledCombo("90 Days","180 Days","365 Days");
        JPanel f = filterRow(label("No sales for:"), per,
            actionButton("🔄 Refresh", () -> loadDeadStock(mdl, (String)per.getSelectedItem())));
        loadDeadStock(mdl, "90 Days");
        return reportContainer("💀 Dead Stock Report", f, tbl, null);
    }

    private JPanel buildPurchaseReceivePanel() {
        String[] cols = {"Date","Product","Category","Vendor","Qty","Cost Price","Total"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JLabel totLbl = summaryLabel("Total Received: 0");
        JDateChooser[] dr = new JDateChooser[2];
        JPanel f = dateRangeFilter(dr,
            () -> loadPurchaseReceive(fmtDate(dr[0]), fmtDate(dr[1]), mdl, totLbl));
        loadPurchaseReceive(defaultFrom(), defaultTo(), mdl, totLbl);
        return reportContainer("📥 Purchase Receive", f, tbl, summaryRow(totLbl));
    }

    private JPanel buildOutOfStockPanel() {
        String[] cols = {"Product","Category","Vendor","Cost Price","Sell Price","Last Received","Days Out"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JPanel f = filterRow(actionButton("🔄 Refresh", () -> loadOutOfStock(mdl)));
        loadOutOfStock(mdl);
        return reportContainer("📦 Out of Stock", f, tbl, null);
    }

    private JPanel buildNearExpirePanel() {
        String[] cols = {"Product","Category","Expire Date","Stock","Sell Price","Days Left","Status"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> per = styledCombo("30 Days","60 Days","90 Days");
        JPanel f = filterRow(label("Expiring within:"), per,
            actionButton("🔄 Refresh", () -> loadNearExpire(mdl, (String)per.getSelectedItem())));
        loadNearExpire(mdl, "30 Days");
        return reportContainer("🕐 Near Expiry", f, tbl, null);
    }

    private JPanel buildExpiredStockPanel() {
        String[] cols = {"Product","Category","Expire Date","Stock","Cost Price","Loss Value","Days Overdue"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JPanel f = filterRow(actionButton("🔄 Refresh", () -> loadExpiredStock(mdl)));
        loadExpiredStock(mdl);
        return reportContainer("❌ Expired Stock", f, tbl, null);
    }

    private JPanel buildProfitAnalysisPanel() {
        String[] cols = {"Product","Category","Qty Sold","Revenue","Cost","Profit","Margin %"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JLabel revLbl = summaryLabel("Revenue: 0");
        JLabel cstLbl = summaryLabel("Cost: 0");
        JLabel prfLbl = summaryLabel("Profit: 0");
        JDateChooser[] dr = new JDateChooser[2];
        JPanel f = dateRangeFilter(dr,
            () -> loadProfitAnalysis(fmtDate(dr[0]), fmtDate(dr[1]), mdl, revLbl, cstLbl, prfLbl));
        loadProfitAnalysis(defaultFrom(), defaultTo(), mdl, revLbl, cstLbl, prfLbl);
        return reportContainer("📊 Profit Analysis", f, tbl, summaryRow(revLbl, cstLbl, prfLbl));
    }

    private JPanel buildTopProductsPanel() {
        String[] cols = {"Rank","Product","Category","Qty Sold","Revenue","Profit"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> per = styledCombo("This Month","Last Month","Last 3 Months","This Year");
        JSpinner lim = new JSpinner(new SpinnerNumberModel(10, 5, 50, 5));
        lim.setPreferredSize(new Dimension(60, 30));
        styleSpinner(lim);
        JPanel f = filterRow(label("Period:"), per, label("Top:"), lim,
            actionButton("🔄 Refresh",
                () -> loadTopProducts(mdl, (String)per.getSelectedItem(), (int)lim.getValue())));
        loadTopProducts(mdl, "This Month", 10);
        return reportContainer("📈 Top Products", f, tbl, null);
    }

    // ══════════════════════════════════════════════════════════════
    //  SEND REPORTS TAB
    // ══════════════════════════════════════════════════════════════

    private JPanel buildSendReportsTab() {
        JPanel outer = opaquePanel(new BorderLayout(14, 14));
        outer.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildRecipientPanel(), buildAutoSendPanel());
        split.setDividerLocation(500);
        split.setBackground(new Color(0, 0, 0, 0));
        split.setBorder(null);
        split.setOpaque(false);
        outer.add(split, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildRecipientPanel() {
        GlassPanel p = new GlassPanel(ACCENT);
        p.setLayout(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel t = new JLabel("📬  Email Recipients");
        t.setFont(FONT_HEAD);
        t.setForeground(ACCENT);
        JTextField emailTf = inputField("email@example.com");
        JTextField nameTf  = inputField("Display Name (optional)");
        JButton addBtn = accentButton("＋  Add Recipient", ACCENT);
        addBtn.addActionListener(e -> {
            String em = emailTf.getText().trim();
            String nm = nameTf.getText().trim();
            if (!em.contains("@") || em.isEmpty()) { showError("Enter a valid email address."); return; }
            String entry = nm.isEmpty() ? em : nm + " <" + em + ">";
            if (!recipientListModel.contains(entry)) { recipientListModel.addElement(entry); saveRecipients(); }
            emailTf.setText(""); nameTf.setText("");
        });
        JPanel addRow = opaquePanel(new BorderLayout(8, 0));
        JPanel fields = opaquePanel(new GridLayout(1, 2, 8, 0));
        fields.add(emailTf); fields.add(nameTf);
        addRow.add(fields, BorderLayout.CENTER);
        addRow.add(addBtn, BorderLayout.EAST);
        recipientListModel = new DefaultListModel<>();
        loadRecipients();
        recipientList = new JList<>(recipientListModel);
        recipientList.setBackground(BG_INPUT);
        recipientList.setForeground(TEXT_PRI);
        recipientList.setFont(FONT_BODY);
        recipientList.setSelectionBackground(ACCENT.darker());
        recipientList.setFixedCellHeight(36);
        recipientList.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JScrollPane scroll = new JScrollPane(recipientList);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        scroll.setOpaque(false);
        JButton removeBtn = accentButton("✕  Remove Selected", DANGER);
        removeBtn.addActionListener(e -> {
            int i = recipientList.getSelectedIndex();
            if (i != -1) { recipientListModel.remove(i); saveRecipients(); }
        });
        JLabel info = new JLabel("Reports are sent as PDF attachments via Brevo to all recipients above.");
        info.setFont(FONT_SMALL);
        info.setForeground(TEXT_SEC);
        JPanel bot = opaquePanel(new BorderLayout(8, 4));
        bot.add(removeBtn, BorderLayout.WEST);
        bot.add(info,      BorderLayout.CENTER);
        p.add(t, BorderLayout.NORTH);
        JPanel body = opaquePanel(new BorderLayout(0, 10));
        body.add(addRow, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(bot,    BorderLayout.SOUTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildAutoSendPanel() {
        JPanel outer = opaquePanel(new BorderLayout(0, 14));
        outer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        GlassPanel sendNow = new GlassPanel(SUCCESS);
        sendNow.setLayout(new BorderLayout(0, 8));
        sendNow.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel snTitle = new JLabel("✉  Send Reports Now");
        snTitle.setFont(FONT_HEAD);
        snTitle.setForeground(SUCCESS);
        JPanel checkGrid = opaquePanel(new GridLayout(0, 2, 6, 4));
        manualReportChecks = new JCheckBox[REPORT_NAMES.length];
        for (int i = 0; i < REPORT_NAMES.length; i++) {
            manualReportChecks[i] = styledCheck(REPORT_NAMES[i]);
            checkGrid.add(manualReportChecks[i]);
        }
        manualDateRange = new JDateChooser[2];
        JPanel drRow = dateRangeFilter(manualDateRange, () -> {});
        JButton sendBtn = accentButton("✉  Send Selected Reports", SUCCESS);
        sendBtn.setPreferredSize(new Dimension(260, 40));
        sendBtn.addActionListener(e -> {
            if (!settingsComplete()) {
                showError("Please complete all business settings before sending reports.\n\nGo to the Settings tab and fill in all fields.");
                mainTabs.setSelectedIndex(4);
                return;
            }
            List<String> sel = new ArrayList<>();
            for (JCheckBox c : manualReportChecks) if (c.isSelected()) sel.add(c.getText());
            if (sel.isEmpty()) { showError("Select at least one report."); return; }
            if (recipientListModel.isEmpty()) { showError("No recipients added."); return; }
            sendSelectedReports(sel, fmtDate(manualDateRange[0]), fmtDate(manualDateRange[1]));
        });
        JPanel snBody = opaquePanel(new BorderLayout(0, 6));
        snBody.add(drRow,     BorderLayout.NORTH);
        snBody.add(checkGrid, BorderLayout.CENTER);
        snBody.add(sendBtn,   BorderLayout.SOUTH);
        sendNow.add(snTitle,  BorderLayout.NORTH);
        sendNow.add(snBody,   BorderLayout.CENTER);
        GlassPanel autoBlock = new GlassPanel(ACCENT2);
        autoBlock.setLayout(new BorderLayout(0, 0));
        autoBlock.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel asTitle = new JLabel("⏰  Auto-Send Scheduler");
        asTitle.setFont(FONT_HEAD);
        asTitle.setForeground(ACCENT2);
        asTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JPanel asContent = opaquePanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
        gc.insets = new Insets(4, 0, 4, 0);
        autoSendEnabledChk = new JCheckBox("Enable Daily Auto-Send");
        autoSendEnabledChk.setFont(new Font("Segoe UI", Font.BOLD, 13));
        autoSendEnabledChk.setForeground(ACCENT2);
        autoSendEnabledChk.setOpaque(false);
        autoSendEnabledChk.addActionListener(e -> {
            if (autoSendEnabledChk.isSelected()) {
                if (!settingsComplete()) {
                    showError("Please complete all business settings before enabling auto-send.");
                    autoSendEnabledChk.setSelected(false);
                    return;
                }
                saveAutoSendPrefs();
                startAutoSendTimer();
            } else {
                stopAutoSendTimer();
                saveAutoSendPrefs();
            }
        });
        gc.gridy = 0;
        asContent.add(autoSendEnabledChk, gc);
        JPanel timeRow = opaquePanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel timeLabel = new JLabel("Send at:");
        timeLabel.setFont(FONT_BODY);
        timeLabel.setForeground(TEXT_PRI);
        autoSendTimeCombo = new JComboBox<>(new String[]{"6:00 AM","8:00 AM","12:00 PM","5:00 PM","8:00 PM","11:59 PM"});
        autoSendTimeCombo.setBackground(BG_INPUT);
        autoSendTimeCombo.setForeground(TEXT_PRI);
        autoSendTimeCombo.setFont(FONT_BODY);
        autoSendTimeCombo.setPreferredSize(new Dimension(120, 30));
        JButton saveAutoBtn = accentButton("💾 Save", ACCENT2);
        saveAutoBtn.setPreferredSize(new Dimension(90, 30));
        saveAutoBtn.addActionListener(e -> {
            saveAutoSendPrefs();
            if (autoSendEnabledChk.isSelected()) startAutoSendTimer();
            showInfo("Auto-send schedule saved.");
        });
        timeRow.add(timeLabel);
        timeRow.add(autoSendTimeCombo);
        timeRow.add(saveAutoBtn);
        gc.gridy = 1;
        asContent.add(timeRow, gc);
        JLabel autoReportLabel = new JLabel("Select reports to auto-send daily:");
        autoReportLabel.setFont(FONT_SMALL);
        autoReportLabel.setForeground(TEXT_SEC);
        autoReportLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        gc.gridy = 2;
        asContent.add(autoReportLabel, gc);
        JPanel autoReportGrid = opaquePanel(new GridLayout(0, 2, 6, 3));
        autoSendCheckboxes = new JCheckBox[REPORT_NAMES.length];
        for (int i = 0; i < REPORT_NAMES.length; i++) {
            autoSendCheckboxes[i] = new JCheckBox(REPORT_NAMES[i]);
            autoSendCheckboxes[i].setFont(new Font("Segoe UI", Font.PLAIN, 11));
            autoSendCheckboxes[i].setForeground(TEXT_PRI);
            autoSendCheckboxes[i].setOpaque(false);
            autoReportGrid.add(autoSendCheckboxes[i]);
        }
        gc.gridy = 3;
        asContent.add(autoReportGrid, gc);
        JLabel offlineNote = new JLabel("<html><i>⚡ Missed sends while offline are queued and retried on reconnect.</i></html>");
        offlineNote.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        offlineNote.setForeground(WARNING);
        offlineNote.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        gc.gridy = 4;
        asContent.add(offlineNote, gc);
        JScrollPane asScroll = new JScrollPane(asContent,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        asScroll.setBorder(null);
        asScroll.setOpaque(false);
        asScroll.getViewport().setOpaque(false);
        asScroll.getVerticalScrollBar().setUnitIncrement(12);
        autoBlock.add(asTitle,  BorderLayout.NORTH);
        autoBlock.add(asScroll, BorderLayout.CENTER);
        outer.add(sendNow,   BorderLayout.NORTH);
        outer.add(autoBlock, BorderLayout.CENTER);
        return outer;
    }

    // ══════════════════════════════════════════════════════════════
    //  SETTINGS VALIDATION
    // ══════════════════════════════════════════════════════════════

    private boolean settingsComplete() {
        return !businessName.isEmpty() && !registrationNo.isEmpty() && !tinNo.isEmpty()
                && !mobileNo.isEmpty() && !emailAddr.isEmpty() && !address.isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    //  BREVO EMAIL METHODS
    // ══════════════════════════════════════════════════════════════

    private void sendEmailWithBrevo(String recipientEmail, String reportName,
                                    byte[] pdfData, String recipientName) throws IOException {
        String displayName = (recipientName != null && !recipientName.isEmpty())
                ? recipientName : recipientEmail.split("@")[0];
        String bizName = businessName.isEmpty() ? "Gratis POS" : businessName;
        String emailBody = String.format(
            "Dear %s,\n\nPlease find attached: %s\n\nBusiness : %s\nDate     : %s\nGenerated: %s\n\nBest regards,\n%s",
            displayName, reportName, bizName,
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            bizName);
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"sender\":{\"name\":\"").append(escJson(FROM_NAME))
            .append("\",\"email\":\"").append(escJson(FROM_EMAIL)).append("\"},");
        json.append("\"to\":[{\"email\":\"").append(escJson(recipientEmail))
            .append("\",\"name\":\"").append(escJson(displayName)).append("\"}],");
        json.append("\"subject\":\"").append(escJson(reportName + " - " + LocalDate.now())).append("\",");
        json.append("\"textContent\":\"").append(escJson(emailBody)).append("\"");
        if (pdfData != null && pdfData.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(pdfData);
            String fileName = reportName.replaceAll("[^A-Za-z0-9_]", "_") + "_" + LocalDate.now() + ".pdf";
            json.append(",\"attachment\":[{\"content\":\"").append(b64)
                .append("\",\"name\":\"").append(escJson(fileName)).append("\"}]");
        }
        json.append("}");
        URL url = new URL(BREVO_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("accept",       "application/json");
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("api-key",      BREVO_API_KEY);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        if (status == 201) {
            LOGGER.info("✅ Brevo: email sent to " + recipientEmail);
            recordMailHistory(reportName, recipientEmail, "DELIVERED", null);
        } else {
            InputStream err = conn.getErrorStream();
            String body = err == null ? "(no body)" : new String(err.readAllBytes(), StandardCharsets.UTF_8);
            String msg = "Brevo HTTP " + status + ": " + body;
            LOGGER.warning(msg);
            recordMailHistory(reportName, recipientEmail, "FAILED", msg);
            throw new IOException(msg);
        }
        conn.disconnect();
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ══════════════════════════════════════════════════════════════
    //  OFFLINE MISSED-SEND QUEUE
    // ══════════════════════════════════════════════════════════════

    private void flushMissedAutoSends() {
        if (!Database.isOnline()) return;
        Preferences prefs = Preferences.userNodeForPackage(MasterPanel.class);
        String raw = prefs.get(MISSED_SENDS_KEY, "");
        if (raw.isEmpty()) return;
        String[] entries = raw.split("\n");
        prefs.remove(MISSED_SENDS_KEY);
        new Thread(() -> {
            for (String entry : entries) {
                if (entry.trim().isEmpty()) continue;
                String[] parts = entry.split("\\|", -1);
                if (parts.length < 4) continue;
                String reportName = parts[1];
                String from = parts[2];
                String to   = parts[3];
                try {
                    byte[] pdf = generateReportPDF(reportName, from, to);
                    for (int i = 0; i < recipientListModel.size(); i++) {
                        String e2  = extractEmail(recipientListModel.get(i));
                        String nm2 = extractName(recipientListModel.get(i));
                        sendEmailWithBrevo(e2, "[Delayed] " + reportName, pdf, nm2);
                    }
                    LOGGER.info("Flushed missed send: " + reportName + " for " + from);
                } catch (Exception ex) {
                    LOGGER.warning("Failed to flush missed send: " + ex.getMessage());
                    queueMissedSend(reportName, from, to);
                }
            }
        }, "flush-missed-sends").start();
    }

    private void queueMissedSend(String reportName, String from, String to) {
        Preferences prefs = Preferences.userNodeForPackage(MasterPanel.class);
        String existing = prefs.get(MISSED_SENDS_KEY, "");
        String entry = LocalDate.now() + "|" + reportName + "|" + from + "|" + to;
        prefs.put(MISSED_SENDS_KEY, existing + (existing.isEmpty() ? "" : "\n") + entry);
    }

    private void sendCurrentReportByEmail(JTable table, String reportTitle) {
        if (recipientListModel.isEmpty()) {
            showError("No recipients. Add email addresses in the 'Send Reports' tab.");
            return;
        }
        int total = recipientListModel.size();
        JDialog prog = buildProgressDialog("Sending: " + reportTitle, total);
        JProgressBar bar  = (JProgressBar) ((JPanel)prog.getContentPane()).getComponent(1);
        JLabel      info  = (JLabel)       ((JPanel)prog.getContentPane()).getComponent(2);
        new Thread(() -> {
            updateProgress(bar, info, 5, "Generating PDF…");
            byte[] pdf;
            try {
                pdf = generateProfessionalPDF(reportTitle, table, defaultFrom(), defaultTo());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { prog.dispose(); showError("PDF error: " + ex.getMessage()); });
                return;
            }
            for (int i = 0; i < total; i++) {
                String entry = recipientListModel.get(i);
                String email = extractEmail(entry);
                String name  = extractName(entry);
                int pct = 5 + (int)((double)(i + 1) / total * 90);
                updateProgress(bar, info, pct, "Sending to " + email + " (" + (i+1) + "/" + total + ")…");
                try {
                    sendEmailWithBrevo(email, reportTitle, pdf, name);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to send to " + email, ex);
                }
            }
            updateProgress(bar, info, 100, "Done!");
            try { Thread.sleep(900); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                prog.dispose();
                loadMailHistory("All");
                showInfo("Report sent to " + total + " recipient(s).");
            });
        }, "send-report-brevo").start();
        prog.setVisible(true);
    }

    private void sendSelectedReports(List<String> reportTypes, String from, String to) {
        if (recipientListModel.isEmpty()) { showError("No recipients."); return; }
        int total = reportTypes.size() * recipientListModel.size();
        JDialog prog = buildProgressDialog("Sending " + reportTypes.size() + " Reports", total);
        JProgressBar bar  = (JProgressBar) ((JPanel)prog.getContentPane()).getComponent(1);
        JLabel      info  = (JLabel)       ((JPanel)prog.getContentPane()).getComponent(2);
        new Thread(() -> {
            int done = 0;
            for (String rt : reportTypes) {
                updateProgress(bar, info, (int)(done * 100.0 / total), "Generating: " + rt + "…");
                byte[] pdf;
                try {
                    pdf = generateReportPDF(rt, from, to);
                } catch (Exception ex) {
                    for (int i = 0; i < recipientListModel.size(); i++)
                        recordMailHistory(rt, recipientListModel.get(i), "FAILED", "PDF: " + ex.getMessage());
                    done += recipientListModel.size();
                    continue;
                }
                for (int i = 0; i < recipientListModel.size(); i++) {
                    String entry = recipientListModel.get(i);
                    String email = extractEmail(entry);
                    String name  = extractName(entry);
                    done++;
                    updateProgress(bar, info, (int)(done * 100.0 / total), "Sending " + rt + " → " + email);
                    try {
                        sendEmailWithBrevo(email, rt, pdf, name);
                    } catch (Exception ex) {
                        recordMailHistory(rt, email, "FAILED", ex.getMessage());
                    }
                }
            }
            updateProgress(bar, info, 100, "All reports sent!");
            try { Thread.sleep(900); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                prog.dispose();
                loadMailHistory("All");
                showInfo("Reports sent successfully.");
            });
        }, "send-selected-brevo").start();
        prog.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  MAIL HISTORY TAB
    // ══════════════════════════════════════════════════════════════

    private JPanel buildMailHistoryTab() {
        GlassPanel p = new GlassPanel(ACCENT);
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("📜  Mail History");
        title.setFont(FONT_HEAD);
        title.setForeground(ACCENT);
        String[] cols = {"#","Sent At","Report Type","Recipient","Status","Error","Action"};
        mailHistoryModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = styledTable(mailHistoryModel);
        tbl.getColumnModel().getColumn(0).setMaxWidth(40);
        tbl.getColumnModel().getColumn(4).setMaxWidth(110);
        tbl.getColumnModel().getColumn(6).setMaxWidth(90);
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String status = (String) table.getModel().getValueAt(row, 4);
                if (!isSelected) {
                    if ("DELIVERED".equals(status))   c.setBackground(new Color(52, 211, 153, 25));
                    else if ("FAILED".equals(status)) c.setBackground(new Color(255, 100, 100, 25));
                    else c.setBackground(row % 2 == 0 ? new Color(15,18,40,120) : new Color(20,24,52,120));
                }
                ((JLabel)c).setForeground(TEXT_PRI);
                ((JLabel)c).setHorizontalAlignment(SwingConstants.LEFT);
                ((JLabel)c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        });
        tbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = tbl.rowAtPoint(e.getPoint());
                int col = tbl.columnAtPoint(e.getPoint());
                if (col == 6 && row >= 0) {
                    String status = (String) mailHistoryModel.getValueAt(row, 4);
                    if ("FAILED".equals(status)) {
                        String rt  = (String) mailHistoryModel.getValueAt(row, 2);
                        String rec = (String) mailHistoryModel.getValueAt(row, 3);
                        if (JOptionPane.showConfirmDialog(masterDialog,
                                "Resend \"" + rt + "\" to " + rec + "?",
                                "Confirm Resend", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                            resendFailedMail(row, rt, rec);
                    } else showInfo("Only FAILED emails can be resent.");
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tbl);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JComboBox<String> statusFilter = styledCombo("All","DELIVERED","FAILED","PENDING");
        JButton refreshBtn = actionButton("🔄 Refresh");
        refreshBtn.addActionListener(e -> loadMailHistory((String)statusFilter.getSelectedItem()));
        JButton clearBtn = accentButton("🗑  Clear History", DANGER);
        clearBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(masterDialog, "Clear all mail history?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                clearMailHistory(); loadMailHistory("All");
            }
        });
        JPanel filterRow = filterRow(label("Status:"), statusFilter, refreshBtn, clearBtn);
        JPanel legend = opaquePanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        legend.add(legendDot(SUCCESS, "Delivered"));
        legend.add(legendDot(DANGER,  "Failed — click row to resend"));
        legend.add(legendDot(WARNING, "Pending"));
        JPanel top = opaquePanel(new BorderLayout(0, 6));
        top.add(title,     BorderLayout.NORTH);
        top.add(filterRow, BorderLayout.CENTER);
        top.add(legend,    BorderLayout.SOUTH);
        p.add(top,    BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        loadMailHistory("All");
        return p;
    }

    private JLabel legendDot(Color c, String text) {
        JLabel l = new JLabel("⬤  " + text);
        l.setFont(FONT_SMALL);
        l.setForeground(c);
        return l;
    }

    // ══════════════════════════════════════════════════════════════
    //  ADJUSTMENT LOG TAB
    // ══════════════════════════════════════════════════════════════

    private JPanel buildAdjustmentLogTab() {
        GlassPanel p = new GlassPanel(ACCENT);
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        String[] cols = {"Date","Product","Action","Qty","User","Notes"};
        DefaultTableModel mdl = noEditModel(cols);
        JTable tbl = styledTable(mdl);
        JComboBox<String> actionFilter = styledCombo("All","ADD_STOCK","REMOVE_STOCK","RECEIVED","SOLD","PRICE_UPDATE","REFUNDED");
        JButton refreshBtn = actionButton("🔄 Refresh");
        refreshBtn.addActionListener(e -> loadAdjustmentLog(mdl, (String)actionFilter.getSelectedItem()));
        JButton exportBtn = actionButton("⬇ Export PDF");
        exportBtn.addActionListener(e -> exportTableToPDF(tbl, "Adjustment_Log"));
        JPanel filterRow = filterRow(label("Action:"), actionFilter, refreshBtn, exportBtn);
        JScrollPane scroll = new JScrollPane(tbl);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JLabel title = new JLabel("📝  Stock Adjustment Log");
        title.setFont(FONT_HEAD);
        title.setForeground(ACCENT);
        JPanel top = opaquePanel(new BorderLayout(0, 8));
        top.add(title,     BorderLayout.NORTH);
        top.add(filterRow, BorderLayout.SOUTH);
        p.add(top,    BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        loadAdjustmentLog(mdl, "All");
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  SETTINGS TAB
    // ══════════════════════════════════════════════════════════════

    private JPanel buildSettingsTab() {
        GlassPanel p = new GlassPanel(ACCENT);
        p.setLayout(new BorderLayout(20, 0));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        GlassPanel form = glassPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        String[] lbls = {
            "Business Name: *","Registration No: *","TIN / EIN: *",
            "Mobile Number: *","Email: *","Website:","Address: *","Currency:"
        };
        settingsFields = new JTextField[lbls.length];
        for (int i = 0; i < lbls.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            JLabel l = new JLabel(lbls[i]);
            l.setFont(FONT_HEAD);
            l.setForeground(lbls[i].contains("*") ? TEXT_PRI : TEXT_SEC);
            form.add(l, gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            settingsFields[i] = inputField(i == lbls.length - 1 ? "e.g. KES, USD, TZS (leave blank for none)" : "");
            form.add(settingsFields[i], gbc);
        }
        gbc.gridx = 0; gbc.gridy = lbls.length; gbc.gridwidth = 2;
        JLabel req = new JLabel("* Required before sending reports");
        req.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        req.setForeground(WARNING);
        form.add(req, gbc);
        populateSettingsFields();
        JButton save = accentButton("💾  Save Settings", ACCENT);
        save.addActionListener(e -> saveSettings());
        JPanel btnRow = opaquePanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        btnRow.add(save);
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JLabel title = new JLabel("⚙️  Business Settings");
        title.setFont(FONT_HEAD); title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        p.add(title,  BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(btnRow, BorderLayout.SOUTH);
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  AUTO-SEND TIMER
    // ══════════════════════════════════════════════════════════════

    private void startAutoSendTimer() {
        stopAutoSendTimer();
        if (autoSendEnabledChk == null || !autoSendEnabledChk.isSelected()) return;
        autoSendTimer = new Timer("auto-send-timer", true);
        autoSendTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (autoSendTimeCombo == null) return;
                String scheduled = (String) autoSendTimeCombo.getSelectedItem();
                if (scheduled != null && timeMatches(scheduled)) {
                    List<String> sel = new ArrayList<>();
                    if (autoSendCheckboxes != null)
                        for (JCheckBox c : autoSendCheckboxes) if (c.isSelected()) sel.add(c.getText());
                    if (!sel.isEmpty()) {
                        if (!Database.isOnline()) {
                            String today = LocalDate.now().toString();
                            for (String rt : sel) queueMissedSend(rt, today, today);
                            LOGGER.info("Auto-send queued (offline): " + sel);
                        } else {
                            String today = LocalDate.now().toString();
                            sendSelectedReports(sel, today, today);
                            LOGGER.info("Auto-send fired via Brevo: " + sel);
                        }
                    }
                }
            }
        }, 60_000L, 60_000L);
        LOGGER.info("Auto-send timer started");
    }

    private void stopAutoSendTimer() {
        if (autoSendTimer != null) { autoSendTimer.cancel(); autoSendTimer = null; }
    }

    private boolean timeMatches(String scheduled) {
        LocalDateTime now = LocalDateTime.now();
        try {
            String[] parts = scheduled.split("[: ]");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            boolean pm = "PM".equalsIgnoreCase(parts[2]);
            if (pm && h != 12) h += 12;
            if (!pm && h == 12) h = 0;
            return h == now.getHour() && Math.abs(m - now.getMinute()) <= 1;
        } catch (Exception e) { return false; }
    }

    // ══════════════════════════════════════════════════════════════
    //  PROGRESS DIALOG
    // ══════════════════════════════════════════════════════════════

    private JDialog buildProgressDialog(String title, int totalSteps) {
        JDialog dlg = new JDialog(masterDialog, title, false);
        dlg.setSize(480, 180);
        dlg.setLocationRelativeTo(masterDialog);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dlg.setResizable(false);
        JPanel root = new JPanel(new BorderLayout(0, 12)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_BASE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_HEAD);
        titleLbl.setForeground(ACCENT);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setFont(FONT_SMALL);
        bar.setForeground(ACCENT);
        bar.setBackground(BG_INPUT);
        bar.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        bar.setPreferredSize(new Dimension(400, 26));
        JLabel info = new JLabel("Initialising…");
        info.setFont(FONT_SMALL);
        info.setForeground(TEXT_SEC);
        root.add(titleLbl, BorderLayout.NORTH);
        root.add(bar,      BorderLayout.CENTER);
        root.add(info,     BorderLayout.SOUTH);
        dlg.setContentPane(root);
        return dlg;
    }

    private void updateProgress(JProgressBar bar, JLabel info, int pct, String msg) {
        SwingUtilities.invokeLater(() -> {
            bar.setValue(pct);
            bar.setString(pct + "%");
            info.setText(msg);
            if (pct >= 100) { bar.setForeground(SUCCESS); info.setForeground(SUCCESS); }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  PDF REPORT GENERATION (ALL REPORTS WITH DIRECT DATABASE FETCH)
    // ══════════════════════════════════════════════════════════════

    private byte[] generateReportPDF(String reportType, String from, String to) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        doc.setMargins(40, 40, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();
        
        addReportHeader(doc, reportType, from, to);
        
        switch (reportType) {
            case "Daily Sale Report":
                buildDailySaleReport(doc, from, to);
                break;
            case "Weekly Sale Report":
            case "Monthly Sale Report":
                buildPeriodSalesReport(doc, from, to);
                break;
            case "Price List Report":
                buildPriceListReport(doc);
                break;
            case "Fast Moving Report":
                buildFastMovingReport(doc, from, to);
                break;
            case "Dead Stock Report":
                buildDeadStockReport(doc);
                break;
            case "Purchase Received Report":
                buildPurchaseReceivedReport(doc, from, to);
                break;
            case "Out of Stock Report":
                buildOutOfStockReport(doc);
                break;
            case "Near Expire Report":
                buildNearExpireReport(doc);
                break;
            case "Expired Stock Report":
                buildExpiredStockReport(doc);
                break;
            case "Profit Analysis":
                buildProfitAnalysisReport(doc, from, to);
                break;
            default:
                doc.add(new Paragraph("Report not found: " + reportType));
        }
        
        doc.close();
        return baos.toByteArray();
    }

    private void addReportHeader(Document doc, String title, String from, String to) throws Exception {
        com.lowagie.text.Font titleFont = new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 20, com.lowagie.text.Font.BOLD, new Color(30, 70, 160));
        com.lowagie.text.Font infoFont = new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, new Color(80, 80, 100));
        
        String bizName = businessName.isEmpty() ? "GRATIS POS" : businessName.toUpperCase();
        Paragraph bizPara = new Paragraph(bizName, titleFont);
        bizPara.setAlignment(Element.ALIGN_CENTER);
        doc.add(bizPara);
        
        Paragraph subPara = new Paragraph(title, new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 14, com.lowagie.text.Font.BOLD, new Color(60, 60, 80)));
        subPara.setAlignment(Element.ALIGN_CENTER);
        subPara.setSpacingBefore(10);
        doc.add(subPara);
        
        String period = from.equals(to) ? "Date: " + from : "Period: " + from + " to " + to;
        Paragraph periodPara = new Paragraph(period, infoFont);
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingBefore(5);
        doc.add(periodPara);
        
        String dateTime = "Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph datePara = new Paragraph(dateTime, infoFont);
        datePara.setAlignment(Element.ALIGN_CENTER);
        doc.add(datePara);
        
        doc.add(new Paragraph(" "));
        doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(1f, 100f, new Color(180, 190, 220), Element.ALIGN_CENTER, -2)));
        doc.add(new Paragraph(" "));
    }

    private void addSummaryRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD)));
        labelCell.setPadding(5);
        labelCell.setBorderColor(new Color(180, 200, 220));
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL)));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBorderColor(new Color(180, 200, 220));
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private PdfPCell dataCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new com.lowagie.text.Font(
            com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private void buildDailySaleReport(Document doc, String from, String to) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        int totalQuantity = 0;
        
        String sql = "SELECT p.name, c.name as category, si.quantity, si.unit_price, si.total_price, " +
                     "s.user, DATE(s.sale_date) as sale_date " +
                     "FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "JOIN products p ON si.product_id = p.id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, from);
            stmt.setString(2, to);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getInt("quantity"),
                    fmtCurrency(rs.getBigDecimal("unit_price")),
                    fmtCurrency(rs.getBigDecimal("total_price")),
                    rs.getString("user"),
                    rs.getString("sale_date")
                });
                totalSales = totalSales.add(rs.getBigDecimal("total_price"));
                totalQuantity += rs.getInt("quantity");
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{20f, 15f, 8f, 12f, 12f, 12f, 12f});
            
            String[] headers = {"Product", "Category", "Qty", "Unit Price", "Sub Total", "Sold By", "Date"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(30, 70, 160));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
        } else {
            doc.add(new Paragraph("No sales data for this period."));
        }
        
        doc.add(new Paragraph(" "));
        
        BigDecimal discounts = Database.getTotalDiscounts(from, to);
        BigDecimal expenses = Database.getTotalExpenses(from, to);
        BigDecimal netSales = totalSales.subtract(discounts).subtract(expenses);
        
        PdfPTable summary = new PdfPTable(2);
        summary.setWidthPercentage(50);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addSummaryRow(summary, "Total Items Sold:", String.valueOf(totalQuantity));
        addSummaryRow(summary, "Total Sales:", fmtCurrency(totalSales));
        addSummaryRow(summary, "Discounts:", fmtCurrency(discounts));
        addSummaryRow(summary, "Expenses:", fmtCurrency(expenses));
        addSummaryRow(summary, "Net Sales:", fmtCurrency(netSales));
        doc.add(summary);
    }

    private void buildPeriodSalesReport(Document doc, String from, String to) throws Exception {
        List<Database.SalesData> salesData = Database.getSalesData(from, to);
        
        if (!salesData.isEmpty()) {
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{20f, 20f, 20f, 20f, 20f});
            
            String[] headers = {"Date", "Total Sales", "Discounts", "Expenses", "Net Sale"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(103, 65, 163));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            BigDecimal totalSales = BigDecimal.ZERO;
            BigDecimal totalDiscounts = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;
            
            for (Database.SalesData sd : salesData) {
                BigDecimal disc = Database.getDailyDiscount(sd.date.toString());
                BigDecimal exp = Database.getDailyExpense(sd.date.toString());
                BigDecimal net = sd.total.subtract(disc).subtract(exp);
                
                totalSales = totalSales.add(sd.total);
                totalDiscounts = totalDiscounts.add(disc);
                totalExpenses = totalExpenses.add(exp);
                totalNet = totalNet.add(net);
                
                table.addCell(dataCell(sd.date.toString(), Element.ALIGN_CENTER));
                table.addCell(dataCell(fmtCurrency(sd.total), Element.ALIGN_RIGHT));
                table.addCell(dataCell(fmtCurrency(disc), Element.ALIGN_RIGHT));
                table.addCell(dataCell(fmtCurrency(exp), Element.ALIGN_RIGHT));
                table.addCell(dataCell(fmtCurrency(net), Element.ALIGN_RIGHT));
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Total Days:", String.valueOf(salesData.size()));
            addSummaryRow(summary, "Total Discounts:", fmtCurrency(totalDiscounts));
            addSummaryRow(summary, "Total Expenses:", fmtCurrency(totalExpenses));
            addSummaryRow(summary, "Total Net Sales:", fmtCurrency(totalNet));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No sales data for this period."));
        }
    }

    private void buildPriceListReport(Document doc) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        
        String sql = "SELECT p.name, c.name as category, p.cost_price, p.selling_price, " +
                     "p.stock_quantity, p.vendor, " +
                     "((p.selling_price - p.cost_price) / NULLIF(p.cost_price, 0) * 100) as margin " +
                     "FROM products p " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.client_id = ? ORDER BY p.name";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    fmtCurrency(rs.getBigDecimal("cost_price")),
                    fmtCurrency(rs.getBigDecimal("selling_price")),
                    rs.getInt("stock_quantity"),
                    rs.getString("vendor"),
                    String.format("%.1f%%", rs.getDouble("margin"))
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{25f, 15f, 12f, 12f, 8f, 15f, 8f});
            
            String[] headers = {"Product", "Category", "Cost Price", "Sell Price", "Stock", "Vendor", "Margin"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(52, 211, 153));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
        } else {
            doc.add(new Paragraph("No products found."));
        }
    }

    private void buildFastMovingReport(Document doc, String from, String to) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        
        String sql = "SELECT p.name, c.name as category, SUM(si.quantity) as total_qty, " +
                     "SUM(si.total_price) as total_revenue, MAX(s.sale_date) as last_sale " +
                     "FROM sale_items si " +
                     "JOIN products p ON si.product_id = p.id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ? " +
                     "GROUP BY p.id ORDER BY total_qty DESC LIMIT 20";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, from);
            stmt.setString(2, to);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                rows.add(new Object[]{rank++, rs.getString("name"), rs.getString("category"),
                    rs.getInt("total_qty"), fmtCurrency(rs.getBigDecimal("total_revenue")), rs.getString("last_sale")});
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{8f, 30f, 15f, 10f, 20f, 17f});
            
            String[] headers = {"Rank", "Product", "Category", "Qty Sold", "Revenue", "Last Sale"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(22, 160, 133));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 1 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
        } else {
            doc.add(new Paragraph("No fast moving products found."));
        }
    }

    private void buildDeadStockReport(Document doc) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalIdleValue = BigDecimal.ZERO;
        
        String sql = "SELECT p.name, c.name as category, p.stock_quantity, p.cost_price, " +
                     "(p.stock_quantity * p.cost_price) as total_value, " +
                     "COALESCE(MAX(s.sale_date), 'Never Sold') as last_sale " +
                     "FROM products p " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "LEFT JOIN sale_items si ON p.id = si.product_id " +
                     "LEFT JOIN sales s ON si.sale_id = s.id AND s.client_id = p.client_id " +
                     "WHERE p.client_id = ? AND p.stock_quantity > 0 " +
                     "GROUP BY p.id " +
                     "HAVING last_sale = 'Never Sold' OR last_sale < date('now', '-90 days') " +
                     "ORDER BY total_value DESC";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BigDecimal value = rs.getBigDecimal("total_value");
                totalIdleValue = totalIdleValue.add(value);
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getInt("stock_quantity"),
                    fmtCurrency(rs.getBigDecimal("cost_price")),
                    fmtCurrency(value),
                    rs.getString("last_sale")
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{25f, 15f, 10f, 15f, 15f, 20f});
            
            String[] headers = {"Product", "Category", "Stock", "Cost Price", "Total Value", "Last Sale"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(155, 89, 182));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Dead Stock Items:", String.valueOf(rows.size()));
            addSummaryRow(summary, "Total Idle Value:", fmtCurrency(totalIdleValue));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No dead stock found."));
        }
    }

    private void buildPurchaseReceivedReport(Document doc, String from, String to) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalRetail = BigDecimal.ZERO;
        
        String sql = "SELECT DATE(i.flow_date) as receive_date, p.name, c.name as category, " +
                     "i.vendor, i.quantity, p.cost_price, (i.quantity * p.cost_price) as total_cost, " +
                     "(i.quantity * p.selling_price) as total_retail " +
                     "FROM item_flows i " +
                     "JOIN products p ON i.product_id = p.id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE i.action = 'RECEIVED' AND DATE(i.flow_date) BETWEEN ? AND ? " +
                     "AND i.client_id = ? ORDER BY i.flow_date DESC";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, from);
            stmt.setString(2, to);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BigDecimal cost = rs.getBigDecimal("total_cost");
                BigDecimal retail = rs.getBigDecimal("total_retail");
                totalCost = totalCost.add(cost);
                totalRetail = totalRetail.add(retail);
                rows.add(new Object[]{
                    rs.getString("receive_date"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("vendor"),
                    rs.getInt("quantity"),
                    fmtCurrency(rs.getBigDecimal("cost_price")),
                    fmtCurrency(cost)
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{12f, 20f, 12f, 15f, 8f, 12f, 15f});
            
            String[] headers = {"Date", "Product", "Category", "Vendor", "Qty", "Cost Price", "Total"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(41, 128, 185));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 1 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Total Cost:", fmtCurrency(totalCost));
            addSummaryRow(summary, "Total Retail Value:", fmtCurrency(totalRetail));
            addSummaryRow(summary, "Expected Profit:", fmtCurrency(totalRetail.subtract(totalCost)));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No purchase records found for this period."));
        }
    }

    private void buildOutOfStockReport(Document doc) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        
        String sql = "SELECT p.name, c.name as category, p.vendor, p.cost_price, p.selling_price, " +
                     "COALESCE(MAX(i.flow_date), 'Never') as last_received, " +
                     "julianday('now') - julianday(COALESCE(MAX(i.flow_date), 'now')) as days_out " +
                     "FROM products p " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "LEFT JOIN item_flows i ON p.id = i.product_id AND i.action = 'RECEIVED' " +
                     "WHERE p.client_id = ? AND p.stock_quantity = 0 " +
                     "GROUP BY p.id ORDER BY days_out DESC";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("vendor"),
                    fmtCurrency(rs.getBigDecimal("cost_price")),
                    fmtCurrency(rs.getBigDecimal("selling_price")),
                    rs.getString("last_received"),
                    (int) rs.getDouble("days_out") + " days"
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{22f, 14f, 14f, 12f, 12f, 14f, 12f});
            
            String[] headers = {"Product", "Category", "Vendor", "Cost Price", "Sell Price", "Last Received", "Days Out"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(231, 76, 60));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Out of Stock Items:", String.valueOf(rows.size()));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No out of stock products found."));
        }
    }

    private void buildNearExpireReport(Document doc) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        
        String sql = "SELECT p.name, c.name as category, p.expire_date, p.stock_quantity, " +
                     "p.selling_price, " +
                     "CAST(julianday(p.expire_date) - julianday('now') AS INTEGER) as days_left " +
                     "FROM products p " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.client_id = ? AND p.expire_date IS NOT NULL " +
                     "AND julianday(p.expire_date) - julianday('now') BETWEEN 0 AND 60 " +
                     "ORDER BY days_left ASC";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int daysLeft = rs.getInt("days_left");
                String status = daysLeft <= 7 ? "CRITICAL" : (daysLeft <= 30 ? "WARNING" : "OK");
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("expire_date"),
                    rs.getInt("stock_quantity"),
                    fmtCurrency(rs.getBigDecimal("selling_price")),
                    daysLeft + " days",
                    status
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{25f, 15f, 12f, 8f, 12f, 10f, 10f});
            
            String[] headers = {"Product", "Category", "Expire Date", "Stock", "Sell Price", "Days Left", "Status"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(230, 126, 34));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    boolean isCritical = row[6].toString().equals("CRITICAL");
                    com.lowagie.text.Font font = new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8,
                        isCritical ? com.lowagie.text.Font.BOLD : com.lowagie.text.Font.NORMAL,
                        isCritical ? new Color(192, 57, 43) : new Color(50, 50, 50));
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), font));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            int criticalCount = (int) rows.stream().filter(r -> r[6].toString().equals("CRITICAL")).count();
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Products at Risk:", String.valueOf(rows.size()));
            addSummaryRow(summary, "Critical (≤7 days):", String.valueOf(criticalCount));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No near-expiry products found."));
        }
    }

    private void buildExpiredStockReport(Document doc) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalLoss = BigDecimal.ZERO;
        
        String sql = "SELECT p.name, c.name as category, p.expire_date, p.stock_quantity, " +
                     "p.cost_price, (p.stock_quantity * p.cost_price) as loss_value, " +
                     "CAST(julianday('now') - julianday(p.expire_date) AS INTEGER) as days_overdue " +
                     "FROM products p " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "WHERE p.client_id = ? AND p.expire_date IS NOT NULL " +
                     "AND julianday(p.expire_date) - julianday('now') < 0 " +
                     "ORDER BY p.expire_date ASC";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BigDecimal loss = rs.getBigDecimal("loss_value");
                totalLoss = totalLoss.add(loss);
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("expire_date"),
                    rs.getInt("stock_quantity"),
                    fmtCurrency(rs.getBigDecimal("cost_price")),
                    fmtCurrency(loss),
                    rs.getInt("days_overdue") + " days"
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{22f, 14f, 12f, 8f, 12f, 16f, 12f});
            
            String[] headers = {"Product", "Category", "Expire Date", "Stock", "Cost Price", "Loss Value", "Days Overdue"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(192, 57, 43));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Expired Items:", String.valueOf(rows.size()));
            addSummaryRow(summary, "Total Loss Value:", fmtCurrency(totalLoss));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No expired stock found."));
        }
    }

    private void buildProfitAnalysisReport(Document doc, String from, String to) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        
        String sql = "SELECT p.name, c.name as category, SUM(si.quantity) as qty, " +
                     "SUM(si.total_price) as revenue, " +
                     "SUM(si.quantity * p.cost_price) as cost " +
                     "FROM sale_items si " +
                     "JOIN products p ON si.product_id = p.id " +
                     "JOIN categories c ON p.category_id = c.id " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ? " +
                     "GROUP BY p.id ORDER BY revenue DESC LIMIT 50";
        
        try (PreparedStatement stmt = Database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, from);
            stmt.setString(2, to);
            stmt.setString(3, Database.getCurrentClientId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BigDecimal rev = rs.getBigDecimal("revenue");
                BigDecimal cost = rs.getBigDecimal("cost");
                BigDecimal profit = rev.subtract(cost);
                totalRevenue = totalRevenue.add(rev);
                totalCost = totalCost.add(cost);
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getInt("qty"),
                    fmtCurrency(rev),
                    fmtCurrency(cost),
                    fmtCurrency(profit)
                });
            }
        }
        
        if (!rows.isEmpty()) {
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{25f, 15f, 8f, 15f, 15f, 15f});
            
            String[] headers = {"Product", "Category", "Qty", "Revenue", "Cost", "Profit"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(103, 65, 163));
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[i].toString(), new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            
            doc.add(new Paragraph(" "));
            
            BigDecimal totalProfit = totalRevenue.subtract(totalCost);
            double margin = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ? 
                totalProfit.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100 : 0;
            
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(50);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addSummaryRow(summary, "Total Revenue:", fmtCurrency(totalRevenue));
            addSummaryRow(summary, "Total Cost:", fmtCurrency(totalCost));
            addSummaryRow(summary, "Total Profit:", fmtCurrency(totalProfit));
            addSummaryRow(summary, "Profit Margin:", String.format("%.1f%%", margin));
            doc.add(summary);
        } else {
            doc.add(new Paragraph("No profit data for this period."));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LEGACY PDF METHOD (for export button)
    // ══════════════════════════════════════════════════════════════

    private byte[] generateProfessionalPDF(String reportTitle, JTable table, String from, String to) throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int r = 0; r < table.getRowCount(); r++) {
            Object[] row = new Object[table.getColumnCount()];
            for (int c = 0; c < table.getColumnCount(); c++) row[c] = table.getValueAt(r, c);
            rows.add(row);
        }
        String[] colNames = new String[table.getColumnCount()];
        for (int i = 0; i < colNames.length; i++) colNames[i] = table.getColumnName(i);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        doc.setMargins(40, 40, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();
        
        addReportHeader(doc, reportTitle, from, to);
        
        if (!rows.isEmpty()) {
            PdfPTable pdfTable = new PdfPTable(colNames.length);
            pdfTable.setWidthPercentage(100);
            for (String h : colNames) {
                PdfPCell cell = new PdfPCell(new Phrase(h, new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(30, 70, 160));
                cell.setPadding(6);
                pdfTable.addCell(cell);
            }
            for (Object[] row : rows) {
                for (Object val : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(val != null ? val.toString() : "—", new com.lowagie.text.Font(
                        com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL)));
                    cell.setPadding(4);
                    pdfTable.addCell(cell);
                }
            }
            doc.add(pdfTable);
        } else {
            doc.add(new Paragraph("No data available."));
        }
        
        doc.close();
        return baos.toByteArray();
    }

    private void exportTableToPDF(JTable table, String fileName) {
        try {
            byte[] data = generateProfessionalPDF(fileName.replace("_", " "), table, defaultFrom(), defaultTo());
            String path = System.getProperty("user.home") + "/Desktop/" + fileName + "_" + LocalDate.now() + ".pdf";
            java.nio.file.Files.write(java.nio.file.Paths.get(path), data);
            showInfo("Exported to: " + path);
        } catch (Exception e) { showError("Export failed: " + e.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════
    //  DATA LOADING METHODS (UI only)
    // ══════════════════════════════════════════════════════════════

    private void loadCashSale(String from, String to, DefaultTableModel mdl,
            JLabel totLbl, JLabel discLbl, JLabel netLbl, JLabel txnLbl) {
        mdl.setRowCount(0);
        BigDecimal totSales = BigDecimal.ZERO, totDisc = BigDecimal.ZERO;
        Set<Integer> txnIds = new HashSet<>();
        String sql = "SELECT s.id,s.sale_date,p.name,c.name as cat,p.selling_price,si.quantity," +
                     "si.total_price,s.discount,s.user FROM sales s" +
                     " JOIN sale_items si ON s.id=si.sale_id JOIN products p ON si.product_id=p.id" +
                     " JOIN categories c ON p.category_id=c.id" +
                     " WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id=?";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, from); st.setString(2, to); st.setString(3, Database.getCurrentClientId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                    fmtCurrency(rs.getBigDecimal("selling_price")),
                    rs.getInt("quantity"), fmtCurrency(rs.getBigDecimal("total_price")),
                    rs.getString("user"), rs.getString("sale_date")});
                totSales = totSales.add(rs.getBigDecimal("total_price"));
                totDisc  = totDisc.add(rs.getBigDecimal("discount"));
                txnIds.add(rs.getInt("id"));
            }
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadCashSale", e); }
        totLbl.setText("Total: " + fmtCurrency(totSales));
        discLbl.setText("Discount: " + fmtCurrency(totDisc));
        netLbl.setText("Net: " + fmtCurrency(totSales.subtract(totDisc)));
        txnLbl.setText("Txns: " + txnIds.size());
    }

    private void loadPriceList(DefaultTableModel mdl, String cat) {
        mdl.setRowCount(0);
        String sql = "SELECT p.name,c.name as cat,p.cost_price,p.selling_price,p.stock_quantity,p.vendor," +
                     "((p.selling_price-p.cost_price)/NULLIF(p.cost_price,0)*100) as margin" +
                     " FROM products p JOIN categories c ON p.category_id=c.id WHERE p.client_id=?" +
                     ("All Categories".equals(cat) ? "" : " AND c.name=?") + " ORDER BY p.name";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId());
            if (!"All Categories".equals(cat)) st.setString(2, cat);
            ResultSet rs = st.executeQuery();
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                fmtCurrency(rs.getBigDecimal("cost_price")),
                fmtCurrency(rs.getBigDecimal("selling_price")),
                rs.getInt("stock_quantity"), rs.getString("vendor"),
                String.format("%.1f%%", rs.getDouble("margin"))});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadPriceList", e); }
    }

    private void loadFastMoving(DefaultTableModel mdl, String period) {
        mdl.setRowCount(0);
        int days = period.contains("7") ? 7 : period.contains("30") ? 30 : 90;
        String sql = "SELECT p.name,c.name as cat,SUM(si.quantity) as sold,SUM(si.total_price) as rev," +
                     "MAX(s.sale_date) as last FROM sale_items si JOIN products p ON si.product_id=p.id" +
                     " JOIN categories c ON p.category_id=c.id JOIN sales s ON si.sale_id=s.id" +
                     " WHERE s.client_id=? AND s.sale_date>=date('now',?) GROUP BY p.id ORDER BY sold DESC LIMIT 50";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId()); st.setString(2, "-" + days + " days");
            ResultSet rs = st.executeQuery(); int rank = 1;
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                rs.getInt("sold"), fmtCurrency(rs.getBigDecimal("rev")), rs.getString("last"), rank++});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadFastMoving", e); }
    }

    private void loadDeadStock(DefaultTableModel mdl, String period) {
        mdl.setRowCount(0);
        int days = Integer.parseInt(period.split(" ")[0]);
        String sql = "SELECT p.name,c.name as cat,p.stock_quantity,p.cost_price,(p.stock_quantity*p.cost_price) as val," +
                     "COALESCE(MAX(s.sale_date),'Never Sold') as last FROM products p" +
                     " LEFT JOIN categories c ON p.category_id=c.id" +
                     " LEFT JOIN sale_items si ON p.id=si.product_id" +
                     " LEFT JOIN sales s ON si.sale_id=s.id AND s.client_id=?" +
                     " WHERE p.client_id=? AND p.stock_quantity>0 GROUP BY p.id" +
                     " HAVING last='Never Sold' OR last<date('now',?) ORDER BY val DESC";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId()); st.setString(2, Database.getCurrentClientId());
            st.setString(3, "-" + days + " days");
            ResultSet rs = st.executeQuery();
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                rs.getInt("stock_quantity"), fmtCurrency(rs.getBigDecimal("cost_price")),
                fmtCurrency(rs.getBigDecimal("val")), rs.getString("last")});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadDeadStock", e); }
    }

    private void loadPurchaseReceive(String from, String to, DefaultTableModel mdl, JLabel totLbl) {
        mdl.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        String sql = "SELECT DATE(i.flow_date) as dt,p.name,c.name as cat,i.vendor,i.quantity,p.cost_price," +
                     "(i.quantity*p.cost_price) as tot FROM item_flows i JOIN products p ON i.product_id=p.id" +
                     " JOIN categories c ON p.category_id=c.id WHERE i.action='RECEIVED'" +
                     " AND DATE(i.flow_date) BETWEEN ? AND ? AND i.client_id=? ORDER BY i.flow_date DESC";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, from); st.setString(2, to); st.setString(3, Database.getCurrentClientId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                BigDecimal tot = rs.getBigDecimal("tot");
                total = total.add(tot);
                mdl.addRow(new Object[]{rs.getString("dt"), rs.getString("name"), rs.getString("cat"),
                    rs.getString("vendor"), rs.getInt("quantity"),
                    fmtCurrency(rs.getBigDecimal("cost_price")), fmtCurrency(tot)});
            }
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadPurchaseReceive", e); }
        totLbl.setText("Total Received: " + fmtCurrency(total));
    }

    private void loadOutOfStock(DefaultTableModel mdl) {
        mdl.setRowCount(0);
        String sql = "SELECT p.name,c.name as cat,p.vendor,p.cost_price,p.selling_price," +
                     "COALESCE(MAX(i.flow_date),'Never') as last," +
                     "julianday('now')-julianday(COALESCE(MAX(i.flow_date),'now')) as d" +
                     " FROM products p JOIN categories c ON p.category_id=c.id" +
                     " LEFT JOIN item_flows i ON p.id=i.product_id AND i.action='RECEIVED'" +
                     " WHERE p.client_id=? AND p.stock_quantity=0 GROUP BY p.id ORDER BY d DESC";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                rs.getString("vendor"), fmtCurrency(rs.getBigDecimal("cost_price")),
                fmtCurrency(rs.getBigDecimal("selling_price")),
                rs.getString("last"), (int)rs.getDouble("d") + " days"});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadOutOfStock", e); }
    }

    private void loadNearExpire(DefaultTableModel mdl, String period) {
        mdl.setRowCount(0);
        int days = Integer.parseInt(period.split(" ")[0]);
        String sql = "SELECT p.name,c.name as cat,p.expire_date,p.stock_quantity,p.selling_price," +
                     "julianday(p.expire_date)-julianday('now') as dl" +
                     " FROM products p JOIN categories c ON p.category_id=c.id" +
                     " WHERE p.client_id=? AND p.expire_date IS NOT NULL AND p.expire_date!=''" +
                     " AND julianday(p.expire_date)-julianday('now') BETWEEN 0 AND ? ORDER BY dl ASC";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId()); st.setInt(2, days);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                int dl = (int)rs.getDouble("dl");
                String status = dl <= 7 ? "CRITICAL" : dl <= 30 ? "Warning" : "OK";
                mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"), rs.getString("expire_date"),
                    rs.getInt("stock_quantity"), fmtCurrency(rs.getBigDecimal("selling_price")),
                    dl + " days", status});
            }
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadNearExpire", e); }
    }

    private void loadExpiredStock(DefaultTableModel mdl) {
        mdl.setRowCount(0);
        String sql = "SELECT p.name,c.name as cat,p.expire_date,p.stock_quantity,p.cost_price," +
                     "(p.stock_quantity*p.cost_price) as loss,julianday('now')-julianday(p.expire_date) as od" +
                     " FROM products p JOIN categories c ON p.category_id=c.id" +
                     " WHERE p.client_id=? AND p.expire_date IS NOT NULL AND p.expire_date!=''" +
                     " AND julianday(p.expire_date)-julianday('now')<0 ORDER BY p.expire_date ASC";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"),
                rs.getString("expire_date"), rs.getInt("stock_quantity"),
                fmtCurrency(rs.getBigDecimal("cost_price")),
                fmtCurrency(rs.getBigDecimal("loss")), (int)rs.getDouble("od") + " days"});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadExpiredStock", e); }
    }

    private void loadProfitAnalysis(String from, String to, DefaultTableModel mdl,
            JLabel revLbl, JLabel cstLbl, JLabel prfLbl) {
        mdl.setRowCount(0);
        BigDecimal totRev = BigDecimal.ZERO, totCost = BigDecimal.ZERO;
        String sql = "SELECT p.name,c.name as cat,SUM(si.quantity) as qty,SUM(si.total_price) as rev," +
                     "SUM(si.quantity*p.cost_price) as cost FROM sale_items si" +
                     " JOIN products p ON si.product_id=p.id JOIN categories c ON p.category_id=c.id" +
                     " JOIN sales s ON si.sale_id=s.id" +
                     " WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id=?" +
                     " GROUP BY p.id ORDER BY rev DESC LIMIT 100";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, from); st.setString(2, to); st.setString(3, Database.getCurrentClientId());
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                BigDecimal rev  = rs.getBigDecimal("rev");
                BigDecimal cost = rs.getBigDecimal("cost");
                BigDecimal prf  = rev.subtract(cost);
                double margin   = cost.compareTo(BigDecimal.ZERO) > 0
                    ? prf.divide(cost, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100 : 0;
                totRev  = totRev.add(rev);
                totCost = totCost.add(cost);
                mdl.addRow(new Object[]{rs.getString("name"), rs.getString("cat"), rs.getInt("qty"),
                    fmtCurrency(rev), fmtCurrency(cost), fmtCurrency(prf),
                    String.format("%.1f%%", margin)});
            }
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadProfitAnalysis", e); }
        revLbl.setText("Revenue: " + fmtCurrency(totRev));
        cstLbl.setText("Cost: " + fmtCurrency(totCost));
        prfLbl.setText("Profit: " + fmtCurrency(totRev.subtract(totCost)));
    }

    private void loadTopProducts(DefaultTableModel mdl, String period, int limit) {
        mdl.setRowCount(0);
        String dc = switch (period) {
            case "This Month"    -> "AND strftime('%Y-%m',s.sale_date)=strftime('%Y-%m','now')";
            case "Last Month"    -> "AND strftime('%Y-%m',s.sale_date)=strftime('%Y-%m','now','-1 month')";
            case "Last 3 Months" -> "AND s.sale_date>=date('now','-3 months')";
            default              -> "AND strftime('%Y',s.sale_date)=strftime('%Y','now')";
        };
        String sql = "SELECT p.name,c.name as cat,SUM(si.quantity) as sold,SUM(si.total_price) as rev," +
                     "SUM(si.quantity*p.cost_price) as cost FROM sale_items si" +
                     " JOIN products p ON si.product_id=p.id JOIN categories c ON p.category_id=c.id" +
                     " JOIN sales s ON si.sale_id=s.id WHERE s.client_id=? " + dc +
                     " GROUP BY p.id ORDER BY sold DESC LIMIT ?";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId()); st.setInt(2, limit);
            ResultSet rs = st.executeQuery(); int rank = 1;
            while (rs.next()) {
                BigDecimal rev  = rs.getBigDecimal("rev");
                BigDecimal cost = rs.getBigDecimal("cost");
                mdl.addRow(new Object[]{rank++, rs.getString("name"), rs.getString("cat"),
                    rs.getInt("sold"), fmtCurrency(rev), fmtCurrency(rev.subtract(cost))});
            }
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadTopProducts", e); }
    }

    private void loadAdjustmentLog(DefaultTableModel mdl, String action) {
        mdl.setRowCount(0);
        String sql = "SELECT datetime(i.flow_date,'localtime') as dt,p.name,i.action,i.quantity,i.user" +
                     " FROM item_flows i JOIN products p ON i.product_id=p.id WHERE i.client_id=?" +
                     ("All".equals(action) ? "" : " AND i.action=?") +
                     " ORDER BY i.flow_date DESC LIMIT 500";
        try (PreparedStatement st = Database.getConnection().prepareStatement(sql)) {
            st.setString(1, Database.getCurrentClientId());
            if (!"All".equals(action)) st.setString(2, action);
            ResultSet rs = st.executeQuery();
            while (rs.next()) mdl.addRow(new Object[]{rs.getString("dt"), rs.getString("name"),
                rs.getString("action"), rs.getInt("quantity"),
                rs.getString("user") != null ? rs.getString("user") : "SYSTEM", ""});
        } catch (SQLException e) { LOGGER.log(Level.WARNING, "loadAdjustmentLog", e); }
    }

    // ══════════════════════════════════════════════════════════════
    //  MAIL HISTORY STORAGE
    // ══════════════════════════════════════════════════════════════

    private void loadMailHistory(String statusFilter) {
        if (mailHistoryModel == null) return;
        mailHistoryModel.setRowCount(0);
        Preferences prefs = Preferences.userNodeForPackage(MasterPanel.class);
        String raw = prefs.get("mailHistory", "");
        if (raw.isEmpty()) return;
        int rowNum = 1;
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) continue;
            String status = parts[3];
            if (!"All".equals(statusFilter) && !status.equals(statusFilter)) continue;
            mailHistoryModel.addRow(new Object[]{rowNum++, parts[0], parts[1], parts[2],
                status, parts[4], "FAILED".equals(status) ? "Resend" : ""});
        }
    }

    private void recordMailHistory(String reportType, String recipient, String status, String error) {
        Preferences prefs = Preferences.userNodeForPackage(MasterPanel.class);
        String existing = prefs.get("mailHistory", "");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String newLine = ts + "|" + reportType + "|" + recipient + "|" + status + "|" +
                         (error == null ? "" : error.replace("|", ",").replace("\n", " "));
        String[] lines = existing.isEmpty() ? new String[0] : existing.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 499);
        for (int i = start; i < lines.length; i++) sb.append(lines[i]).append("\n");
        sb.append(newLine);
        prefs.put("mailHistory", sb.toString());
    }

    private void clearMailHistory() {
        Preferences.userNodeForPackage(MasterPanel.class).remove("mailHistory");
    }

    private void resendFailedMail(int row, String reportType, String recipient) {
        JDialog prog = buildProgressDialog("Resending: " + reportType, 1);
        JProgressBar bar  = (JProgressBar) ((JPanel)prog.getContentPane()).getComponent(1);
        JLabel      info  = (JLabel)       ((JPanel)prog.getContentPane()).getComponent(2);
        new Thread(() -> {
            updateProgress(bar, info, 20, "Generating PDF…");
            try {
                byte[] pdf = generateReportPDF(reportType,
                    LocalDate.now().minusDays(30).toString(), LocalDate.now().toString());
                updateProgress(bar, info, 60, "Sending via Brevo…");
                sendEmailWithBrevo(recipient, reportType, pdf, null);
                updateProgress(bar, info, 100, "Done!");
                Thread.sleep(700);
                SwingUtilities.invokeLater(() -> { prog.dispose(); loadMailHistory("All"); showInfo("Resent to " + recipient); });
            } catch (Exception ex) {
                recordMailHistory(reportType, recipient, "FAILED", ex.getMessage());
                SwingUtilities.invokeLater(() -> { prog.dispose(); loadMailHistory("All"); showError("Resend failed: " + ex.getMessage()); });
            }
        }, "resend-brevo").start();
        prog.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  PREFERENCES — PERSISTENT SETTINGS
    // ══════════════════════════════════════════════════════════════

    private void saveSettings() {
        businessName   = settingsFields[0].getText().trim();
        registrationNo = settingsFields[1].getText().trim();
        tinNo          = settingsFields[2].getText().trim();
        mobileNo       = settingsFields[3].getText().trim();
        emailAddr      = settingsFields[4].getText().trim();
        website        = settingsFields[5].getText().trim();
        address        = settingsFields[6].getText().trim();
        currency       = settingsFields[7].getText().trim();
        Preferences p = Preferences.userNodeForPackage(MasterPanel.class);
        p.put("biz_name",  businessName);
        p.put("biz_reg",   registrationNo);
        p.put("biz_tin",   tinNo);
        p.put("biz_tel",   mobileNo);
        p.put("biz_email", emailAddr);
        p.put("biz_web",   website);
        p.put("biz_addr",  address);
        p.put("biz_cur",   currency);
        try { p.flush(); } catch (Exception ignored) {}
        showInfo("Settings saved successfully.");
    }

    private void loadSettings() {
        Preferences p = Preferences.userNodeForPackage(MasterPanel.class);
        businessName   = p.get("biz_name",  "");
        registrationNo = p.get("biz_reg",   "");
        tinNo          = p.get("biz_tin",   "");
        mobileNo       = p.get("biz_tel",   "");
        emailAddr      = p.get("biz_email", "");
        website        = p.get("biz_web",   "");
        address        = p.get("biz_addr",  "");
        currency       = p.get("biz_cur",   "");
    }

    private void populateSettingsFields() {
        if (settingsFields == null || settingsFields.length < 8) return;
        settingsFields[0].setText(businessName);
        settingsFields[1].setText(registrationNo);
        settingsFields[2].setText(tinNo);
        settingsFields[3].setText(mobileNo);
        settingsFields[4].setText(emailAddr);
        settingsFields[5].setText(website);
        settingsFields[6].setText(address);
        settingsFields[7].setText(currency);
    }

    private void saveRecipients() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recipientListModel.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(recipientListModel.get(i));
        }
        Preferences.userNodeForPackage(MasterPanel.class).put("recipients", sb.toString());
    }

    private void loadRecipients() {
        if (recipientListModel == null) return;
        String raw = Preferences.userNodeForPackage(MasterPanel.class).get("recipients", "");
        if (raw.isEmpty()) return;
        for (String r : raw.split(";")) if (!r.trim().isEmpty()) recipientListModel.addElement(r.trim());
    }

    private void saveAutoSendPrefs() {
        Preferences p = Preferences.userNodeForPackage(MasterPanel.class);
        p.putBoolean("autoSendEnabled", autoSendEnabledChk != null && autoSendEnabledChk.isSelected());
        p.put("autoSendTime", autoSendTimeCombo != null ? (String)autoSendTimeCombo.getSelectedItem() : "8:00 AM");
        if (autoSendCheckboxes != null) {
            StringBuilder sb = new StringBuilder();
            for (JCheckBox c : autoSendCheckboxes) if (c.isSelected()) sb.append(c.getText()).append(";");
            p.put("autoSendReports", sb.toString());
        }
        try { p.flush(); } catch (Exception ignored) {}
    }

    private void loadAutoSendPrefs() {
        Preferences p = Preferences.userNodeForPackage(MasterPanel.class);
        if (autoSendEnabledChk != null) autoSendEnabledChk.setSelected(p.getBoolean("autoSendEnabled", false));
        if (autoSendTimeCombo  != null) autoSendTimeCombo.setSelectedItem(p.get("autoSendTime", "8:00 AM"));
        String rpts = p.get("autoSendReports", "");
        if (autoSendCheckboxes != null && !rpts.isEmpty()) {
            Set<String> enabled = new HashSet<>(Arrays.asList(rpts.split(";")));
            for (JCheckBox c : autoSendCheckboxes) c.setSelected(enabled.contains(c.getText()));
        }
        if (autoSendEnabledChk != null && autoSendEnabledChk.isSelected()) startAutoSendTimer();
    }

    // ══════════════════════════════════════════════════════════════
    //  UI FACTORY HELPERS
    // ══════════════════════════════════════════════════════════════

    private JPanel opaquePanel(LayoutManager lm) {
        JPanel p = new JPanel(lm); p.setOpaque(false); return p;
    }

    private JTabbedPane buildStyledTabs() {
        JTabbedPane tp = new JTabbedPane();
        tp.setFont(FONT_HEAD);
        tp.setBackground(new Color(14, 18, 40, 200));
        tp.setForeground(TEXT_PRI);
        tp.setBorder(null);
        return tp;
    }

    private JTable styledTable(DefaultTableModel mdl) {
        JTable tbl = new JTable(mdl);
        tbl.setRowHeight(30);
        tbl.setFont(FONT_BODY);
        tbl.setForeground(TEXT_PRI);
        tbl.setBackground(new Color(15, 18, 40, 0));
        tbl.setOpaque(false);
        tbl.setSelectionBackground(new Color(99, 179, 255, 70));
        tbl.setGridColor(BORDER_C);
        tbl.setShowHorizontalLines(true);
        tbl.setShowVerticalLines(false);
        tbl.setIntercellSpacing(new Dimension(0, 1));
        JTableHeader th = tbl.getTableHeader();
        th.setFont(FONT_HEAD);
        th.setBackground(new Color(20, 28, 65));
        th.setForeground(ACCENT);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT.darker()));
        th.setReorderingAllowed(false);
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel) setBackground(r % 2 == 0 ? new Color(15,18,40,120) : new Color(22,27,55,120));
                setForeground(TEXT_PRI);
                setHorizontalAlignment(c > 0 ? SwingConstants.CENTER : SwingConstants.LEFT);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                setOpaque(true);
                return this;
            }
        });
        return tbl;
    }

    private DefaultTableModel noEditModel(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JTextField inputField(String placeholder) {
        JTextField f = new JTextField(20) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus() && !placeholder.isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setFont(FONT_SMALL);
                    g2.setColor(new Color(130, 145, 195, 130));
                    g2.drawString(placeholder, 10, getHeight() / 2 + 4);
                    g2.dispose();
                }
            }
        };
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT_PRI);
        f.setCaretColor(ACCENT);
        f.setFont(FONT_BODY);
        f.setOpaque(true);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return f;
    }

    private JPanel dateRangeFilter(JDateChooser[] drOut, Runnable onRefresh) {
        drOut[0] = buildDateChooser(LocalDate.now().minusDays(30));
        drOut[1] = buildDateChooser(LocalDate.now());
        JButton btn = actionButton("Refresh", onRefresh);
        return filterRow(label("From:"), drOut[0], label("To:"), drOut[1], btn);
    }

    private JDateChooser buildDateChooser(LocalDate initial) {
        JDateChooser dc = new JDateChooser();
        dc.setDateFormatString("yyyy-MM-dd");
        dc.setDate(java.sql.Date.valueOf(initial));
        dc.setPreferredSize(new Dimension(145, 32));
        dc.setBackground(BG_INPUT);
        dc.setForeground(TEXT_PRI);
        dc.setFont(FONT_BODY);
        dc.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
        if (dc.getDateEditor() instanceof com.toedter.calendar.JTextFieldDateEditor ed) {
            ed.setBackground(BG_INPUT);
            ed.setForeground(TEXT_PRI);
            ed.setCaretColor(ACCENT);
            ed.setFont(FONT_BODY);
            ed.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        }
        return dc;
    }

    private String fmtDate(JDateChooser dc) {
        if (dc == null || dc.getDate() == null) return LocalDate.now().toString();
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(dc.getDate());
    }

    private String defaultFrom() { return LocalDate.now().minusDays(30).toString(); }
    private String defaultTo()   { return LocalDate.now().toString(); }

    private JComboBox<String> styledCombo(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        styleCombo(c);
        return c;
    }

    private void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_INPUT);
        c.setForeground(TEXT_PRI);
        c.setFont(FONT_BODY);
        c.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(BG_INPUT);
        s.setForeground(TEXT_PRI);
        s.setFont(FONT_BODY);
        JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) s.getEditor();
        ed.getTextField().setBackground(BG_INPUT);
        ed.getTextField().setForeground(TEXT_PRI);
        ed.getTextField().setCaretColor(ACCENT);
        s.setBorder(BorderFactory.createLineBorder(BORDER_C, 1));
    }

    private JCheckBox styledCheck(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setFont(FONT_BODY);
        c.setForeground(TEXT_PRI);
        c.setOpaque(false);
        return c;
    }

    private JButton actionButton(String text) { return actionButton(text, null); }

    private JButton actionButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(FONT_BODY);
        b.setForeground(TEXT_PRI);
        b.setBackground(BG_INPUT);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT.darker()); b.setForeground(Color.WHITE); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(BG_INPUT); b.setForeground(TEXT_PRI); }
        });
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    private JButton accentButton(String text, Color accent) {
        JButton b = new JButton(text);
        b.setFont(FONT_BODY);
        b.setForeground(Color.WHITE);
        b.setBackground(accent.darker());
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent.darker().darker(), 1),
            BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(accent); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(accent.darker()); }
        });
        return b;
    }

    private JButton emailButton(String text) { return accentButton(text, new Color(34, 120, 220)); }

    private JToggleButton navToggle(String text) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(FONT_SMALL);
        b.setForeground(TEXT_SEC);
        b.setBackground(BG_GLASS2);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C, 1),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> {
            if (b.isSelected()) { b.setBackground(ACCENT.darker()); b.setForeground(Color.WHITE); }
            else                { b.setBackground(BG_GLASS2);       b.setForeground(TEXT_SEC); }
        });
        return b;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BODY);
        l.setForeground(TEXT_SEC);
        return l;
    }

    private JLabel summaryLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEAD);
        l.setForeground(SUCCESS);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBackground(BG_GLASS);
        l.setOpaque(true);
        l.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_C, 1),
            BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        return l;
    }

    private JPanel summaryRow(JLabel... labels) {
        JPanel p = new JPanel(new GridLayout(1, labels.length, 10, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        for (JLabel l : labels) p.add(l);
        return p;
    }

    private JPanel filterRow(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setOpaque(false);
        for (Component c : comps) p.add(c);
        return p;
    }

    private String extractEmail(String entry) {
        if (entry.contains("<")) return entry.substring(entry.indexOf('<') + 1, entry.lastIndexOf('>'));
        return entry.trim();
    }

    private String extractName(String entry) {
        if (entry.contains("<")) return entry.substring(0, entry.indexOf('<')).trim();
        return entry.split("@")[0];
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(masterDialog, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(masterDialog, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            MasterPanel.open();
        });
    }
}
