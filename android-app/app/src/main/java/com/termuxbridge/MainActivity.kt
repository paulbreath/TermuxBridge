package com.termuxbridge

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termuxbridge.service.BridgeAccessibilityService
import com.termuxbridge.service.HttpServerService
import com.termuxbridge.ui.theme.TermuxBridgeTheme

class MainActivity : ComponentActivity() {
    
    private var serverRunning = mutableStateOf(false)
    private var accessibilityEnabled = mutableStateOf(false)
    private var overlayPermissionGranted = mutableStateOf(false)
    private var serverPort = mutableStateOf(8080)
    private var statusMessage = mutableStateOf("")
    
    private val overlayPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkOverlayPermission()
    }
    
    private val accessibilityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkAccessibilityStatus()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查所有权限状态
        checkOverlayPermission()
        checkAccessibilityStatus()
        checkServerStatus()
        
        setContent {
            TermuxBridgeTheme {
                MainScreen(
                    accessibilityEnabled = accessibilityEnabled.value,
                    serverRunning = serverRunning.value,
                    overlayPermissionGranted = overlayPermissionGranted.value,
                    serverPort = serverPort.value,
                    statusMessage = statusMessage.value,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onEnableAccessibility = { openAccessibilitySettings() },
                    onStartServer = { startHttpServer() },
                    onStopServer = { stopHttpServer() },
                    onTestServer = { testServerConnection() }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        checkAccessibilityStatus()
        checkServerStatus()
    }
    
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionGranted.value = Settings.canDrawOverlays(this)
        } else {
            overlayPermissionGranted.value = true
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    private fun checkAccessibilityStatus() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        
        accessibilityEnabled.value = enabledServices.contains(packageName)
    }
    
    private fun checkServerStatus() {
        serverRunning.value = HttpServerService.isRunning
        serverPort.value = HttpServerService.port
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityLauncher.launch(intent)
    }
    
    private fun startHttpServer() {
        if (!accessibilityEnabled.value) {
            statusMessage.value = "请先启用无障碍服务"
            return
        }
        
        val intent = Intent(this, HttpServerService::class.java).apply {
            action = HttpServerService.ACTION_START
            putExtra(HttpServerService.EXTRA_PORT, serverPort.value)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        serverRunning.value = true
        statusMessage.value = "HTTP服务器已启动在端口 ${serverPort.value}"
    }
    
    private fun stopHttpServer() {
        val intent = Intent(this, HttpServerService::class.java).apply {
            action = HttpServerService.ACTION_STOP
        }
        startService(intent)
        serverRunning.value = false
        statusMessage.value = "HTTP服务器已停止"
    }
    
    private fun testServerConnection() {
        statusMessage.value = "测试连接中..."
        
        Thread {
            try {
                val url = java.net.URL("http://127.0.0.1:${serverPort.value}/ping")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                
                val response = connection.inputStream.bufferedReader().readText()
                
                runOnUiThread {
                    statusMessage.value = "连接成功! 响应: $response"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusMessage.value = "连接失败: ${e.message}"
                }
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    accessibilityEnabled: Boolean,
    serverRunning: Boolean,
    overlayPermissionGranted: Boolean,
    serverPort: Int,
    statusMessage: String,
    onRequestOverlayPermission: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onTestServer: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Termux Bridge",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态卡片
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                serverRunning = serverRunning,
                serverPort = serverPort
            )
            
            // 悬浮窗权限（关键！）
            if (!overlayPermissionGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⚠️ 悬浮窗权限（必须）",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            "此权限是启用无障碍服务的前提条件",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE65100)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("授权悬浮窗权限")
                        }
                    }
                }
            }
            
            // 无障碍服务设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "无障碍服务",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (accessibilityEnabled) "✓ 已启用" else "✗ 未启用",
                            color = if (accessibilityEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        
                        Button(
                            onClick = onEnableAccessibility,
                            enabled = overlayPermissionGranted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (accessibilityEnabled) 
                                    MaterialTheme.colorScheme.secondary 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (accessibilityEnabled) "打开设置" else "启用服务")
                        }
                    }
                    
                    if (!overlayPermissionGranted) {
                        Text(
                            "⚠️ 请先授权悬浮窗权限",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
            
            // HTTP服务器控制
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "HTTP服务器",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("端口: $serverPort")
                        
                        if (serverRunning) {
                            Button(
                                onClick = onStopServer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                )
                            ) {
                                Text("停止")
                            }
                        } else {
                            Button(
                                onClick = onStartServer,
                                enabled = accessibilityEnabled
                            ) {
                                Text("启动")
                            }
                        }
                    }
                    
                    Button(
                        onClick = onTestServer,
                        enabled = serverRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("测试连接")
                    }
                }
            }
            
            // 状态消息
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        statusMessage,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }
            
            // 安装步骤
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "安装步骤",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val steps = listOf(
                        "1. 点击「授权悬浮窗权限」按钮",
                        "2. 在设置页面开启权限",
                        "3. 返回应用，点击「启用服务」",
                        "4. 在无障碍设置中找到 Termux Bridge",
                        "5. 开启开关并确认",
                        "6. 返回应用，启动HTTP服务器"
                    )
                    
                    steps.forEach { step ->
                        Text(step, fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusCard(
    accessibilityEnabled: Boolean,
    serverRunning: Boolean,
    serverPort: Int
) {
    val isReady = accessibilityEnabled && serverRunning
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isReady) "服务运行中" else "服务未就绪",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "http://127.0.0.1:$serverPort",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isReady) Color(0xFF4CAF50) else Color(0xFFF44336),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isReady) "✓" else "✗",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
