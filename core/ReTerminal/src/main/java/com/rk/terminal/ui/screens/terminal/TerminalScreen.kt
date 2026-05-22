package com.rk.terminal.ui.screens.terminal

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.google.android.material.R
import androidx.compose.ui.res.stringResource
import com.rk.resources.strings
import com.rk.libcommons.child
import com.rk.libcommons.dpToPx
import com.rk.libcommons.localDir
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalColors
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.util.Properties

var darkText = mutableStateOf(false)

fun getViewColor(): Int{
    return if (darkText.value){
        Color.BLACK
    }else{
        Color.WHITE
    }
}

fun getComposeColor():androidx.compose.ui.graphics.Color{
    return if (darkText.value){
        androidx.compose.ui.graphics.Color.Black
    }else{
        androidx.compose.ui.graphics.Color.White
    }
}

var showVirtualKeys = mutableStateOf(Settings.virtualKeys)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    mainActivityActivity: MainActivity,
    navController: NavController
) {
    val sessionController = mainActivityActivity.sessionController ?: return
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()


    LaunchedEffect(Unit){
        darkText.value = !isDarkMode
        scope.launch(Dispatchers.Main){
            TerminalUiRegistry.virtualKeysView.get()?.apply {
                virtualKeysViewClient =
                    TerminalUiRegistry.terminalView.get()?.mTermSession?.let {
                        VirtualKeysListener(
                            it
                        )
                    }

                buttonTextColor = getViewColor()


                reload(
                    VirtualKeysInfo(
                        VIRTUAL_KEYS,
                        "",
                        VirtualKeysConstants.CONTROL_CHARS_ALIASES
                    )
                )
            }

            TerminalUiRegistry.terminalView.get()?.apply {
                onScreenUpdated()


                mEmulator?.mColors?.mCurrentColors?.apply {
                    set(256, getViewColor())
                }
            }
        }


    }

    Box {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }

        if (drawerState.isClosed){
            SetStatusBarTextColor(isDarkIcons = darkText.value)
        }else{
            SetStatusBarTextColor(isDarkIcons = !isDarkMode)
        }

        fun createSession(){
            fun generateUniqueString(existingStrings: List<String>): String {
                var index = 1
                var newString: String

                do {
                    newString = "main$index"
                    index++
                } while (newString in existingStrings)

                return newString
            }

            val sessionId = generateUniqueString(sessionController.sessionList.keys.toList())

            TerminalUiRegistry.terminalView.get()
                ?.let {
                    val client = TerminalBackEnd(it, mainActivityActivity)
                    sessionController.createSession(
                        sessionId,
                        client,
                        mainActivityActivity
                    )
                }

            changeSession(mainActivityActivity, session_id = sessionId)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(strings.session),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Row {
                                val keyboardController = LocalSoftwareKeyboardController.current
                                IconButton(onClick = {
                                    navController.navigate(MainActivityRoutes.Settings.route)
                                    keyboardController?.hide()
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null
                                    )
                                }

                                IconButton(onClick = {
                                    createSession()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null
                                    )
                                }

                            }


                        }

                        sessionController.sessionList.keys.toList().let {
                            LazyColumn {
                                items(it) { session_id ->
                                    SelectableCard(
                                        selected = session_id == sessionController.currentSession.value,
                                        onSelect = {
                                            changeSession(
                                                mainActivityActivity,
                                                session_id
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = session_id,
                                                style = MaterialTheme.typography.bodyLarge
                                            )

                                            if (session_id != sessionController.currentSession.value) {
                                                Spacer(modifier = Modifier.weight(1f))

                                                IconButton(
                                                    onClick = {
                                                        println(session_id)
                                                        sessionController.terminateSession(
                                                            session_id
                                                        )
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                        }
                                    }
                                }
                            }
                        }

                    }
                }

            },
            content = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val color = getComposeColor()
                        Column {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                title = {
                                    Column {
                                        Text(text = "ReTerminal", color = color)
                                        Text(
                                            style = MaterialTheme.typography.bodySmall,
                                            text = sessionController.currentSession.value,
                                            color = color
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, null, tint = color)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        createSession()
                                    }) {
                                        Icon(Icons.Default.Add, null, tint = color)
                                    }
                                }
                            )

                            Column(modifier = Modifier.imePadding().navigationBarsPadding()) {
                                AndroidView(
                                    factory = { context ->
                                        TerminalView(context, null).apply {
                                            TerminalUiRegistry.terminalView = java.lang.ref.WeakReference(this)
                                            setTextSize(
                                                dpToPx(
                                                    Settings.terminal_font_size.toFloat(),
                                                    context
                                                )
                                            )
                                            val client = TerminalBackEnd(this, mainActivityActivity)

                                            val sessionId = sessionController.currentSession.value
                                            val session = sessionController.getSession(sessionId)
                                                ?: sessionController.createSession(
                                                    sessionId,
                                                    client,
                                                    mainActivityActivity
                                                )

                                            session.updateTerminalSessionClient(client)
                                            attachSession(session)
                                            setTerminalViewClient(client)

                                            post {
                                                val color = getViewColor()

                                                keepScreenOn = true
                                                requestFocus()
                                                isFocusableInTouchMode = true

                                                mEmulator?.mColors?.mCurrentColors?.apply {
                                                    set(256, color)
                                                }

                                                val colorsFile = localDir().child("colors.properties")
                                                if (colorsFile.exists() && colorsFile.isFile){
                                                    val props = Properties()
                                                    FileInputStream(colorsFile).use { input ->
                                                        props.load(input)
                                                    }
                                                    TerminalColors.COLOR_SCHEME.updateWith(props)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    update = { terminalView ->
                                        terminalView.onScreenUpdated()
                                       val color = getViewColor()

                                        terminalView.mEmulator?.mColors?.mCurrentColors?.apply {
                                            set(256, color)
                                        }
                                    },
                                )

                                if (showVirtualKeys.value){
                                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                                    TerminalUiRegistry.terminalView.get()?.requestFocus()
                                    AndroidView(
                                        factory = { context ->
                                            VirtualKeysView(context, null).apply {
                                                TerminalUiRegistry.virtualKeysView = java.lang.ref.WeakReference(this)
                                                virtualKeysViewClient =
                                                    TerminalUiRegistry.terminalView.get()?.mTermSession?.let {
                                                        VirtualKeysListener(it)
                                                    }

                                                buttonTextColor = onSurfaceColor

                                                reload(
                                                    VirtualKeysInfo(
                                                        VIRTUAL_KEYS,
                                                        "",
                                                        VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(75.dp)
                                    )
                                }else{
                                    TerminalUiRegistry.virtualKeysView = java.lang.ref.WeakReference(null)
                                }

                            }
                        }



                }

            })
    }
}

@Composable
fun SetStatusBarTextColor(isDarkIcons: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return

    SideEffect {
        WindowCompat.getInsetsController(window, view)?.isAppearanceLightStatusBars = isDarkIcons
    }
}



@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        enabled = enabled,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}


fun changeSession(mainActivityActivity: MainActivity, session_id: String) {
    val sessionController = mainActivityActivity.sessionController ?: return
    TerminalUiRegistry.terminalView.get()?.apply {
        val client = TerminalBackEnd(this, mainActivityActivity)
        val session =
            sessionController.getSession(session_id)
                ?: sessionController.createSession(
                    session_id,
                    client,
                    mainActivityActivity
                )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            val typedValue = TypedValue()

            context.theme.resolveAttribute(
                R.attr.colorOnSurface,
                typedValue,
                true
            )
            keepScreenOn = true
            requestFocus()
            isFocusableInTouchMode = true

            mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, typedValue.data)
            }
        }
        TerminalUiRegistry.virtualKeysView.get()?.apply {
            virtualKeysViewClient =
                TerminalUiRegistry.terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }

    }
    sessionController.currentSession.value = session_id

}


const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
