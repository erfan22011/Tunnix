# 📘 Tunnix Project Context & Engineering Memory

> **IMPORTANT:** Any new AI agent or developer session working on this codebase **MUST read this document before making any changes**. All development must branch from and preserve the verified stable baseline.

---

## 1. Release History & Baselines

* **App Name:** Tunnix (`com.tunnix.app`)
* **Current Active Release:** `1.0.6` (`versionCode = 7`, `versionName = "1.0.6"`)
* **Previous Stable Releases:**
  * `1.0.5` (`versionCode = 6`, `versionName = "1.0.5"`, commit: `ed11f05`)
  * `1.0.4` (`versionCode = 5`, `versionName = "1.0.4"`, commit: `57234ff`)
  * `1.0.3` (`versionCode = 4`, `versionName = "1.0.3"`, commit: `e7ea730`)
  * `1.0.2` (`versionCode = 3`, `versionName = "1.0.2"`, commit: `39c3f9e`)
  * `1.0.1` (`versionCode = 2`, `versionName = "1.0.1"`, commit: `f670292`)
* **Permanent Baseline Tag:** `stable-baseline-v1.0.1`
* **Release Tags:** `v1.0.1`, `v1.0.2`, `v1.0.3`, `v1.0.4`, `v1.0.5`, `v1.0.6`
* **Instant Rollback Command to v1.0.1:**
  ```bash
  git checkout main && git reset --hard stable-baseline-v1.0.1 && git clean -fd
  ```

---

## 1.0 What was Fixed & Added in Version 1.0.6

1. **Domestic Traffic Direct Routing (Bypass Iranian Websites & Services):**
   * Added `bypassIran: Boolean = true` setting in `AppSettingsDataStore`.
   * Integrated into Xray-core config generation (`XrayConfigGenerator.kt`):
     - DNS routing: routes `regexp:.*\\.ir$`, `domain:ir`, `geosite:ir`, and major Iranian services (Digikala, Snapp, Divar, Aparat, Rubika, Eitaa, Bale, Telewebion, Varzesh3, banks, PSPs) directly to `localhost`.
     - Routing rules: top-priority `direct` rule for Iranian domains, regex patterns, and `geoip:ir`.
     - `domainStrategy` set to `IPIfNonMatch` to resolve and check against `geoip:ir` if domain doesn't match directly.
     - Sockets opened by freedom (`direct`) outbound are protected via `VpnService.protect()`, bypassing the TUN interface and routing directly via the physical interface. Zero packets touch the VPN tunnel.
   * Instant direct routing on connection: because the config is loaded at tunnel start, no initial packets leak to the proxy.
   * Real-time tunnel update: toggling `bypassIran` in Settings restarts the active tunnel immediately with the updated routing config without requiring manual reconnect.
2. **Settings UI Integration:**
   * Added Section 3 GlassCard toggle with switch control, Persian and English descriptions.
   * Fallback and runtime version updated to `1.0.6`.
3. **Elimination of Initial Startup Flash/Jump (Redeem to Main Screen Transition):**
   * Added `isInitialized` to `UiState` and guarded `TunnixNavigation` in `MainActivity.kt` with `if (uiState.isInitialized)`.
   * Previously, `hasSubscription` defaulted to `false` during the initial 15-30ms while DataStore read from disk, causing `NavHost` to build with `startDestination = "redeem"` and then slide-animate to `"main"` on every app launch.
   * Now, the initial destination is resolved before `TunnixNavigation` is composed, rendering directly on the Main screen with zero visual jumping, sliding, or flashing.
4. **Fix Server List Jumping, Flickering, and Selection Failure:**
   * Fixed infinite recursive re-fetch loop in `MainViewModel`: `observeDataStore()` previously triggered `loadSubscription()` on every DataStore emission (including theme change, language change, bypassIran toggle, server selection, and cache saving).
   * Guarded initial launch refresh with `hasDoneInitialRefresh` so it executes strictly once on startup if online.
   * Added coroutine `fetchJob?.cancel()` to cancel any stale in-flight network requests and prevent race conditions.
   * `refreshSubscription` now strictly preserves the user's active selection (`currentlySelectedId`) across network refreshes instead of reverting to older server IDs.
   * `selectServer()` now dynamically reconnects the active tunnel if the VPN is currently connected.

---

## 1.0 What was Fixed & Added in Version 1.0.5

1. **Persistent Server Caching (Offline Mode Availability):**
   * Stored parsed server configurations directly in persistent storage (`AppSettingsDataStore`).
   * When opening the app without internet connectivity, the server list is immediately loaded and populated from cache. The app does not display an empty list or block the user with network errors.
2. **Network State Auto-Recovery (Real-time Reconnect):**
   * Registered system default network callback in `MainViewModel`.
   * When internet connectivity is restored (e.g. mobile data or Wi-Fi turned on), the subscription is automatically refreshed in the background without needing to restart the app.
3. **Resilient Server List Retention:**
   * If an online refresh encounters a network timeout or connection error, existing cached servers are strictly preserved and never cleared or wiped.
4. **Dynamic Version Name in Settings Screen:**
   * `SettingsScreen.kt` dynamically retrieves `versionName` at runtime via `PackageManager`, accurately displaying the current release (1.0.5) instead of a hardcoded string.

---

## 1.0 What was Fixed & Added in Version 1.0.4

1. **VPN Lifecycle & Error State Preservation:**
   * Resolved regression where `stopTunnel()` coroutine overwrote `VpnState.Error` with `VpnState.Disconnected`. Errors are now preserved and surfaced directly to UI.
   * Eliminated race condition on quick server switching and reconnects by tracking active jobs and start IDs.
2. **Crash Prevention on Invalid Server Intent:**
   * Prevented `ForegroundServiceDidNotStartInTimeException` on Android 8+ by guaranteeing a foreground notification is immediately posted before service termination.
3. **Blocking Mode for Virtual TUN (`setBlocking(true)`):**
   * Changed `TunInterfaceBuilder.kt` from `builder.setBlocking(false)` to `builder.setBlocking(true)` to match Go/Xray tun2socks blocking read semantics.
4. **Seamless Network Transition Monitoring:**
   * Replaced transport-specific NetworkCallback with `registerDefaultNetworkCallback` (Android 7+), preventing the app from getting permanently stuck in `Reconnecting` during Wi-Fi to cellular handover.
5. **Accurate Traffic Metrics Calculation:**
   * Fixed compounded accumulation (`+=`) in `XrayCoreController.queryTrafficStats()` by directly tracking cumulative metrics returned by the Xray-core stats engine.
6. **Protocol & Transport Expansion (Reality & gRPC):**
   * Added complete `realitySettings` block generation (`publicKey`, `shortId`, `spiderX`, `serverName`, `fingerprint`) for VLESS Reality configs.
   * Added `grpcSettings` (`serviceName`, `multiMode`) and `httpSettings` generation.
7. **Security & Parser Hardening:**
   * Mitigated SSRF / Open Redirect bypass in `SubscriptionFetcher` by validating redirect targets with network interceptor.
   * Hardened country flag detection in `ServerItemCard.kt` and parsers using tokenized boundaries to eliminate false matches (e.g. Sweden vs Germany, Russia vs USA).
   * Guarded native CoreController loading in JVM tests without returning false positives in Android production.
   * Removed unnecessary `"geoip:private"` dependency from Xray routing rules.

---

## 1.1 What was Fixed & Added in Version 1.0.3

1. **Dynamic System Bars Color Coordination (Status Bar & Navigation Bar):**
   * Configured `SideEffect` in `TunnixTheme` using `WindowCompat.getInsetsController`.
   * In Light Mode: `statusBarColor` and `navigationBarColor` match `PearlBackground` (`#F8FAFC`), and system icons/navigation buttons automatically flip to dark (`isAppearanceLightStatusBars = true`, `isAppearanceLightNavigationBars = true`).
   * In Dark Mode: `statusBarColor` and `navigationBarColor` match `ObsidianBackground` (`#0B0F17`) with light system icons.
2. **Smooth Fluid Screen Transitions:**
   * Updated `TunnixNavigation.kt` to combine `FastOutSlowInEasing` slide transitions with smooth `fadeIn` (320ms) and `fadeOut` (220ms).
3. **Smooth Interactive Card Selection & Ripple:**
   * Added `animateColorAsState` and `animateDpAsState` to `ServerItemCard.kt` for card border color, border width, and rack icon background.
   * Selection checkmark now scales and fades smoothly with `AnimatedVisibility(enter = fadeIn + scaleIn, exit = fadeOut + scaleOut)`.
4. **Smooth Power Button with Spring Bounce & Fluid Halo:**
   * `LiquidPowerButton` active color transitions smoothly with `animateColorAsState(tween(350, FastOutSlowInEasing))`.
   * Added tactile spring press bounce (`Spring.DampingRatioMediumBouncy`) compressing by 8% on touch with rubbery rebound.
   * Multi-ring spinning comet halo and pulsing glow in connecting state.
5. **Zero Layout Shift (CLS) on Connection:**
   * Anchored status header in `MainScreen.kt` to a fixed single-line `height(26.dp)`. Removed redundant multi-line subtitle that previously pushed the power button and list down on tap. All UI elements remain 100% stationary.
6. **Dark Mode Screen Transition Fix:**
   * Root `Surface` in `MainActivity.kt` explicitly wrapped with `TunnixTheme` and colored `ObsidianBackground`. Eliminated white background flash between screens.

---

## 1.2 What was Fixed & Added in Version 1.0.2

1. **VLESS Certificate Pinning (`pcs` parameter):**
   * Supported `pcs` (Pinned Peer Certificate SHA-256) by mapping it to `tlsSettings.pinnedPeerCertSha256` in `XrayConfigGenerator.kt`.
   * Verified against bundled native Xray-core `v1.260327.1` in `libv2ray.aar` (`libgojni.so`).
2. **ALPN Collision Resolution (TCP + HTTP Transport):**
   * Enforced `alpn = ["http/1.1"]` strictly when `network == NetworkType.TCP` and `headerType == "http"`.
   * Preserves `["h2"]` for XHTTP and gRPC, and `["http/1.1"]` for WebSocket.
3. **Comprehensive Server Address & Domain Leak Protection:**
   * `displayName` in `ServerConfig.kt` dynamically strips server IPs, domains, and ports from any position (leading, middle, trailing, parentheses, brackets, hyphen) while strictly preserving legitimate remarks like `(clone)`, `(VIP)`, `(US)`, and custom names.
   * `VpnNotificationHelper.kt` now displays `server.displayName` instead of `"${server.name} (${server.address})"`.
   * `ServerItemCard.kt` and `MainScreen.kt` render sanitized `displayName`.
   * `TunnixVpnService.handleError()` sanitizes error messages replacing IPs/domains with `[server]`.
   * `SubscriptionDomainRules.kt` error message sanitized to not leak allowed domain list.
4. **MIUI/Android Upgrade Fix:**
   * Incremented `versionCode` from 2 to 3 to resolve "App not installed as package appears to be invalid" update errors.

---

## 2. Technology Stack & Architecture

* **Platform:** Android (Min SDK: 26, Target SDK: 34, Compile SDK: 34)
* **Languages & Runtimes:** Kotlin 1.9+, Java 17, Go (embedded inside Xray-core)
* **UI Framework:** Jetpack Compose, Material3, Liquid Glass Dark/Light theme, Full Persian RTL & English LTR
* **Core Engine:** Native Xray-core (`libv2ray.aar` from AndroidLibXrayLite v26.11.9)
* **Tunneling Mechanism:** Android `VpnService` generating a virtual TUN file descriptor bound directly to Xray's Go loop (`Libv2ray.startLoop`)
* **Modular Clean Architecture (9 Modules):**
  1. `:app` — Application entrypoint, `MainActivity`, `MainViewModel`, dependency wiring.
  2. `:core-models` — Domain data classes (`ServerConfig`, `VpnState`, `SecureVault`, `TrafficStats`).
  3. `:libv2ray` — Native AAR wrapper bridging Android to the Go Xray binary.
  4. `:subscription-parser` — Decodes Base64 subscriptions and parses VLESS, VMess, Trojan, and Shadowsocks links.
  5. `:config-modifier` — Asymmetric XHTTP engine that injects hidden downlink settings on designated ports.
  6. `:xray-core` — Converts domain configs to native Xray JSON schemas and manages core lifecycle.
  7. `:vpn-service` — `TunnixVpnService`, `TunInterfaceBuilder`, `VpnNotificationHelper`, and `VpnTunnelManager`.
  8. `:traffic-monitor` — Real-time network throughput and cumulative data tracking.
  9. `:settings` — Preferences, theme mode, language settings, and DataStore persistence.
  10. `:ui` — Composable components (`LiquidPowerButton`, `ServerItemCard`, `TrafficStatsCard`, `MainScreen`).

---

## 3. Important Source Files & Responsibilities

| File | Module | Responsibility |
| :--- | :--- | :--- |
| `MainViewModel.kt` | `:app` | Central UI state holder, manages server selection, subscription fetching, and tunnel trigger. |
| `ServerConfig.kt` | `:core-models` | Primary data class representing VPN servers across protocols. |
| `SecureVault.kt` | `:core-models` | In-memory encrypted repository for hardcoded port 2053 & 6793 configurations. |
| `ShadowsocksParser.kt` | `:subscription-parser` | Extracts and parses `ss://` configs (SIP002 and legacy base64 formats). |
| `XhttpConfigModifier.kt` | `:config-modifier` | Inspects server ports and injects asymmetric `downloadSettings` and `xPaddingBytes`. |
| `XrayConfigGenerator.kt` | `:xray-core` | Constructs standard Xray JSON configuration (`inbounds`, `outbounds`, `routing`, `dns`). |
| `XrayCoreController.kt` | `:xray-core` | Invokes `Libv2ray.initV2Env` and `Libv2ray.startLoop(configJson, tunFd)`. |
| `TunnixVpnService.kt` | `:vpn-service` | Android foreground VPN service managing tunnel lifecycle. |
| `TunInterfaceBuilder.kt` | `:vpn-service` | Configures MTU, IP routing (`0.0.0.0/0`), and establishes the TUN file descriptor. |
| `VpnNotificationHelper.kt`| `:vpn-service` | Builds foreground notifications with disconnect action. |
| `MainScreen.kt` | `:ui` | Main dashboard displaying power button, real-time speed, and server selection. |

---

## 4. Confirmed Working Features (Must NOT Be Broken)

1. **Native Core Tunneling:** Android TUN interface binding directly to native Xray-core.
2. **Shadowsocks Protocol Support:** Extraction, parsing, and connection to `ss://` servers from subscriptions.
3. **Dual Asymmetric Ports (2053 & 6793):**
   * **Port 2053:** Uplink connects to server address; Downlink dynamically routed to `2053ipapp.genus.ir` (SNI/Host: `2053ovapp.genus.ir`).
   * **Port 6793:** Uplink connects to server address; Downlink dynamically routed to `6793ipapp.genus.ir` (SNI/Host: `6793ovapp.genus.ir`).
   * **Padding & Mode:** `xPaddingBytes: "100-1000"`, `mode: "stream-up"`.
   * **Security:** Values are encrypted in memory via `SecureVault` and completely hidden from user settings UI.
4. **Subscription Importing:** Decoding Base64 subscriptions and auto-populating server cards.
5. **Real-time Traffic Monitoring:** Accurate bandwidth speeds and session data usage display.
6. **Permanent Release Signing:** Consistent RSA-4096 `release.keystore` with v1, v2, and v3 schemes.
7. **Architectural ABI Splits:** Standalone `arm64-v8a` (~20 MB), `armeabi-v7a` (~20.5 MB), and `universal` (~31.7 MB).

---

## 5. Known Issues & Pending Tasks (To Be Addressed Carefully)

* **Issue 1: Package Update Error ("App not installed as package appears to be invalid"):**
  * *Root Cause:* Installing an update over an existing version on MIUI/Android requires an incremented `versionCode` (e.g., `versionCode = 3`). Keeping `versionCode = 2` causes package installer to reject same-version installs.
* **Issue 2: Server IP & Domain Visibility:**
  * *Root Cause:* In `f670292`, `VpnNotificationHelper.kt` formats the notification as `"${it.name} (${it.address})"`, exposing the raw domain (e.g. `(ovv.pelqexa.ir)`). Server domains and IP addresses must be 100% hidden across the entire application and notification shade.
* **Issue 3: UI Header Text Wrapping:**
  * *Root Cause:* In `MainScreen.kt`, the header `Servers (${servers.size})` and `Using: ...` share an unconstrained `Row`. When the server name is long, `Servers` breaks onto two lines (`Servers` and `1`). Needs isolated badge and single-line truncation.
* **Issue 4: TCP-HTTP Protocol Conflict:**
  * *Root Cause:* For `type=tcp` with `headerType=http`, ALPN must be exclusively `http/1.1`. Negotiating `h2` causes a binary framing conflict with Xray's text HTTP requests.
* **Issue 5: TUN Non-blocking Loop Termination:**
  * *Root Cause:* `TunInterfaceBuilder.kt` has `builder.setBlocking(false)`. Native gVisor/Go TUN reader requires `builder.setBlocking(true)` to prevent immediate termination on read.
* **Issue 6: Router Asset Dependency:**
  * *Root Cause:* Routing rule with `"geoip:private"` fails if `geoip.dat` is missing. Direct explicit CIDRs avoid dependency on external assets.

---

## 6. Previous Failed Changes & Regression Prevention Rules

* **DO NOT generate ad-hoc keystores in CI:** Always use the committed `release.keystore`. Dynamic keystores break the ability of users to install updates over previous versions.
* **DO NOT overwrite `VpnState.Error` with `VpnState.Disconnected`:** In `TunnixVpnService.handleError()`, avoid calling asynchronous `stopTunnel()` that overwrites the error state before the UI can render it.
* **DO NOT reference undefined theme tokens:** `LiquidGlassColors` contains `cardGlass`, `surface`, `glassBorder`, `textPrimary`, `textSecondary`, and `textMuted`. Do not invent `surfaceLight` or other unmapped color names.
* **DO NOT allow APK double-signing conflicts:** Gradle's `signingConfig` already signs the APK during `assembleRelease`. When running `apksigner`, ensure alignment and signature verification are validated cleanly.

---

## 7. Build & Verification Procedures

* **Feature & Parsing Verification:**
  ```bash
  python tests/test_v101_features.py
  ```
* **Full End-to-End Suite (25 Tests):**
  ```bash
  python tests/test_end_to_end_suite.py
  ```
* **Local Release Build:**
  ```bash
  ./gradlew assembleRelease
  ```
* **Release Triggering:**
  A git push with a version tag (`v*`) triggers `.github/workflows/android-release.yml` on GitHub Actions.

---

## 8. Strict Development Rules for Future Sessions

1. **Baseline Invariance:** Never overwrite or rebase past `stable-baseline-v1.0.1` (`f670292`).
2. **Step-by-Step Verification:** Run unit test suites before committing or pushing any code.
3. **No Unapproved Releases:** Do not delete releases, force push tags, or trigger CI release builds without explicit user instructions.
4. **Data Privacy:** Never display raw server IP addresses or domain names in UI text or system notifications.
