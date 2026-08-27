package com.example.cargomanifestapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)

class JarvisActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var setStatus: ((String) -> Unit)? = null
    private var setHeard: ((String) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else {
            setStatus?.invoke("Izin mikrofon diperlukan untuk mendengar perintah.")
            speak("Microphone permission is required, sir.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTts()
        setupSpeechRecognizer()

        setContent {
            var status by remember { mutableStateOf("Siap. Tekan mikrofon dan bicara.") }
            var heard by remember { mutableStateOf("Belum ada perintah.") }
            setStatus = { status = it }
            setHeard = { heard = it }

            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("JARVIS", fontWeight = FontWeight.Bold, color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, "Kembali", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF673AB7))
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("JARVIS", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("Cargo Assistant", fontSize = 16.sp, color = Color.Gray)
                        Spacer(Modifier.height(28.dp))
                        Box(
                            modifier = Modifier.size(170.dp).background(Color(0xFF673AB7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Mikrofon",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(status, textAlign = TextAlign.Center, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Perintah terakhir", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(heard)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { toggleListening() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isListening) "HENTIKAN MENDENGAR" else "AKTIFKAN JARVIS")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Contoh: “Buka Stowing”, “Buka Bukti Timbang”, “Buka Manifest”, “Buka Pencarian Manifest”, atau “Flight Tracking”.",
                            fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val british = Locale.UK
                if (tts?.isLanguageAvailable(british) ?: -1 >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = british
                } else {
                    tts?.language = Locale.US
                }
                tts?.setSpeechRate(0.92f)
                tts?.setPitch(0.88f)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { setStatus?.invoke("Mendengarkan...") ; isListening = true }
            override fun onBeginningOfSpeech() { setStatus?.invoke("Silakan lanjutkan.") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                setStatus?.invoke("Tidak ada perintah yang terbaca. Coba lagi.")
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) handleCommand(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            setStatus?.invoke("Siap.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) setupSpeechRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        setStatus?.invoke("Memulai pendengaran...")
        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.getDefault())
        setHeard?.invoke(raw)
        when {
            command.contains("stowing") -> openActivity(StowingActivity::class.java, "Membuka Stowing.")
            command.contains("bukti timbang") || command.contains("timbang") -> openActivity(BuktiTimbangActivity::class.java, "Membuka Bukti Timbang.")
            command.contains("flight") || command.contains("penerbangan") -> openActivity(FlightTrackingActivity::class.java, "Membuka Flight Tracking.")
            command.contains("pencarian") || command.contains("cari manifest") -> {
                speak("Pencarian manifest tersedia dari menu utama, sir.")
                setStatus?.invoke("Perintah dikenali: Pencarian Manifest")
            }
            command.contains("manifest") -> {
                speak("Manifest cargo tersedia dari menu utama, sir.")
                setStatus?.invoke("Perintah dikenali: Manifest Cargo")
            }
            command.contains("halo") || command.contains("hai") || command.contains("hello") -> {
                speak("Good evening, sir. JARVIS is online.")
                setStatus?.invoke("JARVIS online.")
            }
            command.contains("siapa kamu") -> speak("I am your Cargo Assistant. JARVIS is online and ready.")
            else -> {
                speak("Maaf, perintah itu belum tersedia. Saya masih dalam tahap awal.")
                setStatus?.invoke("Perintah belum tersedia.")
            }
        }
    }

    private fun openActivity(clazz: Class<out Activity>, message: String) {
        setStatus?.invoke(message)
        speak(message)
        startActivity(Intent(this, clazz))
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
