package com.termuxbridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    private var serverPort = mutableStateOf(8080)
    private var statusMessage = mutableStateOf("")
    
    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkAccessibilityStatus()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAccessibilityStatus()
        checkServerStatus()
        
        setContent {
            TermuxBridgeTheme {
                MainScreen(
                    accessibilityEnabled = accessibilityEnabled.value,
                    serverRunning = serverRunning.value,
                    serverPort = serverPort.value,
                    statusMessage = statusMessage.value,
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
        checkAccessibilityStatus()
        checkServerStatus()
    }
    
    private fun checkAccessibilityStatus() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
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
                
                val responseCode = connection.responseCode
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
    serverPort: Int,
    statusMessage: String,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                serverRunning = serverRunning,
                serverPort = serverPort
            )
            
            // 无障碍服务设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "无障碍服务",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (accessibilityEnabled) "已启用" else "未启用",
                            color = if (accessibilityEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        
                        Button(
                            onClick = onEnableAccessibility,
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
                    
                    Text(
                        "注意：需要在系统设置中启用 Termux Bridge 的无障碍服务",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // HTTP服务器控制
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "HTTP服务器",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("端口: $serverPort")
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            
            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "使用说明",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val instructions = listOf(
                        "1. 点击"启用服务"打开无障碍设置",
                        "2. 找到 Termux Bridge 并启用",
                        "3. 返回应用，点击"启动"按钮",
                        "4. 在Termux中使用命令控制手机",
                        "",
                        "示例命令：",
                        "curl -X POST http://127.0.0.1:8080/cmd \\",
                        "  -H 'Content-Type: application/json' \\",
                        "  -d '{\"action\":\"tap\",\"x\":540,\"y\":960}'"
                    )
                    
                    instructions.forEach { line ->
                        Text(
                            line,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
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
            
            // 命令参考
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "命令参考",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val commands = listOf(
                        Pair("tap", "点击坐标 {x, y}"),
                        Pair("tap_element", "点击元素 {text/resourceId}"),
                        Pair("swipe", "滑动 {startX, startY, endX, endY, duration}"),
                        Pair("input_text", "输入文本 {text}"),
                        Pair("key", "按键事件 {keyCode}"),
                        Pair("find_element", "查找元素 {text}"),
                        Pair("dump", "获取界面结构"),
                        Pair("screenshot", "截图(需额外权限)")
                    )
                    
                    commands.forEach { (cmd, desc) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                cmd,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.weight(0.3f)
                            )
                            Text(
                                desc,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.7f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatusCard(
    accessibilityEnabled: Boolean,
    serverRunning: Boolean,
    serverPort: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (accessibilityEnabled && serverRunning) 
                Color(0xFFE8F5E9) 
            else 
                Color(0xFFFFEBEE)
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
                    text = if (accessibilityEnabled && serverRunning) "服务运行中" else "服务未就绪",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "http://127.0.0.1:$serverPort",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (accessibilityEnabled && serverRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (accessibilityEnabled && serverRunning) "✓" else "✗",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
