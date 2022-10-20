package com.szmsd.chargerules.service;

import com.szmsd.chargerules.domain.BasProductService;
import com.szmsd.chargerules.domain.ChargeLog;
import com.szmsd.chargerules.dto.ChargeLogDto;
import com.szmsd.common.core.domain.R;
import com.szmsd.finance.dto.QueryChargeDto;
import com.szmsd.finance.vo.QueryChargeVO;

import java.util.List;

public interface IChargeLogService {

    /**
     * 保存日志
     * @param chargeLog chargeLog
     * @return result
     */
    int save(ChargeLog chargeLog);

    /**
     * 根据条件查询日志
     * @param chargeLogDto chargeLogDto
     * @return ChargeLog
     */
    ChargeLog selectLog(ChargeLogDto chargeLogDto);

    /**
     * 查询日志
     * @param chargeLogDto chargeLogDto
     * @return List
     */
    List<ChargeLog> selectPage(ChargeLogDto chargeLogDto);

    List<QueryChargeVO> selectChargeLogList(QueryChargeDto queryDto);

    /**
     * 修改
     * @param id id
     */
    int update(Long id);

    R selectBasProductService(BasProductService basProductService);
}
