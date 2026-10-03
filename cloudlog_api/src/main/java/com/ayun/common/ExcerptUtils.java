package com.ayun.common;

import java.util.regex.Pattern;

/**
 * 文章摘要生成：把 Markdown 正文折成一段纯文本摘录，是「摘要是正文的服务端派生物」这条口径的唯一实现处
 * （business-model.md §2.3、db-design.md §1.7）。作者侧没有摘要输入框，所以这条必须是确定性纯函数——
 * 同一正文任何时候生成都得到同一个值，列表卡片与 meta description 才不会随时间漂移
 */
public final class ExcerptUtils {

    /**
     * 摘要的码点上限，含尾部省略号。摘要列是 TEXT 不限宽，这一档取自展示口径：
     * 列表卡片那一行与搜索结果的 description 都在此宽度内收住
     */
    public static final int MAX_LENGTH = 150;

    /**
     * 围栏代码块（三个及以上反引号，连内文整体去掉）。排在最前是必须的：不先摘掉它，
     * 下面的行内代码规则会跨过整段代码去配对反引号，把中间的正文一起吃掉
     */
    private static final Pattern FENCED_CODE = Pattern.compile("(?s)```+.*?```+");

    /** 行内代码，连内容整体去掉——摘录里出现一个标识符不构成「这段话讲了什么」 */
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");

    /**
     * 没配对的围栏残留：只写了开头的 ``` 就结束正文（或结尾少了三个反引号）时，
     * 上面那条配对规则吃不到它，代码块的语言名会留在摘要开头
     */
    private static final Pattern UNPAIRED_FENCE = Pattern.compile("(?m)^[ \\t]*`{3,}[^\\n]*$");

    /** 图片 {@code ![alt](url)} 整体去掉：alt 是给屏幕阅读器与破图占位的，不是给摘要的 */
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");

    /** 链接 {@code [text](url)} 只留锚文本 */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");

    /**
     * 残留 HTML 标签。只认「尖括号里以字母或斜杠开头」的真标签，
     * 不写成 {@code <[^>]+>}——否则正文里的「5 &lt; 6 &gt; 4」会被整段当成标签吃掉
     */
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][^>]*>");

    /** 表格分隔行 {@code |---|:---:|}，整行去掉 */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("(?m)^[ \\t]*\\|[\\s:|-]+\\|[ \\t]*$");

    /** 独占一行的水平线 {@code ---}、{@code ***}、{@code ___} */
    private static final Pattern THEMATIC_BREAK = Pattern.compile("(?m)^[ \\t]*[-*_]{3,}[ \\t]*$");

    /**
     * 行首的块级标记：标题的 {@code #}、引用的 {@code >}、无序列表的 {@code -} {@code +} {@code *}、
     * 有序列表的 {@code 1.}。只按行首位置去掉，词内的连字符与下划线原样留着
     */
    private static final Pattern BLOCK_MARKUP = Pattern.compile(
            "(?m)^[ \\t]*(?:#{1,6}[ \\t]*|>[ \\t]?|[-+*][ \\t]+|\\d+[.、][ \\t]+)");

    /** 强调号 {@code *} 与 {@code **}：清完块级标记后剩下的都属强调或脚注残留，一律去掉 */
    private static final Pattern ASTERISK = Pattern.compile("\\*+");

    /**
     * 下划线强调只认成对的双下划线 {@code __text__}。单下划线不动——
     * 本项目的正文里 {@code updated_at}、{@code post_id} 这类列名出现频率远高于斜体强调，
     * 一律删字符会把技术摘要删成乱码
     */
    private static final Pattern DOUBLE_UNDERSCORE = Pattern.compile("_{2,}");

    /** 删除线只认成对的双波浪线 {@code ~~text~~}，单波浪线（{@code 1~3 天}）不动 */
    private static final Pattern STRIKETHROUGH = Pattern.compile("~{2,}");

    /** 表格单元格分隔符：换成空格而不是删掉，否则「| a | b |」会粘成「ab」 */
    private static final Pattern CELL_DIVIDER = Pattern.compile("\\|");

    /** 连续空白（含换行）折叠为单个空格 */
    private static final Pattern BLANK_RUN = Pattern.compile("[\\s\\u00A0]+");

    private ExcerptUtils() {
    }

    /**
     * 由 Markdown 正文生成摘要：去代码、去图片、链接留锚文本、去 HTML 与 Markdown 标记、
     * 空白折叠，最后按码点截断。省略号只在真的截断时追加，且计入 limit——
     * 返回值长度恒不超过 limit 个码点，列表行与 meta description 拿到的都是同一档宽度
     *
     * @param markdown 正文原文，可为 null
     * @param limit    最大码点数，含尾部省略号；非正数即空串
     * @return 纯文本摘要，永不为 null；正文里没有可读文字时为空串
     */
    public static String generate(String markdown, int limit) {
        if (markdown == null || limit <= 0) {
            return "";
        }
        String text = FENCED_CODE.matcher(markdown).replaceAll("");
        text = UNPAIRED_FENCE.matcher(text).replaceAll("");
        text = INLINE_CODE.matcher(text).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = TABLE_SEPARATOR.matcher(text).replaceAll("");
        text = THEMATIC_BREAK.matcher(text).replaceAll("");
        text = BLOCK_MARKUP.matcher(text).replaceAll("");
        text = ASTERISK.matcher(text).replaceAll("");
        text = DOUBLE_UNDERSCORE.matcher(text).replaceAll("");
        text = STRIKETHROUGH.matcher(text).replaceAll("");
        text = CELL_DIVIDER.matcher(text).replaceAll(" ");
        text = BLANK_RUN.matcher(text).replaceAll(" ").trim();

        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= limit) {
            return text;
        }
        int end = text.offsetByCodePoints(0, limit - 1);
        return text.substring(0, end) + "…";
    }
}
