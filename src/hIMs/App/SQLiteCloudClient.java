package hIMs.App;
import java.time.LocalDate;
import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
public class SQLiteCloudClient {

    private static final Logger LOGGER = Logger.getLogger(SQLiteCloudClient.class.getName());

    private static final String API_KEY  = "QdNkfvSsBAxIn5ICUPbz4yccYkONO6rkbbcz0aM2BnU";
    private static final String HOST     = "cxkhjiw2dz.g5.sqlite.cloud";
    private static final String DATABASE = "Gratis-pos-org";
    private static final String API_URL  = "https://" + HOST + "/v2/weblite/sql";

    private static final int TIMEOUT = 15000;
    private static final String BACKUP_DIR = "pos_backups";
    
    // Sync configuration
    private static final String SYNC_QUEUE_TABLE = "sync_queue";
    private static final int BACKGROUND_SYNC_INTERVAL_SECONDS = 30;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    private static String currentUserEmail = null;
    private static String currentUsername = null;
    private static String currentClientId = null;
    private static String currentDatabasePath = null;
    private static Connection localConnection = null;
    
    private static boolean schemaVerified = false;
    
    private static String getClientIdFile() {
        String email = currentUserEmail != null ? currentUserEmail : "default";
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        return System.getProperty("user.home") + File.separator + ".gratispos" + 
               File.separator + "client_id_" + safeEmail + ".dat";
    }
    
    // Background sync
    private static volatile boolean backgroundSyncRunning = false;
    private static ScheduledExecutorService scheduler = null;
    private static final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    
    // Connection cache
    private static long lastConnectionCheck = 0;
    private static boolean cachedConnectionStatus = false;
    private static final long CONNECTION_CACHE_MS = 30000;

    static {
        new File(BACKUP_DIR).mkdirs();
        new File(System.getProperty("user.home") + File.separator + ".gratispos").mkdirs();
        try {
            FileHandler fh = new FileHandler(BACKUP_DIR + "/sqlitecloud_sync.log", 10_485_760, 5, true);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
        } catch (IOException ignored) {}
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopBackgroundSync();
            closeLocalConnection();
        }));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOCAL-FIRST SYNC QUEUE METHODS
    // ════════════════════════════════════════════════════════════════════════
    
    private static void createSyncQueueTable() {
        if (localConnection == null) return;
        
        // FIRST: Create the table if it doesn't exist
        String sql = "CREATE TABLE IF NOT EXISTS " + SYNC_QUEUE_TABLE + " (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "user_email TEXT, " +
                     "client_id TEXT, " +
                     "table_name TEXT NOT NULL, " +
                     "record_id INTEGER, " +
                     "operation TEXT NOT NULL, " +
                     "data TEXT NOT NULL, " +
                     "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                     "attempted_at DATETIME, " +
                     "status TEXT DEFAULT 'pending', " +
                     "retry_count INTEGER DEFAULT 0, " +
                     "error_message TEXT)";
        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute(sql);
            LOGGER.fine("Sync queue table created/verified");
        } catch (SQLException e) {
            LOGGER.warning("Failed to create sync queue table: " + e.getMessage());
            return;
        }
        
        // SECOND: Migrate existing table (add missing columns)
        migrateSyncQueueTable();
    }
    
    public static void queueSyncOperation(String tableName, int recordId, String operation, JsonObject data) {
        if (currentUserEmail == null || currentClientId == null) {
            LOGGER.warning("Cannot queue sync: user not set");
            return;
        }
        
        if (!isLocalConnectionValid()) {
            LOGGER.warning("Cannot queue sync: local connection invalid");
            return;
        }
        
        String sql = "INSERT INTO " + SYNC_QUEUE_TABLE + 
                     " (user_email, client_id, table_name, record_id, operation, data, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'pending')";
        
        try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
            ps.setString(1, currentUserEmail);
            ps.setString(2, currentClientId);
            ps.setString(3, tableName);
            ps.setInt(4, recordId);
            ps.setString(5, operation);
            ps.setString(6, data.toString());
            ps.executeUpdate();
            LOGGER.fine("Queued: " + operation + " on " + tableName + " id=" + recordId);
        } catch (SQLException e) {
            LOGGER.warning("Failed to queue sync operation: " + e.getMessage());
            // Try to reinitialize connection
            if (currentDatabasePath != null) {
                initLocalConnection(currentDatabasePath);
            }
        }
    }
    public static void queueDeletion(String tableName, int recordId) {
    if (currentUserEmail == null || currentClientId == null) return;
    if (!isLocalConnectionValid()) return;

    String sql = "INSERT INTO " + SYNC_QUEUE_TABLE +
                 " (user_email, client_id, table_name, record_id, operation, data, status) " +
                 "VALUES (?, ?, ?, ?, 'DELETE', '{}', 'pending')";
    try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
        ps.setString(1, currentUserEmail);
        ps.setString(2, currentClientId);
        ps.setString(3, tableName);
        ps.setInt(4, recordId);
        ps.executeUpdate();
        LOGGER.fine("Queued DELETE on " + tableName + " id=" + recordId);
    } catch (SQLException e) {
        LOGGER.warning("Failed to queue deletion: " + e.getMessage());
    }
}

public static boolean syncDeletion(String tableName, int recordId) {
    try {
        String sql = "DELETE FROM " + tableName +
                     " WHERE id = " + recordId +
                     " AND client_id = '" + escape(currentClientId) + "'";
        executeUpdate(sql);
        LOGGER.info("Cloud deletion synced: " + tableName + " id=" + recordId);
        return true;
    } catch (IOException e) {
        LOGGER.warning("Cloud deletion failed for " + tableName + " id=" + recordId + ": " + e.getMessage());
        return false;
    }
}
    
    private static void processSyncQueue() {
        if (!syncInProgress.compareAndSet(false, true)) {
            LOGGER.fine("Sync already in progress, skipping");
            return;
        }

        try {
            // ── Guard 1: No active user session ──
            if (currentClientId == null || currentClientId.isEmpty()) {
                LOGGER.fine("No active session (clientId null), skipping sync queue");
                return;
            }

            if (currentUserEmail == null || currentUserEmail.isEmpty()) {
                LOGGER.fine("No active session (email null), skipping sync queue");
                return;
            }

            // ── Guard 2: Local connection must be valid ──
            if (!isLocalConnectionValid()) {
                LOGGER.fine("Local connection not ready, skipping sync queue");
                // Try to reconnect if we know the DB path
                if (currentDatabasePath != null) {
                    LOGGER.info("Attempting to reconnect local DB: " + currentDatabasePath);
                    initLocalConnection(currentDatabasePath);
                    // Check again after reconnect attempt
                    if (!isLocalConnectionValid()) {
                        LOGGER.warning("Reconnect failed, skipping sync queue");
                        return;
                    }
                    LOGGER.info("Local DB reconnected successfully");
                } else {
                    return;
                }
            }

            // ── Guard 3: Must be online ──
            if (!checkConnection()) {
                LOGGER.fine("Offline, skipping sync queue processing");
                return;
            }

            // ── Fetch and process pending operations ──
            List<Map<String, Object>> pendingOps;
            try {
                pendingOps = fetchPendingOperations();
            } catch (SQLException e) {
                LOGGER.warning("Could not fetch pending operations: " + e.getMessage());
                return;
            }

            if (pendingOps.isEmpty()) {
                LOGGER.fine("Sync queue empty for client: " + currentClientId);
                return;
            }

            LOGGER.info("Processing " + pendingOps.size() +
                        " pending sync operations for client: " + currentClientId);

            int successCount = 0;
            int failCount = 0;

            for (Map<String, Object> op : pendingOps) {
                // Guard: validate the queue item has minimum required fields
                if (op.get("id") == null || op.get("table_name") == null
                        || op.get("operation") == null) {
                    LOGGER.warning("Skipping malformed queue item: " + op);
                    continue;
                }

                // Guard: only process items belonging to current client
                Object itemClientId = op.get("client_id");
                if (itemClientId != null && !itemClientId.toString().isEmpty()
                        && !itemClientId.toString().equals(currentClientId)) {
                    LOGGER.fine("Skipping queue item for different client: " + itemClientId);
                    continue;
                }

                try {
                    boolean success = processQueueItem(op);
                    if (success) {
                        successCount++;
                        markQueueItemProcessed((Integer) op.get("id"));
                    } else {
                        failCount++;
                        markQueueItemFailed((Integer) op.get("id"));
                    }
                } catch (Exception itemEx) {
                    // Don't let one bad item kill the whole queue
                    failCount++;
                    LOGGER.warning("Exception processing queue item id=" +
                                   op.get("id") + ": " + itemEx.getMessage());
                    try {
                        markQueueItemFailed((Integer) op.get("id"));
                    } catch (Exception markEx) {
                        LOGGER.warning("Could not mark item failed: " + markEx.getMessage());
                    }
                }
            }

            if (successCount > 0 || failCount > 0) {
                LOGGER.info("Sync queue processed: " + successCount + " succeeded, " +
                            failCount + " failed out of " + pendingOps.size() +
                            " for client: " + currentClientId);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing sync queue", e);
        } finally {
            // Always release the lock
            syncInProgress.set(false);
        }
    }
    
    private static List<Map<String, Object>> fetchPendingOperations() throws SQLException {
        List<Map<String, Object>> ops = new ArrayList<>();
        
        if (!isLocalConnectionValid()) {
            LOGGER.warning("Cannot fetch pending ops: connection invalid");
            return ops;
        }
        
        String sql = "SELECT * FROM " + SYNC_QUEUE_TABLE + 
                     " WHERE status = 'pending' AND (client_id = ? OR client_id IS NULL) " +
                     " AND retry_count < ? ORDER BY created_at ASC LIMIT ?";
        
        try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
            ps.setString(1, currentClientId);
            ps.setInt(2, MAX_RETRIES);
            ps.setInt(3, MAX_BATCH_SIZE);
            
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    ops.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("fetchPendingOperations failed: " + e.getMessage());
            throw e;
        }
        return ops;
    }
    private static void migrateCloudUsersTable() {
        try {
            // Check if email_verified column exists
            try {
                execute("SELECT email_verified FROM users LIMIT 1");
            } catch (IOException e) {
                // Column doesn't exist, add it
                executeUpdate("ALTER TABLE users ADD COLUMN email_verified INTEGER DEFAULT 0");
                LOGGER.info("Added email_verified column to users table");
            }
            
            // Check if updated_at column exists
            try {
                execute("SELECT updated_at FROM users LIMIT 1");
            } catch (IOException e) {
                executeUpdate("ALTER TABLE users ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP");
                LOGGER.info("Added updated_at column to users table");
            }
            
            // Check if created_date column exists
            try {
                execute("SELECT created_date FROM users LIMIT 1");
            } catch (IOException e) {
                executeUpdate("ALTER TABLE users ADD COLUMN created_date DATETIME DEFAULT CURRENT_TIMESTAMP");
                LOGGER.info("Added created_date column to users table");
            }
            
            // Check if account_status column exists
            try {
                execute("SELECT account_status FROM users LIMIT 1");
            } catch (IOException e) {
                executeUpdate("ALTER TABLE users ADD COLUMN account_status TEXT DEFAULT 'ACTIVE'");
                LOGGER.info("Added account_status column to users table");
            }
            
        } catch (IOException e) {
            LOGGER.warning("Could not migrate cloud users table: " + e.getMessage());
        }
    }
    
    private static boolean processQueueItem(Map<String, Object> item) {
    try {
        String tableName = (String) item.get("table_name");
        String operation = (String) item.get("operation");
        String dataJson  = (String) item.get("data");
        int recordId = ((Number) item.get("record_id")).intValue();

        if ("DELETE".equals(operation)) {
            String sql = "DELETE FROM " + tableName +
                         " WHERE id = " + recordId +
                         " AND client_id = '" + escape(currentClientId) + "'";
            executeUpdate(sql);
            return true;
        }

        if ("INSERT".equals(operation) || "UPDATE".equals(operation)) {
            JsonObject data = JsonParser.parseString(dataJson).getAsJsonObject();
            data.addProperty("client_id", currentClientId);
            data.addProperty("user_email", currentUserEmail);
            String sql = buildUpsertStatement(tableName, data);
            executeUpdate(sql);
            return true;
        }

        // REFUND is handled by its own dedicated path
        if ("REFUND".equals(operation)) {
            return true; // already processed by updateCloudRefund
        }

        LOGGER.warning("Unknown queue operation: " + operation);
        return false;

    } catch (Exception e) {
        LOGGER.warning("Failed to process queue item: " + e.getMessage());
        return false;
    }
}
    
    private static void markQueueItemProcessed(int id) {
        if (!isLocalConnectionValid()) return;
        
        String sql = "UPDATE " + SYNC_QUEUE_TABLE + " SET status = 'processed' WHERE id = ?";
        try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to mark queue item processed: " + e.getMessage());
        }
    }
    
    private static void markQueueItemFailed(int id) {
        if (!isLocalConnectionValid()) return;
        
        String sql = "UPDATE " + SYNC_QUEUE_TABLE + 
                     " SET retry_count = retry_count + 1, attempted_at = CURRENT_TIMESTAMP, " +
                     "status = CASE WHEN retry_count >= ? THEN 'failed' ELSE 'pending' END " +
                     "WHERE id = ?";
        try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
            ps.setInt(1, MAX_RETRIES - 1);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to mark queue item failed: " + e.getMessage());
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    //  BACKGROUND SYNC MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    public static synchronized void startBackgroundSync() {
        if (backgroundSyncRunning) return;
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        backgroundSyncRunning = true;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (checkConnection() && !syncInProgress.get()) {
                    processSyncQueue();
                    pullIncrementalFromCloud();
                }
            } catch (Exception e) {
                LOGGER.warning("Background sync error: " + e.getMessage());
            }
        }, BACKGROUND_SYNC_INTERVAL_SECONDS, BACKGROUND_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        LOGGER.info("Background sync started (interval: " + BACKGROUND_SYNC_INTERVAL_SECONDS + "s)");
    }
    
    public static synchronized void stopBackgroundSync() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        backgroundSyncRunning = false;
        LOGGER.info("Background sync stopped");
    }
    
    private static void pullIncrementalFromCloud() {
        if (currentClientId == null || localConnection == null) return;
        
        try {
            String lastSync = getLastSyncTimestamp();
            LOGGER.fine("Pulling from cloud since: " + (lastSync != null ? lastSync : "beginning"));
            
            String[] tables = {"categories", "products", "item_flows", "sales", 
                    "sale_items", "vendors", "daily_expenses", "users"};
            
            int totalPulled = 0;
            for (String table : tables) {
                totalPulled += pullTableIncremental(table, lastSync);
            }
            
            if (totalPulled > 0) {
                LOGGER.info("Pulled " + totalPulled + " rows from cloud");
                updateLastSyncTimestamp();
                SyncEngine.recomputeAllStockQuantities();
            }
            
        } catch (Exception e) {
            LOGGER.warning("Pull from cloud failed: " + e.getMessage());
        }
    }
    
    private static int pullTableIncremental(String tableName, String lastSync) {
        try {
            String query = "SELECT * FROM " + tableName + " WHERE client_id = ?";
            if (lastSync != null && !lastSync.isEmpty()) {
                query += " AND (updated_at IS NULL OR updated_at > ?)";
            }
            
            List<JsonObject> rows;
            if (lastSync != null && !lastSync.isEmpty()) {
                rows = execute(query, currentClientId, lastSync);
            } else {
                rows = execute(query, currentClientId);
            }
            
            if (rows.isEmpty()) return 0;
            
            Connection conn = Database.getConnection();
            int merged = 0;
            
            for (JsonObject row : rows) {
                if (!row.has("client_id")) row.addProperty("client_id", currentClientId);
                if (!row.has("user_email")) row.addProperty("user_email", currentUserEmail);
                
                if ("products".equals(tableName) && row.has("stock_quantity")) {
                    row.remove("stock_quantity");
                }
                
                if ("users".equals(tableName) && row.has("password")) {
                    row.remove("password");
                }
                
                String sql = buildInsertOrReplace(tableName, row);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.executeUpdate();
                    merged++;
                }
            }
            
            if (merged > 0) {
                LOGGER.fine("Pulled " + merged + " rows from " + tableName);
            }
            return merged;
            
        } catch (Exception e) {
            LOGGER.warning("Failed to pull " + tableName + ": " + e.getMessage());
            return 0;
        }
    }
    
    private static final String LAST_SYNC_KEY = "last_cloud_sync";
    
    private static String getLastSyncTimestamp() {
        if (localConnection == null) return null;
        try (PreparedStatement ps = localConnection.prepareStatement(
                "SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, LAST_SYNC_KEY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException e) {}
        return null;
    }
    
    private static void updateLastSyncTimestamp() {
        if (localConnection == null) return;
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (PreparedStatement ps = localConnection.prepareStatement(
                "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            ps.setString(1, LAST_SYNC_KEY);
            ps.setString(2, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to update last sync timestamp: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CLIENT ID MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════
    
    public static String getOrCreateClientId() {
        // FIRST: Try to get from local database (most reliable source)
        if (localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT value FROM app_settings WHERE key = 'client_id' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String dbClientId = rs.getString("value");
                    if (dbClientId != null && !dbClientId.isEmpty() && !"null".equals(dbClientId)) {
                        // Sync to file if different
                        String fileId = loadClientIdFromFile();
                        if (!dbClientId.equals(fileId)) {
                            saveClientIdToFile(dbClientId);
                            LOGGER.info("Synced client_id from DB to file: " + dbClientId);
                        }
                        return dbClientId;
                    }
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from local DB: " + e.getMessage());
            }
        }
        
        // SECOND: Try to get from products table (actual data has the real ID)
        if (localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT DISTINCT client_id FROM products WHERE client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String productClientId = rs.getString("client_id");
                    if (productClientId != null && !productClientId.isEmpty()) {
                        // Sync everywhere
                        saveClientIdToFile(productClientId);
                        storeClientIdInLocalDB(productClientId);
                        LOGGER.info("Retrieved client_id from products table: " + productClientId);
                        return productClientId;
                    }
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from products: " + e.getMessage());
            }
        }
        
        // THIRD: Try users table
        if (localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT client_id FROM users WHERE client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String userClientId = rs.getString("client_id");
                    if (userClientId != null && !userClientId.isEmpty()) {
                        saveClientIdToFile(userClientId);
                        storeClientIdInLocalDB(userClientId);
                        LOGGER.info("Retrieved client_id from users table: " + userClientId);
                        return userClientId;
                    }
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from users: " + e.getMessage());
            }
        }
        
        // FOURTH: Try file
        String existingId = loadClientIdFromFile();
        if (existingId != null && !existingId.isEmpty() && !"null".equals(existingId)) {
            // Store in local DB for future
            if (localConnection != null) {
                storeClientIdInLocalDB(existingId);
            }
            LOGGER.info("Loaded client_id from file: " + existingId);
            return existingId;
        }
        
        // LAST RESULT: Create new based on email/business name pattern (NOT random UUID)
        String newClientId = generateDeterministicClientId();
        saveClientIdToFile(newClientId);
        if (localConnection != null) {
            storeClientIdInLocalDB(newClientId);
        }
        LOGGER.info("Created new deterministic client ID: " + newClientId);
        return newClientId;
    }

    private static String generateDeterministicClientId() {
        // Use email or username to generate a consistent ID
        String source = currentUserEmail != null ? currentUserEmail : 
                       (currentUsername != null ? currentUsername : "gratispos");
        // Take first 16 chars of MD5 hash
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return source.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(16, source.length()));
        }
    }
    
    public static void syncClientIdAcrossAllSources() {
        String primaryClientId = null;
        
        // Priority 1: Local database app_settings (authoritative)
        if (localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT value FROM app_settings WHERE key = 'client_id' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    primaryClientId = rs.getString("value");
                    if (primaryClientId != null && !primaryClientId.isEmpty() && !"null".equals(primaryClientId)) {
                        LOGGER.info("Found client_id in app_settings: " + primaryClientId);
                    }
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from app_settings: " + e.getMessage());
            }
        }
        
        // Priority 2: Products table (actual data has the real ID)
        if ((primaryClientId == null || primaryClientId.isEmpty() || "null".equals(primaryClientId)) && localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT DISTINCT client_id FROM products WHERE client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    primaryClientId = rs.getString("client_id");
                    LOGGER.info("Found client_id in products table: " + primaryClientId);
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from products: " + e.getMessage());
            }
        }
        
        // Priority 3: Users table
        if ((primaryClientId == null || primaryClientId.isEmpty() || "null".equals(primaryClientId)) && localConnection != null) {
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT client_id FROM users WHERE client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    primaryClientId = rs.getString("client_id");
                    LOGGER.info("Found client_id in users table: " + primaryClientId);
                }
            } catch (SQLException e) {
                LOGGER.fine("Could not read client_id from users: " + e.getMessage());
            }
        }
        
        // Priority 4: File
        if (primaryClientId == null || primaryClientId.isEmpty() || "null".equals(primaryClientId)) {
            primaryClientId = loadClientIdFromFile();
            if (primaryClientId != null && !primaryClientId.isEmpty()) {
                LOGGER.info("Found client_id in file: " + primaryClientId);
            }
        }
        
        // If we found a valid client_id, sync it everywhere
        if (primaryClientId != null && !primaryClientId.isEmpty() && !"null".equals(primaryClientId)) {
            
            // Skip if it's the old UUID (migration cleanup)
            if (primaryClientId.equals("b7d9a229-71fe-4243-8583-0f026bb4d7fe")) {
                LOGGER.warning("Found old UUID client_id, attempting to replace with correct one");
                // Try to get from products again (bypassing the UUID)
                if (localConnection != null) {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "SELECT DISTINCT client_id FROM products WHERE client_id != ? AND client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
                        ps.setString(1, primaryClientId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            primaryClientId = rs.getString("client_id");
                            LOGGER.info("Replaced UUID with correct client_id from products: " + primaryClientId);
                        }
                    } catch (SQLException e) {
                        LOGGER.fine("Could not replace UUID: " + e.getMessage());
                    }
                }
            }
            
            final String finalClientId = primaryClientId;
            
            // Sync to file
            saveClientIdToFile(finalClientId);
            
            // Sync to local DB app_settings
            if (localConnection != null) {
                storeClientIdInLocalDB(finalClientId);
            }
            
            // Update all tables that might have stale or null client_id
            if (localConnection != null) {
                try (Statement stmt = localConnection.createStatement()) {
                    stmt.executeUpdate("UPDATE products SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE users SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE sales SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE sale_items SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE categories SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE vendors SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    stmt.executeUpdate("UPDATE item_flows SET client_id = '" + finalClientId + 
                        "' WHERE client_id IS NULL OR client_id = '' OR client_id = 'null' OR client_id = 'b7d9a229-71fe-4243-8583-0f026bb4d7fe'");
                    LOGGER.info("Updated all tables with correct client_id: " + finalClientId);
                } catch (SQLException e) {
                    LOGGER.warning("Could not update stale client_ids: " + e.getMessage());
                }
            }
            
            currentClientId = finalClientId;
            LOGGER.info("Client ID synchronized across all sources: " + finalClientId);
            
        } else {
            LOGGER.warning("No valid client_id found in any source");
        }
    }
    public static String loadClientIdFromFile() {
        String email = currentUserEmail;
        if (email == null || email.isEmpty()) {
            // Fallback for when no user is logged in yet
            File oldFile = new File(System.getProperty("user.home") + File.separator + ".gratispos" + File.separator + "client_id.dat");
            if (oldFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(oldFile))) {
                    return reader.readLine().trim();
                } catch (IOException e) { return null; }
            }
            return null;
        }
        String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
        File idFile = new File(System.getProperty("user.home") + File.separator + ".gratispos" + 
                              File.separator + "client_id_" + safeEmail + ".dat");
        if (!idFile.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(idFile))) {
            return reader.readLine().trim();
        } catch (IOException e) { return null; }
    }
    
    
    public static void saveClientIdToFile(String clientId) {
        if (clientId == null || clientId.isEmpty()) return;
        try {
            File dir = new File(System.getProperty("user.home") + File.separator + ".gratispos");
            if (!dir.exists()) dir.mkdirs();
            
            String email = currentUserEmail;
            String fileName;
            if (email != null && !email.isEmpty()) {
                String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
                fileName = "client_id_" + safeEmail + ".dat";
            } else {
                fileName = "client_id.dat";
            }
            
            File idFile = new File(dir, fileName);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(idFile))) {
                writer.write(clientId);
                writer.newLine();
            }
        } catch (IOException e) { 
            LOGGER.warning("Failed to save client ID to file: " + e.getMessage()); 
        }
    }
    
    public static void storeClientIdInLocalDB(String clientId) {
        if (localConnection == null || clientId == null || clientId.isEmpty()) return;
        try {
            try (Statement stmt = localConnection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS app_settings " +
                    "(key TEXT PRIMARY KEY, value TEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            }
            // Store under BOTH keys so lookupClientIdLocalFromFile always finds it
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, "client_id");
                ps.setString(2, clientId);
                ps.executeUpdate();
                // Also store with email key for multi-user scenarios
                if (currentUserEmail != null) {
                    ps.setString(1, "client_id_" + currentUserEmail);
                    ps.setString(2, clientId);
                    ps.executeUpdate();
                }
            }
            LOGGER.info("Stored client_id=" + clientId + " in local app_settings");
        } catch (SQLException e) {
            LOGGER.warning("Failed to store client ID in DB: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HTTP EXECUTION METHODS
    // ════════════════════════════════════════════════════════════════════════

    public static List<JsonObject> execute(String sql, Object... args) throws IOException {
        String finalSql = buildSql(sql, args);
        LOGGER.fine("Executing SQL: " + finalSql);

        URL url = new URL(API_URL);
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("database", DATABASE);
            body.addProperty("sql", finalSql);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = readStream(is);

            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + response);
            }

            return parseResponse(response);

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static int executeUpdate(String sql, Object... args) throws IOException {
        String finalSql = buildSql(sql, args);
        LOGGER.info("Executing update: " + finalSql);

        URL url = new URL(API_URL);
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("database", DATABASE);
            body.addProperty("sql", finalSql);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            String response = readStream(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());

            if (status >= 200 && status < 300) {
                // Try to parse response for rows_affected
                try {
                    JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                    if (jsonResponse.has("rows_affected")) {
                        return jsonResponse.get("rows_affected").getAsInt();
                    }
                    if (jsonResponse.has("results") && jsonResponse.getAsJsonArray("results").size() > 0) {
                        JsonObject firstResult = jsonResponse.getAsJsonArray("results").get(0).getAsJsonObject();
                        if (firstResult.has("rows_affected")) {
                            return firstResult.get("rows_affected").getAsInt();
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing, return 1 for success
                }
                return 1;
            } else {
                throw new IOException("HTTP " + status + ": " + response);
            }

        } finally {
            if (conn != null) conn.disconnect();
        }
    }
 // Add this anywhere in SQLiteCloudClient class
    public static void checkSubscriptionOrLock() throws IOException {
        if (currentUserEmail == null || currentClientId == null) return;
        
        // Query subscription status from cloud
        List<JsonObject> result = execute(
            "SELECT status, end_date FROM subscriptions WHERE user_email = ? AND client_id = ?",
            currentUserEmail, currentClientId);
        
        if (result.isEmpty()) {
            // New user - give 30 days free trial
            executeUpdate(
                "INSERT INTO subscriptions (user_email, client_id, status, end_date) VALUES (?, ?, 'active', datetime('now', '+30 days'))",
                currentUserEmail, currentClientId);
            return;
        }
        
        JsonObject sub = result.get(0);
        String status = sub.get("status").getAsString();
        String endDate = sub.get("end_date").getAsString();
        
        if ("expired".equals(status) || LocalDate.parse(endDate).isBefore(LocalDate.now())) {
            throw new IOException("SUBSCRIPTION_EXPIRED");
        }
    }

    public static void executeUpdateDirect(String sql) throws IOException {
        executeUpdate(sql);
    }
    

    public static int executeUpdateDirect(String sql, Object... args) throws IOException {
        return executeUpdate(sql, args);
    }

    private static List<JsonObject> parseResponse(String json) {
        List<JsonObject> results = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return results;
        
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return results;
        
        try {
            JsonElement element = JsonParser.parseString(trimmed);
            
            if (element.isJsonArray()) {
                for (JsonElement e : element.getAsJsonArray()) {
                    if (e.isJsonObject()) results.add(e.getAsJsonObject());
                }
                return results;
            }
            
            JsonObject root = element.getAsJsonObject();

            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                String detail = err.has("detail") ? err.get("detail").getAsString() : json;
                throw new RuntimeException("SQLite Cloud error: " + detail);
            }

            if (root.has("data") && root.get("data").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("data")) {
                    if (e.isJsonObject()) results.add(e.getAsJsonObject());
                }
            } else if (root.has("results")) {
                for (JsonElement e : root.getAsJsonArray("results")) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("values")) {
                        JsonArray values = e.getAsJsonObject().getAsJsonArray("values");
                        if (values.size() > 0) {
                            JsonObject row = new JsonObject();
                            for (int i = 0; i < values.size(); i++) row.add("col_" + i, values.get(i));
                            results.add(row);
                        }
                    } else if (e.isJsonObject()) {
                        results.add(e.getAsJsonObject());
                    }
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warning("Parse error: " + e.getMessage());
        }
        return results;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONNECTION CHECK (WITH CACHING)
    // ════════════════════════════════════════════════════════════════════════

    public static boolean checkConnection() {
        long now = System.currentTimeMillis();
        if (now - lastConnectionCheck < CONNECTION_CACHE_MS) {
            return cachedConnectionStatus;
        }
        lastConnectionCheck = now;

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("database", DATABASE);
            body.addProperty("sql", "SELECT 1 as test");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            conn.disconnect();

            boolean wasOnline = cachedConnectionStatus;
            cachedConnectionStatus = (status >= 200 && status < 300);

            if (!wasOnline && cachedConnectionStatus) {
                startBackgroundSync();
                processSyncQueue();
            }
            
            if (cachedConnectionStatus && !schemaVerified) {
                ensureCloudSchema();
                schemaVerified = true;
            }
            return cachedConnectionStatus;

        } catch (Exception e) {
            cachedConnectionStatus = false;
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SESSION STATE METHODS
    // ════════════════════════════════════════════════════════════════════════

    public static void setCurrentUser(String email, String username, String clientId) {
        currentUserEmail = email;
        currentUsername = username;
        currentClientId = clientId;
        if (localConnection != null && clientId != null) storeClientIdInLocalDB(clientId);
        LOGGER.info("Session: email=" + email + ", clientId=" + clientId);
        
        if (checkConnection()) {
            startBackgroundSync();
        }
    }

    public static void setCurrentUser(String email, String username) {
    // NEVER generate a new UUID here — this overwrites a valid client_id
    // resolved earlier. The 3-arg version must always be called with the
    // correct client_id already resolved.
    // If somehow called directly, preserve existing client_id if we have one.
    if (currentClientId != null && !currentClientId.isEmpty()) {
        LOGGER.warning("setCurrentUser(2-arg) called but clientId already set to: " + 
                       currentClientId + " — preserving it");
        setCurrentUser(email, username, currentClientId);
    } else {
        // Only as absolute last resort — log loudly
        String clientId = getOrCreateClientId();
        LOGGER.severe("setCurrentUser(2-arg): no clientId available, using file-based UUID: " + 
                      clientId + " — this may cause empty data!");
        setCurrentUser(email, username, clientId);
    }
}
    
    public static String getCurrentUserEmail()  { return currentUserEmail; }
    public static String getCurrentUsername()   { return currentUsername; }
    public static String getCurrentClientId()   { return currentClientId; }
    
    public static boolean isSessionActive() {
        return currentClientId != null && !currentClientId.isEmpty() && currentUserEmail != null;
    }

    public static void initLocalConnection(String dbFilePath) {
    try {
        Class.forName("org.sqlite.JDBC");
        currentDatabasePath = dbFilePath;

        if (localConnection != null) {
            try {
                if (!localConnection.isClosed()) localConnection.close();
            } catch (SQLException e) {
                LOGGER.fine("Error closing old connection: " + e.getMessage());
            }
            localConnection = null;
        }

        localConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
        localConnection.setAutoCommit(true);

        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA busy_timeout=10000");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        createSyncQueueTable();

        // FIXED BUG 18: Deduplicate BEFORE creating unique indexes
        deduplicateVendors();
        deduplicateCategories();

        // Now safe to create unique indexes
        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_vendors_unique_name " +
                         "ON vendors(client_id, UPPER(name))");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_unique_name " +
                         "ON categories(client_id, UPPER(name))");
        } catch (SQLException e) {
            LOGGER.warning("Could not create unique indexes: " + e.getMessage());
        }

        if (currentClientId == null) {
            String savedId = loadClientIdFromLocalDB();
            if (savedId != null) currentClientId = savedId;
        }
        LOGGER.info("Local DB connected: " + dbFilePath);
    } catch (Exception e) {
        LOGGER.severe("initLocalConnection: " + e.getMessage());
    }
}
    private static String loadClientIdFromLocalDB() {
        if (localConnection == null) return null;
        try (PreparedStatement ps = localConnection.prepareStatement(
                "SELECT value FROM app_settings WHERE key = 'client_id' LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            LOGGER.fine("Could not load client_id from local DB: " + e.getMessage());
        }
        return null;
    }
    public static Connection getLocalConnection() {
        if (localConnection == null) {
            LOGGER.warning("Local connection is null, reinitializing");
            if (currentDatabasePath != null) {
                initLocalConnection(currentDatabasePath);
            }
            return localConnection;
        }
        
        try {
            if (localConnection.isClosed()) {
                LOGGER.warning("Local connection was closed, reconnecting");
                if (currentDatabasePath != null) {
                    initLocalConnection(currentDatabasePath);
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Error checking connection status: " + e.getMessage());
            if (currentDatabasePath != null) {
                initLocalConnection(currentDatabasePath);
            }
        }
        return localConnection;
    }

    public static void closeLocalConnection() {
        try { 
            if (localConnection != null && !localConnection.isClosed()) {
                localConnection.close();
                LOGGER.info("Local connection closed");
            }
        } catch (SQLException e) { 
            LOGGER.fine("Error closing local connection: " + e.getMessage());
        } finally {
            localConnection = null;
        }
    }
    
    public static boolean isLocalConnectionValid() {
        try {
            if (localConnection == null) return false;
            if (localConnection.isClosed()) return false;
            try (Statement stmt = localConnection.createStatement()) {
                stmt.setQueryTimeout(1);
                stmt.executeQuery("SELECT 1").close();
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    private static void deduplicateVendors() {
    if (localConnection == null) return;
    try (Statement stmt = localConnection.createStatement()) {
        // Keep the row with the lowest id, delete duplicates
        stmt.execute(
            "DELETE FROM vendors WHERE id NOT IN (" +
            "  SELECT MIN(id) FROM vendors GROUP BY UPPER(name), client_id)");
    } catch (SQLException e) {
        LOGGER.fine("Vendor dedup: " + e.getMessage());
    }
}
private static void deduplicateCategories() {
    if (localConnection == null) return;
    try (Statement stmt = localConnection.createStatement()) {
        stmt.execute(
            "DELETE FROM categories WHERE id NOT IN (" +
            "  SELECT MIN(id) FROM categories GROUP BY UPPER(name), client_id)");
    } catch (SQLException e) {
        LOGGER.fine("Category dedup: " + e.getMessage());
    }
}
    

    // ════════════════════════════════════════════════════════════════════════
    //  CLOUD TABLES INITIALIZATION
    // ════════════════════════════════════════════════════════════════════════

public static void initializeCloudTables() {
    if (schemaVerified) return;

    String[] ddl = {
        "CREATE TABLE IF NOT EXISTS users (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "email TEXT UNIQUE NOT NULL, username TEXT NOT NULL, " +
        "password TEXT NOT NULL, privileges TEXT DEFAULT 'user', " +
        "business_name TEXT, created_date TEXT DEFAULT (datetime('now')), " +
        "locked INTEGER DEFAULT 0, lock_reason TEXT, " +
        "password_last_changed TEXT DEFAULT (datetime('now')), " +
        "account_status TEXT DEFAULT 'ACTIVE', email_verified INTEGER DEFAULT 0, " +
        "last_login TEXT, last_login_ip TEXT, " +
        "security_question1 TEXT, security_answer1 TEXT, " +
        "security_question2 TEXT, security_answer2 TEXT, " +
        "user_uid TEXT, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "name TEXT NOT NULL, description TEXT, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS products (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT NOT NULL, client_id TEXT, " +
        "name TEXT NOT NULL, cost_price DECIMAL(10,2) NOT NULL, " +
        "selling_price DECIMAL(10,2) NOT NULL, " +
        "stock_quantity INTEGER NOT NULL DEFAULT 0, " +
        "min_stock_level INTEGER NOT NULL DEFAULT 0, " +
        "category_id INTEGER, " +  // INTEGER not TEXT
        "vendor TEXT, expire_date TEXT, created_date TEXT, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS vendors (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "name TEXT NOT NULL, contact TEXT, phone TEXT, email TEXT, address TEXT, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS sales (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT NOT NULL, client_id TEXT, " +
        "local_id INTEGER, sales_total DECIMAL(10,2) NOT NULL, " +
        "discount DECIMAL(10,2) DEFAULT 0, sale_date TEXT NOT NULL, " +
        "user TEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS sale_items (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT NOT NULL, client_id TEXT, " +
        "local_id INTEGER, sale_id INTEGER, product_id INTEGER, " +
        "quantity INTEGER NOT NULL, unit_price DECIMAL(10,2) NOT NULL, " +
        "total_price DECIMAL(10,2) NOT NULL, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS item_flows (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "product_id INTEGER NOT NULL, action TEXT NOT NULL, " +
        "quantity INTEGER NOT NULL, flow_date TEXT, created_date TEXT, " +
        "vendor TEXT, user TEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS daily_expenses (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "expense_date TEXT NOT NULL, amount DECIMAL(10,2) NOT NULL, " +
        "description TEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS daily_sales_reports (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "report_date TEXT NOT NULL, total_gross_sale DECIMAL(10,2), " +
        "discounts DECIMAL(10,2), cost_of_goods DECIMAL(10,2), " +
        "daily_expenses DECIMAL(10,2), net_sale DECIMAL(10,2), " +
        "gross_profit DECIMAL(10,2), net_profit DECIMAL(10,2), " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS audit_log (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "username TEXT, action TEXT, ip_address TEXT, success INTEGER, " +
        "details TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS login_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "email TEXT, ip_address TEXT, success INTEGER, device_info TEXT, " +
        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)",

        // ── sync_queue with ALL columns Java code expects ──
        "CREATE TABLE IF NOT EXISTS sync_queue (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "table_name TEXT NOT NULL, record_id INTEGER NOT NULL, " +
        "operation TEXT NOT NULL, data TEXT, " +
        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
        "attempted_at DATETIME, status TEXT DEFAULT 'pending', " +
        "retry_count INTEGER DEFAULT 0, error_message TEXT)",

        "CREATE TABLE IF NOT EXISTS app_settings (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "setting_key TEXT NOT NULL, setting_value TEXT, " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS inventory_log (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "user_email TEXT, client_id TEXT, " +
        "product_id INTEGER NOT NULL, action TEXT NOT NULL, " +
        "quantity INTEGER NOT NULL, previous_quantity INTEGER DEFAULT 0, " +
        "date TEXT DEFAULT (date('now','localtime')), " +
        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS internal_messages (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "sender_email TEXT NOT NULL, recipient_email TEXT NOT NULL, " +
        "message TEXT NOT NULL, is_read INTEGER DEFAULT 0, " +
        "sent_at DATETIME DEFAULT CURRENT_TIMESTAMP, read_at DATETIME, " +
        "client_id TEXT, user_email TEXT)",
    };

    for (String sql : ddl) {
        try {
            executeUpdate(sql);
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("already exists") ||
                    msg.contains("duplicate column"))) {
                // expected on re-run
            } else {
                LOGGER.warning("DDL note: " + msg);
            }
        }
    }

    // ── Run migrations for existing cloud tables ──
    runCloudMigrations();

    schemaVerified = true;
    LOGGER.info("Cloud tables verified");
}

// ── Add this new method in SQLiteCloudClient.java ──
private static void runCloudMigrations() {
    String[] migrations = {
        "ALTER TABLE sync_queue ADD COLUMN user_email TEXT",
        "ALTER TABLE sync_queue ADD COLUMN client_id TEXT",
        "ALTER TABLE sync_queue ADD COLUMN data TEXT",
        "ALTER TABLE sync_queue ADD COLUMN retry_count INTEGER DEFAULT 0",
        "ALTER TABLE sync_queue ADD COLUMN error_message TEXT",
        "ALTER TABLE audit_log ADD COLUMN user_email TEXT",
        "ALTER TABLE inventory_log ADD COLUMN user_email TEXT",
        "ALTER TABLE users ADD COLUMN user_uid TEXT",
        "ALTER TABLE users ADD COLUMN user_email TEXT",
        "ALTER TABLE receipts ADD COLUMN user_email TEXT",
        "ALTER TABLE categories ADD COLUMN user_email TEXT",
    };

    for (String sql : migrations) {
        try {
            executeUpdate(sql);
        } catch (IOException e) {
            String msg = e.getMessage();
            // "duplicate column" means migration already ran — that is fine
            if (msg == null || (!msg.contains("duplicate column") &&
                    !msg.contains("already exists"))) {
                LOGGER.fine("Migration note: " + sql + " — " + msg);
            }
        }
    }
    LOGGER.info("Cloud migrations complete");
}

    // ════════════════════════════════════════════════════════════════════════
    //  FETCH CLIENT ID
    // ════════════════════════════════════════════════════════════════════════

    public static String fetchClientIdForUser(String email) {
        try {
            List<JsonObject> result = execute("SELECT client_id FROM users WHERE email = ?", email);
            if (!result.isEmpty() && result.get(0).has("client_id") 
                    && !result.get(0).get("client_id").isJsonNull()) {
                return result.get(0).get("client_id").getAsString();
            }
        } catch (IOException e) { LOGGER.warning("fetchClientIdForUser failed: " + e.getMessage()); }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UPDATE USER
    // ════════════════════════════════════════════════════════════════════════

    public static boolean updateUserDirect(String email, String username, String businessName, 
                                            String privileges, String question1, String answer1,
                                            String question2, String answer2) {
        try {
            String sql = "UPDATE users SET username = ?, business_name = ?, privileges = ?, " +
                        "security_question1 = ?, security_answer1 = ?, " +
                        "security_question2 = ?, security_answer2 = ? WHERE email = ?";
            executeUpdate(sql, username, businessName, privileges, 
                         question1, hashString(answer1), question2, hashString(answer2), email);
            return true;
        } catch (IOException e) { return false; }
    }

    private static String hashString(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  REVISED PULL FROM CLOUD - PRESERVES IDs, PREVENTS DUPLICATES
    // ════════════════════════════════════════════════════════════════════════

    public static boolean pullFromCloud() {
        if (currentUserEmail == null || currentUserEmail.isEmpty()) {
            LOGGER.warning("pullFromCloud: user not set");
            return false;
        }
        // Re-initialize local connection if it was lost (e.g. after logout/login cycle)
        if (!isLocalConnectionValid()) {
            LOGGER.warning("pullFromCloud: local connection invalid — attempting re-init");
            if (currentDatabasePath != null) {
                initLocalConnection(currentDatabasePath);
            }
            if (!isLocalConnectionValid()) {
                LOGGER.severe("pullFromCloud: cannot re-init local connection — aborting");
                return false;
            }
        }
        try {
            LOGGER.info("Pulling data for client: " + currentClientId);
            String clientId = (currentClientId != null && !currentClientId.isEmpty())
                ? currentClientId : currentUserEmail;
            String filterColumn = (currentClientId != null && !currentClientId.isEmpty())
                ? "client_id" : "user_email";

            // Pull categories preserving IDs
            pullCategoriesPreservingIds(clientId, filterColumn);
            
            // Pull vendors with duplicate prevention
            pullVendorsWithDeduplication(clientId, filterColumn);
            
            // Pull products with proper foreign keys
            pullProductsPreservingCategoryIds(clientId, filterColumn);
            
         // Pull remaining tables
            String[] remainingTables = {"item_flows", "sales", "sale_items", "daily_expenses", "users"};
            int totalRows = 0;
            for (String table : remainingTables) {
                totalRows += pullTableDataSimple(table, clientId, filterColumn);
            }

            recomputeAllStockQuantitiesLocal(clientId);
            LOGGER.info("Pull complete: all data synced");
            return true;
        } catch (Exception e) {
            LOGGER.severe("pullFromCloud: " + e.getMessage());
            return false;
        }
    }

    private static void pullCategoriesPreservingIds(String clientId, String filterColumn) {
        if (!isLocalConnectionValid()) {
            LOGGER.warning("Cannot pull categories: connection invalid");
            return;
        }
        
        try {
            List<JsonObject> cloudCategories = execute(
                "SELECT id, name, description, user_email, client_id, updated_at FROM categories WHERE " 
                + filterColumn + " = ?", clientId);

            if (cloudCategories.isEmpty()) return;

            int inserted = 0, updated = 0;
            for (JsonObject cat : cloudCategories) {
                if (!cat.has("id") || cat.get("id").isJsonNull()) continue;
                if (!cat.has("name") || cat.get("name").isJsonNull()) continue;

                int cloudId = cat.get("id").getAsInt();
                String name = cat.get("name").getAsString().toUpperCase().trim();
                String description = getStr(cat, "description");

                // Check by ID first
                boolean exists = false;
                try (PreparedStatement ps = localConnection.prepareStatement(
                        "SELECT id FROM categories WHERE id = ?")) {
                    ps.setInt(1, cloudId);
                    ResultSet rs = ps.executeQuery();
                    exists = rs.next();
                }

                if (exists) {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "UPDATE categories SET name = ?, client_id = ?, user_email = ? WHERE id = ?")) {
                        ps.setString(1, name);
                        ps.setString(2, clientId);
                        ps.setString(3, currentUserEmail);
                        ps.setInt(4, cloudId);
                        ps.executeUpdate();
                        updated++;
                    }
                } else {
                    // Insert with exact original ID
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "INSERT OR IGNORE INTO categories (id, name, description, client_id, user_email) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setInt(1, cloudId);
                        ps.setString(2, name);
                        ps.setString(3, description);
                        ps.setString(4, clientId);
                        ps.setString(5, currentUserEmail);
                        ps.executeUpdate();
                        inserted++;
                    }
                }
            }
            LOGGER.info("Categories synced: " + inserted + " inserted, " + updated + " updated");

        } catch (Exception e) {
            LOGGER.warning("pullCategoriesPreservingIds failed: " + e.getMessage());
        }
    }
    public static void initializeCloudMessagesTable() {
        if (!checkConnection()) return;
        
        try {
            // Just verify the table exists - don't try to create it
            execute("SELECT 1 FROM internal_messages LIMIT 1");
            LOGGER.info("Cloud internal_messages table verified");
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("no such table")) {
                LOGGER.severe("CRITICAL: internal_messages table does NOT exist in cloud!");
                LOGGER.severe("Please create it manually in SQLite Cloud console.");
            } else {
                LOGGER.warning("Could not verify cloud internal_messages table: " + e.getMessage());
            }
        }
    }

    private static void pullVendorsWithDeduplication(String clientId, String filterColumn) {
        try {
            List<JsonObject> cloudVendors = execute(
                "SELECT id, name, contact, phone, email, address, user_email, client_id FROM vendors WHERE " 
                + filterColumn + " = ?", clientId);

            if (cloudVendors.isEmpty()) return;

            // Use HashSet for case-insensitive duplicate detection
            Set<String> existingVendorNames = new HashSet<>();
            try (PreparedStatement ps = localConnection.prepareStatement(
                    "SELECT UPPER(name) as name FROM vendors WHERE client_id = ?")) {
                ps.setString(1, clientId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    existingVendorNames.add(rs.getString("name"));
                }
            }

            int inserted = 0;
            for (JsonObject vendor : cloudVendors) {
                String name = getStr(vendor, "name");
                if (name == null || name.trim().isEmpty()) continue;
                
                String upperName = name.toUpperCase().trim();
                
                // CRITICAL: Skip if vendor already exists (case-insensitive)
                if (existingVendorNames.contains(upperName)) {
                    LOGGER.fine("Skipping duplicate vendor: " + name);
                    continue;
                }
                
                int cloudId = vendor.has("id") && !vendor.get("id").isJsonNull() 
                    ? vendor.get("id").getAsInt() : -1;
                
                String contact = getStr(vendor, "contact");
                String phone = getStr(vendor, "phone");
                String email = getStr(vendor, "email");
                String address = getStr(vendor, "address");
                
                // Insert with ID if available, otherwise let auto-increment work
                if (cloudId > 0) {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "INSERT OR IGNORE INTO vendors (id, name, contact, phone, email, address, client_id, user_email) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, cloudId);
                        ps.setString(2, name);
                        ps.setString(3, contact);
                        ps.setString(4, phone);
                        ps.setString(5, email);
                        ps.setString(6, address);
                        ps.setString(7, clientId);
                        ps.setString(8, currentUserEmail);
                        int result = ps.executeUpdate();
                        if (result > 0) {
                            inserted++;
                            existingVendorNames.add(upperName);
                            LOGGER.info("Inserted vendor: " + name + " (ID: " + cloudId + ")");
                        }
                    }
                } else {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "INSERT INTO vendors (name, contact, phone, email, address, client_id, user_email) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, name);
                        ps.setString(2, contact);
                        ps.setString(3, phone);
                        ps.setString(4, email);
                        ps.setString(5, address);
                        ps.setString(6, clientId);
                        ps.setString(7, currentUserEmail);
                        int result = ps.executeUpdate();
                        if (result > 0) {
                            inserted++;
                            existingVendorNames.add(upperName);
                            LOGGER.info("Inserted vendor: " + name);
                        }
                    }
                }
            }
            LOGGER.info("Vendors synced: " + inserted + " inserted (duplicates skipped)");

        } catch (Exception e) {
            LOGGER.warning("pullVendorsWithDeduplication failed: " + e.getMessage());
        }
    }

    private static void pullProductsPreservingCategoryIds(String clientId, String filterColumn) {
        try {
            List<JsonObject> cloudProducts = execute(
                "SELECT * FROM products WHERE " + filterColumn + " = ?", clientId);

            if (cloudProducts.isEmpty()) return;

            int inserted = 0, updated = 0;
            for (JsonObject product : cloudProducts) {
                if (!product.has("id") || product.get("id").isJsonNull()) continue;

                int cloudProductId = product.get("id").getAsInt();
                String name = getStr(product, "name");
                if (name == null || name.isEmpty()) continue;

                // Handle category_id that could be string or integer
                Integer catId = null;
                if (product.has("category_id") && !product.get("category_id").isJsonNull()) {
                    JsonElement catElement = product.get("category_id");
                    if (catElement.isJsonPrimitive()) {
                        JsonPrimitive prim = catElement.getAsJsonPrimitive();
                        if (prim.isNumber()) {
                            catId = prim.getAsInt();
                        } else if (prim.isString()) {
                            // String category_id - skip foreign key, set to null
                            catId = null;
                        }
                    }
                }

                // Ensure category exists (only for numeric IDs)
             // Ensure category exists locally before inserting product
                if (catId != null && catId > 0) {
                    boolean catExists = false;
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "SELECT id FROM categories WHERE id = ?")) {
                        ps.setInt(1, catId);
                        catExists = ps.executeQuery().next();
                    }

                    if (!catExists) {
                        // ── Always fetch real name from cloud — NEVER use placeholder ──
                        String catName = null;

                        try {
                            // Fetch by ID AND clientId so we only get this user's category
                            List<JsonObject> catResult = execute(
                                "SELECT name FROM categories WHERE id = ? AND client_id = ?",
                                catId, currentClientId);

                            if (!catResult.isEmpty() && catResult.get(0).has("name")
                                    && !catResult.get(0).get("name").isJsonNull()) {
                                catName = catResult.get(0).get("name").getAsString().toUpperCase().trim();
                                LOGGER.info("Fetched real category name from cloud: " +
                                            catName + " (id=" + catId + ")");
                            }
                        } catch (Exception e) {
                            LOGGER.warning("Could not fetch category name from cloud for id=" + catId);
                        }

                        if (catName == null || catName.isEmpty()) {
                            // Cloud had no category with this id for this client
                            // This means the category was deleted — set product category to null
                            // rather than creating a fake CATEGORY_23 entry
                            LOGGER.warning("Category id=" + catId +
                                           " not found in cloud for client=" + currentClientId +
                                           " — product will have no category");
                            catId = null; // Don't insert a fake category
                        } else {
                            // Insert with original ID so all product foreign keys stay intact
                            try (PreparedStatement ps = localConnection.prepareStatement(
                                    "INSERT OR IGNORE INTO categories " +
                                    "(id, name, client_id, user_email) VALUES (?, ?, ?, ?)")) {
                                ps.setInt(1, catId);
                                ps.setString(2, catName);
                                ps.setString(3, clientId);
                                ps.setString(4, currentUserEmail);
                                ps.executeUpdate();
                                LOGGER.info("Restored category: " + catName + " (id=" + catId + ")");
                            }
                        }
                    }
                }

                // Check if product exists by ID
                boolean productExists = false;
                try (PreparedStatement ps = localConnection.prepareStatement(
                        "SELECT id FROM products WHERE id = ?")) {
                    ps.setInt(1, cloudProductId);
                    ResultSet rs = ps.executeQuery();
                    productExists = rs.next();
                }

                if (!productExists) {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "INSERT OR IGNORE INTO products " +
                            "(id, name, category_id, vendor, cost_price, selling_price, " +
                            "stock_quantity, min_stock_level, expire_date, created_date, client_id, user_email) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, cloudProductId);
                        ps.setString(2, name);
                        if (catId != null) ps.setInt(3, catId); else ps.setNull(3, java.sql.Types.INTEGER);
                        ps.setString(4, getStr(product, "vendor"));
                        ps.setBigDecimal(5, getDec(product, "cost_price"));
                        ps.setBigDecimal(6, getDec(product, "selling_price"));
                        ps.setInt(7, getInt(product, "stock_quantity") != null ? getInt(product, "stock_quantity") : 0);
                        ps.setInt(8, getInt(product, "min_stock_level") != null ? getInt(product, "min_stock_level") : 0);
                        ps.setString(9, getStr(product, "expire_date"));
                        ps.setString(10, getStr(product, "created_date"));
                        ps.setString(11, clientId);
                        ps.setString(12, currentUserEmail);
                        inserted += ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = localConnection.prepareStatement(
                            "UPDATE products SET name=?, category_id=?, vendor=?, cost_price=?, selling_price=?, min_stock_level=?, expire_date=? WHERE id=?")) {
                        ps.setString(1, name);
                        if (catId != null) ps.setInt(2, catId); else ps.setNull(2, java.sql.Types.INTEGER);
                        ps.setString(3, getStr(product, "vendor"));
                        ps.setBigDecimal(4, getDec(product, "cost_price"));
                        ps.setBigDecimal(5, getDec(product, "selling_price"));
                        ps.setInt(6, getInt(product, "min_stock_level") != null ? getInt(product, "min_stock_level") : 0);
                        ps.setString(7, getStr(product, "expire_date"));
                        ps.setInt(8, cloudProductId);
                        updated += ps.executeUpdate();
                    }
                }
            }
            LOGGER.info("Products synced: " + inserted + " inserted, " + updated + " updated");

        } catch (Exception e) {
            LOGGER.warning("pullProductsPreservingCategoryIds failed: " + e.getMessage());
        }
    }

    private static int pullTableDataSimple(String tableName, String clientId, String filterColumn) {
    try {
        List<JsonObject> rows = execute("SELECT * FROM " + tableName +
            " WHERE " + filterColumn + " = ?", clientId);
        if (rows.isEmpty()) return 0;

        int count = 0;
        Connection conn = Database.getConnection();   // FIXED BUG 16
        for (JsonObject row : rows) {
            if (!row.has("client_id") || row.get("client_id").isJsonNull())
                row.addProperty("client_id", clientId);
            if (!row.has("user_email") || row.get("user_email").isJsonNull())
                row.addProperty("user_email", currentUserEmail);

            // Skip password field for users table — never overwrite local passwords
            if ("users".equals(tableName) && row.has("password"))
                row.remove("password");

            if (insertRowSimple(tableName, row)) count++;
        }
        if (count > 0) LOGGER.info("  Pulled " + count + " rows from " + tableName);
        return count;
    } catch (Exception e) {
        LOGGER.warning("pullTableDataSimple failed for " + tableName + ": " + e.getMessage());
        return 0;
    }
}

    // Helper methods
    private static String getStr(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : null;
    }
    
    private static Integer getInt(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsInt() : null;
    }
    
    private static java.math.BigDecimal getDec(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsBigDecimal() : java.math.BigDecimal.ZERO;
    }

    private static boolean insertRowSimple(String tableName, JsonObject row) {
    try {
        Connection conn = Database.getConnection();   // FIXED BUG 16, 21
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> values = new ArrayList<>();

        if (!row.has("user_email") && currentUserEmail != null)
            row.addProperty("user_email", currentUserEmail);

        for (String key : row.keySet()) {
            if (!row.get(key).isJsonNull()) {
                if (columns.length() > 0) { columns.append(", "); placeholders.append(", "); }
                columns.append(key);
                placeholders.append("?");
                values.add(getValueAsObject(row.get(key)));
            }
        }

        // FIXED BUG 17: Use INSERT OR IGNORE, NOT INSERT OR REPLACE.
        // INSERT OR REPLACE deletes the old row and re-inserts, changing the rowid
        // and breaking all foreign key references (sale_items.sale_id etc.)
        String sql = "INSERT OR IGNORE INTO " + tableName +
                     " (" + columns + ") VALUES (" + placeholders + ")";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) pstmt.setObject(i + 1, values.get(i));
            pstmt.executeUpdate();
        }
        return true;
    } catch (SQLException e) {
        LOGGER.fine("Insert skip (" + tableName + "): " + e.getMessage());
        return false;
    }
}

    private static Object getValueAsObject(JsonElement element) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsNumber();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isString()) return p.getAsString();
        }
        return element.toString();
    }

    private static void recomputeAllStockQuantitiesLocal(String clientId) {
    try {
        Connection conn = Database.getConnection();   // FIXED BUG 16, 21
        List<Integer> productIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM products WHERE client_id = ?")) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) productIds.add(rs.getInt(1));
        }

        String flowSql =
            "SELECT COALESCE(SUM(CASE " +
            "WHEN UPPER(TRIM(action)) IN ('NEW ITEM','RECEIVED','RECEIVE','RETURN','RETURNED','REFUNDED') THEN quantity " +
            "WHEN UPPER(TRIM(action)) IN ('SOLD','SALE','SELL') THEN -quantity " +
            "ELSE 0 END), 0) as net " +
            "FROM item_flows WHERE product_id=? AND (client_id=? OR client_id IS NULL)";

        try (PreparedStatement selectPs = conn.prepareStatement(flowSql);
             PreparedStatement updatePs = conn.prepareStatement(
                     "UPDATE products SET stock_quantity=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            for (int productId : productIds) {
                selectPs.setInt(1, productId);
                selectPs.setString(2, clientId);
                ResultSet rs = selectPs.executeQuery();
                int stock = rs.next() ? Math.max(0, rs.getInt("net")) : 0;
                updatePs.setInt(1, stock);
                updatePs.setInt(2, productId);
                updatePs.addBatch();
            }
            updatePs.executeBatch();
        }
        LOGGER.info("Recomputed stock for " + productIds.size() + " products");
    } catch (Exception e) {
        LOGGER.warning("recomputeAllStockQuantitiesLocal: " + e.getMessage());
    }
}
    private static void migrateSyncQueueTable() {
        if (localConnection == null) return;
        
        try (Statement stmt = localConnection.createStatement()) {
            // Check if table exists first
            ResultSet tables = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + SYNC_QUEUE_TABLE + "'");
            if (!tables.next()) {
                LOGGER.fine("Sync queue table doesn't exist yet, skipping migration");
                return;
            }
            
            // Check existing columns
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + SYNC_QUEUE_TABLE + ")");
            boolean hasClientId = false;
            boolean hasUserEmail = false;
            boolean hasTableName = false;
            boolean hasRecordId = false;
            boolean hasOperation = false;
            boolean hasData = false;
            boolean hasStatus = false;
            boolean hasRetryCount = false;
            
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("client_id".equals(colName)) hasClientId = true;
                if ("user_email".equals(colName)) hasUserEmail = true;
                if ("table_name".equals(colName)) hasTableName = true;
                if ("record_id".equals(colName)) hasRecordId = true;
                if ("operation".equals(colName)) hasOperation = true;
                if ("data".equals(colName)) hasData = true;
                if ("status".equals(colName)) hasStatus = true;
                if ("retry_count".equals(colName)) hasRetryCount = true;
            }
            
            // Add missing columns
            if (!hasClientId) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN client_id TEXT");
                LOGGER.info("Added client_id column to sync_queue");
            }
            if (!hasUserEmail) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN user_email TEXT");
                LOGGER.info("Added user_email column to sync_queue");
            }
            if (!hasTableName) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN table_name TEXT");
                LOGGER.info("Added table_name column to sync_queue");
            }
            if (!hasRecordId) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN record_id INTEGER");
                LOGGER.info("Added record_id column to sync_queue");
            }
            if (!hasOperation) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN operation TEXT");
                LOGGER.info("Added operation column to sync_queue");
            }
            if (!hasData) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN data TEXT");
                LOGGER.info("Added data column to sync_queue");
            }
            if (!hasStatus) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN status TEXT DEFAULT 'pending'");
                LOGGER.info("Added status column to sync_queue");
            }
            if (!hasRetryCount) {
                stmt.execute("ALTER TABLE " + SYNC_QUEUE_TABLE + " ADD COLUMN retry_count INTEGER DEFAULT 0");
                LOGGER.info("Added retry_count column to sync_queue");
            }
            
            // Add indexes
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON " + SYNC_QUEUE_TABLE + "(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_client ON " + SYNC_QUEUE_TABLE + "(client_id)");
            } catch (SQLException e) {
                LOGGER.fine("Index may already exist: " + e.getMessage());
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to migrate sync_queue table: " + e.getMessage());
        }
    }
    public static void ensureSyncQueueSchema() {
        if (localConnection == null) {
            LOGGER.warning("Cannot ensure sync queue schema: local connection is null");
            return;
        }
        
        try {
            // Force migration to run even if table exists
            migrateSyncQueueTable();
            LOGGER.info("Sync queue schema verified");
        } catch (Exception e) {
            LOGGER.warning("Failed to ensure sync queue schema: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FULL SYNC
    // ════════════════════════════════════════════════════════════════════════

    public static boolean fullSync() {
    if (currentUserEmail == null || currentUserEmail.isEmpty()) {
        LOGGER.warning("fullSync: user not set");
        return false;
    }
    if (currentClientId == null || currentClientId.isEmpty()) {
        currentClientId = getOrCreateClientId();
    }
    if (localConnection == null) {
        LOGGER.warning("fullSync: local DB not initialized");
        return false;
    }
    try {
        LOGGER.info("Starting full sync for client: " + currentClientId);
        String clientId = currentClientId;

        boolean pullSuccess = pullFromCloud();
        if (!pullSuccess) {
            LOGGER.warning("Pull failed, but continuing with push");
        }

        int pushed = 0;
        // CRITICAL: include ALL tables that contain transactional data
        String[] tables = {"categories", "vendors", "products", "item_flows",
                           "sales", "sale_items", "daily_expenses"};
        for (String table : tables) {
            pushed += syncTableSimple(table, clientId);
        }
        LOGGER.info("Push: " + pushed + " rows uploaded");

        createLocalBackup();
        return true;
    } catch (Exception e) {
        LOGGER.severe("fullSync failed: " + e.getMessage());
        return false;
    }
}

    private static int syncTableSimple(String tableName, String clientId) {
    try {
        List<Map<String, Object>> localRows = fetchLocalTableData(tableName, clientId);
        if (localRows.isEmpty()) return 0;
        int count = 0;
        for (Map<String, Object> row : localRows) {
            row.put("user_email", currentUserEmail);
            row.put("client_id", clientId);
            try {
                if ("vendors".equals(tableName)) {
                    List<JsonObject> existing = execute(
                        "SELECT id FROM vendors WHERE UPPER(name) = UPPER(?) AND client_id = ? AND user_email = ?",
                        row.get("name"), clientId, currentUserEmail);
                    if (!existing.isEmpty()) continue;
                }
                if ("categories".equals(tableName)) {
                    List<JsonObject> existing = execute(
                        "SELECT id FROM categories WHERE UPPER(name) = UPPER(?) AND client_id = ? AND user_email = ?",
                        row.get("name"), clientId, currentUserEmail);
                    if (!existing.isEmpty()) continue;
                }
                if ("products".equals(tableName)) {
                    executeUpdate("INSERT OR REPLACE INTO products (id, user_email, client_id, name, cost_price, selling_price, stock_quantity, min_stock_level, category_id, vendor, expire_date, created_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET name=excluded.name, cost_price=excluded.cost_price, selling_price=excluded.selling_price, stock_quantity=excluded.stock_quantity, min_stock_level=excluded.min_stock_level, category_id=excluded.category_id, vendor=excluded.vendor, expire_date=excluded.expire_date, updated_at=CURRENT_TIMESTAMP WHERE user_email = ? AND client_id = ?",
                        row.get("id"), currentUserEmail, clientId, row.get("name"), row.get("cost_price"), row.get("selling_price"), row.get("stock_quantity"), row.get("min_stock_level"), row.get("category_id"), row.get("vendor"), row.get("expire_date"), row.get("created_date"),
                        currentUserEmail, clientId);
                    count++;
                    continue;
                }
                // For sales, sale_items, item_flows: use UPSERT so existing cloud records are UPDATED
                String upsertSql = buildUpsertStatement(tableName, jsonObjectFromMap(row));
                executeUpdate(upsertSql);
                count++;
            } catch (IOException e) {
                LOGGER.fine("Push skip (" + tableName + "): " + e.getMessage());
            }
        }
        if (count > 0) LOGGER.info("  Pushed " + count + " rows from " + tableName);
        return count;
    } catch (Exception e) {
        LOGGER.warning("syncTableSimple failed for " + tableName + ": " + e.getMessage());
        return 0;
    }
}

    private static List<Map<String, Object>> fetchLocalTableData(String tableName, String clientId) throws SQLException {
    List<Map<String, Object>> data = new ArrayList<>();
    if (localConnection == null) return data;
    
    // Query by client_id OR by user_email (covers all data regardless of which column was set)
    String sql;
    if ("users".equals(tableName)) {
        // Never push passwords upstream — handled separately
        sql = "SELECT id, username, privileges, email, business_name, client_id, user_email, updated_at " +
              "FROM " + tableName + " WHERE client_id = ? OR user_email = ?";
    } else {
        sql = "SELECT * FROM " + tableName + " WHERE client_id = ? OR user_email = ?";
    }
    
    try (PreparedStatement ps = localConnection.prepareStatement(sql)) {
        ps.setString(1, clientId);
        ps.setString(2, currentUserEmail);
        ResultSet rs = ps.executeQuery();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            data.add(row);
        }
    }
    return data;
}

    private static String buildInsertStatement(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<>(row.keySet());
        StringBuilder sb = new StringBuilder("INSERT OR REPLACE INTO ").append(table).append(" (");
        sb.append(String.join(", ", cols)).append(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            Object v = row.get(cols.get(i));
            if (v == null) sb.append("NULL");
            else if (v instanceof Number) sb.append(v);
            else sb.append("'").append(escape(v.toString())).append("'");
        }
        return sb.append(")").toString();
    }

    private static String buildSql(String sql, Object[] args) {
        if (args == null || args.length == 0) return sql;
        StringBuilder sb = new StringBuilder();
        int ai = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && ai < args.length) {
                Object arg = args[ai++];
                if (arg == null) sb.append("NULL");
                else if (arg instanceof Number) sb.append(arg);
                else if (arg instanceof Boolean) sb.append((Boolean) arg ? 1 : 0);
                else sb.append("'").append(escape(arg.toString())).append("'");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String buildUpsertStatement(String table, JsonObject data) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        StringBuilder updates = new StringBuilder();
        
        for (String key : data.keySet()) {
            if (cols.length() > 0) {
                cols.append(",");
                vals.append(",");
                updates.append(",");
            }
            cols.append('"').append(key).append('"');
            
            JsonElement element = data.get(key);
            String value;
            if (element.isJsonNull()) {
                value = "NULL";
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive prim = element.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    value = prim.getAsNumber().toString();
                } else if (prim.isBoolean()) {
                    value = prim.getAsBoolean() ? "1" : "0";
                } else {
                    value = "'" + escape(prim.getAsString()) + "'";
                }
            } else {
                value = "'" + escape(element.toString()) + "'";
            }
            
            vals.append(value);
            updates.append('"').append(key).append('"').append("=").append(value);
        }
        
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ") " +
               "ON CONFLICT(id) DO UPDATE SET " + updates;
    }
    private static JsonObject jsonObjectFromMap(Map<String, Object> map) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
        Object v = entry.getValue();
        if (v == null) obj.add(entry.getKey(), com.google.gson.JsonNull.INSTANCE);
        else if (v instanceof Number) obj.addProperty(entry.getKey(), ((Number) v).doubleValue());
        else if (v instanceof Boolean) obj.addProperty(entry.getKey(), (Boolean) v);
        else obj.addProperty(entry.getKey(), v.toString());
    }
    return obj;
}
    

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

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    public static String createLocalBackup() {
        if (currentDatabasePath == null) return null;
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safe = currentUserEmail != null ? currentUserEmail.replaceAll("[^a-zA-Z0-9]", "_") : "unknown";
            String path = BACKUP_DIR + "/backup_" + safe + "_" + ts + ".db";
            try (FileInputStream in = new FileInputStream(currentDatabasePath);
                 FileOutputStream out = new FileOutputStream(path)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return path;
        } catch (IOException e) { return null; }
    }

    private static void ensureCloudSchema() {
        try {
            execute("SELECT 1 FROM users LIMIT 1");
            migrateCloudUsersTable(); // Add this line
        } catch (IOException e) {
            initializeCloudTables();
        }
    }
    
    public static void resetSession() {
        currentUserEmail = null;
        currentUsername = null;
        currentClientId = null;
        stopBackgroundSync();
    }
}
