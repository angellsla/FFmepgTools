#!/bin/bash
# clone-deps.sh
# 一键克隆/更新 FFmpeg 及常用第三方编解码器源码。
# 请根据许可证需求选择是否启用 GPL/nonfree 组件。

set -euo pipefail

mkdir -p deps
cd deps

clone_or_pull() {
    local url=$1
    local dir=$2
    if [ -d "$dir/.git" ]; then
        echo "更新 $dir ..."
        (cd "$dir" && git pull --ff-only)
    else
        echo "克隆 $dir ..."
        git clone "$url" "$dir"
    fi
}

echo "=== 克隆/更新源码 ==="
clone_or_pull https://git.ffmpeg.org/ffmpeg.git ffmpeg
clone_or_pull https://code.videolan.org/videolan/x264.git x264
clone_or_pull https://bitbucket.org/multicoreware/x265_git.git x265
clone_or_pull https://github.com/ultravideo/kvazaar.git kvazaar
clone_or_pull https://aomedia.googlesource.com/aom aom
clone_or_pull https://gitlab.com/AOMediaCodec/SVT-AV1.git svt-av1
clone_or_pull https://code.videolan.org/videolan/dav1d.git dav1d
clone_or_pull https://github.com/rbrito/lame.git lame
clone_or_pull https://gitlab.xiph.org/xiph/opus.git opus
clone_or_pull https://chromium.googlesource.com/webm/libvpx libvpx
clone_or_pull https://github.com/mstorsjo/fdk-aac.git fdk-aac

cd -
echo ""
echo "=== 源码准备完成 ==="
echo "注意："
echo "  x264     -> GPL"
echo "  x265     -> GPL"
echo "  fdk-aac  -> nonfree（影响公开分发）"
echo "  lame     -> LGPL"
echo "  opus     -> BSD"
echo "  libvpx   -> BSD"
echo "  kvazaar  -> BSD"
echo "  aom      -> BSD"
echo "  svt-av1  -> BSD"
echo "  dav1d    -> BSD"
echo ""
echo "编译前建议进入 deps/<项目> 切换到稳定 tag/branch。"
