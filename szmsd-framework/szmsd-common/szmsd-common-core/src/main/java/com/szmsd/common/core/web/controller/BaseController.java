package com.szmsd.common.core.web.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.ApiException;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.exception.com.SystemException;
import com.szmsd.common.core.exception.com.UnauthorizedException;
import com.szmsd.common.core.utils.*;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.utils.sql.SqlUtil;
import com.szmsd.common.core.web.page.PageDomain;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.core.web.page.TableSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.CollectionUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.security.auth.login.LoginException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyEditorSupport;
import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * web?????????????????????
 *
 * @author szmsd
 */
public class BaseController {
    protected static final Logger log = LoggerFactory.getLogger(BaseController.class);

    /**
     * ?????????????????????
     */
    public static String getLen() {
        String len = ServletUtils.getHeaders("Langr");
        if (StringUtils.isEmpty(len)) {
            len = "zh";
        }
        return len;
    }

    /**
     * ??????????????????????????????????????????????????????????????????Date??????
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Date ????????????
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(DateUtils.parseDate(text));
            }
        });
    }

    /**
     * ????????????????????????
     */
    protected void startPage() {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        startPage(pageDomain);
    }

    protected void startPage(QueryDto queryDto) {
        PageDomain pageDomain = new PageDomain();
        pageDomain.setPageNum(queryDto.getPageNum());
        pageDomain.setPageSize(queryDto.getPageSize());
        startPage(pageDomain);
    }

    private void startPage(PageDomain pageDomain) {
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        if (pageNum == null) {
            pageNum = 1;
            pageDomain.setPageNum(pageNum);
        }
        if (pageSize == null) {
            pageSize = 10;
            pageDomain.setPageSize(pageSize);
        }
        String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
        PageHelper.startPage(pageNum, pageSize, orderBy);
    }

    /**
     * ????????????????????????
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> TableDataInfo<T> getDataTable(List<T> list) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());//????????????list instanceof Page ?????????
        return rspData;
    }


    /**
     * ????????????????????????
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> TableDataInfo<T> getDataTable(List<T> list, String msg) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.WEB_MSG);
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());//????????????list instanceof Page ?????????
        rspData.setMsg(msg);
        return rspData;
    }

    /**
     * ???????????????????????????  ???list instanceOf Page ???
     *
     * @param pageInfo
     * @param t
     * @return
     */
    protected <T> TableDataInfo<T> getDataTable(PageInfo<T> pageInfo, Class<?> t) {
        pageInfo = Optional.ofNullable(pageInfo).orElse(new PageInfo());
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setTotal(pageInfo.getTotal());
        rspData.setRows(t == null ? CollectionUtils.isEmpty(pageInfo.getList()) ? new ArrayList() : pageInfo.getList()
                : BeanMapperUtil.mapList(pageInfo.getList(), t));
        return rspData;
    }

    protected <T> TableDataInfo<T> getDataTable(PageInfo<T> pageInfo) {
        return getDataTable(pageInfo, null);
    }

    /**
     * ??????????????????
     *
     * @param rows ????????????
     * @return ????????????
     */
    protected R toOk(int rows) {
        return rows > 0 ? R.ok() : R.failed();
    }

    /**
     * ????????????
     */
    @ExceptionHandler(LoginException.class)
    @ResponseBody
    public R handleLoginException(LoginException e) {
        log.error("???????????????????????? LoginException {}", e.getMessage(), e);
        return R.failed(HttpStatus.ERROR, e.getMessage());
    }

    //?????????????????????
    @ExceptionHandler({ApiException.class})
    @ResponseBody
    public R handleApiException(ApiException e) {
        log.error("????????????????????? ApiException {}", e);
//        LogisticsException logisticsException = LogisticsExceptionUtil.getException(ExceptionMessageEnum.FAIL, getLen());
        return R.failed(e.getCode(), e.getMessage());
    }

    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    @ExceptionHandler({CommonException.class})
    @ResponseBody
    public R<?> handleCommonException(Exception e) {
        log.error("?????????????????? CommonException: {}", e.getMessage(), e);
        int httpStatus = Integer.parseInt(((CommonException) e).getCode());
        return R.failed(httpStatus, ExceptionUtil.getRootErrorMseeage(e));
    }

    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public R<?> handleMethodArgumentNotValidException(Exception e) {
        log.error("?????????????????? MethodArgumentNotValidException: {}", e.getMessage(), e);
        int httpStatus = org.springframework.http.HttpStatus.BAD_REQUEST.value();
        return R.failed(httpStatus, ExceptionUtil.getRootErrorMseeage(e));
    }

    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    @ExceptionHandler({UnauthorizedException.class})
    @ResponseBody
    public R<?> handleUnauthorizedException(Exception e) {
        log.error("?????????????????? UnauthorizedException: {}", e.getMessage(), e);
        return R.failed(HttpStatus.UNAUTHORIZED, ExceptionUtil.getRootErrorMseeage(e));
    }

    //????????????????????????
    @ExceptionHandler({Exception.class})
    @ResponseBody
    public R<?> handleException(Exception e) {
        log.error("?????????????????? Exception: {}", e.getMessage(), e);
        int httpStatus;
        if (e instanceof SystemException) {
            httpStatus = Integer.parseInt(((SystemException) e).getCode());
        } else {
            httpStatus = HttpStatus.ERROR;
        }
        return R.failed(httpStatus, ExceptionUtil.getRootErrorMseeage(e));
    }

    /**
     * @return com.szmsd.inner.common.handler.ResponseEntity
     * @Author Mars
     * @Description //TODO ???????????????
     * @Date 2020/6/16
     * @Param [e]
     **/
    @ExceptionHandler({
            HttpMediaTypeNotAcceptableException.class,
            MissingServletRequestParameterException.class,
            HttpRequestMethodNotSupportedException.class
    })
    @ResponseBody
    public R server500(Exception e) {
        log.error("???????????????:", e);
        // LogisticsException logisticsException = LogisticsExceptionUtil.getException(ExceptionMessageEnum.REQUESTERROR, getLen());
        // return R.failed(HttpStatus.REQUEST_ERROR, logisticsException.getMessage() + ":" + ExceptionUtil.getRootErrorMseeage(e));
        return R.failed(HttpStatus.REQUEST_ERROR, ExceptionUtil.getRootErrorMseeage(e));
    }

    /**
     * @return com.szmsd.inner.common.handler.ResponseEntity
     * @Author Mars
     * @Description //TODO ???????????????
     * @Date 2020/6/16
     * @Param [ex]
     **/
    @ResponseBody
    @ExceptionHandler({RuntimeException.class,
            NullPointerException.class,
            ClassCastException.class,
            NoSuchMethodException.class,
            IndexOutOfBoundsException.class,
            HttpMessageNotReadableException.class,
            TypeMismatchException.class

    })
    public R runtimeExceptionHandler(Exception e) {
        log.error("???????????????:", e);
        // LogisticsException logisticsException = LogisticsExceptionUtil.getException(ExceptionMessageEnum.RUNERROR, getLen());
        // return R.failed(HttpStatus.ERROR, logisticsException.getMessage()+":"+ExceptionUtil.getRootErrorMseeage(e));
        int httpStatus;
        if (e instanceof CommonException) {
            httpStatus = Integer.parseInt(((CommonException) e).getCode());
        } else if (e instanceof SystemException) {
            httpStatus = Integer.parseInt(((SystemException) e).getCode());
        } else {
            httpStatus = HttpStatus.ERROR;
        }
        return R.failed(httpStatus, ExceptionUtil.getRootErrorMseeage(e));
    }

    public void fileStreamWrite(HttpServletResponse response, FileStream fileStream) {
        response.setContentType("application/vnd.ms-excel");
        response.addHeader("Content-Disposition", fileStream.getContentDisposition());
        try (InputStream fis = new ByteArrayInputStream(fileStream.getInputStream()); BufferedInputStream bis = new BufferedInputStream(fis); OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[1024];
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                i = bis.read(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void excelExportTitle(HttpServletResponse response, List<String> rows, String fileName) {
        try (ExcelWriter excel = cn.hutool.poi.excel.ExcelUtil.getWriter(true);
             ServletOutputStream out = response.getOutputStream()) {
            excel.write(CollUtil.newArrayList(rows, ListUtil.empty()), true);
            //response???HttpServletResponse??????
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");
            excel.flush(out);
            //????????????????????????Servlet???
            IoUtil.close(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
