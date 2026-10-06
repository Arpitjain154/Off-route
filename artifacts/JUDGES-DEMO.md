# IDR Navigator — two-minute prototype demo

Install `IDR-Navigator-debug.apk` (Android 8+, version 1.2) over the existing app. The statewide map makes this APK larger; allow the installation and first map initialization to finish. Start the engine outdoors with precise location enabled before presenting. No connected phone was available for automated device verification.

Laptop restart shortcut: run `.\scripts\start-demo.ps1` from PowerShell in the project folder. It starts missing local database/API/web processes and writes logs under `artifacts/`.

1. **Offline coverage:** Open Live navigation and tap **State view**. Show Madhya Pradesh's roads, lakes and cities. Recenter on your GPS fix. The map is packaged locally, with no map API account required.
2. **Real tracking:** With the phone still, wait for a fresh GPS speed measurement and quiet sensors. The HUD should show **Stationary**, zero speed. Settings shows GPS fix age, accuracy and whether speed is measured or estimated. Test this outdoors before the judging session.
3. **Architecture:** Explain that Android captures sensors and runs one Java error-state Kalman filter. GPS, physics constraints and offline GraphHopper matching supply corrections to that same filter. React receives fused positions rather than raw sensor streams.
4. **Record a drive:** Start and stop a short recording. Show saved drive metadata and history. Upload requires the local API connection. To connect a USB phone: `adb reverse tcp:3001 tcp:3001`, then enter `http://127.0.0.1:3001` in app Settings. Keep `npm run db:start` and `npm run dev` running on the laptop.
5. **Actual ML training:** Show Settings → Intelligence on board, then `artifacts/ml/MODEL-CARD.md`. Three TFLite models were trained using 144 IO-VNBD synchronized CSVs. Entire driver recordings were held out. Speed MAE: 4.09 m/s versus 5.98 m/s baseline; motion balanced accuracy: 63.81%. GPS-health proxy classification is not reliable enough to enable.

For an indoor presentation without GPS, use the laptop's `http://localhost:5173` **Test drive**. Describe it explicitly as a simulation for demonstrating the UI and recording workflow. It is not live phone tracking or evidence of tunnel accuracy.

**State the limits clearly:** this is a working engineering prototype, not field-validated navigation. Learned corrections remain disabled pending device validation. IO-VNBD lacks the required pothole/phone-knock annotations; those outputs are not claimed as trained detectors. Do not claim zero drift, guaranteed tunnel accuracy, or a measured position-accuracy result. The actual training report and exported model checks are included.
