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

### Credits

- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original project
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components
- [OPPOPods](https://github.com/Leaf-lsgtky/OppoPods) - by Leaf-lsgtky

### License

GPL-3.0
