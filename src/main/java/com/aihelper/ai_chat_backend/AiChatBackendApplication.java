package com.aihelper.ai_chat_backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aihelper.ai_chat_backend.mapper")  // 扫描 mapper 包
public class AiChatBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiChatBackendApplication.class, args);
    }
}