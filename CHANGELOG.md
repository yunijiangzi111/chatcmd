# 更新日志

---

## ChatCmd 聊天指令

当前版本：**0.8.2** · 完整文档见 [README.md](README.md)

### 0.8.2 —— 2026-09-19

- 修复 `#附魔 手持 效率 5`（「附魔」和「手持」之间带空格）被当成物品名、报「不认识物品「手持」」并弹一串无关候选的问题：现在看到「手持 / 手上」会自动改判成 `#附魔手持`
- 「手持」只认整词：`#附魔 手持剑 锋利5` 里的「手持剑」是一个词，仍按物品名解析，不会被改判

### 0.8.1 —— 2026-09-19

- **附魔手持兼容被改过的 `/enchant`**：有的服务器（或服务端插件）把原版 `/enchant <目标> <附魔> [等级]` 换成了带 `add`（添加）/ `remove`（移除）子指令的形式，再发原版写法只会得到「错误的命令参数」。现在发送前会读一遍服务器下发到客户端的指令树，发现这台服务器的 `/enchant` 需要 `add` 才自动补上（`/enchant add @s 效率 5`），原版环境一个字符都不动
- 判定条件是「原版参数节点 `targets` 不在、而 `add` 子指令在」；两者并存说明原版写法仍然可用，就不插手。回显里显示的永远是实际发出的那条指令

### 0.8.0 —— 2026-09-19

- **高危指令二次确认**：`#清除 ...` 与 `#重置 全部`（含省略参数）不再直接执行，而是先弹一条黄色警告，明说这条指令真正会清掉哪些东西，再发一条 `#确认` 才执行；发别的指令即作废，`#取消` 可主动放弃
- 新增**生成**指令：`#生成 僵尸`、`#生成 坚守者 3`、`#生成 末影龙 ~ ~5 ~`（动词别名：生成、召唤、刷怪、`summon`、`spawn`），收约 80 个实体别名；原版 `/summon` 一次只能生成一个，数量大于 1 时自动拆成多条依次发出
- **规则**指令补全口语同义词，一条规则尽量把常见叫法收全：`naturalRegeneration` 现在「自然回血 / 自然恢复 / 自然回复 / 生命恢复 / 生命回复 / 生命自然恢复 / 自动回血 / 回血」都能命中；另有「不掉落物品 / 死亡不丢东西」「怪物破坏 / 生物变方块」「日夜交替 / 白天黑夜交替」「天气循环」「死亡提示」「生物生成 / 刷怪」「火焰蔓延」「立刻重生」
- 规则名支持**模糊纠错 + 可点击候选**，认不出时在本地报错；取消了旧的「原样透传」行为（输入已转小写，透传出去的驼峰规则名本来也无法生效）
- 实体名模糊纠错沿用「书写系统一致 + 首字符相同」双保险：「坚守者」与「守卫者」只差一个字，但首字不同，永远不会互相误纠

### 0.7.0 —— 2026-09-19

- 新增 **Forge 1.20.1 支持**，成品 jar：`chatcmd-0.7.0-forge+mc1.20.1.jar`（需要 Forge 47.3.0 或更高）
- 新增 `forge` 子模块，与 `neoforge` 共用同一份 `core`；`core` 降到 Java 17，一份字节码同时喂给两个平台（1.20.1 跑 Java 17，1.21.1 跑 Java 21）
- 附魔指令语法按平台自动切换：1.21.1 用物品组件 `[enchantments={...}]`，1.20.1 用 NBT `{Enchantments:[{id:...,lvl:...s}]}`。这是两个版本间**唯一**的指令差异
- Forge 侧声明 `clientSideOnly=true`，误装到专用服务器上不会加载、不会崩服（等价于 NeoForge 侧的 `dist = Dist.CLIENT`）
- `checkCorePurity` 的禁用引用列表补上 `net.minecraftforge.*`，Forge 平台的引用同样不许泄进 `core`
- CI（持续集成）改为同时构建并上传两个平台的 jar，打 tag 时一起挂到 Release

### 0.6.2 —— 2026-09-19

- 附魔名支持模糊纠错（打错字自动纠正）：`#附魔手持 锋利5，节支杀手5` 里的「节支杀手」会被纠成「节肢杀手」，并在回显里明说纠正了什么
- 成品 jar 改名对齐社区惯例：`chatcmd-0.6.2-neoforge+mc1.21.1.jar`（模组id-模组版本-平台+mcMC版本），内容和旧名 `chatcmd-0.6.2.jar` 完全一样

### 0.6.1 —— 2026-09-19

- 修复多个附魔被粘成一串、导致报「附魔等级不是正整数」的问题
- 现在支持逗号（`，` / `,`）或空格分隔多个附魔：`#附魔手持 锋利5，耐久3`
- 原版 `/enchant` 一次只能附一个附魔，所以手持形式写多个时会自动拆成多条指令依次发出

### 0.6.0 —— 2026-09-19

- 新增**结构查找**：`#找 村庄`、`#最近的村庄`、`#找 远古城市` 等，翻译成 `/locate structure`
- 查找成功后额外给出一行**可点击坐标**，单击直接传送过去（原版自带的坐标只能填进聊天框，还得再按回车）
- 村庄、矿井、沉船等使用结构标签（tag），一次覆盖全部变体

### 0.5.1 —— 2026-09-19

- 效果参数顺序定为「等级在前、时长在后」，贴合口语习惯：`#效果 速度 2 30`
- 支持带单位的时长（`30秒` / `30s`），带单位的那个永远算时长，所以顺序可以随便写
- 新增口语整句效果：`#给我30秒的速度2效果`、`#给我速度2`、`#给我30秒的夜视`

### 0.5.0 —— 2026-09-19

- 新增 `#重置`（时间 / 天气 / 效果 / 规则 / 全部）
- 新增 `#效果` 施加药水效果
- 新增 `#附魔`、`#附魔手持`
- 新增 `#经验`
- 新增 `#清除`（掉落物 / 经验球 / 怪物）

### 0.4.0 —— 2026-09-19

- 修复 `##你好` 的递归自触发问题
- 支持量词前移：`#给我1个齿轮`
- 支持无空格数量：`齿轮1`
- 物品改为精确匹配，认不出时本地报错并给出可点击候选
- 新增整句短语：`#白天`、`#永为白昼`（后者一次发两条指令）
- 解析失败时的候选文字可以点击（点击只填入聊天框，还能改）

### 0.3.0 —— 2026-09-19

- 新增 `#` 键快捷键（Shift+3）：直接打开聊天框并预填 `#`
- 新增中英文语言文件
- 物品解析接入游戏注册表的本地化名称表，整合包里那几百个模组物品都能认

### 0.2.0 —— 2026-09-18

- 新增 `##` 逃生通道：`##任意文本` 会把开头带 `#` 的话当普通聊天发出去
- 新增基于编辑距离（Levenshtein）的模糊纠错
- 新增短语语义表（如「死亡不掉落」）
- 修复中文乱码

### 0.1.0 —— 2026-09-18

- 首个可用版本：基础的中文口语 → 原版指令翻译

---

# Changelog

Every version change of this mod is recorded here, in descending order (newest first).

To download a ready-to-use jar, head to the [Releases](https://github.com/yunijiangzi111/chatcmd/releases) page.

## ChatCmd

Current version: **0.8.2** · Full documentation in [README.md](README.md)

### 0.8.2 — 2026-09-19

- Fixed `#附魔 手持 效率 5` (with a space between the verb 附魔 and 手持) being read as an item name, which reported "unknown item 手持" and offered a list of unrelated suggestions. The parser now re-routes to `#附魔手持` whenever it sees 手持 / 手上
- 手持 is only recognised as a whole word: in `#附魔 手持剑 锋利5` the token 手持剑 is one word, so it is still treated as an item name and is not re-routed

### 0.8.1 — 2026-09-19

- **enchant held now copes with a modified `/enchant`**: some servers (or server-side plugins) replace the vanilla `/enchant <targets> <enchantment> [<level>]` with a form that has `add` / `remove` subcommands, where the vanilla form only yields "Incorrect argument for command". Before sending, the mod now reads the command tree the server sent to the client, and when this server's `/enchant` requires `add` it is completed automatically (`/enchant add @s 效率 5`). On a vanilla server not a single character is changed
- The condition is "the vanilla argument node `targets` is absent while the `add` subcommand is present"; if both exist the vanilla form still works, so the mod keeps its hands off. The echo always shows the command that was really sent

### 0.8.0 — 2026-09-19

- **Second confirmation for high-risk commands**: `#清除 ...` and `#重置 全部` (including when the argument is omitted) no longer run immediately. Instead a yellow warning explains exactly what the command will really delete, and you must send `#确认` again to run it. Any other input cancels it; `#取消` declines on purpose
- Added the **summon** command: `#生成 僵尸`, `#生成 坚守者 3`, `#生成 末影龙 ~ ~5 ~` (verb aliases: 生成, 召唤, 刷怪, `summon`, `spawn`), with roughly 80 entity aliases. Vanilla `/summon` spawns only one entity per command, so a count greater than 1 is automatically split into several commands sent in sequence
- The **gamerule** command now collects the common colloquial names for each rule: `naturalRegeneration` is matched by 自然回血 / 自然恢复 / 自然回复 / 生命恢复 / 生命回复 / 生命自然恢复 / 自动回血 / 回血; also added 不掉落物品 / 死亡不丢东西, 怪物破坏 / 生物变方块, 日夜交替 / 白天黑夜交替, 天气循环, 死亡提示, 生物生成 / 刷怪, 火焰蔓延 and 立刻重生
- Rule names now support **fuzzy correction plus clickable candidates**, and report an error locally when nothing matches; the old "pass through as is" behaviour was removed (input is lowercased anyway, so a passed-through camelCase rule name could never work)
- Entity fuzzy matching keeps the "same writing system + same first character" double safeguard: 坚守者 and 守卫者 differ by one character but not in their first character, so they are never corrected into each other

### 0.7.0 — 2026-09-19

- Added **Forge 1.20.1 support**; the artifact is `chatcmd-0.7.0-forge+mc1.20.1.jar` (requires Forge 47.3.0 or newer)
- Added a `forge` submodule sharing the same `core` as `neoforge`; `core` was lowered to Java 17 so one set of bytecode feeds both platforms (1.20.1 runs on Java 17, 1.21.1 on Java 21)
- The enchantment command syntax now switches per platform: 1.21.1 uses item components `[enchantments={...}]`, 1.20.1 uses NBT `{Enchantments:[{id:...,lvl:...s}]}`. This is the **only** command difference between the two versions
- The Forge side declares `clientSideOnly=true`, so it is not loaded on a dedicated server and cannot crash it (the equivalent of `dist = Dist.CLIENT` on the NeoForge side)
- `checkCorePurity` now also forbids `net.minecraftforge.*`, so Forge platform references cannot leak into `core` either
- CI now builds and uploads the jars for both platforms and attaches them together to the Release when a tag is pushed

### 0.6.2 — 2026-09-19

- Enchantment names now support fuzzy correction (typos are fixed automatically): in `#附魔手持 锋利5，节支杀手5` the typo `节支杀手` is corrected to `节肢杀手`, and the mod explicitly tells you what it corrected
- The released jar was renamed to follow community convention: `chatcmd-0.6.2-neoforge+mc1.21.1.jar` (modid-modversion-platform+mcMCversion). Its contents are identical to the old `chatcmd-0.6.2.jar`

### 0.6.1 — 2026-09-19

- Fixed multiple enchantments being concatenated into one string, which caused a "enchantment level is not a positive integer" error
- Multiple enchantments can now be separated by commas (`，` / `,`) or spaces: `#附魔手持 锋利5，耐久3`
- Vanilla `/enchant` applies only one enchantment at a time, so the held-item form automatically splits into multiple commands sent in sequence

### 0.6.0 — 2026-09-19

- Added **structure lookup**: `#找 村庄`, `#最近的村庄`, `#找 远古城市`, etc., translated into `/locate structure`
- After a successful lookup, an extra **clickable coordinate** line is printed; a single click teleports you there (the vanilla coordinate can only be inserted into the chat box, and you still have to press Enter)
- Villages, mineshafts, shipwrecks and others use structure tags, covering every variant in one go

### 0.5.1 — 2026-09-19

- Effect parameter order is now "level first, duration second", matching how people actually speak: `#效果 速度 2 30`
- Durations may carry a unit (`30秒` / `30s`); whichever value has a unit is always treated as the duration, so the order is flexible
- Added whole-sentence natural-language effects: `#给我30秒的速度2效果`, `#给我速度2`, `#给我30秒的夜视`

### 0.5.0 — 2026-09-19

- Added `#重置` (time / weather / effects / gamerules / everything)
- Added `#效果` to apply potion effects
- Added `#附魔` and `#附魔手持`
- Added `#经验`
- Added `#清除` (dropped items / experience orbs / mobs)

### 0.4.0 — 2026-09-19

- Fixed a recursive self-trigger with `##你好`
- Added leading quantifiers: `#给我1个齿轮`
- Added count glued to the item name: `齿轮1`
- Item lookup is now exact; when it fails, the mod reports the error locally and offers clickable candidates
- Added whole-sentence phrases: `#白天`, `#永为白昼` (the latter sends two commands)
- Candidate text offered on parse failure is clickable (clicking only fills the chat box, so you can still edit it)

### 0.3.0 — 2026-09-19

- Added the `#` hotkey (Shift+3): opens the chat box with `#` pre-filled
- Added Chinese and English language files
- Item lookup now uses the game registry's localized name table, so the hundreds of modded items in a modpack are all recognized

### 0.2.0 — 2026-09-18

- Added the `##` escape hatch: `##any text` sends a line starting with `#` as ordinary chat
- Added fuzzy correction based on edit distance (Levenshtein)
- Added a phrase table (e.g. `死亡不掉落` → keepInventory)
- Fixed Chinese character corruption

### 0.1.0 — 2026-09-18

- First usable version: basic Chinese natural language → vanilla command translation
