package com.szmsd.bas.service.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.szmsd.bas.api.feign.BasTranslateFeignService;
import com.szmsd.bas.domain.BasMeteringConfig;
import com.szmsd.bas.domain.BasMeteringConfigData;
import com.szmsd.bas.dto.BasMeteringConfigDto;
import com.szmsd.bas.mapper.BasMeteringConfigDataMapper;
import com.szmsd.bas.mapper.BasMeteringConfigMapper;
import com.szmsd.bas.service.IBasMeteringConfigService;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class IBasMeteringConfigServiceImpl implements IBasMeteringConfigService {
    @Autowired
    private BasMeteringConfigMapper basMeteringConfigMapper;
    @Autowired
    private BasMeteringConfigDataMapper basMeteringConfigDataMapper;
    @Autowired
    private BasTranslateFeignService basTranslateFeignService;

    @Override
    public List<BasMeteringConfig> selectList(BasMeteringConfigDto basMeteringConfigDto) {
        if (basMeteringConfigDto.getCustomerCode()!=null && !basMeteringConfigDto.getCustomerCode().equals("")){
            basMeteringConfigDto.setCustomerCode(new StringBuilder("%").append(basMeteringConfigDto.getCustomerCode()).append("%").toString());
        }
        return basMeteringConfigMapper.selectList(basMeteringConfigDto);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, readOnly = false)
    @Override
    public R insertBasMeteringConfig(BasMeteringConfig basMeteringConfig) {
        try {

            List<String> list= Arrays.asList(basMeteringConfig.getLogisticsErvicesCode().split(","));
            list.forEach(s->{
                String mag="产品code,国家code,客户代码三个条件查出有此规则";
                //新增时查询是否有此规则（三个条件，客户代码，产品code，国家code）
                basMeteringConfig.setLogisticsErvicesCode(s);
                basMeteringConfig.setCreateTime(new Date());
                BasMeteringConfig basMeteringConfig1=basMeteringConfigMapper.selectPrimary(basMeteringConfig);
                if (basMeteringConfig1!=null){
                    R<String> r=  basTranslateFeignService.Translate(mag);
                    if (r.getCode()== HttpStatus.SUCCESS){
                         mag=r.getData();
                    }
                    throw new CommonException(mag);
                }
                basMeteringConfig.setLogisticsErvicesCode(s);
                basMeteringConfigMapper.insertSelective(basMeteringConfig);
                basMeteringConfig.setId(null);
                basMeteringConfig.getBasMeteringConfigDataList().forEach(x->{
                    x.setMeteringId(basMeteringConfig.getDelId());
                    basMeteringConfigDataMapper.insertSelective(x);
                });
            });


            return R.ok("新增成功");
        }catch (Exception e){

        e.printStackTrace();
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return R.failed( ((CommonException) e).getCode());
        }

    }

//

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, readOnly = false)
    @Override
    public R UpdateBasMeteringConfig(BasMeteringConfig basMeteringConfig) {
        try {

            List<String> list= Arrays.asList(basMeteringConfig.getLogisticsErvicesCode().split(","));
            list.forEach(s->{
                String mag="产品code,国家code,客户代码三个条件查出有此规则";
                basMeteringConfig.setLogisticsErvicesCode(s);
                //修改时查询是否有此规则（三个条件，客户代码，产品code，国家code）
                BasMeteringConfig basMeteringConfig1=basMeteringConfigMapper.selectUptePrimary(basMeteringConfig);
                if (basMeteringConfig1!=null){
                    R<String> r=  basTranslateFeignService.Translate(mag);
                    if (r.getCode()== HttpStatus.SUCCESS){
                        mag=r.getData();
                    }
                    throw new CommonException(mag);
                }
                basMeteringConfig.setUpdateTime(new Date());
                basMeteringConfigMapper.updateByPrimaryKeySelective(basMeteringConfig);
                //删除子表数据 从新做添加
                basMeteringConfigDataMapper.deleteByPrimaryKey(basMeteringConfig.getId());
                basMeteringConfig.getBasMeteringConfigDataList().forEach(x->{
                    x.setMeteringId(basMeteringConfig.getId());
                    basMeteringConfigDataMapper.insertSelective(x);
                });
            });
            return R.ok("修改成功");
        }catch (Exception e){
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return R.failed(((CommonException) e).getCode());
        }
    }

    @Override
    public R selectById(Integer id) {
        BasMeteringConfig basMeteringConfig=  basMeteringConfigMapper.selectById(id);
        List<BasMeteringConfigData> list=basMeteringConfigDataMapper.selectByPrimaryKey(id);
        if (list.size()>0){
            basMeteringConfig.setBasMeteringConfigDataList(list);
        }
        return R.ok(basMeteringConfig);
    }



    @Override
    public R intercept(BasMeteringConfigDto basMeteringConfigDto) {
        try {
            log.info("出库拦截传递的参数: {}", basMeteringConfigDto);
//            if (basMeteringConfigDto.getLogisticsErvicesCode()==null||basMeteringConfigDto.getCountryCode()==null||basMeteringConfigDto.getCustomerCode()==null){
//                String mag="产品code,国家code,客户代码存在空值，不符合规则匹配，不用拦截";
//                R<String> r=  basTranslateFeignService.Translate(mag);
//                if (r.getCode()== HttpStatus.SUCCESS){
//                    mag=r.getData();
//                }
//
//                return R.ok(mag);
//            }
         //根据传来的值去查询有无规则
          List<BasMeteringConfigData> list=basMeteringConfigMapper.selectjblj(basMeteringConfigDto);
            if (list.size()>0) {
                list.forEach(x -> {
                    //0表示重量差，1表示百分比
                    if (x.getWeightTypeNameOneCode().equals("billableWeight") && x.getWeightTypeNameTwoCode().equals("volumeWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getVolume(), basMeteringConfigDto.getCalcWeight()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        }

                    }
                    if (x.getWeightTypeNameOneCode().equals("volumeWeight") && x.getWeightTypeNameTwoCode().equals("billableWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getVolume(), basMeteringConfigDto.getCalcWeight()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        }
                    }
                    if (x.getWeightTypeNameOneCode().equals("billableWeight") && x.getWeightTypeNameTwoCode().equals("forecastWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getCalcWeight()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        }
                    }
                    if (x.getWeightTypeNameOneCode().equals("forecastWeight") && x.getWeightTypeNameTwoCode().equals("billableWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getCalcWeight()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                    throw new CommonException("Exceeding the set range, intercept");
                            }
                        }
                    }
                    if (x.getWeightTypeNameOneCode().equals("forecastWeight") && x.getWeightTypeNameTwoCode().equals("volumeWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getVolume()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        }
                    }
                    if (x.getWeightTypeNameOneCode().equals("volumeWeight") && x.getWeightTypeNameTwoCode().equals("forecastWeight")) {
                        if (x.getDifferenceType().equals(0)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            int a = bigDecimal.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        } else if (x.getDifferenceType().equals(1)) {
                            BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                            BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                            BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getVolume()};
                            BigDecimal max = c[0], min = c[0];
                            for (int i = 0; i < c.length; i++) {
                                if (c[i].compareTo(min) == (1)) {
                                    max = c[i];
                                }
                            }
                            //减出来的重量差 除以最大值 算出来百分比
                            BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                            int a = d.compareTo(bigDecimal1);
                            if (a == 1) {
                                throw new CommonException("Exceeding the set range, intercept");
                            }
                        }
                    }
                });
            }else if (list.size()==0){
                //三个必要条件有没有填写的去匹配通用规则
                List<BasMeteringConfigData> list3=basMeteringConfigMapper.selectjbljs(basMeteringConfigDto);
                if (list3.size()>0) {
                    list3.forEach(x -> {
                        //0表示重量差，1表示百分比
                        if (x.getWeightTypeNameOneCode().equals("billableWeight") && x.getWeightTypeNameTwoCode().equals("volumeWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getVolume() != null && basMeteringConfigDto.getCalcWeight() != null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getVolume() != null && basMeteringConfigDto.getCalcWeight() != null) {

                                    BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getVolume(), basMeteringConfigDto.getCalcWeight()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }

                        }
                        if (x.getWeightTypeNameOneCode().equals("volumeWeight") && x.getWeightTypeNameTwoCode().equals("billableWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getVolume()!=null&&basMeteringConfigDto.getCalcWeight()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getVolume() != null && basMeteringConfigDto.getCalcWeight() != null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getVolume(), basMeteringConfigDto.getCalcWeight()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }
                        }
                        if (x.getWeightTypeNameOneCode().equals("billableWeight") && x.getWeightTypeNameTwoCode().equals("forecastWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getCalcWeight()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getWeight() != null && basMeteringConfigDto.getCalcWeight() != null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getCalcWeight()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }
                        }
                        if (x.getWeightTypeNameOneCode().equals("forecastWeight") && x.getWeightTypeNameTwoCode().equals("billableWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getCalcWeight()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getCalcWeight()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getCalcWeight()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }
                        }
                        if (x.getWeightTypeNameOneCode().equals("forecastWeight") && x.getWeightTypeNameTwoCode().equals("volumeWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getVolume()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getVolume()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getVolume()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }
                        }
                        if (x.getWeightTypeNameOneCode().equals("volumeWeight") && x.getWeightTypeNameTwoCode().equals("forecastWeight")) {
                            if (x.getDifferenceType().equals(0)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getVolume()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    int a = bigDecimal.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            } else if (x.getDifferenceType().equals(1)) {
                                if (basMeteringConfigDto.getWeight()!=null&&basMeteringConfigDto.getVolume()!=null) {
                                    BigDecimal bigDecimal = (basMeteringConfigDto.getWeight().subtract(basMeteringConfigDto.getVolume())).abs();
                                    BigDecimal bigDecimal1 = BigDecimal.valueOf(x.getDifferenceScope());
                                    BigDecimal c[] = {basMeteringConfigDto.getWeight(), basMeteringConfigDto.getVolume()};
                                    BigDecimal max = c[0], min = c[0];
                                    for (int i = 0; i < c.length; i++) {
                                        if (c[i].compareTo(min) == (1)) {
                                            max = c[i];
                                        }
                                    }
                                    //减出来的重量差 除以最大值 算出来百分比
                                    BigDecimal d = (bigDecimal.divide(max, 2, BigDecimal.ROUND_HALF_UP)).multiply(BigDecimal.valueOf(100));
                                    int a = d.compareTo(bigDecimal1);
                                    if (a == 1) {
                                        throw new CommonException("Exceeding the set range, intercept");
                                    }
                                }
                            }
                        }
                    });
                }
            }
            return R.ok();
        }catch (Exception e){
            String message=((CommonException) e).getCode();
            e.printStackTrace();
            return R.failed(message);
        }

    }


    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, readOnly = false)
    @Override
    public R deleteBasMeteringConfig(List<Integer> ids) {
        try {
           ids.forEach(x->{
               basMeteringConfigMapper.deleteByPrimaryKey(x);
               basMeteringConfigDataMapper.deleteByPrimaryKey(x);
           });

            return R.ok("删除成功");
        }catch (Exception e){
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return R.failed("删除失败");
        }

    }

}
