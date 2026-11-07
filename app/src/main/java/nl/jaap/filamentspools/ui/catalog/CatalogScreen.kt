package nl.jaap.filamentspools.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jaap.filamentspools.data.db.OwnedFilter
import nl.jaap.filamentspools.ui.common.Swatch

private val ownedLabels = mapOf(
    OwnedFilter.All to "All",
    OwnedFilter.Owned to "Owned",
    OwnedFilter.NotOwned to "Not owned",
    OwnedFilter.Boxed to "Boxed",
    OwnedFilter.OnSpool to "On holder",
    OwnedFilter.RunningOut to "Running out",
)

@Composable
fun CatalogScreen(onOpenProduct: (Long) -> Unit, vm: CatalogViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.filters

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            placeholder = { Text("Search the catalog") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip("Owned", ownedLabels.getValue(f.owned).takeIf { f.owned != OwnedFilter.All }, ownedLabels.values.toList()) { label ->
                vm.onFilters(f.copy(owned = ownedLabels.entries.first { it.value == label }.key))
            }
            OptionChip("Material", f.material, state.options.materials) { vm.onFilters(f.copy(material = it)) }
            OptionChip("Brand", f.brand, state.options.brands) { vm.onFilters(f.copy(brand = it)) }
            OptionChip("Line", f.line, state.options.lines) { vm.onFilters(f.copy(line = it)) }
            OptionChip("Color", f.color, state.options.colors) { vm.onFilters(f.copy(color = it)) }
            if (f.active > 0) TextButton(onClick = { vm.onFilters(CatalogFilters()) }) { Text("Clear") }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (state.products.isEmpty()) {
                item { Text("No products match", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.products, key = { it.product.id }) { row ->
                val p = row.product
                ListItem(
                    modifier = Modifier.clickable { onOpenProduct(p.id) },
                    leadingContent = { Swatch(p.colorHex, p.colorHex2) },
                    headlineContent = { Text(p.articleName) },
                    supportingContent = { Text(listOfNotNull(p.line, p.articleNumber).joinToString(" · ")) },
                    trailingContent = {
                        if (row.owned > 0) {
                            Row {
                                Badge { Text("${row.owned}") }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Chip opening a list of values; picking "Any" clears it. */
@Composable
private fun OptionChip(label: String, selected: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        FilterChip(selected = selected != null, onClick = { open = true }, label = { Text(selected ?: label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Any ${label.lowercase()}") }, onClick = { onSelect(null); open = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: String?, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        FilterChip(selected = selected != null, onClick = { open = true }, label = { Text(selected ?: label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
