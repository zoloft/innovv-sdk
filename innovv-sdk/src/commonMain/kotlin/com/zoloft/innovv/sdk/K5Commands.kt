package com.zoloft.innovv.sdk

/**
 * Numeric command IDs used by the K5 camera.
 *
 * Prefer these constants over magic numbers. The camera protocol and the
 * current K5 setting menu are documented in `docs/protocol.md`.
 */
public object K5Commands {
    /** Capture a photo. */
    public const val CAPTURE_PHOTO: Int = 1001

    /** Set photo resolution. */
    public const val PHOTO_RESOLUTION: Int = 1002

    /** Remaining photo slots. */
    public const val FREE_PHOTO_SLOTS: Int = 1003

    /** Start/stop recording (`par=1`/`0`). */
    public const val RECORD: Int = 2001

    /** Set video resolution. */
    public const val VIDEO_RESOLUTION: Int = 2002

    /** Loop-recording interval. */
    public const val LOOP_RECORDING: Int = 2003

    /** HDR/WDR. */
    public const val HDR: Int = 2004

    /** Exposure compensation. */
    public const val EXPOSURE: Int = 2005

    /** Motion detection. */
    public const val MOTION_DETECTION: Int = 2006

    /** Record audio. */
    public const val RECORD_AUDIO: Int = 2007

    /** Date/time stamp. */
    public const val DATE_STAMP: Int = 2008

    /** Maximum recording time at the current bitrate. */
    public const val MAX_RECORD_TIME: Int = 2009

    /** Live-view size. */
    public const val LIVE_VIEW_SIZE: Int = 2010

    /** G-sensor sensitivity. */
    public const val GSENSOR_SENSITIVITY: Int = 2011

    /** Automatic recording. */
    public const val AUTO_RECORDING: Int = 2012

    /** Video bitrate. */
    public const val VIDEO_BITRATE: Int = 2013

    /** Live-view bitrate. */
    public const val LIVE_VIEW_BITRATE: Int = 2014

    /** Start/stop live view. */
    public const val LIVE_VIEW: Int = 2015

    /** Current recording duration. */
    public const val RECORDING_TIME: Int = 2016

    /** Trigger a raw snapshot. */
    public const val TRIGGER_RAW_SNAPSHOT: Int = 2017

    /** Read a raw encoded JPEG. */
    public const val RAW_JPEG: Int = 2018

    /** Read live-view URLs. */
    public const val LIVE_VIEW_LINKS: Int = 2019

    /** Contrast (HTTP) / recording-time event (TCP 3333). */
    public const val CONTRAST: Int = 2020

    /** Change camera mode. */
    public const val CHANGE_MODE: Int = 3001

    /** Query supported commands. */
    public const val QUERY_COMMANDS: Int = 3002

    /** Set Wi-Fi SSID. */
    public const val SET_WIFI_SSID: Int = 3003

    /** Set Wi-Fi passphrase. */
    public const val SET_WIFI_PASSPHRASE: Int = 3004

    /** Set date (`str=yyyy-MM-dd`). */
    public const val SET_DATE: Int = 3005

    /** Set time (`str=HH:mm:ss`). */
    public const val SET_TIME: Int = 3006

    /** Auto power-off. */
    public const val AUTO_POWER_OFF: Int = 3007

    /** UI language. */
    public const val LANGUAGE: Int = 3008

    /** TV output format. */
    public const val TV_FORMAT: Int = 3009

    /** Format the SD card. */
    public const val FORMAT_SD: Int = 3010

    /** Reset settings to defaults. */
    public const val RESET_SETTINGS: Int = 3011

    /** Firmware version. */
    public const val FIRMWARE_VERSION: Int = 3012

    /** Firmware-update trigger. */
    public const val FIRMWARE_UPDATE: Int = 3013

    /** Full current-values dump. */
    public const val CURRENT_VALUES: Int = 3014

    /** Media file list. */
    public const val FILE_LIST: Int = 3015

    /** Heartbeat. */
    public const val HEARTBEAT: Int = 3016

    /** Free SD-card space. */
    public const val FREE_SPACE: Int = 3017

    /** Reconnect Wi-Fi. */
    public const val RECONNECT_WIFI: Int = 3018

    /** Battery level. */
    public const val BATTERY: Int = 3019

    /** Camera-action event (TCP 3333). */
    public const val CAMERA_EVENT: Int = 3020

    /** Save settings. */
    public const val SAVE_SETTINGS: Int = 3021

    /** Hardware capability. */
    public const val HARDWARE_CAPABILITY: Int = 3022

    /** Remove last user. */
    public const val REMOVE_USER: Int = 3023

    /** SD-card state. */
    public const val SD_CARD_STATE: Int = 3024

    /** Download URL. */
    public const val DOWNLOAD_URL: Int = 3025

    /** Firmware-update path. */
    public const val UPDATE_PATH: Int = 3026

    /** Live-view source. */
    public const val LIVE_VIEW_SOURCE: Int = 3028

    /** Wi-Fi SSID/passphrase. */
    public const val WIFI_INFO: Int = 3029

    /** Supported resolutions. */
    public const val RESOLUTIONS: Int = 3030

    /** Setting menu. */
    public const val SETTING_MENU: Int = 3031

    /** Set station SSID and passphrase (`str=SSID:password`). */
    public const val SET_WIFI_STA: Int = 3032

    /** Wi-Fi connect mode. */
    public const val WIFI_CONNECT_MODE: Int = 3033

    /** Diagnostic/auto-test command. */
    public const val AUTO_TEST: Int = 3034

    /** Current mode/status. */
    public const val MODE_STATUS: Int = 3037

    /** Wi-Fi AP scan. */
    public const val WIFI_SCAN: Int = 3038

    /** Car number / custom ID (legacy). */
    public const val CAR_NUMBER: Int = 3041

    /** JPEG thumbnail for a file URL. */
    public const val THUMBNAIL: Int = 4001

    /** Larger JPEG preview for a file URL. */
    public const val PREVIEW: Int = 4002

    /** Delete one file. */
    public const val DELETE_FILE: Int = 4003

    /** Delete all files. */
    public const val DELETE_ALL: Int = 4004

    /** Movie file metadata for a file URL. */
    public const val MOVIE_INFO: Int = 4005

    /** GPS enable. */
    public const val GPS_ENABLE: Int = 8003

    /** GPS status. */
    public const val GPS_STATUS: Int = 8004

    /** Speed unit. */
    public const val SPEED_UNIT: Int = 8005

    /** Front-camera mirror. */
    public const val FRONT_MIRROR: Int = 8006

    /** Rear-camera mirror. */
    public const val REAR_MIRROR: Int = 8007

    /** Protection/status flag. */
    public const val STATUS_FLAG: Int = 8008

    /** Movie container format. */
    public const val MOVIE_FORMAT: Int = 8010

    /** Wi-Fi off timer. */
    public const val WIFI_OFF_TIMER: Int = 8011

    /** GPS information. */
    public const val GPS_INFO: Int = 8058

    /** Bitrate preset. */
    public const val BITRATE: Int = 8200

    /** Wi-Fi band. */
    public const val WIFI_BAND: Int = 8201

    /** Time-lapse recording interval. */
    public const val TIME_LAPSE: Int = 8206

    /** GPS information stamp. */
    public const val GPS_STAMP: Int = 8210

    /** Light frequency. */
    public const val LIGHT_FREQUENCY: Int = 8215

    /** Video watermark text. */
    public const val WATERMARK: Int = 8219

    /** Get car number. */
    public const val GET_CAR_NUMBER: Int = 8228

    /** Get custom string. */
    public const val GET_CUSTOM_STRING: Int = 8229

    /** Movie encoder. */
    public const val MOVIE_ENCODE: Int = 8304

    /** Microphone volume. */
    public const val MIC_VOLUME: Int = 8306

    /** GPS speed. */
    public const val GPS_SPEED: Int = 8307

    /** GPS coordinates. */
    public const val GPS_COORDINATES: Int = 8308

    /** Parking mode. */
    public const val PARKING_MODE: Int = 8309

    /** Contrast. */
    public const val CONTRAST_VALUE: Int = 8311

    /** Saturation. */
    public const val SATURATION: Int = 8312

    /** Sharpness. */
    public const val SHARPNESS: Int = 8313

    /** White balance. */
    public const val WHITE_BALANCE: Int = 8314

    /** Storage capacity. */
    public const val STORAGE_CAPACITY: Int = 8315

    /** Electronic image stabilization. */
    public const val EIS: Int = 8317

    /** Reset the shortcut menu. */
    public const val RESET_SHORTCUT_MENU: Int = 8318

    /** GPS update rate. */
    public const val GPS_RATE: Int = 8320

    /** G-sensor information stamp. */
    public const val GSENSOR_STAMP: Int = 8321

    /** Front raw JPEG snapshot. */
    public const val FRONT_RAW_JPEG: Int = 8322

    /** Rear raw JPEG snapshot. */
    public const val REAR_RAW_JPEG: Int = 8323
}
