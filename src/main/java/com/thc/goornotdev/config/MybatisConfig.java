package com.thc.goornotdev.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.thc.goornotdev.mapper")
public class MybatisConfig {
}
