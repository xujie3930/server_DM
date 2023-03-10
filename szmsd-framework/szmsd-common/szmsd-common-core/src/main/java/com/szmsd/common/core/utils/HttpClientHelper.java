package com.szmsd.common.core.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * @author Gao Junwen
 * @version 1.0
 * @Description HTTP??????(GET / POST)??????
 * @Created on 2020???8???28???
 * @since JDK1.8
 */
public class HttpClientHelper {

    private static final Logger log = LoggerFactory.getLogger(HttpClientHelper.class);

    private final CloseableHttpClient httpClient;

    private HttpClientHelper() {
        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

            }}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        if (null == sc) {
            throw new RuntimeException("init SSLContext error");
        }
        //???????????????????????????Socket??????
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                // SSLConnectionSocketFactory.getSystemSocketFactory();
                .register("https", new SSLConnectionSocketFactory(sc)).build();
        //HttpConnection???????????????????????????/?????????????????????
        HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connectionFactory = new
                ManagedHttpClientConnectionFactory(DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultHttpResponseParserFactory.INSTANCE);
        //DNS?????????
        DnsResolver dnsResolver = SystemDefaultDnsResolver.INSTANCE;

        //???????????????????????????
        PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, connectionFactory, dnsResolver);
        //?????????Socket??????
        SocketConfig defaultSocketConfig = SocketConfig.custom().setTcpNoDelay(true).build();
        poolConnManager.setDefaultSocketConfig(defaultSocketConfig);

        // ???????????????????????????????????????
        poolConnManager.setMaxTotal(1000);
        // ???????????????????????????????????????????????????????????????????????????DefaultMaxPerRoute?????????maxTotal????????????????????????
        // DefaultMaxPerRoute????????????????????????????????????ConnectPoolTimeoutException: Timeout waiting for connect from pool) ?????????maxTotal?????????
        //???????????????????????????
        poolConnManager.setDefaultMaxPerRoute(1000);
        //??????????????????????????????????????????????????????????????????????????????????????????2S
        poolConnManager.setValidateAfterInactivity(5 * 1000);

        //??????????????????
        RequestConfig requestConfig = RequestConfig.custom()
                //????????????????????????
                .setConnectTimeout(600 * 1000)
                //??????????????????????????????
                .setSocketTimeout(600 * 1000)
                //???????????????????????????????????????????????????
                .setConnectionRequestTimeout(20000)
                .build();

        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setConnectionManager(poolConnManager)
                //?????????????????????????????????
                .setConnectionManagerShared(false)
                //????????????????????????
                .evictIdleConnections(60, TimeUnit.SECONDS)
                //??????????????????
                .evictExpiredConnections()
                //??????????????????????????????????????????????????????????????????
                .setConnectionTimeToLive(60, TimeUnit.SECONDS)
                //????????????????????????
                .setDefaultRequestConfig(requestConfig)
                // ??????????????????????????????keepalive
                .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
                //??????????????????????????????????????????????????????
                .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
                //??????????????????????????????3??????????????????
                .setRetryHandler(new DefaultHttpRequestRetryHandler());

        httpClient = httpClientBuilder.build();

        //JVM????????????????????????????????????????????????
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    log.info(e.getMessage());
                }
            }
        });
    }

    public static synchronized CloseableHttpClient getHttpClient() {
        return HttpClientHelperInstance.INSTANCE.httpClient;
    }

    /**
     * ?????? http post ???????????????????????????????????????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param headerMap   ??????Header
     * @return ??????Body
     */
    public static HttpResponseBody httpPost(String url, String requestBody, Map<String, String> headerMap) {
        return execute(new HttpPost(url), requestBody, headerMap);
    }
    public static HttpResponseBody httpPost(String url, String requestBody, Map<String, String> headerMap, Integer timeout) {
        return execute(new HttpPost(url), requestBody, headerMap, timeout);
    }

    /**
     * ?????? http post ????????????????????????????????????
     *
     * @param url           ??????URL
     * @param requestParams ????????????
     * @param headerMap     ??????Header
     * @return ??????Body
     */
    public static HttpResponseBody httpPost(String url, Map<String, Object> requestParams, Map<String, String> headerMap) {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            String result = null;
            //??????http?????????
            setHeader(httpPost, headerMap);
            List<NameValuePair> paramList = new ArrayList<>();
            for (String key : requestParams.keySet()) {
                paramList.add(new BasicNameValuePair(key, String.valueOf(requestParams.get(key))));
            }
            HttpEntity httpEntity = new UrlEncodedFormEntity(paramList, "UTF-8");
            httpPost.setEntity(httpEntity);
            response = httpClient.execute(httpPost);
            int status = response.getStatusLine().getStatusCode();
            if (status == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity, "UTF-8");
                }
            } else {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity, "UTF-8");
                }
            }
            return new HttpResponseBody.HttpResponseBodyWrapper(status, result);
        } catch (Exception e) {
            try {
                if (null != response)
                    EntityUtils.consume(response.getEntity());
            } catch (IOException e1) {
                log.error(e.getMessage(), e1);
            }
            log.error(e.getMessage(), e);
        }
        return new HttpResponseBody.HttpResponseBodyEmpty();
    }

    /**
     * ?????? http put ??????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param headerMap   ??????Header
     * @return ??????Body
     */
    public static HttpResponseBody httpPut(String url, String requestBody, Map<String, String> headerMap) {
        return execute(new HttpPut(url), requestBody, headerMap);
    }

    /**
     * ?????? http delete ??????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param headerMap   ??????Header
     * @return ??????Body
     */
    public static HttpResponseBody httpDelete(String url, String requestBody, Map<String, String> headerMap) {
        return execute(new HttpDelete(url), requestBody, headerMap);
    }

    /**
     * ?????? http get ??????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param headerMap   ??????Header
     * @return ??????Body
     */
    public static HttpResponseBody httpGet(String url, String requestBody, Map<String, String> headerMap) {
        // get????????????
        if (null != requestBody) {
            StringBuilder paramsBuilder = new StringBuilder();
            if (url.lastIndexOf("?") == -1) {
                paramsBuilder.append("?");
            }
            JSONObject jsonObject = JSON.parseObject(requestBody);
            if (null != jsonObject) {
                for (String key : jsonObject.keySet()) {
                    try {
                        paramsBuilder.append(key)
                                .append("=")
                                .append(URLEncoder.encode(jsonObject.getString(key), "utf-8"))
                                .append("&");
                    } catch (UnsupportedEncodingException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                paramsBuilder.deleteCharAt(paramsBuilder.length() - 1);
                url = url + paramsBuilder.toString();
            }
        }
        log.error("??????token?????????,?????????????????????{}",url);
        return execute(new HttpGet(url), requestBody, headerMap);
    }

    public static HttpResponseBody httpGets(String url, String requestBody, Map<String, String> headerMap) {
        // get????????????
//        if (null != requestBody) {
//            StringBuilder paramsBuilder = new StringBuilder();
//            if (url.lastIndexOf("?") == -1) {
//                paramsBuilder.append("?");
//            }
//            JSONObject jsonObject = JSON.parseObject(requestBody);
//            if (null != jsonObject) {
//                for (String key : jsonObject.keySet()) {
//                    try {
//                        paramsBuilder.append(key)
//                                .append("=")
//                                .append(URLEncoder.encode(jsonObject.getString(key), "utf-8"))
//                                .append("&");
//                    } catch (UnsupportedEncodingException e) {
//                        log.error(e.getMessage(), e);
//                    }
//                }
//                paramsBuilder.deleteCharAt(paramsBuilder.length() - 1);
//                url = url + paramsBuilder.toString();
//            }
//        }
        log.error("??????token?????????,?????????????????????{}",url);
        return execute(new HttpGet(url), requestBody, headerMap);
    }



    public static String builderGetParams(String requestBody) {
        // get????????????
        if (null != requestBody) {
            StringJoiner joiner = new StringJoiner("&");
            JSONObject jsonObject = JSON.parseObject(requestBody);
            if (null != jsonObject) {
                for (String key : jsonObject.keySet()) {
                    try {
                        joiner.add(key + "=" + URLEncoder.encode(jsonObject.getString(key), "utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
            return joiner.toString();
        }
        return "";
    }
    public static HttpResponseBody execute(HttpEntityEnclosingRequestBase request, String requestBody, Map<String, String> headerMap) {
        return execute(request, requestBody, headerMap, null);
    }

    public static HttpResponseBody execute(HttpEntityEnclosingRequestBase request, String requestBody, Map<String, String> headerMap, Integer timeout) {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;
        try {
            String result = null;
            //??????http?????????
            setHeader(request, headerMap);
            setRaw(request, requestBody);
            if(timeout != null){
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(timeout).setSocketTimeout(timeout).build();
                request.setConfig(requestConfig);
            }


            response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            if (status == HttpStatus.SC_OK) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity, "UTF-8");
                }
            } else {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    result = EntityUtils.toString(entity, "UTF-8");
                }
                if (status == 500 && entity == null) {
                    result = "{\"errors\":\"??????????????????????????????\"}";
                }
            }
            return new HttpResponseBody.HttpResponseBodyWrapper(status, result);
        } catch (Exception e) {
            log.error("http??????"+e+":"+e.getMessage());
            log.error("e:"+e.toString()+",timeout:"+timeout);
            if((e.toString().equals("java.net.SocketTimeoutException: Read timed out") || e instanceof  org.apache.http.conn.ConnectTimeoutException)){
                return new HttpResponseBody.HttpResponseBodyWrapper(408, "{\"status\":408,\"Errors\":[{\"Code\":408,\"Message\":\"????????????\"}]}");
            }
            try {
                if (null != response)
                    EntityUtils.consume(response.getEntity());
            } catch (IOException e1) {
                log.error(e.getMessage(), e1);
            }
            log.error(e.getMessage(), e);
        }
        return new HttpResponseBody.HttpResponseBodyEmpty();
    }

    public static HttpResponseBody executeOnByteArray(HttpEntityEnclosingRequestBase request, String requestBody, Map<String, String> headerMap) {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;
        try {
            byte[] result = null;
            //??????http?????????
            setHeader(request, headerMap);
            setRaw(request, requestBody);
            response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                result = EntityUtils.toByteArray(entity);
            }
            Header[] headers = response.getAllHeaders();
            return new HttpResponseBody.HttpResponseByteArrayWrapper(status, headers, result);
        } catch (Exception e) {
            try {
                if (null != response)
                    EntityUtils.consume(response.getEntity());
            } catch (IOException e1) {
                log.error(e.getMessage(), e1);
            }
            log.error(e.getMessage(), e);
            return new HttpResponseBody.HttpResponseBodyEmpty(e.getMessage());
        }
    }

    public static HttpResponseBody httpPostStream(String url, Map<String, String> headerMap, String requestBody) {
        return execute(new HttpPost(url), headerMap, requestBody);
    }

    public static HttpResponseBody httpGetStream(String url, Map<String, String> headerMap, String requestBody) {
        return execute(new HttpGet(url), headerMap, requestBody);
    }

    public static HttpResponseBody execute(HttpEntityEnclosingRequestBase request, Map<String, String> headerMap, String requestBody) {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;
        try {
            //??????http?????????
            setHeader(request, headerMap);
            setRaw(request, requestBody);
            response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            Header[] headers = null;
            byte[] byteArray = null;
            if (entity != null) {
                headers = response.getAllHeaders();
                byteArray = IOUtils.toByteArray(entity.getContent());
            }
            return new HttpResponseBody.HttpResponseByteArrayWrapper(status, headers, byteArray);
        } catch (Exception e) {
            try {
                if (null != response)
                    EntityUtils.consume(response.getEntity());
            } catch (IOException e1) {
                log.error(e.getMessage(), e1);
            }
            log.error(e.getMessage(), e);
        }
        return new HttpResponseBody.HttpResponseBodyEmpty();
    }

    /**
     * ?????? http put ??????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param file        ??????
     * @param headerMap   ??????Header
     * @return
     */
    public static HttpResponseBody httpPut(String url, String requestBody, MultipartFile file, Map<String, String> headerMap) {
        return execute(new HttpPut(url), headerMap, requestBody, file);
    }

    /**
     * ?????? http post ??????
     *
     * @param url         ??????URL
     * @param requestBody ??????Body
     * @param file        ??????
     * @param headerMap   ??????Header
     * @return
     */
    public static HttpResponseBody httpPost(String url, String requestBody, MultipartFile file, Map<String, String> headerMap) {
        return execute(new HttpPost(url), headerMap, requestBody, file);
    }

    public static HttpResponseBody execute(HttpEntityEnclosingRequestBase request, Map<String, String> headerMap, String requestBody, MultipartFile file) {
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;
        try {
            String result = null;
            //??????http?????????
            setHeader(request, headerMap);
            setRaw(request, requestBody, file);
            response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                result = EntityUtils.toString(entity, "UTF-8");
            }
            return new HttpResponseBody.HttpResponseBodyWrapper(status, result);
        } catch (Exception e) {
            try {
                if (null != response)
                    EntityUtils.consume(response.getEntity());
            } catch (IOException e1) {
                log.error(e.getMessage(), e1);
            }
            log.error(e.getMessage(), e);
        }
        return new HttpResponseBody.HttpResponseBodyEmpty();
    }

    public static void setHeader(AbstractHttpMessage httpMessage, Map<String, String> headerMap) {
        if (null == headerMap) {
            return;
        }
        //??????http?????????
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            httpMessage.addHeader(entry.getKey(), String.valueOf(entry.getValue()));
        }
    }

    public static void setRaw(HttpEntityEnclosingRequestBase httpEntity, String requestBody) {
        if (null == requestBody) {
            return;
        }
        ByteArrayEntity byteArrayEntity = new ByteArrayEntity(requestBody.getBytes(StandardCharsets.UTF_8));
        byteArrayEntity.setContentType("application/json;charset=UTF-8");
        httpEntity.setEntity(byteArrayEntity);
    }

    public static void setRaw(HttpEntityEnclosingRequestBase httpEntity, String requestBody, MultipartFile file) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        try {
            String originalFilename = file.getOriginalFilename();
            builder.addBinaryBody(file.getName(), file.getInputStream(), ContentType.MULTIPART_FORM_DATA, originalFilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, String> params = JSON.parseObject(requestBody, Map.class);
        if (params != null) {
            for (String key : params.keySet()) {
                builder.addPart(key, new StringBody(params.get(key), ContentType.create("text/plain", Consts.UTF_8)));
            }
        }
        HttpEntity entity = builder.build();
        httpEntity.setEntity(entity);
    }

    private static class HttpClientHelperInstance {
        private static final HttpClientHelper INSTANCE = new HttpClientHelper();
    }

    public static class HttpDelete extends HttpEntityEnclosingRequestBase {
        public static final String METHOD_NAME = "DELETE";

        public HttpDelete() {
        }

        public HttpDelete(URI uri) {
            this.setURI(uri);
        }

        public HttpDelete(String uri) {
            this.setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }
    }

    public static class HttpGet extends HttpEntityEnclosingRequestBase {
        public static final String METHOD_NAME = "GET";

        public HttpGet() {
        }

        public HttpGet(URI uri) {
            this.setURI(uri);
        }

        public HttpGet(String uri) {
            this.setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }
    }

}
