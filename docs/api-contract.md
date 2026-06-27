# HcqDrive API 契约 (v0.1.0 · M1)

> **唯一真相源 (Single Source of Truth)** — 本文件定义 HcqDrive 安卓端 (服务端) 与 Web 端 (客户端) 之间的全部 HTTP 接口契约。任何代码改动若与本文档冲突,以本文档为准;若需变更,请先更新本文档再改代码。
>
> **维护者**: @hcq
> **版本**: v0.1.0(M1)
> **生效日期**: 2026-06-20

---

## 目录

1. [引言](#1-引言)
2. [基础 URL 与启动](#2-基础-url-与启动)
3. [通用约定](#3-通用约定)
4. [路径约定](#4-路径约定)
5. [鉴权流程](#5-鉴权流程)
6. [错误码表](#6-错误码表)
7. [共享类型定义](#7-共享类型定义)
8. [鉴权端点](#8-鉴权端点)
9. [文件系统端点](#9-文件系统端点)
10. [文件传输端点](#10-文件传输端点)
11. [媒体端点](#11-媒体端点)
12. [系统端点](#12-系统端点)
13. [关键交互时序图](#13-关键交互时序图)
14. [限流与并发](#14-限流与并发)
15. [版本与变更记录](#15-版本与变更记录)

---

## 1. 引言

### 1.1 文档目的

本契约文档用于确保 HcqDrive 安卓端 (Ktor) 服务 与 Web 端 (Vue 3 + TypeScript) 客户端 在 M1 阶段对所有 HTTP 接口实现 **byte-for-byte 一致**。任何字段名、状态码、Header、错误码的差异都视作契约违反。

### 1.2 协议基础

| 维度 | 值 |
|---|---|
| 传输协议 | HTTP/1.1 |
| 数据格式 | JSON(API 响应) / `multipart/form-data`(上传) / `application/octet-stream`(二进制下载) |
| 字符编码 | 全部 UTF-8 |
| 时间格式 | Unix 时间戳(毫秒,UTC) — 即 `number` 字段,值形如 `1718870400000` |
| 路径分隔符 | 统一使用 `/`,**绝不**使用 OS 原生分隔符(`\` 或 `\` 双反斜杠转义) |
| 大小写 | JSON 字段名一律 `camelCase`;HTTP Header 名一律按 RFC 7231 标准大小写(如 `Content-Type`,但 fetch/OkHttp 通常不敏感) |
| Content-Type 默认 | 请求:`application/json; charset=utf-8`;响应:`application/json; charset=utf-8`(二进制接口除外) |

### 1.3 安全响应头(所有响应强制携带)

| Header | 值 | 说明 |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | 防止 MIME 嗅探 |
| `Referrer-Policy` | `no-referrer` | 防 Referer 泄露 |
| `Cache-Control` | `no-store` | 默认值;静态资源(缩略图)可改为 `public, max-age=3600` |

### 1.4 范围

**M1(本版本)覆盖范围**:

- ✅ 配对码 + Bearer Token 鉴权(不含 cookie 模式)
- ✅ 文件浏览/搜索/增删改(单层)
- ✅ 文件上传(简单 multipart + 分片)/下载(Range)/ZIP 打包
- ✅ 缩略图生成、EXIF 读取
- ✅ 媒体按时间分组(`/api/media/photos`)
- ✅ 服务状态与健康检查

**不在 M1 范围(预留字段或占位)**:

- ⏳ Cookie 鉴权(M5 引入,字段已预留,见 5.3)
- ⏳ 文件复制(POST `/api/fs/copy`)、直链分享 — 服务端先实现,客户端 M4 才用
- ⏳ WebSocket 推送上传进度(M1 走轮询)

---

## 2. 基础 URL 与启动

### 2.1 基础 URL 格式

```
http://<手机IP>:<端口>
```

- **默认端口**: `8080`
- **IP 来源**: 服务启动时检测所有 `NetworkInterface` 的 IPv4 地址,优先返回非隧道(`wlan0` / `eth0`)地址
- **显示位置**:
  1. 安卓前台服务通知栏(常驻)
  2. 安卓 App 首页
  3. 配对二维码内容(JSON,见 2.2)

### 2.2 配对二维码内容

二维码编码以下 JSON 字符串(Web 端扫码后解析):

```json
{
  "v": 1,
  "url": "http://192.168.1.105:8080",
  "code": "483921",
  "exp": 1718871300
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `v` | integer | 二维码 schema 版本,当前 `1` |
| `url` | string | 完整服务地址(含 `http://` 头) |
| `code` | string | 6 位数字配对码 |
| `exp` | integer | 配对码过期 Unix 时间戳(秒) |

> 注:二维码内**不**包含 token;配对码是临时凭证,扫码后 Web 端必须再调 `/api/auth/pair` 换取 token。

---

## 3. 通用约定

### 3.1 请求 ID(Tracing)

所有请求响应携带唯一 `X-Request-Id`:

- 客户端可选择发送 `X-Request-Id: <uuid>`(Web 端用 `crypto.randomUUID()`)
- 服务端必须 echo 该值;若客户端未提供,服务端生成一个(返回在响应头)
- Web 端报错时连同 `X-Request-Id` 一起展示,方便对照安卓日志

### 3.2 分页

M1 范围不引入分页:

- `/api/fs/list` 一次性返回整目录,客户端做虚拟滚动
- `/api/fs/search` 一次性返回最多 **500 条**,超过截断并返回 `truncated: true`
- 未来版本会加 `?cursor=...&limit=...`,当前契约不预留字段

### 3.3 布尔值

JSON 中布尔字段一律 native `true` / `false`,**不**用 `0/1`、`"yes"/"no"`。

### 3.4 空值与缺失字段

- 字段值为空(无值) → JSON 输出 `null`,**不**省略字段
- Web 端 TypeScript 类型必须显式声明 `T | null`,**不**用 `?`(避免"字段存在性"歧义)

唯一例外:`FileEntry.thumbnailUrl` 在客户端懒得接时可省略(见 7.2 字段表)。

### 3.5 数字精度

- 文件大小(`size`): `number`(单位字节),64 位整数,JS 安全范围 `< 2^53`
- 时间戳: `number`,毫秒
- 分片上传 offset/length: `number`,字节

---

## 4. 路径约定

### 4.1 路径模型

服务端内部维护一个 **共享根目录**(用户在 App 内配置的任意目录,默认指向 `/sdcard`)。所有 Web 端发来的 `path` 都是 **相对于共享根的虚拟路径**,**绝不**出现绝对磁盘路径或 `C:\`、`/sdcard/`、`..`。

```
虚拟路径 ─→ 服务端 ─→ 实际磁盘路径
/                  /sdcard
/Download          /sdcard/Download
/Download/电影     /sdcard/Download/电影
/.trash            /sdcard/.hcqdrive_trash
```

### 4.2 公共别名(可直接用作 `path` 起点)

这些别名映射到 Android MediaStore 公开目录,**只读**(M1,后续版本允许用户在设置里切换为可写):

| 别名 | 映射到 | 是否只读 | 说明 |
|---|---|---|---|
| `/` | 用户配置根目录 | ✅ 可写 | Web 端首页默认 |
| `/DCIM` | `MediaStore DCIM/` | ✅ 只读(M1) | 相机照片 |
| `/Pictures` | `MediaStore Pictures/` | ✅ 只读(M1) | 系统图库 |
| `/Movies` | `MediaStore Movies/` | ✅ 只读(M1) | 视频 |
| `/Videos` | `MediaStore Movies/` 别名 | ✅ 只读(M1) | 部分设备路径 |
| `/Music` | `MediaStore Music/` | ✅ 只读(M1) | 音频 |
| `/Audio` | `MediaStore Audio/` 别名 | ✅ 只读(M1) | 部分设备路径 |
| `/Download` | `MediaStore Download/` | ✅ 只读(M1) | 下载目录 |

### 4.3 内部别名(以 `.` 开头,用户不可见,但 API 可访问)

| 别名 | 映射到 | 说明 |
|---|---|---|
| `/.trash` | `<root>/.hcqdrive_trash/` | 回收站,M1 实现移到回收站而非物理删除;Web 端不直接展示,但 `/api/fs/list?path=/.trash` 可列出 |

### 4.4 路径合法性校验(服务端强制)

服务端在接收每个 `path` 时 **必须** 校验以下规则,违规返回 `400 FS_PATH_TRAVERSAL`:

1. 必须以 `/` 开头
2. 不得包含 `..`(任何位置)
3. 不得包含反斜杠 `\` 或 null 字符 `\0`
4. 单段长度 `< 255` 字节(UTF-8)
5. 解码后的实际磁盘路径必须在共享根目录内(`startsWith(root + "/")` 或等于 root)
6. 名称不得包含 `/`、`\`、控制字符(< 0x20)

### 4.5 路径在 URL 中的编码

`path` 作为 query 参数时使用 **percent-encoding**,斜杠 `/` 也编码为 `%2F`(避免有些代理把 query 里的 `/` 当路径分隔符)。

```
GET /api/fs/list?path=%2FDCIM%2FCamera
GET /api/fs/stat?path=%2FMovies%2F%E7%92%A7%E5%85%89%E7%9A%84%E8%A2%AB%E5%AD%90.mp4
```

---

## 5. 鉴权流程

### 5.1 三种鉴权方式(优先级从高到低)

服务端按以下顺序识别一次请求的凭证:

1. **`Authorization: Bearer <token>`**(Header)
2. **`Cookie: auth_token=<token>`**(仅 M5 引入,M1 不实现)
3. **`?token=<token>`**(Query 参数,M1 仅用于 `/api/file/raw` 等无法设置 Header 的 `<img>`/`<video>` 场景)

**优先级逻辑**:

```kotlin
// 服务端伪代码
val authHeader = call.request.headers["Authorization"]
val cookie = call.request.cookies["auth_token"]
val queryToken = call.request.queryParameters["token"]

val token = when {
    authHeader?.startsWith("Bearer ") == true -> authHeader.removePrefix("Bearer ").trim()
    !cookie.isNullOrBlank() -> cookie
    !queryToken.isNullOrBlank() -> queryToken
    else -> null
}
```

### 5.2 配对流程(M1 标准流程)

```
┌──────────┐                                ┌──────────────┐
│ 安卓 App  │                                │  Web 浏览器   │
│  (server) │                                │  (client)    │
└────┬──────┘                                └──────┬───────┘
     │ 1. 启动服务,生成 6 位配对码(有效期 5 分钟)   │
     │    在通知栏显示 + 提供 GET /api/auth/pair-code│
     │                                              │
     │                                              │ 2. 用户扫码 / 手动输入 URL + 配对码
     │                                              │
     │<─────── 3. POST /api/auth/pair { code } ─────│
     │        Authorization: Bearer <none>          │
     │                                              │
     │ 4. 校验配对码:                                │
     │    - 存在 & 未过期 & 未用过(防重放)           │
     │    - 成功 → 生成 32 字节 token,绑定 deviceId │
     │    - 持久化 { token → deviceId, expiresAt }  │
     │                                              │
     │─────────── 5. 200 { token, deviceName, ... } ─>│
     │                                              │ 6. localStorage.setItem('hcqdrive:token', ...)
     │                                              │
     │                                              │ 7. 后续请求:
     │<────── 8. GET /api/status ──────────────────│
     │        Authorization: Bearer <token>         │
```

### 5.3 Token 规格(M1)

| 属性 | 值 |
|---|---|
| 长度 | 32 字节随机数据 |
| 编码 | URL-safe Base64(无 padding)→ 43 字符 |
| 示例 | `kZ3vN8mQ2pL5xT7wY9bA4cF6hJ1sD0eR8iU` |
| 有效期 | **30 天** |
| 存储(Web 端) | `localStorage['hcqdrive:token']`(M5 改 HttpOnly Cookie) |
| 存储(安卓) | Room DB `tokens` 表,字段 `(token, deviceName, pairedAt, expiresAt, revoked)` |
| 撤销 | 调 `POST /api/auth/revoke`,服务端从 DB 删除并返回白名单剩余数量 |
| 同设备多 Token | 允许(用户换浏览器/清缓存);撤销只删当前 token |

### 5.4 配对码规格

| 属性 | 值 |
|---|---|
| 长度 | 6 位数字 |
| 字符集 | `[0-9]` |
| 有效期 | 5 分钟(300 秒) |
| 重放防护 | **单次使用**:服务端校验成功后立刻标记 `used=true`,即使在 5 分钟内再次提交也返回 `AUTH_CODE_USED` |
| 同 IP 失败上限 | 5 次/分钟 → 返回 `429 AUTH_RATE_LIMITED` |
| 重新生成 | 自动过期或用户在 App 内手动点"重新生成"按钮 |

### 5.5 公共端点(无需鉴权)

- `GET /api/health`
- `GET /api/status`(**基础信息**,不含设备特定数据;配对后再次 GET 返回完整 `deviceName` 等)
- `POST /api/auth/pair`
- `POST /api/auth/verify`(verify 也无需鉴权,用于客户端检查本地 token 是否还有效)
- `POST /api/auth/revoke`(撤销时只需传 token,无需其他鉴权;通过 token 自身识别身份)

> 上述外的所有 `/api/*` 端点都要求 `Authorization: Bearer <token>`,缺失或失效返回 `401 AUTH_REQUIRED` / `401 AUTH_INVALID`。

---

## 6. 错误码表

### 6.1 统一错误响应结构

所有 `4xx` / `5xx` 响应 body 必须为以下 JSON(Content-Type `application/json`):

```typescript
interface ApiError {
  code: string        // 见下方枚举,大写 + 下划线
  message: string     // 人类可读,英文;M5 增加 i18n
  requestId?: string  // 对应 X-Request-Id 头,方便排查
  details?: unknown   // 可选,额外结构化信息,如验证失败字段
  status: number      // HTTP 状态码(冗余,便于客户端在 body 内直接读取)
}
```

```kotlin
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val details: JsonElement? = null,
    val status: Int,
)
```

### 6.2 错误码枚举

#### 6.2.1 鉴权类(401 / 403)

| `code` | HTTP | 说明 | 客户端处理建议 |
|---|---|---|---|
| `AUTH_REQUIRED` | 401 | 缺少 `Authorization` Header 或 token 为空 | 跳转到配对页 |
| `AUTH_INVALID` | 401 | token 格式错误 / 不存在 / 已过期 / 已撤销 | 清 localStorage,跳转配对页 |
| `AUTH_CODE_INVALID` | 400 | 配对码格式不对(非 6 位数字) | 提示用户重新输入 |
| `AUTH_CODE_EXPIRED` | 400 | 配对码过期(>5 分钟) | 提示用户刷新二维码 |
| `AUTH_CODE_USED` | 400 | 配对码已被使用过 | 同上 |
| `AUTH_RATE_LIMITED` | 429 | 同 IP 1 分钟内失败 5 次 | 提示"请稍后再试",60 秒后重试 |

#### 6.2.2 路径 / 文件系统类(400 / 403 / 404 / 409)

| `code` | HTTP | 说明 |
|---|---|---|
| `FS_PATH_INVALID` | 400 | 路径格式非法(不以 `/` 开头、含 null 等) |
| `FS_PATH_TRAVERSAL` | 400 | 路径含 `..` 或解析后越出共享根目录 |
| `FS_NOT_FOUND` | 404 | 文件 / 目录不存在 |
| `FS_ALREADY_EXISTS` | 409 | `mkdir` / `rename` / `move` 目标已存在 |
| `FS_NOT_EMPTY` | 409 | 删除非空目录(除非带 `recursive=true`) |
| `FS_READ_ONLY` | 403 | 目标目录是只读公共别名,不允许写 |
| `FS_PERMISSION_DENIED` | 403 | Android SAF / 权限不足 |
| `FS_NAME_INVALID` | 400 | 名称含 `/` `\` 控制字符或超长(>255) |
| `FS_IS_DIRECTORY` | 400 | 对目录调用了仅文件可用的操作(如 `stat` 后 raw 下载) |
| `FS_IS_FILE` | 400 | 对文件调用了仅目录可用的操作(如 `list`) |

#### 6.2.3 上传类(400 / 409 / 410 / 413)

| `code` | HTTP | 说明 |
|---|---|---|
| `UPLOAD_NOT_INIT` | 400 | 直接调 `chunk` 而没有先 `init` |
| `UPLOAD_SESSION_NOT_FOUND` | 404 | `uploadId` 不存在 |
| `UPLOAD_EXPIRED` | 410 | 上传会话过期(超过 24 小时未 complete) |
| `UPLOAD_CHUNK_OUT_OF_RANGE` | 400 | `offset + chunkSize > fileSize` |
| `UPLOAD_CHUNK_MISMATCH` | 409 | 分片 hash 不匹配(M1 暂不校验 hash,但字段已预留) |
| `UPLOAD_TOO_LARGE` | 413 | 单文件超过服务端限制(M1 默认 10 GB) |
| `UPLOAD_CONFLICT` | 409 | 目标位置已存在同名且客户端策略为 `fail` |
| `UPLOAD_CANCELLED` | 410 | 上传已被主动取消 |

#### 6.2.4 媒体类(400 / 415 / 422)

| `code` | HTTP | 说明 |
|---|---|---|
| `MEDIA_UNSUPPORTED` | 415 | 文件不是图片 / 视频,无法生成缩略图或 EXIF |
| `MEDIA_DECODE_ERROR` | 422 | 文件存在但解码失败(损坏 / 编码异常) |
| `MEDIA_THUMB_FAIL` | 500 | 缩略图生成系统错误 |

#### 6.2.5 请求体类(400)

| `code` | HTTP | 说明 |
|---|---|---|
| `BAD_REQUEST` | 400 | JSON 解析失败 / 缺少必填字段 / 字段类型错误 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` 不被该端点接受 |

#### 6.2.6 服务类(500 / 503)

| `code` | HTTP | 说明 |
|---|---|---|
| `INTERNAL` | 500 | 未捕获的服务器错误,堆栈已记日志 |
| `SERVICE_UNAVAILABLE` | 503 | 服务正在关闭 / 资源耗尽 |
| `NOT_IMPLEMENTED` | 501 | 端点已声明但当前 M1 未实现(用于渐进式上线) |

### 6.3 错误响应示例

```json
{
  "code": "FS_NOT_FOUND",
  "message": "File not found: /DCIM/missing.jpg",
  "requestId": "8f4d2c9a-1e3b-4a5d-9c8e-2f7b6a5d4c3b",
  "status": 404
}
```

带 `details` 的(用于批量校验):

```json
{
  "code": "BAD_REQUEST",
  "message": "Invalid request body",
  "requestId": "...",
  "details": {
    "field": "path",
    "reason": "must not contain '..'",
    "value": "/DCIM/../etc"
  },
  "status": 400
}
```

---

## 7. 共享类型定义

### 7.1 `EntryKind` — 节点类型枚举

```typescript
type EntryKind = 'file' | 'directory'
```

```kotlin
@Serializable
enum class EntryKind {
    @SerialName("file") FILE,
    @SerialName("directory") DIRECTORY,
}
```

### 7.2 `FileEntry` — 目录列表 / stat 单项

```typescript
interface FileEntry {
  id: string                    // 唯一稳定 ID,跨刷新不变;路径的 SHA-1(20 字节 hex)
  name: string                  // 仅文件名(不含父路径),如 "IMG_0001.jpg"
  path: string                  // 完整虚拟路径,以 "/" 开头,如 "/DCIM/Camera/IMG_0001.jpg"
  kind: EntryKind               // "file" | "directory"
  size: number                  // 字节;目录固定 0
  mime: string | null           // 目录为 null;文件由服务端根据扩展名嗅探,失败 null
  modifiedAt: number            // Unix ms
  createdAt: number             // Unix ms;文件系统不支持时与 modifiedAt 相同
  accessedAt: number            // Unix ms;同上
  hidden: boolean               // 文件名以 "." 开头 → true
  readable: boolean             // 服务端可读(Web 可下)
  writable: boolean             // 服务端可写(Web 可改 / 删)
  thumbnailUrl: string | null   // 仅图片 / 视频有,格式 "/api/file/thumb?path=...&size=256"
                                // 其它类型(包括 PDF) 为 null
  childCount: number | null     // 仅目录有意义,文件为 null;缓存目录可 -1 表示未知
}
```

```kotlin
@Serializable
data class FileEntry(
    val id: String,
    val name: String,
    val path: String,
    val kind: EntryKind,
    val size: Long,
    val mime: String?,
    val modifiedAt: Long,
    val createdAt: Long,
    val accessedAt: Long,
    val hidden: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val thumbnailUrl: String?,
    val childCount: Long?,
)
```

#### 7.2.1 字段详解

| 字段 | 类型 | 可选性 | 说明 |
|---|---|---|---|
| `id` | string | 必填 | 路径的 SHA-1 hex(40 字符),保证同路径跨请求稳定 |
| `name` | string | 必填 | 仅 basename,UTF-8,**不**做 percent-encoding |
| `path` | string | 必填 | 完整虚拟路径,**已** percent-decoded(客户端拿到即可直接展示) |
| `kind` | enum | 必填 | `"file"` 或 `"directory"`,**不**用 `"dir"` 或 `"folder"` |
| `size` | number | 必填 | 字节;目录固定 `0` |
| `mime` | string\|null | 必填 | 文件由服务端嗅探;**不**信任客户端传入的 Content-Type |
| `modifiedAt` | number | 必填 | Unix ms;文件系统支持时为 mtime,否则为 ctime |
| `createdAt` | number | 必填 | 同上语义 |
| `accessedAt` | number | 必填 | 同上语义 |
| `hidden` | boolean | 必填 | 安卓无 dotfile 概念,服务端按文件名 `"."` 开头判定 |
| `readable` | boolean | 必填 | 公共别名目录为 `true`(M1 实际不可读,因为 MediaStore 限制 — 服务端需返回 false 并给 403) |
| `writable` | boolean | 必填 | 公共别名固定 `false`;用户配置根目录的子目录按 SAF 授权 |
| `thumbnailUrl` | string\|null | 必填 | 图片 / 视频非 null;其它 null |
| `childCount` | number\|null | 必填 | 目录为直接子项数(不含递归);`-1` 表示"目录过大,未统计";文件为 `null` |

### 7.3 `ApiError` — 错误响应

见 §6.1。

### 7.4 `StatusResponse` — 服务状态

```typescript
interface StatusResponse {
  app: string                   // 固定 "HcqDrive"
  version: string               // 服务端版本,语义化,例 "0.1.0"
  status: "running" | "stopping" | "starting"
  uptime: number                // 秒,自服务启动起
  connections: number           // 当前活跃 HTTP 连接数(快照)
  deviceName: string            // 安卓设备名,如 "Pixel 7 - hcq"
  paired: boolean               // 当前请求的 token 是否有效(true)/未配对(false)
  serverTime: number            // 服务端当前 Unix ms,方便客户端校时
  rootAlias: string[]           // 公共别名列表,如 ["/", "/DCIM", "/Pictures", ...]
  uploadEnabled: boolean        // 服务端是否允许上传(用户配置)
  deleteEnabled: boolean        // 服务端是否允许删除(用户配置)
  showHidden: boolean           // 服务端是否展示隐藏文件
  maxUploadSize: number         // 单文件上传字节上限(M1 默认 10737418240 = 10 GB)
  maxConnections: number        // 最大并发连接(M1 默认 10)
  apiVersion: string            // 本契约文档版本,例 "0.1.0";客户端可拿来比对
}
```

```kotlin
@Serializable
data class StatusResponse(
    val app: String,
    val version: String,
    val status: String,                  // "running" | "stopping" | "starting"
    val uptime: Long,
    val connections: Int,
    val deviceName: String,
    val paired: Boolean,
    val serverTime: Long,
    val rootAlias: List<String>,
    val uploadEnabled: Boolean,
    val deleteEnabled: Boolean,
    val showHidden: Boolean,
    val maxUploadSize: Long,
    val maxConnections: Int,
    val apiVersion: String,
)
```

> **M1 兼容说明**: 截至 M1 启动版本,服务端实际只返回 `{app, version, status, uptime, connections}` 5 个字段(见 `HttpServer.kt`)。Web 端 TypeScript 已定义为 `{uptime, version, connections, deviceName, paired}`。**两端字段集不一致**。本契约定义的完整字段为 M1 最终形态,服务端需在 M1 第一个可用版本补齐;若客户端在补齐前调用,缺失字段按 `null`/默认值处理(详细迁移步骤见 §15.2)。

### 7.5 `HealthResponse` — 健康检查

```typescript
interface HealthResponse {
  ok: true                      // 恒为 true;非 true 即视为 5xx 错误
  uptime: number                // 秒
}
```

```kotlin
@Serializable
data class HealthResponse(
    val ok: Boolean,             // 恒 true
    val uptime: Long,
)
```

### 7.6 `UploadInitResponse` — 分片上传初始化

```typescript
interface UploadInitResponse {
  uploadId: string              // 服务端生成的 UUID,后续 chunk/complete 用
  chunkSize: number             // 建议分片大小,服务端可基于磁盘和网络动态调整;M1 固定 8 MiB
  expiresAt: number             // 上传会话过期 Unix ms(M1 默认 +24h)
  received: number[]            // 已接收的分片 offset 列表(断点续传用)
}
```

```kotlin
@Serializable
data class UploadInitResponse(
    val uploadId: String,
    val chunkSize: Long,
    val expiresAt: Long,
    val received: List<Long>,
)
```

### 7.7 `UploadCompleteResponse` — 上传完成

```typescript
interface UploadCompleteResponse {
  path: string                  // 落盘后完整路径
  size: number                  // 实际落盘字节数
  mime: string | null
  modifiedAt: number
}
```

```kotlin
@Serializable
data class UploadCompleteResponse(
    val path: String,
    val size: Long,
    val mime: String?,
    val modifiedAt: Long,
)
```

### 7.8 `ChunkAck` — 分片确认

```typescript
interface ChunkAck {
  uploadId: string
  offset: number                // 本片起始字节
  received: number              // 截至当前累计字节
  complete: boolean             // 是否已收齐全部(可选,服务端可在最后一片返回 true)
}
```

```kotlin
@Serializable
data class ChunkAck(
    val uploadId: String,
    val offset: Long,
    val received: Long,
    val complete: Boolean,
)
```

### 7.9 `ExifData` — EXIF 元数据

```typescript
interface ExifData {
  cameraMake: string | null     // "Canon"
  cameraModel: string | null    // "EOS R5"
  lens: string | null
  takenAt: number | null        // Unix ms;原始 DateTimeOriginal
  iso: number | null
  fNumber: number | null        // 光圈 F 值,如 1.8
  exposureTime: string | null   // 快门,字符串,例 "1/200"
  focalLength: number | null    // mm
  flash: boolean | null
  width: number | null          // 像素
  height: number | null
  gpsLat: number | null         // 十进制度
  gpsLng: number | null
  gpsAltitude: number | null    // 米
  orientation: number | null    // 1..8,EXIF orientation
}
```

```kotlin
@Serializable
data class ExifData(
    val cameraMake: String?,
    val cameraModel: String?,
    val lens: String?,
    val takenAt: Long?,
    val iso: Int?,
    val fNumber: Double?,
    val exposureTime: String?,
    val focalLength: Double?,
    val flash: Boolean?,
    val width: Int?,
    val height: Int?,
    val gpsLat: Double?,
    val gpsLng: Double?,
    val gpsAltitude: Double?,
    val orientation: Int?,
)
```

### 7.10 `PhotoGroup` — 媒体库时间分组

```typescript
interface PhotoGroup {
  year: number                  // 2024
  month: number                 // 1..12;0 表示"仅按年聚合"
  count: number                 // 该组内的媒体数
  cover: string                 // 该组内最新一张的 FileEntry.path(客户端可拼 /api/file/thumb)
}

interface PhotosResponse {
  total: number
  truncated: boolean            // 是否超过 5000 截断
  groups: PhotoGroup[]
}
```

```kotlin
@Serializable
data class PhotoGroup(
    val year: Int,
    val month: Int,
    val count: Int,
    val cover: String,
)

@Serializable
data class PhotosResponse(
    val total: Int,
    val truncated: Boolean,
    val groups: List<PhotoGroup>,
)
```

### 7.11 `SearchResponse` — 搜索结果

```typescript
interface SearchResponse {
  query: string
  total: number                 // 命中数
  truncated: boolean            // 超过 500 条时为 true
  entries: FileEntry[]          // 最多 500 项
}
```

```kotlin
@Serializable
data class SearchResponse(
    val query: String,
    val total: Int,
    val truncated: Boolean,
    val entries: List<FileEntry>,
)
```

### 7.12 `ListResponse` — 目录列表

```typescript
interface ListResponse {
  path: string                  // 当前目录虚拟路径
  parent: string | null         // 父目录路径;根目录时为 null
  entries: FileEntry[]
  totalCount: number            // entries.length;冗余字段,客户端快速读取
}
```

```kotlin
@Serializable
data class ListResponse(
    val path: String,
    val parent: String?,
    val entries: List<FileEntry>,
    val totalCount: Int,
)
```

---

## 8. 鉴权端点

### 8.1 `POST /api/auth/pair`

- **Auth**: Public
- **Description**: 用 6 位配对码换取长期 token
- **Request**:
  - Headers: `Content-Type: application/json`
  - Body:
    ```json
    { "code": "483921" }
    ```
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface PairResponse {
      token: string              // 43 字符,见 §5.3
      deviceName: string         // 服务端分配的设备名,如 "Web-Chrome (Linux)"
      deviceId: string           // UUID,服务端生成,标识本设备;后续撤销按此聚合
      expiresAt: number          // Unix ms,token 过期时间
      pairedAt: number           // Unix ms
      apiVersion: string         // "0.1.0"
    }
    ```
- **Errors**:
  - `400 AUTH_CODE_INVALID` — 配对码非 6 位数字
  - `400 AUTH_CODE_EXPIRED` — 超过 5 分钟
  - `400 AUTH_CODE_USED` — 已被使用
  - `429 AUTH_RATE_LIMITED` — 1 分钟失败 ≥5 次
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/auth/pair \
    -H 'Content-Type: application/json' \
    -d '{"code":"483921"}'
  ```

### 8.2 `POST /api/auth/verify`

- **Auth**: Public(传 token 是为了验证 token 自身)
- **Description**: 检查 token 是否有效;**不**刷新有效期
- **Request**:
  - Headers: `Authorization: Bearer <token>`(必填,否则 `AUTH_REQUIRED`)
  - Body: 空
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface VerifyResponse {
      valid: true                // 恒 true,无效走 401 错误分支
      deviceName: string
      deviceId: string
      expiresAt: number
      apiVersion: string
    }
    ```
- **Errors**:
  - `401 AUTH_REQUIRED` / `401 AUTH_INVALID`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/auth/verify \
    -H 'Authorization: Bearer kZ3vN8mQ2pL5xT7wY9bA4cF6hJ1sD0eR8iU'
  ```

### 8.3 `POST /api/auth/revoke`

- **Auth**: Bearer Token
- **Description**: 撤销当前 token;同时返回白名单剩余设备数(便于 Web 端展示)
- **Request**:
  - Headers: `Authorization: Bearer <token>`
  - Body: 空
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface RevokeResponse {
      revoked: true
      remainingDevices: number   // 撤销后白名单剩余设备数
    }
    ```
- **Errors**:
  - `401 AUTH_INVALID`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/auth/revoke \
    -H 'Authorization: Bearer kZ3vN8mQ2pL5xT7wY9bA4cF6hJ1sD0eR8iU'
  ```

### 8.4 `GET /api/auth/pair-code`

- **Auth**: **本机 Auth** — 仅安卓 App 内部使用,不走 HTTP 鉴权;若通过 HTTP 调用必须带 ServiceToken(预共享密钥),见下
- **Description**: 获取当前有效配对码 + 过期时间 + 二维码 JSON
- **Request**:
  - Headers: `X-Service-Token: <pre-shared secret>`(M1 写死常量,见下)
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface PairCodeResponse {
      code: string               // 6 位数字
      expiresAt: number          // Unix s
      remainingSeconds: number   // 距过期秒数,客户端倒计时用
      qrPayload: {               // 直接可编码成二维码的 JSON
        v: 1,
        url: string,
        code: string,
        exp: number,
      }
    }
    ```
- **Errors**:
  - `401 AUTH_REQUIRED` — 缺少 `X-Service-Token` 或值错
- **Example**:
  ```bash
  curl http://127.0.0.1:8080/api/auth/pair-code \
    -H 'X-Service-Token: dev-internal-secret'
  ```
- **Implementation Note**:
  - M1 中 `X-Service-Token` 是硬编码常量(在 App 源码内),仅供同设备 loopback 调用。Web 端永远不应能访问此端点,生产部署必须从 AndroidManifest 网络安全配置里屏蔽。

---

## 9. 文件系统端点

### 9.1 `GET /api/fs/list`

- **Auth**: Bearer Token
- **Description**: 列出某目录下直接子项(不含递归)
- **Request**:
  - Headers: `Authorization: Bearer <token>`
  - Query:
    - `path` (string, 必填, percent-encoded) — 目录虚拟路径
    - `sort` (string, 可选) — `name` | `size` | `modifiedAt` | `kind`,默认 `name`
    - `order` (string, 可选) — `asc` | `desc`,默认 `asc`
    - `showHidden` (boolean, 可选) — `true` / `false`,默认跟随服务端配置
- **Response**:
  - Status: `200`
  - Body: `ListResponse`(§7.12)
- **Errors**:
  - `400 FS_PATH_INVALID` / `400 FS_PATH_TRAVERSAL`
  - `404 FS_NOT_FOUND` — 路径不存在或不是目录
  - `400 FS_IS_FILE` — 路径指向文件而非目录
  - `401 AUTH_REQUIRED` / `401 AUTH_INVALID`
  - `403 FS_READ_ONLY` / `403 FS_PERMISSION_DENIED`
- **Example**:
  ```bash
  curl -G 'http://192.168.1.105:8080/api/fs/list' \
    --data-urlencode 'path=/DCIM/Camera' \
    --data-urlencode 'sort=modifiedAt' \
    --data-urlencode 'order=desc' \
    -H 'Authorization: Bearer xxx'
  ```

### 9.2 `GET /api/fs/stat`

- **Auth**: Bearer Token
- **Description**: 获取单个文件 / 目录的元数据;不返回子项
- **Request**:
  - Query: `path` (string, 必填, percent-encoded)
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface StatResponse {
      entry: FileEntry           // 单个 FileEntry(见 §7.2)
    }
    ```
- **Errors**: 同 `list`
- **Example**:
  ```bash
  curl -G 'http://192.168.1.105:8080/api/fs/stat' \
    --data-urlencode 'path=/DCIM/Camera/IMG_0001.jpg' \
    -H 'Authorization: Bearer xxx'
  ```

### 9.3 `POST /api/fs/mkdir`

- **Auth**: Bearer Token + 服务端 `uploadEnabled` / `deleteEnabled` 不限制(独立开关由配置决定)
- **Description**: 在 `path` 下创建名为 `name` 的子目录
- **Request**:
  - Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`
  - Body:
    ```typescript
    interface MkdirRequest {
      path: string               // 父目录
      name: string               // 新目录名,不含 "/"
    }
    ```
- **Response**:
  - Status: `201`
  - Body: `StatResponse`(返回新建目录的 FileEntry)
- **Errors**:
  - `400 FS_PATH_INVALID` / `400 FS_NAME_INVALID`
  - `409 FS_ALREADY_EXISTS`
  - `403 FS_READ_ONLY` / `403 FS_PERMISSION_DENIED`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/fs/mkdir \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"path":"/Download","name":"旅行照片"}'
  ```

### 9.4 `POST /api/fs/rename`

- **Auth**: Bearer Token
- **Description**: 重命名文件 / 目录(`name` 不能含 `/`)
- **Request**:
  - Body:
    ```typescript
    interface RenameRequest {
      path: string               // 原路径
      newName: string            // 新名称
    }
    ```
- **Response**:
  - Status: `200`
  - Body: `StatResponse`(返回重命名后的 FileEntry,新 `path`)
- **Errors**:
  - `404 FS_NOT_FOUND`
  - `409 FS_ALREADY_EXISTS`(同目录已有同名)
  - `400 FS_NAME_INVALID`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/fs/rename \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"path":"/Download/IMG.jpg","newName":"2024春节_01.jpg"}'
  ```

### 9.5 `POST /api/fs/move`

- **Auth**: Bearer Token
- **Description**: 移动 / 跨目录移动;**M1 同时支持重命名**(若 `newName` 提供)
- **Request**:
  - Body:
    ```typescript
    interface MoveRequest {
      src: string                // 源路径
      dst: string                // 目标目录(必须以 "/" 结尾的目录路径)
      newName?: string           // 可选;不传则保留原名
      overwrite?: boolean        // 默认 false;true 时同名静默覆盖
    }
    ```
- **Response**:
  - Status: `200`
  - Body: `StatResponse`
- **Errors**:
  - `404 FS_NOT_FOUND`(src)
  - `404 FS_NOT_FOUND`(dst 目录不存在)
  - `409 FS_ALREADY_EXISTS`(`overwrite=false` 且目标已存在)
  - `400 FS_PATH_TRAVERSAL`(dst 越界)
  - `403 FS_READ_ONLY`(源或目标只读)
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/fs/move \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"src":"/Download/a.jpg","dst":"/Pictures","newName":"a-renamed.jpg","overwrite":false}'
  ```

### 9.6 `POST /api/fs/delete`

- **Auth**: Bearer Token
- **Description**: 删除文件 / 目录。M1 统一为 **移到回收站**,不物理删除
- **Request**:
  - Body:
    ```typescript
    interface DeleteRequest {
      paths: string[]            // 支持批量(同一事务)
      recursive?: boolean        // 默认 false;目录非空需 true
    }
    ```
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface DeleteResponse {
      deleted: string[]          // 成功移到回收站的路径
      failed: Array<{
        path: string
        code: string             // 如 FS_NOT_FOUND
        message: string
      }>
    }
    ```
- **Errors**:
  - `409 FS_NOT_EMPTY`(`recursive=false` 且目录非空)
  - `404 FS_NOT_FOUND`(单条;部分失败不影响其它)
  - `403 FS_READ_ONLY`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/fs/delete \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"paths":["/Download/a.jpg","/Download/empty-dir"],"recursive":false}'
  ```

### 9.7 `POST /api/fs/restore`

- **Auth**: Bearer Token
- **Description**: 从回收站恢复一个或多个条目到原路径;若原路径已存在,返回 `FS_ALREADY_EXISTS`(除非 `overwrite=true`)
- **Request**:
  - Body:
    ```typescript
    interface RestoreRequest {
      paths: string[]            // 回收站中的路径,形如 "/.trash/2024-01-15/a.jpg"
      overwrite?: boolean        // 默认 false
    }
    ```
- **Response**:
  - Status: `200`
  - Body: `DeleteResponse`(语义相同,字段重命名,但保持结构一致)
- **Errors**:
  - `404 FS_NOT_FOUND`
  - `409 FS_ALREADY_EXISTS`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/fs/restore \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"paths":["/.trash/2024-01-15/a.jpg"]}'
  ```

### 9.8 `GET /api/fs/search`

- **Auth**: Bearer Token
- **Description**: 模糊搜索文件名;支持类型 / 大小 / 时间筛选
- **Request**:
  - Query:
    - `q` (string, 必填) — 关键词,大小写不敏感,匹配文件名 substring
    - `path` (string, 可选) — 搜索起点目录,默认 `/`(递归)
    - `type` (string, 可选) — `image` | `video` | `audio` | `document` | `archive`,过滤 MIME
    - `minSize` (number, 可选) — 字节
    - `maxSize` (number, 可选) — 字节
    - `from` (number, 可选) — Unix ms,只返回 modifiedAt ≥ 此值
    - `to` (number, 可选) — Unix ms,只返回 modifiedAt ≤ 此值
- **Response**:
  - Status: `200`
  - Body: `SearchResponse`(§7.11)
- **Errors**:
  - `400 BAD_REQUEST`(`q` 为空)
  - `400 FS_PATH_INVALID`(若提供 `path`)
- **Example**:
  ```bash
  curl -G 'http://192.168.1.105:8080/api/fs/search' \
    --data-urlencode 'q=IMG' \
    --data-urlencode 'type=image' \
    --data-urlencode 'minSize=1048576' \
    -H 'Authorization: Bearer xxx'
  ```

---

## 10. 文件传输端点

### 10.1 `GET /api/file/raw`

- **Auth**: Bearer Token(**或** `?token=<token>` 用于 `<img>`/`<video>` 标签)
- **Description**: 流式下载原文件;支持 HTTP Range(断点续传)
- **Request**:
  - Query:
    - `path` (string, 必填, percent-encoded)
    - `inline` (boolean, 可选) — `true` 时 `Content-Disposition: inline`,浏览器直接预览;默认 `attachment` 触发下载
    - `token` (string, 可选) — 仅在无法设置 Authorization Header 的场景(`<img src>` 等)
  - Headers:
    - `Authorization: Bearer <token>`(标准)
    - 或 `Range: bytes=0-1023`(可选;省略则返回 200 + 完整文件)
- **Response**:
  - Status:
    - `200 OK` — 完整文件
    - `206 Partial Content` — Range 命中
  - Headers:
    - `Content-Type: <mime>`(由服务端嗅探)
    - `Content-Length: <bytes>`
    - `Content-Range: bytes <start>-<end>/<total>`(仅 206)
    - `Accept-Ranges: bytes`(始终)
    - `Content-Disposition: attachment; filename="<encoded-name>"` 或 `inline`
    - `Last-Modified: <RFC 7231 GMT>`
    - `ETag: "<sha1-of-path>"`
    - `X-Content-Type-Options: nosniff`
    - `Cache-Control: private, max-age=0, must-revalidate`(因 token 而异)
- **Errors**:
  - `404 FS_NOT_FOUND`
  - `400 FS_IS_DIRECTORY`
  - `416`(Range 不合法)— Body 仍返回 `ApiError`
  - `401`(token 缺失/无效)
- **Example**:
  ```bash
  # 完整下载
  curl -o out.jpg 'http://192.168.1.105:8080/api/file/raw?path=%2FDCIM%2FCamera%2FIMG_0001.jpg' \
    -H 'Authorization: Bearer xxx'

  # Range 下载前 1 MiB
  curl -o chunk.bin 'http://192.168.1.105:8080/api/file/raw?path=%2FMovies%2Fbig.mov' \
    -H 'Authorization: Bearer xxx' \
    -H 'Range: bytes=0-1048575'

  # 在 <img> 中使用(无需 Bearer)
  # <img src="http://192.168.1.105:8080/api/file/raw?path=%2FDCIM%2Fa.jpg&token=xxx&inline=true">
  ```

### 10.2 `GET /api/file/thumb`

- **Auth**: Bearer Token 或 `?token=`(同 raw)
- **Description**: 获取缩略图(JPEG,服务端按需生成并缓存)
- **Request**:
  - Query:
    - `path` (string, 必填)
    - `size` (integer, 可选) — 像素,正方形,默认 `256`;允许 `64` / `128` / `256` / `512` / `1024`
- **Response**:
  - Status: `200`
  - Headers:
    - `Content-Type: image/jpeg`
    - `Cache-Control: public, max-age=3600`(服务端有磁盘缓存)
    - `X-Thumb-Source: original` / `cached` / `video-frame`(debug 用)
    - `Content-Length: <bytes>`
- **Errors**:
  - `404 FS_NOT_FOUND`
  - `415 MEDIA_UNSUPPORTED`(非图片 / 视频,例如 PDF)
  - `422 MEDIA_DECODE_ERROR`
  - `500 MEDIA_THUMB_FAIL`
- **Example**:
  ```bash
  curl -o thumb.jpg 'http://192.168.1.105:8080/api/file/thumb?path=%2FDCIM%2Fa.jpg&size=128' \
    -H 'Authorization: Bearer xxx'
  ```

### 10.3 `GET /api/file/zip`

- **Auth**: Bearer Token
- **Description**: 把多个文件 / 目录打包成单个 ZIP 流式下载
- **Request**:
  - Query:
    - `paths` (string[], 必填) — **多次重复**参数,如 `?paths=/a&paths=/b`
    - `name` (string, 可选) — ZIP 文件名(不含 `.zip`),默认 `archive-20240620-142301`
- **Response**:
  - Status: `200`
  - Headers:
    - `Content-Type: application/zip`
    - `Content-Disposition: attachment; filename="<name>.zip"`
    - `Transfer-Encoding: chunked`(流式,不预先计算 Content-Length)
    - `X-Zip-Entries: <count>`
- **Errors**:
  - `400 BAD_REQUEST`(`paths` 为空)
  - `404 FS_NOT_FOUND`(任一路径不存在)
  - `413 UPLOAD_TOO_LARGE`(总大小超限)
- **Example**:
  ```bash
  curl -G 'http://192.168.1.105:8080/api/file/zip' \
    --data-urlencode 'paths=/DCIM/a.jpg' \
    --data-urlencode 'paths=/Download/report.pdf' \
    --data-urlencode 'name=photos-and-docs' \
    -o bundle.zip \
    -H 'Authorization: Bearer xxx'
  ```

### 10.4 `POST /api/file/upload` — 简单单请求上传

- **Auth**: Bearer Token
- **Description**: 小文件(≤ 100 MB)直接 multipart 上传,无需分片
- **Request**:
  - Headers:
    - `Authorization: Bearer <token>`
    - `Content-Type: multipart/form-data; boundary=---xxx`
  - Body(表单字段):
    - `path` (string, text 字段) — 目标目录
    - `file` (binary 字段) — 文件本体
    - `filename` (string, 可选 text 字段) — 覆盖客户端文件名
    - `overwrite` (string "true"/"false", 可选) — 默认 false
- **Response**:
  - Status: `201`
  - Body: `UploadCompleteResponse`(§7.7)
- **Errors**:
  - `413 UPLOAD_TOO_LARGE`
  - `409 UPLOAD_CONFLICT`
  - `403 FS_READ_ONLY`(目标目录只读)
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/file/upload \
    -H 'Authorization: Bearer xxx' \
    -F 'path=/Download' \
    -F 'file=@./local.pdf' \
    -F 'overwrite=false'
  ```

### 10.5 `POST /api/file/upload/init` — 分片上传初始化

- **Auth**: Bearer Token
- **Description**: 创建分片上传会话,获取 `uploadId`
- **Request**:
  - Body(JSON):
    ```typescript
    interface UploadInitRequest {
      path: string               // 目标目录
      filename: string           // 文件名
      size: number               // 总字节数,客户端预先计算
      mime?: string              // 可选;服务端嗅探为准
      overwrite?: boolean        // 默认 false
      sha1?: string              // 可选,客户端预计算的全文 hash(用于 complete 时校验)
    }
    ```
- **Response**:
  - Status: `200`
  - Body: `UploadInitResponse`(§7.6)
- **Errors**:
  - `409 UPLOAD_CONFLICT`
  - `413 UPLOAD_TOO_LARGE`
  - `403 FS_READ_ONLY`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/file/upload/init \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"path":"/Download","filename":"big.mov","size":4294967296,"overwrite":false}'
  ```

### 10.6 `POST /api/file/upload/chunk` — 分片上传

- **Auth**: Bearer Token
- **Description**: 上传一个分片
- **Request**:
  - Headers:
    - `Authorization: Bearer <token>`
    - `Content-Type: application/octet-stream`(M1;或 `multipart/form-data` 二选一)
    - `X-Upload-Id: <uploadId>`
    - `X-Upload-Offset: <offset>`(字节)
  - Body: 分片原始字节
- **Response**:
  - Status: `200`
  - Body: `ChunkAck`(§7.8)
- **Errors**:
  - `404 UPLOAD_SESSION_NOT_FOUND`
  - `410 UPLOAD_EXPIRED`
  - `400 UPLOAD_CHUNK_OUT_OF_RANGE`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/file/upload/chunk \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/octet-stream' \
    -H 'X-Upload-Id: 8f4d2c9a-...' \
    -H 'X-Upload-Offset: 8388608' \
    --data-binary @chunk-002.bin
  ```

### 10.7 `POST /api/file/upload/complete` — 完成分片上传

- **Auth**: Bearer Token
- **Description**: 通知服务端所有分片已上传,触发合并 + 落盘
- **Request**:
  - Body(JSON):
    ```typescript
    interface UploadCompleteRequest {
      uploadId: string
      sha1?: string               // 可选;服务端验证与 init 时的 sha1 一致
    }
    ```
- **Response**:
  - Status: `200`
  - Body: `UploadCompleteResponse`
- **Errors**:
  - `404 UPLOAD_SESSION_NOT_FOUND`
  - `410 UPLOAD_EXPIRED`
  - `409 UPLOAD_CHUNK_MISMATCH`(预留,M1 暂不校验)
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/file/upload/complete \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"uploadId":"8f4d2c9a-..."}'
  ```

### 10.8 `POST /api/file/upload/cancel` — 取消分片上传

- **Auth**: Bearer Token
- **Description**: 主动放弃;服务端清理临时分片文件
- **Request**:
  - Body:
    ```typescript
    interface UploadCancelRequest {
      uploadId: string
    }
    ```
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface UploadCancelResponse {
      cancelled: true
      freedBytes: number          // 释放的临时分片总字节
    }
    ```
- **Errors**:
  - `404 UPLOAD_SESSION_NOT_FOUND`
- **Example**:
  ```bash
  curl -X POST http://192.168.1.105:8080/api/file/upload/cancel \
    -H 'Authorization: Bearer xxx' \
    -H 'Content-Type: application/json' \
    -d '{"uploadId":"8f4d2c9a-..."}'
  ```

---

## 11. 媒体端点

### 11.1 `GET /api/media/exif`

- **Auth**: Bearer Token
- **Description**: 解析图片 EXIF
- **Request**:
  - Query: `path` (string, 必填)
- **Response**:
  - Status: `200`
  - Body:
    ```typescript
    interface ExifResponse {
      exif: ExifData             // 见 §7.9
    }
    ```
- **Errors**:
  - `404 FS_NOT_FOUND`
  - `415 MEDIA_UNSUPPORTED`(非 JPEG / TIFF / HEIC 等可解析 EXIF 的格式)
  - `422 MEDIA_DECODE_ERROR`
- **Example**:
  ```bash
  curl -G 'http://192.168.1.105:8080/api/media/exif' \
    --data-urlencode 'path=/DCIM/Camera/IMG_0001.jpg' \
    -H 'Authorization: Bearer xxx'
  ```

### 11.2 `GET /api/media/photos`

- **Auth**: Bearer Token
- **Description**: 按年/月聚合所有媒体文件(用于照片墙 / 时间轴)
- **Request**:
  - Query:
    - `year` (number, 可选) — 只返回此年;不传返回所有
    - `month` (number, 可选) — 与 year 同时使用;不传按年聚合(`month=0`),传了按月聚合
    - `type` (string, 可选) — `image` | `video` | `all`,默认 `image`
    - `path` (string, 可选) — 限定扫描起点目录,默认 `/`
- **Response**:
  - Status: `200`
  - Body: `PhotosResponse`(§7.10)
- **Errors**:
  - `400 BAD_REQUEST`(`year` 越界)
- **Example**:
  ```bash
  # 全部年份的汇总
  curl 'http://192.168.1.105:8080/api/media/photos' \
    -H 'Authorization: Bearer xxx'

  # 2024 年每月分组
  curl 'http://192.168.1.105:8080/api/media/photos?year=2024' \
    -H 'Authorization: Bearer xxx'
  ```

---

## 12. 系统端点

### 12.1 `GET /api/status`

- **Auth**: Public(基础字段);传 Bearer 后会附加 `deviceName` / `paired` 等设备相关字段
- **Description**: 服务端状态 + 客户端校时 + 配置开关
- **Request**:
  - Headers: `Authorization: Bearer <token>`(可选)
- **Response**:
  - Status: `200`
  - Body: `StatusResponse`(§7.4)
- **Errors**:
  - `503 SERVICE_UNAVAILABLE`(服务正在停止)
- **Example**:
  ```bash
  # 不带 token(只看公共部分)
  curl http://192.168.1.105:8080/api/status

  # 带 token
  curl http://192.168.1.105:8080/api/status \
    -H 'Authorization: Bearer xxx'
  ```

### 12.2 `GET /api/health`

- **Auth**: Public
- **Description**: 极简存活探针;供监控 / 负载均衡 / App 自检使用
- **Request**: 空
- **Response**:
  - Status: `200`
  - Body: `HealthResponse`(§7.5)
- **Example**:
  ```bash
  curl http://192.168.1.105:8080/api/health
  # {"ok":true,"uptime":42}
  ```

---

## 13. 关键交互时序图

### 13.1 配对 + 首次列出目录

```
┌──────────┐                  ┌──────────┐                       ┌──────────┐
│ 安卓 App  │                  │  Web UI  │                       │ Ktor 服务 │
└────┬─────┘                  └────┬─────┘                       └────┬─────┘
     │                              │                                  │
     │ 启动服务,生成配对码 483921    │                                  │
     │ (有效期 5 分钟)              │                                  │
     │                              │                                  │
     │◄─────── 用户扫码 ────────────│                                  │
     │                              │                                  │
     │                              │── POST /api/auth/pair ──────────▶│
     │                              │   { code: "483921" }            │
     │                              │                                  │ ① 校验:格式/存在/未用/未过期
     │                              │                                  │ ② 生成 token
     │                              │                                  │ ③ 持久化:token→deviceId
     │                              │                                  │
     │                              │◀─ 200 { token, deviceName, ... } │
     │                              │                                  │
     │                              │ localStorage.set(token)          │
     │                              │                                  │
     │                              │── GET /api/status ──────────────▶│
     │                              │   Authorization: Bearer xxx     │
     │                              │◀─ 200 StatusResponse ───────────│
     │                              │                                  │
     │                              │── GET /api/fs/list?path=%2F ────▶│
     │                              │   Authorization: Bearer xxx     │
     │                              │                                  │ 列出根目录下 DCIM/Pictures/Download
     │                              │◀─ 200 ListResponse ─────────────│
     │                              │                                  │
```

### 13.2 分片上传(init / chunk / complete 三步)

```
┌──────────┐                              ┌──────────┐
│  Web UI  │                              │ Ktor 服务 │
└────┬─────┘                              └────┬─────┘
     │  用户拖入 big.mov(4 GB)                │
     │  客户端预计算 size=4GB, sha1=abc123     │
     │                                          │
     │── ① POST /api/file/upload/init ────────▶│
     │   { path:"/Movies",                     │
     │     filename:"big.mov",                 │
     │     size: 4294967296,                   │
     │     sha1: "abc123..." }                 │
     │                                          │ 在 /tmp/uploads/<uuid>/ 创建会话
     │                                          │ 记录 expectedSize, expiresAt=+24h
     │                                          │
     │◀─ 200 { uploadId, chunkSize:8388608, ──│
     │        expiresAt, received:[] }         │
     │                                          │
     │── ② POST /api/file/upload/chunk ──────▶│  ┐
     │   X-Upload-Id: <uuid>                   │  │ 循环 N 次,每次一片
     │   X-Upload-Offset: 0  (8 MiB)           │  │ offset = i * 8388608
     │   Body: chunk-000.bin                   │  │
     │◀─ 200 ChunkAck { received:8388608 } ──│  │
     │                                          │  │
     │── ② POST /api/file/upload/chunk ──────▶│  │
     │   X-Upload-Offset: 8388608              │  │
     │   Body: chunk-001.bin                   │  │
     │◀─ 200 ChunkAck { received:16777216 } ─│  │
     │                                          │  ...(共 512 片)
     │── ② POST /api/file/upload/chunk ──────▶│  │
     │   X-Upload-Offset: 4294967296-8388608   │  │
     │   Body: chunk-511.bin                   │  │
     │◀─ 200 ChunkAck { received:4294967296, ┘
     │              complete:true } ──────────│
     │                                          │
     │── ③ POST /api/file/upload/complete ────▶│
     │   { uploadId, sha1:"abc123..." }        │
     │                                          │ ① 校验所有分片已收齐
     │                                          │ ② 拼接 → /Movies/big.mov
     │                                          │ ③ 清理临时分片目录
     │                                          │ ④ (M2 校验 sha1)
     │◀─ 200 UploadCompleteResponse ─────────│
     │     { path:"/Movies/big.mov",           │
     │       size:4294967296,                  │
     │       mime:"video/quicktime",           │
     │       modifiedAt:... }                  │
     │                                          │
     │  前端刷 /api/fs/list?path=/Movies        │
```

### 13.3 下载 + 断点续传(Range)

```
┌──────────┐                              ┌──────────┐
│  浏览器  │                              │ Ktor 服务 │
└────┬─────┘                              └────┬─────┘
     │                                          │
     │── GET /api/file/raw?path=%2FMovies%2Fbig.mov ─▶│
     │   Range: bytes=0-1048575                  │
     │                                          │ ① 解析 Range,定位文件
     │                                          │ ② 检查 ETag,返回 200 或 206
     │◀─ 206 Partial Content ─────────────────│
     │   Content-Range: bytes 0-1048575/4294967296
     │   Content-Length: 1048576
     │   Accept-Ranges: bytes
     │   ETag: "abc..."
     │   Body: [chunk-000]                      │
     │                                          │
     │  ...用户暂停...                          │
     │                                          │
     │── GET /api/file/raw?path=... ──────────▶│
     │   Range: bytes=1048576-                  │
     │   If-Range: "abc..."                     │
     │                                          │ 文件未变,继续 206
     │◀─ 206 Partial Content ─────────────────│
     │   Content-Range: bytes 1048576-4294967295/4294967296
     │   Body: [chunk-001..end]                │
     │                                          │
     │  (若中途文件被改动,服务端返回 200 完整重下)
```

### 13.4 删除 → 回收站 → 恢复

```
Web UI                              Ktor 服务                          Android FS
   │                                    │                                  │
   │ POST /api/fs/delete                │                                  │
   │ { paths:["/DCIM/a.jpg"] }          │                                  │
   │───────────────────────────────────▶│                                  │
   │                                    │ ① 校验 path                      │
   │                                    │ ② 在 /.trash/ 下建日期子目录      │
   │                                    │ ③ 移动原文件 → /.trash/2024-01-15/a.jpg
   │                                    │─────────────────────────────────▶│
   │                                    │                                  │
   │◀── 200 { deleted:["/DCIM/a.jpg"] } │                                  │
   │                                    │                                  │
   │ (用户在"回收站"页面浏览)           │                                  │
   │ GET /api/fs/list?path=%2F.trash    │                                  │
   │───────────────────────────────────▶│                                  │
   │◀── 200 ListResponse ─────────────│                                  │
   │                                    │                                  │
   │ POST /api/fs/restore               │                                  │
   │ { paths:["/.trash/2024-01-15/a.jpg"] }                                │
   │───────────────────────────────────▶│                                  │
   │                                    │ ④ 检查原路径 /DCIM/a.jpg 是否空闲│
   │                                    │ ⑤ 移动 → /DCIM/a.jpg             │
   │                                    │─────────────────────────────────▶│
   │◀── 200 { deleted:[...] } ────────│                                  │
```

---

## 14. 限流与并发

> **M1 状态**: 不限流 / 不限并发,仅留以下硬性上限:

| 项 | 值 | 说明 |
|---|---|---|
| 单文件上传 | 10 GB | 超过返回 `413 UPLOAD_TOO_LARGE` |
| 分片大小 | 8 MiB(服务端推荐) | 客户端可传更小,但不能 > 服务端推荐值 ×2 |
| ZIP 总大小 | 10 GB | 同单文件 |
| 单 Token 并发请求 | 10 | 超出排队,不做硬性拒绝(Android Ktor CIO 默认实现即可) |
| 全局并发连接 | 100 | Ktor CIO 配置上限 |

> **M2 引入**(预留):
> - Token 维度的滑动窗口限流(60 秒内 1000 次)
> - IP 维度的失败计数(配对码错误 5 次/分钟)
> - 上传 / 下载独立通道(避免互相饿死)

---

## 15. 版本与变更记录

### 15.1 当前版本

**v0.1.0** — 2026-06-20

### 15.2 已知偏差 / 待服务端实现

| 项 | 现状 | 应达 | 行动 |
|---|---|---|---|
| `/api/status` 字段集 | 安卓端仅返回 `{app, version, status, uptime, connections}` | 完整 §7.4 字段集 | 安卓端补齐 `deviceName` / `paired` / `serverTime` / `rootAlias` / `uploadEnabled` / `deleteEnabled` / `showHidden` / `maxUploadSize` / `maxConnections` / `apiVersion` |
| `/api/fs/copy` 端点 | 产品文档列出,M1 暂不实现 | M3 | M1 内返回 `501 NOT_IMPLEMENTED`,前端按 disabled 处理 |
| Cookie 鉴权 | M1 不实现 | M5 | M1 阶段前端只用 Bearer |
| WebSocket 实时进度 | M1 走轮询 | M2 | M1 分片上传后,前端用 `ChunkAck.received` 推进度条 |
| Token 撤销白名单查询 | 无独立端点 | 列出当前用户所有 device | M2 新增 `GET /api/auth/devices` + `DELETE /api/auth/devices/:deviceId` |
| 移动 / 复制跨根 | 仅同根目录 | 跨根 | M3 引入 |
| 缩略图缓存清理 | 无显式端点 | `DELETE /api/file/thumb?path=...` | M2 |
| 实时目录推送(WebSocket) | 无 | `WS /api/ws` | M5 |

### 15.3 字段命名历史(防回滚错)

| 现名 | 旧名(若曾用) | 备注 |
|---|---|---|
| `FileEntry.kind` | 一度考虑 `type` / `nodeType` | 已锁定 `kind` |
| `ApiError.code` | 一度考虑 `errorCode` | 已锁定 `code` |
| 时间戳字段 | 一度考虑 ISO 8601 字符串 | 已锁定 Unix 毫秒 `number` |
| `path` query 参数 | 一度考虑 `p` | 已锁定 `path` |

### 15.4 变更流程

任何契约字段、状态码、Header、错误码的变更 **必须** 经过以下流程:

1. 在本文件先开 §15.2 的"已知偏差"条目,描述 Proposed Change
2. 双方实现者(sisyphus-Android + sisyphus-Web)在 review 中 agree
3. 同步更新:
   - 本文档
   - `web/src/types/api.ts`
   - 安卓端 `com.hcqdrive.server` 路由签名
4. 在 §15.5 加一行变更记录

### 15.5 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.1.0 | 2026-06-20 | 初版;锁定 M1 全部 22 个端点;统一错误码 / 路径模型 / 鉴权流程 |

---

## 附录 A:端点速查表

| 方法 | 路径 | Auth | 用途 |
|---|---|---|---|
| POST | `/api/auth/pair` | Public | 配对 |
| POST | `/api/auth/verify` | Public* | 校验 token |
| POST | `/api/auth/revoke` | Bearer | 撤销当前 token |
| GET | `/api/auth/pair-code` | Service-Token | 获取当前配对码(本机) |
| GET | `/api/fs/list` | Bearer | 列目录 |
| GET | `/api/fs/stat` | Bearer | 单项元数据 |
| POST | `/api/fs/mkdir` | Bearer | 新建目录 |
| POST | `/api/fs/rename` | Bearer | 重命名 |
| POST | `/api/fs/move` | Bearer | 移动 |
| POST | `/api/fs/delete` | Bearer | 删除(→ 回收站) |
| POST | `/api/fs/restore` | Bearer | 从回收站恢复 |
| GET | `/api/fs/search` | Bearer | 搜索 |
| GET | `/api/file/raw` | Bearer / ?token= | 下载原文件(Range) |
| GET | `/api/file/thumb` | Bearer / ?token= | 缩略图 |
| GET | `/api/file/zip` | Bearer | ZIP 打包下载 |
| POST | `/api/file/upload` | Bearer | 简单上传 |
| POST | `/api/file/upload/init` | Bearer | 分片上传初始化 |
| POST | `/api/file/upload/chunk` | Bearer | 分片上传 |
| POST | `/api/file/upload/complete` | Bearer | 完成分片上传 |
| POST | `/api/file/upload/cancel` | Bearer | 取消分片上传 |
| GET | `/api/media/exif` | Bearer | EXIF 读取 |
| GET | `/api/media/photos` | Bearer | 按时间聚合的媒体列表 |
| GET | `/api/status` | Public* | 服务状态 |
| GET | `/api/health` | Public | 健康探针 |

**合计 24 个端点**

---

## 附录 B:HTTP 状态码速查

| 状态码 | 出现场景 | 对应 `code` |
|---|---|---|
| 200 | 成功(GET / 通用) | — |
| 201 | 创建成功(mkdir / upload complete) | — |
| 206 | Range 命中 | — |
| 400 | 路径/参数非法 | `FS_PATH_INVALID` / `BAD_REQUEST` / ... |
| 401 | 鉴权失败 | `AUTH_REQUIRED` / `AUTH_INVALID` |
| 403 | 权限不足 | `FS_READ_ONLY` / `FS_PERMISSION_DENIED` |
| 404 | 资源不存在 | `FS_NOT_FOUND` / `UPLOAD_SESSION_NOT_FOUND` |
| 409 | 冲突 | `FS_ALREADY_EXISTS` / `UPLOAD_CONFLICT` / `FS_NOT_EMPTY` |
| 410 | 资源已过期 / 已取消 | `UPLOAD_EXPIRED` / `UPLOAD_CANCELLED` |
| 413 | 内容过大 | `UPLOAD_TOO_LARGE` |
| 415 | 媒体类型不支持 | `MEDIA_UNSUPPORTED` / `UNSUPPORTED_MEDIA_TYPE` |
| 416 | Range 不合法 | (直接返回,code=`BAD_REQUEST`,message="Invalid Range") |
| 422 | 内容解码失败 | `MEDIA_DECODE_ERROR` |
| 429 | 限流 | `AUTH_RATE_LIMITED` |
| 500 | 服务器内部错误 | `INTERNAL` |
| 501 | 未实现 | `NOT_IMPLEMENTED` |
| 503 | 服务暂不可用 | `SERVICE_UNAVAILABLE` |

---

**文档结束 · 如有疑问请在 PR 中评论 §15.2 偏差表**