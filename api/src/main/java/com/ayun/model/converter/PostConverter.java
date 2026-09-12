package com.ayun.model.converter;

import com.ayun.entity.Post;
import com.ayun.model.dto.PostCreateDTO;
import com.ayun.model.dto.PostUpdateDTO;
import com.ayun.model.vo.PostVO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章对象转换器
 */
@Component
public class PostConverter {

    /**
     * Entity 转换为 VO
     */
    public PostVO toVO(Post post) {
        if (post == null) {
            return null;
        }

        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setExcerpt(post.getExcerpt());
        vo.setContent(post.getContent());
        vo.setCover(post.getCover());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setViews(post.getViewCount());
        vo.setLikes(post.getLikeCount());
        vo.setComments(post.getCommentCount());
        vo.setIsTop(post.getIsTop());

        if (post.getAuthor() != null) {
            vo.setAuthor(PostVO.AuthorVO.builder()
                    .id(post.getAuthor().getId())
                    .username(post.getAuthor().getUsername())
                    .avatar(post.getAuthor().getAvatar())
                    .build());
        }

        if (post.getCategory() != null) {
            vo.setCategory(PostVO.CategoryVO.builder()
                    .id(post.getCategory().getId())
                    .name(post.getCategory().getName())
                    .build());
        }

        if (post.getTags() != null) {
            vo.setTags(post.getTags());
        } else {
            vo.setTags(Collections.emptyList());
        }

        return vo;
    }

    /**
     * Entity 列表转换为 VO 列表
     */
    public List<PostVO> toVOList(List<Post> posts) {
        if (posts == null) {
            return Collections.emptyList();
        }
        return posts.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * CreateDTO 转换为 Entity
     */
    public Post toEntity(PostCreateDTO createDTO) {
        if (createDTO == null) {
            return null;
        }

        Post post = new Post();
        post.setTitle(createDTO.getTitle());
        post.setContent(createDTO.getContent());
        post.setExcerpt(createDTO.getExcerpt());
        post.setCover(createDTO.getCover());
        post.setCategoryId(createDTO.getCategoryId());
        post.setStatus(createDTO.getStatus());
        post.setIsTop(createDTO.getIsTop());

        return post;
    }

    /**
     * UpdateDTO 转换为 Entity
     */
    public void updateEntity(PostUpdateDTO updateDTO, Post post) {
        if (updateDTO == null || post == null) {
            return;
        }

        if (updateDTO.getTitle() != null) post.setTitle(updateDTO.getTitle());
        if (updateDTO.getContent() != null) post.setContent(updateDTO.getContent());
        if (updateDTO.getExcerpt() != null) post.setExcerpt(updateDTO.getExcerpt());
        if (updateDTO.getCover() != null) post.setCover(updateDTO.getCover());
        if (updateDTO.getCategoryId() != null) post.setCategoryId(updateDTO.getCategoryId());
        if (updateDTO.getStatus() != null) post.setStatus(updateDTO.getStatus());
        if (updateDTO.getIsTop() != null) post.setIsTop(updateDTO.getIsTop());
    }
}
