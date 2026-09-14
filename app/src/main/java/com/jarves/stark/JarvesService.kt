package com.jarves.stark

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import android.content.ActivityNotFoundException
import java.util.*
import android.provider.MediaStore
import android.content.ComponentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class JarvesService : Service() {
    private lateinit var memory: MemoryStore
    private lateinit var auth: AuthManager
    private var failedAuth = 0
    private var authBlockedUntil = 0L
    private var pendingDelete: String? = null
    private var conversationActive = false
    private var conversationUntil = 0L
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private val channel = "jarves_voice"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val voiceHandler = Handler(Looper.getMainLooper())
    @Volatile private var speaking = false
    @Volatile private var serviceRunning = false

    override fun onCreate() {
        super.onCreate()
        serviceRunning = true
        memory = MemoryStore(this)
        auth = AuthManager(this)
        if (auth.hasSecret()) auth.lock()
        createNotification()
        tts = TextToSpeech(this) { status ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false; if (serviceRunning) voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 400) }
                override fun onError(id: String?) { speaking = false; if (serviceRunning) voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 700) }
            })
            if (status == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN")
        voiceHandler.postDelayed({ if (serviceRunning) listen() }, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "JARVES Voice", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, channel)
            .setContentTitle("JARVES is listening")
            .setContentText("Say: Hey JARVES")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1001, n)
    }
    private fun listen() {
        if (!serviceRunning || speaking) return
        if (MediaManager.isVoiceRecording()) {
            voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 1200)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 3000)
            return
        }
        recognizer?.destroy()
        recognizer = null
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onResults(results: Bundle?) {
                if (!serviceRunning || speaking) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
                recognizer?.destroy()
                recognizer = null
                if (text.isNotBlank()) {
                    val wakeOnly = cleanJarvesWakeWord(text)
                    val secretAttempt = wakeOnly.startsWith("पासवर्ड") || wakeOnly.startsWith("password", true) || wakeOnly.startsWith("secret", true) || (auth.hasSecret() && !auth.isUnlocked() && wakeOnly.isNotBlank())
                    if (!secretAttempt) memory.add("user_voice", text)
                }
                handle(text)
                if (serviceRunning && !speaking) {
                    voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 500)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                recognizer?.destroy()
                recognizer = null
                if (serviceRunning && !speaking) {
                    voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 800)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            recognizer!!.startListening(intent)
        } catch (_: Exception) {
            recognizer?.destroy()
            recognizer = null
            if (serviceRunning && !speaking) {
                voiceHandler.postDelayed({ if (serviceRunning && !speaking) listen() }, 1200)
            }
        }
    }

    private fun isJarvesWakeWord(s: String): Boolean {
        val t = s.lowercase(Locale.getDefault())
        return t.contains("जार्वेस") || t.contains("जार्विस") || t.contains("जार्वेज") ||
            Regex("(?i)(^|[^a-z])(hey|hi|hai|hello|hey there|hi there|hello there)[ ,.!?]*(jarves|jarvis)([^a-z]|$)").containsMatchIn(t) ||
            Regex("(?i)(^|[^a-z])(jarves|jarvis)([^a-z]|$)").containsMatchIn(t)
    }

    private fun cleanJarvesWakeWord(s: String): String {
        return s.lowercase(Locale.getDefault())
            .replace(Regex("(?i)\\b(hey|hi|hai|hello|there|hey there|hi there|hello there)\\b"), " ")
            .replace("आई", " ").replace("हे", " ").replace("हाय", " ").replace("हाई", " ").replace("है", " ")
            .replace("ए", " ").replace("ऐ", " ").replace("ओ", " ")
            .replace("जार्वेस", " ").replace("जार्विस", " ").replace("जार्वेज", " ")
            .replace("जारवेस", " ").replace("जारविस", " ").replace("जर्वेस", " ").replace("जर्विस", " ")
            .replace(Regex("\\s+"), " ").trim()
    }
    private fun handlePhoneCommand(s: String): Boolean {
        val q = s.lowercase(Locale.getDefault()).trim()
        try {
            if (q.contains("सेटिंग") || q.contains("settings")) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)); speak("हाँ भाई, सेटिंग खोल दी।"); return true
            }
            if (q.contains("वाई फाई") || q.contains("वाईफाई") || q.contains("wifi") || q.contains("wi-fi")) {
                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)); speak("हाँ भाई, वाई फाई सेटिंग खोल दी।"); return true
            }
            if (q.contains("ब्लूटूथ") || q.contains("bluetooth")) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); speak("हाँ भाई, ब्लूटूथ सेटिंग खोल दी।"); return true
            }
            if (q.contains("कैमरा") || q.contains("camera")) {
                startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); speak("हाँ भाई, कैमरा खोल दिया।"); return true
            }
            if (q.contains("यूट्यूब") || q.contains("youtube")) {
                val i = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (i != null) { startActivity(i); speak("हाँ भाई, यूट्यूब खोल दिया।"); return true }
            }
            if (q == "फोन" || q.contains("डायलर") || q.contains("फोन खोल") || q.contains("dialer")) {
                startActivity(Intent(Intent.ACTION_DIAL)); speak("हाँ भाई, फोन खोल दिया।"); return true
            }
            if (q.contains("मैसेज") || q.contains("संदेश") || q.contains("message") || q.contains("sms")) {
                val i = Intent("android.intent.action.MESSAGING")
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                speak("हाँ भाई, मैसेज खोल दिया।")
                return true
            }
            if (q == "होम" || q.contains("होम स्क्रीन") || q == "home" || q.contains("home screen")) {
                goHome(); return true
            }
        } catch (_: Exception) {
            speak("भाई, यह काम अभी नहीं हो पाया।")
            return true
        }
        return false
    }

    private fun handle(s: String) {
        if (handlePhoneCommand(s)) return

        val wake = isJarvesWakeWord(s)
        val activeConversation = conversationActive && System.currentTimeMillis() < conversationUntil
        if (!wake && !activeConversation) return
        val cmd = if (wake) cleanJarvesWakeWord(s) else s.trim()
        if (cmd.isBlank()) {
            conversationActive = true
            conversationUntil = System.currentTimeMillis() + 30_000L
            speak("हाँ भाई, बोलो")
            return
        }
        conversationActive = true
        conversationUntil = System.currentTimeMillis() + 30_000L

        // If a secret is configured, JARVES accepts ONLY the secret passphrase/PIN
        // while locked. The passphrase is checked locally and is never sent to AI/backend.
        if (auth.hasSecret() && !auth.isUnlocked()) {
            if (System.currentTimeMillis() < authBlockedUntil) return
            val candidate = cmd.removePrefix("पासवर्ड").removePrefix("password").removePrefix("secret").trim()
            if (candidate.isNotBlank() && auth.verify(candidate)) {
                auth.unlock(); failedAuth = 0
                speak("पहचान हो गई। JARVES unlock हो गया है।")
            } else {
                failedAuth++
                if (failedAuth >= 5) {
                    authBlockedUntil = System.currentTimeMillis() + 30_000L
                    failedAuth = 0
                    speak("गलत secret। सुरक्षा के लिए 30 सेकंड रुकें।")
                }
            }
            return
        }

        // Explicit long-term memory commands. Normal conversation is also saved automatically.
        if (pendingDelete != null) {
            if (cmd in listOf("हाँ", "हां", "yes", "confirm", "कर दो", "डिलीट कर दो")) {
                val c = Calendar.getInstance()
                val kind = pendingDelete
                pendingDelete = null
                try {
                    val n = if (kind == "photos") MediaManager.deletePhotosForDay(this, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    else MediaManager.deleteAudioForDay(this, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    speak("आज की ${if (kind == "photos") "photos" else "voice recordings"} में $n files delete हुईं।")
                } catch (e: Exception) { speak("Media delete नहीं हो पाया: ${e.message}") }
            } else if (cmd in listOf("नहीं", "नही", "no", "cancel", "रद्द")) {
                pendingDelete = null; speak("ठीक है, delete cancel कर दिया।")
            } else speak("Delete करने के लिए हाँ या cancel करने के लिए नहीं बोलें।")
            return
        }

        when {
            cmd.contains("आज की फोटो delete") || cmd.contains("आज की photos delete") || cmd.contains("आज की फोटो डिलीट") -> {
                pendingDelete = "photos"; speak("क्या आज की सभी photos delete कर दूँ? हाँ या नहीं बोलें।")
            }
            cmd.contains("आज की voice recording delete") || cmd.contains("आज की recordings delete") || cmd.contains("आज की audio delete") -> {
                pendingDelete = "audio"; speak("क्या आज की सभी voice recordings delete कर दूँ? हाँ या नहीं बोलें।")
            }
            cmd.contains("voice recording शुरू") || cmd.contains("voice record शुरू") || cmd.contains("रिकॉर्डिंग शुरू") -> {
                recognizer?.destroy(); recognizer = null
                try { speak(MediaManager.startVoiceRecording(this)) } catch (e: Exception) { speak("Voice recording शुरू नहीं हुई: ${e.message}"); listen() }
            }
            cmd.contains("voice recording बंद") || cmd.contains("voice recording रोक") || cmd.contains("रिकॉर्डिंग रोक") -> {
                speak(MediaManager.stopVoiceRecording()); voiceHandler.postDelayed({ if (serviceRunning) listen() }, 300)
            }
            cmd.contains("screen recording") || cmd.contains("स्क्रीन रिकॉर्ड") || cmd.contains("स्क्रीन रिकॉर्डिंग") -> {
                stopSelf(); startMainAction(MainActivity.ACTION_SCREEN); speak("Screen recording की permission window खोल रहा हूँ।")
            }
            cmd.contains("photo लो") || cmd.contains("photo ले लो") || cmd.contains("फोटो लो") || cmd.contains("तस्वीर लो") -> {
                startMainAction(MainActivity.ACTION_PHOTO); speak("Camera खोल रहा हूँ।")
            }
            cmd.contains("video record") || cmd.contains("वीडियो रिकॉर्ड") || cmd.contains("वीडियो रिकॉर्ड करो") -> {
                startMainAction(MainActivity.ACTION_VIDEO); speak("Video camera खोल रहा हूँ।")
            }
            cmd.contains("play store") || cmd.contains("प्ले स्टोर") -> {
                val q = cmd.replace(Regex("(?i).*?(play store|प्ले स्टोर)"), "").replace(Regex("(?i)(में|पर|से|खोलो|खोल|open|install|इंस्टॉल|डाउनलोड)"), " ").trim()
                openPlayStore(q)
            }
            isCalculatorCommand(cmd) -> {
                val result = calculate(cmd)
                if (result != null) speak("जवाब है $result") else speak("मैं यह हिसाब समझ नहीं पाया।")
            }
            cmd.startsWith("याद रखो") || cmd.startsWith("याद रखना") || cmd.startsWith("remember that", true) -> {
                val fact = cmd.replace(Regex("(?i)^(याद रखो|याद रखना|remember that)\\s*"), "").trim()
                if (fact.isNotBlank()) { memory.remember(fact); speak("ठीक है, इसे मेरी स्थायी memory में रख लिया है।") }
                else speak("क्या बात याद रखूँ?")
            }
            cmd.contains("तुम्हें याद") || cmd.contains("याद है") || cmd.contains("पुरानी बात") || cmd.contains("पहले क्या") -> {
                val hits = memory.search(cmd, 8)
                if (hits.isEmpty()) speak("मुझे इस सवाल से जुड़ी पुरानी बात नहीं मिली।")
                else speak("हाँ, मेरी memory में यह जानकारी है: " + hits.take(3).joinToString("; ").take(900))
            }
            (cmd.contains("youtube") || cmd.contains("यूट्यूब")) && !(cmd.contains("पर डाल") || cmd.contains("publish") || cmd.contains("पोस्ट")) -> openPackage("com.google.android.youtube")
            cmd.contains("whatsapp") || cmd.contains("व्हाट्सएप") -> openPackage("com.whatsapp")
            cmd.contains("google") || cmd.contains("गूगल") -> openUrl("https://www.google.com")
            cmd.contains("wifi") || cmd.contains("वाईफाई") -> openSettings(Settings.ACTION_WIFI_SETTINGS)
            cmd.contains("bluetooth") || cmd.contains("ब्लूटूथ") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            cmd.contains("सेटिंग") || cmd.contains("settings") -> openSettings(Settings.ACTION_SETTINGS)

            // AI content automation: send the natural-language brief to the secure content backend.
            cmd.contains("वीडियो बना") || cmd.contains("video bana") || cmd.contains("कार्टून वीडियो") || cmd.contains("वीडियो बनाओ") || cmd.contains("content बना") -> {
                val brief = cmd
                speak("ठीक है। Content brief लेकर AI script, video, captions, thumbnail, title और tags तैयार करेगा।")
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val r = ContentFactoryClient(this@JarvesService).createVideo(brief)
                    speak("Content result: " + r.take(1200))
                }
            }

            cmd.contains("publish") || cmd.contains("पोस्ट") || cmd.contains("पब्लिश") -> {
                speak("Publishing को JARVES में जानबूझकर automatic नहीं रखा गया है। पहले आपकी स्पष्ट approval और official account connection चाहिए।")
            }

            // "JARVES, इसे 5 बजे डाल देना" — the AI backend can interpret the requested schedule.
            cmd.contains("बजे डाल") || cmd.contains("schedule") || cmd.contains("शेड्यूल") -> {
                speak("Schedule request समझ लिया। Content backend को समय और platforms के साथ भेजने के लिए पहले content asset चाहिए।")
            }

            // "JARVES खोलो Instagram/Telegram/Chrome..." -> search installed app by label.
            cmd.contains("खोल") || cmd.contains("open") -> {
                val name = cmd.replace(Regex("(?i)^(.*?)(खोलो|खोल|open)\\s*"), "").trim()
                if (name.isNotBlank()) openByName(name) else speak("कौन सा ऐप खोलूँ?")
            }

            // "कोड 1234" / "type 1234" -> Accessibility service inserts text into focused field.
            cmd.contains("कोड") || cmd.contains("code") || cmd.contains("type") || cmd.contains("टाइप") -> {
                val code = extractCode(cmd)
                if (code.isNotBlank() && JarvesAccessibilityService.typeText(code))
                    speak("कोड डाल दिया")
                else
                    speak("पहले Accessibility की अनुमति दें और जिस बॉक्स में कोड डालना है उसे चुनें")
            }

            cmd.contains("लॉक करो") || cmd.contains("lock jarves") || cmd.contains("jarves lock") -> {
                if (auth.hasSecret()) { auth.lock(); speak("JARVES lock कर दिया है। अगली बार secret password बोलना होगा।") }
                else speak("पहले Voice Security में secret password सेट करें।")
            }

            // "बंद करो" -> leaves current app via Home. Android does not allow arbitrary force-stop.
            cmd.contains("बंद") || cmd.contains("close") || cmd.contains("क्लोज") -> {
                JarvesAccessibilityService.goHome()
                speak("ऐप बंद करके होम पर आ गया")
            }

            // Basic conversational/help responses. For open-ended knowledge,
            // connect an AI backend in a future version; no API key is embedded here.
            cmd.contains("कैसे हो") || cmd.contains("कैसा हो") || cmd.contains("how are you") ->
                speak("मैं ठीक हूँ। आपकी मदद के लिए तैयार हूँ।")
            cmd.contains("नमस्ते") || cmd.contains("हैलो") || cmd.contains("hello") || cmd.contains("hi") ->
                speak("नमस्ते। बताइए, मैं आपकी क्या मदद करूँ?")
            cmd.contains("तुम कौन") || cmd.contains("आप कौन") || cmd.contains("who are you") ->
                speak("मैं JARVES हूँ। मैं आपकी आवाज़ के आदेश समझकर फोन में उपलब्ध काम करने की कोशिश करता हूँ।")
            cmd.contains("क्या कर सकते") || cmd.contains("क्या कर सकता") || cmd.contains("help") ->
                speak("मैं ऐप खोल सकता हूँ, सेटिंग खोल सकता हूँ, टेक्स्ट बॉक्स में टेक्स्ट डाल सकता हूँ और ऐप से होम पर ला सकता हूँ।")
            cmd.contains("समय") || cmd.contains("time") -> {
                val now = java.text.SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(java.util.Date())
                speak("अभी समय है $now")
            }
            cmd.contains("तारीख") || cmd.contains("date") -> {
                val now = java.text.SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(java.util.Date())
                speak("आज की तारीख है $now")
            }

            // Trading app control is separate from market analysis.
            // JARVES never places an order automatically.
            (cmd.contains("olymp") || cmd.contains("olymp trade") || cmd.contains("ओलंप") ||
             cmd.contains("trading app") || cmd.contains("ट्रेडिंग ऐप")) -> {
                openByName(if (cmd.contains("olymp") || cmd.contains("ओलंप")) "Olymp" else "trading")
                speak("Trading app खोल रहा हूँ। कोई order अपने-आप नहीं लगाया जाएगा।")
            }

            // Direct keyless market analysis. This uses the same 11-instrument list as the UI,
            // so voice analysis no longer depends on an AI backend.
            MarketClient.labelFromCommand(cmd) != null &&
            (cmd.contains("market") || cmd.contains("मार्केट") || cmd.contains("analysis") ||
             cmd.contains("एनालिसिस") || cmd.contains("analyse") || cmd.contains("analyze") ||
             cmd.contains("बताओ") || cmd.contains("देखो") || cmd.contains("जांच")) -> {
                val symbol = MarketClient.labelFromCommand(cmd)!!
                speak("$symbol का live market analysis ला रहा हूँ।")
                serviceScope.launch {
                    try {
                        val candles = MarketClient().candles(symbol, "5min", 240)
                        val a = TechnicalAnalyzer.analyze(symbol, "5min", candles)
                        val p = a.indicators
                        val price = "%.5f".format(java.util.Locale.US, candles.last().close)
                        speak("$symbol: ${a.bias}, confidence ${a.confidence} प्रतिशत। Price $price। RSI ${"%.1f".format(java.util.Locale.US, p.rsi)}। EMA20 ${"%.5f".format(java.util.Locale.US, p.ema20)}। EMA50 ${"%.5f".format(java.util.Locale.US, p.ema50)}।")
                    } catch (e: Exception) {
                        speak("Live $symbol data अभी नहीं मिल पाया: ${e.message ?: "network error"}")
                    }
                }
            }

            cmd.contains("market") || cmd.contains("मार्केट") || cmd.contains("analysis") ||
            cmd.contains("एनालिसिस") || cmd.contains("analyse") || cmd.contains("analyze") -> {
                speak("Market analysis के लिए supported instrument का नाम बोलें, जैसे Gold, EUR/USD, Bitcoin या Ethereum।")
            }

            // Open-ended conversation: AI answers first; backend must automatically search live web when needed.
            else -> {
                AiClient.askWithWebFallback(this, cmd) { reply -> speak(reply) }
            }
        }
    }

    private fun startMainAction(action: String) {
        try {
            val i = Intent(this, MainActivity::class.java).apply { this.action = action; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
            startActivity(i)
        } catch (e: Exception) { speak("यह media action अभी उपलब्ध नहीं है।") }
    }

    private fun openPlayStore(query: String) {
        try {
            val uri = if (query.isBlank()) Uri.parse("market://search?q=apps") else Uri.parse("market://search?q=" + Uri.encode(query))
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { openUrl("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps") }
    }

    private fun isCalculatorCommand(s: String): Boolean =
        s.contains("कितना") || s.contains("जोड़") || s.contains("घटा") || s.contains("गुणा") || s.contains("भाग") ||
        Regex("\\d+\\s*[+\\-*/x×÷]\\s*\\d+").containsMatchIn(s)

    private fun calculate(s: String): String? {
        val normalized = s.lowercase(Locale.getDefault()).replace("कितना है", "").replace("कितना", "")
            .replace("गुणा", "*").replace("मल्टीप्लाई", "*").replace("x", "*").replace("×", "*")
            .replace("भाग", "/").replace("÷", "/").replace("जोड़", "+").replace("प्लस", "+").replace("घटा", "-").replace("माइनस", "-")
            .replace("है", " ").trim()
        val m = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)").find(normalized) ?: return null
        val a = m.groupValues[1].toDouble(); val op = m.groupValues[2]; val b = m.groupValues[3].toDouble()
        val r = when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> if (b != 0.0) a / b else return null; else -> return null }
        return if (r % 1.0 == 0.0) r.toLong().toString() else "%.6f".format(Locale.US, r).trimEnd('0').trimEnd('.')
    }

    private fun openNoteEditor(text: String) {
        try {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "JARVES_Note.txt")
                putExtra("jarves_text", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
            speak("नोट लिखने के लिए पेज खोल दिया है")
        } catch (_: ActivityNotFoundException) {
            speak("फोन में नोट बनाने वाला editor नहीं मिला")
        }
    }

    private fun extractCode(s: String): String {
        return Regex("""[A-Za-z0-9@#*._-]{2,32}""").findAll(s)
            .map { it.value }.lastOrNull { !it.equals("code", true) && !it.equals("type", true) }
            ?: ""
    }

    private fun openByName(name: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val target = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, true)
        }
        if (target != null) {
            startActivity(pm.getLaunchIntentForPackage(target.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("${pm.getApplicationLabel(target)} खोल रहा हूँ")
        } else speak("$name ऐप नहीं मिला")
    }

    private fun openPackage(pkg: String) {
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else speak("यह ऐप फोन में नहीं मिला")
        } catch (_: Exception) { speak("यह ऐप फोन में नहीं मिला") }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openSettings(action: String) {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun speak(x: String) {
        if (!serviceRunning) return
        recognizer?.destroy()
        recognizer = null
        speaking = true
        memory.add("jarves", x)
        if (::tts.isInitialized) {
            tts.speak(x, TextToSpeech.QUEUE_FLUSH, null, "jarves")
        }
    }

    override fun onDestroy() {
        serviceRunning = false
        speaking = false
        voiceHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        recognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}
