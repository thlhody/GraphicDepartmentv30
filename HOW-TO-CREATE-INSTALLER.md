# 🚀 How to Create CTTT Installer (Super Simple)

## ✨ One-Click Solution

You don't need to understand Maven! Just run ONE file:

### Windows (Easiest):
```
Double-click: MAKE-INSTALLER.bat
```

### Or PowerShell (More features):
```
Right-click → Run with PowerShell: MAKE-INSTALLER.ps1
```

## 🎯 What Happens

1. **Script builds your app** (using Maven in background)
2. **Creates a folder called `READY-FOR-USERS`**
3. **Puts installer files in that folder**

## 📦 What You Get

After running the script, you'll have:

```
READY-FOR-USERS/
├── INSTALL-CTTT.bat        ← Send this to users!
├── QUICK-INSTALL.bat       ← For impatient users
├── CTTT-Installer.jar      ← Required file
└── README.txt              ← Instructions for users
```

## 🎯 For Your Users

Tell them:
1. **Download the files**
2. **Double-click `INSTALL-CTTT.bat`**
3. **Follow the prompts**
4. **Done!**

## 📋 Requirements (One-time setup)

You need these installed ONCE on your computer:
- ✅ **Java 17+** - Download from https://adoptium.net/
- ✅ **Maven** - Download from https://maven.apache.org/

**Your users don't need to install anything special!**

## 🔧 Troubleshooting

### "Maven not found"
- Install Maven from https://maven.apache.org/
- Add it to your Windows PATH

### "Java not found"
- Install Java 17+ from https://adoptium.net/
- Make sure it's in your Windows PATH

### Script fails
- Make sure you're in the project folder (the one with `pom.xml`)
- Try running as Administrator

## 🎉 Distribution

Once you have the `READY-FOR-USERS` folder:

1. **Zip the entire folder**
2. **Upload to Google Drive / Dropbox / etc.**
3. **Send link to users**
4. **Tell them: "Extract and run INSTALL-CTTT.bat"**

That's it! Your users get a professional installer that sets up everything automatically.

## 💡 Pro Tips

- **Test first**: Run `READY-FOR-USERS\INSTALL-CTTT.bat` yourself
- **For corporate**: Use `QUICK-INSTALL.bat` for silent installs
- **For developers**: They can use the JAR directly

---

**No more complex setup.iss or Inno Setup needed!** 🎉