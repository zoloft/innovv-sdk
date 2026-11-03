package com.zoloft.innovv.sdk

import com.zoloft.innovv.sdk.internal.EventStream
import com.zoloft.innovv.sdk.internal.HttpTransport
import com.zoloft.innovv.sdk.internal.Xml
import com.zoloft.innovv.sdk.internal.parseCameraFile
import com.zoloft.innovv.sdk.internal.parseCameraFiles
import com.zoloft.innovv.sdk.internal.parseGpsInfo
import com.zoloft.innovv.sdk.internal.parseMovieInfo
import com.zoloft.innovv.sdk.internal.parseResolutions
import com.zoloft.innovv.sdk.internal.parseSettingMenu
import com.zoloft.innovv.sdk.internal.parseWifiNetworks
import com.zoloft.innovv.sdk.model.BatteryStatus
import com.zoloft.innovv.sdk.model.CameraEvent
import com.zoloft.innovv.sdk.model.CameraFile
import com.zoloft.innovv.sdk.model.CameraMode
import com.zoloft.innovv.sdk.model.CameraResolution
import com.zoloft.innovv.sdk.model.CameraSettingDefinition
import com.zoloft.innovv.sdk.model.CameraSettings
import com.zoloft.innovv.sdk.model.CaptureResult
import com.zoloft.innovv.sdk.model.CommandResult
import com.zoloft.innovv.sdk.model.DeviceInfo
import com.zoloft.innovv.sdk.model.GpsInfo
import com.zoloft.innovv.sdk.model.LiveViewLinks
import com.zoloft.innovv.sdk.model.MovieInfo
import com.zoloft.innovv.sdk.model.SdCardState
import com.zoloft.innovv.sdk.model.WifiInfo
import com.zoloft.innovv.sdk.model.WifiNetwork
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow

/**
 * Client for the INNOVV K5 local camera API.
 *
 * Create an instance with [InnovvSdk.createClient]. The client is safe to reuse
 * and should be closed when the application no longer needs it.
 *
 * All suspend functions perform network I/O and must be called from a
 * coroutine. Read requests may be retried according to [InnovvConfig.maxRetries];
 * write commands are never retried automatically.
 *
 * The camera control API is unauthenticated plain HTTP. The application is
 * responsible for joining the camera access point and for allowing cleartext
 * traffic. See `docs/platform-support.md`.
 */
public class InnovvClient internal constructor(
    private val config: InnovvConfig,
    internal val transport: HttpTransport,
) : AutoCloseable {
    private val normalizedBaseUrl: String = config.baseUrl.trimEnd('/') + "/"
    private val eventHost: String = Url(normalizedBaseUrl).host

    /** Normalized camera base URL used by this client. */
    public val baseUrl: String get() = normalizedBaseUrl

    /** Sends `cmd=3016`. */
    public suspend fun heartbeat(): CommandResult {
        val xml = readCommand(K5Commands.HEARTBEAT)
        return CommandResult(
            commandId = K5Commands.HEARTBEAT,
            status = requireSuccess(K5Commands.HEARTBEAT, xml),
            rawXml = xml,
        )
    }

    /** Returns the firmware version from `cmd=3012`. */
    public suspend fun getVersion(): String {
        val xml = readCommand(K5Commands.FIRMWARE_VERSION)
        return Xml.textTag(xml, "String")
            ?: throw InnovvParseException("missing <String> in version response")
    }

    /** Returns firmware version and hardware capability from `3012` and `3022`. */
    public suspend fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        firmwareVersion = getVersion(),
        hardwareCapability = getHardwareCapability(),
    )

    /**
     * Returns the raw `cmd=3014` map of command ID to current value.
     *
     * In this response the camera puts the current value in `<Status>`; it is
     * not an error code. Use [CameraSettings] or the typed helpers for a more
     * convenient view.
     */
    public suspend fun getCurrentValues(): Map<Int, Int> {
        val xml = transport.getText(commandUrl(K5Commands.CURRENT_VALUES), retryable = true)
        val values = linkedMapOf<Int, Int>()
        for (function in Xml.blocks(xml, "Function")) {
            val commandId = Xml.intTag(function, "Cmd") ?: continue
            val value = Xml.intTag(function, "Status") ?: continue
            if (value >= 0) values[commandId] = value
        }
        return values
    }

    /** Returns the camera setting menu from `cmd=3031&str=all`. */
    public suspend fun getSettingMenu(): List<CameraSettingDefinition> {
        val xml = transport.getText(commandUrl(K5Commands.SETTING_MENU, text = "all"), retryable = true)
        rejectNegativeStatus(K5Commands.SETTING_MENU, xml)
        return parseSettingMenu(xml)
    }

    /**
     * Returns current values and, by default, the camera setting menu.
     *
     * Set [includeMenu] to `false` for a fast values-only read; `3014` is still a
     * full dump, so cache it and refresh only when needed.
     */
    public suspend fun getSettings(includeMenu: Boolean = true): CameraSettings = CameraSettings(
        values = getCurrentValues(),
        definitions = if (includeMenu) getSettingMenu() else emptyList(),
    )

    /** Sends a numeric setting value (`&par=`) for [commandId]. */
    public suspend fun setSetting(commandId: Int, value: Int): CommandResult =
        writeCommand(commandId, par = value)

    /** Sends a text setting value (`&str=`) for [commandId]. */
    public suspend fun setSetting(commandId: Int, text: String): CommandResult =
        writeCommand(commandId, text = text)

    /** Persists pending settings with `cmd=3021`. */
    public suspend fun saveSettings(): CommandResult = writeCommand(K5Commands.SAVE_SETTINGS)

    /** Changes the camera work mode (`cmd=3001`). */
    public suspend fun setMode(mode: CameraMode): CommandResult =
        writeCommand(K5Commands.CHANGE_MODE, par = mode.code)

    /** Starts recording (`cmd=2001&par=1`). */
    public suspend fun startRecording(): CommandResult =
        writeCommand(K5Commands.RECORD, par = 1)

    /** Stops recording (`cmd=2001&par=0`). */
    public suspend fun stopRecording(): CommandResult =
        writeCommand(K5Commands.RECORD, par = 0)

    /** Captures a photo (`cmd=1001`) and returns the file and free-slot count. */
    public suspend fun capturePhoto(): CaptureResult {
        val xml = writeCommand(K5Commands.CAPTURE_PHOTO).rawXml
        val fileBlock = Xml.blocks(xml, "File").firstOrNull()
        return CaptureResult(
            file = fileBlock?.let { parseCameraFile(it, normalizedBaseUrl) },
            freePictures = Xml.intTag(xml, "FREEPICNUM"),
        )
    }

    /** Formats the SD card (`cmd=3010&par=1`). Destructive. */
    public suspend fun formatSdCard(): CommandResult =
        writeCommand(K5Commands.FORMAT_SD, par = 1)

    /** Resets camera settings (`cmd=3011&par=1`). Destructive. */
    public suspend fun resetSettings(): CommandResult =
        writeCommand(K5Commands.RESET_SETTINGS, par = 1)

    /** Powers the camera off (`cmd=3007&par=4`). */
    public suspend fun powerOff(): CommandResult =
        writeCommand(K5Commands.AUTO_POWER_OFF, par = 4)

    /** Returns the media file list from `cmd=3015`. */
    public suspend fun getFiles(): List<CameraFile> {
        val xml = transport.getText(commandUrl(K5Commands.FILE_LIST), retryable = true)
        rejectNegativeStatus(K5Commands.FILE_LIST, xml)
        return parseCameraFiles(xml, normalizedBaseUrl)
    }

    /** Returns movie metadata from the file's `?custom=1&cmd=4005` URL. */
    public suspend fun getMovieInfo(file: CameraFile): MovieInfo {
        val xml = transport.getText(file.infoUrl, retryable = true)
        requireSuccess(K5Commands.MOVIE_INFO, xml)
        return try {
            parseMovieInfo(xml)
        } catch (failure: IllegalArgumentException) {
            throw InnovvParseException("invalid movie info for ${file.name}", failure)
        }
    }

    /** Returns the JPEG thumbnail (`4001`) for [file]. */
    public suspend fun getThumbnail(file: CameraFile): ByteArray =
        readJpeg(file.thumbnailUrl, "thumbnail")

    /** Returns the larger JPEG preview (`4002`) for [file]. */
    public suspend fun getPreview(file: CameraFile): ByteArray =
        readJpeg(file.previewUrl, "preview")

    /**
     * Streams [file] to [onChunk].
     *
     * The SDK does not buffer the whole file. The callback is suspended while
     * the platform writes each chunk to disk or applies backpressure.
     */
    public suspend fun download(file: CameraFile, onChunk: suspend (ByteArray) -> Unit) {
        transport.stream(file.url, onChunk)
    }

    /** Deletes one file (`cmd=4003`). The camera path is sent as `&str=`. */
    public suspend fun deleteFile(file: CameraFile): CommandResult =
        writeCommand(K5Commands.DELETE_FILE, text = file.path)

    /** Deletes all files (`cmd=4004`). Destructive. */
    public suspend fun deleteAllFiles(): CommandResult =
        writeCommand(K5Commands.DELETE_ALL)

    /** Returns live-view links from `cmd=2019`. */
    public suspend fun getLiveView(): LiveViewLinks {
        val xml = transport.getText(commandUrl(K5Commands.LIVE_VIEW_LINKS), retryable = true)
        rejectNegativeStatus(K5Commands.LIVE_VIEW_LINKS, xml)
        val movie = Xml.textTag(xml, "MovieLiveViewLink")
        val photo = Xml.textTag(xml, "PhotoLiveViewLink")
        if (movie == null && photo == null) {
            throw InnovvParseException("live-view response contained no links")
        }
        return LiveViewLinks(movieUrl = movie, photoUrl = photo)
    }

    /** Returns the AP SSID and passphrase from `cmd=3029`. */
    public suspend fun getWifiInfo(): WifiInfo {
        val xml = transport.getText(commandUrl(K5Commands.WIFI_INFO), retryable = true)
        rejectNegativeStatus(K5Commands.WIFI_INFO, xml)
        val ssid = Xml.textTag(xml, "SSID")
            ?: throw InnovvParseException("missing SSID in Wi-Fi response")
        val passphrase = Xml.textTag(xml, "PASSPHRASE") ?: ""
        return WifiInfo(ssid = ssid, passphrase = passphrase)
    }

    /**
     * Sets the AP SSID and passphrase (`3003`, `3004`) and optionally persists
     * them (`3021`). The camera may restart its access point; reconnect after
     * this call.
     */
    public suspend fun setWifi(
        ssid: String,
        passphrase: String,
        save: Boolean = true,
    ): CommandResult {
        require(ssid.length <= 32) { "SSID must be at most 32 characters" }
        require(passphrase.length in 8..26) { "passphrase must be 8..26 characters" }
        setSetting(K5Commands.SET_WIFI_SSID, ssid)
        val passphraseResult = setSetting(K5Commands.SET_WIFI_PASSPHRASE, passphrase)
        return if (save) saveSettings() else passphraseResult
    }

    /** Reconnects the camera Wi-Fi (`cmd=3018`). */
    public suspend fun reconnectWifi(): CommandResult = writeCommand(K5Commands.RECONNECT_WIFI)

    /** Returns nearby access points from `cmd=3038`. */
    public suspend fun scanWifi(): List<WifiNetwork> {
        val xml = transport.getText(commandUrl(K5Commands.WIFI_SCAN), retryable = true)
        rejectNegativeStatus(K5Commands.WIFI_SCAN, xml)
        return parseWifiNetworks(xml)
    }

    /** Returns the SD-card state from `cmd=3024`. */
    public suspend fun getSdCardState(): SdCardState {
        val value = readValue(K5Commands.SD_CARD_STATE)
        return SdCardState.fromCode(value)
            ?: throw InnovvParseException("unknown SD-card state $value")
    }

    /** Returns free SD-card space in bytes from `cmd=3017`. */
    public suspend fun getFreeSpaceBytes(): Long = readLongValue(K5Commands.FREE_SPACE)

    /** Returns the battery state from `cmd=3019`. */
    public suspend fun getBattery(): BatteryStatus = BatteryStatus(readValue(K5Commands.BATTERY))

    /** Returns the GPS fix from `cmd=8058`. */
    public suspend fun getGpsInfo(): GpsInfo {
        val xml = transport.getText(commandUrl(K5Commands.GPS_INFO), retryable = true)
        rejectNegativeStatus(K5Commands.GPS_INFO, xml)
        return parseGpsInfo(xml)
    }

    /** Returns whether GPS is ready (`cmd=8004`). */
    public suspend fun isGpsReady(): Boolean = readValue(K5Commands.GPS_STATUS) == 1

    /** Returns the raw hardware capability value from `cmd=3022`. */
    public suspend fun getHardwareCapability(): Int = readValue(K5Commands.HARDWARE_CAPABILITY)

    /** Returns the raw mode/status value from `cmd=3037`. */
    public suspend fun getModeStatus(): Int = readValue(K5Commands.MODE_STATUS)

    /** Returns resolutions advertised by `cmd=3030`. */
    public suspend fun getResolutions(): List<CameraResolution> {
        val xml = transport.getText(commandUrl(K5Commands.RESOLUTIONS), retryable = true)
        rejectNegativeStatus(K5Commands.RESOLUTIONS, xml)
        return parseResolutions(xml)
    }

    /** Sets video resolution (`cmd=2002`). */
    public suspend fun setVideoResolution(value: Int): CommandResult =
        setSetting(K5Commands.VIDEO_RESOLUTION, value)

    /** Sets photo resolution (`cmd=1002`). */
    public suspend fun setPhotoResolution(value: Int): CommandResult =
        setSetting(K5Commands.PHOTO_RESOLUTION, value)

    /** Sets loop-recording interval (`cmd=2003`). */
    public suspend fun setLoopRecording(value: Int): CommandResult =
        setSetting(K5Commands.LOOP_RECORDING, value)

    /** Sets the bitrate preset (`cmd=8200`). */
    public suspend fun setBitrate(value: Int): CommandResult =
        setSetting(K5Commands.BITRATE, value)

    /** Sets the Wi-Fi band (`cmd=8201`). */
    public suspend fun setWifiBand(value: Int): CommandResult =
        setSetting(K5Commands.WIFI_BAND, value)

    /** Sets parking mode (`cmd=8309`). */
    public suspend fun setParkingMode(enabled: Boolean): CommandResult =
        setSetting(K5Commands.PARKING_MODE, if (enabled) 1 else 0)

    /** Enables or disables GPS (`cmd=8003`). */
    public suspend fun setGpsEnabled(enabled: Boolean): CommandResult =
        setSetting(K5Commands.GPS_ENABLE, if (enabled) 1 else 0)

    /** Sets microphone volume (`cmd=8306`). */
    public suspend fun setMicVolume(value: Int): CommandResult =
        setSetting(K5Commands.MIC_VOLUME, value)

    /** Sets front and rear mirror modes (`8006`, `8007`). */
    public suspend fun setMirror(front: Int, rear: Int): CommandResult {
        setSetting(K5Commands.FRONT_MIRROR, front)
        return setSetting(K5Commands.REAR_MIRROR, rear)
    }

    /** Sets the movie container format (`cmd=8010`). */
    public suspend fun setMovieFormat(value: Int): CommandResult =
        setSetting(K5Commands.MOVIE_FORMAT, value)

    /** Sets the movie encoder (`cmd=8304`). */
    public suspend fun setEncodeMode(value: Int): CommandResult =
        setSetting(K5Commands.MOVIE_ENCODE, value)

    /** Sets the time-lapse interval (`cmd=8206`). */
    public suspend fun setTimeLapse(value: Int): CommandResult =
        setSetting(K5Commands.TIME_LAPSE, value)

    /** Sets the live-view source (`cmd=3028`). */
    public suspend fun setLiveViewSource(value: Int): CommandResult =
        setSetting(K5Commands.LIVE_VIEW_SOURCE, value)

    /** Sets the Wi-Fi off timer (`cmd=8011`). */
    public suspend fun setWifiOffTimer(value: Int): CommandResult =
        setSetting(K5Commands.WIFI_OFF_TIMER, value)

    /** Sets the video watermark text (`cmd=8219`, `&str=`). */
    public suspend fun setWatermark(text: String): CommandResult =
        setSetting(K5Commands.WATERMARK, text)

    /**
     * Opens the camera event stream on TCP port `3333`.
     *
     * The flow reconnects with exponential backoff when
     * [InnovvConfig.eventReconnect] is `true`. Cancelling the collector closes
     * the socket.
     */
    public fun events(): Flow<CameraEvent> = EventStream(
        host = eventHost,
        port = config.eventPort,
        reconnect = config.eventReconnect,
        logger = config.logger,
    ).events()

    /** Releases the HTTP client. The client must not be used after this call. */
    public override fun close() {
        transport.close()
    }

    private suspend fun readJpeg(url: String, label: String): ByteArray {
        val bytes = transport.getBytes(url)
        if (bytes.size < 2 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            throw InnovvParseException("$label response is not a JPEG")
        }
        return bytes
    }

    private suspend fun readCommand(
        commandId: Int,
        par: Int? = null,
        text: String? = null,
    ): String {
        val xml = transport.getText(commandUrl(commandId, par, text), retryable = true)
        requireSuccess(commandId, xml)
        return xml
    }

    private suspend fun writeCommand(
        commandId: Int,
        par: Int? = null,
        text: String? = null,
    ): CommandResult {
        val xml = transport.getText(commandUrl(commandId, par, text), retryable = false)
        val status = requireSuccess(commandId, xml)
        return CommandResult(commandId = commandId, status = status, rawXml = xml)
    }

    private suspend fun readValue(commandId: Int): Int =
        Xml.intTag(readCommand(commandId), "Value")
            ?: throw InnovvParseException("missing <Value> for command $commandId")

    private suspend fun readLongValue(commandId: Int): Long =
        Xml.longTag(readCommand(commandId), "Value")
            ?: throw InnovvParseException("missing <Value> for command $commandId")

    private fun rejectNegativeStatus(commandId: Int, xml: String) {
        val status = Xml.intTag(xml, "Status") ?: return
        if (status < 0) {
            throw InnovvCommandException(commandId = commandId, status = status, body = xml)
        }
    }

    private fun requireSuccess(commandId: Int, xml: String): Int {
        val status = Xml.intTag(xml, "Status")
            ?: throw InnovvParseException("missing <Status> for command $commandId")
        if (status < 0) {
            throw InnovvCommandException(commandId = commandId, status = status, body = xml)
        }
        return status
    }

    private fun commandUrl(
        commandId: Int,
        par: Int? = null,
        text: String? = null,
    ): String = buildString {
        append(normalizedBaseUrl)
        append("?custom=1&cmd=")
        append(commandId)
        if (par != null) {
            append("&par=")
            append(par)
        }
        if (text != null) {
            append("&str=")
            append(text.encodeURLParameter())
        }
    }
}
