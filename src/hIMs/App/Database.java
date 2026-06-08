package hIMs.App;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {
    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());
    private static String DB_FILE = null;
    private static String DB_URL = "jdbc:sqlite:" + DB_FILE;
    private static final Object connectionLock = new Object();
    private static Connection persistentConnection = null;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    // Flags for online/offline mode
    private static boolean isOnline = false;
    private static boolean forceLocalMode = false;
    
    // User-specific database fields
    private static String currentUserEmail = null;
    private static String currentUserDbFile = null;
    private static String currentUsername = null;
    private static String currentClientId = null;
    
    // Cache for auto-created items
    private static final Set<String> autoCreatedVendors = new HashSet<>();
    private static final Set<String> autoCreatedCategories = new HashSet<>();

    // ================ CLIENT_ID FILTER HELPERS ================
    
    private static String whereClient() {
    if (currentClientId != null && !currentClientId.isEmpty()) {
        return "client_id = ?";
    } else {
        return "user_email = ?";
    }
}

private static void setClientParam(PreparedStatement stmt, int idx) throws SQLException {
    if (currentClientId != null && !currentClientId.isEmpty()) {
        stmt.setString(idx, currentClientId);
    } else if (currentUserEmail != null && !currentUserEmail.isEmpty()) {
        stmt.setString(idx, currentUserEmail);
    } else {
        stmt.setString(idx, "");
    }
}

    // ================ DATABASE FILE MANAGEMENT ================
    
    public static void setDatabaseFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        DB_FILE = fileName;
        DB_URL = "jdbc:sqlite:" + DB_FILE;
        isOnline = false;
        LOGGER.info("Database file set to: " + DB_FILE);
    }
    // twillo recovery code 425C3R24MPKSENAQA2BUULUW

    public static String getDatabaseFile() { return DB_FILE; }

    public static void setCurrentUser(String email, String username, String clientId) {
        
        // ── Fix 3: Detect user switch and close old connection FIRST ──
        if (currentUserEmail != null && !currentUserEmail.equals(email)) {
            LOGGER.info("User switch detected: " + currentUserEmail + " -> " + email);
            synchronized (connectionLock) {
                try {
                    if (persistentConnection != null && !persistentConnection.isClosed()) {
                        persistentConnection.close();
                        LOGGER.info("Closed old connection for: " + currentUserEmail);
                    }
                } catch (SQLException e) {
                    LOGGER.warning("Could not close old connection on user switch: " + e.getMessage());
                }
                persistentConnection = null;
            }
            // Reset all user-scoped state
            autoCreatedVendors.clear();
            autoCreatedCategories.clear();
            cloudMigrationDone = false;
            currentUserDbFile = null;
            DB_FILE = null;
            DB_URL = null;
            LOGGER.info("User-scoped state reset for switch to: " + email);
        }

        // ── Recover clientId if null ──
        if (clientId == null || clientId.isEmpty() || "null".equals(clientId)) {
            LOGGER.warning("setCurrentUser called with null/empty clientId, will try to recover");
            String savedClientId = SQLiteCloudClient.loadClientIdFromFile();
            if (savedClientId != null && !savedClientId.isEmpty()) {
                clientId = savedClientId;
                LOGGER.info("Recovered client_id from file: " + clientId);
            }
        }

        // ── Set session state ──
        currentUserEmail = email;
        currentUsername = username;
        currentClientId = clientId;

        // ── CRITICAL: Generate user-specific DB name from email ──
        // Each email gets exactly one DB file — never shared unless linked user
        String dbName = email.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_") + ".db";
        currentUserDbFile = dbName;
        DB_FILE = dbName;
        DB_URL = "jdbc:sqlite:" + DB_FILE;
        LOGGER.info("DB file set to: " + DB_FILE + " for user: " + email);

        // ── Close any stale connection before opening new DB ──
        // (handles same-user re-login without the switch detection above)
        synchronized (connectionLock) {
            try {
                if (persistentConnection != null && !persistentConnection.isClosed()) {
                    persistentConnection.close();
                }
            } catch (SQLException e) {
                LOGGER.warning("Could not close stale connection: " + e.getMessage());
            }
            persistentConnection = null;
        }

        // ── Persist clientId into the new user's DB ──
        try (Connection conn = getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO app_settings (key, value, updated_at) " +
                 "VALUES ('client_id', ?, CURRENT_TIMESTAMP)")) {
            ps.setString(1, clientId);
            ps.executeUpdate();
            LOGGER.info("Persisted client_id=" + clientId + " to app_settings in: " + DB_FILE);
        } catch (SQLException e) {
            LOGGER.warning("Could not persist client_id to app_settings: " + e.getMessage());
        }

        // ── Save clientId to file (email-scoped) ──
        SQLiteCloudClient.saveClientIdToFile(clientId);

        // ── Propagate to SQLiteCloudClient ──
        SQLiteCloudClient.setCurrentUser(email, username, clientId);

        LOGGER.info("Switched to user database: " + DB_FILE + " | clientId: " + clientId);
    }
   


    public static void setCurrentUser(String email, String username) {
    String clientId = null;

    // Compute user-specific DB name FIRST so local lookup reads the right file
    String userDbName = email.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_") + ".db";

    // 1. Try cloud
    if (!forceLocalMode) {
        clientId = lookupClientIdCloud(email);
    }
    // 2. Try user-specific local DB file
    if (clientId == null || clientId.isEmpty()) {
        clientId = lookupClientIdLocalFromFile(email, userDbName);
    }
    // 3. Try current DB_FILE (may already be set correctly)
    if (clientId == null || clientId.isEmpty()) {
        clientId = lookupClientIdLocalFromFile(email, DB_FILE);
    }
    // 4. NEVER generate a new UUID here — that causes the wrong client_id
    //    Only log a warning; caller must handle missing client_id
    if (clientId == null || clientId.isEmpty()) {
        LOGGER.severe("CRITICAL: Could not resolve client_id for " + email +
                      " — local DB may be missing. App will have no data.");
        // Use a deterministic ID based on email as last resort, NOT a random UUID
        clientId = email.toLowerCase().replaceAll("[^a-zA-Z0-9]", "").substring(0, 
                   Math.min(16, email.replaceAll("[^a-zA-Z0-9]", "").length()));
        LOGGER.warning("Using deterministic fallback client_id: " + clientId);
    }
    setCurrentUser(email, username, clientId);
}

    private static String lookupClientId(String email) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT client_id FROM users WHERE email = ?", email);
            if (!result.isEmpty() && result.get(0).has("client_id")
                    && !result.get(0).get("client_id").isJsonNull()) {
                return result.get(0).get("client_id").getAsString();
            }
        } catch (IOException e) {
            LOGGER.warning("lookupClientId failed: " + e.getMessage());
        }
        return null;
    }
    
    public static String getCurrentUserEmail() { return currentUserEmail; }
    public static String getCurrentUserDbFile() { return currentUserDbFile; }
    public static String getCurrentClientId() { return currentClientId; }
    public static String getCurrentUsername() { return currentUsername; }

    // ================ SQLITE CLOUD METHODS ================

    private static boolean wasOffline = false;

    
    private static String lookupClientIdCloud(String email) {
    try {
        // Query both email columns — accounts use 'email', linked users use 'user_email'
        List<JsonObject> result = SQLiteCloudClient.execute(
            "SELECT client_id FROM users WHERE email = ? OR user_email = ? LIMIT 1",
            email, email);
        if (!result.isEmpty()) {
            JsonObject row = result.get(0);
            if (row.has("client_id") && !row.get("client_id").isJsonNull()) {
                String cid = row.get("client_id").getAsString();
                if (cid != null && !cid.trim().isEmpty() && !cid.equals("null")) {
                    LOGGER.info("Cloud resolved client_id=" + cid + " for " + email);
                    return cid;
                }
            }
        }
    } catch (IOException e) {
        LOGGER.warning("lookupClientIdCloud failed for " + email + ": " + e.getMessage());
    }
    return null;
}
    private static String lookupClientIdLocal(String email) {
    return lookupClientIdLocalFromFile(email, DB_FILE);
}

private static String lookupClientIdLocalFromFile(String email, String dbFile) {
    if (dbFile == null || dbFile.isEmpty()) return null;
    try {
        Class.forName("org.sqlite.JDBC");
        // First try: users table
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT client_id FROM users WHERE (email = ? OR user_email = ?) " +
                 "AND client_id IS NOT NULL AND client_id != '' AND client_id != 'null' LIMIT 1")) {
            ps.setString(1, email);
            ps.setString(2, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String cid = rs.getString("client_id");
                if (cid != null && !cid.trim().isEmpty() && !cid.equals("null")) {
                    LOGGER.info("Local resolved client_id=" + cid + " from users in " + dbFile);
                    return cid;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("users table lookup failed in " + dbFile + ": " + e.getMessage());
        }
        // Second try: app_settings table (most reliable — stored on every login)
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT value FROM app_settings WHERE key = 'client_id' LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String cid = rs.getString("value");
                if (cid != null && !cid.trim().isEmpty() && !cid.equals("null")) {
                    LOGGER.info("Local resolved client_id=" + cid + " from app_settings in " + dbFile);
                    return cid;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("app_settings lookup failed in " + dbFile + ": " + e.getMessage());
        }
    } catch (Exception e) {
        LOGGER.warning("lookupClientIdLocalFromFile failed for " + dbFile + ": " + e.getMessage());
    }
    return null;
}
    

    public static void initializeCloudTables() {
        if (!isOnline) {
            LOGGER.info("Cannot initialize cloud tables: offline");
            return;
        }
        SQLiteCloudClient.initializeCloudTables();
    }

    private static boolean cloudMigrationDone = false;

    private static void migrateCloudSchema() {
        if (!isOnline || cloudMigrationDone) return;
        cloudMigrationDone = true;
        LOGGER.info("Checking cloud schema migrations...");

        String[] migrations = {
            // users table
            "ALTER TABLE users ADD COLUMN created_date TEXT DEFAULT (datetime('now'))",
            "ALTER TABLE users ADD COLUMN email_verified INTEGER DEFAULT 0",
            "ALTER TABLE users ADD COLUMN user_uid TEXT",
            "ALTER TABLE users ADD COLUMN user_email TEXT",

            // products table
            "ALTER TABLE products ADD COLUMN created_date TEXT DEFAULT (date('now','localtime'))",
            "ALTER TABLE products ADD COLUMN expire_date TEXT",
            "ALTER TABLE products ADD COLUMN vendor TEXT",

            // daily_sales_reports table
            "ALTER TABLE daily_sales_reports ADD COLUMN client_id TEXT",

            // sync_queue table (all missing columns)
            "ALTER TABLE sync_queue ADD COLUMN user_email TEXT",
            "ALTER TABLE sync_queue ADD COLUMN client_id TEXT",
            "ALTER TABLE sync_queue ADD COLUMN data TEXT",
            "ALTER TABLE sync_queue ADD COLUMN retry_count INTEGER DEFAULT 0",
            "ALTER TABLE sync_queue ADD COLUMN error_message TEXT",

            // audit_log table
            "ALTER TABLE audit_log ADD COLUMN user_email TEXT",

            // inventory_log table
            "ALTER TABLE inventory_log ADD COLUMN user_email TEXT",

            // receipts table
            "ALTER TABLE receipts ADD COLUMN user_email TEXT",

            // categories table
            "ALTER TABLE categories ADD COLUMN user_email TEXT"
        };

        for (String migration : migrations) {
            try {
                SQLiteCloudClient.execute(migration);
                LOGGER.info("Migration successful: " + migration);
            } catch (IOException e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("duplicate column") ||
                    errorMsg.contains("already exists"))) {
                    LOGGER.fine("Column already exists (OK): " + migration);
                } else if (errorMsg != null && errorMsg.contains("no such table")) {
                    LOGGER.fine("Table not yet created (OK): " + migration);
                } else {
                    LOGGER.warning("Migration failed: " + migration + " - " + errorMsg);
                }
            } catch (Exception e) {
                LOGGER.fine("Migration note: " + migration + " - " + e.getMessage());
            }
        }
    }
    
    public static List<TransactionDetail> getDailyTransactionsList(String date) throws SQLException {
        List<TransactionDetail> transactions = new ArrayList<>();
        String sql = "SELECT p.name AS item_name, si.quantity, si.unit_price, p.cost_price AS buying_price " +
                     "FROM sale_items si JOIN sales s ON si.sale_id = s.id JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) = ? AND s." + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            setClientParam(stmt, 2);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(new TransactionDetail(
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("buying_price")
                    ));
                }
            }
        }
        return transactions;
    }

    public static class TransactionDetail {
        public String itemName;
        public int quantity;
        public BigDecimal unitPrice;
        public BigDecimal buyingPrice;

        public TransactionDetail(String itemName, int quantity, BigDecimal unitPrice, BigDecimal buyingPrice) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.buyingPrice = buyingPrice;
        }
    }

    public static void pullUserDataFromCloud(String email) throws SQLException {
        if (!isOnline) {
            LOGGER.warning("Cannot pull: offline");
            return;
        }
        LOGGER.info("Pulling data for clientId: " + currentClientId);
        boolean pulled = SQLiteCloudClient.pullFromCloud();
        if (pulled) LOGGER.info("✅ Cloud data pulled for: " + email);
        else throw new SQLException("Pull from cloud returned false");
    }

    public static void initializeUserDatabase(String email, String username) throws SQLException {
        // Always initialize local DB first — app must work offline
        try {
            Class.forName("org.sqlite.JDBC");

            // Close any stale connection from a previous session
            synchronized (connectionLock) {
                if (persistentConnection != null) {
                    try { persistentConnection.close(); } catch (SQLException ignored) {}
                    persistentConnection = null;
                }
            }

            Connection localConn = getLocalConnection();
            ensureSchema(localConn);
            checkAndFixSalesTableSchema(localConn);
            ensureAppSettingsTable(localConn);

            // CRITICAL: Persist client_id into app_settings so offline login can find it
            if (currentClientId != null && !currentClientId.isEmpty()) {
                try (PreparedStatement ps = localConn.prepareStatement(
                        "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES ('client_id', ?, CURRENT_TIMESTAMP)")) {
                    ps.setString(1, currentClientId);
                    ps.executeUpdate();
                    LOGGER.info("Persisted client_id=" + currentClientId + " to app_settings");
                } catch (SQLException e) {
                    LOGGER.warning("Could not persist client_id to app_settings: " + e.getMessage());
                }
            }

            SQLiteCloudClient.initLocalConnection(DB_FILE);
            LOGGER.info("Local database ready for: " + email);
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        // SYNC: pull from cloud FIRST (blocking, on current thread)
        if (SQLiteCloudClient.checkConnection()) {
            try {
                LOGGER.info("Online — pulling all cloud data before UI starts...");
                boolean pulled = SQLiteCloudClient.pullFromCloud();
                if (pulled) {
                    LOGGER.info("✅ Initial cloud pull complete for: " + email);
                }
                restoreFromCloudVerifyTables();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Initial cloud pull failed — using local data: " + e.getMessage(), e);
            }
        } else {
            LOGGER.info("Offline — using existing local database for: " + email);
        }

        // After initial pull, start background sync for ongoing changes
        new Thread(() -> {
            try {
                if (!isOnline()) return;
                LOGGER.info("Background sync started for: " + email);
                SQLiteCloudClient.fullSync();
                rebuildAllCategoriesFromProducts();
                rebuildAllVendorsFromProducts();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Background sync failed: " + e.getMessage(), e);
            }
        }, "bg-sync-" + email).start();

        LOGGER.info("User database ready for: " + email);
    }
    public static boolean checkTursoConnection() {
        try {
            return SQLiteCloudClient.checkConnection();
        } catch (Exception e) {
            LOGGER.fine("checkTursoConnection failed: " + e.getMessage());
            return false;
        }
    }
    public static void setOnline(boolean online) {
        isOnline = online;
        if (!online) forceLocalMode = true;
    }

    public static void initializeLocalOnly(String email, String username) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = getLocalConnection();
            ensureSchema(conn);
            ensureAppSettingsTable(conn);
            SQLiteCloudClient.initLocalConnection(DB_FILE);
            LOGGER.info("Local-only database initialized for: " + email);
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }

    public static void pullFromCloud() throws SQLException {
        if (!checkTursoConnection()) {
            LOGGER.warning("Cannot pull: offline");
            return;
        }
        boolean ok = SQLiteCloudClient.pullFromCloud();
        if (!ok) throw new SQLException("pullFromCloud returned false");
    }

    public static void syncAllToCloud() throws SQLException { syncToCloud(); }

    public static void syncToCloud() throws SQLException {
        if (!isOnline) {
            LOGGER.info("Cannot sync: offline");
            return;
        }
        try {
            boolean ok = SQLiteCloudClient.fullSync();
            if (!ok) throw new SQLException("fullSync returned false");
        } catch (NoSuchMethodError e) {
            LOGGER.warning("fullSync not available — skipping cloud sync");
        }
    }
    /**
     * Call this from your background sync timer (every 30-60 seconds).
     * Flushes any operations queued while offline.
     */
    public static void processPendingSyncQueue() {
        if (!isOnline()) return;
        new Thread(() -> {
            try {
                // Count pending items before sync
                int pendingCount = 0;
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT COUNT(*) FROM sync_queue WHERE status = 'pending' AND user_email = ?")) {
                    ps.setString(1, currentUserEmail);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) pendingCount = rs.getInt(1);
                } catch (SQLException e) {
                    LOGGER.warning("Could not count pending queue: " + e.getMessage());
                }

                if (pendingCount == 0) {
                    LOGGER.fine("Sync queue empty — nothing to flush");
                    return;
                }

                LOGGER.info("📤 Flushing " + pendingCount + " pending items to cloud...");

                // Push all local changes to cloud
                syncToCloud();

                // Process any queued refunds specifically
                processPendingRefunds();

                // Mark all pending as synced
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "UPDATE sync_queue SET status = 'synced' " +
                        "WHERE status = 'pending' AND user_email = ?")) {
                    ps.setString(1, currentUserEmail);
                    int updated = ps.executeUpdate();
                    LOGGER.info("✅ Sync queue flushed — " + updated + " items marked synced");
                } catch (SQLException e) {
                    LOGGER.warning("Could not mark queue as synced: " + e.getMessage());
                }

            } catch (Exception e) {
                LOGGER.warning("processPendingSyncQueue failed: " + e.getMessage());
            }
        }, "flush-sync-queue").start();
    }

    public static boolean isOnline() { return isOnline; }
    public static void setForceLocalMode(boolean force) { forceLocalMode = force; if (force) isOnline = false; }

    // ================ DATABASE CONNECTION ================

    public static Connection getLocalConnection() throws SQLException {
        if (DB_FILE == null) {
            throw new SQLException("No database file set. User must login first.");
        }
        synchronized (connectionLock) {
            try {
                Class.forName("org.sqlite.JDBC");
                if (persistentConnection != null && !persistentConnection.isClosed()) {
                    try (Statement test = persistentConnection.createStatement()) {
                        test.setQueryTimeout(1);
                        test.executeQuery("SELECT 1").close();
                        return persistentConnection;
                    } catch (SQLException e) {
                        try { persistentConnection.close(); } catch (SQLException ignored) {}
                        persistentConnection = null;
                    }
                }
                Connection conn = DriverManager.getConnection(DB_URL + "?journal_mode=WAL");
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA busy_timeout=10000;");
                    stmt.execute("PRAGMA synchronous=NORMAL;");
                    stmt.execute("PRAGMA foreign_keys=ON;");
                }
                persistentConnection = conn;
                LOGGER.info("New persistent connection established: " + DB_FILE);
                return persistentConnection;
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found", e);
            }
        }
    }

    private static Connection createTransactionConnection() throws SQLException {
    try {
        Class.forName("org.sqlite.JDBC");
        // Separate connection so it doesn't affect the persistent connection's state
        Connection conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=10000;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        return conn;
    } catch (ClassNotFoundException e) {
        throw new SQLException("SQLite JDBC driver not found", e);
    }
}

    public static synchronized Connection getConnection() throws SQLException {
        return getLocalConnection();
    }

    // ================ DATE HELPERS ================
    
    public static String toDatabaseDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    public static LocalDate fromDatabaseDate(String dateStr) {
        if (dateStr == null || dateStr.equals("N/A")) return null;
        try {
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                dateStr = dateStr.substring(0, 10);
            }
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
    
    // ==== sms id creation======
    

    // ================ DATABASE INITIALIZATION ================
    
    public static void initializeDatabase() throws SQLException {
        // Local DB always comes first — app works offline
        try {
            Class.forName("org.sqlite.JDBC");
            Connection localConn = getLocalConnection();
            ensureSchema(localConn);
            checkAndFixSalesTableSchema(localConn);
            ensureAppSettingsTable(localConn);
            SQLiteCloudClient.initLocalConnection(DB_FILE);
            LOGGER.info("Local database initialized successfully");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        // Cloud init is background-only — never blocks startup
        new Thread(() -> {
            try {
                if (checkTursoConnection()) {
                    initializeCloudTables();
                    LOGGER.info("✅ Cloud sync active");
                    SQLiteCloudClient.startBackgroundSync();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cloud init failed — running offline", e);
                isOnline = false;
            }
        }, "cloud-init").start();
    }

    private static void ensureAppSettingsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS app_settings (" +
                     "key TEXT PRIMARY KEY, " +
                     "value TEXT, " +
                     "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        String[] tableDefinitions = {
            "CREATE TABLE IF NOT EXISTS categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
           "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "user_uid TEXT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "privileges TEXT DEFAULT 'user', " +
                "email TEXT, " +
                "business_name TEXT, " +
                "created_date TEXT DEFAULT (datetime('now')), " +
                "security_question1 TEXT, " +
                "security_answer1 TEXT, " +
                "security_question2 TEXT, " +
                "security_answer2 TEXT, " +
                "password_last_changed TEXT DEFAULT (datetime('now')), " +
                "account_status TEXT DEFAULT 'ACTIVE', " +
                "locked INTEGER DEFAULT 0, " +
                "lock_reason TEXT, " +
                "last_login TEXT, " +
                "last_login_ip TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "cost_price DECIMAL(10,2) NOT NULL, " +
                "selling_price DECIMAL(10,2) NOT NULL, " +
                "stock_quantity INTEGER NOT NULL DEFAULT 0, " +
                "min_stock_level INTEGER NOT NULL DEFAULT 0, " +
                "category_id INTEGER, " +
                "vendor TEXT, " +
                "expire_date TEXT, " +
                "created_date TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (category_id) REFERENCES categories(id))",
                
            "CREATE TABLE IF NOT EXISTS vendors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "name TEXT NOT NULL, " +
                "contact TEXT, " +
                "phone TEXT, " +
                "email TEXT, " +
                "address TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS sales (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT NOT NULL, " +
                "local_id INTEGER, " +
                "sales_total DECIMAL(10,2) NOT NULL, " +
                "discount DECIMAL(10,2) DEFAULT 0, " +
                "sale_date TEXT NOT NULL, " +
                "user TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS sale_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT NOT NULL, " +
                "local_id INTEGER, " +
                "sale_id INTEGER, " +
                "product_id INTEGER, " +
                "quantity INTEGER NOT NULL, " +
                "unit_price DECIMAL(10,2) NOT NULL, " +
                "total_price DECIMAL(10,2) NOT NULL, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sale_id) REFERENCES sales(id), " +
                "FOREIGN KEY (product_id) REFERENCES products(id))",
                
            "CREATE TABLE IF NOT EXISTS receipts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "receipt_date TEXT NOT NULL, " +
                "sales_total DECIMAL(10,2) NOT NULL, " +
                "vendor TEXT, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS item_flows (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "product_id INTEGER, " +
                "action TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "flow_date TEXT NOT NULL, " +
                "created_date TEXT DEFAULT (date('now','localtime')), " +
                "vendor TEXT, " +
                "user TEXT, " +
                "cloud_id INTEGER, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (product_id) REFERENCES products(id))",
                
            "CREATE TABLE IF NOT EXISTS inventory_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "product_id INTEGER NOT NULL, " +
                "action TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "previous_quantity INTEGER DEFAULT 0, " +
                "date TEXT DEFAULT (date('now','localtime')), " +
                "cloud_id INTEGER, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (product_id) REFERENCES products(id))",
                
            "CREATE TABLE IF NOT EXISTS daily_expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "expense_date TEXT NOT NULL DEFAULT (date('now','localtime')), " +
                "amount DECIMAL(10,2) NOT NULL, " +
                "description TEXT, " +
                "cloud_id INTEGER, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS daily_sales_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "report_date TEXT NOT NULL, " +
                "total_gross_sale DECIMAL(10,2) NOT NULL, " +
                "discounts DECIMAL(10,2) NOT NULL, " +
                "cost_of_goods DECIMAL(10,2) NOT NULL, " +
                "daily_expenses DECIMAL(10,2) NOT NULL, " +
                "net_sale DECIMAL(10,2) NOT NULL, " +
                "gross_profit DECIMAL(10,2) NOT NULL, " +
                "net_profit DECIMAL(10,2) NOT NULL, " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                
            "CREATE TABLE IF NOT EXISTS sync_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_email TEXT, " +
                "client_id TEXT, " +
                "table_name TEXT NOT NULL, " +
                "record_id INTEGER NOT NULL, " +
                "operation TEXT NOT NULL, " +
                "data TEXT, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "attempted_at DATETIME, " +
                "status TEXT DEFAULT 'pending')",

            "CREATE INDEX IF NOT EXISTS idx_products_client ON products(client_id)",
            "CREATE INDEX IF NOT EXISTS idx_sales_client ON sales(client_id)",
            "CREATE INDEX IF NOT EXISTS idx_categories_client ON categories(client_id)",
            "CREATE INDEX IF NOT EXISTS idx_sale_items_client ON sale_items(client_id)",
            "CREATE INDEX IF NOT EXISTS idx_item_flows_client ON item_flows(client_id)",
            "CREATE INDEX IF NOT EXISTS idx_products_user_email ON products(user_email)",
            "CREATE INDEX IF NOT EXISTS idx_sales_user_email ON sales(user_email)",
            "CREATE INDEX IF NOT EXISTS idx_categories_user_email ON categories(user_email)",
            "CREATE INDEX IF NOT EXISTS idx_sale_items_user_email ON sale_items(user_email)",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_unique_name_client ON categories(name, client_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_vendors_unique_name_client ON vendors(name, client_id)",

            
        };
        
        try (Statement stmt = conn.createStatement()) {
            for (String sql : tableDefinitions) {
                try { stmt.execute(sql); } 
                catch (SQLException e) { LOGGER.fine("Schema: " + e.getMessage()); }
            }
        }
        
        LOGGER.info("Database schema verified");
    }

    private static void checkAndFixSalesTableSchema(Connection conn) throws SQLException {
        boolean hasCloudId = false;
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "sales", "cloud_id")) {
            hasCloudId = rs.next();
        }
        if (!hasCloudId) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE sales ADD COLUMN cloud_id INTEGER");
            } catch (SQLException e) { LOGGER.warning("Could not add cloud_id: " + e.getMessage()); }
        }
    }

    // ================ AUTO-CREATE VENDORS AND CATEGORIES ================
    
    public static int autoCreateVendor(String vendorName) throws SQLException {
        if (vendorName == null || vendorName.trim().isEmpty()) return -1;
        
        String trimmedName = vendorName.trim();
        
        if (autoCreatedVendors.contains(trimmedName)) {
            return getVendorIdByName(trimmedName);
        }
        
        String checkSql = "SELECT id FROM vendors WHERE name = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(checkSql)) {
            stmt.setString(1, trimmedName);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                autoCreatedVendors.add(trimmedName);
                return rs.getInt("id");
            }
        }
        
        String insertSql = "INSERT INTO vendors (name, client_id, user_email) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, trimmedName);
            stmt.setString(2, currentClientId);
            stmt.setString(3, currentUserEmail);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int vendorId = rs.getInt(1);
                autoCreatedVendors.add(trimmedName);
                LOGGER.info("Auto-created vendor locally: " + trimmedName);

                // Queue sync — never block on cloud here
                final int finalVendorId = vendorId;
                new Thread(() -> {
                    try (PreparedStatement q = getConnection().prepareStatement(
                            "INSERT INTO sync_queue (user_email, table_name, record_id, operation, status) " +
                            "VALUES (?, 'vendors', ?, 'INSERT', 'pending')")) {
                        q.setString(1, currentUserEmail);
                        q.setInt(2, finalVendorId);
                        q.executeUpdate();
                    } catch (SQLException e) {
                        LOGGER.fine("Could not queue vendor sync: " + e.getMessage());
                    }
                }, "queue-vendor").start();

                return vendorId;
            }
        }
        return -1;
    }
    
    public static int autoCreateCategory(String categoryName) throws SQLException {
    if (categoryName == null || categoryName.trim().isEmpty()) return -1;
    
    String trimmedName = categoryName.trim().toUpperCase();
    
    String checkSql = "SELECT id FROM categories WHERE UPPER(name) = ? AND client_id = ? AND user_email = ?";
    try (PreparedStatement stmt = getConnection().prepareStatement(checkSql)) {
        stmt.setString(1, trimmedName);
        stmt.setString(2, currentClientId);
        stmt.setString(3, currentUserEmail);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("id");
        }
    }
    
    String insertSql = "INSERT INTO categories (name, client_id, user_email) VALUES (?, ?, ?)";
    try (PreparedStatement stmt = getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
        stmt.setString(1, trimmedName);
        stmt.setString(2, currentClientId);
        stmt.setString(3, currentUserEmail);
        stmt.executeUpdate();
        
        ResultSet rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            int categoryId = rs.getInt(1);
            LOGGER.info("Auto-created category: " + trimmedName + " for user: " + currentUserEmail);
            return categoryId;
        }
    }
    return -1;
}
    
    private static int getVendorIdByName(String name) throws SQLException {
        String sql = "SELECT id FROM vendors WHERE name = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }
 
   

    

    /**
     * Pull verify and productcategory rows from cloud, then restore missing categories
     * and reconcile products. Called on login when local DB is fresh/empty.
     */
    public static void restoreFromCloudVerifyTables() {
        if (!isOnline()) {
            LOGGER.info("restoreFromCloudVerifyTables: offline, skipping");
            return;
        }
        try {
            // Only pull from categories table, nothing else
            LOGGER.info("Pulling categories from cloud...");
            List<com.google.gson.JsonObject> catRows = SQLiteCloudClient.execute(
                "SELECT id, name, client_id, user_email FROM categories WHERE client_id = ?",
                currentClientId);

            for (com.google.gson.JsonObject row : catRows) {
                if (!row.has("name") || row.get("name").isJsonNull()) continue;
                String name = row.get("name").getAsString().toUpperCase().trim();
                int cloudId = row.get("id").getAsInt();

                // Check if exists locally by ID
                boolean exists = false;
                try (PreparedStatement stmt = getConnection().prepareStatement(
                        "SELECT id FROM categories WHERE id = ? AND client_id = ?")) {
                    stmt.setInt(1, cloudId);
                    stmt.setString(2, currentClientId);
                    ResultSet rs = stmt.executeQuery();
                    exists = rs.next();
                }

                if (!exists) {
                    String insertSql = "INSERT INTO categories (id, name, client_id, user_email) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ins = getConnection().prepareStatement(insertSql)) {
                        ins.setInt(1, cloudId);
                        ins.setString(2, name);
                        ins.setString(3, currentClientId);
                        ins.setString(4, currentUserEmail);
                        ins.executeUpdate();
                        LOGGER.info("Restored category from cloud: " + name);
                    }
                }
            }
            LOGGER.info("restoreFromCloudVerifyTables: " + catRows.size() + " categories verified");

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "restoreFromCloudVerifyTables failed", e);
        }
    }
    public static void pullCategoriesFromCloud() throws SQLException {
        if (!isOnline() || currentClientId == null) return;
        
        try {
            List<JsonObject> cloudCategories = SQLiteCloudClient.execute(
                "SELECT id, name, description FROM categories WHERE client_id = ?",
                currentClientId);
            
            for (JsonObject cat : cloudCategories) {
                String name = cat.get("name").getAsString();
                int cloudId = cat.get("id").getAsInt();
                
                // Check if exists locally by ID first
                boolean exists = false;
                try (PreparedStatement ps = getConnection().prepareStatement(
                        "SELECT id FROM categories WHERE id = ? AND client_id = ?")) {
                    ps.setInt(1, cloudId);
                    ps.setString(2, currentClientId);
                    ResultSet rs = ps.executeQuery();
                    exists = rs.next();
                }
                
                if (!exists) {
                    // Insert preserving the original ID
                    String sql = "INSERT INTO categories (id, name, client_id, user_email) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                        ps.setInt(1, cloudId);
                        ps.setString(2, name);
                        ps.setString(3, currentClientId);
                        ps.setString(4, currentUserEmail);
                        ps.executeUpdate();
                    }
                }
            }
            LOGGER.info("Pulled " + cloudCategories.size() + " categories from cloud");
        } catch (IOException e) {
            LOGGER.warning("pullCategoriesFromCloud failed: " + e.getMessage());
        }
    }
    
    public static int rebuildAllVendorsFromProducts() throws SQLException {
        String sql = "SELECT DISTINCT vendor FROM products WHERE client_id = ? AND vendor IS NOT NULL AND vendor != ''";
        List<String> productVendors = new ArrayList<>();
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, currentClientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                productVendors.add(rs.getString("vendor"));
            }
        }
        
        int created = 0;
        for (String vendorName : productVendors) {
            int id = autoCreateVendor(vendorName);
            if (id > 0) created++;
        }
        
        LOGGER.info("Rebuilt vendors: " + created + " created from " + productVendors.size() + " unique vendors");
        return created;
    }
    
    public static int rebuildAllCategoriesFromProducts() throws SQLException {
    if (currentClientId == null || currentClientId.isEmpty()) return 0;

    // Step 1: Get all distinct category_ids referenced by products for this user
    String missingCatSql =
        "SELECT DISTINCT p.category_id " +
        "FROM products p " +
        "LEFT JOIN categories c ON p.category_id = c.id AND c.client_id = ? AND c.user_email = ? " +
        "WHERE p.client_id = ? AND p.user_email = ? " +
        "AND p.category_id IS NOT NULL " +
        "AND p.category_id > 0 " +
        "AND c.id IS NULL";

    List<Integer> missingCategoryIds = new ArrayList<>();
    try (PreparedStatement stmt = getConnection().prepareStatement(missingCatSql)) {
        stmt.setString(1, currentClientId);
        stmt.setString(2, currentUserEmail);
        stmt.setString(3, currentClientId);
        stmt.setString(4, currentUserEmail);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            missingCategoryIds.add(rs.getInt("category_id"));
        }
    }

    int created = 0;

    // Step 2: For each missing category_id, try cloud then use placeholder
    for (int catId : missingCategoryIds) {
        String categoryName = null;

        // Try to fetch the real name from cloud for this user
        if (isOnline()) {
            try {
                List<JsonObject> result = SQLiteCloudClient.execute(
                    "SELECT name FROM categories WHERE id = ? AND client_id = ? AND user_email = ?",
                    catId, currentClientId, currentUserEmail);
                if (!result.isEmpty() && result.get(0).has("name")
                        && !result.get(0).get("name").isJsonNull()) {
                    categoryName = result.get(0).get("name").getAsString().toUpperCase().trim();
                }
            } catch (IOException e) {
                LOGGER.warning("Could not fetch category name from cloud for id=" + catId);
            }
        }

        // Fall back to placeholder name
        if (categoryName == null || categoryName.isEmpty()) {
            // Category genuinely doesn't exist in cloud for this user
            // Skip it — don't create fake placeholder names
            LOGGER.warning("Category id=" + catId +
                           " has no real name in cloud — skipping placeholder creation");
            continue; // skip to next missing category
        }

        // Insert with the ORIGINAL id so foreign keys stay intact
        String insertSql =
            "INSERT OR IGNORE INTO categories (id, name, client_id, user_email) " +
            "VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(insertSql)) {
            stmt.setInt(1, catId);
            stmt.setString(2, categoryName);
            stmt.setString(3, currentClientId);
            stmt.setString(4, currentUserEmail);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                LOGGER.info("Auto-created missing category: " + categoryName + " (ID=" + catId + ")");
                created++;
            }
        }
    }

    // Step 3: Also ensure GENERAL category exists for truly uncategorized products for this user
    int uncategorized = 0;
    String uncatSql =
        "SELECT COUNT(*) FROM products " +
        "WHERE client_id = ? AND user_email = ? AND (category_id IS NULL OR category_id = 0)";
    try (PreparedStatement stmt = getConnection().prepareStatement(uncatSql)) {
        stmt.setString(1, currentClientId);
        stmt.setString(2, currentUserEmail);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) uncategorized = rs.getInt(1);
    }

    if (uncategorized > 0) {
        String checkSql =
            "SELECT COUNT(*) FROM categories WHERE UPPER(name) = 'GENERAL' AND client_id = ? AND user_email = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(checkSql)) {
            stmt.setString(1, currentClientId);
            stmt.setString(2, currentUserEmail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                String insertSql =
                    "INSERT INTO categories (name, client_id, user_email) VALUES ('GENERAL', ?, ?)";
                try (PreparedStatement ins = getConnection().prepareStatement(insertSql)) {
                    ins.setString(1, currentClientId);
                    ins.setString(2, currentUserEmail);
                    ins.executeUpdate();
                    LOGGER.info("Auto-created GENERAL category for uncategorized products");
                    created++;
                }
            }
        }
    }

    LOGGER.info("rebuildAllCategoriesFromProducts: " + created + " categories created");
    return created;
}

    // ================ PRODUCT HELPER METHODS ================
    
    private static boolean productExistsLocally(String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE name = ? AND " + whereClient();
        try (Connection conn = getLocalConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static void insertProductFromCloud(JsonObject product) throws SQLException {
        String sql = "INSERT INTO products (name, category_id, vendor, cost_price, selling_price, " +
                     "stock_quantity, min_stock_level, expire_date, created_date, client_id, user_email) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, date('now','localtime'), ?, ?)";
        try (Connection conn = getLocalConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, getStringSafe(product, "name"));
            
            Integer catId = getIntSafe(product, "category_id");
            if (catId != null) stmt.setInt(2, catId); else stmt.setNull(2, java.sql.Types.INTEGER);
            
            stmt.setString(3, getStringSafe(product, "vendor"));
            stmt.setBigDecimal(4, getDecimalSafe(product, "cost_price"));
            stmt.setBigDecimal(5, getDecimalSafe(product, "selling_price"));
            stmt.setInt(6, getIntSafe(product, "stock_quantity") != null ? getIntSafe(product, "stock_quantity") : 0);
            stmt.setInt(7, getIntSafe(product, "min_stock_level") != null ? getIntSafe(product, "min_stock_level") : 0);
            stmt.setString(8, getStringSafe(product, "expire_date"));
            stmt.setString(9, getStringSafe(product, "client_id"));
            stmt.setString(10, getStringSafe(product, "user_email"));
            
            stmt.executeUpdate();
        }
    }

    private static void updateProductFromCloud(JsonObject product) throws SQLException {
        String sql = "UPDATE products SET cost_price = ?, selling_price = ?, stock_quantity = ?, " +
                     "min_stock_level = ?, category_id = ?, vendor = ?, expire_date = ? WHERE name = ? AND " + whereClient();
        try (Connection conn = getLocalConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, getDecimalSafe(product, "cost_price"));
            stmt.setBigDecimal(2, getDecimalSafe(product, "selling_price"));
            stmt.setInt(3, getIntSafe(product, "stock_quantity") != null ? getIntSafe(product, "stock_quantity") : 0);
            stmt.setInt(4, getIntSafe(product, "min_stock_level") != null ? getIntSafe(product, "min_stock_level") : 0);
            Integer catId = getIntSafe(product, "category_id");
            if (catId != null) stmt.setInt(5, catId); else stmt.setNull(5, java.sql.Types.INTEGER);
            stmt.setString(6, getStringSafe(product, "vendor"));
            stmt.setString(7, getStringSafe(product, "expire_date"));
            stmt.setString(8, getStringSafe(product, "name"));
            setClientParam(stmt, 9);
            stmt.executeUpdate();
        }
    }

    // ================ SAFE JSON HELPERS ================
    
    private static String getStringSafe(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : null;
    }
    private static BigDecimal getDecimalSafe(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsBigDecimal() : BigDecimal.ZERO;
    }
    private static Integer getIntSafe(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsInt() : null;
    }

    // ================ PRODUCT METHODS ================
    
    public static boolean addProduct(String name, int categoryId, String vendor,
    	    BigDecimal costPrice, BigDecimal sellingPrice,
    	    int stockQuantity, int minStockLevel, LocalDate expireDate) throws SQLException {
    	    if (name == null || name.trim().isEmpty())
    	        throw new IllegalArgumentException("Product name cannot be empty");
    	    if (costPrice == null || sellingPrice == null
    	            || costPrice.compareTo(BigDecimal.ZERO) < 0
    	            || sellingPrice.compareTo(BigDecimal.ZERO) < 0)
    	        throw new IllegalArgumentException("Prices cannot be null or negative");
    	    if (stockQuantity < 0 || minStockLevel < 0)
    	        throw new IllegalArgumentException("Stock quantities cannot be negative");
    	    if (!categoryExists(categoryId))
    	        throw new IllegalArgumentException("Invalid category ID: " + categoryId);

    	    if (vendor != null && !vendor.trim().isEmpty()) {
    	        autoCreateVendorSync(vendor.trim());
    	    }

    	    String sql = "INSERT INTO products (name, category_id, vendor, cost_price, selling_price, " +
    	                 "stock_quantity, min_stock_level, expire_date, created_date, client_id, user_email, updated_at) " +
    	                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, date('now','localtime'), ?, ?, CURRENT_TIMESTAMP)";

    	    Connection conn = getConnection();
    	    try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
    	        stmt.setString(1, name);
    	        stmt.setInt(2, categoryId);
    	        stmt.setString(3, vendor);
    	        stmt.setBigDecimal(4, costPrice);
    	        stmt.setBigDecimal(5, sellingPrice);
    	        stmt.setInt(6, stockQuantity);
    	        stmt.setInt(7, minStockLevel);
    	        stmt.setString(8, toDatabaseDate(expireDate));
    	        stmt.setString(9, currentClientId);
    	        stmt.setString(10, currentUserEmail);
    	        int rowsAffected = stmt.executeUpdate();

    	        if (rowsAffected > 0) {
    	            ResultSet rs = stmt.getGeneratedKeys();
    	            if (rs.next()) {
    	                int productId = rs.getInt(1);

    	                // Log item_flow locally
    	                try (PreparedStatement flowStmt = conn.prepareStatement(
    	                        "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, vendor, user, client_id, user_email) " +
    	                        "VALUES (?, 'NEW ITEM', ?, date('now','localtime'), date('now','localtime'), ?, 'SYSTEM', ?, ?)")) {
    	                    flowStmt.setInt(1, productId);
    	                    flowStmt.setInt(2, stockQuantity);
    	                    flowStmt.setString(3, vendor);
    	                    flowStmt.setString(4, currentClientId);
    	                    flowStmt.setString(5, currentUserEmail);
    	                    flowStmt.executeUpdate();
    	                }

    	                logInventoryAction(productId, "NEW ITEM", stockQuantity, 0);
    	                LOGGER.info("Added product: " + name + " stock=" + stockQuantity);

    	                final int fid = productId;
    	                final String fName = name;
    	                final String fVendor = vendor;
    	                final BigDecimal fCost = costPrice;
    	                final BigDecimal fSell = sellingPrice;
    	                final String fExpire = toDatabaseDate(expireDate);

    	                if (isOnline()) {
    	                    new Thread(() -> {
    	                        try {
    	                            // Push product to cloud
    	                            SQLiteCloudClient.executeUpdate(
    	                                "INSERT OR REPLACE INTO products " +
    	                                "(id, name, category_id, vendor, cost_price, selling_price, " +
    	                                "stock_quantity, min_stock_level, expire_date, created_date, " +
    	                                "client_id, user_email, updated_at) " +
    	                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, date('now','localtime'), ?, ?, CURRENT_TIMESTAMP)",
    	                                fid, fName, categoryId, fVendor, fCost, fSell,
    	                                stockQuantity, minStockLevel, fExpire,
    	                                currentClientId, currentUserEmail);

    	                            // Push NEW ITEM flow to cloud
    	                            SQLiteCloudClient.executeUpdate(
    	                                "INSERT INTO item_flows " +
    	                                "(product_id, action, quantity, flow_date, created_date, vendor, user, client_id, user_email) " +
    	                                "VALUES (?, 'NEW ITEM', ?, date('now','localtime'), date('now','localtime'), ?, 'SYSTEM', ?, ?)",
    	                                fid, stockQuantity, fVendor, currentClientId, currentUserEmail);

    	                            LOGGER.info("Product synced to cloud: id=" + fid);
    	                        } catch (Exception e) {
    	                            LOGGER.warning("Cloud product sync failed: " + e.getMessage());
    	                            SQLiteCloudClient.queueSyncOperation("products", fid, "INSERT",
    	                                new com.google.gson.JsonObject());
    	                        }
    	                    }, "sync-new-product-" + productId).start();
    	                }
    	            }
    	            return true;
    	        }
    	        return false;
    	    }
    	}
private static int autoCreateVendorSync(String vendorName) throws SQLException {
    if (vendorName == null || vendorName.trim().isEmpty()) return -1;
    String trimmedName = vendorName.trim();
    if (autoCreatedVendors.contains(trimmedName)) return getVendorIdByName(trimmedName);

    String checkSql = "SELECT id FROM vendors WHERE name = ? AND client_id = ?";
    try (PreparedStatement stmt = getConnection().prepareStatement(checkSql)) {
        stmt.setString(1, trimmedName);
        stmt.setString(2, currentClientId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            autoCreatedVendors.add(trimmedName);
            return rs.getInt("id");
        }
    }
    String insertSql = "INSERT INTO vendors (name, client_id, user_email, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
    try (PreparedStatement stmt = getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
        stmt.setString(1, trimmedName);
        stmt.setString(2, currentClientId);
        stmt.setString(3, currentUserEmail);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            int id = rs.getInt(1);
            autoCreatedVendors.add(trimmedName);
            LOGGER.info("Auto-created vendor: " + trimmedName);
            return id;
        }
    }
    return -1;
}


    public static List<Product> getAllProducts() throws SQLException {
    List<Product> products = new ArrayList<>();
    Connection conn;
    try {
        conn = getLocalConnection();
    } catch (SQLException e) {
        return products;
    }
    
    String sql;
    PreparedStatement stmt;
    
    if (currentClientId != null && !currentClientId.isEmpty()) {
        sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level, category_id, vendor, expire_date, created_date, client_id FROM products WHERE client_id = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentClientId);
    } else {
        sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level, category_id, vendor, expire_date, created_date, client_id FROM products WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentUserEmail);
    }
    
    try (stmt) {
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            Product p = new Product();
            p.setId(rs.getInt("id"));
            p.setName(rs.getString("name"));
            p.setCostPrice(rs.getBigDecimal("cost_price"));
            p.setSellingPrice(rs.getBigDecimal("selling_price"));
            p.setStockQuantity(rs.getInt("stock_quantity"));
            p.setMinStockLevel(rs.getInt("min_stock_level"));
            p.setCategoryId(rs.getInt("category_id"));
            p.setVendor(rs.getString("vendor"));
            p.setExpireDate(fromDatabaseDate(rs.getString("expire_date")));
            p.setCreatedDate(fromDatabaseDate(rs.getString("created_date")));
            p.setClientId(rs.getString("client_id"));
            products.add(p);
        }
    }
    return products;
}

    public static Product getProductById(int id) throws SQLException {
        String sql;
        PreparedStatement stmt;
        Connection conn = getConnection();
        
        if (currentClientId != null && !currentClientId.isEmpty()) {
            sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level, category_id, vendor, expire_date, created_date, client_id FROM products WHERE id = ? AND client_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            stmt.setString(2, currentClientId);
        } else {
            sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level, category_id, vendor, expire_date, created_date, client_id FROM products WHERE id = ? AND (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            stmt.setString(2, currentUserEmail);
        }
        
        try (stmt) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Product p = new Product();
                p.setId(rs.getInt("id"));
                p.setName(rs.getString("name"));
                p.setCostPrice(rs.getBigDecimal("cost_price"));
                p.setSellingPrice(rs.getBigDecimal("selling_price"));
                p.setStockQuantity(rs.getInt("stock_quantity"));
                p.setMinStockLevel(rs.getInt("min_stock_level"));
                p.setCategoryId(rs.getInt("category_id"));
                p.setVendor(rs.getString("vendor"));
                p.setExpireDate(fromDatabaseDate(rs.getString("expire_date")));
                p.setCreatedDate(fromDatabaseDate(rs.getString("created_date")));
                p.setClientId(rs.getString("client_id"));
                return p;
            }
        }
        return null;
    }

    public static boolean updateProduct(int id, String name, int categoryId, String vendor,
    	    BigDecimal costPrice, BigDecimal sellingPrice,
    	    int stockQuantity, int minStockLevel, LocalDate expireDate) throws SQLException {
    	    if (vendor != null && !vendor.trim().isEmpty()) {
    	        autoCreateVendorSync(vendor.trim());
    	    }
    	    String sql = "UPDATE products SET name=?, category_id=?, vendor=?, cost_price=?, " +
    	                 "selling_price=?, stock_quantity=?, min_stock_level=?, expire_date=?, " +
    	                 "updated_at=CURRENT_TIMESTAMP " +
    	                 "WHERE id=? AND client_id=?";
    	    try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
    	        stmt.setString(1, name);
    	        stmt.setInt(2, categoryId);
    	        stmt.setString(3, vendor);
    	        stmt.setBigDecimal(4, costPrice);
    	        stmt.setBigDecimal(5, sellingPrice);
    	        stmt.setInt(6, stockQuantity);
    	        stmt.setInt(7, minStockLevel);
    	        stmt.setString(8, toDatabaseDate(expireDate));
    	        stmt.setInt(9, id);
    	        stmt.setString(10, currentClientId);
    	        boolean updated = stmt.executeUpdate() > 0;

    	        if (updated && isOnline()) {
    	            final int fid = id;
    	            new Thread(() -> {
    	                try {
    	                    SQLiteCloudClient.executeUpdate(
    	                        "UPDATE products SET name=?, category_id=?, vendor=?, " +
    	                        "cost_price=?, selling_price=?, stock_quantity=?, " +
    	                        "min_stock_level=?, expire_date=?, updated_at=CURRENT_TIMESTAMP " +
    	                        "WHERE id=? AND client_id=?",
    	                        name, categoryId, vendor,
    	                        costPrice, sellingPrice, stockQuantity,
    	                        minStockLevel, toDatabaseDate(expireDate),
    	                        fid, currentClientId);
    	                    LOGGER.info("Product update synced to cloud: id=" + fid);
    	                } catch (Exception e) {
    	                    LOGGER.warning("Cloud product update failed, will sync on next fullSync: "
    	                        + e.getMessage());
    	                    SQLiteCloudClient.queueSyncOperation("products", fid, "UPDATE",
    	                        new com.google.gson.JsonObject());
    	                }
    	            }, "sync-update-product-" + id).start();
    	        }
    	        return updated;
    	    }
    	}

    public static List<Product> getLowStockProducts() throws SQLException {
    List<Product> lowStock = new ArrayList<>();
    String sql = "SELECT id, name, stock_quantity, min_stock_level, selling_price, cost_price " +
                 "FROM products WHERE stock_quantity <= min_stock_level AND " + whereClient();
    try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
        setClientParam(stmt, 1);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            Product p = new Product();
            p.setId(rs.getInt("id"));
            p.setName(rs.getString("name"));
            p.setStockQuantity(rs.getInt("stock_quantity"));
            p.setMinStockLevel(rs.getInt("min_stock_level"));
            p.setSellingPrice(rs.getBigDecimal("selling_price"));
            p.setCostPrice(rs.getBigDecimal("cost_price"));
            lowStock.add(p);
        }
    }
    return lowStock;
}

public static boolean deleteProductCompletely(int productId) throws SQLException {
    Connection conn = null;
    try {
        conn = getLocalConnection();
        conn.setAutoCommit(false);

        // Local cascaded delete
        for (String tbl : new String[]{"sale_items", "item_flows", "inventory_log"}) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + tbl + " WHERE product_id = ?")) {
                ps.setInt(1, productId);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM products WHERE id = ? AND client_id = ?")) {
            ps.setInt(1, productId);
            ps.setString(2, currentClientId);
            ps.executeUpdate();
        }

        // Queue deletion for cloud sync
        SQLiteCloudClient.queueDeletion("products", productId);
        SQLiteCloudClient.queueDeletion("sale_items", productId);   // cloud will handle FK
        SQLiteCloudClient.queueDeletion("item_flows", productId);

        conn.commit();

        // Immediate cloud sync if online
        if (isOnline()) {
            new Thread(() -> {
                try {
                    SQLiteCloudClient.syncDeletion("sale_items",
                        productId);   // by product_id col
                    SQLiteCloudClient.executeUpdate(
                        "DELETE FROM item_flows WHERE product_id = ? AND client_id = ?",
                        productId, currentClientId);
                    SQLiteCloudClient.syncDeletion("products", productId);
                } catch (Exception e) {
                    LOGGER.warning("Cloud product deletion failed, queued: " + e.getMessage());
                }
            }, "cloud-delete-product-" + productId).start();
        }

        LOGGER.info("Product " + productId + " deleted locally and queued for cloud");
        return true;

    } catch (SQLException e) {
        if (conn != null) { try { conn.rollback(); } catch (SQLException ignored) {} }
        throw e;
    } finally {
        if (conn != null) { try { conn.setAutoCommit(true); } catch (SQLException ignored) {} }
    }
}
    
    
    

    // ================ CATEGORY METHODS ================
    
    public static List<Category> getAllCategoryObjects() throws SQLException {
    List<Category> categories = new ArrayList<>();
    String sql;
    PreparedStatement stmt;
    Connection conn = getConnection();
    
    if (currentClientId != null && !currentClientId.isEmpty()) {
        sql = "SELECT id, name, description FROM categories WHERE client_id = ? AND user_email = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentClientId);
        stmt.setString(2, currentUserEmail);
    } else {
        sql = "SELECT id, name, description FROM categories WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentUserEmail);
    }
    
    try (stmt) {
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            Category c = new Category();
            c.setId(rs.getInt("id"));
            c.setName(rs.getString("name"));
            c.setDescription(rs.getString("description"));
            categories.add(c);
        }
    }
    return categories;
}

public static List<String> getAllCategories() throws SQLException {
    List<String> categories = new ArrayList<>();
    String sql;
    PreparedStatement stmt;
    Connection conn = getConnection();
    
    if (currentClientId != null && !currentClientId.isEmpty()) {
        sql = "SELECT name FROM categories WHERE client_id = ? AND user_email = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentClientId);
        stmt.setString(2, currentUserEmail);
    } else {
        sql = "SELECT name FROM categories WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name";
        stmt = conn.prepareStatement(sql);
        stmt.setString(1, currentUserEmail);
    }
    
    try (stmt) {
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) { 
            categories.add(rs.getString("name")); 
        }
    }
    return categories;
}

public static int getCategoryIdByName(String name) throws SQLException {
    if (currentClientId != null && !currentClientId.isEmpty()) {
        String sql = "SELECT id FROM categories WHERE UPPER(name) = UPPER(?) AND client_id = ? AND user_email = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, currentClientId);
            stmt.setString(3, currentUserEmail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
    } else {
        String sql = "SELECT id FROM categories WHERE UPPER(name) = UPPER(?) AND (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, currentUserEmail);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
    }
    return -1;
}

    public static boolean categoryExists(int categoryId) throws SQLException {
    String sql = "SELECT COUNT(*) FROM categories WHERE id = ? AND client_id = ? AND user_email = ?";
    try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
        stmt.setInt(1, categoryId);
        stmt.setString(2, currentClientId);
        stmt.setString(3, currentUserEmail);
        ResultSet rs = stmt.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }
}
    
    public static boolean addCategory(String name, String description) throws SQLException {
        String sql = "INSERT INTO categories (name, description, client_id, user_email) VALUES (?, ?, ?, ?)";
        int newId = -1;
        try (PreparedStatement stmt = getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name.toUpperCase());
            stmt.setString(2, description);
            stmt.setString(3, currentClientId);
            stmt.setString(4, currentUserEmail);
            boolean ok = stmt.executeUpdate() > 0;
            if (ok) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) newId = rs.getInt(1);
            }
            if (!ok) return false;
        }

        final int fId = newId;
        final String fName = name.toUpperCase();
        if (isOnline()) {
            new Thread(() -> {
                try {
                    SQLiteCloudClient.executeUpdate(
                        "INSERT OR IGNORE INTO categories " +
                        "(id, name, description, client_id, user_email, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        fId, fName, description, currentClientId, currentUserEmail);
                    LOGGER.info("Category synced to cloud: " + fName);
                } catch (Exception e) {
                    LOGGER.warning("Cloud category sync failed: " + e.getMessage());
                    SQLiteCloudClient.queueSyncOperation("categories", fId, "INSERT",
                        new com.google.gson.JsonObject());
                }
            }, "sync-category").start();
        } else {
            SQLiteCloudClient.queueSyncOperation("categories", fId, "INSERT",
                new com.google.gson.JsonObject());
        }
        return true;
    }
    
    public static List<String> getAllVendors() throws SQLException {
        List<String> vendors = new ArrayList<>();
        String sql;
        PreparedStatement stmt;
        Connection conn = getConnection();
        
        if (currentClientId != null && !currentClientId.isEmpty()) {
            sql = "SELECT name FROM vendors WHERE client_id = ? ORDER BY name";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentClientId);
        } else {
            sql = "SELECT name FROM vendors WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentUserEmail);
        }
        
        try (stmt) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                vendors.add(rs.getString("name"));
            }
        }
        return vendors;
    }

    // ================ USER METHODS ================
    
    public static boolean validateUser(String username, String password) throws SQLException {
        String sql = "SELECT password FROM users WHERE username = ? AND privileges != 'blocked'";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("password").equals(password);
        }
        return false;
    }

    public static String getUserRole(String username) throws SQLException {
        String sql = "SELECT privileges FROM users WHERE username = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("privileges");
        }
        return "user";
    }

    public static boolean usernameExists(String username) throws SQLException {
    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(
             "SELECT COUNT(*) FROM users WHERE username = ?")) {
        stmt.setString(1, username);
        ResultSet rs = stmt.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }
}

    public static boolean createNewUser(String username, String password,
            String privileges) throws SQLException {
        String uid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO users (username, password, privileges, client_id, user_email, user_uid) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, privileges);
            stmt.setString(4, currentClientId);
            stmt.setString(5, currentUserEmail);
            stmt.setString(6, uid);
            boolean ok = stmt.executeUpdate() > 0;

            if (ok && isOnline()) {
                new Thread(() -> {
                    try {
                        SQLiteCloudClient.executeUpdate(
                            "INSERT OR IGNORE INTO users " +
                            "(username, password, privileges, client_id, user_email, user_uid, " +
                            "created_date, account_status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, datetime('now'), 'ACTIVE')",
                            username, password, privileges,
                            currentClientId, currentUserEmail, uid);
                        LOGGER.info("New user synced to cloud: " + username);
                    } catch (Exception e) {
                        LOGGER.warning("Cloud user create failed: " + e.getMessage());
                    }
                }, "sync-new-user-" + username).start();
            }
            return ok;
        }
    }

    public static boolean createLinkedUser(String email, String username,
            String password, String privileges) throws SQLException {
        String uid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO users (username, password, privileges, client_id, user_email, email, user_uid) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, privileges);
            stmt.setString(4, currentClientId);
            stmt.setString(5, email);
            stmt.setString(6, email);
            stmt.setString(7, uid);
            boolean ok = stmt.executeUpdate() > 0;

            if (ok && isOnline()) {
                new Thread(() -> {
                    try {
                        SQLiteCloudClient.executeUpdate(
                            "INSERT OR IGNORE INTO users " +
                            "(username, password, privileges, client_id, user_email, email, user_uid, " +
                            "created_date, account_status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'), 'ACTIVE')",
                            username, password, privileges,
                            currentClientId, email, email, uid);
                        LOGGER.info("Linked user synced to cloud: " + username);
                    } catch (Exception e) {
                        LOGGER.warning("Cloud linked user create failed: " + e.getMessage());
                    }
                }, "sync-linked-user-" + username).start();
            }
            return ok;
        }
    }
    public static void syncRecordToCloud(String tableName, int recordId) {
        if (!isOnline()) {
            SQLiteCloudClient.queueSyncOperation(tableName, recordId, "UPDATE",
                new com.google.gson.JsonObject());
            return;
        }
        new Thread(() -> {
            try {
                // Fetch the current local row and push it to cloud
                String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
                try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                    ps.setInt(1, recordId);
                    ResultSet rs = ps.executeQuery();
                    ResultSetMetaData meta = rs.getMetaData();
                    if (rs.next()) {
                        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String col = meta.getColumnName(i);
                            Object val = rs.getObject(i);
                            if (val == null) data.add(col, com.google.gson.JsonNull.INSTANCE);
                            else data.addProperty(col, val.toString());
                        }
                        // Build upsert and push
                        SQLiteCloudClient.executeUpdateDirect(
                            buildUpsertFromJson(tableName, data));
                        LOGGER.fine("syncRecordToCloud: " + tableName + " id=" + recordId);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("syncRecordToCloud failed for " + tableName + " id=" + recordId
                    + ": " + e.getMessage());
                SQLiteCloudClient.queueSyncOperation(tableName, recordId, "UPDATE",
                    new com.google.gson.JsonObject());
            }
        }, "sync-" + tableName + "-" + recordId).start();
    }

    private static String buildUpsertFromJson(String table,
            com.google.gson.JsonObject data) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        StringBuilder updates = new StringBuilder();
        for (String key : data.keySet()) {
            if (cols.length() > 0) { cols.append(","); vals.append(","); updates.append(","); }
            cols.append('"').append(key).append('"');
            com.google.gson.JsonElement el = data.get(key);
            String v;
            if (el.isJsonNull()) v = "NULL";
            else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber())
                v = el.getAsString();
            else v = "'" + el.getAsString().replace("'", "''") + "'";
            vals.append(v);
            updates.append('"').append(key).append('"').append("=").append(v);
        }
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ") " +
               "ON CONFLICT(id) DO UPDATE SET " + updates;
    }
    public static void ensureUserUid(String email) throws SQLException {
    // Check if user already has a UID
    String checkSql = "SELECT user_uid FROM users WHERE email = ? AND client_id = ?";
    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(checkSql)) {
        ps.setString(1, email);
        ps.setString(2, currentClientId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            String uid = rs.getString("user_uid");
            if (uid != null && !uid.isEmpty()) {
                return; // Already has UID, do nothing
            }
        }
    }
    
    // Generate simple UID: clientId_ + email prefix
    String simpleUid = currentClientId + "_" + email.split("@")[0];
    
    // Update the user record
    String updateSql = "UPDATE users SET user_uid = ? WHERE email = ? AND client_id = ?";
    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(updateSql)) {
        ps.setString(1, simpleUid);
        ps.setString(2, email);
        ps.setString(3, currentClientId);
        ps.executeUpdate();
        LOGGER.info("Created user_uid: " + simpleUid + " for " + email);
    }
}

    public static List<String> getExistingUsers() throws SQLException {
        List<String> users = new ArrayList<>();
        String sql;
        PreparedStatement stmt;
        Connection conn = getConnection();
        
        if (currentClientId != null && !currentClientId.isEmpty()) {
            sql = "SELECT username FROM users WHERE privileges != 'blocked' AND client_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentClientId);
        } else {
            sql = "SELECT username FROM users WHERE privileges != 'blocked' AND (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentUserEmail);
        }
        
        try (stmt) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) users.add(rs.getString("username"));
        }
        return users;
    }

    public static boolean updateUserPrivileges(String username, String privileges) throws SQLException {
        String sql = "UPDATE users SET privileges = ? WHERE username = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, privileges);
            stmt.setString(2, username);
            setClientParam(stmt, 3);
            return stmt.executeUpdate() > 0;
        }
    }

    public static boolean blockUser(String username) throws SQLException {
        return updateUserPrivileges(username, "blocked");
    }

    public static boolean deleteUser(String username) throws SQLException {
        String sql = "DELETE FROM users WHERE username = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            setClientParam(stmt, 2);
            return stmt.executeUpdate() > 0;
        }
    }

    public static boolean updateUserPassword(String username, String password) throws SQLException {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, password);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        }
    }

    public static List<SaleItem> getSaleItems(String startDate, String endDate) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = "SELECT si.product_id, si.quantity, si.unit_price, DATE(s.sale_date) as sale_date " +
                     "FROM sale_items si JOIN sales s ON si.sale_id = s.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s." + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            setClientParam(stmt, 3);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                SaleItem item = new SaleItem();
                item.setProductId(rs.getInt("product_id"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getBigDecimal("unit_price"));
                item.setDate(fromDatabaseDate(rs.getString("sale_date")));
                items.add(item);
            }
        }
        return items;
    }

    public static String getClientIdFromCloud(String email) {
        try {
            List<JsonObject> result = SQLiteCloudClient.execute(
                "SELECT client_id FROM users WHERE email = ?", email);
            if (!result.isEmpty() && result.get(0).has("client_id") 
                    && !result.get(0).get("client_id").isJsonNull()) {
                return result.get(0).get("client_id").getAsString();
            }
        } catch (IOException e) {
            LOGGER.warning("getClientIdFromCloud failed for " + email + ": " + e.getMessage());
        }
        return null;
    }
    
    public static void setClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) return;
        currentClientId = clientId;
        if (!clientId.equals(SQLiteCloudClient.getCurrentClientId())) {
            SQLiteCloudClient.setCurrentUser(currentUserEmail, currentUsername, clientId);
        }
        LOGGER.info("ClientId set: " + clientId);
    }

    public static String getClientId() {
        if (currentClientId == null) {
            currentClientId = SQLiteCloudClient.getCurrentClientId();
        }
        return currentClientId;
    }

    // ================ INVENTORY METHODS ================
    
    public static boolean logInventoryAction(int productId, String action, int quantity) throws SQLException {
        return logInventoryAction(productId, action, quantity, 0);
    }

    public static boolean logInventoryAction(int productId, String action, int quantity, int previousQuantity) throws SQLException {
        String sql = "INSERT INTO inventory_log (product_id, action, quantity, previous_quantity, date, client_id, user_email) " +
                     "VALUES (?, ?, ?, ?, date('now','localtime'), ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setString(2, action);
            stmt.setInt(3, quantity);
            stmt.setInt(4, previousQuantity);
            stmt.setString(5, currentClientId);
            stmt.setString(6, currentUserEmail);
            return stmt.executeUpdate() > 0;
        }
    }

    public static boolean logInventoryAction(int productId, String action, int quantity, int previousQuantity, String user) throws SQLException {
        String sql = "INSERT INTO inventory_log (product_id, action, quantity, previous_quantity, date, client_id, user_email) " +
                     "VALUES (?, ?, ?, ?, date('now','localtime'), ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setString(2, action + (user != null ? " by " + user : ""));
            stmt.setInt(3, quantity);
            stmt.setInt(4, previousQuantity);
            stmt.setString(5, currentClientId);
            stmt.setString(6, currentUserEmail);
            return stmt.executeUpdate() > 0;
        }
    }

    // ================ SALES METHODS ================
    
    public static void saveTransaction(BigDecimal total, List<SaleItem> items,
            BigDecimal discount, String username) throws SQLException {
if (total == null || items == null || items.isEmpty())
throw new IllegalArgumentException("Total and sale items cannot be null or empty");
if (discount == null) discount = BigDecimal.ZERO;
if (discount.compareTo(total) > 0)
throw new IllegalArgumentException("Discount cannot exceed total");

Connection transConn = null;
try {
transConn = createTransactionConnection();

// STEP 1: Validate stock
for (SaleItem item : items) {
try (PreparedStatement ps = transConn.prepareStatement(
"SELECT name, stock_quantity FROM products WHERE id = ? AND client_id = ?")) {
ps.setInt(1, item.productId);
ps.setString(2, currentClientId);
ResultSet rs = ps.executeQuery();
if (!rs.next()) throw new SQLException("Product not found: " + item.productId);
int available = rs.getInt("stock_quantity");
String productName = rs.getString("name");
if (available < item.quantity)
throw new SQLException("Insufficient stock for: " + productName +
" (available: " + available + ", requested: " + item.quantity + ")");
}
}

// STEP 2: Insert sale header
int saleId;
try (PreparedStatement stmt = transConn.prepareStatement(
"INSERT INTO sales (sales_total, discount, sale_date, user, client_id, user_email) " +
"VALUES (?, ?, date('now','localtime'), ?, ?, ?)",
Statement.RETURN_GENERATED_KEYS)) {
stmt.setBigDecimal(1, total);
stmt.setBigDecimal(2, discount);
stmt.setString(3, username);
stmt.setString(4, currentClientId);
stmt.setString(5, currentUserEmail);
stmt.executeUpdate();
ResultSet rs = stmt.getGeneratedKeys();
if (!rs.next()) throw new SQLException("Failed to get sale ID");
saleId = rs.getInt(1);
}

// STEP 3: Insert items, update stock, log flows
String itemSql  = "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, total_price, client_id, user_email) VALUES (?, ?, ?, ?, ?, ?, ?)";
String stockSql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND client_id = ?";
String flowSql  = "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, user, client_id, user_email) VALUES (?, 'SOLD', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?)";
String invSql   = "INSERT INTO inventory_log (product_id, action, quantity, previous_quantity, date, client_id, user_email) VALUES (?, 'SOLD', ?, ?, date('now','localtime'), ?, ?)";

List<int[]> soldItems = new ArrayList<>(); // [productId, quantity, previousStock]

try (PreparedStatement itemStmt  = transConn.prepareStatement(itemSql);
PreparedStatement stockStmt = transConn.prepareStatement(stockSql);
PreparedStatement flowStmt  = transConn.prepareStatement(flowSql);
PreparedStatement invStmt   = transConn.prepareStatement(invSql)) {

for (SaleItem item : items) {
int previousStock = 0;
try (PreparedStatement ps = transConn.prepareStatement(
"SELECT stock_quantity FROM products WHERE id = ? AND client_id = ?")) {
ps.setInt(1, item.productId);
ps.setString(2, currentClientId);
ResultSet rs = ps.executeQuery();
if (rs.next()) previousStock = rs.getInt("stock_quantity");
}

BigDecimal lineTotal = item.price.multiply(BigDecimal.valueOf(item.quantity));

itemStmt.setInt(1, saleId);
itemStmt.setInt(2, item.productId);
itemStmt.setInt(3, item.quantity);
itemStmt.setBigDecimal(4, item.price);
itemStmt.setBigDecimal(5, lineTotal);
itemStmt.setString(6, currentClientId);
itemStmt.setString(7, currentUserEmail);
itemStmt.addBatch();

stockStmt.setInt(1, item.quantity);
stockStmt.setInt(2, item.productId);
stockStmt.setString(3, currentClientId);
stockStmt.addBatch();

flowStmt.setInt(1, item.productId);
flowStmt.setInt(2, item.quantity);
flowStmt.setString(3, username);
flowStmt.setString(4, currentClientId);
flowStmt.setString(5, currentUserEmail);
flowStmt.addBatch();

invStmt.setInt(1, item.productId);
invStmt.setInt(2, item.quantity);
invStmt.setInt(3, previousStock);
invStmt.setString(4, currentClientId);
invStmt.setString(5, currentUserEmail);
invStmt.addBatch();

soldItems.add(new int[]{item.productId, item.quantity, previousStock});
}

itemStmt.executeBatch();
stockStmt.executeBatch();
flowStmt.executeBatch();
invStmt.executeBatch();
}

// Queue sync inside same transaction
try (PreparedStatement queueStmt = transConn.prepareStatement(
"INSERT INTO sync_queue (user_email, client_id, table_name, record_id, operation, status) " +
"VALUES (?, ?, 'sales', ?, 'INSERT', 'pending')")) {
queueStmt.setString(1, currentUserEmail);
queueStmt.setString(2, currentClientId);
queueStmt.setInt(3, saleId);
queueStmt.executeUpdate();
}

transConn.commit();
LOGGER.info("Transaction committed locally: saleId=" + saleId + " total=" + total);

// Background cloud sync — pushes sale + sale_items + item_flows + stock update
final int finalSaleId = saleId;
final List<SaleItem> finalItems = new ArrayList<>(items);
final BigDecimal finalDiscount = discount;
final List<int[]> finalSoldItems = soldItems;

if (isOnline()) {
new Thread(() -> {
try {
// Push sale header
SQLiteCloudClient.executeUpdate(
"INSERT OR REPLACE INTO sales " +
"(id, sales_total, discount, sale_date, user, client_id, user_email, updated_at) " +
"VALUES (?, ?, ?, date('now','localtime'), ?, ?, ?, CURRENT_TIMESTAMP)",
finalSaleId, total, finalDiscount, username, currentClientId, currentUserEmail);

// Push each sale_item
for (SaleItem item : finalItems) {
BigDecimal lineTotal = item.price.multiply(BigDecimal.valueOf(item.quantity));
SQLiteCloudClient.executeUpdate(
    "INSERT OR IGNORE INTO sale_items " +
    "(sale_id, product_id, quantity, unit_price, total_price, client_id, user_email) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?)",
    finalSaleId, item.productId, item.quantity,
    item.price, lineTotal, currentClientId, currentUserEmail);
}

// Push item_flows (SOLD entries) and update stock in cloud
for (int[] sold : finalSoldItems) {
int productId = sold[0];
int quantity  = sold[1];

SQLiteCloudClient.executeUpdate(
    "INSERT INTO item_flows " +
    "(product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
    "VALUES (?, 'SOLD', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?)",
    productId, quantity, username, currentClientId, currentUserEmail);

SQLiteCloudClient.executeUpdate(
    "UPDATE products SET stock_quantity = stock_quantity - ?, " +
    "updated_at = CURRENT_TIMESTAMP " +
    "WHERE id = ? AND client_id = ?",
    quantity, productId, currentClientId);
}

// Mark queue item synced
try (Connection conn = getConnection();
 PreparedStatement ps = conn.prepareStatement(
     "UPDATE sync_queue SET status='processed' " +
     "WHERE table_name='sales' AND record_id=? AND user_email=?")) {
ps.setInt(1, finalSaleId);
ps.setString(2, currentUserEmail);
ps.executeUpdate();
} catch (SQLException ignored) {}

LOGGER.info("Sale synced to cloud: saleId=" + finalSaleId);
} catch (Exception e) {
LOGGER.warning("Cloud sale sync failed for saleId=" + finalSaleId + ": " + e.getMessage());
}
}, "sync-sale-" + saleId).start();
}

} catch (SQLException e) {
if (transConn != null) { try { transConn.rollback(); } catch (SQLException ignored) {} }
LOGGER.log(Level.SEVERE, "saveTransaction failed — rolled back", e);
throw e;
} finally {
if (transConn != null) {
try { transConn.setAutoCommit(true); transConn.close(); }
catch (SQLException ignored) {}
}
}
}

    public static List<SalesData> getSalesData(String startDate, String endDate) throws SQLException {
        List<SalesData> data = new ArrayList<>();
        String currentClientId = getCurrentClientId();
        String sql = "SELECT DATE(sale_date) as sale_date, SUM(sales_total) as total, SUM(sales_total) as profit " +
                "FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ? GROUP BY DATE(sale_date)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                data.add(new SalesData(fromDatabaseDate(rs.getString("sale_date")), 
                                       rs.getBigDecimal("total"), 
                                       rs.getBigDecimal("profit")));
            }
        }
        return data;
    }

    public static BigDecimal getTotalSales(String startDate, String endDate) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(sales_total), 0) FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static BigDecimal getTotalDiscounts(String startDate, String endDate) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(discount), 0) FROM sales WHERE DATE(sale_date) BETWEEN ? AND ? AND client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static BigDecimal getDailyDiscount(String date) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(discount), 0) FROM sales WHERE DATE(sale_date) = ? AND client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static List<TransactionDetail> getDailyTransactions(String date) throws SQLException {
        List<TransactionDetail> transactions = new ArrayList<>();
        String sql = "SELECT p.name AS item_name, si.quantity, si.unit_price, p.cost_price AS buying_price " +
                     "FROM sale_items si JOIN sales s ON si.sale_id = s.id JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) = ? AND s." + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(new TransactionDetail(
                    rs.getString("item_name"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("buying_price")
                ));
            }
        }
        return transactions;
    }

    public static String getUserEmail(String username) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT email FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("email") : null;
        }
    }

    public static String getUserCreatedDate(String username) throws SQLException {
        String[] cols = {"created_date", "created_at", "updated_at"};
        for (String col : cols) {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT " + col + " FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String val = rs.getString(1);
                    if (val != null && !val.isEmpty()) return val;
                }
            } catch (SQLException ignored) {}
        }
        return "N/A";
    }

    public static List<String> getUsersByClientId(String clientId) throws SQLException {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users WHERE client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, clientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) users.add(rs.getString("username"));
        }
        return users;
    }

    public static String getClientIdForUser(String username) throws SQLException {
        String sql = "SELECT client_id FROM users WHERE username = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("client_id");
        }
        return null;
    }

    public static List<String> getRecentUserActivity(String username, int limit) throws SQLException {
        List<String> activities = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT activity || ' - ' || timestamp FROM activity_log WHERE username = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, username);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) activities.add(rs.getString(1));
        }
        return activities;
    }

    public static void clearOtherSessions(String username) throws SQLException {}

    // ================ EXPENSE METHODS ================
    
    public static BigDecimal getDailyExpense(String date) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM daily_expenses WHERE DATE(expense_date) = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static BigDecimal getDailyExpenseFromDatabase(String date) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM daily_expenses WHERE DATE(expense_date) = ? AND client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static BigDecimal getTotalExpenses(String startDate, String endDate) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM daily_expenses WHERE DATE(expense_date) BETWEEN ? AND ? AND client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static void addOrUpdateMonthlyExpense(String date, String description,
            BigDecimal amount) throws SQLException {

        // 1. Delete old local record
        String deleteSql = "DELETE FROM daily_expenses WHERE expense_date = ? AND description = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(deleteSql)) {
            stmt.setString(1, date);
            stmt.setString(2, description);
            setClientParam(stmt, 3);
            stmt.executeUpdate();
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Also delete from cloud if amount is zero/negative
            if (isOnline()) {
                new Thread(() -> {
                    try {
                        SQLiteCloudClient.executeUpdate(
                            "DELETE FROM daily_expenses WHERE DATE(expense_date) = ? " +
                            "AND description = ? AND client_id = ?",
                            date, description, currentClientId);
                    } catch (Exception e) {
                        LOGGER.warning("Cloud expense delete failed: " + e.getMessage());
                    }
                }, "sync-del-expense").start();
            }
            return;
        }

        // 2. Insert new local record
        String insertSql = "INSERT INTO daily_expenses (expense_date, amount, description, client_id, user_email) " +
                           "VALUES (?, ?, ?, ?, ?)";
        int newId = -1;
        try (PreparedStatement stmt = getConnection().prepareStatement(insertSql,
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, date);
            stmt.setBigDecimal(2, amount);
            stmt.setString(3, description);
            stmt.setString(4, currentClientId);
            stmt.setString(5, currentUserEmail);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) newId = rs.getInt(1);
        }

        // 3. Sync to cloud
        final BigDecimal fAmount = amount;
        final int fId = newId;
        if (isOnline()) {
            new Thread(() -> {
                try {
                    // Remove stale cloud record first
                    SQLiteCloudClient.executeUpdate(
                        "DELETE FROM daily_expenses WHERE DATE(expense_date) = ? " +
                        "AND description = ? AND client_id = ?",
                        date, description, currentClientId);

                    // Insert fresh record
                    SQLiteCloudClient.executeUpdate(
                        "INSERT INTO daily_expenses " +
                        "(expense_date, amount, description, client_id, user_email, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        date, fAmount, description, currentClientId, currentUserEmail);

                    LOGGER.info("Expense synced to cloud: " + description + " = " + fAmount);
                } catch (Exception e) {
                    LOGGER.warning("Cloud expense sync failed — queuing: " + e.getMessage());
                    SQLiteCloudClient.queueSyncOperation("daily_expenses", fId, "INSERT",
                        new com.google.gson.JsonObject());
                }
            }, "sync-expense").start();
        } else {
            SQLiteCloudClient.queueSyncOperation("daily_expenses", fId, "INSERT",
                new com.google.gson.JsonObject());
        }
    }

    public static Map<String, BigDecimal> getMonthlyExpensesByCategory(String yearMonth) throws SQLException {
        Map<String, BigDecimal> expenses = new HashMap<>();
        String sql = "SELECT description, SUM(amount) as total FROM daily_expenses WHERE strftime('%Y-%m', expense_date) = ? AND " + whereClient() + " GROUP BY description";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, yearMonth);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                expenses.put(rs.getString("description"), rs.getBigDecimal("total"));
            }
        }
        return expenses;
    }

    // ================ COST OF GOODS METHODS ================
    
    public static BigDecimal getCostOfGoods(String date) throws SQLException {
        String sql = "SELECT COALESCE(SUM(si.quantity * p.cost_price), 0) FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) = ? AND s." + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public static BigDecimal getCostOfGoods(String startDate, String endDate) throws SQLException {
        String currentClientId = getCurrentClientId();
        String sql = "SELECT COALESCE(SUM(si.quantity * p.cost_price), 0) FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    // ================ ITEM FLOW METHODS ================
    
    public static List<ItemFlow> getItemFlowHistory() throws SQLException {
        List<ItemFlow> flows = new ArrayList<>();
        String sql = "SELECT ifl.product_id, p.name as product, ifl.action, ifl.quantity, DATE(ifl.flow_date) as flow_date, ifl.vendor, ifl.user " +
                     "FROM item_flows ifl LEFT JOIN products p ON ifl.product_id = p.id WHERE ifl." + whereClient() + " ORDER BY ifl.created_date DESC";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            setClientParam(stmt, 1);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                flows.add(new ItemFlow(rs.getInt("product_id"), rs.getString("product"), rs.getString("action"),
                    rs.getInt("quantity"), fromDatabaseDate(rs.getString("flow_date")), null, rs.getString("vendor"), rs.getString("user")));
            }
        }
        return flows;
    }

    public static void insertReceivedProductFlow(int productId, String productName,
            int quantity, int oldStock, int newStock, String vendor, String user) throws SQLException {

        // 1. Insert locally
        String sql = "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, vendor, user, client_id, user_email) " +
                     "VALUES (?, 'RECEIVED', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, quantity);
            stmt.setString(3, vendor);
            stmt.setString(4, user);
            stmt.setString(5, currentClientId);
            stmt.setString(6, currentUserEmail);
            stmt.executeUpdate();
        }

        // 2. Update stock locally
        try (PreparedStatement stmt = getConnection().prepareStatement(
                "UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND client_id = ?")) {
            stmt.setInt(1, newStock);
            stmt.setInt(2, productId);
            stmt.setString(3, currentClientId);
            stmt.executeUpdate();
        }

        // 3. Sync to cloud immediately if online, else queue
        if (isOnline()) {
            new Thread(() -> {
                try {
                    // Push the item_flow
                    SQLiteCloudClient.executeUpdate(
                        "INSERT INTO item_flows " +
                        "(product_id, action, quantity, flow_date, created_date, vendor, user, client_id, user_email) " +
                        "VALUES (?, 'RECEIVED', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?, ?)",
                        productId, quantity, vendor, user, currentClientId, currentUserEmail);

                    // Update stock in cloud
                    SQLiteCloudClient.executeUpdate(
                        "UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = ? AND client_id = ?",
                        newStock, productId, currentClientId);

                    LOGGER.info("Received flow synced to cloud: productId=" + productId + " qty=" + quantity);
                } catch (Exception e) {
                    LOGGER.warning("Cloud receive sync failed — queuing: " + e.getMessage());
                    SQLiteCloudClient.queueSyncOperation("item_flows", productId, "INSERT",
                        new com.google.gson.JsonObject());
                }
            }, "sync-receive-" + productId).start();
        } else {
            SQLiteCloudClient.queueSyncOperation("item_flows", productId, "INSERT",
                new com.google.gson.JsonObject());
        }
    }

    public static void insertSoldProductFlow(int productId, String productName, int quantity, int oldStock, int newStock, String user) throws SQLException {
        String sql = "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
                     "VALUES (?, 'SOLD', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, productId);
            stmt.setInt(2, quantity);
            stmt.setString(3, user);
            stmt.setString(4, getCurrentClientId());
            stmt.setString(5, getCurrentUserEmail());
            stmt.executeUpdate();
        }
    }

    // ================ RECEIPT METHODS ================
    
    public static List<ReceiveEntry> getReceivedEntries(String date) throws SQLException {
        List<ReceiveEntry> entries = new ArrayList<>();
        String currentClientId = getCurrentClientId();
        String sql = "SELECT p.name as product, c.name as category, p.cost_price, p.selling_price, ifl.quantity as stock_quantity, DATE(ifl.flow_date) as created_date " +
                     "FROM item_flows ifl JOIN products p ON ifl.product_id = p.id LEFT JOIN categories c ON p.category_id = c.id " +
                     "WHERE ifl.action = 'RECEIVED' AND DATE(ifl.flow_date) = ? AND p.client_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, currentClientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(new ReceiveEntry(rs.getString("product"), rs.getString("category"),
                    rs.getBigDecimal("cost_price"), rs.getBigDecimal("selling_price"),
                    rs.getInt("stock_quantity"), fromDatabaseDate(rs.getString("created_date"))));
            }
        }
        return entries;
    }

    // ================ DAILY SALES REPORT METHODS ================
    
    public static void saveDailySalesReport(DailySalesReport report) throws SQLException {
        String sql = "INSERT OR REPLACE INTO daily_sales_reports " +
                     "(report_date, total_gross_sale, discounts, cost_of_goods, daily_expenses, " +
                     "net_sale, gross_profit, net_profit, client_id, user_email) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, toDatabaseDate(report.date));
            stmt.setBigDecimal(2, report.totalGrossSale);
            stmt.setBigDecimal(3, report.discounts);
            stmt.setBigDecimal(4, report.costOfGoods);
            stmt.setBigDecimal(5, report.dailyExpenses);
            stmt.setBigDecimal(6, report.netSale);
            stmt.setBigDecimal(7, report.grossProfit);
            stmt.setBigDecimal(8, report.netProfit);
            stmt.setString(9, currentClientId);
            stmt.setString(10, currentUserEmail);
            stmt.executeUpdate();
        }

        // Sync to cloud
        if (isOnline()) {
            new Thread(() -> {
                try {
                    SQLiteCloudClient.executeUpdate(
                        "INSERT OR REPLACE INTO daily_sales_reports " +
                        "(report_date, total_gross_sale, discounts, cost_of_goods, daily_expenses, " +
                        "net_sale, gross_profit, net_profit, client_id, user_email, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                        toDatabaseDate(report.date),
                        report.totalGrossSale, report.discounts, report.costOfGoods,
                        report.dailyExpenses, report.netSale, report.grossProfit, report.netProfit,
                        currentClientId, currentUserEmail);
                    LOGGER.info("Daily sales report synced to cloud: " + report.date);
                } catch (Exception e) {
                    LOGGER.warning("Cloud daily report sync failed: " + e.getMessage());
                }
            }, "sync-daily-report").start();
        }
    }

    public static DailySalesReport getDailySalesReport(String date) throws SQLException {
        String sql = "SELECT * FROM daily_sales_reports WHERE report_date = ? AND " + whereClient();
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, date);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                DailySalesReport report = new DailySalesReport();
                report.date = fromDatabaseDate(rs.getString("report_date"));
                report.totalGrossSale = rs.getBigDecimal("total_gross_sale");
                report.discounts = rs.getBigDecimal("discounts");
                report.costOfGoods = rs.getBigDecimal("cost_of_goods");
                report.dailyExpenses = rs.getBigDecimal("daily_expenses");
                report.netSale = rs.getBigDecimal("net_sale");
                report.grossProfit = rs.getBigDecimal("gross_profit");
                report.netProfit = rs.getBigDecimal("net_profit");
                return report;
            }
        }
        return null;
    }

    // ================ TOP SELLING PRODUCTS ================
    
    public static List<TopSellingProduct> getTopSellingProducts(String startDate, String endDate, int limit) throws SQLException {
        List<TopSellingProduct> products = new ArrayList<>();
        String currentClientId = getCurrentClientId();
        String sql = "SELECT p.id, p.name, SUM(si.quantity) as total_quantity, SUM(si.total_price) as total_revenue " +
                     "FROM sale_items si JOIN sales s ON si.sale_id = s.id JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) BETWEEN ? AND ? AND s.client_id = ? GROUP BY p.id ORDER BY total_quantity DESC LIMIT ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);
            stmt.setString(3, currentClientId);
            stmt.setInt(4, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                products.add(new TopSellingProduct(rs.getInt("id"), rs.getString("name"),
                    rs.getInt("total_quantity"), rs.getBigDecimal("total_revenue")));
            }
        }
        return products;
    }

    public static int getRoleCount(String role) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE privileges = ? AND " + whereClient();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role);
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ================ PAGINATION METHODS ================
    
    public static List<Product> getProductsPaginated(int offset, int limit) throws SQLException {
        List<Product> products = new ArrayList<>();
        String sql;
        PreparedStatement stmt;
        Connection conn = getConnection();
        
        if (currentClientId != null && !currentClientId.isEmpty()) {
            sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level FROM products WHERE client_id = ? ORDER BY name LIMIT ? OFFSET ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentClientId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
        } else {
            sql = "SELECT id, name, cost_price, selling_price, stock_quantity, min_stock_level FROM products WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ? ORDER BY name LIMIT ? OFFSET ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentUserEmail);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
        }
        
        try (stmt) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Product p = new Product();
                p.setId(rs.getInt("id"));
                p.setName(rs.getString("name"));
                p.setCostPrice(rs.getBigDecimal("cost_price"));
                p.setSellingPrice(rs.getBigDecimal("selling_price"));
                p.setStockQuantity(rs.getInt("stock_quantity"));
                p.setMinStockLevel(rs.getInt("min_stock_level"));
                products.add(p);
            }
        }
        return products;
    }

    public static int getTotalProductCount() throws SQLException {
        String sql;
        PreparedStatement stmt;
        Connection conn = getConnection();
        
        if (currentClientId != null && !currentClientId.isEmpty()) {
            sql = "SELECT COUNT(*) FROM products WHERE client_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentClientId);
        } else {
            sql = "SELECT COUNT(*) FROM products WHERE (client_id IS NULL OR client_id = '' OR client_id = 'null') AND user_email = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentUserEmail);
        }
        
        try (stmt) {
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ================ UTILITY METHODS ================
    
    public static void closeConnection() {
        // Don't actually close - just log that it's kept open
        LOGGER.fine("Connection kept open for reuse");
    }

    public static void shutdownConnection() {
        synchronized (connectionLock) {
            try {
                if (persistentConnection != null && !persistentConnection.isClosed()) {
                    persistentConnection.close();
                    LOGGER.info("Database connection closed");
                    persistentConnection = null;
                }
            } catch (SQLException e) { 
                LOGGER.log(Level.WARNING, "Error closing on shutdown", e); 
            }
        }
        SQLiteCloudClient.stopBackgroundSync();
        SQLiteCloudClient.closeLocalConnection();
    }

    public static void closeTransactionResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                Statement stmt = rs.getStatement();
                rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing result set", e);
            }
        }
    }

    public static void syncUserDataFromCloud(String email) {
        try {
            if (!checkTursoConnection()) {
                LOGGER.warning("No internet connection - using local data only");
                return;
            }
            pullUserDataFromCloud(email);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cloud sync failed", e);
        }
    }

    public static void clearCurrentUser() {
        currentUserEmail = null;
        currentUsername = null;
        currentUserDbFile = null;
        currentClientId = null;
        cloudMigrationDone = false;
        autoCreatedVendors.clear();
        autoCreatedCategories.clear();
        LOGGER.info("Current user cleared");
    }
    
    public static void recomputeAllStockQuantities() throws SQLException {
        String clientId = currentClientId;
        if (clientId == null || clientId.isEmpty()) return;
        
        try (Connection conn = getConnection()) {
            List<Integer> productIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM products WHERE client_id = ?")) {
                ps.setString(1, clientId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) productIds.add(rs.getInt(1));
            }
            
            String sql = 
                "SELECT COALESCE(SUM(CASE WHEN UPPER(TRIM(action)) IN ('NEW ITEM','RECEIVED','RECEIVE','RETURN','RETURNED') THEN quantity " +
                "WHEN UPPER(TRIM(action)) IN ('SOLD','SALE','SELL') THEN -quantity " +
                "ELSE 0 END), 0) as net_stock " +
                "FROM item_flows " +
                "WHERE product_id = ? AND (client_id = ? OR client_id IS NULL)";
            
            String updateSql = "UPDATE products SET stock_quantity = ? WHERE id = ?";
            try (PreparedStatement selectStmt = conn.prepareStatement(sql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                for (int productId : productIds) {
                    selectStmt.setInt(1, productId);
                    selectStmt.setString(2, clientId);
                    ResultSet rs = selectStmt.executeQuery();
                    int stock = rs.next() ? Math.max(0, rs.getInt("net_stock")) : 0;
                    updateStmt.setInt(1, stock);
                    updateStmt.setInt(2, productId);
                    updateStmt.addBatch();
                }
                updateStmt.executeBatch();
            }
            
            LOGGER.info("Recomputed stock for " + productIds.size() + " products");
        }
    }
    
    //refund sales function
 // Add to Database.java - Refund Processing Methods

    public static boolean processRefund(int saleItemId, int productId, int quantityRefunded,
            BigDecimal refundAmount, String username) throws SQLException {

        Connection transConn = null;
        try {
            transConn = createTransactionConnection();

            // 1. Verify sale_item exists and get sale_id
            int saleId = -1;
            try (PreparedStatement ps = transConn.prepareStatement(
                    "SELECT sale_id FROM sale_items WHERE id = ?")) {
                ps.setInt(1, saleItemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) saleId = rs.getInt("sale_id");
                else throw new SQLException("Sale item not found: id=" + saleItemId);
            }

            // 2. Get current sale total
            BigDecimal currentTotal = BigDecimal.ZERO;
            try (PreparedStatement ps = transConn.prepareStatement(
                    "SELECT sales_total FROM sales WHERE id = ?")) {
                ps.setInt(1, saleId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) currentTotal = rs.getBigDecimal("sales_total");
            }

            // 3. Get current sale_item quantity
            int currentQuantity = 0;
            try (PreparedStatement ps = transConn.prepareStatement(
                    "SELECT quantity FROM sale_items WHERE id = ?")) {
                ps.setInt(1, saleItemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) currentQuantity = rs.getInt("quantity");
            }
            int newQuantity = currentQuantity - quantityRefunded;

            // 4. Get current stock BEFORE update (for inventory_log previous_quantity)
            final int stockBeforeRefund;
            try (PreparedStatement ps = transConn.prepareStatement(
                    "SELECT stock_quantity FROM products WHERE id = ?")) {
                ps.setInt(1, productId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stockBeforeRefund = rs.getInt("stock_quantity");
                } else {
                    throw new SQLException("Product not found: id=" + productId);
                }
            }

            // 5. Update or delete the sale_item
            if (newQuantity <= 0) {
                try (PreparedStatement ps = transConn.prepareStatement(
                        "DELETE FROM sale_items WHERE id = ?")) {
                    ps.setInt(1, saleItemId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = transConn.prepareStatement(
                        "UPDATE sale_items SET quantity = ?, total_price = unit_price * ? WHERE id = ?")) {
                    ps.setInt(1, newQuantity);
                    ps.setInt(2, newQuantity);
                    ps.setInt(3, saleItemId);
                    ps.executeUpdate();
                }
            }

            // 6. Recompute sales total from remaining sale_items
            BigDecimal newTotal = BigDecimal.ZERO;
            try (PreparedStatement ps = transConn.prepareStatement(
                    "SELECT COALESCE(SUM(total_price), 0) FROM sale_items WHERE sale_id = ?")) {
                ps.setInt(1, saleId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) newTotal = rs.getBigDecimal(1);
            }
            try (PreparedStatement ps = transConn.prepareStatement(
                    "UPDATE sales SET sales_total = ? WHERE id = ?")) {
                ps.setBigDecimal(1, newTotal);
                ps.setInt(2, saleId);
                ps.executeUpdate();
            }

            // 7. Restore product stock
            try (PreparedStatement ps = transConn.prepareStatement(
                    "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?")) {
                ps.setInt(1, quantityRefunded);
                ps.setInt(2, productId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Stock restore failed — product id=" + productId + " not found");
                }
            }

            // 8. Log in item_flows
            try (PreparedStatement ps = transConn.prepareStatement(
                    "INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
                    "VALUES (?, 'REFUNDED', ?, date('now','localtime'), date('now','localtime'), ?, ?, ?)")) {
                ps.setInt(1, productId);
                ps.setInt(2, quantityRefunded);
                ps.setString(3, username);
                ps.setString(4, currentClientId);
                ps.setString(5, currentUserEmail);
                ps.executeUpdate();
            }

            // 9. Log in inventory_log
            try (PreparedStatement ps = transConn.prepareStatement(
                    "INSERT INTO inventory_log (product_id, action, quantity, previous_quantity, date, client_id, user_email) " +
                    "VALUES (?, 'REFUNDED', ?, ?, date('now','localtime'), ?, ?)")) {
                ps.setInt(1, productId);
                ps.setInt(2, quantityRefunded);
                ps.setInt(3, stockBeforeRefund);
                ps.setString(4, currentClientId);
                ps.setString(5, currentUserEmail);
                ps.executeUpdate();
            }

            // 10. Commit locally
            transConn.commit();
            LOGGER.info("✅ Refund saved locally: product=" + productId +
                        " qty=" + quantityRefunded + " newStock=" + (stockBeforeRefund + quantityRefunded));

            // 11. Queue for cloud sync
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO sync_queue (user_email, table_name, record_id, operation, status) " +
                    "VALUES (?, 'sales', ?, 'REFUND', 'pending')")) {
                ps.setString(1, currentUserEmail);
                ps.setInt(2, saleId);
                ps.executeUpdate();
            } catch (SQLException qe) {
                LOGGER.warning("Could not queue refund for sync: " + qe.getMessage());
            }

            // 12. Background cloud sync
            final int finalSaleId = saleId;
            final int finalSaleItemId = saleItemId;
            final BigDecimal finalNewTotal = newTotal;
            final int finalNewQuantity = newQuantity;

            if (isOnline()) {
                new Thread(() -> {
                    try {
                        String cid = currentClientId;

                        // 1. Update sales total in cloud
                        SQLiteCloudClient.executeUpdate(
                            "UPDATE sales SET sales_total = ? WHERE id = ? AND client_id = ?",
                            finalNewTotal, finalSaleId, cid);

                        // 2. Update or delete sale_item in cloud
                        if (finalNewQuantity <= 0) {
                            SQLiteCloudClient.executeUpdate(
                                "DELETE FROM sale_items WHERE id = ? AND client_id = ?",
                                finalSaleItemId, cid);
                        } else {
                            SQLiteCloudClient.executeUpdate(
                                "UPDATE sale_items SET quantity = ?, total_price = unit_price * ? " +
                                "WHERE id = ? AND client_id = ?",
                                finalNewQuantity, finalNewQuantity, finalSaleItemId, cid);
                        }

                        // 3. Restore product stock in cloud
                        SQLiteCloudClient.executeUpdate(
                            "UPDATE products SET stock_quantity = stock_quantity + ?, " +
                            "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND client_id = ?",
                            quantityRefunded, productId, cid);

                        // 4. Log refund flow in cloud item_flows
                        SQLiteCloudClient.executeUpdate(
                            "INSERT INTO item_flows " +
                            "(product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
                            "VALUES (?, 'REFUNDED', ?, datetime('now'), datetime('now'), ?, ?, ?)",
                            productId, quantityRefunded, username, cid, currentUserEmail);

                        // 5. Log refund in cloud inventory_log
                        SQLiteCloudClient.executeUpdate(
                            "INSERT INTO inventory_log " +
                            "(product_id, action, quantity, previous_quantity, date, client_id, user_email) " +
                            "VALUES (?, 'REFUNDED', ?, ?, date('now','localtime'), ?, ?)",
                            productId, quantityRefunded, stockBeforeRefund, cid, currentUserEmail);

                        // 6. Mark queue synced
                        try (PreparedStatement ps = getConnection().prepareStatement(
                                "UPDATE sync_queue SET status='processed' " +
                                "WHERE table_name='sales' AND record_id=? AND user_email=? " +
                                "AND operation='REFUND'")) {
                            ps.setInt(1, finalSaleId);
                            ps.setString(2, currentUserEmail);
                            ps.executeUpdate();
                        } catch (SQLException ignored) {}

                        LOGGER.info("Refund fully synced to cloud: saleId=" + finalSaleId);
                    } catch (Exception e) {
                        LOGGER.warning("Cloud refund sync failed — will retry: " + e.getMessage());
                    }
                }, "sync-refund-" + finalSaleId).start();
            }

            return true;

        } catch (SQLException e) {
            if (transConn != null) {
                try { transConn.rollback(); } catch (SQLException ignored) {}
            }
            LOGGER.log(Level.SEVERE, "processRefund failed — rolled back", e);
            throw e;
        } finally {
            if (transConn != null) {
                try { transConn.setAutoCommit(true); transConn.close(); } catch (SQLException ignored) {}
            }
        }
    }


//NEW: Direct cloud update method
private static void updateCloudRefund(int saleId, int saleItemId, int productId, int quantity,
              BigDecimal refundAmount, BigDecimal newTotal, int newQuantity, String username) throws IOException {

// 1. Update sales total in cloud
SQLiteCloudClient.executeUpdate(
"UPDATE sales SET sales_total = ? WHERE id = ? AND client_id = ?",
newTotal, saleId, currentClientId);

// 2. Update or delete sale_item in cloud
if (newQuantity <= 0) {
SQLiteCloudClient.executeUpdate(
"DELETE FROM sale_items WHERE id = ? AND client_id = ?",
saleItemId, currentClientId);
} else {
SQLiteCloudClient.executeUpdate(
"UPDATE sale_items SET quantity = ?, total_price = unit_price * ? WHERE id = ? AND client_id = ?",
newQuantity, newQuantity, saleItemId, currentClientId);
}

// 3. Restore product stock in cloud
SQLiteCloudClient.executeUpdate(
"UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ? AND client_id = ?",
quantity, productId, currentClientId);

// 4. Log refund in cloud item_flows
SQLiteCloudClient.executeUpdate(
"INSERT INTO item_flows (product_id, action, quantity, flow_date, created_date, user, client_id, user_email) " +
"VALUES (?, 'REFUNDED', ?, datetime('now'), datetime('now'), ?, ?, ?)",
productId, quantity, username, currentClientId, currentUserEmail);

LOGGER.info("Cloud updated: Sales total reduced by " + refundAmount);
}

//Simple method to queue refund for cloud sync (used when offline or cloud update fails)
private static void queueRefundForCloudSync(int saleItemId, int productId, int quantity, 
                    BigDecimal refundAmount, String username) {
try {
String sql = "INSERT INTO sync_queue (user_email, client_id, table_name, record_id, operation, data, status) " +
"VALUES (?, ?, 'refunds', ?, 'REFUND', ?, 'pending')";

String data = "{\"sale_item_id\":" + saleItemId + 
",\"product_id\":" + productId + 
",\"quantity\":" + quantity + 
",\"refund_amount\":" + refundAmount + 
",\"username\":\"" + username + "\"}";

try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
ps.setString(1, currentUserEmail);
ps.setString(2, currentClientId);
ps.setInt(3, saleItemId);
ps.setString(4, data);
ps.executeUpdate();
LOGGER.info("Refund queued for cloud sync");
}
} catch (SQLException e) {
LOGGER.warning("Failed to queue refund: " + e.getMessage());
}
}

public static void processPendingRefunds() {
if (!isOnline()) return;

String sql = "SELECT id, data FROM sync_queue WHERE table_name = 'refunds' AND status = 'pending' AND client_id = ?";

try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
ps.setString(1, currentClientId);
ResultSet rs = ps.executeQuery();

while (rs.next()) {
int queueId = rs.getInt("id");
String data = rs.getString("data");

int saleItemId = extractInt(data, "sale_item_id");
int productId = extractInt(data, "product_id");
int quantity = extractInt(data, "quantity");
BigDecimal refundAmount = extractBigDecimal(data, "refund_amount");
String username = extractString(data, "username");

try {
// First, get current cloud data to apply refund
processQueuedCloudRefund(saleItemId, productId, quantity, refundAmount, username);

try (PreparedStatement update = getConnection().prepareStatement("UPDATE sync_queue SET status = 'processed' WHERE id = ?")) {
update.setInt(1, queueId);
update.executeUpdate();
}
LOGGER.info("Refund synced to cloud: " + queueId);
} catch (Exception e) {
LOGGER.warning("Failed to sync refund: " + e.getMessage());
try (PreparedStatement update = getConnection().prepareStatement("UPDATE sync_queue SET status = 'failed' WHERE id = ?")) {
update.setInt(1, queueId);
update.executeUpdate();
}
}
}
} catch (SQLException e) {
LOGGER.warning("Error processing pending refunds: " + e.getMessage());
}
}

//Process queued refund to cloud
private static void processQueuedCloudRefund(int saleItemId, int productId, int quantity, 
                     BigDecimal refundAmount, String username) throws IOException {
// Get sale_id from sale_item
List<com.google.gson.JsonObject> items = SQLiteCloudClient.execute(
"SELECT sale_id FROM sale_items WHERE id = ? AND client_id = ?", saleItemId, currentClientId);
if (items.isEmpty()) throw new IOException("Sale item not found in cloud");
int saleId = items.get(0).get("sale_id").getAsInt();

// Get current sales total
List<com.google.gson.JsonObject> sales = SQLiteCloudClient.execute(
"SELECT sales_total FROM sales WHERE id = ? AND client_id = ?", saleId, currentClientId);
BigDecimal currentTotal = sales.get(0).get("sales_total").getAsBigDecimal();
BigDecimal newTotal = currentTotal.subtract(refundAmount);

// Update sales total
SQLiteCloudClient.executeUpdate("UPDATE sales SET sales_total = ? WHERE id = ? AND client_id = ?", 
            newTotal, saleId, currentClientId);

// Get current quantity
List<com.google.gson.JsonObject> saleItems = SQLiteCloudClient.execute(
"SELECT quantity FROM sale_items WHERE id = ? AND client_id = ?", saleItemId, currentClientId);
int currentQty = saleItems.get(0).get("quantity").getAsInt();
int newQty = currentQty - quantity;

if (newQty <= 0) {
SQLiteCloudClient.executeUpdate("DELETE FROM sale_items WHERE id = ? AND client_id = ?", 
                saleItemId, currentClientId);
} else {
SQLiteCloudClient.executeUpdate(
"UPDATE sale_items SET quantity = ?, total_price = unit_price * ? WHERE id = ? AND client_id = ?",
newQty, newQty, saleItemId, currentClientId);
}

// Restore stock
SQLiteCloudClient.executeUpdate("UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ? AND client_id = ?",
            quantity, productId, currentClientId);

LOGGER.info("Queued refund processed in cloud");
}


// Helper to extract int from simple JSON string
private static int extractInt(String json, String key) {
    String search = "\"" + key + "\":";
    int start = json.indexOf(search);
    if (start == -1) return -1;
    start += search.length();
    int end = json.indexOf(",", start);
    if (end == -1) end = json.indexOf("}", start);
    try {
        return Integer.parseInt(json.substring(start, end).trim());
    } catch (NumberFormatException e) {
        return -1;
    }
}

// Helper to extract BigDecimal
private static BigDecimal extractBigDecimal(String json, String key) {
    String search = "\"" + key + "\":";
    int start = json.indexOf(search);
    if (start == -1) return BigDecimal.ZERO;
    start += search.length();
    int end = json.indexOf(",", start);
    if (end == -1) end = json.indexOf("}", start);
    try {
        return new BigDecimal(json.substring(start, end).trim());
    } catch (NumberFormatException e) {
        return BigDecimal.ZERO;
    }
}

// Helper to extract String
private static String extractString(String json, String key) {
    String search = "\"" + key + "\":\"";
    int start = json.indexOf(search);
    if (start == -1) return "";
    start += search.length();
    int end = json.indexOf("\"", start);
    if (end == -1) return "";
    return json.substring(start, end);
}

// Simple cloud refund processing
private static void processCloudRefund(int saleItemId, int productId, int quantity, 
                                        BigDecimal refundAmount, String username) throws IOException {
    // Get sale_id
    List<com.google.gson.JsonObject> items = SQLiteCloudClient.execute(
        "SELECT sale_id FROM sale_items WHERE id = ? AND client_id = ?", saleItemId, currentClientId);
    if (items.isEmpty()) throw new IOException("Sale item not found");
    int saleId = items.get(0).get("sale_id").getAsInt();
    
    // Update sale total
    List<com.google.gson.JsonObject> sales = SQLiteCloudClient.execute(
        "SELECT sales_total FROM sales WHERE id = ? AND client_id = ?", saleId, currentClientId);
    BigDecimal currentTotal = sales.get(0).get("sales_total").getAsBigDecimal();
    BigDecimal newTotal = currentTotal.subtract(refundAmount);
    SQLiteCloudClient.executeUpdate("UPDATE sales SET sales_total = ? WHERE id = ?", newTotal, saleId);
    
    // Update or delete sale_item
    List<com.google.gson.JsonObject> saleItems = SQLiteCloudClient.execute(
        "SELECT quantity FROM sale_items WHERE id = ?", saleItemId);
    int currentQty = saleItems.get(0).get("quantity").getAsInt();
    int newQty = currentQty - quantity;
    
    if (newQty <= 0) {
        SQLiteCloudClient.executeUpdate("DELETE FROM sale_items WHERE id = ?", saleItemId);
    } else {
        SQLiteCloudClient.executeUpdate("UPDATE sale_items SET quantity = ?, total_price = unit_price * ? WHERE id = ?", 
                                         newQty, newQty, saleItemId);
    }
    
    // Restore stock
    SQLiteCloudClient.executeUpdate("UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?", quantity, productId);
    
    LOGGER.info("Cloud refund processed");
}

    // Method to get sale items for refund selection
    public static List<SaleItemForRefund> getSaleItemsForRefund(LocalDate date) throws SQLException {
        List<SaleItemForRefund> items = new ArrayList<>();
        String sql = "SELECT si.id as sale_item_id, si.product_id, p.name as product_name, " +
                     "si.quantity, si.unit_price, si.total_price, s.sale_date " +
                     "FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "JOIN products p ON si.product_id = p.id " +
                     "WHERE DATE(s.sale_date) = ? AND s." + whereClient();
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, toDatabaseDate(date));
            setClientParam(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                SaleItemForRefund item = new SaleItemForRefund();
                item.saleItemId = rs.getInt("sale_item_id");
                item.productId = rs.getInt("product_id");
                item.productName = rs.getString("product_name");
                item.quantity = rs.getInt("quantity");
                item.unitPrice = rs.getBigDecimal("unit_price");
                item.totalPrice = rs.getBigDecimal("total_price");
                items.add(item);
            }
        }
        return items;
    }

    // Inner class for refund items
    public static class SaleItemForRefund {
        public int saleItemId;
        public int productId;
        public String productName;
        public int quantity;
        public BigDecimal unitPrice;
        public BigDecimal totalPrice;
    }
    //end of salesrefund 
    

    // ================ INNER CLASSES ================
    
    public static class Product {
        private int id;
        public String name;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private int stockQuantity;
        private int minStockLevel;
        private int categoryId;
        private String vendor;
        private LocalDate expireDate;
        private LocalDate createdDate;
        private String clientId;
        
        public Product() {}
        
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        public BigDecimal getSellingPrice() { return sellingPrice; }
        public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
        public int getStockQuantity() { return stockQuantity; }
        public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
        public int getMinStockLevel() { return minStockLevel; }
        public void setMinStockLevel(int minStockLevel) { this.minStockLevel = minStockLevel; }
        public int getCategoryId() { return categoryId; }
        public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public LocalDate getExpireDate() { return expireDate; }
        public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
        public LocalDate getCreatedDate() { return createdDate; }
        public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
    }

    public static class Category {
        private int id;
        public String name;
        private String description;
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class SaleItem {
        public int productId;
        public int quantity;
        public BigDecimal price;
        public LocalDate date;
        public SaleItem() {}
        public SaleItem(int productId, int quantity, BigDecimal price) {
            this.productId = productId; this.quantity = quantity; this.price = price; this.date = LocalDate.now();
        }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
    }

    public static class SalesData {
        public LocalDate date;
        public BigDecimal total;
        public BigDecimal profit;
        public SalesData(LocalDate date, BigDecimal total, BigDecimal profit) {
            this.date = date; this.total = total; this.profit = profit;
        }
        public LocalDate getDate() { return date; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getProfit() { return profit; }
    }

    public static class ReceiveEntry {
        public String product; public String category; public BigDecimal costPrice;
        public BigDecimal sellingPrice; public int stockQuantity; public LocalDate createdDate;
        public ReceiveEntry(String product, String category, BigDecimal costPrice, BigDecimal sellingPrice, int stockQuantity, LocalDate createdDate) {
            this.product = product; this.category = category; this.costPrice = costPrice;
            this.sellingPrice = sellingPrice; this.stockQuantity = stockQuantity; this.createdDate = createdDate;
        }
    }

    public static class ItemFlow {
        public int productId; public String product; public String action; public int quantity;
        public LocalDate flowDate; public LocalDate createdDate; public String vendor; public String user;
        public ItemFlow(int productId, String product, String action, int quantity, LocalDate flowDate, LocalDate createdDate, String vendor, String user) {
            this.productId = productId; this.product = product; this.action = action; this.quantity = quantity;
            this.flowDate = flowDate; this.createdDate = createdDate; this.vendor = vendor; this.user = user;
        }
    }

    public static class DailySalesReport {
        public LocalDate date; public BigDecimal totalGrossSale; public BigDecimal discounts;
        public BigDecimal costOfGoods; public BigDecimal dailyExpenses; public BigDecimal netSale;
        public BigDecimal grossProfit; public BigDecimal netProfit;
        public DailySalesReport() {}
        public void setDate(LocalDate date) { this.date = date; }
        public void setTotalGrossSale(BigDecimal v) { this.totalGrossSale = v; }
        public void setDiscounts(BigDecimal v) { this.discounts = v; }
        public void setCostOfGoods(BigDecimal v) { this.costOfGoods = v; }
        public void setDailyExpenses(BigDecimal v) { this.dailyExpenses = v; }
        public void setNetSale(BigDecimal v) { this.netSale = v; }
        public void setGrossProfit(BigDecimal v) { this.grossProfit = v; }
        public void setNetProfit(BigDecimal v) { this.netProfit = v; }
    }

    public static class TopSellingProduct {
        public int productId; public String productName; public int totalQuantitySold; public BigDecimal totalRevenue;
        public TopSellingProduct(int productId, String productName, int totalQuantitySold, BigDecimal totalRevenue) {
            this.productId = productId; this.productName = productName; this.totalQuantitySold = totalQuantitySold; this.totalRevenue = totalRevenue;
        }
    }
}
