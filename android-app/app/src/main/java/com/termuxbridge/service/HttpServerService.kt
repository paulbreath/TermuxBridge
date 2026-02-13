package com.termuxbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.termuxbridge.MainActivity
import com.termuxbridge.R
import com.termuxbridge.model.CommandResult
import com.termuxbridge.model.ExecuteCommand
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class HttpServerService : Service() {
    
    companion object {
        const val ACTION_START = "com.termuxbridge.action.START"
        const val ACTION_STOP = "com.termuxbridge.action.STOP"
        const val EXTRA_PORT = "port"
        
        var isRunning = false
            private set
        
        var port = 8080
            private set
        
        private const val NOTIFICATION_CHANNEL_ID = "termux_bridge_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var serverJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                port = intent.getIntExtra(EXTRA_PORT, 8080)
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
        executor.shutdown()
    }
    
    private fun startServer() {
        if (isRunning) return
        
        val notification = createNotification("服务运行中 - 端口 $port")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        
        serverJob = serviceScope.launch {
            try {
                serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                
                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        executor.submit { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!serverSocket?.isClosed!!) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning = false
            }
        }
    }
    
    private fun stopServer() {
        isRunning = false
        serverJob?.cancel()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        serverSocket = null
    }
    
    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()
            
            // 读取请求行
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            
            if (parts.size < 3) return
            
            val method = parts[0]
            val path = parts[1]
            
            // 读取请求头
            var contentLength = 0
            var line: String?
            
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substring(15).trim().toInt()
                }
            }
            
            // 读取请求体
            var body = ""
            if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                reader.read(buffer, 0, contentLength)
                body = String(buffer)
            }
            
            // 处理请求
            val response = when {
                path == "/ping" -> handlePing()
                path == "/status" -> handleStatus()
                path == "/cmd" && method == "POST" -> handleCommand(body)
                path.startsWith("/element/") && method == "POST" -> handleElementCommand(path, body)
                else -> Response(404, mapOf("error" to "Not Found"))
            }
            
            sendResponse(output, response)
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handlePing(): Response {
        return Response(200, mapOf(
            "status" to "ok",
            "service" to "TermuxBridge",
            "version" to "1.0.0"
        ))
    }
    
    private fun handleStatus(): Response {
        val accessibilityEnabled = BridgeAccessibilityService.isServiceEnabled()
        
        return Response(200, mapOf(
            "status" to if (accessibilityEnabled) "ready" else "accessibility_disabled",
            "http_server" to "running",
            "accessibility_service" to accessibilityEnabled,
            "port" to port
        ))
    }
    
    private fun handleCommand(body: String): Response {
        try {
            val json = JSONObject(body)
            val command = ExecuteCommand.fromJson(json)
            
            val service = BridgeAccessibilityService.instance
            
            if (service == null) {
                return Response(503, mapOf(
                    "success" to false,
                    "error" to "Accessibility service not enabled"
                ))
            }
            
            val result = service.executeCommand(command)
            
            return Response(
                if (result.success) 200 else 400,
                result.toMap()
            )
            
        } catch (e: Exception) {
            return Response(400, mapOf(
                "success" to false,
                "error" to "Invalid command: ${e.message}"
            ))
        }
    }
    
    private fun handleElementCommand(path: String, body: String): Response {
        // 解析路径中的元素选择器
        val elementPath = path.removePrefix("/element/")
        
        try {
            val json = if (body.isNotEmpty()) JSONObject(body) else JSONObject()
            val command = ExecuteCommand(elementPath, json)
            
            val service = BridgeAccessibilityService.instance
            
            if (service == null) {
                return Response(503, mapOf(
                    "success" to false,
                    "error" to "Accessibility service not enabled"
                ))
            }
            
            val result = service.executeCommand(command)
            
            return Response(
                if (result.success) 200 else 400,
                result.toMap()
            )
            
        } catch (e: Exception) {
            return Response(400, mapOf(
                "success" to false,
                "error" to "Invalid command: ${e.message}"
            ))
        }
    }
    
    private fun sendResponse(output: OutputStream, response: Response) {
        val jsonResponse = JSONObject(response.body).toString()
        
        val httpResponse = buildString {
            append("HTTP/1.1 ${response.statusCode} ")
            append(when (response.statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                503 -> "Service Unavailable"
                else -> "Unknown"
            })
            append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${jsonResponse.toByteArray(Charsets.UTF_8).size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(jsonResponse)
        }
        
        output.write(httpResponse.toByteArray(Charsets.UTF_8))
        output.flush()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Termux Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Termux Bridge HTTP Server"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Termux Bridge")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    data class Response(
        val statusCode: Int,
        val body: Map<String, Any?>
    )
}
