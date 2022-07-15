package com.szmsd.delivery;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.delivery.domain.DelOutbound;
import org.junit.Test;

import java.util.function.Predicate;

public class TestWrappers {

    @Test
    public void testWrappers() {
        /*TableInfo tableInfo = new TableInfo(DelOutbound.class);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        TableInfoHelper.initTableFields(DelOutbound.class, globalConfig, tableInfo);
        LambdaUtils.installCache(tableInfo);*/

        TableInfo tableInfo = TableInfoHelper.initTableInfo(null, DelOutbound.class);
        LambdaUtils.installCache(tableInfo);

        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        // queryWrapper.select(DelOutbound::getId, DelOutbound::getOrderNo, DelOutbound::getOrderType);
        queryWrapper.select(DelOutbound.class, new Predicate<TableFieldInfo>() {
            @Override
            public boolean test(TableFieldInfo tableFieldInfo) {
                if ("id".equals(tableFieldInfo.getColumn())) {
                    return true;
                }
                return false;
            }
        });
        queryWrapper.eq(DelOutbound::getOrderNo, "CK123456");
        queryWrapper.in(DelOutbound::getOrderType, "AAA", "BBB").or(qw -> {
            qw.eq(DelOutbound::getId, 3);
            qw.eq(DelOutbound::getAmount, 4);
        });

        queryWrapper.and(w -> {
            w.eq(DelOutbound::getTrackingNo, "aaaa").or().eq(DelOutbound::getRefNo, "aaaa");
            w.or().eq(DelOutbound::getOrderNo, "aaaa");
        });

        queryWrapper.and(w -> {
            w.or().eq(DelOutbound::getOrderNo, "aaaa");
        });

        queryWrapper.and(w -> {
            w.eq(DelOutbound::getTrackingNo, "aaaa").or().eq(DelOutbound::getRefNo, "aaaa");
        });

        System.out.println(queryWrapper);
        System.out.println(queryWrapper.getCustomSqlSegment());

        System.out.println(queryWrapper.getSqlSelect());

        System.out.println(queryWrapper.getSqlSegment());
        System.out.println(JSON.toJSONString(queryWrapper.getParamNameValuePairs()));
    }
}
