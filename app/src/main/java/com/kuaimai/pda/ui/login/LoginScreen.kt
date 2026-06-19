package com.kuaimai.pda.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import kotlinx.coroutines.launch

/**
 * 登录页面
 * 用户名+密码登录，登录成功后跳转首页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    userRepository: UserRepository,
    onLoginSuccess: () -> Unit
) {
    // ↓↓↓ 同步加载本地保存的凭据和历史 ↓↓↓
    val loadedHistory = remember { userRepository.getLoginHistory() }
    val loginHistory = remember { mutableStateListOf<String>().apply { addAll(loadedHistory) } }
    var savePasswordChecked by remember { mutableStateOf(userRepository.isSavePasswordEnabled()) }
    val savedUser = remember { userRepository.getSavedUsername() }
    val savedUsername by remember { mutableStateOf(savedUser) }

    var username by remember {
        mutableStateOf(if (savePasswordChecked && savedUser.isNotEmpty()) savedUser else "")
    }
    var password by remember {
        mutableStateOf(
            if (savePasswordChecked && savedUser.isNotEmpty()) userRepository.getSavedPassword() else ""
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var changePasswordError by remember { mutableStateOf("") }
    var isChangingPassword by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快麦取货通") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBlue,
                    titleContentColor = SurfaceWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "用户登录",
                style = MaterialTheme.typography.headlineMedium,
                color = BrandBlue
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 用户名输入（带登录历史下拉）
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded && loginHistory.isNotEmpty(),
                onExpandedChange = { if (loginHistory.isNotEmpty()) dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { newValue ->
                        username = newValue
                        errorMessage = ""
                        if (newValue != savedUsername && password.isNotEmpty()) {
                            password = ""
                        }
                    },
                    label = { Text("用户名") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "用户名",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = {
                        if (loginHistory.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = !isLoading
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded && loginHistory.isNotEmpty(),
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    loginHistory.forEach { historyUsername ->
                        DropdownMenuItem(
                            text = { Text(historyUsername) },
                            onClick = {
                                username = historyUsername
                                dropdownExpanded = false
                                if (historyUsername != savedUsername) {
                                    password = ""
                                } else if (savePasswordChecked) {
                                    password = userRepository.getSavedPassword()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 密码输入
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = { Text("密码") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "密码",
                        modifier = Modifier.size(24.dp)
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 记住密码
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = savePasswordChecked,
                    onCheckedChange = { savePasswordChecked = it },
                    colors = CheckboxDefaults.colors(checkedColor = BrandBlue),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "记住密码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 错误提示
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录按钮
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = BrandBlue
                )
            } else {
                Button(
                    onClick = {
                        if (username.isBlank()) {
                            errorMessage = "请输入用户名"
                            return@Button
                        }
                        if (password.isBlank()) {
                            errorMessage = "请输入密码"
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            val result = userRepository.login(username, password)
                            isLoading = false
                            if (result.isSuccess) {
                                // 保存凭据和登录历史（本地加密存储）
                                if (savePasswordChecked && password.isNotEmpty()) {
                                    userRepository.saveCredentials(username, password)
                                } else {
                                    userRepository.clearSavedCredentials()
                                }
                                userRepository.setSavePasswordEnabled(savePasswordChecked)
                                userRepository.saveToLoginHistory(username)

                                val user = result.getOrNull()
                                if (user != null) {
                                    // 检查是否需要强制修改密码
                                    val loginResult = userRepository.getLoginResult()
                                    if (loginResult?.mustChangePassword == true) {
                                        showChangePasswordDialog = true
                                    } else {
                                        onLoginSuccess()
                                    }
                                } else {
                                    onLoginSuccess()
                                }
                            } else {
                                errorMessage = friendlyErrorMessage(result.exceptionOrNull())
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryLightBg,
                        contentColor = PrimaryLightText
                    )
                ) {
                    Text(
                        text = "登 录",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    // 强制修改密码对话框
    if (showChangePasswordDialog) {
        BackHandler(enabled = true) { /* 吞掉返回键，必须修改密码 */ }
        AlertDialog(
            onDismissRequest = { /* 不允许关闭，必须修改密码 */ },
            title = { Text("安全提示") },
            text = {
                Column {
                    Text("检测到您使用的是默认密码，请立即修改密码以确保安全")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            changePasswordError = ""
                        },
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isChangingPassword
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            changePasswordError = ""
                        },
                        label = { Text("确认密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isChangingPassword
                    )
                    if (changePasswordError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = changePasswordError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.length < 4) {
                            changePasswordError = "密码长度不能少于4位"
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            changePasswordError = "两次密码输入不一致"
                            return@Button
                        }
                        isChangingPassword = true
                        scope.launch {
                            val currentUser = userRepository.currentUser.value
                            val userId = currentUser?.id ?: run {
                                changePasswordError = "无法获取用户信息，请重新登录"
                                isChangingPassword = false
                                return@launch
                            }
                            val result = userRepository.updateUser(
                                userId,
                                newPassword,
                                null,
                                null
                            )
                            isChangingPassword = false
                            if (result.isSuccess) {
                                showChangePasswordDialog = false
                                onLoginSuccess()
                            } else {
                                changePasswordError = "修改密码失败: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isChangingPassword
                ) {
                    Text(if (isChangingPassword) "修改中..." else "确认修改")
                }
            }
        )
    }
}

/**
 * 将网络异常转为中文友好提示
 */
private fun friendlyErrorMessage(throwable: Throwable?): String {
    if (throwable == null) return "登录失败"
    return when (throwable) {
        is java.net.SocketTimeoutException -> "连接超时，请检查网络"
        is java.net.ConnectException -> "无法连接服务器，请检查网络"
        is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络设置"
        else -> {
            if ((throwable as? retrofit2.HttpException)?.code() == 401) "用户名或密码错误"
            else "登录失败: ${throwable.message}"
        }
    }
}
