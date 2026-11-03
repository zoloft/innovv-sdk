# Security policy

## Reporting a vulnerability

Please report security issues privately to the repository owner rather than
opening a public issue. Include:

- affected version,
- platform (Android/iOS/JVM) and OS version,
- a minimal reproduction,
- whether the issue is in the SDK or in the camera firmware.

## Camera security model

The K5 control API is **unauthenticated and plain HTTP**. The camera access
point is the security boundary. This is a property of the shipped firmware, not
of the SDK.

Known implications:

- Anyone on the camera AP can control recording, read the Wi-Fi password
  (`cmd=3029`), download/delete media, or format the card (`cmd=3010`).
- The firmware also enables FTP and TFTP for the SD-card filesystem. The
  firmware image's `/etc/passwd` has an empty root password, and TFTP is
  unauthenticated.
- The SDK does not add authentication, TLS, or authorization.

## Recommendations for applications

1. Change the camera AP SSID/password from the default.
2. Do not expose the camera AP to an untrusted network.
3. Do not log `getWifiInfo()` output.
4. Put `formatSdCard`, `resetSettings`, `deleteAllFiles`, `setWifi`, and
   firmware-update actions behind explicit user confirmation.
5. Allow cleartext HTTP only for the camera host, not for the whole app.
6. Keep the SDK and dependencies up to date.

## Scope

This policy covers the SDK code in this repository. Firmware vulnerabilities
must be reported to the camera vendor.
