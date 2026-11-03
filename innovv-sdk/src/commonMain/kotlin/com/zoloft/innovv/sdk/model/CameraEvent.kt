package com.zoloft.innovv.sdk.model

/**
 * Event pushed by the camera on TCP port `3333`.
 *
 * The event stream is a sequence of `<Function><Cmd>…</Cmd><Status>…</Status>
 * </Function>` messages. The mapping below is derived from the stock
 * application's event switch and its notification constants. Some status codes
 * are model-specific; unrecognised messages are exposed as [Unknown].
 */
public sealed interface CameraEvent {
    /** Raw event command ID. */
    public val commandId: Int

    /** Raw event status field. */
    public val status: Int

    /** Recording duration update from event command `2020`. */
    public data class RecordingTime(
        /** Recording duration in seconds. */
        public val seconds: Int,
    ) : CameraEvent {
        override val commandId: Int get() = 2020
        override val status: Int get() = seconds
    }

    /** Recording started (`3020`, status `1`). */
    public data object RecordStarted : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 1
    }

    /** Recording stopped (`3020`, status `2`). */
    public data object RecordStopped : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 2
    }

    /** Number of detected camera sensors changed (`3020`, status `8`). */
    public data object SensorCountChanged : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 8
    }

    /** SD card inserted (`3020`, status `9`). */
    public data object CardInserted : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 9
    }

    /** SD card removed (`3020`, status `10`). */
    public data object CardRemoved : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 10
    }

    /** Protection state changed (`3020`, status `11`/`12`). */
    public data class ProtectionChanged(
        /** `true` when protection started. */
        public val enabled: Boolean,
    ) : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = if (enabled) 11 else 12
    }

    /** GPS state changed (`3020`, status `13`/`14`). */
    public data class GpsChanged(
        /** `true` when GPS started. */
        public val enabled: Boolean,
    ) : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = if (enabled) 13 else 14
    }

    /** A photo was downloaded or saved (`3020`, status `16`). */
    public data object PhotoDownloaded : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = 16
    }

    /** Time-lapse state changed (`3020`, status `21`/`22`). */
    public data class TimelapseChanged(
        /** `true` when time-lapse started. */
        public val enabled: Boolean,
    ) : CameraEvent {
        override val commandId: Int get() = 3020
        override val status: Int get() = if (enabled) 21 else 22
    }

    /** Battery level changed (`9019`). */
    public data class BatteryChanged(
        /** Raw level, same scale as [BatteryStatus.level]. */
        public val level: Int,
    ) : CameraEvent {
        override val commandId: Int get() = 9019
        override val status: Int get() = level
    }

    /** Unrecognised event command or status. */
    public data class Unknown(
        override val commandId: Int,
        override val status: Int,
    ) : CameraEvent
}
