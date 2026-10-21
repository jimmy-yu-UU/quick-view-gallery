# QV Gallery

A lightweight, ad-free photo & video gallery for Android 11+ (64-bit), inspired by the classic QuickPic.

- Albums (grid / list), sort, search, pin, hide (password protected), exclude
- Fast viewer with deep zoom for huge images, GIF / animated WebP, HEIC / AVIF, RAW (DNG + embedded previews)
- Video playback (Media3)
- Editor: crop (free / fixed ratios), rotate, flip; save as copy or overwrite
- Slideshow with transitions
- Copy / move / rename / delete, SD card & USB drives
- Pattern / PIN lock with biometric unlock
- Material You dynamic color or custom accent; album background image
- Can be set as the default image viewer

## Install

Download the latest APK from [Releases](../../releases/latest).
Every push to `main` is built by GitHub Actions and published as a new release.

## License

QV Gallery is released under the [MIT License](LICENSE).
It uses Jetpack Compose, Coil, Telephoto and Media3, which are licensed under the Apache License 2.0.

## Wi-Fi transfer

- **Phone ↔ phone**: open *Wi-Fi transfer > Receive* on one phone; on the other, pick files and choose it under *Send*, then enter the 6-digit pairing code.
- **Phone → computer**: start receiving, open the shown address (or scan the QR code) in a browser on the same network, enter the pairing code, then browse, preview, download (single files or ZIP) and upload.
- Received files go to `Pictures/QV Transfer`. Hidden and excluded albums are never shared. Traffic stays on the local network (plain HTTP).
