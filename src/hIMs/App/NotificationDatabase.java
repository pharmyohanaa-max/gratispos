package hIMs.App;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotificationDatabase {
    private static final Logger LOGGER = Logger.getLogger(NotificationDatabase.class.getName());
    private static final String DB_URL = "jdbc:sqlite:notifications.db";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void initializeDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notebooks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    user TEXT NOT NULL,
                    created_date TEXT NOT NULL,
                    modified_date TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    alert_type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    user TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    details TEXT,
                    created_date TEXT NOT NULL,
                    resolved BOOLEAN DEFAULT 0
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS return_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    sold_date TEXT NOT NULL,
                    return_date TEXT NOT NULL,
                    requested_by TEXT NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    sale_item_id INTEGER DEFAULT -1,
                    unit_price DECIMAL(10,2) DEFAULT 0,
                    created_date TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS business_info (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    name TEXT NOT NULL,
                    address TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    email TEXT NOT NULL,
                    website TEXT NOT NULL
                )
            """);

            // Migrations
            runMigration(stmt, "ALTER TABLE return_notifications ADD COLUMN sale_item_id INTEGER DEFAULT -1");
            runMigration(stmt, "ALTER TABLE return_notifications ADD COLUMN unit_price DECIMAL(10,2) DEFAULT 0");
            runMigration(stmt, "ALTER TABLE return_notifications ADD COLUMN created_date TEXT");

            LOGGER.info("NotificationDatabase initialized successfully");
        }
    }

    private static void runMigration(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) {
                LOGGER.fine("Migration note: " + e.getMessage());
            }
        }
    }

    // ================== APPROVE RETURN — SINGLE CORRECT IMPLEMENTATION ==================

    /**
     * Approves a return notification and performs ALL inventory/sales corrections
     * atomically in the main inventory database.
     *
     * Actions performed:
     *  1. Mark notification as APPROVED in notifications DB
     *  2. Reduce sale_items quantity by refunded amount (or delete if fully returned)
     *  3. Reduce the parent sales.sales_total by (unit_price × qty_refunded)
     *  4. Restore product stock_quantity
     *  5. Log REFUNDED action in item_flows
     *  6. Log in inventory_log
     *  7. Regenerate daily_sales_reports row for the affected date
     *  8. Cloud sync if online
     */
    public static boolean approveReturnNotification(int notificationId) throws SQLException {
        // ── Step 1: Read return details from notifications DB ──────────────────
        int productId   = -1;
        int quantity    = 0;
        int saleItemId  = -1;
        BigDecimal unitPrice = BigDecimal.ZERO;
        String soldDateStr   = null;

        try (Connection notifConn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = notifConn.prepareStatement(
                 "SELECT product_id, product_name, quantity, sale_item_id, unit_price, sold_date " +
                 "FROM return_notifications WHERE id = ?")) {
            ps.setInt(1, notificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warning("approveReturnNotification: notification " + notificationId + " not found");
                    return false;
                }
                productId   = rs.getInt("product_id");
                quantity    = rs.getInt("quantity");
                saleItemId  = rs.getInt("sale_item_id");
                unitPrice   = rs.getBigDecimal("unit_price");
                soldDateStr = rs.getString("sold_date");
                if (unitPrice == null) unitPrice = BigDecimal.ZERO;
            }
        }

        // If saleItemId wasn't stored, attempt lookup by date
        if (saleItemId <= 0 && soldDateStr != null) {
            saleItemId = lookupSaleItemId(productId, soldDateStr);
        }

        if (saleItemId <= 0) {
            LOGGER.warning("approveReturnNotification: could not find sale_item_id for product " + productId);
            // Still mark notification APPROVED and restore stock even without sale_item linkage
            markNotificationApproved(notificationId);
            restoreStockOnly(productId, quantity);
            return true;
        }

        BigDecimal refundAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // ── Step 2: All inventory changes in one transaction ───────────────────
       Connection invConn = null;
try {
    invConn = DriverManager.getConnection("jdbc:sqlite:" + Database.getDatabaseFile());
    invConn.setAutoCommit(false);
    try (Statement pragmaStmt = invConn.createStatement()) {
        pragmaStmt.execute("PRAGMA journal_mode=WAL;");
        pragmaStmt.execute("PRAGMA foreign_keys=ON;");
        pragmaStmt.execute("PRAGMA busy_timeout=10000;");
    }

    // 2a. Resolve sale_id from sale_item
    int saleId = -1;
    try (PreparedStatement ps = invConn.prepareStatement(
            "SELECT sale_id FROM sale_items WHERE id = ?")) {
        ps.setInt(1, saleItemId);
        ResultSet rs = ps.executeQuery();
        saleId = rs.next() ? rs.getInt(1) : -1;
    }
    if (saleId <= 0) {
        invConn.rollback();
        markNotificationApproved(notificationId);
        restoreStockOnly(productId, quantity);
        return true;
    }

    // 2b. Get current sale_item quantity
    int currentItemQty = 0;
    try (PreparedStatement ps = invConn.prepareStatement(
            "SELECT quantity FROM sale_items WHERE id = ?")) {
        ps.setInt(1, saleItemId);
        ResultSet rs = ps.executeQuery();
        currentItemQty = rs.next() ? rs.getInt("quantity") : 0;
    }
    int newItemQty = currentItemQty - quantity;

    // 2c. Update or delete sale_item
    if (newItemQty <= 0) {
        try (PreparedStatement ps = invConn.prepareStatement(
                "DELETE FROM sale_items WHERE id = ?")) {
            ps.setInt(1, saleItemId);
            ps.executeUpdate();
        }
    } else {
        try (PreparedStatement ps = invConn.prepareStatement(
                "UPDATE sale_items SET quantity = ?, total_price = unit_price * ? WHERE id = ?")) {
            ps.setInt(1, newItemQty);
            ps.setInt(2, newItemQty);
            ps.setInt(3, saleItemId);
            ps.executeUpdate();
        }
    }

    // 2d. Recompute and update sales total
    BigDecimal newSalesTotal = BigDecimal.ZERO;
    try (PreparedStatement ps = invConn.prepareStatement(
            "SELECT COALESCE(SUM(total_price), 0) FROM sale_items WHERE sale_id = ?")) {
        ps.setInt(1, saleId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) newSalesTotal = rs.getBigDecimal(1);
    }
    try (PreparedStatement ps = invConn.prepareStatement(
            "UPDATE sales SET sales_total = ? WHERE id = ?")) {
        ps.setBigDecimal(1, newSalesTotal);
        ps.setInt(2, saleId);
        ps.executeUpdate();
    }

    // 2e. Restore product stock
    try (PreparedStatement ps = invConn.prepareStatement(
            "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?")) {
        ps.setInt(1, quantity);
        ps.setInt(2, productId);
        ps.executeUpdate();
    }

    // 2f. Log in item_flows
    try (PreparedStatement ps = invConn.prepareStatement(
            "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
            "VALUES (?, 'REFUNDED', ?, date('now','localtime'), date('now','localtime'), 'SYSTEM', ?, ?)")) {
        ps.setInt(1, productId);
        ps.setInt(2, quantity);
        ps.setString(3, Database.getCurrentClientId());
        ps.setString(4, Database.getCurrentUserEmail());
        ps.executeUpdate();
    }

    // 2g. Log in inventory_log
    try (PreparedStatement ps = invConn.prepareStatement(
            "INSERT INTO inventory_log (product_id, action, quantity, previous_quantity, date, client_id, user_email) " +
            "VALUES (?, 'REFUNDED', ?, (SELECT stock_quantity FROM products WHERE id = ?), date('now','localtime'), ?, ?)")) {
        ps.setInt(1, productId);
        ps.setInt(2, quantity);
        ps.setInt(3, productId);
        ps.setString(4, Database.getCurrentClientId());
        ps.setString(5, Database.getCurrentUserEmail());
        ps.executeUpdate();
    }

    // 2h. Regenerate daily sales report
    if (soldDateStr != null) {
        regenerateDailySalesReport(invConn, soldDateStr);
    }

    invConn.commit();
    LOGGER.info("Return approved: notification=" + notificationId +
                " product=" + productId + " qty=" + quantity +
                " refund=" + refundAmount);

} catch (SQLException e) {
    if (invConn != null) {
        try { invConn.rollback(); } catch (SQLException ex) { /* ignore */ }
    }
    LOGGER.log(Level.SEVERE, "Failed to approve return (rolled back): " + e.getMessage(), e);
    throw e;
} finally {
    if (invConn != null) {
        try { invConn.setAutoCommit(true); invConn.close(); } catch (SQLException ex) { /* ignore */ }
    }
}

        // ── Step 3: Mark notification APPROVED (separate DB, after inventory success) ─
        markNotificationApproved(notificationId);

        // ── Step 4: Cloud sync if online ──────────────────────────────────────
        if (Database.isOnline()) {
            try {
                Database.syncToCloud();
                LOGGER.info("Cloud sync triggered after refund approval");
            } catch (SQLException e) {
                LOGGER.warning("Cloud sync after refund failed (non-fatal): " + e.getMessage());
            }
        }

        return true;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static void markNotificationApproved(int notificationId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE return_notifications SET status = 'APPROVED' WHERE id = ?")) {
            ps.setInt(1, notificationId);
            ps.executeUpdate();
        }
    }

    private static void restoreStockOnly(int productId, int quantity) throws SQLException {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ? AND client_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.setString(3, Database.getCurrentClientId());
            ps.executeUpdate();
            LOGGER.info("restoreStockOnly: product " + productId + " +" + quantity);
        }
    }

    private static int lookupSaleItemId(int productId, String soldDate) {
        String sql = "SELECT si.id FROM sale_items si JOIN sales s ON si.sale_id = s.id WHERE si.product_id = ? AND DATE(s.sale_date) = ? AND s.client_id = ? ORDER BY s.id DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, soldDate);
            ps.setString(3, Database.getCurrentClientId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {}
        return -1;
    }

    private static int getSaleId(Connection conn, int saleItemId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT sale_id FROM sale_items WHERE id = ?");
        ps.setInt(1, saleItemId);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : -1;
    }

    private static int getSaleItemQuantity(Connection conn, int saleItemId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM sale_items WHERE id = ?");
        ps.setInt(1, saleItemId);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt("quantity") : 0;
    }

    /**
     * Recomputes the true total for a sale by summing its remaining sale_items.
     * This is more reliable than simply subtracting the refund amount, because
     * it handles edge cases (discounts, previous partial returns, rounding).
     */
    private static BigDecimal recomputeSalesTotal(Connection conn, int saleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_price), 0) FROM sale_items WHERE sale_id = ?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    /**
     * Regenerates the daily_sales_reports row for the given date by reading
     * actual current data from sales / sale_items / daily_expenses.
     * Called after any refund so dashboards reading from daily_sales_reports
     * stay in sync with the underlying transaction tables.
     */
    private static void regenerateDailySalesReport(Connection conn, String date) {
        try {
            String clientId   = Database.getCurrentClientId();
            String userEmail  = Database.getCurrentUserEmail();

            // Total gross sales (sum of sales_total for the day — already corrected above)
            BigDecimal grossSales = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(sales_total), 0) FROM sales " +
                    "WHERE DATE(sale_date) = ? AND client_id = ?")) {
                ps.setString(1, date);
                ps.setString(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) grossSales = rs.getBigDecimal(1);
                }
            }

            // Total discounts
            BigDecimal discounts = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(discount), 0) FROM sales " +
                    "WHERE DATE(sale_date) = ? AND client_id = ?")) {
                ps.setString(1, date);
                ps.setString(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) discounts = rs.getBigDecimal(1);
                }
            }

            // Cost of goods sold
            BigDecimal cogs = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(si.quantity * p.cost_price), 0) " +
                    "FROM sale_items si " +
                    "JOIN sales s ON si.sale_id = s.id " +
                    "JOIN products p ON si.product_id = p.id " +
                    "WHERE DATE(s.sale_date) = ? AND s.client_id = ?")) {
                ps.setString(1, date);
                ps.setString(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) cogs = rs.getBigDecimal(1);
                }
            }

            // Daily expenses
            BigDecimal expenses = Database.getDailyExpense(date);

            BigDecimal netSale     = grossSales.subtract(discounts);
            BigDecimal grossProfit = netSale.subtract(cogs);
            BigDecimal netProfit   = grossProfit.subtract(expenses);

            // Upsert into daily_sales_reports
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO daily_sales_reports " +
                    "(report_date, total_gross_sale, discounts, cost_of_goods, daily_expenses, " +
                    " net_sale, gross_profit, net_profit, client_id, user_email) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, date);
                ps.setBigDecimal(2, grossSales);
                ps.setBigDecimal(3, discounts);
                ps.setBigDecimal(4, cogs);
                ps.setBigDecimal(5, expenses);
                ps.setBigDecimal(6, netSale);
                ps.setBigDecimal(7, grossProfit);
                ps.setBigDecimal(8, netProfit);
                ps.setString(9, clientId);
                ps.setString(10, userEmail);
                ps.executeUpdate();
                LOGGER.info("  daily_sales_reports regenerated for " + date +
                            " | gross=" + grossSales + " cogs=" + cogs + " profit=" + grossProfit);
            }
        } catch (SQLException e) {
            // Non-fatal — log and continue; the transaction tables are correct
            LOGGER.warning("regenerateDailySalesReport failed for " + date + ": " + e.getMessage());
        }
    }

    // ================== REJECT ==================

    public static boolean rejectReturnNotification(int notificationId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE return_notifications SET status = 'REJECTED' WHERE id = ?")) {
            ps.setInt(1, notificationId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                LOGGER.info("Return notification rejected: ID " + notificationId);
                return true;
            }
        }
        return false;
    }

    // ================== NOTEBOOK / RECEIPT METHODS ==================

    public static void saveNotebook(String title, String content, String user) throws SQLException {
        String sql = "INSERT INTO notebooks (title, content, user, created_date, modified_date) VALUES (?, ?, ?, ?, ?)";
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, user);
            ps.setString(4, dateStr);
            ps.setString(5, dateStr);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Failed to insert notebook");
            LOGGER.info("Notebook saved: " + title);
        }
    }

    public static Notebook getNotebookByTitle(String title, String user) throws SQLException {
        String sql = "SELECT * FROM notebooks WHERE title = ? AND user = ? ORDER BY created_date DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return notebookFromRs(rs);
            }
        }
        return null;
    }

    public static List<Notebook> getAllReceipts() throws SQLException {
        List<Notebook> list = new ArrayList<>();
        String sql = "SELECT id, title, content, user, created_date, modified_date FROM notebooks ORDER BY created_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(notebookFromRs(rs));
            LOGGER.info("getAllReceipts(): loaded " + list.size() + " receipts");
        }
        return list;
    }

    public static List<Notebook> getReceiptsByUser(String user) throws SQLException {
        List<Notebook> list = new ArrayList<>();
        String sql = "SELECT * FROM notebooks WHERE user = ? ORDER BY created_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(notebookFromRs(rs));
            }
        }
        return list;
    }

    public static List<Notebook> getReceiptsByDate(LocalDate date) throws SQLException {
        List<Notebook> list = new ArrayList<>();
        String dateStr = date.format(DATE_FORMATTER);
        String sql = "SELECT id, title, content, user, created_date, modified_date FROM notebooks WHERE created_date = ? ORDER BY created_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dateStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(notebookFromRs(rs));
            }
        }
        return list;
    }

    private static Notebook notebookFromRs(ResultSet rs) throws SQLException {
        return new Notebook(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("user"),
            LocalDate.parse(rs.getString("created_date"), DATE_FORMATTER),
            LocalDate.parse(rs.getString("modified_date"), DATE_FORMATTER)
        );
    }

    // ================== ALERTS ==================

    public static void saveAlert(String alertType, String message, String user, String severity, String details) throws SQLException {
        String sql = "INSERT INTO alerts (alert_type, message, user, severity, details, created_date) VALUES (?, ?, ?, ?, ?, ?)";
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertType);
            ps.setString(2, message);
            ps.setString(3, user);
            ps.setString(4, severity);
            ps.setString(5, details);
            ps.setString(6, dateStr);
            ps.executeUpdate();
        }
    }

    // ================== RETURN NOTIFICATIONS ==================

    public static void saveReturnNotification(int productId, String productName, int quantity,
                                              String reason, LocalDate soldDate, LocalDate returnDate,
                                              String requestedBy) throws SQLException {
        saveReturnNotification(productId, productName, quantity, reason, soldDate, returnDate, requestedBy, -1, BigDecimal.ZERO);
    }

    public static void saveReturnNotification(int productId, String productName, int quantity,
                                              String reason, LocalDate soldDate, LocalDate returnDate,
                                              String requestedBy, int saleItemId, BigDecimal unitPrice) throws SQLException {
        String sql = """
            INSERT INTO return_notifications
            (product_id, product_name, quantity, reason, sold_date, return_date, requested_by, sale_item_id, unit_price, created_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, productName);
            ps.setInt(3, quantity);
            ps.setString(4, reason);
            ps.setString(5, soldDate.format(DATE_FORMATTER));
            ps.setString(6, returnDate.format(DATE_FORMATTER));
            ps.setString(7, requestedBy);
            ps.setInt(8, saleItemId);
            ps.setBigDecimal(9, unitPrice != null ? unitPrice : BigDecimal.ZERO);
            ps.setString(10, LocalDate.now().format(DATE_FORMATTER));
            ps.executeUpdate();
            LOGGER.info("Return notification saved for product: " + productName);
        }
    }

    public static List<Notification> getAllReturnNotifications() throws SQLException {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM return_notifications ORDER BY created_date DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                BigDecimal unitPrice = rs.getBigDecimal("unit_price");
                list.add(new Notification(
                    rs.getInt("id"),
                    rs.getInt("product_id"),
                    rs.getString("product_name"),
                    rs.getInt("quantity"),
                    rs.getString("reason"),
                    LocalDate.parse(rs.getString("sold_date"), DATE_FORMATTER),
                    LocalDate.parse(rs.getString("return_date"), DATE_FORMATTER),
                    rs.getString("requested_by"),
                    rs.getString("status"),
                    rs.getInt("sale_item_id"),
                    unitPrice != null ? unitPrice : BigDecimal.ZERO
                ));
            }
        }
        return list;
    }

    public static void clearOldNotifications() throws SQLException {
        String cutoff = LocalDate.now().minusDays(30).format(DATE_FORMATTER);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM return_notifications WHERE created_date < ? AND status != 'PENDING'")) {
            ps.setString(1, cutoff);
            int deleted = ps.executeUpdate();
            LOGGER.info("Cleared " + deleted + " old notifications");
        }
    }

    // ================== SOLD ITEMS QUERY ==================

    public static List<Database.SaleItem> getSoldItemsByDateAndName(String date, String productNameFilter) throws SQLException {
        List<Database.SaleItem> items = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT si.product_id, s.sale_date, si.quantity, si.unit_price AS price " +
            "FROM sale_items si JOIN sales s ON si.sale_id = s.id ");

        boolean hasFilter = productNameFilter != null && !productNameFilter.trim().isEmpty();
        if (hasFilter) {
            sql.append("JOIN products p ON si.product_id = p.id ");
            sql.append("WHERE DATE(s.sale_date) = ? AND LOWER(p.name) LIKE LOWER(?)");
        } else {
            sql.append("WHERE DATE(s.sale_date) = ?");
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, date);
            if (hasFilter) ps.setString(2, "%" + productNameFilter + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Database.SaleItem item = new Database.SaleItem();
                    item.setProductId(rs.getInt("product_id"));
                    item.setDate(Database.fromDatabaseDate(rs.getString("sale_date")));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setPrice(rs.getBigDecimal("price"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    public static void restoreCartItemStock(int productId, int quantity) throws SQLException {
        try (PreparedStatement ps = Database.getConnection().prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                Database.logInventoryAction(productId, "CART_ITEM_RESTORED", quantity, 0, "SYSTEM");
            } else {
                throw new SQLException("Failed to restore stock for product ID: " + productId);
            }
        }
    }

    public static String toDatabaseDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    // ================== INNER CLASSES ==================

    public static class Notebook {
        private final int id;
        private final String title, content, user;
        private final LocalDate createdDate, modifiedDate;

        public Notebook(int id, String title, String content, String user,
                        LocalDate createdDate, LocalDate modifiedDate) {
            this.id = id; this.title = title; this.content = content;
            this.user = user; this.createdDate = createdDate; this.modifiedDate = modifiedDate;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getUser() { return user; }
        public LocalDate getCreatedDate() { return createdDate; }
        public LocalDate getModifiedDate() { return modifiedDate; }
    }

    public static class Notification {
        private final int id, productId, quantity, saleItemId;
        private final String productName, reason, requestedBy, status;
        private final LocalDate soldDate, returnDate;
        private final BigDecimal unitPrice;

        public Notification(int id, int productId, String productName, int quantity,
                            String reason, LocalDate soldDate, LocalDate returnDate,
                            String requestedBy, String status, int saleItemId, BigDecimal unitPrice) {
            this.id = id; this.productId = productId; this.productName = productName;
            this.quantity = quantity; this.reason = reason; this.soldDate = soldDate;
            this.returnDate = returnDate; this.requestedBy = requestedBy; this.status = status;
            this.saleItemId = saleItemId;
            this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        }

        public int getId() { return id; }
        public int getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public String getReason() { return reason; }
        public LocalDate getSoldDate() { return soldDate; }
        public LocalDate getReturnDate() { return returnDate; }
        public String getRequestedBy() { return requestedBy; }
        public String getStatus() { return status; }
        public int getSaleItemId() { return saleItemId; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }

    public static class BusinessInfo {
        private final String name, address, phone, email, website;

        public BusinessInfo(String name, String address, String phone, String email, String website) {
            this.name = name; this.address = address; this.phone = phone;
            this.email = email; this.website = website;
        }

        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getWebsite() { return website; }

        public void saveToDatabase(Connection conn) throws SQLException {
            String sql = "INSERT OR REPLACE INTO business_info (id, name, address, phone, email, website) VALUES (1, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name); ps.setString(2, address); ps.setString(3, phone);
                ps.setString(4, email); ps.setString(5, website);
                ps.executeUpdate();
            }
        }

        public static BusinessInfo loadFromDatabase(Connection conn) throws SQLException {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM business_info WHERE id = 1")) {
                if (rs.next()) {
                    return new BusinessInfo(
                        rs.getString("name"), rs.getString("address"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("website")
                    );
                }
            }
            return new BusinessInfo("Your Company Name", "Street Address, City, State, ZIP",
                                    "Business Phone", "Business Email", "Business Website");
        }
    }
}
