package com.ayun.mapper;

import com.ayun.entity.Comment;
import com.ayun.model.vo.AdminCommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章评论 Mapper 接口
 */
@Mapper
public interface CommentMapper {
    /**
     * 根据文章 ID 查询顶层评论（仅已通过）
     */
    List<Comment> findByPostId(@Param("postId") Long postId);

    /**
     * 按一批父评论 ID 查询回复（仅已通过）：一屏根评论的回复取一次就够，
     * 逐条根评论单查会按页容量放大查询数
     */
    List<Comment> findRepliesByParentIds(@Param("parentIds") List<Integer> parentIds);

    /**
     * 根据 ID 查询评论
     */
    Comment findById(@Param("id") Integer id);

    /**
     * 查询某用户最近一条评论的创建时间，用于评论防护的发布间隔校验；无记录时返回 null。
     * 不分状态：待审的评论同样占住间隔，否则刷一条待审就能接着刷下一条
     */
    LocalDateTime findLastCreatedAtByUserId(@Param("userId") Long userId);

    /**
     * 插入新评论
     */
    int insert(Comment comment);

    /**
     * 根据 ID 删除评论
     */
    int deleteById(@Param("id") Integer id);

    /**
     * 管理后台分页多条件查询评论（含待审核/已拒绝，关联文章标题与评论者）
     */
    List<AdminCommentVO> findAdminList(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 查询最新评论（工作台「最近活动」）
     */
    List<AdminCommentVO> findRecent(@Param("limit") Integer limit);

    /**
     * 按审核状态统计评论数
     */
    long countByStatus(@Param("status") String status);

    /**
     * 更新评论审核状态
     */
    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    /**
     * 统计某评论及其直接回复中「已通过」的数量（删除前用于扣减文章计数）
     */
    int countApprovedWithChildren(@Param("id") Integer id);

    /**
     * 删除评论及其直接回复
     */
    int deleteWithChildren(@Param("id") Integer id);

    /**
     * 删除文章的全部评论（删除文章时联动清理）
     */
    int deleteByPostId(@Param("postId") Long postId);
}
