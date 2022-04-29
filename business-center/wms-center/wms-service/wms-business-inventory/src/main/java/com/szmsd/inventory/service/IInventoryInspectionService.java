package com.szmsd.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szmsd.inventory.domain.InventoryInspection;
import com.szmsd.inventory.domain.dto.*;
import com.szmsd.inventory.domain.vo.InventoryInspectionVo;

import java.util.List;

public interface IInventoryInspectionService extends IService<InventoryInspection> {

    boolean add(List<InventoryInspectionDetailsDTO> dto);

    List<InventoryInspectionVo> findList(InventoryInspectionQueryDTO dto);

    InventoryInspectionVo details(String inspectionNo);

    int audit(InventoryInspectionDTO dto);

    boolean inboundInventory(InboundInventoryInspectionDTO dto);
}
