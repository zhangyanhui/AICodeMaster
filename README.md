# AI Code Plugin for IntelliJ IDEA

AI Code Master 是一款集成了 AI 功能的 IntelliJ IDEA
插件，旨在帮助开发者更高效地生成有意义的代码提交信息，解释代码功能，提供优化建议等。插件采用了现代化的界面设计，仿照通义灵码的交互模式，让开发者可以在熟悉的环境中获得
AI 辅助。


## 功能特点

- **智能提交信息生成**：基于代码变更自动生成符合规范的提交信息
- **代码解释与理解**：分析代码并提供清晰易懂的功能解释
- **优化建议**：获取关于代码性能、可读性和安全性的改进建议
- **交互式对话界面**：与 AI 进行多轮对话，深入探讨问题
- **代码模板快速选择**：内置常用问题模板，快速发起请求
- **实时响应反馈**：直观的加载状态和结果展示

## 安装方法

1. 打开 IntelliJ IDEA
2. 进入 `File > Settings > Plugins` (Windows/Linux) 或 `IntelliJ IDEA > Preferences > Plugins` (Mac)
3. 点击 `Marketplace` 选项卡
4. 搜索 "AI Code Master"
5. 点击 `Install` 安装插件
6. 安装完成后点击 `Restart IDE` 重启 IDE

## 使用方法

1. 安装并重启 IDE 后，在右侧工具栏找到 "AI Code Master" 图标并点击
2. 在弹出的面板中，上方文本框输入你的问题或需求
3. 可以选择使用预设模板快速填充常见问题
4. 点击 `提交问题` 按钮发送请求
5. 等待 AI 处理，查看下方结果区域的回复
6. 可以复制结果、生成代码或对结果进行评价

## 界面介绍

插件界面主要分为三个部分：

1. **顶部导航栏**：包含插件图标、名称和设置按钮
2. **问题输入区域**：用于输入问题或选择预设模板
3. **对话历史区域**：展示提问和回答的历史记录，支持代码高亮显示

## 配置说明

在使用插件前，需要配置 AI 服务：

1. 点击设置图标打开配置面板
2. 输入你的 API 密钥
3. 选择你想使用的 AI 模型
4. 配置其他可选参数，如温度、最大响应长度等
5. 点击保存完成配置

## 示例

以下是一个使用 AI Commit 生成提交信息的示例：

```
main:docs: 更新 README.md 并调整图片引用

更新 README.md 文件，新增智能问答和代码优化示例的图片引用：
- 替换 img_4.png 的说明内容
- 添加 img_6.png 作为代码优化示例的展示

确保文档更准确地反映项目功能与示例。
```

## 智能问答的示例
![img.png](/images/img_4.png)
## 选中代码优化示例
![img.png](/images/img_6.png)


## 贡献与反馈

如果你在使用过程中遇到问题或有改进建议，请提交 issue 到我们的 GitHub
仓库：[https://github.com/zhangyanhui/AICodeMaster.git)

我们欢迎任何形式的贡献，包括但不限于：

- 提交 bug 报告
- 提出功能建议
- 贡献代码
- 改进文档

## 许可证

本项目采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证。

## 联系我们

如果你在使用过程中遇到任何问题，或者有任何建议和意见，欢迎通过以下方式联系我们：

- 邮箱：[yohannzhang@qq.com]

## 作者的其他项目，欢迎体验

- 公众号：[诗在盛唐]
  ![img.png](/images/img.png)
- 小程序：[诗在盛唐]
  ![img_2.png](/images/img_2.png)
- 小程序：[不用上班倒计时]
  ![img_3.png](/images/img_3.png)
- 小程序：[模拟手机来电]
  ![img_5.png](/images/img_5.png)

## 鸣谢

本项目是在以下开源项目上进行的改造，在此表示感谢：

- (https://github.com/HMYDK/AIGitCommit.git)

