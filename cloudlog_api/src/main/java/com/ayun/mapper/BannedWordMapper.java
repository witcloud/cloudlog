package com.ayun.mapper;

import com.ayun.entity.BannedWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论违禁词库 Mapper 接口
 */
@Mapper
public interface BannedWordMapper {

    /**
     * 查询全部启用词（机审用）。按 id 升序，使「首个命中词」有稳定定义
     */
    List<BannedWord> findEnabled();

    /**
     * 管理后台分页查询词库，可按词面模糊搜索
     */
    List<BannedWord> findAdminList(@Param("keyword") String keyword);

    /**
     * 按 ID 查询
     */
    BannedWord findById(@Param("id") Integer id);

    /**
     * 按词面查询（唯一键，随库默认 collation 大小写不敏感）
     */
    BannedWord findByWord(@Param("word") String word);

    /**
     * 插入单个词
     */
    int insert(@Param("word") String word, @Param("action") String action);

    /**
     * 批量插入，靠 uk_word 静默跳过重复词；返回真正插入的行数
     */
    int insertIgnoreBatch(@Param("words") List<String> words, @Param("action") String action);

    /**
     * 启用/停用
     */
    int updateEnabled(@Param("id") Integer id, @Param("isEnabled") Integer isEnabled);

    /**
     * 按 ID 删除
     */
    int deleteById(@Param("id") Integer id);
}
