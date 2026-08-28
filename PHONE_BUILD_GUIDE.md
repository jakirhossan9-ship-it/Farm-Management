# Mashallah Agro — Phone-only APK Build

This project is a WebView Android app for the existing Mashallah Agro website.

## No laptop required

You can build the APK from a phone using GitHub Actions:

1. Create/login to a GitHub account in your phone browser.
2. Create a new repository, for example `mashallah-agro-android`.
3. Upload all files from this project into the repository, preserving folders.
4. Open **Actions** → **Build APK**.
5. Tap **Run workflow**.
6. Wait for the build to finish.
7. Open the completed workflow run → **Artifacts** → download `mashallah-agro-debug-apk`.
8. Extract the artifact ZIP and install the APK on your Android phone.

## Important

- This creates a **debug APK** for testing, not a Play Store release APK.
- For Google Play, a signed release build and signing key are required.
- The app loads the existing website, so the WordPress software remains the source of truth.
- Before publishing, verify the WebView URL and app permissions.
