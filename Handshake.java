import java.io.*;
import java.net.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  THREE-WAY HANDSHAKE PROTOCOL
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Mirrors TCP's SYN → SYN-ACK → ACK pattern but at
 *  the application layer, so both sides KNOW the connection
 *  is alive and authenticated before any file data moves.
 *
 *  FLOW:
 *
 *  P1 (Receiver)                       P2 (Sender / Server)
 *  ─────────────                       ────────────────────
 *  [1] Sends SYN   ──────────────────► Receives SYN
 *      {password, sessionId}            Verifies password
 *                                       Stores sessionId
 *
 *                  ◄────────────────── [2] Sends SYN-ACK
 *                                          {status, sessionId,
 *                                           fileName, fileSize}
 *  Receives SYN-ACK
 *  Checks status == OK
 *  Checks sessionId matches
 *
 *  [3] Sends ACK   ──────────────────► Receives ACK
 *      {sessionId, READY}               Checks READY flag
 *
 *                                       [4] Starts sending file ──►
 * //ZAMAN74//ZAMAN74//ZAMAN74//ZAMAN74
 * ═══════════════════════════════════════════════════════════════════
 */
public class Handshake {

    // ── Message type tokens ──────────────────────────────────────────
    public static final String SYN     = "SYN";
    public static final String SYN_ACK = "SYN-ACK";
    public static final String ACK     = "ACK";
    public static final String NACK    = "NACK";   // negative ack (error)
    public static final String READY   = "READY";  // P1 ready to receive

    // ── Status codes inside SYN-ACK ─────────────────────────────────
    public static final String STATUS_OK            = "OK";
    public static final String STATUS_WRONG_PASS    = "ERR_WRONG_PASSWORD";
    public static final String STATUS_NO_FILE       = "ERR_NO_FILE";
    public static final String STATUS_SESSION_MISMATCH = "ERR_SESSION_MISMATCH";

    // ── Timeout (ms) waiting for each handshake step ─────────────────
    public static final int TIMEOUT_MS = 15_000;  // 15 seconds

    // ─────────────────────────────────────────────────────────────────
    //  DATA STRUCTURES
    // ─────────────────────────────────────────────────────────────────

    /** Represents a single handshake message on the wire */
    public static class Message implements Serializable {
        public String type;       // SYN / SYN-ACK / ACK / NACK
        public String sessionId;  // unique ID for this transfer session
        public String status;     // OK or ERR_xxx
        public String password;   // sent only in SYN
        public String fileName;   // sent in SYN-ACK
        public long   fileSize;   // sent in SYN-ACK
        public String flag;       // READY  (sent in ACK by P1)
        public String senderIP;   // informational
        public int    senderPort; // informational

        public Message(String type) { this.type = type; }

        @Override //ZAMAN74
        public String toString() {
            return "[" + type + "]"
                    + " session=" + sessionId
                    + " status="  + status
                    + " file="    + fileName
                    + " size="    + fileSize
                    + " flag="    + flag;
        }
    }

    /** Result returned to both sides after handshake completes */
    public static class Result {
        public final boolean success;
        public final String  reason;     // human-readable if failed
        public final String  sessionId;
        public final String  fileName;
        public final long    fileSize;

        public Result(boolean success, String reason,
                      String sessionId, String fileName, long fileSize) {
            this.success   = success;
            this.reason    = reason;
            this.sessionId = sessionId;
            this.fileName  = fileName;
            this.fileSize  = fileSize;
        } //ZAMAN74

        public static Result fail(String reason) {
            return new Result(false, reason, null, null, 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  WIRE HELPERS  (send / receive a Message over a socket)
    // ─────────────────────────────────────────────────────────────────

    /** Sends a Message over the given DataOutputStream */
    public static void send(DataOutputStream dos, Message msg) throws IOException {
        dos.writeUTF(msg.type);
        dos.writeUTF(nullSafe(msg.sessionId));
        dos.writeUTF(nullSafe(msg.status));
        dos.writeUTF(nullSafe(msg.password));
        dos.writeUTF(nullSafe(msg.fileName));
        dos.writeLong(msg.fileSize);
        dos.writeUTF(nullSafe(msg.flag));
        dos.writeUTF(nullSafe(msg.senderIP));
        dos.writeInt(msg.senderPort);
        dos.flush();
    }

    /** Reads a Message from the given DataInputStream */
    public static Message receive(DataInputStream dis) throws IOException {
        Message msg = new Message(dis.readUTF());
        msg.sessionId  = dis.readUTF();
        msg.status     = dis.readUTF();
        msg.password   = dis.readUTF();
        msg.fileName   = dis.readUTF();
        msg.fileSize   = dis.readLong();
        msg.flag       = dis.readUTF();
        msg.senderIP   = dis.readUTF();
        msg.senderPort = dis.readInt();
        return msg;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    // ─────────────────────────────────────────────────────────────────
    //  SESSION ID GENERATOR
    // ─────────────────────────────────────────────────────────────────
    public static String generateSessionId() {
        long ts   = System.currentTimeMillis();
        int  rand = (int)(Math.random() * 99999);
        return "SID-" + Long.toHexString(ts).toUpperCase()
                + "-" + String.format("%05d", rand);
    }

    // ─────────────────────────────────────────────────────────────────
    //  P2  SIDE  –  performSenderHandshake()
    //  Called by FileSender AFTER a client socket is accepted.
    //  Handles steps [receive SYN] → [send SYN-ACK] → [receive ACK]
    // ─────────────────────────────────────────────────────────────────
    public static Result performSenderHandshake(
            Socket       clientSocket,
            String       correctPassword,
            String       fileName,
            long         fileSize,
            HandshakeLog log) throws IOException {

        clientSocket.setSoTimeout(TIMEOUT_MS);

        DataInputStream  dis = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

        // ── STEP 1 : Receive SYN from P1 ────────────────────────────
        log.info("Waiting for SYN from receiver...");
        Message syn = receive(dis);
        if (!SYN.equals(syn.type)) {
            sendNack(dos, "Expected SYN, got " + syn.type);
            return Result.fail("Protocol error: expected SYN");
        }
        log.info("← SYN received  [session=" + syn.sessionId + "]");

        // ── STEP 1b: Verify password ─────────────────────────────────
        if (!correctPassword.equals(syn.password)) {
            log.warn("Wrong password in SYN!");
            Message nack = new Message(NACK);
            nack.sessionId = syn.sessionId;
            nack.status    = STATUS_WRONG_PASS;
            send(dos, nack);
            return Result.fail("Wrong password");
        }
        log.info("Password verified ✓");

        // ── STEP 2 : Send SYN-ACK to P1 ─────────────────────────────
        Message synAck = new Message(SYN_ACK);
        synAck.sessionId  = syn.sessionId;   // echo back same session ID
        synAck.status     = STATUS_OK;
        synAck.fileName   = fileName;
        synAck.fileSize   = fileSize;
        send(dos, synAck);
        log.info("→ SYN-ACK sent   [file=" + fileName
                + ", size=" + fileSize + " bytes]");

        // ── STEP 3 : Receive ACK from P1 ────────────────────────────
        Message ack = receive(dis);
        if (!ACK.equals(ack.type)) {
            return Result.fail("Expected ACK, got " + ack.type);
        }
        if (!syn.sessionId.equals(ack.sessionId)) {
            sendNack(dos, STATUS_SESSION_MISMATCH);
            return Result.fail("Session ID mismatch in ACK");
        }
        if (!READY.equals(ack.flag)) {
            return Result.fail("Expected READY flag in ACK");
        }
        log.info("← ACK received   [flag=READY] — HANDSHAKE COMPLETE ✓");

        // Remove per-message timeout now; file transfer sets its own
        clientSocket.setSoTimeout(0);

        return new Result(true, "OK", syn.sessionId, fileName, fileSize);
    }

    // ─────────────────────────────────────────────────────────────────
    //  P1  SIDE  –  performReceiverHandshake()
    //  Called by FileReceiver after connecting to P2.
    //  Handles steps [send SYN] → [receive SYN-ACK] → [send ACK]
    // ─────────────────────────────────────────────────────────────────
    public static Result performReceiverHandshake(
            Socket       serverSocket,
            String       password,
            HandshakeLog log) throws IOException {

        serverSocket.setSoTimeout(TIMEOUT_MS);

        DataInputStream  dis = new DataInputStream(serverSocket.getInputStream());
        DataOutputStream dos = new DataOutputStream(serverSocket.getOutputStream());

        String sessionId = generateSessionId();

        // ── STEP 1 : Send SYN to P2 ─────────────────────────────────
        Message syn = new Message(SYN);
        syn.sessionId = sessionId;
        syn.password  = password;
        send(dos, syn);
        log.info("→ SYN sent       [session=" + sessionId + "]");

        // ── STEP 2 : Receive SYN-ACK (or NACK) from P2 ──────────────
        log.info("Waiting for SYN-ACK from sender...");
        Message synAck = receive(dis);

        if (NACK.equals(synAck.type)) {
            String reason = synAck.status;
            log.warn("← NACK received: " + reason);
            if (STATUS_WRONG_PASS.equals(reason))
                return Result.fail("Wrong password – sender rejected connection");
            return Result.fail("Sender rejected: " + reason);
        }

        if (!SYN_ACK.equals(synAck.type)) {
            return Result.fail("Expected SYN-ACK, got " + synAck.type);
        }
        if (!STATUS_OK.equals(synAck.status)) {
            return Result.fail("SYN-ACK status not OK: " + synAck.status);
        }
        if (!sessionId.equals(synAck.sessionId)) {
            return Result.fail("Session ID mismatch in SYN-ACK");
        }
        log.info("← SYN-ACK received [file=" + synAck.fileName
                + ", size=" + synAck.fileSize + " bytes]");

        // ── STEP 3 : Send ACK to P2 ─────────────────────────────────
        Message ack = new Message(ACK);
        ack.sessionId = sessionId;
        ack.flag      = READY;
        send(dos, ack);
        log.info("→ ACK sent       [flag=READY] — HANDSHAKE COMPLETE ✓");

        serverSocket.setSoTimeout(0);

        return new Result(true, "OK",
                sessionId, synAck.fileName, synAck.fileSize);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internal helper
    // ─────────────────────────────────────────────────────────────────
    private static void sendNack(DataOutputStream dos, String reason)
            throws IOException {
        Message nack = new Message(NACK);
        nack.status = reason;
        send(dos, nack);
    }

    // ─────────────────────────────────────────────────────────────────
    //  LOGGING INTERFACE  (so both Swing apps can show log in UI)
    // ─────────────────────────────────────────────────────────────────
    public interface HandshakeLog {
        void info(String msg);
        void warn(String msg);
    }
}