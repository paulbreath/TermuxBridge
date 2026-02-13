#!/bin/bash
# 快速验证脚本 - 在有Android环境的机器上运行

echo "========================================"
echo "  Termux Bridge 快速验证"
echo "========================================"

# 检查环境
echo "1. 检查Java..."
java -version || { echo "需要安装JDK 17+"; exit 1; }

echo ""
echo "2. 检查Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "警告: ANDROID_HOME 未设置"
    echo "请设置: export ANDROID_HOME=/path/to/android-sdk"
fi

echo ""
echo "3. 检查设备..."
adb devices

echo ""
echo "4. 项目文件统计:"
find . -name "*.kt" | wc -l | xargs echo "Kotlin文件数:"
find . -name "*.xml" | wc -l | xargs echo "XML文件数:"
find . -name "*.sh" | wc -l | xargs echo "Shell脚本数:"

echo ""
echo "验证完成！如需构建APK，请使用Android Studio打开项目。"
