package com.example

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioExtractor {
    private const val TAG = "AudioExtractor"

    fun extractAudio(context: Context, videoUri: Uri, outputFile: File, onProgress: (String) -> Unit): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            onProgress("جاري فحص ملف الفيديو المختار...")
            extractor.setDataSource(context, videoUri, null)
            
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            var mime: String? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    mime = trackMime
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                onProgress("خطأ: لم يتم العثور على أي مسار صوتي داخل هذا الفيديو!")
                return false
            }

            onProgress("تم العثور على صوت بصيغة ($mime). جاري تهيئة المستخرج...")
            extractor.selectTrack(audioTrackIndex)

            // Create Muxer. We save to MPEG-4 format since .m4a is highly compatible and lightweight
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            onProgress("جاري استخراج المسار الصوتي محلياً بالكامل وبسرعة عالية...")
            var totalBytesExtracted = 0
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                
                muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                totalBytesExtracted += bufferInfo.size
                
                extractor.advance()
            }

            onProgress("اكتمل استخراج الصوت محلياً! الحجم الكلي المستخرج: ${totalBytesExtracted / 1024} كيلوبايت.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio", e)
            onProgress("فشل استخراج الصوت محلياً: ${e.localizedMessage}. سنحاول استخدام تهيئات بديلة...")
            return false
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }
}
