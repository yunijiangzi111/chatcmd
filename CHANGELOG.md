# 更新日志

---

## ChatCmd 聊天指令

当前版本：**0.6.2** · 完整文档见 [README.md](README.md)

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

Current version: **0.6.2** · Full documentation in [README.md](README.md)

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
