package com.szmsd.system.service.impl;

import com.szmsd.bas.api.feign.BasSellerFeignService;
import com.szmsd.bas.dto.ServiceConditionDto;
import com.szmsd.common.core.constant.UserConstants;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.web.BaseException;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.system.api.constant.DataScopeConstant;
import com.szmsd.system.api.domain.SysRole;
import com.szmsd.system.api.domain.SysUser;
import com.szmsd.system.domain.SysPost;
import com.szmsd.system.domain.SysUserRole;
import com.szmsd.system.mapper.*;
import com.szmsd.system.service.ISysConfigService;
import com.szmsd.system.service.ISysUserService;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户 业务层处理
 *
 * @author lzw
 */
@Service
public class SysUserServiceImpl implements ISysUserService {
    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    @Resource
    private SysUserMapper userMapper;

    @Resource
    private SysRoleMapper roleMapper;

    @Resource
    private SysPostMapper postMapper;

    @Resource
    private SysUserRoleMapper userRoleMapper;

    @Resource
    private SysUserPostMapper userPostMapper;

    @Resource
    private ISysConfigService configService;

    @Autowired
    private BasSellerFeignService basSellerFeignService;

    /**
     * 根据条件分页查询用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
//    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUserList(SysUser user) {
        return userMapper.selectUserList(user);
    }

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserByUserName(String userName, String userType) {
        SysUser sysUser = userMapper.selectUserByUserName(userName, userType);
        if (null == sysUser) {
            return null;
        }
        List<SysRole> roles = sysUser.getRoles();
        if (CollectionUtils.isNotEmpty(roles)) {
            boolean allDataScope = false;
            // 判断有没有所有权限
            for (SysRole role : roles) {
                String dataScope = role.getDataScope();
                if (DataScopeConstant.DATA_SCOPE_ALL.equals(dataScope)) {
                    // 所有权限
                    allDataScope = true;
                    break;
                }
            }
            if (!allDataScope) {
                boolean hasServiceManager = false;
                boolean hasServiceStaff = false;
                for (SysRole role : roles) {
                    if (DataScopeConstant.DATA_SCOPE_SERVICE_MANAGER.equals(role.getDataScope())) {
                        hasServiceManager = true;
                    }
                    if (DataScopeConstant.DATA_SCOPE_SERVICE_STAFF.equals(role.getDataScope())) {
                        hasServiceStaff = true;
                    }
                }
                // 查询归属客户编号
                ServiceConditionDto conditionDto = new ServiceConditionDto();
                if (hasServiceManager) {
                    conditionDto.setServiceManager(String.valueOf(sysUser.getUserId()));
                }
                if (hasServiceStaff) {
                    conditionDto.setServiceStaff(String.valueOf(sysUser.getUserId()));
                }
                String sellerCode = sysUser.getSellerCode();
                List<String> permissions = null;
                if (hasServiceManager || hasServiceStaff) {
                    R<List<String>> listR = this.basSellerFeignService.queryByServiceCondition(conditionDto);
                    if (null != listR) {
                        // 授权信息
                        permissions = listR.getData();
                    }
                }
                // 这里默认将自己的客户编码添加到权限中，对应数据权限 4 @see com.szmsd.system.api.constant.DataScopeConstant.DATA_SCOPE_SELF
                // 处理无任何权限时，能查到数据的问题
                if (CollectionUtils.isEmpty(permissions)) {
                    // 只能查询自己的数据
                    String permission;
                    if (StringUtils.isNotEmpty(sellerCode)) {
                        permission = sellerCode;
                    } else {
                        permission = "0";
                    }
                    permissions = new ArrayList<>();
                    permissions.add(permission);
                } else {
                    if (StringUtils.isNotEmpty(sellerCode)) {
                        if (!CollectionUtils.exists(permissions, sellerCode::equals)) {
                            permissions.add(sellerCode);
                        }
                    }
                }
                sysUser.setPermissions(permissions);
            }
            sysUser.setAllDataScope(allDataScope);
        }
        return sysUser;
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public SysUser selectUserById(Long userId) {
        return userMapper.selectUserById(userId);
    }

    /**
     * 查询用户所属角色组
     *
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(String userName) {
        List<SysRole> list = roleMapper.selectRolesByUserName(userName);
        StringBuffer idsStr = new StringBuffer();
        for (SysRole role : list) {
            idsStr.append(role.getRoleName()).append(",");
        }
        if (StringUtils.isNotEmpty(idsStr.toString())) {
            return idsStr.substring(0, idsStr.length() - 1);
        }
        return idsStr.toString();
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(String userName) {
        List<SysPost> list = postMapper.selectPostsByUserName(userName);
        StringBuffer idsStr = new StringBuffer();
        for (SysPost post : list) {
            idsStr.append(post.getPostName()).append(",");
        }
        if (StringUtils.isNotEmpty(idsStr.toString())) {
            return idsStr.substring(0, idsStr.length() - 1);
        }
        return idsStr.toString();
    }

    /**
     * 校验用户名称是否唯一
     *
     * @param userName 用户名称
     * @return 结果
     */
    @Override
    public String checkUserNameUnique(String userName) {
        int count = userMapper.checkUserNameUnique(userName);
        if (count > 0) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验用户名称是否唯一
     *
     * @param userName 用户名称
     * @return 结果
     */
    @Override
    public String checkUserNameUniqueCus(String userName) {
        int count = userMapper.checkUserNameUniqueCus(userName);
        if (count > 0) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验用户名称是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public String checkPhoneUnique(SysUser user) {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        if (StringUtils.isNotEmpty(user.getPhonenumber())) {
            SysUser info = userMapper.checkPhoneUnique(user.getPhonenumber());

            if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue()) {
                return UserConstants.NOT_UNIQUE;
            }
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public String checkEmailUnique(SysUser user) {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        if (StringUtils.isNotEmpty(user.getEmail())) {
            SysUser info = userMapper.checkEmailUnique(user.getEmail());
            if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue()) {
                return UserConstants.NOT_UNIQUE;
            }
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return
     */
    @Override
    public String checkEmailUniqueCus(SysUser user) {
        Long userId = StringUtils.isNull(user.getUserId()) ? -1L : user.getUserId();
        if (StringUtils.isNotEmpty(user.getEmail())) {
            SysUser info = userMapper.checkEmailUniqueCus(user.getEmail());
            if (StringUtils.isNotNull(info) && info.getUserId().longValue() != userId.longValue()) {
                return UserConstants.NOT_UNIQUE;
            }
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验用户是否允许操作
     *
     * @param user 用户信息
     */
    @Override
    public void checkUserAllowed(SysUser user) {
        if (StringUtils.isNotNull(user.getUserId()) && user.isAdmin()) {
            throw new BaseException("不允许操作超级管理员用户");
        }
    }

    /**
     * 新增保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public R insertUser(SysUser user) {
        // 新增用户信息

        int rows = userMapper.insert(user);
//        // 新增用户岗位关联
//        insertUserPost(user);
        // 新增用户与角色管理
        insertUserRole(user);
        Map map = new HashMap<>();
        map.put("userId", user.getUserId());
        if (rows > 0) {
            return R.ok(map);
        } else {
            return R.failed();
        }

    }

    /**
     * 修改保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateUser(SysUser user) {
        Long userId = user.getUserId();
        Integer roleType = UserConstants.ROLE_TYPE_PC;
        //判断是 E3还是大客户用户,选择删除角色类型1-PC，2-APP，3-VIP,默认删除E3

        if (UserConstants.USER_TYPE_VIP.equals(user.getUserType())) {
            roleType = UserConstants.ROLE_TYPE_VIP;
        }


        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleOtherByUserId(userId, roleType);// todo 只删除角色为E3/相关的
        // 新增用户与角色管理
        insertUserRole(user);
//        // 删除用户与岗位关联
//        userPostMapper.deleteUserPostByUserId(userId);
//        // 新增用户与岗位管理
//        insertUserPost(user);
        return userMapper.updateById(user);
//        return userMapper.updateUser(user);
    }

    /**
     * 修改保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int baseCopyUserEdit(SysUser user) {
        return userMapper.updateById(user);
    }

    /**
     * 修改保存App角色用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateUserApp(SysUser user) {
        Long userId = user.getUserId();
        // 删除用户与角色关联
        userRoleMapper.deleteUserRoleOtherByUserId(userId, UserConstants.ROLE_TYPE_APP);// todo 只删除角色为app相关的
        // 新增用户与角色管理
        insertUserRole(user);
//        // 删除用户与岗位关联
//        userPostMapper.deleteUserPostByUserId(userId);
//        // 新增用户与岗位管理
//        insertUserPost(user);
        return userMapper.updateById(user);
//        return userMapper.updateUser(user);
    }


    /**
     * 修改用户状态
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int updateUserStatus(SysUser user) {
        return userMapper.updateUser(user);
    }

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int updateUserProfile(SysUser user) {
        return userMapper.updateUser(user);
    }

    /**
     * 修改用户头像
     *
     * @param userName 用户ID
     * @param avatar   头像地址
     * @return 结果
     */
    @Override
    public boolean updateUserAvatar(String userName, String avatar) {
        return userMapper.updateUserAvatar(userName, avatar) > 0;
    }

    /**
     * 重置用户密码
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public int resetPwd(SysUser user) {
        return userMapper.updateUser(user);
    }

    @Override
    public int resetPwdBySeller(SysUser user) {
        return userMapper.updateUserBySeller(user);
    }

    /**
     * 重置用户密码
     *
     * @param userName 用户名
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetUserPwd(String userName, String password) {
        return userMapper.resetUserPwd(userName, password);
    }


    /**
     * 重置APP用户密码
     *
     * @param userName 用户名
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetAppUserPwd(String userName, String password) {
        return userMapper.resetAppUserPwd(userName, password);
    }

    /**
     * 新增用户角色信息
     *
     * @param user 用户对象
     */
    public void insertUserRole(SysUser user) {
        Long[] roles = user.getRoleIds();
        if (StringUtils.isNotNull(roles)) {
            // 新增用户与角色管理
            List<SysUserRole> list = new ArrayList<SysUserRole>();
            for (Long roleId : roles) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getUserId());
                ur.setRoleId(roleId);
                list.add(ur);
            }
            if (list.size() > 0) {
                userRoleMapper.batchUserRole(list);
            }
        }
    }
// TODO 用户岗位移到基础资料
//    /**
//     * 新增用户岗位信息
//     *
//     * @param user 用户对象
//     */
//    public void insertUserPost(SysUser user) {
//        Long[] posts = user.getPostIds();
//        if (StringUtils.isNotNull(posts)) {
//            // 新增用户与岗位管理
//            List<SysUserPost> list = new ArrayList<SysUserPost>();
//            for (Long postId : posts) {
//                SysUserPost up = new SysUserPost();
//                up.setUserId(user.getUserId());
//                up.setPostId(postId);
//                list.add(up);
//            }
//            if (list.size() > 0) {
//                userPostMapper.batchUserPost(list);
//            }
//        }
//    }

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public int deleteUserById(Long userId) {
        // 删除用户与角色关联

        userRoleMapper.deleteUserRoleByUserId(userId);
        // 删除用户与岗位表
        userPostMapper.deleteUserPostByUserId(userId);
        return userMapper.deleteUserById(userId);
    }

    @Override
    public int deleteUserByemail(String email) {
        SysUser info = userMapper.checkEmailUnique(email);
        Long userId = info.getUserId();
        // 删除用户与角色关联

        userRoleMapper.deleteUserRoleByUserId(userId);
        // 删除用户与岗位表
        userPostMapper.deleteUserPostByUserId(userId);
        return userMapper.deleteUserById(userId);
    }

    /**
     * 批量逻辑删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    public int deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            checkUserAllowed(new SysUser(userId));
        }
        return userMapper.deleteUserByIds(userIds);
    }

    /**
     * 导入用户数据
     *
     * @param userList        用户数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName        操作用户
     * @return 结果
     */
    @Override
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(userList) || userList.size() == 0) {
            throw new BaseException("导入用户数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        String password = configService.selectConfigByKey("sys.user.initPassword");
        for (SysUser user : userList) {
            try {
                // 验证是否存在这个用户
                SysUser u = userMapper.selectUserByUserName(user.getUserName(), "");
                if (StringUtils.isNull(u)) {
                    user.setPassword(SecurityUtils.encryptPassword(password));
                    user.setCreateByName(operName);
                    this.insertUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 导入成功");
                } else if (isUpdateSupport) {
                    user.setUpdateByName(operName);
                    this.updateUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 更新成功");
                } else {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、账号 " + user.getUserName() + " 已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、账号 " + user.getUserName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new BaseException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    @Override
    public List<SysUser> selectUserListsu(SysUser sysUser) {
        return userMapper.selectUserListsu(sysUser);
    }

}
