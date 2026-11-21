# Driver Profit Assistant V2 — Android companion

This repository contains the Android companion for the Driver Profit Assistant.

## Current scope
- Android accessibility-service scaffold for reading visible inDrive offer text when the user enables it.
- Optional floating overlay to display the text detected from an offer.
- User-controlled reader ON/OFF switch.
- The app does not tap Accept, Reject, or otherwise make trip decisions.

## Important limitations
This is an early companion build, not the complete Driver Profit Assistant. It does not yet provide OCR for image-only offer screens, robust inDrive screen parsing, full trip-profit calculations, account sync, or integration with the Floot dashboard. Those items must be completed before this can be treated as the finished V2 application.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Open the repository's **Actions** tab, select the latest successful **Build Android APK** run, and download the `driver-profit-assistant-debug` artifact.

A debug APK is for installation/testing, not a Play Store release.
