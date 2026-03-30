# P2P LAN File Transfer Application

A high-performance peer-to-peer file transfer application for Local Area Networks, built with Java Swing. Features secure authentication, multi-file queuing, and intelligent network interface detection.

![Java](https://img.shields.io/badge/Java-11+-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)

---

## 🚀 Features

### Core Functionality
- **Multi-file Transfer Queue** - Send multiple files sequentially with one connection
- **Drag & Drop Support** - Intuitive file selection interface
- **Sortable File Table** - Sort by name, size, type, or modification date
- **Real-time Progress Tracking** - Live speed and percentage updates
- **Pause/Resume Transfers** - Control transfer flow on-the-fly
- **Automatic Duplicate Handling** - Files renamed automatically if they exist

### Network & Security
- **3-Way Handshake Protocol** - Custom SYN → SYN-ACK → ACK authentication
- **Password Protection** - Secure password-based authentication
- **Session ID Validation** - Prevents replay attacks
- **Smart IP Detection** - Filters virtual adapters (WSL, Hyper-V, VirtualBox)
- **Multi-Interface Support** - User selection when multiple NICs detected
- **Automatic Port Discovery** - Finds free ports in 49200-49220 range

### Performance
- **Transfer Speed**: 12-16 MB/s on typical WiFi networks
- **Optimized Buffering**: 8KB buffer size for optimal throughput
- **TCP Optimizations**: setTcpNoDelay, 1MB socket buffers
- **File Size Limit**: Up to 16 GB per file

---

## 📋 Table of Contents

- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Usage Guide](#-usage-guide)
- [Technical Specifications](#-technical-specifications)
- [Architecture](#-architecture)
- [Troubleshooting](#-troubleshooting)
- [Team](#-team)
- [License](#-license)

---

## 🔧 Installation

### Prerequisites
- **Java Development Kit (JDK) 11 or higher**
- **Operating System**: Windows 10/11, macOS 10.14+, or Linux (Ubuntu 20.04+)
- **Network**: Both machines must be on the same LAN

### Verify Java Installation
```bash
java -version
```
Expected output: `java version "11.0.x"` or higher

### Download & Compile

1. **Clone the repository**
```bash
git clone https://github.com/alock2103/P2P_File-Transfer-Application.git
cd P2P_File-Transfer-Application
```

2. **Compile the source files**
```bash
javac UnifiedFileTransfer.java NetworkUtils.java Handshake.java
```

3. **Verify compilation**
```bash
ls *.class
```
You should see: `UnifiedFileTransfer.class`, `NetworkUtils.class`, `Handshake.class`

---

## ⚡ Quick Start

### Step 1: Start the Sender

On the machine with files to send:

```bash
java UnifiedFileTransfer
```

1. Click **"📤 SENDER MODE"** (default)
2. Add files via drag-drop or **"Add Files"** button
3. Set a password (e.g., `secure123`)
4. Click **"Start Server & Generate Info"**
5. **Share the displayed connection info** (IP, Port, Password) with the receiver

### Step 2: Start the Receiver

On the machine receiving files:

```bash
java UnifiedFileTransfer
```

1. Click **"📥 RECEIVER MODE"**
2. Enter connection details from sender:
   - **Sender IP**: (e.g., `192.168.1.100`)
   - **Port**: (e.g., `49200`)
   - **Password**: (e.g., `secure123`)
3. Click **"Connect & Start Handshake"**
4. Select save directory when prompted
5. Files download automatically!

---

## 📖 Usage Guide

### Sender Mode Workflow

#### 1. Select Files
- **Drag & Drop**: Drag files directly into the drop zone
- **Add Files Button**: Browse and select multiple files
- **Remove**: Select rows and click "Remove Selected"
- **Sort**: Click column headers to sort by name, size, type, or date

#### 2. Configure Transfer
- **Password**: Set a secure password (case-sensitive)
- **Network Interface**: If multiple networks detected, select the correct one
  - Choose the interface on the **same network** as the receiver
  - Example: Both on WiFi → select WiFi adapter

#### 3. Start Server
- Click **"Start Server & Generate Info"**
- Connection info appears automatically
- **Copy and send this info** to the receiver via email, chat, etc.

#### 4. Confirm Transfer
- Wait for receiver to connect (status shows "Receiver connected")
- Click **"Confirm & Allow Transfer"**
- Files transfer sequentially with real-time progress

#### 5. Monitor Progress
- **Current File**: Shows which file is transferring
- **Progress Bar**: Percentage and MB transferred
- **Speed**: Real-time transfer speed in MB/s
- **Handshake Log**: Shows 3-way handshake for each file

### Receiver Mode Workflow

#### 1. Enter Connection Details
Get these values from the sender:
- **Sender IP**: IPv4 address (e.g., `192.168.1.100`)
- **Port**: Port number (e.g., `49200`)
- **Password**: Exact password (case-sensitive)

#### 2. Connect 
- Click **"Connect & Start Handshake"**
- Select folder to save all files
- Handshake log shows authentication progress

#### 3. Receive Files
- Files download automatically after handshake
- Progress bar shows percentage and speed
- Transfer log displays save locations
- Completion dialog shows total time and file count

### Advanced Features

#### Pause/Resume Transfer
- Click **"Pause / Resume"** button during transfer
- Useful for prioritizing network bandwidth temporarily

#### Last Directory Memory
- Application remembers last used folders
- Speeds up repeated file selections

#### Automatic Filename Conflict Resolution
- If file exists: automatically appends " (1)", " (2)", etc.
- Example: `report.pdf` → `report (1).pdf` → `report (2).pdf`

---

## 🔬 Technical Specifications

### Network Protocol

#### 3-Way Handshake (per file)
```
Receiver                 Sender
   │                        │
   │──── SYN ──────────────>│  [1] Password + Session ID
   │                        │
   │<─── SYN-ACK ───────────│  [2] File metadata (name, size)
   │                        │
   │──── ACK ──────────────>│  [3] READY flag
   │                        │
   │<═══ FILE DATA ═════════│  [4] 8KB chunks
```

#### Port Configuration
- **Range**: 49200-49220 (IANA dynamic/private ports)
- **Auto-discovery**: Finds first available port
- **No admin privileges required**

### File Transfer

#### Buffer & Performance
- **Buffer Size**: 8KB (optimal for WiFi networks)
- **Expected Speed**: 12-16 MB/s on WiFi 4/5
- **Progress Updates**: Every 120ms to reduce UI overhead
- **TCP Options**: 
  - `setTcpNoDelay(true)` - Disable Nagle's algorithm
  - `1MB send/receive buffers` - Improve throughput

#### File Size Limits
- **Maximum**: 16 GB per file
- **Total Queue**: Limited by available disk space

### Network Interface Detection

#### Filtered Virtual Adapters
- WSL (Windows Subsystem for Linux)
- Hyper-V virtual switches
- VirtualBox host-only adapters
- VMware virtual NICs
- VPN tunnel interfaces
- Bluetooth PAN adapters

#### IP Prioritization
1. **192.168.x.x** - Home/office routers (highest priority)
2. **10.x.x.x** - Corporate networks
3. **Other private IPs** - Fallback
4. **127.0.0.1** - Loopback (no network detected)

---

## 🏗️ Architecture

### Component Overview

```
┌─────────────────────────────────────────────┐
│     UnifiedFileTransfer.java (Main)         │
│  ┌────────────┐         ┌────────────┐     │
│  │  Sender    │         │  Receiver  │     │
│  │  Panel     │◄────────┤   Panel    │     │
│  └────────────┘         └────────────┘     │
└─────────────────────────────────────────────┘
           │                      │
           ├──────────┬───────────┤
           ▼          ▼           ▼
    ┌──────────┐ ┌────────┐ ┌──────────┐
    │ Network  │ │Handshake│ │   File   │
    │  Utils   │ │Protocol │ │  I/O     │
    └──────────┘ └────────┘ └──────────┘
```

### Class Responsibilities

#### UnifiedFileTransfer.java
- Dual-mode GUI (sender/receiver)
- File queue management
- Progress tracking & UI updates
- Multi-threaded network I/O

#### NetworkUtils.java
- Real network interface detection
- Virtual adapter filtering
- IP selection & prioritization
- Port availability checking

#### Handshake.java
- 3-way handshake protocol
- Password authentication
- Session ID management
- Message serialization

### Design Patterns

- **MVC Pattern**: Separation of UI, logic, and data
- **Observer Pattern**: UI updates via SwingUtilities.invokeLater
- **Factory Pattern**: Message and Result object creation
- **Strategy Pattern**: Different handshake flows for sender/receiver

---

## 🐛 Troubleshooting

### Connection Issues

#### "Connection timeout"
**Cause**: Receiver can't reach sender's IP
- ✅ Verify both machines on same network
- ✅ Check firewall settings (allow Java on port 49200-49220)
- ✅ Ping sender IP: `ping <sender-ip>`
- ✅ Disable VPN if active

#### "Wrong password"
**Cause**: Password mismatch
- ✅ Passwords are case-sensitive
- ✅ Copy-paste password to avoid typos
- ✅ Check for extra spaces

#### "No free port found"
**Cause**: All ports 49200-49220 occupied
- ✅ Close other network applications
- ✅ Restart the application
- ✅ Check with: `netstat -an | grep 492`

### Network Detection Issues

#### Virtual adapter selected
**Symptom**: IP shows 172.16-31.x.x or "vEthernet"
- ✅ Run `printAllInterfaces()` to see all adapters
- ✅ Manually select correct IP when prompted
- ✅ Disable WSL/Hyper-V if not needed

#### Multiple IPs shown
**Cause**: Multiple active network interfaces
- ✅ Choose IP on **same subnet** as receiver
- ✅ WiFi + Ethernet: Choose the one both machines use
- ✅ Example: Both on WiFi → select WiFi adapter IP

### Transfer Issues 

#### Slow transfer speed
**Causes & Solutions**:
- 📡 **Weak WiFi signal**: Move closer to router
- 🔌 **Use Ethernet**: 10x faster than WiFi
- 💻 **CPU bottleneck**: Close heavy applications
- 🌐 **Network congestion**: Pause other downloads

#### Transfer stuck at X%
**Causes & Solutions**:
- ⏸️ Check if paused: Click "Pause / Resume"
- 🔌 Check network cable connection
- 📶 Verify WiFi signal strength
- 🔄 Restart transfer if frozen >30 seconds

### Compilation Errors

#### "error: cannot find symbol"
```bash
# Ensure all files in same directory
ls *.java
# Should show: UnifiedFileTransfer.java, NetworkUtils.java, Handshake.java

# Compile all together
javac *.java
```

#### "UnsupportedClassVersionError"
```bash
# Check Java version
java -version

# If < 11, update JDK
# Windows: Download from https://adoptium.net/
# macOS: brew install openjdk@11
# Linux: sudo apt install openjdk-11-jdk
```

---

## 📊 Performance Tips

### Maximize Transfer Speed

1. **Use Wired Connection**: Ethernet is 5-10x faster than WiFi
2. **Close Background Apps**: Free up CPU and network bandwidth
3. **WiFi Optimization**:
   - Use 5GHz band if available
   - Position sender/receiver close to router
   - Avoid microwave oven interference
4. **Disable Antivirus Scanning**: Temporarily for large transfers
5. **Transfer Fewer Large Files**: Better than many small files

### Expected Speeds by Network Type

| Network Type        | Expected Speed |
|---------------------|----------------|
| Gigabit Ethernet    | 80-120 MB/s    |
| WiFi 6 (5GHz)       | 40-80 MB/s     |
| WiFi 5 (5GHz)       | 20-50 MB/s     |
| WiFi 4 (2.4GHz)     | 10-20 MB/s     |
| Fast Ethernet       | 10-12 MB/s     |

---

## 👥 Team

**COMP.2800 - Software Development - Winter 2026**

| Name | Role | Contributions |
|------|------|---------------|
| **Shatab Shameer Zaman** | Backend Developer | Backend development, network protocol, handshake implementation |
| **Nicholas Cordeiro** | Backend Developer | File I/O, multi-threading, performance optimization |
| **Samantha Whan** | Frontend Developer | UI/UX design, Swing components, user experience |
| **Nand Thaker** | Testing & Documentation | Quality assurance, user guide, troubleshooting |

**Course Instructor**: Dr. Andreas S. Maniatis  
**Institution**: University of Windsor

---

## 📄 License

MIT License

Copyright (c) 2026 Shatab Shameer Zaman, Nicholas Cordeiro, Samantha Whan, Nand Thaker

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## 🔗 Links

- **GitHub Repository**: https://github.com/alock2103/P2P_File-Transfer-Application
- **Demo Video**: [Coming soon]
- **Documentation**: See Final_Report

---

## 🙏 Acknowledgments

- TCP/IP protocol design inspired by RFC 793
- Network interface detection techniques adapted from Java NetworkInterface API documentation
- UI design follows Java Swing best practices and accessibility guidelines
