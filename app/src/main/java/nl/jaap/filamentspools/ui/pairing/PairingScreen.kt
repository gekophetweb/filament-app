package nl.jaap.filamentspools.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PairingScreen(message: String?, vm: PairingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanning = granted
    }

    if (scanning) {
        Box(Modifier.fillMaxSize()) {
            QrScanner(onResult = { scanning = false; vm.onScanned(it) }, modifier = Modifier.fillMaxSize())
            TextButton(onClick = { scanning = false }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Text("Cancel")
            }
        }
        return
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to Filament Spools", style = MaterialTheme.typography.headlineSmall)
            Text(
                "On the Devices page of the web app, choose Show QR code, then scan it here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        scanning = true
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, Modifier.size(24.dp))
                Text("  Scan QR code")
            }
            PasteQr(onUse = vm::onScanned)
            HorizontalDivider()
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::onServerUrl,
                label = { Text("Server address") },
                supportingText = {
                    Text("Use your computer's network address, e.g. http://192.168.1.20:8000 (emulator: http://10.0.2.2:8000)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = vm::onCode,
                label = { Text("Pairing code") },
                supportingText = state.expiresAt?.let { at ->
                    { Text("Valid until " + DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(ZoneId.systemDefault()))) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = vm::onDeviceName,
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Connect")
            }
        }
    }
}

@Composable
private fun PasteQr(onUse: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    if (!open) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Paste QR text instead") }
        return
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("QR text (JSON)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { open = false }) { Text("Close") }
        TextButton(onClick = { onUse(text); open = false }, enabled = text.isNotBlank()) { Text("Use") }
    }
}
