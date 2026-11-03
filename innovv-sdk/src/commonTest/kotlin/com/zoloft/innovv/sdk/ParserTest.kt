package com.zoloft.innovv.sdk

import com.zoloft.innovv.sdk.internal.parseCameraFiles
import com.zoloft.innovv.sdk.internal.parseEvent
import com.zoloft.innovv.sdk.internal.parseGpsInfo
import com.zoloft.innovv.sdk.internal.parseMovieInfo
import com.zoloft.innovv.sdk.internal.parseResolutions
import com.zoloft.innovv.sdk.internal.parseSettingMenu
import com.zoloft.innovv.sdk.internal.parseWifiNetworks
import com.zoloft.innovv.sdk.model.CameraEvent
import com.zoloft.innovv.sdk.model.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun parsesCameraFiles() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <LIST>
            <ALLFile><File>
            <NAME>2021_0101_120000_001.MP4</NAME>
            <FPATH>A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4</FPATH>
            <SIZE>123456789</SIZE>
            <TIMECODE>1609459200</TIMECODE>
            <TIME>2021/01/01 12:00:00</TIME>
            <ATTR>0</ATTR></File>
            </ALLFile>
            </LIST>
        """.trimIndent()

        val files = parseCameraFiles(xml, "http://192.168.1.254/")
        assertEquals(1, files.size)
        val file = files.single()
        assertEquals("2021_0101_120000_001.MP4", file.name)
        assertEquals("A:\\NOVATEK\\MOVIE\\2021_0101_120000_001.MP4", file.path)
        assertEquals("NOVATEK/MOVIE/2021_0101_120000_001.MP4", file.relativePath)
        assertEquals("http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4", file.url)
        assertEquals(123456789L, file.sizeBytes)
        assertEquals(1609459200L, file.timecodeEpochSeconds)
        assertEquals(FileType.VIDEO, file.type)
        assertEquals(file.url + "?custom=1&cmd=4001", file.thumbnailUrl)
        assertEquals(file.url + "?custom=1&cmd=4005", file.infoUrl)
    }

    @Test
    fun parsesSettingMenu() {
        val xml = """
            <LIST><Item>
            <Cmd>2003</Cmd><Name>Loop Recording</Name>
            <MenuList>
            <Option><Index>0</Index><Id>OFF</Id></Option>
            <Option><Index>1</Index><Id>1 Minute</Id></Option>
            </MenuList>
            </Item></LIST>
        """.trimIndent()

        val menu = parseSettingMenu(xml)
        assertEquals(1, menu.size)
        assertEquals(2003, menu.single().commandId)
        assertEquals("Loop Recording", menu.single().name)
        assertEquals(2, menu.single().options.size)
        assertEquals("1 Minute", menu.single().option(1)?.label)
    }

    @Test
    fun parsesWifiScan() {
        val xml = """
            <List><AP_index>
            <SSID>Camera</SSID>
            <Auth_type>3</Auth_type>
            <RSSI>-55 dBm</RSSI>
            </AP_index></List>
        """.trimIndent()

        val networks = parseWifiNetworks(xml)
        assertEquals(1, networks.size)
        assertEquals("Camera", networks.single().ssid)
        assertEquals(3, networks.single().authType)
        assertEquals(-55, networks.single().rssiDbm)
    }

    @Test
    fun parsesGpsInfo() {
        val xml = """
            <Function>
            <timestamp>1609459200</timestamp><lng>12.5</lng>
            <lat>45.25</lat><alt>100.5</alt><spd>42.0</spd>
            <SAT><SatNum>8</SatNum><SNR>31</SNR></SAT>
            </Function>
        """.trimIndent()

        val gps = parseGpsInfo(xml)
        assertEquals(1609459200L, gps.timestampEpochSeconds)
        assertEquals(12.5, gps.longitude)
        assertEquals(45.25, gps.latitude)
        assertEquals(8, gps.satellites)
        assertTrue(gps.hasFix)
    }

    @Test
    fun parsesMovieInfo() {
        val xml = """
            <Function><Cmd>4005</Cmd><Status>0</Status>
            <String>Width:1920, Height:1080, Length:60 sec</String></Function>
        """.trimIndent()

        val info = parseMovieInfo(xml)
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(60, info.lengthSeconds)
    }

    @Test
    fun parsesResolutions() {
        val xml = """
            <LIST><Item>
            <Name>1080P30</Name><Index>11</Index>
            <Size>1920*1080</Size><FrameRate>30</FrameRate><Type>0</Type>
            </Item></LIST>
        """.trimIndent()

        val resolutions = parseResolutions(xml)
        assertEquals(1, resolutions.size)
        assertEquals(11, resolutions.single().index)
        assertEquals(1920, resolutions.single().width)
        assertEquals(1080, resolutions.single().height)
        assertEquals(30, resolutions.single().frameRate)
    }

    @Test
    fun parsesEvents() {
        assertEquals(
            CameraEvent.RecordingTime(42),
            parseEvent("<Function><Cmd>2020</Cmd><Status>42</Status></Function>"),
        )
        assertEquals(
            CameraEvent.RecordStarted,
            parseEvent("<Function><Cmd>3020</Cmd><Status>1</Status></Function>"),
        )
        assertEquals(
            CameraEvent.CardRemoved,
            parseEvent("<Function><Cmd>3020</Cmd><Status>10</Status></Function>"),
        )
        assertEquals(
            CameraEvent.BatteryChanged(5),
            parseEvent("<Function><Cmd>9019</Cmd><Status>5</Status></Function>"),
        )
        assertNotNull(parseEvent("<Function><Cmd>9999</Cmd><Status>7</Status></Function>"))
    }
}
