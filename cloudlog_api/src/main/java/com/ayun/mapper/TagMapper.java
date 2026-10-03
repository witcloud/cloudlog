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
     * 管理后台查询所有标签（含关联文章数）
     */
    List<com.ayun.model.vo.AdminTagVO> findAllWithCount();

    /**
     * 根据 ID 查询标签
     */
    Tag findById(@Param("id") Integer id);

    /**
     * 根据名称查询标签
     */
    Tag findByName(@Param("name") String name);

    /**
     * 修改标签名称
     */
    int update(@Param("id") Integer id, @Param("name") String name);

    /**
     * 根据 ID 查询关联的标签
     */
    List<Tag> findByPostId(@Param("postId") Long postId);

    /**
     * 插入新标签
     */
    int insert(Tag tag);

    /**
     * 根据 ID 删除标签
     */
    int deleteById(@Param("id") Integer id);

    /**
     * 删除某标签的全部文章关联
     */
    int deletePostTagsByTagId(@Param("tagId") Integer tagId);

    /**
     * 删除会把主键冲突的关联（文章同时挂着源标签和目标标签时，先移除源标签关联）
     */
    int deleteConflictingPostTags(@Param("sourceId") Integer sourceId, @Param("targetId") Integer targetId);

    /**
     * 标签合并：把源标签的文章关联全部转移到目标标签
     */
    int movePostTags(@Param("sourceId") Integer sourceId, @Param("targetId") Integer targetId);

    /**
     * 建立文章与标签的关联关系
     */
    int insertPostTag(@Param("postId") Long postId, @Param("tagId") Integer tagId);
    
    /**
     * 删除文章关联的所有标签
     */
    int deletePostTags(@Param("postId") Long postId);
}
