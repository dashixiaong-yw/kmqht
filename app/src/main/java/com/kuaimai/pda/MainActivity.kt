package com.kuaimai.pda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kuaimai.pda.ui.navigation.AppNavigation
import com.kuaimai.pda.ui.theme.KuaimaiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单Activity架构，承载Compose导航
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuaimaiTheme {
                AppNavigation()
            }
        }
    }
}
