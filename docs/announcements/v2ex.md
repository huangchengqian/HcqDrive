# V2EX 发版帖

> 标题:HcqDrive — 把安卓手机变成局域网云盘(MIT 开源)

---

做完了 M1,放出来。

简单说:**手机当服务器,浏览器当客户端,WiFi 当网络**。
装一个 APK,同 WiFi 的任何设备浏览器扫码就能访问手机里的文件。

不需要注册账号,文件不离开局域网,客户端零安装。
像 AirDrop 但能传任意大小,像 Nextcloud 但服务器就是你的手机。

**技术栈**:
- 安卓:Kotlin 2.0 + Compose + Ktor 3.0 (server) + ZXing + Commons Compress
- Web:Vue 3 + Vite + Tailwind + Pinia
- 无 DI 框架,无 Room,无 Java

**已实现的功能**:
- 一键启动前台服务,通知栏常显配对码 + 访问地址
- 6 位数字配对码(5 分钟过期) + Token 会话
- 文件浏览 / 搜索 / 排序 / 重命名 / 移动 / 复制 / 删除(回收站)
- HTTP Range 断点续传 + 单文件 / 分片上传 + ZIP 打包下载
- 图片 / 视频缩略图 + JPEG EXIF
- Vue 3 Web UI,响应式 + 暗色

**试用**:
- 在线 demo(假数据,纯体验 UI):https://huangchengqian.github.io/HcqDrive/?demo=1
- GitHub:https://github.com/huangchengqian/HcqDrive
- Release APK(debug 签):在 release 页面下载

**TODO(M2)**:
- HTTPS(自签证书打包进 APK)
- 后台上传(切走屏幕也能继续)
- 选择性同步

下个版本准备把 release 签名也搞上,这样能上 Play Store。

欢迎 star、issue、PR,尤其是 web UI 那边有 Vue 经验的同学。
