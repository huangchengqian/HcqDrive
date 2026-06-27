# HcqDrive · Web UI

HcqDrive 安卓局域网云盘的 Web 客户端,基于 **Vue 3 + Vite + TypeScript + Tailwind CSS**。

> 当前为 **M1 骨架**:配对 + 文件列表占位,视觉精致、响应式、暗色主题、TS 严格模式已就绪。

---

## 启动

```bash
cd web
npm install
npm run dev          # http://localhost:5173
```

局域网内其他设备访问:把 `localhost` 换成你电脑的局域网 IP(`npm run dev` 默认绑定 `0.0.0.0:5173`)。

### 其它脚本

| 命令 | 作用 |
|---|---|
| `npm run dev` | 启动 Vite 开发服务器(HMR) |
| `npm run build` | 类型检查 + 生产构建,产物在 `dist/` |
| `npm run preview` | 预览生产产物 |
| `npm run type-check` | 仅做 `vue-tsc` 类型检查 |

### 环境变量

`.env`(可选):

```env
VITE_API_BASE=/      # 默认值,APK 内部使用相对路径
```

---

## 目录结构

```
web/
├── src/
│   ├── main.ts                 # 入口:Pinia + Router + 主题初始化
│   ├── App.vue                 # 根组件:RouterView + 过渡
│   ├── env.d.ts                # Vite + Vue ambient 类型
│   ├── router/index.ts         # 路由 + 鉴权守卫
│   ├── stores/
│   │   ├── auth.ts             # 配对/Token(支持 mock: 配对码 888888)
│   │   └── files.ts            # 文件列表状态 + mock 数据
│   ├── views/
│   │   ├── PairView.vue        # 6 位配对码输入(Stripe 风)
│   │   └── HomeView.vue        # 文件列表(列表/网格 + 空/加载状态)
│   ├── components/
│   │   ├── ui/
│   │   │   ├── BaseButton.vue  # variant × size,loading 状态
│   │   │   ├── BaseInput.vue   # label / error / hint / 字符计数
│   │   │   ├── BaseCard.vue    # 圆角 + 阴影 + 6 档内边距
│   │   │   └── IconButton.vue  # 圆形 hover 背景
│   │   ├── layout/
│   │   │   └── AppHeader.vue   # 顶栏:Logo / 面包屑 / 刷新 / 设置 / 退出
│   │   └── file/
│   │       └── FileListItem.vue
│   ├── api/
│   │   └── client.ts           # fetch 封装 + Bearer Token + 401 跳配对
│   ├── lib/
│   │   ├── format.ts           # 字节 / 相对时间(Intl 原生)
│   │   └── icons.ts            # Lucide 图标 / 颜色按扩展名映射
│   ├── styles/
│   │   └── tailwind.css        # @tailwind + CSS 变量 + 全局基线
│   └── types/
│       └── api.ts              # StatusResponse / FileEntry / PairRequest …
├── public/
├── index.html                  # 主题 FOUC 预防脚本(head 内)
├── package.json
├── tsconfig.json               # strict + noUncheckedIndexedAccess
├── tsconfig.node.json
├── vite.config.ts              # base:'./' 适配 APK assets
├── tailwind.config.ts          # 设计 token(主色/表面/语义/动效)
├── postcss.config.js
├── .gitignore
└── README.md
```

---

## M1 范围

**已交付**
- 配对页(Stripe 风 6 位数字输入):自动跳焦、粘贴、回退、Enter 提交、错误震动、Mock 配对码 `888888`
- 主页:文件列表(list / grid 切换)、搜索、多选、底部操作栏、空/加载状态
- 主题:跟随系统,支持 `html.dark` 切换,FOUC 防护
- 响应式:`< 640` 单列 / `≥ 1024` 表格列 / `≥ 1280` 网格
- Pinia 鉴权守卫:无 token 跳配对页
- 路由:`#/pair` 与 `#/home`(用 hash 模式,APK 离线加载更稳)

**未交付(按产品文档排期)**
- M2:照片墙 / 视频 / 音乐播放 / EXIF
- M3:重命名 / 移动 / 复制 / 删除 / 搜索筛选 / 右键菜单 / 回收站
- M4:直链分享 / mDNS / HTTPS
- M5:断点续传 / 分片上传 / 性能优化

---

## 与安卓端的对接约定

### 1. 路径

构建产物 `web/dist/*` 在打包 APK 时被复制到 `app/src/main/assets/web/`。Vite 已配置 `base: './'`,所有静态资源使用相对路径,在 Android WebView / 局域网直接访问两种场景下都能跑。

### 2. API

`src/api/client.ts` 已实现完整的 fetch 封装:
- 基础路径 = `import.meta.env.VITE_API_BASE`(默认 `/`)
- 自动从 `localStorage['hcqdrive:token']` 读取 Token,加上 `Authorization: Bearer <token>`
- 401 自动清 Token 并跳转 `#/pair`

**M1 阶段安卓端需提供的端点**(与产品文档 5.4 节一致):

| 方法 | 路径 | 请求 / 响应 |
|---|---|---|
| `GET` | `/api/status` | `StatusResponse` |
| `POST` | `/api/auth/pair` | `{ code }` → `{ token, expiresAt, deviceName }` |

所有响应统一 JSON,`{ code, message, status }` 表示错误。

### 3. 鉴权流程

1. 浏览器加载 `index.html` → 跳 `#/pair`(因无 token)
2. 用户输入 6 位码 → `POST /api/auth/pair`
3. 服务端返回 `token` → 存 `localStorage` → 跳 `#/home`
4. 之后所有请求带 `Authorization: Bearer <token>`;401 重新走流程

### 4. M1 期间 Web 端的 mock 行为

- `PairView`:输入 `888888` 直接通过(在 `stores/auth.ts` 内的 `pairRequest` 判断)
- `HomeView`:用 8 条 mock 文件展示布局(在 `stores/files.ts` 的 `MOCK_ENTRIES`)

后端就绪后,把这两处替换为真实 API 调用即可,无需改动 UI 层。

---

## 设计 Token 速览

> 完整定义见 `tailwind.config.ts`,这里只列最常用的几个。

| Token | 用途 | 例子 |
|---|---|---|
| `primary-500/600/700` | 主色 / 按钮 / 强调 | 配对按钮、激活态、链接 |
| `surface-0/50/100/.../950` | 背景分层 | 卡片、悬停、暗色基底 |
| `border-light` / `border-dark` | 边框 | 卡片、输入框 |
| `success/warning/danger-500/600` | 语义色 | 错误提示、危险操作 |
| `rounded-md/lg/xl` | 圆角(8/12/16) | 按钮 / 卡片 / 模态 |
| `shadow-card / floating` | 阴影(2 阶) | 卡片 / 浮层 |
| `ease-out-soft` | 200ms 过渡曲线 | 全部微交互 |

字体:`-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`(中英文混排优化)。

---

## 响应式断点

| 宽度 | 场景 | 行为 |
|---|---|---|
| `< 640px` | 手机竖屏 | 单列,文件名截断,操作栏底部 |
| `640–1024` | 手机横屏 / 小平板 | 列表为主 |
| `≥ 1024px` | 平板 / 桌面 | 表格风格:大小 / 时间对齐列 |
| `≥ 1280px` | 桌面宽屏 | 网格 5 列 |

---

## 验收对应

- ✅ `npm run dev` 直接跑(自动跳 `#/pair`)
- ✅ 输入 `888888` → 自动跳 `#/home` → 看到 mock 文件
- ✅ 暗色模式跟随系统
- ✅ 调整窗口大小布局自适应
- ✅ `npm run build` 成功(同时跑 `vue-tsc` 类型检查)
- ✅ TS strict + `noUncheckedIndexedAccess`
- ✅ 路径别名 `@/` 在 Vite 与 TS 双端配置
- ✅ 无 TODO / FIXME 残留
