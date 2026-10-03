package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.CommentMapper;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.vo.AdminArticleVO;
import com.ayun.model.vo.AdminCommentVO;
import com.ayun.model.vo.DashboardVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台工作台概览业务逻辑类
 */
@Service
public class AdminDashboardService {

    private static final int RECENT_ARTICLE_COUNT = 5;
    private static final int RECENT_ACTIVITY_COUNT = 8;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    /**
     * 汇总工作台概览数据：统计卡片 + 最近文章 + 最近活动
     */
    public DashboardVO getDashboard() {
        long userCount = userMapper.countAll();
        long articleCount = postMapper.countAll();
        long pendingCommentCount = commentMapper.countByStatus("pending");
        long totalViews = postMapper.sumViewCount();

        // 最近文章：仅取第一页，复用管理端筛选查询（PageHelper 只作用于下一条查询）
        PageHelper.startPage(1, RECENT_ARTICLE_COUNT);
        List<Post> posts = postMapper.findAdminAll(null, null, null, null, null, null);

        List<AdminCommentVO> recentComments = commentMapper.findRecent(RECENT_ACTIVITY_COUNT);

        return DashboardVO.builder()
                .userCount(userCount)
                .articleCount(articleCount)
                .pendingCommentCount(pendingCommentCount)
                .totalViews(totalViews)
                .recentArticles(AdminArticleVO.fromList(posts))
                .recentComments(recentComments)
                .build();
    }
}
