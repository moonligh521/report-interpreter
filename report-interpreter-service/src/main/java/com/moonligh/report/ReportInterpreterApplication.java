package com.moonligh.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "com.moonligh.report", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class ReportInterpreterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportInterpreterApplication.class, args);
    }
}
