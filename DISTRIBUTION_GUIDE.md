# CTTT Distribution Guide - For Non-Technical Users

## 🎯 Quick Start - Create Installer for Users

Run this command to build a complete installer package:

```powershell
powershell -ExecutionPolicy Bypass -File build-for-users.ps1
```

This creates **3 types of installers** for different user scenarios:

## 📦 Distribution Options

### 1. 💎 **Native Installer (BEST)** - `CTTT-7.2.0.exe`
- **Size**: ~150MB
- **User Requirements**: None!
- **What it includes**: Everything (Java runtime + CTTT)
- **User Experience**: Double-click → Install → Done!
- **Recommended for**: All non-technical users

### 2. 🚀 **EXE Installer** - `CTTT-Setup.exe`
- **Size**: ~82MB
- **User Requirements**: Java 17+ (installer checks and prompts)
- **What it includes**: CTTT application
- **User Experience**: Double-click → Java check → Install
- **Recommended for**: Users comfortable installing Java

### 3. 📦 **JAR Installer** - `Install-CTTT.bat` + `CTTT-Installer.jar`
- **Size**: ~82MB
- **User Requirements**: Java 17+ must be pre-installed
- **What it includes**: CTTT application
- **User Experience**: Double-click batch file → Install
- **Recommended for**: Technical users or Java developers

## 🛠️ How to Build

### Option A: Build All Types (Recommended)
```bash
# Creates all 3 installer types
powershell -ExecutionPolicy Bypass -File build-for-users.ps1
```

### Option B: Build Specific Type
```bash
# Just the native installer (best for users)
powershell -ExecutionPolicy Bypass -File build-for-users.ps1 -BuildType native

# Just the EXE wrapper
powershell -ExecutionPolicy Bypass -File build-for-users.ps1 -BuildType exe

# Just the JAR installer
powershell -ExecutionPolicy Bypass -File build-for-users.ps1 -BuildType jar
```

### Option C: Simple Batch Alternative
```bash
# Windows batch file version
create-exe-installer.bat
```

## 📋 Prerequisites for Building

### For JAR Installer:
- ✅ Java 17+
- ✅ Maven

### For EXE Installer:
- ✅ Java 17+
- ✅ Maven
- ✅ Launch4j (download from http://launch4j.sourceforge.net/)

### For Native Installer:
- ✅ JDK 17+ (includes jpackage tool)
- ✅ Maven

## 🎯 Distribution Strategy

### For Non-Technical Users:
1. **Build the native installer**: `build-for-users.ps1 -BuildType native`
2. **Send them**: `CTTT-7.2.0.exe` (single file)
3. **Instructions**: "Double-click to install"
4. **That's it!** No Java knowledge required.

### For Semi-Technical Users:
1. **Build the EXE installer**: `build-for-users.ps1 -BuildType exe`
2. **Send them**: `CTTT-Setup.exe`
3. **Instructions**: "Double-click to install. If Java is missing, it will guide you."

### For Developers:
1. **Build the JAR installer**: `build-for-users.ps1 -BuildType jar`
2. **Send them**: `Install-CTTT.bat` + `CTTT-Installer.jar`
3. **Instructions**: "Ensure Java 17+ is installed, then double-click Install-CTTT.bat"

## ✨ What Happens After Installation

No matter which installer type, CTTT automatically:

- ✅ **Installs to**: `C:\Program Files\CreativeTimeAndTaskTracker\`
- ✅ **Creates desktop shortcut**: Users can double-click to open
- ✅ **Configures Windows startup**: CTTT starts automatically with Windows
- ✅ **Sets up networking**: Configures ports and hosts file
- ✅ **Generates SSL certificates**: For secure HTTPS access
- ✅ **Creates system tray icon**: Runs in background
- ✅ **Accessible at**:
  - http://localhost:8447
  - http://CTTT:8447 (via hosts file)

## 🔧 Advanced Options

### Custom Install Directory
```bash
java -Dcttt.installer.mode=install -jar CTTT-Installer.jar --install-dir "D:\MyApps\CTTT"
```

### With Network Path
```bash
java -Dcttt.installer.mode=install -jar CTTT-Installer.jar --network-path "\\server\shared\CTTT"
```

### Silent Installation (for IT departments)
```bash
java -Dcttt.installer.mode=install -jar CTTT-Installer.jar --install-dir "C:\CTTT" --silent
```

## 📁 Output Files

After running `build-for-users.ps1`, you'll find in `dist-final/`:

```
dist-final/
├── CTTT-7.2.0.exe           # Native installer (BEST for users)
├── CTTT-Setup.exe           # EXE wrapper installer
├── CTTT-Installer.jar       # JAR installer
└── Install-CTTT.bat         # Batch wrapper for JAR
```

## 🚀 Quick Distribution

**For immediate distribution to non-technical users:**

1. Run: `powershell -ExecutionPolicy Bypass -File build-for-users.ps1 -BuildType native`
2. Upload `dist-final/CTTT-7.2.0.exe` to file sharing service
3. Send users the download link
4. Tell them: "Download and double-click to install CTTT"

That's it! Your users get a professional Windows installer that handles everything automatically.

## 🔍 Troubleshooting

### "jpackage not found"
- Install JDK 17+ (not just JRE)
- Download from: https://adoptium.net/

### "Launch4j not found"
- Download Launch4j from: http://launch4j.sourceforge.net/
- Install to default location
- Re-run build script

### "Maven build failed"
- Ensure you're in the project root directory
- Run: `mvn clean compile` to check for errors

### Users can't install
- Ensure they run installer "as Administrator"
- For corporate environments, use the native installer (no dependencies)