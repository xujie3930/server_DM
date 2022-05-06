package com.szmsd.common.core.web.page;

import com.szmsd.common.core.constant.HttpStatus;

import java.io.Serializable;
import java.util.List;

/**
 * 表格分页数据对象
 *
 * @author szmsd
 */
public class TableDataInfo<T> implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 列表数据 */
    private List<T> rows;

    /** 消息状态码 */
    private int code;

    /** 消息内容 */
    private String msg;

    /**
     * 其余数据
     */
    private Object otherData;

    /**
     * 表格数据对象
     */
    public TableDataInfo()
    {
    }

    /**
     * 分页
     *
     * @param list 列表数据
     * @param total 总记录数
     */
    public TableDataInfo(List<T> list, int total)
    {
        this.rows = list;
        this.total = total;
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }
    public List<T> getRows()
    {
        return rows;
    }

    public void setRows(List<T> rows)
    {
        this.rows = rows;
    }

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMsg()
    {
        return msg;
    }

    public void setMsg(String msg)
    {
        this.msg = msg;
    }

    public Object getOtherData() {
        return otherData;
    }

    public void setOtherData(Object otherData) {
        this.otherData = otherData;
    }

    public static <T> TableDataInfo<T> convert(PageVO<T> pageVO) {
        TableDataInfo<T> tableDataInfo = new TableDataInfo<>();
        tableDataInfo.setCode(HttpStatus.SUCCESS);
        tableDataInfo.setTotal(pageVO.getTotalRecords());
        tableDataInfo.setRows(pageVO.getData());
        return tableDataInfo;
    }

}