#!/bin/bash
# setup-android-env.sh
# 在 Linux 下一次安装并配置 Android SDK + NDK r28c 编译环境。
# 用法：bash setup-android-env.sh

set -euo pipefail

# ---------- 配置区 ----------
INSTALL_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
SDK_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
NDK_VERSION="28.2.13676358"          # NDK r28c
BUILD_TOOLS="35.0.0"
API_LEVEL="28"
# ----------------------------

echo "=== Android SDK/NDK r28c 环境安装 ==="
echo "安装目录：$INSTALL_DIR"

mkdir -p "$INSTALL_DIR/cmdline-tools"
cd "$INSTALL_DIR/cmdline-tools"

if [ ! -d "latest/bin" ]; then
    echo "下载 Android command line tools..."
    rm -f commandlinetools.zip
    if command -v wget >/dev/null 2>&1; then
        wget -q --show-progress "$SDK_TOOLS_URL" -O commandlinetools.zip
    else
        curl -# -L -o commandlinetools.zip "$SDK_TOOLS_URL"
    fi
    unzip -q commandlinetools.zip
    mv cmdline-tools latest
    rm -f commandlinetools.zip
fi

export ANDROID_HOME="$INSTALL_DIR"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "安装 SDK 组件..."
sdkmanager --install \
    "platform-tools" \
    "build-tools;$BUILD_TOOLS" \
    "platforms;android-$API_LEVEL" \
    "ndk;$NDK_VERSION" \
    "cmdline-tools;latest"

export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
export PATH="$ANDROID_NDK_HOME:$PATH"

# 写入 ~/.bashrc（幂等）
if ! grep -q "ANDROID_HOME=$ANDROID_HOME" "$HOME/.bashrc" 2>/dev/null; then
    cat >> "$HOME/.bashrc" <<EOF

# Android SDK
export ANDROID_HOME=$ANDROID_HOME
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_NDK_HOME=$ANDROID_NDK_HOME
export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_NDK_HOME:\$PATH
EOF
fi

echo ""
echo "=== 安装完成 ==="
echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
echo ""
echo "请执行：source ~/.bashrc"
echo "验证：adb --version"
echo "      $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang --version"
