package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.ViralAiService
import com.example.data.repository.ViralKitRepository
import com.example.export.ApkExporter
import com.example.notifications.TrendAlertPrefsStore
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.AnalyzeScreen
import com.example.ui.screens.GrowthKitHistoryScreen
import com.example.ui.screens.StrategyChatScreen
import com.example.ui.screens.TrendsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.studio.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

const val ROUTE_ANALYZE = "analyze"
const val ROUTE_TRENDS = "trends"
const val ROUTE_CHAT = "chat"
const val ROUTE_VAULT = "vault"
const val ROUTE_HISTORY = "history"

private data class StudioDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val studioDestinations = listOf(
    StudioDestination(
        route = ROUTE_ANALYZE,
        label = "Analyze",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_analyze"
    ),
    StudioDestination(
        route = ROUTE_TRENDS,
        label = "Trends",
        selectedIcon = Icons.Rounded.TravelExplore,
        unselectedIcon = Icons.Outlined.TravelExplore,
        testTag = "nav_trends"
    ),
    StudioDestination(
        route = ROUTE_CHAT,
        label = "Strategist",
        selectedIcon = Icons.Rounded.Forum,
        unselectedIcon = Icons.Outlined.Forum,
        testTag = "nav_chat"
    ),
    StudioDestination(
        route = ROUTE_VAULT,
        label = "Vault",
        selectedIcon = Icons.Rounded.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        testTag = "nav_vault"
    )
)

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    openTrendsRequest: Boolean = false,
    trendingTopicRequest: String? = null,
    openVaultKitIdRequest: String? = null,
    onConsumeOpenTrends: () -> Unit = {},
    onConsumeOpenVaultKit: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            StudioMainShell(
                user = user,
                openTrendsRequest = openTrendsRequest,
                trendingTopicRequest = trendingTopicRequest,
                openVaultKitIdRequest = openVaultKitIdRequest,
                onConsumeOpenTrends = onConsumeOpenTrends,
                onConsumeOpenVaultKit = onConsumeOpenVaultKit
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioMainShell(
    user: FirebaseUser,
    openTrendsRequest: Boolean,
    trendingTopicRequest: String?,
    openVaultKitIdRequest: String?,
    onConsumeOpenTrends: () -> Unit,
    onConsumeOpenVaultKit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val studioViewModel: StudioViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                StudioViewModel(
                    appContext = app.applicationContext,
                    repository = ViralKitRepository(app.applicationContext),
                    aiService = ViralAiService(),
                    alertPrefsStore = TrendAlertPrefsStore(app.applicationContext),
                    currentUserId = user.uid,
                    displayName = user.displayName ?: "Creator",
                    email = user.email
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_ANALYZE

    val snackbarHostState = remember { SnackbarHostState() }
    val statusBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            studioViewModel.consumeStatusBanner()
        }
    }

    var accountMenuExpanded by remember { mutableStateOf(false) }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(openTrendsRequest, trendingTopicRequest) {
        if (openTrendsRequest) {
            navigateToTab(ROUTE_TRENDS)
            if (!trendingTopicRequest.isNullOrBlank()) {
                studioViewModel.searchLiveTrends(trendingTopicRequest)
            }
            onConsumeOpenTrends()
        }
    }

    LaunchedEffect(openVaultKitIdRequest) {
        if (!openVaultKitIdRequest.isNullOrBlank()) {
            studioViewModel.openVaultKitById(openVaultKitIdRequest)
            navigateToTab(ROUTE_VAULT)
            onConsumeOpenVaultKit()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ViralKit",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = when (currentRoute) {
                                    ROUTE_HISTORY -> "Strategy History"
                                    else -> studioDestinations.firstOrNull { it.route == currentRoute }?.label
                                        ?: "Creator Studio"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val res = ApkExporter.downloadApkToDownloadsFolder(context)
                                    val msg = res.getOrElse { it.localizedMessage ?: "Failed to download APK" }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_download_apk_button")
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Download ViralKit APK",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { navController.navigate(ROUTE_HISTORY) { launchSingleTop = true } },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_history_button")
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "View Viral Growth Kit History",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { accountMenuExpanded = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("account_menu_button")
                            ) {
                                Icon(
                                    Icons.Rounded.AccountCircle,
                                    contentDescription = "Creator account menu",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = accountMenuExpanded,
                                onDismissRequest = { accountMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = user.displayName ?: "Creator",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            if (!user.email.isNullOrBlank()) {
                                                Text(
                                                    text = user.email!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Lock,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Signed in · Cloud Library Synced",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    },
                                    onClick = { accountMenuExpanded = false }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Switch Google Account") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                                    },
                                    onClick = {
                                        accountMenuExpanded = false
                                        signOut(credentialManager, scope)
                                    },
                                    modifier = Modifier.testTag("switch_account_menu_item")
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign out") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                    },
                                    onClick = {
                                        accountMenuExpanded = false
                                        signOut(credentialManager, scope)
                                    },
                                    modifier = Modifier.testTag("sign_out_menu_item")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        studioDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        studioDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_ANALYZE,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(ROUTE_ANALYZE) {
                            AnalyzeScreen(
                                viewModel = studioViewModel,
                                onShowToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onOpenChatForKit = { kit ->
                                    studioViewModel.discussKitInChat(kit)
                                    navigateToTab(ROUTE_CHAT)
                                },
                                onExploreTopic = { topic ->
                                    studioViewModel.searchLiveTrends(topic)
                                    navigateToTab(ROUTE_TRENDS)
                                }
                            )
                        }
                        composable(ROUTE_TRENDS) {
                            TrendsScreen(
                                viewModel = studioViewModel,
                                onShowToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(ROUTE_CHAT) {
                            StrategyChatScreen(
                                viewModel = studioViewModel,
                                onShowToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(ROUTE_VAULT) {
                            VaultScreen(
                                viewModel = studioViewModel,
                                onShowToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onNavigateToAnalyze = { navigateToTab(ROUTE_ANALYZE) },
                                onOpenChatForKit = { kit ->
                                    studioViewModel.discussKitInChat(kit)
                                    navigateToTab(ROUTE_CHAT)
                                },
                                onExploreTopic = { topic ->
                                    studioViewModel.searchLiveTrends(topic)
                                    navigateToTab(ROUTE_TRENDS)
                                },
                                onSwitchAccount = { signOut(credentialManager, scope) }
                            )
                        }
                        composable(ROUTE_HISTORY) {
                            GrowthKitHistoryScreen(
                                viewModel = studioViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToAnalyze = { navigateToTab(ROUTE_ANALYZE) },
                                onOpenChatForKit = { kit ->
                                    studioViewModel.discussKitInChat(kit)
                                    navigateToTab(ROUTE_CHAT)
                                },
                                onExploreTopic = { topic ->
                                    studioViewModel.searchLiveTrends(topic)
                                    navigateToTab(ROUTE_TRENDS)
                                },
                                onShowToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
