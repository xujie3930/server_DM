package com.szmsd.chargerules.service;

import com.szmsd.chargerules.dto.CreateProductDTO;
import com.szmsd.chargerules.dto.FreightCalculationDTO;
import com.szmsd.chargerules.dto.PricedProductQueryDTO;
import com.szmsd.chargerules.dto.UpdateProductDTO;
import com.szmsd.chargerules.vo.FreightCalculationVO;
import com.szmsd.chargerules.vo.PricedProductInfoVO;
import com.szmsd.common.core.utils.FileStream;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.http.vo.KeyValuePair;
import com.szmsd.http.vo.PricedProduct;

import java.util.List;
import java.util.Map;

public interface IPricedProductService {

    List<FreightCalculationVO> pricedProducts(FreightCalculationDTO freightCalculationDTO);

    List<KeyValuePair> keyValuePairs();

    TableDataInfo<PricedProduct> selectPage(PricedProductQueryDTO pricedProductQueryDTO);

    void create(CreateProductDTO createProductDTO);

    PricedProductInfoVO getInfo(String productCode);

    void update(UpdateProductDTO updateProductDTO);

    FileStream exportFile(List<String> codes);

    Map selectProductCode(String productCode);
}
