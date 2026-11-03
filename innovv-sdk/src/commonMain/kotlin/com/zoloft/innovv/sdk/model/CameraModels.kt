package com.zoloft.innovv.sdk.model

/** Camera work mode used by the `3001` mode-change command. */
public enum class CameraMode(public val code: Int) {
    /** Still-photo mode. */
    PHOTO(0),

    /** Normal video recording mode. */
    VIDEO(1),

    /** Playback/album mode. */
    PLAYBACK(2),

    /** Video time-lapse mode. */
    VIDEO_TIMELAPSE(3),

    /** Photo time-lapse mode. */
    PHOTO_TIMELAPSE(4),

    /** Slow-motion video mode used by some Novatek models. */
    VIDEO_SLOW_MOTION(5),

    /** Photo-blur mode used by some Novatek models. */
    PHOTO_BLUR(6);

    public companion object {
        /** Returns the mode for a raw camera code, or `null` if unknown. */
        public fun fromCode(code: Int): CameraMode? = entries.firstOrNull { it.code == code }
    }
}

/** SD-card state reported by command `3024`. */
public enum class SdCardState(public val code: Int) {
    /** No card is inserted. */
    REMOVED(0),

    /** Card is present and usable. */
    INSERTED(1),

    /** Card is locked, unreadable, or in an error state. */
    LOCKED_OR_ERROR(2);

    public companion object {
        /** Returns the state for a raw camera code, or `null` if unknown. */
        public fun fromCode(code: Int): SdCardState? = entries.firstOrNull { it.code == code }
    }
}

/** Media type inferred from the file extension in a `3015` response. */
public enum class FileType {
    /** JPEG or other still image. */
    PHOTO,

    /** MP4, MOV, or TS movie. */
    VIDEO,

    /** Unknown extension; the file can still be downloaded. */
    UNKNOWN,
}

/**
 * One media file reported by `cmd=3015`.
 *
 * @property name file name from `<NAME>`.
 * @property path camera path from `<FPATH>`, for example
 *   `A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4`.
 * @property relativePath path suitable for a URL, with `A:` removed and
 *   backslashes converted to `/`.
 * @property url direct HTTP download URL.
 * @property sizeBytes file size from `<SIZE>`.
 * @property timecodeEpochSeconds file creation time from `<TIMECODE>`.
 * @property timeText camera-formatted time from `<TIME>`.
 * @property attributes raw `<ATTR>` value. Its bit meanings are not documented by
 *   the firmware.
 * @property type media type inferred from the extension.
 */
public data class CameraFile(
    public val name: String,
    public val path: String,
    public val relativePath: String,
    public val url: String,
    public val sizeBytes: Long,
    public val timecodeEpochSeconds: Long,
    public val timeText: String,
    public val attributes: Int,
    public val type: FileType,
) {
    /** Thumbnail URL (`?custom=1&cmd=4001`). */
    public val thumbnailUrl: String get() = "$url?custom=1&cmd=4001"

    /** Larger preview URL (`?custom=1&cmd=4002`). */
    public val previewUrl: String get() = "$url?custom=1&cmd=4002"

    /** Movie metadata URL (`?custom=1&cmd=4005`). */
    public val infoUrl: String get() = "$url?custom=1&cmd=4005"
}

/** Result of `cmd=1001` (capture photo). */
public data class CaptureResult(
    /** Captured file, if the camera returned one. */
    public val file: CameraFile?,
    /** Remaining photo slots, if the camera returned `<FREEPICNUM>`. */
    public val freePictures: Int?,
)

/** One resolution advertised by `cmd=3030`. */
public data class CameraResolution(
    /** Camera-side name. */
    public val name: String,
    /** Value sent as `&par=<index>` for the matching command. */
    public val index: Int,
    /** Frame width in pixels. */
    public val width: Int,
    /** Frame height in pixels. */
    public val height: Int,
    /** Frames per second. */
    public val frameRate: Int,
    /** Raw camera type field. */
    public val type: Int,
)

/** Movie metadata returned by `cmd=4005`. */
public data class MovieInfo(
    /** Frame width in pixels. */
    public val width: Int,
    /** Frame height in pixels. */
    public val height: Int,
    /** Clip length in seconds. */
    public val lengthSeconds: Int,
)

/** Live-view links returned by `cmd=2019`. */
public data class LiveViewLinks(
    /** Movie-mode URL, typically RTSP. `null` if the camera omitted it. */
    public val movieUrl: String?,
    /** Photo-mode URL, typically an MJPEG URL on port 8192. `null` if omitted. */
    public val photoUrl: String?,
)

/** Current AP credentials returned by `cmd=3029`. */
public data class WifiInfo(
    /** Access-point SSID. */
    public val ssid: String,
    /** Access-point passphrase. */
    public val passphrase: String,
)

/** One access point returned by `cmd=3038`. */
public data class WifiNetwork(
    /** SSID advertised by the access point. */
    public val ssid: String,
    /** Raw authentication-type value reported by the camera. */
    public val authType: Int,
    /** Signal strength in dBm. */
    public val rssiDbm: Int,
)

/** GPS fix returned by `cmd=8058`. */
public data class GpsInfo(
    /** Fix timestamp in Unix epoch seconds; `0` when there is no fix. */
    public val timestampEpochSeconds: Long,
    /** Longitude in degrees. */
    public val longitude: Double,
    /** Latitude in degrees. */
    public val latitude: Double,
    /** Altitude in metres. */
    public val altitudeMeters: Double,
    /** Speed value reported by the camera. */
    public val speed: Double,
    /** Number of satellites in the fix. */
    public val satellites: Int,
    /** Signal-to-noise ratio reported by the camera. */
    public val snr: Int,
) {
    /** Whether the camera reported a non-zero timestamp. */
    public val hasFix: Boolean get() = timestampEpochSeconds > 0L
}

/** Device identity assembled from `cmd=3012` and `cmd=3022`. */
public data class DeviceInfo(
    /** Firmware version string, for example `IK5.5G.20210827.V1.1.12`. */
    public val firmwareVersion: String,
    /** Raw hardware capability value from `cmd=3022`. */
    public val hardwareCapability: Int,
)

/** Battery state returned by `cmd=3019` or the `9019` event. */
public data class BatteryStatus(
    /** Raw level: 0 full, 1 medium, 2 low, 3 empty, 4 exhausted, 5 charging. */
    public val level: Int,
) {
    /** Whether the camera reports that it is charging. */
    public val isCharging: Boolean get() = level == CHARGING

    /** Whether the level is low, empty, or exhausted. */
    public val isLow: Boolean get() = level >= LOW

    public companion object {
        /** Full battery. */
        public const val FULL: Int = 0
        /** Medium battery. */
        public const val MEDIUM: Int = 1
        /** Low battery. */
        public const val LOW: Int = 2
        /** Empty battery. */
        public const val EMPTY: Int = 3
        /** Exhausted battery. */
        public const val EXHAUSTED: Int = 4
        /** Charging. */
        public const val CHARGING: Int = 5
    }
}

/** Status returned by a successful camera command. */
public data class CommandResult(
    /** Numeric command ID. */
    public val commandId: Int,
    /** Raw non-negative status. `0` means success. */
    public val status: Int,
    /** Complete XML response. */
    public val rawXml: String,
)
