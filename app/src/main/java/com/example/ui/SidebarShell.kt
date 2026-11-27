package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CastForEducation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CastForEducation
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch

private data class SidebarDestination(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkolquikMinimalShell(
    selectedTab: MainTab,
    displayName: String,
    userEmail: String,
    userRole: String,
    isPlatformAdmin: Boolean,
    favoriteCount: Int,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (MainTab) -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    isSearchActive: Boolean = false,
    onSearchClick: () -> Unit = { onSelectTab(MainTab.DISCOVER) },
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onEmergencyCall: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val exploreItems = listOf(
        SidebarDestination(
            tab = MainTab.HOME,
            label = stringResource(R.string.tab_home),
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        SidebarDestination(
            tab = MainTab.DISCOVER,
            label = stringResource(R.string.tab_discover),
            selectedIcon = Icons.Filled.Explore,
            unselectedIcon = Icons.Outlined.Explore
        ),
        SidebarDestination(
            tab = MainTab.DASHBOARD,
            label = stringResource(R.string.tab_dashboard),
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard
        ),
        SidebarDestination(
            tab = MainTab.SAVED,
            label = stringResource(R.string.tab_saved),
            selectedIcon = Icons.Filled.Bookmark,
            unselectedIcon = Icons.Outlined.BookmarkBorder,
            badgeText = if (favoriteCount > 0) "$favoriteCount" else null
        ),
        SidebarDestination(
            tab = MainTab.AI_ADVISOR,
            label = stringResource(R.string.tab_ai_advisor),
            selectedIcon = Icons.Filled.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome
        ),
        SidebarDestination(
            tab = MainTab.TUTORS,
            label = stringResource(R.string.tab_progressive_tutors),
            selectedIcon = Icons.Filled.CastForEducation,
            unselectedIcon = Icons.Outlined.CastForEducation
        ),
        SidebarDestination(
            tab = MainTab.RADIO,
            label = stringResource(R.string.tab_radio),
            selectedIcon = Icons.Filled.Radio,
            unselectedIcon = Icons.Outlined.Radio,
            badgeText = "LIVE"
        ),
        SidebarDestination(
            tab = MainTab.GAMES,
            label = stringResource(R.string.tab_educational_games),
            selectedIcon = Icons.Filled.SportsEsports,
            unselectedIcon = Icons.Outlined.SportsEsports,
            badgeText = "3"
        )
    )

    val familyItems = listOf(
        SidebarDestination(
            tab = MainTab.MESSAGES,
            label = stringResource(R.string.tab_messages),
            selectedIcon = Icons.AutoMirrored.Filled.Chat,
            unselectedIcon = Icons.AutoMirrored.Outlined.Chat
        ),
        SidebarDestination(
            tab = MainTab.PORTAL,
            label = stringResource(R.string.tab_portal),
            selectedIcon = Icons.Filled.FamilyRestroom,
            unselectedIcon = Icons.Outlined.FamilyRestroom
        )
    )

    val isSchoolAdminOrOversight = userRole.equals("admin", ignoreCase = true) || isPlatformAdmin
    val manageItems = buildList {
        add(
            SidebarDestination(
                tab = MainTab.ADVERTS,
                label = stringResource(R.string.tab_adverts),
                selectedIcon = Icons.Filled.Campaign,
                unselectedIcon = Icons.Outlined.Campaign
            )
        )
        if (isSchoolAdminOrOversight) {
            add(
                SidebarDestination(
                    tab = MainTab.ADMIN,
                    label = stringResource(R.string.tab_admin),
                    selectedIcon = Icons.Filled.AdminPanelSettings,
                    unselectedIcon = Icons.Outlined.AdminPanelSettings,
                    badgeText = if (isPlatformAdmin) "Oversight" else null
                )
            )
        }
        if (isPlatformAdmin) {
            add(
                SidebarDestination(
                    tab = MainTab.PLATFORM_ADMIN,
                    label = stringResource(R.string.tab_platform_admin),
                    selectedIcon = Icons.Filled.Security,
                    unselectedIcon = Icons.Outlined.Security,
                    badgeText = "Admin"
                )
            )
        }
    }

    val supportItems = listOf(
        SidebarDestination(
            tab = MainTab.SETTINGS,
            label = stringResource(R.string.tab_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        ),
        SidebarDestination(
            tab = MainTab.HELP,
            label = stringResource(R.string.tab_help),
            selectedIcon = Icons.AutoMirrored.Filled.Help,
            unselectedIcon = Icons.AutoMirrored.Outlined.HelpOutline
        )
    )

    val sectionTitle = when (selectedTab) {
        MainTab.HOME -> stringResource(R.string.app_name)
        MainTab.DISCOVER -> stringResource(R.string.tab_discover)
        MainTab.DASHBOARD -> stringResource(R.string.tab_dashboard)
        MainTab.SAVED -> stringResource(R.string.tab_saved)
        MainTab.AI_ADVISOR -> stringResource(R.string.tab_ai_advisor)
        MainTab.TUTORS -> stringResource(R.string.tab_progressive_tutors)
        MainTab.MESSAGES -> stringResource(R.string.tab_messages)
        MainTab.PORTAL -> stringResource(R.string.tab_portal)
        MainTab.ADVERTS -> stringResource(R.string.tab_adverts)
        MainTab.RADIO -> stringResource(R.string.tab_radio)
        MainTab.GAMES -> stringResource(R.string.tab_educational_games)
        MainTab.ADMIN -> stringResource(R.string.tab_admin)
        MainTab.PLATFORM_ADMIN -> stringResource(R.string.tab_platform_admin)
        MainTab.SETTINGS -> stringResource(R.string.tab_settings)
        MainTab.HELP -> stringResource(R.string.tab_help)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && selectedTab != MainTab.HOME) {
        onSelectTab(MainTab.HOME)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentSidebar = maxWidth >= 840.dp

        @Composable
        fun MainScaffold(showMenuButton: Boolean, showBottomBar: Boolean) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = if (showBottomBar) Modifier else Modifier.navigationBarsPadding()
                    )
                },
                bottomBar = {
                    if (showBottomBar) {
                        SkolquikBottomBar(
                            selectedTab = selectedTab,
                            isSearchActive = isSearchActive,
                            favoriteCount = favoriteCount,
                            onSelectHome = { onSelectTab(MainTab.HOME) },
                            onSelectDiscover = { onSelectTab(MainTab.DISCOVER) },
                            onSelectAi = { onSelectTab(MainTab.AI_ADVISOR) },
                            onSelectSaved = { onSelectTab(MainTab.SAVED) },
                            onSelectSearch = onSearchClick
                        )
                    }
                },
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                if (showMenuButton) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("open_sidebar_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "Open navigation sidebar"
                                        )
                                    }
                                }
                            },
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_launcher_foreground),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                        )
                                    }
                                    Text(
                                        text = sectionTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onNotificationsClick,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("top_bar_notifications_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (notificationCount > 0) {
                                                Badge {
                                                    Text(if (notificationCount > 9) "9+" else "$notificationCount")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Notifications,
                                            contentDescription = "School updates and notifications"
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onSearchClick,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("top_bar_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search schools"
                                    )
                                }
                            }
                        )
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .let { mod -> if (showBottomBar) mod else mod.navigationBarsPadding() }
                ) {
                    content()
                }
            }
        }

        if (usePermanentSidebar) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .width(268.dp)
                        .fillMaxHeight()
                        .testTag("permanent_sidebar")
                ) {
                    SkolquikSidebarContent(
                        selectedTab = selectedTab,
                        exploreItems = exploreItems,
                        familyItems = familyItems,
                        manageItems = manageItems,
                        supportItems = supportItems,
                        displayName = displayName,
                        userEmail = userEmail,
                        userRole = userRole,
                        showCloseButton = false,
                        onClose = {},
                        onSelectTab = onSelectTab,
                        onOpenProfile = onOpenProfile,
                        onSignOut = onSignOut,
                        onEmergencyCall = onEmergencyCall
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MainScaffold(showMenuButton = false, showBottomBar = false)
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .widthIn(max = 304.dp)
                            .testTag("sidebar_drawer_sheet")
                    ) {
                        SkolquikSidebarContent(
                            selectedTab = selectedTab,
                            exploreItems = exploreItems,
                            familyItems = familyItems,
                            manageItems = manageItems,
                            supportItems = supportItems,
                            displayName = displayName,
                            userEmail = userEmail,
                            userRole = userRole,
                            showCloseButton = true,
                            onClose = { scope.launch { drawerState.close() } },
                            onSelectTab = { tab ->
                                scope.launch { drawerState.close() }
                                onSelectTab(tab)
                            },
                            onOpenProfile = {
                                scope.launch { drawerState.close() }
                                onOpenProfile()
                            },
                            onSignOut = {
                                scope.launch { drawerState.close() }
                                onSignOut()
                            },
                            onEmergencyCall = {
                                scope.launch { drawerState.close() }
                                onEmergencyCall()
                            }
                        )
                    }
                }
            ) {
                MainScaffold(showMenuButton = true, showBottomBar = true)
            }
        }
    }
}

@Composable
private fun SkolquikBottomBar(
    selectedTab: MainTab,
    isSearchActive: Boolean,
    favoriteCount: Int,
    onSelectHome: () -> Unit,
    onSelectDiscover: () -> Unit,
    onSelectAi: () -> Unit,
    onSelectSaved: () -> Unit,
    onSelectSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_bottom_bar")
        ) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val homeSelected = selectedTab == MainTab.HOME && !isSearchActive
            NavigationBarItem(
                selected = homeSelected,
                onClick = onSelectHome,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (homeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = stringResource(R.string.nav_bottom_home)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_bottom_home),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (homeSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bottom_nav_home")
            )

            val discoverSelected = selectedTab == MainTab.DISCOVER && !isSearchActive
            NavigationBarItem(
                selected = discoverSelected,
                onClick = onSelectDiscover,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (discoverSelected) Icons.Filled.Explore else Icons.Outlined.Explore,
                        contentDescription = stringResource(R.string.nav_bottom_discover)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_bottom_discover),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (discoverSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bottom_nav_discover")
            )

            val aiSelected = selectedTab == MainTab.AI_ADVISOR
            NavigationBarItem(
                selected = aiSelected,
                onClick = onSelectAi,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (aiSelected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(R.string.nav_bottom_ai)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_bottom_ai),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (aiSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bottom_nav_ai")
            )

            val savedSelected = selectedTab == MainTab.SAVED
            NavigationBarItem(
                selected = savedSelected,
                onClick = onSelectSaved,
                colors = itemColors,
                icon = {
                    BadgedBox(
                        badge = {
                            if (favoriteCount > 0) {
                                Badge { Text("$favoriteCount") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (savedSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.nav_bottom_saved)
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_bottom_saved),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (savedSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bottom_nav_saved")
            )

            val searchSelected = selectedTab == MainTab.DISCOVER && isSearchActive
            NavigationBarItem(
                selected = searchSelected,
                onClick = onSelectSearch,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (searchSelected) Icons.Filled.Search else Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.nav_bottom_search)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_bottom_search),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (searchSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bottom_nav_search")
            )
        }
    }
}

@Composable
private fun SkolquikSidebarContent(
    selectedTab: MainTab,
    exploreItems: List<SidebarDestination>,
    familyItems: List<SidebarDestination>,
    manageItems: List<SidebarDestination>,
    supportItems: List<SidebarDestination>,
    displayName: String,
    userEmail: String,
    userRole: String,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    onEmergencyCall: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        // Brand Header (pure white badge keeps the blue + amber logo crisp and un-overshadowed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search Smart, Enroll Fast.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close sidebar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(10.dp))

        // Scrollable Navigation Groups
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SidebarGroupLabel("HOME & DISCOVER")
            exploreItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    selected = selectedTab == item.tab,
                    onClick = { onSelectTab(item.tab) }
                )
            }

            Spacer(Modifier.height(10.dp))
            SidebarGroupLabel("FAMILY & CONNECT")
            familyItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    selected = selectedTab == item.tab,
                    onClick = { onSelectTab(item.tab) }
                )
            }

            Spacer(Modifier.height(10.dp))
            SidebarGroupLabel("PROMOTE & MANAGE")
            manageItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    selected = selectedTab == item.tab,
                    onClick = { onSelectTab(item.tab) }
                )
            }

            Spacer(Modifier.height(10.dp))
            SidebarGroupLabel("PREFERENCES & SUPPORT")
            supportItems.forEach { item ->
                SidebarNavItem(
                    item = item,
                    selected = selectedTab == item.tab,
                    onClick = { onSelectTab(item.tab) }
                )
            }

            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                selected = false,
                onClick = onEmergencyCall,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.sidebar_emergency_call),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.sidebar_emergency_call),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                badge = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "112",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("sidebar_emergency_call_112")
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(10.dp))

        // Clean User Profile + Sign Out Footer
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenProfile)
                .testTag("profile_settings_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName.ifBlank { "Skolquik Member" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (userEmail.isNotBlank()) userEmail else userRole.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        NavigationDrawerItem(
            selected = false,
            onClick = onSignOut,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_out_button")
        )
    }
}

@Composable
private fun SidebarGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

@Composable
private fun SidebarNavItem(
    item: SidebarDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label
            )
        },
        label = {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        badge = item.badgeText?.let { badge ->
            {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .testTag("nav_${item.tab.name.lowercase()}")
    )
}
