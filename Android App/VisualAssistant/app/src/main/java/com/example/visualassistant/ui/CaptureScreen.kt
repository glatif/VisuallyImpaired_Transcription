package com.example.visualassistant.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.visualassistant.camera.CameraController
import com.example.visualassistant.viewmodel.HighlightRange
import com.example.visualassistant.viewmodel.LoopPhase
import com.example.visualassistant.viewmodel.SessionUiState
import com.example.visualassistant.viewmodel.SessionViewModel

@Composable
fun CaptureScreen(
    state: SessionUiState,
    viewModel: SessionViewModel,
    onEndSession: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraController = remember { CameraController() }
    val latestPreview by cameraController.latestPreview.collectAsState()
    val latestViewModel by rememberUpdatedState(viewModel)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        // Use Crossfade to transition between the polling feed and the high-res capture
        Crossfade(targetState = state.capturedBitmap, label = "vision_fade") { captured ->
            if (captured != null) {
                // High-res color freeze frame during inference
                Image(
                    bitmap = captured.asImageBitmap(),
                    contentDescription = "Captured Frame",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Polling feed
                latestPreview?.let { preview ->
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Network Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Connecting to Pi Camera...", color = Color.Gray)
                }
            }
        }

        LaunchedEffect(Unit) {
            try {
                cameraController.startNetworkStream(scope, state.piHostname)
                latestViewModel.registerCamera(
                    takePicture = { hostname -> cameraController.takePictureWithMetadata(hostname) },
                    hashFlow = cameraController.latestHash,
                    setPaused = { paused -> cameraController.setPaused(paused) },
                    unbind = { cameraController.stop() }
                )
            } catch (_: Throwable) {
            }
        }

        DisposableEffect(Unit) {
            onDispose { cameraController.stop() }
        }

        // Top chrome sits below the status bar; caption bar sits above the nav bar.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = phaseLabel(state.phase) +
                    if (state.frameIndex > 0) "  ·  frame ${state.frameIndex}" else "",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Button(
                onClick = onEndSession,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text("End Session")
            }
        }

        // Overlay Notification Badge
        androidx.compose.animation.AnimatedVisibility(
            visible = state.notificationMessage != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            if (state.notificationMessage != null) {
                Text(
                    text = state.notificationMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        CaptionBar(
            caption = state.caption,
            highlight = state.highlight,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun CaptionBar(
    caption: String,
    highlight: HighlightRange?,
    modifier: Modifier = Modifier
) {
    val annotated = remember(caption, highlight) {
        buildAnnotatedString {
            if (caption.isEmpty()) {
                append("Waiting for first caption…")
                return@buildAnnotatedString
            }
            val start = highlight?.start?.coerceIn(0, caption.length) ?: -1
            val end = highlight?.end?.coerceIn(0, caption.length) ?: -1
            if (start in 0 until end) {
                append(caption.substring(0, start))
                withStyle(
                    SpanStyle(
                        background = Color(0xFFFFEB3B),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(caption.substring(start, end))
                }
                append(caption.substring(end))
            } else {
                append(caption)
            }
        }
    }

    Text(
        text = annotated,
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(16.dp)
    )
}

private fun phaseLabel(phase: LoopPhase): String = when (phase) {
    LoopPhase.IDLE -> "Ready…"
    LoopPhase.CAPTURING -> "Capturing…"
    LoopPhase.INFERRING -> "Captioning…"
    LoopPhase.SPEAKING -> "Speaking…"
    LoopPhase.WAITING_CAPTION -> "Captioning…"
    LoopPhase.ENDING -> "Ending…"
    LoopPhase.ENDED -> "Ended"
}
