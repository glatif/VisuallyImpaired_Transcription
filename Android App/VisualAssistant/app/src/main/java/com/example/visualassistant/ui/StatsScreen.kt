package com.example.visualassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.visualassistant.stats.SessionSnapshot
import java.util.concurrent.TimeUnit

@Composable
fun StatsScreen(
    stats: SessionSnapshot?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Session Stats", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (stats == null) {
            Text("No stats available.")
        } else {
            StatLine("Session ID", stats.sessionId)
            StatLine("Frames processed", stats.framesProcessed.toString())
            
            Spacer(Modifier.height(16.dp))
            Text("Pipeline Breakdown (Averages)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            StatLine("Preprocessing (CPU)", "%.1f ms".format(stats.avgPrepMs))
            StatLine("Encoder (TTFT)", "%.1f ms".format(stats.avgTtftMs))
            StatLine("Decoder (LLM)", "%.1f ms".format(stats.avgDecodeMs))
            StatLine("Network Fetch (Wi-Fi)", "%.1f ms".format(stats.avgNetworkMs))
            StatLine("Full Pipeline", "%.1f ms".format(stats.avgCombinedMs))
            StatLine("Avg Payload Size", "%.1f KB".format(stats.avgPayloadKb))

            Spacer(Modifier.height(16.dp))
            Text("General Inference", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            StatLine("Min inference", "${stats.minInferenceMs} ms")
            StatLine("Max inference", "${stats.maxInferenceMs} ms")
            
            Spacer(Modifier.height(16.dp))
            Text("Memory & System", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            StatLine("Peak PSS", formatKb(stats.peakPssKb))
            StatLine("Avg PSS", formatKb(stats.avgPssKb.toInt()))
            StatLine("Peak JVM heap", "${stats.peakHeapMb} MB")
            StatLine("Avg JVM heap", "%.1f MB".format(stats.avgHeapMb))
            StatLine("Session duration", formatDuration(stats.sessionDurationMs))
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatKb(kb: Int): String {
    return if (kb >= 1024) "%.1f MB".format(kb / 1024.0) else "$kb KB"
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d (%d ms)".format(minutes, seconds, ms)
}
