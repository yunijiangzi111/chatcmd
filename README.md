# mc-mods

自己做的 Minecraft 模组（mod）合集。

A collection of Minecraft mods I made.

**下载成品 jar：[Releases 版本发布](../../releases)** ｜ **全部版本变更：[CHANGELOG.md 更新日志](CHANGELOG.md)**

**Download ready-to-use jars: [Releases](../../releases)** ｜ **All version changes: [CHANGELOG.md](CHANGELOG.md)**

---

## 模组列表

| 模组 | 简介 | 支持版本 | 状态 |
| --- | --- | --- | --- |
| [ChatCmd 聊天指令](chatcmd/) | 在聊天框里用**中文口语**下达指令，模组自动翻译成原版指令（`/give`、`/tp`、`/time set` 等）并以你自己的身份发出 | Minecraft 1.21.1 / NeoForge | 可用 |

点模组名进入各自的目录，里面有完整的中文 + 英文说明文档。版本变更记录统一放在 [CHANGELOG.md](CHANGELOG.md)。

## 仓库结构

```
mc-mods/
├── LICENSE          # MIT 许可证，适用于本仓库全部内容
├── README.md        # 本文件
├── CHANGELOG.md     # 全部模组的版本变更记录
└── chatcmd/         # 一个模组一个子目录
    ├── README.md    # 该模组的完整文档（中文 + 英文）
    ├── LICENSE      # 该模组的许可证副本
    ├── core/        # 纯 Java 解析引擎，零 Minecraft 依赖
    └── neoforge/    # NeoForge 适配层
```

每个模组都是一个**独立可构建**的 Gradle 工程，互不依赖。

## 构建

需要 **JDK 21**（Minecraft 1.21.1 与这些模组都目标 Java 21）。先让 Gradle 找得到它：

```bat
:: Windows
set JAVA_HOME=C:\path\to\jdk-21
```

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
```

然后进入对应模组目录构建（以 ChatCmd 为例）：

```bat
cd chatcmd
gradlew.bat build
```

产物在 `chatcmd/neoforge/build/libs/`。各模组更细的构建说明见其自己的 README。

## 许可证

本项目使用 **MIT 许可证**，全文见 [LICENSE](LICENSE)。

你可以自由地使用、修改、再分发，甚至可以用于商业用途，唯一的要求是**保留版权声明和许可证文本**。

---

# English

A collection of Minecraft mods I made.

## Mods

| Mod | Description | Supported version | Status |
| --- | --- | --- | --- |
| [ChatCmd](chatcmd/) | Issue commands in the chat box using **Chinese natural language**; the mod translates them into vanilla commands (`/give`, `/tp`, `/time set`, etc.) and sends them as you | Minecraft 1.21.1 / NeoForge | Available |

Click a mod name to open its directory, where the full bilingual documentation lives.

## Repository layout

```
mc-mods/
├── LICENSE          # MIT license, covering everything in this repository
├── README.md        # This file
└── chatcmd/         # One directory per mod
    ├── README.md    # Full documentation for that mod (Chinese + English)
    ├── LICENSE      # Copy of the license for that mod
    ├── core/        # Pure-Java parsing engine, zero Minecraft dependencies
    └── neoforge/    # NeoForge adapter layer
```

Each mod is an **independently buildable** Gradle project with no cross-dependencies.

## Building

Requires **JDK 21** (Minecraft 1.21.1 and these mods all target Java 21). First make sure Gradle can find it:

```bat
:: Windows
set JAVA_HOME=C:\path\to\jdk-21
```

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
```

Then build inside the mod's directory (ChatCmd shown as an example):

```bat
cd chatcmd
gradlew.bat build
```

Output lands in `chatcmd/neoforge/build/libs/`. See each mod's own README for more detailed build instructions.

## License

This project is licensed under the **MIT License**. The full text is available in the [LICENSE](LICENSE) file.

You are free to use, modify, and redistribute it, including for commercial purposes, provided you **retain the copyright notice and the license text**.
