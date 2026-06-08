package hIMs.App;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetSocketAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TenantAwareMessageHandler extends WebSocketServer {
    
    private static final Logger LOGGER = Logger.getLogger(TenantAwareMessageHandler.class.getName());
    private static TenantAwareMessageHandler instance;
    private static final Gson gson = new Gson();
    
    // Track online users with their client_id and user_uid
    // Key = user_uid, Value = WebSocket connection and user info
    private final Map<String, UserSession> onlineUsers = new ConcurrentHashMap<>();
    
    static class UserSession {
        String userUid;
        String clientId;
        String username;
        WebSocket connection;
        
        UserSession(String userUid, String clientId, String username, WebSocket conn) {
            this.userUid = userUid;
            this.clientId = clientId;
            this.username = username;
            this.connection = conn;
        }
    }
    
    private TenantAwareMessageHandler(int port) {
        super(new InetSocketAddress(port));
    }
    
    public static void startServer(int port) {
        if (instance == null) {
            instance = new TenantAwareMessageHandler(port);
            instance.start();
            LOGGER.info("Message WebSocket server started on port " + port);
        }
    }
    
    public static void stopServer() {
        if (instance != null) {
            try {
                instance.stop();
                instance = null;
                LOGGER.info("Message WebSocket server stopped");
            } catch (Exception e) {
                LOGGER.warning("Error stopping server: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOGGER.info("New connection from: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Remove user from online tracking
        UserSession session = (UserSession) conn.getAttachment();
        if (session != null) {
            onlineUsers.remove(session.userUid);
            LOGGER.info("User disconnected: " + session.userUid);
        }
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();
            
            // Handle different message types
            switch (type) {
                case "auth":
                    handleAuthentication(conn, json);
                    break;
                case "message":
                    handleChatMessage(conn, json);
                    break;
                case "ping":
                    handlePing(conn);
                    break;
                default:
                    LOGGER.warning("Unknown message type: " + type);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error processing message: " + e.getMessage());
            sendError(conn, "Invalid message format");
        }
    }
    
    private void handleAuthentication(WebSocket conn, JsonObject json) {
        String userUid = json.get("userUid").getAsString();
        String clientId = json.get("clientId").getAsString();
        String username = json.has("username") ? json.get("username").getAsString() : userUid;
        
        // Verify user exists and belongs to this client
        if (isValidUser(userUid, clientId)) {
            UserSession session = new UserSession(userUid, clientId, username, conn);
            onlineUsers.put(userUid, session);
            conn.setAttachment(session);
            
            JsonObject response = new JsonObject();
            response.addProperty("type", "auth_response");
            response.addProperty("status", "success");
            response.addProperty("message", "Authenticated successfully");
            conn.send(response.toString());
            
            LOGGER.info("User authenticated: " + userUid + " (client: " + clientId + ")");
            
            // Deliver any offline messages
            deliverOfflineMessages(conn, userUid);
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("type", "auth_response");
            response.addProperty("status", "error");
            response.addProperty("message", "Authentication failed");
            conn.send(response.toString());
            
            LOGGER.warning("Authentication failed for: " + userUid);
            conn.close();
        }
    }
    
    private void handleChatMessage(WebSocket conn, JsonObject json) {
        UserSession sender = (UserSession) conn.getAttachment();
        
        if (sender == null) {
            sendError(conn, "Not authenticated");
            return;
        }
        
        String senderUid = sender.userUid;
        String clientId = sender.clientId;
        String recipientUid = json.get("recipientUid").getAsString();
        String content = json.get("content").getAsString();
        
        // CRITICAL: Verify recipient is in the SAME client_id
        if (!isUserInSameTenant(recipientUid, clientId)) {
            LOGGER.warning("Security violation: User " + senderUid + 
                          " tried to message " + recipientUid + " (different tenant)");
            sendError(conn, "Cannot message users from different businesses");
            return;
        }
        
        // Save message to database (for persistence and offline delivery)
        int messageId = saveMessageToDatabase(senderUid, recipientUid, content, clientId);
        
        // Send acknowledgment to sender
        JsonObject ack = new JsonObject();
        ack.addProperty("type", "ack");
        ack.addProperty("messageId", messageId);
        ack.addProperty("status", "sent");
        conn.send(ack.toString());
        
        // Forward to recipient if online
        UserSession recipient = onlineUsers.get(recipientUid);
        if (recipient != null && recipient.connection.isOpen()) {
            JsonObject delivery = new JsonObject();
            delivery.addProperty("type", "message");
            delivery.addProperty("senderUid", senderUid);
            delivery.addProperty("senderName", sender.username);
            delivery.addProperty("content", content);
            delivery.addProperty("timestamp", LocalDateTime.now().toString());
            delivery.addProperty("messageId", messageId);
            recipient.connection.send(delivery.toString());
            
            // Update message as delivered
            markMessageAsDelivered(messageId);
            
            LOGGER.info("Message delivered instantly: " + senderUid + " -> " + recipientUid);
        } else {
            LOGGER.info("Message saved for offline: " + senderUid + " -> " + recipientUid);
        }
    }
    
    private void handlePing(WebSocket conn) {
        JsonObject pong = new JsonObject();
        pong.addProperty("type", "pong");
        pong.addProperty("timestamp", System.currentTimeMillis());
        conn.send(pong.toString());
    }
    
    private boolean isValidUser(String userUid, String clientId) {
        String sql = "SELECT COUNT(*) FROM users WHERE user_uid = ? AND client_id = ? AND account_status = 'ACTIVE'";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userUid);
            ps.setString(2, clientId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            LOGGER.warning("User validation failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isUserInSameTenant(String userUid, String clientId) {
        String sql = "SELECT COUNT(*) FROM users WHERE user_uid = ? AND client_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userUid);
            ps.setString(2, clientId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            LOGGER.warning("Tenant check failed: " + e.getMessage());
            return false;
        }
    }
    
    private int saveMessageToDatabase(String senderUid, String recipientUid, String content, String clientId) {
        String sql = "INSERT INTO internal_messages (client_id, sender_id, recipient_id, message, is_read, sent_at) " +
                     "VALUES (?, ?, ?, ?, 0, ?)";
        
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, clientId);
            ps.setString(2, senderUid);
            ps.setString(3, recipientUid);
            ps.setString(4, content);
            ps.setString(5, LocalDateTime.now().toString());
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to save message: " + e.getMessage());
        }
        return -1;
    }
    
    private void markMessageAsDelivered(int messageId) {
        String sql = "UPDATE internal_messages SET delivered_at = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setInt(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to mark message as delivered: " + e.getMessage());
        }
    }
    
    private void markMessageAsRead(int messageId) {
        String sql = "UPDATE internal_messages SET is_read = 1, read_at = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setInt(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to mark message as read: " + e.getMessage());
        }
    }
    
    private void deliverOfflineMessages(WebSocket conn, String userUid) {
        String sql = "SELECT id, sender_id, message, sent_at FROM internal_messages " +
                     "WHERE recipient_id = ? AND is_read = 0 AND delivered_at IS NULL " +
                     "ORDER BY sent_at ASC";
        
        try (Connection dbConn = Database.getConnection();
             PreparedStatement ps = dbConn.prepareStatement(sql)) {
            ps.setString(1, userUid);
            ResultSet rs = ps.executeQuery();
            
            int deliveredCount = 0;
            while (rs.next()) {
                int messageId = rs.getInt("id");
                String senderUid = rs.getString("sender_id");
                String content = rs.getString("message");
                String sentAt = rs.getString("sent_at");
                
                JsonObject offlineMsg = new JsonObject();
                offlineMsg.addProperty("type", "offline_message");
                offlineMsg.addProperty("messageId", messageId);
                offlineMsg.addProperty("senderUid", senderUid);
                offlineMsg.addProperty("content", content);
                offlineMsg.addProperty("sent_at", sentAt);
                conn.send(offlineMsg.toString());
                
                markMessageAsRead(messageId);
                deliveredCount++;
            }
            
            if (deliveredCount > 0) {
                LOGGER.info("Delivered " + deliveredCount + " offline messages to " + userUid);
            }
            
        } catch (SQLException e) {
            LOGGER.warning("Failed to deliver offline messages: " + e.getMessage());
        }
    }
    
    private void sendError(WebSocket conn, String errorMsg) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", errorMsg);
        conn.send(error.toString());
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOGGER.warning("WebSocket error: " + ex.getMessage());
        if (conn != null && conn.getAttachment() != null) {
            UserSession session = (UserSession) conn.getAttachment();
            onlineUsers.remove(session.userUid);
        }
    }
    
    @Override
    public void onStart() {
        LOGGER.info("TenantAware WebSocket server started successfully");
        LOGGER.info("Listening on port: " + getPort());
    }
    
    // Public utility methods
    public static boolean isUserOnline(String userUid) {
        return instance != null && instance.onlineUsers.containsKey(userUid);
    }
    
    public static int getOnlineCount(String clientId) {
        if (instance == null) return 0;
        return (int) instance.onlineUsers.values().stream()
                .filter(session -> session.clientId.equals(clientId))
                .count();
    }
    
    public static void broadcastToClient(String clientId, String message) {
        if (instance == null) return;
        instance.onlineUsers.values().stream()
                .filter(session -> session.clientId.equals(clientId))
                .forEach(session -> session.connection.send(message));
    }
}