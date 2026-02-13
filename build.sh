#!/bin/bash
#
# Termux Bridge 快速构建脚本
# 用于在没有Android Studio的环境中构建APK
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/android-app"

echo "========================================="
echo "    Termux Bridge 构建脚本"
echo "========================================="
echo ""

# 检查环境
check_environment() {
    echo "[1/5] 检查构建环境..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        echo "❌ Java未安装"
        echo "请安装 JDK 17 或更高版本"
        exit 1
    fi
    echo "✓ Java: $(java -version 2>&1 | head -1)"
    
    # 检查ANDROID_HOME
    if [ -z "$ANDROID_HOME" ]; then
        # 尝试常见路径
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        elif [ -d "$HOME/Library/Android/sdk" ]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
        elif [ -d "/usr/local/android-sdk" ]; then
            export ANDROID_HOME="/usr/local/android-sdk"
        else
            echo "❌ ANDROID_HOME 未设置"
            echo "请设置 ANDROID_HOME 环境变量"
            exit 1
        fi
    fi
    echo "✓ ANDROID_HOME: $ANDROID_HOME"
    
    # 检查Android SDK
    if [ ! -d "$ANDROID_HOME/platforms" ]; then
        echo "❌ Android SDK 未正确安装"
        exit 1
    fi
    echo "✓ Android SDK 已安装"
}

# 下载Gradle Wrapper
setup_gradle_wrapper() {
    echo ""
    echo "[2/5] 设置Gradle Wrapper..."
    
    cd "$PROJECT_DIR"
    
    # 创建gradle wrapper目录
    mkdir -p gradle/wrapper
    
    # 下载gradle-wrapper.jar
    if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
        echo "下载 gradle-wrapper.jar..."
        curl -L -o gradle/wrapper/gradle-wrapper.jar \
            "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
    fi
    
    # 创建gradle-wrapper.properties
    cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
    
    # 创建gradlew脚本
    cat > gradlew << 'GRADLEW'
#!/bin/sh

##############################################################################
# Gradle start up script for POSIX
##############################################################################

# Attempt to set APP_HOME
PRG="$0"
# Need this for relative symlinks
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW
    chmod +x gradlew
    
    echo "✓ Gradle Wrapper 已设置"
}

# 构建APK
build_apk() {
    echo ""
    echo "[3/5] 构建APK..."
    
    cd "$PROJECT_DIR"
    
    # 运行Gradle构建
    ./gradlew assembleDebug --no-daemon
    
    echo "✓ APK构建完成"
}

# 检查构建结果
check_result() {
    echo ""
    echo "[4/5] 检查构建结果..."
    
    APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        echo "✓ APK已生成: $APK_PATH"
        echo "  大小: $APK_SIZE"
    else
        echo "❌ APK生成失败"
        exit 1
    fi
}

# 安装指南
show_install_guide() {
    echo ""
    echo "[5/5] 安装指南"
    echo ""
    echo "========================================="
    echo "    构建成功！"
    echo "========================================="
    echo ""
    echo "APK位置: $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "安装方法："
    echo ""
    echo "方法1 - 使用ADB安装（推荐）："
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "方法2 - 传输到手机安装："
    echo "  将APK文件传输到手机，点击安装"
    echo ""
    echo "安装后步骤："
    echo "1. 打开 Termux Bridge 应用"
    echo "2. 点击'启用服务'，在设置中开启无障碍服务"
    echo "3. 返回应用，点击'启动'开启HTTP服务器"
    echo "4. 在Termux中使用客户端工具进行控制"
    echo ""
}

# 主流程
main() {
    check_environment
    setup_gradle_wrapper
    build_apk
    check_result
    show_install_guide
}

main "$@"
