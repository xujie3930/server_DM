package com.szmsd.bas.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szmsd.bas.api.domain.BasUser;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author ziling
 * @since 2020-08-11
 */
public interface IBasUserService extends IService<BasUser> {

    /**
     * 查询模块
     *
     * @param id 模块ID
     * @return 模块
     */
    public BasUser selectBasUserById(String id);

    /**
     * 查询模块列表
     *
     * @param BasUser 模块
     * @return 模块集合
     */
    public List<BasUser> selectBasUserList(BasUser basUser);

    /**
     * 新增模块
     *
     * @param BasUser 模块
     * @return 结果
     */
    public int insertBasUser(BasUser basUser);

    /**
     * 修改模块
     *
     * @param BasUser 模块
     * @return 结果
     */
    public int updateBasUser(BasUser basUser);

    /**
     * 批量删除模块
     *
     * @param ids 需要删除的模块ID
     * @return 结果
     */
    public int deleteBasUserByIds(List<Integer> ids);

    /**
     * 删除模块信息
     *
     * @param id 模块ID
     * @return 结果
     */
    public int deleteBasUserById(String id);
}
