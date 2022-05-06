package com.szmsd.auth.config;

import com.szmsd.common.core.constant.CacheConstants;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.constant.SecurityConstants;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.handler.CustomWebResponseExceptionTranslator;
import com.szmsd.common.security.service.RedisClientDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 认证服务配置
 *
 * @author szmsd
 */
@Configuration
@EnableAuthorizationServer
public class AuthServerConfig extends AuthorizationServerConfigurerAdapter
{
    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private DataSource dataSource;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Resource
    private UserDetailsService userDetailsService;

    @Resource
    private TokenEnhancer tokenEnhancer;

    /**
     * 定义授权和令牌端点以及令牌服务
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints)
    {
        endpoints
                // 请求方式
                .allowedTokenEndpointRequestMethods(HttpMethod.GET, HttpMethod.POST)
                // 指定token存储位置
                .tokenStore(tokenStore())
                // 自定义生成令牌
                .tokenEnhancer(tokenEnhancer)
                // 用户账号密码认证
                .userDetailsService(userDetailsService)
                // 指定认证管理器
                .authenticationManager(authenticationManager)
                // 是否重复使用 refresh_token
                .reuseRefreshTokens(true)
                .addInterceptor(new DocHandlerInterceptor())
                // 自定义异常处理
                .exceptionTranslator(new CustomWebResponseExceptionTranslator());
    }

    /**
     * 配置令牌端点(Token Endpoint)的安全约束
     */
    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer)
    {
        oauthServer.allowFormAuthenticationForClients()
                .tokenKeyAccess("permitAll()")
                .checkTokenAccess("permitAll()")
                .allowFormAuthenticationForClients();
    }

    /**
     * 声明 ClientDetails实现
     */
    public RedisClientDetailsService clientDetailsService()
    {
        RedisClientDetailsService clientDetailsService = new RedisClientDetailsService(dataSource);
        return clientDetailsService;
    }

    /**
     * 配置客户端详情
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception
    {
        clients.withClientDetails(clientDetailsService());
    }

    /**
     * 基于 Redis 实现，令牌保存到缓存
     */
    @Bean
    public TokenStore tokenStore()
    {
        RedisTokenStore tokenStore = new RedisTokenStore(redisConnectionFactory);
        tokenStore.setPrefix(CacheConstants.OAUTH_ACCESS);
        return tokenStore;
    }

    /**
     * 自定义生成令牌
     */
    @Bean
    public TokenEnhancer tokenEnhancer()
    {
        return new TokenEnhancer()
        {
            @Override
            public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication)
            {
                if (accessToken instanceof DefaultOAuth2AccessToken)
                {
                    DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) accessToken;
                    LoginUser user = (LoginUser) authentication.getUserAuthentication().getPrincipal();
                    Map<String, Object> additionalInformation = new LinkedHashMap<String, Object>();
                    additionalInformation.put(SecurityConstants.DETAILS_USERNAME, authentication.getName());
                    additionalInformation.put(SecurityConstants.DETAILS_USER_ID, user.getUserId());
                    additionalInformation.put("code", HttpStatus.SUCCESS);
                    additionalInformation.put("sellerCode", user.getSellerCode());
                    additionalInformation.put("allDataScope", user.isAllDataScope());
                    additionalInformation.put("permissions", user.getPermissions());
                    token.setAdditionalInformation(additionalInformation);
                }
                return accessToken;
            };
        };
    }
}
