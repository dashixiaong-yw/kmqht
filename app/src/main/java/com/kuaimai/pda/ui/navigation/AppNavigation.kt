package com.kuaimai.pda.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.home.HomeScreen
import com.kuaimai.pda.ui.login.LoginScreen
import com.kuaimai.pda.ui.pickdetail.PickDetailScreen
import com.kuaimai.pda.ui.picklist.PickListScreen
import com.kuaimai.pda.ui.product.ProductScreen
import com.kuaimai.pda.ui.settings.SettingsScreen
import com.kuaimai.pda.util.NetworkMonitor
import com.kuaimai.pda.util.SessionExpiredEvent

/**
 * 应用导航
 * 单Activity架构，NavHost管理页面路由
 * 启动时检查登录状态，未登录→登录页
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PICK_LIST = "pickList"
    const val PICK_DETAIL = "pickDetail/{orderId}"
    const val PRODUCT = "product/{skuOuterId}?orderId={orderId}"
    const val SETTINGS = "settings"

    /** 构建取货单详情路由 */
    fun pickDetailRoute(orderId: Long): String = "pickDetail/$orderId"

    /** 构建商品详情路由 */
    fun productRoute(skuOuterId: String, orderId: Long = 0L): String =
        if (orderId > 0) "product/$skuOuterId?orderId=$orderId" else "product/$skuOuterId"
}

@Composable
fun AppNavigation(
    userRepository: UserRepository,
    authRepository: AuthRepository,
    networkMonitor: NetworkMonitor
) {
    val navController = rememberNavController()
    var isCheckingAuth by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf(Routes.LOGIN) }

    // 监听快麦Session过期事件
    var showSessionExpiredDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        SessionExpiredEvent.isExpired.collectLatest { isExpired ->
            if (isExpired) {
                showSessionExpiredDialog = true
            }
        }
    }

    // 启动时验证token有效性
    LaunchedEffect(Unit) {
        if (userRepository.isLoggedIn() && userRepository.isTokenLocallyValid()) {
            startDestination = Routes.HOME
        } else {
            startDestination = Routes.LOGIN
        }
        isCheckingAuth = false
    }

    // 监听token过期事件，自动跳转登录页
    LaunchedEffect(Unit) {
        userRepository.loginRequired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    if (isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = "正在验证登录状态...",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                userRepository = userRepository,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                userRepository = userRepository,
                onNavigateToPickList = { navController.navigate(Routes.PICK_LIST) },
                onNavigateToProduct = {
                    navController.navigate(Routes.productRoute(""))
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                authRepository = authRepository,
                networkMonitor = networkMonitor
            )
        }

        composable(Routes.PICK_LIST) {
            PickListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { orderId ->
                    navController.navigate(Routes.pickDetailRoute(orderId))
                }
            )
        }

        composable(
            route = Routes.PICK_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0L
            PickDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { skuOuterId ->
                    navController.navigate(Routes.productRoute(skuOuterId, orderId = orderId))
                }
            )
        }

        composable(
            route = Routes.PRODUCT,
            arguments = listOf(
                navArgument("skuOuterId") { type = NavType.StringType },
                navArgument("orderId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) {
            ProductScreen(
                onNavigateBack = { navController.popBackStack() },
                userRepository = userRepository
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                userRepository = userRepository,
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }
    }

    // 快麦Session过期弹窗
    if (showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showSessionExpiredDialog = false; SessionExpiredEvent.reset() },
            shape = RoundedCornerShape(16.dp),
            title = { Text("快麦会话已过期") },
            text = { Text("快麦API会话已过期，请在Web管理后台重新授权\n（请用电脑浏览器访问管理后台）") },
            confirmButton = {
                TextButton(onClick = { showSessionExpiredDialog = false; SessionExpiredEvent.reset() }) {
                    Text("知道了")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSessionExpiredDialog = false
                    SessionExpiredEvent.reset()
                    navController.navigate(Routes.SETTINGS)
                }) {
                    Text("前往设置")
                }
            }
        )
    }
}
