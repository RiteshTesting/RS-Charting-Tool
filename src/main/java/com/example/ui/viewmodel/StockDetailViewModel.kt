package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.NseStockRepository
import com.example.model.AlertCondition
import com.example.model.NamedWatchlist
import com.example.model.NseStockItem
import com.example.model.PriceAlert
import com.example.model.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StockDetailUiState(
    val symbol: String = "",
    val isLoading: Boolean = true,
    val stock: NseStockItem? = null,
    val namedWatchlists: List<NamedWatchlist> = emptyList(),
    val alertsForStock: List<PriceAlert> = emptyList(),
    val selectedTimeframe: Timeframe = Timeframe.D1,
    val showCandlesticks: Boolean = true,
    val showEma9: Boolean = false,
    val showEma20: Boolean = true,
    val showEma45: Boolean = false,
    val showSma50: Boolean = true,
    val showSma200: Boolean = true,
    val showBollingerBands: Boolean = true,
    val showVolume: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class StockDetailViewModel(
    initialSymbol: String,
    initialTimeframe: Timeframe,
    private val repository: NseStockRepository
) : ViewModel() {

    private val _symbol = MutableStateFlow(initialSymbol.trim().uppercase())
    private val _state = MutableStateFlow(
        StockDetailUiState(
            symbol = initialSymbol.trim().uppercase(),
            isLoading = true,
            stock = repository.getCachedStock(initialSymbol),
            selectedTimeframe = initialTimeframe
        )
    )
    private var loadJob: Job? = null

    val uiState: StateFlow<StockDetailUiState> = combine(
        _state,
        repository.watchlistSymbols,
        repository.namedWatchlists,
        _symbol.flatMapLatest { sym -> repository.observePriceAlertsForSymbol(sym) }
    ) { state, watchSet, watchlists, alerts ->
        state.copy(
            stock = state.stock?.copy(isWatchlisted = state.symbol in watchSet),
            namedWatchlists = watchlists,
            alertsForStock = alerts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _state.value
    )

    init {
        loadAllTimeframes(forceRefresh = false)
    }

    fun switchSymbol(newSymbol: String) {
        val clean = newSymbol.trim().uppercase()
        if (clean.isEmpty() || clean == _symbol.value) return
        _symbol.value = clean
        val cached = repository.getCachedStock(clean)
        _state.update {
            it.copy(
                symbol = clean,
                stock = cached,
                isLoading = cached == null,
                errorMessage = null
            )
        }
        loadAllTimeframes(forceRefresh = false)
    }

    fun loadAllTimeframes(forceRefresh: Boolean = false) {
        val targetSymbol = _symbol.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.stock == null, errorMessage = null) }
            repository.loadAllTimeframesForStock(targetSymbol, forceRefresh)
                .onSuccess { item ->
                    if (_symbol.value == targetSymbol) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                stock = item,
                                errorMessage = null
                            )
                        }
                    }
                }
                .onFailure { err ->
                    if (_symbol.value == targetSymbol) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.message ?: "Failed to load NSE technical data"
                            )
                        }
                    }
                }
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        _state.update { it.copy(selectedTimeframe = timeframe) }
    }

    fun toggleChartStyle() {
        _state.update { it.copy(showCandlesticks = !it.showCandlesticks) }
    }

    fun toggleEma9() {
        _state.update { it.copy(showEma9 = !it.showEma9) }
    }

    fun toggleEma20() {
        _state.update { it.copy(showEma20 = !it.showEma20) }
    }

    fun toggleEma45() {
        _state.update { it.copy(showEma45 = !it.showEma45) }
    }

    fun toggleSma50() {
        _state.update { it.copy(showSma50 = !it.showSma50) }
    }

    fun toggleSma200() {
        _state.update { it.copy(showSma200 = !it.showSma200) }
    }

    fun toggleBollingerBands() {
        _state.update { it.copy(showBollingerBands = !it.showBollingerBands) }
    }

    fun toggleVolume() {
        _state.update { it.copy(showVolume = !it.showVolume) }
    }

    fun toggleStockInWatchlist(watchlistId: Long) {
        val current = _state.value.stock ?: return
        viewModelScope.launch {
            repository.toggleStockInWatchlist(current, watchlistId)
        }
    }

    fun saveStockToWatchlistByName(watchlistName: String) {
        val current = _state.value.stock ?: return
        if (watchlistName.isBlank()) return
        viewModelScope.launch {
            repository.saveStockToWatchlistByName(current, watchlistName)
        }
    }

    fun createPriceAlert(
        targetPrice: Double,
        condition: AlertCondition,
        onCreated: () -> Unit
    ) {
        val current = _state.value.stock ?: return
        viewModelScope.launch {
            repository.createPriceAlert(
                symbol = current.symbol,
                companyName = current.companyName,
                targetPrice = targetPrice,
                condition = condition,
                lastKnownPrice = current.lastPrice
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
        private val symbol: String,
        private val initialTimeframe: Timeframe,
        private val repository: NseStockRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StockDetailViewModel(symbol, initialTimeframe, repository) as T
        }
    }
}
