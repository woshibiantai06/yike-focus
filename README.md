# 一刻 · Yike Focus

无广告、可离线使用的番茄钟，带正向计时、任务备注和专注报告。

An offline, ad-free Pomodoro timer with count-up sessions, task notes, and focus reports.

[下载安装包 · Download](https://github.com/woshibiantai06/yike-focus/releases) · [报告问题 · Report an issue](https://github.com/woshibiantai06/yike-focus/issues)

![一刻电脑版专注界面 · Yike Focus desktop timer](assets/desktop.png)

[English desktop screenshot](assets/desktop-en.png) · [Android screenshot](assets/android-en.png)

## 开始使用

| 设备 | 下载后怎么打开 |
| --- | --- |
| 电脑 | 下载 `Yike-Focus-Desktop.html`，保存到固定位置，用 Chrome 或 Edge 打开。之后继续用同一个浏览器和文件路径。 |
| 安卓 8.0 及以上 | 下载 `Yike-Focus-Android.apk`，从系统文件管理器打开。系统询问安装来源时，可只给这次使用的文件管理器授权，安装后可关闭授权。 |

- 在**设置 → 语言 / Language**中切换简体中文和英文。
- 专注默认 25 分钟、短休息 5 分钟，四轮后长休息 15 分钟。正向计时手动完成，休息默认 10 分钟。
- 任务、设置和历史存储在使用它的浏览器或手机里。卸载安卓 App 或清除浏览器数据会丢失本地记录。电脑和手机各自保存。
- 这是公开测试版。锁屏和后台提示音受系统限制。旧的个人预览包和公开包可同时安装，且分别保存记录。

## Getting started

| Device | Open the release download |
| --- | --- |
| Desktop | Save `Yike-Focus-Desktop.html` to a permanent folder and open it in Chrome or Edge. Keep using the same browser and file path. |
| Android 8.0 and later | Open `Yike-Focus-Android.apk` in your file manager. If Android asks for permission to install from that source, you can grant it to the file manager and turn it off again after installation. |

- Switch between Simplified Chinese and English in **Settings → 语言 / Language**.
- Countdown mode defaults to 25 minutes of focus, a 5-minute break, and a 15-minute break after every four sessions. Count-up sessions end manually and have a 10-minute break by default.
- Tasks, settings, and history are stored in the current browser or on the phone. Uninstalling the Android app or clearing browser data removes local records. Desktop and Android store records separately.
- This is a public beta. Lock-screen and background sound delivery depends on your device. The previous personal preview and public beta can be installed side by side and have separate data.

## 开发 · Build from source

Open `index.html` directly to develop the timer. The Android build embeds that exact file.

在 Windows 上构建 Android 包需要 PowerShell、JDK 17 或更高版本、Android SDK Platform 34 和 Build Tools 36.1.0。设置 `JAVA_HOME`，或向脚本传入 `-Jdk`。若 SDK 不在默认位置，可传入 `-Sdk`。

To build on Windows, install PowerShell, JDK 17 or later, Android SDK Platform 34, and Build Tools 36.1.0. Set `JAVA_HOME` or pass `-Jdk`. Pass `-Sdk` for a custom SDK location. Set `YIKE_SIGNING_STORE` and `YIKE_SIGNING_PASSWORD` for your own PKCS#12 keystore with the alias `yike-release`, then run:

```powershell
./android/build-apk.ps1
```

The APK and desktop HTML are written to `release/`. Builds signed with a different key require a separate installation from the official package. For a public fork, choose your own app ID.

## 许可 · License

MIT. The embedded Nunito font is licensed under the SIL Open Font License 1.1. Its full license and copyright notice are included in `index.html`.
