package com.szmsd.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szmsd.finance.domain.FssAccountBalanceLogNewEntity;

/**
 * <p>
 * 账户余额表日志新表 服务类
 * </p>
 *
 * @author xujie
 * @since 2023-02-10
 */
public interface FssAccountBalanceLogNewService extends IService<FssAccountBalanceLogNewEntity> {

    void autoSyncBalance();
}
