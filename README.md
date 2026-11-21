# IndAssistant V2

IndAssistant has two parts in this repository:

1. **Web app (PWA):** driver offer calculator, estimated profit dashboard, offer history, vehicle-cost settings, area notes, and CSV/JSON export.
2. **Android companion:** optional accessibility-based offer text reader, on-device ML Kit OCR fallback, reader ON/OFF switch, floating recommendation overlay, and locally stored detected-offer history.

## Web app

Live site: https://whalynbomm-blip.github.io/driver-profit-assistant-v2/

Open the site in Chrome on Android. Use Chrome's menu and choose **Install app** or **Add to Home screen** to keep it on the phone. Web records are stored locally in that browser on that device; they are not yet synced to the Android companion or a cloud account.

## Android companion

GitHub Actions builds a debug APK on pushes to `main`. Open **Actions**, choose the latest successful **Build Android APK** run, then download the `driver-profit-assistant-debug` artifact and extract `app-debug.apk`.

After installing:
- Open IndAssistant and enable **Offer reading** in Android Accessibility settings.
- Allow the floating assistant overlay when prompted.
- Use the reader ON/OFF control whenever you want to pause or resume detection.
- Set your own running cost per kilometre and minimum hourly net target.

The reader checks accessibility text first and uses Android screen capture plus on-device ML Kit OCR as a fallback on Android 11 or newer. The exact inDrive package name and parsing behaviour still need validation against the installed inDrive version and real offer screens.

## Important limitations

- Automatic reading and OCR are implemented in the Android source, but have **not yet been validated on a real inDrive screen/device**. Treat detected values and recommendations as estimates and check every offer.
- Offers with missing or ambiguous fare, distance, or duration details may not be recorded or may require manual checking.
- To move Android reader history into the dashboard: in Android IndAssistant tap **Export detected offers for web dashboard**, save the JSON file, open the web app, go to **History**, and choose the JSON file to import.
- Android detected offers can now be exported from the Android app as a JSON file and imported into the web app from the **History** tab. This is a deliberate file-based bridge; automatic background/cloud sync is not implemented yet. Exporting and importing does not delete the Android history.
- Area notes are reminders only; there is no live map or safety verification.
- No part of IndAssistant taps Accept or Reject. The driver makes the final decision.
- The Android APK is a debug/testing build, not a Play Store release.

## Repository

Android package ID: `za.co.driverprofit.assistant` (kept stable for future updates).
