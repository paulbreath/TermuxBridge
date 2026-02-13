#!/bin/bash
#
# OpenClaw 集成示例
# 演示如何在OpenClaw脚本中使用Termux Bridge
#

# 引入客户端库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/bridge.sh"

# ==================== TikTok 自动化示例 ====================

tiktok_auto_like() {
    local count="${1:-10}"
    
    log_info "开始TikTok自动点赞，共 $count 次"
    
    # 启动TikTok
    log_info "启动TikTok..."
    adb shell am start -n com.zhiliaoapp.musically/.main.MainActivity 2>/dev/null || \
    adb shell monkey -p com.zhiliaoapp.musically -c android.intent.category.LAUNCHER 1
    bridge_wait 3
    
    # 循环点赞
    for i in $(seq 1 $count); do
        log_info "第 $i/$count 次"
        
        # 点击点赞按钮（右侧爱心）
        bridge_tap 970 1350
        bridge_wait 1
        
        # 向上滑动下一个视频
        bridge_swipe_up
        bridge_wait 2
        
        # 随机延迟，模拟人类行为
        local delay=$((RANDOM % 3 + 1))
        bridge_wait $delay
    done
    
    log_success "完成 $count 次点赞"
}

# ==================== Reddit 自动化示例 ====================

reddit_browse() {
    local subreddit="${1:-popular}"
    local posts="${2:-5}"
    
    log_info "浏览Reddit r/$subreddit"
    
    # 启动Reddit
    log_info "启动Reddit..."
    adb shell am start -n com.reddit.frontpage/.launcher.Launcher 2>/dev/null || \
    adb shell monkey -p com.reddit.frontpage -c android.intent.category.LAUNCHER 1
    bridge_wait 3
    
    # 浏览帖子
    for i in $(seq 1 $posts); do
        log_info "浏览第 $i 个帖子"
        
        # 点击帖子
        bridge_tap 540 500
        bridge_wait 3
        
        # 滚动查看内容
        for j in $(seq 1 3); do
            bridge_swipe_up
            bridge_wait 1
        done
        
        # 返回
        bridge_back
        bridge_wait 1
        
        # 下一个帖子
        bridge_swipe_up
        bridge_wait 2
    done
    
    log_success "浏览完成"
}

# ==================== 通用自动化示例 ====================

# 等待元素并点击
wait_and_tap() {
    local text="$1"
    local timeout="${2:-10}"
    
    log_info "等待元素: $text"
    
    if bridge_wait_for "text" "$text" "$timeout"; then
        bridge_tap_element "$text"
        return 0
    else
        log_error "元素未找到: $text"
        return 1
    fi
}

# 滚动查找并点击
scroll_and_tap() {
    local text="$1"
    local max_swipes="${2:-10}"
    
    log_info "滚动查找: $text"
    
    for i in $(seq 1 $max_swipes); do
        # 先检查当前屏幕
        if bridge_find "text" "$text" | grep -q '"success":true'; then
            bridge_tap_element "$text"
            return 0
        fi
        
        # 向下滑动
        bridge_swipe_down
        bridge_wait 1
    done
    
    log_error "未找到元素: $text"
    return 1
}

# 批量操作
batch_operation() {
    local operation="$1"
    local count="${2:-10}"
    local delay="${3:-2}"
    
    log_info "批量执行: $operation ($count 次)"
    
    for i in $(seq 1 $count); do
        log_info "执行第 $i 次"
        
        case "$operation" in
            "swipe_up")
                bridge_swipe_up
                ;;
            "swipe_down")
                bridge_swipe_down
                ;;
            "tap_center")
                bridge_tap 540 960
                ;;
            *)
                log_error "未知操作: $operation"
                return 1
                ;;
        esac
        
        bridge_wait "$delay"
    done
    
    log_success "批量操作完成"
}

# ==================== 使用说明 ====================

show_usage() {
    cat << EOF
OpenClaw 集成示例

用法: source demo_openclaw.sh && <function> [args]

可用函数:

  TikTok自动化:
    tiktok_auto_like [count]     - 自动点赞视频

  Reddit自动化:
    reddit_browse [subreddit] [posts] - 浏览Reddit

  通用工具:
    wait_and_tap <text> [timeout]    - 等待元素并点击
    scroll_and_tap <text> [swipes]   - 滚动查找并点击
    batch_operation <op> [count] [delay] - 批量操作

示例:
    # TikTok自动点赞10个视频
    tiktok_auto_like 10
    
    # 浏览Reddit r/technology 的5个帖子
    reddit_browse technology 5
    
    # 等待"登录"按钮出现并点击
    wait_and_tap "登录" 15
    
    # 滚动查找"设置"选项
    scroll_and_tap "设置" 20

注意:
    1. 确保Termux Bridge服务已启动
    2. 部分应用可能有防自动化检测
    3. 建议添加随机延迟模拟人类行为

EOF
}

# 如果直接执行，显示使用说明
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    show_usage
fi
