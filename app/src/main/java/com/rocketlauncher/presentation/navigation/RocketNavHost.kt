package com.rocketlauncher.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rocketlauncher.data.invite.InviteLinkParser
import com.rocketlauncher.presentation.chat.ChatScreen
import com.rocketlauncher.presentation.login.LoginScreen
import com.rocketlauncher.presentation.rooms.RoomListScreen
import com.rocketlauncher.presentation.profile.MyProfileScreen
import com.rocketlauncher.presentation.search.GlobalSearchScreen

@Composable
fun RocketNavHost(
    navViewModel: NavViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val viewModel = navViewModel
    val isReady by viewModel.isReady.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val pendingOpen by viewModel.pendingOpenChat.collectAsState()
    val pendingShare by viewModel.pendingShareUris.collectAsState()
    val pendingInviteUri by viewModel.pendingInviteUri.collectAsState()
    val inviteError by viewModel.inviteError.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(pendingInviteUri?.toString(), authState.isLoggedIn) {
        if (pendingInviteUri != null && authState.isLoggedIn) {
            viewModel.tryProcessInviteDeepLink()
        }
    }

    LaunchedEffect(inviteError) {
        val msg = inviteError
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearInviteError()
        }
    }

    /**
     * Открытие чата из уведомления: [navController.navigate] только после [isReady] и монтирования [NavHost],
     * иначе на холодном старте — краш (NavController без графа).
     */
    LaunchedEffect(pendingOpen?.openRequestId, authState.isLoggedIn, pendingOpen, isReady) {
        val p = pendingOpen ?: return@LaunchedEffect
        if (!authState.isLoggedIn || !isReady) return@LaunchedEffect
        val route = NavRoutes.chat(p.roomId, p.roomName, p.roomType, p.avatarPath)
        /**
         * Уже открыт другой чат: [launchSingleTop] по шаблону `chat/...` часто не меняет экран.
         * Сбрасываем стек до списка комнат и кладём целевой чат сверху.
         */
        navController.navigate(route) {
            popUpTo(NavRoutes.ROOMS) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = false
        }
        viewModel.consumePendingOpenChat()
    }

    LaunchedEffect(pendingShare, authState.isLoggedIn, isReady) {
        if (pendingShare.isEmpty() || !authState.isLoggedIn || !isReady) return@LaunchedEffect
        navController.navigate(NavRoutes.ROOMS) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    if (!isReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.isLoggedIn) NavRoutes.ROOMS else NavRoutes.LOGIN
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(NavRoutes.ROOMS) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(NavRoutes.ROOMS) {
            val shareUris: List<Uri> = pendingShare
            RoomListScreen(
                pendingShareUris = shareUris,
                onOpenMyProfile = { navController.navigate(NavRoutes.MY_PROFILE) },
                onOpenGlobalMessageSearch = {
                    navController.navigate(NavRoutes.GLOBAL_SEARCH)
                },
                onRoomClick = { id, name, type, avatarPath ->
                    viewModel.transferPendingShareToChatQueue()
                    navController.navigate(NavRoutes.chat(id, name, type, avatarPath))
                },
                onThreadClick = { roomId, roomName, roomType, avatarPath, tmid, threadTitle ->
                    viewModel.transferPendingShareToChatQueue()
                    navController.navigate(NavRoutes.chat(roomId, roomName, roomType, avatarPath, tmid, threadTitle))
                },
                onDiscussionClick = { roomId, roomName, roomType, avatarPath ->
                    viewModel.transferPendingShareToChatQueue()
                    navController.navigate(NavRoutes.chat(roomId, roomName, roomType, avatarPath))
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(NavRoutes.MY_PROFILE) {
            MyProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.GLOBAL_SEARCH) {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenMessage = { roomId, roomName, roomType, avatarPath, messageId ->
                    navController.navigate(
                        NavRoutes.chat(
                            roomId,
                            roomName,
                            roomType,
                            avatarPath,
                            tmid = "",
                            threadTitle = "",
                            highlightMsg = messageId
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("roomName") { type = NavType.StringType },
                navArgument("roomType") { type = NavType.StringType },
                navArgument("avatarPath") { type = NavType.StringType; defaultValue = "" },
                navArgument("tmid") { type = NavType.StringType; defaultValue = "" },
                navArgument("threadTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("highlightMsg") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val roomId = safeUrlDecode(backStackEntry.arguments?.getString("roomId"))
            val roomName = safeUrlDecode(backStackEntry.arguments?.getString("roomName"))
            val roomType = backStackEntry.arguments?.getString("roomType") ?: "c"
            val avatarPath = safeUrlDecode(backStackEntry.arguments?.getString("avatarPath"))
            ChatScreen(
                roomId = roomId,
                roomName = roomName,
                roomType = roomType,
                avatarPath = avatarPath,
                onUrlClick = { url ->
                    val uri = runCatching { Uri.parse(url) }.getOrNull()
                    when {
                        uri == null -> false
                        InviteLinkParser.looksLikeInviteLink(uri) -> {
                            viewModel.handleInAppInviteLink(uri)
                            true
                        }
                        else -> false
                    }
                },
                onBack = { navController.popBackStack() },
                onLeaveChat = { navController.popBackStack() },
                onOpenDirectChat = { dmRoomId, dmName, dmAvatarPath ->
                    navController.navigate(
                        NavRoutes.chat(dmRoomId, dmName, "d", dmAvatarPath)
                    ) {
                        launchSingleTop = false
                    }
                },
                onNavigateToThread = { tmid, threadTitle, highlightMsg ->
                    /**
                     * Без [launchSingleTop]: иначе тот же route «чат комнаты» переиспользует
                     * [NavBackStackEntry] и [ChatViewModel] — tmid не подхватывается, тред не открывается.
                     */
                    navController.navigate(
                        NavRoutes.chat(
                            roomId,
                            roomName,
                            roomType,
                            avatarPath,
                            tmid = tmid,
                            threadTitle = threadTitle,
                            highlightMsg = highlightMsg
                        )
                    )
                },
                onNavigateToDiscussion = { discRoomId, title, discType ->
                    navController.navigate(NavRoutes.chat(discRoomId, title, discType, "")) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
