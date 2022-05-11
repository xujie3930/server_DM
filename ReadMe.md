>版本更新历史
---
## V21-11-24

##### 实现客户报价功能，在匹配不到客户的具体报价的情况下，根据客户所属的类型，去匹配报价。
1.  用户添加报价用户类型
2.  业务费用规则、仓租费规则，可以支持绑定用户类型/指定用户id + 折扣
    * 优先取用户报价，没有就取用户类型的规则，没有就取全局

## V22-05-06 UPDATE

## V22-05-11 UPDATE
     1.SKU属性
     2.srm
     3.包裹查询、轨迹分析和退件那些查询功能
``` sql
ALTER TABLE  `inbound_tracking` 
ADD INDEX `idx_order_no`(`order_no`) USING BTREE COMMENT '订单号';
--2022-0505 refno
ALTER TABLE  `return_express_detail` 
ADD COLUMN `ref_no` varchar(200) DEFAULT NULL COMMENT 'refno(来自出库单)';
```
```yml
-- HTTP
        carrier-service:
          shipment-order:
            pickup-package-services: /api/v1/carrierService/pickupPackage/services
            pickup-package-create: /api/v1/carrierService/pickupPackage/create
```