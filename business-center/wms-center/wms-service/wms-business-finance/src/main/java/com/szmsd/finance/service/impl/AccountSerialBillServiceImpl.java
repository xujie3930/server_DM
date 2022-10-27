package com.szmsd.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.dto.DelOutboundListQueryDto;
import com.szmsd.delivery.vo.DelOutboundListVO;
import com.szmsd.finance.domain.AccountSerialBill;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.mapper.AccountBillRecordMapper;
import com.szmsd.finance.mapper.AccountSerialBillMapper;
import com.szmsd.finance.service.IAccountSerialBillService;
import com.szmsd.finance.service.ISysDictDataService;
import com.szmsd.finance.vo.*;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import com.szmsd.putinstorage.domain.dto.InboundReceiptQueryDTO;
import com.szmsd.putinstorage.domain.vo.InboundReceiptVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountSerialBillServiceImpl extends ServiceImpl<AccountSerialBillMapper, AccountSerialBill> implements IAccountSerialBillService {

    @Resource
    private AccountSerialBillMapper accountSerialBillMapper;

    @Resource
    private ISysDictDataService sysDictDataService;

    @Resource
    private AccountBillRecordMapper accountBillRecordMapper;

    @Override
//    @DataScope("cus_code")
    public List<AccountSerialBill> listPage(AccountSerialBillDTO dto) {
//        QueryWrapper<Object> query1 = Wrappers.query();
//        query1.eq("a.cretea",);
//        LambdaQueryWrapper<AccountSerialBill> query = Wrappers.lambdaQuery();
//        query.in(CollectionUtils.isNotEmpty(dto.getNoList()), AccountSerialBill::getNo, dto.getNoList());
//        query.in(CollectionUtils.isNotEmpty(dto.getCusCodeList()), AccountSerialBill::getCusCode, dto.getCusCodeList());
//        query.in(CollectionUtils.isNotEmpty(dto.getProductCodeList()), AccountSerialBill::getProductCode, dto.getProductCodeList());
//
//
//        if (StringUtils.isNotBlank(dto.getChargeType())) {
//            query.eq(AccountSerialBill::getChargeType, dto.getChargeType());
//        }
//        if (StringUtils.isNotBlank(dto.getWarehouseCode())) {
//            query.eq(AccountSerialBill::getWarehouseCode, dto.getWarehouseCode());
//        }
//        if (StringUtils.isNotBlank(dto.getCurrencyCode())) {
//            query.eq(AccountSerialBill::getCurrencyCode, dto.getCurrencyCode());
//        }
//        if (StringUtils.isNotBlank(dto.getBusinessCategory())) {
//            query.eq(AccountSerialBill::getBusinessCategory, dto.getBusinessCategory());
//        }
//        if (StringUtils.isNotBlank(dto.getProductCategory())) {
//            query.eq(AccountSerialBill::getProductCategory, dto.getProductCategory());
//        }
//        if (StringUtils.isNotBlank(dto.getChargeCategory())) {
//            query.eq(AccountSerialBill::getChargeCategory, dto.getChargeCategory());
//        }
//        if (StringUtils.isNotBlank(dto.getCreateTimeStart())) {
//            query.ge(AccountSerialBill::getCreateTime, dto.getCreateTimeStart());
//        }
//        if (StringUtils.isNotBlank(dto.getCreateTimeEnd())) {
//            query.le(AccountSerialBill::getCreateTime, dto.getCreateTimeEnd());
//        }
//        if (StringUtils.isNotBlank(dto.getIds())) {
//            query.in(AccountSerialBill::getId, (Object[]) dto.getIds().split(","));
//        }
        if (Objects.nonNull(SecurityUtils.getLoginUser())) {
            String cusCode = StringUtils.isNotEmpty(SecurityUtils.getLoginUser().getSellerCode()) ? SecurityUtils.getLoginUser().getSellerCode() : "";
            if (com.szmsd.common.core.utils.StringUtils.isEmpty(dto.getCusCode())) {
                dto.setCusCode(cusCode);
            }
        }
        List<AccountSerialBill> accountSerialBills = accountSerialBillMapper.selectPageList(dto);
        // 修改下单时间等信息
        showProcess(accountSerialBills);
        return accountSerialBills;
    }

    @Resource
    private InboundReceiptFeignService inboundReceiptFeignService;
    @Resource
    private DelOutboundFeignService delOutboundFeignService;

    @Data
    @AllArgsConstructor
    private static class ListProcess {
        private Date orderTime;
        private String no;
        private String warehouseCode;

    }

    /**
     * 账单展示仓库 下单时间
     *
     * @param accountSerialBills
     */
    public void showProcess(List<AccountSerialBill> accountSerialBills) {
        ArrayList<AccountSerialBill> resultList = new ArrayList<>();
        List<AccountSerialBill> noList = accountSerialBills.stream().filter(x -> StringUtils.isNotBlank(x.getNo())).collect(Collectors.toList());
        // 查询下单时间 结算时间，列表时间展示
        CompletableFuture<List<ListProcess>> listCompletableFuture = CompletableFuture.supplyAsync(() -> {
            List<AccountSerialBill> rk = noList.parallelStream().filter(x -> x.getNo().startsWith("RK")).distinct().collect(Collectors.toList());
            String rkNo = rk.stream().map(AccountSerialBill::getNo).distinct().collect(Collectors.joining(","));

            List<ListProcess> result = new ArrayList<>();
            if (StringUtils.isNotBlank(rkNo)) {
                InboundReceiptQueryDTO inboundReceiptQueryDTO = new InboundReceiptQueryDTO();
                inboundReceiptQueryDTO.setWarehouseNo(rkNo);
                TableDataInfo<InboundReceiptVO> inboundReceiptVOTableDataInfo = inboundReceiptFeignService.postPage(inboundReceiptQueryDTO);
                if (inboundReceiptVOTableDataInfo.getCode() == HttpStatus.SUCCESS) {
                    List<InboundReceiptVO> ckPage = inboundReceiptVOTableDataInfo.getRows();
                    Map<String, InboundReceiptVO> collect = ckPage.stream().collect(Collectors.toMap(InboundReceiptVO::getWarehouseNo, x -> x, (x1, x2) -> x1));
                    result = rk.stream().map(x -> {
                        InboundReceiptVO inboundReceiptVO = collect.get(x.getNo());
                        if (Objects.nonNull(inboundReceiptVO)) {
                            Date createTime = inboundReceiptVO.getCreateTime();
                            String warehouseCode = inboundReceiptVO.getWarehouseCode();
                            return new ListProcess(createTime, x.getNo(), warehouseCode);
                        }
                        return null;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
                }
            }
            return result;
        });


        CompletableFuture<List<ListProcess>> listCompletableFuture1 = CompletableFuture.supplyAsync(() -> {
            List<AccountSerialBill> ck = noList.parallelStream().filter(x -> x.getNo().startsWith("CK")).distinct().collect(Collectors.toList());
            String ckNo = ck.stream().map(AccountSerialBill::getNo).distinct().collect(Collectors.joining(","));
            if (StringUtils.isNotBlank(ckNo)) {
                DelOutboundListQueryDto delOutboundListQueryDto = new DelOutboundListQueryDto();
                delOutboundListQueryDto.setOrderNo(ckNo);
                TableDataInfo<DelOutboundListVO> page = delOutboundFeignService.page(delOutboundListQueryDto);
                if (page.getCode() == HttpStatus.SUCCESS) {
                    List<DelOutboundListVO> rows = page.getRows();
                    Map<String, DelOutboundListVO> collect = rows.stream().collect(Collectors.toMap(DelOutboundListVO::getOrderNo, x -> x, (x1, x2) -> x1));
                    return ck.stream().map(x -> {
                        DelOutboundListVO delOutboundListVO = collect.get(x.getNo());
                        if (Objects.nonNull(delOutboundListVO)) {
                            Date createTime = delOutboundListVO.getCreateTime();
                            String warehouseCode = delOutboundListVO.getWarehouseCode();
                            x.setOrderTime(createTime);
                            x.setWarehouseCode(warehouseCode);
                            return new ListProcess(createTime, x.getNo(), warehouseCode);
                        }
                        return null;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
                }
            }
            return new ArrayList<>();
        });

        Void join = CompletableFuture.allOf(listCompletableFuture1, listCompletableFuture).join();

        List<ListProcess> listProcesses = listCompletableFuture.join();
        List<ListProcess> listProcesses1 = listCompletableFuture1.join();
        ArrayList<ListProcess> queryResult = new ArrayList<>();
        queryResult.addAll(listProcesses);
        queryResult.addAll(listProcesses1);
        Map<String, ListProcess> queryResultMap = queryResult.stream().collect(Collectors.toMap(ListProcess::getNo, x -> x, (x1, x2) -> x1));
        List<String> positiveNumber = Arrays.asList("线下充值", "退费", "优惠","赔偿");// 正数

        List<String> negativeNumber = Arrays.asList("补收", "增值消费"); //为负数
        accountSerialBills.forEach(x -> {
            if (StringUtils.isNotBlank(x.getNo())) {
                Optional.ofNullable(queryResultMap.get(x.getNo())).ifPresent(queryResultNo -> {
                    x.setWarehouseCode(queryResultNo.getWarehouseCode());
                    x.setOrderTime(queryResultNo.getOrderTime());
                });
            }
            if (null == x.getAmount()) x.setAmount(BigDecimal.ZERO);
            // 负数
            String businessCategory = x.getBusinessCategory();
            //费用类型，费用类型为汇率转换的也不能去转换负数
            String chargeType=x.getChargeType();
            if (StringUtils.isNotBlank(businessCategory) && positiveNumber.contains(businessCategory)) {
                x.setAmount(x.getAmount().abs());
            } else if (!chargeType.equals("汇率转换充值")){
                Optional.ofNullable(x.getAmount()).ifPresent(amount -> x.setAmount(amount.abs().negate()));
            }

        });

    }

    @Override
    public int add(AccountSerialBillDTO dto) {
        AccountSerialBill accountSerialBill = BeanMapperUtil.map(dto, AccountSerialBill.class);
        if (StringUtils.isBlank(accountSerialBill.getWarehouseName()))
            accountSerialBill.setWarehouseName(sysDictDataService.getWarehouseNameByCode(accountSerialBill.getWarehouseCode()));
        if (StringUtils.isBlank(accountSerialBill.getCurrencyName()))
            accountSerialBill.setCurrencyName(sysDictDataService.getCurrencyNameByCode(dto.getCurrencyCode()));
        accountSerialBill.setBusinessCategory(accountSerialBill.getChargeCategory());//性质列内容，同费用类别
        //单号不为空的时候
        if (StringUtils.isNotBlank(dto.getNo())){
           DelOutbound delOutbound=accountSerialBillMapper.selectDelOutbound(dto.getNo());
           if (delOutbound!=null){
               if (delOutbound.getId()!=null){
                   accountSerialBill.setRefNo(delOutbound.getRefNo());
                   accountSerialBill.setShipmentService(delOutbound.getShipmentService());
                   accountSerialBill.setWeight(delOutbound.getWeight());
                   accountSerialBill.setCalcWeight(delOutbound.getCalcWeight());
                   accountSerialBill.setSpecifications(delOutbound.getSpecifications());
               }
           }



        }
        return accountSerialBillMapper.insert(accountSerialBill);
    }

    @Override
    public boolean saveBatch(List<AccountSerialBillDTO> dto) {
        List<AccountSerialBill> accountSerialBill = BeanMapperUtil.mapList(dto, AccountSerialBill.class);
        List<AccountSerialBill> collect = accountSerialBill.stream().map(value -> {
            if (StringUtils.isBlank(value.getWarehouseName())) {
                value.setWarehouseName(sysDictDataService.getWarehouseNameByCode(value.getWarehouseCode()));
            }
            if (StringUtils.isBlank(value.getCurrencyName())) {
                value.setCurrencyName(sysDictDataService.getCurrencyNameByCode(value.getCurrencyCode()));
            }
            value.setBusinessCategory(value.getChargeCategory());//性质列内容，同费用类别
            //单号不为空的时候
            if (StringUtils.isNotBlank(value.getNo())){
                DelOutbound delOutbound=accountSerialBillMapper.selectDelOutbound(value.getNo());
                if (delOutbound!=null) {
                    if (delOutbound.getId() != null) {
                        value.setRefNo(delOutbound.getRefNo());
                        value.setShipmentService(delOutbound.getShipmentService());
                        value.setWeight(delOutbound.getWeight());
                        value.setCalcWeight(delOutbound.getCalcWeight());
                        value.setSpecifications(delOutbound.getSpecifications());
                    }
                }
            }
            return value;
        }).collect(Collectors.toList());
        boolean b = this.saveBatch(collect);
        if (!b){
            log.error("saveBatch() insert failed. {}", accountSerialBill);
        }
        return b;
    }

    /**
     * 幂等校验 校验重复扣费 ： 单号—发生额-业务类型
     *
     * @param dto
     * @return true : 已存在
     */
    @Override
    public boolean checkForDuplicateCharges(CustPayDTO dto) {
        List<AccountSerialBillDTO> serialBillInfoList = dto.getSerialBillInfoList();
        if (CollectionUtils.isEmpty(serialBillInfoList)){
            return false;
        }
        AccountSerialBillDTO accountSerialBillDTO = serialBillInfoList.get(0);

        Integer count = baseMapper.selectCount(Wrappers.<AccountSerialBill>lambdaQuery()
                .eq(AccountSerialBill::getNo, dto.getNo())
                .eq(AccountSerialBill::getAmount, accountSerialBillDTO.getAmount())
                .eq(AccountSerialBill::getChargeCategory, accountSerialBillDTO.getChargeCategory())
        );

        boolean flag = !count.equals(0);

        return flag;
    }

    @Override
    public List<ElectronicBillVO> electronicPage(EleBillQueryVO queryVO) {
        
        return accountBillRecordMapper.electronicPage(queryVO);
    }

    @Override
    public R<Integer> generatorBill(BillGeneratorRequestVO billRequestVO) {
        return R.ok();
    }

    @Override
    public List<BillBalanceVO> balancePage(EleBillQueryVO queryVO) {

        //step 1.根据客户、费用类型分组

        //step 2.根据客户，计算出本期收入、本期支出、本期余额、本期需要支付数据

        List<BillBalanceVO> billBalanceVOS = new ArrayList<>();

        List<BasCurrencyVO> basCurrencys = new ArrayList<>();

        BasCurrencyVO basCurrencyVO = new BasCurrencyVO();
        basCurrencyVO.setCurrencyName("美元");
        basCurrencyVO.setCurrencyCode("USD");
        basCurrencys.add(basCurrencyVO);

        BasCurrencyVO basCurrencyVO1 = new BasCurrencyVO();
        basCurrencyVO1.setCurrencyName("人民币");
        basCurrencyVO1.setCurrencyCode("CNY");
        basCurrencys.add(basCurrencyVO1);

//
        BillBalanceVO billBalanceVO = new BillBalanceVO();
        billBalanceVO.setCusCode("A111");
        billBalanceVO.setBillStartTime("2022-09-01");
        billBalanceVO.setBillEndTime("2022-09-10");
        billBalanceVO.setBasCurrencys(basCurrencys);

        List<BillChargeCategoryVO> chargeCategorys = new ArrayList<>();

        BillChargeCategoryVO billChargeCategoryVO = new BillChargeCategoryVO();
        billChargeCategoryVO.setChargeCategory("本期收入");

        List<BillCurrencyAmountVO> billCurrencyAmounts = new ArrayList<>();
        BillCurrencyAmountVO currencyVO = new BillCurrencyAmountVO();
        currencyVO.setCurrencyCode("USD");
        currencyVO.setCurrencyName("美元");
        currencyVO.setAmount(new BigDecimal("321421.21"));

        BillCurrencyAmountVO currencyVO23 = new BillCurrencyAmountVO();
        currencyVO23.setCurrencyCode("CNY");
        currencyVO23.setCurrencyName("人民币");
        currencyVO23.setAmount(new BigDecimal("321421.21"));

        billCurrencyAmounts.add(currencyVO);
        billCurrencyAmounts.add(currencyVO23);
        billChargeCategoryVO.setBillCurrencyAmounts(billCurrencyAmounts);

        BillChargeCategoryVO billChargeCategoryVO1 = new BillChargeCategoryVO();
        billChargeCategoryVO1.setChargeCategory("本期支付");

        List<BillCurrencyAmountVO> billCurrencyAmounts1 = new ArrayList<>();
        BillCurrencyAmountVO currencyVO1 = new BillCurrencyAmountVO();
        currencyVO1.setCurrencyCode("CNY");
        currencyVO1.setCurrencyName("人民币");
        currencyVO1.setAmount(new BigDecimal("321421.21"));
        billCurrencyAmounts1.add(currencyVO1);

        billChargeCategoryVO1.setBillCurrencyAmounts(billCurrencyAmounts1);

        chargeCategorys.add(billChargeCategoryVO);
        chargeCategorys.add(billChargeCategoryVO1);

        billBalanceVO.setChargeCategorys(chargeCategorys);

        billBalanceVOS.add(billBalanceVO);


        return billBalanceVOS;
    }

//
}
