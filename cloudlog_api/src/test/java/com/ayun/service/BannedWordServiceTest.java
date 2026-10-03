package com.ayun.service;

import com.ayun.entity.BannedWord;
import com.ayun.mapper.BannedWordMapper;
import com.ayun.model.vo.BannedWordImportResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 违禁词库单元测试：机审匹配（启用集口径、大小写、reject 优先）、进程内缓存的失效时机、
 * 单条添加的校验，以及批量导入的拆分去重与 added/skipped 计数。
 * 词库 SQL 本身（`WHERE is_enabled = 1`、`uk_word` 去重）属集成测试范围、需外部 MySQL，不在这里覆盖。
 */
@ExtendWith(MockitoExtension.class)
class BannedWordServiceTest {

    @Mock
    private BannedWordMapper bannedWordMapper;

    @InjectMocks
    private BannedWordService bannedWordService;

    private static BannedWord word(int id, String word, String action, int enabled) {
        return BannedWord.builder().id(id).word(word).action(action).isEnabled(enabled).build();
    }

    private static BannedWord enabled(int id, String word, String action) {
        return word(id, word, action, 1);
    }

    /* ---- 机审匹配 ---- */

    @Test
    @DisplayName("机审：命中启用词时返回该动作与词面，子串包含且大小写不敏感")
    void matchHitsEnabledWord() {
        when(bannedWordMapper.findEnabled()).thenReturn(List.of(
                enabled(1, "广告", BannedWord.ACTION_PENDING),
                enabled(2, "SPAM", BannedWord.ACTION_PENDING)));

        assertNull(bannedWordService.match("写得很受用，多谢分享"), "未命中的正文不该有结果");

        BannedWordService.Match chinese = bannedWordService.match("专业广告代投");
        assertEquals(BannedWord.ACTION_PENDING, chinese.action());
        assertEquals("广告", chinese.word());

        assertEquals("SPAM", bannedWordService.match("buy Spam now").word(),
                "匹配两边转小写，但回写 hit_word 的是词库里存的原样");
    }

    @Test
    @DisplayName("机审：只认启用集，停用的词即便出现在正文里也不命中")
    void matchIgnoresDisabledWords() {
        // 「广告」已停用，findEnabled 因此不返回它——匹配只可能命中启用集里的词
        when(bannedWordMapper.findEnabled()).thenReturn(List.of(enabled(2, "代购", BannedWord.ACTION_PENDING)));

        assertNull(bannedWordService.match("最棒的广告平台"), "停用词不参与匹配");
        assertEquals("代购", bannedWordService.match("专业代购").word());
    }

    @Test
    @DisplayName("机审：同时命中两级时按 reject 处置，因为它更严且不可入库")
    void matchPrefersRejectOverPending() {
        when(bannedWordMapper.findEnabled()).thenReturn(List.of(
                enabled(1, "广告", BannedWord.ACTION_PENDING),
                enabled(2, "代开发票", BannedWord.ACTION_REJECT)));

        BannedWordService.Match match = bannedWordService.match("广告位招租，代开发票");

        assertEquals(BannedWord.ACTION_REJECT, match.action());
        assertEquals("代开发票", match.word(), "reject 优先，即使 pending 词在正文里出现得更早");
    }

    @Test
    @DisplayName("机审：正文空白时不命中，也不查库")
    void matchShortCircuitsOnBlankInput() {
        assertNull(bannedWordService.match("  "));
        assertNull(bannedWordService.match(null));
        verify(bannedWordMapper, never()).findEnabled();
    }

    /* ---- 进程内缓存 ---- */

    @Test
    @DisplayName("缓存：连续机审只查一次库，任何写操作后失效并重载")
    void cacheIsReusedUntilAWriteInvalidatesIt() {
        when(bannedWordMapper.findEnabled()).thenReturn(List.of(enabled(1, "广告", BannedWord.ACTION_PENDING)));
        when(bannedWordMapper.findById(1)).thenReturn(enabled(1, "广告", BannedWord.ACTION_PENDING));

        bannedWordService.match("广告一");
        bannedWordService.match("广告二");
        verify(bannedWordMapper, times(1)).findEnabled();

        bannedWordService.addWord("代购", null);
        bannedWordService.match("广告三");
        verify(bannedWordMapper, times(2)).findEnabled();

        bannedWordService.toggle(1);
        bannedWordService.match("广告四");
        verify(bannedWordMapper, times(3)).findEnabled();

        bannedWordService.deleteWord(1);
        bannedWordService.match("广告五");
        verify(bannedWordMapper, times(4)).findEnabled();
    }

    /* ---- 单条添加 ---- */

    @Test
    @DisplayName("添加：词面 trim 后入库，动作留空按默认 pending")
    void addWordTrimsAndDefaultsAction() {
        bannedWordService.addWord("  代购  ", "  ");

        verify(bannedWordMapper).insert("代购", BannedWord.ACTION_PENDING);
    }

    @Test
    @DisplayName("添加：词库已有该词时给可读原因，不落库")
    void addWordRejectsDuplicate() {
        when(bannedWordMapper.findByWord("广告")).thenReturn(enabled(1, "广告", BannedWord.ACTION_PENDING));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> bannedWordService.addWord("  广告 ", BannedWord.ACTION_REJECT));

        assertEquals("该违禁词已存在", error.getMessage());
        verify(bannedWordMapper, never()).insert(anyString(), anyString());
    }

    @Test
    @DisplayName("添加：空词、超长词与非法动作都在触库前被拒")
    void addWordValidatesBeforeTouchingDb() {
        assertEquals("违禁词不能为空",
                assertThrows(RuntimeException.class, () -> bannedWordService.addWord("   ", null)).getMessage());
        assertEquals("违禁词不能超过 50 个字符",
                assertThrows(RuntimeException.class, () -> bannedWordService.addWord("词".repeat(51), null)).getMessage());
        assertEquals("非法的处置动作",
                assertThrows(RuntimeException.class, () -> bannedWordService.addWord("代购", "delete")).getMessage());

        verify(bannedWordMapper, never()).insert(anyString(), anyString());
        verify(bannedWordMapper, never()).findByWord(anyString());
    }

    /* ---- 批量导入 ---- */

    @Test
    @DisplayName("导入：按换行与中英文逗号拆分，去空去重后回准确的 added/skipped")
    void batchImportSplitsDedupesAndCounts() {
        AtomicReference<List<String>> sent = new AtomicReference<>();
        // 词库里已有「代购」，所以四个候选词只进去两个
        when(bannedWordMapper.insertIgnoreBatch(anyList(), anyString())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return 2;
        });

        BannedWordImportResultVO result = bannedWordService.batchImport(
                "广告\n代购，刷单\n\n广告\nAD\nad", BannedWord.ACTION_PENDING);

        // 非空 token 共 6 个：广告/代购/刷单/广告/AD/ad；大小写折叠后剩 4 个候选；入库 2 个
        assertEquals(2, result.getAdded());
        assertEquals(4, result.getSkipped(), "skipped = 给进来的 token 数 - 真正入库数");
        assertEquals(List.of("广告", "代购", "刷单", "AD"), sent.get(),
                "候选词保留首次出现的原样大小写，重复项（含大小写变体）不再送库");
    }

    @Test
    @DisplayName("导入：超过 50 字符的条目计入 skipped，不送库——IGNORE 会把截断一起吞成警告")
    void batchImportSkipsOverlongTokens() {
        AtomicReference<List<String>> sent = new AtomicReference<>();
        when(bannedWordMapper.insertIgnoreBatch(anyList(), anyString())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return 1;
        });

        BannedWordImportResultVO result = bannedWordService.batchImport("广告\n" + "词".repeat(51), null);

        assertEquals(1, result.getAdded());
        assertEquals(1, result.getSkipped());
        assertEquals(List.of("广告"), sent.get());
    }

    @Test
    @DisplayName("导入：全是分隔符的文本不发 SQL（foreach 空集合会拼出非法语句），计数全 0")
    void batchImportWithBlankTextSkipsSql() {
        BannedWordImportResultVO result = bannedWordService.batchImport("  \n，,  ", null);

        assertEquals(0, result.getAdded());
        assertEquals(0, result.getSkipped());
        verify(bannedWordMapper, never()).insertIgnoreBatch(anyList(), anyString());
    }

    @Test
    @DisplayName("导入：省略动作时全部按默认 pending")
    void batchImportDefaultsActionToPending() {
        when(bannedWordMapper.insertIgnoreBatch(anyList(), anyString())).thenReturn(1);

        bannedWordService.batchImport("代购", "  ");

        verify(bannedWordMapper).insertIgnoreBatch(List.of("代购"), BannedWord.ACTION_PENDING);
    }

    @Test
    @DisplayName("导入：非法动作直接拒，不落任何词")
    void batchImportRejectsIllegalAction() {
        assertEquals("非法的处置动作",
                assertThrows(RuntimeException.class, () -> bannedWordService.batchImport("代购", "delete")).getMessage());
        verify(bannedWordMapper, never()).insertIgnoreBatch(anyList(), anyString());
    }

    /* ---- 启停与删除 ---- */

    @Test
    @DisplayName("启停：按当前值翻转，不是写死某个状态")
    void toggleFlipsCurrentState() {
        when(bannedWordMapper.findById(1)).thenReturn(enabled(1, "广告", BannedWord.ACTION_PENDING));
        bannedWordService.toggle(1);
        verify(bannedWordMapper).updateEnabled(1, 0);

        when(bannedWordMapper.findById(2)).thenReturn(word(2, "代购", BannedWord.ACTION_PENDING, 0));
        bannedWordService.toggle(2);
        verify(bannedWordMapper).updateEnabled(2, 1);
    }

    @Test
    @DisplayName("启停与删除：词不存在时给可读原因，不发写语句")
    void toggleAndDeleteRequireExistingWord() {
        when(bannedWordMapper.findById(404)).thenReturn(null);

        assertEquals("违禁词不存在",
                assertThrows(RuntimeException.class, () -> bannedWordService.toggle(404)).getMessage());
        assertEquals("违禁词不存在",
                assertThrows(RuntimeException.class, () -> bannedWordService.deleteWord(404)).getMessage());

        verify(bannedWordMapper, never()).updateEnabled(anyInt(), anyInt());
        verify(bannedWordMapper, never()).deleteById(anyInt());
    }
}
