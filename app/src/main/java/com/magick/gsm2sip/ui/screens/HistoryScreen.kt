package com.magick.gsm2sip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.magick.gsm2sip.data.CallDirection
import com.magick.gsm2sip.data.CallRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Call history showing both the GSM and SIP legs with timestamps. */
@Composable
fun HistoryScreen(
    history: List<CallRecord>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        if (history.isEmpty()) {
            Text("No calls yet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history) { record -> HistoryRow(record) }
        }
    }
}

@Composable
private fun HistoryRow(record: CallRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val dir = when (record.direction) {
                CallDirection.SIP_TO_GSM -> "SIP → GSM"
                CallDirection.GSM_TO_SIP -> "GSM → SIP"
            }
            Text(dir, style = MaterialTheme.typography.titleSmall)
            Text("SIP leg:  ${record.sipRemote}", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
            Text("GSM leg:  ${record.gsmNumber}", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
            Text("Started:  ${fmt(record.startedAtMillis)}",
                style = MaterialTheme.typography.bodySmall)
            record.gsmConnectedAtMillis?.let {
                Text("GSM up:   ${fmt(it)}", style = MaterialTheme.typography.bodySmall)
            }
            record.endedAtMillis?.let {
                Text("Ended:    ${fmt(it)}  (${record.durationSeconds ?: 0}s)",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Audio bridged: ${record.audioBridged}   ·   ${record.result}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private val FMT = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
private fun fmt(ts: Long): String = FMT.format(Date(ts))
