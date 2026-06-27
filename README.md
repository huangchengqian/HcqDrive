# HcqDrive

> **M1 完整交付** — 安卓局域网云盘,服务端仅运行在安卓,客户端浏览器访问。

HcqDrive 是一款运行在安卓设备上的局域网个人云盘。开启后同一 WiFi 下的任何设备(电脑/手机/平板)通过浏览器即可访问手机里的照片、视频、文件。

**客户端零安装**:纯浏览器,扫码即用(或 `http://手机IP:8080`)。

---

## 当前阶段:M1 (完整功能)

M1 已交付完整的"启动 → 配对 → 浏览 → 传输"闭环:

### 功能清单

| 模块 | 功能 |
|---|---|
| **服务控制** | 一键启动/停止,前台服务常驻通知栏,显示配对码 + 访问地址 + 连接数 |
| **配对鉴权** | 6 位数字配对码(5 分钟过期),Token 会话保持,二维码扫码,Web API 鉴权 |
| **文件浏览** | 列目录、导航、搜索过滤、排序、网格/列表视图切换 |
| **文件操作** | 下载(Range 断点续传)、上传(单文件 + 分片)、ZIP 打包下载 |
| **文件管理** | 重命名、移动、复制、删除(回收站)、新建文件夹 |
| **媒体处理** | 缩略图生成(图片/视频)、EXIF 信息解析 |
| **Web UI** | Vue 3 响应式界面,配对输入、文件浏览、上传拖拽、右键菜单、对话框、暗色模式 |
| **系统** | 前台保活、权限请求链、错误统一处理、日志 |

### 产品文档
详见 [`产品文档.md`](产品文档.md)(v1.1)和 [`docs/api-contract.md`](docs/api-contract.md)(API 契约)。

---

## 技术栈

### 安卓端
- **语言**:Kotlin 2.0.21
- **HTTP**:Ktor Server 3.0.1 + CIO 引擎
- **UI**:Jetpack Compose + Material 3 (BOM 2024.10.01)
- **底层**:kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime
- **其他**:ZXing(二维码)、Apache Commons Compress(ZIP)、Coil(图片/可选)
- 无 DI 框架,无 Room,无 Java 代码

### Web 端
- Vue 3.5 + Composition API + `<script setup>`
- Vite 5 + TypeScript 5 + Tailwind CSS 3.4
- Pinia(状态)、Vue Router 4(路由)、Lucide Vue(图标)
- 响应式:手机 / 平板 / 桌面三端适配
- 完整暗色/亮色双主题

---

## 项目结构

```
HcqDrive/
├── app/                           # 安卓 App
│   ├── build.gradle.kts           # AGP + Kotlin + Compose + Ktor
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/web/            # Web UI 构建产物(需 npm run build 后复制)
│       ├── resources/web/         # Ktor 静态资源目录
│       ├── kotlin/com/hcqdrive/
│       │   ├── HcqDriveApp.kt     # Application + 通知 Channel
│       │   ├── MainActivity.kt    # Compose UI: 配对码 + QR + 启停
│       │   ├── auth/              # 鉴权模块
│       │   ├── fs/                # 文件服务模块
│       │   ├── media/             # 媒体处理(缩略图/EXIF)
│       │   ├── qr/                # 二维码生成
│       │   ├── server/            # Ktor 服务端
│       │   │   ├── HttpServer.kt  # 启动/中间件/路由装配
│       │   │   ├── WebUiHandler.kt# SPA 静态资源 + fallback
│       │   │   ├── middleware/    # Auth/CORS/Logging 插件
│       │   │   └── routes/        # Auth/Fs/File/Media/Routes
│       │   ├── service/           # ForegroundService(保活)
│       │   ├── transfer/          # 传输(ZIP打包/分片上传)
│       │   └── ui/                # QrActivity + Compose 主题
│       └── res/                   # 资源(图标/颜色/主题/配置)
├── web/                           # Web UI 源码(独立前端项目)
│   ├── src/
│   │   ├── api/                   # API 客户端(6 个模块文件)
│   │   ├── components/            # UI 组件(6 个 UI 基件 + 5 对话框)
│   │   ├── composables/           # 可复用逻辑(10 个)
│   │   ├── lib/                   # 工具函数
│   │   ├── stores/                # Pinia 状态(5 个)
│   │   ├── views/                 # 页面(4 个)
│   │   └── types/                 # TypeScript 类型定义
│   ├── package.json / vite.config / tailwind.config
│   └── README.md
├── docs/
│   └── api-contract.md            # API 契约文档(1717 行)
├── gradle/
│   ├── libs.versions.toml         # 版本目录(全依赖集中管理)
│   └── wrapper/gradle-wrapper.properties
├── 产品文档.md                     # 产品 PRD(v1.1)
├── build.gradle.kts               # 根构建配置
├── settings.gradle.kts            # 模块声明
└── README.md
```

**统计**:27 个 Kotlin 源文件(2632 行) + 63 个 Web 源文件(7363 行) + API 契约(1717 行)

---

## 快速开始

### 安卓端

用 Android Studio (Hedgehog 或更新) 打开项目根目录 `/Users/hcq/VibeCoding/HcqDrive/`:

1. AS 会自动下载 `gradle-wrapper.jar`(点弹窗确认)
2. Sync 完成后直接 Run 到真机(API 24+)
3. 启动 App → 点"启动服务" → 同意权限请求
4. 屏幕上显示 6 位配对码 + 二维码 + 访问地址 `http://192.168.x.x:8080`

### Web 端

```bash
cd web
npm install
npm run dev     # 开发: http://localhost:5173
npm run build   # 构建: 产物在 web/dist/
```

构建后复制到安卓 assets:
```bash
# 手动(或用 Gradle 任务):
cp -r web/dist/* app/src/main/assets/web/
```

### 验证流程

1. 安卓手机开服务 → 显示配对码(如 `123456`)
2. 同 WiFi 电脑浏览器访问 `http://手机IP:8080`
3. 输入配对码 → 进入文件管理器
4. 浏览 `/DCIM`、`/Pictures`、`/Download` 目录
5. 下载/上传文件
6. 通知栏显示实时配对码 + 连接数,可点击查看大二维码

---

## API 端点一览

完整文档见 [`docs/api-contract.md`](docs/api-contract.md)(1717 行)。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/pair` | 配对(Kotlin `AuthService`) |
| POST | `/api/auth/verify` | 验证 Token |
| POST | `/api/auth/revoke` | 撤销设备 |
| GET | `/api/fs/list` | 列目录(FileService) |
| GET | `/api/fs/stat` | 获取元数据 |
| POST | `/api/fs/mkdir` | 新建文件夹 |
| POST | `/api/fs/rename` | 重命名 |
| POST | `/api/fs/move` | 移动 |
| POST | `/api/fs/delete` | 删除(移回收站) |
| GET | `/api/file/raw` | 下载(Range 断点续传) |
| GET | `/api/file/thumb` | 缩略图 |
| GET | `/api/file/zip` | ZIP 打包下载 |
| POST | `/api/file/upload` | 单文件上传 |
| POST | `/api/file/upload/init` | 分片上传初始化 |
| POST | `/api/file/upload/chunk` | 上传分片 |
| POST | `/api/file/upload/complete` | 完成分片上传 |
| GET | `/api/media/exif` | EXIF 信息 |
| GET | `/api/status` | 服务状态 |

---

## License

开源(待定)。
