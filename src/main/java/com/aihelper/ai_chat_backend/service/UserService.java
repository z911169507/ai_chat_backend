package com.aihelper.ai_chat_backend.service;

public interface UserService {
    void register(String username, String password);
    String login(String username, String password);  // 返回 JWT 令牌
}