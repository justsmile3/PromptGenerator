<div align="center">

<img src="docs/screenshots/icon.png" width="120" alt="SD Prompt Studio 图标">

# SD Prompt Studio

**Android 上的 Stable Diffusion 1.5 本地提示词工作台**

一键生成连贯的 danbooru 风格标签，再通过 LocalDream 在手机 NPU 上出图。

[![Release](https://img.shields.io/badge/release-v3.7-ffd26f?style=flat-square)](../../releases)
[![Build APK](https://img.shields.io/github/actions/workflow/status/OWNER/REPO/build.yml?style=flat-square&label=build)](../../actions)
[![Platform](https://img.shields.io/badge/platform-Android%205.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![APK Size](https://img.shields.io/badge/APK-157%20KB-8a7bff?style=flat-square)](../../releases)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
[![No Ads](https://img.shields.io/badge/ads-none-34d399?style=flat-square)](#-隐私)
[![Offline](https://img.shields.io/badge/works-offline-38bdf8?style=flat-square)](#-隐私)

[English](README.md) · [简体中文](README.zh-CN.md)

<img src="docs/screenshots/01-generate.png" width="250"> <img src="docs/screenshots/02-result.png" width="250"> <img src="docs/screenshots/04-chat.png" width="250">

</div>

---

## 目录

- [这是什么](#-这是什么)
- [功能总览](#-功能总览)
- [截图](#-截图)
- [安装](#-安装)
- [快速上手](#-快速上手)
- [LocalDream 联动](#-localdream-联动)
- [项目结构](#-项目结构)
- [从源码构建](#-从源码构建)
- [GitHub Actions](#-github-actions)
- [技术说明](#-技术说明)
- [常见问题](#-常见问题)
- [隐私](#-隐私)
- [免责声明](#-免责声明)
- [License](#-license)
- [参与贡献](#-参与贡献)

---

## 🎯 这是什么

**SD Prompt Studio** 是一个 **157 KB 的单文件 Android 应用**，解决三个痛点：

1. **不会写提示词** —— 内置 4000+ danbooru 标签，点一下就能出一整套
2. **提示词没有逻辑** —— 55 个「场景模板」保证一次只出现一个行为、一个地点、一组镜头，标签之间不会互相打架
3. **出图要来回切 App** —— 直接调用 LocalDream 的 HTTP 接口出图，还带批量、超分、画廊管理

它**不是**在线服务，**全部逻辑跑在你手机本地**，不需要注册、没有广告、没有追踪。

---

## ✨ 功能总览

### 🎲 提示词生成

| 功能 | 说明 |
|---|---|
| **智能推荐参数** | 根据提示词复杂度自动给出采样器 / 步数 / CFG / Clip skip |
| **Token 实时计数** | 严格控制在 CLIP 的 **77 token** 上限内，超出自动裁剪 |
| **单个重掷** | 点任意标签只重掷这一个，其余不动 |
| **标签锁定** | 长按标签锁定 🔒，之后怎么随机都不变 |
| **微调变体** | 保留种子 + 只换 2~4 个细节标签 → **同风格、不同细节** |
| **中英双语** | 580+ 条中文对照，支持英文 / 中文 / 双语三种输出 |
| **自然语言描述句** | 除了标签，还能生成一段可读的中文/英文描述 |
| **界面语言切换** | 整个 App 界面可在中文 / English 之间切换 |
| **配置备份** | 一键导出/导入全部配置为文本 |

### 🎬 场景逻辑引擎

普通随机生成会把各个词池独立乱抽，结果必然出现互相矛盾的组合，模型根本画不出来。

本项目改成 **场景驱动**：

```
① 先定一个连贯场景（55 选 1）
② 场景自带：核心行为 + 匹配的姿势/表情/地点/镜头/光照/人数
③ 只从场景允许的范围里补细节
④ 剩余槽位才放通用标签（发色/瞳色/画质/画风）
```

**55 个场景模板**，按内容分级：

| 档位 | 数量 |
|---|---|
| 第 1 档 · 平台安全向 | 5 |
| 第 2 档 · 暗示 | 5 |
| 第 3 档 · 成熟 | 5 |
| 第 4 档 · 成人 | 27 |
| 第 5 档 · 无限制 | 13 |
| **合计** | **55** |

**12 组互斥规则**保证一致性：

```
姿势组     31 项 → 一次只出现一个姿势
行为组     30 项 → 一次只保留一种主要行为
发色 / 瞳色 / 体型 / 着装状态 / 姿态 组 …
```

实测 660 次生成：核心标签丢失 **0** 次，互斥违反 **0** 次。

### 💬 AI 对话

内置 OpenAI 兼容的流式对话客户端，**10 个官方平台预设**：

| 平台 | 免费额度 | Base URL |
|---|---|---|
| **Groq** | ✅ 完全免费、极快 | `https://api.groq.com/openai/v1` |
| **Google Gemini** | ✅ 免费额度较大 | `https://generativelanguage.googleapis.com/v1beta/openai` |
| **OpenRouter** | ✅ 有 `:free` 模型 | `https://openrouter.ai/api/v1` |
| **智谱 GLM** | ✅ `glm-4-flash` 免费 | `https://open.bigmodel.cn/api/paas/v4` |
| **硅基流动** | ✅ 注册送额度 | `https://api.siliconflow.cn/v1` |
| **阿里百炼** | ✅ 有免费额度 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| DeepSeek / Moonshot / OpenAI / 自定义 | 💰 | — |

**杀手功能 —— 一键导入提示词：**

```
你：画一个雨夜电车里的和服少女，回头看向镜头
AI：1girl, solo, kimono, train interior, rain, night, neon lights, looking back...
    ↓ 点「↧ 导入提示词」
    ↓ 自动抽出英文标签（会剥掉 markdown 代码块和中文解释）
生成页：直接可用的提示词 ✅
```

> ⚠️ 本项目**不内置任何共享 API Key**。请使用你自己注册的 Key，界面内有直达官网申请按钮。

### 🖼️ LocalDream 联动出图

利用 [LocalDream](https://github.com/xororz/local-dream) 的本地 HTTP 接口（`127.0.0.1:8081`），把提示词直接送进手机 NPU 出图。

| 功能 | 说明 |
|---|---|
| **一键出图** | 提示词 + 采样器 / 步数 / CFG / 种子 直接提交 |
| **实时进度** | SSE 流式接收采样进度：`第 2/4 张 · 采样 12/22 步 · 18s` |
| **批量出图** | 一次 1 / 2 / 4 / 6 / 8 张，种子自动递增（+7919）保证不重复 |
| **4× 超分** | 调用 LocalDream 未公开的 `/upscale` 接口，512 → 2048 |
| **超分路径自动探测** | 自动尝试 10 个候选路径，找到能用的那个自动填入 |
| **完成提示音** | 可选系统提示音或任意音频文件，出图完成时播放 |

**连接稳定性处理**（都是实测踩出来的）：
- 全部请求 `Proxy.NO_PROXY` 绕过系统代理 —— 否则装了 VPN 时首次连 `127.0.0.1` 必超时
- 收到 SSE 终止事件后**先断开连接再通知前端** —— 否则下一张请求会被后端推理锁挡住
- 连接类错误自动重试 3 次

### 🖼️ 画廊管理

| 功能 | 说明 |
|---|---|
| **自动入库** | 出图后自动存入 App 私有画廊 |
| **多选批量** | 批量保存到相册 / 批量超分 / 批量删除 |
| **配方查看** | 点开大图可看完整提示词 + 全部参数 |
| **一键还原** | 「微调重掷」会还原那张图的提示词和种子，改几个细节重新出图 |
| **超分角标** | 已 4× 超分的图左上角标 `4×` |

### 🎨 双主题 UI

- **现代磨砂** —— 圆角 + 毛玻璃 + 渐变主色
- **原神风** —— 金边斜角 + 菱形装饰 + 衬线标题 + 金色渐变按钮（含配套 App 图标）

两者都支持自定义主色 / 辅色 / 背景色 / 模糊度 / 透明度。

### 🎮 其他

- **角色库** —— 四个米哈游作品共 **140 个角色**
- **12 套场景预设** —— 一键套用整套氛围（樱花、赛博朋克、水下…）
- **排除标签** —— 强制不让某些标签出现
- **额外反向词** —— 追加到自动反向词后面
- **底部 6 页导航** —— 生成 / 对话 / 设置 / 角色 / 画廊 / 更多

---

## 📸 截图

| 生成页 | 出图结果 | AI 对话 |
|---|---|---|
| ![](docs/screenshots/01-generate.png) | ![](docs/screenshots/02-result.png) | ![](docs/screenshots/04-chat.png) |

| 画廊 | 设置 | 角色库 |
|---|---|---|
| ![](docs/screenshots/03-gallery.png) | ![](docs/screenshots/05-settings.png) | ![](docs/screenshots/06-characters.png) |

<div align="center">
<img src="docs/screenshots/07-theme.png" width="260">
</div>

---

## 📲 安装

### 下载

从 [Releases](../../releases) 下载 `PromptGenerator.apk`，或从最新的 [Actions](../../actions) 构建产物里下载。

> 预编译的 APK 是 **debug 签名**，仅供个人侧载使用。

### 步骤

1. 在手机上打开 APK
2. 如果提示「禁止安装未知应用」→ 去设置里允许你的文件管理器安装应用
3. MIUI / HyperOS 可能弹「安全检测」→ 选**继续安装**

### 系统要求

| 项目 | 要求 |
|---|---|
| Android | 5.0 (API 21) 及以上 |
| 网络 | 仅 AI 对话需要；提示词生成完全离线 |
| 出图 | 需另装 [LocalDream](https://github.com/xororz/local-dream) |

### ⚠️ 升级注意

卸载会**清空画廊**（图片存在 App 私有目录 `/data/data/com.promptgen.app/files/gallery/`）。

要保留就先在画廊里多选 → 点「💾 保存」，图片会导出到公共相册 `Pictures/PromptGen/`。

---

## 🚀 快速上手

### 只要提示词（不需要出图）

```
1. 打开 App → 底部「生成」
2. 点「随机生成」→ 得到一整套提示词
3. 点「复制配方」→ 粘贴到任意 SD 工具（WebUI / ComfyUI / 手机端）
```

标签不满意？**点它**就重掷这一个。

想固定某个标签？**长按它**变 🔒，之后随机都不会变。

### 出一组「同风格不同细节」的图

这是最推荐的玩法：

```
出一张满意的图
      ↓
🎲 微调重掷        ← 保留种子，只换 2~4 个细节标签
      ↓
🎲 微调重掷 ×N     ← 反复点，得到一组风格统一、细节各异的图
```

**原理**：SD 里保持同一颗种子 + 微调提示词，构图和光影基本不变，只有细节不同。

微调引擎保证：
- ✅ 画质 / 画风 / 主体 / 角色 / **核心行为** / 场景 **永远不动**
- ✅ 只动发色、瞳色、表情、姿势、镜头、光照、身体细节
- ✅ 场景内标签只在**该场景允许的候选**里替换

实测 300 次微调：核心标签丢失 **0** 次，平均保留 **84.3%** 标签。

### 用 AI 帮你写提示词

```
1. 底部「对话」→ 右上角 ⚙️
2. 平台选「Groq（完全免费·极快）」→ 点「🌐 打开官网获取 Key」
3. 注册拿到 Key → 粘进「API Key」→ 点「🔌 测试连接」
4. 输入你的想法 → 发送
5. 点 AI 回复下方的「↧ 导入提示词」
```

---

## 🔗 LocalDream 联动

本 App 通过 HTTP 调用 LocalDream 的本地接口。**LocalDream 需要单独安装。**

### 用到的接口

| 接口 | 用途 |
|---|---|
| `POST /generate` | SSE 流式出图 |
| `POST /tokenize` | 连通性检测 |
| `POST /upscale` | 4× 超分（未公开接口） |

### 出图前提

1. **先在 LocalDream 里加载一个模型** —— 它的接口只在模型加载后才启动
2. **分辨率必须与 LocalDream 里选的一致**（一般是 512×512）
3. 超分还需要额外填超分模型路径

### 超分路径怎么填

LocalDream 的路径规则是**固定**的（从其源码里挖出来的）：

```
<模型目录>/models/<超分ID>/upscaler.bin
```

超分 ID 只有两个：

| ID | 模型 |
|---|---|
| `upscaler_anime` | Real-ESRGAN x4plus anime 6B |
| `upscaler_realistic` | 4x-UltraSharpV2 Lite |

默认模型目录在私有存储，所以完整路径是：

```
/data/data/io.github.xororz.localdream/files/models/upscaler_anime/upscaler.bin
```

**不用手打** —— 用「🔎 自动探测路径」按钮，它会自动试 10 个候选路径，找到能用的自动填入。

### ⚠️ 超分必须切换到 LocalDream 的超分页

LocalDream 的**生成后端和超分后端是互斥的两个进程**，都占 8081 端口。

```
① 本 App 生成图片（生成后端运行中）
② 切到 LocalDream → 打开「超分」页（后端切换为超分模式）
③ 切回本 App → 超分
```

---

## 📁 项目结构

```
sd-prompt-studio/
├── README.md
├── README.zh-CN.md
├── LICENSE
├── CONTRIBUTING.md
├── .gitignore
│
├── .github/
│   ├── workflows/build.yml             # CI：构建 + 发布 APK
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.yml
│       ├── feature_request.yml
│       └── config.yml
│
├── src/
│   └── index.html                      # 全部前端逻辑（单文件，184 KB）
│
├── android/
│   ├── AndroidManifest.xml
│   ├── build.sh                        # POSIX 构建脚本（CI / Linux / macOS）
│   ├── build.ps1                       # PowerShell 构建脚本（Windows）
│   ├── gen_icon.py                     # 程序化图标生成（Pillow + NumPy）
│   ├── res/mipmap-*/ic_launcher.png    # 5 种密度
│   └── src/com/promptgen/app/
│       └── MainActivity.java           # WebView 宿主 + 全部原生能力（52 KB）
│
├── docs/
│   └── screenshots/
│
└── releases/
    └── PromptGenerator-v3.7.apk
```

### 前端（`src/index.html`）

单文件应用 —— 无框架、无构建步骤、无 npm 依赖。打开就能跑。

| 模块 | 职责 |
|---|---|
| `P` | 30+ 个词池，4000+ 标签 |
| `CN` | 580+ 条中英对照 |
| `SCENARIOS` | 55 个场景模板 |
| `EXCLUSIVE` | 12 组互斥规则 |
| `generateTags()` | 场景驱动的标签生成 |
| `makeVariation()` | 变体引擎 |
| `computeRecommendation()` | 采样参数推荐 |
| `outString()` / `toSentence()` | 双语 + 自然语言输出 |
| `AI_PROVIDERS` | 10 个 LLM 平台预设 |

### 原生（`MainActivity.java`）

| 能力 | 为什么必须在原生层 |
|---|---|
| **HTTP / SSE** | WebView 从 `file://` 加载，跨域被 CORS 拦截 |
| **LocalDream 联动** | 同上，SSE 流式进度 |
| **AI 对话** | 同上，需解析 `data:` SSE 帧 |
| **超分** | 二进制 RGB 请求体 + JPEG 响应 |
| **画廊读写** | 私有存储、缩略图生成、MediaStore 导出 |
| **提示音** | `RingtoneManager` / `MediaPlayer` |
| **文件选择** | `ACTION_OPEN_DOCUMENT` |

---

## 🔨 从源码构建

本项目**不使用 Gradle**，用手工流水线打包，依赖只有 Android SDK、JDK 17 和（可选的）Python。

### 环境要求

| 工具 | 版本 |
|---|---|
| Android SDK Build-Tools | 34.0.0 |
| Android Platform | android-33 |
| JDK | 17 |
| Python | 3.8+ 含 Pillow、NumPy（仅生成图标用） |
| Bash *或* PowerShell | 运行构建脚本 |

### 构建（Linux / macOS）

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT

mkdir -p android/assets
cp src/index.html android/assets/index.html

bash android/build.sh
# → PromptGenerator.apk
```

### 构建（Windows）

```powershell
# 1. 改 android/build.ps1 顶部的路径
$SDK      = "C:\Users\你\AppData\Local\Android\Sdk"
$JDK      = "C:\path\to\jdk-17"
$KEYSTORE = "C:\Users\你\.android\debug.keystore"

# 2. 把前端拷进 assets
New-Item -ItemType Directory -Force android\assets | Out-Null
Copy-Item src\index.html android\assets\index.html

# 3. 重新生成图标（可选）
python android\gen_icon.py

# 4. 打包
Set-ExecutionPolicy -Scope Process Bypass -Force
.\android\build.ps1
```

### 流水线做的事

```
1. aapt2 compile   资源编译
2. aapt2 link      链接资源 + manifest + assets
3. javac           编译 Java（-encoding UTF-8 --release 8）
4. d8              dex 所有 .class
5. aapt add        把 classes.dex 塞进 APK
6. zipalign        4 字节对齐
7. apksigner       签名（v1 + v2 + v3）
```

### 踩过的坑

| 症状 | 原因 | 解决 |
|---|---|---|
| 装上就闪退 | `d8` 只 dex 了外层 `.class`，内部类丢了 | 递归枚举**所有** `.class` |
| `编码GBK的不可映射字符` | `javac` 默认用系统编码 | 加 `-encoding UTF-8` |
| 构建被无端中断 | `javac` 的过时 API 提示走 stderr，PowerShell 当致命错误 | `$PSNativeCommandUseErrorActionPreference = $false` |
| 修改后启动白屏 | 遍历词池时把对象当数组 | 加 `Array.isArray` 判断 |

---

## ⚙️ GitHub Actions

`.github/workflows/build.yml` 会在每次 push 和 PR 时构建签名 APK，推送 `v*` 标签时自动发布到 GitHub Release。

**它做什么：**

1. 检出代码
2. 配置 JDK 17 (Temurin) 和 Android SDK
3. 安装 `build-tools;34.0.0` 和 `platforms;android-33`
4. 若没有 keystore 则用 `keytool` 生成一个 debug keystore
5. 把 `src/index.html` 拷进 `android/assets/`
6. 执行 `android/build.sh`
7. 上传 APK 作为构建产物
8. 推送 `v*` 标签时发布到 Release

### 用自己的 keystore 签名（可选）

想产出 release 签名的 APK，在仓库 Settings → Secrets 里加这几个，工作流会自动识别：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 your.keystore` |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key 别名 |
| `KEY_PASSWORD` | key 密码 |

不配置的话，CI 会退回使用自动生成的 debug keystore。

### 触发一次发布

```bash
git tag v3.7
git push origin v3.7
```

APK 会出现在 **Releases** 页面。

---

## 🧠 技术说明

### 为什么用 WebView 而不是原生 UI

184 KB 的单文件前端承载了全部交互逻辑（55 个场景模板、4000+ 标签、变体引擎、双语输出…），用原生重写体积和开发成本都会翻好几倍。**最终 APK 只有 157 KB。**

### WebView 的坑与对策

| 问题 | 对策 |
|---|---|
| `file://` 跨域，`fetch` 被 CORS 拦截 | 所有网络请求放 Java 侧，用 `@JavascriptInterface` 回传 |
| `alert()` / `confirm()` 在 WebView 里无效 | 自实现 `appToast()` / `appConfirm()` + `WebChromeClient.onJsAlert` |
| `@JavascriptInterface` 方法不在 UI 线程 | 所有回调走 `runOnUiThread` |
| 回调 JS 时字符串含引号/换行会破坏语法 | 用 `JSONObject.quote()` 做转义 |

### 性能优化

| 问题 | 原因 | 解决 |
|---|---|---|
| 出图变慢 | 存图在主线程做「写盘 + 解码 + 缩略图 + 索引」 | 完全移到后台线程 |
| 批量出图卡顿 | SSE 连接未关闭就发起下一张，被后端推理锁挡住 | 先 `disconnect()` 再通知前端 |
| 首次连接必超时 | `HttpURLConnection` 默认走系统代理 | `openConnection(Proxy.NO_PROXY)` |

### 图标为什么是程序化生成的

`android/gen_icon.py` 用 Pillow + NumPy 画图，不是贴图：

- 径向辉光用 NumPy 算距离场混合
- 菱形用 polygon 四顶点绘制（可调扁率）
- 内外两层描边 + 10 层递减 alpha 模拟发光
- 文字用 `textbbox` 精确居中，8 方向偏移模拟外发光

好处是改配色/形状只需改参数重新生成，**永远是矢量级清晰**。

---

## ❓ 常见问题

<details>
<summary><b>装上就闪退？</b></summary>

先卸载旧版再装新版。如果还闪退，用 `adb logcat | grep -i promptgen` 抓日志提 Issue。
</details>

<details>
<summary><b>首次连接 LocalDream 总是 timeout？</b></summary>

v2.5 已修复（绕过系统代理 + 连接前先断开上一张的 SSE）。如果你在用旧版，请升级。
</details>

<details>
<summary><b>超分报 HTTP 500 "Failed to initialize upscaler model"？</b></summary>

说明路径指向的文件加载不了。最常见是路径写错。

正确格式：`<模型目录>/models/<超分ID>/upscaler.bin`

用「🔎 自动探测路径」按钮自动找。
</details>

<details>
<summary><b>文件管理器里看不到画廊图片？</b></summary>

正常现象。画廊存在 App 私有目录 `/data/data/com.promptgen.app/files/gallery/`，Android 10+ 的沙箱机制下任何文件管理器都看不到。

要让图片出现在相册，用「💾 保存」导出到 `Pictures/PromptGen/`。
</details>

<details>
<summary><b>AI 对话报 401 / 429？</b></summary>

- `401` → API Key 无效或没填对
- `403` → Key 无权限，或该模型对你不可用
- `404` → Base URL 或模型名不对
- `429` → 触发限流（免费额度常见），稍后再试

界面会把错误翻译成人话。
</details>

<details>
<summary><b>为什么没有内置免费 API Key？</b></summary>

因为网上流传的「免费 Key」是**盗用他人付费额度**，既不稳定（几小时失效）也不合法。本项目改为内置 10 个**官方平台预设**，其中 6 个有真正的免费额度，你注册一下用自己的 Key 就能长期稳定使用。
</details>

<details>
<summary><b>出图慢 / 生成慢？</b></summary>

- 先确认「每次生成张数」不是 8 张（那是 8 倍耗时）
- 进度条会显示单张耗时，8s Gen 3 上 22 步正常是 15~40 秒
- 如果单张就要 1 分钟以上，检查 LocalDream 的步数设置

</details>

<details>
<summary><b>支持 SDXL 吗？</b></summary>

不支持。本项目面向 **SD1.5**（512×512、CLIP 77 token 上限）。

另外注意：Snapdragon 8s Gen 3（SM8635）的 HTP 是**阉割版**（VTCM < 8MB），只能跑 `_min` 档的 QNN 模型，跑不了 SDXL。
</details>

---

## 🔒 隐私

| 项目 | 说明 |
|---|---|
| **无广告** | 没有任何广告 SDK |
| **无追踪** | 没有任何统计 / 埋点 / 崩溃上报 |
| **无账号** | 不需要注册登录 |
| **离线可用** | 提示词生成完全离线，不联网 |
| **API Key** | 只存在 App 私有存储 `localStorage`，不会上传到任何第三方 |
| **出图** | 只请求 `127.0.0.1:8081`（你自己的 LocalDream） |
| **AI 对话** | 只请求你自己配置的 API 地址 |

**唯一的联网行为**：AI 对话功能（需要你主动配置 API 才会用到）。

---

## ⚠️ 免责声明

- 本项目仅供**技术学习与个人使用**
- 生成的内容由使用者自行负责
- 请遵守你所在地区的法律法规
- 请勿用于生成任何违法内容
- 本项目与 LocalDream、Stability AI、danbooru 及各 LLM 平台均无关联

项目内置了内容分级系统，方便使用者按所在平台和地区的规范自行选择。使用者应自行遵守所发布平台的服务条款。

---

## 📄 License

[MIT](LICENSE)

第三方声明：

| 组件 | License |
|---|---|
| [LocalDream](https://github.com/xororz/local-dream) | CC BY-NC 4.0（**非商业**） |

> 本项目通过 HTTP 接口调用 LocalDream，**未包含也未修改其源码**。但请注意 LocalDream 本身是**非商业许可**。

---

## 🤝 参与贡献

欢迎贡献！请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

简要说明：

- 🐛 **Bug** → 用 [bug 模板](../../issues/new?template=bug_report.yml)
- 💡 **新功能** → 用 [feature 模板](../../issues/new?template=feature_request.yml)
- 🔧 **代码** → fork → 建分支 → 提 PR
- 🌍 **翻译** → `src/index.html` 里的 `CN` 字典很好扩展

---

<div align="center">

**如果这个项目对你有帮助，给个 ⭐ Star 吧！**

Made with ❤️ for the SD community

</div>
