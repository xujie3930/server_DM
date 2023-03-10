package com.szmsd.bas.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szmsd.bas.domain.BasCarrierKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author YM
 * @since 2022-01-24
 */
@Mapper
public interface BasCarrierKeywordMapper extends BaseMapper<BasCarrierKeyword> {

    Map  selectCarrierKeyword(@Param("map") Map map);
}
