<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland Icon"/>

# OPPOPods

**System-level OPPO earphone control for HyperOS devices**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/OppoPods?style=flat-square&logo=github&color=black)](https://github.com/1812z/OppoPods/releases)
![Downloads](https://img.shields.io/github/downloads/1812z/OppoPods/total?style=flat-square)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS%203%2F4-orange?style=flat-square)](https://hyperos.mi.com)


**English** | **[Simplified Chinese](README.md)**

</div>


An Xposed module that provides system-level OPPO earphone control for Xiaomi HyperOS devices.


### Earphone Features

- **Noise Cancellation Control** — Switch between Off / Noise Cancellation / Adaptive / Transparency modes
- **Game Mode** — Low-latency audio toggle, with support for automatically enabling it when connected
- **Battery Display** — Real-time battery display for the left earbud, right earbud, and charging case

### HyperOS Integration
- **Hyper Island** — Supports the official Hyper Island or the module's built-in Hyper Island
- **Fusion Device Center** — Supports controls in Fusion Device Center
- **Settings Integration** — Supports controls in system Bluetooth settings
- **Device Transfer** — Supports one-tap multi-device transfer in Fusion Device Center
- **Model Spoofing** — Spoofs a supported Xiaomi earphone model

### Module Features
- **Quick Popup** — Tap the notification or Control Center earphone card to open a floating popup with battery, noise cancellation, and game mode controls; tap "More" to enter the full page
- **Quick Launch** — From the notification or Control Center earphone card, quickly jump to HeyMelody, module settings, or system settings
- **Custom Earphone Image** — Supports one-click import of HeyMelody resources

### System Requirements

- Xiaomi device running **HyperOS** (Android 15+) (Hyper Island only supports OS3/4)
- **LSPosed** API version >= 101

### Usage

1. Install the APK
2. Enable the module in LSPosed and select the recommended scopes
3. Use the one-tap scope restart button in the top-right corner of the app
4. Connect your OPPO earphones via Bluetooth

### OPPO LE Audio (LC3)

Open **Settings → OPPO LE Audio**. The master switch is followed by latency, connection, stability and vendor-protocol settings.

The master switch defaults to off. Device selection, all advanced options, parameter editing and reset are disabled until it is enabled. Existing saved master-switch values are preserved.

Use **Select target devices** below the master switch to select paired earbuds and save a MAC whitelist. Cancel leaves it unchanged. The whitelist defaults to empty and manages no devices, so select your earbuds after upgrading. Device names are no longer used; classic and LE members are associated only through system address/group mappings. Removing a target releases its module GATT holders. Restoring advanced defaults preserves target selection.

AT replies alone are insufficient: preserve/repair classic A2DP and HFP policy first, establish the lead earbud HFP initialization channel, give LE Audio a connection opportunity, complete the group, retain both GATT links and activate the lead. Enabling only system LC3 may disable classic/HFP and prevent AT initialization. Java CONNECTED alone does not guarantee stable audio on both earbuds.

Connection-policy repair, LE priority, direct LE initiation, startup classic reconnection, GATT retention, group completion, context repair and vendor initialization default to enabled. **HFP gating defaults to off and should remain off** for Enco X3 earbud coordination. **Global low latency defaults to off** because bitrate and quality may decrease and the system may reset game state during playback. Context repair never overwrites a nonzero system value. GATT holders may increase power usage and are released on manual disconnect, ACL loss or feature disable.

User-disabled profiles are tracked in the current Bluetooth process; inherited disabled policies from a previous process cannot be reliably distinguished and may be repaired. Startup classic reconnection is limited to one attempt per startup cycle and is not triggered by LE disconnection after closing the case. Missing group members receive one direct completion attempt per manual connection intent; an initial service-discovery failure with ACL still online may trigger one GATT cache refresh and retry.

After upgrading, restart Bluetooth, put both earbuds in the case and reopen it. Basic `OppoPods-LEAudio` logs now include hook installation, policy repair, AT traffic, LE state, group completion and GATT holder lifecycle. Defaults: Vendor ID `1946`, OESF mask `0x3f`.

This port restores the connection/stability path, but does not yet include the reference module BLE/SIRK cross-phone ownership protocol or automatic group yielding on HCI 0x13. Manual disconnect pauses local group reconnection and releases holders; a new manual connection resumes it.

### Credits

- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original project
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components
- [OPPOPods](https://github.com/Leaf-lsgtky/OppoPods) - by Leaf-lsgtky
- [OPPOLeaConnect](https://github.com/Leaf-lsgtky/OPPOLeaConnect) — reference for LE Audio connection, policy repair and earbud stability (GPL-3.0)

### License

GPL-3.0
