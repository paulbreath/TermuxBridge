#!/bin/bash
#
# Termux Bridge 模拟器测试脚本
# 用于在Android模拟器上自动化测试
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/android-app/app/build/outputs/apk/debug/app-debug.apk"

echo "========================================="
echo "    Termux Bridge 模拟器测试"
echo "========================================="
echo ""

# 检查ADB
check_adb() {
    if ! command -v adb &> /dev/null; then
        echo "❌ ADB未找到"
        echo "请确保Android SDK已安装并添加到PATH"
        exit 1
    fi
    echo "✓ ADB已就绪"
}

# 检查模拟器连接
check_emulator() {
    echo ""
    echo "检查设备连接..."
    
    DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
    
    if [ "$DEVICES" -eq 0 ]; then
        echo "❌ 未检测到设备"
        echo ""
        echo "请先启动模拟器："
        echo "  1. 使用Android Studio AVD Manager"
        echo "  2. 或运行: emulator -avd <avd_name>"
        echo ""
        echo "可用的AVD列表："
        emulator -list-avds 2>/dev/null || echo "  (无)"
        exit 1
    fi
    
    echo "✓ 已连接设备:"
    adb devices -l | grep -v "List"
}

# 安装APK
install_apk() {
    echo ""
    echo "安装 Termux Bridge..."
    
    if [ ! -f "$APK_PATH" ]; then
        echo "❌ APK未找到: $APK_PATH"
        echo "请先运行 ./build.sh 构建APK"
        exit 1
    fi
    
    # 卸载旧版本
    adb uninstall com.termuxbridge 2>/dev/null || true
    
    # 安装新版本
    adb install "$APK_PATH"
    
    echo "✓ APK安装成功"
}

# 启动应用
launch_app() {
    echo ""
    echo "启动 Termux Bridge..."
    
    adb shell am start -n com.termuxbridge/.MainActivity
    
    echo "✓ 应用已启动"
}

# 启用无障碍服务（需要用户手动操作）
enable_accessibility() {
    echo ""
    echo "========================================="
    echo "请在模拟器上完成以下步骤："
    echo "========================================="
    echo ""
    echo "1. 在应用中点击'启用服务'"
    echo "2. 系统会自动跳转到无障碍设置"
    echo "3. 选择 'Termux Bridge'"
    echo "4. 开启开关"
    echo "5. 点击确定"
    echo "6. 返回 Termux Bridge 应用"
    echo ""
    read -p "完成后按 Enter 继续..."
}

# 启动HTTP服务器
start_server() {
    echo ""
    echo "启动HTTP服务器..."
    
    # 通过Intent启动服务
    adb shell am startservice -n com.termuxbridge/.service.HttpServerService \
        -a com.termuxbridge.action.START \
        --ei port 8080
    
    sleep 2
    echo "✓ 服务已启动"
}

# 测试连接
test_connection() {
    echo ""
    echo "测试服务连接..."
    
    # 使用adb转发端口
    adb forward tcp:8080 tcp:8080
    
    # 测试连接
    RESPONSE=$(curl -s http://127.0.0.1:8080/ping 2>/dev/null || echo "failed")
    
    if echo "$RESPONSE" | grep -q '"status":"ok"'; then
        echo "✓ 服务连接成功"
        echo "  响应: $RESPONSE"
    else
        echo "⚠ 连接测试失败，请检查服务状态"
        echo "  响应: $RESPONSE"
    fi
}

# 运行功能测试
run_tests() {
    echo ""
    echo "运行功能测试..."
    echo ""
    
    # 测试点击
    echo "测试1: 点击屏幕中心..."
    curl -s -X POST http://127.0.0.1:8080/cmd \
        -H "Content-Type: application/json" \
        -d '{"action":"tap","params":{"x":540,"y":960}}'
    echo ""
    sleep 1
    
    # 测试返回键
    echo "测试2: 返回键..."
    curl -s -X POST http://127.0.0.1:8080/cmd \
        -H "Content-Type: application/json" \
        -d '{"action":"back"}'
    echo ""
    sleep 1
    
    # 测试Home键
    echo "测试3: Home键..."
    curl -s -X POST http://127.0.0.1:8080/cmd \
        -H "Content-Type: application/json" \
        -d '{"action":"home"}'
    echo ""
    sleep 1
    
    # 测试滑动
    echo "测试4: 滑动..."
    curl -s -X POST http://127.0.0.1:8080/cmd \
        -H "Content-Type: application/json" \
        -d '{"action":"swipe","params":{"startX":540,"startY":1500,"endX":540,"endY":500,"duration":300}}'
    echo ""
    sleep 1
    
    # 测试获取界面结构
    echo "测试5: 获取界面结构..."
    RESULT=$(curl -s -X POST http://127.0.0.1:8080/cmd \
        -H "Content-Type: application/json" \
        -d '{"action":"dump"}')
    
    if echo "$RESULT" | grep -q '"success":true'; then
        echo "✓ 界面结构获取成功"
    else
        echo "⚠ 界面结构获取失败"
    fi
}

# 完成提示
show_summary() {
    echo ""
    echo "========================================="
    echo "    测试完成！"
    echo "========================================="
    echo ""
    echo "Termux Bridge 已在模拟器上运行"
    echo ""
    echo "API地址: http://127.0.0.1:8080"
    echo ""
    echo "测试命令:"
    echo "  curl http://127.0.0.1:8080/ping"
    echo "  curl http://127.0.0.1:8080/status"
    echo ""
    echo "停止服务:"
    echo "  adb shell am stopservice -n com.termuxbridge/.service.HttpServerService"
    echo ""
    echo "取消端口转发:"
    echo "  adb forward --remove tcp:8080"
    echo ""
}

# 主流程
main() {
    check_adb
    check_emulator
    install_apk
    launch_app
    enable_accessibility
    start_server
    test_connection
    run_tests
    show_summary
}

main "$@"
