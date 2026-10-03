package com.ayun.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘要生成的口径：摘要是正文的服务端派生物，作者侧看不到它，所以这里定的就是对外那两行字
 * （列表卡片与 meta description）。两条底线：代码与图片不进气泡，截断不截半字符
 */
class ExcerptUtilsTest {

    @Test
    @DisplayName("围栏代码块连内文整体去掉")
    void dropsFencedCodeWhole() {
        assertEquals("开头一段 结尾一段",
                ExcerptUtils.generate("开头一段\n\n```java\nint x = 1;\nSystem.out.println(x);\n```\n\n结尾一段", 150));
    }

    @Test
    @DisplayName("没配对的围栏残留也清掉，语言名不该留在摘要开头")
    void dropsUnpairedFenceMarker() {
        assertEquals("code here", ExcerptUtils.generate("```java\ncode here", 150));
    }

    @Test
    @DisplayName("行内代码整体去掉")
    void dropsInlineCode() {
        assertEquals("用 跑一遍就行", ExcerptUtils.generate("用 `mvn -o clean test` 跑一遍就行", 150));
    }

    @Test
    @DisplayName("图片整枚删掉，链接只留锚文本")
    void keepsLinkTextAndDropsImage() {
        assertEquals("看 和 文档",
                ExcerptUtils.generate("看 ![图1](http://a/b.png) 和 [文档](https://x.io/page)", 150));
    }

    @Test
    @DisplayName("纯图片正文得到空串，不是半句话")
    void imageOnlyBodyYieldsEmpty() {
        assertEquals("", ExcerptUtils.generate("![](a.png)\n![b](c.png)", 150));
    }

    @Test
    @DisplayName("标题、引用、列表、表格的标记位清掉，文字留下")
    void stripsBlockMarkupKeepsText() {
        assertEquals("标题 引用一行 项目一 项目二 有序 a b",
                ExcerptUtils.generate("## 标题\n> 引用一行\n- 项目一\n* 项目二\n1. 有序\n| a | b |\n|---|---|\n", 150));
    }

    @Test
    @DisplayName("强调符去掉，词内的连字符、下划线与波浪号留下")
    void dropsEmphasisButKeepsWordPunctuation() {
        assertEquals("加粗 与 斜体，well-known 与 updated_at，1~3 天，删除线",
                ExcerptUtils.generate("**加粗** 与 *斜体*，well-known 与 updated_at，1~3 天，~~删除线~~", 150));
    }

    @Test
    @DisplayName("残留 HTML 标签删掉，但「5 < 6 > 4」这种比较式不是标签")
    void dropsHtmlTagsNotAngleBrackets() {
        assertEquals("你好 当 5 < 6 > 4", ExcerptUtils.generate("<p>你好</p> 当 5 < 6 > 4", 150));
    }

    @Test
    @DisplayName("连续空白折成单空格并去掉首尾")
    void collapsesBlankRuns() {
        assertEquals("a b c", ExcerptUtils.generate("  a \n\n\t b   c  ", 150));
    }

    @Test
    @DisplayName("超长中文按码点截断，省略号计入上限")
    void truncatesOverLongChinese() {
        String excerpt = ExcerptUtils.generate("字".repeat(200), 150);
        assertEquals(150, excerpt.codePointCount(0, excerpt.length()));
        assertTrue(excerpt.endsWith("…"));
        assertEquals("字".repeat(149) + "…", excerpt);
    }

    @Test
    @DisplayName("恰好等于上限不算截断，不加省略号")
    void leavesExactLimitUnmarked() {
        String excerpt = ExcerptUtils.generate("字".repeat(150), 150);
        assertEquals(150, excerpt.codePointCount(0, excerpt.length()));
        assertFalse(excerpt.contains("…"));
    }

    @Test
    @DisplayName("emoji 是代理对，按码点截断才不会截出半个字符")
    void truncatesOnCodePointBoundary() {
        assertEquals("🙂".repeat(9) + "…", ExcerptUtils.generate("🙂".repeat(160), 10));
    }

    @Test
    @DisplayName("空正文与非正数上限都给空串")
    void yieldsEmptyForNoInput() {
        assertEquals("", ExcerptUtils.generate(null, 150));
        assertEquals("", ExcerptUtils.generate("   ", 150));
        assertEquals("", ExcerptUtils.generate("abc", 0));
    }
}
