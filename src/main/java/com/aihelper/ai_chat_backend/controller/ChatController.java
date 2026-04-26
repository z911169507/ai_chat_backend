package com.aihelper.ai_chat_backend.controller;

import com.aihelper.ai_chat_backend.entity.Conversation;
import com.aihelper.ai_chat_backend.entity.Message;
import com.aihelper.ai_chat_backend.service.ChatService;
import com.aihelper.ai_chat_backend.utils.JwtUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    public ChatController(ChatService chatService, JwtUtil jwtUtil) {
        this.chatService = chatService;
        this.jwtUtil = jwtUtil;
    }

    // 从请求头中获取当前用户ID
    private Long getUserIdFromHeader(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getUserIdFromToken(token);
    }

//    @PostMapping("/send-stream/{conversationId}")
//    public SseEmitter sendMessageStream(@RequestHeader("Authorization") String authHeader,
//                                        @PathVariable Long conversationId,
//                                        @RequestBody Map<String, String> body) {
//
//        // 0L 表示永不超时；AI 回答可能很慢，建议不设超时或设 5 分钟
//        SseEmitter emitter = new SseEmitter(0L);
//
//        // 异步执行，避免阻塞 Tomcat 线程
//        new Thread(() -> {
//            try {
//                // ========== 示例 1：模拟逐字输出 ==========
//                String reply = "这是一个模拟的流式回复，用于测试前端效果。";
//                for (int i = 0; i < reply.length(); i++) {
//                    String chunk = String.valueOf(reply.charAt(i));
//                    emitter.send(SseEmitter.event().data(chunk));
//                    Thread.sleep(50); // 模拟延迟，生产环境去掉
//                }
//
//                // ========== 示例 2：调用第三方 AI 流式 API（如 OpenAI）==========
//                // openAiClient.stream(userMessage, chunk -> {
//                //     try {
//                //         emitter.send(SseEmitter.event().data(chunk));
//                //     } catch (IOException e) {
//                //         emitter.completeWithError(e);
//                //     }
//                // });
//
//                // 可选：发送结束标记，前端可识别 [DONE] 做收尾
//                emitter.send(SseEmitter.event().data("[DONE]"));
//                emitter.complete();
//
//            } catch (Exception e) {
//                emitter.completeWithError(e);
//            }
//        }).start();
//
//        return emitter;
//    }
    @PostMapping("/send-stream/{conversationId}")
    public SseEmitter sendMessageStream(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromHeader(authHeader);
        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("消息不能为空"); // 由全局异常处理
        }
        return chatService.sendMessageStream(userId, conversationId, userMessage);
    }
    // 创建新对话
    @PostMapping("/conversation")
    public Map<String, Object> createConversation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromHeader(authHeader);
        String systemPrompt = body.get("system");
        String content = body.get("content");

//        if (firstMessage == null || firstMessage.isBlank()) {
//            return Map.of("code", 400, "message", "消息不能为空");
//        }
        Conversation conversation = chatService.createConversation(userId, systemPrompt, content);
        return Map.of("code", 200, "message", "创建成功", "conversationId", conversation.getId());
    }

    // 获取对话列表
    @GetMapping("/conversations")
    public Map<String, Object> getConversations(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        List<Conversation> list = chatService.getConversations(userId);
        return Map.of("code", 200, "data", list);
    }
    // 在已有对话中发送新消息
    @PostMapping("/send/{conversationId}")
    public Map<String, Object> sendMessage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body) {
        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of("code", 400, "message", "消息不能为空");
        }
        // 此处可以校验 conversationId 是否属于该用户，防止越权，我们稍后加强
        Message aiMessage = chatService.sendMessage(conversationId, userMessage);
        return Map.of("code", 200, "message", "发送成功", "data", aiMessage);
    }
    // 获取某个对话的消息列表
    @GetMapping("/messages/{conversationId}")
    public Map<String, Object> getMessages(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long conversationId) {
        List<Message> messages = chatService.getMessages(conversationId);
        return Map.of("code", 200, "data", messages);
    }
}