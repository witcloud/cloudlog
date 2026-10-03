package com.ayun.service;

import com.ayun.common.BizException;
import com.ayun.entity.Comment;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.model.dto.AdminCommentQueryDTO;
import com.ayun.model.vo.AdminCommentVO;
import com.ayun.model.vo.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台评论审核业务逻辑类
 */
@Service
public class AdminCommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 分页多条件查询全站评论
     */
    public PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<AdminCommentVO> list = commentMapper.findAdminList(queryDTO.getStatus(), queryDTO.getKeyword());
        PageInfo<AdminCommentVO> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize(), list);
    }

    /**
     * 各审核状态的评论数（筛选页签角标）
     */
    public Map<String, Long> getStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pending", commentMapper.countByStatus("pending"));
        counts.put("approved", commentMapper.countByStatus("approved"));
        counts.put("rejected", commentMapper.countByStatus("rejected"));
        return counts;
    }

    /**
     * 审核评论（通过/拒绝/退回待审），并保持文章评论数与「已通过」评论数一致
     */
    @Transactional
    public void updateStatus(Integer id, String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new BizException("非法的评论状态");
        }
        Comment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new BizException("评论不存在");
        }
        String oldStatus = comment.getStatus();
        commentMapper.updateStatus(id, status);

        if (!"approved".equals(oldStatus) && "approved".equals(status)) {
            postMapper.incrementCommentCount(comment.getPostId());
        } else if ("approved".equals(oldStatus) && !"approved".equals(status)) {
            postMapper.decrementCommentCountBy(comment.getPostId(), 1);
        }
    }

    /**
     * 删除评论及其直接回复，已通过的按数量扣减文章评论数
     */
    @Transactional
    public void deleteComment(Integer id) {
        Comment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new BizException("评论不存在");
        }
        int approvedCount = commentMapper.countApprovedWithChildren(id);
        commentMapper.deleteWithChildren(id);
        if (approvedCount > 0) {
            postMapper.decrementCommentCountBy(comment.getPostId(), approvedCount);
        }
    }
}
