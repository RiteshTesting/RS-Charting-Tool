package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.EodFeedPreferences
import com.example.data.repository.NseStockRepository
import com.example.domain.TechnicalIndicatorCalculator
import com.example.model.AlertCondition
import com.example.model.DataSource
import com.example.model.MaCondition
import com.example.model.MaType
import com.example.model.NamedWatchlist
import com.example.model.NseIndexUniverse
import com.example.model.NseSearchSuggestion
import com.example.model.NseStockItem
import com.example.model.PriceAlert
import com.example.model.RsiFilterMode
import com.example.model.SavedPreset
import com.example.model.ScreenerFilterState
import com.example.model.SortOption
import com.example.model.Timeframe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScreenerUiState(
    val isInitialLoading: Boolean = true,
    val isScanningIndicators: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val filter: ScreenerFilterState = ScreenerFilterState(),
    val rawStocks: List<NseStockItem> = emptyList(),
    val filteredStocks: List<NseStockItem> = emptyList(),
    val namedWatchlists: List<NamedWatchlist> = emptyList(),
    val selectedWatchlistId: Long? = null,
    val watchlistStocksMap: Map<String, NseStockItem> = emptyMap(),
    val watchlistSymbols: Set<String> = emptySet(),
    val watchlistPickerStock: NseStockItem? = null,
    val alertSheetStock: NseStockItem? = null,
    val priceAlerts: List<PriceAlert> = emptyList(),
    val savedPresets: List<SavedPreset> = emptyList(),
    val searchSuggestions: List<NseSearchSuggestion> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val dataSource: DataSource = DataSource.NSE_DIRECT,
    val lastUpdatedMillis: Long? = null,
    val eodLastUpdated: String? = null,
    val eodFeedUrl: String = EodFeedPreferences.DEFAULT_EOD_PATH,
    val isRefreshingEod: Boolean = false,
    val eodStatusMessage: String? = null,
    val errorMessage: String? = null,
    val selectedDetailSymbol: String? = null,
    val chartNavigationSymbols: List<String> = emptyList(),
    val scannerCategory: com.example.domain.ScannerCategory = com.example.domain.ScannerCategory.ALL,
    val scannerQuery: String = "",
    val isMultiScanMode: Boolean = false,
    val selectedScannerIds: Set<String> = emptySet()
) {
    val activeWatchlist: NamedWatchlist?
        get() = namedWatchlists.firstOrNull { it.id == selectedWatchlistId }
            ?: namedWatchlists.firstOrNull()

    val activeWatchlistStocks: List<NseStockItem>
        get() {
            val wl = activeWatchlist ?: return emptyList()
            val rawBySymbol = rawStocks.associateBy { it.symbol }
            return wl.symbols.mapNotNull { sym ->
                watchlistStocksMap[sym] ?: rawBySymbol[sym]?.copy(isWatchlisted = true)
            }
        }
}

class ScreenerViewModel(
    private val repository: NseStockRepository,
    private val eodFeedPreferences: EodFeedPreferences? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenerUiState())
    private var scanJob: Job? = null
    private var searchJob: Job? = null

    val uiState: StateFlow<ScreenerUiState> = combine(
        _state,
        repository.watchlistSymbols,
        repository.namedWatchlists,
        repository.savedPresets,
        repository.priceAlerts
    ) { base, watchSet, watchlists, presets, alerts ->
        val enrichedRaw = base.rawStocks.map { it.copy(isWatchlisted = it.symbol in watchSet) }
        val filtered = TechnicalIndicatorCalculator.sortStocks(
            stocks = enrichedRaw.filter { TechnicalIndicatorCalculator.matchesFilter(it, base.filter) },
            filter = base.filter
        )
        val validSelectedId = base.selectedWatchlistId?.takeIf { id -> watchlists.any { it.id == id } }
            ?: watchlists.firstOrNull()?.id

        base.copy(
            rawStocks = enrichedRaw,
            filteredStocks = filtered,
            watchlistSymbols = watchSet,
            namedWatchlists = watchlists,
            selectedWatchlistId = validSelectedId,
            savedPresets = presets,
            priceAlerts = alerts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScreenerUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultWatchlist()
        }
        if (eodFeedPreferences != null) {
            viewModelScope.launch {
                val initialUrl = runCatching { eodFeedPreferences.eodUrl.firstOrNull() }.getOrNull()
                if (!initialUrl.isNullOrBlank()) {
                    _state.update { it.copy(eodFeedUrl = initialUrl) }
                }
                refreshEodData(customUrl = initialUrl, fallbackToNse = true)
                eodFeedPreferences.eodUrl.collect { savedUrl ->
                    _state.update { it.copy(eodFeedUrl = savedUrl) }
                }
            }
        } else {
            refreshEodData(fallbackToNse = true)
        }
        observeWatchlistUpdates()
    }

    fun refreshEodData(
        customUrl: String? = null,
        fallbackToNse: Boolean = true
    ) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val targetUrl = customUrl?.trim()?.ifEmpty { null }
                ?: _state.value.eodFeedUrl.takeIf { it.isNotBlank() }
                ?: EodFeedPreferences.DEFAULT_EOD_PATH

            _state.update {
                it.copy(
                    isInitialLoading = it.rawStocks.isEmpty(),
                    isRefreshingEod = true,
                    errorMessage = null,
                    eodStatusMessage = null
                )
            }

            val payload = repository.fetchEodScreenerData(targetUrl)
            if (payload != null && payload.stocks.isNotEmpty()) {
                _state.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isScanningIndicators = false,
                        isRefreshingEod = false,
                        scannedCount = payload.stocks.size,
                        totalCount = payload.stocks.size,
                        rawStocks = payload.stocks,
                        dataSource = DataSource.EOD_JSON,
                        eodLastUpdated = payload.lastUpdated,
                        lastUpdatedMillis = System.currentTimeMillis(),
                        eodStatusMessage = "Loaded ${payload.stocks.size} stocks from ${payload.originLabel}",
                        errorMessage = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isRefreshingEod = false,
                        eodLastUpdated = null,
                        eodStatusMessage = "screener_data.json not found locally; using live NSE feed"
                    )
                }
                if (fallbackToNse) {
                    loadUniverseAndScan(forceRefresh = true)
                }
            }
        }
    }

    fun updateEodFeedUrl(url: String) {
        val cleaned = url.trim().ifEmpty { EodFeedPreferences.DEFAULT_EOD_PATH }
        _state.update { it.copy(eodFeedUrl = cleaned) }
        viewModelScope.launch {
            eodFeedPreferences?.setEodUrl(cleaned)
            refreshEodData(customUrl = cleaned, fallbackToNse = true)
        }
    }

    fun importLocalEodJson(jsonContent: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshingEod = true, eodStatusMessage = null) }
            val payload = repository.importEodScreenerJson(jsonContent)
            if (payload != null && payload.stocks.isNotEmpty()) {
                runCatching { eodFeedPreferences?.saveLocalEodJson(jsonContent) }
                _state.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isScanningIndicators = false,
                        isRefreshingEod = false,
                        scannedCount = payload.stocks.size,
                        totalCount = payload.stocks.size,
                        rawStocks = payload.stocks,
                        dataSource = DataSource.EOD_JSON,
                        eodLastUpdated = payload.lastUpdated,
                        lastUpdatedMillis = System.currentTimeMillis(),
                        eodStatusMessage = "Imported ${payload.stocks.size} EOD stocks",
                        errorMessage = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isRefreshingEod = false,
                        eodStatusMessage = "Invalid screener_data.json format"
                    )
                }
            }
        }
    }

    private fun observeWatchlistUpdates() {
        viewModelScope.launch {
            repository.watchlistSymbols.collect { symbols ->
                refreshWatchlistSymbols(symbols)
            }
        }
    }

    private fun refreshWatchlistSymbols(symbols: Set<String>) {
        if (symbols.isEmpty()) {
            _state.update { it.copy(watchlistStocksMap = emptyMap()) }
            return
        }
        viewModelScope.launch {
            val tf = _state.value.filter.timeframe
            val hydrated = repository.refreshWatchlistSymbols(symbols, tf)
            _state.update { it.copy(watchlistStocksMap = hydrated.associateBy { s -> s.symbol }) }
        }
    }

    fun loadUniverseAndScan(forceRefresh: Boolean = false) {
        scanJob?.cancel()
        val currentFilter = _state.value.filter
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isInitialLoading = it.rawStocks.isEmpty(),
                    isScanningIndicators = true,
                    scannedCount = 0,
                    errorMessage = null
                )
            }
            val result = repository.scanUniverse(
                universe = currentFilter.universe,
                timeframe = currentFilter.timeframe,
                forceRefresh = forceRefresh
            ) { progress ->
                _state.update { state ->
                    state.copy(
                        isInitialLoading = progress.stocks.isEmpty(),
                        isScanningIndicators = !progress.isComplete,
                        scannedCount = progress.scannedCount,
                        totalCount = progress.totalCount,
                        rawStocks = mergeCustomSearchStocks(state.rawStocks, progress.stocks),
                        dataSource = progress.primarySource,
                        lastUpdatedMillis = if (progress.isComplete) System.currentTimeMillis() else state.lastUpdatedMillis
                    )
                }
            }

            result.onSuccess { finalUpdate ->
                _state.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isScanningIndicators = false,
                        scannedCount = finalUpdate.scannedCount,
                        totalCount = finalUpdate.totalCount,
                        rawStocks = finalUpdate.stocks,
                        dataSource = finalUpdate.primarySource,
                        lastUpdatedMillis = System.currentTimeMillis(),
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _state.update { state ->
                    state.copy(
                        isInitialLoading = false,
                        isScanningIndicators = false,
                        errorMessage = err.message ?: "Failed to load NSE market data"
                    )
                }
            }
        }
    }

    private fun mergeCustomSearchStocks(
        existing: List<NseStockItem>,
        incoming: List<NseStockItem>
    ): List<NseStockItem> {
        val incomingMap = incoming.associateBy { it.symbol }
        val extras = existing.filter { it.symbol !in incomingMap && it.sector == "NSE Equity" }
        return incoming + extras
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(filter = it.filter.copy(query = query)) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(searchSuggestions = emptyList(), isSearchingRemote = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350L)
            _state.update { it.copy(isSearchingRemote = true) }
            val suggestions = repository.searchNseEquities(trimmed)
            _state.update { it.copy(searchSuggestions = suggestions, isSearchingRemote = false) }
        }
    }

    fun selectSearchSuggestion(suggestion: NseSearchSuggestion, openDetail: (String) -> Unit) {
        _state.update {
            it.copy(
                filter = it.filter.copy(query = suggestion.symbol),
                searchSuggestions = emptyList()
            )
        }
        viewModelScope.launch {
            val tf = _state.value.filter.timeframe
            repository.fetchSingleStockWithTimeframe(suggestion.symbol, tf).onSuccess { stock ->
                _state.update { state ->
                    val withoutDup = state.rawStocks.filterNot { it.symbol == stock.symbol }
                    state.copy(rawStocks = listOf(stock) + withoutDup)
                }
            }
            openDetail(suggestion.symbol)
        }
    }

    fun selectUniverse(universe: NseIndexUniverse) {
        if (_state.value.filter.universe == universe) return
        _state.update {
            it.copy(
                filter = it.filter.copy(universe = universe, query = ""),
                rawStocks = emptyList(),
                searchSuggestions = emptyList()
            )
        }
        loadUniverseAndScan(forceRefresh = false)
    }

    fun selectTimeframe(timeframe: Timeframe) {
        if (_state.value.filter.timeframe == timeframe) return
        _state.update { it.copy(filter = it.filter.copy(timeframe = timeframe)) }
        loadUniverseAndScan(forceRefresh = false)
        viewModelScope.launch {
            val symbols = uiState.value.watchlistSymbols
            if (symbols.isNotEmpty()) {
                refreshWatchlistSymbols(symbols)
            }
        }
    }

    fun updateFilter(transform: (ScreenerFilterState) -> ScreenerFilterState) {
        _state.update { it.copy(filter = transform(it.filter)) }
    }

    fun applyQuickPreset(
        rsiMode: RsiFilterMode = RsiFilterMode.ANY,
        maCondition: MaCondition = MaCondition.ANY,
        maType: MaType = _state.value.filter.maType,
        maPeriod: Int = _state.value.filter.maPeriod
    ) {
        _state.update { state ->
            state.copy(
                filter = state.filter.copy(
                    rsiFilterMode = rsiMode,
                    maCondition = maCondition,
                    maType = maType,
                    maPeriod = maPeriod
                )
            )
        }
    }

    fun resetFilters() {
        _state.update { state ->
            state.copy(
                filter = state.filter.copy(
                    query = "",
                    rsiPeriod = 14,
                    rsiFilterMode = RsiFilterMode.ANY,
                    rsiMin = 0f,
                    rsiMax = 100f,
                    maType = MaType.EMA,
                    maPeriod = 50,
                    maCondition = MaCondition.ANY,
                    minVolRatio = 0f,
                    minRsRating = 0f,
                    maxDistFrom52wPct = 100f,
                    sortOption = SortOption.RSI_ASC
                ),
                searchSuggestions = emptyList()
            )
        }
    }

    // Named Watchlist Actions
    fun openWatchlistPicker(stock: NseStockItem) {
        _state.update { it.copy(watchlistPickerStock = stock) }
    }

    fun dismissWatchlistPicker() {
        _state.update { it.copy(watchlistPickerStock = null) }
    }

    fun selectWatchlist(watchlistId: Long) {
        _state.update { it.copy(selectedWatchlistId = watchlistId) }
    }

    fun createWatchlist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createWatchlist(name)
            _state.update { it.copy(selectedWatchlistId = id) }
        }
    }

    fun renameWatchlist(watchlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameWatchlist(watchlistId, newName)
        }
    }

    fun deleteWatchlist(watchlistId: Long) {
        viewModelScope.launch {
            repository.deleteWatchlist(watchlistId)
        }
    }

    fun toggleStockInWatchlist(stock: NseStockItem, watchlistId: Long) {
        viewModelScope.launch {
            repository.toggleStockInWatchlist(stock, watchlistId)
        }
    }

    fun saveStockToNewWatchlistName(stock: NseStockItem, watchlistName: String) {
        if (watchlistName.isBlank()) return
        viewModelScope.launch {
            val id = repository.saveStockToWatchlistByName(stock, watchlistName)
            _state.update { it.copy(selectedWatchlistId = id) }
        }
    }

    fun removeStockFromActiveWatchlist(symbol: String) {
        val watchlistId = uiState.value.activeWatchlist?.id ?: return
        viewModelScope.launch {
            repository.removeStockFromWatchlist(watchlistId, symbol)
        }
    }

    fun saveCurrentPreset(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveFilterPreset(name, _state.value.filter)
        }
    }

    fun applySavedPreset(preset: SavedPreset) {
        val timeframeChanged = _state.value.filter.timeframe != preset.timeframe
        _state.update { state ->
            state.copy(
                filter = state.filter.copy(
                    timeframe = preset.timeframe,
                    rsiPeriod = preset.rsiPeriod,
                    rsiFilterMode = preset.rsiFilterMode,
                    rsiMin = preset.rsiMin,
                    rsiMax = preset.rsiMax,
                    maType = preset.maType,
                    maPeriod = preset.maPeriod,
                    maCondition = preset.maCondition
                )
            )
        }
        if (timeframeChanged) {
            loadUniverseAndScan(forceRefresh = false)
        }
    }

    fun deletePreset(id: Int) {
        viewModelScope.launch {
            repository.deletePreset(id)
        }
    }

    fun setSelectedDetailSymbol(symbol: String?) {
        _state.update { it.copy(selectedDetailSymbol = symbol) }
    }

    fun setChartNavigationQueue(symbols: List<String>) {
        _state.update { it.copy(chartNavigationSymbols = symbols.distinct()) }
    }

    fun stepSelectedDetailSymbol(delta: Int): String? {
        val currentState = uiState.value
        val list = currentState.filteredStocks.map { it.symbol }
        if (list.isEmpty()) return null
        val currentSym = currentState.selectedDetailSymbol ?: list.first()
        val next = stepSymbolInQueue(list, currentSym, delta) ?: return null
        _state.update { it.copy(selectedDetailSymbol = next) }
        return next
    }

    companion object {
        fun stepSymbolInQueue(
            queue: List<String>,
            currentSymbol: String,
            delta: Int
        ): String? {
            if (queue.isEmpty()) return null
            val idx = queue.indexOf(currentSymbol)
            val nextIdx = if (idx < 0) {
                0
            } else {
                ((idx + delta) % queue.size + queue.size) % queue.size
            }
            return queue[nextIdx]
        }
    }

    fun selectScannerCategory(category: com.example.domain.ScannerCategory) {
        _state.update { it.copy(scannerCategory = category) }
    }

    fun onScannerQueryChanged(query: String) {
        _state.update { it.copy(scannerQuery = query) }
    }

    fun toggleMultiScanMode() {
        _state.update {
            val next = !it.isMultiScanMode
            it.copy(
                isMultiScanMode = next,
                selectedScannerIds = if (next) it.selectedScannerIds else emptySet()
            )
        }
    }

    fun toggleScannerSelection(scannerId: String) {
        _state.update { state ->
            val updated = if (scannerId in state.selectedScannerIds) {
                state.selectedScannerIds - scannerId
            } else {
                state.selectedScannerIds + scannerId
            }
            state.copy(selectedScannerIds = updated)
        }
    }

    fun clearSelectedScanners() {
        _state.update { it.copy(selectedScannerIds = emptySet()) }
    }

    fun openPriceAlertSheet(stock: NseStockItem) {
        _state.update { it.copy(alertSheetStock = stock) }
    }

    fun dismissPriceAlertSheet() {
        _state.update { it.copy(alertSheetStock = null) }
    }

    fun createPriceAlert(
        stock: NseStockItem,
        targetPrice: Double,
        condition: AlertCondition,
        onCreated: () -> Unit
    ) {
        viewModelScope.launch {
            repository.createPriceAlert(
                symbol = stock.symbol,
                companyName = stock.companyName,
                targetPrice = targetPrice,
                condition = condition,
                lastKnownPrice = stock.lastPrice
            )
            onCreated()
        }
    }

    fun togglePriceAlertActive(alertId: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.setPriceAlertActive(alertId, isActive)
        }
    }

    fun deletePriceAlert(alertId: Long) {
        viewModelScope.launch {
            repository.deletePriceAlert(alertId)
        }
    }

    class Factory(
        private val repository: NseStockRepository,
        private val eodFeedPreferences: EodFeedPreferences? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScreenerViewModel(repository, eodFeedPreferences) as T
        }
    }
}
