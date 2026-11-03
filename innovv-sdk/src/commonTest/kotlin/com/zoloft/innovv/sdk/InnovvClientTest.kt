package com.zoloft.innovv.sdk

import com.zoloft.innovv.sdk.internal.HttpTransport
import com.zoloft.innovv.sdk.model.CameraFile
import com.zoloft.innovv.sdk.model.FileType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InnovvClientTest {
    @Test
    fun heartbeatUsesCustomFirst() = runTest {
        var seenUrl = ""
        val client = newClient(
            response = "<Function><Cmd>3016</Cmd><Status>0</Status></Function>",
            onRequest = { seenUrl = it },
        )

        val result = client.heartbeat()

        assertEquals("http://192.168.1.254/?custom=1&cmd=3016", seenUrl)
        assertEquals(0, result.status)
    }

    @Test
    fun currentValuesTreatsStatusAsValue() = runTest {
        val client = newClient(
            response = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Function><Cmd>2002</Cmd><Status>21</Status></Function>
                <Function><Cmd>2003</Cmd><Status>2</Status></Function>
                <Function><Cmd>8003</Cmd><Status>1</Status></Function>
            """.trimIndent(),
        )

        val values = client.getCurrentValues()

        assertEquals(21, values[2002])
        assertEquals(2, values[2003])
        assertEquals(1, values[8003])
    }

    @Test
    fun settingUsesPar() = runTest {
        var seenUrl = ""
        val client = newClient(
            response = "<Function><Cmd>2002</Cmd><Status>0</Status></Function>",
            onRequest = { seenUrl = it },
        )

        val result = client.setSetting(K5Commands.VIDEO_RESOLUTION, 21)

        assertEquals("http://192.168.1.254/?custom=1&cmd=2002&par=21", seenUrl)
        assertEquals(0, result.status)
    }

    @Test
    fun textSettingIsEncoded() = runTest {
        var seenUrl = ""
        val client = newClient(
            response = "<Function><Cmd>8219</Cmd><Status>0</Status></Function>",
            onRequest = { seenUrl = it },
        )

        client.setSetting(K5Commands.WATERMARK, "K5 Camera")

        assertTrue(seenUrl.contains("cmd=8219"))
        assertTrue(seenUrl.contains("str=K5%20Camera"), seenUrl)
    }

    @Test
    fun negativeStatusThrowsCommandException() = runTest {
        val client = newClient(
            response = "<Function><Cmd>2001</Cmd><Status>-8</Status></Function>",
        )

        val failure = assertFailsWith<InnovvCommandException> {
            client.startRecording()
        }
        assertEquals(2001, failure.commandId)
        assertEquals(-8, failure.status)
    }

    @Test
    fun capturePhotoParsesFileAndSlots() = runTest {
        val client = newClient(
            response = """
                <Function><Cmd>1001</Cmd><Status>0</Status>
                <File>
                <NAME>snap.JPG</NAME>
                <FPATH>A:\NOVATEK\PHOTO\snap.JPG</FPATH>
                </File>
                <FREEPICNUM>7</FREEPICNUM>
                </Function>
            """.trimIndent(),
        )

        val result = client.capturePhoto()

        assertEquals("snap.JPG", result.file?.name)
        assertEquals(
            "http://192.168.1.254/NOVATEK/PHOTO/snap.JPG",
            result.file?.url,
        )
        assertEquals(7, result.freePictures)
    }

    @Test
    fun wifiInfoParsesCdata() = runTest {
        val client = newClient(
            response = """
                <LIST>
                <SSID><![CDATA[K5 Camera]]></SSID>
                <PASSPHRASE><![CDATA[12345678]]></PASSPHRASE>
                </LIST>
            """.trimIndent(),
        )

        val info = client.getWifiInfo()

        assertEquals("K5 Camera", info.ssid)
        assertEquals("12345678", info.passphrase)
    }

    @Test
    fun liveViewReadsLinks() = runTest {
        val client = newClient(
            response = """
                <LIST>
                <MovieLiveViewLink>rtsp://192.168.1.254/xxx.mov</MovieLiveViewLink>
                <PhotoLiveViewLink>http://192.168.1.254:8192</PhotoLiveViewLink>
                </LIST>
            """.trimIndent(),
        )

        val links = client.getLiveView()

        assertEquals("rtsp://192.168.1.254/xxx.mov", links.movieUrl)
        assertEquals("http://192.168.1.254:8192", links.photoUrl)
    }

    @Test
    fun movieInfoUsesFileUrl() = runTest {
        var seenUrl = ""
        val client = newClient(
            response = """
                <Function><Cmd>4005</Cmd><Status>0</Status>
                <String>Width:1920, Height:1080, Length:60 sec</String></Function>
            """.trimIndent(),
            onRequest = { seenUrl = it },
        )

        val info = client.getMovieInfo(
            CameraFile(
                name = "clip.mp4",
                path = "A:\\NOVATEK\\MOVIE\\clip.mp4",
                relativePath = "NOVATEK/MOVIE/clip.mp4",
                url = "http://192.168.1.254/NOVATEK/MOVIE/clip.mp4",
                sizeBytes = 1,
                timecodeEpochSeconds = 1,
                timeText = "",
                attributes = 0,
                type = FileType.VIDEO,
            ),
        )

        assertEquals(
            "http://192.168.1.254/NOVATEK/MOVIE/clip.mp4?custom=1&cmd=4005",
            seenUrl,
        )
        assertEquals(1920, info.width)
        assertEquals(60, info.lengthSeconds)
    }

    @Test
    fun thumbnailRejectsNonJpeg() = runTest {
        val client = newClient(
            response = "<Function><Cmd>4001</Cmd><Status>-1</Status></Function>",
        )

        assertFailsWith<InnovvParseException> {
            client.getThumbnail(
                CameraFile(
                    name = "clip.mp4",
                    path = "A:\\NOVATEK\\MOVIE\\clip.mp4",
                    relativePath = "NOVATEK/MOVIE/clip.mp4",
                    url = "http://192.168.1.254/NOVATEK/MOVIE/clip.mp4",
                    sizeBytes = 1,
                    timecodeEpochSeconds = 1,
                    timeText = "",
                    attributes = 0,
                    type = FileType.VIDEO,
                ),
            )
        }
    }

    private fun newClient(
        response: String,
        onRequest: (String) -> Unit = {},
    ): InnovvClient {
        val config = InnovvConfig()
        val engine = MockEngine { request ->
            onRequest(request.url.toString())
            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/xml"),
            )
        }
        return InnovvClient(
            config = config,
            transport = HttpTransport(HttpClient(engine), config),
        )
    }
}
