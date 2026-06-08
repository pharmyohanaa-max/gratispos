package hIMs.App;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class LocalUserStore {
    private static final Logger LOGGER = Logger.getLogger(LocalUserStore.class.getName());
    private static LocalUserStore instance;
    private Map<String, LocalUser> users = new HashMap<>();
    private File storageFile;
    
    public enum Role {
        CEO(1, "Full System Control", 100),
        OWNER(2, "Full Business Control", 95),
        SUPERADMIN(2, "System Administration", 90),
        ADMIN(3, "Administrative Access", 80),
        MANAGER(5, "Management Access", 60),
        DIRECTOR(3, "Director Access", 70),
        SUPERVISOR(10, "Supervisory Access", 50),
        CASHIER(20, "Cashier Access", 40),
        EMPLOYEE(50, "Employee Access", 30),
        USER(100, "Basic User Access", 20),
        VIEWER(100, "View Only Access", 10);
        
        private final int maxCount;
        private final String description;
        private final int accessLevel;
        
        Role(int maxCount, String description, int accessLevel) {
            this.maxCount = maxCount;
            this.description = description;
            this.accessLevel = accessLevel;
        }
        
        public int getMaxCount() { return maxCount; }
        public String getDescription() { return description; }
        public int getAccessLevel() { return accessLevel; }
    }
    
    // Roles that have full access to all menu items
    private static final Set<Role> FULL_ACCESS_ROLES = new HashSet<>(Arrays.asList(
        Role.CEO, Role.OWNER, Role.SUPERADMIN, Role.ADMIN
    ));
    
    public static class LocalUser {
        public String email;
        public String username;
        public String name;
        public Role role;
        public boolean isActive;
        public String passwordHash;
        public boolean isInvited;
        
        public LocalUser(String email, String username, Role role, boolean isActive) {
            this.email = email;
            this.username = username;
            this.name = username;
            this.role = role;
            this.isActive = isActive;
            this.isInvited = false;
            this.passwordHash = "";
        }
        
        public LocalUser(String email, String username, Role role, boolean isActive, String passwordHash) {
            this.email = email;
            this.username = username;
            this.name = username;
            this.role = role;
            this.isActive = isActive;
            this.passwordHash = passwordHash;
            this.isInvited = false;
        }
        
        public LocalUser(String email, String username, String name, Role role, boolean isActive, String passwordHash) {
            this.email = email;
            this.username = username;
            this.name = name;
            this.role = role;
            this.isActive = isActive;
            this.passwordHash = passwordHash;
            this.isInvited = false;
        }
        
        public boolean hasFullAccess() {
            return FULL_ACCESS_ROLES.contains(role);
        }
        
        public boolean canAccess(String feature) {
            if (hasFullAccess()) return true;
            return canAccessFeature(feature);
        }
        
        private boolean canAccessFeature(String feature) {
            switch (role) {
                case MANAGER:
                    return !feature.equals("Admin") && !feature.equals("Security") && 
                           !feature.equals("Backup") && !feature.equals("Manual-Fix");
                case DIRECTOR:
                    return !feature.equals("Admin") && !feature.equals("Security") && 
                           !feature.equals("Backup");
                case SUPERVISOR:
                    return feature.equals("Sale") || feature.equals("Receive Product") ||
                           feature.equals("Inventory") || feature.equals("Below Stock");
                case CASHIER:
                    return feature.equals("Sale") || feature.equals("Inventory");
                case EMPLOYEE:
                    return feature.equals("Sale") || feature.equals("Receive Product");
                case USER:
                case VIEWER:
                    return feature.equals("Inventory") || feature.equals("Medicine Info");
                default:
                    return false;
            }
        }
    }
    
    private LocalUserStore() {
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, ".gratispos");
        if (!dir.exists()) dir.mkdirs();
        storageFile = new File(dir, "local_users.txt");
        loadFromFile();
    }
    
    public static synchronized LocalUserStore getInstance() {
        if (instance == null) {
            instance = new LocalUserStore();
        }
        return instance;
    }
    
    private void loadFromFile() {
        if (!storageFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(storageFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    String email = parts[0];
                    String username = parts[1];
                    String name = parts.length > 5 ? parts[5] : username;
                    Role role = Role.valueOf(parts[2]);
                    boolean isActive = Boolean.parseBoolean(parts[3]);
                    String passwordHash = parts[4];
                    users.put(email, new LocalUser(email, username, name, role, isActive, passwordHash));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to load users: " + e.getMessage());
        }
    }
    
    private void saveToFile() {
        try (FileWriter writer = new FileWriter(storageFile)) {
            for (LocalUser user : users.values()) {
                writer.write(user.email + "|" + user.username + "|" + user.role.name() + "|" + user.isActive + "|" + user.passwordHash + "|" + user.name + "\n");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to save users: " + e.getMessage());
        }
    }
    
    
    
    public LocalUser registerUser(String email, String username, Role role, String password) {
        String passwordHash = hashPassword(password);
        LocalUser user = new LocalUser(email, username, role, true, passwordHash);
        users.put(email, user);
        saveToFile();
        return user;
    }
    
    public LocalUser registerUser(String email, String username, String name, Role role, String password) {
        String passwordHash = hashPassword(password);
        LocalUser user = new LocalUser(email, username, name, role, true, passwordHash);
        users.put(email, user);
        saveToFile();
        return user;
    }
    
    public LocalUser getUser(String email) {
        return users.get(email);
    }
    
    public LocalUser getUserByUsername(String username) {
        for (LocalUser user : users.values()) {
            if (user.username.equals(username)) {
                return user;
            }
        }
        return null;
    }
    public void saveUsers() {
        saveToFile();
    }

    public void addUser(LocalUser user) {
        if (user != null && user.email != null && !user.email.isEmpty()) {
            users.put(user.email, user);
            saveToFile();
            LOGGER.info("User added to LocalUserStore: " + user.username + " (" + user.email + ")");
        }
    }
    
    public List<LocalUser> getAllUsers() {
        List<LocalUser> userList = new ArrayList<>();
        userList.addAll(users.values());
        return userList;
    }
    
    public LocalUser getCEO() {
        for (LocalUser user : users.values()) {
            if (user.role == Role.CEO && user.isActive) {
                return user;
            }
        }
        return null;
    }
    
    public boolean hasCEO() {
        return getCEO() != null;
    }
    
    public LocalUser authenticate(String email, String password) {
        LocalUser user = users.get(email);
        if (user != null && user.isActive) {
            String hashedInput = hashPassword(password);
            if (user.passwordHash.equals(hashedInput)) {
                return user;
            }
        }
        return null;
    }
    
    public LocalUser authenticateByUsername(String username, String password) {
        LocalUser user = getUserByUsername(username);
        if (user != null && user.isActive) {
            String hashedInput = hashPassword(password);
            if (user.passwordHash.equals(hashedInput)) {
                return user;
            }
        }
        return null;
    }
    
    public boolean isInvited(String email) {
        LocalUser user = users.get(email);
        return user != null && user.isInvited;
    }
    
    public boolean inviteUser(String email, String username, Role role, String invitedBy) {
        if (users.containsKey(email)) return false;
        LocalUser user = new LocalUser(email, username, role, false);
        user.isInvited = true;
        users.put(email, user);
        saveToFile();
        return true;
    }
    
    public boolean activateUser(String email, String password) {
        LocalUser user = users.get(email);
        if (user != null && user.isInvited) {
            user.isActive = true;
            user.passwordHash = hashPassword(password);
            user.isInvited = false;
            saveToFile();
            return true;
        }
        return false;
    }
    
    public int countByRole(Role role) {
        int count = 0;
        for (LocalUser user : users.values()) {
            if (user.role == role && user.isActive) {
                count++;
            }
        }
        return count;
    }
    
    public int getMaxCountForRole(Role role) {
        return role.getMaxCount();
    }
    
    // FIXED BUG 38: registerCEO is synchronized to prevent two-thread race
public synchronized LocalUser registerCEO(String email, String username, String password) {
    if (hasCEO()) return null;
    String passwordHash = hashPassword(password);
    LocalUser user = new LocalUser(email, username, Role.CEO, true, passwordHash);
    users.put(email, user);
    saveToFile();
    return user;
}

// FIXED BUG 39: clearCache reloads from file so users aren't wiped mid-session
public void clearCache() {
    if (users != null) users.clear();
    loadFromFile();   // ← reload immediately so lookups still work
    LOGGER.info("LocalUserStore cache cleared and reloaded from disk");
}
        
    public void updateUserPassword(String email, String newPassword) {
        LocalUser user = users.get(email);
        if (user != null) {
            user.passwordHash = hashPassword(newPassword);
            user.isActive = true;
            user.isInvited = false;
            saveToFile();
        }
    }
    
    public void removeUser(String email) {
        if (users != null && users.containsKey(email)) {
            users.remove(email);
            LOGGER.info("User removed from local store: " + email);
            
            try {
                String safeEmail = email.replaceAll("[^a-zA-Z0-9]", "_");
                String userDbPath = System.getProperty("user.home") + File.separator + ".gratispos" + 
                                    File.separator + "users" + File.separator + safeEmail + ".db";
                File userDbFile = new File(userDbPath);
                if (userDbFile.exists()) {
                    userDbFile.delete();
                    LOGGER.info("User database deleted: " + userDbPath);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not delete user database file", e);
            }
        }
    }
    
    public boolean canAddRole(Role role) {
        int currentCount = countByRole(role);
        int maxCount = getMaxCountForRole(role);
        return currentCount < maxCount;
    }
    
    public boolean hasFullAccess(String username) {
        LocalUser user = getUserByUsername(username);
        return user != null && user.hasFullAccess();
    }
    
    public boolean canAccessFeature(String username, String feature) {
        LocalUser user = getUserByUsername(username);
        if (user == null) return false;
        return user.canAccess(feature);
    }
    
    private String hashPassword(String password) {
        if (password == null) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return password;
        }
    }
}
