package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.Category;
import com.ayun.entity.Tag;
import com.ayun.mapper.CategoryMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.TagMapper;
import com.ayun.model.vo.AdminTagVO;
import com.ayun.model.vo.SiteStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据业务逻辑类（分类与标签）
 */
@Service
public class MetaService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 获取所有文章分类（文章数口径为已发布，公开接口用）
     * @return 分类列表
     */
    public List<Category> getAllCategories() {
        return categoryMapper.findAll();
    }

    /**
     * 获取所有文章分类（文章数口径为全量，含草稿与定时，管理后台用）
     * @return 分类列表
     */
    public List<Category> getAllCategoriesWithCount() {
        return categoryMapper.findAllWithAllCount();
    }

    /**
     * 获取站点统计（前台首页统计卡与分类计数），口径为已发布文章
     * @return 文章总数 / 分类总数 / 最近更新 / 各分类文章数
     */
    public SiteStatsVO getSiteStats() {
        Map<Integer, Integer> categoryCounts = new LinkedHashMap<>();
        for (SiteStatsVO.CategoryCount row : postMapper.countPublishedByCategory()) {
            categoryCounts.put(row.getCategoryId(), row.getCount());
        }
        return SiteStatsVO.builder()
                .articleCount(postMapper.countPublished())
                .categoryCount(categoryMapper.countAll())
                .latestPublishedAt(postMapper.findLatestPublishedAt())
                .categoryCounts(categoryCounts)
                .build();
    }

    /**
     * 获取所有文章标签
     * @return 标签列表
     */
    public List<Tag> getAllTags() {
        return tagMapper.findAll();
    }

    /**
     * 获取所有标签（含关联文章数，管理后台用）
     */
    public List<AdminTagVO> getAllTagsWithCount() {
        return tagMapper.findAllWithCount();
    }

    /**
     * 创建新分类
     * @param category 分类对象
     */
    public void createCategory(Category category) {
        checkCategoryName(category.getName(), null);
        categoryMapper.insert(category);
    }

    /**
     * 更新分类信息
     * @param category 分类对象
     */
    public void updateCategory(Category category) {
        checkCategoryName(category.getName(), category.getId());
        categoryMapper.update(category);
    }

    /**
     * 删除分类。分类下仍有文章（含草稿与定时）时拒绝删除——库表不设外键也不做软删除，
     * 直接删会让文章的 category_id 悬空，而读取侧没有「未分类」这一呈现
     * @param id 分类 ID
     */
    public void deleteCategory(Integer id) {
        int postCount = categoryMapper.countPosts(id);
        if (postCount > 0) {
            throw new BizException("该分类下还有 " + postCount + " 篇文章，请先迁移或删除后再删除分类");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 创建新标签
     * @param tag 标签对象
     */
    public void createTag(Tag tag) {
        checkTagName(tag.getName(), null);
        tagMapper.insert(tag);
    }

    /**
     * 修改标签名称（标签治理）
     * @param id 标签 ID
     * @param name 新名称
     */
    public void updateTag(Integer id, String name) {
        if (tagMapper.findById(id) == null) {
            throw new BizException("标签不存在");
        }
        checkTagName(name, id);
        tagMapper.update(id, name);
    }

    /**
     * 合并标签：源标签的文章关联全部转移到目标标签后删除源标签
     * @param sourceId 被合并的标签 ID
     * @param targetId 合并目标标签 ID
     */
    @Transactional
    public void mergeTags(Integer sourceId, Integer targetId) {
        if (sourceId.equals(targetId)) {
            throw new BizException("不能把标签合并到它自己");
        }
        Tag source = tagMapper.findById(sourceId);
        Tag target = tagMapper.findById(targetId);
        if (source == null || target == null) {
            throw new BizException("标签不存在");
        }
        // 同一篇文章同时挂着两个标签时会违反 post_tags 主键，先移除将冲突的关联
        tagMapper.deleteConflictingPostTags(sourceId, targetId);
        tagMapper.movePostTags(sourceId, targetId);
        tagMapper.deleteById(sourceId);
    }

    /**
     * 删除标签，并联动清理文章关联
     * @param id 标签 ID
     */
    @Transactional
    public void deleteTag(Integer id) {
        tagMapper.deletePostTagsByTagId(id);
        tagMapper.deleteById(id);
    }

    /**
     * 分类名重名校验（更新时允许与自身同名）
     */
    private void checkCategoryName(String name, Integer selfId) {
        if (name == null || name.isBlank()) {
            throw new BizException("分类名称不能为空");
        }
        Category existing = categoryMapper.findByName(name);
        if (existing != null && !existing.getId().equals(selfId)) {
            throw new BizException("已存在同名分类");
        }
    }

    /**
     * 标签名重名校验（更新时允许与自身同名）
     */
    private void checkTagName(String name, Integer selfId) {
        if (name == null || name.isBlank()) {
            throw new BizException("标签名称不能为空");
        }
        Tag existing = tagMapper.findByName(name);
        if (existing != null && !existing.getId().equals(selfId)) {
            throw new BizException("已存在同名标签");
        }
    }
}
