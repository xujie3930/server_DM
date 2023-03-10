package com.szmsd.chargerules.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.bas.api.feign.BasSellerFeignService;
import com.szmsd.bas.vo.BasSellerInfoVO;
import com.szmsd.chargerules.domain.WarehouseOperation;
import com.szmsd.chargerules.domain.WarehouseOperationDetails;
import com.szmsd.chargerules.dto.WarehouseOperationDTO;
import com.szmsd.chargerules.mapper.WarehouseOperationDetailsMapper;
import com.szmsd.chargerules.mapper.WarehouseOperationMapper;
import com.szmsd.chargerules.service.IWarehouseOperationDetailsService;
import com.szmsd.chargerules.service.IWarehouseOperationService;
import com.szmsd.chargerules.vo.WarehouseOperationVo;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class WarehouseOperationServiceImpl extends ServiceImpl<WarehouseOperationMapper, WarehouseOperation> implements IWarehouseOperationService {

    @Resource
    private WarehouseOperationMapper warehouseOperationMapper;

    @Resource
    private IWarehouseOperationDetailsService warehouseOperationDetailsService;
    @Resource
    private BasSellerFeignService basSellerFeignService;
    @Autowired
    private WarehouseOperationDetailsMapper warehouseOperationDetailsMapper;

    private final String REGEX = "\\d+D\\d+";

    @Transactional
    @Override
    public int save(WarehouseOperationDTO dto) {
        if (!checkWarehouse(dto)) {
            WarehouseOperation domain = new WarehouseOperation();
            BeanUtils.copyProperties(dto, domain);

            warehouseOperationMapper.insert(domain);
            if (dto.getDetails()!=null){
                AssertUtil.notEmpty(dto.getDetails(), "????????????");
                long count = dto.getDetails().stream().peek(value -> value.setWarehouseOperationId(domain.getId())).filter(value -> !Pattern.matches(REGEX, value.getChargeDays())).count();
                if (count > 0) throw new CommonException("999", "????????????????????????");

            }
            List<WarehouseOperationVo> warehouseOperationDb = this.listPage(dto);
            if ( dto.getDetails()!=null) {

                if (isIntersection(dto.getDetails(), warehouseOperationDb.get(0).getDetails()))
                    throw new CommonException("999", "??????+??????????????????");
            }

            if (dto.getDetails()!=null) {
                warehouseOperationDetailsService.saveBatch(dto.getDetails());
            }
            if (dto.getLocationDetails()!=null) {
                //dto.getLocationDetails().stream().peek(value -> value.setWarehouseOperationId(domain.getId()));
                AssertUtil.notEmpty(dto.getLocationDetails(), "????????????");
                dto.getLocationDetails().forEach(x->{
                    x.setWarehouseOperationId(domain.getId());
                });
                warehouseOperationDetailsService.saveBatch(dto.getLocationDetails());
            }
        }
        return 1;
    }

    /**
     * ???????????????????????? ???????????????
     *
     * @param dto dto
     * @return true:?????????????????? false?????????????????????
     */
    private boolean checkWarehouse(WarehouseOperationDTO dto) {
        AssertUtil.notNull(dto.getWarehouseCode(), "????????????");

            if (dto.getDetails()!=null) {
                List<WarehouseOperationVo> list = this.listPage(dto);
                if (CollectionUtils.isNotEmpty(list)) { //??????????????? ?????????????????????????????????
                    if (isIntersection(dto.getDetails(), list.get(0).getDetails()))
                        throw new CommonException("999", "??????+??????????????????");

                    List<WarehouseOperationDetails> collect = dto.getDetails().stream().peek(value -> value.setWarehouseOperationId(list.get(0).getId())).collect(Collectors.toList());
                return warehouseOperationDetailsService.saveBatch(collect);
            }
            if (dto.getLocationDetails()!=null) {
                List<WarehouseOperationDetails> collect = dto.getLocationDetails().stream().peek(value -> value.setWarehouseOperationId(list.get(0).getId())).collect(Collectors.toList());
                warehouseOperationDetailsService.saveBatch(collect);
            }
        }
        return false;
    }

    /**
     * ????????????????????????
     *
     * @param dto list
     * @param db  db
     * @return true?????????/????????? false????????????/??????
     */
    public boolean isIntersection(List<WarehouseOperationDetails> dto, List<WarehouseOperationDetails> db) {
        if (dto.size() == 1 && db.size() == 0) return false;
        List<WarehouseOperationDetails> list = Stream.of(dto, db).flatMap(Collection::stream).collect(Collectors.toList());
        for (int i = 0; i < list.size(); i++) {
            String[] ds = list.get(i).getChargeDays().split("D");
            int start = Integer.parseInt(ds[0]);
            int end = Integer.parseInt(ds[1]);
            for (int j = i + 1; j < list.size(); j++) {
                String[] ds2 = list.get(j).getChargeDays().split("D");
                int start2 = Integer.parseInt(ds2[0]);
                int end2 = Integer.parseInt(ds2[1]);
                if (Math.max(start, start2) < Math.min(end, end2)) {
                    log.error("???????????????????????????start:{}, end:{}, start2:{}, end2:{}", start, end, start2, end2);
                    return true;
                }
            }
        }
        return false;

    }

    @Transactional
    @Override
    public int update(WarehouseOperationDTO dto) {
        if (dto.getDetails()!=null){
            long count = dto.getDetails().stream().peek(value -> value.setWarehouseOperationId(dto.getId())).filter(value -> !Pattern.matches(REGEX, value.getChargeDays())).count();
            if (count > 0) throw new CommonException("999", "????????????????????????");
        }
        WarehouseOperation map = BeanMapperUtil.map(dto, WarehouseOperation.class);
        this.updateDetails(dto);
        return warehouseOperationMapper.updateById(map);
    }

    private void updateDetails(WarehouseOperationDTO dto) {
        LambdaQueryWrapper<WarehouseOperationDetails> query = Wrappers.lambdaQuery();
        query.eq(WarehouseOperationDetails::getWarehouseOperationId, dto.getId());

        if (dto.getLocationDetails()!=null){
            dto.getLocationDetails().forEach(x->{
                x.setWarehouseOperationId(dto.getId());
            });
            query.eq(WarehouseOperationDetails::getComputeType,1);
            warehouseOperationDetailsService.remove(query);
            warehouseOperationDetailsService.saveBatch(dto.getLocationDetails());

        }
        query.eq(WarehouseOperationDetails::getWarehouseOperationId, dto.getId());
        if (dto.getDetails()!=null){
            query.eq(WarehouseOperationDetails::getComputeType,0);
            warehouseOperationDetailsService.remove(query);
            if (isIntersection(dto.getDetails(), new ArrayList<>())) throw new CommonException("999", "??????+??????????????????");
            warehouseOperationDetailsService.saveBatch(dto.getDetails());

        }


    }

    @Override
    public List<WarehouseOperationVo> listPage(WarehouseOperationDTO dto) {
        return warehouseOperationMapper.listPage(dto);
    }

    @Override
    public List<WarehouseOperationVo> selectOperationByRule(WarehouseOperationDTO queryDTO) {
        log.info("?????????????????????????????????{}", queryDTO);
        String cusTypeCode = queryDTO.getCusTypeCode();
        String cusCodeList = queryDTO.getCusCodeList();
        boolean queryByCusCode = StringUtils.isNotBlank(cusCodeList);
        if (queryByCusCode) {
            queryDTO.setCusTypeCode(null);
        }
        //?????????+????????????????????????????????????????????????????????????????????????????????????????????? ????????????????????????????????????????????????????????????
        List<WarehouseOperationVo> warehouseOperationVos = warehouseOperationMapper.listPage(queryDTO);
        if (CollectionUtils.isEmpty(warehouseOperationVos)) {
            //?????????????????????
            if (queryByCusCode) {
                R<BasSellerInfoVO> info = basSellerFeignService.getInfoBySellerCode(cusCodeList);

                log.info("BasSellerInfoVO:{}",JSONObject.toJSONString(info));

                if(info.getCode() == 200) {

                    BasSellerInfoVO userInfo = info.getData();

                    String discountUserType = userInfo.getDiscountUserType();
                    if (StringUtils.isBlank(discountUserType)) {
                        return null;
                    }
                    cusTypeCode = discountUserType;

                }
            }
            queryDTO.setCusCodeList(null);
            queryDTO.setCusTypeCode(cusTypeCode);
            warehouseOperationVos = warehouseOperationMapper.listPage(queryDTO);
        }
        log.info("?????????????????????{}", JSONObject.toJSONString(warehouseOperationVos));
        return warehouseOperationVos;
    }

    private boolean isInTheInterval(long current, long min, long max) {
        return Math.max(min, current) == Math.min(current, max);
    }

    @Override
    public BigDecimal charge(int days, BigDecimal cbm, String warehouseCode, WarehouseOperationVo dto) {
        List<WarehouseOperationDetails> details = dto.getDetails();
        for (WarehouseOperationDetails detail : details) {
            String chargeDays = detail.getChargeDays();
            String[] ds = chargeDays.split("D");
            int start = Integer.parseInt(ds[0]);
            int end = Integer.parseInt(ds[1]);
            if (isInTheInterval(days, start, end)) {
                return cbm.multiply(detail.getPrice()).multiply(detail.getDiscountRate()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        log.error("??????????????????????????????????????? days???{},warehouseCode {}", days, warehouseCode);
        return BigDecimal.ZERO;
    }

    @Override
    public WarehouseOperationVo details(int id) {
        WarehouseOperationVo warehouseOperationVo=warehouseOperationMapper.selectDetailsById(id);
        //??????
        List<WarehouseOperationDetails> warehouseOperationDetails=warehouseOperationDetailsMapper.selectWarehouseOperationDetailsrs(id,0);
        //??????
        List<WarehouseOperationDetails> locationwarehouseOperationDetails=warehouseOperationDetailsMapper.selectWarehouseOperationDetailsrs(id,1);
        if (warehouseOperationDetails.size()>0){
            warehouseOperationVo.setDetails(warehouseOperationDetails);
        }
        if (locationwarehouseOperationDetails.size()>0){
            warehouseOperationVo.setLocationDetails(locationwarehouseOperationDetails);
        }
        return warehouseOperationVo;
    }

}
