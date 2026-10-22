package com.matan.journal.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matan.journal.JournalApp
import com.matan.journal.data.Entry
import com.matan.journal.data.Journal
import com.matan.journal.ui.screens.EntryScreen
import com.matan.journal.ui.screens.InsightsScreen
import com.matan.journal.ui.screens.LessonsScreen
import com.matan.journal.ui.screens.MemoriesScreen
import com.matan.journal.ui.screens.SettingsScreen
import com.matan.journal.ui.screens.TimelineScreen
import com.matan.journal.ui.screens.TodayScreen
import com.matan.journal.ui.screens.WeekScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ja = app as JournalApp
    val repo = ja.repo
    val settingsStore = ja.settings
    val settings = settingsStore.flow
    val journal: StateFlow<Journal?> = repo.journal().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Saved in the ViewModel scope so it survives leaving the screen. */
    fun saveEntry(e: Entry) { viewModelScope.launch { repo.saveEntry(e) } }
    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}

object Routes {
    const val TODAY = "today"
    const val TIMELINE = "timeline"
    const val INSIGHTS = "insights"
    const val MEMORIES = "memories"
    const val SETTINGS = "settings"
    const val LESSONS = "lessons"
    fun entry(day: Long) = "entry/$day"
    fun week(start: Long) = "week/$start"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.TODAY, "היום", Icons.Default.Create),
    TabItem(Routes.TIMELINE, "היומן", Icons.Default.DateRange),
    TabItem(Routes.INSIGHTS, "תובנות", Icons.Default.Star),
    TabItem(Routes.MEMORIES, "זיכרונות", Icons.Default.Favorite),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(pad)) {
            composable(Routes.TODAY) { TodayScreen(vm, nav) }
            composable(Routes.TIMELINE) { TimelineScreen(vm, nav) }
            composable(Routes.INSIGHTS) { InsightsScreen(vm, nav) }
            composable(Routes.MEMORIES) { MemoriesScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.LESSONS) { LessonsScreen(vm, nav) }
            composable("entry/{day}", listOf(navArgument("day") { type = NavType.LongType })) { EntryScreen(vm, nav, it.arguments!!.getLong("day")) }
            composable("week/{start}", listOf(navArgument("start") { type = NavType.LongType })) { WeekScreen(vm, nav, it.arguments!!.getLong("start")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    nav: NavController? = null,
    tab: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = if (tab) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { if (nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}
