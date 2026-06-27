# Twitter / HN / Reddit — English short-form posts

## Twitter / X (under 280 chars)

> HcqDrive (MIT): your Android phone becomes a private LAN cloud. Scan a QR, any browser on the same Wi-Fi gets instant access to your photos/videos/files. No cloud, no account, no client to install. v0.5.1 just shipped.
>
> https://github.com/huangchengqian/HcqDrive
> https://huangchengqian.github.io/HcqDrive/?demo=1

## Hacker News — Show HN

> Title: HcqDrive – Turn your Android phone into a private LAN cloud (MIT)
>
> Body:
>
> I made an Android app that runs a Ktor server on the phone itself. Any browser on the same Wi-Fi can scan a QR, enter a 6-digit code, and get a file manager rooted at the phone's storage. Upload, download, thumbnails, EXIF, ZIP bulk download — all in the browser, no client to install on the other device.
>
> It's the piece I always wanted between AirDrop (one-off sends only) and Nextcloud (too much setup). The phone is the server. Wi-Fi is the network. There is no cloud.
>
> Stack: Kotlin 2.0 + Compose + Ktor 3 on Android, Vue 3 + Vite + Tailwind on the web. No DI framework, no Room, no Java. 19 REST endpoints, full contract in the repo.
>
> Live demo (mock data, no phone needed): https://huangchengqian.github.io/HcqDrive/?demo=1
>
> GitHub: https://github.com/huangchengqian/HcqDrive
>
> M1 is feature-complete. M2 will add HTTPS (self-signed cert) and background uploads. Looking for feedback on the API shape especially — happy to break things in the next minor if it makes the integration story nicer.

## Reddit — r/androiddev, r/selfhosted, r/kotlin

> Title: HcqDrive — Android phone as a private LAN cloud (Kotlin + Ktor server, Vue 3 web client, MIT)
>
> Body:
>
> Spent the last few months building HcqDrive. The pitch: install one APK on your Android phone, and any browser on the same Wi-Fi can access the phone's files. No client on the other device, no cloud, no account. Like AirDrop but for everything; like Nextcloud but the phone is the server.
>
> Tech: Kotlin 2.0 + Jetpack Compose + Ktor 3 (CIO engine as the server) + ZXing + Apache Commons Compress on the Android side, Vue 3 + Vite + Tailwind + Pinia on the web side. No DI framework, no Room, no Java anywhere.
>
> What works in M1:
> - Foreground service with a persistent notification showing the pairing code, URL, and live connection count
> - 6-digit pairing code with 5-minute expiry, per-device session tokens
> - File browser: search, sort, rename, move, copy, delete (recycle bin), new folder
> - HTTP Range download resume, single + chunked upload, ZIP bulk download
> - Thumbnails for photos and videos, EXIF for JPEGs
> - Responsive Vue 3 web UI with full dark mode
>
> 19 REST endpoints, full contract at `docs/api-contract.md` (1700+ lines).
>
> Live web demo (mock data, no phone needed): https://huangchengqian.github.io/HcqDrive/?demo=1
>
> Repo: https://github.com/huangchengqian/HcqDrive
>
> Feedback wanted on the pairing/auth flow and the API shape. M2 plans are HTTPS, background uploads, and selective sync. PRs welcome — especially on the Vue side.
