package com.szmsd.bas.controller;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.szmsd.bas.domain.BasMaterial;
import com.szmsd.bas.domain.BasSeller;
import com.szmsd.bas.dto.BasMaterialQueryDto;
import com.szmsd.bas.service.IBasMaterialService;
import com.szmsd.bas.service.IBasSellerService;
import com.szmsd.bas.service.IBasSerialNumberService;
import com.szmsd.bas.vo.BasMaterialVO;
import com.szmsd.bas.vo.BaseProductVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.log.annotation.Log;
import com.szmsd.common.log.enums.BusinessType;
import com.szmsd.common.security.utils.SecurityUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;


/**
* <p>
    *  前端控制器
    * </p>
*
* @author l
* @since 2021-03-12
*/


@Api(tags = {"包材模块"})
@RestController
@RequestMapping("/bas/material")
public class BasMaterialController extends BaseController{

     @Resource
     private IBasMaterialService basMaterialService;

    @Autowired
    private IBasSellerService basSellerService;

     /**
       * 查询模块列表
     */
      @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:list')")
      @GetMapping("/list")
      @ApiOperation(value = "查询模块列表",notes = "查询模块列表")
      public TableDataInfo list(BasMaterialQueryDto basMaterialQueryDto)
     {
            startPage();
            List<BasMaterial> list = basMaterialService.selectBasMaterialList(basMaterialQueryDto);
            return getDataTable(list);
      }

    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:list')")
    @GetMapping("/listByCode")
    @ApiOperation(value = "通过code查询列表", notes = "通过code查询列表")
    public TableDataInfo listByCode(String code) {
        QueryWrapper<BasSeller> basSellerQueryWrapper = new QueryWrapper<>();
        basSellerQueryWrapper.eq("user_name", SecurityUtils.getLoginUser().getUsername());
        BasSeller basSeller = basSellerService.getOne(basSellerQueryWrapper);
        startPage();
        List<BasMaterialVO> list = basMaterialService.selectBaseMaterialByCode(code,basSeller.getSellerCode());
        return getDataTable(list);
    }

    /**
     * 查询模块列表
     */
    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:list')")
    @GetMapping("/listBasMaterial")
    @ApiOperation(value = "查询模块列表",notes = "查询模块列表")
    public R<List<BasMaterial>> listBasMaterial(BasMaterialQueryDto basMaterialQueryDto)
    {
        List<BasMaterial> list = basMaterialService.selectBasMaterialList(basMaterialQueryDto);
        return R.ok(list);
    }


    /**
    * 导出模块列表
    */
     @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:export')")
     @Log(title = "模块", businessType = BusinessType.EXPORT)
     @GetMapping("/export")
     @ApiOperation(value = "导出模块列表",notes = "导出模块列表")
     public void export(HttpServletResponse response, BasMaterialQueryDto basMaterialQueryDto) throws IOException {
     List<BasMaterial> list = basMaterialService.selectBasMaterialList(basMaterialQueryDto);
     ExcelUtil<BasMaterial> util = new ExcelUtil<BasMaterial>(BasMaterial.class);
     util.exportExcel(response,list, "BasMaterial");

     }

    /**
    * 获取模块详细信息
    */
    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:query')")
    @GetMapping(value = "getInfo/{id}")
    @ApiOperation(value = "获取模块详细信息",notes = "获取模块详细信息")
    public R getInfo(@PathVariable("id") String id)
    {
    return R.ok(basMaterialService.selectBasMaterialById(id));
    }

    /**
    * 新增模块
    */
    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:add')")
    @Log(title = "模块", businessType = BusinessType.INSERT)
    @PostMapping("add")
    @ApiOperation(value = "新增模块",notes = "新增模块")
    public R add(@RequestBody BasMaterial basMaterial)
    {
    return toOk(basMaterialService.insertBasMaterial(basMaterial));
    }

    /**
    * 修改模块
    */
    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:edit')")
    @Log(title = "模块", businessType = BusinessType.UPDATE)
    @PutMapping("edit")
    @ApiOperation(value = " 修改模块",notes = "修改模块")
    public R edit(@RequestBody BasMaterial basMaterial) throws IllegalAccessException {
    return toOk(basMaterialService.updateBasMaterial(basMaterial));
    }

    /**
    * 删除模块
    */
    @PreAuthorize("@ss.hasPermi('BasMaterial:BasMaterial:remove')")
    @Log(title = "模块", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    @ApiOperation(value = "删除模块",notes = "删除模块")
    public R remove(@RequestBody List<Long> ids) throws IllegalAccessException {
    return R.ok(basMaterialService.deleteBasMaterialByIds(ids));
    }

}
