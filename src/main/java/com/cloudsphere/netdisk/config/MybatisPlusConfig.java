package com.cloudsphere.netdisk.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MybatisPlusConfig {

    /**
     * 手动为 Spring Boot 4.x 容器注入 MyBatis-Plus 专属的 SqlSessionFactory
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        // 注意：因为使用的是 MyBatis-Plus，必须使用 MybatisSqlSessionFactoryBean
        // 如果误用原生的 SqlSessionFactoryBean，会导致 BaseMapper 里的方法（如 selectOne）失效
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();

        // 将 Spring Boot 4 从 application.yml 里完美解析出的数据源（DataSource）手动灌进去
        factoryBean.setDataSource(dataSource);

        return factoryBean.getObject();
    }
}