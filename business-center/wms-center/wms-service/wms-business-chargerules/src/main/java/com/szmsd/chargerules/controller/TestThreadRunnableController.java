package com.szmsd.chargerules.controller;

import com.szmsd.chargerules.config.ThreadPoolConfig;
import com.szmsd.chargerules.domain.ChargeLog;
import com.szmsd.chargerules.dto.WarehouseOperationDTO;
import com.szmsd.chargerules.service.IPayService;
import com.szmsd.chargerules.service.IWarehouseOperationService;
import com.szmsd.chargerules.vo.WarehouseOperationVo;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.DateUtils;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.inventory.api.feign.InventoryFeignService;
import com.szmsd.inventory.domain.Inventory;
import com.szmsd.inventory.domain.dto.InventorySkuVolumeQueryDTO;
import com.szmsd.inventory.domain.vo.InventorySkuVolumeVO;
import com.szmsd.inventory.domain.vo.SkuVolumeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Api(tags = {"测试"})
@RestController
@RequestMapping("/test")
public class TestThreadRunnableController {

    @Resource
    private IPayService payService;

    @Resource
    private InventoryFeignService inventoryFeignService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IWarehouseOperationService warehouseOperationService;

    @Resource
    private ThreadPoolConfig threadPoolConfig;

    private Executor asyncTaskExecutor;

    @PostConstruct
    private void init() {
        asyncTaskExecutor = threadPoolConfig.getAsyncExecutor();
    }


    @GetMapping("/run")
    @ApiOperation(value = "测试")
    public void run() {

        log.info("executeWarehouse() start...");
        RLock lock = redissonClient.getLock("executeOperation");

        try {
            if (lock.tryLock()) {
                Map<String, List<Inventory>> warehouseSkuMap = getWarehouseSku();
                for (Map.Entry<String, List<Inventory>> entry : warehouseSkuMap.entrySet()) {
                    asyncTaskExecutor.execute(() -> {
                        String warehouseCode = entry.getKey();
                        List<Inventory> value = entry.getValue();
                        getSkuByWarehouse(warehouseCode, value);
                    });
                }
            }
        } catch (Exception e) {
            log.error("executeWarehouse() execute error: ", e);
        } finally {
            if (lock.isLocked()){
                lock.unlock();
            }
        }
        log.info("executeWarehouse() end...");

    }

    /**
     * 遍历SKU详情
     *
     * @param warehouseCode warehouseCode
     * @param value         List
     */
    private void getSkuByWarehouse(String warehouseCode, List<Inventory> value) {
        for (Inventory inventory : value) {
            List<InventorySkuVolumeVO> skuVolumeVo = getSkuVolume(new InventorySkuVolumeQueryDTO(inventory.getSku(), warehouseCode));
            for (InventorySkuVolumeVO inventorySkuVolumeVO : skuVolumeVo) {
                List<SkuVolumeVO> skuVolumes = inventorySkuVolumeVO.getSkuVolumes();
                getSkuDetails(warehouseCode, skuVolumes);
            }
        }
    }

    /**
     * 获取收费配置
     *
     * @param warehouseCode warehouseCode
     * @param skuVolumes    skuVolumes
     */
    private void getSkuDetails(String warehouseCode, List<SkuVolumeVO> skuVolumes) {
        for (SkuVolumeVO skuVolume : skuVolumes) {
            //LocalDateTime now = LocalDateTime.now();
            WarehouseOperationDTO warehouseOperationDTO = new WarehouseOperationDTO();
            warehouseOperationDTO.setWarehouseCode(warehouseCode);
            warehouseOperationDTO.setEffectiveTime(new Date());
            warehouseOperationDTO.setExpirationTime(new Date());
            warehouseOperationDTO.setCusCodeList(skuVolume.getCusCode());
            List<WarehouseOperationVo> warehouseOperationConfig = warehouseOperationService
                    .selectOperationByRule(warehouseOperationDTO);
              /*  List<WarehouseOperationVo> warehouseOperationConfig = warehouseOperationMapper.listPage(new WarehouseOperationDTO().setWarehouseCode(warehouseCode)
                        .setEffectiveTime(now).setExpirationTime(now).setCusCodeList(skuVolume.getCusCode())
                );*/
            if (CollectionUtils.isEmpty(warehouseOperationConfig)) {
                log.error("getSkuDetails() 未找到收费配置 warehouseCode: {}", warehouseCode);
                continue;
            }
            WarehouseOperationVo warehouseOperationVo = warehouseOperationConfig.get(0);
            BigDecimal amount = charge(warehouseCode, skuVolume, warehouseOperationVo);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                log.error("getSkuDetails() 计算费用为0 仓库：{}, SKU:{}, 数量：{} 体积：{}立方厘米", warehouseCode, skuVolume.getSku(), skuVolume.getQty(), skuVolume.getVolume());
                continue;
            }

            pay(warehouseCode, skuVolume, warehouseOperationVo, amount);
        }
    }

    /**
     * 计算费用
     *
     * @param warehouseCode        目的仓库代码
     * @param skuVolume            SKU体积
     * @param warehouseOperationVo warehouseOperationVo
     * @return amount
     */
    private BigDecimal charge(String warehouseCode, SkuVolumeVO skuVolume, WarehouseOperationVo warehouseOperationVo) {
        String datePoor = DateUtils.getDatePoor(new Date(), DateUtils.parseDate(skuVolume.getOperateOn()));
        int days = Integer.parseInt(datePoor.substring(0, datePoor.indexOf("天")));
        //立方厘米转为立方米
        BigDecimal volume = skuVolume.getVolume().divide(new BigDecimal(1000000), 4, BigDecimal.ROUND_HALF_UP);
        return warehouseOperationService.charge(days, volume, warehouseCode, warehouseOperationVo);
    }

    /**
     * 支付
     *
     * @param warehouseCode        warehouseCode
     * @param skuVolume            skuVolume
     * @param warehouseOperationVo warehouseOperationVo
     * @param amount               amount
     */
    private void pay(String warehouseCode, SkuVolumeVO skuVolume, WarehouseOperationVo warehouseOperationVo, BigDecimal amount) {
        ChargeLog chargeLog = new ChargeLog();
        chargeLog.setWarehouseCode(warehouseCode);
        chargeLog.setCurrencyCode(warehouseOperationVo.getCurrencyCode());
        chargeLog.setOperationType("仓租费");
        chargeLog.setPayMethod(BillEnum.PayMethod.BALANCE_DEDUCTIONS.name());
        CustPayDTO custPayDTO = setCustPayDto(skuVolume, amount, chargeLog);
        payService.pay(custPayDTO, chargeLog);
    }

    /**
     * 获取SKU的详细信息
     *
     * @param dto dto
     * @return list
     */
    private List<InventorySkuVolumeVO> getSkuVolume(InventorySkuVolumeQueryDTO dto) {
        R<List<InventorySkuVolumeVO>> result = inventoryFeignService.querySkuVolume(dto);
        List<InventorySkuVolumeVO> data = result.getData();
        if (result.getCode() == 200 && CollectionUtils.isNotEmpty(data)) {
            return data;
        }
        log.error("getSkuVolume() failed: {}", result.getMsg());
        return new ArrayList<>();
    }

    /**
     * 获取所有的仓库和SKU
     *
     * @return map
     */
    private Map<String, List<Inventory>> getWarehouseSku() {
        //查询所有可用库存大于0 sku
        R<List<Inventory>> result = inventoryFeignService.getWarehouseSku();
        List<Inventory> data = result.getData();
        if (result.getCode() == 200 && CollectionUtils.isNotEmpty(data)) {
            return data.stream().collect(Collectors.groupingBy(Inventory::getWarehouseCode));
        }
        log.error("getWarehouseSku() failed: {}", result.getMsg());
        return new HashMap<>();
    }

    private CustPayDTO setCustPayDto(SkuVolumeVO sku, BigDecimal amount, ChargeLog chargeLog) {
        CustPayDTO custPayDTO = new CustPayDTO();
        List<AccountSerialBillDTO> serialBillInfoList = new ArrayList<>();
        AccountSerialBillDTO accountSerialBillDTO = new AccountSerialBillDTO();
        accountSerialBillDTO.setChargeCategory(BillEnum.CostCategoryEnum.WAREHOUSE_RENTAL.getName());
        accountSerialBillDTO.setChargeType(chargeLog.getOperationType());
        accountSerialBillDTO.setAmount(amount);
        accountSerialBillDTO.setCurrencyCode(chargeLog.getCurrencyCode());
        accountSerialBillDTO.setWarehouseCode(chargeLog.getWarehouseCode());
        accountSerialBillDTO.setNo(sku.getSku());
        serialBillInfoList.add(accountSerialBillDTO);
        custPayDTO.setCusCode(sku.getCusCode());
        custPayDTO.setPayType(BillEnum.PayType.PAYMENT_NO_FREEZE);
        custPayDTO.setPayMethod(BillEnum.PayMethod.WAREHOUSE_RENT);
        custPayDTO.setCurrencyCode(chargeLog.getCurrencyCode());
        custPayDTO.setAmount(amount);
        custPayDTO.setNo(chargeLog.getOrderNo());
        custPayDTO.setSerialBillInfoList(serialBillInfoList);
        custPayDTO.setOrderType(chargeLog.getOperationType());
        return custPayDTO;
    }
}
