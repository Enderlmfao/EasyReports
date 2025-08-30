# EasyReport  
### A Modern, GUI-Based Reporting Plugin  

**EasyReport** is the simple, modern solution to player reporting. Forget clunky commands and confusing formats. This plugin provides an intuitive GUI for players and sends clean, detailed reports directly to your Discord—making moderation efficient and professional.  

Manage your server with clarity. EasyReport bridges the gap between in-game events and your staff's workflow.  

---

## 🚀 Features
- **Intuitive GUI Reporting**: Players use `/report <player>` to open a clean interface with your pre-defined reasons.  
- **Private Chat Prompts**: Guides users through private messages for custom reasons or proof—no public spam.  
- **Discord Integration**: Sends formatted, detailed reports directly to your Discord webhook.  
- **Fully Customizable**:  
  - Edit reasons in the config.  
  - Change webhook name, avatar, and embed color.  
  - Customize in-game staff alert messages with color codes.  
- **Instant Staff Alerts**: Staff with `quickreport.staff` permission get notified immediately in-game.  
- **In-Game Report Viewer**: `/reports` lets staff view recent reports in a GUI.  
- **Live Reload**: `/easyreport reload` applies changes instantly—no restart required.  

---

## ⚡ Installation & Setup
1. Download **EasyReport.jar** and place it in your `/plugins` folder.  
2. Start the server once to generate config files.  
3. Open `config.yml` and paste your Discord webhook URL.  
   - Add a name and avatar for your webhook (**required**).  
4. Customize `config.yml` to your liking.  
5. Run `/easyreport reload` or restart the server. Done!  

---

## 📜 Commands & Permissions
### Player Commands
```
/report <player>
```
- Opens the GUI to report a player.  
- **Permission**: `quickreport.report` *(Default: true)*  

### Staff Commands
```
/reports
```
- Views recent reports in a GUI.  
- **Permission**: `quickreport.staff` *(Default: op)*  

```
/easyreport reload
```
- Reloads the config.yml file.  
- **Permission**: `quickreport.reload` *(Default: op)*  
