package com.szmsd.chargerules;

import com.alibaba.fastjson.JSON;
import com.szmsd.bas.dto.BaseProductMeasureDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = BusinessChargeRulesApplication.class)
public class CollectingAndThenTest {

    @Test
    public void a() {

        List<SkuProductVO> skuProductVOList = new ArrayList<>();

        SkuProductVO skuProductVO = new SkuProductVO();
        skuProductVO.setSku("A01");
        skuProductVO.setName("产品名称01");
        skuProductVO.setWareCode("NJ");

        SkuProductVO skuProductVO1 = new SkuProductVO();
        skuProductVO1.setSku("A01");
        skuProductVO1.setName("产品名称01");
        skuProductVO1.setWareCode("NA");

        SkuProductVO skuProductVO2 = new SkuProductVO();
        skuProductVO2.setSku("A02");
        skuProductVO2.setName("产品名称02");
        skuProductVO2.setWareCode("NJ");

        skuProductVOList.add(skuProductVO);
        skuProductVOList.add(skuProductVO1);
        skuProductVOList.add(skuProductVO2);


        Map<String,SkuProductVO> map = skuProductVOList.stream().collect(Collectors.groupingBy(SkuProductVO::getSku, Collectors.collectingAndThen(Collectors.toList(), e -> e.get(0))));

        System.out.println(JSON.toJSONString(map));

    }
}
