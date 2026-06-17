package com.kuaimai.pda.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kuaimai.pda.BuildConfig
import com.kuaimai.pda.data.api.dto.UserResponse
import com.kuaimai.pda.data.repository.UserRepository
import com.kuaimai.pda.ui.theme.BrandBlue
import com.kuaimai.pda.ui.theme.SuccessBg
import com.kuaimai.pda.ui.theme.SuccessText
import com.kuaimai.pda.ui.theme.PrimaryLightBg
import com.kuaimai.pda.ui.theme.PrimaryLightText
import com.kuaimai.pda.ui.theme.SurfaceWhite
import com.kuaimai.pda.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 权限代码与显示名称映射
 */
private val PERMISSION_LABELS = mapOf(
    "settings" to "设置管理",
    "update_supplier" to "修改供应商",
    "update_remark" to "修改备注",
    "manage_area_image" to "库区图管理",
    "manage_box_image" to "箱规图管理"
)

/**
 * 设置页面
 * 包含：当前用户信息、用户管理、退出登录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userRepository: UserRepository,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUser by userRepository.currentUser.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingUserId by remember { mutableStateOf<Long?>(null) }
    var deletingUserId by remember { mutableStateOf<Long?>(null) }

    // 配置状态（移到顶层避免重组不一致）
    var serverUrl by remember { mutableStateOf(viewModel.getServerUrl()) }
    var apiKey by remember { mutableStateOf(viewModel.getApiKey()) }

    // 用户管理状态
    var isLoadingUsers by remember { mutableStateOf(false) }
    val userList = remember { mutableStateListOf<UserResponse>() }

    // 加载用户列表
    LaunchedEffect(Unit) {
        if (userRepository.hasPermission("settings")) {
            isLoadingUsers = true
            val result = userRepository.getUsers()
            if (result.isSuccess) {
                userList.clear()
                userList.addAll(result.getOrNull()?.data ?: emptyList())
            }
            isLoadingUsers = false
        }
    }

    // 退出登录确认弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        userRepository.logout()
                        onLogout()
                    }
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑用户弹窗
    val editingUser = userList.find { it.id == editingUserId }
    if (editingUser != null) {
        UserEditDialog(
            title = "编辑用户",
            initialUser = editingUser,
            onDismiss = { editingUserId = null },
            onConfirm = { _, password, permissions, isActive ->
                scope.launch {
                    val result = userRepository.updateUser(
                        editingUser.id, password, permissions, isActive
                    )
                    if (result.isSuccess) {
                        val listResult = userRepository.getUsers()
                        if (listResult.isSuccess) {
                            userList.clear()
                            userList.addAll(listResult.getOrNull()?.data ?: emptyList())
                        }
                    }
                    editingUserId = null
                }
            }
        )
    }

    // 删除用户弹窗
    val deletingUser = userList.find { it.id == deletingUserId }
    if (deletingUser != null) {
        AlertDialog(
            onDismissRequest = { deletingUserId = null },
            title = { Text("删除用户") },
            text = { Text("确定要删除用户 ${deletingUser.username} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        userRepository.deleteUser(deletingUser.id)
                        val listResult = userRepository.getUsers()
                        if (listResult.isSuccess) {
                            userList.clear()
                            userList.addAll(listResult.getOrNull()?.data ?: emptyList())
                        }
                    }
                    deletingUserId = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUserId = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前用户信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryLightBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前用户",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUser?.username ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryLightText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "权限: ${currentUser?.permissions?.map { PERMISSION_LABELS[it] ?: it }?.joinToString("、") ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 服务器配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "服务器配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 服务器地址
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveServerUrl(serverUrl) },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLightBg, contentColor = PrimaryLightText)
                    ) {
                        Text("保存")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // API Key
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveApiKey(apiKey) },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLightBg, contentColor = PrimaryLightText)
                    ) {
                        Text("保存")
                    }
                }
            }

            // 扫码与反馈配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "扫码与反馈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 扫码方式选择
                    val scanMethod by viewModel.scanMethod.collectAsState()
                    Text("扫码方式", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scanMethod == 0,
                            onClick = { viewModel.setScanMethod(0) }
                        )
                        Text("PDA硬件扫码", modifier = Modifier.clickable { viewModel.setScanMethod(0) })
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = scanMethod == 1,
                            onClick = { viewModel.setScanMethod(1) }
                        )
                        Text("相机扫码", modifier = Modifier.clickable { viewModel.setScanMethod(1) })
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = scanMethod == 2,
                            onClick = { viewModel.setScanMethod(2) }
                        )
                        Text("手动输入", modifier = Modifier.clickable { viewModel.setScanMethod(2) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 声音开关
                    val soundEnabled by viewModel.soundEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("扫码声音反馈")
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { viewModel.toggleSound(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 振动开关
                    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("扫码振动反馈")
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { viewModel.toggleVibration(it) }
                        )
                    }
                }
            }

            // 用户管理（仅settings权限可见）
            if (userRepository.hasPermission("settings")) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "用户管理",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            var showAddDialog by remember { mutableStateOf(false) }
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "添加用户")
                            }

                            if (showAddDialog) {
                                UserEditDialog(
                                    title = "添加用户",
                                    onDismiss = { showAddDialog = false },
                                    onConfirm = { username, password, permissions, _ ->
                                        scope.launch {
                                            val result = userRepository.createUser(username, password ?: "", permissions)
                                            if (result.isSuccess) {
                                                val listResult = userRepository.getUsers()
                                                if (listResult.isSuccess) {
                                                    userList.clear()
                                                    userList.addAll(listResult.getOrNull()?.data ?: emptyList())
                                                }
                                            }
                                            showAddDialog = false
                                        }
                                    }
                                )
                            }
                        }

                        Divider()

                        if (isLoadingUsers) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .size(32.dp),
                                color = BrandBlue
                            )
                        } else {
                            userList.forEach { user ->
                                UserItemRow(
                                    user = user,
                                    currentUserId = currentUser?.id ?: 0,
                                    onEdit = { editingUserId = user.id },
                                    onDelete = { deletingUserId = user.id }
                                )
                                if (user != userList.last()) {
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }

            // 退出登录按钮
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessBg,
                    contentColor = SuccessText
                )
            ) {
                Text("退出登录", style = MaterialTheme.typography.titleMedium)
            }

            // 版本信息
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 用户行
 */
@Composable
private fun UserItemRow(
    user: UserResponse,
    currentUserId: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (user.isActive) "启用" else "禁用",
                style = MaterialTheme.typography.bodySmall,
                color = if (user.isActive) SuccessText else MaterialTheme.colorScheme.error
            )
            Text(
                text = user.permissions.map { PERMISSION_LABELS[it] ?: it }.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 编辑按钮
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑",
                tint = BrandBlue,
                modifier = Modifier.size(20.dp)
            )
        }

        // 删除按钮（不能删除自己）
        if (user.id != currentUserId) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 用户编辑/添加弹窗
 */
@Composable
private fun UserEditDialog(
    title: String,
    initialUser: UserResponse? = null,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String?, permissions: List<String>, isActive: Boolean?) -> Unit
) {
    var username by remember { mutableStateOf(initialUser?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(initialUser?.isActive ?: true) }
    val selectedPermissions = remember { mutableStateListOf<String>() }

    // 初始化权限
    LaunchedEffect(initialUser) {
        selectedPermissions.clear()
        if (initialUser != null) {
            selectedPermissions.addAll(initialUser.permissions)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 用户名
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    enabled = initialUser == null,
                    modifier = Modifier.fillMaxWidth()
                )

                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initialUser == null) "密码" else "新密码（留空不修改）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 启用/禁用开关（仅编辑模式显示）
                if (initialUser != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("启用账户", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("权限分配", style = MaterialTheme.typography.labelMedium)

                // 权限复选框
                PERMISSION_LABELS.forEach { (code, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedPermissions.contains(code)) {
                                    selectedPermissions.remove(code)
                                } else {
                                    selectedPermissions.add(code)
                                }
                            }
                    ) {
                        Checkbox(
                            checked = selectedPermissions.contains(code),
                            onCheckedChange = { checked ->
                                if (checked) selectedPermissions.add(code) else selectedPermissions.remove(code)
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (initialUser == null && (username.isBlank() || password.isBlank())) return@TextButton
                val isActiveParam = if (initialUser != null) isActive else null
                onConfirm(username, password.ifEmpty { null }, selectedPermissions.toList(), isActiveParam)
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
