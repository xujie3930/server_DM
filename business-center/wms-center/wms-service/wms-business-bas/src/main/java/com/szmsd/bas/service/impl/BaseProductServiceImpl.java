package com.szmsd.bas.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlKeyword;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.bas.api.client.BasSubClientService;
import com.szmsd.bas.api.domain.BasAttachment;
import com.szmsd.bas.api.domain.dto.AttachmentDTO;
import com.szmsd.bas.api.domain.dto.AttachmentDataDTO;
import com.szmsd.bas.api.domain.dto.BasAttachmentQueryDTO;
import com.szmsd.bas.api.enums.AttachmentTypeEnum;
import com.szmsd.bas.api.enums.BaseMainEnum;
import com.szmsd.bas.api.feign.BasSubFeignService;
import com.szmsd.bas.api.feign.RemoteAttachmentService;
import com.szmsd.bas.constant.ProductConstant;
import com.szmsd.bas.domain.BasSeller;
import com.szmsd.bas.domain.BasePacking;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.*;
import com.szmsd.bas.mapper.BaseProductMapper;
import com.szmsd.bas.plugin.vo.BasSubWrapperVO;
import com.szmsd.bas.service.IBasSellerService;
import com.szmsd.bas.service.IBasSerialNumberService;
import com.szmsd.bas.service.IBasePackingService;
import com.szmsd.bas.service.IBaseProductService;
import com.szmsd.bas.util.ObjectUtil;
import com.szmsd.bas.vo.BasProductMultipleTicketDTO;
import com.szmsd.bas.vo.BaseProductVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.exception.web.BaseException;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.utils.bean.QueryWrapperUtil;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.datascope.annotation.DataScope;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.http.api.feign.HtpBasFeignService;
import com.szmsd.http.api.feign.HtpRmiFeignService;
import com.szmsd.http.config.CkThreadPool;
import com.szmsd.http.dto.HttpRequestSyncDTO;
import com.szmsd.http.dto.ProductRequest;
import com.szmsd.bas.api.dto.CkSkuCreateDTO;
import com.szmsd.http.enums.DomainEnum;
import com.szmsd.http.enums.RemoteConstant;
import com.szmsd.http.vo.HttpResponseVO;
import com.szmsd.http.vo.ResponseVO;
import com.szmsd.putinstorage.domain.dto.AttachmentFileDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.Resource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * <p>
 * ???????????????
 * </p>
 *
 * @author l
 * @since 2021-03-04
 */
@Service
@Slf4j
public class BaseProductServiceImpl extends ServiceImpl<BaseProductMapper, BaseProduct> implements IBaseProductService {

    private static final String regex = "^[a-z0-9A-Z]+$";
    @Autowired
    private IBasSellerService basSellerService;
    @Resource
    private HtpBasFeignService htpBasFeignService;
    @Resource
    private IBasSerialNumberService baseSerialNumberService;
    @Autowired
    private RemoteAttachmentService remoteAttachmentService;
    @Resource
    private BaseProductMapper baseProductMapper;
    @Resource
    private BasSubFeignService basSubFeignService;
    @Autowired
    private IBasePackingService basePackingService;
    @Resource
    private CkThreadPool ckThreadPool;
    @Resource
    private HtpRmiFeignService htpRmiFeignService;
    @Autowired
    private BasSubClientService basSubClientService;

    /**
     * ????????????
     *
     * @param id ??????ID
     * @return ??????
     */
    @Override
    public BaseProduct selectBaseProductById(String id) {
        return baseMapper.selectById(id);
    }

    /**
     * ??????????????????
     *
     * @param baseProduct ??????
     * @return ??????
     */
    @Override
    public List<BaseProduct> selectBaseProductList(BaseProduct baseProduct) {
        QueryWrapper<BaseProduct> where = new QueryWrapper<BaseProduct>();
        return baseMapper.selectList(where);
    }

    @Override
//    @DataScope("seller_code")
    public List<BaseProduct> selectBaseProductPage(BaseProductQueryDto queryDto) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(queryDto.getCodes())) {
            //String[] codes = queryDto.getCodes().split(",");
            queryWrapper.in("code", queryDto.getCodesList());
        }
        if (Objects.nonNull(SecurityUtils.getLoginUser())) {
            String cusCode = StringUtils.isNotEmpty(SecurityUtils.getLoginUser().getSellerCode()) ? SecurityUtils.getLoginUser().getSellerCode() : "";
            if (com.szmsd.common.core.utils.StringUtils.isEmpty(queryDto.getSellerCodes())) {
                queryDto.setSellerCodes(cusCode);
            }
        }


        if (StringUtils.isNotEmpty(queryDto.getSellerCode())) {
            queryWrapper.eq("seller_code", queryDto.getSellerCode());
        }

        log.info("doc??????sku??????token?????????{}",  SecurityUtils.getLoginUser());

        log.info("doc??????sku???????????????code???{}", queryDto.getCodes());
        log.info("doc??????sku???????????????codes???{}", queryDto.getSellerCodes());
        if (StringUtils.isNotEmpty(queryDto.getSellerCodes())) {
            String[] sellerCodes = queryDto.getSellerCodes().split(",");
            queryWrapper.in("seller_code", sellerCodes);
        }
        if (CollectionUtils.isNotEmpty(queryDto.getIds())) {
            queryWrapper.in("id", queryDto.getIds());
        }
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "category", queryDto.getCategory());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "source", queryDto.getSource());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "code", queryDto.getCode());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.LIKE, "product_name", queryDto.getProductName());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.LIKE, "product_name_chinese", queryDto.getProductNameChinese());
//        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "seller_code", queryDto.getSellerCode());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "product_attribute", queryDto.getProductAttribute());
       /* if (queryDto.getIsActive() != null) {
            queryWrapper.eq("is_active", queryDto.getIsActive());
        }*/
        queryWrapper.orderByDesc("create_time");
        return super.list(queryWrapper);
    }

    @Override
    public List<BaseProduct> listSkuBySeller(BaseProductQueryDto queryDto) {
        QueryWrapper<BasSeller> basSellerQueryWrapper = new QueryWrapper<>();
        basSellerQueryWrapper.eq("user_name", SecurityUtils.getLoginUser().getUsername());
        BasSeller basSeller = basSellerService.getOne(basSellerQueryWrapper);
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("seller_code", basSeller.getSellerCode());
        queryWrapper.eq("is_active", true);
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "category", queryDto.getCategory());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "code", queryDto.getCode());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.LIKE, "product_name", queryDto.getProductName());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "product_attribute", queryDto.getProductAttribute());
        queryWrapper.orderByDesc("create_time");
        return super.list(queryWrapper);
    }

    @Override
    public TableDataInfo<BaseProductVO> selectBaseProductByCode(String code, String sellerCode, String category, int current, int size) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "category", category);
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.LIKE, "code", code);
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "seller_code", sellerCode);
        queryWrapper.eq("is_active", true);
        int total = super.count(queryWrapper);
        queryWrapper.orderByDesc("create_time");
        queryWrapper.last("limit " + (current - 1) * size + "," + size);
        List<BaseProductVO> baseProductVOList = BeanMapperUtil.mapList(super.list(queryWrapper), BaseProductVO.class);
        baseProductVOList.forEach(b -> {
            if (b.getCode() != null) {
                List<BasAttachment> attachment = ListUtils.emptyIfNull(remoteAttachmentService
                        .list(new BasAttachmentQueryDTO().setAttachmentType(AttachmentTypeEnum.SKU_IMAGE.getAttachmentType()).setBusinessNo(b.getCode()).setBusinessItemNo(null)).getData());
                if (CollectionUtils.isNotEmpty(attachment)) {
                    List<AttachmentFileDTO> documentsFiles = new ArrayList();
                    for (BasAttachment a : attachment) {
                        documentsFiles.add(new AttachmentFileDTO().setId(a.getId()).setAttachmentName(a.getAttachmentName()).setAttachmentUrl(a.getAttachmentUrl()));
                    }
                    b.setDocumentsFiles(documentsFiles);
                }
            }
        });
        TableDataInfo table = new TableDataInfo();
        table.setTotal(total);
        table.setRows(baseProductVOList);
        table.setCode(200);
        return table;
    }

    @Override
    public List<BaseProductMeasureDto> batchSKU(BaseProductBatchQueryDto dto) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        if (CollectionUtils.isEmpty(dto.getCodes())) {
            return Collections.emptyList();
        } else {
            queryWrapper.eq("is_active", true);
            queryWrapper.in("code", dto.getCodes());
            QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "seller_code", dto.getSellerCode());
        }
        return BeanMapperUtil.mapList(super.list(queryWrapper), BaseProductMeasureDto.class);
    }

    @Override
    public void importBaseProduct(List<BaseProductImportDto> list) {

        //??????????????????
        QueryWrapper<BasSeller> basSellerQueryWrapper = new QueryWrapper<>();
        basSellerQueryWrapper.eq("user_name", SecurityUtils.getLoginUser().getUsername());
        BasSeller seller = basSellerService.getOne(basSellerQueryWrapper);
        verifyBaseProductRequired(list, seller.getSellerCode());
        for (BaseProductImportDto b : list) {
            b.setHavePackingMaterial(b.getHavePackingMaterialName().equals("???") ? true : false);
        }
        List<BaseProduct> baseProductList = BeanMapperUtil.mapList(list, BaseProduct.class);
        List<Tuple2<BaseProduct, ProductRequest>> waitSyncList = new ArrayList<>();
        for (BaseProduct b : baseProductList) {
            b.setCategory(ProductConstant.SKU_NAME);
            b.setCategoryCode(ProductConstant.SKU);
            b.setSellerCode(seller.getSellerCode());
            b.setInitHeight(new BigDecimal(b.getInitHeight()).setScale(2, ROUND_HALF_UP).doubleValue());
            b.setInitLength(new BigDecimal(b.getInitLength()).setScale(2, ROUND_HALF_UP).doubleValue());
            b.setInitWidth(new BigDecimal(b.getInitWidth()).setScale(2, ROUND_HALF_UP).doubleValue());
            b.setInitWeight(new BigDecimal(b.getInitWeight()).setScale(2, ROUND_HALF_UP).doubleValue());
            b.setHeight(b.getInitHeight());
            b.setLength(b.getInitLength());
            b.setWidth(b.getInitWidth());
            b.setWeight(b.getInitWeight());
            b.setInitVolume(new BigDecimal(b.getInitHeight() * b.getInitLength() * b.getInitWidth()).setScale(2, ROUND_HALF_UP));
            b.setVolume(b.getInitVolume());
            b.setIsActive(true);
            b.setSource(BaseMainEnum.NORMAL_IN.getCode());
            b.setWarehouseAcceptance(false);
            ProductRequest productRequest = BeanMapperUtil.map(b, ProductRequest.class);
            productRequest.setProductDesc(b.getProductDescription());
            waitSyncList.add(Tuples.of(b, productRequest));
//            R<ResponseVO> r = htpBasFeignService.createProduct(productRequest);
//            if (!r.getData().getSuccess()) {
//                throw new BaseException("???wms??????:" + r.getData().getMessage());
//            }
        }
        Tuple2<List<BaseProduct>, String> result = syncToWms(waitSyncList);
        super.saveBatch(result.getT1());
        AssertUtil.isTrue(StringUtils.isBlank(result.getT2()), result.getT2());

    }

    @Override
    public void attribute(EtSkuAttributeRequest etSkuAttributeRequest) {

        java.util.Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("059");
        java.util.Map<String, String> map059 = new HashMap();
        if(listMap.get("059") != null){
            map059 = listMap.get("059").stream()
                    .collect(Collectors.toMap(BasSubWrapperVO::getSubValue,
                            BasSubWrapperVO:: getSubName, (v1, v2) -> v1));
        }

        log.info("et??????sku??????: {}", etSkuAttributeRequest);
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", etSkuAttributeRequest.getSku());
        if (super.count(queryWrapper) < 1) {
            throw new BaseException("Sku does not exist");
        }
        String operationOn = etSkuAttributeRequest.getOperateOn();
        BaseProduct baseProduct = new BaseProduct();
        if (StringUtils.isNotEmpty(operationOn)) {
            try {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = df.parse(operationOn);
                baseProduct.setOperateOn(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        baseProduct.setOperator(etSkuAttributeRequest.getOperator());

        if(!map059.containsKey(etSkuAttributeRequest.getSkuAttribute())){
            throw new BaseException("Sku attribute does not exist"+etSkuAttributeRequest.getSkuAttribute());
        }
        baseProduct.setProductAttribute(etSkuAttributeRequest.getSkuAttribute());
        baseProduct.setProductAttributeName(map059.get(etSkuAttributeRequest.getSkuAttribute()));

        UpdateWrapper<BaseProduct> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("code", etSkuAttributeRequest.getSku());
        super.update(baseProduct, updateWrapper);
    }


    @Override
    public void measuringProduct(MeasuringProductRequest request) {
        log.info("??????sku?????????: {}", request);
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", request.getCode());
        //queryWrapper.eq("category", ProductConstant.SKU_NAME);
        if (super.count(queryWrapper) < 1) {
            throw new BaseException("Sku does not exist");
        }
        BigDecimal volume = new BigDecimal(request.getHeight()).multiply(new BigDecimal(request.getWidth()))
                .multiply(new BigDecimal(request.getLength()))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        String operationOn = request.getOperateOn();
        request.setOperateOn(null);
        BaseProduct baseProduct = BeanMapperUtil.map(request, BaseProduct.class);
        if (StringUtils.isNotEmpty(operationOn)) {
            try {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = df.parse(operationOn);
                baseProduct.setOperateOn(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        baseProduct.setCode(null);
        baseProduct.setWarehouseAcceptance(true);
        baseProduct.setVolume(volume);
        UpdateWrapper<BaseProduct> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("code", request.getCode());
        super.update(baseProduct, updateWrapper);
    }

    @Override
    public List<BaseProduct> listSku(BaseProduct baseProduct) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "code", baseProduct.getCode());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "category", baseProduct.getCategory());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.LIKE, "product_name", baseProduct.getProductName());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "seller_code", baseProduct.getSellerCode());
        QueryWrapperUtil.filter(queryWrapper, SqlKeyword.EQ, "product_attribute", baseProduct.getProductAttribute());
        queryWrapper.eq("is_active", true);
        queryWrapper.orderByDesc("create_time");
        return super.list(queryWrapper);
    }

    @Override
    public R<BaseProduct> getSku(BaseProduct baseProduct) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(baseProduct.getCode())) {
            queryWrapper.eq("code", baseProduct.getCode());
            queryWrapper.eq("is_active", true);
        } else {
            return R.failed("Valid sku Number is empty");
        }
        return R.ok(super.getOne(queryWrapper));
    }

    @Override
    public List<BaseProductExportDto> exportProduceList(BaseProductQueryDto queryDto, String len) {
        List<BaseProduct> list = selectBaseProductPage(queryDto);
        List<BaseProductExportDto> exportList = BeanMapperUtil.mapList(list, BaseProductExportDto.class);
        Iterator<BaseProductExportDto> iterable = exportList.iterator();
        int count = 1;
        while (iterable.hasNext()) {
            BaseProductExportDto b = iterable.next();
            b.setNo(count++);

            if("en".equals(len)){
                b.setWarehouseAcceptanceValue(b.getWarehouseAcceptance() == true ? "Yes" : "No");

            }else{
                b.setWarehouseAcceptanceValue(b.getWarehouseAcceptance() == true ? "???" : "???");

            }
        }

        return exportList;
    }

    /**
     * ????????????
     *
     * @param baseProductDto ??????
     * @return ??????
     */
    @Override
    @Transactional
    public int insertBaseProduct(BaseProductDto baseProductDto) {

        if (StringUtils.isEmpty(baseProductDto.getCode())) {
            if (ProductConstant.SKU_NAME.equals(baseProductDto.getCategory())) {
                String skuCode = "S" + baseProductDto.getSellerCode() + baseSerialNumberService.generateNumber(ProductConstant.SKU_NAME);
                baseProductDto.setCode(skuCode);
            } else {
                baseProductDto.setCode("WL" + baseProductDto.getSellerCode() + baseSerialNumberService.generateNumber("MATERIAL"));
            }
        } else {
            if (baseProductDto.getCode().length() < 2) {
                throw new CommonException("400", baseProductDto.getCode() + "The encoding length cannot be less than two characters");
            }
        }
        //?????? ????????????
        verifyBaseProduct(baseProductDto);
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", baseProductDto.getCode());
        if (super.count(queryWrapper) == 1) {
            throw new CommonException("400", baseProductDto.getCode() + "Duplicate encoding");
        }
        //????????????
        baseProductDto.setIsActive(true);
        //????????????????????????
        baseProductDto.setWarehouseAcceptance(false);

        BaseProduct baseProduct = BeanMapperUtil.map(baseProductDto, BaseProduct.class);
        //?????????????????????????????????
        baseProduct.setWarehouseAcceptance(true);

        //SKU????????????????????????
        baseProduct.setWarehouseAcceptance(false);
        if (StringUtils.isBlank(baseProduct.getSource())) {
            baseProduct.setSource(BaseMainEnum.NORMAL_IN.getCode());
        }
        baseProduct.setWeight(baseProduct.getInitWeight());
        baseProduct.setWidth(baseProduct.getInitWidth());
        baseProduct.setLength(baseProduct.getInitLength());
        baseProduct.setHeight(baseProduct.getInitHeight());
        baseProduct.setVolume(baseProduct.getInitVolume());
        ProductRequest productRequest = BeanMapperUtil.map(baseProductDto, ProductRequest.class);
        productRequest.setProductImage(baseProductDto.getProductImageBase64());
        productRequest.setProductDesc(baseProductDto.getProductDescription());
        /*R<ResponseVO> r = htpBasFeignService.createProduct(productRequest);
        //??????wms
        toWms(r);*/

        Tuple2<List<BaseProduct>, String> result = syncToWms(Collections.singletonList(Tuples.of(baseProduct, productRequest)));
        AssertUtil.isTrue(StringUtils.isBlank(result.getT2()), result.getT2());
        if (CollectionUtils.isNotEmpty(baseProductDto.getDocumentsFiles())) {
            AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(baseProductDto.getCode()).businessItemNo(null).fileList(baseProductDto.getDocumentsFiles()).attachmentTypeEnum(AttachmentTypeEnum.SKU_IMAGE).build();
            this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
        }
        return baseMapper.insert(baseProduct);
    }

    public Tuple2<List<BaseProduct>, String> syncToWms(List<Tuple2<BaseProduct, ProductRequest>> requestTupleList) {
        LoginUser loginUser=SecurityUtils.getLoginUser();
        List<CompletableFuture<BaseProduct>> futures = new ArrayList<>();

        requestTupleList.forEach(tuple -> {

            BaseProduct baseProduct = tuple.getT1();
            ProductRequest productRequest = tuple.getT2();
            CompletableFuture<BaseProduct> future = CompletableFuture.supplyAsync(() -> {
                R<ResponseVO> r = htpBasFeignService.createProduct(productRequest);
                R.getDataAndException(r);
                //??????wms
                toWms(r);
                log.info("?????????WMS???SKU???????????? {} ?????? {}", productRequest, JSONObject.toJSONString(r));
                return baseProduct;
            }, ckThreadPool).thenApplyAsync(x -> {
                // ?????????sku
                if (StringUtils.isBlank(x.getCategory()) || !ProductConstant.SKU_NAME.equals(x.getCategory())) {
                    log.info("?????????CK1??????SKU????????????????????? {}", x);
                    return x;
                }

                CkSkuCreateDTO ckSkuCreateDTO = CkSkuCreateDTO.createCkSkuCreateDTO(x);
                //??????????????????
                //TODO ????????????wms
                HttpRequestSyncDTO httpRequestDto = new HttpRequestSyncDTO();
                httpRequestDto.setRemoteTypeEnum(RemoteConstant.RemoteTypeEnum.SKU_CREATE);
                httpRequestDto.setMethod(HttpMethod.POST);
                httpRequestDto.setBinary(false);
                httpRequestDto.setUri("${" + DomainEnum.Ck1OpenAPIDomain.name() + "}/v1/merchantSkus");
                httpRequestDto.setBody(ckSkuCreateDTO);
                if (loginUser!=null){
                    httpRequestDto.setUserName(loginUser.getUsername());
                }
                R<HttpResponseVO> httpResponseVOR = htpRmiFeignService.rmiSync(httpRequestDto);
                R.getDataAndException(httpResponseVOR);
               /* R<HttpResponseVO> rmiR = htpRmiFeignService.rmi(httpRequestDto);
                log.info("?????????CK1???SKU???????????? {} ?????? {}", httpRequestDto, JSONObject.toJSONString(rmiR));
                HttpResponseVO dataAndException = R.getDataAndException(rmiR);
                dataAndException.checkStatus();*/
                return baseProduct;
            }, ckThreadPool);
            futures.add(future);
        });
        List<BaseProduct> canAddList = new ArrayList<>();
        StringBuilder hasError = new StringBuilder("");
        AtomicInteger index = new AtomicInteger(1);
        futures.forEach(x -> {
            BaseProduct baseProduct = null;
            int indexThis = index.getAndIncrement();
            try {
                baseProduct = x.get();
            } catch (Exception e) {
                log.error("???SKU??????????????????", e);
                hasError.append("???").append(indexThis).append("?????????????????????:").append(e.getMessage()).append("\n");
            }
            if (Objects.nonNull(baseProduct))
                canAddList.add(baseProduct);
        });
        return Tuples.of(canAddList, hasError.toString());
    }

    @Override
    public List<BaseProduct> BatchInsertBaseProduct(List<BaseProductDto> baseProductDtos) {
        baseProductDtos.forEach(o -> {
            if (StringUtils.isEmpty(o.getCode())) {
                if (ProductConstant.SKU_NAME.equals(o.getCategory())) {
                    String skuCode = "S" + o.getSellerCode() + baseSerialNumberService.generateNumber(ProductConstant.SKU_NAME);
                    o.setCode(skuCode);
                    o.setCategoryCode("SKUtype");
                } else {
                    o.setCode("WL" + o.getSellerCode() + baseSerialNumberService.generateNumber("MATERIAL"));
                    o.setCategoryCode("packagetype");
                }
            } else {
                if (o.getCode().length() < 2) {
                    throw new CommonException("400", o.getCategory() + "The encoding length cannot be less than two characters");
                }
            }
            //?????? ????????????
            verifyBaseProduct(o);
            QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("code", o.getCode());
            if (super.count(queryWrapper) == 1) {
                throw new BaseException(o.getCategory() + "Duplicate encoding");
            }
            //????????????
            o.setIsActive(true);
            o.setSource(BaseMainEnum.COLLECT_IN.getCode());
            //o.setSource("02");
            //????????????????????????
            o.setWarehouseAcceptance(false);
        });
        List<BaseProduct> baseProducts = BeanMapperUtil.mapList(baseProductDtos, BaseProduct.class);
        List<Tuple2<BaseProduct, ProductRequest>> waitSyncList = new ArrayList<>();
        baseProducts.forEach(o -> {
            //?????????????????????????????????
            o.setWarehouseAcceptance(true);
            //SKU????????????????????????
            o.setWarehouseAcceptance(false);
            o.setWeight(o.getInitWeight());
            o.setWidth(o.getInitWidth());
            o.setLength(o.getInitLength());
            o.setHeight(o.getInitHeight());
            o.setVolume(o.getInitVolume());
            ProductRequest productRequest = BeanMapperUtil.map(o, ProductRequest.class);
            productRequest.setProductDesc(o.getProductDescription());
            waitSyncList.add(Tuples.of(o, productRequest));
        });
        Tuple2<List<BaseProduct>, String> result = syncToWms(waitSyncList);
        super.saveBatch(result.getT1());
        AssertUtil.isTrue(StringUtils.isBlank(result.getT2()), result.getT2());
        return baseProducts;
    }
    @Override
    public void rePushBaseProduct(String sku) {
        if (StringUtils.isBlank(sku)) return;
        log.info("?????????sku???{}", sku);
        BaseProduct baseProduct = baseMapper.selectOne(Wrappers.<BaseProduct>lambdaQuery().eq(BaseProduct::getCode, sku));
        if (baseProduct != null) {
            BaseProductDto baseProductDto = new BaseProductDto();
            BeanUtils.copyProperties(baseProduct, baseProductDto);
            BasAttachmentQueryDTO basAttachmentQueryDTO = new BasAttachmentQueryDTO();
            basAttachmentQueryDTO.setBusinessNo(sku);
            basAttachmentQueryDTO.setAttachmentType("SKU??????");
            R<List<BasAttachment>> list = this.remoteAttachmentService.list(basAttachmentQueryDTO);
            List<BasAttachment> skuImage = R.getDataAndException(list);
            List<AttachmentDataDTO> attachmentList = skuImage.stream().map(x -> {
                AttachmentDataDTO attachmentDataDTO = new AttachmentDataDTO();
                BeanUtils.copyProperties(x, attachmentDataDTO);
                return attachmentDataDTO;
            }).collect(Collectors.toList());
            baseProductDto.setDocumentsFiles(attachmentList);
            try {
                log.info("?????????sku???{}---{}", sku, JSONObject.toJSONString(baseProductDto));
                this.updateBaseProduct(baseProductDto);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * ????????????
     *
     * @param baseProductDto ??????
     * @return ??????
     */
    @Override
    public int updateBaseProduct(BaseProductDto baseProductDto) throws IllegalAccessException {
        BaseProduct bp = super.getById(baseProductDto.getId());
        if (bp.getCategory().equals(ProductConstant.SKU_NAME)) {
            baseProductDto.setCategory(ProductConstant.SKU_NAME);
        }
        verifyBaseProduct(baseProductDto);
        ProductRequest productRequest = BeanMapperUtil.map(baseProductDto, ProductRequest.class);
        BaseProduct baseProduct = super.getById(baseProductDto.getId());
        ObjectUtil.fillNull(productRequest, baseProduct);
        AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(baseProduct.getCode()).businessItemNo(null).fileList(baseProductDto.getDocumentsFiles()).attachmentTypeEnum(AttachmentTypeEnum.SKU_IMAGE).build();
        this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
        productRequest.setProductImage(baseProductDto.getProductImageBase64());
        productRequest.setProductDesc(baseProductDto.getProductDescription());
        R<ResponseVO> r = htpBasFeignService.createProduct(productRequest);
        //??????wms
        toWms(r);
        return baseMapper.updateById(baseProductDto);
    }

    /**
     * ??????????????????
     *
     * @param ids ?????????????????????ID
     * @return ??????
     */
    @Override
    public Boolean deleteBaseProductByIds(List<Long> ids) throws IllegalAccessException {
        //????????????WMS
        for (Long id : ids) {
            ProductRequest productRequest = new ProductRequest();
            productRequest.setIsActive(false);
            BaseProduct baseProduct = super.getById(id);
            ObjectUtil.fillNull(productRequest, baseProduct);
            R<ResponseVO> r = htpBasFeignService.createProduct(productRequest);
            //??????wms
            toWms(r);
        }
        UpdateWrapper<BaseProduct> updateWrapper = new UpdateWrapper();
        updateWrapper.in("id", ids);
        updateWrapper.set("is_active", false);
        return super.update(updateWrapper);
    }

    /**
     * ??????????????????
     *
     * @param id ??????ID
     * @return ??????
     */
    @Override
    public int deleteBaseProductById(String id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public R<Boolean> checkSkuValidToDelivery(BaseProduct baseProduct) {
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", baseProduct.getCode());
        queryWrapper.eq("is_active", true);
        //???????????????SKU
        int count = super.count(queryWrapper);
        R r = new R();
        if (count == 1) {
            r.setData(true);
            r.setCode(200);
            r.setMsg("success");
        } else {
            r.setData(false);
            r.setCode(-200);
            r.setMsg("SKU does not exist");
        }
        return r;
    }

    private List<BaseProduct> queryConditionList(BaseProductConditionQueryDto conditionQueryDto) {

        List<String> skuList = conditionQueryDto.getSkus();

        if (skuList == null || skuList.size() == 0 ) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<BaseProduct> queryWrapper = Wrappers.lambdaQuery();
        if (null != conditionQueryDto.getWarehouseCode()) {
            queryWrapper.eq(BaseProduct::getWarehouseCode, conditionQueryDto.getWarehouseCode());
        }
        if (null != conditionQueryDto.getSellerCode()) {
            queryWrapper.eq(BaseProduct::getSellerCode, conditionQueryDto.getSellerCode());
        }
        if (null != conditionQueryDto.getSource()) {
            queryWrapper.eq(BaseProduct::getSource, conditionQueryDto.getSource());
        }
        queryWrapper.in(BaseProduct::getCode, conditionQueryDto.getSkus());
        return this.list(queryWrapper);
    }

    @Override
    public List<String> listProductAttribute(BaseProductConditionQueryDto conditionQueryDto) {
        List<BaseProduct> list = this.queryConditionList(conditionQueryDto);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(BaseProduct::getProductAttribute).collect(Collectors.toList());
    }

    @Override
    public String importMultipleTicket(MultipartFile file) {
        try {
            InputStream inputStream = file.getInputStream();
            ExcelUtil<BasProductMultipleTicketDTO> util = new ExcelUtil<>(BasProductMultipleTicketDTO.class);
            List<BasProductMultipleTicketDTO> basSellerMultipleTicketDTOS = util.importExcel(inputStream);
            if (CollectionUtils.isEmpty(basSellerMultipleTicketDTOS))
                return "The import data is empty";
            List<BasProductMultipleTicketDTO> updateList = basSellerMultipleTicketDTOS.parallelStream()
                    .filter(x -> StringUtils.isNotBlank(x.getSellerCode()) && StringUtils.isNotBlank(x.getMultipleTicketFlagStr()))
                    .collect(Collectors.toList());
            List<String> sellerCodeList = updateList.parallelStream().map(BasProductMultipleTicketDTO::getSellerCode).distinct().collect(Collectors.toList());
            List<String> skuList = updateList.parallelStream().map(BasProductMultipleTicketDTO::getSku).distinct().collect(Collectors.toList());
            if (CollectionUtils.isEmpty(sellerCodeList) || CollectionUtils.isEmpty(skuList))
                return "Exception in importing data content";

            List<BaseProduct> canUpdateSellerCodeList = baseMapper.selectList(Wrappers.<BaseProduct>lambdaQuery()
                    .in(BaseProduct::getSellerCode, sellerCodeList)
                    .in(BaseProduct::getCode, skuList)
                    .select(BaseProduct::getSellerCode, BaseProduct::getId, BaseProduct::getCode));
            // ????????? ??????sku
            Map<String, List<BaseProduct>> canUpdateSellerCodeMap = canUpdateSellerCodeList.stream().collect(Collectors.groupingBy(BaseProduct::getSellerCode));
            sellerCodeList.clear();
            skuList.clear();
            List<String> errorMsg = new ArrayList<>();
            List<BaseProduct> updateResultList = new ArrayList<>();
            // ???????????????
            Map<String, List<BasProductMultipleTicketDTO>> updateInfoMap = updateList.stream().collect(Collectors.groupingBy(BasProductMultipleTicketDTO::getSellerCode));
            updateInfoMap.forEach((sellerCode, infoList) -> {
                List<BaseProduct> baseProducts = canUpdateSellerCodeMap.get(sellerCode);
                if (CollectionUtils.isEmpty(baseProducts)) {
                    errorMsg.add("user does not exist???" + sellerCode);
                    return;
                }

                Map<String, BaseProduct> skuInfoMap = baseProducts.stream().collect(Collectors.toMap(BaseProduct::getCode, x -> x));
                infoList.forEach(x -> {
                    BaseProduct baseProduct = skuInfoMap.get(x.getSku());
                    if (null != baseProduct) {
                        BaseProduct baseProduct1 = new BaseProduct();
                        baseProduct1.setId(baseProduct.getId());
                        baseProduct1.setMultipleTicketFlag(x.getMultipleTicketFlag());
                        updateResultList.add(baseProduct1);
                    } else {
                        errorMsg.add("user " + sellerCode + " No SKU exists ???" + x.getSku());
                    }
                });
            });
            if (CollectionUtils.isNotEmpty(updateResultList))
                this.updateBatchById(updateResultList);
            return StringUtils.join(errorMsg, ";");
        } catch (Exception e) {
            log.error("", e);
            return "Exception in importing data";
        }
    }

    @Override
    public List<BaseProduct> queryProductList(BaseProductConditionQueryDto conditionQueryDto) {
        List<BaseProduct> list = this.queryConditionList(conditionQueryDto);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list;
    }

    private void verifyBaseProduct(BaseProductDto baseProductDto) {

        //?????????????????????????????????

        if (ProductConstant.SKU_NAME.equals(baseProductDto.getCategory())) {
            if (StringUtils.isNotEmpty(baseProductDto.getProductAttribute())) {
                if ("Battery".equals(baseProductDto.getProductAttribute())) {
                    if (StringUtils.isEmpty(baseProductDto.getElectrifiedMode()) || StringUtils.isEmpty(baseProductDto.getBatteryPackaging())) {
                        throw new BaseException("Not filled in electric information");
                    }
                } else {
                    if (StringUtils.isNotEmpty(baseProductDto.getElectrifiedMode()) || StringUtils.isNotEmpty(baseProductDto.getBatteryPackaging())) {
                        throw new BaseException("Please do not fill in electric information");
                    }
                }

                if (baseProductDto.getHavePackingMaterial() == true) {
                    if (StringUtils.isEmpty(baseProductDto.getBindCode())) {
                        throw new BaseException("Not filled in Attached Packaging materials");
                    }
                } else {
                    if (StringUtils.isNotEmpty(baseProductDto.getBindCode())) {
                        throw new BaseException("Please do not fill in  Attached Packaging material");
                    }
                }
            }
        }
    }

    private void verifyBaseProductRequired(List<BaseProductImportDto> list, String sellerCode) {

        StringBuilder s1 = new StringBuilder("");
        BasePacking basePacking = new BasePacking();
        BaseProduct baseProduct = new BaseProduct();
        //????????????
        QueryWrapper<BaseProduct> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", ProductConstant.BC_NAME);
        queryWrapper.eq("seller_code", sellerCode);
        //??????????????????
        Map<String, String> typeMap = basSubFeignService.getSubList(BaseMainEnum.SKU_TYPE.getCode()).getData();
        Map<String, String> eleMap = basSubFeignService.getSubList(BaseMainEnum.SKU_ELE.getCode()).getData();
        Map<String, String> elePackageMap = basSubFeignService.getSubList(BaseMainEnum.SKU_ELEPACKAGE.getCode()).getData();
        int count = 1;
        for (BaseProductImportDto b : list) {
            StringBuilder s = new StringBuilder("");
            if (StringUtils.isEmpty(b.getCode())) {
                String skuCode = "S" + sellerCode + baseSerialNumberService.generateNumber(ProductConstant.SKU_NAME);
                b.setCode(skuCode);
            } else {
//                if (!Pattern.matches(regex, b.getCode())) {
//                    s.append("SKU?????????????????????????????????????????????????????????");
//                }
                if (b.getCode().length() < 2) {

                    s.append("SKU????????????????????????");
                }
                QueryWrapper<BaseProduct> queryWrapper1 = new QueryWrapper<>();
                queryWrapper1.eq("code", b.getCode());
                if (super.count(queryWrapper1) > 0) {
                    s.append(b.getCode() + "??????????????????,");
                }

            }
            if (StringUtils.isEmpty(b.getProductName())) {

                s.append("???????????????????????????,");
            }
            if (StringUtils.isEmpty(b.getProductAttributeName())) {

                s.append("?????????????????????,");
            } else {
                if ("??????".equals(b.getProductAttributeName())) {
                    if (StringUtils.isEmpty(b.getElectrifiedModeName()) || StringUtils.isEmpty(b.getBatteryPackagingName())) {
                        s.append("???????????????????????????,");
                    } else {

                        if (!eleMap.isEmpty()) {
                            if (eleMap.containsKey(b.getElectrifiedModeName())) {
                                b.setElectrifiedMode(eleMap.get(b.getElectrifiedModeName()));
                            } else {
                                s.append("??????????????????????????????");
                            }
                        } else {
                            s.append("??????????????????????????????");
                        }

                        if (!elePackageMap.isEmpty()) {
                            if (elePackageMap.containsKey(b.getBatteryPackagingName())) {
                                b.setBatteryPackaging(elePackageMap.get(b.getBatteryPackagingName()));
                            } else {
                                s.append("??????????????????????????????");
                            }
                        } else {
                            s.append("??????????????????????????????");
                        }
                    }
                } else {
                    if (!StringUtils.isEmpty(b.getElectrifiedModeName()) || !StringUtils.isEmpty(b.getBatteryPackagingName())) {
                        s.append("????????????????????????,");
                    }
                }
                if (!typeMap.isEmpty()) {
                    if (typeMap.containsKey(b.getProductAttributeName())) {
                        b.setProductAttribute(typeMap.get(b.getProductAttributeName()));
                    } else {
                        s.append("??????????????????????????????");
                    }
                } else {
                    s.append("??????????????????????????????");
                }
            }
            if (StringUtils.isEmpty(b.getHavePackingMaterialName())) {
                s.append("???????????????????????????,");
            } else {
                if ("???".equals(b.getHavePackingMaterialName())) {
                    if (StringUtils.isEmpty(b.getBindCode())) {
                        s.append("????????????????????????");
                    } else {
                        queryWrapper.eq("code", b.getBindCode());
                        baseProduct = super.getOne(queryWrapper);
                        if (baseProduct != null) {
                            b.setBindCodeName(baseProduct.getProductName());
                        } else {
                            s.append("??????????????????????????????");
                        }
                    }

                } else {
                    if (StringUtils.isNotEmpty(b.getBindCode())) {
                        s.append("???????????????????????????");
                    }
                }
            }
            if (StringUtils.isEmpty(b.getSuggestPackingMaterial())) {
//                s.append("?????????????????????,");
            } else {
                basePacking.setPackingMaterialType(b.getSuggestPackingMaterial());
                List<BasePackingDto> basePackings = basePackingService.selectBasePackingList(basePacking);
                if (CollectionUtils.isNotEmpty(basePackings)) {
                    b.setSuggestPackingMaterialCode(basePackings.get(0).getPackageMaterialCode());
                    b.setPriceRange(basePackings.get(0).getPriceRange());
                } else {
                    s.append("??????????????????????????????,");
                }
            }
            if (b.getInitLength() == null) {
                s.append("????????????,");
            }
            if (b.getInitHeight() == null) {
                s.append("????????????,");
            }
            if (b.getInitWeight() == null) {
                s.append("???????????????,");
            }
            if (b.getInitWidth() == null) {
                s.append("????????????,");
            }
            if (b.getInitWidth() == null) {
                s.append("????????????,");
            }
            if (b.getDeclaredValue() == null) {
                s.append("?????????????????????,");
            }
            if (StringUtils.isEmpty(b.getProductDescription())) {
                s.append("?????????????????????,");
            } else {
                if (b.getProductDescription().length() > 40) {
                    s.append("?????????????????????????????????,");
                }
            }
            if (!s.toString().equals("")) {
                s1.append("<br/>???" + count + "????????????" + s);
            }
            count++;
        }

        Map<String, Integer> map = new HashMap<>();
        for (BaseProductImportDto b : list) {
            if (map.containsKey(b.getCode())) {
                map.put(b.getCode(), map.get(b.getCode()) + 1);
            } else {
                map.put(b.getCode(), 1);
            }
        }
        if (map.size() != list.size()) {
            s1.append("<br/>???????????????sku?????????:");
            for (Object key : map.keySet()) {
                Integer value = (Integer) map.get(key);
                if (value > 1) {
                    s1.append(key + ",\n");
                }
            }

        }
        if (!s1.toString().equals("")) {
            throw new RuntimeException(s1.toString());
        }
    }

    private void toWms(R<ResponseVO> r) {
        if (r == null) {
            throw new RuntimeException("Wms service call failed");
        }
        if (r.getData() == null) {
            //throw new BaseException("wms??????????????????");
        } else {
            if (r.getData().getSuccess() == null) {
                if (r.getData().getErrors() != null) {
                    throw new RuntimeException("Failed to transmit wms" + r.getData().getErrors());
                }
            } else {
                if (!r.getData().getSuccess()) {
                    throw new RuntimeException("Failed to transmit wms" + r.getData().getMessage());
                }
            }
        }
    }


}

