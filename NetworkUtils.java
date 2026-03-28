import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * NetworkUtils - Correctly identifies real network interfaces
 * Filters out virtual adapters (WSL, Hyper-V, VirtualBox, VMware)
 */
public class NetworkUtils {

    // Virtual adapter keywords to SKIP
    private static final String[] VIRTUAL_KEYWORDS = {
            "wsl", "hyper-v", "hyperv", "virtualbox", "vmware",
            "vethernet", "loopback", "teredo", "isatap", "6to4",
            "tunnel", "pseudo", "bluetooth", "vpn"
    };

    /** Get ALL real IPv4 addresses, no virtual adapters */
    public static List<String> getRealIPs() {
        List<String> realIPs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;

                String name = iface.getDisplayName().toLowerCase();
                String id   = iface.getName().toLowerCase();
                boolean isVirtual = false;

                for (String kw : VIRTUAL_KEYWORDS) {
                    if (name.contains(kw) || id.contains(kw)) {
                        isVirtual = true;
                        break;
                    }
                }
                if (isVirtual) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (!isLikelyVirtualIP(ip)) {
                            realIPs.add(ip);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace(); //ZAMAN74
        }
        return realIPs;
    }

    /** 172.16-31.x.x is almost always WSL/Hyper-V on Windows */
    private static boolean isLikelyVirtualIP(String ip) {
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) { }
        }
        return false;
    }

    /** Get the single best IP: prefers 192.168.x.x then 10.x.x.x */
    public static String getBestIP() {
        List<String> ips = getRealIPs();
        if (ips.isEmpty()) return "127.0.0.1";
        for (String ip : ips) if (ip.startsWith("192.168.")) return ip;
        for (String ip : ips) if (ip.startsWith("10."))      return ip;
        return ips.get(0);
    }

    /** Let user pick which IP if multiple found */
    public static String letUserPickIP(JFrame parent) {
        List<String> ips = getRealIPs();
        if (ips.isEmpty()) return "127.0.0.1";
        if (ips.size() == 1) return ips.get(0);
        String[] opts = ips.toArray(new String[0]);
        String chosen = (String) JOptionPane.showInputDialog(
                parent,
                "Multiple network adapters found.\nSelect the one on the same network as receiver:",
                "Select Network Interface",//ZAMAN74
                JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0]);
        return chosen != null ? chosen : ips.get(0);
    }

    /** Print all interfaces to console - use this for debugging */
    public static void printAllInterfaces() {
        System.out.println("\n=== ALL NETWORK INTERFACES ===");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
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
        } catch (SocketException e) { e.printStackTrace(); }
        System.out.println("==============================\n");
    }

    /** Check if a port is free */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;//ZAMAN74
        } catch (Exception e) { return false; }
    }

    /** Find first free port in range startPort to startPort+20 */
    public static int findFreePort(int startPort) {
        for (int p = startPort; p < startPort + 20; p++) {
            if (isPortAvailable(p)) return p;//ZAMAN74
        }
        throw new RuntimeException("No free port found near " + startPort);
    }

    public static void main(String[] args) {
        printAllInterfaces();//ZAMAN74
        System.out.println("Best IP : " + getBestIP());
        System.out.println("Real IPs: " + getRealIPs());
    }
}