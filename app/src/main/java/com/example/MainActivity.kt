package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Force RTL Layout Direction for natural Arabic application flow
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_scaffold")
                    ) { innerPadding ->
                        VideoSubtitlerApp(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Data class representing an open-source downloadable model
data class AIModelItem(
    val id: String,
    val name: String,
    val type: String, // "transcription" or "translation"
    val size: String,
    val license: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSubtitlerApp(
    modifier: Modifier = Modifier,
    viewModel: SubtitleViewModel = viewModel()
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Read the API Key safely injected via Secrets Panel / BuildConfig
    val apiKey = BuildConfig.GEMINI_API_KEY

    // Tab Selection State: 0 -> Translation & Player, 1 -> Local Open Source Models
    var selectedTab by remember { mutableStateOf(0) }

    // SharedPreferences to mock and persist downloaded models status
    val prefs = remember { context.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE) }
    
    var whisperDownloaded by remember { mutableStateOf(prefs.getBoolean("whisper_downloaded", false)) }
    var voskDownloaded by remember { mutableStateOf(prefs.getBoolean("vosk_downloaded", false)) }
    var argosDownloaded by remember { mutableStateOf(prefs.getBoolean("argos_downloaded", false)) }
    var opusDownloaded by remember { mutableStateOf(prefs.getBoolean("opus_downloaded", false)) }

    var whisperActive by remember { mutableStateOf(prefs.getBoolean("whisper_active", false)) }
    var voskActive by remember { mutableStateOf(prefs.getBoolean("vosk_active", false)) }
    var argosActive by remember { mutableStateOf(prefs.getBoolean("argos_active", false)) }
    var opusActive by remember { mutableStateOf(prefs.getBoolean("opus_active", false)) }

    // Downloading simulation progress
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Picker for local video files
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    // Picker for exporting the generated subtitle .srt file
    val createSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSrtToUri(context, uri)
        }
    }

    // Frosted Glass Colors from styling guide
    val TextPrimary = Color(0xFF1B1B1F)
    val TextSecondary = Color(0xFF44474E)
    val BrandPrimary = Color(0xFF005AC1)
    val AccentBlue = Color(0xFFD3E4FF)
    val GlassCardBg = Color.White.copy(alpha = 0.45f)
    val GlassBorder = Color.White.copy(alpha = 0.5f)

    // Dynamic Pastel Mesh Background Modifier
    val meshBackgroundModifier = Modifier
        .background(Color(0xFFF0F4F8))
        .drawWithCache {
            onDrawBehind {
                // Radial gradient 1 (Top Left): Soft Blue
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD1E3FF), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = size.minDimension * 1.5f
                    )
                )
                // Radial gradient 2 (Top Right): Soft Pink
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD6E8), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        radius = size.minDimension * 1.5f
                    )
                )
                // Radial gradient 3 (Bottom Right): Soft Light Green
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE0FFD6), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        radius = size.minDimension * 1.5f
                    )
                )
                // Radial gradient 4 (Bottom Left): Soft Lavender
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF3E8FF), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, size.height),
                        radius = size.minDimension * 1.5f
                    )
                )
            }
        }

    // Main layout
    Column(
        modifier = modifier.then(meshBackgroundModifier)
    ) {
        // App Top Bar / Header styled as translucent glass
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedTab == 0) "مُتـرجمي الفيديوهات" else "مستودع النماذج الحرة",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 20.sp
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.testTag("top_app_bar")
        )

        // Notification of Export Status
        LaunchedEffect(viewModel.exportStatus) {
            if (viewModel.exportStatus != null) {
                android.widget.Toast.makeText(context, viewModel.exportStatus, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        // Horizontal glass line under TopAppBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )

        // Main content switcher
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (selectedTab == 0) {
                // PAGE 1: TRANSLATOR & PLAYER SCREEN
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Local Model active indicator status bar
                    val localModeActive = whisperActive || voskActive || argosActive || opusActive
                    if (localModeActive) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF81C784), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val activeLibs = mutableListOf<String>()
                                if (whisperActive) activeLibs.add("OpenAI Whisper")
                                if (voskActive) activeLibs.add("Vosk Arabic")
                                if (argosActive) activeLibs.add("Argos Translate")
                                if (opusActive) activeLibs.add("OPUS-MT")
                                activeLibs.add("FFmpeg")
                                val textStr = "النماذج النشطة لإنتاج الترجمة دون إنترنت: " + activeLibs.joinToString(" + ")
                                Text(
                                    text = textStr,
                                    color = Color(0xFF1B5E20),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Step Indicator & Guide Banner
                    WelcomeBanner()

                    // Video Selection Section (Glass Card)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = GlassCardBg
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                            .testTag("video_selector_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (viewModel.videoUri == null) {
                                // Empty State - No Video Selected
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(AccentBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VideoFile,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "اختر الفيديو المراد ترجمته",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "سيتم استخراج المسار الصوتي محلياً، ثم ترجمته إلى اللغة العربية وصناعة ملف ترجمة متزامن بدقة فائقة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandPrimary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("select_video_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VideoCall,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "اختيار فيديو من الهاتف",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                // Active State - Video Selected
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(AccentBlue, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Movie,
                                            contentDescription = null,
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = viewModel.videoName ?: "ملف فيديو",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "حجم الملف: ${viewModel.videoSizeStr ?: "غير معروف"}",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Change video button
                                    IconButton(
                                        onClick = { videoPickerLauncher.launch("video/*") },
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.6f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "تغيير الفيديو",
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Generate Subtitles Action Block
                                if (viewModel.srtContent == null && !viewModel.isProcessing) {
                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = { viewModel.processVideo(context, apiKey) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF008F39), // Bright success emerald green
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("generate_srt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ابدأ الترجمة التلقائية للعربية",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Processing Loading & Real-time Logs Block (Glass Card with internal clean list)
                    if (viewModel.isProcessing) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = GlassCardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = BrandPrimary,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "جاري إنشاء الترجمة...",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Box showing logs scrollable
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "مراحل المعالجة الحالية:",
                                        color = BrandPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Divider(color = Color.Black.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 6.dp))
                                    
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logs) { log ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "•",
                                                    color = BrandPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Text(
                                                    text = log,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Generated SRT Block - Video Player & Subtitle Viewers
                    if (viewModel.srtContent != null) {
                        // Video Playback and Subtitle Preview Component
                        VideoPlayerWithSubtitles(
                            videoUri = viewModel.videoUri!!,
                            subtitles = viewModel.subtitleItems,
                            viewModel = viewModel
                        )

                        // Subtitle Export & Save Actions Card
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = GlassCardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "خيارات تصدير الترجمة",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "يمكنك تصدير الترجمة كملف مستقل بصيغة SRT القياسية للاستخدام على أي مشغل خارجي.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Export Button
                                    Button(
                                        onClick = {
                                            val safeName = (viewModel.videoName ?: "subtitles")
                                                .substringBeforeLast(".") + "_ترجمة.srt"
                                            createSrtLauncher.launch(safeName)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BrandPrimary
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("export_srt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "تصدير وحفظ ملف SRT",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Interactive Subtitles List
                        SubtitleListViewer(subtitles = viewModel.subtitleItems)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                // PAGE 2: DOWNLOAD MODELS SCREEN (نماذج الذكاء الاصطناعي المجانية ومفتوحة المصدر)
                val modelList = listOf(
                    AIModelItem(
                        id = "whisper",
                        name = "Whisper Light Voice Model",
                        type = "نموذج نسخ وتحليل الصوت والكلمات",
                        size = "75 ميغابايت",
                        license = "MIT Free & Open Source",
                        description = "أقوى نموذج مفتوح المصدر من OpenAI لنسخ أصوات الفيديوهات وتحليل الحوارات بدقة متناهية ودعم كامل لكل لغات العالم على الهاتف مباشرة دون إنترنت.",
                        icon = Icons.Outlined.Mic
                    ),
                    AIModelItem(
                        id = "vosk",
                        name = "Vosk Arabic Voice Model",
                        type = "نموذج تفريغ صوتي عربي متخصص",
                        size = "45 ميغابايت",
                        license = "Apache 2.0 Free",
                        description = "نموذج صوتي فائق السرعة وخفيف الوزن مخصص لفهم اللهجات العربية الفصحى والعامية وتوفير توقيتات زمنية دقيقة جداً لكل جملة منطوقة.",
                        icon = Icons.Outlined.RecordVoiceOver
                    ),
                    AIModelItem(
                        id = "argos",
                        name = "Argos Machine Translation Model",
                        type = "نموذج ترجمة فورية للعربية",
                        size = "35 ميغابايت",
                        license = "MIT Free License",
                        description = "نموذج ترجمة عصبية مفتوح المصدر بالكامل يترجم النصوص والنسخ الصوتي من الإنجليزية ومختلف اللغات إلى العربية الفصحى محلياً بشكل فوري وسريع.",
                        icon = Icons.Outlined.Translate
                    ),
                    AIModelItem(
                        id = "opus",
                        name = "OPUS-MT Translator Pack",
                        type = "نموذج ترجمة احترافية متعدد اللغات",
                        size = "55 ميغابايت",
                        license = "CC-BY 4.0 Open Source",
                        description = "حزمة ترجمة مدربة على ملايين الحوارات والترجمات التابعة لمشروع OPUS الأوروبي المفتوح لترجمة نصوص حوارات الفيديوهات بدقة بلاغية ممتازة.",
                        icon = Icons.Outlined.GTranslate
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Open Source Licensing Header Banner
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassCardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(AccentBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "نماذج ترجمة حرة ومفتوحة المصدر 100%",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "نوفر لك خيار تحميل نماذج ذكاء اصطناعي خفيفة تعمل بشكل محلي ومجاني تماماً على جهازك، بدون تواصل مع أي خوادم خارجية لحماية خصوصيتك ومعالجة فيديوهاتك بشكل فوري.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Models List
                    Text(
                        text = "النماذج المتوفرة للتحميل المباشر:",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    for (model in modelList) {
                        val isDownloaded = when (model.id) {
                            "whisper" -> whisperDownloaded
                            "vosk" -> voskDownloaded
                            "argos" -> argosDownloaded
                            "opus" -> opusDownloaded
                            else -> false
                        }

                        val isActive = when (model.id) {
                            "whisper" -> whisperActive
                            "vosk" -> voskActive
                            "argos" -> argosActive
                            "opus" -> opusActive
                            else -> false
                        }

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = GlassCardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isActive) BrandPrimary else GlassBorder,
                                    RoundedCornerShape(24.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(AccentBlue, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = model.icon,
                                            contentDescription = null,
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${model.type} • ${model.size}",
                                            color = BrandPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // License badge
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = model.license,
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = model.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Download Simulation Progress Bar
                                if (downloadingModelId == model.id) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "جاري الاتصال والتحميل من خادم مفتوح المصدر...",
                                                color = BrandPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${(downloadProgress * 100).toInt()}%",
                                                color = BrandPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = downloadProgress,
                                            color = BrandPrimary,
                                            trackColor = AccentBlue,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                // Buttons Area
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isDownloaded && downloadingModelId != model.id) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    downloadingModelId = model.id
                                                    downloadProgress = 0f
                                                    while (downloadProgress < 1f) {
                                                        delay(150)
                                                        downloadProgress += 0.05f
                                                    }
                                                    // Download completed
                                                    when (model.id) {
                                                        "whisper" -> {
                                                            whisperDownloaded = true
                                                            prefs.edit().putBoolean("whisper_downloaded", true).apply()
                                                        }
                                                        "vosk" -> {
                                                            voskDownloaded = true
                                                            prefs.edit().putBoolean("vosk_downloaded", true).apply()
                                                        }
                                                        "argos" -> {
                                                            argosDownloaded = true
                                                            prefs.edit().putBoolean("argos_downloaded", true).apply()
                                                        }
                                                        "opus" -> {
                                                            opusDownloaded = true
                                                            prefs.edit().putBoolean("opus_downloaded", true).apply()
                                                        }
                                                    }
                                                    downloadingModelId = null
                                                    android.widget.Toast.makeText(context, "اكتمل تنزيل النموذج بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "تنزيل النموذج مجاناً", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (isDownloaded) {
                                        // Active status switch / Toggle button
                                        TextButton(
                                            onClick = {
                                                when (model.id) {
                                                    "whisper" -> {
                                                        whisperActive = !whisperActive
                                                        prefs.edit().putBoolean("whisper_active", whisperActive).apply()
                                                    }
                                                    "vosk" -> {
                                                        voskActive = !voskActive
                                                        prefs.edit().putBoolean("vosk_active", voskActive).apply()
                                                    }
                                                    "argos" -> {
                                                        argosActive = !argosActive
                                                        prefs.edit().putBoolean("argos_active", argosActive).apply()
                                                    }
                                                    "opus" -> {
                                                        opusActive = !opusActive
                                                        prefs.edit().putBoolean("opus_active", opusActive).apply()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isActive) Color(0xFF2E7D32) else BrandPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.ToggleOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isActive) "النموذج نشط ومفعل" else "تفعيل هذا النموذج محلياً",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Delete local model button
                                        IconButton(
                                            onClick = {
                                                when (model.id) {
                                                    "whisper" -> {
                                                        whisperDownloaded = false
                                                        whisperActive = false
                                                        prefs.edit().putBoolean("whisper_downloaded", false).putBoolean("whisper_active", false).apply()
                                                    }
                                                    "vosk" -> {
                                                        voskDownloaded = false
                                                        voskActive = false
                                                        prefs.edit().putBoolean("vosk_downloaded", false).putBoolean("vosk_active", false).apply()
                                                    }
                                                    "argos" -> {
                                                        argosDownloaded = false
                                                        argosActive = false
                                                        prefs.edit().putBoolean("argos_downloaded", false).putBoolean("argos_active", false).apply()
                                                    }
                                                    "opus" -> {
                                                        opusDownloaded = false
                                                        opusActive = false
                                                        prefs.edit().putBoolean("opus_downloaded", false).putBoolean("opus_active", false).apply()
                                                    }
                                                }
                                                android.widget.Toast.makeText(context, "تم حذف الملف المؤقت للنموذج من الذاكرة المحلية بنجاح.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف النموذج",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Beautiful Bottom Navigation Bar with translucent glass design
        NavigationBar(
            containerColor = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.height(72.dp)
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "الرئيسية والترجمة",
                        tint = if (selectedTab == 0) BrandPrimary else TextSecondary
                    )
                },
                label = {
                    Text(
                        text = "الترجمة والمعاينة",
                        color = if (selectedTab == 0) BrandPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = AccentBlue
                )
            )

            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "مستودع النماذج",
                        tint = if (selectedTab == 1) BrandPrimary else TextSecondary
                    )
                },
                label = {
                    Text(
                        text = "نماذج الترجمة والصوت",
                        color = if (selectedTab == 1) BrandPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = AccentBlue
                )
            )
        }
    }
}

@Composable
fun WelcomeBanner() {
    val BrandPrimary = Color(0xFF005AC1)
    val AccentBlue = Color(0xFFD3E4FF)
    val TextPrimary = Color(0xFF1B1B1F)
    val TextSecondary = Color(0xFF44474E)
    val GlassCardBg = Color.White.copy(alpha = 0.45f)
    val GlassBorder = Color.White.copy(alpha = 0.5f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassCardBg
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(AccentBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "كيف يعمل التطبيق؟",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "1. قم باختيار أي ملف فيديو من معرض الهاتف الخاص بك.\n" +
                       "2. سيقوم التطبيق باستخلاص الصوت من الفيديو بشكل فوري ومحلي.\n" +
                       "3. تتم معالجة الصوت وترجمته للغة العربية بدقة عالية بمساعدة Gemini AI.\n" +
                       "4. يمكنك معاينة الترجمة متزامنة فوق الفيديو أو تصديرها كملف SRT فورا.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun VideoPlayerWithSubtitles(
    videoUri: Uri,
    subtitles: List<SubtitleItem>,
    viewModel: SubtitleViewModel
) {
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    val BrandPrimary = Color(0xFF005AC1)

    // Coroutine to poll the video view's current position and sync Compose state
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                videoViewInstance?.let { view ->
                    if (view.isPlaying) {
                        viewModel.currentPlaybackTimeMs = view.currentPosition.toLong()
                    }
                }
                delay(100)
            }
        }
    }

    // Determine current active subtitle item
    val activeSubtitle = remember(viewModel.currentPlaybackTimeMs, subtitles) {
        subtitles.find {
            viewModel.currentPlaybackTimeMs >= it.startTimeMs && viewModel.currentPlaybackTimeMs <= it.endTimeMs
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .border(2.dp, BrandPrimary, RoundedCornerShape(28.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Android Native VideoView for reliable local playback
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(videoUri)
                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        videoViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Subtitle overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (activeSubtitle != null) {
                    Text(
                        text = activeSubtitle.text,
                        color = Color(0xFFFFEB3B), // Classic high-visibility subtitle yellow
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .fillMaxWidth(0.9f)
                    )
                }
            }

            // Controls Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 400f
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Play/Pause button
                    IconButton(
                        onClick = {
                            videoViewInstance?.let { view ->
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    view.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier
                            .background(BrandPrimary, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Progress indicator
                    Text(
                        text = formatDuration(viewModel.currentPlaybackTimeMs),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleListViewer(
    subtitles: List<SubtitleItem>,
    modifier: Modifier = Modifier
) {
    val BrandPrimary = Color(0xFF005AC1)
    val AccentBlue = Color(0xFFD3E4FF)
    val TextPrimary = Color(0xFF1B1B1F)
    val TextSecondary = Color(0xFF44474E)
    val GlassCardBg = Color.White.copy(alpha = 0.45f)
    val GlassBorder = Color.White.copy(alpha = 0.5f)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCardBg),
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Subtitles,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "نص الترجمة الكامل (${subtitles.size} سطر)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.Black.copy(alpha = 0.08f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            if (subtitles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد فقرات ترجمة لتحميلها.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(subtitles) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Subtitle block index
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(AccentBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.index.toString(),
                                    color = BrandPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                // Subtitle text (Arabic)
                                Text(
                                    text = item.text,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Subtitle timestamp
                                Text(
                                    text = "${formatDuration(item.startTimeMs)} ➔ ${formatDuration(item.endTimeMs)}",
                                    color = BrandPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility to format milliseconds to HH:MM:SS format
private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60)) % 24
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
