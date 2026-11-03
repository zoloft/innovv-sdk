package com.zoloft.innovv.sdk.model

/** One option in a camera setting menu. */
public data class CameraSettingOption(
    /** Value sent as `&par=<index>`. */
    public val index: Int,
    /** Human-readable label returned by the camera. */
    public val label: String,
)

/**
 * A setting definition returned by `cmd=3031&str=all`.
 *
 * @property commandId numeric camera command used to set the value.
 * @property name camera-side menu name, for example `Loop Recording`.
 * @property options available values. The camera's option list may be larger or
 *   smaller than the K5 stock application's bundled menu.
 */
public data class CameraSettingDefinition(
    public val commandId: Int,
    public val name: String,
    public val options: List<CameraSettingOption>,
) {
    /** Returns the option with [index], or `null` when it is not advertised. */
    public fun option(index: Int): CameraSettingOption? = options.firstOrNull { it.index == index }
}

/**
 * Snapshot of the camera's current setting values and optional menu definitions.
 *
 * The values come from `cmd=3014`. In that response the XML `<Status>` field is
 * the current value, not an error code.
 */
public data class CameraSettings(
    /** Current value keyed by camera command ID. */
    public val values: Map<Int, Int>,
    /** Menu definitions, empty when [InnovvClient.getSettings] was called with
     * `includeMenu = false`. */
    public val definitions: List<CameraSettingDefinition> = emptyList(),
) {
    /** Current value for [commandId], or `null` when the camera did not report it. */
    public fun valueOf(commandId: Int): Int? = values[commandId]

    /** Definition for [commandId], or `null` when no menu was requested or the
     * camera does not advertise it. */
    public fun definitionOf(commandId: Int): CameraSettingDefinition? =
        definitions.firstOrNull { it.commandId == commandId }
}
