package hIMs.App;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SyncEngine — Complete bidirectional sync for multi-computer POS system.
 * 
 * ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    SQLite Cloud (Central)                       │
 * │  All data tagged with client_id → Organization-wide sharing    │
 * └─────────────────────────────────────────────────────────────────┘
 *                           ↕ sync
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Computer A (John)          │  Computer B (Mark)               │
 * │  Local SQLite               │  Local SQLite                    │
 * │  client_id = "abc123"       │  client_id = "abc123"            │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * KEY PRINCIPLES:
 * 1. client_id ties all users in same organization together
 * 2. Stock is NEVER stored directly — computed from item_flows
 * 3. No DELETE during sync — only INSERT OR REPLACE (merge)
 * 4. Categories mapped by NAME, not ID (handles auto-increment differences)
 * 5. Incremental sync using updated_at timestamps
 */
public class SyncEngine {

    private static final Logger LOGGER = Logger.getLogger(SyncEngine.class.getName());
    private static final String LAST_SYNC_KEY = "last_sync_timestamp";
    
    // Tables in dependency order (parents before children)
    private static final String[] SYNC_TABLES = {
        "categories",
        "vendors", 
        "products",
        "item_flows",
        "sales",
        "sale_items",
        "daily_expenses",
        "daily_sales_reports"
    };
    
    // Tables that should NOT have stock_quantity pushed (cloud computes from flows)
    private static final Set<String> TABLES_EXCLUDING_STOCK = new HashSet<>(Arrays.asList("products"));
    
    // ════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Full bidirectional sync with incremental updates.
     * Call on login, then every 30-60 seconds from background timer.
     */
    public static boolean fullSync() {
        if (!isReady()) {
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║              SyncEngine.fullSync() START                   ║");
        LOGGER.info("║  Client: " + padRight(getClientId(), 38) + "║");
        LOGGER.info("║  User:   " + padRight(getEmail(), 38) + "║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        
        try {
            // Step 1: Pull changes from cloud (since last sync)
            int rowsPulled = pullIncremental();
            
            // Step 2: Recompute stock from item_flows (source of truth)
            int recomputedCount = recomputeAllStockQuantities();
            
            // Step 3: Push local changes to cloud (since last sync)
            int rowsPushed = pushIncremental();
            
            // Step 4: Update last sync timestamp
            updateLastSyncTimestamp();
            
            // Step 5: Create backup (keep last 5)
            createLocalBackup();
            cleanupOldBackups(5);
            
            long duration = System.currentTimeMillis() - startTime;
            
            LOGGER.info(String.format(
                "✅ Sync complete in %d ms | Pulled: %d | Pushed: %d | Stock recomputed: %d",
                duration, rowsPulled, rowsPushed, recomputedCount));
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Sync failed", e);
            return false;
        }
    }
    
    /**
     * Quick push after local changes (sale, product add, receipt, etc.)
     * Does NOT pull — only uploads local changes.
     */
    public static boolean quickPush() {
        if (!isReady()) return false;
        try {
            int rowsPushed = pushIncremental();
            LOGGER.fine("quickPush: " + rowsPushed + " rows pushed");
            return rowsPushed >= 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "quickPush failed", e);
            return false;
        }
    }
    
    /**
     * Pull only — call when notified of remote changes.
     */
    public static boolean pullOnly() {
        if (!isReady()) return false;
        try {
            int rowsPulled = pullIncremental();
            recomputeAllStockQuantities();
            LOGGER.fine("pullOnly: " + rowsPulled + " rows pulled");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "pullOnly failed", e);
            return false;
        }
    }
    
    /**
     * Force full pull (ignores last sync timestamp).
     * Use after database restore or manual recovery.
     */
    public static boolean forceFullPull() {
        if (!isReady()) return false;
        try {
            clearLastSyncTimestamp();
            int rowsPulled = pullIncremental();
            recomputeAllStockQuantities();
            LOGGER.info("forceFullPull: " + rowsPulled + " rows pulled");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "forceFullPull failed", e);
            return false;
        }
    }
    
    /**
     * Force full push (ignores last sync timestamp).
     * Use to rebuild cloud data from local.
     */
    public static boolean forceFullPush() {
        if (!isReady()) return false;
        try {
            clearLastSyncTimestamp();
            int rowsPushed = pushIncremental();
            LOGGER.info("forceFullPush: " + rowsPushed + " rows pushed");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "forceFullPush failed", e);
            return false;
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  INCREMENTAL PULL (Cloud → Local)
    // ════════════════════════════════════════════════════════════════════════
    
    private static int pullIncremental() throws Exception {
        String clientId = getClientId();
        String email = getEmail();
        String lastSync = getLastSyncTimestamp();
        
        LOGGER.info("📥 PULL incremental | client_id=" + clientId + " | since=" + (lastSync != null ? lastSync : "BEGINNING"));
        
        int totalPulled = 0;
        
        for (String table : SYNC_TABLES) {
            int pulled = pullTableIncremental(table, clientId, email, lastSync);
            totalPulled += pulled;
        }
        
        // Also pull users (special handling — exclude passwords)
        int usersPulled = pullUsersIncremental(clientId, email, lastSync);
        totalPulled += usersPulled;
        
        LOGGER.info("📥 PULL complete: " + totalPulled + " rows");
        return totalPulled;
    }
    
    /**
     * Pull one table incrementally (only rows changed since lastSync).
     */
    private static int pullTableIncremental(String tableName, String clientId, String email, String lastSync) {
        try {
            StringBuilder query = new StringBuilder("SELECT * FROM " + tableName + " WHERE client_id = ?");
            
            // Add updated_at filter for incremental sync
            if (lastSync != null && !lastSync.isEmpty()) {
                query.append(" AND (updated_at IS NULL OR updated_at > ?)");
            }
            
            List<JsonObject> cloudRows;
            if (lastSync != null && !lastSync.isEmpty()) {
                cloudRows = SQLiteCloudClient.execute(query.toString(), clientId, lastSync);
            } else {
                cloudRows = SQLiteCloudClient.execute(query.toString(), clientId);
            }
            
            // Also pull legacy rows (user_email without client_id)
            String fallbackQuery = "SELECT * FROM " + tableName + 
                " WHERE user_email = ? AND (client_id IS NULL OR client_id = '' OR client_id = 'null')";
            List<JsonObject> legacyRows = SQLiteCloudClient.execute(fallbackQuery, email);
            for (JsonObject row : legacyRows) {
                row.addProperty("client_id", clientId);
            }
            cloudRows.addAll(legacyRows);
            
            if (cloudRows.isEmpty()) return 0;
            
            Connection local = Database.getConnection();
            int merged = 0;
            
            if ("products".equals(tableName)) {
                merged = pullProductsWithMapping(cloudRows, local, clientId, email);
            } else if ("categories".equals(tableName)) {
                merged = pullCategoriesWithDedupe(cloudRows, local, clientId, email);
            } else {
                for (JsonObject row : cloudRows) {
                    try {
                        ensureRowHasIds(row, clientId, email);
                        String sql = buildInsertOrReplace(tableName, row);
                        try (PreparedStatement ps = local.prepareStatement(sql)) {
                            ps.executeUpdate();
                            merged++;
                        }
                    } catch (Exception e) {
                        LOGGER.fine("  " + tableName + " row skip: " + e.getMessage());
                    }
                }
            }
            
            if (merged > 0) {
                LOGGER.info("  ✅ " + tableName + ": " + merged + " rows merged");
            }
            return merged;
            
        } catch (Exception e) {
            LOGGER.warning("pullTableIncremental failed for " + tableName + ": " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Pull users table — EXCLUDES password column for security.
     */
    private static int pullUsersIncremental(String clientId, String email, String lastSync) {
        try {
            StringBuilder query = new StringBuilder(
                "SELECT id, user_email, client_id, username, privileges, business_name, " +
                "created_date, email_verified, account_status, locked, lock_reason, " +
                "last_login, updated_at FROM users WHERE client_id = ?");
            
            if (lastSync != null && !lastSync.isEmpty()) {
                query.append(" AND (updated_at IS NULL OR updated_at > ?)");
            }
            
            List<JsonObject> cloudRows;
            if (lastSync != null && !lastSync.isEmpty()) {
                cloudRows = SQLiteCloudClient.execute(query.toString(), clientId, lastSync);
            } else {
                cloudRows = SQLiteCloudClient.execute(query.toString(), clientId);
            }
            
            if (cloudRows.isEmpty()) return 0;
            
            Connection local = Database.getConnection();
            int merged = 0;
            
            for (JsonObject row : cloudRows) {
                try {
                    ensureRowHasIds(row, clientId, email);
                    // Don't sync password — keep local password unchanged
                    String sql = "INSERT OR REPLACE INTO users (id, user_email, client_id, username, " +
                        "privileges, business_name, created_date, email_verified, account_status, " +
                        "locked, lock_reason, last_login, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = local.prepareStatement(sql)) {
                        ps.setInt(1, getIntSafe(row, "id"));
                        ps.setString(2, getStringSafe(row, "user_email"));
                        ps.setString(3, getStringSafe(row, "client_id"));
                        ps.setString(4, getStringSafe(row, "username"));
                        ps.setString(5, getStringSafe(row, "privileges"));
                        ps.setString(6, getStringSafe(row, "business_name"));
                        ps.setString(7, getStringSafe(row, "created_date"));
                        ps.setInt(8, getIntSafe(row, "email_verified") != null ? getIntSafe(row, "email_verified") : 0);
                        ps.setString(9, getStringSafe(row, "account_status"));
                        ps.setInt(10, getIntSafe(row, "locked") != null ? getIntSafe(row, "locked") : 0);
                        ps.setString(11, getStringSafe(row, "lock_reason"));
                        ps.setString(12, getStringSafe(row, "last_login"));
                        ps.setString(13, getStringSafe(row, "updated_at"));
                        ps.executeUpdate();
                        merged++;
                    }
                } catch (Exception e) {
                    LOGGER.fine("  users row skip: " + e.getMessage());
                }
            }
            
            if (merged > 0) {
                LOGGER.info("  ✅ users: " + merged + " rows merged (passwords excluded)");
            }
            return merged;
            
        } catch (Exception e) {
            LOGGER.warning("pullUsersIncremental failed: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Pull products with category ID mapping (by name, not numeric ID).
     */
    private static int pullProductsWithMapping(List<JsonObject> cloudProducts,
            Connection local, String clientId, String email) {
        int count = 0;
        try {
            // Build cloud category ID → name map
            Map<Integer, String> cloudCatIdToName = new HashMap<>();
            List<JsonObject> cloudCats = SQLiteCloudClient.execute(
                "SELECT id, name FROM categories WHERE client_id = ?", clientId);
            for (JsonObject cat : cloudCats) {
                if (cat.has("id") && !cat.get("id").isJsonNull() &&
                    cat.has("name") && !cat.get("name").isJsonNull()) {
                    cloudCatIdToName.put(cat.get("id").getAsInt(), cat.get("name").getAsString());
                }
            }
            
            // Build local name → category ID map
            Map<String, Integer> localCatNameToId = new HashMap<>();
            try (PreparedStatement ps = local.prepareStatement(
                    "SELECT id, name FROM categories WHERE client_id = ? OR user_email = ?")) {
                ps.setString(1, clientId);
                ps.setString(2, email);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        localCatNameToId.put(rs.getString("name"), rs.getInt("id"));
                    }
                }
            }
            
            // Insert each product with remapped category_id
            for (JsonObject product : cloudProducts) {
                try {
                    // Remap category_id
                    if (product.has("category_id") && !product.get("category_id").isJsonNull()) {
                        try {
                            int cloudCatId = product.get("category_id").getAsInt();
                            String catName = cloudCatIdToName.get(cloudCatId);
                            if (catName != null && localCatNameToId.containsKey(catName)) {
                                product.addProperty("category_id", localCatNameToId.get(catName));
                            } else {
                                product.add("category_id", null);
                            }
                        } catch (NumberFormatException e) {
                            // category_id is text (name directly)
                            String catName = product.get("category_id").getAsString();
                            if (localCatNameToId.containsKey(catName)) {
                                product.addProperty("category_id", localCatNameToId.get(catName));
                            } else {
                                product.add("category_id", null);
                            }
                        }
                    }
                    
                    // Remove stock_quantity from cloud product (we'll recompute locally)
                    if (product.has("stock_quantity")) {
                        product.remove("stock_quantity");
                    }
                    
                    ensureRowHasIds(product, clientId, email);
                    String sql = buildInsertOrReplace("products", product);
                    try (PreparedStatement ps = local.prepareStatement(sql)) {
                        ps.executeUpdate();
                        count++;
                    }
                } catch (Exception e) {
                    LOGGER.fine("  products row skip: " + e.getMessage());
                }
            }
            
            if (count > 0) {
                LOGGER.info("  ✅ products: " + count + " rows merged (category mapping applied)");
            }
        } catch (Exception e) {
            LOGGER.warning("pullProductsWithMapping failed: " + e.getMessage());
        }
        return count;
    }
    
    /**
     * Pull categories with deduplication by name.
     */
    private static int pullCategoriesWithDedupe(List<JsonObject> cloudCategories,
            Connection local, String clientId, String email) {
        int count = 0;
        try {
            Set<String> existingNames = new HashSet<>();
            try (PreparedStatement ps = local.prepareStatement(
                    "SELECT name FROM categories WHERE client_id = ?")) {
                ps.setString(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        existingNames.add(rs.getString("name"));
                    }
                }
            }
            
            for (JsonObject cat : cloudCategories) {
                String name = getStringSafe(cat, "name");
                if (name == null || name.isEmpty()) continue;
                
                // Skip duplicates
                if (existingNames.contains(name)) {
                    continue;
                }
                
                ensureRowHasIds(cat, clientId, email);
                String sql = buildInsertOrReplace("categories", cat);
                try (PreparedStatement ps = local.prepareStatement(sql)) {
                    ps.executeUpdate();
                    count++;
                    existingNames.add(name);
                }
            }
            
            if (count > 0) {
                LOGGER.info("  ✅ categories: " + count + " new categories added");
            }
        } catch (Exception e) {
            LOGGER.warning("pullCategoriesWithDedupe failed: " + e.getMessage());
        }
        return count;
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  INCREMENTAL PUSH (Local → Cloud)
    // ════════════════════════════════════════════════════════════════════════
    
    private static int pushIncremental() throws Exception {
        String clientId = getClientId();
        String email = getEmail();
        String lastSync = getLastSyncTimestamp();
        
        LOGGER.info("📤 PUSH incremental | client_id=" + clientId + " | since=" + (lastSync != null ? lastSync : "BEGINNING"));
        
        int totalPushed = 0;
        
        for (String table : SYNC_TABLES) {
            int pushed = pushTableIncremental(table, clientId, email, lastSync);
            totalPushed += pushed;
        }
        
        LOGGER.info("📤 PUSH complete: " + totalPushed + " rows");
        return totalPushed;
    }
    
    /**
     * Push one table incrementally (only rows changed since lastSync).
     */
    private static int pushTableIncremental(String tableName, String clientId, String email, String lastSync) {
        try (Connection local = Database.getConnection()) {
            List<Map<String, Object>> rows = fetchLocalRowsChanged(local, tableName, clientId, email, lastSync);
            if (rows.isEmpty()) return 0;
            
            int pushed = 0;
            for (Map<String, Object> row : rows) {
                row.put("client_id", clientId);
                row.put("user_email", email);
                
                // For products table, remove stock_quantity before pushing
                if (TABLES_EXCLUDING_STOCK.contains(tableName) && row.containsKey("stock_quantity")) {
                    row.remove("stock_quantity");
                }
                
                try {
                    String sql = buildCloudInsertOrReplace(tableName, row);
                    SQLiteCloudClient.executeUpdateDirect(sql);
                    pushed++;
                } catch (Exception e) {
                    LOGGER.fine("  " + tableName + " row skip: " + e.getMessage());
                }
            }
            
            if (pushed > 0) {
                LOGGER.info("  ✅ " + tableName + ": " + pushed + " rows pushed");
            }
            return pushed;
            
        } catch (Exception e) {
            LOGGER.warning("pushTableIncremental failed for " + tableName + ": " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Fetch local rows that changed since last sync.
     */
    private static List<Map<String, Object>> fetchLocalRowsChanged(Connection conn, String tableName,
            String clientId, String email, String lastSync) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        
        String sql;
        if (lastSync != null && !lastSync.isEmpty()) {
            sql = "SELECT * FROM " + tableName + 
                  " WHERE (client_id = ? OR user_email = ?) AND (updated_at IS NULL OR updated_at > ?)";
        } else {
            sql = "SELECT * FROM " + tableName + " WHERE client_id = ? OR user_email = ?";
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, email);
            if (lastSync != null && !lastSync.isEmpty()) {
                ps.setString(3, lastSync);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.fine("fetchLocalRowsChanged (" + tableName + "): " + e.getMessage());
        }
        return rows;
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  STOCK RECOMPUTATION (Source of Truth)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Recompute stock for all products from item_flows.
     * This is the SINGLE SOURCE OF TRUTH for inventory.
     */
    public static int recomputeAllStockQuantities() {
        String clientId = getClientId();
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.warning("recomputeAllStockQuantities: no client_id");
            return 0;
        }
        
        int updated = 0;
        try (Connection conn = Database.getConnection()) {
            // Get all products for this client
            List<Integer> productIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM products WHERE client_id = ?")) {
                ps.setString(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        productIds.add(rs.getInt(1));
                    }
                }
            }
            
            if (productIds.isEmpty()) return 0;
            
            // Batch update stock quantities
            String updateSql = "UPDATE products SET stock_quantity = ? WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                for (int productId : productIds) {
                    int stock = computeSingleProductStock(conn, productId, clientId);
                    updateStmt.setInt(1, stock);
                    updateStmt.setInt(2, productId);
                    updateStmt.addBatch();
                    updated++;
                }
                updateStmt.executeBatch();
            }
            
            LOGGER.info("🔄 Stock recomputed for " + updated + " products");
            return updated;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "recomputeAllStockQuantities failed", e);
            return 0;
        }
    }
    
    /**
     * Compute stock for a single product from item_flows.
     */
    private static int computeSingleProductStock(Connection conn, int productId, String clientId)
            throws SQLException {
        // SUM positive actions minus negative actions
        String sql = 
            "SELECT COALESCE(SUM(CASE WHEN UPPER(TRIM(action)) IN ('NEW ITEM','RECEIVED','RECEIVE','RETURN','RETURNED') THEN quantity " +
            "WHEN UPPER(TRIM(action)) IN ('SOLD','SALE','SELL') THEN -quantity " +
            "ELSE 0 END), 0) as net_stock " +
            "FROM item_flows " +
            "WHERE product_id = ? AND (client_id = ? OR client_id IS NULL)";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("net_stock"));
                }
            }
        }
        return 0;
    }
    
    /**
     * Recompute stock for a single product (public helper).
     */
    public static int recomputeProductStock(int productId) {
        String clientId = getClientId();
        if (clientId == null) return 0;
        
        try (Connection conn = Database.getConnection()) {
            int stock = computeSingleProductStock(conn, productId, clientId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE products SET stock_quantity = ? WHERE id = ?")) {
                ps.setInt(1, stock);
                ps.setInt(2, productId);
                ps.executeUpdate();
            }
            return stock;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "recomputeProductStock failed for " + productId, e);
            return 0;
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  LAST SYNC TIMESTAMP MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    private static String getLastSyncTimestamp() {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, LAST_SYNC_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            LOGGER.fine("Could not read last sync timestamp: " + e.getMessage());
        }
        return null;
    }
    
    private static void updateLastSyncTimestamp() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            ps.setString(1, LAST_SYNC_KEY);
            ps.setString(2, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Could not update last sync timestamp: " + e.getMessage());
        }
    }
    
    private static void clearLastSyncTimestamp() {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM app_settings WHERE key = ?")) {
            ps.setString(1, LAST_SYNC_KEY);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Could not clear last sync timestamp: " + e.getMessage());
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  BACKUP MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    private static final String BACKUP_DIR = "pos_backups";
    
    static {
        new File(BACKUP_DIR).mkdirs();
    }
    
    private static String createLocalBackup() {
        String dbPath = Database.getDatabaseFile();
        if (dbPath == null) return null;
        
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupName = String.format("%s/backup_%s_%s.db", BACKUP_DIR, getClientId(), timestamp);
            
            try (FileInputStream in = new FileInputStream(dbPath);
                 FileOutputStream out = new FileOutputStream(backupName)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            LOGGER.info("📀 Backup created: " + backupName);
            return backupName;
        } catch (IOException e) {
            LOGGER.warning("Backup failed: " + e.getMessage());
            return null;
        }
    }
    
    private static void cleanupOldBackups(int keepCount) {
        File backupDir = new File(BACKUP_DIR);
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".db"));
        if (backups == null || backups.length <= keepCount) return;
        
        Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keepCount; i < backups.length; i++) {
            if (backups[i].delete()) {
                LOGGER.fine("Deleted old backup: " + backups[i].getName());
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  SQL BUILDERS (Safe escaping)
    // ════════════════════════════════════════════════════════════════════════
    
    private static String buildInsertOrReplace(String table, JsonObject row) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        
        for (String key : row.keySet()) {
            if (cols.length() > 0) {
                cols.append(",");
                vals.append(",");
            }
            cols.append('"').append(key).append('"');
            
            JsonElement element = row.get(key);
            if (element.isJsonNull()) {
                vals.append("NULL");
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive prim = element.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    vals.append(prim.getAsNumber());
                } else if (prim.isBoolean()) {
                    vals.append(prim.getAsBoolean() ? 1 : 0);
                } else {
                    vals.append("'").append(escape(prim.getAsString())).append("'");
                }
            } else {
                vals.append("'").append(escape(element.toString())).append("'");
            }
        }
        
        return "INSERT OR REPLACE INTO " + table + " (" + cols + ") VALUES (" + vals + ")";
    }
    
    private static String buildCloudInsertOrReplace(String table, Map<String, Object> row) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (cols.length() > 0) {
                cols.append(",");
                vals.append(",");
            }
            cols.append('"').append(entry.getKey()).append('"');
            
            Object v = entry.getValue();
            if (v == null) {
                vals.append("NULL");
            } else if (v instanceof Number) {
                vals.append(v);
            } else if (v instanceof Boolean) {
                vals.append(((Boolean) v) ? 1 : 0);
            } else {
                vals.append("'").append(escape(v.toString())).append("'");
            }
        }
        
        return "INSERT OR REPLACE INTO " + table + " (" + cols + ") VALUES (" + vals + ")";
    }
    
    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════
    
    private static boolean isReady() {
        String clientId = getClientId();
        String email = getEmail();
        
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.fine("SyncEngine: no client_id");
            return false;
        }
        if (email == null || email.isEmpty()) {
            LOGGER.fine("SyncEngine: no user email");
            return false;
        }
        if (!Database.isOnline()) {
            LOGGER.fine("SyncEngine: offline");
            return false;
        }
        return true;
    }
    
    private static String getClientId() {
        String id = Database.getCurrentClientId();
        if (id != null && !id.isEmpty()) return id;
        id = SQLiteCloudClient.getCurrentClientId();
        if (id != null && !id.isEmpty()) return id;
        return null;
    }
    
    private static String getEmail() {
        String email = SQLiteCloudClient.getCurrentUserEmail();
        if (email == null || email.isEmpty()) {
            email = Database.getCurrentUserEmail();
        }
        return email;
    }
    
    private static void ensureRowHasIds(JsonObject row, String clientId, String email) {
        if (!row.has("client_id") || row.get("client_id").isJsonNull()) {
            row.addProperty("client_id", clientId);
        }
        if (!row.has("user_email") || row.get("user_email").isJsonNull()) {
            row.addProperty("user_email", email);
        }
    }
    
    private static String getStringSafe(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : null;
    }
    
    private static Integer getIntSafe(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                return json.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private static String padRight(String s, int len) {
        if (s == null) s = "null";
        if (s.length() > len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }
}
