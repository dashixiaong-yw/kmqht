package com.kuaimai.pda.ui.guide

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuaimai.pda.scanner.CameraScanScreen
import com.kuaimai.pda.ui.settings.SettingsViewModel.Companion.KEY_GUIDE_SHOWN
import com.kuaimai.pda.ui.settings.SettingsViewModel.Companion.KEY_SCAN_METHOD
import com.kuaimai.pda.util.AppConstants
import com.kuaimai.pda.util.PrefsKeys
import com.kuaimai.pda.util.SetupQrParser
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite

/**
 * 首次使用引导页面
 * Step 1: 配置服务器地址（支持扫码配置）
 * Step 2: 选择扫码方式
 * Step 3: 完成
 */
@Composable
fun GuideScreen(
    prefs: SharedPreferences,
    encryptedPrefs: SharedPreferences,
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var serverUrl by remember { mutableStateOf(AppConstants.DEFAULT_SERVER_URL) }
    var apiKey by remember { mutableStateOf("") }
    var selectedScanMethod by remember { mutableIntStateOf(0) }
    var showCameraScan by remember { mutableStateOf(false) }
    var qrScanError by remember { mutableStateOf(false) }

    // 扫码配置模式
    if (showCameraScan) {
        CameraScanScreen(
            onBarcodeScanned = { barcode ->
                showCameraScan = false
                val result = SetupQrParser.parse(barcode)
                if (result != null) {
                    qrScanError = false
                    serverUrl = result.serverUrl
                    if (result.apiKey.isNotEmpty()) {
                        apiKey = result.apiKey
                    }
                } else {
                    qrScanError = true
                }
            },
            onClose = { showCameraScan = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 扫码解析失败提示
        if (qrScanError) {
            Text(
                text = "未识别到有效配置二维码，请重试或手动输入",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (currentStep) {
            0 -> StepServerConfig(
                serverUrl = serverUrl,
                apiKey = apiKey,
                onScanConfig = { showCameraScan = true },
                onNext = {
                    encryptedPrefs.edit().putString(PrefsKeys.KEY_SERVER_URL, serverUrl.trim()).apply()
                    if (apiKey.isNotBlank()) {
                        encryptedPrefs.edit().putString(PrefsKeys.KEY_API_KEY, apiKey.trim()).apply()
                    }
                    currentStep = 1
                }
            )
            1 -> StepScanMethod(
                selectedMethod = selectedScanMethod,
                onMethodSelected = { selectedScanMethod = it },
                onNext = {
                    // 保存扫码方式到SharedPreferences
                    prefs.edit().putInt(KEY_SCAN_METHOD, selectedScanMethod).apply()
                    currentStep = 2
                }
            )
            2 -> StepComplete(
                onFinish = {
                    // 标记引导已完成
                    prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
                    onFinish()
                }
            )
        }
    }
}

/**
 * Step 1: 服务器配置（预置FRP地址，扫码可切换内网地址）
 */
@Composable
private fun StepServerConfig(
    serverUrl: String,
    apiKey: String,
    onScanConfig: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "欢迎使用快麦取货通",
        style = MaterialTheme.typography.headlineSmall,
        color = BrandBlue
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "第1步：服务器配置",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "服务器地址已自动配置",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = serverUrl,
        style = MaterialTheme.typography.bodyLarge,
        color = BrandBlue
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onScanConfig,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandBlue,
            contentColor = SurfaceWhite
        )
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = "扫码切换地址",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text("扫码切换地址（内网部署时使用）")
    }

    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryLightBg,
            contentColor = PrimaryLightText
        )
    ) {
        Text("下一步")
    }
}

/**
 * Step 2: 选择扫码方式
 */
@Composable
private fun StepScanMethod(
    selectedMethod: Int,
    onMethodSelected: (Int) -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "第2步：选择扫码方式",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(24.dp))

    val scanMethods = listOf(
        "PDA硬件扫码（iData/Urovo/Zebra/Newland）",
        "相机扫码（ML Kit）",
        "手动输入条码"
    )

    scanMethods.forEachIndexed { index, label ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            RadioButton(
                selected = selectedMethod == index,
                onClick = { onMethodSelected(index) }
            )
            Text(text = label, modifier = Modifier.padding(start = 8.dp))
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandBlue,
            contentColor = SurfaceWhite
        )
    ) {
        Text("下一步")
    }
}

/**
 * Step 3: 完成
 */
@Composable
private fun StepComplete(onFinish: () -> Unit) {
    Text(
        text = "设置完成！",
        style = MaterialTheme.typography.headlineSmall,
        color = BrandBlue
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "配置已保存，立即生效",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "您也可以在设置页面随时修改配置",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryLightBg,
            contentColor = PrimaryLightText
        )
    ) {
        Text("开始使用")
    }
}
