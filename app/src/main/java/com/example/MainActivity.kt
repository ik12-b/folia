package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.FoliaTheme
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ParchmentBg
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueLight
import com.example.ui.viewmodel.FoliaViewModel
import com.example.ui.viewmodel.ThemeMode

enum class MainNavDestination(val label: String) {
    WORKSPACE("Ruang Kerja"),
    DOCUMENTS("Koleksi"),
    SETTINGS("Pengaturan")
}

class MainActivity : ComponentActivity() {

    // Activity-scoped instance, shared by both the splash screen's
    // keep-on-screen condition below and the Composable content further
    // down (same property, referenced directly instead of re-fetched via
    // viewModel() inside setContent).
    private val viewModel: FoliaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()/setContentView (or, for
        // Compose, before setContent) per the SplashScreen API contract --
        // it reads the activity's theme (Theme.Folia.Splash, set in the
        // manifest) to draw the initial splash frame, then this same call
        // switches the activity's theme to postSplashScreenTheme
        // (Theme.MyApplication) for everything drawn afterward.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash screen on screen until ONNX model warm-up and
        // repository/database initialization (see FoliaViewModel.init)
        // have finished, so the splash functions as a real loading gate
        // rather than a fixed-duration cosmetic animation that might
        // disappear before the app underneath is actually ready to
        // display real content.
        splashScreen.setKeepOnScreenCondition { !viewModel.isAppReady.value }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemInDark
            }

            FoliaTheme(darkTheme = isDark) {
                FoliaApp(viewModel = viewModel, isDarkMode = isDark)
            }
        }
    }
}

@Composable
fun FoliaApp(
    viewModel: FoliaViewModel,
    isDarkMode: Boolean
) {
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val selectedDocId by viewModel.selectedDocumentId.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainNavDestination.DOCUMENTS) }

    LaunchedEffect(documents) {
        if (selectedDocId == null && documents.isNotEmpty()) {
            viewModel.selectDocument(documents.first().id)
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Ultra-Compact Slim Navigation Dock (only 44dp height)
            Surface(
                color = if (isDarkMode) Color(0xFF131D2D) else Color.White,
                tonalElevation = 8.dp,
                shadowElevation = if (isDarkMode) 0.dp else 4.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_bottom_nav_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomInset)
                        .height(44.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Workspace Tab
                    val isWorkspaceSelected = currentTab == MainNavDestination.WORKSPACE
                    SlimNavTab(
                        icon = if (isWorkspaceSelected) Icons.Default.AutoStories else Icons.Outlined.AutoStories,
                        label = "Ruang Kerja",
                        isSelected = isWorkspaceSelected,
                        selectedColor = if (isDarkMode) GoldLight else GoldDark,
                        unselectedColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        onClick = {
                            if (selectedDocId == null && documents.isNotEmpty()) {
                                viewModel.selectDocument(documents.first().id)
                            }
                            currentTab = MainNavDestination.WORKSPACE
                        },
                        tag = "nav_item_workspace"
                    )

                    // 2. Documents / Collection Tab
                    val isDocsSelected = currentTab == MainNavDestination.DOCUMENTS
                    SlimNavTab(
                        icon = if (isDocsSelected) Icons.Default.CollectionsBookmark else Icons.Outlined.CollectionsBookmark,
                        label = "Koleksi Naskah",
                        isSelected = isDocsSelected,
                        selectedColor = if (isDarkMode) ScholarBlueLight else ScholarBlue,
                        unselectedColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        onClick = { currentTab = MainNavDestination.DOCUMENTS },
                        tag = "nav_item_documents"
                    )

                    // 3. Settings Tab
                    val isSettingsSelected = currentTab == MainNavDestination.SETTINGS
                    SlimNavTab(
                        icon = if (isSettingsSelected) Icons.Default.Settings else Icons.Outlined.Settings,
                        label = "Pengaturan",
                        isSelected = isSettingsSelected,
                        selectedColor = if (isDarkMode) GoldLight else GoldDark,
                        unselectedColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        onClick = { currentTab = MainNavDestination.SETTINGS },
                        tag = "nav_item_settings"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) DarkBg else ParchmentBg)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = paddingValues.calculateBottomPadding())
                .imePadding()
        ) {
            when (currentTab) {
                MainNavDestination.WORKSPACE -> {
                    WorkspaceScreen(
                        viewModel = viewModel,
                        onBackToLibrary = {
                            currentTab = MainNavDestination.DOCUMENTS
                        }
                    )
                }
                MainNavDestination.DOCUMENTS -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenDocument = { docId ->
                            viewModel.selectDocument(docId)
                            currentTab = MainNavDestination.WORKSPACE
                        }
                    )
                }
                MainNavDestination.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SlimNavTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) selectedColor else unselectedColor,
            modifier = Modifier.size(16.dp)
        )
        if (isSelected) {
            Text(
                text = " $label",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = selectedColor
            )
        }
    }
}
