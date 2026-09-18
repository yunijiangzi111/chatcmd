package io.github.xiaoh.chatcmd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
