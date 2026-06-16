package com.kuaimai.pda.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite

/**
 * 首次使用引导页面
 * Step 1: 配置服务器地址
 * Step 2: 选择扫码方式
 * Step 3: 完成
 */
@Composable
fun GuideScreen(
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var serverUrl by remember { mutableStateOf("http://") }
    var selectedScanMethod by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (currentStep) {
            0 -> StepServerConfig(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                onNext = { currentStep = 1 }
            )
            1 -> StepScanMethod(
                selectedMethod = selectedScanMethod,
                onMethodSelected = { selectedScanMethod = it },
                onNext = { currentStep = 2 }
            )
            2 -> StepComplete(onFinish = onFinish)
        }
    }
}

/**
 * Step 1: 配置服务器地址
 */
@Composable
private fun StepServerConfig(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "欢迎使用快麦取货通",
        style = MaterialTheme.typography.headlineSmall,
        color = BrandBlue
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "第1步：配置服务器地址",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text("服务器地址") },
        placeholder = { Text("例如: http://192.168.1.100:8000") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onNext,
        enabled = serverUrl.startsWith("http"),
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
        text = "您可以在设置页面随时修改配置",
        style = MaterialTheme.typography.bodyMedium
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
