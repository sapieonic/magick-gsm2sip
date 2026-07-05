package com.magick.gsm2sip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.data.SipTransport

/**
 * SIP credential + gateway settings. Includes the one-tap VoBiz preset button
 * that auto-fills the trunk defaults while keeping every field overridable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: SipConfig,
    onSave: (SipConfig) -> Unit,
    onApplyVoBizPreset: (SipConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local editable copy; committed on Save.
    var draft by remember(config) { mutableStateOf(config) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onApplyVoBizPreset(draft) },
                modifier = Modifier.weight(1f),
            ) { Text("Apply VoBiz preset") }
            Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) {
                Text("Save")
            }
        }
        Text(
            "VoBiz preset fills in sip.vobiz.com, STUN, 300s registration, 60s keep-alive " +
                "and the G.722→G.711 codec order. Credentials are preserved.",
            style = MaterialTheme.typography.bodySmall,
        )
        Divider()

        SectionLabel("SIP account")
        Field("SIP server / domain", draft.domain) { draft = draft.copy(domain = it) }
        Field("Username", draft.username) { draft = draft.copy(username = it) }
        Field("Password", draft.password, isPassword = true) { draft = draft.copy(password = it) }
        Field("Realm", draft.realm) { draft = draft.copy(realm = it) }
        Field("Display name", draft.displayName) { draft = draft.copy(displayName = it) }
        NumberField("Port", draft.port) { draft = draft.copy(port = it) }
        TransportDropdown(draft.transport) { draft = draft.copy(transport = it) }

        SectionLabel("Outbound / caller ID")
        Field("Outbound proxy (optional)", draft.outboundProxy) { draft = draft.copy(outboundProxy = it) }
        Field("Caller ID / DID (optional)", draft.callerId) { draft = draft.copy(callerId = it) }

        SectionLabel("NAT / STUN")
        SwitchRow("Enable STUN", draft.stunEnabled) { draft = draft.copy(stunEnabled = it) }
        Field("STUN server", draft.stunServer) { draft = draft.copy(stunServer = it) }

        SectionLabel("Registration")
        NumberField("Registration expiry (s)", draft.regExpirySeconds) {
            draft = draft.copy(regExpirySeconds = it)
        }
        NumberField("Keep-alive OPTIONS (s)", draft.keepAliveSeconds) {
            draft = draft.copy(keepAliveSeconds = it)
        }

        SectionLabel("Behaviour")
        SwitchRow("Auto-answer inbound GSM", draft.autoAnswerGsm) {
            draft = draft.copy(autoAnswerGsm = it)
        }
        SwitchRow("RFC 2833 DTMF (telephone-event)", draft.dtmfRfc2833) {
            draft = draft.copy(dtmfRfc2833 = it)
        }
        SwitchRow("Auto-start on boot", draft.autoStartOnBoot) {
            draft = draft.copy(autoStartOnBoot = it)
        }

        SectionLabel("Codec priority")
        Text(
            draft.codecPriority.joinToString("  →  "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Field(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportDropdown(current: SipTransport, onChange: (SipTransport) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Transport") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SipTransport.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.name) }, onClick = {
                    onChange(t); expanded = false
                })
            }
        }
    }
}
