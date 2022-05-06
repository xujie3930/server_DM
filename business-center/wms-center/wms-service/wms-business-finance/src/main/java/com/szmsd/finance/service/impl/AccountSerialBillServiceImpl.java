package com.szmsd.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.datascope.annotation.DataScope;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.delivery.dto.DelOutboundListQueryDto;
import com.szmsd.delivery.vo.DelOutboundListVO;
import com.szmsd.finance.domain.AccountSerialBill;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.mapper.AccountSerialBillMapper;
import com.szmsd.finance.service.IAccountSerialBillService;
import com.szmsd.finance.service.ISysDictDataService;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import com.szmsd.putinstorage.domain.dto.InboundReceiptQueryDTO;
import com.szmsd.putinstorage.domain.vo.InboundReceiptVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Override
    @DataScope("cus_code")
    public List<AccountSerialBill> listPage(AccountSerialBillDTO dto) {
        LambdaQueryWrapper<AccountSerialBill> query = Wrappers.lambdaQuery();
        query.in(CollectionUtils.isNotEmpty(dto.getNoList()), AccountSerialBill::getNo, dto.getNoList());
        query.in(CollectionUtils.isNotEmpty(dto.getCusCodeList()), AccountSerialBill::getCusCode, dto.getCusCodeList());
        query.in(CollectionUtils.isNotEmpty(dto.getProductCodeList()), AccountSerialBill::getProductCode, dto.getProductCodeList());
        if (StringUtils.isNotBlank(dto.getChargeType())) {
            query.eq(AccountSerialBill::getChargeType, dto.getChargeType());
        }
        if (StringUtils.isNotBlank(dto.getWarehouseCode())) {
            query.eq(AccountSerialBill::getWarehouseCode, dto.getWarehouseCode());
        }
        if (StringUtils.isNotBlank(dto.getCurrencyCode())) {
            query.eq(AccountSerialBill::getCurrencyCode, dto.getCurrencyCode());
        }
        if (StringUtils.isNotBlank(dto.getBusinessCategory())) {
            query.eq(AccountSerialBill::getBusinessCategory, dto.getBusinessCategory());
        }
        if (StringUtils.isNotBlank(dto.getProductCategory())) {
            query.eq(AccountSerialBill::getProductCategory, dto.getProductCategory());
        }
        if (StringUtils.isNotBlank(dto.getChargeCategory())) {
            query.eq(AccountSerialBill::getChargeCategory, dto.getChargeCategory());
        }
        if (StringUtils.isNotBlank(dto.getCreateTimeStart())) {
            query.ge(AccountSerialBill::getCreateTime, dto.getCreateTimeStart());
        }
        if (StringUtils.isNotBlank(dto.getCreateTimeEnd())) {
            query.le(AccountSerialBill::getCreateTime, dto.getCreateTimeEnd());
        }
        if (StringUtils.isNotBlank(dto.getIds())) {
            query.in(AccountSerialBill::getId, (Object[]) dto.getIds().split(","));
        }
        List<AccountSerialBill> accountSerialBills = accountSerialBillMapper.selectPageList(query);
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
        List<String> positiveNumber = Arrays.asList("线下充值", "退费", "优惠");
        // 退费下的 集合为负数
        List<String> negativeNumber = Arrays.asList("补收", "增值消费");
        long count = accountSerialBills.parallelStream().peek(x -> {
            if (StringUtils.isNotBlank(x.getNo())) {
                Optional.ofNullable(queryResultMap.get(x.getNo())).ifPresent(queryResultNo -> {
                    x.setWarehouseCode(queryResultNo.getWarehouseCode());
                    x.setOrderTime(queryResultNo.getOrderTime());
                });
            }
            // 负数
            String chargeCategory = x.getChargeCategory();
            boolean b = StringUtils.isBlank(chargeCategory);
            boolean b2 = ("退费".equals(chargeCategory) && negativeNumber.contains(x.getBusinessCategory()));
            boolean b1 = !positiveNumber.contains(chargeCategory);

            if (b || b1 || b2) {
                Optional.ofNullable(x.getAmount()).ifPresent(amount -> x.setAmount(amount.abs().negate()));
            } else {
                x.setAmount(x.getAmount().abs());
            }
        }).count();

    }

    @Override
    public int add(AccountSerialBillDTO dto) {
        AccountSerialBill accountSerialBill = BeanMapperUtil.map(dto, AccountSerialBill.class);
        if (StringUtils.isBlank(accountSerialBill.getWarehouseName()))
            accountSerialBill.setWarehouseName(sysDictDataService.getWarehouseNameByCode(accountSerialBill.getWarehouseCode()));
        if (StringUtils.isBlank(accountSerialBill.getCurrencyName()))
            accountSerialBill.setCurrencyName(sysDictDataService.getCurrencyNameByCode(dto.getCurrencyCode()));
        accountSerialBill.setBusinessCategory(accountSerialBill.getChargeCategory());//性质列内容，同费用类别
        return accountSerialBillMapper.insert(accountSerialBill);
    }

    @Override
    public boolean saveBatch(List<AccountSerialBillDTO> dto) {
        List<AccountSerialBill> accountSerialBill = BeanMapperUtil.mapList(dto, AccountSerialBill.class);
        List<AccountSerialBill> collect = accountSerialBill.stream().map(value -> {
            if (StringUtils.isBlank(value.getWarehouseName()))
                value.setWarehouseName(sysDictDataService.getWarehouseNameByCode(value.getWarehouseCode()));
            if (StringUtils.isBlank(value.getCurrencyName()))
                value.setCurrencyName(sysDictDataService.getCurrencyNameByCode(value.getCurrencyCode()));
            value.setBusinessCategory(value.getChargeCategory());//性质列内容，同费用类别
            return value;
        }).collect(Collectors.toList());
        boolean b = this.saveBatch(collect);
        if (!b) log.error("saveBatch() insert failed. {}", accountSerialBill);
        return b;
    }

}
