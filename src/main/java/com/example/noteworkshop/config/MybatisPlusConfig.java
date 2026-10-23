package com.example.noteworkshop.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.noteworkshop.mapper")
public class MybatisPlusConfig {
}