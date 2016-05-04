“阻止运行”支持非Xposed模式，但是需要改动系统。改动有两种方式，一是源码方式，二是直接smali方式。目前提供android 5.X和android 6.0的补丁，本文适用于android 6.0。

* 说明

```
命令行> 命令
```

表示在`电脑`的`命令行`中执行`命令`，如果没有特别声明，Linux/Mac OS X/Windows下均可使用。

# smali 方式

## 需求
- [java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 运行smali/baksmali需要Java，请下载JDK。
- [smali](http://github.com/JesusFreke/smali) 把smali源码编译成dex，请使用2.1.1及以上版本，二进制下载在 [smali ‐ Bitbucket](https://bitbucket.org/JesusFreke/smali/downloads) 上。
- [baksmali](http://github.com/JesusFreke/smali) 把dex反编译成smali，请使用2.1.1及以上版本，二进制下载`也`在 [smali ‐ Bitbucket](https://bitbucket.org/JesusFreke/smali/downloads) 上。
- patch 打补丁，Linux/Mac OS X下自带，windows需要下载[Patch for Windows](http://gnuwin32.sourceforge.net/packages/patch.htm)，另外[Git for Windows](https://git-for-windows.github.io/)也自带。
- [api-23.smali.patch](api-23.smali.patch), [api-23.patch](api-23.patch)
- (可选) [oat2dex](https://github.com/testwhat/SmaliEx/) 把oat转换成dex，二进制下载在 [Releases - testwhat/SmaliEx](https://github.com/testwhat/SmaliEx/releases) 上。

## 从系统中获取 `services.jar`

```
命令行> adb pull /system/framework/services.jar
```

如果`services.jar`很小，只有几百个字节的话，那么系统是经过优化的，需要下载`services.odex`以及`boot.oat`。

## 反编译 `services`

### 反编译 `services.jar`

`baksmali`加上`-b -s`参数是为了去掉调试信息，因为补丁文件也是这样生成的。

```
命令行> java -jar baksmali.jar -a 23 -b -s services.jar -o services
```

### 反编译 `services.odex`

`baksmali`支持6.0的OAT格式，但是不支持5.X的OAT格式。所以，5.X需要额外下载`oat2dex`。

```
命令行> java -jar baksmali.jar -a 23 -x -c boot.oat -d . -b -s services.odex -o services
```

## 打补丁

```
Linux/Mac OS X命令行> # Linux / Mac OS X使用
Linux/Mac OS X命令行> patch -p0 < api-23.smali.patch
```

在Windows的某些版本下(如windows 7)，名称中含`patch`的程序，必须额外加上特定的`manifest`，否则不能运行，所以换个名字吧。此外，由于补丁文件不是在Windows下生成，所以需要先转换成Windows的\r\n格式。

```
Windows命令行> :: Windows 使用
Windows命令行> move patch.exe p@tch.exe
Windows命令行> type api-23.smali.patch | more /p > api-23.win32.smali.patch
Windows命令行> p@tch.exe -p0 < api-23.win32.smali.patch
```

如果有出现任何 `*.orig` 文件，都需要仔细确认；如果无法确认，请联系作者。

## 重新打包 `services.jar`

`smali`加上`-j 1`参数可以保证每次生成的dex文件一样。

```
命令行> java -jar smali.jar -a 23 -j 1 -o classes.dex services
命令行> jar -cvf services.jar classes.dex
```

## 更新 `services.jar`

把 `services.jar` 放入系统 `/system/framework/`，重启。

## 进阶方式

把改过后的文件，打成一个包 `pr.jar`，然后改动 `boot.img` 中的 `init.environ.rc`，在这个前面加入 `pr.jar`，可以不用改动系统分区。

# 源码 方式

这个方式适合从头开始编译的ROM系统，只需要在 `frameworks/base` 下应用 `api-23.patch` 补丁即可。

```
命令行> cd frameworks/base
命令行> patch -p1 < /path/to/api-23.patch
命令行> mmm services:services
```
