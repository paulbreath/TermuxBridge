#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Termux Bridge Python Client Library
用于Termux环境中调用Termux Bridge服务的Python库
"""

import json
import time
import urllib.request
import urllib.error
from typing import Optional, Dict, Any, List, Union


class BridgeError(Exception):
    """Bridge API 错误"""
    pass


class TermuxBridge:
    """Termux Bridge 客户端"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8080, timeout: int = 10):
        """
        初始化客户端
        
        Args:
            host: 服务地址
            port: 服务端口
            timeout: 请求超时时间(秒)
        """
        self.base_url = f"http://{host}:{port}"
        self.timeout = timeout
    
    def _request(self, path: str, data: Optional[Dict] = None) -> Dict[str, Any]:
        """
        发送HTTP请求
        
        Args:
            path: 请求路径
            data: 请求数据
            
        Returns:
            响应数据
        """
        url = f"{self.base_url}{path}"
        
        try:
            if data:
                json_data = json.dumps(data).encode('utf-8')
                req = urllib.request.Request(
                    url,
                    data=json_data,
                    headers={'Content-Type': 'application/json'},
                    method='POST'
                )
            else:
                req = urllib.request.Request(url)
            
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                return json.loads(response.read().decode('utf-8'))
                
        except urllib.error.URLError as e:
            raise BridgeError(f"无法连接到服务: {e}")
        except json.JSONDecodeError as e:
            raise BridgeError(f"响应解析失败: {e}")
    
    def _send_command(self, action: str, params: Optional[Dict] = None) -> Dict[str, Any]:
        """
        发送命令
        
        Args:
            action: 命令类型
            params: 命令参数
            
        Returns:
            响应数据
        """
        payload = {"action": action, "params": params or {}}
        return self._request("/cmd", payload)
    
    # ==================== 基础命令 ====================
    
    def ping(self) -> Dict[str, Any]:
        """检查服务是否响应"""
        return self._request("/ping")
    
    def status(self) -> Dict[str, Any]:
        """获取服务状态"""
        return self._request("/status")
    
    def is_ready(self) -> bool:
        """检查服务是否就绪"""
        try:
            result = self.status()
            return result.get("status") == "ready"
        except BridgeError:
            return False
    
    # ==================== 触控操作 ====================
    
    def tap(self, x: int, y: int) -> Dict[str, Any]:
        """
        点击坐标
        
        Args:
            x: X坐标
            y: Y坐标
            
        Returns:
            执行结果
        """
        return self._send_command("tap", {"x": x, "y": y})
    
    def tap_element(
        self,
        text: Optional[str] = None,
        resource_id: Optional[str] = None,
        desc: Optional[str] = None,
        class_name: Optional[str] = None,
        index: int = 0
    ) -> Dict[str, Any]:
        """
        点击元素
        
        Args:
            text: 元素文本
            resource_id: 资源ID
            desc: 内容描述
            class_name: 类名
            index: 匹配索引
            
        Returns:
            执行结果
        """
        params = {"index": index}
        if text:
            params["text"] = text
        if resource_id:
            params["resourceId"] = resource_id
        if desc:
            params["desc"] = desc
        if class_name:
            params["className"] = class_name
        
        return self._send_command("tap_element", params)
    
    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: int = 300
    ) -> Dict[str, Any]:
        """
        滑动
        
        Args:
            start_x: 起始X坐标
            start_y: 起始Y坐标
            end_x: 结束X坐标
            end_y: 结束Y坐标
            duration: 持续时间(毫秒)
            
        Returns:
            执行结果
        """
        return self._send_command("swipe", {
            "startX": start_x,
            "startY": start_y,
            "endX": end_x,
            "endY": end_y,
            "duration": duration
        })
    
    def swipe_up(self, duration: int = 300) -> Dict[str, Any]:
        """向上滑动（翻页）"""
        return self.swipe(540, 1500, 540, 500, duration)
    
    def swipe_down(self, duration: int = 300) -> Dict[str, Any]:
        """向下滑动（翻页）"""
        return self.swipe(540, 500, 540, 1500, duration)
    
    def swipe_left(self, duration: int = 300) -> Dict[str, Any]:
        """向左滑动"""
        return self.swipe(900, 960, 180, 960, duration)
    
    def swipe_right(self, duration: int = 300) -> Dict[str, Any]:
        """向右滑动"""
        return self.swipe(180, 960, 900, 960, duration)
    
    def long_press(self, x: int, y: int, duration: int = 1000) -> Dict[str, Any]:
        """
        长按
        
        Args:
            x: X坐标
            y: Y坐标
            duration: 持续时间(毫秒)
            
        Returns:
            执行结果
        """
        return self._send_command("long_press", {"x": x, "y": y, "duration": duration})
    
    # ==================== 输入操作 ====================
    
    def input_text(self, text: str) -> Dict[str, Any]:
        """
        输入文本
        
        Args:
            text: 要输入的文本
            
        Returns:
            执行结果
        """
        return self._send_command("input_text", {"text": text})
    
    # ==================== 全局操作 ====================
    
    def back(self) -> Dict[str, Any]:
        """返回键"""
        return self._send_command("back")
    
    def home(self) -> Dict[str, Any]:
        """Home键"""
        return self._send_command("home")
    
    def recent(self) -> Dict[str, Any]:
        """最近任务"""
        return self._send_command("recent")
    
    def notifications(self) -> Dict[str, Any]:
        """通知栏"""
        return self._send_command("notifications")
    
    def quick_settings(self) -> Dict[str, Any]:
        """快速设置"""
        return self._send_command("quick_settings")
    
    # ==================== 查询操作 ====================
    
    def find_element(
        self,
        text: Optional[str] = None,
        resource_id: Optional[str] = None,
        desc: Optional[str] = None,
        class_name: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        查找元素
        
        Args:
            text: 元素文本
            resource_id: 资源ID
            desc: 内容描述
            class_name: 类名
            
        Returns:
            执行结果，包含匹配的元素列表
        """
        params = {}
        if text:
            params["text"] = text
        if resource_id:
            params["resourceId"] = resource_id
        if desc:
            params["desc"] = desc
        if class_name:
            params["className"] = class_name
        
        return self._send_command("find_element", params)
    
    def dump_hierarchy(self) -> Dict[str, Any]:
        """获取界面层级结构"""
        return self._send_command("dump")
    
    def get_elements(self) -> List[Dict[str, Any]]:
        """
        获取当前界面所有可交互元素
        
        Returns:
            元素列表
        """
        result = self.dump_hierarchy()
        if result.get("success"):
            # 解析层级结构，提取可点击元素
            elements = []
            # 这里需要递归解析JSON
            data = result.get("data")
            if isinstance(data, str):
                data = json.loads(data)
            self._extract_clickable(data, elements)
            return elements
        return []
    
    def _extract_clickable(self, node: Dict, result: List):
        """递归提取可点击元素"""
        if not node:
            return
        
        if node.get("clickable") or node.get("scrollable"):
            result.append({
                "text": node.get("text", ""),
                "resourceId": node.get("resourceId", ""),
                "className": node.get("className", ""),
                "bounds": node.get("bounds", {}),
                "clickable": node.get("clickable", False),
                "scrollable": node.get("scrollable", False)
            })
        
        for child in node.get("children", []):
            self._extract_clickable(child, result)
    
    # ==================== 滚动操作 ====================
    
    def scroll_forward(
        self,
        text: Optional[str] = None,
        resource_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """向前滚动"""
        params = {}
        if text:
            params["text"] = text
        if resource_id:
            params["resourceId"] = resource_id
        return self._send_command("scroll_forward", params)
    
    def scroll_backward(
        self,
        text: Optional[str] = None,
        resource_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """向后滚动"""
        params = {}
        if text:
            params["text"] = text
        if resource_id:
            params["resourceId"] = resource_id
        return self._send_command("scroll_backward", params)
    
    # ==================== 等待操作 ====================
    
    def wait(self, seconds: float) -> None:
        """等待"""
        time.sleep(seconds)
    
    def wait_for_element(
        self,
        text: Optional[str] = None,
        resource_id: Optional[str] = None,
        timeout: int = 10,
        interval: float = 1.0
    ) -> bool:
        """
        等待元素出现
        
        Args:
            text: 元素文本
            resource_id: 资源ID
            timeout: 超时时间(秒)
            interval: 检查间隔(秒)
            
        Returns:
            是否找到元素
        """
        start_time = time.time()
        
        while time.time() - start_time < timeout:
            result = self.find_element(text=text, resource_id=resource_id)
            if result.get("success"):
                return True
            time.sleep(interval)
        
        return False
    
    # ==================== 高级操作 ====================
    
    def start_app(self, package_name: str) -> Dict[str, Any]:
        """
        启动应用
        
        Args:
            package_name: 应用包名
            
        Returns:
            执行结果
        """
        return self._send_command("start_app", {"packageName": package_name})
    
    def tap_text(self, text: str, exact: bool = False) -> Dict[str, Any]:
        """
        便捷方法：点击包含指定文本的元素
        
        Args:
            text: 文本内容
            exact: 是否精确匹配
            
        Returns:
            执行结果
        """
        return self.tap_element(text=text)
    
    def scroll_to_text(self, text: str, max_swipes: int = 10) -> bool:
        """
        滚动直到找到指定文本
        
        Args:
            text: 目标文本
            max_swipes: 最大滑动次数
            
        Returns:
            是否找到
        """
        for _ in range(max_swipes):
            result = self.find_element(text=text)
            if result.get("success"):
                return True
            self.swipe_up()
            self.wait(0.5)
        return False
    
    def type_and_enter(self, text: str) -> Dict[str, Any]:
        """
        输入文本并回车
        
        Args:
            text: 要输入的文本
            
        Returns:
            执行结果
        """
        self.input_text(text)
        self.wait(0.1)
        return self.tap_element(text="\n")


# 便捷函数
_default_bridge = None

def get_bridge(host: str = "127.0.0.1", port: int = 8080) -> TermuxBridge:
    """获取默认Bridge实例"""
    global _default_bridge
    if _default_bridge is None:
        _default_bridge = TermuxBridge(host, port)
    return _default_bridge


# 命令行接口
if __name__ == "__main__":
    import sys
    
    def print_help():
        print("""
Termux Bridge Python Client

用法: python bridge.py <command> [args]

命令:
    status              - 检查服务状态
    ping                - 检查服务响应
    tap <x> <y>         - 点击坐标
    swipe <sx> <sy> <ex> <ey> [duration] - 滑动
    input <text>        - 输入文本
    back                - 返回键
    home                - Home键
    find <text>         - 查找元素
    dump                - 获取界面结构

示例:
    python bridge.py status
    python bridge.py tap 540 960
    python bridge.py swipe 540 1500 540 500
    python bridge.py input "Hello"
""")
    
    if len(sys.argv) < 2:
        print_help()
        sys.exit(0)
    
    bridge = get_bridge()
    cmd = sys.argv[1]
    
    try:
        if cmd == "status":
            print(json.dumps(bridge.status(), indent=2))
        elif cmd == "ping":
            print(json.dumps(bridge.ping(), indent=2))
        elif cmd == "tap" and len(sys.argv) >= 4:
            x, y = int(sys.argv[2]), int(sys.argv[3])
            print(json.dumps(bridge.tap(x, y), indent=2))
        elif cmd == "swipe" and len(sys.argv) >= 6:
            sx, sy = int(sys.argv[2]), int(sys.argv[3])
            ex, ey = int(sys.argv[4]), int(sys.argv[5])
            duration = int(sys.argv[6]) if len(sys.argv) > 6 else 300
            print(json.dumps(bridge.swipe(sx, sy, ex, ey, duration), indent=2))
        elif cmd == "input" and len(sys.argv) >= 3:
            text = " ".join(sys.argv[2:])
            print(json.dumps(bridge.input_text(text), indent=2))
        elif cmd == "back":
            print(json.dumps(bridge.back(), indent=2))
        elif cmd == "home":
            print(json.dumps(bridge.home(), indent=2))
        elif cmd == "find" and len(sys.argv) >= 3:
            text = " ".join(sys.argv[2:])
            print(json.dumps(bridge.find_element(text=text), indent=2))
        elif cmd == "dump":
            print(json.dumps(bridge.dump_hierarchy(), indent=2))
        else:
            print_help()
    except BridgeError as e:
        print(f"错误: {e}")
        sys.exit(1)
