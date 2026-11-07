package nl.jaap.filamentspools.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import nl.jaap.filamentspools.data.db.OwnedFilter
import nl.jaap.filamentspools.data.db.ProductDao
import nl.jaap.filamentspools.data.db.ProductWithCounts
import javax.inject.Inject

data class CatalogFilters(
    val material: String? = null,
    val brand: String? = null,
    val line: String? = null,
    val color: String? = null,
    val owned: OwnedFilter = OwnedFilter.All,
) {
    val active: Int get() = listOfNotNull(material, brand, line, color).size + if (owned != OwnedFilter.All) 1 else 0
}

data class FilterOptions(
    val materials: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val lines: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
)

data class CatalogUiState(
    val query: String = "",
    val filters: CatalogFilters = CatalogFilters(),
    val products: List<ProductWithCounts> = emptyList(),
    val options: FilterOptions = FilterOptions(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(productDao: ProductDao) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(CatalogFilters())

    private val products = combine(query, filters, ::Pair).flatMapLatest { (q, f) ->
        productDao.observeFiltered(q, f.material, f.brand, f.line, f.color, f.owned)
    }

    private val options = combine(
        productDao.distinctMaterials(),
        productDao.distinctBrands(),
        productDao.distinctLines(),
        productDao.distinctColors(),
        ::FilterOptions,
    )

    val state: StateFlow<CatalogUiState> =
        combine(query, filters, products, options, ::CatalogUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogUiState())

    fun onQuery(value: String) {
        query.value = value
    }

    fun onFilters(value: CatalogFilters) {
        filters.value = value
    }
}
