package com.ayun.service;

import com.ayun.entity.Category;
import com.ayun.entity.Tag;
import com.ayun.mapper.CategoryMapper;
import com.ayun.mapper.TagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 元数据业务逻辑类（分类与标签）
 */
@Service
public class MetaService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private TagMapper tagMapper;

    /**
     * 获取所有文章分类
     * @return 分类列表
     */
    public List<Category> getAllCategories() {
        return categoryMapper.findAll();
    }

    /**
     * 获取所有文章标签
     * @return 标签列表
     */
    public List<Tag> getAllTags() {
        return tagMapper.findAll();
    }

    /**
     * 创建新分类
     * @param category 分类对象
     */
    public void createCategory(Category category) {
        categoryMapper.insert(category);
    }

    /**
     * 更新分类信息
     * @param category 分类对象
     */
    public void updateCategory(Category category) {
        categoryMapper.update(category);
    }

    /**
     * 删除分类
     * @param id 分类 ID
     */
    public void deleteCategory(Integer id) {
        categoryMapper.deleteById(id);
    }

    /**
     * 创建新标签
     * @param tag 标签对象
     */
    public void createTag(Tag tag) {
        tagMapper.insert(tag);
    }

    /**
     * 删除标签
     * @param id 标签 ID
     */
    public void deleteTag(Integer id) {
        tagMapper.deleteById(id);
    }
}
