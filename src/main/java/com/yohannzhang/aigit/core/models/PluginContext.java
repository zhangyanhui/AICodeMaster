package com.yohannzhang.aigit.core.models;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

public interface PluginContext {
    /**
     * 获取项目根目录路径
     */
    Path getProjectPath();

    /**
     * 获取输出流
     */
    PrintStream getOutput();

    /**
     * 注册插件命令
     * @param command 命令名称
     * @param handler 命令处理器
     */
    void registerCommand(String command, BiConsumer<PluginContext, Map<String, String>> handler);

    /**
     * 获取插件配置
     */
    Map<String, String> getConfig();

    /**
     * 获取插件数据目录
     */
    Path getDataPath();
} 