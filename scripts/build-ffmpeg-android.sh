#!/bin/bash
# build-ffmpeg-android.sh
# 使用 Android NDK r28c 交叉编译 FFmpeg，并可选启用常用第三方编解码器。
#
# 前置条件：
#   1) 运行 setup-android-env.sh 配置好 ANDROID_NDK_HOME
#   2) 运行 clone-deps.sh 准备好源码（或自行 clone）
#   3) 本脚本会按 deps/ 目录是否存在来自动启用对应编码器
#
# 用法：
#   bash build-ffmpeg-android.sh              # 默认启用 GPL（libx264），不启用 nonfree
#   ENABLE_NONFREE=1 bash build-ffmpeg-android.sh
#
# 目录结构：
#   ffmpeg-tools/
#     ffmpeg/           FFmpeg 源码
#     deps/             第三方源码
#     Android-ffmpeg/   产物输出目录

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- 配置区 ----------
FFMPEG_DIR="${FFMPEG_DIR:-$(pwd)/deps/ffmpeg}"
DEPS_DIR="${DEPS_DIR:-$(pwd)/deps}"
OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)/Android-ffmpeg}"

API_LEVEL=28
TARGET_ABI="arm64-v8a"
ARCH=aarch64
CPU=armv8-a
CROSS_PREFIX="aarch64-linux-android${API_LEVEL}-"

ENABLE_GPL=${ENABLE_GPL:-1}          # 启用 GPL 才能使用 libx264
ENABLE_NONFREE=${ENABLE_NONFREE:-0}  # 启用 nonfree 才能使用 libfdk_aac（影响公开分发）

JOBS=$(nproc 2>/dev/null || echo 4)
# ----------------------------

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "错误：未设置 ANDROID_NDK_HOME"
    echo "请先运行：bash setup-android-env.sh && source ~/.bashrc"
    exit 1
fi

if [ ! -d "$FFMPEG_DIR" ]; then
    echo "错误：未找到 FFmpeg 源码目录 $FFMPEG_DIR"
    echo "请先运行：bash clone-deps.sh"
    exit 1
fi

if ! command -v pkg-config >/dev/null 2>&1; then
    echo "错误：未安装 pkg-config，请先安装（例如 sudo apt install pkg-config）"
    exit 1
fi

if ! command -v make >/dev/null 2>&1; then
    echo "错误：未安装 make，请先安装（例如 sudo apt install build-essential）"
    exit 1
fi

if ! command -v meson >/dev/null 2>&1; then
    echo "错误：未安装 meson，请先安装（例如 sudo apt install meson）"
    exit 1
fi

if ! command -v cmake >/dev/null 2>&1; then
    echo "错误：未安装 cmake，请先安装（例如 sudo apt install cmake）"
    exit 1
fi

# WSL 挂载 Windows 分区时 git 所有权检查会失败，自动把 deps 下所有 git 仓库加入安全目录
for dir in "$DEPS_DIR"/*; do
    if [ -d "$dir/.git" ]; then
        git config --global --add safe.directory "$dir" >/dev/null 2>&1 || true
    fi
done

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"

CC="$TOOLCHAIN/bin/${CROSS_PREFIX}clang"
CXX="$TOOLCHAIN/bin/${CROSS_PREFIX}clang++"
LD="$CC"
AR="$TOOLCHAIN/bin/llvm-ar"
NM="$TOOLCHAIN/bin/llvm-nm"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
STRIP="$TOOLCHAIN/bin/llvm-strip"

for tool in "$CC" "$CXX" "$AR" "$STRIP"; do
    if [ ! -x "$tool" ]; then
        echo "错误：找不到工具 $tool"
        echo "请确认 NDK r28c 已正确安装。"
        exit 1
    fi
done

PREFIX="$(pwd)/build-${TARGET_ABI}"
mkdir -p "$PREFIX/lib" "$PREFIX/include" "$OUTPUT_DIR"

export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
export PATH="$TOOLCHAIN/bin:$PATH"

# ---------- 构建外部库 ----------
build_x264() {
    if [ ! -d "$DEPS_DIR/x264" ]; then
        echo "跳过 x264：源码不存在"
        return 0
    fi
    echo "=== 构建 x264 ==="
    cd "$DEPS_DIR/x264"
    rm -f "$PREFIX/lib/libx264.so" "$PREFIX/lib/libx264.a" "$PREFIX/lib/pkgconfig/x264.pc"
    make clean >/dev/null 2>&1 || true
    CC="$CC" AR="$AR" STRIP="$STRIP" RANLIB="$RANLIB" ./configure \
        --prefix="$PREFIX" \
        --host=aarch64-linux-android \
        --sysroot="$SYSROOT" \
        --disable-cli \
        --disable-asm \
        --enable-static \
        --enable-pic \
        --bit-depth=all
    make -j$JOBS
    make install
    cd -
}

build_lame() {
    if [ ! -d "$DEPS_DIR/lame" ]; then
        echo "跳过 lame：源码不存在"
        return 0
    fi
    echo "=== 构建 lame ==="
    cd "$DEPS_DIR/lame"
    find . -type f -exec touch {} +
    make distclean >/dev/null 2>&1 || true
    CC="$CC" AR="$AR" STRIP="$STRIP" RANLIB="$RANLIB" ./configure \
        --prefix="$PREFIX" \
        --host=aarch64-linux-android \
        --disable-shared \
        --enable-static \
        --disable-frontend \
        --with-sysroot="$SYSROOT"
    make -j$JOBS
    make install
    cd -
}

build_opus() {
    if [ ! -d "$DEPS_DIR/opus" ]; then
        echo "跳过 opus：源码不存在"
        return 0
    fi
    echo "=== 构建 opus ==="
    cd "$DEPS_DIR"
    if [ ! -f "opus/dnn/fargan_data.h" ]; then
        echo "opus git 源码缺少生成文件，改用 release tarball..."
        rm -rf opus
        if command -v wget >/dev/null 2>&1; then
            wget -q --show-progress https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz
        else
            curl -# -L -o opus-1.5.2.tar.gz https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz
        fi
        tar -xzf opus-1.5.2.tar.gz
        mv opus-1.5.2 opus
        rm opus-1.5.2.tar.gz
    fi
    cd opus
    rm -rf build
    BUILD_TMP=$(mktemp -d)
    cat >"$BUILD_TMP/cross.txt" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
pkgconfig = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'

[properties]
sys_root = '$SYSROOT'
EOF
    rm -f "$PREFIX/lib/libopus.so" "$PREFIX/lib/libopus.a" "$PREFIX/lib/pkgconfig/opus.pc"
    meson setup "$BUILD_TMP" --cross-file "$BUILD_TMP/cross.txt" --prefix="$PREFIX" --libdir=lib -Ddefault_library=static -Dtests=disabled -Ddocs=disabled -Dextra-programs=disabled
    meson compile -C "$BUILD_TMP"
    meson install -C "$BUILD_TMP"
    rm -rf "$BUILD_TMP"
    cd -
}

build_vpx() {
    if [ ! -d "$DEPS_DIR/libvpx" ]; then
        echo "跳过 libvpx：源码不存在"
        return 0
    fi
    echo "=== 构建 libvpx ==="
    cd "$DEPS_DIR/libvpx"
    rm -f "$PREFIX/lib/libvpx.so" "$PREFIX/lib/libvpx.a" "$PREFIX/lib/pkgconfig/vpx.pc"
    make clean >/dev/null 2>&1 || true
    CROSS="$TOOLCHAIN/bin/aarch64-linux-android-" \
    CC="$CC" CXX="$CXX" AR="$AR" STRIP="$STRIP" RANLIB="$RANLIB" \
    ./configure \
        --target=arm64-android-gcc \
        --prefix="$PREFIX" \
        --disable-examples \
        --disable-unit-tests \
        --disable-tools \
        --disable-docs \
        --enable-vp8 \
        --enable-vp9 \
        --enable-pic \
        --disable-shared
    make -j$JOBS
    make install
    cd -
}

build_fdk_aac() {
    if [ ! -d "$DEPS_DIR/fdk-aac" ]; then
        echo "跳过 fdk-aac：源码不存在"
        return 0
    fi
    echo "=== 构建 fdk-aac ==="
    cd "$DEPS_DIR/fdk-aac"
    rm -f "$PREFIX/lib/libfdk-aac.so" "$PREFIX/lib/libfdk-aac.a" "$PREFIX/lib/pkgconfig/fdk-aac.pc"
    make clean >/dev/null 2>&1 || true
    CC="$CC" CXX="$CXX" AR="$AR" STRIP="$STRIP" RANLIB="$RANLIB" ./configure \
        --prefix="$PREFIX" \
        --host=aarch64-linux-android \
        --disable-shared \
        --enable-static \
        --with-sysroot="$SYSROOT"
    make -j$JOBS
    make install
    cd -
}

build_x265() {
    if [ ! -d "$DEPS_DIR/x265/source" ]; then
        echo "跳过 x265：源码不存在"
        return 0
    fi
    echo "=== 构建 x265 (8bit/10bit/12bit multilib) ==="
    cd "$DEPS_DIR/x265/source"
    rm -f "$PREFIX/lib/libx265.so" "$PREFIX/lib/libx265.a" "$PREFIX/lib/pkgconfig/x265.pc"
    rm -rf build-8 build-10 build-12 x265_extract

    export X265_CC="$CC"
    export X265_CXX="$CXX"
    export X265_AR="$AR"
    export X265_RANLIB="$RANLIB"
    export X265_STRIP="$STRIP"
    export X265_CFLAGS="--sysroot=$SYSROOT -fPIC -I$PREFIX/include"
    export X265_CXXFLAGS="--sysroot=$SYSROOT -fPIC -I$PREFIX/include"
    export X265_LDFLAGS="--sysroot=$SYSROOT -L$PREFIX/lib"
    export X265_PREFIX="$PREFIX"

    X265_CMAKE_ARGS=(
        -DCMAKE_TOOLCHAIN_FILE="$SCRIPT_DIR/x265-android-toolchain.cmake"
        -DCMAKE_INSTALL_PREFIX="$PREFIX"
        -DENABLE_SHARED=OFF
        -DENABLE_CLI=OFF
        -DENABLE_LIBNUMA=OFF
        -DCMAKE_BUILD_TYPE=Release
    )

    mkdir -p build-12 && cd build-12
    cmake "${X265_CMAKE_ARGS[@]}" -DHIGH_BIT_DEPTH=ON -DMAIN12=ON -DEXPORT_C_API=OFF ..
    cmake --build . -j$JOBS
    cp libx265.a ../build-12-libx265_main12.a
    cd ..

    mkdir -p build-10 && cd build-10
    cmake "${X265_CMAKE_ARGS[@]}" -DHIGH_BIT_DEPTH=ON -DEXPORT_C_API=OFF ..
    cmake --build . -j$JOBS
    cp libx265.a ../build-10-libx265_main10.a
    cd ..

    mkdir -p build-8 && cd build-8
    cp ../build-10-libx265_main10.a libx265_main10.a
    cp ../build-12-libx265_main12.a libx265_main12.a
    cmake "${X265_CMAKE_ARGS[@]}" \
        -DEXTRA_LIB="x265_main10.a;x265_main12.a" \
        -DEXTRA_LINK_FLAGS=-L. \
        -DLINKED_10BIT=ON \
        -DLINKED_12BIT=ON ..
    cmake --build . -j$JOBS
    cmake --install . --prefix="$PREFIX"

    # 合并 8/10/12bit 三个静态库为一个 libx265.a
    cd ..
    rm -rf x265_extract libx265_combined.a
    mkdir -p x265_extract/8 x265_extract/10 x265_extract/12
    (cd x265_extract/8 && "$AR" x ../../build-8/libx265.a)
    (cd x265_extract/10 && "$AR" x ../../build-10/libx265.a)
    (cd x265_extract/12 && "$AR" x ../../build-12/libx265.a)
    find x265_extract -name '*.o' | sort | xargs "$AR" rcs libx265_combined.a
    "$RANLIB" libx265_combined.a
    cp libx265_combined.a "$PREFIX/lib/libx265.a"
    rm -rf x265_extract libx265_combined.a

    # 修复 x265.pc：x265 是 C++ 库，Android 静态链接需要 c++_static
    sed -i 's/Libs.private: .*/Libs.private: -lc++_static -lm -ldl/' "$PREFIX/lib/pkgconfig/x265.pc"

    rm -f build-10-libx265_main10.a build-12-libx265_main12.a
    cd -
}

build_kvazaar() {
    if [ ! -d "$DEPS_DIR/kvazaar" ]; then
        echo "跳过 kvazaar：源码不存在"
        return 0
    fi
    echo "=== 构建 kvazaar ==="
    cd "$DEPS_DIR/kvazaar"
    rm -f "$PREFIX/lib/libkvazaar.so" "$PREFIX/lib/libkvazaar.a" "$PREFIX/lib/pkgconfig/kvazaar.pc"
    rm -rf build
    cmake -B build \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTS=OFF \
        -DBUILD_APPS=OFF \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build build -j$JOBS
    cmake --install build
    cd -
}

build_aom() {
    if [ ! -d "$DEPS_DIR/aom" ]; then
        echo "跳过 aom：源码不存在"
        return 0
    fi
    echo "=== 构建 aom ==="
    cd "$DEPS_DIR/aom"
    rm -f "$PREFIX/lib/libaom.so" "$PREFIX/lib/libaom.a" "$PREFIX/lib/pkgconfig/aom.pc"
    rm -rf build
    cmake -B build \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DENABLE_SHARED=OFF \
        -DENABLE_TESTS=OFF \
        -DENABLE_TOOLS=OFF \
        -DENABLE_DOCS=OFF \
        -DENABLE_EXAMPLES=OFF \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build build -j$JOBS
    cmake --install build
    cd -
}

build_svt_av1() {
    if [ ! -d "$DEPS_DIR/svt-av1" ]; then
        echo "跳过 svt-av1：源码不存在"
        return 0
    fi
    echo "=== 构建 svt-av1 ==="
    cd "$DEPS_DIR/svt-av1"
    rm -f "$PREFIX/lib/libSvtAv1Enc.so" "$PREFIX/lib/libSvtAv1Enc.a" "$PREFIX/lib/pkgconfig/SvtAv1Enc.pc"
    rm -rf build
    cmake -B build \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-$API_LEVEL \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTING=OFF \
        -DBUILD_APPS=OFF \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build build -j$JOBS
    cmake --install build
    cd -
}

build_dav1d() {
    if [ ! -d "$DEPS_DIR/dav1d" ]; then
        echo "跳过 dav1d：源码不存在"
        return 0
    fi
    echo "=== 构建 dav1d ==="
    cd "$DEPS_DIR/dav1d"
    rm -f "$PREFIX/lib/libdav1d.so" "$PREFIX/lib/libdav1d.a" "$PREFIX/lib/pkgconfig/dav1d.pc"
    rm -rf build
    BUILD_TMP=$(mktemp -d)
    cat >"$BUILD_TMP/cross.txt" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'
pkgconfig = 'pkg-config'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'

[properties]
sys_root = '$SYSROOT'
EOF
    meson setup "$BUILD_TMP" --cross-file "$BUILD_TMP/cross.txt" --prefix="$PREFIX" --libdir=lib -Ddefault_library=static -Denable_tools=false -Denable_tests=false
    meson compile -C "$BUILD_TMP"
    meson install -C "$BUILD_TMP"
    rm -rf "$BUILD_TMP"
    cd -
}

# 依次构建
[ "$ENABLE_GPL" = "1" ] && build_x264
[ "$ENABLE_GPL" = "1" ] && build_x265
build_lame
build_opus
build_vpx
[ "$ENABLE_GPL" = "1" ] && build_kvazaar
build_aom
build_svt_av1
build_dav1d
[ "$ENABLE_NONFREE" = "1" ] && build_fdk_aac

# ---------- 构建 FFmpeg ----------
cd "$FFMPEG_DIR"
make clean >/dev/null 2>&1 || true

EXTRA_CFLAGS="-I$PREFIX/include -fPIC"
EXTRA_LDFLAGS="-L$PREFIX/lib -static-libstdc++"
EXTRA_LIBS="-lm -llog -landroid -lz -ldl"

CONF_FLAGS=(
    --prefix="$PREFIX"
    --target-os=android
    --arch="$ARCH"
    --cpu="$CPU"
    --sysroot="$SYSROOT"
    --cross-prefix="$TOOLCHAIN/bin/aarch64-linux-android-"
    --cc="$CC"
    --cxx="$CXX"
    --ld="$CXX"
    --ar="$AR"
    --nm="$NM"
    --ranlib="$RANLIB"
    --strip="$STRIP"
    --extra-cflags="$EXTRA_CFLAGS"
    --extra-ldflags="$EXTRA_LDFLAGS"
    --extra-libs="$EXTRA_LIBS"
    --pkg-config=pkg-config
    --pkg-config-flags="--static"
    --enable-pic
    --enable-static
    --disable-shared
    --enable-ffmpeg
    --enable-ffprobe
    --enable-avdevice
    --enable-avcodec
    --enable-avformat
    --enable-avutil
    --enable-swresample
    --enable-swscale
    --enable-avfilter
    --enable-network
    --enable-jni
    --enable-mediacodec
    --enable-decoder=h264_mediacodec
    --enable-decoder=hevc_mediacodec
    --enable-decoder=mpeg4_mediacodec
    --enable-hwaccel=mediacodec
    --disable-doc
    --disable-htmlpages
    --disable-manpages
    --disable-podpages
    --disable-txtpages
    --disable-debug
    --disable-vulkan
    --disable-d3d11va
    --disable-dxva2
    --disable-vaapi
    --disable-vdpau
    --disable-videotoolbox
    --disable-audiotoolbox
    --disable-bzlib
)

# 动态组装 encoder/decoder 列表
ENCODER_LIST="aac,mpeg4,h264_mediacodec,hevc_mediacodec,mpeg4_mediacodec"
DECODER_LIST="aac,h264,hevc,mpeg4,mp3,opus,vorbis"

if [ "$ENABLE_GPL" = "1" ] && [ -d "$DEPS_DIR/x264" ]; then
    CONF_FLAGS+=(--enable-gpl --enable-libx264)
    ENCODER_LIST="$ENCODER_LIST,libx264"
fi

if [ "$ENABLE_GPL" = "1" ] && [ -d "$DEPS_DIR/x265" ]; then
    CONF_FLAGS+=(--enable-libx265)
    ENCODER_LIST="$ENCODER_LIST,libx265"
fi

if [ -d "$DEPS_DIR/lame" ]; then
    CONF_FLAGS+=(--enable-libmp3lame)
    ENCODER_LIST="$ENCODER_LIST,libmp3lame"
fi

if [ -d "$DEPS_DIR/opus" ]; then
    CONF_FLAGS+=(--enable-libopus)
    ENCODER_LIST="$ENCODER_LIST,libopus"
    DECODER_LIST="$DECODER_LIST,libopus"
fi

if [ -d "$DEPS_DIR/libvpx" ]; then
    CONF_FLAGS+=(--enable-libvpx)
    ENCODER_LIST="$ENCODER_LIST,libvpx,libvpx_vp9"
fi

if [ "$ENABLE_GPL" = "1" ] && [ -d "$DEPS_DIR/kvazaar" ]; then
    CONF_FLAGS+=(--enable-libkvazaar)
    ENCODER_LIST="$ENCODER_LIST,libkvazaar"
fi

if [ -d "$DEPS_DIR/aom" ]; then
    CONF_FLAGS+=(--enable-libaom)
    ENCODER_LIST="$ENCODER_LIST,libaom_av1"
    DECODER_LIST="$DECODER_LIST,libaom_av1"
fi

if [ -d "$DEPS_DIR/svt-av1" ]; then
    CONF_FLAGS+=(--enable-libsvtav1)
    ENCODER_LIST="$ENCODER_LIST,libsvtav1"
fi

if [ -d "$DEPS_DIR/dav1d" ]; then
    CONF_FLAGS+=(--enable-libdav1d)
    DECODER_LIST="$DECODER_LIST,libdav1d"
fi

if [ "$ENABLE_NONFREE" = "1" ] && [ -d "$DEPS_DIR/fdk-aac" ]; then
    CONF_FLAGS+=(--enable-nonfree --enable-libfdk-aac)
    ENCODER_LIST="$ENCODER_LIST,libfdk_aac"
fi

CONF_FLAGS+=(
    --enable-encoder="$ENCODER_LIST"
    --enable-decoder="$DECODER_LIST"
)

echo ""
echo "=== FFmpeg configure 参数 ==="
printf '%s\n' "${CONF_FLAGS[@]}"
echo ""

./configure "${CONF_FLAGS[@]}"
make -j$JOBS
make install

# ---------- 复制产物 ----------
cd -
cp "$PREFIX/bin/ffmpeg" "$OUTPUT_DIR/ffmpeg"
cp "$PREFIX/bin/ffprobe" "$OUTPUT_DIR/ffprobe"

# 部分 C++ 依赖库（x265/SVT-AV1/AOM）仍会动态链接 libc++_shared.so，
# 因此把 NDK 提供的共享库一并打包，避免运行时报 "library libc++_shared.so not found"。
LIBCXX_SO="$SYSROOT/usr/lib/$ARCH-linux-android/libc++_shared.so"
if [ -f "$LIBCXX_SO" ]; then
    cp "$LIBCXX_SO" "$OUTPUT_DIR/libc++_shared.so"
else
    echo "警告：未找到 $LIBCXX_SO，运行 FFmpeg 可能需要手动提供 libc++_shared.so"
fi

# 静态构建不会生成 libffmpeg.so；App 主流程使用 CLI，libffmpeg.so 不是必须的。
# 如果你确实需要 libffmpeg.so，可在此用 static libs 手动打包，或改用 --enable-shared。
if [ -f "$PREFIX/lib/libffmpeg.so" ]; then
    cp "$PREFIX/lib/libffmpeg.so" "$OUTPUT_DIR/libffmpeg.so"
fi

"$STRIP" "$OUTPUT_DIR/ffmpeg" || true
"$STRIP" "$OUTPUT_DIR/ffprobe" || true

echo ""
echo "=== 编译完成 ==="
echo "产物目录：$OUTPUT_DIR"
ls -lh "$OUTPUT_DIR/ffmpeg" "$OUTPUT_DIR/ffprobe"
"$OUTPUT_DIR/ffmpeg" -version | head -n 1 || true
echo ""
echo "启用的编码器：$ENCODER_LIST"
echo "启用的解码器：$DECODER_LIST"
