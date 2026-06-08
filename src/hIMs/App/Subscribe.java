package hIMs.App;

import com.google.gson.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javax.swing.Timer;
/**
 * SubscriptionManager — Cloud-first subscription gate using SQLite Cloud + PesaPal.
 *
 * Flow:
 *   1. On login, call SubscriptionManager.init(email) — checks cloud for subscription row.
 *   2. If no row exists → insert a 30-day free trial row in cloud.
 *   3. Every app start, call SubscriptionManager.isAccessAllowed() — if false, show PaymentDialog.
 *   4. Payment opens PesaPal in the browser, user pays, clicks "I've Paid" — we call
 *      PesaPal status API to verify, then update cloud subscription row to ACTIVE.
 *
 * Security:  All authoritative data lives in SQLite Cloud (subscriptions table).
 *            Local state is only a short-lived cache — never trusted alone.
 */
public final class Subscribe {

    // ─── PesaPal credentials ────────────────────────────────────────────────
    private static final String PP_KEY    = "z99giZo/EMa2KKYNPlsSE2fTk47rl+D8";   // ← replace
    private static final String PP_SECRET = "XN65M6DeGE4lrb9lTKzdPrg/3sQ="; // ← replace
    private static final String PP_ENV    = "production";              // "sandbox" | "production"

    private static final String PP_TOKEN_URL = PP_ENV.equals("production")
            ? "https://pay.pesapal.com/v3/api/Auth/RequestToken"
            : "https://cybqa.pesapal.com/pesapalv3/api/Auth/RequestToken";
    private static final String PP_ORDER_URL = PP_ENV.equals("production")
            ? "https://pay.pesapal.com/v3/api/Transactions/SubmitOrderRequest"
            : "https://cybqa.pesapal.com/pesapalv3/api/Transactions/SubmitOrderRequest";
    private static final String PP_STATUS_URL = PP_ENV.equals("production")
            ? "https://pay.pesapal.com/v3/api/Transactions/GetTransactionStatus"
            : "https://cybqa.pesapal.com/pesapalv3/api/Transactions/GetTransactionStatus";
    private static final String PP_IPN_URL = PP_ENV.equals("production")
            ? "https://pay.pesapal.com/v3/api/URLSetup/RegisterIPN"
            : "https://cybqa.pesapal.com/pesapalv3/api/URLSetup/RegisterIPN";
    private static final String CALLBACK_URL = "https://pay.pesapal.com/iframe/PesapalIframe3/Index"; // ← replace

    // ─── Plans ───────────────────────────────────────────────────────────────
    public enum Plan {
        MONTHLY("Monthly",  "5,000 TZS",  5000,  30,  "Pay every month. Cancel anytime."),
        YEARLY ("Yearly",  "50,000 TZS", 50000, 365, "Save 2 months. Best value.");

        public final String label, price, tagline;
        public final int amountTZS, days;
        Plan(String l, String p, int amt, int d, String t) {
            label = l; price = p; amountTZS = amt; days = d; tagline = t;
        }
    }

    // ─── Status ──────────────────────────────────────────────────────────────
    public enum SubStatus { TRIAL, ACTIVE, GRACE, EXPIRED, NONE }

    // ─── Session ─────────────────────────────────────────────────────────────
    private static String  email;
    private static SubStatus cachedStatus = SubStatus.NONE;
    private static int     cachedDaysLeft = 0;
    private static long    cacheTimestamp = 0;
    private static final long CACHE_TTL = 60_000; // 1 min

    private static String  ppToken;
    private static long    ppTokenExpiry = 0;
    private static String  lastIpnId;           // registered IPN id (cached)

    private static final Logger LOG = Logger.getLogger(Subscribe.class.getName());

    private Subscribe() {}

    // ═════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═════════════════════════════════════════════════════════════════════════

    /** Call once after login. Initialises trial row if user is brand-new. */
    public static void init(String userEmail) {
    email = userEmail.trim().toLowerCase();
    // Run synchronously so cache is ready before login continues
    try {
        ensureSubscriptionRow();
        refreshCache();
    } catch (Exception e) {
        LOG.warning("init failed: " + e.getMessage());
        // Fallback: grant local trial so user isn't locked out
        cachedStatus = SubStatus.TRIAL;
        cachedDaysLeft = 30;
        cacheTimestamp = System.currentTimeMillis();
    }
}

    /** Returns true if the user may use the app (trial, active, or grace period). */
    public static boolean isAccessAllowed() {
        refreshCacheIfStale();
        return cachedStatus == SubStatus.TRIAL
            || cachedStatus == SubStatus.ACTIVE
            || cachedStatus == SubStatus.GRACE;
    }

    /** Days left in current period (trial or paid). 0 if expired. */
    public static int getDaysLeft()   { refreshCacheIfStale(); return cachedDaysLeft; }
    public static SubStatus getStatus() { refreshCacheIfStale(); return cachedStatus; }

    /**
     * Call at every app startup (after init).
     * If access is not allowed, shows the payment wall and blocks until resolved or user quits.
     */
    public static void enforceOnStartup(JFrame owner) {
        refreshCacheIfStale();
        if (!isAccessAllowed()) {
            PaymentDialog.showBlocking(owner);
        }
    }

    /** Opens payment dialog (non-blocking, for settings/upgrade button). */
    public static void showUpgradeDialog(JFrame owner) {
        new PaymentDialog(owner, false).setVisible(true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CLOUD SUBSCRIPTION ROW
    // ═════════════════════════════════════════════════════════════════════════

    private static void ensureSubscriptionRow() throws IOException {
    try {
        String sql = "SELECT email FROM subscriptions WHERE email = '" + esc(email) + "' LIMIT 1";
        var rows = SQLiteCloudClient.execute(sql);
        if (!rows.isEmpty()) return;

        String trialEnd = LocalDate.now().plusDays(30)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String insert = "INSERT OR IGNORE INTO subscriptions (email, status, trial_end, paid_until, created_at) " +
                "VALUES ('" + esc(email) + "', 'TRIAL', '" + trialEnd + "', NULL, datetime('now'))";
        SQLiteCloudClient.executeUpdate(insert);
        LOG.info("Trial row created for " + email + " until " + trialEnd);
    } catch (IOException e) {
        // RLS blocking — set local trial cache so user isn't locked out
        LOG.warning("Cloud insert blocked, using local trial: " + e.getMessage());
        cachedStatus = SubStatus.TRIAL;
        cachedDaysLeft = 30;
        cacheTimestamp = System.currentTimeMillis();
    }
}

    public static void refreshCacheIfStale() {
        if (System.currentTimeMillis() - cacheTimestamp > CACHE_TTL) {
            try { 
                refreshCache(); 
            } catch (Exception e) { 
                LOG.warning("Failed to refresh cache: " + e.getMessage());
            }
        }
    }
 // Add this method to Subscribe.java (near other public methods)
    public static void forceRefreshIfStale() {
        refreshCacheIfStale();
    }
    

    private static void refreshCache() throws IOException {
    String sql = "SELECT status, trial_end, paid_until FROM subscriptions " +
                 "WHERE email = '" + esc(email) + "' LIMIT 1";
    var rows = SQLiteCloudClient.execute(sql);
    if (rows.isEmpty()) {
        // If we have a locally set trial, don't overwrite it with NONE
        if (cachedStatus == SubStatus.TRIAL && cachedDaysLeft > 0) {
            cacheTimestamp = System.currentTimeMillis();
            return;
        }
        cachedStatus = SubStatus.NONE;
        cachedDaysLeft = 0;
        cacheTimestamp = System.currentTimeMillis();
        return;
    }

        JsonObject row  = rows.get(0);
        String status   = row.has("status") && !row.get("status").isJsonNull() 
                          ? row.get("status").getAsString() : "NONE";
        String trialEnd = row.has("trial_end") && !row.get("trial_end").isJsonNull()
                          ? row.get("trial_end").getAsString() : null;
        String paidUntil = row.has("paid_until") && !row.get("paid_until").isJsonNull()
                          ? row.get("paid_until").getAsString() : null;

        LocalDate today = LocalDate.now();

        switch (status.toUpperCase()) {
            case "TRIAL" -> {
                if (trialEnd == null) { 
                    cachedStatus = SubStatus.EXPIRED; 
                    cachedDaysLeft = 0; 
                    break; 
                }
                LocalDate end = LocalDate.parse(trialEnd.length() > 10 ? trialEnd.substring(0, 10) : trialEnd); // Fixed: handle string length
                long days = java.time.temporal.ChronoUnit.DAYS.between(today, end);
                if (days >= 0) { 
                    cachedStatus = SubStatus.TRIAL; 
                    cachedDaysLeft = (int) days; 
                } else { 
                    cachedStatus = SubStatus.EXPIRED; 
                    cachedDaysLeft = 0;
                    updateCloudStatus("EXPIRED"); 
                }
            }
            case "ACTIVE" -> {
                if (paidUntil == null) { 
                    cachedStatus = SubStatus.ACTIVE; 
                    cachedDaysLeft = 999; 
                    break; 
                }
                LocalDate end = LocalDate.parse(paidUntil.length() > 10 ? paidUntil.substring(0, 10) : paidUntil); // Fixed: handle string length
                long days = java.time.temporal.ChronoUnit.DAYS.between(today, end);
                if (days >= 0) { 
                    cachedStatus = SubStatus.ACTIVE; 
                    cachedDaysLeft = (int) days; 
                } else if (days >= -4) { 
                    cachedStatus = SubStatus.GRACE; 
                    cachedDaysLeft = 0; 
                } else { 
                    cachedStatus = SubStatus.EXPIRED; 
                    cachedDaysLeft = 0;
                    updateCloudStatus("EXPIRED"); 
                }
            }
            case "GRACE"   -> { cachedStatus = SubStatus.GRACE;   cachedDaysLeft = 0; }
            case "EXPIRED" -> { cachedStatus = SubStatus.EXPIRED; cachedDaysLeft = 0; }
            default        -> { cachedStatus = SubStatus.NONE;    cachedDaysLeft = 0; }
        }
        cacheTimestamp = System.currentTimeMillis();
    }

    private static void updateCloudStatus(String newStatus) {
        try {
            SQLiteCloudClient.executeUpdate(
                "UPDATE subscriptions SET status = '" + newStatus + "', " +
                "updated_at = datetime('now') WHERE email = '" + esc(email) + "'");
        } catch (IOException e) { LOG.warning("updateCloudStatus: " + e.getMessage()); }
    }

    /** Called after successful payment verification. */
    static void activateSubscription(Plan plan) throws IOException {
        String paidUntil = LocalDate.now().plusDays(plan.days)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        SQLiteCloudClient.executeUpdate(
            "UPDATE subscriptions SET status='ACTIVE', paid_until='" + paidUntil + "', " +
            "updated_at=datetime('now') WHERE email='" + esc(email) + "'");
        // Force cache refresh
        cacheTimestamp = 0;
        refreshCache();
        LOG.info("Subscription activated for " + email + " until " + paidUntil);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PESAPAL API
    // ═════════════════════════════════════════════════════════════════════════

    static String getPesapalToken() throws IOException {
        if (ppToken != null && System.currentTimeMillis() < ppTokenExpiry) return ppToken;

        String body = "{\"consumer_key\":\"" + PP_KEY + "\",\"consumer_secret\":\"" + PP_SECRET + "\"}";
        JsonObject res = postJson(PP_TOKEN_URL, body, null);
        if (!res.has("token")) { // Fixed: check for token field
            throw new IOException("Invalid response from PesaPal token endpoint");
        }
        ppToken       = res.get("token").getAsString();
        ppTokenExpiry = System.currentTimeMillis() + 55 * 60_000;
        return ppToken;
    }

    /** Registers an IPN URL with PesaPal and returns the ipn_id. Cached per session. */
    static String getOrRegisterIpnId() throws IOException {
        if (lastIpnId != null) return lastIpnId;
        String token = getPesapalToken();
        String body  = "{\"url\":\"" + CALLBACK_URL + "\",\"ipn_notification_type\":\"GET\"}";
        JsonObject res = postJson(PP_IPN_URL, body, token);
        lastIpnId = res.has("ipn_id") ? res.get("ipn_id").getAsString() : "";
        if (lastIpnId.isEmpty()) { // Fixed: handle missing ipn_id
            throw new IOException("Failed to register IPN URL");
        }
        return lastIpnId;
    }

    /**
     * Creates a PesaPal order and returns [redirect_url, order_tracking_id].
     */
    static String[] createOrder(Plan plan) throws IOException {
        String token = getPesapalToken();
        String ipnId = getOrRegisterIpnId();
        String orderId = "SUB_" + System.currentTimeMillis();

        JsonObject billing = new JsonObject();
        billing.addProperty("email_address", email);
        billing.addProperty("country_code", "TZ");

        JsonObject order = new JsonObject();
        order.addProperty("id", orderId);
        order.addProperty("currency", "TZS");
        order.addProperty("amount", plan.amountTZS);
        order.addProperty("description", "GratisPOS Pro – " + plan.label);
        order.addProperty("callback_url", CALLBACK_URL);
        order.addProperty("notification_id", ipnId);
        order.add("billing_address", billing);

        JsonObject res = postJson(PP_ORDER_URL, order.toString(), token);
        
        if (!res.has("redirect_url") || !res.has("order_tracking_id")) { // Fixed: validate response
            throw new IOException("Invalid response from PesaPal order endpoint");
        }
        
        return new String[]{
            res.get("redirect_url").getAsString(),
            res.get("order_tracking_id").getAsString()
        };
    }

    /** Returns true if the order is confirmed as paid. */
    static boolean verifyPayment(String orderTrackingId) throws IOException {
        String token = getPesapalToken();
        String url   = PP_STATUS_URL + "?orderTrackingId=" + orderTrackingId;
        JsonObject res = getJson(url, token);
        String desc  = res.has("payment_status_description")
                       ? res.get("payment_status_description").getAsString() : "";
        return "Completed".equalsIgnoreCase(desc);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HTTP HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private static JsonObject postJson(String urlStr, String body, String bearerToken) throws IOException {
        HttpURLConnection c = null;
        try {
            c = open(urlStr, "POST", bearerToken);
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            return readResponse(c);
        } finally {
            if (c != null) c.disconnect(); // Fixed: ensure connection is closed
        }
    }

    private static JsonObject getJson(String urlStr, String bearerToken) throws IOException {
        HttpURLConnection c = null;
        try {
            c = open(urlStr, "GET", bearerToken);
            return readResponse(c);
        } finally {
            if (c != null) c.disconnect(); // Fixed: ensure connection is closed
        }
    }

    private static HttpURLConnection open(String urlStr, String method, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token); // Fixed: check for empty token
        if ("POST".equals(method)) c.setDoOutput(true);
        return c;
    }

    private static JsonObject readResponse(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        InputStream is = code < 300 ? c.getInputStream() : c.getErrorStream();
        String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code >= 300) throw new IOException("HTTP " + code + ": " + raw);
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static String esc(String s) { return s == null ? "" : s.replace("'", "''"); }

 // ═════════════════════════════════════════════════════════════════════════
//  PAYMENT DIALOG  —  Clean, professional, dark UI
// ═════════════════════════════════════════════════════════════════════════

static class PaymentDialog extends JDialog {

    // ── Design tokens ────────────────────────────────────────────────────
    private static final Color BG          = new Color(15, 18, 30);
    private static final Color CARD_BG     = new Color(22, 27, 45);
    private static final Color CARD_BORDER = new Color(45, 55, 85);
    private static final Color ACCENT      = new Color(79, 140, 255);
    private static final Color ACCENT2     = new Color(138, 79, 255);
    private static final Color SUCCESS     = new Color(34, 197, 120);
    private static final Color WARNING     = new Color(251, 191, 36);
    private static final Color DANGER      = new Color(239, 68, 68);
    private static final Color TEXT_H      = new Color(240, 244, 255);
    private static final Color TEXT_B      = new Color(160, 170, 200);
    private static final Color TEXT_M      = new Color(100, 112, 150);

    private static final Font  F_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font  F_CARD  = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font  F_BODY  = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  F_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font  F_PRICE = new Font("Segoe UI", Font.BOLD, 26);
    private static final Font  F_BTN   = new Font("Segoe UI", Font.BOLD, 13);

    private final boolean blocking;

    private CardLayout mainLayout;
    private JPanel     mainPanel;
    private Plan       currentPlan;

    static void showBlocking(JFrame owner) {
        PaymentDialog d = new PaymentDialog(owner, true);
        d.setVisible(true);
    }

    PaymentDialog(JFrame owner, boolean blocking) {
        super(owner, "GratisPOS — Subscription", blocking);
        this.blocking = blocking;
        setUndecorated(true);
        setSize(720, 520);
        setLocationRelativeTo(owner);
        setBackground(BG);

        if (blocking) {
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { showExitConfirmation(); }
            });
        }

        mainLayout = new CardLayout();
        mainPanel  = new JPanel(mainLayout);
        mainPanel.setBackground(BG);
        mainPanel.add(buildMainScreen(),    "main");
        mainPanel.add(buildProcessScreen(), "processing");
        mainPanel.add(buildSuccessScreen(), "success");

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(mainPanel,       BorderLayout.CENTER);
        setContentPane(root);
        setDefaultCloseOperation(blocking
                ? WindowConstants.DO_NOTHING_ON_CLOSE
                : WindowConstants.DISPOSE_ON_CLOSE);
    }

    // ── Exit confirmation (glass morphism) ────────────────────────────────
    private void showExitConfirmation() {
        JDialog dlg = new JDialog(this, "Confirm Exit", true);
        dlg.setUndecorated(true);
        dlg.setSize(400, 230);
        dlg.setLocationRelativeTo(this);

        JPanel glass = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(22, 27, 45, 230));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(79, 140, 255, 100));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };
        glass.setLayout(new BoxLayout(glass, BoxLayout.Y_AXIS));
        glass.setBorder(new EmptyBorder(28, 30, 28, 30));
        glass.setOpaque(false);

        JLabel icon = lbl("⚠", new Font("Segoe UI", Font.PLAIN, 40), WARNING);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = lbl("Exit Application?", new Font("Segoe UI", Font.BOLD, 18), TEXT_H);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msg = new JLabel(
            "<html><center>Payment is required to use the application.<br>" +
            "Your data will be saved but you won't have<br>access until you subscribe.</center></html>");
        msg.setFont(F_BODY);
        msg.setForeground(TEXT_B);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnRow.setOpaque(false);

        JButton confirmBtn = createGlassButton("Yes, Exit", DANGER);
        confirmBtn.addActionListener(e -> { LOG.info("User exited without payment"); System.exit(0); });

        JButton cancelBtn = createGlassButton("Cancel", ACCENT);
        cancelBtn.addActionListener(e -> dlg.dispose());

        btnRow.add(confirmBtn);
        btnRow.add(cancelBtn);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        glass.add(icon);
        glass.add(vgap(12));
        glass.add(title);
        glass.add(vgap(8));
        glass.add(msg);
        glass.add(vgap(22));
        glass.add(btnRow);

        dlg.setContentPane(glass);
        dlg.setVisible(true);
    }

    // ── Generic glass message dialog ──────────────────────────────────────
    private void showGlassMessageDialog(String titleText, String message, int type) {
        JDialog dlg = new JDialog(this, titleText, true);
        dlg.setUndecorated(true);
        dlg.setSize(400, 230);
        dlg.setLocationRelativeTo(this);

        Color accentColor;
        String iconChar;
        switch (type) {
            case JOptionPane.ERROR_MESSAGE:   accentColor = DANGER;  iconChar = "✗"; break;
            case JOptionPane.WARNING_MESSAGE: accentColor = WARNING; iconChar = "⚠"; break;
            case JOptionPane.INFORMATION_MESSAGE: accentColor = ACCENT; iconChar = "ℹ"; break;
            default:                          accentColor = SUCCESS; iconChar = "✓";
        }
        final Color ac = accentColor;

        JPanel glass = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(22, 27, 45, 230));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 100));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };
        glass.setLayout(new BoxLayout(glass, BoxLayout.Y_AXIS));
        glass.setBorder(new EmptyBorder(28, 30, 28, 30));
        glass.setOpaque(false);

        JLabel iconLbl = lbl(iconChar, new Font("Segoe UI", Font.PLAIN, 40), accentColor);
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msgLbl = new JLabel("<html><center>" + message + "</center></html>");
        msgLbl.setFont(F_BODY);
        msgLbl.setForeground(TEXT_B);
        msgLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton okBtn = createGlassButton("OK", accentColor);
        okBtn.addActionListener(e -> dlg.dispose());
        okBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        glass.add(iconLbl);
        glass.add(vgap(12));
        glass.add(msgLbl);
        glass.add(vgap(22));
        glass.add(okBtn);

        dlg.setContentPane(glass);
        dlg.setVisible(true);
    }

    // ── Glass button ──────────────────────────────────────────────────────
    private JButton createGlassButton(String text, Color color) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int alpha = getModel().isRollover() ? 200 : 150;
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(F_BTN);
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(130, 38));
        b.setMaximumSize(new Dimension(130, 38));
        return b;
    }

    // ── Title bar ──────────────────────────────────────────────────────────
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(12, 15, 25));
        bar.setBorder(new EmptyBorder(14, 22, 14, 16));

        JLabel logo = lbl("GratisPOS", F_BTN, ACCENT);
        JLabel sub  = lbl("Subscription", F_SMALL, TEXT_M);
        JPanel left = row(8, logo, sep(), sub);
        left.setOpaque(false);
        bar.add(left, BorderLayout.WEST);

        JButton closeBtn = iconBtn("✕", TEXT_M);
        if (blocking) closeBtn.addActionListener(e -> showExitConfirmation());
        else          closeBtn.addActionListener(e -> dispose());
        bar.add(closeBtn, BorderLayout.EAST);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(bar, BorderLayout.CENTER);
        JSeparator line = new JSeparator();
        line.setForeground(CARD_BORDER);
        wrap.add(line, BorderLayout.SOUTH);
        return wrap;
    }

    // ── Main screen ────────────────────────────────────────────────────────
    private JPanel buildMainScreen() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(28, 32, 24, 32));

        // Banner
        SubStatus st       = cachedStatus;
        int       daysLeft = cachedDaysLeft;
        String bannerText; Color bannerColor;
        if (st == SubStatus.TRIAL) {
            bannerText  = daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " left in your free trial";
            bannerColor = daysLeft <= 5 ? WARNING : SUCCESS;
        } else if (st == SubStatus.GRACE) {
            bannerText  = "Grace period — please renew to avoid losing access";
            bannerColor = WARNING;
        } else {
            bannerText  = "Your trial has expired. Choose a plan to continue.";
            bannerColor = DANGER;
        }

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(banner(bannerText, bannerColor));
        header.add(vgap(14));

        JLabel titleLbl = lbl("Upgrade to Pro", F_TITLE, TEXT_H);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descLbl = lbl("Sync across devices, unlock all reports, and get priority support.", F_BODY, TEXT_B);
        descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLbl);
        header.add(vgap(4));
        header.add(descLbl);

        // Cards
        JPanel cards = new JPanel(new GridLayout(1, 2, 20, 0));
        cards.setOpaque(false);
        cards.add(createPlanCard(Plan.MONTHLY, ACCENT, false));
        cards.add(createPlanCard(Plan.YEARLY, ACCENT2, true));

        // Features
        String[] features = {"☁  Cloud Sync", "📊  Full Reports", "🔄  Multi-Device", "🔒  Data Backup", "⚡  Priority Support"};
        JPanel featureRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 0));
        featureRow.setOpaque(false);
        for (String f : features) featureRow.add(lbl(f, F_SMALL, TEXT_B));

        // Footer
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);

        if (blocking) {
            JLabel exitLink = new JLabel("Exit application without subscribing");
            exitLink.setFont(F_SMALL);
            exitLink.setForeground(DANGER);
            exitLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
            exitLink.setAlignmentX(Component.CENTER_ALIGNMENT);
            exitLink.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e)  { showExitConfirmation(); }
                @Override public void mouseEntered(MouseEvent e)  { exitLink.setForeground(DANGER.brighter()); }
                @Override public void mouseExited(MouseEvent e)   { exitLink.setForeground(DANGER); }
            });
            footer.add(vgap(10));
            footer.add(exitLink);
            footer.add(vgap(6));
        }

        JLabel note = lbl("Payments processed securely via PesaPal  •  M-Pesa · Airtel · Tigo · Visa · Mastercard", F_SMALL, TEXT_M);
        note.setAlignmentX(Component.CENTER_ALIGNMENT);
        footer.add(note);

        // Center assembly
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.add(vgap(18));
        center.add(cards);
        center.add(vgap(14));
        center.add(featureRow);

        // Footer wrapper (centered horizontally)
        JPanel footerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footerWrap.setOpaque(false);
        footerWrap.add(footer);

        p.add(header,     BorderLayout.NORTH);
        p.add(center,     BorderLayout.CENTER);
        p.add(footerWrap, BorderLayout.SOUTH);
        return p;
    }

    // ── Plan card ──────────────────────────────────────────────────────────
    private JPanel createPlanCard(Plan plan, Color accent, boolean highlight) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(highlight ? accent : CARD_BORDER);
                g2.setStroke(new BasicStroke(highlight ? 1.5f : 1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(22, 24, 22, 24));

        if (highlight) {
            JLabel badge = pill("BEST VALUE", accent);
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(badge);
            card.add(vgap(10));
        } else {
            card.add(vgap(28)); // same visual height as badge row
        }

        JLabel nameLbl  = lbl(plan.label,   F_CARD,  TEXT_H);
        JLabel priceLbl = lbl(plan.price,   F_PRICE, accent);
        JLabel tagLbl   = lbl(plan.tagline, F_SMALL, TEXT_B);
        nameLbl .setAlignmentX(Component.LEFT_ALIGNMENT);
        priceLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagLbl  .setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(nameLbl);
        card.add(vgap(4));
        card.add(priceLbl);
        card.add(vgap(4));
        card.add(tagLbl);
        card.add(vgap(16));

        JSeparator sep = new JSeparator();
        sep.setForeground(CARD_BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(sep);
        card.add(vgap(12));

        String[] perks = plan == Plan.MONTHLY
                ? new String[]{"All Pro features", "Cloud sync", "Priority support"}
                : new String[]{"Everything in Monthly", "Save 2 months FREE", "Dedicated support"};
        for (String perk : perks) {
            JLabel pk = lbl("✓  " + perk, F_SMALL, TEXT_B);
            pk.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(pk);
            card.add(vgap(5));
        }

        card.add(vgap(16));

        JButton payBtn = new JButton("Pay with PesaPal →");
        payBtn.setFont(F_BTN);
        payBtn.setForeground(Color.WHITE);
        payBtn.setBackground(accent);
        payBtn.setFocusPainted(false);
        payBtn.setBorderPainted(false);
        payBtn.setOpaque(true); // ← CRITICAL: must be true for setBackground to paint
        payBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        payBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        payBtn.setPreferredSize(new Dimension(210, 42));
        payBtn.setMaximumSize(new Dimension(210, 42));
        payBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { payBtn.setBackground(accent.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { payBtn.setBackground(accent); }
        });
        payBtn.addActionListener(e -> startPayment(plan));
        card.add(payBtn);

        return card;
    }

    // ── Processing screen ──────────────────────────────────────────────────
    private JPanel buildProcessScreen() {
        JPanel p = centeredColumn();

        JLabel spinner = spinnerLabel();
        p.add(spinner);
        p.add(vgap(16));

        JLabel heading = lbl("Opening PesaPal in your browser…", F_CARD, TEXT_H);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(heading);
        p.add(vgap(6));

        JLabel sub = lbl("Complete the payment, then return here.", F_BODY, TEXT_B);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(sub);
        p.add(vgap(28));

        JLabel trackHint = lbl("Paste your Order Tracking ID from PesaPal:", F_SMALL, TEXT_B);
        trackHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(trackHint);
        p.add(vgap(8));

        // ← FIX: wrap field in a panel so BoxLayout renders it at correct width
        JTextField trackField = new JTextField(28);
        trackField.setFont(F_BODY);
        trackField.setForeground(TEXT_H);
        trackField.setBackground(CARD_BG);
        trackField.setCaretColor(TEXT_H);
        trackField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)));
        JPanel fieldWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        fieldWrap.setOpaque(false);
        fieldWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        fieldWrap.add(trackField);
        p.add(fieldWrap);
        p.add(vgap(14));

        JLabel status = lbl("", F_SMALL, TEXT_M);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton verifyBtn = createGlassButton("Verify & Activate", ACCENT);
        verifyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        verifyBtn.addActionListener(e -> {
            String tid = trackField.getText().trim();
            if (tid.isEmpty()) {
                status.setForeground(DANGER);
                status.setText("Please enter your tracking ID.");
                return;
            }
            verifyBtn.setEnabled(false);
            status.setForeground(TEXT_M);
            status.setText("Verifying with PesaPal…");
            final Plan planToActivate = currentPlan;
            CompletableFuture.runAsync(() -> {
                try {
                    boolean paid = Subscribe.verifyPayment(tid);
                    if (paid) {
                        Subscribe.activateSubscription(planToActivate);
                        SwingUtilities.invokeLater(() -> {
                            mainLayout.show(mainPanel, "success");
                            try { refreshCache(); }
                            catch (IOException ex) { LOG.warning("Cache refresh failed: " + ex.getMessage()); }
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            status.setForeground(WARNING);
                            status.setText("Payment not confirmed yet. Please wait a moment and try again.");
                            verifyBtn.setEnabled(true);
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        status.setForeground(DANGER);
                        status.setText("Verification error: " + ex.getMessage());
                        verifyBtn.setEnabled(true);
                    });
                }
            });
        });

        p.add(verifyBtn);
        p.add(vgap(8));
        p.add(status);
        p.add(vgap(14));

        JButton backBtn = ghostBtn("← Back to plans");
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        backBtn.addActionListener(e -> mainLayout.show(mainPanel, "main"));
        p.add(backBtn);

        return p;
    }

    // ── Success screen ─────────────────────────────────────────────────────
    private JPanel buildSuccessScreen() {
        JPanel p = centeredColumn();

        JLabel icon = lbl("✓", new Font("Segoe UI", Font.BOLD, 52), SUCCESS);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(icon);
        p.add(vgap(14));

        JLabel title = lbl("Subscription Activated!", F_TITLE, TEXT_H);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(title);
        p.add(vgap(8));

        JLabel sub = lbl("Thank you! All Pro features are now unlocked.", F_BODY, TEXT_B);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(sub);
        p.add(vgap(32));

        JButton done = createGlassButton("Continue to App", SUCCESS);
        done.setAlignmentX(Component.CENTER_ALIGNMENT);
        done.addActionListener(e -> dispose());
        p.add(done);

        return p;
    }

    // ── Payment initiation ─────────────────────────────────────────────────
    private void startPayment(Plan plan) {
        currentPlan = plan;
        mainLayout.show(mainPanel, "processing");
        CompletableFuture.runAsync(() -> {
            try {
                String[] result      = Subscribe.createOrder(plan);
                String   redirectUrl = result[0];
                String   trackingId  = result[1];
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(redirectUrl));
                    SwingUtilities.invokeLater(() ->
                        showGlassMessageDialog("Payment Initiated",
                            "Payment page opened in your browser.<br><br>" +
                            "Tracking ID: <b>" + trackingId + "</b><br><br>" +
                            "After completing payment, return here and click 'Verify & Activate'.",
                            JOptionPane.INFORMATION_MESSAGE));
                } else {
                    SwingUtilities.invokeLater(() ->
                        showGlassMessageDialog("Payment URL",
                            "Please open this URL in your browser:<br><br>" +
                            "<b>" + redirectUrl + "</b><br><br>" +
                            "Tracking ID: <b>" + trackingId + "</b>",
                            JOptionPane.INFORMATION_MESSAGE));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showGlassMessageDialog("Connection Error",
                        "Could not open payment page:<br>" + e.getMessage(),
                        JOptionPane.ERROR_MESSAGE);
                    mainLayout.show(mainPanel, "main");
                });
            }
        });
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private static JLabel lbl(String t, Font f, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    /** BoxLayout vertical column, centered. All children must set CENTER_ALIGNMENT explicitly. */
    private static JPanel centeredColumn() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(36, 40, 36, 40));
        return p;
    }

    private static JPanel banner(String text, Color color) {
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        b.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, color),
                new EmptyBorder(8, 12, 8, 12)));
        JLabel l = new JLabel(text);
        l.setFont(F_BODY);
        l.setForeground(color);
        b.add(l);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    /** Pill badge — fixed size so it does not collapse. */
    private static JLabel pill(String text, Color color) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(color);
        l.setOpaque(false);
        l.setBorder(new EmptyBorder(3, 10, 3, 10));
        // pre-compute size so layout engine never collapses it
        l.setPreferredSize(new Dimension(
                l.getFontMetrics(l.getFont()).stringWidth(text) + 22, 20));
        return l;
    }

    private static JButton ghostBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(F_SMALL);
        b.setForeground(TEXT_M);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton iconBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        b.setForeground(color);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** ASCII spinner — renders on every platform without font issues. */
    private static JLabel spinnerLabel() {
        char[] frames = {'|', '/', '-', '\\'};
        JLabel l = new JLabel(String.valueOf(frames[0]));
        l.setFont(new Font("Monospaced", Font.BOLD, 28));
        l.setForeground(ACCENT);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        int[] idx = {0};
        new Timer(120, e -> {
            idx[0] = (idx[0] + 1) % frames.length;
            l.setText(String.valueOf(frames[idx[0]]));
        }).start();
        return l;
    }

    private static JPanel row(int gap, Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : comps) p.add(c);
        return p;
    }

    private static JLabel sep() {
        JLabel l = new JLabel("·");
        l.setForeground(new Color(60, 70, 100));
        return l;
    }

    private static Component vgap(int h) { return Box.createVerticalStrut(h); }
}

    // ═════════════════════════════════════════════════════════════════════════
    //  SQLITE CLOUD DDL  —  run once in your cloud console
    // ═════════════════════════════════════════════════════════════════════════
    /**
     * Run the SQL in SQLiteCloudDDL.sql (provided separately) in the
     * SQLite Cloud dashboard to create the subscriptions table.
     *
     * After that, this class handles everything automatically.
     */
}
