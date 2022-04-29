package com.szmsd.chargerules.service;


import com.szmsd.chargerules.domain.BasSpecialOperation;
import com.szmsd.chargerules.domain.SpecialOperation;
import com.szmsd.chargerules.dto.SpecialOperationDTO;

import java.util.List;

public interface ISpecialOperationService {

    int save(SpecialOperationDTO dto);

    int update(SpecialOperation dto);

    List<SpecialOperation> listPage(SpecialOperationDTO dto);

    SpecialOperation selectOne(BasSpecialOperation basSpecialOperation);

    /**
     * 根据id查询详情
     * @param id id
     * @return SpecialOperation
     */
    SpecialOperation details(int id);
}
