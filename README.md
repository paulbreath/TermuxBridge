# Termux Bridge

Termux Bridge 是一个无Root环境下实现Termux控制Android应用的桥接工具。

## 架构概览

```
┌─────────────────┐     HTTP API      ┌──────────────────────┐     Accessibility     ┌──────────────┐
│    Termux       │ ───────────────> │    Android Bridge    │ ──────────────────> │  目标应用    │
│  (Shell/Python) │                   │       应用           │                      │ (TikTok等)   │
│                 │ <─────────────── │                      │ <────────────────── │              │
└─────────────────┘     JSON响应     └──────────────────────┘      执行操作结果     └──────────────┘
```

## 功能特性

- ✅ **无Root运行** - 只需无障碍服务授权
- ✅ **HTTP API** - 标准REST接口，易于集成
- ✅ **触控模拟** - 点击、滑动、长按、手势
- ✅ **元素定位** - 通过文本/ID/描述精确定位
- ✅ **界面感知** - 获取完整界面元素树
- ✅ **全局操作** - 返回、Home、最近任务等
- ✅ **文本输入** - 向任意输入框输入文本
- ✅ **多客户端** - Shell和Python客户端库

## 快速开始

### 1. 安装Android应用

#### 方法A: 使用Android Studio构建

```bash
# 1. 使用Android Studio打开 android-app 目录
# 2. 等待Gradle同步完成
# 3. 点击 Run 按钮安装到设备/模拟器
```

#### 方法B: 命令行构建

```bash
cd android-app
./gradlew assembleDebug
# APK位置: app/build/outputs/apk/debug/app-debug.apk
```

### 2. 启用无障碍服务

1. 打开 Termux Bridge 应用
2. 点击"启用服务"按钮
3. 在系统设置中找到"Termux Bridge"
4. 开启无障碍服务开关
5. 返回应用

### 3. 启动HTTP服务器

1. 在Termux Bridge应用中点击"启动"按钮
2. 确认状态显示"服务运行中"

### 4. 在Termux中使用

```bash
# 复制客户端文件到Termux
cp termux-client/bridge.sh ~/bridge.sh
cp termux-client/bridge.py ~/bridge.py

# 使用Shell客户端
source ~/bridge.sh
bridge_ping                    # 检查连接
bridge_tap 540 960            # 点击屏幕
bridge_swipe_up               # 向上滑动
bridge_tap_element "登录"     # 点击按钮

# 使用Python客户端
python ~/bridge.py status     # 检查状态
python ~/bridge.py tap 540 960
python ~/bridge.py dump       # 获取界面结构
```

## API 文档

### 基础端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/ping` | GET | 检查服务响应 |
| `/status` | GET | 获取服务状态 |
| `/cmd` | POST | 执行命令 |

### 命令格式

所有命令通过 `POST /cmd` 发送，JSON格式：

```json
{
    "action": "命令类型",
    "params": {
        "参数名": "参数值"
    }
}
```

### 支持的命令

#### 触控操作

| 命令 | 参数 | 示例 |
|------|------|------|
| `tap` | x, y | `{"action":"tap","params":{"x":540,"y":960}}` |
| `swipe` | startX, startY, endX, endY, duration | `{"action":"swipe","params":{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":300}}` |
| `long_press` | x, y, duration | `{"action":"long_press","params":{"x":540,"y":960,"duration":1000}}` |

#### 元素操作

| 命令 | 参数 | 示例 |
|------|------|------|
| `tap_element` | text/resourceId/desc, index | `{"action":"tap_element","params":{"text":"登录"}}` |
| `find_element` | text/resourceId/desc | `{"action":"find_element","params":{"text":"设置"}}` |
| `scroll_forward` | text/resourceId | `{"action":"scroll_forward"}` |
| `scroll_backward` | text/resourceId | `{"action":"scroll_backward"}` |

#### 输入操作

| 命令 | 参数 | 示例 |
|------|------|------|
| `input_text` | text | `{"action":"input_text","params":{"text":"Hello"}}` |

#### 全局操作

| 命令 | 参数 | 描述 |
|------|------|------|
| `back` | - | 返回键 |
| `home` | - | Home键 |
| `recent` | - | 最近任务 |
| `notifications` | - | 通知栏 |
| `quick_settings` | - | 快速设置 |

#### 查询操作

| 命令 | 参数 | 描述 |
|------|------|------|
| `dump` | - | 获取界面元素树 |

## 使用示例

### Shell脚本示例

```bash
#!/bin/bash
source ~/bridge.sh

# 自动化TikTok点赞
bridge_tap_element "关注"
bridge_wait 1
bridge_swipe_up
bridge_wait 1
bridge_tap_element "喜欢"

# 批量操作
for i in {1..10}; do
    bridge_swipe_up
    bridge_wait 2
    bridge_tap_element "喜欢"
    bridge_wait 1
done
```

### Python示例

```python
from bridge import TermuxBridge

bridge = TermuxBridge()

# 检查服务状态
if not bridge.is_ready():
    print("服务未就绪")
    exit(1)

# 自动化示例
def automate_task():
    # 点击搜索框
    bridge.tap_element(text="搜索")
    bridge.wait(0.5)
    
    # 输入搜索内容
    bridge.input_text("Python教程")
    bridge.wait(0.5)
    
    # 点击搜索按钮
    bridge.tap_element(text="搜索")
    
    # 滑动浏览结果
    for _ in range(5):
        bridge.swipe_up()
        bridge.wait(2)

automate_task()
```

## 模拟器测试

### 配置Android模拟器

1. 创建AVD (Android Virtual Device)
   ```bash
   # 使用Android Studio的AVD Manager创建
   # 或使用命令行
   avdmanager create avd -n test -k "system-images;android-34;google_apis;x86_64"
   ```

2. 启动模拟器
   ```bash
   emulator -avd test
   ```

3. 安装应用
   ```bash
   adb install app-debug.apk
   ```

4. 启用无障碍服务
   - 设置 > 无障碍 > Termux Bridge > 开启

### 运行测试

```bash
# 在模拟器的Termux中运行
bash test_bridge.sh
```

## 常见问题

### Q: 无障碍服务无法启用？

A: 部分ROM需要额外步骤：
1. 设置 > 应用管理 > Termux Bridge
2. 权限管理 > 允许所有权限
3. 返回无障碍设置，再次尝试启用

### Q: HTTP服务器启动失败？

A: 检查端口是否被占用：
```bash
# 在Termux中检查端口
netstat -tlnp | grep 8080
```

### Q: 点击位置不准确？

A: 使用 `bridge_dump` 获取元素准确坐标：
```bash
bridge_dump | jq '.data.bounds'
```

### Q: 某些应用无法操作？

A: 部分应用有防自动化检测，可能需要：
- 添加随机延迟
- 模拟人类行为模式
- 避免过快操作

## 安全注意事项

⚠️ **重要提示**

无障碍服务具有强大的系统权限，请：

1. **仅启用可信应用** - 不要启用来源不明的无障碍服务
2. **审查代码** - 如有疑虑，审查源代码后再使用
3. **定期检查** - 定期检查已启用的无障碍服务列表
4. **敏感操作** - 涉及支付/转账时保持警惕

## 项目结构

```
TermuxBridge/
├── android-app/           # Android桥接应用
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/termuxbridge/
│   │       │   ├── MainActivity.kt
│   │       │   ├── service/
│   │       │   │   ├── BridgeAccessibilityService.kt
│   │       │   │   └── HttpServerService.kt
│   │       │   ├── model/
│   │       │   │   └── CommandResult.kt
│   │       │   └── util/
│   │       │       └── NodeInfoExtractor.kt
│   │       └── res/
│   │           ├── xml/
│   │           └── values/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── termux-client/         # Termux客户端工具
│   ├── bridge.sh          # Shell客户端库
│   ├── bridge.py          # Python客户端库
│   └── test_bridge.sh     # 测试脚本
│
├── docs/                  # 文档
│   └── API.md
│
└── README.md
```

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request！
