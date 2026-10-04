package com.ayun.mapper;

import com.ayun.entity.Post;
import com.ayun.model.vo.PostVO;
import com.ayun.model.vo.SiteStatsVO;
import com.ayun.model.vo.StatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章内容 Mapper 接口
 */
@Mapper
public interface PostMapper {
    /**
     * 分页多条件查询文章列表（首页、搜索、分类/标签页与侧栏热榜共用）
     */
    List<Post> findAllBrief(@Param("authorId") Long authorId,
                            @Param("categoryId") Integer categoryId,
                            @Param("tagId") Integer tagId,
                            @Param("keyword") String keyword,
                            @Param("sort") String sort);

    /**
     * 管理后台分页多条件查询全站文章（含草稿/定时发布）
     */
    List<Post> findAdminAll(@Param("status") String status,
                            @Param("authorId") Long authorId,
                            @Param("categoryId") Integer categoryId,
                            @Param("keyword") String keyword,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate);

    /**
     * 查询发表过文章的作者列表（管理后台筛选用）
     */
    List<PostVO.AuthorVO> findAuthors();

    /**
     * 统计全站文章总数
     */
    long countAll();

    /**
     * 统计全站已发布文章数（前台口径，不含草稿与定时发布）
     */
    long countPublished();

    /**
     * 全站最近发布时间，无已发布文章时为 null
     */
    LocalDateTime findLatestPublishedAt();

    /**
     * 按分类统计已发布文章数（未分类的文章不计入）
     */
    List<SiteStatsVO.CategoryCount> countPublishedByCategory();

    /**
     * 统计全站累计阅读量
     */
    long sumViewCount();

    /**
     * 作者维度的已发布文章聚合（篇数 / 获赞总数 / 阅读总量），只填 articleCount、likeCount、viewCount 三格
     */
    PostVO.AuthorVO findAuthorStats(@Param("authorId") Long authorId);

    /**
     * 查询当前用户的未发布文章（草稿与定时发布）；status 非空时只取其中一种
     */
    List<Post> findDrafts(@Param("authorId") Long authorId,
                          @Param("keyword") String keyword,
                          @Param("year") Integer year,
                          @Param("month") Integer month,
                          @Param("status") String status);

    /**
     * 未发布文章的分状态计数：与 findDrafts 共用 draftScope 谓词段，但不叠加状态
     */
    StatusCountVO countUnpublishedByStatus(@Param("authorId") Long authorId,
                                           @Param("keyword") String keyword,
                                           @Param("year") Integer year,
                                           @Param("month") Integer month);

    /**
     * 查询当前用户已发布的文章
     */
    List<Post> findMine(@Param("authorId") Long authorId,
                        @Param("keyword") String keyword,
                        @Param("year") Integer year,
                        @Param("month") Integer month);

    /**
     * 按属主查询未发布文章（草稿与定时发布）：id + author_id + 状态三条件都在 SQL 谓词里，
     * 供草稿箱删除在进入级联前确认属主
     */
    Post findOwnedDraft(@Param("id") Long id, @Param("authorId") Long authorId);

    /**
     * 当前用户的全部未发布文章 ID（草稿与定时发布），清空草稿箱时逐条走级联删除
     */
    List<Long> findDraftIdsByAuthor(@Param("authorId") Long authorId);

    /**
     * 只取封面与正文两列，供删除级联算出这篇文章引用了哪些对象（business-model.md §3.3 图片对象回收）。
     * 不复用 findById：级联三条路径都只要这两列，详情那条查询还带作者与分类两个 JOIN
     */
    Post findCoverAndContent(@Param("id") Long id);

    /**
     * 清空文章的发布时间（scheduled 撤下为 draft 时恢复「从未发布」语义）
     */
    int clearPublishedAt(@Param("id") Long id);

    /**
     * 根据 ID 查询文章详情
     */
    Post findById(@Param("id") Long id);
    
    /**
     * 插入新文章（id 由 Service 层以雪花 ID 显式赋值后写入）
     */
    int insert(Post post);
    
    /**
     * 更新文章信息
     */
    int update(Post post);
    
    /**
     * 根据 ID 删除文章
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 增加文章阅读量
     */
    int incrementViewCount(@Param("id") Long id);
    
    /**
     * 增加文章评论数
     */
    int incrementCommentCount(@Param("id") Long id);

    /**
     * 扣减文章评论数（不允许减为负数）
     */
    int decrementCommentCountBy(@Param("id") Long id, @Param("count") int count);

    /**
     * 某用户点赞过的已发布文章，按点赞时刻倒序
     */
    List<Post> findLikedByUser(@Param("userId") Long userId);

    /**
     * 对外可见（published / scheduled）但摘要为空的文章，供启动期回填逐篇从正文派生。
     * 只取 id 与 content 两列——回填只读正文、只写摘要
     */
    List<Post> findNeedingExcerpt();

    /**
     * 只写 excerpt 一列。回填不能顺带改写 updated_at，否则存量文章会在列表排序与 sitemap 的
     * lastmod 上集体变成「刚刚编辑过」；updated_at 自赋值用于压住列上的 ON UPDATE CURRENT_TIMESTAMP
     */
    int updateExcerpt(@Param("id") Long id, @Param("excerpt") String excerpt);

    /**
     * sitemap 素材：全部已发布文章的 id 与 updated_at，按 id 排序。
     * 排序固定是为了同一批数据每次生成的 <url> 顺序一致，爬虫比对差异时不会看到整文件重排
     */
    List<Post> findAllPublishedForSitemap();

    /**
     * RSS 素材：最新 limit 篇已发布文章的标题、摘要与两个时刻列，按发布时间倒序。
     * 带上 created_at 是因为存量手工发布的行可能没有 published_at（db-design.md §5.3），
     * 订阅项的 pubDate 需要一个一定存在的时刻
     */
    List<Post> findLatestPublishedForFeed(@Param("limit") int limit);
}
