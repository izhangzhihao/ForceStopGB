# English

## Why this module

Android 3.1 introduces [Launch controls](http://developer.android.com/about/versions/android-3.1.html#launchcontrols) on stopped applications. However, there are no such features on android 2.3, some 4.X devices didn't implement it well. Furthermore, there are some way for apps to wakeup some stopped apps.

"`Prevent Running`" hajacks several system API to prevent not-in-use apps in `prevent list` from running or keep running. Furthermore, it applies to system apps too, specially, support google-family apps(`GAPPS`).

Not-in-use packages in `prevent list` can only run:

- some other app call it's activity (share, launcher)
- widget on home, however, it can only run 30 seconds
- sync service if you allow, it can only run 30 seconds too
- system services (excluding normal gapps), or alipay's service

**NOTE**: When Google Play Services(`GMS`) and related apps are in `prevent list`, only `GAPPS` and `GCM`-apps can use it. However, you cannot receive `GCM` message if `GMS` is not running.

**WARNING**: Don't prevent `system` apps nor daily apps, otherwise, you may miss important message.

**WARNING**: Don't prevent "`Xposed Installer`", otherwise the module won't active when you update it.

"`Prevent Running`" should work from android 2.3 to 6.0. However, I mainly use 5.1.

## How to use

1. Install "`Prevent Running`", activate it in "`Xposed Installer`", reboot.
2. Open "`Prevent Running`",  then add/remove application to/from prevent list.
3. Use android normally, press `back` or remove it from recent task to exit, and press `HOME` for pause.

And "`Prevent Running`" would keep non-"service" processes, of cource it cannot turn to "service".

## Special Search

- `-3` for `third` party apps
- `-a` for `a`ll apps (default show third party apps and gapps)
- `-s` for `s`ystem apps
- `-e` for `e`nabled apps
- `-r` for `r`unning apps
- `-g` for `g`apps, i.e. apps from google
- `-sg` for `s`ystem apps excluding `g`apps

## [Importance for Processes](http://developer.android.com/intl/zh-tw/reference/android/app/ActivityManager.RunningAppProcessInfo.html#constants):

### background

This process contains background code that is expendable.

### empty

This process is empty of any actively running code.

### foreground

This process is running the foreground UI; that is, it is the thing currently at the top of the screen that the user is interacting with.

### foreground service (since Android 6.0)

This process is running a foreground service, for example to perform music playback even while the user is not immediately in the app. This generally indicates that the process is doing something the user actively cares about.

### gone (since Android 5.0)

This process does not exist.

### perceptible

This process is not something the user is directly aware of, but is otherwise perceptable to them to some degree.

### service

This process is contains services that should remain running. These are background services apps have started, not something the user is aware of, so they may be killed by the system relatively freely (though it is generally desired that they stay running as long as they want to).

### top sleeping (since Android 6.0)

This process is running the foreground UI, but the device is asleep so it is not visible to the user. This means the user is not really aware of the process, because they can not see or interact with it, but it is quite important because it what they expect to return to once unlocking the device.

### visible

This process is running something that is actively visible to the user, though not in the immediate foreground. This may be running a window that is behind the current foreground (so paused and with its state saved, not interacting with the user, but visible to them to some degree); it may also be running other services under the system's control that it inconsiders important.

## Project

Project: [ForceStopGB - GitHub](https://github.com/liudongmiao/ForceStopGB). If you like, feel free to donate.

# 中文

## 模块介绍

Android 3.1对强行停止的程序引入了[启动控制](http://developer.android.com/about/versions/android-3.1.html#launchcontrols)。但是，在Android 2.3没有这个功能，而有些Android 4.X的设备根本没有实现。再者，很多流氓，总是有办法不断启动。

“`阻止运行`”通过劫持几个系统API，保证`阻止列表`里的应用只在需要时才启动，同时支持谷歌家族应用。

没有运行的`阻止列表`应用只会在以下几种情况下启动：

- 启动器或者第三方应用启动活动(Activity)，如手动打开、分享、部分支付等；
- 桌面小部件定时更新，但是只能运行30秒；
- 同步开启时的定时同步，也只能运行30秒；
- 除`谷歌服务`外的系统服务，或者支付宝的支付服务；
- 其它可能的用户行为引起的启动。

**注意**：当`谷歌服务`在阻止列表时，只有`谷歌家族应用`和第三方的`GCM`应用可以使用。同时，当有任何一个`谷歌家族应用`没有退出时，都不会退出`谷歌服务`。当然，只有`GMS`运行时才能接收`GCM`消息，并唤醒相应应用。

**警告**：请谨慎阻止“系统应用”，以及常用应用。要不然，你可能无法及时收到短信或其它重要消息。“`阻止运行`”不会显示和系统同一签名的系统应用，也不会显示系统内置的启动器。

**警告**：请不要阻止“`Xposed Installer`”，否则模块更新时，无法更新模块路径，导致重启以后无法加载模块。

**提示**：有些用户无法或不愿分清`HOME`与`返回键`区别，可以开启“强行停止后台程序”，在离开程序一段时间后并黑屏时退出应用。这项功能默认关闭。

“`阻止运行`”支持Android 2.3到6.0，本人主要在5.1上测试。(2.3请安装本人移植的xposed框架。）

## 使用说明

1. 安装“`阻止运行`”，在“`Xposed Installer`”中激活它，重启（必须）。
2. 重启后，打开“`阻止运行`”，配置`阻止列表`(这个只需要一次)。
3. 正常使用手机，临时退出时按`HOME`，不用时按`返回键`退出或者从最近列表划掉。

同时，“`阻止运行`”不杀非`服务`的程序，但是保证非`服务`类进程不会变成`服务`在后台***一直***运行。

**高级**：在`Xposed Installer`之外，本程序提供`ROM补丁`模式，只需替换相应文件，即可直接使用“`阻止运行`”。如有需要，请联系作者；或者阅读源码目录`aosp`下的文档。

## 特别搜索

- `-3` 用户安装的第`三`方程序
- `-a` 全部程序（默认仅展示第三方和谷歌家族的程序）
- `-s` 系统预装的程序
- `-e` 没有阻止的程序
- `-r` 正在运行的程序
- `-g` `谷`歌家族的程序
- `-sg` 除谷歌家族外的系统程序

## [进程级别](http://developer.android.com/intl/zh-tw/reference/android/app/ActivityManager.RunningAppProcessInfo.html#constants)

### 后台(background)

可被回收的后台进程。(译者注：或译缓存的后台进程，不需要主动清理。)

### 空(empty)

进程不包含任何正在运行的代码。

### 前台(foreground)

进程正在前台运行，也是你正在使用的应用。（译者注：当你在“`阻止运行`”中查看进程状态时，“`阻止运行`”永远是前台。）

### 前台服务(foreground service, 自Android 6.0)

进程包含前台服务，比如播放音乐等，通常表明正在处理一些用户关心的事情。

### 无(gone, 自Android 5.0)

进程不存在。

### 察觉(perceptible)

虽然用户不能直接注意到，但是某种层次上可以感觉到（译者注：如输入法）。

### 服务(service)

包含需要持续运行的服务。这些是已经启动的后台服务，用户并不能注意到，它们也有可能被系统回收（虽然从设计上会被一直运行下去）。

### 前台休眠(top sleeping，自Android 6.0)

进程在前台运行，但是设备正在休眠，所以用户看不见。这意味着用户并不能真正注意到它，因为看不见也无法操作，但是当设备解锁时期望立即看到，所以也非常重要。

### 可见(visible)

进程对用户可见，虽然不一定是最近的前台。它可能运行在当前前台后的窗口，虽然已经暂停并且保存状态，也无法使用，但是某种层次上用户能见到；也可能是系统控制下的其它重要服务。

## 项目

“`阻止运行`”开源，项目地址：[ForceStopGB - GitHub](https://github.com/liudongmiao/ForceStopGB)。如果喜欢，请随意捐赠。
