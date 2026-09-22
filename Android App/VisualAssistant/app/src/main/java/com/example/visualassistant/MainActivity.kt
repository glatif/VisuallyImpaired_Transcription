package com.example.visualassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.visualassistant.ui.CaptureScreen
import com.example.visualassistant.ui.HomeScreen
import com.example.visualassistant.ui.StatsScreen
import com.example.visualassistant.ui.theme.VisualAssistantTheme
import com.example.visualassistant.viewmodel.AppScreen
import com.example.visualassistant.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private var pendingStartAfterPermission = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                viewModel.startSession()
            }
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisualAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        viewModel = viewModel,
                        onRequestStart = { ensureCameraThenStart() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Prototype scope: pause/stop the loop when backgrounded (no foreground Service).
        if (viewModel.ui.value.screen == AppScreen.CAPTURE) {
            viewModel.endSession()
        }
    }

    private fun ensureCameraThenStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startSession()
            else -> {
                pendingStartAfterPermission = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

@Composable
private fun AppRoot(
    viewModel: SessionViewModel,
    onRequestStart: () -> Unit
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    when (state.screen) {
        AppScreen.HOME -> {
            val context = LocalContext.current
            HomeScreen(
                state = state,
                onRetrySetup = viewModel::ensureModelReady,
                onStartSession = onRequestStart,
                onClearError = viewModel::clearError,
                onSetSimilarity = viewModel::setSimilarityThreshold,
                onSetOverlap = viewModel::setOverlapThreshold,
                onSetPiHostname = viewModel::setPiHostname,
                onSetSpeechRate = viewModel::setSpeechRate,
                onSetSpeechPitch = viewModel::setSpeechPitch,
                onSetSelectedVoice = viewModel::setSelectedVoice,
                onSetSelectedEngine = viewModel::setSelectedEngine,
                onPreviewVoice = viewModel::previewVoice,
                onDownloadVoices = {
                    try {
                        val intent = viewModel.getInstallTtsIntent()
                        context.startActivity(intent)
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Unable to open system TTS settings", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        AppScreen.CAPTURE -> CaptureScreen(
            state = state,
            viewModel = viewModel,
            onEndSession = viewModel::endSession
        )
        AppScreen.STATS -> StatsScreen(
            stats = state.stats,
            onDone = viewModel::backToHome
        )
    }
}
