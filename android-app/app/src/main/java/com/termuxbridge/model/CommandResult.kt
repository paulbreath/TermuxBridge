package com.termuxbridge.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 执行命令模型
 */
data class ExecuteCommand(
    val action: String,
    val params: JSONObject
) {
    companion object {
        fun fromJson(json: JSONObject): ExecuteCommand {
            val action = json.optString("action", "")
            val params = json.optJSONObject("params") ?: JSONObject()
            return ExecuteCommand(action, params)
        }
    }
}

/**
 * 命令执行结果
 */
data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
) {
    companion object {
        fun success(message: String, data: Any? = null): CommandResult {
            return CommandResult(true, message, data)
        }
        
        fun error(message: String): CommandResult {
            return CommandResult(false, message, null)
        }
    }
    
    fun toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "success" to success,
            "message" to message
        )
        
        when (data) {
            is JSONObject -> result["data"] = data.toString()
            is JSONArray -> result["data"] = data.toString()
            is Map<*, *> -> result["data"] = data
            is List<*> -> result["data"] = data
            else -> if (data != null) result["data"] = data
        }
        
        return result
    }
}
