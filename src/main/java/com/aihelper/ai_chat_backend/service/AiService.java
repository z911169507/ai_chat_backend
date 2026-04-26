package com.aihelper.ai_chat_backend.service;

import java.util.Objects;
import com.aihelper.ai_chat_backend.entity.Message;
import com.aihelper.ai_chat_backend.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class AiService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final MessageMapper messageMapper;

    public AiService(ObjectMapper objectMapper, WebClient.Builder webClientBuilder, MessageMapper messageMapper) {
        this.restClient = RestClient.create("http://localhost:11434");
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl("http://localhost:11434").build();
        this.messageMapper = messageMapper;   // 新增这一行
    }

    public void chatStream(Long conversationId, String userMessage, SseEmitter emitter) {
        // 1. 查历史消息
        List<Message> historyMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
        );

        // 2. 组装完整的 messages 列表
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (Message msg : historyMessages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messagesList.add(m);
        }
        for (Map<String, Object> stringObjectMap : messagesList) {
            System.out.println(stringObjectMap.get("content"));
        }
        // 追加当前用户消息
//        Map<String, Object> currentMsg = new HashMap<>();
//        currentMsg.put("role", "user");
//        currentMsg.put("content", userMessage);
//        messagesList.add(currentMsg);


        // 3. 构建请求体（使用完整历史）
        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-r1:8b",
                "messages", messagesList,
                "stream", true
        );
        // 用于收集完整的思考与回答，以备不时之需
        StringBuilder thinkingBuilder = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();
        boolean[] contentStarted = {false}; // 标记是否已经开始输出 content

        webClient.post()
                .uri("/api/chat")
                // 修复点：使用 Objects.requireNonNull 消除 Null type safety 警告
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(Objects.requireNonNull(requestBody))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnComplete(() -> {
                    // 流正常结束：保存最终的消息
                    String finalThinking = thinkingBuilder.isEmpty() ? null : thinkingBuilder.toString();
                    String finalContent = contentBuilder.toString();

                    Message aiMsg = new Message();
                    aiMsg.setConversationId(conversationId);
                    aiMsg.setRole("assistant");
                    aiMsg.setThinking(finalThinking);
                    aiMsg.setContent(finalContent);
                    messageMapper.insert(aiMsg);

                    emitter.complete();
                })
                .doOnError(emitter::completeWithError)
                .subscribe(chunk -> {
                    try {
                        // 解析收到的 JSON 块
                        JsonNode root = objectMapper.readTree(chunk);
                        if (root.has("message")) {
                            JsonNode msg = root.get("message");
                            if (msg.has("content")) {
                                String contentPart = msg.get("content").asText();
                                if (!contentPart.isEmpty()) {
                                    contentStarted[0] = true;
                                    contentBuilder.append(contentPart);
                                }
                            }
                            if (msg.has("thinking")) {
                                String thinkPart = msg.get("thinking").asText();
                                if (!thinkPart.isEmpty()) {
                                    thinkingBuilder.append(thinkPart);
                                }
                            }
                        }
                        // 将原始块转发给前端
                        emitter.send(SseEmitter.event().data(Objects.requireNonNull(chunk)));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
    }
    // 调用 Ollama 的 /api/chat 接口，返回 AI 消息（含 thinking 和 content）
    public Message chat(Long conversationId, String userMessage) {
        // 1. 查历史消息
        List<Message> historyMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreateTime)
        );

        // 2. 组装消息列表
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (Message msg : historyMessages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messagesList.add(m);
        }
        Map<String, Object> currentMsg = new HashMap<>();
        currentMsg.put("role", "system");
        currentMsg.put("content", userMessage);
        messagesList.add(currentMsg);

        // 3. 构建请求体
        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-r1:8b",
                "messages", messagesList,
                "stream", false
        );

        String responseJson = restClient.post()
                .uri("/api/chat")
                // 修复点：使用 Objects.requireNonNull 消除 Null type safety 警告
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .body(Objects.requireNonNull(requestBody))
                .retrieve()
                .body(String.class);

        // 解析响应（保持不变）
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode msgNode = root.get("message");
            String content = msgNode.get("content").asText("");
            String thinking = msgNode.has("thinking") ? msgNode.get("thinking").asText() : null;

            Message aiMsg = new Message();
            aiMsg.setConversationId(conversationId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(content);
            aiMsg.setThinking(thinking);
            return aiMsg;
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 响应失败", e);
        }
    }
 }


