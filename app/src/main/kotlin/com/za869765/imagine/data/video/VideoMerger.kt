package com.za869765.imagine.data.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.za869765.imagine.data.storage.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 長片組合 P3 — 把多段短片「直接串接」成一支長片。
 *
 * 用穩定的 MediaExtractor + MediaMuxer 做 sample-copy(只搬已編碼的封包,不重新編碼),
 * 所以很快、不耗電,但要求各片段「同編碼 + 同解析度」—— 本 App 的影片都來自 xAI Imagine,
 * 通常一致。遇到不一致的片段會丟出例外,UI 顯示「格式不一致」而非產出壞檔。
 *
 * 音軌:以第一段為準。第一段有音軌就一起搬;若後續某段缺音軌/音軌格式不符 → 視為不一致而中止
 * (避免產出半殘音訊)。
 */
object VideoMerger {

    /** 回傳合成檔的 file:// uri 字串;失敗回 null。inputs 需 >= 2 段。 */
    suspend fun merge(ctx: Context, inputs: List<Uri>, prompt: String): String? =
        withContext(Dispatchers.IO) {
            if (inputs.size < 2) return@withContext null
            val out = MediaSaver.newVideoFile(ctx)
            var muxer: MediaMuxer? = null
            try {
                val (videoFmt, audioFmt) = probeFormats(ctx, inputs.first())
                muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val videoDst = muxer.addTrack(videoFmt)
                val audioDst = if (audioFmt != null) muxer.addTrack(audioFmt) else -1
                muxer.start()

                var offsetUs = 0L
                for (uri in inputs) {
                    val vMax = copyTrack(ctx, uri, "video/", muxer, videoDst, offsetUs, expect = videoFmt)
                    val aMax = if (audioDst >= 0) {
                        copyTrack(ctx, uri, "audio/", muxer, audioDst, offsetUs, expect = null)
                    } else 0L
                    // 下一段接在這段之後(留 ~一個 frame 的安全間隔)
                    offsetUs += maxOf(vMax, aMax) + FRAME_GAP_US
                }

                muxer.stop()
                MediaSaver.registerSaved(ctx, out, prompt)
            } catch (_: Throwable) {
                runCatching { if (out.exists()) out.delete() }
                null
            } finally {
                runCatching { muxer?.release() }
            }
        }

    private const val FRAME_GAP_US = 40_000L

    // 探第一段:影像軌必須有(否則丟例外);音軌可有可無。
    private fun probeFormats(ctx: Context, uri: Uri): Pair<MediaFormat, MediaFormat?> {
        val ex = MediaExtractor()
        try {
            ex.setSource(ctx, uri)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (video == null && mime.startsWith("video/")) video = fmt
                else if (audio == null && mime.startsWith("audio/")) audio = fmt
            }
            return Pair(video ?: error("片段沒有影像軌"), audio)
        } finally {
            runCatching { ex.release() }
        }
    }

    // 搬一個 track 的所有封包到 muxer;回傳此段該軌最大 presentationTime(us),供計算下一段位移。
    private fun copyTrack(
        ctx: Context,
        uri: Uri,
        mimePrefix: String,
        muxer: MediaMuxer,
        dstIndex: Int,
        offsetUs: Long,
        expect: MediaFormat?,
    ): Long {
        val ex = MediaExtractor()
        try {
            ex.setSource(ctx, uri)
            var src = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith(mimePrefix)) {
                    src = i; fmt = f; break
                }
            }
            if (src < 0 || fmt == null) {
                error(if (mimePrefix == "video/") "片段沒有影像軌" else "片段音軌不一致，無法直接合成")
            }
            // 影像軌:跟第一段比對 mime/寬/高,不一致就中止(避免產壞檔)
            if (expect != null) {
                val sameMime = expect.getString(MediaFormat.KEY_MIME) == fmt.getString(MediaFormat.KEY_MIME)
                val sameW = optInt(expect, MediaFormat.KEY_WIDTH) == optInt(fmt, MediaFormat.KEY_WIDTH)
                val sameH = optInt(expect, MediaFormat.KEY_HEIGHT) == optInt(fmt, MediaFormat.KEY_HEIGHT)
                if (!sameMime || !sameW || !sameH) error("片段畫面格式/尺寸不一致，無法直接合成")
            }

            ex.selectTrack(src)
            val maxInput = if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                2 * 1024 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInput)
            val info = MediaCodec.BufferInfo()
            var maxPts = 0L
            while (true) {
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = ex.sampleTime
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts + offsetUs
                info.flags = mapFlags(ex.sampleFlags)
                muxer.writeSampleData(dstIndex, buffer, info)
                if (pts > maxPts) maxPts = pts
                ex.advance()
            }
            return maxPts
        } finally {
            runCatching { ex.release() }
        }
    }

    // 內部 file:// 檔用絕對路徑開最穩;其餘走 ContentResolver。
    private fun MediaExtractor.setSource(ctx: Context, uri: Uri) {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) setDataSource(path) else setDataSource(ctx, uri, null)
    }

    private fun optInt(f: MediaFormat, key: String): Int =
        if (f.containsKey(key)) f.getInteger(key) else -1

    private fun mapFlags(extractorFlags: Int): Int {
        var f = 0
        if (extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            f = f or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return f
    }
}
