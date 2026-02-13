package com.termuxbridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.termuxbridge.model.CommandResult
import com.termuxbridge.model.ExecuteCommand
import com.termuxbridge.util.NodeInfoExtractor
import kotlinx.coroutines.*
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {
    
    companion object {
        var instance: BridgeAccessibilityService? = null
            private set
        
        fun isServiceEnabled(): Boolean = instance != null
        
        fun getInstance(): BridgeAccessibilityService? = instance
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里监听界面变化事件
    }
    
    override fun onInterrupt() {
        // 处理中断
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
    
    /**
     * 执行命令
     */
    fun executeCommand(command: ExecuteCommand): CommandResult {
        return when (command.action) {
            "tap" -> executeTap(command)
            "tap_element" -> executeTapElement(command)
            "swipe" -> executeSwipe(command)
            "long_press" -> executeLongPress(command)
            "input_text" -> executeInputText(command)
            "key" -> executeKeyEvent(command)
            "find_element" -> executeFindElement(command)
            "dump" -> executeDumpHierarchy()
            "scroll_forward" -> executeScrollForward(command)
            "scroll_backward" -> executeScrollBackward(command)
            "back" -> executeBack()
            "home" -> executeHome()
            "recent" -> executeRecent()
            "notifications" -> executeNotifications()
            "quick_settings" -> executeQuickSettings()
            else -> CommandResult.error("Unknown action: ${command.action}")
        }
    }
    
    /**
     * 点击坐标
     */
    private fun executeTap(command: ExecuteCommand): CommandResult {
        val x = command.params.optInt("x", -1)
        val y = command.params.optInt("y", -1)
        
        if (x < 0 || y < 0) {
            return CommandResult.error("Invalid coordinates: x=$x, y=$y")
        }
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGestureSync(gesture)
    }
    
    /**
     * 点击元素
     */
    private fun executeTapElement(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", null)
        val resourceId = command.params.optString("resourceId", null)
        val desc = command.params.optString("desc", null)
        val className = command.params.optString("className", null)
        val index = command.params.optInt("index", 0)
        
        val nodes = findNodes(text, resourceId, desc, className)
        
        if (nodes.isEmpty()) {
            return CommandResult.error("Element not found: text=$text, resourceId=$resourceId, desc=$desc")
        }
        
        if (index >= nodes.size) {
            return CommandResult.error("Index out of range: index=$index, found=${nodes.size}")
        }
        
        val node = nodes[index]
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        
        val path = Path().apply {
            moveTo(centerX, centerY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGestureSync(gesture)
    }
    
    /**
     * 滑动
     */
    private fun executeSwipe(command: ExecuteCommand): CommandResult {
        val startX = command.params.optInt("startX", 0)
        val startY = command.params.optInt("startY", 0)
        val endX = command.params.optInt("endX", 0)
        val endY = command.params.optInt("endY", 0)
        val duration = command.params.optLong("duration", 300)
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(gesture)
    }
    
    /**
     * 长按
     */
    private fun executeLongPress(command: ExecuteCommand): CommandResult {
        val x = command.params.optInt("x", -1)
        val y = command.params.optInt("y", -1)
        val duration = command.params.optLong("duration", 1000)
        
        if (x < 0 || y < 0) {
            return CommandResult.error("Invalid coordinates: x=$x, y=$y")
        }
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGestureSync(gesture)
    }
    
    /**
     * 输入文本
     */
    private fun executeInputText(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", "")
        
        val rootNode = rootInActiveWindow ?: return CommandResult.error("No active window")
        
        // 查找当前焦点的可编辑节点
        val focusNode = findFocusableNode(rootNode)
        
        if (focusNode == null) {
            return CommandResult.error("No focusable node found")
        }
        
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val result = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        return if (result) {
            CommandResult.success("Text input: $text")
        } else {
            CommandResult.error("Failed to input text")
        }
    }
    
    /**
     * 按键事件
     */
    private fun executeKeyEvent(command: ExecuteCommand): CommandResult {
        val keyCode = command.params.optInt("keyCode", 0)
        
        return try {
            val result = performGlobalAction(keyCode)
            if (result) {
                CommandResult.success("Key event sent: $keyCode")
            } else {
                CommandResult.error("Failed to send key event")
            }
        } catch (e: Exception) {
            CommandResult.error("Key event error: ${e.message}")
        }
    }
    
    /**
     * 返回
     */
    private fun executeBack(): CommandResult {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        return if (result) {
            CommandResult.success("Back action performed")
        } else {
            CommandResult.error("Failed to perform back action")
        }
    }
    
    /**
     * Home
     */
    private fun executeHome(): CommandResult {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        return if (result) {
            CommandResult.success("Home action performed")
        } else {
            CommandResult.error("Failed to perform home action")
        }
    }
    
    /**
     * 最近任务
     */
    private fun executeRecent(): CommandResult {
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        return if (result) {
            CommandResult.success("Recent action performed")
        } else {
            CommandResult.error("Failed to perform recent action")
        }
    }
    
    /**
     * 通知栏
     */
    private fun executeNotifications(): CommandResult {
        val result = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        return if (result) {
            CommandResult.success("Notifications action performed")
        } else {
            CommandResult.error("Failed to perform notifications action")
        }
    }
    
    /**
     * 快速设置
     */
    private fun executeQuickSettings(): CommandResult {
        val result = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        return if (result) {
            CommandResult.success("Quick settings action performed")
        } else {
            CommandResult.error("Failed to perform quick settings action")
        }
    }
    
    /**
     * 向前滚动
     */
    private fun executeScrollForward(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", null)
        val resourceId = command.params.optString("resourceId", null)
        
        val nodes = findNodes(text, resourceId, null, null)
        
        if (nodes.isEmpty()) {
            return CommandResult.error("Element not found for scroll")
        }
        
        for (node in nodes) {
            if (node.isScrollable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (result) {
                    return CommandResult.success("Scroll forward performed")
                }
            }
        }
        
        return CommandResult.error("Failed to scroll forward")
    }
    
    /**
     * 向后滚动
     */
    private fun executeScrollBackward(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", null)
        val resourceId = command.params.optString("resourceId", null)
        
        val nodes = findNodes(text, resourceId, null, null)
        
        if (nodes.isEmpty()) {
            return CommandResult.error("Element not found for scroll")
        }
        
        for (node in nodes) {
            if (node.isScrollable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                if (result) {
                    return CommandResult.success("Scroll backward performed")
                }
            }
        }
        
        return CommandResult.error("Failed to scroll backward")
    }
    
    /**
     * 查找元素
     */
    private fun executeFindElement(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", null)
        val resourceId = command.params.optString("resourceId", null)
        val desc = command.params.optString("desc", null)
        val className = command.params.optString("className", null)
        
        val nodes = findNodes(text, resourceId, desc, className)
        
        if (nodes.isEmpty()) {
            return CommandResult.error("Element not found")
        }
        
        val resultArray = org.json.JSONArray()
        
        for (node in nodes) {
            val nodeInfo = NodeInfoExtractor.extract(node)
            resultArray.put(nodeInfo)
        }
        
        return CommandResult.success("Found ${nodes.size} elements", resultArray)
    }
    
    /**
     * 获取界面层级结构
     */
    private fun executeDumpHierarchy(): CommandResult {
        val rootNode = rootInActiveWindow ?: return CommandResult.error("No active window")
        
        val hierarchy = NodeInfoExtractor.extractHierarchy(rootNode)
        
        return CommandResult.success("Hierarchy dumped", hierarchy)
    }
    
    /**
     * 同步执行手势
     */
    private fun dispatchGestureSync(gesture: GestureDescription): CommandResult {
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result = true
                latch.countDown()
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result = false
                latch.countDown()
            }
        }
        
        dispatchGesture(gesture, callback, null)
        
        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            return CommandResult.error("Gesture timeout")
        }
        
        return if (result) {
            CommandResult.success("Gesture completed")
        } else {
            CommandResult.error("Gesture cancelled")
        }
    }
    
    /**
     * 查找节点
     */
    private fun findNodes(
        text: String?,
        resourceId: String?,
        desc: String?,
        className: String?
    ): List<AccessibilityNodeInfo> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        findNodesRecursive(rootNode, text, resourceId, desc, className, result)
        
        return result
    }
    
    private fun findNodesRecursive(
        node: AccessibilityNodeInfo,
        text: String?,
        resourceId: String?,
        desc: String?,
        className: String?,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        var match = true
        
        if (text != null && !text.isNullOrEmpty()) {
            val nodeText = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (!nodeText.contains(text, ignoreCase = true) && 
                !contentDesc.contains(text, ignoreCase = true)) {
                match = false
            }
        }
        
        if (resourceId != null && !resourceId.isNullOrEmpty()) {
            val nodeId = node.viewIdResourceName ?: ""
            if (!nodeId.contains(resourceId, ignoreCase = true)) {
                match = false
            }
        }
        
        if (desc != null && !desc.isNullOrEmpty()) {
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (!nodeDesc.contains(desc, ignoreCase = true)) {
                match = false
            }
        }
        
        if (className != null && !className.isNullOrEmpty()) {
            val nodeClass = node.className?.toString() ?: ""
            if (!nodeClass.contains(className, ignoreCase = true)) {
                match = false
            }
        }
        
        if (match) {
            result.add(node)
        }
        
        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesRecursive(child, text, resourceId, desc, className, result)
            }
        }
    }
    
    /**
     * 查找可编辑焦点节点
     */
    private fun findFocusableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findFocusableNode(child)
                if (result != null) {
                    return result
                }
            }
        }
        
        return null
    }
}
