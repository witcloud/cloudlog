package com.ayun.service;

import com.ayun.entity.Comment;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评论业务逻辑类
 */
@Service
public class CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 根据文章 ID 获取所有评论及其回复
     * @param postId 文章 ID
     * @return 评论列表（包含嵌套的回复）
     */
    public List<Comment> getCommentsByPostId(Integer postId) {
        List<Comment> rootComments = commentMapper.findByPostId(postId);
        for (Comment comment : rootComments) {
            comment.setReplies(commentMapper.findRepliesByParentId(comment.getId()));
        }
        return rootComments;
    }

    /**
     * 发表评论，并增加文章的评论计数
     * @param comment 评论对象
     */
    @Transactional
    public void addComment(Comment comment) {
        commentMapper.insert(comment);
        postMapper.incrementCommentCount(comment.getPostId());
    }

    /**
     * 删除评论
     * @param id 评论 ID
     */
    public void deleteComment(Integer id) {
        commentMapper.deleteById(id);
    }
}
