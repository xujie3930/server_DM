package com.szmsd.bas.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szmsd.bas.domain.BasWarehouse;
import com.szmsd.bas.dto.*;
import com.szmsd.bas.vo.BasWarehouseInfoVO;
import com.szmsd.bas.vo.BasWarehouseVO;
import com.szmsd.system.api.domain.SysUser;

import java.util.List;

/**
 * <p>
 * bas_warehouse - 仓库 服务类
 * </p>
 *
 * @author liangchao
 * @since 2021-03-06
 */
public interface IBasWarehouseService extends IService<BasWarehouse> {

    List<BasWarehouseVO> selectList(BasWarehouseQueryDTO queryDTO);

    void saveOrUpdate(AddWarehouseRequest addWarehouseRequest);

    BasWarehouseInfoVO queryInfo(String warehouseCode);

    void saveWarehouseCus(BasWarehouseCusDTO basWarehouseCusDTO);

    void statusChange(BasWarehouseStatusChangeDTO basWarehouseStatusChangeDTO);

    List<WarehouseKvDTO> selectInboundWarehouse();

    List<WarehouseKvDTO> selectCusInboundWarehouse();

    boolean vailCusWarehouse(String warehouseCode);

    boolean vailCusWarehouse(String warehouseCode, SysUser user);

    /**
     * 根据仓库编码查询仓库信息
     *
     * @param warehouseCode warehouseCode
     * @return BasWarehouse
     */
    BasWarehouse queryByWarehouseCode(String warehouseCode);

    /**
     * 根据仓库编码查询仓库信息
     *
     * @param warehouseCodes warehouseCodes
     * @return BasWarehouse
     */
    List<BasWarehouse> queryByWarehouseCodes(List<String> warehouseCodes);
}

