package com.ayun.mapper;

import com.ayun.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章评论 Mapper 接口
 */
@Mapper
public interface CommentMapper {
    /**
     * 根据文章 ID 查询顶层评论
     */
    List<Comment> findByPostId(@Param("postId") Integer postId);
    
    /**
     * 根据父评论 ID 查询回复
     */
    List<Comment> findRepliesByParentId(@Param("parentId") Integer parentId);
    
    /**
     * 插入新评论
     */
    int insert(Comment comment);
    
    /**
     * 根据 ID 删除评论
     */
    int deleteById(@Param("id") Integer id);
}
