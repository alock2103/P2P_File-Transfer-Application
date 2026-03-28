import javax.swing.*;
import javax.swing.table.*;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Unified P2P File Transfer Application
 * Supports multi-file transfer, sorting, drag-drop, last directory memory
 * Compatible with NetworkUtils.java and Handshake.java
 */
public class UnifiedFileTransfer extends JFrame {

    // ── Preferences for last directory ───────────────────────────────
    private static final Preferences prefs = Preferences.userNodeForPackage(UnifiedFileTransfer.class);
    private static final String PREF_LAST_DIR_SEND = "lastDirSend";
    private static final String PREF_LAST_DIR_RECV = "lastDirRecv";

    // ── Mode ──────────────────────────────────────────────────────────
    private enum Mode { SENDER, RECEIVER }
    private Mode currentMode = Mode.SENDER;

    // ── Sender State ──────────────────────────────────────────────────
    private List<FileItem> fileQueue = new ArrayList<>();
    private DefaultTableModel fileTableModel;
    private JTable fileTable;
    private JTextField passwordFieldSender;
    private JLabel ipLabel, portLabel;
    private JTextArea connectionInfoArea;
    private JButton startServerBtn, confirmBtn, addFilesBtn, removeFileBtn, clearAllBtn;
    private JTextArea handshakeLogSender;
    private JProgressBar progressBarSender;
    private JLabel statusLabelSender, currentFileLabel;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private volatile boolean isPaused = false;
    private volatile boolean isTransferring = false;
    private int serverPort = 49200;
    private String localIP;
    private String password;
    private int currentFileIndex = 0;

    // ── Receiver State ────────────────────────────────────────────────
    private JTextField ipField, portField, passwordFieldReceiver;
    private JLabel fileInfoLabel;
    private JButton connectBtn;
    private JTextArea handshakeLogReceiver;
    private JProgressBar progressBarReceiver;
    private JLabel statusLabelReceiver;
    private JTextArea transferLogArea;
    private Socket receiverSocket;

    // ── UI Panels ─────────────────────────────────────────────────────
    private CardLayout cardLayout;
    private JPanel contentPanel;

    private static final long MAX_FILE_SIZE = 16_000_000_000L; // 16 GB

    public UnifiedFileTransfer() {
        setTitle("P2P File Transfer — Unified App");
        setSize(900, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        NetworkUtils.printAllInterfaces();
        localIP = NetworkUtils.getBestIP();

        buildUI();
        setLocationRelativeTo(null);
    }

    // ══════════════════════════════════════════════════════════════════
    //  UI BUILDER
    // ══════════════════════════════════════════════════════════════════
    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());

        // ── Mode Toggle ───────────────────────────────────────────────
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        modePanel.setBackground(new Color(240, 245, 255));
        modePanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(100, 150, 200)));

        JToggleButton senderBtn = new JToggleButton("📤 SENDER MODE");
        JToggleButton receiverBtn = new JToggleButton("📥 RECEIVER MODE");

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(senderBtn);
        modeGroup.add(receiverBtn);
        senderBtn.setSelected(true);

        senderBtn.setPreferredSize(new Dimension(200, 40));
        receiverBtn.setPreferredSize(new Dimension(200, 40));
        senderBtn.setFont(new Font("Arial", Font.BOLD, 14));
        receiverBtn.setFont(new Font("Arial", Font.BOLD, 14));

        senderBtn.addActionListener(e -> switchMode(Mode.SENDER));
        receiverBtn.addActionListener(e -> switchMode(Mode.RECEIVER));

        modePanel.add(senderBtn);
        modePanel.add(receiverBtn);

        // ── Content Panel with CardLayout ────────────────────────────
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(buildSenderPanel(), "SENDER");
        contentPanel.add(buildReceiverPanel(), "RECEIVER");

        main.add(modePanel, BorderLayout.NORTH);
        main.add(contentPanel, BorderLayout.CENTER);

        add(main);
    }

    // ══════════════════════════════════════════════════════════════════
    //  SENDER PANEL
    // ══════════════════════════════════════════════════════════════════
    private JComponent buildSenderPanel() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // 1. File Selection with Table
        JPanel fp = section("1.  Select Files  (Drag & Drop or Add Files)");

        // File table
        String[] columns = {"File Name", "Size", "Type", "Modified"};
        fileTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(fileTableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(24);
        fileTable.getTableHeader().setReorderingAllowed(false);

        // Enable sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(fileTableModel);
        fileTable.setRowSorter(sorter);

        // Custom comparator for Size column (column 1) - sort by actual bytes not string
        sorter.setComparator(1, new Comparator<String>() {
            @Override
            public int compare(String size1, String size2) {
                return Long.compare(parseSize(size1), parseSize(size2));
            }

            // Convert "15.23 MB" → bytes (as long)
            private long parseSize(String sizeStr) {
                if (sizeStr == null || sizeStr.isEmpty()) return 0;

                String[] parts = sizeStr.split(" ");
                if (parts.length != 2) return 0;

                double num = Double.parseDouble(parts[0]);
                String unit = parts[1];

                switch (unit) {
                    case "B":  return (long) num;
                    case "KB": return (long) (num * 1024);
                    case "MB": return (long) (num * 1048576);
                    case "GB": return (long) (num * 1073741824L);
                    default:   return 0;
                }
            }
        });

        // Column widths
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setPreferredSize(new Dimension(800, 200));

        // Drag & drop zone
        JPanel dropZone = new JPanel();
        dropZone.setBorder(BorderFactory.createDashedBorder(new Color(100, 150, 200), 2, 5, 5, true));
        dropZone.setPreferredSize(new Dimension(800, 80));
        dropZone.setBackground(new Color(250, 252, 255));
        JLabel dropLabel = new JLabel("📁 Drag & Drop Files Here", JLabel.CENTER);
        dropLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dropLabel.setForeground(new Color(100, 100, 100));
        dropZone.add(dropLabel);

        // Enable drag and drop
        dropZone.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    addFilesToQueue(files);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        });

        fp.add(dropZone);
        fp.add(Box.createVerticalStrut(10));

        // Buttons
        JPanel btnRow = row();
        addFilesBtn = new JButton("Add Files");
        removeFileBtn = new JButton("Remove Selected");
        clearAllBtn = new JButton("Clear All");

        addFilesBtn.addActionListener(e -> selectFiles());
        removeFileBtn.addActionListener(e -> removeSelectedFiles());
        clearAllBtn.addActionListener(e -> clearAllFiles());

        btnRow.add(addFilesBtn);
        btnRow.add(removeFileBtn);
        btnRow.add(clearAllBtn);
        fp.add(btnRow);
        fp.add(tableScroll);

        main.add(fp); main.add(gap());

        // 2. Password
        JPanel pp = section("2.  Set Password");
        JPanel pr = row();
        pr.add(new JLabel("Password: "));
        passwordFieldSender = new JTextField(15);
        pr.add(passwordFieldSender);
        pp.add(pr);
        main.add(pp); main.add(gap());

        // 3. Start Server
        JPanel sp = section("3.  Start Server");
        JPanel sr = row();
        startServerBtn = styled(new JButton("Start Server & Generate Info"), new Color(0, 140, 0));
        startServerBtn.addActionListener(e -> startServer());
        sr.add(startServerBtn);
        sp.add(sr);
        main.add(sp); main.add(gap());

        // 4. Connection Info
        JPanel ip2 = section("4.  Connection Info  (give to Receiver)");
        ipLabel = mono("IP:   –");
        portLabel = mono("Port: –");
        ip2.add(ipLabel);
        ip2.add(portLabel);
        connectionInfoArea = logBox(4);
        ip2.add(new JScrollPane(connectionInfoArea));
        main.add(ip2); main.add(gap());

        // 5. Confirm Transfer
        JPanel cp = section("5.  Confirm Transfer");
        JPanel cr = row();
        confirmBtn = styled(new JButton("Confirm & Allow Transfer"), new Color(0, 90, 200));
        confirmBtn.setEnabled(false);
        confirmBtn.addActionListener(e -> confirmTransfer());
        cr.add(confirmBtn);
        cp.add(cr);
        main.add(cp); main.add(gap());

        // 6. Handshake Log
        JPanel hp = section("6.  3-Way Handshake Log");
        handshakeLogSender = logBox(4);
        hp.add(new JScrollPane(handshakeLogSender));
        main.add(hp); main.add(gap());

        // 7. Progress
        JPanel prp = section("7.  Transfer Progress");
        statusLabelSender = new JLabel("Status: Ready");
        currentFileLabel = new JLabel("Current file: –");
        prp.add(statusLabelSender);
        prp.add(currentFileLabel);
        progressBarSender = new JProgressBar(0, 100);
        progressBarSender.setStringPainted(true);
        progressBarSender.setPreferredSize(new Dimension(800, 28));
        prp.add(progressBarSender);

        JPanel ctrl = row();
        JButton pauseBtn = new JButton("Pause / Resume");
        pauseBtn.addActionListener(e -> togglePause());
        ctrl.add(pauseBtn);
        prp.add(ctrl);
        main.add(prp);

        JScrollPane outer = new JScrollPane(main);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        return outer;
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECEIVER PANEL
    // ══════════════════════════════════════════════════════════════════
    private JComponent buildReceiverPanel() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Instructions
        JPanel inst = section("How It Works");
        JLabel il = new JLabel("<html>"
                + "<b>3-Way Handshake Flow:</b><br>"
                + "&nbsp;&nbsp;<font color='blue'>[1] You → Sender :</font>  SYN  (password + session ID)<br>"
                + "&nbsp;&nbsp;<font color='green'>[2] Sender → You :</font>  SYN-ACK  (file name + size confirmed)<br>"
                + "&nbsp;&nbsp;<font color='blue'>[3] You → Sender :</font>  ACK  (READY flag)<br>"
                + "&nbsp;&nbsp;<font color='green'>[4] Sender → You :</font>  File data begins…"
                + "</html>");
        inst.add(il);
        main.add(inst); main.add(gap());

        // 1. Connection Details
        JPanel cp = section("1.  Enter Connection Details  (from Sender's screen)");
        cp.add(labelledField("Sender IP :", ipField = new JTextField("localhost", 16)));
        cp.add(labelledField("Port      :", portField = new JTextField("49200", 10)));
        cp.add(labelledField("Password  :", passwordFieldReceiver = new JPasswordField(16)));
        main.add(cp); main.add(gap());

        // 2. File Info
        JPanel fp = section("2.  File Information  (filled after handshake)");
        fileInfoLabel = new JLabel("No connection yet.");
        fileInfoLabel.setForeground(Color.GRAY);
        fileInfoLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        fp.add(fileInfoLabel);
        main.add(fp); main.add(gap());

        // 3. Connect Button
        JPanel btnP = section("3.  Connect & Start Handshake");
        JPanel btnR = row();
        connectBtn = styled(new JButton("Connect & Start Handshake"), new Color(0, 140, 0));
        connectBtn.setPreferredSize(new Dimension(240, 36));
        connectBtn.addActionListener(e -> connectAndHandshake());
        btnR.add(connectBtn);
        btnP.add(btnR);
        main.add(btnP); main.add(gap());

        // 4. Handshake Log
        JPanel hp = section("4.  3-Way Handshake Log");
        handshakeLogReceiver = logBox(6);
        hp.add(new JScrollPane(handshakeLogReceiver));
        main.add(hp); main.add(gap());

        // 5. Progress
        JPanel prp = section("5.  Download Progress");
        statusLabelReceiver = new JLabel("Status: Ready");
        prp.add(statusLabelReceiver);
        progressBarReceiver = new JProgressBar(0, 100);
        progressBarReceiver.setStringPainted(true);
        progressBarReceiver.setPreferredSize(new Dimension(800, 28));
        prp.add(progressBarReceiver);
        main.add(prp); main.add(gap());

        // 6. Transfer Log
        JPanel tlp = section("6.  Transfer Log");
        transferLogArea = logBox(5);
        tlp.add(new JScrollPane(transferLogArea));
        main.add(tlp);

        JScrollPane outer = new JScrollPane(main);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        return outer;
    }

    // ══════════════════════════════════════════════════════════════════
    //  MODE SWITCHING
    // ══════════════════════════════════════════════════════════════════
    private void switchMode(Mode mode) {
        currentMode = mode;
        cardLayout.show(contentPanel, mode.toString());
    }

    // ══════════════════════════════════════════════════════════════════
    //  SENDER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════
    private void selectFiles() {
        String lastDir = prefs.get(PREF_LAST_DIR_SEND, System.getProperty("user.home"));
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setMultiSelectionEnabled(true);

        // Add sorting to file chooser
        fc.setFileView(new FileView() {
            @Override
            public String getName(File f) {
                return f.getName();
            }
        });

        // Sort files in the dialog
        fc.addPropertyChangeListener(evt -> {
            if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                // Trigger re-sort when directory changes
                fc.rescanCurrentDirectory();
            }
        });

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            if (files.length > 0) {
                // Save the directory for next time
                prefs.put(PREF_LAST_DIR_SEND, files[0].getParent());
                addFilesToQueue(Arrays.asList(files));
            }
        }
    }

    private void addFilesToQueue(List<File> files) {
        for (File file : files) {
            if (file.length() > MAX_FILE_SIZE) {
                warn("File " + file.getName() + " exceeds 16 GB limit! Skipped.");
                continue;
            }

            FileItem item = new FileItem(file);
            fileQueue.add(item);

            fileTableModel.addRow(new Object[]{
                    item.name,
                    fmt(item.size),
                    item.type,
                    item.modified
            });
        }
    }

    private void removeSelectedFiles() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) return;

        // Convert view indices to model indices
        List<Integer> modelIndices = new ArrayList<>();
        for (int row : rows) {
            modelIndices.add(fileTable.convertRowIndexToModel(row));
        }

        // Sort in reverse to remove from end first
        modelIndices.sort(Collections.reverseOrder());

        for (int modelIndex : modelIndices) {
            fileQueue.remove(modelIndex);
            fileTableModel.removeRow(modelIndex);
        }
    }

    private void clearAllFiles() {
        fileQueue.clear();
        fileTableModel.setRowCount(0);
    }

    private void startServer() {
        if (fileQueue.isEmpty()) {
            warn("Please add at least one file!");
            return;
        }

        password = passwordFieldSender.getText().trim();
        if (password.isEmpty()) {
            warn("Please set a password!");
            return;
        }

        List<String> ips = NetworkUtils.getRealIPs();
        if (ips.size() > 1) localIP = NetworkUtils.letUserPickIP(this);

        new Thread(() -> {
            try {
                serverPort = NetworkUtils.findFreePort(49200);
                serverSocket = new ServerSocket(serverPort);

                SwingUtilities.invokeLater(() -> {
                    ipLabel.setText("IP:   " + localIP);
                    portLabel.setText("Port: " + serverPort);

                    StringBuilder info = new StringBuilder("═══ GIVE THIS TO RECEIVER ═══\n");
                    info.append("IP       :  ").append(localIP).append("\n");
                    info.append("Port     :  ").append(serverPort).append("\n");
                    info.append("Password :  ").append(password).append("\n");
                    info.append("Files    :  ").append(fileQueue.size()).append(" file(s)\n");
                    long totalSize = fileQueue.stream().mapToLong(f -> f.size).sum();
                    info.append("Total    :  ").append(fmt(totalSize));

                    connectionInfoArea.setText(info.toString());
                    setStatusSender("Server started. Waiting for receiver…");
                    startServerBtn.setEnabled(false);
                    addFilesBtn.setEnabled(false);
                    removeFileBtn.setEnabled(false);
                    clearAllBtn.setEnabled(false);
                    passwordFieldSender.setEnabled(false);
                });

                clientSocket = serverSocket.accept();
                String from = clientSocket.getInetAddress().getHostAddress();

                // Optimize TCP socket for faster transfer
                clientSocket.setTcpNoDelay(true);           // Disable Nagle's algorithm
                clientSocket.setSendBufferSize(1048576);    // 1MB send buffer
                clientSocket.setReceiveBufferSize(1048576); // 1MB receive buffer

                SwingUtilities.invokeLater(() -> {
                    setStatusSender("Receiver connected from " + from + " — click Confirm");
                    confirmBtn.setEnabled(true);
                });

            } catch (IOException ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    warn("Failed to start server:\n" + ex.getMessage());
                    resetSenderUI();
                });
            }
        }).start();
    }

    private void confirmTransfer() {
        confirmBtn.setEnabled(false);
        setStatusSender("Starting multi-file transfer…");
        currentFileIndex = 0;

        new Thread(this::transferAllFiles).start();
    }

    private void transferAllFiles() {
        for (currentFileIndex = 0; currentFileIndex < fileQueue.size(); currentFileIndex++) {
            FileItem item = fileQueue.get(currentFileIndex);

            SwingUtilities.invokeLater(() ->
                    currentFileLabel.setText("Current file: " + item.name + " (" + (currentFileIndex + 1) + "/" + fileQueue.size() + ")"));

            appendHandshakeLogSender("──── FILE " + (currentFileIndex + 1) + "/" + fileQueue.size() + " ────────────────");
            appendHandshakeLogSender("──── HANDSHAKE START ────────────────");

            try {
                Handshake.Result result = Handshake.performSenderHandshake(
                        clientSocket,
                        password,
                        item.name,
                        item.size,
                        new Handshake.HandshakeLog() {
                            public void info(String m) { appendHandshakeLogSender("  ✔  " + m); }
                            public void warn(String m) { appendHandshakeLogSender("  ✘  " + m); }
                        }
                );

                if (!result.success) {
                    appendHandshakeLogSender("──── HANDSHAKE FAILED ───────────────");
                    appendHandshakeLogSender("Reason: " + result.reason);
                    SwingUtilities.invokeLater(() -> {
                        setStatusSender("Handshake failed: " + result.reason);
                        warn("Handshake failed:\n" + result.reason);
                        resetSenderUI();
                    });
                    return;
                }

                appendHandshakeLogSender("──── HANDSHAKE COMPLETE  ✓ ──────────");
                appendHandshakeLogSender("Session: " + result.sessionId);
                SwingUtilities.invokeLater(() ->
                        setStatusSender("Sending " + item.name + "…"));

                sendFile(item);

            } catch (IOException ex) {
                ex.printStackTrace();
                appendHandshakeLogSender("ERROR: " + ex.getMessage());
                SwingUtilities.invokeLater(() ->
                        setStatusSender("Error: " + ex.getMessage()));
                return;
            }
        }

        // All files sent - signal end of transfer
        try {
            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
            dos.writeUTF("END_OF_TRANSFER");
            dos.flush();
            Thread.sleep(100); // Give receiver time to read
            clientSocket.close();
            serverSocket.close();
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            setStatusSender("All files sent successfully!");
            currentFileLabel.setText("Transfer complete!");

            int result = JOptionPane.showConfirmDialog(this,
                    String.format("All %d files sent successfully!\n\nDo you want to send more files?", fileQueue.size()),
                    "Transfer Complete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                // Reset for another transfer
                resetSenderUI();
                setStatusSender("Ready to send again. Configure and start server.");
            } else {
                // Just re-enable UI but keep files in queue
                resetSenderUI();
            }
        });
    }

    private void sendFile(FileItem item) throws IOException {
        isTransferring = true;
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
        FileInputStream fis = new FileInputStream(item.file);

        byte[] buf = new byte[8192]; // 8KB - optimal for your network
        long total = item.size;
        long sent = 0;
        int n;
        long t0 = System.currentTimeMillis();

        while ((n = fis.read(buf)) != -1) {
            while (isPaused) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
            dos.write(buf, 0, n);
            sent += n;

            final long s = sent;
            final int pct = (int) (sent * 100 / total);
            SwingUtilities.invokeLater(() -> {
                progressBarSender.setValue(pct);
                setStatusSender(String.format("Sending %s… %d%%  (%.2f / %.2f MB)",
                        item.name, pct, s / 1e6, total / 1e6));
            });
        }

        fis.close();
        dos.flush();
        isTransferring = false;

        double secs = (System.currentTimeMillis() - t0) / 1000.0;
        double speed = (total / 1e6) / secs;

        appendHandshakeLogSender(String.format("File sent in %.1f s at %.2f MB/s", secs, speed));
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECEIVER FUNCTIONS
    // ══════════════════════════════════════════════════════════════════
    private void connectAndHandshake() {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();
        String password = passwordFieldReceiver.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty() || password.isEmpty()) {
            warn("Please fill in all fields!");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            warn("Port must be a number!");
            return;
        }

        connectBtn.setEnabled(false);
        handshakeLogReceiver.setText("");
        transferLogArea.setText("");
        progressBarReceiver.setValue(0);
        fileInfoLabel.setText("Connecting…");
        fileInfoLabel.setForeground(Color.DARK_GRAY);

        new Thread(() -> {
            try {
                appendHLogReceiver("──── CONNECTION ─────────────────────");
                appendHLogReceiver("Connecting to " + ip + ":" + port + "…");
                setStatusReceiver("Connecting…");

                receiverSocket = new Socket();
                receiverSocket.connect(new InetSocketAddress(ip, port), 10_000);

                // Optimize TCP socket for faster transfer
                receiverSocket.setTcpNoDelay(true);           // Disable Nagle's algorithm
                receiverSocket.setSendBufferSize(1048576);    // 1MB send buffer
                receiverSocket.setReceiveBufferSize(1048576); // 1MB receive buffer

                appendHLogReceiver("TCP connection established ✓");

                // Receive multiple files in a loop
                receiveAllFiles(password);

            } catch (SocketTimeoutException ex) {
                appendHLogReceiver("ERROR: Connection timeout");
                SwingUtilities.invokeLater(() -> {
                    warn("Connection timeout!");
                    setStatusReceiver("Timeout");
                    connectBtn.setEnabled(true);
                });
            } catch (IOException ex) {
                // Normal end - sender closed connection after all files sent
                appendHLogReceiver("──── TRANSFER COMPLETE ──────────────");
                SwingUtilities.invokeLater(() -> {
                    setStatusReceiver("All files received!");
                    resetReceiverUI();
                });
            }
        }).start();
    }

    private void receiveAllFiles(String password) throws IOException {
        int fileCount = 0;
        long transferStartTime = System.currentTimeMillis(); // Track total transfer time

        // ── SELECT SAVE DIRECTORY ONCE FOR ALL FILES ──────────────────
        String lastDir = prefs.get(PREF_LAST_DIR_RECV, System.getProperty("user.home"));
        File[] saveDirectory = {null};

        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser fc = new JFileChooser(lastDir);
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setDialogTitle("Select folder to save all files");

                if (fc.showDialog(this, "Select Folder") == JFileChooser.APPROVE_OPTION) {
                    saveDirectory[0] = fc.getSelectedFile();
                    prefs.put(PREF_LAST_DIR_RECV, saveDirectory[0].getAbsolutePath());
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (saveDirectory[0] == null) {
            appendHLogReceiver("Transfer cancelled by user.");
            receiverSocket.close();
            SwingUtilities.invokeLater(() -> {
                setStatusReceiver("Cancelled");
                connectBtn.setEnabled(true);
            });
            return;
        }

        final File saveDir = saveDirectory[0];
        appendHLogReceiver("Saving all files to: " + saveDir.getAbsolutePath());

        while (true) {
            fileCount++;
            final int currentFileNum = fileCount;
            appendHLogReceiver("──── FILE " + currentFileNum + " ────────────────────");
            appendHLogReceiver("──── HANDSHAKE START ────────────────");

            Handshake.Result result;
            try {
                result = Handshake.performReceiverHandshake(
                        receiverSocket,
                        password,
                        new Handshake.HandshakeLog() {
                            public void info(String m) { appendHLogReceiver("  ✔  " + m); }
                            public void warn(String m) { appendHLogReceiver("  ✘  " + m); }
                        }
                );
            } catch (Exception e) {
                // Connection closed = all files received
                final int totalFiles = fileCount - 1;
                final double totalSeconds = (System.currentTimeMillis() - transferStartTime) / 1000.0;

                appendHLogReceiver("──── ALL FILES RECEIVED ─────────────");

                String timeStr;
                if (totalSeconds < 60) {
                    timeStr = String.format("%.1f seconds", totalSeconds);
                } else {
                    double minutes = totalSeconds / 60.0;
                    timeStr = String.format("%.1f minutes", minutes);
                }

                final String finalTimeStr = timeStr;
                SwingUtilities.invokeLater(() -> {
                    setStatusReceiver("Transfer complete!");
                    JOptionPane.showMessageDialog(this,
                            String.format("Transfer Successful!\n\n%d file(s) received\nTime: %s",
                                    totalFiles, finalTimeStr),
                            "Transfer Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                    resetReceiverUI();
                });
                receiverSocket.close();
                return;
            }

            if (!result.success) {
                // Actual handshake failure
                appendHLogReceiver("──── HANDSHAKE FAILED ───────────────");
                appendHLogReceiver("Reason: " + result.reason);
                SwingUtilities.invokeLater(() -> {
                    setStatusReceiver("Handshake failed");
                    warn("Handshake failed:\n" + result.reason);
                    fileInfoLabel.setText("Connection rejected.");
                    fileInfoLabel.setForeground(Color.RED);
                    connectBtn.setEnabled(true);
                });
                receiverSocket.close();
                return;
            }

            appendHLogReceiver("──── HANDSHAKE COMPLETE  ✓ ──────────");
            appendHLogReceiver("File: " + result.fileName);
            appendHLogReceiver("Size: " + fmt(result.fileSize));

            SwingUtilities.invokeLater(() -> {
                fileInfoLabel.setText("File " + currentFileNum + ": " + result.fileName + "  (" + fmt(result.fileSize) + ")");
                fileInfoLabel.setForeground(new Color(0, 120, 0));
                setStatusReceiver("Receiving file " + currentFileNum + "…");
            });

            receiveFile(result.fileName, result.fileSize, currentFileNum, saveDir);
        }
    }

    private void receiveFile(String fileName, long fileSize, int fileNum, File saveDir) throws IOException {
        // Create save target in the selected directory
        File saveTarget = new File(saveDir, fileName);

        // Handle duplicate filenames automatically
        if (saveTarget.exists()) {
            String baseName = fileName;
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            }

            int counter = 1;
            while (saveTarget.exists()) {
                saveTarget = new File(saveDir, baseName + " (" + counter + ")" + extension);
                counter++;
            }
        }

        appendTLogReceiver("File " + fileNum + ": " + saveTarget.getAbsolutePath());
        setStatusReceiver("Receiving file " + fileNum + "…");

        InputStream in = receiverSocket.getInputStream();
        FileOutputStream fos = new FileOutputStream(saveTarget);

        byte[] buf = new byte[8192]; // 8KB - optimal for your network
        long received = 0;
        int n;
        long t0 = System.currentTimeMillis();
        long lastUpd = t0;

        while (received < fileSize) {
            int toRead = (int) Math.min(buf.length, fileSize - received);
            n = in.read(buf, 0, toRead);
            if (n == -1) {
                appendTLogReceiver("WARNING: stream ended early!");
                break;
            }
            fos.write(buf, 0, n);
            received += n;

            final long r = received;
            final int pct = (int) (received * 100 / fileSize);
            long now = System.currentTimeMillis();
            if (now - lastUpd > 120) {
                SwingUtilities.invokeLater(() -> {
                    progressBarReceiver.setValue(pct);
                    setStatusReceiver(String.format("Receiving… %d%%  (%.2f / %.2f MB)",
                            pct, r / 1e6, fileSize / 1e6));
                });
                lastUpd = now;
            }
        }

        fos.close();

        double secs = (System.currentTimeMillis() - t0) / 1000.0;
        double speed = (fileSize / 1e6) / secs;

        appendTLogReceiver(String.format("File %d complete! %.1f s, %.2f MB/s", fileNum, secs, speed));
        appendTLogReceiver("Saved: " + saveTarget.getAbsolutePath());

        SwingUtilities.invokeLater(() -> {
            progressBarReceiver.setValue(100);
            setStatusReceiver(String.format("File %d done! %.2f MB/s", fileNum, speed));
        });

        // Note: Don't close socket here — more files may be coming
        // The loop in receiveAllFiles will continue or handle errors
    }

    private void resetReceiverUI() {
        connectBtn.setEnabled(true);
        progressBarReceiver.setValue(0);
        handshakeLogReceiver.setText("");
        transferLogArea.setText("");
        fileInfoLabel.setText("No connection yet.");
        fileInfoLabel.setForeground(Color.GRAY);

        // Close socket if still open
        try {
            if (receiverSocket != null && !receiverSocket.isClosed()) {
                receiverSocket.close();
            }
        } catch (IOException ignored) {}

        receiverSocket = null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════
    private void togglePause() {
        if (!isTransferring) return;
        isPaused = !isPaused;
        setStatusSender(isPaused ? "PAUSED" : "Resumed…");
    }

    private void resetSenderUI() {
        startServerBtn.setEnabled(true);
        addFilesBtn.setEnabled(true);
        removeFileBtn.setEnabled(true);
        clearAllBtn.setEnabled(true);
        passwordFieldSender.setEnabled(true);
        confirmBtn.setEnabled(false);
        progressBarSender.setValue(0);
        currentFileLabel.setText("Current file: –");
        handshakeLogSender.setText("");
        connectionInfoArea.setText("");
        ipLabel.setText("IP:   –");
        portLabel.setText("Port: –");

        // Close sockets if still open
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        clientSocket = null;
        serverSocket = null;
    }

    private void setStatusSender(String msg) {
        SwingUtilities.invokeLater(() -> statusLabelSender.setText("Status: " + msg));
    }

    private void setStatusReceiver(String msg) {
        SwingUtilities.invokeLater(() -> statusLabelReceiver.setText("Status: " + msg));
    }

    private void appendHandshakeLogSender(String msg) {
        SwingUtilities.invokeLater(() -> {
            String ts = String.format("[%tT]  ", System.currentTimeMillis());
            handshakeLogSender.append(ts + msg + "\n");
            handshakeLogSender.setCaretPosition(handshakeLogSender.getDocument().getLength());
        });
    }

    private void appendHLogReceiver(String msg) {
        SwingUtilities.invokeLater(() -> {
            String ts = String.format("[%tT]  ", System.currentTimeMillis());
            handshakeLogReceiver.append(ts + msg + "\n");
            handshakeLogReceiver.setCaretPosition(handshakeLogReceiver.getDocument().getLength());
        });
    }

    private void appendTLogReceiver(String msg) {
        SwingUtilities.invokeLater(() -> {
            String ts = String.format("[%tT]  ", System.currentTimeMillis());
            transferLogArea.append(ts + msg + "\n");
            transferLogArea.setCaretPosition(transferLogArea.getDocument().getLength());
        });
    }

    // ── Layout Helpers ────────────────────────────────────────────────
    private JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title,
                0, 0, new Font("Arial", Font.BOLD, 12)));
        return p;
    }

    private JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT));
    }

    private Component gap() {
        return Box.createVerticalStrut(10);
    }

    private JPanel labelledField(String label, JTextField field) {
        JPanel r = row();
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(90, 24));
        r.add(l);
        r.add(field);
        return r;
    }

    private JLabel mono(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Monospaced", Font.BOLD, 13));
        return l;
    }

    private JButton styled(JButton b, Color bg) {
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        return b;
    }

    private JTextArea logBox(int rows) {
        JTextArea a = new JTextArea(rows, 65);
        a.setEditable(false);
        a.setFont(new Font("Monospaced", Font.PLAIN, 11));
        a.setBackground(new Color(245, 245, 245));
        return a;
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    private String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.2f KB", b / 1024.0);
        if (b < 1073741824L) return String.format("%.2f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    // ══════════════════════════════════════════════════════════════════
    //  FILE ITEM CLASS
    // ══════════════════════════════════════════════════════════════════
    static class FileItem {
        File file;
        String name;
        long size;
        String type;
        String modified;

        FileItem(File file) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();

            // Get file type
            String ext = "";
            int i = name.lastIndexOf('.');
            if (i > 0) {
                ext = name.substring(i + 1).toUpperCase();
            }
            this.type = ext.isEmpty() ? "FILE" : ext;

            // Get last modified
            this.modified = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(file.lastModified()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  MAIN
    // ══════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UnifiedFileTransfer().setVisible(true));
    }
}