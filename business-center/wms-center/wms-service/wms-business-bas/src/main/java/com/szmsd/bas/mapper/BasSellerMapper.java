package com.szmsd.bas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.szmsd.bas.domain.BasSeller;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szmsd.bas.dto.BasSellerQueryDto;
import com.szmsd.bas.dto.BasSellerSysDto;
import com.szmsd.finance.domain.AccountBalance;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author l
 * @since 2021-03-09
 */
public interface BasSellerMapper extends BaseMapper<BasSeller> {

    List<BasSellerSysDto> selectBasSeller(@Param(Constants.WRAPPER) QueryWrapper<BasSeller> queryWrapper,@Param("reviewState")Boolean reviewState,@Param("pageNum")int pageNum,@Param("pageSize")int pageSize);
    int countBasSeller(@Param(Constants.WRAPPER) QueryWrapper<BasSeller> queryWrapper,@Param("reviewState")Boolean reviewState);


    List<Map>  selectfssAccountBalance(BasSellerQueryDto basSeller);

    List<BasSellerSysDto> selectBasSellers(@Param(Constants.WRAPPER) QueryWrapper<BasSeller> queryWrapper,@Param("reviewState")Boolean reviewState);

    void insertAccountBalance(AccountBalance accountBalance);
}
