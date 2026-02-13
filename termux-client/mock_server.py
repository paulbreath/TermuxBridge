#!/usr/bin/env python3
"""
Termux Bridge 模拟服务器
用于在没有实际Android设备的情况下测试客户端代码
"""

import json
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
import sys

class MockBridgeHandler(BaseHTTPRequestHandler):
    """模拟Termux Bridge API响应"""
    
    def log_message(self, format, *args):
        """自定义日志格式"""
        print(f"[Mock] {args[0]}")
    
    def send_json(self, status, data):
        """发送JSON响应"""
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
    
    def do_GET(self):
        """处理GET请求"""
        if self.path == '/ping':
            self.send_json(200, {
                "status": "ok",
                "service": "TermuxBridge Mock",
                "version": "1.0.0-mock"
            })
        elif self.path == '/status':
            self.send_json(200, {
                "status": "ready",
                "http_server": "running",
                "accessibility_service": True,
                "port": 8080
            })
        else:
            self.send_json(404, {"error": "Not Found"})
    
    def do_POST(self):
        """处理POST请求"""
        if self.path == '/cmd':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                command = json.loads(body)
                action = command.get('action', '')
                params = command.get('params', {})
                
                # 模拟命令执行
                result = self.mock_execute(action, params)
                self.send_json(200, result)
                
            except json.JSONDecodeError:
                self.send_json(400, {"success": False, "error": "Invalid JSON"})
        else:
            self.send_json(404, {"error": "Not Found"})
    
    def mock_execute(self, action, params):
        """模拟命令执行"""
        print(f"  → 执行命令: {action}, 参数: {params}")
        
        responses = {
            'tap': {"success": True, "message": f"已点击坐标 ({params.get('x')}, {params.get('y')})"},
            'swipe': {"success": True, "message": f"已滑动 ({params.get('startX')}, {params.get('startY')}) → ({params.get('endX')}, {params.get('endY')})"},
            'long_press': {"success": True, "message": f"已长按 ({params.get('x')}, {params.get('y')})"},
            'tap_element': {"success": True, "message": f"已点击元素: {params.get('text') or params.get('resourceId')}"},
            'input_text': {"success": True, "message": f"已输入文本: {params.get('text')}"},
            'back': {"success": True, "message": "已执行返回操作"},
            'home': {"success": True, "message": "已执行Home操作"},
            'recent': {"success": True, "message": "已打开最近任务"},
            'notifications': {"success": True, "message": "已打开通知栏"},
            'quick_settings': {"success": True, "message": "已打开快速设置"},
            'find_element': {
                "success": True, 
                "message": f"找到元素: {params.get('text') or params.get('resourceId')}",
                "data": [
                    {"text": params.get('text', ''), "bounds": {"centerX": 540, "centerY": 960}}
                ]
            },
            'dump': {
                "success": True,
                "message": "已获取界面结构",
                "data": {
                    "className": "android.widget.FrameLayout",
                    "children": [
                        {"className": "android.widget.LinearLayout", "text": "测试元素", "clickable": True}
                    ]
                }
            }
        }
        
        return responses.get(action, {"success": False, "error": f"未知命令: {action}"})


def run_server(port=8080):
    """启动模拟服务器"""
    server = HTTPServer(('127.0.0.1', port), MockBridgeHandler)
    print(f"╔══════════════════════════════════════════╗")
    print(f"║   Termux Bridge 模拟服务器               ║")
    print(f"║   端口: {port}                            ║")
    print(f"║   按 Ctrl+C 停止                         ║")
    print(f"╚══════════════════════════════════════════╝")
    print()
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务器已停止")
        server.shutdown()


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    run_server(port)
