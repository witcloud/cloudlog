package com.ayun.mapper;

import com.ayun.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章内容 Mapper 接口
 */
@Mapper
public interface PostMapper {
    /**
     * 分页多条件查询文章列表
     */
    List<Post> findAll(@Param("categoryId") Integer categoryId, 
                       @Param("tagId") Integer tagId, 
                       @Param("keyword") String keyword, 
                       @Param("sort") String sort);
    
    /**
     * 根据 ID 查询文章详情
     */
    Post findById(@Param("id") Integer id);
    
    /**
     * 插入新文章
     */
    int insert(Post post);
    
    /**
     * 更新文章信息
     */
    int update(Post post);
    
    /**
     * 根据 ID 删除文章
     */
    int deleteById(@Param("id") Integer id);
    
    /**
     * 增加文章阅读量
     */
    int incrementViewCount(@Param("id") Integer id);
    
    /**
     * 增加文章点赞数
     */
    int incrementLikeCount(@Param("id") Integer id);
    
    /**
     * 增加文章评论数
     */
    int incrementCommentCount(@Param("id") Integer id);
}
