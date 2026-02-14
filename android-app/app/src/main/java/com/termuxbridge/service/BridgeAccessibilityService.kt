package com.termuxbridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.termuxbridge.model.CommandResult
import com.termuxbridge.model.ExecuteCommand
import com.termuxbridge.util.NodeInfoExtractor
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Bridge Accessibility Service - Enhanced Version
 * 
 * Fixes "No active window" issue with multiple fallback strategies,
 * inspired by browser-use's multi-data-source approach.
 */
class BridgeAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TermuxBridge"
        private const val CACHE_VALIDITY_MS = 5000L // 5 seconds cache validity
        
        @JvmStatic
        var instance: BridgeAccessibilityService? = null
            private set
        
        @JvmStatic
        fun isServiceEnabled(): Boolean = instance != null
        
        // Cache for last known root node (inspired by browser-use's caching strategy)
        private var lastKnownRootNode: AccessibilityNodeInfo? = null
        private var lastUpdateTime: Long = 0
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // Log service capabilities for debugging
        logServiceCapabilities()
    }
    
    /**
     * Log service capabilities for debugging
     */
    private fun logServiceCapabilities() {
        try {
            val rootNode = rootInActiveWindow
            val windowCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                windows.size
            } else 0
            
            Log.d(TAG, """
                |Service Connected:
                |  - rootInActiveWindow: ${if (rootNode != null) "available" else "null"}
                |  - Window count: $windowCount
                |  - SDK: ${Build.VERSION.SDK_INT}
            """.trimMargin())
        } catch (e: Exception) {
            Log.e(TAG, "Service capability check failed: ${e.message}")
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Update cache on window changes (inspired by browser-use's event-driven updates)
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    updateCache()
                }
            }
        }
    }
    
    /**
     * Update cached root node
     */
    private fun updateCache() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                lastKnownRootNode = rootNode
                lastUpdateTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update cache: ${e.message}")
        }
    }
    
    override fun onInterrupt() {
        // Handle interruption
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lastKnownRootNode = null
        serviceScope.cancel()
    }
    
    /**
     * Get root node with multiple fallback strategies
     * Inspired by browser-use's multi-data-source approach
     */
    private fun getRootNode(): AccessibilityNodeInfo? {
        // Strategy 1: Direct rootInActiveWindow
        rootInActiveWindow?.let { 
            Log.d(TAG, "Got root from rootInActiveWindow")
            return it 
        }
        
        // Strategy 2: Use cached node
        if (lastKnownRootNode != null && 
            System.currentTimeMillis() - lastUpdateTime < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Got root from cache")
            return lastKnownRootNode
        }
        
        // Strategy 3: Get from all windows (Android 5.1+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            tryGetFromAllWindows()?.let { 
                Log.d(TAG, "Got root from windows list")
                return it 
            }
        }
        
        // Strategy 4: Retry with delay (sometimes window switching takes time)
        for (i in 1..3) {
            Thread.sleep(100)
            rootInActiveWindow?.let { 
                Log.d(TAG, "Got root after retry $i")
                return it 
            }
        }
        
        Log.w(TAG, "All strategies failed to get root node")
        return null
    }
    
    /**
     * Try to get root node from all windows (Android 5.1+)
     * Inspired by browser-use's approach to handle multiple contexts
     */
    private fun tryGetFromAllWindows(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        
        return try {
            val windowList = windows
            if (windowList.isEmpty()) {
                return null
            }
            
            // Priority: Find active window with content
            for (window in windowList) {
                if (window.isActive) {
                    val root = window.root
                    if (root != null && root.childCount > 0) {
                        return root
                    }
                }
            }
            
            // Fallback: Return first window with content
            for (window in windowList) {
                val root = window.root
                if (root != null && root.childCount > 0) {
                    return root
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get from all windows: ${e.message}")
            null
        }
    }
    
    /**
     * Diagnose why no window is available
     */
    private fun diagnoseNoWindow(): String {
        val sb = StringBuilder()
        
        // Check service status
        sb.append("Service enabled: ${instance != null}; ")
        
        // Check cache
        sb.append("Cache: ${if (lastKnownRootNode != null) "valid" else "empty"}; ")
        
        // Check window list
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val windowCount = windows.size
                sb.append("Windows: $windowCount; ")
            } catch (e: Exception) {
                sb.append("Windows: error; ")
            }
        }
        
        // Check if device is locked
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            sb.append("Locked: ${keyguardManager.isDeviceLocked}")
        }
        
        return sb.toString()
    }
    
    /**
     * Execute command
     */
    fun executeCommand(command: ExecuteCommand): CommandResult {
        return when (command.action) {
            "tap" -> executeTap(command)
            "tap_element" -> executeTapElement(command)
            "swipe" -> executeSwipe(command)
            "long_press" -> executeLongPress(command)
            "input_text" -> executeInputText(command)
            "input_paste" -> executeInputPaste(command)
            "input_keyboard" -> executeInputKeyboard(command)
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
     * Tap at coordinates
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
     * Tap element
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
     * Swipe gesture
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
     * Long press
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
     * Input text - Enhanced version with focus handling
     * Inspired by browser-use's approach: focus first, then input
     */
    private fun executeInputText(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", "")
        val useFocus = command.params.optBoolean("focus", true)
        
        val rootNode = getRootNode() 
            ?: return CommandResult.error("No active window - ${diagnoseNoWindow()}")
        
        // Find focusable editable node
        val focusNode = findFocusableNode(rootNode)
        
        if (focusNode == null) {
            return CommandResult.error("No focusable node found")
        }
        
        // Step 1: Focus the element first (inspired by browser-use)
        if (useFocus) {
            val focusResult = focusAndClickNode(focusNode)
            if (!focusResult.success) {
                Log.w(TAG, "Focus attempt failed, trying direct input anyway")
            }
            // Small delay to let focus take effect
            Thread.sleep(100)
        }
        
        // Step 2: Set text directly
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
     * Input text using clipboard paste
     * This method works with the system input method
     */
    private fun executeInputPaste(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", "")
        val clearFirst = command.params.optBoolean("clear", true)
        
        val rootNode = getRootNode() 
            ?: return CommandResult.error("No active window - ${diagnoseNoWindow()}")
        
        // Find focusable editable node
        val focusNode = findFocusableNode(rootNode)
        
        if (focusNode == null) {
            return CommandResult.error("No focusable node found")
        }
        
        // Step 1: Focus and click the element
        val focusResult = focusAndClickNode(focusNode)
        if (!focusResult.success) {
            Log.w(TAG, "Focus attempt had issues: ${focusResult.message}")
        }
        
        // Wait for focus and keyboard to appear
        Thread.sleep(200)
        
        // Step 2: Clear existing text if requested
        if (clearFirst) {
            // Select all and delete
            focusNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            Thread.sleep(50)
            
            // Try to select all
            val selectAllArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Int.MAX_VALUE)
            }
            focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
            Thread.sleep(50)
            
            // Delete selection
            focusNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        
        // Step 3: Copy text to clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("TermuxBridge", text)
        clipboard.setPrimaryClip(clip)
        
        // Wait for clipboard to be ready
        Thread.sleep(100)
        
        // Step 4: Simulate paste (Ctrl+V or via accessibility)
        val pasteResult = focusNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        
        return if (pasteResult) {
            CommandResult.success("Text pasted via clipboard: $text")
        } else {
            // Fallback: Try direct set text
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val fallbackResult = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            
            if (fallbackResult) {
                CommandResult.success("Text input (fallback): $text")
            } else {
                CommandResult.error("Failed to input text via paste or fallback")
            }
        }
    }
    
    /**
     * Input text by simulating keyboard typing
     * Character by character input that works with input methods
     */
    private fun executeInputKeyboard(command: ExecuteCommand): CommandResult {
        val text = command.params.optString("text", "")
        val delay = command.params.optLong("delay", 50) // ms between keystrokes
        
        val rootNode = getRootNode() 
            ?: return CommandResult.error("No active window - ${diagnoseNoWindow()}")
        
        // Find focusable editable node
        val focusNode = findFocusableNode(rootNode)
        
        if (focusNode == null) {
            return CommandResult.error("No focusable node found")
        }
        
        // Step 1: Focus and click the element
        focusAndClickNode(focusNode)
        Thread.sleep(200)
        
        // Step 2: Clear existing text
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        Thread.sleep(100)
        
        // Step 3: Type each character
        // Note: This uses ACTION_SET_TEXT incrementally as true keyboard simulation
        // requires additional Android APIs not available in accessibility service
        
        var currentText = ""
        for (char in text) {
            currentText += char
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText)
            }
            focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Thread.sleep(delay)
        }
        
        return CommandResult.success("Text typed character by character: $text")
    }
    
    /**
     * Focus and click a node to prepare for input
     * Inspired by browser-use's focus handling
     */
    private fun focusAndClickNode(node: AccessibilityNodeInfo): CommandResult {
        // Method 1: Try ACTION_FOCUS
        var focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
        if (focused) {
            Log.d(TAG, "Node focused via ACTION_FOCUS")
            return CommandResult.success("Node focused")
        }
        
        // Method 2: Click on the node to focus it
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
        
        val clickResult = dispatchGestureSync(gesture)
        
        return if (clickResult.success) {
            Log.d(TAG, "Node focused via click at ($centerX, $centerY)")
            CommandResult.success("Node focused via click")
        } else {
            CommandResult.error("Failed to focus node")
        }
    }
    
    /**
     * Key event
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
     * Back action
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
     * Home action
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
     * Recent apps
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
     * Notifications panel
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
     * Quick settings panel
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
     * Scroll forward
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
     * Scroll backward
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
     * Find element
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
     * Get UI hierarchy - Enhanced version with multiple fallback strategies
     * Inspired by browser-use's multi-data-source approach
     */
    private fun executeDumpHierarchy(): CommandResult {
        val rootNode = getRootNode()
        
        if (rootNode == null) {
            val diagnosis = diagnoseNoWindow()
            Log.e(TAG, "executeDumpHierarchy failed: $diagnosis")
            return CommandResult.error("No active window - Diagnosis: $diagnosis")
        }
        
        try {
            // Enhanced extraction with visibility detection
            val hierarchy = NodeInfoExtractor.extractHierarchyWithVisibility(rootNode)
            return CommandResult.success("Hierarchy dumped", hierarchy)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract hierarchy: ${e.message}")
            return CommandResult.error("Failed to extract hierarchy: ${e.message}")
        }
    }
    
    /**
     * Dispatch gesture synchronously
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
     * Find nodes - Updated to use getRootNode()
     */
    private fun findNodes(
        text: String?,
        resourceId: String?,
        desc: String?,
        className: String?
    ): List<AccessibilityNodeInfo> {
        val rootNode = getRootNode() ?: return emptyList()
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
        
        if (text != null && text.isNotEmpty()) {
            val nodeText = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (!nodeText.contains(text, ignoreCase = true) && 
                !contentDesc.contains(text, ignoreCase = true)) {
                match = false
            }
        }
        
        if (resourceId != null && resourceId.isNotEmpty()) {
            val nodeId = node.viewIdResourceName ?: ""
            if (!nodeId.contains(resourceId, ignoreCase = true)) {
                match = false
            }
        }
        
        if (desc != null && desc.isNotEmpty()) {
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (!nodeDesc.contains(desc, ignoreCase = true)) {
                match = false
            }
        }
        
        if (className != null && className.isNotEmpty()) {
            val nodeClass = node.className?.toString() ?: ""
            if (!nodeClass.contains(className, ignoreCase = true)) {
                match = false
            }
        }
        
        if (match) {
            result.add(node)
        }
        
        // Recursively traverse child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesRecursive(child, text, resourceId, desc, className, result)
            }
        }
    }
    
    /**
     * Find editable focusable node
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
