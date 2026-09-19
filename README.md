# ChatCmd 聊天指令

用中文在聊天框里下达 Minecraft 指令。输入 `#给我1个钻石剑`，模组会把它翻译成原版指令 `/give @s minecraft:diamond_sword 1`，并以你自己的身份发出去。

当前版本：**0.8.1**

中文 | [English](#english)

---

## 项目简介

ChatCmd 是一个 Minecraft **客户端模组（client-side mod）**，它拦截聊天框里以 `#` 开头的输入，把**中文口语**翻译成**原版指令**，再以玩家自己的身份发送出去。

三条核心设计原则：

1. **中文口语 → 原版指令**：只做翻译，不做别的。翻译结果就是一条普通的原版指令。
2. **不绕过任何权限（permission）**：模组只是替你打字，指令以你的身份发出，能否生效完全由服务端（server-side）按你的权限判定。
3. **不静默改词**：模糊纠错（fuzzy matching）命中时一定用黄色文字明说纠成了什么；不确定时宁可报错，也不猜。

架构上分成三个模块：

- `core` —— 纯 Java 解析引擎，零 Minecraft 依赖，可脱离游戏单独跑单元测试。
- `neoforge` —— NeoForge（一个 Minecraft 模组加载器）适配层，对应 Minecraft 1.21.1。
- `forge` —— Forge（另一个 Minecraft 模组加载器）适配层，对应 Minecraft 1.20.1。

两个适配层共用同一份 `core`，差异只有两处：平台 API 的调用方式，以及附魔指令的语法（1.20.5 起改用物品组件语法，1.20.1 还是 NBT 语法）。

## 安装

本模组同时提供两个平台：

| 你要玩的版本 | 装哪个 jar | 需要的前置 |
| --- | --- | --- |
| Minecraft **1.21.1** | `chatcmd-0.8.1-neoforge+mc1.21.1.jar` | NeoForge（一个 Minecraft 模组加载器）**21.1.228** 或更高 |
| Minecraft **1.20.1** | `chatcmd-0.8.1-forge+mc1.20.1.jar` | Forge（另一个 Minecraft 模组加载器）**47.3.0** 或更高 |

Java（一种编程语言运行环境）随游戏自带，两个版本都不需要单独安装。

安装方式：

1. 把对应平台的 jar 放进 `.minecraft/mods` 目录。
2. 启动游戏。

这是**客户端模组（client-side mod）**：只装在客户端即可，**服务端不需要安装**。NeoForge 侧声明了 `dist = Dist.CLIENT`，Forge 侧声明了 `clientSideOnly=true`，即使误装到专用服务器上也不会加载客户端类，不会导致崩服。

## 快速开始

在聊天框里输入以 `#` 开头的文字即可。例如：

```
#给我1个钻石剑
```

模组会发出 `/give @s minecraft:diamond_sword 1`，并在聊天框里用灰色文字回显实际发出去的指令：

```
[ChatCmd] 已执行：/give @s minecraft:diamond_sword 1
```

再比如：

| 你输入 | 实际发出 |
| --- | --- |
| `#给我1个钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#时间 白天` | `/time set day` |
| `#传送 100 64 100` | `/tp @s 100 64 100` |
| `#模式 创造` | `/gamemode creative` |
| `#效果 速度 2 30` | `/effect give @s minecraft:speed 30 1` |
| `#找 村庄` | `/locate structure #minecraft:village` |

**快捷键**：按 `#`（即 Shift+3）可以直接打开聊天框并预填一个 `#`，省掉「先按 T 再打 #」这一步（原版的 `/` 键就是这么做的）。可以在「选项 → 控制」的「ChatCmd 聊天指令」分类里自行改键。注意数字键本身也是原版的快捷栏切换键，所以按住 Shift 按 3 有可能连带切换快捷栏第 3 格，嫌烦就改绑到别的键。

## 支持的指令

下表每一行都对应源码里的一个指令类型。所有指令在发送时都**不含前导斜杠**（表格里加上 `/` 只是为了方便阅读）。

### 给我（give）

动词别名：`给我`、`给`、`give`

用法：`#给我 <物品> [数量]`（数量省略时为 1）

| 你输入 | 实际发出 |
| --- | --- |
| `#给我 钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 钻石剑 5` | `/give @s minecraft:diamond_sword 5` |
| `#给我1个钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 齿轮1` | `/give @s <齿轮的物品 ID> 1` |

物品解析分三级：

1. **手工别名表**（下表 55 项）；
2. **游戏注册表（registry）本地化名称**：第一次用到时遍历全部物品，把「当前语言的物品显示名 → 物品 ID」全量建表。整合包里那几百个模组物品都能认，不必手写进别名表；
3. **原版 ID 原样透传**：输入看起来像原版 ID（纯小写字母/数字/下划线/点/横线，可带命名空间）时直接使用，例如 `#给我 diamond_sword 3`、`#给我 create:cogwheel`。

模糊纠错**只对手工别名表**生效（见下文「规则与安全设计」），因为模组物品名彼此高度相似（齿轮 / 大齿轮 / 小齿轮），自动猜错等于静默给错东西。

手工别名表：

| 中文名 | 原版物品 ID | 中文名 | 原版物品 ID |
| --- | --- | --- | --- |
| 钻石剑 | `minecraft:diamond_sword` | 铁剑 | `minecraft:iron_sword` |
| 钻石镐 | `minecraft:diamond_pickaxe` | 铁镐 | `minecraft:iron_pickaxe` |
| 钻石斧 | `minecraft:diamond_axe` | 铁锭 | `minecraft:iron_ingot` |
| 钻石锹 | `minecraft:diamond_shovel` | 金剑 | `minecraft:golden_sword` |
| 钻石锄 | `minecraft:diamond_hoe` | 金锭 | `minecraft:gold_ingot` |
| 钻石 | `minecraft:diamond` | 石剑 | `minecraft:stone_sword` |
| 钻石块 | `minecraft:diamond_block` | 木剑 | `minecraft:wooden_sword` |
| 下界合金剑 | `minecraft:netherite_sword` | 下界合金锭 | `minecraft:netherite_ingot` |
| 弓 | `minecraft:bow` | 箭 | `minecraft:arrow` |
| 盾牌 | `minecraft:shield` | 石头 | `minecraft:stone` |
| 圆石 | `minecraft:cobblestone` | 泥土 | `minecraft:dirt` |
| 草方块 | `minecraft:grass_block` | 橡木原木 | `minecraft:oak_log` |
| 木板 | `minecraft:oak_planks` | 玻璃 | `minecraft:glass` |
| 沙子 | `minecraft:sand` | 黑曜石 | `minecraft:obsidian` |
| 基岩 | `minecraft:bedrock` | 火把 | `minecraft:torch` |
| 箱子 | `minecraft:chest` | 工作台 | `minecraft:crafting_table` |
| 熔炉 | `minecraft:furnace` | 漏斗 | `minecraft:hopper` |
| 发射器 | `minecraft:dispenser` | 命令方块 | `minecraft:command_block` |
| 红石 | `minecraft:redstone` | 煤炭 | `minecraft:coal` |
| 木棍 | `minecraft:stick` | 苹果 | `minecraft:apple` |
| 金苹果 | `minecraft:golden_apple` | 附魔金苹果 | `minecraft:enchanted_golden_apple` |
| 面包 | `minecraft:bread` | 小麦 | `minecraft:wheat` |
| 胡萝卜 | `minecraft:carrot` | 马铃薯 | `minecraft:potato` |
| 甘蔗 | `minecraft:sugar_cane` | 生牛肉 | `minecraft:beef` |
| 牛排 | `minecraft:cooked_beef` | 腐肉 | `minecraft:rotten_flesh` |
| 末影珍珠 | `minecraft:ender_pearl` | 药水 | `minecraft:potion` |
| 水桶 | `minecraft:water_bucket` | 熔岩桶 | `minecraft:lava_bucket` |
| tnt | `minecraft:tnt` | | |

### 时间（time）

动词别名：`时间`、`时刻`、`time`

用法：`#时间 <白天|正午|夜晚|午夜>`

| 中文取值（别名） | 原版取值 | 生成的指令 |
| --- | --- | --- |
| 白天、早上、早晨、`day` | `day` | `/time set day` |
| 正午、中午、`noon` | `noon` | `/time set noon` |
| 夜晚、晚上、傍晚、黄昏、`night` | `night` | `/time set night` |
| 午夜、凌晨、`midnight` | `midnight` | `/time set midnight` |

### 传送（teleport）

动词别名：`传送`、`tp`、`teleport`

用法：`#传送 <x> <y> <z>`

坐标支持 `~`、`~5`、`~-3`、整数 `100`、小数 `-64.5`。

| 你输入 | 实际发出 |
| --- | --- |
| `#传送 100 64 100` | `/tp @s 100 64 100` |
| `#传送 ~ ~5 ~-3` | `/tp @s ~ ~5 ~-3` |
| `#传送 100,64,100` | `/tp @s 100 64 100` |

### 模式（gamemode）

动词别名：`模式`、`gamemode`

用法：`#模式 <创造|生存|冒险|旁观>`

| 中文取值（别名） | 原版取值 | 生成的指令 |
| --- | --- | --- |
| 创造、创意、`creative` | `creative` | `/gamemode creative` |
| 生存、`survival` | `survival` | `/gamemode survival` |
| 冒险、冒险家、`adventure` | `adventure` | `/gamemode adventure` |
| 旁观、旁观者、`spectator` | `spectator` | `/gamemode spectator` |

### 天气（weather）

动词别名：`天气`、`weather`

用法：`#天气 <晴|雨|雷>`

| 中文取值（别名） | 原版取值 | 生成的指令 |
| --- | --- | --- |
| 晴、晴天、晴朗、放晴、`clear` | `clear` | `/weather clear` |
| 雨、下雨、`rain` | `rain` | `/weather rain` |
| 雷、雷雨、打雷、`thunder` | `thunder` | `/weather thunder` |

### 难度（difficulty）

动词别名：`难度`、`difficulty`

用法：`#难度 <和平|简单|普通|困难>`

| 中文取值（别名） | 原版取值 | 生成的指令 |
| --- | --- | --- |
| 和平、`peaceful` | `peaceful` | `/difficulty peaceful` |
| 简单、`easy` | `easy` | `/difficulty easy` |
| 普通、`normal` | `normal` | `/difficulty normal` |
| 困难、`hard` | `hard` | `/difficulty hard` |

### 规则（gamerule）

动词别名：`游戏规则`、`规则`、`gamerule`

用法：`#规则 <规则> <开|关>`

| 中文规则名（别名） | 原版规则名 |
| --- | --- |
| 死亡不掉落、保留物品、不掉落物品、死亡不丢东西、`keepinventory` | `keepInventory` |
| 生物破坏、怪物破坏、生物变方块、`mobgriefing` | `mobGriefing` |
| 昼夜交替、日夜交替、白天黑夜交替、`dodaylightcycle` | `doDaylightCycle` |
| 天气变化、天气循环、`doweathercycle` | `doWeatherCycle` |
| 自然回血、自然恢复、自然回复、生命恢复、生命回复、生命自然恢复、自动回血、回血、`naturalregeneration` | `naturalRegeneration` |
| 死亡消息、死亡提示、`showdeathmessages` | `showDeathMessages` |
| 怪物生成、生物生成、刷怪、`domobspawning` | `doMobSpawning` |
| 火势蔓延、火焰蔓延、`dofiretick` | `doFireTick` |
| 立即重生、立刻重生、`doimmediaterespawn` | `doImmediateRespawn` |

开关取值：`开`、`开启`、`打开`、`启用`、`是`、`true`、`on` → `true`；`关`、`关闭`、`关掉`、`禁用`、`否`、`false`、`off` → `false`。

同一条规则一个人一个叫法，「自然回血」和「生命恢复」其实是同一条 `naturalRegeneration`，所以上表把常见叫法尽量收全了。

表里都没有时还会走一步**模糊纠错**：打错一个字会被自动纠正，并在回显里用黄色文字明说纠成了什么。例如 `#规则 死亡不掉洛 开` 会被纠成「死亡不掉落」。

连模糊纠错也认不出时，**在本地报错并列出可点击候选**，绝不把认不出的规则名原样透传出去 —— 输入在归一化阶段已经转成小写，原版那些驼峰命名的规则透传出去本来就无法生效，你只会收到一句莫名其妙的「未知的游戏规则」。

| 你输入 | 实际发出 |
| --- | --- |
| `#规则 死亡不掉落 开` | `/gamerule keepInventory true` |
| `#规则 昼夜交替 关` | `/gamerule doDaylightCycle false` |
| `#规则 生命恢复 开` | `/gamerule naturalRegeneration true` |

### 重置（reset）

动词别名：`重置`、`恢复`、`复原`

用法：`#重置 [时间|天气|效果|规则|全部]`，不写参数等同于「全部」。

| 中文取值（别名） | 实际发出的指令 |
| --- | --- |
| 时间、昼夜、昼夜交替、白天黑夜 | `/gamerule doDaylightCycle true` |
| 天气、天气变化 | `/gamerule doWeatherCycle true`、`/weather clear` |
| 效果、药水效果、状态 | `/effect clear @s` |
| 规则、游戏规则 | `/gamerule keepInventory false`、`/gamerule mobGriefing true` |
| 全部、所有、一切（或省略参数） | 以上全部，共 6 条指令 |

「时间」是把昼夜交替重新打开，用来结束「永为白昼 / 永为黑夜」。

**「全部」（含省略参数）属于高危指令，需要二次确认**：发出去后不会立刻执行，而是先弹一条黄色警告说明将要执行哪几条指令，再发一条 `#确认` 才真正执行（见下文「高危指令的二次确认」）。其余取值（时间 / 天气 / 效果 / 规则）影响范围有限，直接执行。

### 效果（effect）

动词别名：`效果`、`状态效果`、`buff`

用法：`#效果 <效果名> [等级] [时长]`

参数顺序是**等级在前、时长在后**（跟口语习惯一致）。带 `秒` / `s` 单位的那个永远算时长，所以 `#效果 速度 30秒 2` 也认。

- 时长省略时默认 **30 秒**（原版 `/effect give` 不写时长时的默认值）；
- 等级省略时默认 **1 级**；
- 原版这条指令的第 3 个参数是 amplifier（放大器），`0` 才是 I 级，所以你说的「等级 2」会被换算成 `1`，模组会在回显里注明这个换算。

| 你输入 | 实际发出 |
| --- | --- |
| `#效果 速度` | `/effect give @s minecraft:speed 30 0` |
| `#效果 速度 2 30` | `/effect give @s minecraft:speed 30 1` |
| `#效果 速度 30秒 2` | `/effect give @s minecraft:speed 30 1` |

效果名表（中文名与英文名都可用）：

| 中文名（英文别名） | 原版效果 ID | 中文名（英文别名） | 原版效果 ID |
| --- | --- | --- | --- |
| 速度（`speed`）、迅捷 | `minecraft:speed` | 缓慢（`slowness`）、迟缓 | `minecraft:slowness` |
| 急迫（`haste`）、急速 | `minecraft:haste` | 挖掘疲劳（`mining_fatigue`） | `minecraft:mining_fatigue` |
| 力量（`strength`） | `minecraft:strength` | 瞬间治疗（`instant_health`） | `minecraft:instant_health` |
| 瞬间伤害（`instant_damage`） | `minecraft:instant_damage` | 跳跃提升（`jump_boost`） | `minecraft:jump_boost` |
| 反胃（`nausea`） | `minecraft:nausea` | 生命恢复（`regeneration`） | `minecraft:regeneration` |
| 抗性提升（`resistance`） | `minecraft:resistance` | 防火（`fire_resistance`） | `minecraft:fire_resistance` |
| 水下呼吸（`water_breathing`） | `minecraft:water_breathing` | 隐身（`invisibility`） | `minecraft:invisibility` |
| 失明（`blindness`） | `minecraft:blindness` | 夜视（`night_vision`） | `minecraft:night_vision` |
| 饥饿（`hunger`） | `minecraft:hunger` | 虚弱（`weakness`） | `minecraft:weakness` |
| 中毒（`poison`） | `minecraft:poison` | 凋零（`wither`） | `minecraft:wither` |
| 生命提升（`health_boost`） | `minecraft:health_boost` | 伤害吸收（`absorption`） | `minecraft:absorption` |
| 饱和（`saturation`） | `minecraft:saturation` | 发光（`glowing`） | `minecraft:glowing` |
| 漂浮（`levitation`） | `minecraft:levitation` | 幸运（`luck`） | `minecraft:luck` |
| 霉运（`unluck`） | `minecraft:unluck` | 缓降（`slow_falling`） | `minecraft:slow_falling` |
| 潮涌能量（`conduit_power`） | `minecraft:conduit_power` | 海豚的恩惠（`dolphins_grace`） | `minecraft:dolphins_grace` |
| 不祥之兆（`bad_omen`） | `minecraft:bad_omen` | 村庄英雄（`hero_of_the_village`） | `minecraft:hero_of_the_village` |
| 黑暗（`darkness`） | `minecraft:darkness` | 试炼之兆（`trial_omen`） | `minecraft:trial_omen` |
| 袭击之兆（`raid_omen`） | `minecraft:raid_omen` | 风袭（`wind_charged`） | `minecraft:wind_charged` |
| 织网（`weaving`） | `minecraft:weaving` | 渗浆（`oozing`） | `minecraft:oozing` |
| 寄生（`infested`） | `minecraft:infested` | | |

### 附魔（enchant）

动词别名：`附魔`

用法：`#附魔 <物品> <附魔><等级>`，多个附魔用逗号或空格隔开。

附魔指令的写法**随 Minecraft 版本变化**，模组按平台自动选：

- **Minecraft 1.20.5 起**（本模组的 neoforge 版）：物品数据改成了「物品组件（item components）」语法，用新的 `[enchantments={...}]` 写法，老的 `stick{Enchantments:[...]}` 写法已被原版移除。
- **Minecraft 1.20.4 及以前**（本模组的 forge 1.20.1 版）：还是 NBT（一种数据格式）写法 `{Enchantments:[{id:...,lvl:...s}]}`。

| 你输入 | neoforge 版（1.21.1）实际发出 | forge 版（1.20.1）实际发出 |
| --- | --- | --- |
| `#附魔 钻石剑 锋利5` | `/give @s minecraft:diamond_sword[enchantments={"minecraft:sharpness":5}]` | `/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s}]}` |
| `#附魔 钻石剑 锋利5,耐久3` | `/give @s minecraft:diamond_sword[enchantments={"minecraft:sharpness":5,"minecraft:unbreaking":3}]` | `/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s},{id:"minecraft:unbreaking",lvl:3s}]}` |

只有这一条指令受版本影响，`#附魔手持`（走原版 `/enchant`）和其它所有指令在两个版本下完全一样。

附魔等级省略时默认 1 级，并且**原样透传**，不做「口语减一」的换算。

### 附魔手持（enchant held）

动词别名：`附魔手持`、`手持附魔`、`enchant`

用法：`#附魔手持 <附魔><等级>`，多个附魔用逗号或空格隔开。

原版 `/enchant` 一次只能附一个，所以写多个附魔时会**拆成多条指令依次发出**，并在回显里说明。

| 你输入 | 实际发出 |
| --- | --- |
| `#附魔手持 锋利5` | `/enchant @s minecraft:sharpness 5` |
| `#附魔手持 锋利5,耐久3` | `/enchant @s minecraft:sharpness 5`、`/enchant @s minecraft:unbreaking 3` |

**服务器改过 `/enchant` 怎么办**：部分服务器（或服务端插件）会把原版 `/enchant <目标> <附魔> [等级]` 换成带 `add`（添加）/ `remove`（移除）子指令的形式，这时直接发原版写法只会得到「错误的命令参数」。指令树由服务器下发到客户端，模组在发送前会读一遍它：只要发现这台服务器的 `/enchant` 必须有 `add`，就自动补成 `/enchant add @s minecraft:sharpness 5`；原版环境一个字符都不动，也不会有额外提示。

附魔名表（中文名与英文名都可用）：

| 中文名（英文别名） | 原版附魔 ID | 中文名（英文别名） | 原版附魔 ID |
| --- | --- | --- | --- |
| 锋利（`sharpness`） | `minecraft:sharpness` | 亡灵杀手（`smite`） | `minecraft:smite` |
| 节肢杀手（`bane_of_arthropods`） | `minecraft:bane_of_arthropods` | 击退（`knockback`） | `minecraft:knockback` |
| 火焰附加（`fire_aspect`） | `minecraft:fire_aspect` | 抢夺（`looting`） | `minecraft:looting` |
| 横扫之刃（`sweeping_edge`） | `minecraft:sweeping_edge` | 效率（`efficiency`） | `minecraft:efficiency` |
| 精准采集（`silk_touch`） | `minecraft:silk_touch` | 耐久（`unbreaking`） | `minecraft:unbreaking` |
| 时运（`fortune`） | `minecraft:fortune` | 力量（`power`） | `minecraft:power` |
| 冲击（`punch`） | `minecraft:punch` | 火矢（`flame`） | `minecraft:flame` |
| 无限（`infinity`） | `minecraft:infinity` | 引雷（`channeling`） | `minecraft:channeling` |
| 忠诚（`loyalty`） | `minecraft:loyalty` | 激流（`riptide`） | `minecraft:riptide` |
| 穿刺（`impaling`） | `minecraft:impaling` | 多重射击（`multishot`） | `minecraft:multishot` |
| 快速装填（`quick_charge`） | `minecraft:quick_charge` | 穿透（`piercing`） | `minecraft:piercing` |
| 保护（`protection`） | `minecraft:protection` | 火焰保护（`fire_protection`） | `minecraft:fire_protection` |
| 摔落保护（`feather_falling`） | `minecraft:feather_falling` | 爆炸保护（`blast_protection`） | `minecraft:blast_protection` |
| 弹射物保护（`projectile_protection`） | `minecraft:projectile_protection` | 水下呼吸（`respiration`） | `minecraft:respiration` |
| 水下速掘（`aqua_affinity`） | `minecraft:aqua_affinity` | 荆棘（`thorns`） | `minecraft:thorns` |
| 深海探索者（`depth_strider`） | `minecraft:depth_strider` | 冰霜行者（`frost_walker`） | `minecraft:frost_walker` |
| 灵魂疾行（`soul_speed`） | `minecraft:soul_speed` | 迅捷潜行（`swift_sneak`） | `minecraft:swift_sneak` |
| 经验修补（`mending`） | `minecraft:mending` | 绑定诅咒（`binding_curse`） | `minecraft:binding_curse` |
| 消失诅咒（`vanishing_curse`） | `minecraft:vanishing_curse` | | |

### 经验（xp）

动词别名：`经验`、`经验值`、`xp`

用法：`#经验 <数量>`

| 你输入 | 实际发出 |
| --- | --- |
| `#经验 100` | `/xp add @s 100` |

### 清除（clear）

动词别名：`清除`、`清掉`

用法：`#清除 <掉落物|经验球|怪物>`

| 中文取值（别名） | 实际发出的指令 |
| --- | --- |
| 掉落物、物品、地上的东西 | `/kill @e[type=minecraft:item]` |
| 经验球、经验 | `/kill @e[type=minecraft:experience_orb]` |
| 效果、药水效果 | `/effect clear @s` |
| 怪物、生物 | `/kill @e[type=!player,type=!minecraft:item,type=!minecraft:experience_orb]` |

注意「怪物」这一项：原版没有「只杀敌对生物」的选择器，所以它排除玩家、掉落物和经验球之后把**剩下的全部**清掉 —— 动物和村民也会被一起清掉。

**所有 `#清除` 都属于高危指令，需要二次确认**：发出去后不会立刻执行，而是先弹一条黄色警告，告诉你这条指令真正会清掉哪些东西（例如「怪物」那一条会明确说「动物、村民、已驯服的宠物也会一起被清掉」），再发一条 `#确认` 才真正执行（见下文「高危指令的二次确认」）。

这个动词**禁止模糊纠错**（见下文「规则与安全设计」）。

### 找（locate structure）

动词别名：`最近的`、`最近`、`找结构`、`寻找`、`查找`、`找`、`locate`

用法：`#找 <结构名>`

结构名表：

| 中文名（别名） | 原版结构 ID |
| --- | --- |
| 村庄、村子 | `#minecraft:village` |
| 平原村庄 | `minecraft:village_plains` |
| 沙漠村庄 | `minecraft:village_desert` |
| 雪原村庄 | `minecraft:village_snowy` |
| 热带草原村庄 | `minecraft:village_savanna` |
| 针叶林村庄 | `minecraft:village_taiga` |
| 掠夺者前哨站、掠夺者哨塔、哨塔 | `minecraft:pillager_outpost` |
| 林地府邸、府邸 | `minecraft:mansion` |
| 要塞 | `minecraft:stronghold` |
| 废弃矿井 | `minecraft:mineshaft` |
| 矿洞 | `#minecraft:mineshaft` |
| 海底神殿、海底宫殿 | `minecraft:monument` |
| 沉船 | `#minecraft:shipwreck` |
| 海底废墟 | `#minecraft:ocean_ruin` |
| 古迹废墟 | `minecraft:trail_ruins` |
| 沙漠神殿 | `minecraft:desert_pyramid` |
| 丛林神庙、神庙 | `minecraft:jungle_pyramid` |
| 沼泽小屋、女巫小屋 | `minecraft:swamp_hut` |
| 雪屋、冰屋 | `minecraft:igloo` |
| 远古城市 | `minecraft:ancient_city` |
| 试炼密室、试炼厅 | `minecraft:trial_chambers` |
| 废弃传送门 | `#minecraft:ruined_portal` |
| 下界要塞、地狱要塞 | `minecraft:fortress` |
| 堡垒遗迹、堡垒 | `minecraft:bastion_remnant` |
| 末地城 | `minecraft:end_city` |
| 紫水晶洞 | `minecraft:amethyst_geode` |
| 沙漠水井 | `minecraft:desert_well` |
| 埋藏的宝藏 | `minecraft:buried_treasure` |
| 下界化石 | `minecraft:nether_fossil` |

能用结构标签（tag）的优先用标签，这样「村庄」一次就能找到平原/沙漠/雪原等全部变体。

| 你输入 | 实际发出 |
| --- | --- |
| `#找 村庄` | `/locate structure #minecraft:village` |
| `#最近的村庄` | `/locate structure #minecraft:village` |

查找成功后，模组会额外给出**一行可点击的坐标**：绿色带下划线，鼠标悬停显示「点击直接传送到 x ~ z」，**单击立刻传送**（`/tp @s x ~ z`）。纵坐标沿用原版回执里的 `~`，保持玩家当前高度。

### 生成（summon）

动词别名：`生成`、`召唤`、`刷怪`、`summon`、`spawn`

用法：`#生成 <实体> [数量] [x y z]`

| 你输入 | 实际发出 |
| --- | --- |
| `#生成 僵尸` | `/summon minecraft:zombie` |
| `#生成 坚守者 3` | `/summon minecraft:warden`、共 3 条 |
| `#生成 末影龙 ~ ~5 ~` | `/summon minecraft:ender_dragon ~ ~5 ~` |
| `#召唤 苦力怕 2 ~ ~2 ~` | `/summon minecraft:creeper ~ ~2 ~`、共 2 条 |

原版 `/summon` 一次只能生成一个实体，所以数量大于 1 时会**自动拆成多条指令依次发出**，并在回显里说明。为免刷屏，数量上限为 **16**。

不写坐标时在玩家脚下生成；坐标支持与 `#传送` 完全相同的写法（绝对坐标 `100 64 100`、相对坐标 `~ ~5 ~-3`、逗号分隔 `100,64,100`）。

| 分类 | 支持的实体名（别名） |
| --- | --- |
| 敌对 | 僵尸、僵尸村民、尸壳、溺尸、骷髅、弓箭手、流浪者、凋灵骷髅、苦力怕、爬行者、蜘蛛、洞穴蜘蛛、末影人、小黑、末影螨、蠹虫、史莱姆、岩浆怪、恶魂、烈焰人、女巫、掠夺者、卫道士、唤魔者、幻术师、劫掠兽、潜影贝、幻翼、监守者、坚守者、远古守卫者、守卫者 |
| 下界 | 猪灵、猪灵蛮兵、疣猪兽、僵尸猪灵、僵尸猪人、僵尸疣猪兽 |
| BOSS | 末影龙、凋灵 |
| 友好 | 村民、流浪商人、铁傀儡、雪傀儡、豹猫、猫、狼、狗、狐狸、熊猫、北极熊、羊驼、马、驴、骡、骷髅马、僵尸马、骆驼、猪、牛、哞菇、羊、鸡、兔子、青蛙、美西螈、海龟、海豚、鱿鱼、发光鱿鱼、蜜蜂、蝙蝠、鹦鹉、山羊、盔甲架 |

注意「监守者」和「坚守者」是同一个实体的两种叫法（官方译名是「监守者」，「坚守者」是玩家的习惯叫法），都指向 `minecraft:warden`。

实体名也支持模糊纠错。「坚守者」和「守卫者」只差一个字，但首字符不同，所以永远不会互相误纠 —— 模糊纠错要求「书写系统一致 + 首字符相同」（见下文「规则与安全设计」）。认不出时在本地报错并列出可点击候选。

### 整句短语

有些说法不归某个动词管，或者一句顶多条指令。这些可以**直接说，不必先写动词**：

| 你输入 | 实际发出的指令 |
| --- | --- |
| `#死亡不掉落`、`#开启死亡不掉落`、`#打开死亡不掉落` | `/gamerule keepInventory true` |
| `#关闭死亡不掉落`、`#关掉死亡不掉落` | `/gamerule keepInventory false` |
| `#白天`、`#白昼`、`#天亮` | `/time set day` |
| `#夜晚`、`#黑夜` | `/time set night` |
| `#永为白昼`、`#永远白天` | `/time set day`、`/gamerule doDaylightCycle false` |
| `#永为黑夜`、`#永远黑夜` | `/time set night`、`/gamerule doDaylightCycle false` |
| `#恢复昼夜交替` | `/gamerule doDaylightCycle true` |
| `#清除效果`、`#清除所有效果`、`#清除药水效果` | `/effect clear @s` |
| `#设置重生点`、`#设置出生点` | `/spawnpoint` |
| `#杀死自己`、`#自杀` | `/kill @s` |

## 特殊写法

以下都是解析引擎对口语的额外照顾，全部有对应实现。

**量词前移**：数量可以写在物品名前面，量词可有可无。

| 你输入 | 实际发出 |
| --- | --- |
| `#给我1个齿轮` | `/give @s <齿轮的物品 ID> 1` |
| `#给我 1把钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 2个 石头` | `/give @s minecraft:stone 2` |
| `#给我 2钻石块` | `/give @s minecraft:diamond_block 2` |

支持的量词：个、把、块、组、颗、支、张、件、只、条、根、桶、瓶、箱、份、套、堆、枚、面、双、对、台、辆。

**数量在最后（名字与数量之间必须有空格）**：`#给我 钻石剑 5`。

**数量自己占一个参数位且排在前面**：`#给我 1个 齿轮`。

**无空格数量（名字和数量粘在一起）**：`#给我 齿轮1` —— 只在**精确匹配失败**时才把尾部数字当数量，这样名字本身带数字的物品不会被误伤。这一步排在模糊纠错之前，否则 `钻石剑3` 会先被模糊匹配吃掉，结果变成给 1 个钻石剑而不是 3 个。

**枚举取值可以拆开写**：枚举类参数的内部空格会被全部去掉再整体查表，所以 `#时间 白 天` 也能命中。

**坐标可以用逗号分隔**：`#传送 100,64,100`。

**附魔的多种分隔与省略**：

- 多个附魔用逗号或空格隔开都行：`#附魔手持 锋利5 耐久3`、`#附魔手持 锋利5,耐久3`；
- 光秃秃的数字会并回前一项：`#附魔手持 击退 255` 表示「击退 255 级」，而不是「击退」加一个叫 `255` 的附魔；
- 等级可以省略，默认 1 级：`#附魔手持 锋利`。

**口语整句效果**：不用先说「效果」两个字，直接在「给我 / 帮我 / 我要 / 给 / 要 / 来」后面说完整的一句话。

| 你输入 | 实际发出 |
| --- | --- |
| `#给我30秒的速度2效果` | `/effect give @s minecraft:speed 30 1` |
| `#给我速度2` | `/effect give @s minecraft:speed 30 1` |
| `#给我30秒的夜视` | `/effect give @s minecraft:night_vision 30 0` |
| `#给我速度2状态` | `/effect give @s minecraft:speed 30 1` |

只有确实指向一个药水效果时才会走这条路，认不出就原样交回常规路径，所以 `#给我 钻石剑 5` 不会被抢走。名字必须精确命中效果表；带「状态 / 效果」标记词时，名字允许模糊纠错。

**全角转半角、英文转小写**：输入会先做归一化 —— 全角字符（含全角空格、全角数字）转成半角，英文转小写，再去掉首尾空白。中文不受影响。

## 规则与安全设计

这一节说明模组在「猜」和「不猜」之间的取舍。

### 不绕过权限

模组只是把中文翻译成原版指令，然后**以玩家自己的身份**发出去。它不做任何权限校验，也不试图绕过任何权限校验：能否生效完全由服务端按你的权限判定。所以你在单人存档里是管理员、在多人服务器里不是，同一条 `#` 输入的结果会完全不同 —— 这不是模组的开关，是服务端说了算。

翻译失败或权限不足时，你会在聊天框里看到服务端返回的原始提示（与原版指令完全一致）。

### 绝不静默改词

模糊纠错（fuzzy matching）一旦命中并自动执行，模组一定会在聊天框里用**黄色**文字明说改成了什么：

```
[ChatCmd] 已按模糊匹配识别为「钻石剑」
```

不会有「悄悄替你改一个字然后照常执行」的情况。

### 高危指令的二次确认

会造成**大面积不可逆破坏**的指令不会一发出就执行，而是先要你点个头。目前需要确认的有两类：

- **所有 `#清除`**（掉落物 / 经验球 / 效果 / 怪物）；
- **`#重置 全部`**，包括不写参数的 `#重置`。

流程是「**一条警告 + 再发一条 `#确认`**」：

1. 你发出高危指令，此时**什么都不执行**；
2. 模组用**黄色**文字回一条警告，说明这条指令真正会做什么（警告文案按实际要发的指令生成，说的都是真话），并附一行可点击的 `#确认` / `#取消`；
3. 你再发一条 `#确认`，才真正执行；发 `#取消`（或 `取消`、`算了`、`不要`、`no` 等）则放弃；
4. 在这之间发了**任何别的指令**，待确认状态立即作废 —— 不会出现「过了十条指令之后突然被执行」的情况。

以 `#清除 怪物` 为例，因为原版没有「只杀敌对生物」的选择器，警告会明确写出它会连动物、村民、驯服的宠物一起清掉，而不是只说一句「确定吗？」。

「确认」这一步允许的口语：`确认`、`确定`、`是`、`好`、`好的`、`可以`、`执行`、`yes`、`y`、`ok`。

### 危险动词禁止模糊纠错

只对会造成**不可逆破坏**的动词关闭模糊纠错，目前只有 `#清除` 一个。打错一个字就批量杀实体，代价太大，所以这类动词**宁可报错也不猜**，让你自己重打。

### 模糊纠错的阈值规则

模糊纠错只在精确匹配**全部失败之后**才启用，并且必须同时满足两个前提：

1. **书写系统一致**：中文别名只和中文输入比，英文别名只和英文输入比；
2. **首字符相同**：从根上掐掉跨语种、跨字的离谱纠错（否则「跳舞」会被纠成「tp」）。

在此之上，按词长限制允许的编辑距离（Levenshtein Distance，莱文斯坦距离）：

| 词长 | 中文 | 英文 / 数字 |
| --- | --- | --- |
| ≤ 2 字 | 0（不纠错） | 1 |
| 3 ~ 4 字 | 1 | 1 |
| ≥ 5 字 | 2 | 2 |

中文阈值更严，因为中文短词区分度极低：「下雨」和「下雪」只差一个字，语义却完全不同。所以 2 字以内的中文要求完全相等。

此外，自动纠错要求**唯一最优解**：如果两个候选一样近（并列），模组会放弃纠错，宁可让你重打。

### 认不出时给可点击候选

解析失败时，模组会用红色文字给出提示，并在下面附一行**可点击的候选**：

```
[ChatCmd] 你是不是想用：#模式 创造
```

点击候选的动作是「**填入聊天框**」，不是「直接执行」：点一下只是把候选填进聊天框，你还能改，按回车才真正发出去。所以猜错也不会有任何后果。

候选的生成方式有两种：先列出编辑距离最近的几项；一项都列不出来时（例如 `#模式 飞行`），干脆把该指令类型的全部取值摆出来让你点。

另外，如果某个取值其实是一条整句短语（例如 `#模式 死亡不掉落` 里的「死亡不掉落」），提示会直接告诉你正确的说法：

```
[ChatCmd] 「死亡不掉落」不归 模式 管。你是不是想用：#死亡不掉落 ？
```

### `##` 逃生通道

想发一条真的以 `#` 开头的普通聊天（不是指令），就写两个 `#`：

| 你输入 | 实际效果 |
| --- | --- |
| `##你好` | 作为普通聊天发出 `#你好` |

模组会去掉一个 `#`，把剩下的文本作为**普通聊天**发出，并用灰色文字确认：

```
[ChatCmd] 已作为普通聊天发出：#你好
```

这样你就永远有办法说出以 `#` 开头的话，不会被模组吃掉。

### 认不出物品时本地报错

物品名认不出时，模组**在本地就报错并列出候选**，绝不把中文名硬拼成 `minecraft:中文名` 丢给服务端。否则你看到的会是一句莫名其妙的「未知的物品 minecraft:齿轮1」。

## 更新日志

全部版本的完整变更记录见 [CHANGELOG.md 更新日志](CHANGELOG.md)（中文 + 英文），成品 jar 见 [Releases 版本发布](https://github.com/yunijiangzi111/chatcmd/releases)。

## 从源码构建

需要 **JDK 21**（Minecraft 1.21.1 与模组都目标 Java 21）。构建前先让 Gradle 找得到它，最省事的办法是设置环境变量 `JAVA_HOME`：

```bat
:: Windows
set JAVA_HOME=C:\path\to\jdk-21
```

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
```

也可以在 `gradle.properties` 里写 `org.gradle.java.home=<JDK 21 路径>`（推荐把这一行放在**用户级**配置 `~/.gradle/gradle.properties` 里，这样换个工程或换台机器都不用重填）。

**JDK 17 不用你手动装**：`core` 模块定在 Java 17（Forge 1.20.1 跑在 Java 17 上），Gradle 会通过 `settings.gradle` 里的 `foojay-resolver-convention` 插件**自动下载**它。

跑单元测试（`core` 是纯 Java 模块，零 Minecraft 依赖，可以脱离游戏直接测）：

```bat
gradlew.bat :core:test
```

```bash
./gradlew :core:test
```

构建模组 jar：

```bat
:: NeoForge（1.21.1）→ neoforge/build/libs/chatcmd-0.8.1-neoforge+mc1.21.1.jar
gradlew.bat :neoforge:jar

:: Forge（1.20.1）→ forge/build/libs/chatcmd-0.8.1-forge+mc1.20.1.jar
gradlew.bat :forge:reobfJar
```

```bash
./gradlew :neoforge:jar
./gradlew :forge:reobfJar
```

注意 Forge 侧要跑的是 **`reobfJar`**，不是 `jar`：Forge 1.20.1 的开发产物名字是混淆前的（官方名），必须再经过一道**重混淆（reobfuscate）**才能给正式版游戏加载。`jar` 的产物落在 `forge/build/devlibs/`（开发用），`reobfJar` 的产物才是 `forge/build/libs/` 里那个发给玩家的成品。

其它有用的任务：

- `:core:check` —— 除了跑测试，还会执行 `checkCorePurity`：扫描 `core` 源码，一旦出现 `net.minecraft.*` / `net.neoforged.*` / `net.minecraftforge.*` 引用就让构建失败，保证「零 Minecraft 依赖」这条约束不会随代码腐化。
- `:neoforge:runClient` —— 启动一个带本模组的 NeoForge 开发客户端（1.21.1），用于本地调试。
- `:forge:runClient` —— 启动一个带本模组的 Forge 开发客户端（1.20.1），用于本地调试。

工具链版本：Gradle 9.2.1、ModDevGradle 2.0.147、NeoForge 21.1.228 / Minecraft 1.21.1、Forge 47.3.0 / Minecraft 1.20.1。

## 发布新版本

本仓库只有 ChatCmd 这一个模组，所以 tag（版本标签）直接用简洁的 `v<版本号>`，不需要模组前缀：

1. 改好代码，把 `gradle.properties` 里的 `mod_version` 升上去。
2. 打 tag 并推送：`git tag v0.7.1 && git push origin v0.7.1`。

CI（持续集成）会自动构建并把**两个平台**的成品 jar 一起挂到 [Releases 版本发布](https://github.com/yunijiangzi111/chatcmd/releases) 页面，**不需要在网页上手动点发布**。产物命名对齐社区惯例 `<模组id>-<版本号>-<平台>+mc<MC版本>.jar`，例如 `chatcmd-0.7.1-neoforge+mc1.21.1.jar` 与 `chatcmd-0.7.1-forge+mc1.20.1.jar`。

## 许可证

本项目使用 **MIT 许可证**，全文见仓库根目录的 [LICENSE](LICENSE) 文件。

这意味着你可以自由地使用、修改、再分发本模组，甚至可以用于商业用途，唯一的要求是**保留版权声明和许可证文本**。

模组元数据里的 `mod_license` 字段（`gradle.properties`）与之一致，声明为 `MIT`。

---

# English

Use Chinese in the chat box to issue Minecraft commands. Type `#给我1个钻石剑` and the mod translates it into the vanilla command `/give @s minecraft:diamond_sword 1`, then sends it as you.

Current version: **0.8.1**

[中文](#chatcmd-聊天指令) | English

## Introduction

ChatCmd is a Minecraft **client-side mod**. It intercepts chat input starting with `#`, translates **spoken Chinese** into **vanilla commands**, and sends them as the player.

Three core design principles:

1. **Spoken Chinese to vanilla commands**: translation is all it does. The result is an ordinary vanilla command.
2. **No permission bypass**: the mod only types for you. Commands are sent as you, and whether they take effect is decided entirely by the server according to your permissions.
3. **No silent word changes**: when fuzzy matching hits, the mod always states in yellow what it corrected to. When unsure, it reports an error instead of guessing.

The project is split into three modules:

- `core` — a pure Java parsing engine with zero Minecraft dependencies, testable without the game.
- `neoforge` — the NeoForge adapter, targeting Minecraft 1.21.1.
- `forge` — the Forge adapter, targeting Minecraft 1.20.1.

Both adapters share the same `core`. They differ in exactly two places: how the platform API is called, and the enchantment command syntax (1.20.5 and later use the item components syntax, 1.20.1 still uses NBT).

## Installation

Both platforms are provided:

| The version you play | Which jar to install | Required mod loader |
| --- | --- | --- |
| Minecraft **1.21.1** | `chatcmd-0.8.1-neoforge+mc1.21.1.jar` | NeoForge **21.1.228** or newer |
| Minecraft **1.20.1** | `chatcmd-0.8.1-forge+mc1.20.1.jar` | Forge **47.3.0** or newer |

Java ships with the game, so no separate install is needed for either version.

Installation:

1. Put the jar for your platform into `.minecraft/mods`.
2. Start the game.

This is a **client-side mod**: installing it on the client is enough. The server does **not** need it. The NeoForge side declares `dist = Dist.CLIENT`, and the Forge side declares `clientSideOnly=true`, so even if it is accidentally installed on a dedicated server, the client classes are not loaded and the server will not crash.

## Quick Start

Type text starting with `#` in the chat box. For example:

```
#给我1个钻石剑
```

The mod sends `/give @s minecraft:diamond_sword 1` and echoes the command it actually sent in gray text:

```
[ChatCmd] 已执行：/give @s minecraft:diamond_sword 1
```

More examples:

| You type | Actually sent |
| --- | --- |
| `#给我1个钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#时间 白天` | `/time set day` |
| `#传送 100 64 100` | `/tp @s 100 64 100` |
| `#模式 创造` | `/gamemode creative` |
| `#效果 速度 2 30` | `/effect give @s minecraft:speed 30 1` |
| `#找 村庄` | `/locate structure #minecraft:village` |

**Key binding**: press `#` (that is, Shift+3) to open the chat box with a `#` pre-filled, saving the "press T then type #" step (this is what the vanilla `/` key does). You can rebind it under "Options -> Controls" in the "ChatCmd" category. Note that number keys are also the vanilla hotbar keys, so holding Shift and pressing 3 may also switch the third hotbar slot; rebind it if that bothers you.

## Supported Commands

Every row below corresponds to a command type in the source. All commands are sent **without a leading slash** (the `/` in the tables is only for readability).

### give

Verb aliases: `给我`, `给`, `give`

Usage: `#给我 <item> [count]` (count defaults to 1)

| You type | Actually sent |
| --- | --- |
| `#给我 钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 钻石剑 5` | `/give @s minecraft:diamond_sword 5` |
| `#给我1个钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 齿轮1` | `/give @s <item id of 齿轮> 1` |

Item resolution has three levels:

1. **The hand-written alias table** (55 entries below);
2. **Game registry localized names**: on first use the mod walks the entire item registry and builds a full "current-language display name -> item id" table. That covers the hundreds of modded items in a modpack, so they do not have to be hand-written into the alias table;
3. **Raw vanilla id passthrough**: when the input looks like a vanilla id (lowercase ASCII letters/digits/underscore/dot/hyphen, optionally namespaced) it is used as is, e.g. `#给我 diamond_sword 3` or `#给我 create:cogwheel`.

Fuzzy correction applies **only to the hand-written alias table** (see "Rules and Safety Design"), because modded item names are extremely similar to each other (齿轮 / 大齿轮 / 小齿轮) and guessing wrong means silently giving the wrong item.

Hand-written alias table:

| Chinese name | Vanilla item id | Chinese name | Vanilla item id |
| --- | --- | --- | --- |
| 钻石剑 | `minecraft:diamond_sword` | 铁剑 | `minecraft:iron_sword` |
| 钻石镐 | `minecraft:diamond_pickaxe` | 铁镐 | `minecraft:iron_pickaxe` |
| 钻石斧 | `minecraft:diamond_axe` | 铁锭 | `minecraft:iron_ingot` |
| 钻石锹 | `minecraft:diamond_shovel` | 金剑 | `minecraft:golden_sword` |
| 钻石锄 | `minecraft:diamond_hoe` | 金锭 | `minecraft:gold_ingot` |
| 钻石 | `minecraft:diamond` | 石剑 | `minecraft:stone_sword` |
| 钻石块 | `minecraft:diamond_block` | 木剑 | `minecraft:wooden_sword` |
| 下界合金剑 | `minecraft:netherite_sword` | 下界合金锭 | `minecraft:netherite_ingot` |
| 弓 | `minecraft:bow` | 箭 | `minecraft:arrow` |
| 盾牌 | `minecraft:shield` | 石头 | `minecraft:stone` |
| 圆石 | `minecraft:cobblestone` | 泥土 | `minecraft:dirt` |
| 草方块 | `minecraft:grass_block` | 橡木原木 | `minecraft:oak_log` |
| 木板 | `minecraft:oak_planks` | 玻璃 | `minecraft:glass` |
| 沙子 | `minecraft:sand` | 黑曜石 | `minecraft:obsidian` |
| 基岩 | `minecraft:bedrock` | 火把 | `minecraft:torch` |
| 箱子 | `minecraft:chest` | 工作台 | `minecraft:crafting_table` |
| 熔炉 | `minecraft:furnace` | 漏斗 | `minecraft:hopper` |
| 发射器 | `minecraft:dispenser` | 命令方块 | `minecraft:command_block` |
| 红石 | `minecraft:redstone` | 煤炭 | `minecraft:coal` |
| 木棍 | `minecraft:stick` | 苹果 | `minecraft:apple` |
| 金苹果 | `minecraft:golden_apple` | 附魔金苹果 | `minecraft:enchanted_golden_apple` |
| 面包 | `minecraft:bread` | 小麦 | `minecraft:wheat` |
| 胡萝卜 | `minecraft:carrot` | 马铃薯 | `minecraft:potato` |
| 甘蔗 | `minecraft:sugar_cane` | 生牛肉 | `minecraft:beef` |
| 牛排 | `minecraft:cooked_beef` | 腐肉 | `minecraft:rotten_flesh` |
| 末影珍珠 | `minecraft:ender_pearl` | 药水 | `minecraft:potion` |
| 水桶 | `minecraft:water_bucket` | 熔岩桶 | `minecraft:lava_bucket` |
| tnt | `minecraft:tnt` | | |

### time

Verb aliases: `时间`, `时刻`, `time`

Usage: `#时间 <白天|正午|夜晚|午夜>`

| Chinese values (aliases) | Vanilla value | Generated command |
| --- | --- | --- |
| 白天, 早上, 早晨, `day` | `day` | `/time set day` |
| 正午, 中午, `noon` | `noon` | `/time set noon` |
| 夜晚, 晚上, 傍晚, 黄昏, `night` | `night` | `/time set night` |
| 午夜, 凌晨, `midnight` | `midnight` | `/time set midnight` |

### teleport

Verb aliases: `传送`, `tp`, `teleport`

Usage: `#传送 <x> <y> <z>`

Coordinates support `~`, `~5`, `~-3`, integers `100`, and decimals `-64.5`.

| You type | Actually sent |
| --- | --- |
| `#传送 100 64 100` | `/tp @s 100 64 100` |
| `#传送 ~ ~5 ~-3` | `/tp @s ~ ~5 ~-3` |
| `#传送 100,64,100` | `/tp @s 100 64 100` |

### gamemode

Verb aliases: `模式`, `gamemode`

Usage: `#模式 <创造|生存|冒险|旁观>`

| Chinese values (aliases) | Vanilla value | Generated command |
| --- | --- | --- |
| 创造, 创意, `creative` | `creative` | `/gamemode creative` |
| 生存, `survival` | `survival` | `/gamemode survival` |
| 冒险, 冒险家, `adventure` | `adventure` | `/gamemode adventure` |
| 旁观, 旁观者, `spectator` | `spectator` | `/gamemode spectator` |

### weather

Verb aliases: `天气`, `weather`

Usage: `#天气 <晴|雨|雷>`

| Chinese values (aliases) | Vanilla value | Generated command |
| --- | --- | --- |
| 晴, 晴天, 晴朗, 放晴, `clear` | `clear` | `/weather clear` |
| 雨, 下雨, `rain` | `rain` | `/weather rain` |
| 雷, 雷雨, 打雷, `thunder` | `thunder` | `/weather thunder` |

### difficulty

Verb aliases: `难度`, `difficulty`

Usage: `#难度 <和平|简单|普通|困难>`

| Chinese values (aliases) | Vanilla value | Generated command |
| --- | --- | --- |
| 和平, `peaceful` | `peaceful` | `/difficulty peaceful` |
| 简单, `easy` | `easy` | `/difficulty easy` |
| 普通, `normal` | `normal` | `/difficulty normal` |
| 困难, `hard` | `hard` | `/difficulty hard` |

### gamerule

Verb aliases: `游戏规则`, `规则`, `gamerule`

Usage: `#规则 <rule> <开|关>`

| Chinese rule name (aliases) | Vanilla rule name |
| --- | --- |
| 死亡不掉落, 保留物品, 不掉落物品, 死亡不丢东西, `keepinventory` | `keepInventory` |
| 生物破坏, 怪物破坏, 生物变方块, `mobgriefing` | `mobGriefing` |
| 昼夜交替, 日夜交替, 白天黑夜交替, `dodaylightcycle` | `doDaylightCycle` |
| 天气变化, 天气循环, `doweathercycle` | `doWeatherCycle` |
| 自然回血, 自然恢复, 自然回复, 生命恢复, 生命回复, 生命自然恢复, 自动回血, 回血, `naturalregeneration` | `naturalRegeneration` |
| 死亡消息, 死亡提示, `showdeathmessages` | `showDeathMessages` |
| 怪物生成, 生物生成, 刷怪, `domobspawning` | `doMobSpawning` |
| 火势蔓延, 火焰蔓延, `dofiretick` | `doFireTick` |
| 立即重生, 立刻重生, `doimmediaterespawn` | `doImmediateRespawn` |

Switch values: `开`, `开启`, `打开`, `启用`, `是`, `true`, `on` -> `true`; `关`, `关闭`, `关掉`, `禁用`, `否`, `false`, `off` -> `false`.

Everyone has their own name for the same rule — 自然回血 and 生命恢复 are both `naturalRegeneration` — so the table above tries to collect every common phrasing.

If none of them match, one more step runs: **fuzzy correction**. A single typo is corrected automatically and the reply says in yellow what it was corrected to. For example `#规则 死亡不掉洛 开` is corrected to 死亡不掉落.

When even fuzzy correction finds nothing, the mod **reports the error locally and lists clickable candidates**. It never passes an unrecognized rule name through as is — input is lowercased during normalization, so a passed-through camelCase rule name could never work anyway; all you would get back is a puzzling "unknown game rule".

| You type | Actually sent |
| --- | --- |
| `#规则 死亡不掉落 开` | `/gamerule keepInventory true` |
| `#规则 昼夜交替 关` | `/gamerule doDaylightCycle false` |
| `#规则 生命恢复 开` | `/gamerule naturalRegeneration true` |

### reset

Verb aliases: `重置`, `恢复`, `复原`

Usage: `#重置 [时间|天气|效果|规则|全部]`. Omitting the argument is the same as `全部` (all).

| Chinese values (aliases) | Commands actually sent |
| --- | --- |
| 时间, 昼夜, 昼夜交替, 白天黑夜 | `/gamerule doDaylightCycle true` |
| 天气, 天气变化 | `/gamerule doWeatherCycle true`, `/weather clear` |
| 效果, 药水效果, 状态 | `/effect clear @s` |
| 规则, 游戏规则 | `/gamerule keepInventory false`, `/gamerule mobGriefing true` |
| 全部, 所有, 一切 (or omit the argument) | All of the above, 6 commands in total |

"时间" turns the daylight cycle back on, which is how you end "永为白昼 / 永为黑夜" (always day / always night).

**`全部` (all), including when the argument is omitted, is a high-risk command and needs a second confirmation**: it does not run immediately. A yellow warning first explains which commands are about to run, and you must send `#确认` to actually run them (see "Second confirmation for high-risk commands" below). The other values (时间 / 天气 / 效果 / 规则) have limited impact and run directly.

### effect

Verb aliases: `效果`, `状态效果`, `buff`

Usage: `#效果 <effect> [level] [duration]`

The parameter order is **level first, duration second** (matching spoken habits). A token carrying a `秒` / `s` unit always counts as the duration, so `#效果 速度 30秒 2` is also accepted.

- Duration defaults to **30 seconds** (the vanilla `/effect give` default when the duration is omitted);
- Level defaults to **1**;
- The third vanilla parameter of this command is the amplifier, where `0` is level I, so your "level 2" is converted to `1`. The mod states this conversion in its echo.

| You type | Actually sent |
| --- | --- |
| `#效果 速度` | `/effect give @s minecraft:speed 30 0` |
| `#效果 速度 2 30` | `/effect give @s minecraft:speed 30 1` |
| `#效果 速度 30秒 2` | `/effect give @s minecraft:speed 30 1` |

Effect name table (both Chinese and English names work):

| Chinese name (English alias) | Vanilla effect id | Chinese name (English alias) | Vanilla effect id |
| --- | --- | --- | --- |
| 速度 (`speed`), 迅捷 | `minecraft:speed` | 缓慢 (`slowness`), 迟缓 | `minecraft:slowness` |
| 急迫 (`haste`), 急速 | `minecraft:haste` | 挖掘疲劳 (`mining_fatigue`) | `minecraft:mining_fatigue` |
| 力量 (`strength`) | `minecraft:strength` | 瞬间治疗 (`instant_health`) | `minecraft:instant_health` |
| 瞬间伤害 (`instant_damage`) | `minecraft:instant_damage` | 跳跃提升 (`jump_boost`) | `minecraft:jump_boost` |
| 反胃 (`nausea`) | `minecraft:nausea` | 生命恢复 (`regeneration`) | `minecraft:regeneration` |
| 抗性提升 (`resistance`) | `minecraft:resistance` | 防火 (`fire_resistance`) | `minecraft:fire_resistance` |
| 水下呼吸 (`water_breathing`) | `minecraft:water_breathing` | 隐身 (`invisibility`) | `minecraft:invisibility` |
| 失明 (`blindness`) | `minecraft:blindness` | 夜视 (`night_vision`) | `minecraft:night_vision` |
| 饥饿 (`hunger`) | `minecraft:hunger` | 虚弱 (`weakness`) | `minecraft:weakness` |
| 中毒 (`poison`) | `minecraft:poison` | 凋零 (`wither`) | `minecraft:wither` |
| 生命提升 (`health_boost`) | `minecraft:health_boost` | 伤害吸收 (`absorption`) | `minecraft:absorption` |
| 饱和 (`saturation`) | `minecraft:saturation` | 发光 (`glowing`) | `minecraft:glowing` |
| 漂浮 (`levitation`) | `minecraft:levitation` | 幸运 (`luck`) | `minecraft:luck` |
| 霉运 (`unluck`) | `minecraft:unluck` | 缓降 (`slow_falling`) | `minecraft:slow_falling` |
| 潮涌能量 (`conduit_power`) | `minecraft:conduit_power` | 海豚的恩惠 (`dolphins_grace`) | `minecraft:dolphins_grace` |
| 不祥之兆 (`bad_omen`) | `minecraft:bad_omen` | 村庄英雄 (`hero_of_the_village`) | `minecraft:hero_of_the_village` |
| 黑暗 (`darkness`) | `minecraft:darkness` | 试炼之兆 (`trial_omen`) | `minecraft:trial_omen` |
| 袭击之兆 (`raid_omen`) | `minecraft:raid_omen` | 风袭 (`wind_charged`) | `minecraft:wind_charged` |
| 织网 (`weaving`) | `minecraft:weaving` | 渗浆 (`oozing`) | `minecraft:oozing` |
| 寄生 (`infested`) | `minecraft:infested` | | |

### enchant

Verb aliases: `附魔`

Usage: `#附魔 <item> <enchant><level>`, multiple enchants separated by commas or spaces.

The enchantment command syntax **changes with the Minecraft version**, and the mod picks it per platform:

- **Minecraft 1.20.5 and later** (this mod's neoforge build): item data uses the "item components" syntax, so the new `[enchantments={...}]` form is used; the old `stick{Enchantments:[...]}` form was removed from vanilla.
- **Minecraft 1.20.4 and earlier** (this mod's forge 1.20.1 build): still the NBT form `{Enchantments:[{id:...,lvl:...s}]}`.

| You type | neoforge build (1.21.1) actually sent | forge build (1.20.1) actually sent |
| --- | --- | --- |
| `#附魔 钻石剑 锋利5` | `/give @s minecraft:diamond_sword[enchantments={"minecraft:sharpness":5}]` | `/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s}]}` |
| `#附魔 钻石剑 锋利5,耐久3` | `/give @s minecraft:diamond_sword[enchantments={"minecraft:sharpness":5,"minecraft:unbreaking":3}]` | `/give @s minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s},{id:"minecraft:unbreaking",lvl:3s}]}` |

This is the only command affected by the version; `#附魔手持` (which uses the vanilla `/enchant`) and every other command behave identically on both versions.

The enchant level defaults to 1 and is **passed through as is**, with no "spoken minus one" conversion.

### enchant held

Verb aliases: `附魔手持`, `手持附魔`, `enchant`

Usage: `#附魔手持 <enchant><level>`, multiple enchants separated by commas or spaces.

The vanilla `/enchant` command can only apply one enchantment at a time, so multiple enchants are **split into several commands sent in order**, and the mod says so in its echo.

| You type | Actually sent |
| --- | --- |
| `#附魔手持 锋利5` | `/enchant @s minecraft:sharpness 5` |
| `#附魔手持 锋利5,耐久3` | `/enchant @s minecraft:sharpness 5`, `/enchant @s minecraft:unbreaking 3` |

**What if the server changed `/enchant`**: some servers (or server-side plugins) replace the vanilla `/enchant <targets> <enchantment> [<level>]` with a form that has `add` / `remove` subcommands. Sending the vanilla form there only yields "Incorrect argument for command". The command tree is sent from the server to the client, so before sending the mod reads it: if this server's `/enchant` requires `add`, the command is automatically completed to `/enchant add @s minecraft:sharpness 5`. On a vanilla server not a single character is changed, and there is no extra notice.

Enchant name table (both Chinese and English names work):

| Chinese name (English alias) | Vanilla enchant id | Chinese name (English alias) | Vanilla enchant id |
| --- | --- | --- | --- |
| 锋利 (`sharpness`) | `minecraft:sharpness` | 亡灵杀手 (`smite`) | `minecraft:smite` |
| 节肢杀手 (`bane_of_arthropods`) | `minecraft:bane_of_arthropods` | 击退 (`knockback`) | `minecraft:knockback` |
| 火焰附加 (`fire_aspect`) | `minecraft:fire_aspect` | 抢夺 (`looting`) | `minecraft:looting` |
| 横扫之刃 (`sweeping_edge`) | `minecraft:sweeping_edge` | 效率 (`efficiency`) | `minecraft:efficiency` |
| 精准采集 (`silk_touch`) | `minecraft:silk_touch` | 耐久 (`unbreaking`) | `minecraft:unbreaking` |
| 时运 (`fortune`) | `minecraft:fortune` | 力量 (`power`) | `minecraft:power` |
| 冲击 (`punch`) | `minecraft:punch` | 火矢 (`flame`) | `minecraft:flame` |
| 无限 (`infinity`) | `minecraft:infinity` | 引雷 (`channeling`) | `minecraft:channeling` |
| 忠诚 (`loyalty`) | `minecraft:loyalty` | 激流 (`riptide`) | `minecraft:riptide` |
| 穿刺 (`impaling`) | `minecraft:impaling` | 多重射击 (`multishot`) | `minecraft:multishot` |
| 快速装填 (`quick_charge`) | `minecraft:quick_charge` | 穿透 (`piercing`) | `minecraft:piercing` |
| 保护 (`protection`) | `minecraft:protection` | 火焰保护 (`fire_protection`) | `minecraft:fire_protection` |
| 摔落保护 (`feather_falling`) | `minecraft:feather_falling` | 爆炸保护 (`blast_protection`) | `minecraft:blast_protection` |
| 弹射物保护 (`projectile_protection`) | `minecraft:projectile_protection` | 水下呼吸 (`respiration`) | `minecraft:respiration` |
| 水下速掘 (`aqua_affinity`) | `minecraft:aqua_affinity` | 荆棘 (`thorns`) | `minecraft:thorns` |
| 深海探索者 (`depth_strider`) | `minecraft:depth_strider` | 冰霜行者 (`frost_walker`) | `minecraft:frost_walker` |
| 灵魂疾行 (`soul_speed`) | `minecraft:soul_speed` | 迅捷潜行 (`swift_sneak`) | `minecraft:swift_sneak` |
| 经验修补 (`mending`) | `minecraft:mending` | 绑定诅咒 (`binding_curse`) | `minecraft:binding_curse` |
| 消失诅咒 (`vanishing_curse`) | `minecraft:vanishing_curse` | | |

### xp

Verb aliases: `经验`, `经验值`, `xp`

Usage: `#经验 <amount>`

| You type | Actually sent |
| --- | --- |
| `#经验 100` | `/xp add @s 100` |

### clear

Verb aliases: `清除`, `清掉`

Usage: `#清除 <掉落物|经验球|怪物>`

| Chinese values (aliases) | Commands actually sent |
| --- | --- |
| 掉落物, 物品, 地上的东西 | `/kill @e[type=minecraft:item]` |
| 经验球, 经验 | `/kill @e[type=minecraft:experience_orb]` |
| 效果, 药水效果 | `/effect clear @s` |
| 怪物, 生物 | `/kill @e[type=!player,type=!minecraft:item,type=!minecraft:experience_orb]` |

Note the "怪物" entry: vanilla has no selector for "hostile mobs only", so it excludes players, dropped items and experience orbs and kills **everything else** — animals and villagers are removed too.

**Every `#清除` is a high-risk command and needs a second confirmation**: it does not run immediately. A yellow warning first tells you what the command will really delete (for the "怪物" entry it states explicitly that animals, villagers and tamed pets are removed as well), and you must send `#确认` to actually run it (see "Second confirmation for high-risk commands" below).

Fuzzy correction is **disabled** for this verb (see "Rules and Safety Design").

### locate structure

Verb aliases: `最近的`, `最近`, `找结构`, `寻找`, `查找`, `找`, `locate`

Usage: `#找 <structure>`

Structure name table:

| Chinese name (aliases) | Vanilla structure id |
| --- | --- |
| 村庄, 村子 | `#minecraft:village` |
| 平原村庄 | `minecraft:village_plains` |
| 沙漠村庄 | `minecraft:village_desert` |
| 雪原村庄 | `minecraft:village_snowy` |
| 热带草原村庄 | `minecraft:village_savanna` |
| 针叶林村庄 | `minecraft:village_taiga` |
| 掠夺者前哨站, 掠夺者哨塔, 哨塔 | `minecraft:pillager_outpost` |
| 林地府邸, 府邸 | `minecraft:mansion` |
| 要塞 | `minecraft:stronghold` |
| 废弃矿井 | `minecraft:mineshaft` |
| 矿洞 | `#minecraft:mineshaft` |
| 海底神殿, 海底宫殿 | `minecraft:monument` |
| 沉船 | `#minecraft:shipwreck` |
| 海底废墟 | `#minecraft:ocean_ruin` |
| 古迹废墟 | `minecraft:trail_ruins` |
| 沙漠神殿 | `minecraft:desert_pyramid` |
| 丛林神庙, 神庙 | `minecraft:jungle_pyramid` |
| 沼泽小屋, 女巫小屋 | `minecraft:swamp_hut` |
| 雪屋, 冰屋 | `minecraft:igloo` |
| 远古城市 | `minecraft:ancient_city` |
| 试炼密室, 试炼厅 | `minecraft:trial_chambers` |
| 废弃传送门 | `#minecraft:ruined_portal` |
| 下界要塞, 地狱要塞 | `minecraft:fortress` |
| 堡垒遗迹, 堡垒 | `minecraft:bastion_remnant` |
| 末地城 | `minecraft:end_city` |
| 紫水晶洞 | `minecraft:amethyst_geode` |
| 沙漠水井 | `minecraft:desert_well` |
| 埋藏的宝藏 | `minecraft:buried_treasure` |
| 下界化石 | `minecraft:nether_fossil` |

Where a structure tag is available it is preferred, so "村庄" finds all variants (plains, desert, snowy, and so on) in one go.

| You type | Actually sent |
| --- | --- |
| `#找 村庄` | `/locate structure #minecraft:village` |
| `#最近的村庄` | `/locate structure #minecraft:village` |

After a successful lookup the mod prints an extra **clickable coordinate line**: green and underlined, with the hover text "点击直接传送到 x ~ z" (click to teleport to x ~ z). A single click **teleports immediately** (`/tp @s x ~ z`). The vertical coordinate reuses the `~` from the vanilla reply, keeping your current height.

### summon

Verb aliases: `生成`, `召唤`, `刷怪`, `summon`, `spawn`

Usage: `#生成 <entity> [count] [x y z]`

| You type | Actually sent |
| --- | --- |
| `#生成 僵尸` | `/summon minecraft:zombie` |
| `#生成 坚守者 3` | `/summon minecraft:warden`, 3 commands in total |
| `#生成 末影龙 ~ ~5 ~` | `/summon minecraft:ender_dragon ~ ~5 ~` |
| `#召唤 苦力怕 2 ~ ~2 ~` | `/summon minecraft:creeper ~ ~2 ~`, 2 commands in total |

Vanilla `/summon` spawns only one entity per command, so a count greater than 1 is **automatically split into several commands sent in sequence**, and the reply says so. To avoid flooding the chat, the count is capped at **16**.

Without coordinates the entity spawns at the player's feet. Coordinates accept exactly the same forms as `#传送` (absolute `100 64 100`, relative `~ ~5 ~-3`, comma-separated `100,64,100`).

| Group | Supported entity names (aliases) |
| --- | --- |
| Hostile | 僵尸, 僵尸村民, 尸壳, 溺尸, 骷髅, 弓箭手, 流浪者, 凋灵骷髅, 苦力怕, 爬行者, 蜘蛛, 洞穴蜘蛛, 末影人, 小黑, 末影螨, 蠹虫, 史莱姆, 岩浆怪, 恶魂, 烈焰人, 女巫, 掠夺者, 卫道士, 唤魔者, 幻术师, 劫掠兽, 潜影贝, 幻翼, 监守者, 坚守者, 远古守卫者, 守卫者 |
| Nether | 猪灵, 猪灵蛮兵, 疣猪兽, 僵尸猪灵, 僵尸猪人, 僵尸疣猪兽 |
| Boss | 末影龙, 凋灵 |
| Passive | 村民, 流浪商人, 铁傀儡, 雪傀儡, 豹猫, 猫, 狼, 狗, 狐狸, 熊猫, 北极熊, 羊驼, 马, 驴, 骡, 骷髅马, 僵尸马, 骆驼, 猪, 牛, 哞菇, 羊, 鸡, 兔子, 青蛙, 美西螈, 海龟, 海豚, 鱿鱼, 发光鱿鱼, 蜜蜂, 蝙蝠, 鹦鹉, 山羊, 盔甲架 |

Note that 监守者 and 坚守者 are two names for the same entity (the official translation is 监守者, while 坚守者 is what players usually say); both map to `minecraft:warden`.

Entity names support fuzzy correction too. 坚守者 and 守卫者 differ by one character but not in their first character, so they are never corrected into each other — fuzzy correction requires the same writing system **and** the same first character (see "Rules and Safety Design" below). When nothing matches, the mod reports the error locally and lists clickable candidates.

### Whole-sentence phrases

Some phrases belong to no single verb, or stand for more than one command. You can **say them directly, without naming a verb first**:

| You type | Commands actually sent |
| --- | --- |
| `#死亡不掉落`, `#开启死亡不掉落`, `#打开死亡不掉落` | `/gamerule keepInventory true` |
| `#关闭死亡不掉落`, `#关掉死亡不掉落` | `/gamerule keepInventory false` |
| `#白天`, `#白昼`, `#天亮` | `/time set day` |
| `#夜晚`, `#黑夜` | `/time set night` |
| `#永为白昼`, `#永远白天` | `/time set day`, `/gamerule doDaylightCycle false` |
| `#永为黑夜`, `#永远黑夜` | `/time set night`, `/gamerule doDaylightCycle false` |
| `#恢复昼夜交替` | `/gamerule doDaylightCycle true` |
| `#清除效果`, `#清除所有效果`, `#清除药水效果` | `/effect clear @s` |
| `#设置重生点`, `#设置出生点` | `/spawnpoint` |
| `#杀死自己`, `#自杀` | `/kill @s` |

## Special Syntax

All of the following are extra accommodations for spoken Chinese, and all have a corresponding implementation.

**Count moved to the front**: the count may appear before the item name, and the measure word is optional.

| You type | Actually sent |
| --- | --- |
| `#给我1个齿轮` | `/give @s <item id of 齿轮> 1` |
| `#给我 1把钻石剑` | `/give @s minecraft:diamond_sword 1` |
| `#给我 2个 石头` | `/give @s minecraft:stone 2` |
| `#给我 2钻石块` | `/give @s minecraft:diamond_block 2` |

Supported measure words: 个, 把, 块, 组, 颗, 支, 张, 件, 只, 条, 根, 桶, 瓶, 箱, 份, 套, 堆, 枚, 面, 双, 对, 台, 辆.

**Count at the end (a space is required between the name and the count)**: `#给我 钻石剑 5`.

**Count occupying its own argument slot, in front**: `#给我 1个 齿轮`.

**Count glued to the name with no space**: `#给我 齿轮1`. The trailing digits are treated as the count only when **exact matching fails**, so items whose names contain digits are not damaged. This step runs before fuzzy correction; otherwise `钻石剑3` would be swallowed by fuzzy matching and become 1 diamond sword instead of 3.

**Enum values may be split apart**: internal spaces in enum arguments are removed entirely before the table lookup, so `#时间 白 天` also matches.

**Coordinates may be comma-separated**: `#传送 100,64,100`.

**Several ways to separate and omit enchants**:

- Multiple enchants may be separated by commas or spaces: `#附魔手持 锋利5 耐久3`, `#附魔手持 锋利5,耐久3`;
- A bare number is merged back into the previous entry: `#附魔手持 击退 255` means "击退 level 255", not "击退" plus an enchant called `255`;
- The level may be omitted, defaulting to 1: `#附魔手持 锋利`.

**Spoken whole-sentence effects**: you do not have to say the word "效果" first. Just say a full sentence after "给我 / 帮我 / 我要 / 给 / 要 / 来".

| You type | Actually sent |
| --- | --- |
| `#给我30秒的速度2效果` | `/effect give @s minecraft:speed 30 1` |
| `#给我速度2` | `/effect give @s minecraft:speed 30 1` |
| `#给我30秒的夜视` | `/effect give @s minecraft:night_vision 30 0` |
| `#给我速度2状态` | `/effect give @s minecraft:speed 30 1` |

This path is taken only when the input really points at a potion effect; if it cannot be recognized, the input is handed back to the normal path unchanged, so `#给我 钻石剑 5` is never stolen. The name must exactly hit the effect table; when the marker word "状态 / 效果" is present, the name may be fuzzy-corrected.

**Full-width to half-width, English to lowercase**: input is normalized first — full-width characters (including the full-width space and full-width digits) become half-width, English becomes lowercase, and leading/trailing whitespace is trimmed. Chinese is unaffected.

## Rules and Safety Design

This section explains the trade-offs the mod makes between guessing and not guessing.

### No permission bypass

The mod only translates Chinese into vanilla commands and then sends them **as the player**. It performs no permission checks and attempts to bypass none: whether a command takes effect is decided entirely by the server according to your permissions. So the same `#` input can behave completely differently in a single-player world where you are an operator versus on a multiplayer server — that is not a mod switch, the server decides.

When a translation fails or permissions are insufficient, you see the server's own message in the chat box, exactly as with a vanilla command.

### No silent word changes

Whenever fuzzy matching hits and a command is executed automatically, the mod always states in **yellow** what it corrected to:

```
[ChatCmd] 已按模糊匹配识别为「钻石剑」
```

There is no case where it quietly changes a character and carries on as if nothing happened.

### Second confirmation for high-risk commands

Commands that cause **large-scale irreversible damage** do not run the moment you send them; they ask you to nod first. Two groups currently need confirmation:

- **every `#清除`** (dropped items / experience orbs / effects / mobs);
- **`#重置 全部`**, including a bare `#重置` with no argument.

The flow is "**one warning + send `#确认` again**":

1. You send the high-risk command and **nothing runs**;
2. The mod replies in **yellow** with a warning stating what the command will really do (the wording is generated from the commands about to be sent, so it always tells the truth), plus a clickable `#确认` / `#取消` line;
3. You send `#确认` and only then does it run; `#取消` (or 取消, 算了, 不要, `no`, ...) declines;
4. Sending **any other command** in between cancels the pending state at once — there is no way for it to suddenly fire ten commands later.

Taking `#清除 怪物` as an example: since vanilla has no "hostile mobs only" selector, the warning states explicitly that animals, villagers and tamed pets are removed as well, instead of a vague "are you sure?".

Accepted replies for the confirmation step: `确认`, `确定`, `是`, `好`, `好的`, `可以`, `执行`, `yes`, `y`, `ok`.

### Fuzzy correction disabled for dangerous verbs

Fuzzy correction is turned off only for verbs that cause **irreversible damage**, which is currently just `#清除` (clear). One typo would kill entities in bulk, which is too high a price, so such verbs **report an error rather than guess**, letting you retype.

### Fuzzy correction thresholds

Fuzzy correction is enabled only **after exact matching has failed completely**, and two preconditions must both hold:

1. **Same writing system**: a Chinese alias is only compared against Chinese input, an English alias only against English input;
2. **Same first character**: this eliminates absurd cross-language, cross-character corrections at the root (otherwise "跳舞" would be corrected to "tp").

On top of that, the allowed edit distance (Levenshtein distance) depends on word length:

| Word length | Chinese | English / digits |
| --- | --- | --- |
| <= 2 | 0 (no correction) | 1 |
| 3 ~ 4 | 1 | 1 |
| >= 5 | 2 | 2 |

The Chinese threshold is stricter because short Chinese words have very low discriminative power: "下雨" (rain) and "下雪" (snow) differ by one character but mean completely different things. So Chinese words of two characters or fewer must match exactly.

In addition, automatic correction requires a **unique best match**: if two candidates are equally close (a tie), the mod gives up and lets you retype.

### Clickable candidates when nothing is recognized

When parsing fails, the mod prints a red hint and, below it, a line of **clickable candidates**:

```
[ChatCmd] 你是不是想用：#模式 创造
```

Clicking a candidate **fills it into the chat box**; it does not execute it. One click only puts the candidate into the chat box, you can still edit it, and it is sent only when you press Enter. So a wrong guess has no consequences.

Candidates are produced in two ways: first the few nearest by edit distance; if none can be produced (for example `#模式 飞行`), the mod simply lists every value of that command type for you to click.

Also, if a value is actually a whole-sentence phrase (for example "死亡不掉落" in `#模式 死亡不掉落`), the hint tells you the correct wording directly:

```
[ChatCmd] 「死亡不掉落」不归 模式 管。你是不是想用：#死亡不掉落 ？
```

### The `##` escape hatch

To send an ordinary chat message that genuinely starts with `#` (rather than a command), type two `#` characters:

| You type | What happens |
| --- | --- |
| `##你好` | Sent as ordinary chat: `#你好` |

The mod removes one `#`, sends the remaining text as **ordinary chat**, and confirms in gray text:

```
[ChatCmd] 已作为普通聊天发出：#你好
```

So you always have a way to say something starting with `#`; the mod never swallows it.

### Local error when an item is not recognized

When an item name is not recognized, the mod **reports the error locally and lists candidates**. It never assembles a Chinese name into `minecraft:<Chinese name>` and throws it at the server. Otherwise you would see a baffling "unknown item minecraft:齿轮1".

## Changelog

The complete version history lives in [CHANGELOG.md](CHANGELOG.md) (Chinese + English). Ready-to-use jars are on the [Releases](https://github.com/yunijiangzi111/chatcmd/releases) page.

## Building from Source

You need **JDK 21** (both Minecraft 1.21.1 and the mod target Java 21). Before building, make sure Gradle can find it; the easiest way is to set the `JAVA_HOME` environment variable:

```bat
:: Windows
set JAVA_HOME=C:\path\to\jdk-21
```

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
```

Alternatively, put `org.gradle.java.home=<path to JDK 21>` in `gradle.properties` (keeping that line in the **user-level** file `~/.gradle/gradle.properties` is recommended, so you never have to repeat it when switching projects or machines).

**JDK 17 is downloaded for you**: the `core` module targets Java 17 (Forge 1.20.1 runs on Java 17), and Gradle fetches it automatically through the `foojay-resolver-convention` plugin declared in `settings.gradle`.

Run the unit tests (`core` is a pure Java module with zero Minecraft dependencies, so it can be tested without the game):

```bat
gradlew.bat :core:test
```

```bash
./gradlew :core:test
```

Build the mod jar:

```bat
:: NeoForge (1.21.1) -> neoforge/build/libs/chatcmd-0.8.1-neoforge+mc1.21.1.jar
gradlew.bat :neoforge:jar

:: Forge (1.20.1) -> forge/build/libs/chatcmd-0.8.1-forge+mc1.20.1.jar
gradlew.bat :forge:reobfJar
```

```bash
./gradlew :neoforge:jar
./gradlew :forge:reobfJar
```

Note that on the Forge side you must run **`reobfJar`**, not `jar`: the Forge 1.20.1 development artifact (named with official mappings) has to go through a **reobfuscation** pass before the release game can load it. The `jar` output lands in `forge/build/devlibs/` (development only), while `reobfJar` produces the ready-to-ship artifact in `forge/build/libs/`.

Other useful tasks:

- `:core:check` — besides running the tests, this runs `checkCorePurity`: it scans the `core` sources and fails the build if any `net.minecraft.*` / `net.neoforged.*` / `net.minecraftforge.*` reference appears, keeping the "zero Minecraft dependencies" constraint from rotting as the code evolves.
- `:neoforge:runClient` — launches a development NeoForge client (1.21.1) with the mod loaded, for local debugging.
- `:forge:runClient` — launches a development Forge client (1.20.1) with the mod loaded, for local debugging.

Toolchain versions: Gradle 9.2.1, ModDevGradle 2.0.147, NeoForge 21.1.228 / Minecraft 1.21.1, Forge 47.3.0 / Minecraft 1.20.1.

## Releasing a New Version

This repository holds ChatCmd only, so the tag (version label) is simply `v<version>` with no mod prefix:

1. Write the code, then bump `mod_version` in `gradle.properties`.
2. Tag and push: `git tag v0.7.1 && git push origin v0.7.1`.

CI builds automatically and attaches the jars for **both platforms** to the [Releases](https://github.com/yunijiangzi111/chatcmd/releases) page — **no manual clicking in the browser**. Artifacts follow the community convention `<modid>-<version>-<platform>+mc<MCversion>.jar`, for example `chatcmd-0.7.1-neoforge+mc1.21.1.jar` and `chatcmd-0.7.1-forge+mc1.20.1.jar`.

## License

This project is licensed under the **MIT License**. The full text is available in the [LICENSE](LICENSE) file at the repository root.

You are free to use, modify, and redistribute this mod, including for commercial purposes, provided you **retain the copyright notice and the license text**.

The `mod_license` field in `gradle.properties` matches this, declared as `MIT`.
