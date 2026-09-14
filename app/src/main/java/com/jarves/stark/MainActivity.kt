package com.jarves.stark

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var memory: MemoryStore
    private lateinit var auth: AuthManager
    private var pendingPhotoUri: Uri? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        memory = MemoryStore(this)
        auth = AuthManager(this)
        handleAction(intent)
        requestNotificationPermission()
        WatchAgent.start(this)
        findViewById<Switch>(R.id.toggle).setOnCheckedChangeListener { _, on ->
            if (on) startJarves() else stopService(Intent(this, JarvesService::class.java))
            status.text = if (on) "JARVES ON\nHey JARVES / हे जार्वेस\nLong-term memory: ON" else "JARVES OFF"
        }
        findViewById<Button>(R.id.diagnostics).setOnClickListener { Toast.makeText(this, "Diagnostics: JARVES सिस्टम जाँच उपलब्ध है", Toast.LENGTH_LONG).show() }

        findViewById<Button>(R.id.settings).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.security).setOnClickListener { securityDialog() }
        findViewById<Button>(R.id.aiConfig).setOnClickListener { configAi() }
        findViewById<Button>(R.id.appFactory).setOnClickListener { appFactory() }
        findViewById<Button>(R.id.trading).setOnClickListener { configMarket() }
        findViewById<Button>(R.id.memory).setOnClickListener { memoryDialog() }
        findViewById<Button>(R.id.content).setOnClickListener { contentDialog() }
        findViewById<Button>(R.id.editVideo).setOnClickListener { pickVideo() }
        findViewById<Button>(R.id.news).setOnClickListener { newsDialog() }
        findViewById<Button>(R.id.media).setOnClickListener { mediaDialog() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }


    private fun handleAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_PHOTO -> capturePhoto()
            ACTION_VIDEO -> captureVideo()
            ACTION_SCREEN -> requestScreenRecording()
        }
    }

    private fun securityDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 5, 30, 0) }
        val secret = EditText(this).apply { hint = "Secret password / passphrase (कम से कम 4 अक्षर या अंक)"; inputType = 0x00000081 }
        val info = TextView(this).apply {
            text = "यह password JARVES की local memory में plain text में सेव नहीं होगा।\n\nउदाहरण: JARVES, 7391\nया कोई अपना secret phrase."
        }
        box.addView(info); box.addView(secret)
        AlertDialog.Builder(this).setTitle("JARVES Voice Security")
            .setMessage(if (auth.hasSecret()) "Secret password पहले से सेट है। नया password रखने पर पुराना बदल जाएगा।" else "पहली बार अपना secret password सेट करें।")
            .setView(box)
            .setPositiveButton("Save Secret") { _, _ -> if (auth.setSecret(secret.text.toString())) toast("Secret password सेट हो गया।") else toast("कम से कम 4 अक्षर/अंक का password रखें") }
            .setNeutralButton("Lock Now") { _, _ -> auth.lock(); toast("JARVES locked") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun configAi() {
        val e = EditText(this).apply { hint = "AI backend URL"; setText(AiClient.getBackendUrl(this@MainActivity)) }
        AlertDialog.Builder(this).setTitle("AI Backend").setMessage("API key APK में न रखें; सुरक्षित server URL दें।")
            .setView(e).setPositiveButton("Save") { _, _ -> AiClient.saveBackendUrl(this, e.text.toString().trim()); toast("AI backend saved") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun configMarket() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 5, 35, 0) }
        val instruments = MarketClient.SUPPORTED_INSTRUMENTS.map { it.label }
        val sym = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, instruments)
        }
        val interval = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("1min", "5min", "15min", "30min", "1h"))
        }
        val saved = getPreferences(0).getString("symbol", instruments.first())
        val savedIndex = instruments.indexOf(saved).takeIf { it >= 0 } ?: 0
        sym.setSelection(savedIndex)
        box.addView(sym); box.addView(interval)
        AlertDialog.Builder(this)
            .setTitle("JARVES Trading Market")
            .setMessage("केवल आपके चुने हुए Forex, Gold, Silver और Crypto instruments। किसी Market API key की जरूरत नहीं है।")
            .setView(box)
            .setPositiveButton("Analyze") { _, _ ->
                val s = sym.selectedItem.toString()
                val tf = interval.selectedItem.toString()
                getPreferences(0).edit().putString("symbol", s).apply()
                status.text = "Live $s data ला रहा हूँ..."
                scope.launch {
                    try {
                        val d = MarketClient().candles(s, tf, 240)
                        val a = TechnicalAnalyzer.analyze(s, tf, d)
                        val p = a.indicators
                        status.text = "${a.symbol} ${a.timeframe}\n${a.bias} | confidence ${a.confidence}%\nPrice ${"%.5f".format(d.last().close)}\nRSI ${"%.1f".format(p.rsi)} | MACD ${"%.5f".format(p.macd)} | Signal ${"%.5f".format(p.signal)}\nEMA20 ${"%.5f".format(p.ema20)} | EMA50 ${"%.5f".format(p.ema50)}\nATR ${"%.5f".format(p.atr)}\nSupport ${"%.5f".format(p.support)} | Resistance ${"%.5f".format(p.resistance)}\n\n${a.note}"
                    } catch (e: Exception) {
                        status.text = "Market error: ${e.message ?: "live data नहीं मिला"}"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appFactory() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 5, 35, 0) }
        val url = EditText(this).apply { hint = "Secure App Factory backend URL"; setText(AppFactoryClient.getUrl(this@MainActivity)) }
        val spec = EditText(this).apply { hint = "कौन-सी ऐप बनानी है, screens, features..."; minLines = 5 }
        box.addView(url); box.addView(spec)
        AlertDialog.Builder(this).setTitle("JARVES App Factory").setMessage("Secure backend project बनाएगा और build शुरू करेगा।")
            .setView(box).setPositiveButton("Create") { _, _ -> AppFactoryClient.saveUrl(this, url.text.toString().trim()); status.text = "App Factory: build शुरू कर रहा हूँ..."; AppFactoryClient.createAsync(this, spec.text.toString().trim()) { result -> runOnUiThread { status.text = result; if (result.startsWith("http")) AppFactoryClient.openUrl(this, result) } } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun memoryDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 5, 30, 0) }
        val info = TextView(this); val q = EditText(this).apply { hint = "Memory में खोजें..." }; val result = TextView(this).apply { setPadding(0, 16, 0, 0) }
        info.text = "Stored memories: ${memory.count()}\nDatabase size: ${memory.databaseBytes() / 1024} KB\nRetention: automatic expiry नहीं।"
        box.addView(info); box.addView(q); box.addView(result)
        val d = AlertDialog.Builder(this).setTitle("JARVES Long-Term Memory").setView(box).setPositiveButton("Search", null).setNeutralButton("Export", null).setNegativeButton("Close", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val r = memory.search(q.text.toString(), 50); result.text = if (r.isEmpty()) "कुछ नहीं मिला" else r.joinToString("\n\n") }; d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { exportMemory() } }
        d.show()
    }

    private fun exportMemory() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "JARVES_Memory.txt") }, REQ_MEMORY)
    }

    private fun newsDialog() {
        WatchAgent.start(this)
        AlertDialog.Builder(this)
            .setTitle("JARVES Automatic Trading Watch")
            .setMessage("JARVES background में हर 15 मिनट market prices और economic calendar check करेगा। EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD, Gold, Silver, Bitcoin, Ethereum और Litecoin पर significant movement पर alert देगा। Relevant High/Medium impact USD/EUR/GBP/JPY/CHF/AUD/NZD news के लिए 5h, 30m, 15m और 5m alerts अपने-आप schedule होंगे.\n\nयह system केवल information/alerts देता है; trade या order अपने-आप नहीं लगाएगा।")
            .setPositiveButton("Watch ON") { _, _ -> WatchAgent.start(this); toast("JARVES Trading Watch ON") }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun mediaDialog() {
        val options = arrayOf("Photo लें", "Video record करें", "Screen record करें", "Screen recording रोकें", "Voice recording शुरू करें", "Voice recording रोकें", "आज की Photos delete", "आज की Voice recordings delete")
        AlertDialog.Builder(this).setTitle("JARVES Media Tools").setItems(options) { _, which ->
            when (which) {
                0 -> capturePhoto(); 1 -> captureVideo(); 2 -> requestScreenRecording(); 3 -> stopService(Intent(this, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP)); 4 -> startVoiceRecording(); 5 -> stopVoiceRecording(); 6 -> confirmDeletePhotos(); 7 -> confirmDeleteAudio()
            }
        }.show()
    }

    private fun capturePhoto() {
        val values = android.content.ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "JARVES_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JARVES") }
        pendingPhotoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { pendingPhotoUri?.let { putExtra(MediaStore.EXTRA_OUTPUT, it); addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        if (i.resolveActivity(packageManager) != null) startActivityForResult(i, REQ_PHOTO) else toast("Camera app नहीं मिला")
    }

    private fun captureVideo() {
        val i = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply { putExtra(android.provider.MediaStore.EXTRA_VIDEO_QUALITY, 1) }
        if (i.resolveActivity(packageManager) != null) startActivityForResult(i, REQ_VIDEO) else toast("Camera video app नहीं मिला")
    }

    private fun requestScreenRecording() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_SCREEN)
    }

    private fun startVoiceRecording() { try { status.text = MediaManager.startVoiceRecording(this) } catch (e: Exception) { status.text = "Voice recording error: ${e.message}" } }
    private fun stopVoiceRecording() { status.text = MediaManager.stopVoiceRecording() }

    private fun confirmDeletePhotos() {
        AlertDialog.Builder(this).setTitle("आज की Photos delete करें?").setMessage("यह आज की MediaStore photos हटाने का अनुरोध है।")
            .setPositiveButton("Delete") { _, _ -> val c = java.util.Calendar.getInstance(); val n = MediaManager.deletePhotosForDay(this, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)); toast("$n photos delete हुईं") }.setNegativeButton("Cancel", null).show()
    }
    private fun confirmDeleteAudio() {
        AlertDialog.Builder(this).setTitle("आज की Voice recordings delete करें?").setPositiveButton("Delete") { _, _ -> val c = java.util.Calendar.getInstance(); val n = MediaManager.deleteAudioForDay(this, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)); toast("$n audio files delete हुईं") }.setNegativeButton("Cancel", null).show()
    }

    private fun contentDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 5, 30, 0) }
        val url = EditText(this).apply { hint = "Secure Content/Publishing backend URL"; setText(getPreferences(0).getString("content_backend_url", "")) }
        val topic = EditText(this).apply { hint = "जैसे: जिम पर मजेदार कार्टून वीडियो"; minLines = 3 }
        val time = EditText(this).apply { hint = "Schedule: 2026-09-13T17:00:00+05:30 (optional)" }
        val title = EditText(this).apply { hint = "Title (खाली छोड़ें तो AI बनाएगा)" }
        box.addView(url); box.addView(topic); box.addView(time); box.addView(title)
        AlertDialog.Builder(this).setTitle("JARVES AI Content Studio").setMessage("Original script, voice/video, captions, thumbnail, title, description और tags backend से बनाए जा सकते हैं। Publishing के लिए official OAuth/backend connection जरूरी है।")
            .setView(box).setPositiveButton("Create") { _, _ -> getPreferences(0).edit().putString("content_backend_url", url.text.toString().trim()).apply(); status.text = "AI content बन रहा है..."; scope.launch { val r = ContentFactoryClient(this@MainActivity).createVideo(topic.text.toString().trim()); status.text = "Content result:\n$r\nSchedule: ${time.text}" } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickVideo() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE) }, REQ_EDIT_VIDEO) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEMORY && resultCode == Activity.RESULT_OK && data?.data != null) try { contentResolver.openOutputStream(data.data!!)?.use { it.write(memory.exportText().toByteArray(Charsets.UTF_8)) }; toast("Memory export हो गया") } catch (e: Exception) { toast("Export error: ${e.message}") }
        if (requestCode == REQ_EDIT_VIDEO && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!; val e = EditText(this).apply { hint = "AI क्या करे? जैसे: cut, captions, thumbnail, Hindi voice, Shorts 9:16" }
            AlertDialog.Builder(this).setTitle("AI Video Editor").setView(e).setPositiveButton("Edit") { _, _ -> scope.launch { status.text = ContentFactoryClient(this@MainActivity).editVideo(uri, e.text.toString()) } }.setNegativeButton("Cancel", null).show()
        }
        if (requestCode == REQ_SCREEN && resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, ScreenRecordService::class.java).apply { putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode); putExtra(ScreenRecordService.EXTRA_DATA, data) }
            ContextCompat.startForegroundService(this, i); toast("Screen recording शुरू")
        }
        if (requestCode == REQ_PHOTO && resultCode == Activity.RESULT_OK) toast("Photo JARVES/Pictures में save हो गई")
        if (requestCode == REQ_VIDEO && resultCode == Activity.RESULT_OK) toast("Video save हो गई")
    }

    private fun startJarves() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10); return }
        ContextCompat.startForegroundService(this, Intent(this, JarvesService::class.java))
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_PHOTO = "com.jarves.stark.ACTION_PHOTO"; const val ACTION_VIDEO = "com.jarves.stark.ACTION_VIDEO"; const val ACTION_SCREEN = "com.jarves.stark.ACTION_SCREEN"
        const val REQ_MEMORY = 701; const val REQ_EDIT_VIDEO = 702; const val REQ_SCREEN = 703; const val REQ_PHOTO = 704; const val REQ_VIDEO = 705
    }
}
