package com.example

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun generateArabicSubtitles(
        audioFile: File,
        apiKey: String,
        onProgress: (String) -> Unit
    ): String? {
        try {
            onProgress("تحضير ملف الصوت للإرسال...")
            if (!audioFile.exists()) {
                onProgress("خطأ: لم يتم العثور على ملف الصوت المستخرج.")
                return null
            }

            val audioBytes = audioFile.readBytes()
            if (audioBytes.isEmpty()) {
                onProgress("خطأ: ملف الصوت فارغ.")
                return null
            }

            onProgress("تشفير الصوت بصيغة Base64...")
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            onProgress("إعداد طلب الترجمة والنسخ الصوتي...")
            
            // Build the JSON request body using standard org.json API
            val requestJson = JSONObject()
            
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Part 1: The detailed, bilingual prompt to translate to Arabic
            val promptPart = JSONObject()
            val promptText = """
You are an expert, professional subtitler and audio translator.
Analyze the attached audio and generate extremely accurate Arabic subtitles.
IMPORTANT instructions:
1. Translate the spoken content from its original language (which could be English, French, Spanish, Hindi, Arabic, or any other language) directly into high-quality, modern, natural Arabic (العربية الفصحى المبسطة) or natural sounding phrasing.
2. Format the output strictly as a SubRip (.srt) subtitle file.
3. Each subtitle block must follow standard SubRip formatting:
   [Index]
   [Start Timestamp] --> [End Timestamp]
   [Arabic Subtitle Text]
   
   Example:
   1
   00:00:01,120 --> 00:00:04,500
   مرحباً بكم في هذا الفيديو التعليمي.
   
4. Timestamps must be highly synchronized with the speech in the audio.
5. Return ONLY the raw SRT content. Do not wrap the output in markdown code blocks like ```srt or ```, do not write any explanations, do not include any introductory or concluding text. Just start directly with the first subtitle index "1".
""".trimIndent()
            promptPart.put("text", promptText)
            partsArray.put(promptPart)

            // Part 2: The audio inline data
            val audioPart = JSONObject()
            val inlineDataObj = JSONObject()
            inlineDataObj.put("mimeType", "audio/mp4")
            inlineDataObj.put("data", base64Audio)
            audioPart.put("inlineData", inlineDataObj)
            partsArray.put(audioPart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            // Add optional configuration for creativity restraint
            val generationConfig = JSONObject()
            generationConfig.put("temperature", 0.3)
            requestJson.put("generationConfig", generationConfig)

            val requestBodyString = requestJson.toString()
            onProgress("جاري إرسال الملف إلى خادم الذكاء الاصطناعي (Gemini)... قد يستغرق ذلك دقيقة أو دقيقتين اعتماداً على طول الفيديو وسرعة الإنترنت.")

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBodyString.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: ${response.code} - $responseBody")
                    onProgress("فشل الاتصال بخادم الذكاء الاصطناعي. رمز الخطأ: ${response.code}")
                    return null
                }

                if (responseBody == null) {
                    onProgress("خطأ: تلقينا استجابة فارغة من الخادم.")
                    return null
                }

                onProgress("جاري تحليل استجابة خادم الذكاء الاصطناعي وترتيب الترجمة...")
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    onProgress("خطأ: لم يتمكن الذكاء الاصطناعي من إنتاج ترجمة لهذا المقطع.")
                    return null
                }

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                if (content == null) {
                    onProgress("خطأ: استجابة خادم الذكاء الاصطناعي غير مكتملة.")
                    return null
                }

                val parts = content.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    onProgress("خطأ: لم يتم العثور على نصوص في الاستجابة.")
                    return null
                }

                val text = parts.getJSONObject(0).optString("text")
                if (text.isNullOrBlank()) {
                    onProgress("خطأ: الترجمة الناتجة فارغة.")
                    return null
                }

                onProgress("تم توليد الترجمة بنجاح!")
                
                // Clean markdown code block wraps if the model ignored instructions
                var cleanedSrt = text.trim()
                if (cleanedSrt.startsWith("```")) {
                    cleanedSrt = cleanedSrt.substringAfter("\n")
                }
                if (cleanedSrt.endsWith("```")) {
                    cleanedSrt = cleanedSrt.substringBeforeLast("```")
                }
                return cleanedSrt.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation", e)
            onProgress("حدث خطأ أثناء الاتصال: ${e.localizedMessage}")
            return null
        }
    }
}
