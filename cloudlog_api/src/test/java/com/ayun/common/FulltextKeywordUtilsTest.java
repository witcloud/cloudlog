package com.ayun.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 搜索关键词的 MATCH/LIKE 分岔判定。这组用例就是 ngram 停用词坑的清单：
 * 每个判 false 的样例都对应一类实测 0 命中的关键词（MySQL 8.4，ngram_token_size=2），
 * 机制说明见 {@link FulltextKeywordUtils} 的类注释
 */
class FulltextKeywordUtilsTest {

    @Test
    @DisplayName("中文与数字关键词交给全文索引")
    void cjkAndDigitsAreIndexable() {
        assertTrue(FulltextKeywordUtils.isIndexable("开发"));
        assertTrue(FulltextKeywordUtils.isIndexable("我的"));
        assertTrue(FulltextKeywordUtils.isIndexable("环境配置"));
        assertTrue(FulltextKeywordUtils.isIndexable("，环境"));
        assertTrue(FulltextKeywordUtils.isIndexable("2026"));
        assertTrue(FulltextKeywordUtils.isIndexable("开发2026"));
    }

    @Test
    @DisplayName("含 ASCII 字母的关键词退回 LIKE：a/i 是 ngram 分词的分隔符，Mac/Java 这类词进不了索引")
    void asciiLettersFallBackToLike() {
        assertFalse(FulltextKeywordUtils.isIndexable("Mac"));
        assertFalse(FulltextKeywordUtils.isIndexable("mac"));
        assertFalse(FulltextKeywordUtils.isIndexable("Java"));
        assertFalse(FulltextKeywordUtils.isIndexable("AI"));
        assertFalse(FulltextKeywordUtils.isIndexable("iOS"));
        assertFalse(FulltextKeywordUtils.isIndexable("nginx"));
        assertFalse(FulltextKeywordUtils.isIndexable("k8s"));
        // 双字符停用词本身不进索引，当关键词搜也是 0 命中
        assertFalse(FulltextKeywordUtils.isIndexable("of"));
        assertFalse(FulltextKeywordUtils.isIndexable("to"));
        assertFalse(FulltextKeywordUtils.isIndexable("de"));
    }

    @Test
    @DisplayName("含 ASCII 符号的关键词退回 LIKE：符号两侧切段后剩单字符，切不出词元")
    void asciiSymbolsFallBackToLike() {
        assertFalse(FulltextKeywordUtils.isIndexable("C++"));
        assertFalse(FulltextKeywordUtils.isIndexable("C#"));
        assertFalse(FulltextKeywordUtils.isIndexable("-5"));
        assertFalse(FulltextKeywordUtils.isIndexable("100%"));
        assertFalse(FulltextKeywordUtils.isIndexable("a_b"));
    }

    @Test
    @DisplayName("单字、空串与 null 一律退回 LIKE：ngram 词元是两个字符，单字不在索引里")
    void shortKeywordsFallBackToLike() {
        assertFalse(FulltextKeywordUtils.isIndexable(null));
        assertFalse(FulltextKeywordUtils.isIndexable(""));
        assertFalse(FulltextKeywordUtils.isIndexable("M"));
        assertFalse(FulltextKeywordUtils.isIndexable("环"));
    }
}
