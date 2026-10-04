package com.ayun.common;

/**
 * 站内搜索关键词的「能否交给 ngram 全文索引」判定，PostMapper.xml 的 findAllBrief
 * 靠它决定走 MATCH 还是退回 LIKE。
 *
 * <p>InnoDB 的 ngram 分词器在建索引与解析查询词时都会套用停用词表（innodb_ft_enable_stopword 默认开），
 * 而默认停用词表里带单字母 {@code a}、{@code i}（INFORMATION_SCHEMA.INNODB_FT_DEFAULT_STOPWORD 可查）。
 * 实测（MySQL 8.4，ngram_token_size=2）它们被当成分隔符：含 {@code a}/{@code i} 的 ASCII 连续段会被
 * 从中切断（"Mac" → "M" + "c"，两侧都不足 2 字符，切不出词元），于是 Mac/Java/AI/iOS/nginx 这类词
 * 压根进不了倒排索引，短语查询无论怎么写都必然 0 命中——标题带「Mac」的文章搜「Mac」返回 0 篇即此因，
 * 且无法在查询侧绕开。同理两类词也搜不到：</p>
 *
 * <ul>
 *   <li>恰好是停用词的双字符组合（of/to/de/en/be/by/on/or/as/at/is/it/la）：这个词元被停用词过滤掉；</li>
 *   <li>含 ASCII 符号的（C++、C#、-5）：符号不是词字符，切段后剩单字符，同样切不出词元。</li>
 * </ul>
 *
 * <p>所以只把「不含 ASCII 字母与符号」的关键词（中文等全角字符 + ASCII 数字）交给 MATCH，
 * 其余一律退回 LIKE。LIKE 是子串语义，命中范围恒为 MATCH 的超集，正确性不依赖分词器的任何行为；
 * 博客量级下对 title+content 的 %kw% 扫描成本可接受，换来的是确定性的分岔边界。</p>
 */
public final class FulltextKeywordUtils {

    private FulltextKeywordUtils() {
    }

    /**
     * 判定关键词能否交给 ngram 全文索引。null、空串、单字符一律 false：
     * ngram 词元固定两个字符（ngram_token_size=2），单字的词不在索引里，MATCH 对它必然 0 命中。
     */
    public static boolean isIndexable(String keyword) {
        if (keyword == null || keyword.length() < 2) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            // ASCII 里只有数字是安全的（12、2026 能切出词元），字母与符号都会踩中上面两类坑
            if (c <= 0x7F && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }
}
