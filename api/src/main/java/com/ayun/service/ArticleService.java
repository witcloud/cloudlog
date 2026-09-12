package com.ayun.service;

import com.ayun.entity.Post;
import com.ayun.mapper.PostMapper;
import com.ayun.mapper.TagMapper;
import com.ayun.mapper.UserMapper;
import com.ayun.model.converter.PostConverter;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostQueryDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.PageResult;
import com.ayun.model.vo.PostVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章业务逻辑类
 */
@Service
public class ArticleService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PostConverter postConverter;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_KEY = "article:view_count:";

    /**
     * 分页查询文章列表
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    public PageResult<PostVO> getArticles(PostQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<Post> list = postMapper.findAll(
                queryDTO.getCategoryId(),
                queryDTO.getTagId(),
                queryDTO.getKeyword(),
                queryDTO.getSort()
        );
        PageInfo<Post> pageInfo = new PageInfo<>(list);

        // 为列表中的文章填充标签
        for (Post post : list) {
            post.setTags(tagMapper.findByPostId(post.getId()));
        }

        // 转换为 VO 列表
        List<PostVO> voList = postConverter.toVOList(list);

        return PageResult.of(
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                voList
        );
    }

    /**
     * 根据 ID 获取文章详情
     * @param id 文章 ID
     * @return 文章 VO
     */
    public PostVO getArticleById(Integer id) {
        Post post = postMapper.findById(id);
        if (post != null) {
            post.setTags(tagMapper.findByPostId(id));
            // 在 Redis 中增加阅读量
            redisTemplate.opsForValue().increment(VIEW_COUNT_KEY + id);
            // 实时同步阅读量到数据库
            postMapper.incrementViewCount(id);
        }
        return postConverter.toVO(post);
    }

    /**
     * 创建新文章
     * @param createDTO 文章创建 DTO
     */
    @Transactional
    public void createPost(PostCreateDTO createDTO) {
        Post post = postConverter.toEntity(createDTO);
        
        // 设置作者 ID
         String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
         com.ayun.entity.User user = userMapper.findByEmail(email);
         post.setAuthorId(user.getId());

        if ("published".equals(post.getStatus())) {
            post.setPublishedAt(LocalDateTime.now());
        }
        postMapper.insert(post);
        
        // 处理标签
        if (createDTO.getTagIds() != null) {
            for (Integer tagId : createDTO.getTagIds()) {
                tagMapper.insertPostTag(post.getId(), tagId);
            }
        }
    }

    /**
     * 更新文章信息
     * @param id 文章 ID
     * @param updateDTO 文章更新 DTO
     */
    @Transactional
    public void updatePost(Integer id, PostUpdateDTO updateDTO) {
        Post post = postMapper.findById(id);
        if (post == null) {
            return;
        }

        postConverter.updateEntity(updateDTO, post);
        postMapper.update(post);

        if (updateDTO.getTagIds() != null) {
            tagMapper.deletePostTags(id);
            for (Integer tagId : updateDTO.getTagIds()) {
                tagMapper.insertPostTag(id, tagId);
            }
        }
    }

    /**
     * 删除文章
     * @param id 文章 ID
     */
    public void deletePost(Integer id) {
        postMapper.deleteById(id);
    }

    /**
     * 文章点赞
     * @param id 文章 ID
     */
    public void likePost(Integer id) {
        postMapper.incrementLikeCount(id);
    }
}
