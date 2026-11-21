# Redmi Bug Hunter X — Android v0.1

Native Java Android app, minSdk 26, no root required. Includes local device-security guidance, an authorization-only HTTPS baseline audit, offline security utilities and lessons, and privacy settings shortcuts.

## Build the debug APK using only an Android phone and GitHub

1. Sign into GitHub in your phone browser. Make a **private** repository, e.g., `redmi-bug-hunter`.
2. Extract this ZIP in your file manager. Upload **the contents** of this directory to the repository, preserving `.github/workflows/build-apk.yml`. GitHub mobile website may need browser "Desktop site" for the upload UI. Alternatively use an Android Git client that preserves dotfiles.
3. Go to the repository's **Actions** tab and select **Build Redmi Bug Hunter APK** → **Run workflow**. A push to `main` or `master` also triggers a build.
4. Open the completed green run → **Artifacts** → download **RedmiBugHunter-debug-apk**. Extract that artifact ZIP to obtain `app-debug.apk`.
5. Install the APK through Android Files. Android may ask you to allow installations from that source.

> GitHub-hosted builds require an internet connection and may require GitHub Actions to be enabled for the repository. A debug APK is automatically signed with a build-machine debug key; subsequent build runs may use different keys and need uninstall/reinstall. Keep any reports before uninstalling.

## Local Android IDE build

Open this project's directory as a Gradle project in an Android IDE supporting JDK 17, Android Gradle Plugin 8.5.2, Gradle 8.7, Android SDK 35. Build `:app:assembleDebug`.

## App features and scope

- **Device:** locally displays the Android version, reported security patch, whether a secure screen lock is detected, and whether USB debugging is enabled. It provides a shortcut to Android security settings. Device details are not uploaded.
- **Web audit:** requires explicit authorization and an exact HTTPS hostname. Its deeper passive analysis reports DNS and response timing, TLS certificate/cipher details, complete headers, redirect safety, CSP directive quality, framing defenses, HSTS quality, CORS, caching, cookie scope/prefix rules, technology disclosure, and limited HTML indicators such as mixed content and insecure form actions. Response inspection is capped at 256 KiB and results may be shared as structured JSON.
- **Offline utilities:** Base64, URL and HTML encode/decode; SHA-256 and SHA-512 hashing; unverified JWT header/payload inspection; Unix timestamp conversion; and UUID generation. Utility input stays on the device.
- **HTTP workbench:** sends one authorization-gated HTTPS request using GET, POST, PUT, DELETE, HEAD, or OPTIONS. Supports custom headers, bounded request bodies, same-host redirects, and a size-limited response preview.
- **Scoped traffic inspector:** runs an explicit foreground-only local proxy on `127.0.0.1:8080`. It accepts HTTPS `CONNECT` tunnels only for one exact authorized hostname on ports 443 or 8443, records connection time and byte counts, and leaves TLS payloads encrypted. Test clients must be configured manually and the persistent notification indicates when inspection is active.
- **Learn:** offline explanations of HTTPS, security headers, cookies, and responsible testing.
- **Privacy:** shortcuts to this app's Android permission page and Android privacy settings.
- No exploitation, login attempts, credential collection, bulk crawling, or stealth capabilities.

## Limitations

The device status is a basic snapshot, not a full security assessment; available settings and labels may vary by Android/MIUI version. HTTP response headers do not prove exploitation; verify audit findings manually. Use the Actions build to confirm compilation.
