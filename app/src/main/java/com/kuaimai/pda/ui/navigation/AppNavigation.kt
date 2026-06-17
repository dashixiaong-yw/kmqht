package com.kuaimai.pda.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import android.content.SharedPreferences
import com.kuaimai.pda.data.repository.AuthRepository
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.guide.GuideScreen
import com.kuaimai.pda.ui.home.HomeScreen
import com.kuaimai.pda.ui.login.LoginScreen
import com.kuaimai.pda.ui.pickdetail.PickDetailScreen
import com.kuaimai.pda.ui.picklist.PickListScreen
import com.kuaimai.pda.ui.product.ProductScreen
import com.kuaimai.pda.ui.settings.SettingsScreen
import com.kuaimai.pda.ui.settings.SettingsViewModel.Companion.KEY_GUIDE_SHOWN
import com.kuaimai.pda.util.NetworkMonitor
import com.kuaimai.pda.util.SessionExpiredEvent
import javax.inject.Named

/**
 * 应用导航
 * 单Activity架构，NavHost管理页面路由
 * 启动时检查登录状态，未登录→登录页
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val GUIDE = "guide"
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
    prefs: SharedPreferences,
    @Named("encrypted") encryptedPrefs: SharedPreferences,
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

    // 启动时验证token有效性，并判断是否首次使用
    LaunchedEffect(Unit) {
        if (userRepository.isLoggedIn()) {
            val valid = userRepository.validateToken()
            if (valid) {
                // 已登录且token有效，检查是否首次使用
                val guideShown = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
                startDestination = if (guideShown) Routes.HOME else Routes.GUIDE
            } else {
                startDestination = Routes.LOGIN
            }
        } else {
            startDestination = Routes.LOGIN
        }
        isCheckingAuth = false
    }

    // 监听token过期事件，自动跳转登录页
    LaunchedEffect(Unit) {
        userRepository.loginRequired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
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
                    // 登录成功后检查是否需要引导
                    val guideShown = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
                    val target = if (guideShown) Routes.HOME else Routes.GUIDE
                    navController.navigate(target) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.GUIDE) {
            GuideScreen(
                prefs = prefs,
                encryptedPrefs = encryptedPrefs,
                onFinish = {
                    // 引导完成，导航到主页
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.GUIDE) { inclusive = true }
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
                prefs = prefs,
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
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }

    // 快麦Session过期弹窗
    if (showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showSessionExpiredDialog = false; SessionExpiredEvent.reset() },
            title = { Text("快麦会话已过期") },
            text = { Text("快麦API会话已过期，请在Web管理后台重新授权\n（浏览器访问 http://服务器地址:8900/admin）") },
            confirmButton = {
                TextButton(onClick = { showSessionExpiredDialog = false; SessionExpiredEvent.reset() }) {
                    Text("知道了")
                }
            }
        )
    }
}
