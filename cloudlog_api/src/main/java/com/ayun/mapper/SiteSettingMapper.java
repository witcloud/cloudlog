package com.ayun.mapper;

import com.ayun.entity.SiteSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站点配置 Mapper 接口
 */
@Mapper
public interface SiteSettingMapper {

    /**
     * 根据配置键查询配置
     */
    SiteSetting findByKey(@Param("key") String key);

    /**
     * 写入配置（存在则更新）
     */
    int upsert(@Param("key") String key, @Param("value") String value);
}
