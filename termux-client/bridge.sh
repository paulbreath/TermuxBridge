#!/bin/bash
#
# Termux Bridge Client Library
# 用于Termux环境中调用Termux Bridge服务
#

# 默认配置
BRIDGE_HOST="${BRIDGE_HOST:-127.0.0.1}"
BRIDGE_PORT="${BRIDGE_PORT:-8080}"
BRIDGE_URL="http://${BRIDGE_HOST}:${BRIDGE_PORT}"
BRIDGE_TIMEOUT="${BRIDGE_TIMEOUT:-10}"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 发送命令到桥接服务
bridge_send() {
    local action="$1"
    local params="$2"
    
    local json_payload
    if [ -n "$params" ]; then
        json_payload="{\"action\":\"$action\",\"params\":$params}"
    else
        json_payload="{\"action\":\"$action\"}"
    fi
    
    curl -s -X POST "${BRIDGE_URL}/cmd" \
        -H "Content-Type: application/json" \
        -d "$json_payload" \
        --connect-timeout "$BRIDGE_TIMEOUT" \
        --max-time "$((BRIDGE_TIMEOUT * 2))"
}

# 检查服务状态
bridge_status() {
    local response
    response=$(curl -s "${BRIDGE_URL}/status" --connect-timeout 3 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        echo "$response"
        return 0
    else
        log_error "无法连接到 Termux Bridge 服务"
        return 1
    fi
}

# 检查服务是否就绪
bridge_ping() {
    local response
    response=$(curl -s "${BRIDGE_URL}/ping" --connect-timeout 3 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        log_success "Termux Bridge 服务正常运行"
        echo "$response"
        return 0
    else
        log_error "Termux Bridge 服务未响应"
        return 1
    fi
}

# 点击坐标
bridge_tap() {
    local x="$1"
    local y="$2"
    
    if [ -z "$x" ] || [ -z "$y" ]; then
        log_error "用法: bridge_tap <x> <y>"
        return 1
    fi
    
    log_info "点击坐标 ($x, $y)"
    bridge_send "tap" "{\"x\":$x,\"y\":$y}"
}

# 点击元素
bridge_tap_element() {
    local selector="$1"
    local value="$2"
    
    if [ -z "$value" ]; then
        # 默认按文本查找
        value="$selector"
        selector="text"
    fi
    
    log_info "点击元素 $selector=\"$value\""
    bridge_send "tap_element" "{\"$selector\":\"$value\"}"
}

# 滑动
bridge_swipe() {
    local startX="$1"
    local startY="$2"
    local endX="$3"
    local endY="$4"
    local duration="${5:-300}"
    
    if [ -z "$startX" ] || [ -z "$startY" ] || [ -z "$endX" ] || [ -z "$endY" ]; then
        log_error "用法: bridge_swipe <startX> <startY> <endX> <endY> [duration]"
        return 1
    fi
    
    log_info "滑动 ($startX, $startY) -> ($endX, $endY)"
    bridge_send "swipe" "{\"startX\":$startX,\"startY\":$startY,\"endX\":$endX,\"endY\":$endY,\"duration\":$duration}"
}

# 向上滑动（翻页）
bridge_swipe_up() {
    local duration="${1:-300}"
    log_info "向上滑动"
    bridge_swipe 540 1500 540 500 "$duration"
}

# 向下滑动（翻页）
bridge_swipe_down() {
    local duration="${1:-300}"
    log_info "向下滑动"
    bridge_swipe 540 500 540 1500 "$duration"
}

# 长按
bridge_long_press() {
    local x="$1"
    local y="$2"
    local duration="${3:-1000}"
    
    if [ -z "$x" ] || [ -z "$y" ]; then
        log_error "用法: bridge_long_press <x> <y> [duration]"
        return 1
    fi
    
    log_info "长按 ($x, $y) 持续 ${duration}ms"
    bridge_send "long_press" "{\"x\":$x,\"y\":$y,\"duration\":$duration}"
}

# 输入文本
bridge_input() {
    local text="$1"
    
    if [ -z "$text" ]; then
        log_error "用法: bridge_input <text>"
        return 1
    fi
    
    log_info "输入文本: $text"
    bridge_send "input_text" "{\"text\":\"$text\"}"
}

# 返回键
bridge_back() {
    log_info "按返回键"
    bridge_send "back"
}

# Home键
bridge_home() {
    log_info "按Home键"
    bridge_send "home"
}

# 最近任务
bridge_recent() {
    log_info "打开最近任务"
    bridge_send "recent"
}

# 通知栏
bridge_notifications() {
    log_info "打开通知栏"
    bridge_send "notifications"
}

# 快速设置
bridge_quick_settings() {
    log_info "打开快速设置"
    bridge_send "quick_settings"
}

# 查找元素
bridge_find() {
    local selector="$1"
    local value="$2"
    
    if [ -z "$value" ]; then
        value="$selector"
        selector="text"
    fi
    
    log_info "查找元素 $selector=\"$value\""
    bridge_send "find_element" "{\"$selector\":\"$value\"}"
}

# 获取界面结构
bridge_dump() {
    log_info "获取界面结构"
    bridge_send "dump"
}

# 向前滚动
bridge_scroll_forward() {
    local selector="${1:-text}"
    local value="$2"
    
    log_info "向前滚动"
    if [ -n "$value" ]; then
        bridge_send "scroll_forward" "{\"$selector\":\"$value\"}"
    else
        bridge_send "scroll_forward"
    fi
}

# 向后滚动
bridge_scroll_backward() {
    local selector="${1:-text}"
    local value="$2"
    
    log_info "向后滚动"
    if [ -n "$value" ]; then
        bridge_send "scroll_backward" "{\"$selector\":\"$value\"}"
    else
        bridge_send "scroll_backward"
    fi
}

# 等待
bridge_wait() {
    local seconds="$1"
    
    if [ -z "$seconds" ]; then
        seconds=1
    fi
    
    sleep "$seconds"
}

# 等待元素出现
bridge_wait_for() {
    local selector="$1"
    local value="$2"
    local timeout="${3:-10}"
    local interval="${4:-1}"
    
    log_info "等待元素 $selector=\"$value\" (超时: ${timeout}s)"
    
    local elapsed=0
    while [ "$elapsed" -lt "$timeout" ]; do
        local result
        result=$(bridge_send "find_element" "{\"$selector\":\"$value\"}")
        
        if echo "$result" | grep -q '"success":true'; then
            log_success "元素已出现"
            return 0
        fi
        
        sleep "$interval"
        elapsed=$((elapsed + interval))
    done
    
    log_error "等待超时"
    return 1
}

# 帮助信息
bridge_help() {
    cat << EOF
Termux Bridge 客户端工具

用法: source bridge.sh && bridge_<command> [args]

命令列表:
  基础命令:
    bridge_status              - 检查服务状态
    bridge_ping                - 检查服务是否响应
    
  触控操作:
    bridge_tap <x> <y>         - 点击指定坐标
    bridge_tap_element <text>  - 点击指定文本的元素
    bridge_swipe <sx> <sy> <ex> <ey> [duration] - 滑动
    bridge_swipe_up [duration] - 向上滑动（翻页）
    bridge_swipe_down [duration] - 向下滑动（翻页）
    bridge_long_press <x> <y> [duration] - 长按
    
  输入操作:
    bridge_input <text>        - 输入文本
    
  全局操作:
    bridge_back                - 返回键
    bridge_home                - Home键
    bridge_recent              - 最近任务
    bridge_notifications       - 通知栏
    bridge_quick_settings      - 快速设置
    
  查询操作:
    bridge_find <text>         - 查找元素
    bridge_dump                - 获取界面结构
    
  滚动操作:
    bridge_scroll_forward      - 向前滚动
    bridge_scroll_backward     - 向后滚动
    
  等待操作:
    bridge_wait <seconds>      - 等待指定秒数
    bridge_wait_for <text> [timeout] - 等待元素出现

环境变量:
    BRIDGE_HOST    - 服务地址 (默认: 127.0.0.1)
    BRIDGE_PORT    - 服务端口 (默认: 8080)
    BRIDGE_TIMEOUT - 连接超时 (默认: 10秒)

示例:
    # 点击屏幕中心
    bridge_tap 540 960
    
    # 点击"登录"按钮
    bridge_tap_element "登录"
    
    # 向上滑动翻页
    bridge_swipe_up
    
    # 输入文字
    bridge_input "Hello World"
    
    # 获取界面元素
    bridge_dump | jq .

EOF
}

# 如果直接执行，显示帮助
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    bridge_help
fi
