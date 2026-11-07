package nl.jaap.filamentspools.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jaap.filamentspools.ui.common.formatLocal

private enum class Confirm { SignOut, ChangeServer }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(headlineContent = { Text("Server") }, supportingContent = { Text(state.serverUrl) })
            ListItem(headlineContent = { Text("Signed in as") }, supportingContent = { Text(state.userName) })
            ListItem(headlineContent = { Text("Token") }, supportingContent = { Text(state.tokenType) })
            ListItem(
                headlineContent = { Text("Last synced") },
                supportingContent = {
                    Text(
                        when {
                            state.syncing -> "Syncing…"
                            state.lastSync != null -> formatLocal(state.lastSync!!, "d MMM yyyy HH:mm")
                            else -> "Never"
                        },
                    )
                },
            )
            state.syncError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::syncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                    Text("Sync now")
                }
                OutlinedButton(onClick = { confirm = Confirm.ChangeServer }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change server")
                }
                OutlinedButton(onClick = { confirm = Confirm.SignOut }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
                Text(
                    "Signing out removes the token from this phone only. Revoke it on the Devices page to disable it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (which == Confirm.SignOut) "Sign out?" else "Change server?") },
            text = {
                Text(
                    if (which == Confirm.SignOut) {
                        "The token and the cached spools are removed from this phone."
                    } else {
                        "You will sign out and pair again with the new server."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (which == Confirm.SignOut) vm.signOut() else vm.changeServer()
                }) { Text(if (which == Confirm.SignOut) "Sign out" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
