package com.yohannzhang.aigit.util;

import com.yohannzhang.aigit.config.ApiKeySettings;
import com.yohannzhang.aigit.constant.Constants;

/**
 * PromptUtil
 *
 * @author hmydk
 */
public class PromptUtil {

    public static final String DEFAULT_PROMPT_1 = getDefaultPrompt();
    public static final String DEFAULT_PROMPT_2 = getPrompt3();
    public static final String DEFAULT_PROMPT_3 = getPrompt4();

    public static String constructPrompt(String diff) {
        String promptContent = "";

        // get prompt content
        ApiKeySettings settings = ApiKeySettings.getInstance();
        if (Constants.PROJECT_PROMPT.equals(settings.getPromptType())) {
            promptContent = FileUtil.loadProjectPrompt();
        } else {
            promptContent = settings.getCustomPrompt().getPrompt();
        }

        // check prompt content
        if (!promptContent.contains("{diff}")) {
            throw new IllegalArgumentException("The prompt file must contain the placeholder {diff}.");
        }
        if (!promptContent.contains("{language}")) {
            throw new IllegalArgumentException("The prompt file must contain the placeholder {language}.");
        }

        // replace placeholder
        promptContent = promptContent.replace("{diff}", diff);
        promptContent = promptContent.replace("{language}", settings.getCommitLanguage());
        return promptContent;
    }

    private static String getPrompt4() {
        return """
                You are a Git commit message generation expert. Please analyze the following code changes and generate a clear, standardized commit message in {language}.
                
                Code changes:
                {diff}
                
                Requirements for the commit message:
                1. First line should start with one of these types:
                   feat: (new feature)
                   fix: (bug fix)
                   docs: (documentation)
                   style: (formatting)
                   refactor: (code refactoring)
                   perf: (performance)
                   test: (testing)
                   chore: (maintenance)
                
                2. First line should be no longer than 72 characters
                
                3. After the first line, leave one blank line and provide detailed explanation if needed:
                   - Why was this change necessary?
                   - How does it address the issue?
                   - Any breaking changes?
                
                4. Use present tense
                
                Please output only the commit message, without any additional explanations.
                """;
    }

    private static String getDefaultPrompt() {
        return """
                你是一个Git提交信息生成专家。请分析以下代码变更并生成清晰、标准化的提交信息（{language}）。
                
                代码变更：
                {diff}
                
                提交信息要求：
                1. 首行必须以以下类型开头：
                   feat:（新功能）
                   fix:（修复缺陷）
                   docs:（文档）
                   style:（格式调整）
                   refactor:（代码重构）
                   perf:（性能优化）
                   test:（测试）
                   chore:（维护）
                2. 首行长度不得超过72个字符。
                3. 如需详细说明，请说明：
                   - 此变更为何必要？
                   - 如何解决问题？
                   - 是否有破坏性变更？
                4. 使用现在时态。
                5. 请仅输出提交信息，不包含其他内容。
                6. commit message 中须在最后标注 【AI辅助】*。

                示例（Java）：
                fix: 修复用户登录时的空指针异常

                此变更解决了在用户未提供密码时抛出的NullPointerException。
                通过在验证逻辑中添加空值检查，确保系统在异常输入下仍能正常运行。

                【AI辅助】*
                """;
    }

    private static String getPrompt3() {
        return """
                 Generate a concise yet detailed git commit message using the following format and information:
                
                 <type>(<scope>): <subject>
                
                 <body>
                
                 <footer>
                
                 Use the following placeholders in your analysis:
                 - diff begin ：
                 {diff}
                 - diff end.
                
                 Guidelines:
                
                 1. <type>: Commit type (required)
                    - Use standard types: feat, fix, docs, style, refactor, perf, test, chore
                
                 2. <scope>: Area of impact (required)
                    - Briefly mention the specific component or module affected
                
                 3. <subject>: Short description (required)
                    - Summarize the main change in one sentence (max 50 characters)
                    - Use the imperative mood, e.g., "add" not "added" or "adds"
                    - Don't capitalize the first letter
                    - No period at the end
                
                 4. <body>: Detailed description (required)
                    - Explain the motivation for the change
                    - Describe the key modifications (max 3 bullet points)
                    - Mention any important technical details
                    - Use the imperative mood
                
                 5. <footer>: (optional)
                    - Note any breaking changes
                    - Reference related issues or PRs
                
                 Example:
                
                 feat(user-auth): implement two-factor authentication
                
                 • Add QR code generation for 2FA setup
                 • Integrate Google Authenticator API
                 • Update user settings for 2FA options
                
                 Notes:
                 - Keep the entire message under 300 characters
                 - Focus on what and why, not how
                 - Summarize diff to highlight key changes; don't include raw diff output
                
                Note: The whole result should be given in {language} and the final result must not contain ‘```’
                """;
    }
}
