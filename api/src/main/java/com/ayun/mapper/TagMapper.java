package com.ayun.mapper;

import com.ayun.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章标签 Mapper 接口
 */
@Mapper
public interface TagMapper {
    /**
     * 查询所有标签
     */
    List<Tag> findAll();
    
    /**
     * 根据文章 ID 查询关联的标签
     */
    List<Tag> findByPostId(@Param("postId") Integer postId);
    
    /**
     * 插入新标签
     */
    int insert(Tag tag);
    
    /**
     * 根据 ID 删除标签
     */
    int deleteById(@Param("id") Integer id);
    
    /**
     * 建立文章与标签的关联关系
     */
    int insertPostTag(@Param("postId") Integer postId, @Param("tagId") Integer tagId);
    
    /**
     * 删除文章关联的所有标签
     */
    int deletePostTags(@Param("postId") Integer postId);
}
