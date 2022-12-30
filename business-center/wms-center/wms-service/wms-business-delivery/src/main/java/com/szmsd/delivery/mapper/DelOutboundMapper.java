package com.szmsd.delivery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.szmsd.bas.domain.BasSeller;
import com.szmsd.common.datascope.annotation.DataScope;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.vo.DelOutboundDetailListVO;
import com.szmsd.delivery.vo.DelOutboundListExceptionMessageExportVO;
import com.szmsd.delivery.vo.DelOutboundListExceptionMessageVO;
import com.szmsd.delivery.vo.DelOutboundListVO;
import com.szmsd.finance.dto.QueryChargeDto;
import com.szmsd.finance.vo.QueryChargeVO;
import com.szmsd.inventory.domain.dto.QueryFinishListDTO;
import com.szmsd.inventory.domain.vo.QueryFinishListVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 出库单 Mapper 接口
 * </p>
 *
 * @author asd
 * @since 2021-03-05
 */
public interface DelOutboundMapper extends BaseMapper<DelOutbound> {

    /**
     * 出库管理 - 分页
     *
     * @param queryWrapper queryWrapper
     * @return DelOutboundListVO
     */
//    @DataScope("o.seller_code")
    List<DelOutboundListVO> pageList(@Param(Constants.WRAPPER) QueryWrapper<DelOutboundListQueryDto> queryWrapper);

    /**
     * 按条件查询出库单及详情列表
     *
     * @param queryWrapper queryWrapper
     * @return DelOutboundListVO
     */
    List<DelOutboundDetailListVO> getDelOutboundAndDetailsList(@Param(Constants.WRAPPER) QueryWrapper<DelOutboundListQueryDto> queryWrapper);

    /**
     * 按条件查询出库单
     *
     * @param query query
     * @return list
     */
//    @DataScope("t.custom_code")
    List<QueryChargeVO> selectDelOutboundList(QueryChargeDto query);

    /**
     * 出库管理 - 导出分页
     *
     * @param queryWrapper queryWrapper
     * @return List<DelOutboundExportListDto>
     */
    List<DelOutboundExportListDto> exportList(@Param(Constants.WRAPPER) QueryWrapper<DelOutboundListQueryDto> queryWrapper);

    /**
     * 退件单重派导出
     *
     * @param queryWrapper queryWrapper
     * @return List<DelOutboundReassignExportListDto>
     */
    List<DelOutboundReassignExportListDto> reassignExportList(@Param(Constants.WRAPPER) QueryWrapper<DelOutboundListQueryDto> queryWrapper);

    int updateTrackingNo(DelOutboundBatchUpdateTrackingNoEmailDto dto);

    List<DelOutboundListExceptionMessageVO> exceptionMessageList(@Param("orderNos") List<String> orderNos);

    List<DelOutboundListExceptionMessageExportVO> exceptionMessageExportList(@Param("orderNos") List<String> orderNos);

    List<QueryFinishListVO> queryFinishList(QueryFinishListDTO queryFinishListDTO);

    DelOutboundListQueryDto pageLists(@Param("orderNo") String orderNo);

    Map  selectQuerySettings(@Param("shipmentRule") String shipmentRule);

    DelOutbound  selectTrackingNo(@Param("orderNo") String orderNo);

    void  updateFssAccountSerial(DelOutboundBatchUpdateTrackingNoDto delOutboundBatchUpdateTrackingNoDto);

    List<DelOutbound>  selectorderNos(@Param("orderNos") List<String> orderNos);

    List<BasSeller> selectdelsellerCodes();

    List<DelOutbound> selectByState(@Param("state") String state, @Param("pageNumber") int pageNumber,  @Param("pageSize")Integer pageSize);

    int updateThridPartcount(Long id);

    int updateDeloutByOrder(List<DelOutbound> delOutbounds);

    List<DelOutbound> selectOmsWmsLogerror(@Param("createTime") String createTime);

    List<DelOutbound>  selectOmsWmsLogsuccess(@Param("createTime") String createTime);

    void  updateamazonLogisticsRouteId(DelOutboundBatchUpdateTrackingNoDto delOutboundBatchUpdateTrackingNoDto);
}
