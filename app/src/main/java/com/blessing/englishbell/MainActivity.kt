package com.blessing.englishbell

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import androidx.annotation.RequiresPermission
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.foundation.layout.fillMaxHeight

// ✅ 모든 고정 레이아웃에서 공유할 폭
private object UiDims {
    val PANE_WIDTH = 340.dp
}

// ------------------------------------------------------------
// 0) Application & Notification Channel
// ------------------------------------------------------------
class EnglishBellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        // 채널 기본 사운드(eng_prompt_01) 지정 (O+에서 유효)
        val defaultUri = Uri.parse("android.resource://$packageName/${R.raw.eng_prompt_01}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val ch = NotificationChannelCompat.Builder(CH_ID, NotificationManager.IMPORTANCE_DEFAULT)
            .setName("English Bell")
            .setDescription("영어 대화 알람")
            .setSound(defaultUri, attrs)
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(ch)
    }

    companion object {
        const val CH_ID = "english_bell_channel"
    }
}

// ------------------------------------------------------------
// Cupertino 스타일 TimePicker (BottomSheet)
// ------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CupertinoTimePickerSheet(
    initialMillis: Long,
    use24h: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val cal = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis }
    }

    var hour24 by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }
    var am by remember { mutableStateOf(hour24 < 12) }

    fun commit() {
        val h = if (use24h) hour24 else {
            val base = (hour24 % 12).let { if (it == 0) 12 else it }
            var h24 = base % 12
            if (!am) h24 += 12
            h24
        }
        onConfirm(h, minute)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (use24h) {
                WheelNumberPicker(
                    selected = hour24,
                    range = 0..23,
                    formatter = { "%02d".format(it) },
                    onChange = { hour24 = it },
                    widthDp = 90
                )
            } else {
                val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
                WheelNumberPicker(
                    selected = hour12,
                    range = 1..12,
                    formatter = { "%02d".format(it) },
                    onChange = {
                        val currentIsNoon = hour24 >= 12
                        hour24 = (it % 12) + if (currentIsNoon) 12 else 0
                    },
                    widthDp = 90
                )
            }

            Spacer(Modifier.width(8.dp))

            WheelNumberPicker(
                selected = minute,
                range = 0..59,
                formatter = { "%02d".format(it) },
                onChange = { minute = it },
                widthDp = 90
            )

            if (!use24h) {
                Spacer(Modifier.width(8.dp))
                WheelTextPicker(
                    items = listOf("AM", "PM"),
                    selected = if (am) 0 else 1,
                    onChange = { am = (it == 0) },
                    widthDp = 90
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                commit()
                onDismiss()
            }) { Text("확인") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WheelNumberPicker(
    selected: Int,
    range: IntRange,
    formatter: (Int) -> String,
    onChange: (Int) -> Unit,
    widthDp: Int
) {
    AndroidView(
        modifier = Modifier
            .width(widthDp.dp)
            .height(160.dp),
        factory = { ctx ->
            android.widget.NumberPicker(ctx).apply {
                minValue = range.first
                maxValue = range.last
                wrapSelectorWheel = true
                descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
                setFormatter { v -> formatter(v) }
                value = selected
                setOnValueChangedListener { _, _, newVal -> onChange(newVal) }
            }
        },
        update = { picker ->
            picker.minValue = range.first
            picker.maxValue = range.last
            picker.value = selected.coerceIn(range)
        }
    )
}

@Composable
private fun WheelTextPicker(
    items: List<String>,
    selected: Int,
    onChange: (Int) -> Unit,
    widthDp: Int
) {
    AndroidView(
        modifier = Modifier
            .width(widthDp.dp)
            .height(160.dp),
        factory = { ctx ->
            android.widget.NumberPicker(ctx).apply {
                minValue = 0
                maxValue = items.lastIndex
                displayedValues = items.toTypedArray()
                wrapSelectorWheel = true
                descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
                value = selected
                setOnValueChangedListener { _, _, newVal -> onChange(newVal) }
            }
        },
        update = { picker ->
            picker.displayedValues = null
            picker.minValue = 0
            picker.maxValue = items.lastIndex
            picker.displayedValues = items.toTypedArray()
            picker.value = selected.coerceIn(0, items.lastIndex)
        }
    )
}

// ------------------------------------------------------------
// 1) Data Models
// ------------------------------------------------------------
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant" | "system"
    val text: String
)

data class StartResponse(val assistant_text: String)
data class ReplyRequest(val history: List<ChatMessage>, val user_text: String)
data class ReplyResponse(val assistant_text: String)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var totalSeconds: Int? = 0
)

enum class AlarmType(val display: String) { DAILY("매일"), WEEKLY("요일별"), INTERVAL("시간 주기") }

data class Alarm(
    var id: String = UUID.randomUUID().toString(),
    var type: AlarmType,
    var timeMillis: Long,
    var weekdays: MutableSet<Int> = mutableSetOf(), // 1..7 (Sun..Sat)
    var intervalMin: Int? = null,
    var isActive: Boolean = true
) {
    fun description(): String = when (type) {
        AlarmType.DAILY -> "매일 ${fmtHM(timeMillis)}"
        AlarmType.WEEKLY -> {
            val days = weekdays.toList().sorted().joinToString(", ") { wd ->
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, wd) }
                SimpleDateFormat("E", Locale.KOREAN).format(cal.time)
            }
            "$days ${fmtHM(timeMillis)}"
        }
        AlarmType.INTERVAL -> "${intervalMin ?: 0}분마다"
    }
}

private fun fmtHM(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

// ------------------------------------------------------------
// 2) Simple JSON Store (SharedPreferences)
// ------------------------------------------------------------
object Store {
    private const val PREF = "english_bell_store"
    private const val KEY_SESSIONS = "sessions_json"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEEP_DAYS = 10

    fun readSessions(ctx: Context): MutableList<ChatSession> {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_SESSIONS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<ChatSession>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = ChatSession(
                id = o.getString("id"),
                startTime = o.getLong("startTime"),
                messages = parseMessages(o.getJSONArray("messages")).toMutableList(),
                totalSeconds = if (o.has("totalSeconds") && !o.isNull("totalSeconds"))
                    o.getInt("totalSeconds") else 0
            )
            out += s
        }
        val startToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val cutoff = startToday - (KEEP_DAYS - 1) * 24L * 3600_000L
        return out.filter { dayStart(it.startTime) >= cutoff }
            .sortedByDescending { it.startTime }
            .toMutableList()
    }

    fun saveSession(ctx: Context, session: ChatSession) {
        val list = readSessions(ctx)
        val idx = list.indexOfFirst { it.id == session.id }
        if (idx >= 0) list[idx] = session else list.add(0, session)
        writeSessions(ctx, list)
    }

    fun deleteSession(ctx: Context, id: String) {
        val list = readSessions(ctx)
        writeSessions(ctx, list.filter { it.id != id }.toMutableList())
    }

    private fun writeSessions(ctx: Context, list: MutableList<ChatSession>) {
        val arr = JSONArray()
        list.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("startTime", s.startTime)
                put("messages", JSONArray().also { ma ->
                    s.messages.forEach { m ->
                        ma.put(JSONObject().apply {
                            put("id", m.id); put("role", m.role); put("text", m.text)
                        })
                    }
                })
                put("totalSeconds", s.totalSeconds ?: 0)
            }
            arr.put(obj)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun secondsForDay(ctx: Context, dateMillis: Long): Int {
        val sod = dayStart(dateMillis)
        val eod = sod + 24L * 3600_000L
        return readSessions(ctx).filter { it.startTime in sod until eod }
            .sumOf { it.totalSeconds ?: 0 }
    }

    fun dailyTotals(ctx: Context): List<Pair<Long, Int>> {
        val map = mutableMapOf<Long, Int>()
        readSessions(ctx).forEach {
            val day = dayStart(it.startTime)
            map[day] = (map[day] ?: 0) + (it.totalSeconds ?: 0)
        }
        return map.entries.sortedByDescending { it.key }.map { it.key to it.value }
    }

    fun sessionsOn(ctx: Context, dayMillis: Long): List<ChatSession> {
        val sod = dayStart(dayMillis)
        val eod = sod + 24L * 3600_000L
        return readSessions(ctx).filter { it.startTime in sod until eod }
            .sortedByDescending { it.startTime }
    }

    // --- Alarms ---
    fun readAlarms(ctx: Context): MutableList<Alarm> {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_ALARMS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Alarm>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = Alarm(
                id = o.getString("id"),
                type = AlarmType.valueOf(o.getString("type")),
                timeMillis = o.getLong("timeMillis"),
                weekdays = o.optJSONArray("weekdays")?.let { ja ->
                    (0 until ja.length()).map { ja.getInt(it) }.toMutableSet()
                } ?: mutableSetOf(),
                intervalMin = if (o.has("intervalMin") && !o.isNull("intervalMin"))
                    o.getInt("intervalMin") else null,
                isActive = o.getBoolean("isActive")
            )
            out += a
        }
        return out
    }

    fun saveAlarms(ctx: Context, alarms: List<Alarm>) {
        val arr = JSONArray()
        alarms.forEach { a ->
            val o = JSONObject().apply {
                put("id", a.id)
                put("type", a.type.name)
                put("timeMillis", a.timeMillis)
                put("weekdays", JSONArray().also { ja -> a.weekdays.sorted().forEach { ja.put(it) } })
                if (a.intervalMin != null) put("intervalMin", a.intervalMin)
                put("isActive", a.isActive)
            }
            arr.put(o)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    private fun parseMessages(arr: JSONArray): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += ChatMessage(
                id = o.getString("id"),
                role = o.getString("role"),
                text = o.getString("text")
            )
        }
        return list
    }

    private fun dayStart(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

// ------------------------------------------------------------
// 3) Alarms: AlarmManager + BroadcastReceiver
// ------------------------------------------------------------
class AlarmReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        val sounds = listOf(
            R.raw.eng_prompt_01,
            R.raw.eng_prompt_02,
            R.raw.eng_prompt_03,
            R.raw.eng_prompt_04,
            R.raw.eng_prompt_05
        )
        val sound = sounds.random()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, EnglishBellApp.CH_ID)
            .setSmallIcon(R.drawable.ic_stat_name) // 앱에 리소스 있어야 함
            .setContentTitle("영어 대화 알람")
            .setContentText("영어 대화할 시간입니다!")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // O 미만에서만 per-notification sound 지정
        val uri = UriUtils.rawUri(context, sound)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setSound(uri, AudioManager.STREAM_NOTIFICATION)
        }
        // O+는 채널 사운드 사용

        NotificationManagerCompat.from(context)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }
}

object UriUtils {
    fun rawUri(ctx: Context, @RawRes resId: Int): Uri =
        Uri.parse("android.resource://${ctx.packageName}/$resId")
}

object AlarmScheduler {
    fun scheduleAll(ctx: Context, alarms: List<Alarm>) {
        cancelAll(ctx, alarms)
        alarms.filter { it.isActive }.forEach { schedule(ctx, it) }
    }

    fun toggle(ctx: Context, alarm: Alarm) {
        cancel(ctx, alarm)
        if (alarm.isActive) schedule(ctx, alarm)
    }

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun schedule(ctx: Context, alarm: Alarm) {
        when (alarm.type) {
            AlarmType.DAILY -> {
                val dc = Calendar.getInstance().apply { timeInMillis = alarm.timeMillis }
                val next = nextTimeAt(dc.get(Calendar.HOUR_OF_DAY), dc.get(Calendar.MINUTE))
                setRepeating(ctx, alarm.id, next, 24L * 3600_000L)
            }
            AlarmType.WEEKLY -> {
                val hm = Calendar.getInstance().apply { timeInMillis = alarm.timeMillis }
                val hour = hm.get(Calendar.HOUR_OF_DAY)
                val minute = hm.get(Calendar.MINUTE)
                alarm.weekdays.forEach { wd ->
                    val next = nextWeekdayAt(wd, hour, minute)
                    setRepeating(ctx, "${alarm.id}_w$wd", next, 7L * 24L * 3600_000L)
                }
            }
            AlarmType.INTERVAL -> {
                val intervalMin = kotlin.math.max(1, alarm.intervalMin ?: 30)
                val triggerAt = System.currentTimeMillis() + (intervalMin * 60_000L)
                setRepeating(ctx, alarm.id, triggerAt, intervalMin * 60_000L)
            }
        }
    }

    private fun cancelAll(ctx: Context, alarms: List<Alarm>) {
        alarms.forEach { cancel(ctx, it) }
    }

    private fun cancel(ctx: Context, alarm: Alarm) {
        when (alarm.type) {
            AlarmType.WEEKLY -> alarm.weekdays.forEach { wd -> cancelPI(ctx, "${alarm.id}_w$wd") }
            else -> cancelPI(ctx, alarm.id)
        }
    }

    private fun cancelPI(ctx: Context, reqId: String) {
        val pi = PendingIntent.getBroadcast(
            ctx, reqId.hashCode(),
            Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am(ctx).cancel(pi)
    }

    private fun setRepeating(ctx: Context, reqId: String, first: Long, interval: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, reqId.hashCode(),
            Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am(ctx).setRepeating(AlarmManager.RTC_WAKEUP, first, interval, pi)
    }

    private fun nextTimeAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) > hour ||
            (cal.get(Calendar.HOUR_OF_DAY) == hour && cal.get(Calendar.MINUTE) >= minute)
        ) cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun nextWeekdayAt(weekday: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        val currentW = cal.get(Calendar.DAY_OF_WEEK)
        var delta = (weekday - currentW + 7) % 7
        if (delta == 0) {
            if (cal.get(Calendar.HOUR_OF_DAY) > hour ||
                (cal.get(Calendar.HOUR_OF_DAY) == hour && cal.get(Calendar.MINUTE) >= minute)
            ) delta = 7
        }
        cal.add(Calendar.DAY_OF_YEAR, delta)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

// ------------------------------------------------------------
// 4) Speech (STT) & TTS
// ------------------------------------------------------------
class SpeechController(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onPartial: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastPartial: String = ""
    private val silenceTimeoutMs = 2500L
    private var silenceHandler: Handler? = null
    private val silenceRunnable = Runnable {
        if (lastPartial.isNotEmpty()) onText(lastPartial)
        stop()
    }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { resetSilence() }
                override fun onBeginningOfSpeech() { resetSilence() }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { stop() }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val text = list.firstOrNull().orEmpty()
                    if (text.isNotEmpty()) onText(text)
                    stop()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    lastPartial = list.firstOrNull().orEmpty()
                    onPartial(lastPartial)
                    resetSilence()
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
        isListening = true
        resetSilence()
    }

    fun stop() {
        isListening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        silenceHandler?.removeCallbacksAndMessages(null)
        silenceHandler = null
        lastPartial = ""
    }

    fun isRecognizing(): Boolean = isListening

    private fun resetSilence() {
        if (silenceHandler == null) silenceHandler = Handler(Looper.getMainLooper())
        silenceHandler?.removeCallbacksAndMessages(null)
        silenceHandler?.postDelayed(silenceRunnable, silenceTimeoutMs)
    }
}

class TTSController(ctx: Context, private val onSpeakingChanged: (Boolean) -> Unit) :
    TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.voice = tts?.voices?.firstOrNull {
                it.locale.language == "en" && it.locale.country == "US" &&
                        it.quality >= Voice.QUALITY_HIGH
            } ?: tts?.defaultVoice
        }
    }

    fun speak(text: String) {
        stop()
        if (!ready) return
        onSpeakingChanged(true)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onSpeakingChanged(true) }
            override fun onDone(utteranceId: String?) { onSpeakingChanged(false) }
            override fun onError(utteranceId: String?) { onSpeakingChanged(false) }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.shutdown() }
}

// ------------------------------------------------------------
// 5) Chat ViewModel (network + state)
// ------------------------------------------------------------
class ChatVM(private val ctx: Context) {
    private val isDebug = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val serverURL = if (isDebug)
        "https://fe18a029cc8f.ngrok-free.app"
    else
        "http://13.124.208.108:2479"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chatActive = MutableStateFlow(true)
    val chatActive: StateFlow<Boolean> = _chatActive.asStateFlow()

    private val _recognizedPartial = MutableStateFlow("")
    val recognizedPartial: StateFlow<String> = _recognizedPartial.asStateFlow()

    private var currentSession: ChatSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isActive() = _chatActive.value

    fun startChat(tts: TTSController) {
        if (_messages.value.isNotEmpty()) return
        currentSession = ChatSession()
        scope.launch {
            _loading.emit(true); _error.emit(null)
            try {
                val url = URL("$serverURL/chat/start")
                val body = JSONObject()
                    .put("prompt", "Hello! Would you like to practice some interesting expressions with me?")
                    .toString()
                val resp = httpPostJson(url, body)
                val sr = JSONObject(resp)
                val assistant = ChatMessage(role = "assistant", text = sr.getString("assistant_text"))
                val newList = _messages.value.toMutableList().apply { add(assistant) }
                _messages.emit(newList)
                currentSession?.messages?.add(assistant)
                _loading.emit(false)
                withContextMain { tts.speak(assistant.text) }
            } catch (e: Exception) {
                _loading.emit(false)
                _error.emit("Failed to start chat: ${e.message}")
            }
        }
    }

    fun onPartialText(text: String) {
        scope.launch { _recognizedPartial.emit(text) }
    }

    fun sendRecognized(text: String, tts: TTSController) {
        if (text.isBlank() || _loading.value || !_chatActive.value) return
        scope.launch {
            val user = ChatMessage(role = "user", text = text)
            val appended = _messages.value.toMutableList().apply { add(user) }
            _messages.emit(appended)
            currentSession?.messages?.add(user)
            _loading.emit(true); _error.emit(null)
            try {
                val url = URL("$serverURL/chat/reply")
                val req = JSONObject().apply {
                    put("history", JSONArray().also { arr ->
                        _messages.value.filter { it.role != "system" }.forEach { m ->
                            arr.put(JSONObject().apply {
                                put("id", m.id); put("role", m.role); put("text", m.text)
                            })
                        }
                    })
                    put("user_text", user.text)
                }.toString()
                val resp = httpPostJson(url, req)
                val jr = JSONObject(resp)
                val assistant = ChatMessage(role = "assistant", text = jr.getString("assistant_text"))
                val newList = _messages.value.toMutableList().apply { add(assistant) }
                _messages.emit(newList)
                currentSession?.messages?.add(assistant)
                _loading.emit(false)
                withContextMain { tts.speak(assistant.text) }
            } catch (e: Exception) {
                _loading.emit(false)
                _error.emit("Failed to get AI response: ${e.message}")
            }
        }
    }

    fun endChat() {
        _chatActive.value = false
        scope.launch {
            val sys = ChatMessage(role = "system", text = "대화가 종료되었습니다.")
            _messages.emit(_messages.value + sys)
        }
    }

    fun resumeChat(speech: SpeechController) {
        _chatActive.value = true
        speech.start()
    }

    fun setElapsedAndPersist(elapsedSec: Int) {
        currentSession?.let { s ->
            s.totalSeconds = elapsedSec
            if (s.messages.isNotEmpty()) Store.saveSession(ctx, s)
        }
    }

    fun setPartialTextUi(text: String) {
        scope.launch { _recognizedPartial.emit(text) }
    }

    fun pushAssistantTTS(text: String, tts: TTSController) {
        tts.speak(text)
    }

    private fun httpPostJson(url: URL, jsonBody: String): String {
        try {
        val con = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }
        con.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream

        if (stream == null) {
            throw RuntimeException("HTTP $code (no response body)")
        }

        val sb = StringBuilder()
        BufferedReader(InputStreamReader(stream)).use { br ->
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                sb.append(line)
            }
        }

        if (code !in 200..299) throw RuntimeException("HTTP $code $sb")
        return sb.toString()
    } catch (e: Exception) {
        android.util.Log.e("ChatVM", "HTTP fail: ${url} -> ${e.message}", e)
        throw e
    }





    }

}

private suspend fun withContextMain(block: suspend () -> Unit) =
    withContext(Dispatchers.Main) { block() }

// ------------------------------------------------------------
// 6) UI (Compose)
// ------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissions()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val ctx = LocalContext.current
                var page by remember { mutableStateOf<Page>(Page.Main) }
                var currentDateForList by remember { mutableStateOf<Long?>(null) }
                var openChat by remember { mutableStateOf(false) }

                val chatVM = remember { ChatVM(ctx) }
                var elapsed by remember { mutableIntStateOf(0) }
                var isSpeaking by remember { mutableStateOf(false) }

                val tts = remember { TTSController(ctx) { isSpeaking = it } }
                val speech = remember {
                    SpeechController(
                        ctx,
                        onText = { final ->
                            chatVM.setPartialTextUi("")
                            chatVM.sendRecognized(final, tts)
                        },
                        onPartial = { partial -> chatVM.onPartialText(partial) }
                    )
                }

                // Timer tick
                val activeState by chatVM.chatActive.collectAsState()
                LaunchedEffect(activeState, openChat) {
                    while (true) {
                        delay(1000)
                        if (chatVM.isActive() && openChat) elapsed += 1
                    }
                }

                // Navigation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brushBg())
                ) {
                    when (val p = page) {
                        Page.Main -> MainView(
                            openChat = {
                                openChat = true
                                elapsed = 0
                                page = Page.Chat
                                chatVM.startChat(tts)
                                speech.start()
                            },
                            openDates = { page = Page.Dates },
                            alarmsViewModel = remember { AlarmsVM(ctx) }
                        )
                        Page.Chat -> ChatView(
                            vm = chatVM,
                            isSpeaking = isSpeaking,
                            elapsedSec = elapsed,
                            onBack = {
                                openChat = false
                                chatVM.setElapsedAndPersist(elapsed)
                                tts.stop()
                                speech.stop()
                                page = Page.Main
                                chatVM.endChat()
                            },
                            onSpeakAgain = { msg -> chatVM.pushAssistantTTS(msg.text, tts) },
                            onEnd = { chatVM.endChat() },
                            onResume = { chatVM.resumeChat(speech) }
                        )
                        Page.Dates -> DatesListView(
                            onBack = { page = Page.Main },
                            onPickDay = { day ->
                                currentDateForList = day
                                page = Page.SessionsByDate
                            }
                        )
                        Page.SessionsByDate -> SessionsByDateView(
                            dayMillis = currentDateForList!!,
                            onBack = { page = Page.Dates },
                            onOpenSession = { session -> page = Page.Detailed(session) }
                        )
                        is Page.Detailed -> DetailedChatView(
                            session = p.session,
                            onBack = { page = Page.SessionsByDate }
                        )
                    }
                }
            }
        }
    }

    private fun ensurePermissions() {
        val needs = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needs += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needs.isNotEmpty()) {
            requestPermissions(needs.toTypedArray(), 1001)
        }
    }
}

sealed class Page {
    object Main : Page()
    object Chat : Page()
    object Dates : Page()
    object SessionsByDate : Page()
    data class Detailed(val session: ChatSession) : Page()
}

@Composable
private fun brushBg() = Brush.linearGradient(
    listOf(Color(0x40218CFF), Color(0x403F51B5))
)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .then(Modifier.padding(16.dp))
    ) { content() }
}

@Composable
fun GradientProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF3F51B5)))
                )
        )
    }
}

// ----------------- MAIN VIEW -----------------
@Composable
fun MainView(
    openChat: () -> Unit,
    openDates: () -> Unit,
    alarmsViewModel: AlarmsVM
) {
    val ctx = LocalContext.current
    val now by remember { mutableStateOf(System.currentTimeMillis()) }
    val todaySec = remember(now) { Store.secondsForDay(ctx, now) }
    val progress = (todaySec / 3600f).coerceIn(0f, 1f)

    var showTimePicker by remember { mutableStateOf(false) }
    val use24h = true

    var tab by remember { mutableStateOf(AlarmType.DAILY) }
    var selectedTime by remember { mutableStateOf(timeToday(9, 0)) }
    var selectedInterval by remember { mutableFloatStateOf(30f) }
    var selectedWeekdays by remember { mutableStateOf(mutableSetOf<Int>()) }

    var showLimit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("English Bell", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = openDates) {
                Icon(painterResource(id = android.R.drawable.ic_menu_recent_history), contentDescription = "날짜별")
            }
        }

        // ✅ 대화하기 버튼 (같은 고정 폭, 중앙 정렬)
        SectionCard(
            modifier = Modifier
                .width(UiDims.PANE_WIDTH)
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            Button(
                onClick = openChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
            ) {
                Icon(Icons.Default.Speaker, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("대화하기", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ① 탭 + 저장 (고정 폭)
        SectionCard(
            modifier = Modifier
                .width(UiDims.PANE_WIDTH)
                .align(Alignment.CenterHorizontally)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Segmented(
                    options = listOf(AlarmType.DAILY, AlarmType.WEEKLY, AlarmType.INTERVAL),
                    selected = tab,
                    onSelect = { tab = it }
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    val new = when (tab) {
                        AlarmType.INTERVAL -> Alarm(
                            type = AlarmType.INTERVAL, timeMillis = System.currentTimeMillis(),
                            intervalMin = selectedInterval.toInt(), isActive = true
                        )
                        AlarmType.DAILY -> Alarm(
                            type = AlarmType.DAILY, timeMillis = selectedTime, isActive = true
                        )
                        AlarmType.WEEKLY -> Alarm(
                            type = AlarmType.WEEKLY, timeMillis = selectedTime,
                            weekdays = selectedWeekdays.toMutableSet(), isActive = true
                        )
                    }
                    val ok = alarmsViewModel.addAlarm(new)
                    if (!ok) showLimit = true
                }) { Text("저장") }
            }
        }

        // ② 시간 선택 (고정 폭)
        SectionCard(
            modifier = Modifier
                .width(UiDims.PANE_WIDTH)
                .align(Alignment.CenterHorizontally)
        ) {
            if (tab == AlarmType.DAILY || tab == AlarmType.WEEKLY) {
                Text("시간 선택", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        SimpleDateFormat(if (use24h) "HH:mm" else "hh:mm a", Locale.getDefault())
                            .format(Date(selectedTime))
                    )
                }
            }

            if (tab == AlarmType.WEEKLY) {
                Spacer(Modifier.height(8.dp))
                Text("요일 선택", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val weekdays = listOf("일","월","화","수","목","금","토")
                    (1..7).forEach { w ->
                        val on = selectedWeekdays.contains(w)
                        Text(
                            weekdays[w - 1],
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(0xFF7C4DFF) else Color.White.copy(0.6f))
                                .clickable {
                                    if (on) selectedWeekdays.remove(w) else selectedWeekdays.add(w)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (on) Color.White else Color.Unspecified
                        )
                    }
                }
            }

            if (tab == AlarmType.INTERVAL) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("알람 주기", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${selectedInterval.roundToInt()}분", color = Color.Gray)
                }
                Slider(
                    value = selectedInterval,
                    onValueChange = { selectedInterval = it },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ③ 오늘 대화 (고정 폭)
        SectionCard(
            modifier = Modifier
                .width(UiDims.PANE_WIDTH)
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 대화", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${mmss(todaySec)} / 60:00", color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            GradientProgressBar(progress = progress)
        }

        // ④ 알람 목록 (고정 폭)
        SectionCard(
            modifier = Modifier
                .width(UiDims.PANE_WIDTH)
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp)
        ) {
            Text("내 알람 목록 (최대 5개)", fontWeight = FontWeight.SemiBold)
            val alarms by alarmsViewModel.alarms.collectAsState()
            if (alarms.isEmpty()) {
                Text("등록된 알람이 없습니다.", color = Color.Gray)
            } else {
                alarms.forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Text(a.description(), modifier = Modifier.weight(1f))
                        Switch(
                            checked = a.isActive,
                            onCheckedChange = { alarmsViewModel.toggle(a.id) }
                        )
                        IconButton(onClick = { alarmsViewModel.delete(a.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                        }
                    }
                    androidx.compose.material3.Divider(color = Color.Black.copy(alpha = 0.1f))
                }
            }
        }

        if (showLimit) {
            AlertDialog(
                onDismissRequest = { showLimit = false },
                confirmButton = {
                    TextButton(onClick = { showLimit = false }) { Text("확인") }
                },
                title = { Text("저장할 수 없어요😂") },
                text = { Text("알람은 최대 5개까지 저장할 수 있어요.") }
            )
        }
    }

    if (showTimePicker) {
        CupertinoTimePickerSheet(
            initialMillis = selectedTime,
            use24h = use24h,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                val c = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedTime = c.timeInMillis
            }
        )
    }
}


@Composable
fun Segmented(options: List<AlarmType>, selected: AlarmType, onSelect: (AlarmType) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(0.7f))
            .padding(2.dp)
    ) {
        options.forEach { opt ->
            val on = opt == selected
            Text(
                opt.display,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0xFF7C4DFF) else Color.Transparent)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (on) Color.White else Color.Unspecified
            )
        }
    }
}

@Composable
fun TimeWheel(timeMillis: Long, onChange: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    var value by remember { mutableFloatStateOf(minutesOfDay.toFloat()) }
    Text(fmtHM(cal.timeInMillis), color = Color.Gray)
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = {
            value = it
            val m = it.roundToInt()
            val h = m / 60
            val min = m % 60
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            onChange(c.timeInMillis)
        },
        valueRange = 0f..(24 * 60).toFloat(),
        steps = 24 * 12
    )
}

private fun timeToday(h: Int, m: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun mmss(sec: Int): String {
    val m = sec / 60; val s = sec % 60
    return "%02d:%02d".format(m, s)
}

// ----------------- CHAT VIEW -----------------
@Composable
fun ChatView(
    vm: ChatVM,
    isSpeaking: Boolean,
    elapsedSec: Int,
    onBack: () -> Unit,
    onSpeakAgain: (ChatMessage) -> Unit,
    onEnd: () -> Unit,
    onResume: () -> Unit
) {
    val messages by vm.messages.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val active by vm.chatActive.collectAsState()
    val partial by vm.recognizedPartial.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            Text("English Tutor", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                mmss(elapsedSec),
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray
            )
            Spacer(Modifier.width(8.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            items(messages.size) { idx ->
                val m = messages[idx]
                MessageBubble(
                    message = m,
                    onSpeaker = { if (m.role == "assistant") onSpeakAgain(m) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(12.dp)) }
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }

        AudioControlBar(
            active = active,
            recognizing = partial.isNotEmpty(),
            recognizedText = partial,
            onEnd = onEnd,
            onResume = onResume
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage, onSpeaker: () -> Unit) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isUser) {
            IconButton(onClick = onSpeaker) {
                Icon(Icons.Default.Speaker, contentDescription = "speak", tint = Color(0xFF3F51B5))
            }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) Color(0xFF3F51B5) else Color(0xFFEEEEEE))
                .padding(10.dp)
        ) {
            Text(
                message.text,
                color = if (isUser) Color.White else Color.Black
            )
        }
    }
}

@Composable
fun AudioControlBar(
    active: Boolean,
    recognizing: Boolean,
    recognizedText: String,
    onEnd: () -> Unit,
    onResume: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.8f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    recognizing -> "Listening..."
                    recognizedText.isNotEmpty() -> recognizedText
                    else -> "말씀해주세요..."
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEnd) {
                Icon(
                    painterResource(id = android.R.drawable.presence_busy),
                    contentDescription = "end", tint = Color.Red
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Autorenew, contentDescription = null, tint = Color(0xFF2E7D32))
            Spacer(Modifier.width(6.dp))
            Text("재개", color = Color(0xFF2E7D32), modifier = Modifier.clickable { onResume() })
            Spacer(Modifier.weight(1f))
        }
    }
}

// ----------------- DATES LIST -----------------
@Composable
fun DatesListView(onBack: () -> Unit, onPickDay: (Long) -> Unit) {
    val ctx = LocalContext.current
    val items = remember { Store.dailyTotals(ctx) }
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "날짜별 기록", onBack = onBack)
        if (items.isEmpty()) {
            EmptyState("표시할 날짜가 없어요", "대화를 시작해 기록을 쌓아보세요!")
        } else {
            LazyColumn {
                items(items.size) { i ->
                    val (day, sec) = items[i]
                    val progress = (sec / 3600f).coerceIn(0f, 1f)
                    Column(
                        Modifier
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(0.7f))
                            .clickable { onPickDay(day) }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtDay(day), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("${mmss(sec)} / 60:00", color = Color.Gray)
                        }
                        Spacer(Modifier.height(8.dp))
                        GradientProgressBar(progress = progress)
                    }
                }
            }
        }
    }
}

private fun fmtDay(dayStartMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dayStartMillis))

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(36.dp))
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(id = android.R.drawable.ic_dialog_info), contentDescription = null, tint = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White)
        Text(subtitle, color = Color.White.copy(0.8f))
    }
}

// ----------------- SESSIONS BY DATE -----------------
@Composable
fun SessionsByDateView(
    dayMillis: Long,
    onBack: () -> Unit,
    onOpenSession: (ChatSession) -> Unit
) {
    val ctx = LocalContext.current
    val sessions = remember { Store.sessionsOn(ctx, dayMillis) }
    Column(Modifier.fillMaxSize()) {
        TopBar(title = fmtDay(dayMillis), onBack = onBack)
        LazyColumn {
            items(sessions.size) { i ->
                val s = sessions[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.startTime)))
                    Text(mmss(s.totalSeconds ?: 0), color = Color.Gray)
                }
                androidx.compose.material3.Divider()
            }
        }
    }
}

// ----------------- DETAILED CHAT VIEW (TTS only) -----------------
@Composable
fun DetailedChatView(session: ChatSession, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tts = remember { TTSController(ctx) { } }

    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "English Tutor", onBack = {
            tts.stop()
            onBack()
        })
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            items(session.messages.size) { i ->
                val m = session.messages[i]
                MessageBubble(message = m) {
                    if (m.role == "assistant") tts.speak(m.text)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ------------------------------------------------------------
// 7) Alarms ViewModel
// ------------------------------------------------------------
class AlarmsVM(private val ctx: Context) {
    private val _alarms = MutableStateFlow(Store.readAlarms(ctx))
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun addAlarm(a: Alarm): Boolean {
        if (_alarms.value.size >= 5) return false
        val list = _alarms.value.toMutableList()
        list.add(a)
        persist(list)
        AlarmScheduler.scheduleAll(ctx, list)
        return true
    }

    fun toggle(id: String) {
        val list = _alarms.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isActive = !list[idx].isActive)
            persist(list)
            AlarmScheduler.toggle(ctx, list[idx])
        }
    }

    fun delete(id: String) {
        val list = _alarms.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val item = list.removeAt(idx)
            persist(list)
            AlarmScheduler.toggle(ctx, item.copy(isActive = false))
        }
    }

    private fun persist(list: MutableList<Alarm>) {
        Store.saveAlarms(ctx, list)
        _alarms.value = list
    }
}
