package com.termuxbridge.util

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Node Info Extractor - Enhanced Version
 * 
 * Added visibility detection inspired by browser-use's approach
 * to provide better element visibility information.
 */
object NodeInfoExtractor {
    
    // Screen dimensions for visibility calculation
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    
    /**
     * Initialize screen dimensions (should be called once)
     */
    fun initScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }
    
    /**
     * Extract single node information
     */
    fun extract(node: AccessibilityNodeInfo): JSONObject {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        return JSONObject().apply {
            put("className", node.className?.toString() ?: "")
            put("text", node.text?.toString() ?: "")
            put("contentDescription", node.contentDescription?.toString() ?: "")
            put("resourceId", node.viewIdResourceName ?: "")
            put("packageName", node.packageName?.toString() ?: "")
            put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
                put("centerX", bounds.centerX())
                put("centerY", bounds.centerY())
                put("width", bounds.width())
                put("height", bounds.height())
            })
            put("clickable", node.isClickable)
            put("scrollable", node.isScrollable)
            put("editable", node.isEditable)
            put("enabled", node.isEnabled)
            put("focusable", node.isFocusable)
            put("focused", node.isFocused)
            put("checked", node.isChecked)
            put("checkable", node.isCheckable)
            put("selected", node.isSelected)
            put("childCount", node.childCount)
            
            // Add visibility information (inspired by browser-use)
            put("isVisibleToUser", isNodeVisible(node))
            put("isOnScreen", isNodeOnScreen(node, bounds))
        }
    }
    
    /**
     * Extract entire UI hierarchy with visibility detection
     * Inspired by browser-use's enhanced DOM extraction
     */
    fun extractHierarchyWithVisibility(node: AccessibilityNodeInfo, depth: Int = 0): JSONObject {
        val nodeInfo = extract(node)
        val children = JSONArray()
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.put(extractHierarchyWithVisibility(child, depth + 1))
            }
        }
        
        nodeInfo.put("children", children)
        nodeInfo.put("depth", depth)
        
        return nodeInfo
    }
    
    /**
     * Extract UI hierarchy (original method, kept for compatibility)
     */
    fun extractHierarchy(node: AccessibilityNodeInfo, depth: Int = 0): JSONObject {
        return extractHierarchyWithVisibility(node, depth)
    }
    
    /**
     * Check if node is visible to user
     * Inspired by browser-use's visibility detection approach
     */
    private fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        // Check basic visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!node.isVisibleToUser) return false
        }
        
        // Check bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        return bounds.width() > 0 && bounds.height() > 0
    }
    
    /**
     * Check if node is currently on screen
     */
    private fun isNodeOnScreen(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        // Get screen dimensions if not initialized
        if (screenWidth == 0 || screenHeight == 0) {
            try {
                val displayMetrics = Resources.getSystem().displayMetrics
                screenWidth = displayMetrics.widthPixels
                screenHeight = displayMetrics.heightPixels
            } catch (e: Exception) {
                // Default to common resolution
                screenWidth = 1080
                screenHeight = 2400
            }
        }
        
        // Check if bounds intersect with screen
        return bounds.left < screenWidth && bounds.right > 0 &&
               bounds.top < screenHeight && bounds.bottom > 0
    }
    
    /**
     * Extract flat list of nodes
     */
    fun extractFlatList(node: AccessibilityNodeInfo, result: JSONArray = JSONArray()): JSONArray {
        val nodeInfo = extract(node)
        result.put(nodeInfo)
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractFlatList(child, result)
            }
        }
        
        return result
    }
    
    /**
     * Find clickable elements
     */
    fun findClickableNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (node.isClickable && node.isEnabled) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findClickableNodes(child, result)
            }
        }
        
        return result
    }
    
    /**
     * Find editable elements
     */
    fun findEditableNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (node.isEditable && node.isEnabled) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findEditableNodes(child, result)
            }
        }
        
        return result
    }
    
    /**
     * Find visible interactive elements (inspired by browser-use)
     */
    fun findVisibleInteractiveNodes(
        node: AccessibilityNodeInfo, 
        result: MutableList<AccessibilityNodeInfo> = mutableListOf()
    ): List<AccessibilityNodeInfo> {
        val isInteractive = node.isClickable || node.isScrollable || 
                           node.isEditable || node.isCheckable
        
        if (isInteractive && node.isEnabled && isNodeVisible(node)) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findVisibleInteractiveNodes(child, result)
            }
        }
        
        return result
    }
    
    /**
     * Get brief description of node
     */
    fun getBriefDescription(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString()?.take(20) ?: ""
        val resourceId = node.viewIdResourceName?.substringAfterLast(':') ?: ""
        
        return buildString {
            append(className)
            if (text.isNotEmpty()) append(" [\"$text\"]")
            if (resourceId.isNotEmpty()) append(" #$resourceId")
        }
    }
    
    /**
     * Count total nodes in hierarchy
     */
    fun countNodes(node: AccessibilityNodeInfo): Int {
        var count = 1
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                count += countNodes(child)
            }
        }
        return count
    }
    
    /**
     * Extract interactive elements as simplified list
     * Returns only essential info for quick access
     */
    fun extractInteractiveElements(node: AccessibilityNodeInfo): JSONArray {
        val result = JSONArray()
        val interactiveNodes = findVisibleInteractiveNodes(node)
        
        for (nodeInfo in interactiveNodes) {
            val bounds = Rect()
            nodeInfo.getBoundsInScreen(bounds)
            
            result.put(JSONObject().apply {
                put("className", nodeInfo.className?.toString()?.substringAfterLast('.') ?: "")
                put("text", nodeInfo.text?.toString() ?: "")
                put("resourceId", nodeInfo.viewIdResourceName ?: "")
                put("centerX", bounds.centerX())
                put("centerY", bounds.centerY())
                put("clickable", nodeInfo.isClickable)
                put("editable", nodeInfo.isEditable)
            })
        }
        
        return result
    }
}
