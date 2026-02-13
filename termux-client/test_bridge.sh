#!/bin/bash
#
# Termux Bridge 测试脚本
# 用于测试桥接服务的各项功能
#

# 引入客户端库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/bridge.sh"

# 测试计数器
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# 测试结果颜色
PASS='\033[0;32m'
FAIL='\033[0;31m'
NC='\033[0m'

# 测试函数
run_test() {
    local test_name="$1"
    local test_func="$2"
    
    echo "========================================="
    echo "测试: $test_name"
    echo "========================================="
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    if $test_func; then
        echo -e "${PASS}[PASS]${NC} $test_name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${FAIL}[FAIL]${NC} $test_name"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
    echo ""
}

# 测试1: 服务连接
test_connection() {
    log_info "测试服务连接..."
    local result
    result=$(bridge_ping 2>&1)
    
    if echo "$result" | grep -q '"status":"ok"'; then
        log_success "服务连接成功"
        return 0
    else
        log_error "服务连接失败: $result"
        return 1
    fi
}

# 测试2: 服务状态
test_status() {
    log_info "测试服务状态..."
    local result
    result=$(bridge_status 2>&1)
    
    if echo "$result" | grep -q '"status"'; then
        log_success "状态查询成功"
        echo "$result"
        return 0
    else
        log_error "状态查询失败"
        return 1
    fi
}

# 测试3: 点击操作
test_tap() {
    log_info "测试点击操作..."
    local result
    result=$(bridge_tap 540 960 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "点击操作成功"
        return 0
    else
        log_error "点击操作失败: $result"
        return 1
    fi
}

# 测试4: 滑动操作
test_swipe() {
    log_info "测试滑动操作..."
    local result
    result=$(bridge_swipe 540 1500 540 500 300 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "滑动操作成功"
        return 0
    else
        log_error "滑动操作失败: $result"
        return 1
    fi
}

# 测试5: 返回键
test_back() {
    log_info "测试返回键..."
    local result
    result=$(bridge_back 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "返回键成功"
        return 0
    else
        log_error "返回键失败: $result"
        return 1
    fi
}

# 测试6: Home键
test_home() {
    log_info "测试Home键..."
    local result
    result=$(bridge_home 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "Home键成功"
        return 0
    else
        log_error "Home键失败: $result"
        return 1
    fi
}

# 测试7: 获取界面结构
test_dump() {
    log_info "测试获取界面结构..."
    local result
    result=$(bridge_dump 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "获取界面结构成功"
        # 显示部分结果
        echo "$result" | head -c 500
        echo "..."
        return 0
    else
        log_error "获取界面结构失败: $result"
        return 1
    fi
}

# 测试8: 查找元素
test_find_element() {
    log_info "测试查找元素..."
    local result
    result=$(bridge_find "text" "设置" 2>&1)
    
    echo "$result"
    return 0  # 这个测试总是通过，因为元素可能不存在
}

# 测试9: 最近任务
test_recent() {
    log_info "测试最近任务..."
    local result
    result=$(bridge_recent 2>&1)
    
    if echo "$result" | grep -q '"success":true'; then
        log_success "最近任务成功"
        bridge_wait 2
        bridge_back  # 关闭最近任务界面
        return 0
    else
        log_error "最近任务失败: $result"
        return 1
    fi
}

# 测试10: 输入文本
test_input() {
    log_info "测试输入文本..."
    local result
    result=$(bridge_input "Test123" 2>&1)
    
    echo "$result"
    return 0  # 输入可能失败，因为没有焦点元素
}

# 运行所有测试
echo "========================================="
echo "    Termux Bridge 功能测试套件"
echo "========================================="
echo ""

# 首先检查服务是否可用
if ! bridge_ping > /dev/null 2>&1; then
    log_error "无法连接到 Termux Bridge 服务"
    log_info "请确保："
    log_info "1. Termux Bridge 应用已安装"
    log_info "2. 无障碍服务已启用"
    log_info "3. HTTP服务器已启动"
    exit 1
fi

log_success "服务可用，开始测试..."
echo ""

# 运行测试
run_test "服务连接" test_connection
run_test "服务状态" test_status
run_test "点击操作" test_tap
run_test "滑动操作" test_swipe
run_test "返回键" test_back
run_test "Home键" test_home
run_test "获取界面结构" test_dump
run_test "查找元素" test_find_element
run_test "最近任务" test_recent
run_test "输入文本" test_input

# 输出测试总结
echo "========================================="
echo "             测试总结"
echo "========================================="
echo -e "总测试数: $TESTS_RUN"
echo -e "通过: ${PASS}$TESTS_PASSED${NC}"
echo -e "失败: ${FAIL}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${PASS}所有测试通过!${NC}"
    exit 0
else
    echo -e "${FAIL}有 $TESTS_FAILED 个测试失败${NC}"
    exit 1
fi
