package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.BannedWord;
import com.ayun.mapper.BannedWordMapper;
import com.ayun.model.dto.AdminBannedWordQueryDTO;
import com.ayun.model.vo.BannedWordImportResultVO;
import com.ayun.model.vo.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论违禁词库业务逻辑：后台的增删启停与批量导入，以及评论机审的匹配入口。
 * 词库读多写极少，机审侧走进程内缓存，任何写操作后整体失效（business-model.md §2.9、§4.4 第 2 条）。
 *
 * <p>写方法一律**不加** {@code @Transactional}：每个都只有一条写语句，自身即原子，
 * 而缓存失效必须发生在提交之后——若在事务内失效，另一线程可能在提交前重新加载并把旧词表缓存住，
 * 新词要等到下一次写操作才生效。
 */
@Service
public class BannedWordService {

    /** 词面长度上限，与 `banned_words.word VARCHAR(50)` 一致；超出即拒绝，不静默截断 */
    private static final int MAX_WORD_LENGTH = 50;

    /** 批量导入的拆分符：换行 + 中英文逗号（business-model.md §2.9） */
    private static final String IMPORT_DELIMITERS = "[\\r\\n,，]";

    @Autowired
    private BannedWordMapper bannedWordMapper;

    /** {@code null} = 未加载或已失效；volatile 让失效对机审线程立即可见 */
    private volatile List<CachedWord> enabledCache;

    /**
     * 分页查询词库（后台列表，按词面模糊搜索）
     */
    public PageResult<BannedWord> getWords(AdminBannedWordQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<BannedWord> list = bannedWordMapper.findAdminList(queryDTO.getKeyword());
        PageInfo<BannedWord> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    /**
     * 添加单个词。重名给可读原因而不是数据库异常；`uk_word` 才是去重的硬保证
     */
    public void addWord(String word, String action) {
        String trimmed = normalizeWord(word);
        String resolvedAction = resolveAction(action);
        if (bannedWordMapper.findByWord(trimmed) != null) {
            throw new BizException("该违禁词已存在");
        }
        bannedWordMapper.insert(trimmed, resolvedAction);
        invalidateCache();
    }

    /**
     * 批量导入：拆分 → trim → 去空 → 去重 → `INSERT IGNORE`。
     * `added` 是真正入库数，`skipped` 是「给了但没进去」的总数（文本内重复 + 词库已有 + 超长），
     * 三者合并计数不区分原因——导入方只需要知道有多少没进去（business-model.md §2.9）。
     * 拆分规则由服务端承担，客户端传原始文本即可，避免两侧各写一套拆法。
     */
    public BannedWordImportResultVO batchImport(String text, String action) {
        String resolvedAction = resolveAction(action);
        List<String> tokens = splitTokens(text);

        // 文本内去重按小写键，与 uk_word 在 utf8mb4_0900_ai_ci 下的大小写不敏感保持一致；
        // 超长词在这里挡掉——INSERT IGNORE 会把截断一起吞成警告，不能靠它兜
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String token : tokens) {
            if (token.length() <= MAX_WORD_LENGTH) {
                distinct.putIfAbsent(token.toLowerCase(), token);
            }
        }

        int added = 0;
        if (!distinct.isEmpty()) {
            added = bannedWordMapper.insertIgnoreBatch(new ArrayList<>(distinct.values()), resolvedAction);
            invalidateCache();
        }
        return BannedWordImportResultVO.builder()
                .added(added)
                .skipped(tokens.size() - added)
                .build();
    }

    /**
     * 启用 ⇄ 停用。停用不是删除：词条留着，机审跳过它
     */
    public void toggle(Integer id) {
        BannedWord existing = requireWord(id);
        int next = Integer.valueOf(1).equals(existing.getIsEnabled()) ? 0 : 1;
        bannedWordMapper.updateEnabled(id, next);
        invalidateCache();
    }

    /**
     * 删除词。不回写 `comments.hit_word`——那是命中当刻的值拷贝，词库后来的变动不追溯（db-design.md §2.12）
     */
    public void deleteWord(Integer id) {
        requireWord(id);
        bannedWordMapper.deleteById(id);
        invalidateCache();
    }

    /**
     * 机审匹配（契约见 business-model.md §4.4 第 2、3 条）：只取启用词，正文与词项都转小写后做子串包含判断。
     * 不做分词、不做 AC 自动机、不做正则。`reject` 优先于 `pending`：同时命中两级时按 `reject` 处置。
     *
     * @param content 评论正文
     * @return 命中结果；未命中（或词库为空）返回 {@code null}
     */
    public Match match(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        List<CachedWord> words = enabledWords();
        if (words.isEmpty()) {
            return null;
        }
        String lowerContent = content.toLowerCase();
        String firstPendingWord = null;
        for (CachedWord cached : words) {
            if (!lowerContent.contains(cached.lowerWord())) {
                continue;
            }
            if (BannedWord.ACTION_REJECT.equals(cached.action())) {
                return new Match(BannedWord.ACTION_REJECT, cached.word());
            }
            if (firstPendingWord == null) {
                firstPendingWord = cached.word();
            }
        }
        return firstPendingWord == null ? null : new Match(BannedWord.ACTION_PENDING, firstPendingWord);
    }

    /**
     * 机审命中结果：处置动作 + 命中的词面（即写进 `comments.hit_word` 的那一个）
     */
    public record Match(String action, String word) {
    }

    /** 缓存项：预先算好小写词面，避免每条评论重复 `toLowerCase` */
    private record CachedWord(String word, String lowerWord, String action) {
    }

    private List<CachedWord> enabledWords() {
        List<CachedWord> local = enabledCache;
        if (local == null) {
            // 不加锁：并发首次加载最多重复查一次库，结果等价，比让每条评论在此串行更划算
            local = bannedWordMapper.findEnabled().stream()
                    .map(w -> new CachedWord(w.getWord(), w.getWord().toLowerCase(), w.getAction()))
                    .toList();
            enabledCache = local;
        }
        return local;
    }

    private void invalidateCache() {
        enabledCache = null;
    }

    private BannedWord requireWord(Integer id) {
        BannedWord existing = bannedWordMapper.findById(id);
        if (existing == null) {
            throw new BizException("违禁词不存在");
        }
        return existing;
    }

    /** 词面归一：trim + 非空 + 长度校验。不做大小写折叠，词库按博主录入的原样展示 */
    private String normalizeWord(String word) {
        if (word == null || word.isBlank()) {
            throw new BizException("违禁词不能为空");
        }
        String trimmed = word.trim();
        if (trimmed.length() > MAX_WORD_LENGTH) {
            throw new BizException("违禁词不能超过 " + MAX_WORD_LENGTH + " 个字符");
        }
        return trimmed;
    }

    /** 动作取值收口：留空按默认 `pending`，非法值直接拒，不让它落到 ENUM 上报数据库错 */
    private String resolveAction(String action) {
        if (action == null || action.isBlank()) {
            return BannedWord.ACTION_PENDING;
        }
        String trimmed = action.trim();
        if (!BannedWord.ACTION_PENDING.equals(trimmed) && !BannedWord.ACTION_REJECT.equals(trimmed)) {
            throw new BizException("非法的处置动作");
        }
        return trimmed;
    }

    /** 拆分为非空 token 列表，保留出现顺序；去重与超长过滤交给调用方 */
    private List<String> splitTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : text.split(IMPORT_DELIMITERS)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }
}
