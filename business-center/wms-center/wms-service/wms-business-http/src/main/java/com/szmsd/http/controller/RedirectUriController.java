package com.szmsd.http.controller;

import com.alibaba.fastjson.JSON;
import com.szmsd.common.core.domain.R;
import com.szmsd.http.config.DomainTokenConfig;
import com.szmsd.http.config.DomainTokenValue;
import com.szmsd.http.plugins.AbstractDomainToken;
import com.szmsd.http.plugins.DomainToken;
import com.szmsd.http.utils.RedirectUriUtil;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Api(tags = {"HTTP回调接收接口"})
@ApiSort(10000)
@RestController
@RequestMapping("/api/redirect/uri")
public class RedirectUriController {
    private final Logger logger = LoggerFactory.getLogger(RedirectUriController.class);

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private DomainTokenConfig domainTokenConfig;
    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping
    @ApiOperation(value = "HTTP回调接收接口 - #1", position = 100)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "state", value = "回调状态"),
            @ApiImplicitParam(name = "code", value = "授权校验码", required = true)
    })
    public R<String> redirectUri(@RequestParam(value = "state") String state,
                                 @RequestParam(value = "code") String code) {
        logger.info("回调参数state：{}", state);
        logger.info("回调参数code：{}",code);
        logger.info("回调已进来");
        String wrapRedirectUriKey = RedirectUriUtil.wrapRedirectUriKey(state);
        // 设置到缓存中，有效期1小时
        this.redisTemplate.opsForValue().set(wrapRedirectUriKey, code, 1, TimeUnit.HOURS);
        return R.ok(state);
    }

    @GetMapping(value = {"/token/config"})
    @ApiOperation(value = "HTTP回调接收接口 - #2", position = 200)
    public R<String> tokenConfig() {
        return R.ok(JSON.toJSONString(domainTokenConfig.getValues()));
    }

    @GetMapping(value = {"/token/clear"})
    @ApiOperation(value = "HTTP回调接收接口 - #21", position = 210)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "domain", value = "domain")
    })
    public R<Object> tokenClear(@RequestParam(value = "domain") String domain) {
        if (StringUtils.isEmpty(domain)) {
            return R.failed("domain Cannot be empty");
        }
        this.logger.info("请求参数：{}", domain);
        DomainTokenValue domainTokenValue = domainTokenConfig.getToken(domain);
        if (null == domainTokenValue) {
            return R.failed("No configuration，" + domain);
        }
        String domainToken = domainTokenValue.getDomainToken();
        try {
            DomainToken bean = this.applicationContext.getBean(domainToken, DomainToken.class);

            String accessTokenKey = bean.accessTokenKey();
            this.logger.info("accessTokenKey: {}", accessTokenKey);
            String wrapAccessTokenKey = RedirectUriUtil.wrapAccessTokenKey(accessTokenKey);
            this.logger.info("wrapAccessTokenKey: {}", wrapAccessTokenKey);
            Boolean aBoolean = this.redisTemplate.delete(wrapAccessTokenKey);

            String refreshTokenKey = bean.refreshTokenKey();
            this.logger.info("refreshTokenKey: {}", refreshTokenKey);
            String wrapRefreshTokenKey = RedirectUriUtil.wrapRefreshTokenKey(refreshTokenKey);
            this.logger.info("wrapRefreshTokenKey: {}", wrapRefreshTokenKey);
            Boolean aBoolean1 = this.redisTemplate.delete(wrapRefreshTokenKey);

            Map<String, Object> map = new HashMap<>();
            map.put("accessTokenKey", aBoolean);
            map.put("refreshTokenKey", aBoolean1);
            return R.ok(map);
        } catch (Exception e) {
            return R.failed("Execution failed，error message：" + e.getMessage());
        }
    }

    @PostMapping(value = {"/token/setRefreshToken"})
    @ApiOperation(value = "HTTP回调接收接口 - #21", position = 210)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "domain", value = "domain")
    })
    public R<Object> setRefreshToken(@RequestParam(value = "domain") String domain,
                                     @RequestParam(value = "refreshToken") String refreshToken) {
        if (StringUtils.isEmpty(domain)) {
            return R.failed("domain Cannot be empty");
        }
        this.logger.info("请求参数：{}", domain);
        DomainTokenValue domainTokenValue = domainTokenConfig.getToken(domain);
        if (null == domainTokenValue) {
            return R.failed("No configuration，" + domain);
        }
        String domainToken = domainTokenValue.getDomainToken();
        try {
            DomainToken bean = this.applicationContext.getBean(domainToken, DomainToken.class);

            String refreshTokenKey = bean.refreshTokenKey();
            this.logger.info("refreshTokenKey: {}", refreshTokenKey);
            String wrapRefreshTokenKey = RedirectUriUtil.wrapRefreshTokenKey(refreshTokenKey);
            this.logger.info("wrapRefreshTokenKey: {}", wrapRefreshTokenKey);
            this.redisTemplate.opsForValue().set(wrapRefreshTokenKey, refreshToken);

            Map<String, Object> map = new HashMap<>();
            map.put("refreshTokenKey", refreshTokenKey);
            map.put("wrapRefreshTokenKey", wrapRefreshTokenKey);
            return R.ok(map);
        } catch (Exception e) {
            return R.failed("Execution failed，error message：" + e.getMessage());
        }
    }

    @PostMapping(value = {"/token/setAccessToken"})
    @ApiOperation(value = "HTTP回调接收接口 - #21", position = 210)
    public R<Object> setAccessToken(@RequestParam(value = "key") String key,
                                     @RequestParam(value = "accessToken") String accessToken) {

        try {

            this.logger.info("setAccessToken: {}", accessToken);
            String accessTokenKey = RedirectUriUtil.wrapAccessTokenKey(key);
            this.logger.info("setAccessToken: {}", accessTokenKey);
            this.redisTemplate.opsForValue().set(key, accessToken);

            Map<String, Object> map = new HashMap<>();
            map.put("accessTokenKey", key);
            map.put("accessToken", accessToken);
            return R.ok(map);
        } catch (Exception e) {
            return R.failed("Execution failed，error message：" + e.getMessage());
        }
    }

    @GetMapping(value = {"/token/info"})
    @ApiOperation(value = "HTTP回调接收接口 - #3", position = 300)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "domain", value = "domain")
    })
    public R<Object> getTokenInfo(@RequestParam(value = "domain") String domain) {
        if (StringUtils.isEmpty(domain)) {
            return R.failed("domain Cannot be empty");
        }
        this.logger.info("请求参数：{}", domain);
        DomainTokenValue domainTokenValue = domainTokenConfig.getToken(domain);
        if (null == domainTokenValue) {
            return R.failed("No configuration，" + domain);
        }
        String domainToken = domainTokenValue.getDomainToken();
        try {
            DomainToken bean = this.applicationContext.getBean(domainToken, DomainToken.class);
            if (bean instanceof AbstractDomainToken) {
                AbstractDomainToken abstractDomainToken = (AbstractDomainToken) bean;
                abstractDomainToken.setDomain(domain);
                abstractDomainToken.setDomainTokenValue(domainTokenValue);
            }
            String tokenName = bean.getTokenName();
            String tokenValue = bean.getTokenValue();
            Map<String, Object> map = new HashMap<>();
            map.put("tokenName", tokenName);
            map.put("tokenValue", tokenValue);
            return R.ok(map);
        } catch (Exception e) {
            return R.failed("Execution failed，error message：" + e.getMessage());
        }
    }
}
