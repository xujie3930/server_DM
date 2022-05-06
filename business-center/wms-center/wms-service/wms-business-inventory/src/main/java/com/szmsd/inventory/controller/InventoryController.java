package com.szmsd.inventory.controller;

import com.szmsd.bas.api.client.BasSubClientService;
import com.szmsd.bas.dto.BaseProductEnExportDto;
import com.szmsd.bas.dto.BaseProductExportDto;
import com.szmsd.bas.plugin.vo.BasSubWrapperVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.DateUtils;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.plugin.annotation.AutoValue;
import com.szmsd.inventory.domain.Inventory;
import com.szmsd.inventory.domain.dto.*;
import com.szmsd.inventory.domain.vo.*;
import com.szmsd.inventory.service.IInventoryRecordService;
import com.szmsd.inventory.service.IInventoryService;
import com.szmsd.inventory.service.IInventoryWrapperService;
import com.szmsd.inventory.service.LockerUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Api(tags = {"库存"})
@RestController
@RequestMapping("/inventory")
public class InventoryController extends BaseController {

    @Resource
    private IInventoryService inventoryService;
    @Resource
    private IInventoryRecordService iInventoryRecordService;
    @Autowired
    private IInventoryWrapperService inventoryWrapperService;

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private BasSubClientService basSubClientService;

    @PreAuthorize("@ss.hasPermi('inventory:inbound')")
    @PostMapping("/inbound")
    @ApiOperation(value = "#入库上架", notes = "库存管理 - Inbound - /api/inbound/receiving #B1 接收入库上架 - 修改库存")
    public R inbound(@RequestBody InboundInventoryDTO inboundInventoryDTO) {
        inventoryService.inbound(inboundInventoryDTO);
        return R.ok();
    }

    @PreAuthorize("@ss.hasPermi('inventory:page')")
    @GetMapping("/page")
    @AutoValue
    @ApiOperation(value = "查询", notes = "库存管理 - 分页查询")
    public TableDataInfo<InventorySkuVO> page(InventorySkuQueryDTO inventorySkuQueryDTO) {
        startPage();
        List<InventorySkuVO> list = inventoryService.selectList(inventorySkuQueryDTO);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('inventory:queryFinishList')")
    @PostMapping("/queryFinishList")
    @ApiOperation(value = "查询已完成的单号", notes = "查询已完成的单号")
    public TableDataInfo<QueryFinishListVO> queryFinishList(@RequestBody QueryFinishListDTO queryFinishListDTO) {
        startPage(queryFinishListDTO);
        List<QueryFinishListVO> list = inventoryService.queryFinishList(queryFinishListDTO);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('inventory:page')")
    @GetMapping("/export")
    @ApiOperation(value = "导出", notes = "库存管理 - 导出")
    public TableDataInfo<InventorySkuVO> export(InventorySkuQueryDTO inventorySkuQueryDTO, HttpServletResponse response) {
        String len = getLen();
        List<InventorySkuVO> list = inventoryService.selectList(inventorySkuQueryDTO);

        if("en".equals(len)){

            // 查询产品属性
            java.util.Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("059");
            java.util.Map<String, String> map059 = new HashMap();
            if(listMap.get("059") != null){
                map059 = listMap.get("059").stream()
                        .collect(Collectors.toMap(BasSubWrapperVO::getSubName, BasSubWrapperVO:: getSubNameEn, (v1, v2) -> v1));
            }


            List<InventorySkuEnVO> enList = new ArrayList();
            for (int i = 0; i < list.size();i++) {
                InventorySkuVO vo = list.get(i);
                if(map059.containsKey(vo.getSkuPropertyName())){
                    vo.setSkuPropertyName(map059.get(vo.getSkuPropertyName()));
                }

                InventorySkuEnVO enDto = new InventorySkuEnVO();
                BeanUtils.copyProperties(vo, enDto);
                enList.add(enDto);
                list.set(i, null);
            }
            ExcelUtil<InventorySkuEnVO> util = new ExcelUtil<InventorySkuEnVO>(InventorySkuEnVO.class);
            util.exportExcel(response, enList, "Inventory_Exprot"+ DateUtils.dateTimeNow());
            return getDataTable(list);

        }else{
            ExcelUtil<InventorySkuVO> util = new ExcelUtil<>(InventorySkuVO.class);
            util.exportExcel(response, list, "产品库存_" + DateUtils.dateTimeNow());
            return getDataTable(list);
        }

    }

    @PreAuthorize("@ss.hasPermi('inbound:receipt:page')")
    @GetMapping("/record/page")
    @ApiOperation(value = "日志记录", notes = "库存日志")
    public TableDataInfo<InventoryRecordVO> logsPage(InventoryRecordQueryDTO inventoryRecordQueryDTO) {
        startPage();
        List<InventoryRecordVO> inventoryRecordVOS = iInventoryRecordService.selectList(inventoryRecordQueryDTO);
        return getDataTable(inventoryRecordVOS);
    }

    @PreAuthorize("@ss.hasPermi('inbound:skuvolume')")
    @PostMapping("/skuVolume")
    @ApiOperation(value = "获取库存SKU体积", notes = "获取库存SKU体积")
    public R<List<InventorySkuVolumeVO>> querySkuVolume(@RequestBody InventorySkuVolumeQueryDTO inventorySkuVolumeQueryDTO) {
        startPage();
        List<InventorySkuVolumeVO> inventorySkuVolumeVOS = iInventoryRecordService.selectSkuVolume(inventorySkuVolumeQueryDTO);
        return R.ok(inventorySkuVolumeVOS);
    }

    @PreAuthorize("@ss.hasPermi('inbound:queryAvailableList')")
    @PostMapping("/queryAvailableList")
    @ApiOperation(value = "根据仓库编码，SKU查询可用库存 - 分页")
    public TableDataInfo<InventoryAvailableListVO> queryAvailableList(@RequestBody InventoryAvailableQueryDto queryDto) {
        startPage(queryDto);
        return getDataTable(this.inventoryService.queryAvailableList(queryDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:queryAvailableList2')")
    @PostMapping("/queryAvailableList2")
    @ApiOperation(value = "根据仓库编码，SKU查询可用库存 - 不分页")
    public R<List<InventoryAvailableListVO>> queryAvailableList2(@RequestBody InventoryAvailableQueryDto queryDto) {
        return R.ok(this.inventoryService.queryAvailableList(queryDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:queryOnlyAvailable')")
    @PostMapping("/queryOnlyAvailable")
    @ApiOperation(value = "根据仓库编码，SKU查询可用库存 - 单条")
    public R<InventoryAvailableListVO> queryOnlyAvailable(@RequestBody InventoryAvailableQueryDto queryDto) {
        return R.ok(this.inventoryService.queryOnlyAvailable(queryDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:querySku')")
    @PostMapping("/querySku")
    @ApiOperation(value = "查询SKU信息")
    public R<List<InventoryVO>> querySku(@RequestBody InventoryAvailableQueryDto queryDto) {
        return R.ok(this.inventoryService.querySku(queryDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:queryOnlySku')")
    @PostMapping("/queryOnlySku")
    @ApiOperation(value = "查询SKU信息")
    public R<InventoryVO> queryOnlySku(@RequestBody InventoryAvailableQueryDto queryDto) {
        return R.ok(this.inventoryService.queryOnlySku(queryDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:freeze')")
    @PostMapping("/freeze")
    @ApiOperation(value = "库存管理 - 冻结库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> freeze(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.freeze(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:unFreeze')")
    @PostMapping("/unFreeze")
    @ApiOperation(value = "库存管理 - 取消冻结库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> unFreeze(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.unFreeze(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:unFreezeAndFreeze')")
    @PostMapping("/unFreezeAndFreeze")
    @ApiOperation(value = "库存管理 - 重置冻结库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> unFreezeAndFreeze(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.unFreezeAndFreeze(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:deduction')")
    @PostMapping("/deduction")
    @ApiOperation(value = "库存管理 - 扣减库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> deduction(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.deduction(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:unDeduction')")
    @PostMapping("/unDeduction")
    @ApiOperation(value = "库存管理 - 取消扣减库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> unDeduction(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.unDeduction(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:unDeductionAndDeduction')")
    @PostMapping("/unDeductionAndDeduction")
    @ApiOperation(value = "库存管理 - 重置扣减库存")
    @ApiImplicitParam(name = "operateListDto", value = "参数", dataType = "InventoryOperateListDto")
    public R<Integer> unDeductionAndDeduction(@RequestBody InventoryOperateListDto operateListDto) {
        return R.ok(this.inventoryWrapperService.unDeductionAndDeduction(operateListDto));
    }

    @PreAuthorize("@ss.hasPermi('inbound:adjustment')")
    @PostMapping("/adjustment")
    @ApiOperation(value = "库存管理 - 调整")
    public R adjustment(@RequestBody InventoryAdjustmentDTO inventoryAdjustmentDTO) {
        inventoryService.adjustment(inventoryAdjustmentDTO);
        return R.ok();
    }

    @PreAuthorize("@ss.hasPermi('inventory:getWarehouseSku')")
    @GetMapping("/getWarehouseSku")
    @ApiOperation(value = "查询仓库和SKU", notes = "库存管理 - 查询仓库和SKU")
    public R<List<Inventory>> getWarehouseSku() {
        return R.ok(inventoryService.getWarehouseSku());
    }

    @PreAuthorize("@ss.hasPermi('inventory:getWarehouseSku')")
    @GetMapping("/queryInventoryAge/weeks/bySku/{warehouseCode}/{sku}")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "warehouseCode", value = "仓库code", example = "NJ"),
            @ApiImplicitParam(name = "sku", value = "sku", example = "CN20210601006"),
    })
    @ApiOperation(value = "查询sku的库龄", notes = "查询sku的库龄-周")
    public R<List<SkuInventoryAgeVo>> queryInventoryAgeBySku(@PathVariable("warehouseCode") String warehouseCode,@PathVariable("sku") String sku) {
        return R.ok(inventoryService.queryInventoryAgeBySku(warehouseCode,sku));
    }

    @PostMapping("/testLock")
    public R<?> testLock(@RequestBody InventoryAdjustmentDTO inventoryAdjustmentDTO) {
        String key = "test:lock:" + inventoryAdjustmentDTO.getSku();
        new LockerUtil<Integer>(redissonClient).doExecute(key, () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(inventoryAdjustmentDTO.getQuantity());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            return null;
        });
        return R.ok();
    }

}
