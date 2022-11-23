package com.szmsd.http.service.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.alibaba.fastjson.serializer.ValueFilter;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.FileStream;
import com.szmsd.common.core.utils.HttpClientHelper;
import com.szmsd.common.core.utils.HttpResponseBody;
import com.szmsd.http.annotation.LogIgnore;
import com.szmsd.http.config.BusinessAsyncUncaughtExceptionHandler;
import com.szmsd.http.config.HttpConfig;
import com.szmsd.http.config.inner.DefaultApiConfig;
import com.szmsd.http.config.inner.UrlGroupConfig;
import com.szmsd.http.config.inner.api.ApiConfig;
import com.szmsd.http.config.inner.url.UrlApiConfig;
import com.szmsd.http.config.inner.url.UrlConfig;
import com.szmsd.http.domain.HtpUrlGroup;
import com.szmsd.http.enums.HttpUrlType;
import com.szmsd.http.service.IHtpConfigService;
import com.szmsd.http.service.http.resolver.Actuator;
import com.szmsd.http.service.http.resolver.ActuatorParameter;
import com.szmsd.http.service.http.resolver.ResponseResolverActuatorParameter;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zhangyuyuan
 * @date 2021-04-13 15:03
 */
abstract class AbstractRequest extends BaseRequest {

    protected HttpConfig httpConfig;
    @Resource
    private IHtpConfigService iHtpConfigService;
    @Autowired
    private Actuator actuator;

    private final Logger logger = LoggerFactory.getLogger(AbstractRequest.class);

    @Autowired
    private InboundReceiptFeignService inboundReceiptFeignService;

    protected AbstractRequest(HttpConfig httpConfig) {
        this.httpConfig = httpConfig;
    }

    abstract HttpUrlType getHttpUrlType();

    String getUrlGroupName(String warehouseCode) {
        // 兼容处理，没有仓库编码调用默认映射规则
        String urlGroupName = null;
        if (this.isNotEmpty(warehouseCode)) {
            String matchWarehouseGroupName = null;
            Map<String, Set<String>> warehouseGroupMap = this.httpConfig.getWarehouseGroup();
            if (null != warehouseGroupMap) {
                Set<String> warehouseGroupNameSet = warehouseGroupMap.keySet();
                for (String warehouseGroupName : warehouseGroupNameSet) {
                    if (warehouseGroupMap.get(warehouseGroupName).contains(warehouseCode)) {
                        matchWarehouseGroupName = warehouseGroupName;
                        break;
                    }
                }
            }
            if (this.isNotEmpty(matchWarehouseGroupName)) {
                urlGroupName = this.httpConfig.getMapperGroup().get(matchWarehouseGroupName);
            }
        }
        if (this.isEmpty(urlGroupName)) {
            // 获取默认映射
            urlGroupName = this.httpConfig.getDefaultUrlGroup();
        }
        if (this.isEmpty(urlGroupName)) {
            throw new CommonException("999", "仓库编码[" + warehouseCode + "]未配置映射规则");
        }
        return urlGroupName;
    }

    UrlConfig getUrlConfig(String urlGroupName) {
        UrlGroupConfig urlGroupConfig = this.httpConfig.getUrlGroup().get(urlGroupName);
        if (null == urlGroupConfig) {
            throw new CommonException("999", "url group config cant be null");
        }
        HttpUrlType httpUrlType = this.getHttpUrlType();
        if (null == httpUrlType) {
            throw new CommonException("999", "http url type cant be null");
        }
        UrlConfig urlConfig = this.getUrlConfigAdapter(urlGroupConfig, httpUrlType);
        if (null == urlConfig) {
            HtpUrlGroup htpUrlGroup = iHtpConfigService.selectHtpUrlGroup(urlGroupName);
            throw new CommonException("999", htpUrlGroup.getGroupName() + "的[" + HttpUrlType.valueOf(httpUrlType.name()).getKey() + "]服务未配置");
        }
        return urlConfig;
    }

    UrlConfig getUrlConfigAdapter(UrlGroupConfig urlGroupConfig, HttpUrlType httpUrlType) {
        UrlConfig urlConfig = null;
        switch (httpUrlType) {
            case WMS:
                urlConfig = urlGroupConfig.getWms();
                break;
            case THIRD_PAYMENT:
                urlConfig = urlGroupConfig.getThirdPayment();
                break;
            case PRICED_PRODUCT:
                urlConfig = urlGroupConfig.getPricedProduct();
                break;
            case CARRIER_SERVICE:
                urlConfig = urlGroupConfig.getCarrierService();
                break;
            case PRODUCT_REMOTE_AREA:
                urlConfig = urlGroupConfig.getProductRemoteArea();
                break;
            case SRM:
                urlConfig = urlGroupConfig.getSrm();
                break;
            case PRICED:
                urlConfig = urlGroupConfig.getPriced();
                break;
        }
        return urlConfig;
    }

    ApiConfig getApiConfig(UrlConfig urlConfig) {
        ApiConfig apiConfig = null;
        if (urlConfig instanceof UrlApiConfig) {
            apiConfig = ((UrlApiConfig) urlConfig).getApi();
        }
        if (null == apiConfig) {
            DefaultApiConfig defaultApiConfig = this.httpConfig.getDefaultApiConfig();
            HttpUrlType httpUrlType = this.getHttpUrlType();
            switch (httpUrlType) {
                case WMS:
                    return defaultApiConfig.getWms();
                case THIRD_PAYMENT:
                    return defaultApiConfig.getThirdPayment();
                case PRICED_PRODUCT:
                    return defaultApiConfig.getPricedProduct();
                case CARRIER_SERVICE:
                    return defaultApiConfig.getCarrierService();
                case PRODUCT_REMOTE_AREA:
                    return defaultApiConfig.getProductRemoteArea();
                case SRM:
                    return defaultApiConfig.getSrm();
                case PRICED:
                    return defaultApiConfig.getPriced();
            }
        }
        return apiConfig;
    }

    String getApi(UrlConfig urlConfig, String api) {
        ApiConfig apiConfig = this.getApiConfig(urlConfig);
        if (null == apiConfig) {
            throw new CommonException("999", "api config is null");
        }
        // api
        // base-info.seller         ->>> baseInfo.seller
        // base-info.shipment-rule  ->>> baseInfo.shipmentRule
        // exception.processing     ->>> exception.processing
        if (api.contains("-")) {
            String[] strs = api.split("-");
            StringBuilder builder = new StringBuilder(strs[0]);
            for (int i = 1; i < strs.length; i++) {
                String str = strs[i];
                builder.append(str.substring(0, 1).toUpperCase())
                        .append(str.substring(1));
            }
            api = builder.toString();
        }
        String s = Utils.get(apiConfig, api);
        if (this.isEmpty(s)) {
            throw new CommonException("999", "api [" + api + "] is null");
        }
        return s;
    }

    HttpResponseBody httpResponseBodySingle(String warehouseCode, String urlGroupName, UrlConfig urlConfig, String api, Object object, HttpMethod httpMethod, Object... pathVariable) {
        return httpResponseBodySingle(warehouseCode, urlGroupName, urlConfig, api, object, null, httpMethod, pathVariable);
    }

    HttpResponseBody httpResponseBodySingle(String warehouseCode, String urlGroupName, UrlConfig urlConfig, String api, Object object, Integer timeout, HttpMethod httpMethod, Object... pathVariable) {
        //http://pricedproduct-internalapi.dmfcn.net
        String url = urlConfig.getUrl() + getApi(urlConfig, api);
        if (url.contains("{")) {
            url = MessageFormat.format(url, pathVariable);
        }
        String requestBody = JSON.toJSONString(object);
        Map<String, String> headerMap = urlConfig.getHeaders();
        Date requestTime = new Date();
        HttpResponseBody responseBody;
        if (HttpMethod.POST.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpPost(url, requestBody, headerMap, timeout);
        } else if (HttpMethod.PUT.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpPut(url, requestBody, headerMap);
        } else if (HttpMethod.DELETE.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpDelete(url, requestBody, headerMap);
        } else if (HttpMethod.GET.equals(httpMethod)) {
            //拼接成符合的格式

            responseBody = HttpClientHelper.httpGet(url, requestBody, headerMap);
        }else {
            throw new CommonException("999", "未处理的请求方式");
        }
        String logRequestBody;
        if (null != object && object.getClass().isAnnotationPresent(LogIgnore.class)) {
            LogIgnore logIgnore = object.getClass().getAnnotation(LogIgnore.class);
            String[] value = logIgnore.value();
            if (logIgnore.abbr()) {
                Set<String> fieldSet = Arrays.stream(value).collect(Collectors.toSet());
                logRequestBody = JSON.toJSONString(object, new ValueFilter() {
                    @Override
                    public Object process(Object o, String s, Object o1) {
                        if (Objects.isNull(o1)) {
                            return o1;
                        }
                        if (!fieldSet.contains(s)) {
                            return o1;
                        }
                        if (o1 instanceof String) {
                            return "String(length=" + ((String) o1).length() + ")";
                        } else if (o1 instanceof byte[]) {
                            return "String(byte[]=" + ((byte[]) o1).length + ")";
                        }
                        return "Object(No Math Java Bean Type [" + o1.getClass() + "])";
                    }
                });
            } else {
                SimplePropertyPreFilter filter = new SimplePropertyPreFilter();
                filter.getExcludes().addAll(Arrays.asList(value));
                logRequestBody = JSON.toJSONString(object, filter);
            }
        } else {
            logRequestBody = requestBody;
        }
        if (api.equals("inbound.create")&&HttpMethod.POST.equals(httpMethod)){
            Map inboundReceiptMap = JSONObject.parseObject(JSONObject.toJSONString(object), Map.class);
             if (responseBody.getStatus()==500){
                 inboundReceiptFeignService.updateInboundReceipt(String.valueOf(inboundReceiptMap.get("refOrderNo")));
             }


        }
        this.addLog(warehouseCode, urlGroupName, url, httpMethod.name(), headerMap, logRequestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
        return responseBody;
    }



    HttpResponseBody httpResponseBodySinglees(String warehouseCode, String urlGroupName, UrlConfig urlConfig, String api, Object object, Integer timeout, HttpMethod httpMethod, Object... pathVariable) {
        //http://pricedproduct-internalapi.dmfcn.net
        String url = urlConfig.getUrl() + getApi(urlConfig, api);
        if (url.contains("{")) {
            url = MessageFormat.format(url, object);
        }
        String requestBody = JSON.toJSONString(object);
        Map<String, String> headerMap = urlConfig.getHeaders();
        Date requestTime = new Date();
        HttpResponseBody responseBody;
        if (HttpMethod.POST.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpPost(url, requestBody, headerMap, timeout);
        } else if (HttpMethod.PUT.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpPut(url, requestBody, headerMap);
        } else if (HttpMethod.DELETE.equals(httpMethod)) {
            responseBody = HttpClientHelper.httpDelete(url, requestBody, headerMap);
        } else if (HttpMethod.GET.equals(httpMethod)) {
            //拼接成符合的格式

            responseBody = HttpClientHelper.httpGets(url, object.toString(), headerMap);
        }else {
            throw new CommonException("999", "未处理的请求方式");
        }
        String logRequestBody;
        if (null != object && object.getClass().isAnnotationPresent(LogIgnore.class)) {
            LogIgnore logIgnore = object.getClass().getAnnotation(LogIgnore.class);
            String[] value = logIgnore.value();
            if (logIgnore.abbr()) {
                Set<String> fieldSet = Arrays.stream(value).collect(Collectors.toSet());
                logRequestBody = JSON.toJSONString(object, new ValueFilter() {
                    @Override
                    public Object process(Object o, String s, Object o1) {
                        if (Objects.isNull(o1)) {
                            return o1;
                        }
                        if (!fieldSet.contains(s)) {
                            return o1;
                        }
                        if (o1 instanceof String) {
                            return "String(length=" + ((String) o1).length() + ")";
                        } else if (o1 instanceof byte[]) {
                            return "String(byte[]=" + ((byte[]) o1).length + ")";
                        }
                        return "Object(No Math Java Bean Type [" + o1.getClass() + "])";
                    }
                });
            } else {
                SimplePropertyPreFilter filter = new SimplePropertyPreFilter();
                filter.getExcludes().addAll(Arrays.asList(value));
                logRequestBody = JSON.toJSONString(object, filter);
            }
        } else {
            logRequestBody = requestBody;
        }
        this.addLog(warehouseCode, urlGroupName, url, httpMethod.name(), headerMap, logRequestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
        return responseBody;
    }


    boolean hasMultipleChannelUrlSet(String api) {
        String formatApi = Utils.formatApi(api);
        return this.httpConfig.getMultipleChannelUrlSet().contains(formatApi);
    }


    HttpResponseBody httpRequestBodyAdapter(String warehouseCode, String api, ReFunction<String, UrlConfig, HttpResponseBody> reFunction) {
        // 先调用自己的服务，自己的服务调用成功之后再去调用其它的服务
        String urlGroupName = this.getUrlGroupName(warehouseCode);
        UrlConfig urlConfig = this.getUrlConfig(urlGroupName);
        //调用前后增加日志,用于查看耗时
        logger.info("调用服务前:urlConfig:{},urlGroupName:{}", urlConfig.toString(), urlGroupName);
        HttpResponseBody httpResponseBody = reFunction.apply(urlGroupName, urlConfig);
        logger.info("调用服务后:{}", httpResponseBody.getBody());
        if (!this.hasMultipleChannelUrlSet(api)) {
            // 不是多通道api，直接返回结果集
            return httpResponseBody;
        }
        // 对结果集进行解析，判断是否返回成功
        // 如果返回是成功，返回这次请求的结果集，并且异步调用其它服务
        HttpUrlType httpUrlType = this.getHttpUrlType();
        if (null == httpUrlType) {
            throw new CommonException("999", "http url type cant be null");
        }
        // 如果是true，就推给多个服务器
        ActuatorParameter actuatorParameter = new ResponseResolverActuatorParameter(httpUrlType, httpResponseBody);
        if (this.actuator.execute(actuatorParameter)) {
            // 循环调用
            Map<String, UrlGroupConfig> urlGroup = this.httpConfig.getUrlGroup();
            for (Map.Entry<String, UrlGroupConfig> entry : urlGroup.entrySet()) {
                // 自己的不再调用
                if (urlGroupName.equals(entry.getKey())) {
                    continue;
                }
                // 线程池执行任务
                MultipleChannelRequest.run(() -> {
                    UrlGroupConfig urlGroupConfig = entry.getValue();
                    UrlConfig urlConfigAdapter = getUrlConfigAdapter(urlGroupConfig, httpUrlType);
                    logger.info("推送多服务器前:urlConfig:{},urlConfigAdapter:{}", entry.getKey(), urlConfigAdapter.toString());
                    reFunction.apply(entry.getKey(), urlConfigAdapter);
                    logger.info("推送多服务器后:urlConfig:{},urlConfigAdapter:{}", entry.getKey(), urlConfigAdapter.toString());
                });
            }
        }
        return httpResponseBody;
    }

    HttpResponseBody httpRequestBody(String warehouseCode, String api, Object object, HttpMethod httpMethod, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> httpResponseBodySingle(warehouseCode, urlGroupName, urlConfig, api, object, httpMethod, pathVariable));
    }
    HttpResponseBody httpRequestBody(String warehouseCode, String api, Object object, Integer timeout, HttpMethod httpMethod, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> httpResponseBodySingle(warehouseCode, urlGroupName, urlConfig, api, object, timeout, httpMethod, pathVariable));
    }


    HttpResponseBody httpRequestBodyes(String warehouseCode, String api, Object object, Integer timeout, HttpMethod httpMethod, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> httpResponseBodySinglees(warehouseCode, urlGroupName, urlConfig, api, object, timeout, httpMethod, pathVariable));
    }

    protected String httpRequest(String warehouseCode, String api, Object object, HttpMethod httpMethod, Object... pathVariable) {
        return this.httpRequestBody(warehouseCode, api, object, httpMethod, pathVariable).getBody();
    }

    protected String httpPost(String warehouseCode, String api, Object object) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.POST);
    }

    protected String httpPut(String warehouseCode, String api, Object object) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.PUT);
    }

    protected String httpDelete(String warehouseCode, String api, Object object) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.DELETE);
    }

    protected String httpPost(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.POST, pathVariable);
    }

    protected String httpPut(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.PUT, pathVariable);
    }

    protected String httpDelete(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequest(warehouseCode, api, object, HttpMethod.DELETE, pathVariable);
    }

    protected HttpResponseBody httpPostBody(String warehouseCode, String api, Object object) {
        return this.httpRequestBody(warehouseCode, api, object, HttpMethod.POST);
    }

    protected HttpResponseBody httpPostBody(String warehouseCode, String api, Object object, Integer timeout) {
        return this.httpRequestBody(warehouseCode, api, object, timeout, HttpMethod.POST);
    }

    protected HttpResponseBody httpGetBody(String warehouseCode, String api, Object object,  Object... pathVariable) {
        return this.httpRequestBody(warehouseCode, api, object, HttpMethod.GET, pathVariable);
    }

    protected HttpResponseBody httpGetBody(String warehouseCode, String api,Integer timeout, Object object, Object... pathVariable) {
        return this.httpRequestBody(warehouseCode, api, object,timeout,HttpMethod.GET, pathVariable);
    }

    protected HttpResponseBody httpGetBodyes(String warehouseCode, String api,Integer timeout, Object object, Object... pathVariable) {
        return this.httpRequestBodyes(warehouseCode, api, object,timeout,HttpMethod.GET, pathVariable);
    }

    protected HttpResponseBody httpPutBody(String warehouseCode, String api, Object object,  Object... pathVariable) {
        return this.httpRequestBody(warehouseCode, api, object, HttpMethod.PUT, pathVariable);
    }

    protected HttpResponseBody httpDeleteBody(String warehouseCode, String api, Object object,  Object... pathVariable) {
        return this.httpRequestBody(warehouseCode, api, object, HttpMethod.DELETE, pathVariable);
    }

    protected FileStream httpPostFile(String warehouseCode, String api, Object object) {
        Date requestTime = new Date();
        HttpResponseBody httpResponseBody = this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            Map<String, String> headerMap = urlConfig.getHeaders();
            String requestBody = JSON.toJSONString(object);
            HttpResponseBody httpResponseBody1 = HttpClientHelper.httpPostStream(url, headerMap, requestBody);
            addLog(warehouseCode, urlGroupName, url, HttpMethod.POST.name(), headerMap, requestBody, requestTime, "FileInputStream", httpResponseBody1.getStatus());
            return httpResponseBody1;
        });
        FileStream responseBody = null;
        if (httpResponseBody instanceof HttpResponseBody.HttpResponseByteArrayWrapper) {
            responseBody = new FileStream();
            HttpResponseBody.HttpResponseByteArrayWrapper httpResponseByteArrayWrapper = (HttpResponseBody.HttpResponseByteArrayWrapper) httpResponseBody;
            Header[] headers = httpResponseByteArrayWrapper.getHeaders();
            responseBody.setContentDisposition(this.getContentDisposition(headers));
            byte[] byteArray = httpResponseByteArrayWrapper.getByteArray();
            if (null != byteArray) {
                responseBody.setInputStream(byteArray);
            }
        }
        return responseBody;
    }

    private String getContentDisposition(Header[] headers) {
        if (ArrayUtils.isEmpty(headers)) {
            return null;
        }
        Header header = this.getHeader(headers, "Content-Disposition");
        if (null == header) {
            return null;
        }
        return header.getValue();
    }

    private Header getHeader(Header[] headers, String headerName) {
        for (Header header : headers) {
            if (null == header) {
                continue;
            }
            if (headerName.equals(header.getName())) {
                return header;
            }
        }
        return null;
    }

    protected HttpResponseBody httpGetFile(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            if (url.contains("{")) {
                url = MessageFormat.format(url, pathVariable);
            }
            Map<String, String> headerMap = urlConfig.getHeaders();
            Date requestTime = new Date();
            String requestBody = null;
            if (null != object) {
                requestBody = JSON.toJSONString(object);
            }
            HttpResponseBody httpResponseBody1 = HttpClientHelper.httpGetStream(url, headerMap, requestBody);
            StringBuilder responseBody = new StringBuilder();
            if (httpResponseBody1 instanceof HttpResponseBody.HttpResponseBodyEmpty) {
                responseBody.append("HttpResponseBodyEmpty{}");
            } else if (httpResponseBody1 instanceof HttpResponseBody.HttpResponseByteArrayWrapper) {
                HttpResponseBody.HttpResponseByteArrayWrapper httpResponseByteArrayWrapper = (HttpResponseBody.HttpResponseByteArrayWrapper) httpResponseBody1;
                responseBody.append("HttpResponseBodyEmpty{")
                        .append("status=").append(httpResponseByteArrayWrapper.getStatus())
                        .append("byteArrayLength=").append(httpResponseByteArrayWrapper.getByteArray().length)
                        .append("}");
            }
            addLog(warehouseCode, urlGroupName, url, HttpMethod.POST.name(), headerMap, requestBody, requestTime, responseBody.toString(), httpResponseBody1.getStatus());
            return httpResponseBody1;
        });
    }

    protected String httpPostMuFile(String warehouseCode, String api, Object object, MultipartFile file, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            if (url.contains("{")) {
                url = MessageFormat.format(url, pathVariable);
            }
            Map<String, String> headerMap = urlConfig.getHeaders();
            Date requestTime = new Date();
            String requestBody = JSON.toJSONString(object);
            HttpResponseBody responseBody = HttpClientHelper.httpPost(url, requestBody, file, headerMap);
            addLog(warehouseCode, urlGroupName, url, HttpMethod.PUT.name(), headerMap, requestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
            return responseBody;
        }).getBody();
    }

    protected HttpResponseBody httpPostFilter(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            if (url.contains("{")) {
                url = MessageFormat.format(url, pathVariable);
            }
            Map<String, String> headerMap = urlConfig.getHeaders();
            Date requestTime = new Date();
            String requestBody = JSON.toJSONString(object);
            HttpResponseBody responseBody = HttpClientHelper.httpPost(url, requestBody, headerMap);
            addLog(warehouseCode, urlGroupName, url, HttpMethod.PUT.name(), headerMap, requestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
            return responseBody;
        });
    }

    protected String httpPutMuFile(String warehouseCode, String api, Object object, MultipartFile file, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            if (url.contains("{")) {
                url = MessageFormat.format(url, pathVariable);
            }
            Map<String, String> headerMap = urlConfig.getHeaders();
            Date requestTime = new Date();
            String requestBody = JSON.toJSONString(object);
            HttpResponseBody responseBody = HttpClientHelper.httpPut(url, requestBody, file, headerMap);
            addLog(warehouseCode, urlGroupName, url, HttpMethod.PUT.name(), headerMap, requestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
            return responseBody;
        }).getBody();
    }

    protected String httpGet(String warehouseCode, String api, Object object, Object... pathVariable) {
        return this.httpRequestBodyAdapter(warehouseCode, api, (urlGroupName, urlConfig) -> {
            String url = urlConfig.getUrl() + getApi(urlConfig, api);
            if (url.contains("{")) {
                url = MessageFormat.format(url, pathVariable);
            }
            Map<String, String> headerMap = urlConfig.getHeaders();
            String requestBody = JSON.toJSONString(object);
            Date requestTime = new Date();
            HttpResponseBody responseBody = HttpClientHelper.httpGet(url, requestBody, headerMap);
            addLog(warehouseCode, urlGroupName, url, HttpMethod.GET.name(), headerMap, requestBody, requestTime, responseBody.getBody(), responseBody.getStatus());
            return responseBody;
        }).getBody();
    }

}
