package ${package.Controller};
import org.springframework.security.access.prepost.PreAuthorize;
import com.szmsd.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import ${package.Service}.${table.serviceName};
import ${package.Entity}.${entity};
import com.szmsd.common.log.annotation.Log;
import com.szmsd.common.core.web.page.TableDataInfo;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.log.enums.BusinessType;
import io.swagger.annotations.Api;
import java.util.List;
import java.io.IOException;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.ApiOperation;
<#if superControllerClassPackage??>${superControllerClassPackage};</#if>


/**
* <p>
    * ${table.comment!} 前端控制器
    * </p>
*
* @author ${author}
* @since ${date}
*/


@Api(tags = {"${table.comment!}"})
@RestController
@RequestMapping("<#if package.ModuleName??>/${package.ModuleName}</#if>/<#if controllerMappingHyphenStyle??>${controllerMappingHyphen}<#else>${table.entityPath}</#if>")
<#if kotlin>class ${table.controllerName}<#if superControllerClass??>:${superControllerClass}()</#if><#else><#if superControllerClass??>public class ${table.controllerName} extends ${superControllerClass}{<#else>public class ${table.controllerName} {</#if>

     @Resource
     private ${table.serviceName} ${(table.serviceName?substring(1))?uncap_first};
     /**
       * 查询${table.comment!}模块列表
     */
      @PreAuthorize("@ss.hasPermi('${entity}:${entity}:list')")
      @GetMapping("/list")
      @ApiOperation(value = "查询${table.comment!}模块列表",notes = "查询${table.comment!}模块列表")
      public TableDataInfo list(${entity} ${entity?uncap_first})
     {
            startPage();
            List<${entity}> list = ${(table.serviceName?substring(1))?uncap_first}.select${entity}List(${entity?uncap_first});
            return getDataTable(list);
      }

    /**
    * 导出${table.comment!}模块列表
    */
     @PreAuthorize("@ss.hasPermi('${entity}:${entity}:export')")
     @Log(title = "${table.comment!}模块", businessType = BusinessType.EXPORT)
     @GetMapping("/export")
     @ApiOperation(value = "导出${table.comment!}模块列表",notes = "导出${table.comment!}模块列表")
     public void export(HttpServletResponse response, ${entity} ${entity?uncap_first}) throws IOException {
     List<${entity}> list = ${(table.serviceName?substring(1))?uncap_first}.select${entity}List(${entity?uncap_first});
     ExcelUtil<${entity}> util = new ExcelUtil<${entity}>(${entity}.class);
        util.exportExcel(response,list, "${entity}");

     }

    /**
    * 获取${table.comment!}模块详细信息
    */
    @PreAuthorize("@ss.hasPermi('${entity}:${entity}:query')")
    @GetMapping(value = "getInfo/{id}")
    @ApiOperation(value = "获取${table.comment!}模块详细信息",notes = "获取${table.comment!}模块详细信息")
    public R getInfo(@PathVariable("id") String id)
    {
    return R.ok(${(table.serviceName?substring(1))?uncap_first}.select${entity}ById(id));
    }

    /**
    * 新增${table.comment!}模块
    */
    @PreAuthorize("@ss.hasPermi('${entity}:${entity}:add')")
    @Log(title = "${table.comment!}模块", businessType = BusinessType.INSERT)
    @PostMapping("add")
    @ApiOperation(value = "新增${table.comment!}模块",notes = "新增${table.comment!}模块")
    public R add(@RequestBody ${entity} ${entity?uncap_first})
    {
    return toOk(${(table.serviceName?substring(1))?uncap_first}.insert${entity}(${entity?uncap_first}));
    }

    /**
    * 修改${table.comment!}模块
    */
    @PreAuthorize("@ss.hasPermi('${entity}:${entity}:edit')")
    @Log(title = "${table.comment!}模块", businessType = BusinessType.UPDATE)
    @PutMapping("edit")
    @ApiOperation(value = " 修改${table.comment!}模块",notes = "修改${table.comment!}模块")
    public R edit(@RequestBody ${entity} ${entity?uncap_first})
    {
    return toOk(${(table.serviceName?substring(1))?uncap_first}.update${entity}(${entity?uncap_first}));
    }

    /**
    * 删除${table.comment!}模块
    */
    @PreAuthorize("@ss.hasPermi('${entity}:${entity}:remove')")
    @Log(title = "${table.comment!}模块", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    @ApiOperation(value = "删除${table.comment!}模块",notes = "删除${table.comment!}模块")
    public R remove(@RequestBody List<String> ids)
    {
    return toOk(${(table.serviceName?substring(1))?uncap_first}.delete${entity}ByIds(ids));
    }

}
</#if>
