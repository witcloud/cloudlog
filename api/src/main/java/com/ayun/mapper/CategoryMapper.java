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
     * 查询所有分类
     */
    List<Category> findAll();
    
    /**
     * 根据 ID 查询分类
     */
    Category findById(@Param("id") Integer id);
    
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
