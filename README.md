# FFmpeg 工具箱

基于 FFmpeg 的 Android 媒体处理工具，采用 Kotlin、Jetpack Compose 和 Miuix Compose UI 构建。应用内置 FFmpeg/FFprobe CLI，用于视频转码、裁剪、合并、GIF 转换、音频处理和媒体信息查看，并支持通过 Shizuku 获取更高权限来浏览和导入系统文件。

## 功能

- 视频转码、裁剪、合并、混流
- 音频转码、提取、多文件合并
- 视频转 GIF
- 媒体信息查看
- 后台任务队列管理
- 基于 Shizuku 的特权文件浏览与导入

## 环境要求

- Android SDK API 37
- Android NDK r28c
- JDK 17
- WSL / Linux 构建环境，用于编译 FFmpeg 与 native 依赖
- 构建 FFmpeg 前，需要先准备 `deps/` 目录下的第三方编解码器源码

## 构建说明

### 1. 克隆仓库

```bash
git clone <repo-url>
cd ffmpeg-tools
```

### 2. 准备 FFmpeg 依赖

在 WSL / Linux 环境下执行：

```bash
bash scripts/clone-deps.sh
bash scripts/build-ffmpeg-android.sh
```

`scripts/build-ffmpeg-android.sh` 会将 `ffmpeg`、`ffprobe` 和运行所需的 `libc++_shared.so` 输出到 `Android-ffmpeg/`。打包 APK 前，需要确保这些产物已经复制到应用的 FFmpeg assets 目录，或由你的本地打包流程完成这一步。

### 3. 配置 Android SDK

在项目根目录创建 `local.properties`：

```properties
sdk.dir=C:\\Users\\<your-name>\\AppData\\Local\\Android\\Sdk
```

### 4. 配置 Release 签名

Release 签名信息从 `local.properties` 读取。

```properties
RELEASE_STORE_FILE=keystore/release.keystore
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=your-key-password
```

### 5. 构建

```bash
./gradlew :app:assembleRelease
```

APK 输出目录：
- Release: `app/build/outputs/apk/release/`

## 构建配置与许可证影响

| 配置 | 默认值 | 影响 |
| --- | --- | --- |
| `ENABLE_GPL` | `1` | 启用 x264、x265、Kvazaar 等 GPL 相关组件；公开分发默认 APK 时应按 GPL 兼容要求处理。 |
| `ENABLE_NONFREE` | `0` | 默认不启用 fdk-aac。 |
| `ENABLE_NONFREE=1` | 手动开启 | 会启用 fdk-aac 并触发 nonfree 组合；不建议公开分发该 FFmpeg 构建产物，仅建议本地或私用测试。 |

## 第三方项目引用

| 项目 | 用途 | 许可证 | 默认参与 APK | 链接 |
| --- | --- | --- | --- | --- |
| Shizuku API | 特权文件浏览与系统级操作桥接 | MIT | 是 | https://github.com/RikkaApps/Shizuku-API |
| Miuix Compose UI | 应用 UI 组件与主题 | Apache-2.0 | 是 | https://github.com/miuix-kotlin-multiplatform/miuix |
| FFmpeg | 媒体处理 CLI、编解码、封装、滤镜 | LGPL；启用 GPL 组件后为 GPL 范围 | 是 | https://ffmpeg.org/ |
| x264 | H.264 软件编码 | GPL | 是，`ENABLE_GPL=1` 时启用 | https://code.videolan.org/videolan/x264 |
| x265 | HEVC 软件编码 | GPL | 是，`ENABLE_GPL=1` 时启用 | https://bitbucket.org/multicoreware/x265_git |
| Kvazaar | HEVC 软件编码 | BSD | 是，脚本在 `ENABLE_GPL=1` 时启用 | https://github.com/ultravideo/kvazaar |
| libaom | AV1 编码/解码 | BSD | 是 | https://aomedia.googlesource.com/aom |
| SVT-AV1 | AV1 编码 | BSD | 是 | https://gitlab.com/AOMediaCodec/SVT-AV1 |
| dav1d | AV1 解码 | BSD | 是 | https://code.videolan.org/videolan/dav1d |
| LAME | MP3 编码 | LGPL | 是 | https://github.com/rbrito/lame |
| Opus | Opus 编码/解码 | BSD | 是 | https://gitlab.xiph.org/xiph/opus |
| libvpx | VP8/VP9 编码 | BSD | 是 | https://chromium.googlesource.com/webm/libvpx |
| fdk-aac | AAC 编码 | Fraunhofer FDK AAC license；FFmpeg 组合标记为 nonfree | 否，只有 `ENABLE_NONFREE=1` 时启用 | https://github.com/mstorsjo/fdk-aac |

`scripts/clone-deps.sh` 会拉取上述 native 依赖源码。

## 目录结构

```text
ffmpeg-tools/
├── app/                     # Android 应用源码
├── scripts/                 # FFmpeg、NDK 和依赖准备脚本
├── deps/                    # 第三方 native 源码，本地脚本拉取
├── Android-ffmpeg/          # FFmpeg 构建产物输出目录
├── build-arm64-v8a/         # native 构建输出目录
├── gradle/                  # Gradle wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```