package com.szmsd.bas.service.impl;

import com.szmsd.bas.domain.BasMeteringConfig;
import com.szmsd.bas.domain.BasMeteringConfigData;
import com.szmsd.bas.dto.BasMeteringConfigDto;
import com.szmsd.bas.mapper.BasMeteringConfigDataMapper;
import com.szmsd.bas.mapper.BasMeteringConfigMapper;
import com.szmsd.bas.service.IBasMeteringConfigService;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class IBasMeteringConfigServiceImpl implements IBasMeteringConfigService {
    @Autowired
    private BasMeteringConfigMapper basMeteringConfigMapper;
    @Autowired
    private BasMeteringConfigDataMapper basMeteringConfigDataMapper;

    @Override
    public List<BasMeteringConfig> selectList(BasMeteringConfigDto basMeteringConfigDto) {
        if (basMeteringConfigDto.getCustomerCode()!=null){
            basMeteringConfigDto.setCustomerCode(new StringBuilder("%").append(basMeteringConfigDto.getCustomerCode()).append("%").toString());
        }
        return basMeteringConfigMapper.selectList(basMeteringConfigDto);
    }

    @Override
    public R insertBasMeteringConfig(BasMeteringConfig basMeteringConfig) {
        try {

            List<String> list= Arrays.asList(basMeteringConfig.getLogisticsErvicesCode().split(","));
            list.forEach(s->{
                //新增时查询是否有此规则（三个条件，客户代码，产品code，国家code）
                basMeteringConfig.setLogisticsErvicesCode(s);
                BasMeteringConfig basMeteringConfig1=basMeteringConfigMapper.selectPrimary(basMeteringConfig);
                if (basMeteringConfig1!=null){
                    throw new CommonException("产品code,国家code,客户代码三个条件查出有此规则");
                }
                basMeteringConfig.setLogisticsErvicesCode(s);
                basMeteringConfigMapper.insertSelective(basMeteringConfig);

                basMeteringConfig.getBasMeteringConfigDataList().forEach(x->{
                    x.setMeteringId(basMeteringConfig.getId());
                    basMeteringConfigDataMapper.insertSelective(x);
                });
            });


            return R.ok("新增成功");
        }catch (Exception e){
        e.printStackTrace();
            return R.failed("修改失败");
        }

    }

//

    @Override
    public R UpdateBasMeteringConfig(BasMeteringConfig basMeteringConfig) {
        try {
            List<String> list= Arrays.asList(basMeteringConfig.getLogisticsErvicesCode().split(","));
            list.forEach(s->{
                basMeteringConfig.setLogisticsErvicesCode(s);
                //修改时查询是否有此规则（三个条件，客户代码，产品code，国家code）
                BasMeteringConfig basMeteringConfig1=basMeteringConfigMapper.selectUptePrimary(basMeteringConfig);
                if (basMeteringConfig1!=null){
                    throw new CommonException("产品code,国家code,客户代码三个条件查出有此规则");
                }
                basMeteringConfigMapper.updateByPrimaryKeySelective(basMeteringConfig);
                //删除子表数据 从新做添加
                basMeteringConfigDataMapper.deleteByPrimaryKey(basMeteringConfig.getId());
                basMeteringConfig.getBasMeteringConfigDataList().forEach(x->{
                    x.setMeteringId(basMeteringConfig.getId());
                    basMeteringConfigDataMapper.insertSelective(x);
                });
            });
            return R.ok("新增成功");
        }catch (Exception e){
            e.printStackTrace();
            return R.failed("修改失败");
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
            if (basMeteringConfigDto.getLogisticsErvicesCode()==null||basMeteringConfigDto.getCountryCode()==null||basMeteringConfigDto.getCustomerCode()==null){
                return R.ok("产品code,国家code,客户代码存在空值，不符合规则匹配，不用拦截");
            }
         //根据传来的值去查询有无规则
          List<BasMeteringConfigData> list=basMeteringConfigMapper.selectjblj(basMeteringConfigDto);
            list.forEach(x->{
                //0表示重量差，1表示百分比
             if (x.getWeightTypeNameOne().equals("计费重")&&x.getWeightTypeNameOne().equals("体积重")){
                 if (x.getDifferenceType()==0){
                     BigDecimal bigDecimal= (basMeteringConfigDto.getVolume().subtract(basMeteringConfigDto.getCalcWeight())).abs();
                     BigDecimal bigDecimal1=BigDecimal.valueOf(x.getDifferenceScope());
                     int a=bigDecimal.compareTo(bigDecimal1);
                     if (a==0){

                     }
                 }
             }
             if (x.getWeightTypeNameOne().equals("体积重")&&x.getWeightTypeNameOne().equals("计费重")){

                }
             if (x.getWeightTypeNameOne().equals("计费重")&&x.getWeightTypeNameOne().equals("下单重量")){

                }
             if (x.getWeightTypeNameOne().equals("下单重量")&&x.getWeightTypeNameOne().equals("计费重")){

             }
             if (x.getWeightTypeNameOne().equals("下单重量")&&x.getWeightTypeNameOne().equals("体积重")){

             }
             if (x.getWeightTypeNameOne().equals("体积重")&&x.getWeightTypeNameOne().equals("下单重量")){

             }
            });
            return R.ok();
        }catch (Exception e){
            e.printStackTrace();
            return R.failed();
        }

    }

}
