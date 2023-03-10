package com.szmsd.finance.handler;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.bas.api.feign.BasFeignService;
import com.szmsd.bas.vo.BasSellerInfoVO;
import com.szmsd.common.core.utils.BigDecimalUtil;
import com.szmsd.common.core.utils.DateUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.finance.domain.AccountSerialBill;
import com.szmsd.finance.domain.FssConvertUnit;
import com.szmsd.finance.mapper.AccountBalanceLogMapper;
import com.szmsd.finance.mapper.AccountSerialBillMapper;
import com.szmsd.finance.mapper.FssConvertUnitMapper;
import com.szmsd.finance.util.ExcelUtil;
import com.szmsd.finance.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class BillGeneratorExcelTask implements Callable<AccountBillRecordTaskResultVO> {

    private BillGeneratorBO billGeneratorBO;

    private AccountSerialBillMapper accountSerialBillMapper;

    private FssConvertUnitMapper fssConvertUnitMapper;

    private AccountBalanceLogMapper accountBalanceLogMapper;

    private BasFeignService basFeignService;

    private HttpServletRequest request;

    public BillGeneratorExcelTask(){

    }

    public BillGeneratorExcelTask(BillGeneratorBO billGeneratorBO){
        this.billGeneratorBO = billGeneratorBO;
    }

    @Override
    public AccountBillRecordTaskResultVO call() throws Exception {

        Long current = System.currentTimeMillis();

        BillGeneratorRequestVO billRequestVO = billGeneratorBO.getBillRequestVO();
        BasSellerInfoVO basSellerInfoVO = billGeneratorBO.getBasSellerInfoVO();
        accountSerialBillMapper = billGeneratorBO.getAccountSerialBillMapper();
        String filePath = billGeneratorBO.getFilePath();
        accountBalanceLogMapper = billGeneratorBO.getAccountBalanceLogMapper();
        basFeignService = billGeneratorBO.getBasFeignService();
        request = billGeneratorBO.getRequest();
        fssConvertUnitMapper = billGeneratorBO.getFssConvertUnitMapper();

        String scheme = request.getScheme();
        int port = request.getServerPort();
        String serverName = request.getServerName();

        String fileName = "dm-oms-template-"+current+".xlsx";
        String modelPath = "classpath:template/dm-oms-template.xlsx";
        InputStream inputStream = null;

        try {
            inputStream = ResourceUtils.getURL(modelPath).openStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ConcurrentHashMap<Integer, List<?>> titleDataMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, List<?>> sheetAndDataMap = new ConcurrentHashMap<>();

        ConcurrentHashMap<Integer, List<?>> otherAndDataMap = new ConcurrentHashMap<>();

        //sheet 0.??????????????????????????????????????????????????????
        List<BasSellerExcelInfoVO> cusTitleMap = this.generatorTitle(billRequestVO,basSellerInfoVO);

        EleBillQueryVO queryVO = new EleBillQueryVO();
        queryVO.setCusCode(billRequestVO.getCusCode());
        queryVO.setBillStartTime(billRequestVO.getBillStartTime());
        queryVO.setBillEndTime(billRequestVO.getBillEndTime());

        BillSelectBalanceTask billSelectBalanceTask = new BillSelectBalanceTask(accountSerialBillMapper,accountBalanceLogMapper,basFeignService);

        //???????????????
        List<BillBalanceVO> billBalanceVOS = billSelectBalanceTask.find(queryVO);
        //????????????????????????
        List<BillBalanceExcelResultVO> billBalanceExcelResultVOS = this.generatorBillExcelResult(billBalanceVOS);

        //???????????????
        List<BillBusinessTotalVO> businessTotalVOS = this.selectBusinessTotal(queryVO);
        sheetAndDataMap.put(0,billBalanceExcelResultVOS);
        titleDataMap.put(0,cusTitleMap);
        otherAndDataMap.put(0,businessTotalVOS);

        //sheet 1.??????????????????
        EleBillQueryVO directDeliveryQueryVO = new EleBillQueryVO();
        directDeliveryQueryVO.setCusCode(billRequestVO.getCusCode());
        directDeliveryQueryVO.setBillStartTime(billRequestVO.getBillStartTime());
        directDeliveryQueryVO.setBillEndTime(billRequestVO.getBillEndTime());
        List<String> orderTypeList = new ArrayList<>();
        orderTypeList.add("PackageTransfer");
        directDeliveryQueryVO.setSheetNo(1);
        directDeliveryQueryVO.setOrderTypeList(orderTypeList);
        List<BillDirectDeliveryTotalVO> directDeliverys = this.selectDirectDelivery(directDeliveryQueryVO);
        sheetAndDataMap.put(1,directDeliverys);

        //sheet 2.????????????
        EleBillQueryVO storegeQueryVO = new EleBillQueryVO();
        storegeQueryVO.setCusCode(billRequestVO.getCusCode());
        storegeQueryVO.setBillStartTime(billRequestVO.getBillStartTime());
        storegeQueryVO.setBillEndTime(billRequestVO.getBillEndTime());
        List<String> storegeOrderTypeList = new ArrayList<>();
        storegeOrderTypeList.add("Batch");
        storegeOrderTypeList.add("Collection");
        storegeOrderTypeList.add("Destroy");
        storegeOrderTypeList.add("NewSku");
        storegeOrderTypeList.add("Normal");
        storegeOrderTypeList.add("SelfPick");
        storegeOrderTypeList.add("SplitSku");
        storegeQueryVO.setOrderTypeList(storegeOrderTypeList);
        storegeQueryVO.setSheetNo(2);
        List<BillDirectDeliveryTotalVO> storegeDeliverys = this.selectDirectDelivery(storegeQueryVO);
        sheetAndDataMap.put(2,storegeDeliverys);

        //sheet 3.??????

        //sheet 4.????????????
        EleBillQueryVO bigGoodsQueryVO = new EleBillQueryVO();
        bigGoodsQueryVO.setCusCode(billRequestVO.getCusCode());
        bigGoodsQueryVO.setBillStartTime(billRequestVO.getBillStartTime());
        bigGoodsQueryVO.setBillEndTime(billRequestVO.getBillEndTime());
        List<String> bigGoodsTypeList = new ArrayList<>();
        bigGoodsTypeList.add("BulkOrder");
        bigGoodsQueryVO.setOrderTypeList(bigGoodsTypeList);
        bigGoodsQueryVO.setSheetNo(4);
        List<BillDirectDeliveryTotalVO> bigGoodsDeliverys = this.selectDirectDelivery(bigGoodsQueryVO);
        sheetAndDataMap.put(4,bigGoodsDeliverys);

        //sheet 5.??????&??????&??????&??????
        EleBillQueryVO billDetailQueryVO = new EleBillQueryVO();
        billDetailQueryVO.setCusCode(billRequestVO.getCusCode());
        billDetailQueryVO.setBillStartTime(billRequestVO.getBillStartTime());
        billDetailQueryVO.setBillEndTime(billRequestVO.getBillEndTime());
        billDetailQueryVO.setSheetNo(5);
        List<AccountSerialBill> billDetails = this.selectBillDetails(billDetailQueryVO);
        sheetAndDataMap.put(5,billDetails);

        //sheet 6.??????&??????
        EleBillQueryVO discountDetailQueryVO = new EleBillQueryVO();
        discountDetailQueryVO.setCusCode(billRequestVO.getCusCode());
        discountDetailQueryVO.setBillStartTime(billRequestVO.getBillStartTime());
        discountDetailQueryVO.setBillEndTime(billRequestVO.getBillEndTime());
        discountDetailQueryVO.setSheetNo(6);
        List<AccountSerialBill> discountDetails = this.selectBillDetails(discountDetailQueryVO);
        sheetAndDataMap.put(6,discountDetails);

        //sheet 7 ????????????
        EleBillQueryVO discountDetailQueryVO7 = new EleBillQueryVO();
        discountDetailQueryVO7.setCusCode(billRequestVO.getCusCode());
        discountDetailQueryVO7.setBillStartTime(billRequestVO.getBillStartTime());
        discountDetailQueryVO7.setBillEndTime(billRequestVO.getBillEndTime());
        discountDetailQueryVO7.setSheetNo(7);
        List<AccountSerialBill> discountDetailszz = this.selectBillDetails(discountDetailQueryVO7);
        sheetAndDataMap.put(7,discountDetailszz);

        try{

            AccountBillRecordTaskResultVO taskResultVO = new AccountBillRecordTaskResultVO();


            String requestUrl = scheme+"://"+serverName+":"+port;

            String f = filePath + fileName;
            File file = new File(f);

            //??????????????????
            int [] mergeCollindex = {0,1,2};
            //??????????????????
            int mergeRowIndex = 20;
            ExcelUtil.exportFile(file,"bas",titleDataMap,"bill",sheetAndDataMap,"business",otherAndDataMap,inputStream,mergeRowIndex,mergeCollindex);

            String fileUrl = requestUrl + f;

            taskResultVO.setFileName(fileName);
            taskResultVO.setRecordId(billGeneratorBO.getRecordId());
            taskResultVO.setFileUrl(fileUrl);

            return taskResultVO;

        }catch (Exception e){
            e.printStackTrace();
            log.error("??????????????????:",e);
        }

        return null;
    }

    private List<AccountSerialBill> selectBillDetails(EleBillQueryVO billDetailQueryVO) {

        return accountSerialBillMapper.selectBillDetails(billDetailQueryVO);
    }

    private List<BillDirectDeliveryTotalVO> selectDirectDelivery(EleBillQueryVO queryVO) {

        List<BillDirectDeliveryTotalVO> billDirectDeliveryTotalVOS = accountSerialBillMapper.selectDirectDelivery(queryVO);

        List<FssConvertUnit> fssConvertUnits = fssConvertUnitMapper.selectList(Wrappers.<FssConvertUnit>query().lambda());
        Map<String,FssConvertUnit> convertUnitMap = fssConvertUnits.stream().collect(Collectors.toMap(FssConvertUnit::getCalcUnit, v->v));

        for(BillDirectDeliveryTotalVO billDirectDeliveryTotalVO : billDirectDeliveryTotalVOS){

            String resultCalaWeight = "";
            String resultWeight = "";
            String resultforecastWeight = "";
//            if(billDirectDeliveryTotalVO.getCalcWeight() != null && billDirectDeliveryTotalVO.getCalcWeightUnit() != null){
//                resultCalaWeight = billDirectDeliveryTotalVO.getCalcWeight() + billDirectDeliveryTotalVO.getCalcWeightUnit();
//            }

            String calcWeightUnit = billDirectDeliveryTotalVO.getCalcWeightUnit();
            BigDecimal calcWeight = billDirectDeliveryTotalVO.getCalcWeight();

            if(StringUtils.isNotEmpty(calcWeightUnit) && convertUnitMap != null){
                FssConvertUnit fssConvertUnit = convertUnitMap.get(calcWeightUnit);

                if(fssConvertUnit != null){
                    BigDecimal convertValue =  fssConvertUnit.getConvertValue();
                    BigDecimal result = BigDecimalUtil.setScale(calcWeight.multiply(convertValue),3);
                    resultCalaWeight = result.toString();
                }
            }

            if(billDirectDeliveryTotalVO.getWeight() != null){
                resultWeight = billDirectDeliveryTotalVO.getWeight();
            }

            if(billDirectDeliveryTotalVO.getForecastWeight() != null){
                resultforecastWeight = billDirectDeliveryTotalVO.getForecastWeight();
            }

            String businessCategory = billDirectDeliveryTotalVO.getBusinessCategory();

            if(businessCategory != null && businessCategory.equals("????????????")){

                billDirectDeliveryTotalVO.setTotalAmount(billDirectDeliveryTotalVO.getTotalAmount().negate());
                billDirectDeliveryTotalVO.setFreightFee(billDirectDeliveryTotalVO.getFreightFee().negate());
                billDirectDeliveryTotalVO.setExWarehourseFee(billDirectDeliveryTotalVO.getExWarehourseFee().negate());
                billDirectDeliveryTotalVO.setPackageFee(billDirectDeliveryTotalVO.getPackageFee().negate());
                billDirectDeliveryTotalVO.setHanderFee(billDirectDeliveryTotalVO.getHanderFee().negate());
                billDirectDeliveryTotalVO.setFuelFee(billDirectDeliveryTotalVO.getFuelFee().negate());
                billDirectDeliveryTotalVO.setRemoteFee(billDirectDeliveryTotalVO.getRemoteFee().negate());
                billDirectDeliveryTotalVO.setOtherFee(billDirectDeliveryTotalVO.getOtherFee().negate());
            }

            billDirectDeliveryTotalVO.setResultCalcWeight(resultCalaWeight);
            billDirectDeliveryTotalVO.setWeight(resultWeight);
            billDirectDeliveryTotalVO.setForecastWeight(resultforecastWeight);
        }

        return billDirectDeliveryTotalVOS;
    }

    /**
     * ??????????????????
     * @param queryVO
     * @return
     */
    private List<BillBusinessTotalVO> selectBusinessTotal(EleBillQueryVO queryVO) {

        List<BillBusinessTotalVO> businessTotalVOS = new ArrayList<>();

        //????????????
        List<String> orderTypePackageTransferList = new ArrayList<>();
        orderTypePackageTransferList.add("PackageTransfer");
        queryVO.setOrderTypeList(orderTypePackageTransferList);
        queryVO.setSheetNo(1);
        List<BillBusinessTotalVO> resultVOS = accountSerialBillMapper.selectAllOrderType(queryVO);

        //List<BillBusinessTotalVO> packageTrans = this.generatorOrderType(resultVOS);

        //????????????
        List<String> orderTypeWmsList = new ArrayList<>();
        orderTypeWmsList.add("Batch");
        orderTypeWmsList.add("Collection");
        orderTypeWmsList.add("Destroy");
        orderTypeWmsList.add("NewSku");
        orderTypeWmsList.add("Normal");
        orderTypeWmsList.add("SelfPick");
        orderTypeWmsList.add("SplitSku");
        queryVO.setOrderTypeList(orderTypeWmsList);
        queryVO.setSheetNo(2);
        List<BillBusinessTotalVO> orderWmsVOS = accountSerialBillMapper.selectAllOrderType(queryVO);


        //????????????
        List<String> bigGoodsWmsList = new ArrayList<>();
        bigGoodsWmsList.add("BulkOrder");
        queryVO.setOrderTypeList(bigGoodsWmsList);
        queryVO.setSheetNo(4);
        List<BillBusinessTotalVO> bigGoodsVOS = accountSerialBillMapper.selectAllOrderType(queryVO);

        //??????
        queryVO.setOrderTypeList(null);
        queryVO.setSheetNo(null);
        List<BillBusinessTotalVO> rechargeData = accountSerialBillMapper.recharge(queryVO);

        //??????
        List<BillBusinessTotalVO> withdrawalData = accountSerialBillMapper.withdrawal(queryVO);

        //??????
        List<BillBusinessTotalVO> supplementaryData  = accountSerialBillMapper.supplementary(queryVO);

        //??????
        //List<BillBusinessTotalVO> discountData  = accountSerialBillMapper.discount(queryVO);

        //??????
        //List<BillBusinessTotalVO> compensateData = accountSerialBillMapper.compensate(queryVO);

        //??????
        //List<BillBusinessTotalVO> refundData = accountSerialBillMapper.refund(queryVO);

        //??????/??????/??????
        List<BillBusinessTotalVO> businessAll  = accountSerialBillMapper.businessAll(queryVO);

        //????????????
        List<BillBusinessTotalVO> balanceData  = accountSerialBillMapper.balanceConversion(queryVO);

        businessTotalVOS.addAll(resultVOS);
        businessTotalVOS.addAll(bigGoodsVOS);
        businessTotalVOS.addAll(orderWmsVOS);
        businessTotalVOS.addAll(rechargeData);
        businessTotalVOS.addAll(withdrawalData);
        businessTotalVOS.addAll(supplementaryData);
        businessTotalVOS.addAll(businessAll);
        //businessTotalVOS.addAll(discountData);
        //businessTotalVOS.addAll(refundData);
        businessTotalVOS.addAll(balanceData);

        return businessTotalVOS;
    }

    private List<BillBusinessTotalVO> generatorOrderType(List<BillBusinessTotalVO> resultVOS) {



        return null;
    }

    /**
     * ????????????????????????
     * @param billBalanceVOS
     * @return
     */
    private List<BillBalanceExcelResultVO> generatorBillExcelResult(List<BillBalanceVO> billBalanceVOS) {

        if(CollectionUtils.isEmpty(billBalanceVOS)){
            return new ArrayList<>();
        }

        BillBalanceVO billBalanceVO = billBalanceVOS.get(0);
        List<BillChargeCategoryVO> billChargeCategoryVOS = billBalanceVO.getChargeCategorys();
        List<BillBalanceExcelResultVO> billBalanceExcelResultVOS = new ArrayList<>();

        for(BillChargeCategoryVO chargeCategoryVO : billChargeCategoryVOS){

            BillBalanceExcelResultVO billBalanceExcelResultVO = new BillBalanceExcelResultVO();
            billBalanceExcelResultVO.setChargeCategory(chargeCategoryVO.getChargeCategory());

            List<BillCurrencyAmountVO> billCurrencyAmountVOS = chargeCategoryVO.getBillCurrencyAmounts();

            for(BillCurrencyAmountVO amountVO :billCurrencyAmountVOS){

                String currencyCode = amountVO.getCurrencyCode();
                if(currencyCode.equals("USD")){
                    billBalanceExcelResultVO.setUsd(amountVO.getAmount());
                }
                if(currencyCode.equals("CNY")){
                    billBalanceExcelResultVO.setCny(amountVO.getAmount());
                }
                if(currencyCode.equals("GBP")){
                    billBalanceExcelResultVO.setGbp(amountVO.getAmount());
                }
                if(currencyCode.equals("AUD")){
                    billBalanceExcelResultVO.setAud(amountVO.getAmount());
                }
                if(currencyCode.equals("EUR")){
                    billBalanceExcelResultVO.setEur(amountVO.getAmount());
                }
                if(currencyCode.equals("CAD")){
                    billBalanceExcelResultVO.setCad(amountVO.getAmount());
                }
                if(currencyCode.equals("HKD")){
                    billBalanceExcelResultVO.setHkd(amountVO.getAmount());
                }
                if(currencyCode.equals("JPY")){
                    billBalanceExcelResultVO.setJpy(amountVO.getAmount());
                }
            }

            billBalanceExcelResultVOS.add(billBalanceExcelResultVO);
        }

        return billBalanceExcelResultVOS;
    }

    private List<BasSellerExcelInfoVO> generatorTitle(BillGeneratorRequestVO billRequestVO, BasSellerInfoVO basSellerInfoVO) {

        String billStartTime = billRequestVO.getBillStartTime();
        String billEndTime = billRequestVO.getBillEndTime();

        String billTime = billStartTime + "/\n" + billEndTime;


        List<BasSellerExcelInfoVO> basSellerInfoVOS = new ArrayList<>();

        BasSellerExcelInfoVO excelInfoVO = new BasSellerExcelInfoVO();

        excelInfoVO.setCusCode(basSellerInfoVO.getSellerCode());
        excelInfoVO.setCusName(basSellerInfoVO.getUserName());
        excelInfoVO.setGeneratorTime(DateUtils.getTime());
        excelInfoVO.setBillTime(billTime);
        excelInfoVO.setManagerNickName(basSellerInfoVO.getServiceManagerNickName());
        excelInfoVO.setStaffNickName(basSellerInfoVO.getServiceStaffNickName() == null ? "":basSellerInfoVO.getServiceStaffNickName());

        basSellerInfoVOS.add(excelInfoVO);

        return basSellerInfoVOS;
    }
}
