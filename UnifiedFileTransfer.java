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
 * Unified Peer-to-Peer File Transfer Application
 * 
 * <p>This application enables secure file transfer between two computers on a Local Area Network (LAN)
 * using TCP sockets and a custom 3-way handshake authentication protocol. The application provides
 * a unified interface that can operate in either sender or receiver mode.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Multi-file transfer with queue management</li>
 *   <li>Drag-and-drop file selection</li>
 *   <li>Sortable file table with size comparator</li>
 *   <li>Automatic duplicate filename handling</li>
 *   <li>Last directory memory using Java Preferences API</li>
 *   <li>Password-based authentication via 3-way handshake</li>
 *   <li>Real-time progress tracking and speed display</li>
 *   <li>Pause/Resume transfer capability</li>
 *   <li>Transfer completion dialog with timing statistics</li>
 * </ul>
 * 
 * <p><b>Network Protocol:</b></p>
 * <ul>
 *   <li>TCP sockets for reliable data transfer</li>
 *   <li>3-way handshake: SYN → SYN-ACK → ACK</li>
 *   <li>Port range: 49200-49220 (IANA dynamic/private ports)</li>
 *   <li>Buffer size: 8KB (optimized for WiFi networks)</li>
 *   <li>TCP optimizations: setTcpNoDelay(true), 1MB socket buffers</li>
 * </ul>
 * 
 * <p><b>Technical Implementation:</b></p>
 * <ul>
 *   <li>Java Swing for GUI (CardLayout for mode switching)</li>
 *   <li>Multi-threading for non-blocking network I/O</li>
 *   <li>Thread-safe UI updates via SwingUtilities.invokeLater</li>
 *   <li>File size limit: 16 GB per file</li>
 *   <li>Progress updates every 120ms to reduce UI overhead</li>
 * </ul>
 * 
 * @author Shatab Shameer Zaman (Backend Development)
 * @author Nicholas Cordeiro (Backend Development)
 * @author Samantha Whan (Frontend/UI Development)
 * @author Nand Thaker (Testing and Documentation)
 * @version 1.0
 * @since 2026-02-14
 */
public class UnifiedFileTransfer extends JFrame {

    // ══════════════════════════════════════════════════════════════════
    //  CONSTANTS AND PREFERENCES
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Java Preferences API instance for persistent storage of user settings.
     * Used to remember last selected directories across application sessions.
     */
    private static final Preferences prefs = Preferences.userNodeForPackage(UnifiedFileTransfer.class);
    
    /**
     * Preference key for storing the last directory used in sender mode.
     */
    private static final String PREF_LAST_DIR_SEND = "lastDirSend";
    
    /**
     * Preference key for storing the last directory used in receiver mode.
     */
    private static final String PREF_LAST_DIR_RECV = "lastDirRecv";
    
    /**
     * Maximum file size allowed for transfer (16 GB).
     * This limit prevents memory overflow and ensures reasonable transfer times
     * on typical LAN connections.
     */
    private static final long MAX_FILE_SIZE = 16_000_000_000L;

    // ══════════════════════════════════════════════════════════════════
    //  APPLICATION MODE
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Enumeration representing the two operational modes of the application.
     */
    private enum Mode { 
        /** Sender mode - initiates file transfer */ 
        SENDER, 
        /** Receiver mode - accepts incoming files */
        RECEIVER 
    }
    
    /**
     * Current operational mode of the application.
     * Determines which UI panel is displayed and which network operations are active.
     */
    private Mode currentMode = Mode.SENDER;

    // ══════════════════════════════════════════════════════════════════
    //  SENDER STATE VARIABLES
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Queue of files selected for transfer in sender mode.
     * Files are transferred sequentially in the order they appear in this list.
     */
    private List<FileItem> fileQueue = new ArrayList<>();
    
    /**
     * Table model for displaying file metadata in the sender UI.
     * Columns: File Name, Size, Type, Modified Date
     */
    private DefaultTableModel fileTableModel;
    
    /**
     * JTable component displaying the file queue with sortable columns.
     */
    private JTable fileTable;
    
    /**
     * Password input field for sender authentication.
     * This password must match on both sender and receiver sides.
     */
    private JTextField passwordFieldSender;
    
    /**
     * Label displaying the sender's local IP address.
     */
    private JLabel ipLabel;
    
    /**
     * Label displaying the server port number.
     */
    private JLabel portLabel;
    
    /**
     * Text area showing connection information to be shared with the receiver.
     * Includes IP, port, password, file count, and total size.
     */
    private JTextArea connectionInfoArea;
    
    /**
     * Button to start the TCP server and wait for receiver connection.
     */
    private JButton startServerBtn;
    
    /**
     * Button to confirm and initiate file transfer after receiver connects.
     */
    private JButton confirmBtn;
    
    /**
     * Button to open file chooser for adding files to the queue.
     */
    private JButton addFilesBtn;
    
    /**
     * Button to remove selected files from the transfer queue.
     */
    private JButton removeFileBtn;
    
    /**
     * Button to clear all files from the transfer queue.
     */
    private JButton clearAllBtn;
    
    /**
     * Text area displaying the 3-way handshake protocol messages for each file.
     */
    private JTextArea handshakeLogSender;
    
    /**
     * Progress bar showing the current file transfer progress (0-100%).
     */
    private JProgressBar progressBarSender;
    
    /**
     * Label showing current transfer status and speed.
     */
    private JLabel statusLabelSender;
    
    /**
     * Label showing which file is currently being transferred.
     */
    private JLabel currentFileLabel;
    
    /**
     * Server socket listening for incoming receiver connections.
     * Bound to a port in the range 49200-49220.
     */
    private ServerSocket serverSocket;
    
    /**
     * Client socket representing the connection to the receiver.
     * Established after serverSocket.accept() succeeds.
     */
    private Socket clientSocket;
    
    /**
     * Flag indicating whether the transfer is currently paused.
     * Accessed by multiple threads, hence volatile.
     */
    private volatile boolean isPaused = false;
    
    /**
     * Flag indicating whether a file transfer is currently in progress.
     * Used to prevent concurrent transfer operations.
     */
    private volatile boolean isTransferring = false;
    
    /**
     * TCP port number on which the server listens.
     * Dynamically assigned from the range 49200-49220.
     */
    private int serverPort = 49200;
    
    /**
     * Local IP address of the sender machine.
     * Automatically detected using NetworkUtils.getBestIP().
     */
    private String localIP;
    
    /**
     * Password set by the sender for authentication.
     * Must be provided to the receiver for successful connection.
     */
    private String password;
    
    /**
     * Index of the file currently being transferred in the fileQueue list.
     */
    private int currentFileIndex = 0;

    // ══════════════════════════════════════════════════════════════════
    //  RECEIVER STATE VARIABLES
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Text field for entering the sender's IP address.
     */
    private JTextField ipField;
    
    /**
     * Text field for entering the sender's port number.
     */
    private JTextField portField;
    
    /**
     * Password field for entering the sender's authentication password.
     */
    private JTextField passwordFieldReceiver;
    
    /**
     * Label displaying information about the file being received.
     * Shows file name and size after successful handshake.
     */
    private JLabel fileInfoLabel;
    
    /**
     * Button to initiate connection to the sender and start handshake.
     */
    private JButton connectBtn;
    
    /**
     * Text area displaying the 3-way handshake protocol messages.
     */
    private JTextArea handshakeLogReceiver;
    
    /**
     * Progress bar showing download progress for the current file (0-100%).
     */
    private JProgressBar progressBarReceiver;
    
    /**
     * Label showing current download status and speed.
     */
    private JLabel statusLabelReceiver;
    
    /**
     * Text area logging all received files with timestamps and save locations.
     */
    private JTextArea transferLogArea;
    
    /**
     * Socket connection to the sender.
     * Established by connecting to sender's IP and port.
     */
    private Socket receiverSocket;

    // ══════════════════════════════════════════════════════════════════
    //  UI LAYOUT COMPONENTS
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * CardLayout manager for switching between sender and receiver panels.
     */
    private CardLayout cardLayout;
    
    /**
     * Container panel holding both sender and receiver UI panels.
     */
    private JPanel contentPanel;

    /**
     * Constructs the main application window and initializes all components.
     * 
     * <p>Initialization sequence:</p>
     * <ol>
     *   <li>Sets window title and size (900x850 pixels)</li>
     *   <li>Configures window close operation</li>
     *   <li>Detects and prints all network interfaces for debugging</li>
     *   <li>Automatically selects the best local IP address</li>
     *   <li>Builds the complete UI with sender and receiver panels</li>
     *   <li>Centers the window on screen</li>
     * </ol>
     */
    public UnifiedFileTransfer() {
        setTitle("P2P File Transfer — Unified App");
        setSize(900, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        NetworkUtils.printAllInterfaces();
        localIP = NetworkUtils.getBestIP();
        
        buildUI();
        setLocationRelativeTo(null);
    }

    /**
     * Builds the complete user interface with mode toggle and dual panels.
     * 
     * <p>UI Structure:</p>
     * <ul>
     *   <li>Top: Mode toggle buttons (Sender/Receiver)</li>
     *   <li>Center: CardLayout panel containing both operational modes</li>
     * </ul>
     * 
     * <p>The CardLayout allows instant switching between sender and receiver
     * interfaces without recreating components.</p>
     */
    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());
        
        // ── Mode Toggle Panel ─────────────────────────────────────────
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

    /**
     * Constructs the complete sender mode user interface panel.
     * 
     * <p>Panel Sections (top to bottom):</p>
     * <ol>
     *   <li>File Selection: Drag-drop zone + sortable table + management buttons</li>
     *   <li>Password: Authentication password input</li>
     *   <li>Server Control: Start server button</li>
     *   <li>Connection Info: IP, port, password display for sharing with receiver</li>
     *   <li>Transfer Confirmation: Confirm button (enabled after receiver connects)</li>
     *   <li>Handshake Log: Real-time protocol messages for each file</li>
     *   <li>Progress: Current file, overall progress bar, pause/resume control</li>
     * </ol>
     * 
     * @return JComponent containing the complete sender UI in a scrollable panel
     */
    private JComponent buildSenderPanel() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Section 1: File Selection with Table
        JPanel fp = section("1.  Select Files  (Drag & Drop or Add Files)");

        // Initialize sortable file table
        String[] columns = {"File Name", "Size", "Type", "Modified"};
        fileTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(fileTableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(24);
        fileTable.getTableHeader().setReorderingAllowed(false);

        // Enable column sorting with custom size comparator
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(fileTableModel);
        fileTable.setRowSorter(sorter);

        // Custom comparator for Size column - sorts by actual bytes, not string
        sorter.setComparator(1, new Comparator<String>() {
            @Override
            public int compare(String size1, String size2) {
                return Long.compare(parseSize(size1), parseSize(size2));
            }

            /**
             * Converts size string (e.g., "15.23 MB") to bytes for numerical comparison.
             * 
             * @param sizeStr Size string with unit (B, KB, MB, GB)
             * @return Size in bytes as long integer
             */
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

        // Set column widths for optimal display
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setPreferredSize(new Dimension(800, 200));

        // Drag & drop zone with visual feedback
        JPanel dropZone = new JPanel();
        dropZone.setBorder(BorderFactory.createDashedBorder(new Color(100, 150, 200), 2, 5, 5, true));
        dropZone.setPreferredSize(new Dimension(800, 80));
        dropZone.setBackground(new Color(250, 252, 255));
        JLabel dropLabel = new JLabel("📁 Drag & Drop Files Here", JLabel.CENTER);
        dropLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dropLabel.setForeground(new Color(100, 100, 100));
        dropZone.add(dropLabel);

        // Enable drag and drop functionality using TransferHandler
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

        // File management buttons
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

        // Section 2: Password Configuration
        JPanel pp = section("2.  Set Password");
        JPanel pr = row();
        pr.add(new JLabel("Password: "));
        passwordFieldSender = new JTextField(15);
        pr.add(passwordFieldSender);
        pp.add(pr);
        main.add(pp); main.add(gap());

        // Section 3: Server Initialization
        JPanel sp = section("3.  Start Server");
        JPanel sr = row();
        startServerBtn = styled(new JButton("Start Server & Generate Info"), new Color(0, 140, 0));
        startServerBtn.addActionListener(e -> startServer());
        sr.add(startServerBtn);
        sp.add(sr);
        main.add(sp); main.add(gap());

        // Section 4: Connection Information Display
        JPanel ip2 = section("4.  Connection Info  (give to Receiver)");
        ipLabel = mono("IP:   –");
        portLabel = mono("Port: –");
        ip2.add(ipLabel);
        ip2.add(portLabel);
        connectionInfoArea = logBox(4);
        ip2.add(new JScrollPane(connectionInfoArea));
        main.add(ip2); main.add(gap());

        // Section 5: Transfer Confirmation
        JPanel cp = section("5.  Confirm Transfer");
        JPanel cr = row();
        confirmBtn = styled(new JButton("Confirm & Allow Transfer"), new Color(0, 90, 200));
        confirmBtn.setEnabled(false);
        confirmBtn.addActionListener(e -> confirmTransfer());
        cr.add(confirmBtn);
        cp.add(cr);
        main.add(cp); main.add(gap());

        // Section 6: Handshake Protocol Log
        JPanel hp = section("6.  3-Way Handshake Log");
        handshakeLogSender = logBox(4);
        hp.add(new JScrollPane(handshakeLogSender));
        main.add(hp); main.add(gap());

        // Section 7: Transfer Progress Display
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

    /**
     * Constructs the complete receiver mode user interface panel.
     * 
     * <p>Panel Sections (top to bottom):</p>
     * <ol>
     *   <li>Instructions: Visual explanation of 3-way handshake protocol</li>
     *   <li>Connection Details: IP, port, password input fields</li>
     *   <li>File Info: Displays file metadata after handshake completion</li>
     *   <li>Connect Button: Initiates connection and handshake process</li>
     *   <li>Handshake Log: Real-time protocol messages</li>
     *   <li>Progress: Download progress bar and status</li>
     *   <li>Transfer Log: History of all received files with save locations</li>
     * </ol>
     * 
     * @return JComponent containing the complete receiver UI in a scrollable panel
     */
    private JComponent buildReceiverPanel() {
        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Instructional section explaining the handshake protocol
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

        // Section 1: Connection Configuration
        JPanel cp = section("1.  Enter Connection Details  (from Sender's screen)");
        cp.add(labelledField("Sender IP :", ipField = new JTextField("localhost", 16)));
        cp.add(labelledField("Port      :", portField = new JTextField("49200", 10)));
        cp.add(labelledField("Password  :", passwordFieldReceiver = new JPasswordField(16)));
        main.add(cp); main.add(gap());

        // Section 2: File Information Display
        JPanel fp = section("2.  File Information  (filled after handshake)");
        fileInfoLabel = new JLabel("No connection yet.");
        fileInfoLabel.setForeground(Color.GRAY);
        fileInfoLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        fp.add(fileInfoLabel);
        main.add(fp); main.add(gap());

        // Section 3: Connection Initiation
        JPanel btnP = section("3.  Connect & Start Handshake");
        JPanel btnR = row();
        connectBtn = styled(new JButton("Connect & Start Handshake"), new Color(0, 140, 0));
        connectBtn.setPreferredSize(new Dimension(240, 36));
        connectBtn.addActionListener(e -> connectAndHandshake());
        btnR.add(connectBtn);
        btnP.add(btnR);
        main.add(btnP); main.add(gap());

        // Section 4: Handshake Protocol Log
        JPanel hp = section("4.  3-Way Handshake Log");
        handshakeLogReceiver = logBox(6);
        hp.add(new JScrollPane(handshakeLogReceiver));
        main.add(hp); main.add(gap());

        // Section 5: Download Progress Display
        JPanel prp = section("5.  Download Progress");
        statusLabelReceiver = new JLabel("Status: Ready");
        prp.add(statusLabelReceiver);
        progressBarReceiver = new JProgressBar(0, 100);
        progressBarReceiver.setStringPainted(true);
        progressBarReceiver.setPreferredSize(new Dimension(800, 28));
        prp.add(progressBarReceiver);
        main.add(prp); main.add(gap());

        // Section 6: Transfer History Log
        JPanel tlp = section("6.  Transfer Log");
        transferLogArea = logBox(5);
        tlp.add(new JScrollPane(transferLogArea));
        main.add(tlp);

        JScrollPane outer = new JScrollPane(main);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        return outer;
    }

    /**
     * Switches the application between sender and receiver modes.
     * 
     * <p>Updates the current mode state and uses CardLayout to display
     * the appropriate UI panel without recreating components.</p>
     * 
     * @param mode The mode to switch to (SENDER or RECEIVER)
     */
    private void switchMode(Mode mode) {
        currentMode = mode;
        cardLayout.show(contentPanel, mode.toString());
    }

    /**
     * Opens a file chooser dialog for selecting files to add to the transfer queue.
     * 
     * <p>Features:</p>
     * <ul>
     *   <li>Remembers last used directory via Preferences API</li>
     *   <li>Supports multi-file selection</li>
     *   <li>Validates files against MAX_FILE_SIZE limit</li>
     *   <li>Saves selected directory for next use</li>
     * </ul>
     */
    private void selectFiles() {
        String lastDir = prefs.get(PREF_LAST_DIR_SEND, System.getProperty("user.home"));
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setMultiSelectionEnabled(true);

        fc.setFileView(new FileView() {
            @Override
            public String getName(File f) {
                return f.getName();
            }
        });

        fc.addPropertyChangeListener(evt -> {
            if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                fc.rescanCurrentDirectory();
            }
        });

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            if (files.length > 0) {
                prefs.put(PREF_LAST_DIR_SEND, files[0].getParent());
                addFilesToQueue(Arrays.asList(files));
            }
        }
    }

    /**
     * Adds multiple files to the transfer queue with validation.
     * 
     * <p>For each file:</p>
     * <ul>
     *   <li>Checks file size against MAX_FILE_SIZE limit (16 GB)</li>
     *   <li>Creates FileItem with metadata (name, size, type, modified date)</li>
     *   <li>Adds row to file table for display</li>
     *   <li>Skips files exceeding size limit with warning</li>
     * </ul>
     * 
     * @param files List of File objects to add to the queue
     */
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

    /**
     * Removes selected files from the transfer queue and table.
     * 
     * <p>Implementation details:</p>
     * <ul>
     *   <li>Converts view indices to model indices (table may be sorted)</li>
     *   <li>Removes in reverse order to maintain index validity</li>
     *   <li>Updates both fileQueue list and table model</li>
     * </ul>
     */
    private void removeSelectedFiles() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) return;

        List<Integer> modelIndices = new ArrayList<>();
        for (int row : rows) {
            modelIndices.add(fileTable.convertRowIndexToModel(row));
        }

        modelIndices.sort(Collections.reverseOrder());

        for (int modelIndex : modelIndices) {
            fileQueue.remove(modelIndex);
            fileTableModel.removeRow(modelIndex);
        }
    }

    /**
     * Clears all files from the transfer queue and table.
     */
    private void clearAllFiles() {
        fileQueue.clear();
        fileTableModel.setRowCount(0);
    }

    /**
     * Starts the TCP server and waits for receiver connection.
     * 
     * <p>Process:</p>
     * <ol>
     *   <li>Validates that at least one file is queued</li>
     *   <li>Validates that password is set</li>
     *   <li>Prompts user to select IP if multiple interfaces available</li>
     *   <li>Finds free port in range 49200-49220</li>
     *   <li>Creates ServerSocket and starts listening</li>
     *   <li>Displays connection info for sharing with receiver</li>
     *   <li>Blocks on accept() until receiver connects</li>
     *   <li>Enables confirm button after successful connection</li>
     * </ol>
     * 
     * <p>Runs in background thread to avoid blocking UI.</p>
     */
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
                
                clientSocket.setTcpNoDelay(true);
                clientSocket.setSendBufferSize(1048576);
                clientSocket.setReceiveBufferSize(1048576);

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

    /**
     * Confirms the transfer and initiates sequential file transmission.
     */
    private void confirmTransfer() {
        confirmBtn.setEnabled(false);
        setStatusSender("Starting multi-file transfer…");
        currentFileIndex = 0;

        new Thread(this::transferAllFiles).start();
    }

    /**
     * Transfers all files in the queue sequentially using 3-way handshake protocol.
     * 
     * <p>For each file:</p>
     * <ol>
     *   <li>Updates UI with current file info</li>
     *   <li>Performs 3-way handshake</li>
     *   <li>On success: transfers file data</li>
     *   <li>On failure: aborts and resets UI</li>
     * </ol>
     */
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

        try {
            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
            dos.writeUTF("END_OF_TRANSFER");
            dos.flush();
            Thread.sleep(100);
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
                resetSenderUI();
                setStatusSender("Ready to send again. Configure and start server.");
            } else {
                resetSenderUI();
            }
        });
    }

    /**
     * Sends a single file over the established socket connection using buffered I/O.
     * 
     * <p>Transfer Process:</p>
     * <ol>
     *   <li>Reads file in 8KB chunks</li>
     *   <li>Writes each chunk to socket immediately</li>
     *   <li>Updates progress bar every chunk</li>
     *   <li>Respects pause flag</li>
     *   <li>Calculates and logs transfer speed</li>
     * </ol>
     * 
     * @param item FileItem containing the file to send
     * @throws IOException if socket write fails or file cannot be read
     */
    private void sendFile(FileItem item) throws IOException {
        isTransferring = true;
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
        FileInputStream fis = new FileInputStream(item.file);

        byte[] buf = new byte[8192];
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

    /**
     * Initiates connection to sender and begins the handshake process.
     * 
     * <p>Process:</p>
     * <ol>
     *   <li>Validates input fields</li>
     *   <li>Creates TCP socket with timeout</li>
     *   <li>Optimizes socket settings</li>
     *   <li>Calls receiveAllFiles() for multi-file reception</li>
     * </ol>
     */
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
                
                receiverSocket.setTcpNoDelay(true);
                receiverSocket.setSendBufferSize(1048576);
                receiverSocket.setReceiveBufferSize(1048576);

                appendHLogReceiver("TCP connection established ✓");

                receiveAllFiles(password);

            } catch (SocketTimeoutException ex) {
                appendHLogReceiver("ERROR: Connection timeout");
                SwingUtilities.invokeLater(() -> {
                    warn("Connection timeout!");
                    setStatusReceiver("Timeout");
                    connectBtn.setEnabled(true);
                });
            } catch (IOException ex) {
                appendHLogReceiver("──── TRANSFER COMPLETE ──────────────");
                SwingUtilities.invokeLater(() -> {
                    setStatusReceiver("All files received!");
                    resetReceiverUI();
                });
            }
        }).start();
    }

    /**
     * Receives multiple files sequentially from the sender.
     * 
     * <p>Process:</p>
     * <ol>
     *   <li>Prompts user to select save directory once</li>
     *   <li>Performs 3-way handshake for each file</li>
     *   <li>On success: saves file data</li>
     *   <li>On connection close: shows success dialog with time</li>
     *   <li>Continues until sender signals END_OF_TRANSFER</li>
     * </ol>
     * 
     * @param password Authentication password from sender
     * @throws IOException if connection fails
     */
    private void receiveAllFiles(String password) throws IOException {
        int fileCount = 0;
        long transferStartTime = System.currentTimeMillis();
        
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

    /**
     * Receives a single file and saves it to disk with automatic duplicate handling.
     * 
     * <p>Automatic filename handling:</p>
     * <ul>
     *   <li>If filename exists: appends " (1)", " (2)", etc.</li>
     *   <li>Preserves file extension</li>
     * </ul>
     * 
     * @param fileName Original filename from sender
     * @param fileSize Total file size in bytes
     * @param fileNum Sequential file number for logging
     * @param saveDir Directory to save the file
     * @throws IOException if socket read fails or file cannot be written
     */
    private void receiveFile(String fileName, long fileSize, int fileNum, File saveDir) throws IOException {
        File saveTarget = new File(saveDir, fileName);
        
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

        byte[] buf = new byte[8192];
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
    }

    /**
     * Resets the receiver UI to its initial state.
     */
    private void resetReceiverUI() {
        connectBtn.setEnabled(true);
        progressBarReceiver.setValue(0);
        handshakeLogReceiver.setText("");
        transferLogArea.setText("");
        fileInfoLabel.setText("No connection yet.");
        fileInfoLabel.setForeground(Color.GRAY);

        try {
            if (receiverSocket != null && !receiverSocket.isClosed()) {
                receiverSocket.close();
            }
        } catch (IOException ignored) {}

        receiverSocket = null;
    }

    /**
     * Toggles the pause state of the current file transfer.
     */
    private void togglePause() {
        if (!isTransferring) return;
        isPaused = !isPaused;
        setStatusSender(isPaused ? "PAUSED" : "Resumed…");
    }

    /**
     * Resets the sender UI to its initial state.
     */
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

    /**
     * Formats byte size into human-readable string.
     * 
     * @param b Size in bytes
     * @return Formatted size string (e.g., "15.23 MB")
     */
    private String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.2f KB", b / 1024.0);
        if (b < 1073741824L) return String.format("%.2f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    /**
     * Data class representing a file in the transfer queue.
     */
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

            String ext = "";
            int i = name.lastIndexOf('.');
            if (i > 0) {
                ext = name.substring(i + 1).toUpperCase();
            }
            this.type = ext.isEmpty() ? "FILE" : ext;

            this.modified = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(file.lastModified()));
        }
    }

    /**
     * Application entry point.
     * 
     * @param args Command line arguments (unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UnifiedFileTransfer().setVisible(true));
    }
}
