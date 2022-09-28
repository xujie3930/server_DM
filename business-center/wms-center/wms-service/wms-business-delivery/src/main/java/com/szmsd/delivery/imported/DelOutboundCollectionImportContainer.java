package com.szmsd.delivery.imported;

import com.szmsd.bas.api.domain.vo.BasRegionSelectListVO;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.enums.DelOutboundConstant;
import com.szmsd.delivery.enums.DelOutboundOrderTypeEnum;
import com.szmsd.inventory.domain.vo.InventoryAvailableListVO;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhangyuyuan
 * @date 2021-04-09 21:49
 */
public class DelOutboundCollectionImportContainer extends DelOutboundCollectionImportContext {

    private final List<DelOutboundCollectionDetailImportDto2> detailList;
    private final Map<Integer, List<DelOutboundCollectionDetailImportDto2>> detailMapList;
    private final ImportValidationData importValidationData;
    private final String sellerCode;
    private Map<String, BaseProduct> productMap;


    public DelOutboundCollectionImportContainer(List<DelOutboundCollectionImportDto> dataList,
                                                List<BasRegionSelectListVO> countryList,
                                                List<DelOutboundCollectionDetailImportDto2> detailList,
                                                ImportValidationData importValidationData,
                                                String sellerCode, Map<String, BaseProduct> productMap) {
        super(dataList, countryList);
        this.detailList = detailList;
        this.detailMapList = this.detailToMapList();
        this.importValidationData = importValidationData;
        this.sellerCode = sellerCode;
        this.productMap = productMap;
    }

    public List<DelOutboundDto> get() {
        List<DelOutboundCollectionImportDto> dataList = super.getDataList();
        List<DelOutboundDto> outboundDtoList = new ArrayList<>();
        for (DelOutboundCollectionImportDto dto : dataList) {
            DelOutboundDto outboundDto = new DelOutboundDto();
            outboundDto.setCustomCode(this.sellerCode);
            outboundDto.setOrderType(DelOutboundOrderTypeEnum.COLLECTION.getCode());
            outboundDto.setSellerCode(this.sellerCode);
            outboundDto.setSourceType(DelOutboundConstant.SOURCE_TYPE_IMP);


            outboundDto.setWarehouseCode(dto.getWarehouseCode());
            outboundDto.setShipmentRule(dto.getShipmentRule());
            outboundDto.setIoss(dto.getIoss());
            outboundDto.setCodAmount(dto.getCodAmount());

            outboundDto.setWeight(dto.getWeight());
            outboundDto.setLength(dto.getLength());
            outboundDto.setWidth(dto.getWidth());
            outboundDto.setHeight(dto.getHeight());
            outboundDto.setAddress(this.buildAddress(dto));
            outboundDto.setDetails(this.buildDetails(dto));
            outboundDto.setRefNo(dto.getRefNo());
            outboundDto.setRemark(dto.getRemark());

            outboundDtoList.add(outboundDto);
        }
        return outboundDtoList;
    }

    public DelOutboundAddressDto buildAddress(DelOutboundCollectionImportDto dto) {
        DelOutboundAddressDto address = new DelOutboundAddressDto();
        address.setConsignee(dto.getConsignee());
        address.setCountryCode(super.getCountryCodeCache(dto.getCountry(), this.countryCache, this.countryEnCache));
        address.setCountry(super.getCountryNameCache(dto.getCountry(), this.countryCodeCache, this.countryCache, this.countryEnCache));
        address.setStateOrProvince(dto.getStateOrProvince());
        address.setCity(dto.getCity());
        address.setStreet1(dto.getStreet1());
        address.setStreet2(dto.getStreet2());
        address.setPostCode(super.stringNumber(dto.getPostCode()));
        address.setPhoneNo(super.stringNumber(dto.getPhoneNo()));
        address.setEmail(dto.getEmail());
        return address;
    }

    public List<DelOutboundDetailDto> buildDetails(DelOutboundCollectionImportDto dto) {
        List<DelOutboundDetailDto> details = new ArrayList<>();
        List<DelOutboundCollectionDetailImportDto2> list = this.detailMapList.get(dto.getSort());
        if (CollectionUtils.isEmpty(list)) {
            return details;
        }
        for (DelOutboundCollectionDetailImportDto2 dto2 : list) {
            DelOutboundDetailDto detail = new DelOutboundDetailDto();


            detail.setSku(dto2.getCode());
            if(productMap !=null && productMap.containsKey(detail.getSku())){
                BaseProduct product =  productMap.get(detail.getSku());
                detail.setProductName(product.getProductName());
                detail.setProductNameChinese(product.getProductNameChinese());
                detail.setHsCode(product.getHsCode());
                detail.setBatteryPackaging(product.getBatteryPackaging());
                detail.setElectrifiedMode(product.getElectrifiedMode());
                detail.setDeclaredValue(product.getDeclaredValue());
                detail.setProductAttribute(product.getProductAttribute());
                detail.setRemark(product.getProductDescription());
            }
            detail.setQty(Long.valueOf(dto2.getQty()));
            details.add(detail);
        }
        return details;
    }

    private Map<Integer, List<DelOutboundCollectionDetailImportDto2>> detailToMapList() {
        if (null == this.detailList) {
            return Collections.emptyMap();
        }
        return this.detailList.stream().collect(Collectors.groupingBy(DelOutboundCollectionDetailImportDto2::getSort));
    }
}
