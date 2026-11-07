package nl.jaap.filamentspools.ui.printer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jaap.filamentspools.data.db.ProductWithCounts
import nl.jaap.filamentspools.data.db.SpoolWithProduct
import nl.jaap.filamentspools.ui.common.Swatch
import nl.jaap.filamentspools.ui.common.formatLocal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(onOpenSpool: (String) -> Unit, vm: PrinterViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmUseUp by remember { mutableStateOf<SpoolWithProduct?>(null) }
    val uri = LocalUriHandler.current

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        placeholder = { Text("Search name, number or color") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.offlineSince?.let { at ->
                    item { Banner("Offline — last synced " + formatLocal(at, "HH:mm")) }
                }
                if (state.offlineSince == null && state.neverSynced && !state.refreshing && state.syncError == null &&
                    state.onHolder.isEmpty() && state.boxed.isEmpty()
                ) {
                    item { Banner("Not synced yet — pull down to refresh") }
                }
                state.syncError?.let { item { Banner("Sync failed: $it") } }

                section("On the holder", state.onHolder, empty = "Nothing on the holder") { row ->
                    SpoolCard(row, onClick = { onOpenSpool(row.spool.uuid) }) {
                        if (state.canWrite) {
                            OutlinedButton(onClick = { confirmUseUp = row }, enabled = row.spool.uuid !in state.busyUuids) {
                                Text("Used up")
                            }
                        }
                    }
                }

                if (state.runningOut.isNotEmpty()) {
                    item { SectionTitle("Running out") }
                    items(state.runningOut, key = { "out-" + it.product.id }) { p ->
                        RunningOutCard(p, onBuy = p.product.buyUrl?.let { url -> { uri.openUri(url) } })
                    }
                }

                section("Boxed", state.boxed, empty = "No boxed spools") { row ->
                    SpoolCard(row, onClick = { onOpenSpool(row.spool.uuid) }) {
                        if (state.canWrite) {
                            val busy = row.spool.uuid in state.busyUuids
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmUseUp = row }, enabled = !busy) { Text("Used up") }
                                Button(onClick = { vm.open(row.spool.uuid) }, enabled = !busy) { Text("Open") }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmUseUp?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmUseUp = null },
            title = { Text("Mark used up?") },
            text = { Text("${row.product?.articleName ?: "This spool"} will count as used in the usage graphs.") },
            confirmButton = { TextButton(onClick = { vm.useUp(row.spool.uuid); confirmUseUp = null }) { Text("Used up") } },
            dismissButton = { TextButton(onClick = { confirmUseUp = null }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<SpoolWithProduct>,
    empty: String,
    card: @Composable (SpoolWithProduct) -> Unit,
) {
    item { SectionTitle(title) }
    if (rows.isEmpty()) {
        item { Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    items(rows, key = { title + it.spool.uuid }) { card(it) }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
private fun Banner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, Modifier.padding(12.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun SpoolCard(row: SpoolWithProduct, onClick: () -> Unit, actions: @Composable () -> Unit) {
    val p = row.product
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(p?.colorHex, p?.colorHex2)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p?.articleName ?: "Unknown product", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(p?.line, p?.articleNumber, if (row.spool.isRefill) "refill" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.spool.location?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            actions()
        }
    }
}

@Composable
private fun RunningOutCard(p: ProductWithCounts, onBuy: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(p.product.colorHex, p.product.colorHex2)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.product.articleName, style = MaterialTheme.typography.titleSmall)
                Text("${p.onSpool} on the holder, none boxed", style = MaterialTheme.typography.bodySmall)
            }
            if (onBuy != null) FilledTonalButton(onClick = onBuy) { Text("Buy") }
        }
    }
}
