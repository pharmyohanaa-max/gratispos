package hIMs.App;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.List;
import java.util.ArrayList;

public class Note extends JDialog {

    private static final long serialVersionUID = 1L;
    private final JPanel contentPanel = new JPanel(new BorderLayout());

    private JPanel displayPanel;
    private JPanel savedNotesPanel;

    private String currentCategory = "";
    private int currentEditingNoteId = -1; // -1 means creating new note

    private static final String DB_URL = "jdbc:sqlite:notifications.db";
    private static final String ICON_ADD = "+";
    private static final String ICON_FAVORITE = "♥";
    private static final String ICON_CHECK = "✓";
    private static final String ICON_LEGAL = "§";
    private static final String ICON_INNOVATION = "↗";
    private static final String ICON_OPPORTUNITY = "◆";

    private static final String[] CATEGORIES = {
    	    "Add Order",
    	    "Favorite Quotes",
    	    "Check List",
    	    "Legal Notes",
    	    "Future Innovations",
    	    "Niche Opportunities"
    	};

    public static void main(String[] args) {
        try {
            initializeNotesTable();
            Note dialog = new Note();
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Note() {
        setTitle("Notebook");
        setBounds(100, 100, 1000, 600);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(contentPanel, BorderLayout.CENTER);

        // CRITICAL: Initialize NotificationDatabase BEFORE any operations
        try {
            NotificationDatabase.initializeDatabase();
            initializeNotesTable();
            System.out.println("Notebook database initialized successfully");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Failed to initialize notebook database: " + e.getMessage() + 
                "\n\nThe notebook feature may not work properly.\n" +
                "Please contact support if this issue persists.", 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE);
            // Continue with UI creation even if database fails
        }

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(45, 45, 45));
        sidebar.setPreferredSize(new Dimension(220, getHeight()));

        JLabel bookmarkLabel = new JLabel("🔖 Book-mark");
        bookmarkLabel.setForeground(Color.GREEN);
        bookmarkLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        bookmarkLabel.setBorder(new EmptyBorder(20, 20, 5, 10));
        sidebar.add(bookmarkLabel);

        JLabel orderLabel = new JLabel("Order");
        orderLabel.setForeground(new Color(0, 200, 100));
        orderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        orderLabel.setBorder(new EmptyBorder(0, 20, 20, 10));
        sidebar.add(orderLabel);

        // Add Menu Buttons
        sidebar.add(createMenuButton(ICON_ADD + " Add Order", "Add Order"));
        sidebar.add(createMenuButton(ICON_FAVORITE + " Favorite Quotes", "Favorite Quotes"));
        sidebar.add(createMenuButton(ICON_CHECK + " Check List", "Check List"));
        sidebar.add(createMenuButton(ICON_LEGAL + " Legal Notes", "Legal Notes"));
        sidebar.add(createMenuButton(ICON_INNOVATION + " Future Innovations", "Future Innovations"));
        sidebar.add(createMenuButton(ICON_OPPORTUNITY + " Niche Opportunities", "Niche Opportunities")); // ◆ Diamond

        contentPanel.add(sidebar, BorderLayout.WEST);

        // Display Panel
        displayPanel = new JPanel(new BorderLayout());
        displayPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.add(displayPanel, BorderLayout.CENTER);

        // Saved Notes Panel
        savedNotesPanel = new JPanel();
        savedNotesPanel.setLayout(new BoxLayout(savedNotesPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(savedNotesPanel);
        scrollPane.setPreferredSize(new Dimension(250, getHeight()));
        contentPanel.add(scrollPane, BorderLayout.EAST);
    }

    // Initialize notes table in database
    private static void initializeNotesTable() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    private JButton createMenuButton(String label, String category) {
        JButton button = new JButton(label);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFocusPainted(false);
        button.setBackground(new Color(60, 63, 65));
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setMaximumSize(new Dimension(220, 40));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorderPainted(false);

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(new Color(80, 80, 80));
            }

            public void mouseExited(MouseEvent evt) {
                button.setBackground(new Color(60, 63, 65));
            }
        });

        button.addActionListener(e -> {
            currentCategory = category;
            currentEditingNoteId = -1; // Reset to new note mode
            showParagraphEditor(category);
            updateSavedNotesList(category);
        });

        return button;
    }

    private void showParagraphEditor(String category) {
        displayPanel.removeAll();

        // Handle checklist separately
        if ("Check List".equals(category)) {
            showChecklistEditor(category);
            return;
        }

        JPanel editorPanel = new JPanel();
        editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.Y_AXIS));
        editorPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea textArea = new JTextArea(10, 50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        String symbol = "";
        switch (category) {
            case "Add Order": symbol = "• "; break;
            case "Favorite Quotes": symbol = "♥ "; break;
            case "Legal Notes": symbol = "§ "; break;
            case "Future Innovations": symbol = "↗ "; break;
            default: symbol = ""; break;
        }

        // Load existing note if editing
        if (currentEditingNoteId != -1) {
            try {
                String content = loadNoteContent(currentEditingNoteId);
                textArea.setText(content);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error loading note: " + e.getMessage());
            }
        } else {
            textArea.setText(symbol);
        }

        String finalSymbol = symbol;
        textArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    SwingUtilities.invokeLater(() -> {
                        textArea.append("\n" + finalSymbol);
                    });
                }
            }
        });

        JScrollPane textScroll = new JScrollPane(textArea);
        editorPanel.add(textScroll);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(new Color(46, 204, 113));
        saveButton.setForeground(Color.WHITE);
        
        JButton clearButton = new JButton("Clear");
        clearButton.setBackground(new Color(231, 76, 60));
        clearButton.setForeground(Color.WHITE);

        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);
        editorPanel.add(Box.createVerticalStrut(10));
        editorPanel.add(buttonPanel);

        saveButton.addActionListener(e -> {
            String content = textArea.getText().trim();
            if (!content.isEmpty()) {
                try {
                    if (currentEditingNoteId == -1) {
                        // Create new note
                        saveNoteToDatabase(category, content);
                        JOptionPane.showMessageDialog(this, "Note saved successfully!");
                    } else {
                        // Update existing note
                        updateNoteInDatabase(currentEditingNoteId, content);
                        JOptionPane.showMessageDialog(this, "Note updated successfully!");
                    }
                    updateSavedNotesList(category);
                    currentEditingNoteId = -1; // Reset
                    textArea.setText(finalSymbol);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving note: " + ex.getMessage());
                }
            }
        });

        clearButton.addActionListener(e -> {
            textArea.setText(finalSymbol);
            currentEditingNoteId = -1; // Reset to new note mode
        });

        displayPanel.add(editorPanel, BorderLayout.CENTER);
        displayPanel.revalidate();
        displayPanel.repaint();
    }

    private void showChecklistEditor(String category) {
        displayPanel.removeAll();

        JPanel checklistPanel = new JPanel();
        checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
        checklistPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(checklistPanel);
        displayPanel.add(scrollPane, BorderLayout.CENTER);

        List<JCheckBox> checkBoxes = new ArrayList<>();
        List<JTextField> textFields = new ArrayList<>();
        List<JPanel> itemPanels = new ArrayList<>();

        JButton addButton = new JButton("Add Item");
        addButton.setFocusPainted(false);
        addButton.setBackground(new Color(52, 152, 219));
        addButton.setForeground(Color.WHITE);
        addButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        addButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton saveButton = new JButton("Save");
        saveButton.setBackground(new Color(46, 204, 113));
        saveButton.setForeground(Color.WHITE);

        JButton clearButton = new JButton("Clear");
        clearButton.setBackground(new Color(231, 76, 60));
        clearButton.setForeground(Color.WHITE);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controlPanel.add(addButton);
        controlPanel.add(saveButton);
        controlPanel.add(clearButton);

        displayPanel.add(controlPanel, BorderLayout.SOUTH);

        Runnable addCheckBox = () -> {
            JPanel itemPanel = new JPanel();
            itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.X_AXIS));
            itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            JCheckBox cb = new JCheckBox();
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 18));

            JTextField textField = new JTextField("New item", 20);
            textField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            textField.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

            JButton deleteButton = new JButton("X"); // Changed from ✖ 
            deleteButton.setFocusPainted(false);
            deleteButton.setBackground(new Color(231, 76, 60));
            deleteButton.setForeground(Color.WHITE);
            deleteButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            deleteButton.setPreferredSize(new Dimension(30, 30));
            deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            checkBoxes.add(cb);
            textFields.add(textField);
            itemPanels.add(itemPanel);

            itemPanel.add(cb);
            itemPanel.add(textField);
            itemPanel.add(Box.createHorizontalStrut(5));
            itemPanel.add(deleteButton);
            checklistPanel.add(itemPanel);
            checklistPanel.revalidate();
            checklistPanel.repaint();

            textField.requestFocus();
            textField.selectAll();

            deleteButton.addActionListener(e -> {
                int index = itemPanels.indexOf(itemPanel);
                if (index != -1) {
                    checkBoxes.remove(index);
                    textFields.remove(index);
                    itemPanels.remove(index);
                    checklistPanel.remove(itemPanel);
                    checklistPanel.revalidate();
                    checklistPanel.repaint();
                }
            });
        };

        // Load existing checklist if editing
        if (currentEditingNoteId != -1) {
            try {
                String content = loadNoteContent(currentEditingNoteId);
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    
                    JPanel itemPanel = new JPanel();
                    itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.X_AXIS));
                    itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                    JCheckBox cb = new JCheckBox();
                    cb.setFont(new Font("Segoe UI", Font.PLAIN, 18));
                    
                    String text;
                    if (line.startsWith("[x] ") || line.startsWith("[X] ")) {
                        cb.setSelected(true);
                        text = line.substring(4);
                    } else if (line.startsWith("[ ] ")) {
                        cb.setSelected(false);
                        text = line.substring(4);
                    } else {
                        text = line;
                    }

                    JTextField textField = new JTextField(text, 20);
                    textField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
                    textField.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

                    JButton deleteButton = new JButton("X");
                    deleteButton.setFocusPainted(false);
                    deleteButton.setBackground(new Color(231, 76, 60));
                    deleteButton.setForeground(Color.WHITE);
                    deleteButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    deleteButton.setPreferredSize(new Dimension(30, 30));
                    deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    checkBoxes.add(cb);
                    textFields.add(textField);
                    itemPanels.add(itemPanel);

                    itemPanel.add(cb);
                    itemPanel.add(textField);
                    itemPanel.add(Box.createHorizontalStrut(5));
                    itemPanel.add(deleteButton);
                    checklistPanel.add(itemPanel);

                    deleteButton.addActionListener(e -> {
                        int index = itemPanels.indexOf(itemPanel);
                        if (index != -1) {
                            checkBoxes.remove(index);
                            textFields.remove(index);
                            itemPanels.remove(index);
                            checklistPanel.remove(itemPanel);
                            checklistPanel.revalidate();
                            checklistPanel.repaint();
                        }
                    });
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error loading checklist: " + e.getMessage());
            }
        } else {
            addCheckBox.run(); // Add first item for new checklist
        }

        addButton.addActionListener(e -> addCheckBox.run());

        saveButton.addActionListener(e -> {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < checkBoxes.size(); i++) {
                JCheckBox cb = checkBoxes.get(i);
                JTextField tf = textFields.get(i);
                String text = tf.getText().trim();
                if (!text.isEmpty()) {
                    String state = cb.isSelected() ? "[x] " : "[ ] ";
                    content.append(state).append(text).append("\n");
                }
            }
            
            if (content.length() > 0) {
                try {
                    if (currentEditingNoteId == -1) {
                        saveNoteToDatabase(category, content.toString());
                        JOptionPane.showMessageDialog(this, "Checklist saved successfully!");
                    } else {
                        updateNoteInDatabase(currentEditingNoteId, content.toString());
                        JOptionPane.showMessageDialog(this, "Checklist updated successfully!");
                    }
                    updateSavedNotesList(category);
                    currentEditingNoteId = -1;
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving checklist: " + ex.getMessage());
                }
            }
        });

        clearButton.addActionListener(e -> {
            checklistPanel.removeAll();
            checkBoxes.clear();
            textFields.clear();
            itemPanels.clear();
            addCheckBox.run();
            checklistPanel.revalidate();
            checklistPanel.repaint();
            currentEditingNoteId = -1;
        });

        displayPanel.revalidate();
        displayPanel.repaint();
    }

    // Save note to database
    private void saveNoteToDatabase(String category, String content) throws SQLException {
        String sql = "INSERT INTO notes (category, content) VALUES (?, ?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            pstmt.setString(2, content);
            pstmt.executeUpdate();
        }
    }

    // Update existing note in database
    private void updateNoteInDatabase(int noteId, String content) throws SQLException {
        String sql = "UPDATE notes SET content = ?, modified_date = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, content);
            pstmt.setInt(2, noteId);
            pstmt.executeUpdate();
        }
    }

    // Load note content from database
    private String loadNoteContent(int noteId) throws SQLException {
        String sql = "SELECT content FROM notes WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, noteId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        }
        return "";
    }

    // Get all notes for a category
    private List<NoteItem> getNotesForCategory(String category) throws SQLException {
        List<NoteItem> notes = new ArrayList<>();
        String sql = "SELECT id, content, created_date FROM notes WHERE category = ? ORDER BY created_date ASC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    notes.add(new NoteItem(
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getString("created_date")
                    ));
                }
            }
        }
        return notes;
    }

    // Delete note from database
    private void deleteNoteFromDatabase(int noteId) throws SQLException {
        String sql = "DELETE FROM notes WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, noteId);
            pstmt.executeUpdate();
        }
    }

    private void updateSavedNotesList(String category) {
        savedNotesPanel.removeAll();
        
        try {
            List<NoteItem> notes = getNotesForCategory(category);
            
            for (int i = 0; i < notes.size(); i++) {
                NoteItem note = notes.get(i);
                String label = "Note " + (i + 1);

                JPanel noteItemPanel = new JPanel(new BorderLayout(5, 0));
                noteItemPanel.setMaximumSize(new Dimension(240, 40));
                noteItemPanel.setBackground(new Color(52, 73, 94));

                JButton noteButton = new JButton(label);
                noteButton.setFocusPainted(false);
                noteButton.setBackground(new Color(52, 73, 94));
                noteButton.setForeground(Color.WHITE);
                noteButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                noteButton.setHorizontalAlignment(SwingConstants.LEFT);
                noteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                noteButton.setBorderPainted(false);

                JButton deleteButton = new JButton("✖");
                deleteButton.setFocusPainted(false);
                deleteButton.setBackground(new Color(192, 57, 43));
                deleteButton.setForeground(Color.WHITE);
                deleteButton.setFont(new Font("Segoe UI", Font.BOLD, 10));
                deleteButton.setPreferredSize(new Dimension(30, 35));
                deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                deleteButton.setBorderPainted(false);

                int noteId = note.getId();
                noteButton.addActionListener(e -> {
                    currentEditingNoteId = noteId;
                    showParagraphEditor(category);
                });

                deleteButton.addActionListener(e -> {
                    int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to delete " + label + "?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (confirm == JOptionPane.YES_OPTION) {
                        try {
                            deleteNoteFromDatabase(noteId);
                            updateSavedNotesList(category);
                            JOptionPane.showMessageDialog(this, "Note deleted successfully!");
                            
                            // Clear display if deleted note was being edited
                            if (currentEditingNoteId == noteId) {
                                currentEditingNoteId = -1;
                                showParagraphEditor(category);
                            }
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(this, "Error deleting note: " + ex.getMessage());
                        }
                    }
                });

                noteItemPanel.add(noteButton, BorderLayout.CENTER);
                noteItemPanel.add(deleteButton, BorderLayout.EAST);
                savedNotesPanel.add(noteItemPanel);
                savedNotesPanel.add(Box.createVerticalStrut(5));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading notes: " + e.getMessage());
        }

        savedNotesPanel.revalidate();
        savedNotesPanel.repaint();
    }

    // Helper class to hold note data
    private static class NoteItem {
        private final int id;
        private final String content;
        private final String createdDate;

        public NoteItem(int id, String content, String createdDate) {
            this.id = id;
            this.content = content;
            this.createdDate = createdDate;
        }

        public int getId() { return id; }
        public String getContent() { return content; }
        public String getCreatedDate() { return createdDate; }
    }
}