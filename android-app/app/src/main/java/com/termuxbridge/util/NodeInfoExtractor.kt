package com.termuxbridge.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 节点信息提取工具
 */
object NodeInfoExtractor {
    
    /**
     * 提取单个节点的信息
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
        }
    }
    
    /**
     * 提取整个界面层级结构
     */
    fun extractHierarchy(node: AccessibilityNodeInfo, depth: Int = 0): JSONObject {
        val nodeInfo = extract(node)
        val children = JSONArray()
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.put(extractHierarchy(child, depth + 1))
            }
        }
        
        nodeInfo.put("children", children)
        nodeInfo.put("depth", depth)
        
        return nodeInfo
    }
    
    /**
     * 提取扁平化的节点列表
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
     * 查找可点击的元素
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
     * 查找可编辑的元素
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
     * 获取节点的简要描述
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
}
