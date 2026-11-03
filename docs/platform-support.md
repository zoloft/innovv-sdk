# Platform support

## 1. Support matrix

| Platform | Targets | Minimum | Latest two major versions |
|---|---|---|---|
| Android | `android` (KMP Android library) | API 26 (Android 8.0) | Android 16 (API 36) and Android 17 (API 37) |
| iOS | `iosArm64`, `iosSimulatorArm64`, `iosX64` | iOS 18.0 deployment target | iOS 26 and iOS 27 |
| JVM | `jvm` | JDK 17 | current JDK 17+ |

Android is compiled with **compileSdk 37** and the new
`com.android.kotlin.multiplatform.library` plugin. The SDK uses no Android APIs
above `minSdk 26`; the compile SDK is raised to 37 because OkHttp 5.5.1
requires it.

iOS is built with Kotlin/Native and a static `InnovvSDK.framework`. The
framework declares a minimum OS version of 18.0. Linking and signing require
macOS with Xcode; Linux can compile the Kotlin/Native klibs and is used for
common tests.

## 2. Android

### 2.1 Manifest permissions

The SDK manifest contributes:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

An application that changes or joins Wi-Fi networks must add its own
permissions, for example:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Android 13+ alternative for nearby Wi-Fi devices. -->
<uses-permission
    android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
```

The SDK itself does **not** join or scan the device's Wi-Fi. It assumes the
application has already associated with the camera AP and can reach
`192.168.1.254`.

### 2.2 Cleartext HTTP

The K5 control API is plain HTTP. An Android app targeting API 28+ blocks
cleartext by default. Add a network-security exception for the camera:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.1.254</domain>
    </domain-config>
</network-security-config>
```

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... />
```

For a debug build only, `android:usesCleartextTraffic="true"` is also possible,
but a host-specific exception is safer.

### 2.3 Background/lifecycle

- Do not keep a camera connection open when the user leaves the camera screen
  unless the application has a foreground service and a clear reason.
- `InnovvClient.close()` releases the OkHttp pool.
- The event `Flow` should be collected from a lifecycle-aware scope.

### 2.4 R8 / ProGuard

The SDK does not use reflection. Ktor and OkHttp publish their own consumer
rules. If the application enables full mode, verify the following in a release
build:

- request/response models are not accessed reflectively (they are not),
- coroutines and Ktor are kept by their own rules,
- no custom serialization is used.

## 3. iOS

### 3.1 Framework artifacts

The Kotlin targets produce static frameworks named `InnovvSDK`:

```bash
# Device
./gradlew :innovv-sdk:linkDebugFrameworkIosArm64

# Apple Silicon simulator
./gradlew :innovv-sdk:linkDebugFrameworkIosSimulatorArm64

# Intel simulator
./gradlew :innovv-sdk:linkDebugFrameworkIosX64
```

On macOS, link the framework and add it to the Xcode target. The SDK's public
API is Kotlin-first; Swift sees the generated `InnovvSDK` classes and suspend
functions through the Kotlin/Native Objective-C export.

The framework is static (`isStatic = true`) and has bundle ID
`com.zoloft.innovv.sdk` and minimum OS version 18.0.

### 3.2 App Transport Security

The camera uses plain HTTP. Add a host-specific ATS exception:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <!-- Allows HTTP to local-network hosts such as 192.168.1.254. -->
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>
```

`NSAllowsLocalNetworking` is the preferred option for a camera on the local
network. If your app uses a hostname instead of an IP address and still needs an
exception, add a host-specific `NSExceptionDomains` entry. Do not enable
`NSAllowsArbitraryLoads` for the whole app.

### 3.3 Local network permission

If the app accesses the camera over the local network, add a usage description:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>Connects to the INNOVV K5 dashcam over its Wi-Fi access point.</string>
```

Joining an access point programmatically uses `NEHotspotConfiguration` and the
Hotspot Configuration entitlement. The SDK does not perform this step; the iOS
application owns the network lifecycle.

### 3.4 RTSP/MJPEG playback

- Movie live view is typically RTSP. Use a player that supports RTSP over TCP
  and low-latency buffering.
- Photo live view is typically MJPEG on port 8192. Parse JPEG SOI/EOI frames
  from the HTTP stream.
- Use the URLs returned by `getLiveView()`; do not hard-code the path.

## 4. JVM

The JVM target is useful for:

- common tests,
- desktop tooling,
- media sync scripts.

It uses the Ktor CIO engine. The raw TCP event stream uses `ktor-network` on
the JVM. JDK 17+ is required.

## 5. CI recommendations

A complete matrix should include:

| Job | Runner | Task |
|---|---|---|
| Common/JVM tests | Ubuntu + JDK 21 | `./gradlew :innovv-sdk:jvmTest` |
| Android compile | Ubuntu + Android SDK 37 | `./gradlew :innovv-sdk:compileAndroidMain` |
| Android unit tests | Ubuntu + Android SDK 37 | `./gradlew :innovv-sdk:testAndroidHostTest` |
| iOS compile | macOS + Xcode | `./gradlew :innovv-sdk:compileKotlinIosArm64 :innovv-sdk:compileKotlinIosSimulatorArm64` |
| iOS framework | macOS + Xcode | `./gradlew :innovv-sdk:linkDebugFrameworkIosSimulatorArm64` |
| Publish check | Ubuntu | `./gradlew :innovv-sdk:publishToMavenLocal` |

Linux cannot link/sign Apple frameworks; use macOS for the iOS link job.

## 6. Known platform limitations

- The camera API is plain HTTP; there is no TLS.
- The SDK does not manage Wi-Fi association or Android `Network` binding.
- iOS hotspot joining is application-owned and requires entitlements.
- The event port `3333` is a plain TCP stream; corporate networks or VPNs that
  block arbitrary ports will break it.
- The camera may only support a small number of concurrent clients. Test the
  desired concurrency.
