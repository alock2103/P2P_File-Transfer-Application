import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Network Utilities for P2P File Transfer Application
 *
 * This utility class provides intelligent network interface detection and IP address
 * selection for establishing peer-to-peer connections over Local Area Networks (LAN).
 * It addresses the common problem of multiple network interfaces on modern computers
 * (physical adapters, virtual adapters, VPNs, etc.) by filtering out virtual interfaces
 * and selecting the most appropriate IP address for LAN file transfer.
 *
 * Key Features:
 * - Automatic detection of real (physical) network interfaces
 * - Filtering of virtual adapters (WSL, Hyper-V, VirtualBox, VMware)
 * - Intelligent IP address prioritization (192.168.x.x preferred for home networks)
 * - User selection dialog when multiple valid interfaces exist
 * - Automatic port discovery within specified range
 * - Debug output for troubleshooting connection issues
 *
 * Virtual Adapter Detection:
 * Modern Windows systems often have multiple virtual network adapters that should not
 * be used for LAN file transfer. Common examples include WSL (Windows Subsystem for Linux)
 * typically using 172.16-31.x.x range, Hyper-V virtual switches, VirtualBox host-only adapters,
 * VMware virtual adapters, Bluetooth PAN adapters, and VPN tunnel interfaces.
 *
 * IP Address Prioritization:
 * 1. 192.168.x.x - Standard home/office router range (highest priority)
 * 2. 10.x.x.x - Corporate network range
 * 3. Other valid private IPs
 *
 * @author Shatab Shameer Zaman (Backend Development)
 * @author Nicholas Cordeiro (Backend Development)
 * @author Samantha Whan (Frontend/UI Development)
 * @author Nand Thaker (Testing and Documentation)
 * @version 1.0
 * @since 2026-02-14
 */
public class NetworkUtils {

    /**
     * Keywords used to identify virtual network adapters.
     *
     * These strings are checked against both the interface display name and
     * system name (lowercase comparison). Any interface matching these patterns
     * is considered virtual and excluded from real IP detection.
     *
     * Common virtual adapter patterns:
     * - "wsl" - Windows Subsystem for Linux
     * - "hyper-v", "hyperv" - Microsoft Hyper-V virtualization
     * - "virtualbox" - Oracle VirtualBox virtual adapters
     * - "vmware" - VMware Workstation/Player virtual adapters
     * - "vethernet" - Hyper-V virtual Ethernet
     * - "loopback" - Loopback adapter (127.0.0.1)
     * - "teredo", "isatap", "6to4" - IPv6 transition mechanisms
     * - "tunnel", "pseudo" - Generic tunnel/virtual interfaces
     * - "bluetooth" - Bluetooth Personal Area Network
     * - "vpn" - VPN tunnel interfaces
     */
    private static final String[] VIRTUAL_KEYWORDS = {
            "wsl", "hyper-v", "hyperv", "virtualbox", "vmware",
            "vethernet", "loopback", "teredo", "isatap", "6to4",
            "tunnel", "pseudo", "bluetooth", "vpn"
    };

    /**
     * Retrieves all real (non-virtual) IPv4 addresses from active network interfaces.
     *
     * This method enumerates all network interfaces on the system and applies
     * multiple filters to identify genuine physical network adapters suitable for
     * LAN file transfer.
     *
     * Filtering Process:
     * 1. Skips interfaces that are down (not active)
     * 2. Skips loopback interfaces (127.0.0.1)
     * 3. Checks interface name against VIRTUAL_KEYWORDS blacklist
     * 4. Filters out likely virtual IPs (172.16-31.x.x range)
     * 5. Includes only IPv4 addresses (ignores IPv6)
     *
     * Use Cases:
     * - Sender application: Display all available IPs for user selection
     * - Network diagnostics: Verify correct adapter detection
     * - Multi-homed systems: Identify all valid LAN interfaces
     *
     * @return List of IPv4 address strings (e.g., ["192.168.1.100", "10.0.0.50"])
     *         Returns empty list if no suitable interfaces found
     */
    public static List<String> getRealIPs() {
        List<String> realIPs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Skip inactive and loopback interfaces
                if (!iface.isUp() || iface.isLoopback()) continue;

                // Get interface names in lowercase for case-insensitive matching
                String name = iface.getDisplayName().toLowerCase();
                String id   = iface.getName().toLowerCase();

                // Check if interface name contains any virtual adapter keywords
                boolean isVirtual = false;
                for (String kw : VIRTUAL_KEYWORDS) {
                    if (name.contains(kw) || id.contains(kw)) {
                        isVirtual = true;
                        break;
                    }
                }
                if (isVirtual) continue;

                // Extract IPv4 addresses from this interface
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Additional IP-based filtering for virtual adapters
                        if (!isLikelyVirtualIP(ip)) {
                            realIPs.add(ip);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return realIPs;
    }

    /**
     * Determines if an IP address is likely from a virtual adapter based on address range.
     *
     * This method provides an additional layer of virtual adapter detection by
     * analyzing the IP address itself, complementing the interface name filtering.
     *
     * Detection Logic:
     * The 172.16.0.0/12 range (172.16.0.0 - 172.31.255.255) is designated by RFC 1918
     * for private networks. However, on Windows systems, this specific range is almost
     * exclusively used by WSL (Windows Subsystem for Linux) and Hyper-V virtual switches,
     * not by physical network adapters or home routers.
     *
     * Rationale:
     * - Home routers typically use 192.168.x.x
     * - Corporate networks typically use 10.x.x.x
     * - 172.16-31.x.x is rare for physical networks
     * - WSL and Hyper-V default to this range
     *
     * @param ip IPv4 address string (e.g., "172.20.10.5")
     * @return true if IP is in the 172.16-31.x.x range (likely virtual),
     *         false otherwise (likely physical adapter)
     */
    private static boolean isLikelyVirtualIP(String ip) {
        if (ip.startsWith("172.")) {
            try {
                // Extract second octet to check range
                int second = Integer.parseInt(ip.split("\\.")[1]);
                // RFC 1918 range 172.16-31.x.x is used by WSL/Hyper-V
                return second >= 16 && second <= 31;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }

    /**
     * Selects the single best IP address for LAN file transfer operations.
     *
     * This method implements intelligent prioritization to automatically select
     * the most appropriate IP address when multiple valid interfaces exist, eliminating
     * the need for user intervention in most common scenarios.
     *
     * Selection Priority:
     * 1. 192.168.x.x (Highest priority)
     *    - Standard home router range
     *    - Most common in residential/small office networks
     *    - Default for most consumer networking equipment
     *
     * 2. 10.x.x.x (Medium priority)
     *    - Common in corporate/enterprise networks
     *    - Larger address space for bigger organizations
     *
     * 3. First available IP (Lowest priority)
     *    - Fallback for unusual network configurations
     *    - Other valid private IP ranges
     *
     * 4. 127.0.0.1 (Fallback when no interfaces found)
     *    - Loopback address for testing on same machine
     *    - Indicates no active network connections
     *
     * Use Cases:
     * - Automatic sender IP detection on application startup
     * - Default IP for single-interface systems
     * - Fallback when user cancels IP selection dialog
     *
     * @return Single best IPv4 address string for LAN operations,
     *         or "127.0.0.1" if no suitable interfaces found
     */
    public static String getBestIP() {
        List<String> ips = getRealIPs();
        if (ips.isEmpty()) return "127.0.0.1";
        // Prioritize common home network range
        for (String ip : ips) if (ip.startsWith("192.168.")) return ip;
        // Corporate network range as second choice
        for (String ip : ips) if (ip.startsWith("10."))      return ip;
        // Any other valid IP as last resort
        return ips.get(0);
    }

    /**
     * Displays a dialog for user to select network interface when multiple options exist.
     *
     * This method is called when the system has multiple valid network interfaces
     * (e.g., Ethernet + WiFi, or multiple network cards). It presents a user-friendly
     * dialog allowing manual selection of the appropriate interface for file transfer.
     *
     * Dialog Behavior:
     * - Shows all detected real IPs as selectable options
     * - Pre-selects the first IP (typically the best match)
     * - Provides clear instructions about selecting the same network as receiver
     * - Returns first IP if user cancels (safe default)
     * - Automatically returns single IP without showing dialog
     *
     * When This Is Called:
     * - Desktop with both Ethernet and WiFi active
     * - Server with multiple network cards
     * - Laptop connected to multiple networks simultaneously
     * - System with VPN and LAN connections active
     *
     * User Guidance:
     * The dialog instructs users to "Select the one on the SAME network as the receiver"
     * because file transfer only works when both sender and receiver are on the same subnet.
     * For example, both on same WiFi network should choose WiFi adapter IP, both on same
     * wired network should choose Ethernet adapter IP.
     *
     * @param parent Parent JFrame for dialog positioning (can be null)
     * @return Selected IPv4 address string, first IP if cancelled,
     *         or "127.0.0.1" if no interfaces found
     */
    public static String letUserPickIP(JFrame parent) {
        List<String> ips = getRealIPs();
        if (ips.isEmpty()) return "127.0.0.1";
        if (ips.size() == 1) return ips.get(0);

        String[] opts   = ips.toArray(new String[0]);
        String   chosen = (String) JOptionPane.showInputDialog(
                parent,
                "Multiple network adapters found.\n"
                        + "Select the one on the SAME network as the receiver:",
                "Select Network Interface",
                JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);

        return chosen != null ? chosen : ips.get(0);
    }

    /**
     * Prints comprehensive network interface information to console for debugging.
     *
     * This diagnostic method outputs detailed information about every network
     * interface on the system, regardless of whether it's considered "real" or "virtual".
     * This is invaluable for troubleshooting connection issues and verifying correct
     * adapter detection.
     *
     * Information Displayed Per Interface:
     * - Display Name: User-friendly interface name (e.g., "Wi-Fi", "Ethernet")
     * - System Name: Internal OS identifier (e.g., "eth0", "wlan0")
     * - Up Status: Whether interface is currently active
     * - Loopback Flag: Whether it's the loopback adapter (127.0.0.1)
     * - Virtual Flag: OS-reported virtual status (may miss some virtual adapters)
     * - IP Addresses: All IPv4 and IPv6 addresses bound to this interface
     *
     * Usage Recommendations:
     * - Call on application startup during development/testing
     * - Include output in bug reports for connection issues
     * - Verify virtual adapter detection is working correctly
     * - Confirm expected IP addresses are present
     *
     * Performance Note:
     * This method is called once during UnifiedFileTransfer initialization.
     * The enumeration overhead is minimal (10-50ms) and provides valuable debugging
     * information in console logs.
     */
    public static void printAllInterfaces() {
        System.out.println("\n=== ALL NETWORK INTERFACES ===");
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                System.out.println("Interface : " + iface.getDisplayName());
                System.out.println("  Name    : " + iface.getName());
                System.out.println("  Up      : " + iface.isUp());
                System.out.println("  Loopback: " + iface.isLoopback());
                System.out.println("  Virtual : " + iface.isVirtual());

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    System.out.println("  Address : " + a.getHostAddress()
                            + (a instanceof Inet4Address ? " [IPv4]" : " [IPv6]"));
                }
                System.out.println();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        System.out.println("==============================\n");
    }

    /**
     * Checks if a specific TCP port is available for binding on the local machine.
     *
     * This method attempts to create a ServerSocket on the specified port to verify
     * availability. If the socket creation succeeds, the port is free; if it throws
     * an exception, the port is already in use by another application.
     *
     * Implementation Details:
     * - Uses try-with-resources for automatic socket cleanup
     * - Sets SO_REUSEADDR to allow quick rebinding after socket close
     * - Does not actually listen for connections (immediate close)
     * - Thread-safe - can be called concurrently
     *
     * Use Cases:
     * - Verify specific port before attempting to start server
     * - Find next available port in a sequence (via findFreePort)
     * - Validate user-provided port numbers
     *
     * Common Port Conflicts:
     * - Web servers: 80, 443, 8080
     * - Databases: 3306 (MySQL), 5432 (PostgreSQL)
     * - System services: Ports below 1024 (require admin/root)
     *
     * @param port Port number to check (valid range: 1-65535)
     * @return true if port is available for binding, false if already in use
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Finds the first available TCP port within a specified range.
     *
     * This method sequentially tests ports starting from the given startPort,
     * checking up to 20 ports to find one that is available for binding. This is
     * essential for the file transfer application to automatically find a usable
     * port when the default port is already in use.
     *
     * Search Range:
     * Tests ports [startPort, startPort + 19] inclusive (20 ports total)
     * Example: findFreePort(49200) tests 49200-49219
     *
     * Port Range Selection (49200-49220):
     * - Falls within IANA dynamic/private port range (49152-65535)
     * - Unlikely to conflict with well-known services
     * - Does not require administrator/root privileges
     * - Safe for P2P applications
     *
     * Algorithm:
     * 1. Start with desired port (e.g., 49200)
     * 2. Check if port is available using isPortAvailable()
     * 3. If available: return immediately
     * 4. If not available: try next port (49201, 49202, ...)
     * 5. Continue until free port found or range exhausted
     *
     * Error Handling:
     * If all 20 ports in the range are occupied (extremely rare), throws
     * RuntimeException. This typically indicates system has many active network
     * services, port range should be adjusted to different numbers, or firewall
     * or security software is blocking port access.
     *
     * Thread Safety:
     * Safe to call concurrently from multiple threads. Each call performs
     * independent port checks with no shared state.
     *
     * @param startPort First port to check (recommended: 49200)
     * @return First available port in range [startPort, startPort+19]
     * @throws RuntimeException if no free port found in 20-port range
     */
    public static int findFreePort(int startPort) {
        for (int p = startPort; p < startPort + 20; p++) {
            if (isPortAvailable(p)) return p;
        }
        throw new RuntimeException(
                "No free port found between " + startPort
                        + " and " + (startPort + 20));
    }

    /**
     * Standalone test method for debugging network detection.
     *
     * This main method allows NetworkUtils to be run as a standalone application
     * for testing and verification of network interface detection without launching
     * the full file transfer application.
     *
     * Usage:
     * Run from command line: java NetworkUtils
     *
     * Verification Checklist:
     * - Verify your real network adapter appears in output
     * - Confirm virtual adapters (WSL, Hyper-V) are NOT in Real IPs list
     * - Check that Best IP matches your expected LAN address
     * - Ensure loopback (127.0.0.1) is not in Real IPs list
     *
     * @param args Command line arguments (unused)
     */
    public static void main(String[] args) {
        printAllInterfaces();
        System.out.println("Best IP  : " + getBestIP());
        System.out.println("Real IPs : " + getRealIPs());
    }
}
