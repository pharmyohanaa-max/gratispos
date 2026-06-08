package hIMs.App;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.json.JSONObject;

public class AuthHttpServer {

    private static HttpServer server;
    private static final int PORT = 8080;
    private static final String ADMIN_USERNAME = "superadmin";
    private static final String ADMIN_PASSWORD = "ubuntujoro";

    // ---------------- START SERVER ----------------
    public static void start() throws IOException {
        if (server != null) return; // already running

        // Bind to all network interfaces (0.0.0.0) for global access
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/recover", new RecoverHandler()); // optional
        server.setExecutor(null); // default executor
        server.start();
        System.out.println("AuthHttpServer running on port " + PORT);
    }

    // ---------------- STOP SERVER ----------------
    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("AuthHttpServer stopped");
        }
    }

    // ---------------- LOGIN HANDLER ----------------
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            enableCORS(exchange);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject reqJson = new JSONObject(body);
            String username = reqJson.optString("username");
            String password = reqJson.optString("password");

            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "Missing username or password");
                return;
            }

            // Emergency admin
            if (username.equals(ADMIN_USERNAME) && password.equals(ADMIN_PASSWORD)) {
                JSONObject response = new JSONObject();
                response.put("username", username);
                response.put("role", "SuperAdmin");
                response.put("businessName", "Gratis-PoS Admin");
                sendResponse(exchange, 200, response.toString());
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:adhiambo.db")) {
                String sql = "SELECT username, privileges, username AS businessName FROM users WHERE username = ? AND password = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, username);
                    pstmt.setString(2, password);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        JSONObject response = new JSONObject();
                        response.put("username", username);
                        response.put("role", rs.getString("privileges"));
                        response.put("businessName", rs.getString("businessName"));
                        sendResponse(exchange, 200, response.toString());
                    } else {
                        sendResponse(exchange, 401, "Incorrect username or password");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Database error");
            }
        }
    }

    // ---------------- REGISTER HANDLER ----------------
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            enableCORS(exchange);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject reqJson = new JSONObject(body);

            String username = reqJson.optString("username");
            String password = reqJson.optString("password");
            String role = reqJson.optString("role", "Employee");
            String businessName = reqJson.optString("businessName", "My Business");

            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "Missing username or password");
                return;
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:adhiambo.db")) {
                createUsersTableIfNotExists(conn);

                String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, username);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendResponse(exchange, 409, "Username already exists");
                        return;
                    }
                }

                String insertSql = "INSERT INTO users (username, password, privileges) VALUES (?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, username);
                    insertStmt.setString(2, password);
                    insertStmt.setString(3, role);
                    insertStmt.executeUpdate();
                }

                JSONObject response = new JSONObject();
                response.put("message", "User created successfully");
                sendResponse(exchange, 201, response.toString());

            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Database error");
            }
        }
    }

    // ---------------- RECOVER HANDLER (OPTIONAL) ----------------
    static class RecoverHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enableCORS(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject reqJson = new JSONObject(body);
            String email = reqJson.optString("email");

            if (email.isEmpty()) {
                sendResponse(exchange, 400, "Missing email");
                return;
            }

            // Just simulate recovery
            JSONObject response = new JSONObject();
            response.put("message", "Recovery email sent to " + email);
            sendResponse(exchange, 200, response.toString());
        }
    }

    // ---------------- UTILITIES ----------------
    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void enableCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                exchange.sendResponseHeaders(204, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void createUsersTableIfNotExists(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "privileges TEXT DEFAULT 'Employee'" +
                ")";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        }
    }

    // ---------------- MAIN ----------------
    public static void main(String[] args) throws IOException {
        start();
    }
}
