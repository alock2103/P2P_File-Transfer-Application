# Deployment Documentation

## Azure Virtual Machine Deployment

### Overview
The P2P File Transfer Application has been successfully deployed to Microsoft Azure cloud infrastructure for remote access, testing, and demonstration purposes.

### VM Specifications
- **Cloud Provider**: Microsoft Azure
- **VM Size**: Standard B1s (1 vCPU, 1 GB RAM)
- **Operating System**: Ubuntu 24.04 LTS
- **IP Address**: 20.230.173.54 (Static)

### Access Information

**Remote Desktop Protocol (xRDP):**
- **Host**: `20.230.173.54`
- **Port**: `3389` (default RDP)
- **Username**: `student`
- **Password**: `Unified@1234`

### Deployment Features

1. **One-Click Execution**: `UnifiedFileTransfer.sh` file launches two instances automatically
2. **Self-Testing Capability**: Application can send files to itself for verification
3. **Pre-configured Environment**: Java JDK 11+ pre-installed
4. **Remote GUI Access**: Full desktop environment via xRDP
5. **Auto-sync with GitHub**: VM pulls latest code on startup

### Connecting to the VM

#### Windows:
1. Press `Windows key + R` to open Run dialog
2. Type `mstsc` and press Enter
3. In the "Computer" field, enter: `20.230.173.54`
4. Click "Connect"
5. Enter credentials when prompted:
   - Username: `student`
   - Password: `Unified@1234`
6. Click "OK" to connect

#### macOS:
1. Download "Microsoft Remote Desktop" from the Mac App Store
2. Open the application
3. Click "Add PC" (+ button in top bar)
4. In "PC name", enter: `20.230.173.54`
5. Set "User account" to "Add User Account"
6. Enter username: `student` and password: `Unified@1234`
7. Click "Add" to save
8. Double-click the connection in the list to launch
9. Accept any certificate warnings

#### Linux:
1. Install Remmina: `sudo apt install remmina`
2. Open Remmina
3. Click "New connection profile" (+ icon)
4. Set Protocol to "RDP - Remote Desktop Protocol"
5. In "Server", enter: `20.230.173.54`
6. In "Username", enter: `student`
7. In "Password", enter: `Unified@1234`
8. In "Resolution", choose appropriate setting (recommended: 1280x720)
9. Click "Save and Connect"

### Running the Application

#### Quick Start (Easiest Method):
1. Connect to VM using instructions above
2. Once logged in, locate `UnifiedFileTransfer.sh` on the desktop
3. **Double-click `UnifiedFileTransfer.sh`**
4. **Two instances launch automatically:**
   - Instance 1: Pre-configured for Sender mode
   - Instance 2: Pre-configured for Receiver mode
5. Application is ready to use immediately

#### Manual Launch:
1. Connect to VM via Remote Desktop
2. Open Terminal application
3. Navigate to application directory:
   ```bash
   cd ~/P2P_File-Transfer-Application
   ```
4. Run the application:
   ```bash
   java UnifiedFileTransfer
   ```

#### Via SSH (Alternative):
```bash
# Connect via SSH
ssh student@20.230.173.54

# Navigate to application
cd ~/P2P_File-Transfer-Application

# Compile if needed
javac *.java

# Run application
java UnifiedFileTransfer
```

### Self-Testing Mode

The VM deployment includes a unique testing feature that demonstrates full application functionality on a single machine.

#### UnifiedFileTransfer.sh Script:
- **Location**: Desktop or `/home/student/`
- **Function**: Launches two application instances simultaneously
- **Purpose**: Enables file transfer testing without a second machine

#### How Self-Testing Works:
1. Double-click `UnifiedFileTransfer.sh` on the VM desktop
2. **Two instances launch automatically:**
   - Instance 1: Configured for Sender mode
   - Instance 2: Configured for Receiver mode
3. **Application sends files to itself** using localhost loopback
4. Validates complete functionality:
   - Network interface detection
   - 3-way handshake protocol
   - Password authentication
   - File transfer and I/O operations
   - Progress tracking and UI updates

#### Technical Details:
- Sender binds to localhost (127.0.0.1) on port 49200
- Receiver connects to 127.0.0.1:49200
- Files transfer between instances on same VM
- Proves end-to-end functionality without requiring two separate machines

#### Why This Matters:
- **Demonstrates full workflow** in single environment
- **Validates all components** working together
- **Simplifies testing** for reviewers and evaluators
- **Proves portability** - works on single machine or across network

### Testing Instructions

#### Sender Mode Testing:
1. Launch application (via script or manual method)
2. Click "📤 SENDER MODE"
3. Add test files via drag-drop or "Add Files" button
4. Set password (e.g., `test123`)
5. Click "Start Server & Generate Info"
6. Note displayed IP (`127.0.0.1` for localhost) and port

#### Receiver Mode Testing:
1. In second instance (or launch another manually)
2. Click "📥 RECEIVER MODE"
3. Enter connection details:
   - Sender IP: `127.0.0.1` (or `20.230.173.54` from external machine)
   - Port: (displayed in sender, e.g., `49200`)
   - Password: Same as sender (e.g., `test123`)
4. Click "Connect & Start Handshake"
5. Select save directory when prompted
6. Verify files transfer successfully

#### Multi-Machine Testing:
1. Connect to VM from one machine (acts as sender)
2. Connect from your local machine (acts as receiver)
3. Use VM's public IP: `20.230.173.54`
4. Follow standard sender/receiver workflow
5. Verify file transfer across network

### Network Configuration

**Firewall Rules:**
- Port 3389 (RDP): Open for remote desktop access
- Ports 49200-49220: Open for file transfer application
- Port 22 (SSH): Open for alternative terminal access

**Security Group Settings:**
- Inbound rules configured for application ports
- Outbound traffic: Unrestricted for file transfers

**Network Interfaces:**
- Primary interface: eth0 (public IP: 20.230.173.54)
- Loopback interface: lo (127.0.0.1 for self-testing)

### System Requirements Met

✅ **Java Runtime Environment**: OpenJDK 11+ installed  
✅ **Network Connectivity**: Full LAN simulation capability  
✅ **GUI Support**: X11 forwarding via xRDP  
✅ **Persistent Storage**: Application files persist across reboots  
✅ **Auto-Update**: Startup script syncs with GitHub repository  
✅ **Desktop Environment**: XFCE4 lightweight desktop  

### Deployment Verification

The deployment has been verified through:
- ✅ Successful compilation of all Java source files
- ✅ Application launches without errors
- ✅ GUI renders correctly via RDP
- ✅ Network interface detection works properly
- ✅ File transfer functionality tested end-to-end
- ✅ Multi-file queue management operational
- ✅ Handshake protocol authentication verified
- ✅ Self-testing mode functional (localhost transfer)
- ✅ Remote access tested from Windows, macOS, and Linux clients

### Performance Characteristics

**On VM:**
- Transfer speed: 80-100 MB/s (localhost loopback)
- UI responsiveness: Moderate (1 vCPU, 1GB RAM limitation)
- Startup time: ~3-5 seconds
- RDP latency: Depends on internet connection speed

**Note on VM Performance:**
The VM is configured with minimal resources (1 vCPU, 1 GB RAM) which may result in slower GUI responsiveness compared to local execution. This is normal for cloud VMs and does not affect application functionality.

### Troubleshooting

#### Cannot Connect via RDP
**Symptoms**: Connection timeout, "Remote Desktop can't connect"
- ✅ Verify VM is running (check Azure portal)
- ✅ Confirm your firewall allows outbound port 3389
- ✅ Try from different network (corporate firewalls may block RDP)
- ✅ Alternative: Use SSH access instead

#### Application Won't Start
**Symptoms**: Error messages, application doesn't launch
- ✅ Check Java version: `java -version` (should be 11+)
- ✅ Verify all .class files present: `ls *.class`
- ✅ Recompile if needed: `javac *.java`
- ✅ Check file permissions: `chmod +x UnifiedFileTransfer.sh`

#### Network Detection Issues
**Symptoms**: Wrong IP detected, multiple IPs shown
- ✅ VM has single network interface (eth0)
- ✅ IP will be auto-detected as 20.230.173.54
- ✅ For self-testing, manually enter 127.0.0.1
- ✅ Verify NetworkUtils.java is filtering correctly

#### File Transfer Fails
**Symptoms**: Handshake fails, connection timeout
- ✅ Verify password matches exactly (case-sensitive)
- ✅ Check port is in range 49200-49220
- ✅ Ensure sender started before receiver connects
- ✅ Confirm firewall rules allow application ports

#### GUI Appears Slow or Laggy
**Symptoms**: Delayed mouse/keyboard input
- ✅ Normal for 1 vCPU VM - not a bug
- ✅ Reduce RDP color depth in client settings
- ✅ Lower screen resolution (try 1024x768)
- ✅ Close unnecessary applications on VM

### Maintenance

**Updating Application Code:**
```bash
cd ~/P2P_File-Transfer-Application
git pull origin main
javac *.java
```

**Restarting VM:**
- Use Azure portal to restart if needed
- Startup script automatically syncs code on boot
- All files persist across restarts

**Refreshing Application:**
- Exit running instances
- Pull latest code from GitHub
- Recompile: `javac *.java`
- Restart via `UnifiedFileTransfer.sh`

### Security Considerations

- ✅ VM credentials are temporary for demonstration purposes
- ✅ Passwords will be rotated after project evaluation period
- ✅ VM will be deallocated after course completion
- ✅ Firewall restricts access to necessary ports only
- ✅ No sensitive data stored on VM
- ✅ Regular security updates applied via startup script

**Important**: Do not store personal or sensitive files on the VM. It is a shared demonstration environment.

### Contact for Access Issues

If experiencing connectivity problems during demonstration:
- Verify VM status with team member who deployed
- Check Azure portal for VM running state
- Confirm network security group rules are active
- Test from alternative network/device
- **Fallback**: Local demonstration available on team laptops

### Known Limitations

1. **VM Performance**: Single vCPU may cause UI lag (cosmetic only)
2. **Concurrent Users**: Only one RDP session at a time
3. **Network Speed**: Limited by Azure bandwidth allocation
4. **Storage**: 30 GB disk space (sufficient for demonstration)
5. **Region**: May have higher latency for non-North American users

### Additional Resources

**GitHub Repository**: https://github.com/alock2103/P2P_File-Transfer-Application  
**Documentation**: See README.md for detailed usage guide  
**Source Code**: Fully commented .java files in repository  

---

**Deployment Date**: March 28, 2026  
**Deployed By**: Project Team - COMP.2800 Winter 2026  
**Last Updated**: March 28, 2026  
**Next Review**: After project evaluation (March 29, 2026)
