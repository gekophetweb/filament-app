package nl.jaap.filamentspools.ui.spool

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jaap.filamentspools.ui.catalog.FormField
import nl.jaap.filamentspools.ui.common.Swatch
import nl.jaap.filamentspools.ui.common.eventLabel
import nl.jaap.filamentspools.ui.common.statusLabel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Dialog { UseUp, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoolScreen(onBack: () -> Unit, vm: SpoolViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.goneMessage) {
        state.goneMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val row = state.spool
    val s = row?.spool
    val p = row?.product

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p?.articleName ?: "Spool") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.canEdit && state.editing == null) {
                        IconButton(onClick = vm::startEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (s == null) return@Scaffold
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(p?.colorHex, p?.colorHex2, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(statusLabel(s.status), style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(p?.line, p?.articleNumber).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }

            val edit = state.editing
            if (edit != null) {
                EditSection(edit, state, vm)
            } else {
                Detail("Refill", if (s.isRefill) "Yes" else "No")
                Detail("Location", s.location ?: "—")
                Detail("Purchased", s.purchasedAt ?: "—")
                Detail("Price", s.price ?: "—")
                Detail("Notes", s.notes ?: "—")
            }

            if (edit == null && (state.canOpen || state.canUseUp || state.canRemove)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canOpen) Button(onClick = vm::open, enabled = !state.busy) { Text("Open") }
                    if (state.canUseUp) OutlinedButton(onClick = { dialog = Dialog.UseUp }, enabled = !state.busy) { Text("Used up") }
                    Spacer(Modifier.weight(1f))
                    if (state.canRemove) TextButton(onClick = { dialog = Dialog.Remove }, enabled = !state.busy) { Text("Remove") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("History", style = MaterialTheme.typography.titleMedium)
            state.events.forEach { e ->
                ListItem(
                    headlineContent = { Text(eventLabel(e.type)) },
                    supportingContent = e.note?.let { { Text(it) } },
                    trailingContent = { Text(formatEventTime(e.createdAt), style = MaterialTheme.typography.bodySmall) },
                )
            }
            state.eventsError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.hasMoreEvents) TextButton(onClick = vm::loadMoreEvents) { Text("Load more") }
        }
    }

    when (dialog) {
        Dialog.UseUp -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Mark used up?") },
            text = { Text("It will count as used in the usage graphs.") },
            confirmButton = { TextButton(onClick = { vm.useUp(); dialog = null }) { Text("Used up") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        Dialog.Remove -> RemoveDialog(onDismiss = { dialog = null }, onRemove = { note -> vm.remove(note); dialog = null })
        null -> Unit
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun EditSection(edit: EditForm, state: SpoolUiState, vm: SpoolViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Refill (no plastic spool)", Modifier.weight(1f))
        Switch(checked = edit.isRefill, onCheckedChange = { vm.onEdit(edit.copy(isRefill = it)) })
    }
    FormField("Purchase date (YYYY-MM-DD)", edit.purchasedAt, state.fieldErrors["purchased_at"], KeyboardType.Number) {
        vm.onEdit(edit.copy(purchasedAt = it))
    }
    FormField("Price", edit.price, state.fieldErrors["price"], KeyboardType.Decimal) { vm.onEdit(edit.copy(price = it)) }
    FormField("Location", edit.location, state.fieldErrors["location"]) { vm.onEdit(edit.copy(location = it)) }
    FormField("Notes", edit.notes, state.fieldErrors["notes"], singleLine = false) { vm.onEdit(edit.copy(notes = it)) }
    state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::saveEdit, enabled = !state.busy) { Text("Save") }
        TextButton(onClick = vm::cancelEdit) { Text("Cancel") }
    }
}

@Composable
private fun RemoveDialog(onDismiss: () -> Unit, onRemove: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this spool?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use this for mistakes or spools sold/given away — it is not counted as used.")
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onRemove(note) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatEventTime(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("d MMM HH:mm").format(OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()))
    }.getOrDefault(value)
