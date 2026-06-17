package com.kuaimai.pda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.home.HomeScreen
import com.kuaimai.pda.ui.login.LoginScreen
import com.kuaimai.pda.ui.pickdetail.PickDetailScreen
import com.kuaimai.pda.ui.picklist.PickListScreen
import com.kuaimai.pda.ui.product.ProductScreen
import com.kuaimai.pda.ui.settings.SettingsScreen

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
    const val PRODUCT = "product/{skuOuterId}"
    const val SETTINGS = "settings"

    /** 构建取货单详情路由 */
    fun pickDetailRoute(orderId: Long): String = "pickDetail/$orderId"

    /** 构建商品详情路由 */
    fun productRoute(skuOuterId: String): String = "product/$skuOuterId"
}

@Composable
fun AppNavigation(
    userRepository: UserRepository
) {
    val navController = rememberNavController()
    var isCheckingAuth by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf(Routes.LOGIN) }

    // 启动时验证token有效性
    LaunchedEffect(Unit) {
        if (userRepository.isLoggedIn()) {
            val valid = userRepository.validateToken()
            startDestination = if (valid) Routes.HOME else Routes.LOGIN
        } else {
            startDestination = Routes.LOGIN
        }
        isCheckingAuth = false
    }

    if (isCheckingAuth) {
        // 验证中显示空白或splash
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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
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
        ) {
            PickDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProduct = { skuOuterId ->
                    navController.navigate(Routes.productRoute(skuOuterId))
                }
            )
        }

        composable(
            route = Routes.PRODUCT,
            arguments = listOf(navArgument("skuOuterId") { type = NavType.StringType })
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
}
