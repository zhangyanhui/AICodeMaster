package com.yohannzhang.aigit.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@State(
        name = "ChatHistoryStorage",
        storages = @Storage("AICodeMasterChatHistory.xml")
)
public class ChatHistoryService implements PersistentStateComponent<ChatHistoryService.State> {

    public void clearAll() {
    }

    public static class State {
        // 使用LinkedHashMap保持对话顺序
        public Map<String, String> chatHistoryMap = new LinkedHashMap<>();
    }

    private State state = new State();

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static ChatHistoryService getInstance() {
        return ApplicationManager.getApplication().getService(ChatHistoryService.class);
    }

    /**
     * 添加新的问答记录
     *
     * @param question 用户问题
     * @param answer   AI回答
     */
    public void addChatRecord(String question, String answer) {
        state.chatHistoryMap.put(question, answer);
    }

    /**
     * 获取不可修改的历史记录视图
     *
     * @return 只读的聊天历史Map
     */
    public Map<String, String> getChatHistory() {
        return Collections.unmodifiableMap(state.chatHistoryMap);
    }

    /**
     * 清空所有聊天记录
     */
    public void clearChatHistory() {
        state.chatHistoryMap.clear();
    }

    /**
     * 删除指定问题的记录
     *
     * @param question 要删除的问题
     * @return 是否成功删除
     */
    public boolean removeChatRecord(String question) {
        return state.chatHistoryMap.remove(question) != null;
    }

    /**
     * 获取最近N条聊天记录
     *
     * @param limit 最大记录数
     * @return 截取后的Map
     */
    public Map<String, String> getRecentChatHistory(int limit) {
        if (limit <= 0 || state.chatHistoryMap.isEmpty()) {
            return Collections.emptyMap();
        }

        return state.chatHistoryMap.entrySet().stream()
                .limit(limit)
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        Map::putAll);
    }

}
