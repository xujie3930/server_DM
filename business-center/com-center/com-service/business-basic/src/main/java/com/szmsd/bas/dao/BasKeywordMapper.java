package com.szmsd.bas.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szmsd.bas.api.domain.BasKeyword;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author ziling
 * @since 2020-06-13
 */
@Mapper
public interface BasKeywordMapper extends BaseMapper<BasKeyword> {

    int deleteBydestination(String siteCode);
}
