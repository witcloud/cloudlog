package com.ayun.mapper;

import com.ayun.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章分类 Mapper 接口
 */
@Mapper
public interface CategoryMapper {
    /**
     * 查询所有分类（文章数口径为已发布，公开接口用）
     */
    List<Category> findAll();

    /**
     * 查询所有分类（文章数口径为全量，含草稿与定时，管理后台用）
     */
    List<Category> findAllWithAllCount();

    /**
     * 统计分类下的文章数（全量口径，含草稿与定时）
     */
    int countPosts(@Param("categoryId") Integer categoryId);

    /**
     * 统计分类总数
     */
    long countAll();
    
    /**
     * 根据 ID 查询分类
     */
    Category findById(@Param("id") Integer id);

    /**
     * 根据名称查询分类（重名校验）
     */
    Category findByName(@Param("name") String name);
    
    /**
     * 插入新分类
     */
    int insert(Category category);
    
    /**
     * 更新分类信息
     */
    int update(Category category);
    
    /**
     * 根据 ID 删除分类
     */
    int deleteById(@Param("id") Integer id);
}
