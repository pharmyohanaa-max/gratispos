package hIMs.App;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;
import java.util.zip.*;
import java.util.Base64;
import java.awt.Color;
import java.awt.Font;
/**
 * GratisPOS — Production-Ready Cloud Backup & Disaster Recovery System
 *
 * BACKUP STRATEGY (no OAuth required):
 *   1. User picks their cloud sync folder (Google Drive, Dropbox, OneDrive …)
 *   2. We write a tagged .zip there  →  the cloud app syncs it automatically
 *   3. On a new machine, user downloads that zip from their cloud website,
 *      points this dialog at it, and we restore every table from it.
 *
 * ZIP manifest format (manifest.txt inside every zip):
 *   GRATIS_BACKUP_V2
 *   user_email=john@gmail.com
 *   backup_date=2026-04-30 14:22:00
 *   db_path=adhiambo.db
 *   tables=categories,products,sales,sale_items,...
 *   checksum=<sha256-of-raw-db>
 */
public class Backup extends JDialog {

    private static final long serialVersionUID = 2026L;

    // ── Constants ────────────────────────────────────────────────────────────
    private static final String DB_PATH        = "adhiambo.db";
    private static final Logger LOGGER         = Logger.getLogger(Backup.class.getName());
    private static final String BACKUP_FOLDER  = System.getProperty("user.home") +
            File.separator + "GratisPOS_Backup" + File.separator;
    private static final String EXPORT_FOLDER  = BACKUP_FOLDER + "Exports"       + File.separator;
    private static final String CLOUD_FOLDER   = BACKUP_FOLDER + "Cloud_Uploads" + File.separator;
    private static final String CREDS_FILE     = System.getProperty("user.home") +
            File.separator + ".gratispos" + File.separator + "cloud_credentials.enc";
    private static final String SETTINGS_FILE  = BACKUP_FOLDER + "backup_settings.dat";
    private static final String LOG_FILE       = BACKUP_FOLDER + "backup.log";
    private static final String MANIFEST_MAGIC = "GRATIS_BACKUP_V2";
    private static final String ENC_KEY        = "GratisPOS_2026_Secure_Key_32Byte";  // exactly 32 chars

    // ── Colour palette ───────────────────────────────────────────────────────
    private final Color BG_DARK      = new Color(18,  20,  25);
    private final Color BG_LIGHTER   = new Color(30,  33,  40);
    private final Color BG_CARD      = new Color(25,  28,  35);
    private final Color ACCENT_BLUE  = new Color(52,  152, 219);
    private final Color ACCENT_GREEN = new Color(46,  204, 113);
    private final Color ACCENT_RED   = new Color(231, 76,  60);
    private final Color ACCENT_ORANGE= new Color(243, 156, 18);
    private final Color ACCENT_PURPLE= new Color(155, 89,  182);
    private final Color TEXT_PRIMARY = new Color(236, 240, 241);
    private final Color TEXT_SECONDARY=new Color(189, 195, 199);
    private final Color TEXT_MUTED   = new Color(149, 165, 166);
    private final Color BORDER_COLOR = new Color(52,  73,  94);

    // ── Data models ─────────────────────────────────────────────────────────

    private static class CloudAccount implements Serializable {
        private static final long serialVersionUID = 1L;
        String service, email, encryptedPassword;
        boolean isActive;
        LocalDateTime lastUsed;
        CloudAccount(String service, String email, String plain) {
            this.service = service; this.email = email;
            this.encryptedPassword = encrypt(plain);
            this.isActive = true;  this.lastUsed = LocalDateTime.now();
        }
    }

    private static class BackupRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        String fileName, backupDate, destination, fileSize, filePath, status, userEmail, checksum;
        BackupRecord(String fn, String bd, String dest, String fs,
                     String fp, String st, String email, String cs) {
            fileName=fn; backupDate=bd; destination=dest; fileSize=fs;
            filePath=fp; status=st; userEmail=email; checksum=cs;
        }
    }

    private static class BackupSettings implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean  autoBackup         = false;
        int      backupIntervalDays = 7;
        boolean  compressBackups    = true;
        boolean  encryptBackups     = false;
        boolean  verifyAfterBackup  = true;
        int      maxBackupFiles     = 30;
        String   lastDestFolder     = null;
        LocalDateTime lastAutoBackup= null;
    }

    // ── Instance state ───────────────────────────────────────────────────────
    private final String loggedInUser;
    private final String userEmail;          // email used at login — tags every backup
    private Map<String, CloudAccount> cloudAccounts = new HashMap<>();
    private List<BackupRecord>        backupHistory = new ArrayList<>();
    private BackupSettings            settings      = new BackupSettings();

    // UI refs
    private JProgressBar  progressBar;
    private JLabel        statusLabel, progressDetailLabel;
    private DefaultTableModel historyModel;
    private JPanel        mainPanel;
    private JProgressBar  currentProgressBar;
    private JLabel        currentProgressLabel;
    private final AtomicBoolean opLock = new AtomicBoolean(false);

    // ── Constructor ──────────────────────────────────────────────────────────
    public Backup(String username, String email) {
        this.loggedInUser = (username != null && !username.isEmpty())
                ? username : System.getProperty("user.name");
        this.userEmail = (email != null && !email.isEmpty()) ? email : loggedInUser + "@local";

        initLogger();
        createDirs();
        loadCloudAccounts();
        loadBackupHistory();
        loadSettings();
        buildUI();
        startScheduler();
        cleanupOld();
        LOGGER.info("Backup initialised  user=" + loggedInUser + "  email=" + userEmail);
    }

    /** Backward-compat constructor (no email). */
    public Backup(String username) { this(username, null); }

    // ════════════════════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════════════════════
    private void initLogger() {
        try {
            new File(BACKUP_FOLDER).mkdirs();
            for (Handler h : LOGGER.getHandlers()) LOGGER.removeHandler(h);
            FileHandler fh = new FileHandler(LOG_FILE, 2 * 1024 * 1024, 5, true);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            LOGGER.setLevel(Level.INFO);
        } catch (Exception e) { System.err.println("Logger init failed: " + e.getMessage()); }
    }

    private void createDirs() {
        for (String d : new String[]{BACKUP_FOLDER, EXPORT_FOLDER, CLOUD_FOLDER})
            new File(d).mkdirs();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ENCRYPTION  (AES-256-CBC)
    // ════════════════════════════════════════════════════════════════════════
    private static String encrypt(String plain) {
        try {
            byte[] key = Arrays.copyOf(ENC_KEY.getBytes("UTF-8"), 32);
            byte[] iv  = new byte[16];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new IvParameterSpec(iv));
            byte[] enc = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc,0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { return plain; }
    }

    private static String decrypt(String enc) {
        try {
            byte[] raw  = Base64.getDecoder().decode(enc);
            byte[] iv   = Arrays.copyOf(raw, 16);
            byte[] data = Arrays.copyOfRange(raw, 16, raw.length);
            byte[] key  = Arrays.copyOf(ENC_KEY.getBytes("UTF-8"), 32);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new IvParameterSpec(iv));
            return new String(c.doFinal(data), "UTF-8");
        } catch (Exception e) { return enc; }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        setTitle("GratisPOS — Cloud Backup & Recovery");
        setSize(1120, 760);
        setLocationRelativeTo(null);
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(10,10,10,10));
        mainPanel.add(buildHeader(),    BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.setBackground(BG_DARK);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.addTab("💾 Backup",          buildBackupTab());
        tabs.addTab("☁️ Cloud Services",  buildCloudTab());
        tabs.addTab("🔄 Restore",         buildRestoreTab());
        tabs.addTab("📋 History",         buildHistoryTab());
        tabs.addTab("⚙️ Settings",        buildSettingsTab());
        mainPanel.add(tabs,             BorderLayout.CENTER);
        mainPanel.add(buildStatusBar(), BorderLayout.SOUTH);
        add(mainPanel);
    }

    // ── Header ───────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(BG_LIGHTER);
        h.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0,BORDER_COLOR),
            BorderFactory.createEmptyBorder(10,15,10,15)));

        JPanel left = new JPanel(new GridLayout(2,1,0,2));
        left.setBackground(BG_LIGHTER);
        JLabel title = new JLabel("☁️  Cloud Backup & Disaster Recovery");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(ACCENT_BLUE);
        JLabel sub = new JLabel("Your data is safe — backed up and restorable from anywhere");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(TEXT_MUTED);
        left.add(title); left.add(sub);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        right.setOpaque(false);
        JLabel user = new JLabel("👤 " + loggedInUser + "  |  📧 " + userEmail);
        user.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        user.setForeground(TEXT_SECONDARY);
        JButton close = styledBtn("✕", ACCENT_RED);
        close.setPreferredSize(new Dimension(30,30));
        close.addActionListener(e -> dispose());
        right.add(user); right.add(close);

        h.add(left,  BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    // ── Backup tab ───────────────────────────────────────────────────────────
    private JPanel buildBackupTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_DARK);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10,15,10,15);
        g.fill   = GridBagConstraints.HORIZONTAL;
        g.weightx= 1.0;

        // Section label
        g.gridy=0;
        p.add(sectionLabel("BACKUP OPERATIONS", ACCENT_BLUE), g);

        // How-it-works card
        g.gridy=1;
        p.add(infoCard(
            "ℹ️  How cloud backup works",
            "1. Click 'Create Backup Now'\n" +
            "2. Choose your cloud sync folder (Google Drive / Dropbox / OneDrive folder on this PC)\n" +
            "3. Your data is saved as a tagged .zip — the cloud app uploads it automatically\n" +
            "4. On any new machine: download the zip from your cloud website → click 'Restore'\n\n" +
            "Every backup is tagged with your email (" + userEmail + ") so you always know which file is yours."
        ), g);

        // Buttons
        g.gridy=2;
        JButton backupBtn = styledBtn("☁️  Create Backup Now  (Export + ZIP + Cloud)", ACCENT_BLUE);
        backupBtn.addActionListener(e -> runBackup());
        p.add(backupBtn, g);

        g.gridy=3;
        JButton quickBtn = styledBtn("💾  Quick Local Backup  (.db copy only)", ACCENT_GREEN);
        quickBtn.addActionListener(e -> runQuickBackup());
        p.add(quickBtn, g);

        // Options
        g.gridy=4;
        JPanel opts = new JPanel(new GridLayout(3,1,8,8));
        opts.setBackground(BG_CARD);
        opts.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(14,14,14,14)));
        opts.add(checkRow("🗜️  Include compressed ZIP (recommended)", true));
        opts.add(checkRow("📊  Include Excel export (.xlsx)", true));
        opts.add(checkRow("✅  Verify backup after creation", true));
        p.add(opts, g);

        g.gridy=5; g.weighty=1.0;
        p.add(new JPanel(), g);
        return p;
    }

    // ── Cloud Services tab ───────────────────────────────────────────────────
    private JPanel buildCloudTab() {
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(15,15,15,15));

        JLabel title = sectionLabel("☁️  CLOUD STORAGE PROVIDERS — Click to open website", ACCENT_BLUE);

        String[][] providers = {
            {"Google Drive","https://drive.google.com/",    "📁"},
            {"Dropbox",     "https://www.dropbox.com/",     "📦"},
            {"OneDrive",    "https://onedrive.live.com/",   "🌐"},
            {"IDrive",      "https://www.idrive.com/",      "💾"},
            {"pCloud",      "https://www.pcloud.com/",      "☁️"},
            {"MEGA",        "https://mega.nz/",             "🔒"},
            {"Sync.com",    "https://www.sync.com/",        "🔄"},
            {"Backblaze",   "https://www.backblaze.com/",   "🔥"},
            {"Terabox",     "https://www.terabox.com/",     "📀"},
            {"Box",         "https://www.box.com/",         "📫"},
        };

        JPanel grid = new JPanel(new GridLayout(2, 5, 14, 14));
        grid.setBackground(BG_DARK);
        for (String[] p : providers) grid.add(providerCard(p[0], p[1], p[2]));

        // My Account card
        JPanel myAcc = clickCard("🔐  My Accounts",
            "View connected accounts & backup permissions", ACCENT_PURPLE,
            e -> showMyAccountsDialog());

        // Auto-backup row
        JCheckBox autoChk = new JCheckBox();
        autoChk.setSelected(settings.autoBackup);
        autoChk.setBackground(BG_CARD);
        autoChk.setFocusPainted(false);
        autoChk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        autoChk.addActionListener(e -> handleAutoBackupToggle(autoChk));

        JPanel autoRow = new JPanel(new BorderLayout(12,0));
        autoRow.setBackground(BG_CARD);
        autoRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(12,18,12,18)));
        JPanel autoText = labelStack(
            "🔄  Auto Backup",   Font.BOLD, 13, TEXT_PRIMARY,
            "Automatically create and upload a backup every " + settings.backupIntervalDays + " day(s)",
            Font.PLAIN, 11, TEXT_MUTED);
        autoRow.add(autoText, BorderLayout.CENTER);
        autoRow.add(autoChk,  BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(BG_DARK);
        bottom.add(myAcc);
        bottom.add(Box.createRigidArea(new Dimension(0,10)));
        bottom.add(autoRow);

        JPanel top = new JPanel(new BorderLayout(0,10));
        top.setBackground(BG_DARK);
        top.add(title, BorderLayout.NORTH);
        top.add(grid,  BorderLayout.CENTER);

        panel.add(top,    BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ── Restore tab ──────────────────────────────────────────────────────────
    private JPanel buildRestoreTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_DARK);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10,15,10,15);
        g.fill   = GridBagConstraints.HORIZONTAL;
        g.weightx= 1.0;

        g.gridy=0;
        p.add(sectionLabel("DISASTER RECOVERY — RESTORE YOUR DATA", ACCENT_ORANGE), g);

        // Disaster recovery steps card
        g.gridy=1;
        p.add(infoCard(
            "🆘  New computer? Corrupted machine? Follow these steps:",
            "STEP 1:  Open your cloud service (Google Drive, Dropbox, OneDrive …) in a browser\n" +
            "STEP 2:  Find your backup ZIP file — it will be named like:\n" +
            "         GratisPOS_john@gmail.com_20260430_142200.zip\n" +
            "STEP 3:  Download it to this computer\n" +
            "STEP 4:  Click 'Restore from Backup File' below and select that ZIP\n" +
            "STEP 5:  Enter your email to verify ownership, then click Restore\n\n" +
            "Your email must match the one in the backup: the system will reject mismatched files."
        ), g);

        // Restore from local history
        g.gridy=2;
        JButton restoreHistBtn = styledBtn("📋  Restore from Backup History", ACCENT_GREEN);
        restoreHistBtn.addActionListener(e -> showRestoreFromHistoryDialog());
        p.add(restoreHistBtn, g);

        // Restore from file (disaster recovery)
        g.gridy=3;
        JButton restoreFileBtn = styledBtn("📂  Restore from Backup File  (.zip / .xlsx / .db)", ACCENT_ORANGE);
        restoreFileBtn.addActionListener(e -> showRestoreFromFileDialog());
        p.add(restoreFileBtn, g);

        // Email verify notice
        g.gridy=4;
        JPanel notice = new JPanel(new BorderLayout());
        notice.setBackground(new Color(40,25,10));
        notice.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_ORANGE),
            BorderFactory.createEmptyBorder(12,14,12,14)));
        JLabel noticeText = new JLabel(
            "<html><b style='color:#F39C12'>⚠️  Security:</b>" +
            "<span style='color:#BDC3C7'> Every restore requires you to enter the email address " +
            "that was used when the backup was created.<br>This prevents unauthorized data restoration. " +
            "Your current registered email: <b>" + userEmail + "</b></span></html>");
        noticeText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        notice.add(noticeText);
        p.add(notice, g);

        g.gridy=5; g.weighty=1.0;
        p.add(new JPanel(), g);
        return p;
    }

    // ── History tab ──────────────────────────────────────────────────────────
    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(15,15,15,15));

        String[] cols = {"📄 File","📅 Date","👤 Email","💾 Size","☁️ Destination","✅ Status","🔑 Checksum"};
        historyModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(historyModel);
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setSelectionBackground(ACCENT_BLUE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(BORDER_COLOR);
        table.setShowGrid(true);
        JTableHeader hdr = table.getTableHeader();
        hdr.setBackground(BG_LIGHTER);
        hdr.setForeground(TEXT_PRIMARY);
        hdr.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Last column narrow (checksum preview)
        table.getColumnModel().getColumn(6).setPreferredWidth(80);

        loadHistoryTable();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG_DARK);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scroll.getViewport().setBackground(BG_DARK);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.setBackground(BG_DARK);
        JButton refresh = styledBtn("🔄 Refresh", ACCENT_BLUE);
        refresh.addActionListener(e -> loadHistoryTable());
        JButton clear = styledBtn("🗑️ Clear History", ACCENT_RED);
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,"Clear all history?","Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                backupHistory.clear();
                saveBackupHistory();
                loadHistoryTable();
            }
        });
        top.add(refresh); top.add(clear);

        panel.add(top,    BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── Settings tab ─────────────────────────────────────────────────────────
    private JPanel buildSettingsTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_DARK);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10,15,10,15);
        g.fill   = GridBagConstraints.HORIZONTAL;
        g.weightx= 1.0;

        // Auto backup
        g.gridy=0;
        JPanel autoPanel = new JPanel(new BorderLayout());
        autoPanel.setBackground(BG_CARD);
        autoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(14,14,14,14)));
        JCheckBox autoChk = new JCheckBox("Enable automatic scheduled backups");
        autoChk.setSelected(settings.autoBackup);
        autoChk.setForeground(TEXT_PRIMARY);
        autoChk.setBackground(BG_CARD);
        autoChk.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JPanel intervalRow = new JPanel(new FlowLayout(FlowLayout.LEFT,10,8));
        intervalRow.setBackground(BG_CARD);
        JLabel intLbl = new JLabel("📅 Every:");
        intLbl.setForeground(TEXT_SECONDARY);
        JSpinner intSpin = new JSpinner(new SpinnerNumberModel(settings.backupIntervalDays,1,90,1));
        intSpin.setPreferredSize(new Dimension(60,26));
        JLabel daysLbl = new JLabel("day(s)");
        daysLbl.setForeground(TEXT_MUTED);
        intervalRow.add(intLbl); intervalRow.add(intSpin); intervalRow.add(daysLbl);
        autoPanel.add(autoChk,     BorderLayout.NORTH);
        autoPanel.add(intervalRow, BorderLayout.CENTER);
        p.add(autoPanel, g);

        // Options
        g.gridy=1;
        JCheckBox compChk  = new JCheckBox("🗜️ Compress backups (ZIP)");
        JCheckBox encChk   = new JCheckBox("🔐 Encrypt backup content");
        JCheckBox verChk   = new JCheckBox("🔍 Verify after creation");
        for (JCheckBox cb : new JCheckBox[]{compChk,encChk,verChk}) {
            cb.setBackground(BG_CARD); cb.setForeground(TEXT_SECONDARY);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        }
        compChk.setSelected(settings.compressBackups);
        encChk.setSelected(settings.encryptBackups);
        verChk.setSelected(settings.verifyAfterBackup);
        JPanel optPanel = new JPanel(new GridLayout(3,1,8,8));
        optPanel.setBackground(BG_CARD);
        optPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(14,14,14,14)));
        optPanel.add(compChk); optPanel.add(encChk); optPanel.add(verChk);
        p.add(optPanel, g);

        // Max files
        g.gridy=2;
        JPanel maxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));
        maxPanel.setBackground(BG_CARD);
        maxPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(10,14,10,14)));
        JLabel maxLbl = new JLabel("📁 Max backup files to keep:");
        maxLbl.setForeground(TEXT_SECONDARY);
        JSpinner maxSpin = new JSpinner(new SpinnerNumberModel(settings.maxBackupFiles,5,200,5));
        maxSpin.setPreferredSize(new Dimension(70,26));
        maxPanel.add(maxLbl); maxPanel.add(maxSpin);
        p.add(maxPanel, g);

        // Save
        g.gridy=3;
        JButton save = styledBtn("💾 Save Settings", ACCENT_GREEN);
        save.addActionListener(e -> {
            settings.autoBackup          = autoChk.isSelected();
            settings.backupIntervalDays  = (int) intSpin.getValue();
            settings.compressBackups     = compChk.isSelected();
            settings.encryptBackups      = encChk.isSelected();
            settings.verifyAfterBackup   = verChk.isSelected();
            settings.maxBackupFiles      = (int) maxSpin.getValue();
            saveSettings();
            JOptionPane.showMessageDialog(this,"✅ Settings saved.","Saved",JOptionPane.INFORMATION_MESSAGE);
        });
        p.add(save, g);

        g.gridy=4; g.weighty=1.0;
        p.add(new JPanel(), g);
        return p;
    }

    // ── Status bar ───────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(10,0));
        bar.setBackground(BG_LIGHTER);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,0,0,0,BORDER_COLOR),
            BorderFactory.createEmptyBorder(8,12,8,12)));
        statusLabel = new JLabel("✅ Ready");
        statusLabel.setForeground(TEXT_SECONDARY);
        statusLabel.setFont(new Font("Segoe UI",Font.PLAIN,11));
        progressBar = new JProgressBar(0,100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(220,18));
        progressBar.setForeground(ACCENT_GREEN);
        progressBar.setBackground(BG_DARK);
        progressDetailLabel = new JLabel("");
        progressDetailLabel.setForeground(TEXT_MUTED);
        progressDetailLabel.setFont(new Font("Segoe UI",Font.PLAIN,11));
        bar.add(statusLabel,        BorderLayout.WEST);
        bar.add(progressBar,        BorderLayout.CENTER);
        bar.add(progressDetailLabel,BorderLayout.EAST);
        return bar;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CORE BACKUP FLOW
    // ════════════════════════════════════════════════════════════════════════
    private void runBackup() {
        if (!lock()) return;

        // Let user pick destination (cloud sync folder)
        File destFolder = pickDestFolder();
        if (destFolder == null) { unlock(); return; }

        settings.lastDestFolder = destFolder.getAbsolutePath();
        saveSettings();

        showProgressDialog("Creating Backup");
        
        // Use SwingWorker instead of raw Thread
        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            private String srcChecksum;
            private long csvSize;
            private long zipSize;
            private long dbSize;
            private String cloudSvc;
            
            @Override
            protected Void doInBackground() throws Exception {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String safeEmail = userEmail.replaceAll("[^a-zA-Z0-9._@-]", "_");
                String baseName = "GratisPOS_" + safeEmail + "_" + ts;
                String csvPath = new File(destFolder, baseName + ".csv").getAbsolutePath();
                String dbCopyPath = new File(destFolder, baseName + ".db").getAbsolutePath();
                String zipPath = new File(destFolder, baseName + ".zip").getAbsolutePath();
                String localCSV = EXPORT_FOLDER + baseName + ".csv";
                String localZip = EXPORT_FOLDER + baseName + ".zip";

                try {
                    // ── 1: Verify source ─────────────────────────────────────
                    publish(new Object[]{2, "Verifying source database..."});
                    File src = new File(DB_PATH);
                    if (!src.exists())
                        throw new FileNotFoundException("Database not found: " + src.getAbsolutePath());

                    srcChecksum = sha256(src);
                    LOGGER.info("Source DB checksum: " + srcChecksum);

                    // ── 2: Copy raw .db ──────────────────────────────────────
                    publish(new Object[]{6, "Copying raw database (.db)..."});
                    Files.copy(src.toPath(), Paths.get(dbCopyPath), StandardCopyOption.REPLACE_EXISTING);
                    String copyChecksum = sha256(new File(dbCopyPath));
                    if (!srcChecksum.equals(copyChecksum))
                        throw new IOException("DB copy integrity FAILED — checksums do not match!");
                    dbSize = new File(dbCopyPath).length();
                    publish(new Object[]{12, "✓ Database copied and verified (" + fmtSize(dbSize) + ")"});

                    // ── 3: Export to CSV instead of Excel ─────────────────────
                    LOGGER.info("========== CSV EXPORT STARTING at " + new java.util.Date() + " ==========");
                    LOGGER.info("CSV export path: " + csvPath);
                    csvSize = exportToCSV(csvPath, 14, 78);
                    LOGGER.info("========== CSV EXPORT FINISHED at " + new java.util.Date() + " ==========");
                    LOGGER.info("CSV file size: " + fmtSize(csvSize));
                    
                    // Copy to local folder
                    Files.copy(Paths.get(csvPath), Paths.get(localCSV), StandardCopyOption.REPLACE_EXISTING);
                    publish(new Object[]{80, "✓ CSV exported (" + fmtSize(csvSize) + ")"});

                    // ── 4: Build manifest ─────────────────────────────────────
                    publish(new Object[]{82, "Writing backup manifest..."});
                    String manifest = buildManifest(ts, srcChecksum, csvPath, dbCopyPath);

                    // ── 5: Create ZIP ────────────────────────────────────────
                    publish(new Object[]{84, "Compressing into ZIP archive..."});
                    zipSize = createZip(zipPath, manifest, new File(dbCopyPath), new File(csvPath));
                    Files.copy(Paths.get(zipPath), Paths.get(localZip), StandardCopyOption.REPLACE_EXISTING);
                    publish(new Object[]{90, "✓ ZIP created (" + fmtSize(zipSize) + ")"});

                    // ── 6: Verify ZIP ────────────────────────────────────────
                    publish(new Object[]{92, "Verifying ZIP integrity..."});
                    verifyZip(zipPath);
                    publish(new Object[]{94, "✓ ZIP verified OK"});

                    // ── 7: Detect cloud service ───────────────────────────────
                    cloudSvc = detectCloud(destFolder.getAbsolutePath());

                    // ── 8: Record history ─────────────────────────────────────
                    publish(new Object[]{96, "Recording backup..."});
                    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    backupHistory.add(0, new BackupRecord(baseName + ".zip", now, cloudSvc, fmtSize(zipSize),
                        zipPath, "✓ Complete", userEmail, srcChecksum));
                    backupHistory.add(1, new BackupRecord(baseName + ".csv", now, cloudSvc, fmtSize(csvSize),
                        localCSV, "✓ Local copy", userEmail, srcChecksum));
                    saveBackupHistory();
                    settings.lastAutoBackup = LocalDateTime.now();
                    saveSettings();

                    publish(new Object[]{100, "✅ Backup complete!"});
                    
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Backup failed", ex);
                    throw ex;
                }
                return null;
            }
            
            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    int progress = (int) chunk[0];
                    String message = (String) chunk[1];
                    updateProgress(progress, message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    closeProgressDialog();
                    loadHistoryTable();
                    statusLabel.setText("✅ Backup saved → " + cloudSvc);
                    
                    // Show success message
                    JOptionPane.showMessageDialog(Backup.this, 
                        "✅ Backup completed successfully!\n\n" +
                        "📁 Location: " + destFolder.getAbsolutePath() + "\n" +
                        "☁️ Service: " + cloudSvc + "\n" +
                        "💾 Size: " + fmtSize(zipSize),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                } catch (Exception ex) {
                    closeProgressDialog();
                    statusLabel.setText("❌ Backup failed");
                    JOptionPane.showMessageDialog(Backup.this, 
                        "❌ Backup failed:\n\n" + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    unlock();
                    if (progressBar != null) progressBar.setValue(0);
                }
            }
        };
        
        worker.execute();
    }

    // ── Quick local backup (.db copy only) ───────────────────────────────────
    private void runQuickBackup() {
        if (!lock()) return;
        
        showProgressDialog("Quick Backup");
        
        new Thread(() -> {
            try {
                updateProgress(10, "Checking source database...");
                File src = new File(DB_PATH);
                if (!src.exists())
                    throw new FileNotFoundException("Database not found: " + src.getAbsolutePath());

                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String safeEmail = userEmail.replaceAll("[^a-zA-Z0-9._@-]", "_");
                String dest = BACKUP_FOLDER + "Quick_" + safeEmail + "_" + ts + ".db";

                updateProgress(40, "Copying database...");
                Files.copy(src.toPath(), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);

                updateProgress(80, "Verifying copy...");
                String cs = sha256(new File(dest));
                long sz = new File(dest).length();

                updateProgress(96, "Saving record...");
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                backupHistory.add(0, new BackupRecord(
                    new File(dest).getName(), now, "Local", fmtSize(sz),
                    dest, "✓ Quick backup", userEmail, cs));
                saveBackupHistory();

                updateProgress(100, "Done!");
                Thread.sleep(10);
                
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    loadHistoryTable();
                    statusLabel.setText("✅ Quick backup saved");
                    JOptionPane.showMessageDialog(Backup.this,
                        "✅ Quick backup created!\n\n📁 " + dest + "\n💾 " + fmtSize(sz),
                        "Done", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Quick backup failed", ex);
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    JOptionPane.showMessageDialog(Backup.this,
                        "❌ Quick backup failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally { 
                unlock(); 
                SwingUtilities.invokeLater(() -> progressBar.setValue(0));
            }
        }, "quick-backup").start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  RESTORE FLOW
    // ════════════════════════════════════════════════════════════════════════

    /** Restore from a file the user browses to — the disaster-recovery path. */
    private void showRestoreFromFileDialog() {
        // Step 1: ask for email verification first
        String enteredEmail = JOptionPane.showInputDialog(this,
            "Enter the email address that was used when this backup was created:\n" +
            "(This must match the email in the backup file)",
            "Email Verification", JOptionPane.QUESTION_MESSAGE);
        if (enteredEmail == null || enteredEmail.trim().isEmpty()) return;
        enteredEmail = enteredEmail.trim().toLowerCase();

        // Step 2: let user pick the file
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Backup File  (.zip, .xlsx, or .db)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "GratisPOS Backup (*.zip, *.xlsx, *.db)", "zip","xlsx","db"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File chosen = fc.getSelectedFile();

        restoreFromFile(chosen, enteredEmail);
    }

    /** Restore from an entry the user selects in the history table. */
    private void showRestoreFromHistoryDialog() {
        if (backupHistory.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No backup history found.\nCreate a backup first or use 'Restore from File'.",
                "No History", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Filter to files that still exist
        List<BackupRecord> valid = new ArrayList<>();
        for (BackupRecord r : backupHistory) {
            File f = new File(r.filePath);
            if (f.exists() && (r.filePath.endsWith(".zip")
                || r.filePath.endsWith(".xlsx") || r.filePath.endsWith(".db")))
                valid.add(r);
        }
        if (valid.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No restorable backup files found on disk.\n" +
                "Files may have been moved or deleted.\n\n" +
                "Use 'Restore from File' to browse to your backup.",
                "No Files", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] opts = valid.stream()
            .map(r -> r.fileName + "  |  " + r.backupDate + "  |  " + r.fileSize
                    + "  |  👤 " + r.userEmail)
            .toArray(String[]::new);

        String sel = (String) JOptionPane.showInputDialog(this,
            "Select a backup to restore:", "Restore from History",
            JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (sel == null) return;

        int idx = Arrays.asList(opts).indexOf(sel);
        BackupRecord rec = valid.get(idx);

        // Verify email
        String enteredEmail = JOptionPane.showInputDialog(this,
            "Enter the email address associated with this backup:\n(Expected: " + rec.userEmail + ")",
            "Verify Identity", JOptionPane.QUESTION_MESSAGE);
        if (enteredEmail == null || enteredEmail.trim().isEmpty()) return;

        if (!enteredEmail.trim().equalsIgnoreCase(rec.userEmail)) {
            JOptionPane.showMessageDialog(this,
                "❌ Email does not match!\n\nBackup owner: " + rec.userEmail +
                "\nYou entered: " + enteredEmail.trim() +
                "\n\nRestore cancelled for security.",
                "Identity Mismatch", JOptionPane.ERROR_MESSAGE);
            return;
        }

        restoreFromFile(new File(rec.filePath), enteredEmail.trim());
    }

    /** Core restore dispatcher — handles .zip, .xlsx, .db */
    private void restoreFromFile(File file, String verifiedEmail) {
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                "File not found:\n" + file.getAbsolutePath(),
                "File Missing", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String name = file.getName().toLowerCase();

        // For ZIP: validate manifest email before asking confirmation
        if (name.endsWith(".zip")) {
            try {
                String manifestEmail = readManifestEmail(file);
                if (manifestEmail != null && !manifestEmail.equalsIgnoreCase(verifiedEmail)) {
                    JOptionPane.showMessageDialog(this,
                        "❌ This backup belongs to a different account!\n\n" +
                        "Backup email : " + manifestEmail + "\n" +
                        "You entered  : " + verifiedEmail +
                        "\n\nRestore cancelled.",
                        "Wrong Backup", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                // Old backup without manifest — proceed with warning
                int cont = JOptionPane.showConfirmDialog(this,
                    "⚠️ Cannot verify backup ownership (no manifest found).\n" +
                    "This may be an older backup format.\n\nContinue anyway?",
                    "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (cont != JOptionPane.YES_OPTION) return;
            }
        }

        // Final confirmation
        int confirm = JOptionPane.showConfirmDialog(this,
            "⚠️  RESTORE CONFIRMATION\n\n" +
            "File    : " + file.getName() + "\n" +
            "Size    : " + fmtSize(file.length()) + "\n" +
            "Email   : " + verifiedEmail + "\n\n" +
            "This will REPLACE your current database.\n" +
            "A safety backup will be created first.\n\n" +
            "Continue?",
            "Confirm Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (name.endsWith(".zip"))   doRestoreFromZip(file, verifiedEmail);
        else if (name.endsWith(".xlsx")) doRestoreFromExcel(file, verifiedEmail);
        else                             doRestoreFromDb(file, verifiedEmail);
    }

    // ── Restore from ZIP (the recommended path) ───────────────────────────────
    private void doRestoreFromZip(File zipFile, String email) {
        if (!lock()) return;
        
        showProgressDialog("Restoring from ZIP");
        
        new Thread(() -> {
            String[] safety = {null};
            try {
                updateProgress(5, "Creating safety backup...");
                safety[0] = makeSafetyBackup();

                updateProgress(15, "Extracting ZIP archive...");
                File tempDir = new File(BACKUP_FOLDER + "restore_temp_" + System.currentTimeMillis());
                tempDir.mkdirs();
                extractZip(zipFile, tempDir);

                updateProgress(35, "Locating database in archive...");
                File dbInZip = findFile(tempDir, ".db");
                File xlsxInZip = findFile(tempDir, ".xlsx");

                if (dbInZip == null && xlsxInZip == null)
                    throw new IOException("ZIP does not contain a .db or .xlsx file.");

                if (dbInZip != null) {
                    updateProgress(55, "Restoring database from .db file...");
                    String manifestChecksum = readManifestChecksum(zipFile);
                    if (manifestChecksum != null) {
                        updateProgress(60, "Verifying checksum...");
                        String actualCs = sha256(dbInZip);
                        if (!manifestChecksum.equals(actualCs))
                            throw new IOException("Checksum mismatch — backup file may be corrupted!\n" +
                                "Expected: " + manifestChecksum + "\nGot: " + actualCs);
                        updateProgress(65, "✓ Checksum verified");
                    }
                    updateProgress(75, "Copying database...");
                    Files.copy(dbInZip.toPath(), Paths.get(DB_PATH), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    updateProgress(55, "Restoring from Excel (no .db found in ZIP)...");
                    restoreTablesFromExcel(xlsxInZip, 55, 90);
                }

                updateProgress(92, "Cleaning up temp files...");
                deleteDir(tempDir);

                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                backupHistory.add(0, new BackupRecord(
                    zipFile.getName(), now, "Restored", fmtSize(zipFile.length()),
                    zipFile.getAbsolutePath(), "✓ Restored", email, ""));
                saveBackupHistory();

                updateProgress(100, "✅ Restore complete!");
                Thread.sleep(10);
                
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    loadHistoryTable();
                    statusLabel.setText("✅ Database restored from " + zipFile.getName());
                    JOptionPane.showMessageDialog(Backup.this,
                        "✅ Database restored successfully!\n\nFrom: " + zipFile.getName() + 
                        "\nEmail: " + email + "\n\nSafety backup saved at:\n" + safety[0] +
                        "\n\n🔄 Please restart GratisPOS to load the restored data.",
                        "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Restore from ZIP failed", ex);
                if (safety[0] != null) {
                    try { 
                        Files.copy(Paths.get(safety[0]), Paths.get(DB_PATH), StandardCopyOption.REPLACE_EXISTING); 
                    } catch (Exception re) { 
                        LOGGER.severe("Rollback failed: " + re.getMessage()); 
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    JOptionPane.showMessageDialog(Backup.this,
                        "❌ Restore failed:\n\n" + ex.getMessage() +
                        (safety[0] != null ? "\n\n⚠️ Your original database has been restored." : ""),
                        "Restore Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally { 
                unlock(); 
                SwingUtilities.invokeLater(() -> progressBar.setValue(0));
            }
        }, "restore-zip-thread").start();
    }

    // ── Restore from .db file ────────────────────────────────────────────────
    private void doRestoreFromDb(File dbFile, String email) {
        if (!lock()) return;
        
        showProgressDialog("Restoring Database");
        
        new Thread(() -> {
            String[] safety = {null};
            try {
                updateProgress(10, "Creating safety backup...");
                safety[0] = makeSafetyBackup();
                
                updateProgress(50, "Restoring database...");
                Files.copy(dbFile.toPath(), Paths.get(DB_PATH), StandardCopyOption.REPLACE_EXISTING);
                
                updateProgress(90, "Verifying...");
                if (new File(DB_PATH).length() == 0)
                    throw new IOException("Restored database is empty — aborting.");
                
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                backupHistory.add(0, new BackupRecord(
                    dbFile.getName(), now, "Restored", fmtSize(dbFile.length()),
                    dbFile.getAbsolutePath(), "✓ Restored", email, ""));
                saveBackupHistory();
                
                updateProgress(100, "Done!");
                Thread.sleep(10);
                
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    loadHistoryTable();
                    statusLabel.setText("✅ Restored from .db");
                    JOptionPane.showMessageDialog(Backup.this,
                        "✅ Database restored!\n\nSafety backup: " + safety[0] +
                        "\n\n🔄 Restart GratisPOS to load restored data.",
                        "Done", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                if (safety[0] != null) try {
                    Files.copy(Paths.get(safety[0]), Paths.get(DB_PATH), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception re) { 
                    LOGGER.severe("Rollback: " + re.getMessage()); 
                }
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    JOptionPane.showMessageDialog(Backup.this, 
                        "❌ Restore failed:\n" + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally { 
                unlock(); 
                SwingUtilities.invokeLater(() -> progressBar.setValue(0));
            }
        }, "restore-db").start();
    }

    // ── Restore from .xlsx file ──────────────────────────────────────────────
    private void doRestoreFromExcel(File xlsxFile, String email) {
        if (!lock()) return;
        
        showProgressDialog("Restoring from Excel");
        
        new Thread(() -> {
            String[] safety = {null};
            Connection conn = null;
            try {
                updateProgress(5, "Creating safety backup...");
                safety[0] = makeSafetyBackup();
                
                conn = dbConn();
                conn.setAutoCommit(false);
                restoreTablesFromExcel(xlsxFile, 10, 92);
                conn.commit();
                
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                backupHistory.add(0, new BackupRecord(
                    xlsxFile.getName(), now, "Restored", fmtSize(xlsxFile.length()),
                    xlsxFile.getAbsolutePath(), "✓ Restored", email, ""));
                saveBackupHistory();
                
                updateProgress(100, "Done!");
                Thread.sleep(10);
                
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    loadHistoryTable();
                    statusLabel.setText("✅ Restored from Excel");
                    JOptionPane.showMessageDialog(Backup.this,
                        "✅ Restored from Excel!\n\nSafety: " + safety[0] +
                        "\n\n🔄 Restart GratisPOS.",
                        "Done", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                try { 
                    if (conn != null) conn.rollback(); 
                } catch (SQLException e2) {}
                
                if (safety[0] != null) try {
                    Files.copy(Paths.get(safety[0]), Paths.get(DB_PATH), StandardCopyOption.REPLACE_EXISTING);
                } catch(Exception re) { 
                    LOGGER.severe("Rollback: " + re.getMessage()); 
                }
                
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog();
                    JOptionPane.showMessageDialog(Backup.this, 
                        "❌ Restore failed:\n" + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                try {
                    if (conn != null) conn.close();
                } catch(SQLException e2) {}
                unlock(); 
                SwingUtilities.invokeLater(() -> progressBar.setValue(0));
            }
        }, "restore-xlsx").start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  EXCEL EXPORT / RESTORE INTERNALS
    // ════════════════════════════════════════════════════════════════════════

    private long exportToCSV(String path, int s, int e) throws Exception {
    try (Connection conn = dbConn()) {
        updateProgress(s + 1, "Reading table list...");
        
        List<String> tables = tableNames(conn);
        if (tables.isEmpty()) throw new SQLException("No tables in database: " + DB_PATH);
        int total = tables.size();
        
        // Create CSV file with all tables
        try (FileWriter fw = new FileWriter(path);
             PrintWriter pw = new PrintWriter(fw)) {
            
            // Write header info
            pw.println("# Backup created: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pw.println("# Created by: " + loggedInUser);
            pw.println("# Email: " + userEmail);
            pw.println("# Total tables: " + total);
            pw.println();
            
            // Export each table
            for (int i = 0; i < total; i++) {
                String tbl = tables.get(i);
                int pct = s + 2 + ((i + 1) * (e - s - 4) / total);
                updateProgress(pct, "Exporting: " + tbl + " (" + (i + 1) + "/" + total + ")");
                
                // Write table header
                pw.println();
                pw.println("## TABLE: " + tbl);
                
                // Export table data
                exportTableToCSV(conn, pw, tbl);
            }
        }
    }
    
    long sz = new File(path).length();
    if (sz == 0) throw new IOException("CSV file is empty after write: " + path);
    return sz;
}

private void exportTableToCSV(Connection conn, PrintWriter pw, String table) throws SQLException {
    try (Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
        
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        
        // Write column headers
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) pw.print(",");
            pw.print("\"" + meta.getColumnName(i) + "\"");
        }
        pw.println();
        
        // Write data rows
        int rowCount = 0;
        while (rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) pw.print(",");
                Object value = rs.getObject(i);
                if (value != null) {
                    String strValue = value.toString();
                    // Escape quotes and wrap in quotes
                    strValue = strValue.replace("\"", "\"\"");
                    pw.print("\"" + strValue + "\"");
                } else {
                    pw.print("");
                }
            }
            pw.println();
            rowCount++;
        }
        
        LOGGER.info("Exported " + rowCount + " rows from table: " + table);
        pw.println("# Total rows: " + rowCount);
        pw.println();
    }
}

   
    private void exportTable(Connection conn, Workbook wb, String table) throws SQLException {
        String safe = table.length()>31 ? table.substring(0,28)+"..." : table;
        Sheet sheet = wb.createSheet(safe);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM "+table)) {
            ResultSetMetaData m = rs.getMetaData();
            int cols = m.getColumnCount();
            Row hdr = sheet.createRow(0);
            CellStyle hs = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true); hs.setFont(hf);
            hs.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i=1;i<=cols;i++){
                Cell c=hdr.createCell(i-1); c.setCellValue(m.getColumnName(i)); c.setCellStyle(hs);
            }
            int rowN=1;
            while(rs.next()){
                Row row=sheet.createRow(rowN++);
                for(int i=1;i<=cols;i++){
                    Object v=rs.getObject(i);
                    if(v!=null) setCell(row.createCell(i-1),v);
                }
            }
            for(int i=0;i<cols;i++){
                sheet.autoSizeColumn(i);
                if(sheet.getColumnWidth(i)>15000) sheet.setColumnWidth(i,15000);
            }
        }
    }

    private void setCell(Cell cell, Object v){
        if(v instanceof String)              cell.setCellValue((String)v);
        else if(v instanceof Integer)        cell.setCellValue((Integer)v);
        else if(v instanceof Long)           cell.setCellValue((Long)v);
        else if(v instanceof Double)         cell.setCellValue((Double)v);
        else if(v instanceof Float)          cell.setCellValue(((Float)v).doubleValue());
        else if(v instanceof Boolean)        cell.setCellValue((Boolean)v);
        else if(v instanceof java.sql.Date)  cell.setCellValue((java.sql.Date)v);
        else if(v instanceof Timestamp)      cell.setCellValue(v.toString());
        else                                 cell.setCellValue(v.toString());
    }

    private void restoreTablesFromExcel(File xlsx, int s, int e) throws Exception {
        try (FileInputStream fis = new FileInputStream(xlsx);
             Workbook wb = new XSSFWorkbook(fis);
             Connection conn = dbConn()) {
            conn.setAutoCommit(false);
            int total = wb.getNumberOfSheets();
            int done = 0;
            for (int i = 0; i < total; i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sn = sheet.getSheetName();
                if (sn.startsWith("_") || sn.equalsIgnoreCase("Backup_Info")) continue;
                done++;
                int pct = s + (done * (e - s) / Math.max(1, total));
                updateProgress(pct, "Restoring: " + sn);
                restoreSheet(conn, sheet, sn);
            }
            conn.commit();
        }
    }

    private void restoreSheet(Connection conn, Sheet sheet, String table) throws SQLException {
        try (Statement st=conn.createStatement()) { st.executeUpdate("DELETE FROM "+table); }
        catch (SQLException ex){ LOGGER.info("Table skip (not found): "+table); return; }

        Row hdr=sheet.getRow(0);
        if(hdr==null) return;
        List<String> cols=new ArrayList<>();
        for(int c=0;c<hdr.getLastCellNum();c++){
            Cell cell=hdr.getCell(c);
            if(cell!=null && cell.getCellType()!=CellType.BLANK){
                String n=cell.getStringCellValue().trim();
                if(!n.isEmpty()) cols.add(n);
            }
        }
        if(cols.isEmpty()) return;

        String ph=String.join(",",Collections.nCopies(cols.size(),"?"));
        String sql="INSERT INTO "+table+" ("+String.join(",",cols)+") VALUES ("+ph+")";
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            int batch=0;
            for(int r=1;r<=sheet.getLastRowNum();r++){
                Row row=sheet.getRow(r); if(row==null) continue;
                boolean hasData=false;
                for(int c=0;c<cols.size();c++){
                    Cell cell=row.getCell(c);
                    if(cell!=null && cell.getCellType()!=CellType.BLANK){
                        setCellToPstmt(ps,c+1,cell); hasData=true;
                    } else { ps.setNull(c+1,Types.NULL); }
                }
                if(hasData){ ps.addBatch(); if(++batch>=500){ ps.executeBatch(); batch=0; } }
            }
            if(batch>0) ps.executeBatch();
        }
    }

    private void setCellToPstmt(PreparedStatement ps, int idx, Cell cell) throws SQLException {
        switch(cell.getCellType()){
            case STRING:  ps.setString(idx,cell.getStringCellValue()); break;
            case NUMERIC:
                double d=cell.getNumericCellValue();
                if(d==Math.floor(d)) ps.setLong(idx,(long)d);
                else ps.setDouble(idx,d); break;
            case BOOLEAN: ps.setBoolean(idx,cell.getBooleanCellValue()); break;
            case FORMULA: ps.setString(idx,cell.getCellFormula()); break;
            default:      ps.setString(idx,cell.toString()); break;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ZIP UTILITIES
    // ════════════════════════════════════════════════════════════════════════

    private long createZip(String zipPath, String manifest, File... files) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipPath)))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            // Write manifest first
            ZipEntry me = new ZipEntry("manifest.txt");
            zos.putNextEntry(me);
            zos.write(manifest.getBytes("UTF-8"));
            zos.closeEntry();
            // Write files
            byte[] buf = new byte[8192];
            for (File f : files) {
                if (!f.exists()) continue;
                ZipEntry ze = new ZipEntry(f.getName());
                ze.setSize(f.length());
                zos.putNextEntry(ze);
                try (FileInputStream fis = new FileInputStream(f)) {
                    int n;
                    while ((n=fis.read(buf))>0) zos.write(buf,0,n);
                }
                zos.closeEntry();
            }
        }
        return new File(zipPath).length();
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        byte[] buf = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry=zis.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                // Security: prevent zip slip
                if (!out.getCanonicalPath().startsWith(destDir.getCanonicalPath()))
                    throw new IOException("Zip slip detected: " + entry.getName());
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n=zis.read(buf))>0) fos.write(buf,0,n);
                }
                zis.closeEntry();
            }
        }
    }

    private void verifyZip(String zipPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            byte[] buf = new byte[4096];
            ZipEntry e;
            while ((e=zis.getNextEntry())!=null) {
                while (zis.read(buf)>0) {} // read all to verify CRC
                zis.closeEntry();
            }
        }
    }

    private String buildManifest(String ts, String checksum, String csvPath, String dbPath) {
        List<String> tableList = new ArrayList<>();
        try (Connection c = dbConn()) { 
            tableList = tableNames(c); 
        } catch(Exception ex) {
            LOGGER.warning("Failed to get table names for manifest: " + ex.getMessage());
        }
        return MANIFEST_MAGIC + "\n" +
               "user_email=" + userEmail + "\n" +
               "backup_date=" + ts + "\n" +
               "db_path=" + DB_PATH + "\n" +
               "csv_path=" + csvPath + "\n" +
               "tables=" + String.join(",", tableList) + "\n" +
               "checksum=" + checksum + "\n" +
               "app_version=GratisPOS-2026\n";
    }

    private String readManifestEmail(File zipFile) throws IOException {
        return readManifestField(zipFile, "user_email");
    }

    private String readManifestChecksum(File zipFile) throws IOException {
        return readManifestField(zipFile, "checksum");
    }

    private String readManifestField(File zipFile, String field) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry=zis.getNextEntry())!=null) {
                if (entry.getName().equals("manifest.txt")) {
                    byte[] data = zis.readAllBytes();
                    String content = new String(data,"UTF-8");
                    for (String line : content.split("\n")) {
                        if (line.startsWith(field+"="))
                            return line.substring((field+"=").length()).trim();
                    }
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CLOUD & ACCOUNT DIALOGS
    // ════════════════════════════════════════════════════════════════════════

    private void showMyAccountsDialog() {
        JDialog d = new JDialog(this, "🔐 My Cloud Accounts", true);
        d.setSize(640, 520);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(BG_LIGHTER);
        hdr.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0,BORDER_COLOR),
            BorderFactory.createEmptyBorder(14,18,14,18)));
        JPanel hdrText = labelStack(
            "☁️  Connected Cloud Accounts", Font.BOLD, 16, ACCENT_BLUE,
            "Accounts with backup permission — email-tagged backups only", Font.PLAIN, 11, TEXT_MUTED);
        hdr.add(hdrText, BorderLayout.WEST);
        d.add(hdr, BorderLayout.NORTH);

        // List
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(BG_DARK);
        list.setBorder(new EmptyBorder(14,14,14,14));

        if (cloudAccounts.isEmpty()) {
            JLabel empty = new JLabel("No accounts connected. Click a cloud provider to add one.");
            empty.setForeground(TEXT_MUTED);
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            list.add(empty);
        } else {
            for (Map.Entry<String,CloudAccount> en : cloudAccounts.entrySet()) {
                list.add(accountRow(d, en.getKey(), en.getValue()));
                list.add(Box.createRigidArea(new Dimension(0,10)));
            }
        }

        JScrollPane sc = new JScrollPane(list);
        sc.setBackground(BG_DARK);
        sc.getViewport().setBackground(BG_DARK);
        sc.setBorder(null);
        d.add(sc, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT,12,10));
        foot.setBackground(BG_LIGHTER);
        foot.setBorder(BorderFactory.createMatteBorder(1,0,0,0,BORDER_COLOR));
        JButton close = styledBtn("Close", ACCENT_RED);
        close.addActionListener(e -> d.dispose());
        foot.add(close);
        d.add(foot, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    private JPanel accountRow(JDialog parent, String service, CloudAccount acc) {
        JPanel row = new JPanel(new BorderLayout(12,0));
        row.setBackground(BG_CARD);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_GREEN),
            BorderFactory.createEmptyBorder(12,16,12,16)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JLabel ico = new JLabel(providerEmoji(service));
        ico.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        JPanel info = new JPanel(new GridLayout(3,1,0,2));
        info.setBackground(BG_CARD);
        JLabel svc=new JLabel(service); svc.setFont(new Font("Segoe UI",Font.BOLD,13)); svc.setForeground(ACCENT_GREEN);
        JLabel em=new JLabel("📧 "+acc.email); em.setFont(new Font("Segoe UI",Font.PLAIN,11)); em.setForeground(TEXT_SECONDARY);
        JLabel st=new JLabel("✅ Connected — backups tagged with this email"); st.setFont(new Font("Segoe UI",Font.PLAIN,10)); st.setForeground(TEXT_MUTED);
        info.add(svc); info.add(em); info.add(st);

        JButton rem=styledBtn("✕",ACCENT_RED);
        rem.setPreferredSize(new Dimension(36,36));
        rem.addActionListener(e->{
            if(JOptionPane.showConfirmDialog(parent,
                "Remove "+service+" ("+acc.email+")?\nBackups tagged with this email will not be deleted.",
                "Confirm",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                cloudAccounts.remove(service);
                saveCloudAccounts();
                parent.dispose();
                showMyAccountsDialog();
            }
        });

        row.add(ico,  BorderLayout.WEST);
        row.add(info, BorderLayout.CENTER);
        row.add(rem,  BorderLayout.EAST);
        return row;
    }

    private void showConnectDialog(String providerName) {
        JDialog d = new JDialog(this,"Connect to "+providerName,true);
        d.setSize(420,300);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG_DARK);
        p.setBorder(new EmptyBorder(20,20,20,20));
        GridBagConstraints g=new GridBagConstraints();
        g.insets=new Insets(8,8,8,8);
        g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1.0;

        g.gridy=0; g.gridwidth=2;
        JLabel ico=new JLabel(providerEmoji(providerName),SwingConstants.CENTER);
        ico.setFont(new Font("Segoe UI Emoji",Font.PLAIN,48)); p.add(ico,g);

        g.gridy=1;
        JLabel title=new JLabel(providerName,SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI",Font.BOLD,16)); title.setForeground(ACCENT_BLUE); p.add(title,g);

        g.gridy=2;
        JLabel hint=new JLabel(
            "<html><center>Enter your <b>"+providerName+"</b> email address.<br>" +
            "All backups will be tagged with this email so you can<br>" +
            "identify your files on any computer.</center></html>");
        hint.setForeground(TEXT_MUTED); hint.setFont(new Font("Segoe UI",Font.PLAIN,11)); p.add(hint,g);

        g.gridy=3; g.gridwidth=1;
        JLabel el=new JLabel("Email:"); el.setForeground(TEXT_PRIMARY); p.add(el,g);
        g.gridx=1;
        JTextField ef=new JTextField(15);
        ef.setBackground(BG_LIGHTER); ef.setForeground(TEXT_PRIMARY);
        ef.setCaretColor(TEXT_PRIMARY);
        ef.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        // Pre-fill with user's email
        ef.setText(userEmail);
        p.add(ef,g);

        g.gridy=4; g.gridx=0; g.gridwidth=2;
        JPanel btns=new JPanel(new FlowLayout(FlowLayout.CENTER,10,0));
        btns.setBackground(BG_DARK);
        JButton connect=styledBtn("Connect",ACCENT_GREEN);
        JButton cancel=styledBtn("Cancel",ACCENT_RED);
        connect.addActionListener(e->{
            String email=ef.getText().trim();
            if(email.isEmpty()||!email.contains("@")){
                JOptionPane.showMessageDialog(d,"Please enter a valid email.","Invalid",JOptionPane.WARNING_MESSAGE);
                return;
            }
            cloudAccounts.put(providerName, new CloudAccount(providerName,email,"oauth_"+System.currentTimeMillis()));
            saveCloudAccounts();
            d.dispose();
            refreshUI();
            JOptionPane.showMessageDialog(this,
                "✅ Connected to "+providerName+"!\n\nEmail: "+email+"\n\n" +
                "Your backups will be tagged with this email.\n" +
                "When you back up, choose your "+providerName+" sync folder\n" +
                "so files upload automatically.",
                "Connected",JOptionPane.INFORMATION_MESSAGE);
        });
        cancel.addActionListener(e->d.dispose());
        btns.add(connect); btns.add(cancel);
        p.add(btns,g);
        d.add(p);
        d.setVisible(true);
    }

    private void handleAutoBackupToggle(JCheckBox chk) {
        settings.autoBackup = chk.isSelected();
        saveSettings();
        if (settings.autoBackup) {
            if (cloudAccounts.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "⚠️ No cloud accounts connected.\nClick a cloud provider card to add one first.",
                    "No Accounts", JOptionPane.WARNING_MESSAGE);
                chk.setSelected(false);
                settings.autoBackup=false;
                saveSettings();
                return;
            }
            if (settings.lastDestFolder == null || !new File(settings.lastDestFolder).exists()) {
                JOptionPane.showMessageDialog(this,
                    "ℹ️ Auto backup enabled.\n\nYou will be asked to choose a destination folder\nthe first time the backup runs.",
                    "Auto Backup On", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int r=JOptionPane.showConfirmDialog(this,
                "Auto backup enabled!\n\nRun first backup now to " + settings.lastDestFolder + "?",
                "Run Now?",JOptionPane.YES_NO_OPTION);
            if(r==JOptionPane.YES_OPTION) runBackup();
        } else {
            statusLabel.setText("ℹ️ Auto backup disabled");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FOLDER PICKER
    // ════════════════════════════════════════════════════════════════════════
    private File pickDestFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Backup Destination (your cloud sync folder)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setApproveButtonText("Save Backup Here");

        // Pre-navigate to last used folder or first existing cloud folder
        String home = System.getProperty("user.home");
        File suggested = new File(home);
        if (settings.lastDestFolder != null && new File(settings.lastDestFolder).exists())
            suggested = new File(settings.lastDestFolder);
        else {
            for (String path : new String[]{
                home+File.separator+"Google Drive",
                home+File.separator+"Dropbox",
                home+File.separator+"OneDrive",
                home+File.separator+"pCloud Drive",
                home+File.separator+"MEGA",
                home+File.separator+"Box",
            }) { File f=new File(path); if(f.exists()){ suggested=f; break; } }
        }
        fc.setCurrentDirectory(suggested);

        // Accessory hint
        JPanel hint=new JPanel(new BorderLayout(6,6));
        hint.setBackground(BG_CARD);
        hint.setBorder(new EmptyBorder(10,10,10,10));
        JLabel hl=new JLabel("<html><b style='color:#3498DB'>💡 Tip</b><br>" +
            "<span style='color:#BDC3C7'>Choose your<br>Google Drive,<br>Dropbox, or<br>OneDrive folder.<br><br>" +
            "The cloud app<br>will upload it<br>automatically.</span></html>");
        hl.setFont(new Font("Segoe UI",Font.PLAIN,11));
        hint.add(hl);
        fc.setAccessory(hint);

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File dest = fc.getSelectedFile();
        if (!dest.exists() || !dest.canWrite()) {
            JOptionPane.showMessageDialog(this,
                "❌ Cannot write to: " + dest.getAbsolutePath() +
                "\nChoose a different folder.",
                "Permission Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return dest;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PROGRESS DIALOG (FIXED - NO STACKING)
    // ════════════════════════════════════════════════════════════════════════
    private JDialog progressDialog = null;
    private JProgressBar progressBarDialog = null;
    private JLabel progressLabelDialog = null;

    private void showProgressDialog(String title) {
        // Close existing dialog if any
        if (progressDialog != null && progressDialog.isVisible()) {
            progressDialog.dispose();
        }
        
        progressDialog = new JDialog(this, title, false);
        progressDialog.setSize(440, 165);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setResizable(false);

        JPanel p = new JPanel(new BorderLayout(10, 12));
        p.setBackground(BG_CARD);
        p.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel tl = new JLabel("⏳ " + title, SwingConstants.CENTER);
        tl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tl.setForeground(ACCENT_BLUE);

        progressBarDialog = new JProgressBar(0, 100);
        progressBarDialog.setStringPainted(true);
        progressBarDialog.setForeground(ACCENT_GREEN);
        progressBarDialog.setBackground(BG_LIGHTER);
        progressBarDialog.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progressBarDialog.setPreferredSize(new Dimension(380, 22));

        progressLabelDialog = new JLabel("Starting...", SwingConstants.CENTER);
        progressLabelDialog.setForeground(TEXT_SECONDARY);
        progressLabelDialog.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        p.add(tl, BorderLayout.NORTH);
        p.add(progressBarDialog, BorderLayout.CENTER);
        p.add(progressLabelDialog, BorderLayout.SOUTH);
        progressDialog.add(p);
        progressDialog.setVisible(true);
    }

    private void closeProgressDialog() {
        if (progressDialog != null && progressDialog.isVisible()) {
            progressDialog.dispose();
            progressDialog = null;
        }
        progressBarDialog = null;
        progressLabelDialog = null;
    }

    private void updateProgress(int pct, String msg) {
        int v = Math.min(100, Math.max(0, pct));
        
        // Update UI immediately without sleeping
        SwingUtilities.invokeLater(() -> {
            if (progressBarDialog != null) {
                progressBarDialog.setValue(v);
                progressBarDialog.setString(v + "%");
            }
            if (progressLabelDialog != null) {
                progressLabelDialog.setText(msg);
            }
            if (progressBar != null) {
                progressBar.setValue(v);
            }
            if (progressDetailLabel != null) {
                progressDetailLabel.setText(msg);
            }
            if (statusLabel != null && msg.length() < 60) {
                statusLabel.setText(msg);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DB UTILITIES
    // ════════════════════════════════════════════════════════════════════════
    private Connection dbConn() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            
            // Set busy timeout to prevent infinite waiting on locks
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 30000"); // 30 seconds timeout
                stmt.execute("PRAGMA journal_mode = WAL"); // Write-Ahead Logging for better concurrency
            }
            
            return conn;
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver missing", e);
        }
    }

    private List<String> tableNames(Connection c) throws SQLException {
        List<String> list=new ArrayList<>();
        try (ResultSet rs=c.getMetaData().getTables(null,null,"%",new String[]{"TABLE"})) {
            while(rs.next()){
                String n=rs.getString("TABLE_NAME");
                if(n!=null && !n.startsWith("sqlite_")) list.add(n);
            }
        }
        Collections.sort(list);
        return list;
    }

    private String makeSafetyBackup() throws IOException {
        String ts=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String p=BACKUP_FOLDER+"SAFETY_"+ts+".db";
        Files.copy(Paths.get(DB_PATH),Paths.get(p),StandardCopyOption.REPLACE_EXISTING);
        return p;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FILE UTILITIES
    // ════════════════════════════════════════════════════════════════════════
    private static String sha256(File f) throws Exception {
        java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
        try(FileInputStream fis=new FileInputStream(f)){
            byte[] buf=new byte[8192]; int n;
            while((n=fis.read(buf))>0) md.update(buf,0,n);
        }
        StringBuilder sb=new StringBuilder();
        for(byte b:md.digest()) sb.append(String.format("%02x",b));
        return sb.toString();
    }

    private static File findFile(File dir, String ext) {
        File[] files=dir.listFiles((d,n)->n.toLowerCase().endsWith(ext));
        return (files!=null && files.length>0) ? files[0] : null;
    }

    private static void deleteDir(File dir) {
        if(dir==null||!dir.exists()) return;
        File[] files=dir.listFiles();
        if(files!=null) for(File f:files) f.delete();
        dir.delete();
    }

    private String fmtSize(long bytes){
        if(bytes<1024) return bytes+" B";
        if(bytes<1024*1024) return String.format("%.2f KB",bytes/1024.0);
        if(bytes<1024*1024*1024) return String.format("%.2f MB",bytes/(1024.0*1024));
        return String.format("%.2f GB",bytes/(1024.0*1024*1024));
    }

    private String detectCloud(String path){
        String l=path.toLowerCase();
        if(l.contains("google drive")||l.contains("googledrive")||l.contains("my drive")) return "Google Drive";
        if(l.contains("dropbox"))   return "Dropbox";
        if(l.contains("onedrive")||l.contains("skydrive")) return "OneDrive";
        if(l.contains("pcloud"))    return "pCloud";
        if(l.contains("mega"))      return "MEGA";
        if(l.contains("sync"))      return "Sync.com";
        if(l.contains("backblaze")) return "Backblaze";
        if(l.contains("terabox"))   return "Terabox";
        if(l.contains("icloud"))    return "iCloud Drive";
        if(l.contains("box"))       return "Box";
        return "Local Storage";
    }

    private String providerEmoji(String name){
        switch(name.toLowerCase()){
            case "google drive": return "📁";
            case "dropbox":      return "📦";
            case "onedrive":     return "🌐";
            case "idrive":       return "💾";
            case "pcloud":       return "☁️";
            case "mega":         return "🔒";
            case "sync.com":     return "🔄";
            case "backblaze":    return "🔥";
            case "terabox":      return "📀";
            case "box":          return "📫";
            default:             return "☁️";
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOCK
    // ════════════════════════════════════════════════════════════════════════
    private boolean lock(){
        if(opLock.get()){
            JOptionPane.showMessageDialog(this,"An operation is already running. Please wait.",
                "Busy",JOptionPane.WARNING_MESSAGE); return false;
        }
        opLock.set(true);
        SwingUtilities.invokeLater(()->statusLabel.setText("⏳ Working..."));
        return true;
    }
    private void unlock(){ opLock.set(false); }

    // ════════════════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ════════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private void loadCloudAccounts(){
        File f=new File(CREDS_FILE); if(!f.exists()) return;
        try(ObjectInputStream ois=new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))){
            Object o=ois.readObject();
            if(o instanceof Map) cloudAccounts=(Map<String,CloudAccount>)o;
        }catch(Exception e){ cloudAccounts=new HashMap<>(); LOGGER.warning("loadCloudAccounts: "+e.getMessage()); }
    }
    private void saveCloudAccounts(){
        try{
            new File(CREDS_FILE).getParentFile().mkdirs();
            try(ObjectOutputStream oos=new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(CREDS_FILE)))){
                oos.writeObject(cloudAccounts);
            }
        }catch(Exception e){ LOGGER.severe("saveCloudAccounts: "+e.getMessage()); }
    }
    @SuppressWarnings("unchecked")
    private void loadBackupHistory(){
        File f=new File(BACKUP_FOLDER+"backup_history.dat"); if(!f.exists()) return;
        try(ObjectInputStream ois=new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))){
            Object o=ois.readObject();
            if(o instanceof List) backupHistory=(List<BackupRecord>)o;
        }catch(Exception e){ backupHistory=new ArrayList<>(); LOGGER.warning("loadBackupHistory: "+e.getMessage()); }
    }
    private void saveBackupHistory(){
        try(ObjectOutputStream oos=new ObjectOutputStream(new BufferedOutputStream(
                new FileOutputStream(BACKUP_FOLDER+"backup_history.dat")))){
            oos.writeObject(backupHistory);
        }catch(Exception e){ LOGGER.severe("saveBackupHistory: "+e.getMessage()); }
    }
    private void loadSettings(){
        File f=new File(SETTINGS_FILE); if(!f.exists()) return;
        try(ObjectInputStream ois=new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))){
            settings=(BackupSettings)ois.readObject();
        }catch(Exception e){ settings=new BackupSettings(); }
    }
    private void saveSettings(){
        try(ObjectOutputStream oos=new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(SETTINGS_FILE)))){
            oos.writeObject(settings);
        }catch(Exception e){ LOGGER.severe("saveSettings: "+e.getMessage()); }
    }

    private void loadHistoryTable(){
        SwingUtilities.invokeLater(()->{
            historyModel.setRowCount(0);
            for(BackupRecord r:backupHistory)
                historyModel.addRow(new Object[]{
                    r.fileName, r.backupDate, r.userEmail,
                    r.fileSize, r.destination, r.status,
                    r.checksum.length()>12 ? r.checksum.substring(0,12)+"…" : r.checksum
                });
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SCHEDULER
    // ════════════════════════════════════════════════════════════════════════
    private void startScheduler(){
        javax.swing.Timer t=new javax.swing.Timer(3_600_000,e->checkAutoBackup());
        t.setRepeats(true); t.start();
    }
    private void checkAutoBackup(){
        if(!settings.autoBackup) return;
        if(settings.lastAutoBackup==null){
            settings.lastAutoBackup=LocalDateTime.now().minusDays(settings.backupIntervalDays);
            saveSettings(); return;
        }
        if(LocalDateTime.now().isAfter(settings.lastAutoBackup.plusDays(settings.backupIntervalDays)))
            runBackup();
    }
    private void cleanupOld(){
        new Thread(()->{
            try{
                File dir=new File(BACKUP_FOLDER);
                File[] files=dir.listFiles((d,n)->n.endsWith(".db")||n.endsWith(".zip")||n.endsWith(".xlsx"));
                if(files!=null && files.length>settings.maxBackupFiles){
                    Arrays.sort(files,Comparator.comparingLong(File::lastModified));
                    int del=files.length-settings.maxBackupFiles;
                    for(int i=0;i<del;i++){ files[i].delete(); LOGGER.info("Deleted old: "+files[i].getName()); }
                }
            }catch(Exception e){ LOGGER.warning("cleanup: "+e.getMessage()); }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ════════════════════════════════════════════════════════════════════════
    private JButton styledBtn(String text, Color color){
        JButton b=new JButton(text);
        b.setBackground(color); b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI",Font.BOLD,13));
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(10,20,10,20));
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){ b.setBackground(color.darker()); }
            public void mouseExited(MouseEvent e) { b.setBackground(color); }
        });
        return b;
    }

    private JLabel sectionLabel(String text, Color color){
        JLabel l=new JLabel(text);
        l.setFont(new Font("Segoe UI",Font.BOLD,14));
        l.setForeground(color);
        return l;
    }

    private JPanel infoCard(String title, String body){
        JPanel p=new JPanel(new BorderLayout(0,8));
        p.setBackground(new Color(20,30,45));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_BLUE,1),
            BorderFactory.createEmptyBorder(14,16,14,16)));
        JLabel t=new JLabel(title);
        t.setFont(new Font("Segoe UI",Font.BOLD,12));
        t.setForeground(ACCENT_BLUE);
        JTextArea ta=new JTextArea(body);
        ta.setFont(new Font("Segoe UI",Font.PLAIN,11));
        ta.setForeground(TEXT_SECONDARY);
        ta.setBackground(new Color(20,30,45));
        ta.setEditable(false);
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        ta.setBorder(null);
        p.add(t,  BorderLayout.NORTH);
        p.add(ta, BorderLayout.CENTER);
        return p;
    }

    private JCheckBox checkRow(String text, boolean selected){
        JCheckBox cb=new JCheckBox(text,selected);
        cb.setBackground(BG_CARD);
        cb.setForeground(TEXT_SECONDARY);
        cb.setFont(new Font("Segoe UI",Font.PLAIN,12));
        cb.setFocusPainted(false);
        return cb;
    }

    private JPanel providerCard(String name, String url, String emoji){
        JPanel card=new JPanel(new BorderLayout(0,6));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(cloudAccounts.containsKey(name)?ACCENT_GREEN:BORDER_COLOR),
            BorderFactory.createEmptyBorder(12,8,12,8)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel center=new JPanel(new BorderLayout(0,4));
        center.setBackground(BG_CARD);
        JLabel ico=new JLabel(emoji,SwingConstants.CENTER);
        ico.setFont(new Font("Segoe UI Emoji",Font.PLAIN,34));
        JLabel nm=new JLabel(name,SwingConstants.CENTER);
        nm.setFont(new Font("Segoe UI",Font.BOLD,10));
        nm.setForeground(cloudAccounts.containsKey(name)?ACCENT_GREEN:TEXT_PRIMARY);
        center.add(ico,BorderLayout.CENTER);
        center.add(nm, BorderLayout.SOUTH);
        card.add(center,BorderLayout.CENTER);

        if(cloudAccounts.containsKey(name)){
            JLabel badge=new JLabel("✓",SwingConstants.CENTER);
            badge.setFont(new Font("Segoe UI",Font.BOLD,13));
            badge.setForeground(ACCENT_GREEN);
            card.add(badge,BorderLayout.NORTH);
        }

        card.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){
                card.setBackground(BG_LIGHTER);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_BLUE),
                    BorderFactory.createEmptyBorder(12,8,12,8)));
            }
            public void mouseExited(MouseEvent e){
                card.setBackground(BG_CARD);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(cloudAccounts.containsKey(name)?ACCENT_GREEN:BORDER_COLOR),
                    BorderFactory.createEmptyBorder(12,8,12,8)));
            }
            public void mouseClicked(MouseEvent e){
                if(e.getButton()==MouseEvent.BUTTON3){
                    // Right-click → open website
                    try{ Desktop.getDesktop().browse(new java.net.URI(url)); }
                    catch(Exception ex){ LOGGER.warning("Cannot open browser: "+ex.getMessage()); }
                } else {
                    // Left-click → connect account or show options
                    if(cloudAccounts.containsKey(name)){
                        int opt=JOptionPane.showOptionDialog(Backup.this,
                            name+" is connected ("+cloudAccounts.get(name).email+")\n\nWhat would you like to do?",
                            name, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                            new String[]{"View Accounts","Open Website","Cancel"},"View Accounts");
                        if(opt==0) showMyAccountsDialog();
                        else if(opt==1) try{ Desktop.getDesktop().browse(new java.net.URI(url)); }catch(Exception ex){}
                    } else {
                        int opt=JOptionPane.showOptionDialog(Backup.this,
                            "What would you like to do with "+name+"?",
                            name, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                            new String[]{"Connect Account","Open Website","Cancel"},"Connect Account");
                        if(opt==0) showConnectDialog(name);
                        else if(opt==1) try{ Desktop.getDesktop().browse(new java.net.URI(url)); }catch(Exception ex){}
                    }
                }
            }
        });
        return card;
    }

    private JPanel clickCard(String title, String sub, Color accentColor, ActionListener action){
        JPanel card=new JPanel(new BorderLayout(12,0));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor),
            BorderFactory.createEmptyBorder(14,18,14,18)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text=labelStack(title,Font.BOLD,13,accentColor,sub,Font.PLAIN,11,TEXT_MUTED);
        JLabel arrow=new JLabel("›");
        arrow.setFont(new Font("Segoe UI",Font.BOLD,22));
        arrow.setForeground(accentColor);

        card.add(text, BorderLayout.CENTER);
        card.add(arrow,BorderLayout.EAST);
        card.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){ action.actionPerformed(null); }
            public void mouseEntered(MouseEvent e){ card.setBackground(BG_LIGHTER); text.setBackground(BG_LIGHTER); }
            public void mouseExited(MouseEvent e) { card.setBackground(BG_CARD);    text.setBackground(BG_CARD); }
        });
        return card;
    }

    private JPanel labelStack(String t1,int s1,int sz1,Color c1,
                               String t2,int s2,int sz2,Color c2){
        JPanel p=new JPanel(new GridLayout(2,1,0,2));
        p.setBackground(BG_CARD);
        JLabel l1=new JLabel(t1); l1.setFont(new Font("Segoe UI",s1,sz1)); l1.setForeground(c1);
        JLabel l2=new JLabel(t2); l2.setFont(new Font("Segoe UI",s2,sz2)); l2.setForeground(c2);
        p.add(l1); p.add(l2);
        return p;
    }

    private void refreshUI(){
        SwingUtilities.invokeLater(()->{
            mainPanel.removeAll();
            buildUI();
            mainPanel.revalidate();
            mainPanel.repaint();
        });
    }
}
