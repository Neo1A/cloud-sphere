package com.cloudsphere.netdisk.config;

import com.cloudsphere.netdisk.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    /**
     * 注册鉴权拦截器链
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                // 🎯 核心注入：刚性剔除这三个分享路由，允许外网匿名免密、免 Token 直接访问！
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/file/share/info/**",     // 🔓 放行获取分享元数据
                        "/file/share/verify",      // 🔓 放行验证提取口令
                        "/file/share/download/**"  // 🔓 放行匿名流式直连物理下载
                );
    }


    /**
     * 全局跨域规范配置，防止 Vue 3 发生 Axios 跨域阻断
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}