package com.zoloft.innovv.sdk.internal

import com.zoloft.innovv.sdk.model.CameraEvent
import com.zoloft.innovv.sdk.model.CameraFile
import com.zoloft.innovv.sdk.model.CameraResolution
import com.zoloft.innovv.sdk.model.CameraSettingDefinition
import com.zoloft.innovv.sdk.model.CameraSettingOption
import com.zoloft.innovv.sdk.model.FileType
import com.zoloft.innovv.sdk.model.GpsInfo
import com.zoloft.innovv.sdk.model.MovieInfo
import com.zoloft.innovv.sdk.model.WifiNetwork

internal fun parseCameraFile(block: String, baseUrl: String): CameraFile? {
    val path = Xml.textTag(block, "FPATH") ?: return null
    val relativePath = path
        .removePrefix("A:")
        .replace('\\', '/')
        .trimStart('/')
    if (relativePath.isEmpty()) return null

    val name = Xml.textTag(block, "NAME")
        ?: relativePath.substringAfterLast('/')
    val url = baseUrl.trimEnd('/') + "/" + relativePath
    val extension = relativePath.substringAfterLast('.', "").lowercase()
    val type = when (extension) {
        "mp4", "mov", "ts" -> FileType.VIDEO
        "jpg", "jpeg" -> FileType.PHOTO
        else -> FileType.UNKNOWN
    }

    return CameraFile(
        name = name,
        path = path,
        relativePath = relativePath,
        url = url,
        sizeBytes = Xml.longTag(block, "SIZE") ?: 0L,
        timecodeEpochSeconds = Xml.longTag(block, "TIMECODE") ?: 0L,
        timeText = Xml.textTag(block, "TIME") ?: "",
        attributes = Xml.intTag(block, "ATTR") ?: 0,
        type = type,
    )
}

internal fun parseCameraFiles(xml: String, baseUrl: String): List<CameraFile> =
    Xml.blocks(xml, "File").mapNotNull { parseCameraFile(it, baseUrl) }

internal fun parseSettingMenu(xml: String): List<CameraSettingDefinition> =
    Xml.blocks(xml, "Item").mapNotNull { item ->
        val commandId = Xml.intTag(item, "Cmd") ?: return@mapNotNull null
        val name = Xml.textTag(item, "Name") ?: ""
        val options = Xml.blocks(item, "Option").mapNotNull { option ->
            val index = Xml.intTag(option, "Index") ?: return@mapNotNull null
            val label = Xml.textTag(option, "Id") ?: ""
            CameraSettingOption(index = index, label = label)
        }
        CameraSettingDefinition(
            commandId = commandId,
            name = name,
            options = options,
        )
    }

internal fun parseWifiNetworks(xml: String): List<WifiNetwork> =
    Xml.blocks(xml, "AP_index").mapNotNull { block ->
        val ssid = Xml.textTag(block, "SSID") ?: return@mapNotNull null
        val authType = Xml.intTag(block, "Auth_type") ?: 0
        val rssiText = Xml.textTag(block, "RSSI") ?: ""
        val rssi = Regex("-?\\d+").find(rssiText)?.value?.toIntOrNull() ?: 0
        WifiNetwork(ssid = ssid, authType = authType, rssiDbm = rssi)
    }

internal fun parseGpsInfo(xml: String): GpsInfo = GpsInfo(
    timestampEpochSeconds = Xml.longTag(xml, "timestamp") ?: 0L,
    longitude = Xml.doubleTag(xml, "lng") ?: 0.0,
    latitude = Xml.doubleTag(xml, "lat") ?: 0.0,
    altitudeMeters = Xml.doubleTag(xml, "alt") ?: 0.0,
    speed = Xml.doubleTag(xml, "spd") ?: 0.0,
    satellites = Xml.intTag(xml, "SatNum") ?: 0,
    snr = Xml.intTag(xml, "SNR") ?: 0,
)

internal fun parseMovieInfo(xml: String): MovieInfo {
    val value = Xml.textTag(xml, "String")
        ?: throw IllegalArgumentException("missing movie info <String>")
    val fields = value.split(',').associate { part ->
        val key = part.substringBefore(':', "").trim()
        val raw = part.substringAfter(':', "").trim().substringBefore(' ')
        key.lowercase() to raw
    }
    return MovieInfo(
        width = fields["width"]?.toIntOrNull() ?: 0,
        height = fields["height"]?.toIntOrNull() ?: 0,
        lengthSeconds = fields["length"]?.toIntOrNull() ?: 0,
    )
}

internal fun parseResolutions(xml: String): List<CameraResolution> =
    Xml.blocks(xml, "Item").mapNotNull { item ->
        val index = Xml.intTag(item, "Index") ?: return@mapNotNull null
        val size = Xml.textTag(item, "Size") ?: ""
        val width = size.substringBefore('*', "").toIntOrNull() ?: 0
        val height = size.substringAfter('*', "").toIntOrNull() ?: 0
        CameraResolution(
            name = Xml.textTag(item, "Name") ?: "",
            index = index,
            width = width,
            height = height,
            frameRate = Xml.intTag(item, "FrameRate") ?: 0,
            type = Xml.intTag(item, "Type") ?: 0,
        )
    }

internal fun parseEvent(xml: String): CameraEvent? {
    val commandId = Xml.intTag(xml, "Cmd") ?: return null
    val status = Xml.intTag(xml, "Status") ?: return null
    return when (commandId) {
        2020 -> CameraEvent.RecordingTime(status)
        9019 -> CameraEvent.BatteryChanged(status)
        3020 -> when (status) {
            1 -> CameraEvent.RecordStarted
            2 -> CameraEvent.RecordStopped
            8 -> CameraEvent.SensorCountChanged
            9 -> CameraEvent.CardInserted
            10 -> CameraEvent.CardRemoved
            11 -> CameraEvent.ProtectionChanged(true)
            12 -> CameraEvent.ProtectionChanged(false)
            13 -> CameraEvent.GpsChanged(true)
            14 -> CameraEvent.GpsChanged(false)
            16 -> CameraEvent.PhotoDownloaded
            21 -> CameraEvent.TimelapseChanged(true)
            22 -> CameraEvent.TimelapseChanged(false)
            else -> CameraEvent.Unknown(commandId, status)
        }
        else -> CameraEvent.Unknown(commandId, status)
    }
}
