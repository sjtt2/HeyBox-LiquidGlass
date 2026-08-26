![HeyBox-LiquidGlass](https://socialify.git.ci/sjtt2/HeyBox-LiquidGlass/image?custom_description=%E4%B8%BA%E5%B0%8F%E9%BB%91%E7%9B%92+App+%E6%89%93%E9%80%A0%E7%9A%84+iOS+26+%E9%A3%8E%E6%A0%BC%E6%B6%B2%E6%80%81%E7%8E%BB%E7%92%83%E5%BA%95%E9%83%A8%E5%AF%BC%E8%88%AA%E6%A0%8F+%C2%B7+LSPosed+%E6%A8%A1%E5%9D%97%EF%BC%88libxposed+API+102%EF%BC%89&description=1&font=JetBrains+Mono&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fsjtt2%2FHeyBox-LiquidGlass%2Fff221e1f7b233823ce6dfcee8e1af3c52efe1afe%2Fres%2Fmipmap-xxxhdpi%2Fic_launcher.png&name=1&owner=1&pattern=Plus&pulls=1&stargazers=1&theme=Auto)
# 小黑盒液态玻璃 HeyBox-LiquidGlass

[![API](https://img.shields.io/badge/libxposed-API%20102-brightgreen)](https://github.com/libxposed/api)
[![Platform](https://img.shields.io/badge/platform-Android%2013%2B-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)


## ✨ 特性

- 🫧 **真·液态玻璃**：单 pass AGSL 透镜管线 —— 圆角矩形 SDF 折射、边缘色散、重力传感器高光
- 💧 **玻璃滴选中动效**：切换标签时液滴滑动、拉伸、回弹，支持手指拖拽吸附
- 🌗 **深浅色跟随**：读取应用真实主题（uiMode）设定前景黑白，运行时再由背景亮度感知实时微调
- ⚙️ **内置设置**：设置 → 长按「通用设置」打开液态玻璃配置（暗/亮底色与不透明度、反色开关、恢复默认）
- ♿ **降级兜底**：Android 13 以下自动退化为轻磨砂管线，渲染器异常不崩溃

## 📦 安装

1. 准备一个**支持 libxposed API 102** 的 LSPosed 环境
2. 从 [Releases](../../releases) 下载 APK 并安装
3. 在 LSPosed 中启用本模块（作用域已限定 `com.max.xiaoheihe`）
4. **强制停止**小黑盒后重新打开

> 已在小黑盒 `v1.3.392 (versionCode 1114)` 上测试。其他版本如资源 ID 有变动会自动放弃注入并输出日志。

> [!WARNING]
> 使用免Root框架'「NPatch」'时，需要把'破解签名校验'改成'Extreme'，不然会有缺少参数闪退的问题


## 🔍 工作原理

```
Hook Instrumentation.callActivityOnResume
  └─ MainActivity 布局手术：
       fl_container 解除底部约束（内容延伸至屏幕底）
       rg_main / vg_tips / vg_mid_tab 迁入玻璃容器
       └─ API 33+：挂载 LiquidGlassTabBar
            ├─ 原 RadioGroup 隐藏保留（选中状态机制照常工作）
            ├─ 点击玻璃滴   → rb.performClick() → 应用原生切页
            ├─ 应用内切页   → RadioGroup 监听(反射包装) → 液滴滑动
            └─ 发布按钮     → 复用原 vg_mid_tab，居中于预留空位
```

渲染层基于 [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)，
通过 AAR 字节码合并方式集成（含 kotlin-stdlib 与原生模糊库），零位图、全 GPU。

日志 tag：`HeyBoxLiquidGlass`

## 🛠️ 构建

无需 Gradle/Android Studio，脚本直接调用 JDK + build-tools：

```powershell
# 1. 按 build.ps1 头部注释下载工具链（android.jar / build-tools 34 /
#    libxposed api AAR / QWEA0 main AAR / kotlin-stdlib），放入 $ToolRoot
# 2. 执行
.\build.ps1
```

产物输出至仓库上级目录 `HeyBoxLiquidGlass-vX.Y.Z.apk`，自动生成 debug 签名。

### 可调参数

| 参数 | 位置 | 默认 |
|---|---|---|
| 折射强度 / 边缘带 | `attachQwea0TabBar()` | 60dp / 16dp |
| 色散强度 | 同上 | 0.12 |
| 中央空隙宽度 | `CENTER_GAP_WEIGHT` | 1.3 |
| 主题探测方式 | uiMode（跟随应用与系统） | — |

## ⚠️ 已知限制

- 需要 Android 13+；更低版本仅提供轻磨砂降级效果
- 导航栏覆盖区域内的列表底部条目需滚动后才可见（沉浸式底栏的固有取舍）
- 小黑盒大版本更新可能导致资源 ID 变化，模块将静默放弃注入

## 🙏 致谢

- [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) — MIT，液态玻璃渲染管线
- [libxposed/api](https://github.com/libxposed/api) — Apache-2.0，现代 Xposed 模块 API
- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — Apache-2.0，早期方案参考

## 📄 License

[MIT](LICENSE) © 2026 sjtt2

vendored 代码保留原作者版权声明（见 `src/com/qmdeve/liquidglass/` 文件头）。
