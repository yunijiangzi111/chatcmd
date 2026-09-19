package io.github.xiaoh.chatcmd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CommandParser} 的单元测试。脱离游戏运行，不需要 Minecraft。
 */
class CommandParserTest {

    private final CommandParser parser = new CommandParser(AliasTable.defaultTable(), ItemIdResolver.EMPTY);

    // ---------- 正常路径：计划 §5 的 6 个模板 ----------

    @Test
    @DisplayName("给我 钻石剑 5 -> give @s minecraft:diamond_sword 5")
    void giveWithCount() {
        ParseResult r = parser.parse("#给我 钻石剑 5");
        assertTrue(r.isOk(), r.toString());
        assertEquals("give @s minecraft:diamond_sword 5", r.payload());
    }

    @Test
    @DisplayName("数量缺省时默认给 1 个")
    void giveWithoutCountDefaultsToOne() {
        assertEquals("give @s minecraft:diamond_sword 1", parser.parse("#给我 钻石剑").payload());
    }

    @Test
    @DisplayName("时间白天 -> time set day")
    void timeDay() {
        assertEquals("time set day", parser.parse("#时间白天").payload());
    }

    @Test
    @DisplayName("传送 100 64 100 -> tp @s 100 64 100")
    void tpAbsolute() {
        assertEquals("tp @s 100 64 100", parser.parse("#传送 100 64 100").payload());
    }

    @Test
    @DisplayName("模式创造 -> gamemode creative")
    void gamemodeCreative() {
        assertEquals("gamemode creative", parser.parse("#模式创造").payload());
    }

    @Test
    @DisplayName("天气晴 -> weather clear")
    void weatherClear() {
        assertEquals("weather clear", parser.parse("#天气晴").payload());
    }

    @Test
    @DisplayName("难度和平 -> difficulty peaceful")
    void difficultyPeaceful() {
        assertEquals("difficulty peaceful", parser.parse("#难度和平").payload());
    }

    // ---------- 匹配算法细节 ----------

    @Test
    @DisplayName("动词取最长前缀：给我 优先于 给")
    void longestVerbPrefixWins() {
        // 若误取较短的「给」，剩余串会变成「我石头 2」，结果就不是下面这条指令
        assertEquals("give @s minecraft:stone 2", parser.parse("#给我石头 2").payload());
    }

    @Test
    @DisplayName("枚举类参数去掉内部空格后整体查表")
    void enumArgsIgnoreInnerSpaces() {
        assertEquals("time set day", parser.parse("#时间 白 天").payload());
    }

    @Test
    @DisplayName("多参数按逗号切分")
    void commaSeparatedArgs() {
        assertEquals("tp @s 100 64 100", parser.parse("#传送 100,64,100").payload());
    }

    @Test
    @DisplayName("坐标支持相对写法 ~ / ~5 / ~-3")
    void relativeCoordinates() {
        assertEquals("tp @s ~ ~5 ~-3", parser.parse("#传送 ~ ~5 ~-3").payload());
    }

    @Test
    @DisplayName("全角空格与全角数字会被归一化成半角")
    void fullWidthNormalized() {
        assertEquals("time set day", parser.parse("#时间\u3000白天").payload());
        assertEquals("tp @s 100 64 100", parser.parse("#传送\uff11\uff10\uff10 64 100").payload());
    }

    @Test
    @DisplayName("英文输入统一转小写")
    void englishUppercased() {
        assertEquals("time set day", parser.parse("#TIME DAY").payload());
    }

    // ---------- 物品名三级回退 ----------

    @Test
    @DisplayName("手工表没有时走原版 ID 透传，自动补 minecraft: 前缀")
    void itemFallsThroughToRawId() {
        assertEquals("give @s minecraft:diamond_sword 3", parser.parse("#给我 diamond_sword 3").payload());
    }

    @Test
    @DisplayName("带命名空间的 ID 原样透传")
    void namespacedIdPassThrough() {
        assertEquals("give @s create:cogwheel 1", parser.parse("#给我 create:cogwheel").payload());
    }

    @Test
    @DisplayName("优先使用 ItemIdResolver 的解析结果")
    void resolverTakesPrecedence() {
        ItemIdResolver resolver = alias ->
                alias.equals("custom_thing") ? Optional.of("mymod:custom_thing") : Optional.empty();
        CommandParser withResolver = new CommandParser(AliasTable.defaultTable(), resolver);
        assertEquals("give @s mymod:custom_thing 1", withResolver.parse("#给我 custom_thing").payload());
    }

    // ---------- 边界与失败路径 ----------

    @Test
    @DisplayName("不以 # 开头 -> 不归我管，原样放行")
    void notMyInput() {
        assertSame(ParseResult.Status.NOT_MY_INPUT, parser.parse("你好啊").status());
        assertSame(ParseResult.Status.NOT_MY_INPUT, parser.parse("").status());
        assertSame(ParseResult.Status.NOT_MY_INPUT, parser.parse(null).status());
    }

    @Test
    @DisplayName("##任意文本 -> 逃生通道，去掉一个 # 后作为聊天发出")
    void escapeHatch() {
        ParseResult r = parser.parse("###不是指令");
        assertSame(ParseResult.Status.ESCAPE_CHAT, r.status());
        assertEquals("##不是指令", r.payload());
    }

    @Test
    @DisplayName("认不出动词 -> UNKNOWN_VERB")
    void unknownVerb() {
        assertSame(ParseResult.Status.UNKNOWN_VERB, parser.parse("#跳舞").status());
        assertSame(ParseResult.Status.UNKNOWN_VERB, parser.parse("#").status());
    }

    @Test
    @DisplayName("参数个数不对 -> BAD_ARGS")
    void badArgCount() {
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#传送 100 64").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#时间").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#给我 钻石剑 5 6").status());
    }

    @Test
    @DisplayName("坐标格式不对 -> BAD_ARGS")
    void badCoordinate() {
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#传送 a b c").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#传送 100 64 ~x").status());
    }

    @Test
    @DisplayName("枚举取值不在表里 -> UNKNOWN_VALUE")
    void unknownEnumValue() {
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#模式飞行").status());
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#天气下雪").status());
    }

    @Test
    @DisplayName("数量不是正整数 -> BAD_ARGS")
    void badCount() {
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#给我 钻石剑 abc").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#给我 钻石剑 0").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#给我 钻石剑 -1").status());
    }

    @Test
    @DisplayName("解析失败时都带有给用户看的提示")
    void errorsCarryHint() {
        assertTrue(parser.parse("#跳舞").hint().contains("动词"));
        assertTrue(parser.parse("#传送 100 64").hint().contains("#传送"));
        assertTrue(parser.parse("#模式飞行").hint().contains("#模式"));
    }

    // ---------- v0.2：同义词补充 ----------

    @Test
    @DisplayName("同义词：中午 -> noon（不需要靠模糊匹配猜）")
    void timeNoonSynonym() {
        assertEquals("time set noon", parser.parse("#时间 中午").payload());
        assertEquals("time set day", parser.parse("#时间 早上").payload());
    }

    // ---------- v0.2：整句短语 + 游戏规则 ----------

    @Test
    @DisplayName("整句短语：死亡不掉落 -> gamerule keepInventory true")
    void phraseKeepInventory() {
        ParseResult r = parser.parse("#死亡不掉落");
        assertTrue(r.isOk(), r.toString());
        assertEquals("gamerule keepInventory true", r.payload());
    }

    @Test
    @DisplayName("整句短语：关闭死亡不掉落 -> gamerule keepInventory false")
    void phraseKeepInventoryOff() {
        assertEquals("gamerule keepInventory false", parser.parse("#关闭死亡不掉落").payload());
    }

    @Test
    @DisplayName("走错动词时提示正确说法，而不是只说「取值无效」")
    void wrongVerbGivesPhraseSuggestion() {
        ParseResult r = parser.parse("#模式 死亡不掉落");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertTrue(r.hint().contains("#死亡不掉落"), r.hint());
    }

    @Test
    @DisplayName("游戏规则：中文规则名 + 中文开关")
    void gameruleChinese() {
        assertEquals("gamerule keepInventory true", parser.parse("#规则 死亡不掉落 开").payload());
        assertEquals("gamerule mobGriefing false", parser.parse("#游戏规则 生物破坏 关").payload());
    }

    @Test
    @DisplayName("游戏规则：输入被转小写后仍还原成原版驼峰写法")
    void gameruleEnglishCamelCase() {
        assertEquals("gamerule keepInventory true", parser.parse("#gamerule keepinventory on").payload());
        assertEquals("gamerule mobGriefing false", parser.parse("#规则 mobgriefing off").payload());
    }

    @Test
    @DisplayName("游戏规则开关不合法 -> UNKNOWN_VALUE")
    void gameruleBadSwitch() {
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#规则 死亡不掉落 也许").status());
    }

    // ---------- v0.2：模糊匹配（编辑距离容错） ----------

    @Test
    @DisplayName("英文动词打错一个字 -> 自动纠错并回显命中项")
    void fuzzyVerbEnglish() {
        ParseResult r = parser.parse("#giv 钻石剑 2");
        assertTrue(r.isOk(), r.toString());
        assertEquals("give @s minecraft:diamond_sword 2", r.payload());
        assertTrue(r.hint().contains("模糊匹配"), r.hint());
        assertTrue(r.hint().contains("give"), r.hint());
    }

    @Test
    @DisplayName("物品名打错一个字 -> 钻右剑 纠正为 钻石剑")
    void fuzzyItem() {
        ParseResult r = parser.parse("#给我 钻右剑 3");
        assertTrue(r.isOk(), r.toString());
        assertEquals("give @s minecraft:diamond_sword 3", r.payload());
        assertTrue(r.hint().contains("钻石剑"), r.hint());
    }

    @Test
    @DisplayName("整句短语打错一个字 -> 自动纠错")
    void fuzzyPhrase() {
        ParseResult r = parser.parse("#死亡不掉洛");
        assertTrue(r.isOk(), r.toString());
        assertEquals("gamerule keepInventory true", r.payload());
        assertTrue(r.hint().contains("死亡不掉落"), r.hint());
    }

    @Test
    @DisplayName("中文短词不自动纠错：下雪 不会被纠成 下雨、飞行 不会被纠成别的模式")
    void shortChineseNotAutoCorrected() {
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#天气下雪").status());
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#模式飞行").status());
    }

    @Test
    @DisplayName("动词差太远 -> 报错，只在提示里给出「你是不是想用」")
    void fuzzyTooFarFallsBackToError() {
        ParseResult r = parser.parse("#模组 创造");
        assertSame(ParseResult.Status.UNKNOWN_VERB, r.status());
        assertTrue(r.hint().contains("#模式"), r.hint());
    }

    @Test
    @DisplayName("毫不相关的输入不会被硬纠错")
    void unrelatedInputNotCorrected() {
        ParseResult r = parser.parse("#跳舞");
        assertSame(ParseResult.Status.UNKNOWN_VERB, r.status());
        assertFalse(r.hint().contains("你是不是想用"), r.hint());
    }

    @Test
    @DisplayName("模糊匹配的回显只出现在成功结果里，精确匹配不带说明")
    void exactMatchHasNoNote() {
        assertTrue(parser.parse("#给我 钻石剑 5").hint().isEmpty());
    }

    // ---------- v0.4：实机复测反馈 ----------

    @Test
    @DisplayName("逃生通道：##你好 只去掉一个 #，不会被当成指令报错")
    void escapeHatchSingleHash() {
        ParseResult r = parser.parse("##你好");
        assertSame(ParseResult.Status.ESCAPE_CHAT, r.status());
        assertEquals("#你好", r.payload());
    }

    @Test
    @DisplayName("数量前移：1把钻石剑 / 给我1个石头 / 1个 石头 / 3 石头")
    void countMovedForward() {
        assertEquals("give @s minecraft:diamond_sword 1", parser.parse("#给我 1把钻石剑").payload());
        assertEquals("give @s minecraft:stone 1", parser.parse("#给我1个石头").payload());
        assertEquals("give @s minecraft:stone 2", parser.parse("#给我 2个 石头").payload());
        assertEquals("give @s minecraft:stone 3", parser.parse("#给我 3 石头").payload());
    }

    @Test
    @DisplayName("名字和数量粘在一起：原样认不出才拆尾部数字")
    void trailingCountSplit() {
        assertEquals("give @s minecraft:diamond_sword 3", parser.parse("#给我 钻石剑3").payload());
    }

    @Test
    @DisplayName("认不出的中文物品名在本地报错 + 给候选，不再拼成 minecraft: 前缀丢给服务端")
    void unknownChineseItemStaysLocal() {
        ItemIdResolver resolver = new ItemIdResolver() {
            @Override
            public Optional<String> resolve(String alias) {
                return alias.equals("齿轮") ? Optional.of("create:cogwheel") : Optional.empty();
            }

            @Override
            public Collection<String> candidates() {
                return List.of("齿轮", "大齿轮");
            }
        };
        CommandParser withResolver = new CommandParser(AliasTable.defaultTable(), resolver);

        // 模组物品精准匹配照常工作
        assertEquals("give @s create:cogwheel 2", withResolver.parse("#给我 齿轮 2").payload());
        // 尾部数字拆分之后，仍然是精准匹配
        assertEquals("give @s create:cogwheel 1", withResolver.parse("#给我 齿轮1").payload());

        ParseResult r = withResolver.parse("#给我 齿轮组 3");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertTrue(r.suggestions().contains("#给我 齿轮 3"), r.suggestions().toString());
    }

    @Test
    @DisplayName("整句短语：永为白昼 = 先调到白天 + 再关掉昼夜交替（一条口语两条指令）")
    void phraseEternalDay() {
        ParseResult r = parser.parse("#永为白昼");
        assertTrue(r.isOk(), r.toString());
        assertEquals(List.of("time set day", "gamerule doDaylightCycle false"), r.commands());
        assertEquals("time set day\ngamerule doDaylightCycle false", r.payload());
    }

    @Test
    @DisplayName("口语整句：直接说 白天 / 夜晚 就是一条指令")
    void bareTimeWords() {
        assertEquals("time set day", parser.parse("#白天").payload());
        assertEquals(List.of("time set day"), parser.parse("#白天").commands());
        assertEquals("time set night", parser.parse("#夜晚").payload());
    }

    @Test
    @DisplayName("枚举取值打错 -> 给出可点击候选，点一下就填回聊天框")
    void enumValueSuggestions() {
        ParseResult near = parser.parse("#天气下雪");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, near.status());
        assertTrue(near.suggestions().contains("#天气下雨"), near.suggestions().toString());

        // 差得太远时，把该类型的取值全列出来让用户挑
        ParseResult far = parser.parse("#模式飞行");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, far.status());
        assertEquals(List.of("#模式创造", "#模式生存", "#模式冒险", "#模式旁观"), far.suggestions());
    }

    @Test
    @DisplayName("认不出动词 -> 候选保留原有参数：模组 创造 -> #模式 创造")
    void unknownVerbSuggestions() {
        ParseResult r = parser.parse("#模组 创造");
        assertSame(ParseResult.Status.UNKNOWN_VERB, r.status());
        assertTrue(r.suggestions().contains("#模式 创造"), r.suggestions().toString());
    }

    @Test
    @DisplayName("毫不相关时不给候选，避免误导")
    void noSuggestionsWhenNothingClose() {
        assertTrue(parser.parse("#跳舞").suggestions().isEmpty());
    }

    @Test
    @DisplayName("游戏规则开关打错 -> 候选是 开 / 关")
    void gameruleSwitchSuggestions() {
        ParseResult r = parser.parse("#规则 死亡不掉落 也许");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertEquals(List.of("#规则 死亡不掉落 开", "#规则 死亡不掉落 关"), r.suggestions());
    }

    // ---------- v0.5：重置 / 效果 / 附魔 / 清除 ----------

    @Test
    @DisplayName("重置 时间 = 把昼夜交替打开，这就是「结束永昼」")
    void resetTime() {
        assertEquals("gamerule doDaylightCycle true", parser.parse("#重置 时间").payload());
        assertEquals("gamerule doDaylightCycle true", parser.parse("#重置 昼夜交替").payload());
        // 「恢复」也是同一个动词
        assertEquals("gamerule doDaylightCycle true", parser.parse("#恢复 时间").payload());
    }

    @Test
    @DisplayName("重置 效果 = 清空身上药水效果")
    void resetEffects() {
        assertEquals("effect clear @s", parser.parse("#重置 效果").payload());
        assertEquals("effect clear @s", parser.parse("#重置 药水效果").payload());
    }

    @Test
    @DisplayName("重置 天气 = 开回天气循环 + 放晴")
    void resetWeather() {
        assertEquals(List.of("gamerule doWeatherCycle true", "weather clear"),
                parser.parse("#重置 天气").commands());
    }

    @Test
    @DisplayName("不带对象的重置 = 全部，一次发 6 条指令")
    void resetWithoutTargetIsAll() {
        ParseResult r = parser.parse("#重置");
        assertTrue(r.isOk(), r.toString());
        assertEquals(List.of(
                "gamerule doDaylightCycle true",
                "gamerule doWeatherCycle true",
                "weather clear",
                "effect clear @s",
                "gamerule keepInventory false",
                "gamerule mobGriefing true"), r.commands());
        assertEquals(r.payload(), parser.parse("#重置 全部").payload());
    }

    @Test
    @DisplayName("重置对象不认识 -> 报错并给候选")
    void resetUnknownTarget() {
        ParseResult r = parser.parse("#重置 宇昼");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertTrue(r.hint().contains("#重置"), r.hint());
    }

    @Test
    @DisplayName("效果：等级在前、时长在后（等级减 1 换算成 amplifier）")
    void effectWithDurationAndLevel() {
        // 等级在前、时长在后；原版第 3 个参数是 amplifier，所以等级要减 1
        assertEquals("effect give @s minecraft:speed 30 1", parser.parse("#效果 速度 2 30").payload());
        assertEquals("effect give @s minecraft:strength 60 0", parser.parse("#效果 力量 60秒").payload());
    }

    @Test
    @DisplayName("效果：带「秒」单位的那个永远算时长，顺序可以调")
    void effectWithUnitIgnoresOrder() {
        assertEquals("effect give @s minecraft:speed 30 1", parser.parse("#效果 速度 30秒 2").payload());
        assertEquals("effect give @s minecraft:speed 30 1", parser.parse("#效果 速度 2 30s").payload());
    }

    @Test
    @DisplayName("效果：只写名字时用原版默认时长 30 秒、I 级")
    void effectBareName() {
        assertEquals("effect give @s minecraft:night_vision 30 0", parser.parse("#效果 夜视").payload());
        assertEquals("effect give @s minecraft:speed 30 0", parser.parse("#效果 speed").payload());
    }

    @Test
    @DisplayName("效果名不认识 -> 报错 + 可点击候选")
    void effectUnknownName() {
        ParseResult r = parser.parse("#效果 飞飞飞");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertFalse(r.suggestions().isEmpty(), r.toString());
        assertTrue(r.suggestions().contains("#效果 速度"), r.suggestions().toString());
    }

    @Test
    @DisplayName("效果参数不是数字 -> BAD_ARGS；等级 0 也拒掉")
    void effectBadDuration() {
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#效果 速度 很久").status());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#效果 速度 0 30").status());
    }

    @Test
    @DisplayName("口语整句：给我30秒的速度2效果")
    void effectSentenceFull() {
        ParseResult r = parser.parse("#给我30秒的速度2效果");
        assertTrue(r.isOk(), r.toString());
        assertEquals("effect give @s minecraft:speed 30 1", r.payload());
        assertTrue(r.hint().contains("amplifier"), r.hint());
    }

    @Test
    @DisplayName("口语整句：给我速度2 / 给我30秒的夜视 / 给我速度2状态")
    void effectSentenceVariants() {
        assertEquals("effect give @s minecraft:speed 30 1", parser.parse("#给我速度2").payload());
        assertEquals("effect give @s minecraft:night_vision 30 0", parser.parse("#给我30秒的夜视").payload());
        assertEquals("effect give @s minecraft:speed 30 1", parser.parse("#给我速度2状态").payload());
        assertEquals("effect give @s minecraft:speed 60 0", parser.parse("#帮我速度60秒").payload());
    }

    @Test
    @DisplayName("口语整句不抢物品的活：给物品还是走 #给我")
    void effectSentenceDoesNotStealGive() {
        assertEquals("give @s minecraft:diamond_sword 5", parser.parse("#给我 钻石剑 5").payload());
        assertEquals("give @s minecraft:stone 2", parser.parse("#给我石头 2").payload());
        assertEquals("give @s minecraft:diamond_sword 1", parser.parse("#给我1个钻石剑").payload());
    }

    @Test
    @DisplayName("口语整句带「效果/状态」标记时，效果名允许模糊纠错")
    void effectSentenceFuzzy() {
        ParseResult r = parser.parse("#给我30秒的生命恢夏效果");
        assertTrue(r.isOk(), r.toString());
        assertEquals("effect give @s minecraft:regeneration 30 0", r.payload());
        assertTrue(r.hint().contains("模糊匹配"), r.hint());
    }

    @Test
    @DisplayName("附魔：击退255木棍 -> 物品组件语法")
    void enchantSingle() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:knockback\":255}]",
                parser.parse("#附魔 木棍 击退255").payload());
    }

    @Test
    @DisplayName("附魔：名字和等级之间加空格也行")
    void enchantWithSpaceBetween() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:knockback\":255}]",
                parser.parse("#附魔 木棍 击退 255").payload());
    }

    @Test
    @DisplayName("附魔：逗号分隔可以挂多个附魔")
    void enchantMultiple() {
        assertEquals("give @s minecraft:diamond_sword[enchantments="
                        + "{\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3}]",
                parser.parse("#附魔 钻石剑 锋利5,耐久3").payload());
    }

    @Test
    @DisplayName("附魔：不写等级默认 1 级")
    void enchantDefaultLevel() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:mending\":1}]",
                parser.parse("#附魔 木棍 经验修补").payload());
    }

    @Test
    @DisplayName("附魔名取最长前缀：火焰保护 不会被 火焰附加 抢先命中")
    void enchantLongestPrefixWins() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:fire_protection\":3}]",
                parser.parse("#附魔 木棍 火焰保护3").payload());
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:fire_aspect\":3}]",
                parser.parse("#附魔 木棍 火焰附加3").payload());
    }

    @Test
    @DisplayName("附魔：物品名不认识 -> 本地报错，不拼 minecraft: 前缀")
    void enchantUnknownItem() {
        ParseResult r = parser.parse("#附魔 齿轮组 击退5");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertTrue(r.hint().contains("物品"), r.hint());
    }

    @Test
    @DisplayName("附魔名打错 -> 模糊纠错（唯一最近才纠），并明说纠成了什么")
    void enchantNameTypoAutoCorrected() {
        ParseResult r = parser.parse("#附魔 木棍 经验修休3");
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:mending\":3}]", r.payload());
        // 绝不静默改词，必须告诉玩家纠成了什么
        assertTrue(r.hint().contains("已按模糊匹配识别为「经验修补」"), r.hint());
    }

    @Test
    @DisplayName("附魔名纠不动 -> 报错 + 给候选，不猜")
    void enchantUnknownName() {
        // 首字对不上：模糊匹配的第一道闸门就挡掉了
        ParseResult far = parser.parse("#附魔 木棍 飞天遁地3");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, far.status());
        assertTrue(far.payload().isEmpty(), far.payload());
        assertFalse(far.suggestions().isEmpty());

        // 「经验补休」是交换了两个字（编辑距离 2），超出安全阈值，宁可报错也不猜
        ParseResult swapped = parser.parse("#附魔 木棍 经验补休3");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, swapped.status());
        assertTrue(swapped.suggestions().contains("#附魔 木棍 经验修补"),
                swapped.suggestions().toString());
    }

    @Test
    @DisplayName("附魔名认得出但等级写坏 -> 报等级错，并给出只带附魔名的候选")
    void enchantBadLevel() {
        ParseResult r = parser.parse("#附魔 木棍 击退r255");
        assertSame(ParseResult.Status.BAD_ARGS, r.status());
        assertTrue(r.hint().contains("附魔等级"), r.hint());
        assertTrue(r.suggestions().contains("#附魔 木棍 击退"), r.suggestions().toString());
    }

    @Test
    @DisplayName("附魔手持：给手上拿的那件附魔")
    void enchantHeld() {
        assertEquals("enchant @s minecraft:sharpness 10", parser.parse("#附魔手持 锋利10").payload());
        assertEquals("enchant @s minecraft:sharpness 10", parser.parse("#附魔手持 锋利 10").payload());
        assertEquals("enchant @s minecraft:mending 1", parser.parse("#附魔手持 经验修补").payload());
    }

    @Test
    @DisplayName("附魔手持：多个附魔拆成多条指令（原版 /enchant 一次只能附一个）")
    void enchantHeldMultiple() {
        String two = "enchant @s minecraft:sharpness 5\nenchant @s minecraft:unbreaking 3";
        // 英文逗号
        assertEquals(two, parser.parse("#附魔手持 锋利5,耐久3").payload());
        // 中文逗号（normalize 会把全角逗号转成半角）
        assertEquals(two, parser.parse("#附魔手持 锋利5，耐久3").payload());
        // 空格分隔也行
        assertEquals(two, parser.parse("#附魔手持 锋利5 耐久3").payload());
        // 用户实际报的那条（节肢杀手 拼写正确时）
        assertEquals("enchant @s minecraft:sharpness 5\nenchant @s minecraft:bane_of_arthropods 5",
                parser.parse("#附魔手持 锋利5，节肢杀手5").payload());
        // 拆多条时要说明
        assertTrue(parser.parse("#附魔手持 锋利5,耐久3").hint().contains("拆成 2 条"),
                parser.parse("#附魔手持 锋利5,耐久3").hint());
    }

    @Test
    @DisplayName("附魔：多个附魔用中文逗号隔开也能识别")
    void enchantMultipleWithFullWidthComma() {
        assertEquals("give @s minecraft:diamond_sword[enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3}]",
                parser.parse("#附魔 钻石剑 锋利5，耐久3").payload());
        assertEquals("give @s minecraft:diamond_sword[enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3}]",
                parser.parse("#附魔 钻石剑 锋利5 耐久3").payload());
    }

    @Test
    @DisplayName("附魔手持：第二个附魔名打错字 -> 只纠那一项，另一项原样保留")
    void enchantHeldSecondNameTypo() {
        ParseResult r = parser.parse("#附魔手持 锋利5，节支杀手5");
        assertEquals("enchant @s minecraft:sharpness 5\nenchant @s minecraft:bane_of_arthropods 5",
                r.payload());
        assertTrue(r.hint().contains("已按模糊匹配识别为「节肢杀手」"), r.hint());
    }

    @Test
    @DisplayName("附魔：数字尾巴合并规则 —— 击退 255 是一个附魔，不是两个")
    void enchantBareLevelMerge() {
        assertEquals("give @s minecraft:stick[enchantments={\"minecraft:knockback\":255}]",
                parser.parse("#附魔 木棍 击退 255").payload());
        // 前一项已经带等级了，后面的裸数字就不并了
        assertSame(ParseResult.Status.UNKNOWN_VALUE, parser.parse("#附魔 木棍 击退5 255").status());
    }

    @Test
    @DisplayName("经验：xp add")
    void xpAdd() {
        assertEquals("xp add @s 100", parser.parse("#经验 100").payload());
        assertEquals("xp add @s 100", parser.parse("#xp 100").payload());
        assertSame(ParseResult.Status.BAD_ARGS, parser.parse("#经验 一堆").status());
    }

    @Test
    @DisplayName("清除：掉落物 / 经验球 / 怪物")
    void clearEntities() {
        assertEquals("kill @e[type=minecraft:item]", parser.parse("#清除 掉落物").payload());
        assertEquals("kill @e[type=minecraft:experience_orb]", parser.parse("#清除 经验球").payload());
        assertEquals("kill @e[type=!player,type=!minecraft:item,type=!minecraft:experience_orb]",
                parser.parse("#清除 怪物").payload());
    }

    @Test
    @DisplayName("清除：带空格的「清除 效果」和「清除效果」都走清效果")
    void clearEffectsBothWays() {
        assertEquals("effect clear @s", parser.parse("#清除效果").payload());
        assertEquals("effect clear @s", parser.parse("#清除 效果").payload());
    }

    @Test
    @DisplayName("危险动词禁掉模糊纠错：清楚 掉落物 绝不会被执行")
    void clearVerbNeverFuzzyMatched() {
        ParseResult r = parser.parse("#清楚 掉落物");
        assertSame(ParseResult.Status.UNKNOWN_VERB, r.status());
        assertTrue(r.payload().isEmpty(), r.payload());
    }

    @Test
    @DisplayName("常用短语：设置重生点 / 杀死自己")
    void miscPhrases() {
        assertEquals("spawnpoint", parser.parse("#设置重生点").payload());
        assertEquals("kill @s", parser.parse("#杀死自己").payload());
        assertEquals("kill @s", parser.parse("#自杀").payload());
    }

    @Test
    @DisplayName("结构查找：最近的村庄 / 找村庄，村庄走结构标签一次找齐全部变体")
    void locateVillage() {
        assertEquals("locate structure #minecraft:village", parser.parse("#最近的村庄").payload());
        assertEquals("locate structure #minecraft:village", parser.parse("#找 村庄").payload());
        assertEquals("locate structure #minecraft:village", parser.parse("#查找村子").payload());
    }

    @Test
    @DisplayName("结构查找：远古城市 / 试炼密室 / 海底神殿")
    void locateSpecific() {
        assertEquals("locate structure minecraft:ancient_city", parser.parse("#找远古城市").payload());
        assertEquals("locate structure minecraft:trial_chambers", parser.parse("#查找 试炼 密室").payload());
        assertEquals("locate structure minecraft:monument", parser.parse("#最近的 海底神殿").payload());
        assertEquals("locate structure minecraft:fortress", parser.parse("#找 下界要塞").payload());
    }

    @Test
    @DisplayName("结构查找：认不出的结构名本地报错，并给出可点击候选")
    void locateUnknown() {
        ParseResult r = parser.parse("#找 飞飞飞");
        assertSame(ParseResult.Status.UNKNOWN_VALUE, r.status());
        assertTrue(r.payload().isEmpty(), r.payload());
        assertFalse(r.suggestions().isEmpty());
        assertTrue(r.suggestions().contains("#找 村庄"), r.suggestions().toString());
    }

    @Test
    @DisplayName("结构查找：新增的「找」动词不影响原有的给物品写法")
    void locateDoesNotSteal() {
        assertEquals("give @s minecraft:diamond_sword 1", parser.parse("#给我 钻石剑").payload());
        assertEquals("locate structure #minecraft:village", parser.parse("#找 村庄").payload());
    }
}
