package hIMs.App;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GratisData {
    
    private static final Logger LOGGER = Logger.getLogger(GratisData.class.getName());
    private static GratisData instance;
    private Connection localConnection;
    private boolean useCloud = true;
    
    // Current logged-in user
    private User currentUser;
    
    private GratisData() {
        initializeLocalDatabase();
    }
    
    public static synchronized GratisData getInstance() {
        if (instance == null) {
            instance = new GratisData();
        }
        return instance;
    }
    
    private void initializeLocalDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            localConnection = DriverManager.getConnection("jdbc:sqlite:gratisData.db");
            createLocalTables();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize local database", e);
        }
    }
    
    private void createLocalTables() throws SQLException {
        String usersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                phone TEXT,
                password_hash TEXT,
                profile_pic TEXT,
                role TEXT DEFAULT 'USER',
                google_id TEXT,
                auth_provider TEXT DEFAULT 'local',
                business_name TEXT,
                client_id TEXT,
                is_active INTEGER DEFAULT 1,
                last_login TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        String sessionsTable = """
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                token TEXT UNIQUE NOT NULL,
                device_info TEXT,
                expires_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;
        
        try (Statement stmt = localConnection.createStatement()) {
            stmt.execute(usersTable);
            stmt.execute(sessionsTable);
        }
    }
    
    // ==================== FETCH USERS FROM CLOUD ====================
    
    /**
     * Fetch all users from the cloud database
     */
    public List<User> fetchAllUsersFromCloud() throws Exception {
        List<User> users = new ArrayList<>();
        
        if (!isCloudAvailable()) {
            LOGGER.warning("Cloud not available, returning local users only");
            return getAllLocalUsers();
        }
        
        try {
            List<JsonObject> results = SQLiteCloudClient.execute(
                "SELECT id, email, username, privileges, business_name, client_id, " +
                "created_date, last_login, account_status, email_verified, " +
                "security_question1, security_question2, password " +
                "FROM users ORDER BY created_date DESC"
            );
            
            for (JsonObject obj : results) {
                User user = new User(
                    obj.get("id").getAsInt(),
                    obj.get("username").getAsString(),
                    obj.get("email").getAsString(),
                    "", // phone not in cloud schema
                    obj.get("privileges").getAsString(),
                    null, // google_id
                    null, // profile_pic
                    obj.get("account_status").getAsString().equals("ACTIVE"),
                    "local",
                    obj.has("business_name") && !obj.get("business_name").isJsonNull() ? 
                        obj.get("business_name").getAsString() : "",
                    obj.has("client_id") && !obj.get("client_id").isJsonNull() ? 
                        obj.get("client_id").getAsString() : "",
                    obj.get("created_date").getAsString(),
                    obj.has("last_login") && !obj.get("last_login").isJsonNull() ? 
                        obj.get("last_login").getAsString() : null,
                    obj.get("email_verified").getAsInt() == 1
                );
                users.add(user);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch users from cloud", e);
            throw e;
        }
        
        return users;
    }
    
    /**
     * Fetch a single user by email from cloud
     */
    public User fetchUserFromCloud(String email) throws Exception {
        if (!isCloudAvailable()) {
            return getLocalUserByEmail(email);
        }
        
        try {
            List<JsonObject> results = SQLiteCloudClient.execute(
                "SELECT id, email, username, privileges, business_name, client_id, " +
                "created_date, last_login, account_status, email_verified " +
                "FROM users WHERE email = ?", email
            );
            
            if (results.isEmpty()) {
                return null;
            }
            
            JsonObject obj = results.get(0);
            return new User(
                obj.get("id").getAsInt(),
                obj.get("username").getAsString(),
                obj.get("email").getAsString(),
                "",
                obj.get("privileges").getAsString(),
                null,
                null,
                obj.get("account_status").getAsString().equals("ACTIVE"),
                "local",
                obj.has("business_name") && !obj.get("business_name").isJsonNull() ? 
                    obj.get("business_name").getAsString() : "",
                obj.has("client_id") && !obj.get("client_id").isJsonNull() ? 
                    obj.get("client_id").getAsString() : "",
                obj.get("created_date").getAsString(),
                obj.has("last_login") && !obj.get("last_login").isJsonNull() ? 
                    obj.get("last_login").getAsString() : null,
                obj.get("email_verified").getAsInt() == 1
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch user from cloud: " + email, e);
            throw e;
        }
    }
    
    /**
     * Fetch users by role from cloud
     */
    public List<User> fetchUsersByRoleFromCloud(String role) throws Exception {
        List<User> users = new ArrayList<>();
        
        if (!isCloudAvailable()) {
            return getLocalUsersByRole(role);
        }
        
        try {
            List<JsonObject> results = SQLiteCloudClient.execute(
                "SELECT id, email, username, privileges, business_name, client_id, " +
                "created_date, last_login, account_status, email_verified " +
                "FROM users WHERE privileges = ? ORDER BY username", role
            );
            
            for (JsonObject obj : results) {
                User user = new User(
                    obj.get("id").getAsInt(),
                    obj.get("username").getAsString(),
                    obj.get("email").getAsString(),
                    "",
                    obj.get("privileges").getAsString(),
                    null,
                    null,
                    obj.get("account_status").getAsString().equals("ACTIVE"),
                    "local",
                    obj.has("business_name") && !obj.get("business_name").isJsonNull() ? 
                        obj.get("business_name").getAsString() : "",
                    obj.has("client_id") && !obj.get("client_id").isJsonNull() ? 
                        obj.get("client_id").getAsString() : "",
                    obj.get("created_date").getAsString(),
                    obj.has("last_login") && !obj.get("last_login").isJsonNull() ? 
                        obj.get("last_login").getAsString() : null,
                    obj.get("email_verified").getAsInt() == 1
                );
                users.add(user);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch users by role: " + role, e);
            throw e;
        }
        
        return users;
    }
    
    // ==================== LOCAL DATABASE OPERATIONS ====================
    
    /**
     * Sync cloud users to local database
     */
    public void syncUsersToLocal() {
        if (!isCloudAvailable()) {
            LOGGER.warning("Cannot sync: Cloud not available");
            return;
        }
        
        try {
            List<User> cloudUsers = fetchAllUsersFromCloud();
            
            for (User cloudUser : cloudUsers) {
                // Check if user exists locally
                User localUser = getLocalUserByEmail(cloudUser.email);
                
                if (localUser == null) {
                    // Insert new user
                    insertLocalUser(cloudUser);
                } else {
                    // Update existing user
                    updateLocalUser(cloudUser);
                }
            }
            
            LOGGER.info("Synced " + cloudUsers.size() + " users to local database");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to sync users to local", e);
        }
    }
    
    private void insertLocalUser(User user) {
        String sql = """
            INSERT INTO users (name, email, role, business_name, client_id, 
            is_active, created_at, updated_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, user.name);
            pstmt.setString(2, user.email);
            pstmt.setString(3, user.role);
            pstmt.setString(4, user.businessName);
            pstmt.setString(5, user.clientId);
            pstmt.setInt(6, user.isActive ? 1 : 0);
            pstmt.setString(7, user.createdAt);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert local user: " + user.email, e);
        }
    }
    
    private void updateLocalUser(User user) {
        String sql = """
            UPDATE users SET name = ?, role = ?, business_name = ?, client_id = ?, 
            is_active = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE email = ?
        """;
        
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, user.name);
            pstmt.setString(2, user.role);
            pstmt.setString(3, user.businessName);
            pstmt.setString(4, user.clientId);
            pstmt.setInt(5, user.isActive ? 1 : 0);
            pstmt.setString(6, user.email);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update local user: " + user.email, e);
        }
    }
    
    public List<User> getAllLocalUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY name";
        
        try (Statement stmt = localConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                users.add(extractUserFromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get local users", e);
        }
        
        return users;
    }
    
    public User getLocalUserByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return extractUserFromResultSet(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get local user: " + email, e);
        }
        
        return null;
    }
    
    public List<User> getLocalUsersByRole(String role) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = ? ORDER BY name";
        
        try (PreparedStatement pstmt = localConnection.prepareStatement(sql)) {
            pstmt.setString(1, role);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(extractUserFromResultSet(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get local users by role: " + role, e);
        }
        
        return users;
    }
    
    private User extractUserFromResultSet(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("role"),
            rs.getString("google_id"),
            rs.getString("profile_pic"),
            rs.getInt("is_active") == 1,
            rs.getString("auth_provider"),
            rs.getString("business_name"),
            rs.getString("client_id"),
            rs.getString("created_at"),
            rs.getString("last_login"),
            false
        );
    }
    
    // ==================== CURRENT USER MANAGEMENT ====================
    
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            syncUsersToLocal(); // Refresh local cache when user logs in
        }
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    public void clearCurrentUser() {
        this.currentUser = null;
    }
    
    public boolean isLoggedIn() {
        return currentUser != null;
    }
    
    public boolean isAdmin() {
        return currentUser != null && 
               ("SUPERADMIN".equals(currentUser.role) || "ADMIN".equals(currentUser.role));
    }
    
    public boolean isSuperAdmin() {
        return currentUser != null && "SUPERADMIN".equals(currentUser.role);
    }
    
    // ==================== UTILITY METHODS ====================
    
    private boolean isCloudAvailable() {
        try {
            return Database.checkTursoConnection();
        } catch (Exception e) {
            return false;
        }
    }
    
    public void refreshUsers() {
        if (isCloudAvailable()) {
            syncUsersToLocal();
        }
    }
    
    public void closeConnection() {
        try {
            if (localConnection != null && !localConnection.isClosed()) {
                localConnection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to close local connection", e);
        }
    }
    
    // ==================== USER INNER CLASS ====================
    
    public static class User {
        public final int id;
        public final String name;
        public final String email;
        public final String phone;
        public final String role;
        public final String googleId;
        public final String profilePic;
        public final boolean isActive;
        public final String authProvider;
        public final String businessName;
        public final String clientId;
        public final String createdAt;
        public final String lastLogin;
        public final boolean emailVerified;
        
        // Full constructor
        public User(int id, String name, String email, String phone, String role,
                   String googleId, String profilePic, boolean isActive, String authProvider,
                   String businessName, String clientId, String createdAt, 
                   String lastLogin, boolean emailVerified) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.phone = phone != null ? phone : "";
            this.role = role != null ? role : "USER";
            this.googleId = googleId;
            this.profilePic = profilePic;
            this.isActive = isActive;
            this.authProvider = authProvider != null ? authProvider : "local";
            this.businessName = businessName != null ? businessName : "";
            this.clientId = clientId != null ? clientId : "";
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
            this.emailVerified = emailVerified;
        }
        
       
        
        // Constructor without cloud-specific fields
        public User(int id, String name, String email, String phone, String role,
                   String googleId, String profilePic, boolean isActive, String authProvider) {
            this(id, name, email, phone, role, googleId, profilePic, isActive, 
                 authProvider, "", "", null, null, false);
        }
        
        public boolean isGoogleUser() {
            return "google".equals(authProvider);
        }
        
        public boolean hasPassword() {
            return "local".equals(authProvider);
        }
        
        public String getDisplayRole() {
            switch (role.toUpperCase()) {
                case "SUPERADMIN": return "Super Administrator";
                case "ADMIN": return "Administrator";
                case "MANAGER": return "Manager";
                case "CASHIER": return "Cashier";
                default: return role;
            }
        }
        
        @Override
        public String toString() {
            return name + " (" + email + ") - " + role;
        }
    }
}