# InstallAndroid

Standalone LineageOS Internal Storage (eMMC / NVMe) Installer Application.
Designed for Google Trogdor (Qualcomm SC7180) Chromebooks and portable to other Depthcharge / ChromeOS hardware running LineageOS.

## Features
- Hardware detection (Live USB boot source vs. internal storage target)
- Depthcharge boot priority partition configuration (Priority=15, Tries=15, Successful=1)
- Seamless root service integration via Android init properties
- Real-time sector-level progress tracking during OS container installation
- Non-casefolded F2FS userdata initialization compatible with modern kernels
