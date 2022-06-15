package com.szmsd.ec.shopify.controller;

import com.alibaba.fastjson.JSONArray;
import com.szmsd.bas.api.feign.BasSellerShopifyPermissionFeignService;
import com.szmsd.bas.domain.BasSellerShopifyPermission;
import com.szmsd.common.core.constant.Constants;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.ec.dto.ShopifyOrderDTO;
import com.szmsd.ec.shopify.service.ShopifyOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author hyx
 * @version V1.0
 * @ClassName:ShopifyOrder
 * @Description: oms_shopify_order shopify订单表Controller
 * @date 2021-04-14 13:58:31
 */
@Api(tags = "shopify订单表")
@RestController
@RequestMapping("/shopifyOrder")
public class ShopifyOrderController extends BaseController {

    @Autowired
    private ShopifyOrderService shopifyOrderService;
    @Autowired
    private BasSellerShopifyPermissionFeignService basSellerShopifyPermissionFeignService;
    
//    @Resource
//    private CurrUserFeignService currUserFeignService;

    @ApiOperation(value = "根据参数获取分页列表")
    @GetMapping("/listPage")
    public TableDataInfo listPage(ShopifyOrderDTO queryDTO) {
        startPage();
//        CacheEmployeeDTO vipLoginUserDTO = currUserFeignService.getCompanyVipUser();
//        if(vipLoginUserDTO != null && vipLoginUserDTO.getCustomerId() != null){
//            queryDTO.setCustomerId(vipLoginUserDTO.getCustomerId());
//        }
//        if(vipLoginUserDTO != null &&!StringUtils.equals(vipLoginUserDTO.getCheckStatus(),"2")){
//            PageResultUtils<ShopifyOrderDTO> pru= PageResultUtils.processPage(PageUtils.build(ShopifyOrderDTO.class));
//            pru.setCode(222);
//            pru.setMessage("请先提交资料并耐心等待系统审核通过");
//            return pru;
//        }
        List<ShopifyOrderDTO> orderDTOS = this.shopifyOrderService.listPage(queryDTO);
        return getDataTable(orderDTOS);
    }

    @ApiOperation("手动修改订单(购买服务调用)")
    @PostMapping("/updateOrderList")
    public R updateOrderList(@RequestBody List<ShopifyOrderDTO> tDTOList) {
        for (int i = 0; i < tDTOList.size(); i++) {
            this.shopifyOrderService.updateOrder(tDTOList.get(i));
        }
        return R.ok();
    }

    @ApiOperation(value = "获取Shopify订单各状态合计数量")
    @GetMapping("/getCountByStatus")
    public R getCountByStatus(ShopifyOrderDTO queryDTO) {
//        CacheEmployeeDTO vipLoginUserDTO = currUserFeignService.getCompanyVipUser();
//        if(vipLoginUserDTO != null && vipLoginUserDTO.getCustomerId() != null){
//            queryDTO.setCustomerId(vipLoginUserDTO.getCustomerId());
//        }
        return R.ok(shopifyOrderService.getCountByStatus(queryDTO));
    }

    @ApiOperation(value = "根据店铺名称获取Shopify仓库列表")
    @GetMapping("/getShopWarehouseList")
    public R getShopWarehouseList(@RequestParam(required = false) String shopName){
        JSONArray jsonArray = new JSONArray();
        BasSellerShopifyPermission permission = new BasSellerShopifyPermission();
        if (StringUtils.isNotBlank(shopName)) {
            permission.setShop(shopName);
        }
        permission.setSellerCode(SecurityUtils.getLoginUser().getSellerCode());
        log.info("获取店铺仓库, 当前登录客户：{}", SecurityUtils.getLoginUser().getSellerCode());
        R<List<BasSellerShopifyPermission>> shopList = basSellerShopifyPermissionFeignService.list(permission);
        if (Constants.SUCCESS.equals(shopList.getCode()) && shopList.getData() != null) {
            List<BasSellerShopifyPermission> shopListData = shopList.getData();
            shopListData.forEach(item -> {
                JSONArray locations = shopifyOrderService.getShopLocations(item.getShop(), item.getAccessToken());
                if (CollectionUtils.isNotEmpty(locations)) {
                    jsonArray.addAll(locations);
                }
            });
        }
        return R.ok(jsonArray);
    }

}