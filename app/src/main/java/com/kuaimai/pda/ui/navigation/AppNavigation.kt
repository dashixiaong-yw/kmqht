package com.kuaimai.pda.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kuaimai.pda.ui.home.HomeScreen
import com.kuaimai.pda.ui.pickdetail.PickDetailScreen
import com.kuaimai.pda.ui.picklist.PickListScreen
import com.kuaimai.pda.ui.product.ProductScreen
import com.kuaimai.pda.ui.settings.SettingsScreen

/**
 * 应用导航
 * 单Activity架构，NavHost管理页面路由
 */
object Routes {
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
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPickList = { navController.navigate(Routes.PICK_LIST) },
                onNavigateToProduct = {
                    // 默认跳转到空商品页，由扫码进入
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
