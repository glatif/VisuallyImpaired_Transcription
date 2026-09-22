package com.example.visualassistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.visualassistant.ui.components.GlassesAnimation
import com.example.visualassistant.viewmodel.SessionUiState

@Composable
fun HomeScreen(
    state: SessionUiState,
    onRetrySetup: () -> Unit,
    onStartSession: () -> Unit,
    onClearError: () -> Unit,
    onSetSimilarity: (Int) -> Unit,
    onSetOverlap: (Int) -> Unit,
    onSetPiHostname: (String) -> Unit = {},
    onSetSpeechRate: (Float) -> Unit = {},
    onSetSpeechPitch: (Float) -> Unit = {},
    onSetSelectedVoice: (String) -> Unit = {},
    onSetSelectedEngine: (String) -> Unit = {},
    onPreviewVoice: () -> Unit = {},
    onDownloadVoices: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val infiniteTransition = rememberInfiniteTransition(label = "ai_core_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // App Title area
        Text(
            text = "Visual Assistant",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "On-device AI for assistive vision",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(28.dp))

        // AI Core / Status area
        Box(contentAlignment = Alignment.Center) {
            if (state.modelLoading || state.isWarmingUp) {
                Spacer(
                    Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .alpha(glowAlpha)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crossfade(targetState = state.modelStatus, label = "status_text") { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (state.modelLoading || state.isWarmingUp) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Progress indicators for download
        AnimatedVisibility(
            visible = state.modelLoading && state.downloadFraction != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { state.downloadFraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
                state.downloadProgressText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Error area
        state.errorMessage?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Error: $err",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onClearError()
                    onRetrySetup()
                },
                enabled = !state.modelLoading && !state.isWarmingUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retry Connection")
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- GLASSES CONNECTION STATUS ---
        GlassesAnimation(isOnline = state.glassesOnline)
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = (if (state.glassesOnline) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (state.glassesOnline) Color(0xFF4CAF50) else Color.Gray, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.glassesOnline) "Glasses Online" else "Searching for Glasses...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.glassesOnline) Color(0xFF2E7D32) else Color.Gray
                )
            }
        }

        // ================= SESSION SETTINGS =================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Session Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))

                // --- VISION & MOTION ---
                Text(
                    text = "Vision & Motion Gate",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Motion Sensitivity Slider
                val animatedSimScale by animateFloatAsState(
                    targetValue = 1f + (state.similarityThreshold / 128f),
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "sim_scale"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Motion Sensitivity",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.scale(animatedSimScale)
                    ) {
                        Text(
                            text = "${state.similarityThreshold}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = state.similarityThreshold.toFloat(),
                    onValueChange = { onSetSimilarity(it.toInt()) },
                    valueRange = 0f..64f,
                    steps = 64,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Redundancy Limit Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Redundancy Limit",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${state.overlapThreshold} words",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = state.overlapThreshold.toFloat(),
                    onValueChange = { onSetOverlap(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 9
                )

                Spacer(Modifier.height(16.dp))

                // --- NETWORK CAMERA SETTINGS ---
                Text(
                    text = "Remote Camera (Raspberry Pi)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = state.piHostname,
                    onValueChange = onSetPiHostname,
                    label = { Text("Pi Hostname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                Spacer(Modifier.height(16.dp))

                // --- VOICE & SPEECH SETTINGS ---
                Text(
                    text = "Voice & Speech (Local English)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Local Voice Selector Dropdown
                var voiceMenuExpanded by remember { mutableStateOf(false) }
                val selectedVoiceObj = state.availableVoices.find { it.name == state.selectedVoiceName }
                val selectedVoiceLabel = selectedVoiceObj?.displayName
                    ?: state.selectedVoiceName
                    ?: if (state.availableVoices.isNotEmpty()) "Select Local English Voice" else "Loading local English voices..."

                Text(
                    text = "Local Voice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { voiceMenuExpanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = selectedVoiceLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Voice"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        if (state.availableVoices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No local offline English voices found") },
                                onClick = { voiceMenuExpanded = false }
                            )
                        } else {
                            state.availableVoices.forEach { v ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = v.displayName,
                                            fontWeight = if (v.name == state.selectedVoiceName) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSetSelectedVoice(v.name)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // TTS Engine Selector (if multiple exist)
                if (state.availableEngines.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    var engineMenuExpanded by remember { mutableStateOf(false) }
                    val currentEngine = state.availableEngines.find { it.packageName == state.selectedEnginePackage }
                    val currentEngineLabel = currentEngine?.label ?: "Default TTS Engine"

                    Text(
                        text = "Speech Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { engineMenuExpanded = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = currentEngineLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Engine"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = engineMenuExpanded,
                            onDismissRequest = { engineMenuExpanded = false }
                        ) {
                            state.availableEngines.forEach { eng ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = eng.label,
                                            fontWeight = if (eng.packageName == state.selectedEnginePackage) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSetSelectedEngine(eng.packageName)
                                        engineMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Speech Rate Slider with smooth animation
                val animatedRateScale by animateFloatAsState(
                    targetValue = state.speechRate,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "rate_scale"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Speech Rate",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "${"%.1f".format(animatedRateScale)}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = state.speechRate,
                    onValueChange = { onSetSpeechRate(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 15
                )

                Spacer(Modifier.height(8.dp))

                // Pitch Slider
                val animatedPitchScale by animateFloatAsState(
                    targetValue = state.speechPitch,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "pitch_scale"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Voice Pitch",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${"%.1f".format(animatedPitchScale)}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = state.speechPitch,
                    onValueChange = { onSetSpeechPitch(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 10
                )

                Spacer(Modifier.height(12.dp))

                // Action Buttons: Test Voice & Download Offline Voices
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviewVoice,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Test Voice", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onDownloadVoices,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download Voices", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action Button
        AnimatedVisibility(
            visible = state.modelReady && !state.isWarmingUp,
            enter = fadeIn() + scaleIn(initialScale = 0.8f)
        ) {
            Button(
                onClick = onStartSession,
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Start Vision Session", fontWeight = FontWeight.Bold)
            }
        }

        if (!state.modelReady && !state.modelLoading && !state.isWarmingUp && state.errorMessage == null) {
            OutlinedButton(
                onClick = onRetrySetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Initialize System")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
