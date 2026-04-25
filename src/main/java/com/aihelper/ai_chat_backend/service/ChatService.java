package com.aihelper.ai_chat_backend.service;

import com.aihelper.ai_chat_backend.entity.Conversation;
import com.aihelper.ai_chat_backend.entity.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {
    // 创建新对话（同时存入第一条用户消息，并返回 AI 模拟回复）
    Conversation createConversation(Long userId, String title, String firstMessage);
    SseEmitter sendMessageStream(Long userId, Long conversationId, String userMessage);
    // 获取用户的所有对话列表
    List<Conversation> getConversations(Long userId);
    // 在已有对话中发送用户消息，并返回AI的回复消息
    Message sendMessage(Long conversationId, String userMessage);
    // 获取某个对话的所有消息
    List<Message> getMessages(Long conversationId);
}