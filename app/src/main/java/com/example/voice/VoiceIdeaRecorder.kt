package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.TargetLanguage

sealed interface VoiceCaptureState {
    data object Idle : VoiceCaptureState
    data class Listening(val partialText: String = "", val rmsLevel: Float = 0f) : VoiceCaptureState
    data class Error(val message: String) : VoiceCaptureState
}

class VoiceToTextController(
    private val context: Context,
    private val onStateChanged: (VoiceCaptureState) -> Unit,
    private val onFinalTranscript: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(language: TargetLanguage) {
        if (!isAvailable()) {
            onStateChanged(
                VoiceCaptureState.Error(
                    "On-device speech recognizer is unavailable. Use system voice prompt or type your idea."
                )
            )
            return
        }

        destroy()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val localeTag = when (language) {
            TargetLanguage.BENGALI -> "bn-IN"
            TargetLanguage.HINGLISH -> "en-IN"
            TargetLanguage.ENGLISH -> "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onStateChanged(VoiceCaptureState.Listening(partialText = "", rmsLevel = 0.1f))
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                val currentPartial = ""
                onStateChanged(VoiceCaptureState.Listening(partialText = currentPartial, rmsLevel = normalized))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone availability."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error during speech recognition. Please retry."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Speak clearly and try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to speak again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
                    else -> "Voice recognition stopped (code $error)."
                }
                onStateChanged(VoiceCaptureState.Error(message))
            }

            override fun onResults(results: Bundle?) {
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                onStateChanged(VoiceCaptureState.Idle)
                if (transcript.isNotEmpty()) {
                    onFinalTranscript(transcript)
                } else {
                    onStateChanged(VoiceCaptureState.Error("Couldn't catch that. Try speaking again."))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    onStateChanged(VoiceCaptureState.Listening(partialText = partial, rmsLevel = 0.5f))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onStateChanged(VoiceCaptureState.Listening(partialText = "Listening...", rmsLevel = 0.15f))
        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

@Composable
fun VoiceIdeaRecorderCard(
    context: Context,
    selectedLanguage: TargetLanguage,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onLaunchSystemSpeechFallback: (Intent) -> Unit,
    onTranscriptReady: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var captureState by remember { mutableStateOf<VoiceCaptureState>(VoiceCaptureState.Idle) }
    var livePartialText by remember { mutableStateOf("") }
    var liveRms by remember { mutableFloatStateOf(0f) }
    var autoAnalyzeOnStop by remember { mutableStateOf(true) }

    val controller = remember(context) {
        VoiceToTextController(
            context = context,
            onStateChanged = { nextState ->
                when (nextState) {
                    is VoiceCaptureState.Listening -> {
                        if (nextState.partialText.isNotBlank() && nextState.partialText != "Listening...") {
                            livePartialText = nextState.partialText
                        }
                        liveRms = nextState.rmsLevel
                        captureState = VoiceCaptureState.Listening(
                            partialText = livePartialText,
                            rmsLevel = liveRms
                        )
                    }
                    is VoiceCaptureState.Idle -> {
                        livePartialText = ""
                        liveRms = 0f
                        captureState = VoiceCaptureState.Idle
                    }
                    is VoiceCaptureState.Error -> {
                        livePartialText = ""
                        liveRms = 0f
                        captureState = nextState
                    }
                }
            },
            onFinalTranscript = { text ->
                onTranscriptReady(text, autoAnalyzeOnStop)
            }
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }

    val isListening = captureState is VoiceCaptureState.Listening
    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1f + (liveRms * 0.14f) else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "micPulseScale"
    )
    val borderTint by animateColorAsState(
        targetValue = if (isListening) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        },
        label = "voiceCardBorder"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderTint),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .scale(pulseScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Voice idea recorder",
                            tint = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isListening) "Listening (${selectedLanguage.label})..." else "Voice-to-Text Idea Capture",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isListening) {
                            livePartialText.ifBlank { "Speak your Reel/Short hook or video concept aloud" }
                        } else {
                            "Dictate your video idea directly for instant Viral Growth Kit analysis"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isListening) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (isListening) {
                LinearProgressIndicator(
                    progress = { liveRms.coerceIn(0.08f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }

            if (captureState is VoiceCaptureState.Error) {
                Text(
                    text = (captureState as VoiceCaptureState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isListening) {
                            controller.stopListening()
                        } else if (!hasAudioPermission) {
                            onRequestPermission()
                        } else if (controller.isAvailable()) {
                            autoAnalyzeOnStop = true
                            controller.startListening(selectedLanguage)
                        } else {
                            val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Speak your video idea for instant ViralKit analysis"
                                )
                            }
                            onLaunchSystemSpeechFallback(fallbackIntent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        contentColor = if (isListening) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSecondary
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("voice_record_instant_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Stop & Analyze" else "Speak & Instant Audit",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (!isListening) {
                    OutlinedButton(
                        onClick = {
                            if (!hasAudioPermission) {
                                onRequestPermission()
                            } else if (controller.isAvailable()) {
                                autoAnalyzeOnStop = false
                                controller.startListening(selectedLanguage)
                            } else {
                                val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                }
                                onLaunchSystemSpeechFallback(fallbackIntent)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("voice_dictate_only_button")
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Dictate text only",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Dictate")
                    }
                }
            }
        }
    }
}
