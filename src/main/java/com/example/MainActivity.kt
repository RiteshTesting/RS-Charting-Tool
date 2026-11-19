package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.AppDatabase
import com.example.data.local.AppThemeMode
import com.example.data.local.EodFeedPreferences
import com.example.data.local.ThemePreferences
import com.example.data.remote.EodScreenerDataClient
import com.example.data.repository.NseStockRepository
import com.example.notifications.PriceAlertNotificationHelper
import com.example.notifications.PriceAlertScheduler
import com.example.ui.components.PriceAlertSheet
import com.example.ui.screens.ScannerResultsScreen
import com.example.ui.screens.ScannersScreen
import com.example.ui.screens.ScreenerScreen
import com.example.ui.screens.StockDetailScreen
import com.example.ui.screens.WatchlistScreen
import com.example.ui.theme.NsePulseTheme
import com.example.ui.viewmodel.ScreenerViewModel
import com.example.ui.viewmodel.StockDetailViewModel
import kotlinx.coroutines.launch

object Routes {
    const val SCREENER = "screener"
    const val SCANNERS = "scanners"
    const val SCANNER_RESULTS = "scanner_results/{scannerIds}"
    const val WATCHLIST = "watchlist"
    const val STOCK_DETAIL = "stock/{symbol}"
    fun stockDetail(symbol: String) = "stock/$symbol"
    fun scannerResults(scannerIds: List<String>) = "scanner_results/${scannerIds.joinToString(",")}"
}

class MainActivity : ComponentActivity() {
    private var pendingOpenSymbol by mutableStateOf<String?>(null)
    private var stockKeyStepper: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PriceAlertNotificationHelper.ensureChannel(this)
        pendingOpenSymbol = intent?.getStringExtra(PriceAlertNotificationHelper.EXTRA_OPEN_SYMBOL)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePreferences = remember(context) { ThemePreferences(context) }
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM
            )
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.DAY -> false
                AppThemeMode.NIGHT -> true
                AppThemeMode.SYSTEM -> systemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            val toggleDayNight: () -> Unit = {
                val nextMode = if (isDarkTheme) AppThemeMode.DAY else AppThemeMode.NIGHT
                scope.launch { themePreferences.setThemeMode(nextMode) }
            }
            val selectThemeMode: (AppThemeMode) -> Unit = { mode ->
                scope.launch { themePreferences.setThemeMode(mode) }
            }

            NsePulseTheme(darkTheme = isDarkTheme) {
                NsePulseApp(
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onToggleDayNight = toggleDayNight,
                    onSelectThemeMode = selectThemeMode,
                    pendingOpenSymbol = pendingOpenSymbol,
                    onConsumePendingSymbol = { pendingOpenSymbol = null },
                    onRegisterStockKeyStepper = { handler -> stockKeyStepper = handler }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenSymbol = intent.getStringExtra(PriceAlertNotificationHelper.EXTRA_OPEN_SYMBOL)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (stockKeyStepper?.invoke(-1) == true) return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (stockKeyStepper?.invoke(1) == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun NsePulseApp(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isDarkTheme: Boolean = true,
    onToggleDayNight: () -> Unit = {},
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    pendingOpenSymbol: String? = null,
    onConsumePendingSymbol: () -> Unit = {},
    onRegisterStockKeyStepper: (((Int) -> Boolean)?) -> Unit = {}
) {
    val context = LocalContext.current
    val eodFeedPreferences = remember(context) { EodFeedPreferences(context) }
    val repository = remember(context) {
        NseStockRepository(
            dao = AppDatabase.getInstance(context).stockDao(),
            eodClient = EodScreenerDataClient(context)
        )
    }
    val screenerViewModel: ScreenerViewModel = viewModel(
        factory = ScreenerViewModel.Factory(repository, eodFeedPreferences)
    )
    val uiState by screenerViewModel.uiState.collectAsStateWithLifecycle()
    val isCheckingAlerts by remember(context) {
        PriceAlertScheduler.observeImmediateCheckRunning(context)
    }.collectAsStateWithLifecycle(initialValue = false)

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isDetailRoute = currentDestination?.route?.startsWith("stock/") == true ||
        currentDestination?.route?.startsWith("scanner_results/") == true

    val hasActiveAlerts = remember(uiState.priceAlerts) {
        uiState.priceAlerts.any { it.isActive }
    }
    LaunchedEffect(hasActiveAlerts) {
        PriceAlertScheduler.syncBackgroundMonitoring(context, hasActiveAlerts)
    }

    LaunchedEffect(pendingOpenSymbol) {
        val sym = pendingOpenSymbol
        if (!sym.isNullOrBlank()) {
            val fallbackQueue = uiState.filteredStocks.map { it.symbol }.ifEmpty { listOf(sym) }
            screenerViewModel.setChartNavigationQueue(fallbackQueue)
            navController.navigate(Routes.stockDetail(sym))
            onConsumePendingSymbol()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isExpandedWidth && !isDetailRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.SCREENER } == true,
                            onClick = {
                                navController.navigate(Routes.SCREENER) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.route == Routes.SCREENER } == true
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.CandlestickChart else Icons.Outlined.CandlestickChart,
                                    contentDescription = stringResource(R.string.nav_screener)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_screener)) },
                            modifier = Modifier.testTag("nav_tab_screener")
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.SCANNERS } == true,
                            onClick = {
                                navController.navigate(Routes.SCANNERS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.route == Routes.SCANNERS } == true
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.AutoGraph else Icons.Outlined.AutoGraph,
                                    contentDescription = stringResource(R.string.nav_scanners)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_scanners)) },
                            modifier = Modifier.testTag("nav_tab_scanners")
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.WATCHLIST } == true,
                            onClick = {
                                navController.navigate(Routes.WATCHLIST) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.route == Routes.WATCHLIST } == true
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.nav_watchlist)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_watchlist)) },
                            modifier = Modifier.testTag("nav_tab_watchlist")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedWidth) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.SCREENER } == true,
                            onClick = {
                                navController.navigate(Routes.SCREENER) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.CandlestickChart,
                                    contentDescription = stringResource(R.string.nav_screener)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_screener)) }
                        )
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.SCANNERS } == true,
                            onClick = {
                                navController.navigate(Routes.SCANNERS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.AutoGraph,
                                    contentDescription = stringResource(R.string.nav_scanners)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_scanners)) }
                        )
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.route == Routes.WATCHLIST } == true,
                            onClick = {
                                navController.navigate(Routes.WATCHLIST) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = stringResource(R.string.nav_watchlist)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_watchlist)) }
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.SCREENER,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.SCREENER) {
                        val openStock: (String) -> Unit = { symbol ->
                            val queue = uiState.filteredStocks.map { it.symbol }
                                .ifEmpty { listOf(symbol) }
                            screenerViewModel.setChartNavigationQueue(queue)
                            if (isExpandedWidth) {
                                screenerViewModel.setSelectedDetailSymbol(symbol)
                            } else {
                                navController.navigate(Routes.stockDetail(symbol))
                            }
                        }

                        if (isExpandedWidth) {
                            val screenerQueue = remember(uiState.filteredStocks) {
                                uiState.filteredStocks.map { it.symbol }
                            }
                            val currentModalsOpen = uiState.watchlistPickerStock != null || uiState.alertSheetStock != null
                            DisposableEffect(screenerQueue, currentModalsOpen) {
                                onRegisterStockKeyStepper { delta ->
                                    if (currentModalsOpen || screenerQueue.size <= 1) {
                                        false
                                    } else {
                                        screenerViewModel.stepSelectedDetailSymbol(delta) != null
                                    }
                                }
                                onDispose { onRegisterStockKeyStepper(null) }
                            }

                            Row(modifier = Modifier.fillMaxSize()) {
                                ScreenerScreen(
                                    uiState = uiState,
                                    themeMode = themeMode,
                                    isDarkTheme = isDarkTheme,
                                    onToggleDayNight = onToggleDayNight,
                                    onSelectThemeMode = onSelectThemeMode,
                                    onQueryChanged = screenerViewModel::onQueryChanged,
                                    onSelectSuggestion = { suggestion ->
                                        screenerViewModel.selectSearchSuggestion(suggestion, openStock)
                                    },
                                    onSelectUniverse = screenerViewModel::selectUniverse,
                                    onSelectTimeframe = screenerViewModel::selectTimeframe,
                                    onUpdateFilter = screenerViewModel::updateFilter,
                                    onApplyQuickPreset = screenerViewModel::applyQuickPreset,
                                    onResetFilters = screenerViewModel::resetFilters,
                                    onSavePreset = screenerViewModel::saveCurrentPreset,
                                    onRefreshEod = { screenerViewModel.refreshEodData(fallbackToNse = true) },
                                    onUpdateEodFeedUrl = screenerViewModel::updateEodFeedUrl,
                                    onImportLocalEodJson = screenerViewModel::importLocalEodJson,
                                    onRefresh = { screenerViewModel.refreshEodData(fallbackToNse = true) },
                                    onStockClick = openStock,
                                    onToggleWatchlist = screenerViewModel::openWatchlistPicker,
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                                val detailSymbol = uiState.selectedDetailSymbol
                                    ?: uiState.filteredStocks.firstOrNull()?.symbol
                                Box(modifier = Modifier.weight(1.1f)) {
                                    if (detailSymbol != null) {
                                        val detailVm: StockDetailViewModel = viewModel(
                                            key = "expanded_detail_pane",
                                            factory = StockDetailViewModel.Factory(
                                                symbol = detailSymbol,
                                                initialTimeframe = uiState.filter.timeframe,
                                                repository = repository
                                            )
                                        )
                                        LaunchedEffect(detailSymbol) {
                                            detailVm.switchSymbol(detailSymbol)
                                        }
                                        val detailState by detailVm.uiState.collectAsStateWithLifecycle()
                                        StockDetailScreen(
                                            uiState = detailState,
                                            showBackButton = false,
                                            stockQueue = screenerQueue,
                                            onSwitchSymbol = { nextSym ->
                                                screenerViewModel.setSelectedDetailSymbol(nextSym)
                                            },
                                            themeMode = themeMode,
                                            isDarkTheme = isDarkTheme,
                                            onToggleDayNight = onToggleDayNight,
                                            onSelectThemeMode = onSelectThemeMode,
                                            onBack = {},
                                            onSelectTimeframe = detailVm::selectTimeframe,
                                            onToggleChartStyle = detailVm::toggleChartStyle,
                                            onToggleEma9 = detailVm::toggleEma9,
                                            onToggleEma20 = detailVm::toggleEma20,
                                            onToggleEma45 = detailVm::toggleEma45,
                                            onToggleSma50 = detailVm::toggleSma50,
                                            onToggleSma200 = detailVm::toggleSma200,
                                            onToggleBollingerBands = detailVm::toggleBollingerBands,
                                            onToggleVolume = detailVm::toggleVolume,
                                            onToggleStockInWatchlist = detailVm::toggleStockInWatchlist,
                                            onSaveStockToWatchlistByName = detailVm::saveStockToWatchlistByName,
                                            onCreatePriceAlert = { target, cond ->
                                                detailVm.createPriceAlert(target, cond) {
                                                    PriceAlertScheduler.triggerImmediateCheck(context)
                                                }
                                            },
                                            onTogglePriceAlertActive = { id, active ->
                                                detailVm.togglePriceAlertActive(id, active)
                                                if (active) PriceAlertScheduler.triggerImmediateCheck(context)
                                            },
                                            onDeletePriceAlert = detailVm::deletePriceAlert,
                                            onRefresh = { detailVm.loadAllTimeframes(forceRefresh = true) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Select an NSE stock to inspect multi-timeframe RSI & Moving Averages",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            ScreenerScreen(
                                uiState = uiState,
                                themeMode = themeMode,
                                isDarkTheme = isDarkTheme,
                                onToggleDayNight = onToggleDayNight,
                                onSelectThemeMode = onSelectThemeMode,
                                onQueryChanged = screenerViewModel::onQueryChanged,
                                onSelectSuggestion = { suggestion ->
                                    screenerViewModel.selectSearchSuggestion(suggestion, openStock)
                                },
                                onSelectUniverse = screenerViewModel::selectUniverse,
                                onSelectTimeframe = screenerViewModel::selectTimeframe,
                                onUpdateFilter = screenerViewModel::updateFilter,
                                onApplyQuickPreset = screenerViewModel::applyQuickPreset,
                                onResetFilters = screenerViewModel::resetFilters,
                                onSavePreset = screenerViewModel::saveCurrentPreset,
                                onRefreshEod = { screenerViewModel.refreshEodData(fallbackToNse = true) },
                                onUpdateEodFeedUrl = screenerViewModel::updateEodFeedUrl,
                                onImportLocalEodJson = screenerViewModel::importLocalEodJson,
                                onRefresh = { screenerViewModel.refreshEodData(fallbackToNse = true) },
                                onStockClick = openStock,
                                onToggleWatchlist = screenerViewModel::openWatchlistPicker
                            )
                        }
                    }

                    composable(Routes.SCANNERS) {
                        ScannersScreen(
                            uiState = uiState,
                            themeMode = themeMode,
                            isDarkTheme = isDarkTheme,
                            onToggleDayNight = onToggleDayNight,
                            onSelectThemeMode = onSelectThemeMode,
                            onSelectUniverse = screenerViewModel::selectUniverse,
                            onSelectTimeframe = screenerViewModel::selectTimeframe,
                            onSelectCategory = screenerViewModel::selectScannerCategory,
                            onScannerQueryChanged = screenerViewModel::onScannerQueryChanged,
                            onToggleMultiScanMode = screenerViewModel::toggleMultiScanMode,
                            onToggleScannerSelection = screenerViewModel::toggleScannerSelection,
                            onClearSelectedScanners = screenerViewModel::clearSelectedScanners,
                            onOpenScannerResults = { ids ->
                                navController.navigate(Routes.scannerResults(ids))
                            },
                            onRefresh = { screenerViewModel.loadUniverseAndScan(forceRefresh = true) }
                        )
                    }

                    composable(
                        route = Routes.SCANNER_RESULTS,
                        arguments = listOf(navArgument("scannerIds") { type = NavType.StringType })
                    ) { entry ->
                        val rawIds = entry.arguments?.getString("scannerIds").orEmpty()
                        val ids = rawIds.split(",").filter { it.isNotBlank() }
                        ScannerResultsScreen(
                            scannerIds = ids,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onSelectTimeframe = screenerViewModel::selectTimeframe,
                            onStockClick = { symbol, orderedSymbols ->
                                screenerViewModel.setChartNavigationQueue(orderedSymbols)
                                navController.navigate(Routes.stockDetail(symbol))
                            },
                            onOpenWatchlistPicker = screenerViewModel::openWatchlistPicker
                        )
                    }

                    composable(Routes.WATCHLIST) {
                        WatchlistScreen(
                            uiState = uiState,
                            themeMode = themeMode,
                            isDarkTheme = isDarkTheme,
                            onToggleDayNight = onToggleDayNight,
                            onSelectThemeMode = onSelectThemeMode,
                            isCheckingAlerts = isCheckingAlerts,
                            onStockClick = { symbol ->
                                val queue = uiState.activeWatchlistStocks.map { it.symbol }
                                    .ifEmpty { listOf(symbol) }
                                screenerViewModel.setChartNavigationQueue(queue)
                                navController.navigate(Routes.stockDetail(symbol))
                            },
                            onOpenWatchlistPicker = screenerViewModel::openWatchlistPicker,
                            onOpenPriceAlertSheet = screenerViewModel::openPriceAlertSheet,
                            onSelectWatchlist = screenerViewModel::selectWatchlist,
                            onCreateWatchlist = screenerViewModel::createWatchlist,
                            onRenameWatchlist = screenerViewModel::renameWatchlist,
                            onDeleteWatchlist = screenerViewModel::deleteWatchlist,
                            onCheckAlertsNow = { PriceAlertScheduler.triggerImmediateCheck(context) },
                            onToggleAlertActive = { id, active ->
                                screenerViewModel.togglePriceAlertActive(id, active)
                                if (active) PriceAlertScheduler.triggerImmediateCheck(context)
                            },
                            onDeleteAlert = screenerViewModel::deletePriceAlert,
                            onApplyPreset = { preset ->
                                screenerViewModel.applySavedPreset(preset)
                                navController.navigate(Routes.SCREENER) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDeletePreset = screenerViewModel::deletePreset
                        )
                    }

                    composable(
                        route = Routes.STOCK_DETAIL,
                        arguments = listOf(navArgument("symbol") { type = NavType.StringType })
                    ) { entry ->
                        val initialSymbol = entry.arguments?.getString("symbol") ?: return@composable
                        val detailVm: StockDetailViewModel = viewModel(
                            key = "detail_$initialSymbol",
                            factory = StockDetailViewModel.Factory(
                                symbol = initialSymbol,
                                initialTimeframe = uiState.filter.timeframe,
                                repository = repository
                            )
                        )
                        val detailState by detailVm.uiState.collectAsStateWithLifecycle()
                        val queue = remember(uiState.chartNavigationSymbols, uiState.filteredStocks, detailState.symbol) {
                            uiState.chartNavigationSymbols.ifEmpty {
                                uiState.filteredStocks.map { it.symbol }.ifEmpty { listOf(detailState.symbol) }
                            }
                        }
                        val currentQueueState by rememberUpdatedState(queue)
                        val currentSymbolState by rememberUpdatedState(detailState.symbol)

                        DisposableEffect(detailVm) {
                            onRegisterStockKeyStepper { delta ->
                                val q = currentQueueState
                                if (q.size <= 1) {
                                    false
                                } else {
                                    val next = ScreenerViewModel.stepSymbolInQueue(
                                        queue = q,
                                        currentSymbol = currentSymbolState,
                                        delta = delta
                                    )
                                    if (next != null) {
                                        detailVm.switchSymbol(next)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                            onDispose { onRegisterStockKeyStepper(null) }
                        }

                        StockDetailScreen(
                            uiState = detailState,
                            showBackButton = true,
                            stockQueue = queue,
                            onSwitchSymbol = detailVm::switchSymbol,
                            themeMode = themeMode,
                            isDarkTheme = isDarkTheme,
                            onToggleDayNight = onToggleDayNight,
                            onSelectThemeMode = onSelectThemeMode,
                            onBack = { navController.popBackStack() },
                            onSelectTimeframe = detailVm::selectTimeframe,
                            onToggleChartStyle = detailVm::toggleChartStyle,
                            onToggleEma9 = detailVm::toggleEma9,
                            onToggleEma20 = detailVm::toggleEma20,
                            onToggleEma45 = detailVm::toggleEma45,
                            onToggleSma50 = detailVm::toggleSma50,
                            onToggleSma200 = detailVm::toggleSma200,
                            onToggleBollingerBands = detailVm::toggleBollingerBands,
                            onToggleVolume = detailVm::toggleVolume,
                            onToggleStockInWatchlist = detailVm::toggleStockInWatchlist,
                            onSaveStockToWatchlistByName = detailVm::saveStockToWatchlistByName,
                            onCreatePriceAlert = { target, cond ->
                                detailVm.createPriceAlert(target, cond) {
                                    PriceAlertScheduler.triggerImmediateCheck(context)
                                }
                            },
                            onTogglePriceAlertActive = { id, active ->
                                detailVm.togglePriceAlertActive(id, active)
                                if (active) PriceAlertScheduler.triggerImmediateCheck(context)
                            },
                            onDeletePriceAlert = detailVm::deletePriceAlert,
                            onRefresh = { detailVm.loadAllTimeframes(forceRefresh = true) }
                        )
                    }
                }
            }
        }

        uiState.watchlistPickerStock?.let { pickerStock ->
            com.example.ui.components.WatchlistPickerSheet(
                stock = pickerStock,
                watchlists = uiState.namedWatchlists,
                onToggleInWatchlist = { watchlistId ->
                    screenerViewModel.toggleStockInWatchlist(pickerStock, watchlistId)
                },
                onCreateAndSave = { name ->
                    screenerViewModel.saveStockToNewWatchlistName(pickerStock, name)
                },
                onDismiss = screenerViewModel::dismissWatchlistPicker
            )
        }

        uiState.alertSheetStock?.let { alertStock ->
            PriceAlertSheet(
                stock = alertStock,
                onCreateAlert = { targetPrice, condition ->
                    screenerViewModel.createPriceAlert(alertStock, targetPrice, condition) {
                        PriceAlertScheduler.triggerImmediateCheck(context)
                    }
                },
                onDismiss = screenerViewModel::dismissPriceAlertSheet
            )
        }
    }
}
