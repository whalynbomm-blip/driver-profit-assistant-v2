# IndAssistant

IndAssistant is delivered as one Android APK with the earnings dashboard embedded inside the app.

## One installable Android app

Install the IndAssistant APK. From its main screen you can:
- Enable Android Accessibility offer reading and the floating recommendation overlay.
- Configure running cost per kilometre and target net earnings per hour.
- Open the earnings dashboard inside IndAssistant (no separate browser app is needed).
- View detected ride history in the dashboard. When the dashboard opens or resumes, it reads detected offers from the Android app and merges new records into its local dashboard history without duplicating existing records.
- Change the shared running-cost and hourly-target settings in either the Android settings screen or the embedded dashboard.

The embedded dashboard is hosted at https://whalynbomm-blip.github.io/driver-profit-assistant-v2/ and therefore needs an internet connection to load. The Android offer reader and floating overlay remain native Android features.

## Web dashboard

The same dashboard remains available separately in Chrome:
https://whalynbomm-blip.github.io/driver-profit-assistant-v2/

## Build

GitHub Actions builds a debug APK on pushes to `main`. Open **Actions**, choose the latest successful **Build Android APK** run, then download the `driver-profit-assistant-debug` artifact and extract `app-debug.apk`.

## Important limitations

- Offer reading and OCR still need real-device validation against the installed inDrive version. Always verify detected fare, distance, duration and recommendation.
- Android history is transferred into the embedded dashboard when it opens/resumes; the bridge is on-device and does not upload rides to a cloud account.
- Dashboard records created separately in Chrome are stored in that browser and are not automatically copied into the Android app.
- The app never taps Accept or Reject. The driver makes the final decision.
- The APK is a debug/testing build, not a Play Store release.

## Repository

Android package ID: `za.co.driverprofit.assistant`.
