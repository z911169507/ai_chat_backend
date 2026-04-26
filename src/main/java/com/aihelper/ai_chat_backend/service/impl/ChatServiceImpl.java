package com.aihelper.ai_chat_backend.service.impl;

import com.aihelper.ai_chat_backend.entity.Conversation;
import com.aihelper.ai_chat_backend.entity.Message;
import com.aihelper.ai_chat_backend.mapper.ConversationMapper;
import com.aihelper.ai_chat_backend.mapper.MessageMapper;
import com.aihelper.ai_chat_backend.service.AiService;
import com.aihelper.ai_chat_backend.service.ChatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {
    private final AiService aiService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    // 手动构造器（Lombok 还没好）
    public ChatServiceImpl(ConversationMapper conversationMapper, MessageMapper messageMapper, AiService aiService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.aiService = aiService;
    }
    @Override
    @Transactional
    public Message sendMessage(Long conversationId, String userMessage) {
        // 1. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 2. 调用真实 AI 服务
        Message aiMsg = aiService.chat(conversationId, userMessage);
        aiMsg.setConversationId(conversationId);  // AiService 已设置，此处确保
        messageMapper.insert(aiMsg);
        return aiMsg;
    }
    @Override
    @Transactional
    public Conversation createConversation(Long userId, String systemPrompt, String content) {
        // 1. 创建对话
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(systemPrompt);
        conversationMapper.insert(conversation);

        // 2. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conversation.getId());
        userMsg.setRole("system");
        userMsg.setContent(content);
        messageMapper.insert(userMsg);

        // 3. 模拟 AI 回复（后续会对接真实 AI）
//        Message aiMsg = aiService.chat(conversation.getId(), content);
//        aiMsg.setConversationId(conversation.getId());  // 确保关联
//        messageMapper.insert(aiMsg);
//你好
        return conversation;
    }
    @Override
    public SseEmitter sendMessageStream(Long userId, Long conversationId, String userMessage) {
        // 1. 校验对话归属
        Conversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );
        if (conv == null) {
            throw new RuntimeException("对话不存在或无权访问");
        }

        // 2. 保存用户消息到数据库
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 3. 创建 SseEmitter，超时时间设为 3 分钟
        SseEmitter emitter = new SseEmitter(180000L);

        // 4. 异步调用流式 AI
        aiService.chatStream(conversationId, userMessage, emitter);

        // 注意：AI回复的保存需要等流结束后再插入，这里先略过，后续优化
        return emitter;
    }
    @Override
    public List<Conversation> getConversations(Long userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getCreateTime);
        return conversationMapper.selectList(wrapper);
    }

    @Override
    public List<Message> getMessages(Long conversationId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime);
        return messageMapper.selectList(wrapper);
    }
}