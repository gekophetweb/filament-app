package nl.jaap.filamentspools.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jaap.filamentspools.ui.common.Swatch
import nl.jaap.filamentspools.ui.common.statusLabel
import nl.jaap.filamentspools.ui.printer.SpoolCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(onBack: () -> Unit, onOpenSpool: (String) -> Unit, vm: ProductViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val row = state.product
    val p = row?.product

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p?.articleName ?: "Product") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (row == null || p == null) {
            Text("This product is not in the cached catalog", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(p.colorHex, p.colorHex2, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(listOfNotNull(p.brand, p.line).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            p.articleNumber,
                            p.diameter?.let { "$it mm" },
                            p.nominalWeight?.let { "$it g" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("${row.boxed} boxed · ${row.onSpool} on the holder · ${row.owned} owned")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.buyUrl?.let { url -> OutlinedButton(onClick = { uri.openUri(url) }) { Text("Buy") } }
                p.productUrl?.let { url -> OutlinedButton(onClick = { uri.openUri(url) }) { Text("Product page") } }
            }

            if (state.spools.isNotEmpty()) {
                HorizontalDivider()
                Text("Your spools", style = MaterialTheme.typography.titleMedium)
                state.spools.forEach { s ->
                    SpoolCard(s, onClick = { onOpenSpool(s.spool.uuid) }) { Text(statusLabel(s.spool.status)) }
                }
            }

            if (state.canWrite) {
                HorizontalDivider()
                AddSpoolSection(state, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSpoolSection(state: ProductUiState, vm: ProductViewModel) {
    val f = state.form
    Text("Add spool", style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !f.onSpool, onClick = { vm.onForm(f.copy(onSpool = false)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text("Boxed")
        }
        SegmentedButton(selected = f.onSpool, onClick = { vm.onForm(f.copy(onSpool = true)) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text("On the holder")
        }
    }
    if (state.showRefill) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Refill (no plastic spool)", Modifier.weight(1f))
            Switch(checked = f.isRefill, onCheckedChange = { vm.onForm(f.copy(isRefill = it)) })
        }
    }
    FormField("Purchase date (YYYY-MM-DD)", f.purchasedAt, state.fieldErrors["purchased_at"], KeyboardType.Number) {
        vm.onForm(f.copy(purchasedAt = it))
    }
    FormField("Price", f.price, state.fieldErrors["price"], KeyboardType.Decimal) { vm.onForm(f.copy(price = it)) }
    FormField("Location", f.location, state.fieldErrors["location"]) { vm.onForm(f.copy(location = it)) }
    FormField("Notes", f.notes, state.fieldErrors["notes"], singleLine = false) { vm.onForm(f.copy(notes = it)) }
    (state.formError ?: state.fieldErrors["filament_product_id"] ?: state.fieldErrors["status"])?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = vm::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
        if (state.saving) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Add spool")
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    error: String?,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
