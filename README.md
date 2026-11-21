# Redmi Bug Hunter X — Android v0.1

Native Java Android app, minSdk 26, no root required. HTTPS-only single-request authorized web security baseline auditor.

## Build the debug APK using only an Android phone and GitHub

1. Sign into GitHub in your phone browser. Make a **private** repository, e.g., `redmi-bug-hunter`.
2. Extract this ZIP in your file manager. Upload **the contents** of this directory to the repository, preserving `.github/workflows/build-apk.yml`. GitHub mobile website may need browser "Desktop site" for the upload UI. Alternatively use an Android Git client that preserves dotfiles.
3. Go to the repository's **Actions** tab and select **Build Redmi Bug Hunter APK** → **Run workflow**. A push to `main` or `master` also triggers a build.
4. Open the completed green run → **Artifacts** → download **RedmiBugHunter-debug-apk**. Extract that artifact ZIP to obtain `app-debug.apk`.
5. Install the APK through Android Files. Android may ask you to allow installations from that source.

> GitHub-hosted builds require an internet connection and may require GitHub Actions to be enabled for the repository. A debug APK is automatically signed with a build-machine debug key; subsequent build runs may use different keys and need uninstall/reinstall. Keep any reports before uninstalling.

## Local Android IDE build

Open this project's directory as a Gradle project in an Android IDE supporting JDK 17, Android Gradle Plugin 8.5.2, Gradle 8.7, Android SDK 35. Build `:app:assembleDebug`.

## App scope and behavior

- Requires explicit in-app authorization confirmation.
- Requires an exact scope hostname matching the HTTPS URL, no custom port.
- Sends one HTTPS GET (redirects disabled), using Android's normal TLS certificate validation.
- Reviews HSTS, CSP, X-Content-Type-Options, Referrer-Policy and Set-Cookie attributes.
- Results may be shared as JSON text from Android's share sheet.
- No exploitation, login attempts, credential collection, bulk crawling, or stealth capabilities.

## Limitations

This is the first Android-native module, not the complete multi-module research suite. The app has not been compiled in the packaging environment because Android SDK tools were unavailable. Use the Actions build to confirm compilation. HTTP response headers do not prove exploitation; verify findings manually.
