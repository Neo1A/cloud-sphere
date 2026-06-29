package com.cloudsphere.netdisk.config;

import com.cloudsphere.netdisk.interceptor.GlobalLogInterceptor;
import com.cloudsphere.netdisk.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final GlobalLogInterceptor globalLogInterceptor;
    private final JwtInterceptor jwtInterceptor;

    /**
     * 注册鉴权与审计拦截器链
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 1. 🚀 第一道防线：全局流量审计拦截器。拦截所有路径，没有 exclude 盲区！
        registry.addInterceptor(globalLogInterceptor)
                .addPathPatterns("/**")
                .order(1); // 👑 优先级设定为 1，确保最先切入，最后执行 afterCompletion

        // 2. 🔐 第二道防线：受控业务身份鉴权拦截器。
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/api/debug/**",           // 🔧 调试接口白名单（生产环境需移除）
                        "/file/share/info/**",     // 🔓 放行获取分享元数据
                        "/file/share/verify",      // 🔓 放行验证提取口令
                        "/file/share/download/**"  // 🔓 放行匿名流式直连物理下载
                )
                .order(2); // 优先级设为 2，紧随全局拦截器之后执行
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