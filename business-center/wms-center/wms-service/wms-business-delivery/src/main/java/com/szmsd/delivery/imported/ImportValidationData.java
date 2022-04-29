package com.szmsd.delivery.imported;

import com.szmsd.inventory.domain.vo.InventoryAvailableListVO;

/**
 * @author zhangyuyuan
 * @date 2021-04-09 21:31
 */
public interface ImportValidationData {

    boolean exists(String warehouseCode, String sku);

    boolean sub(String warehouseCode, String sku, int qty);

    InventoryAvailableListVO get(String warehouseCode, String sku);

    /**
     * 重置数量
     *
     * @param warehouseCode warehouseCode
     * @param sku           sku
     * @param qty           qty
     */
    void resetQty(String warehouseCode, String sku, int qty);
}
