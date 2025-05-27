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
        return "You are a Git commit message generation expert. Please analyze the following code changes and generate a clear, standardized commit message in {language}.\n"
                + "\n"
                + "Code changes:\n"
                + "{diff}\n"
                + "\n"
                + "Requirements for the commit message:\n"
                + "1. First line should start with one of these types:\n"
                + "   feat: (new feature)\n"
                + "   fix: (bug fix)\n"
                + "   docs: (documentation)\n"
                + "   style: (formatting)\n"
                + "   refactor: (code refactoring)\n"
                + "   perf: (performance)\n"
                + "   test: (testing)\n"
                + "   chore: (maintenance)\n"
                + "\n"
                + "2. First line should be no longer than 72 characters\n"
                + "\n"
                + "3. After the first line, leave one blank line and provide detailed explanation if needed:\n"
                + "   - Why was this change necessary?\n"
                + "   - How does it address the issue?\n"
                + "   - Any breaking changes?\n"
                + "\n"
                + "4. Use present tense\n"
                + "\n"
                + "Please output only the commit message, without any additional explanations.\n";
    }

    private static String getDefaultPrompt() {
        return "You are an AI assistant tasked with generating a Git commit message based on the provided code changes. Your goal is to create a clear, concise, and informative commit message that follows best practices.\n"
                + "\n"
                + "Input:\n"
                + "- Code diff:\n"
                + "```\n"
                + "{diff}\n"
                + "```\n"
                + "\n"
                + "Instructions:\n"
                + "1. Analyze the provided code diff and branch name.\n"
                + "2. Generate a commit message following this format:\n"
                + "   - First line: A short, imperative summary (50 characters or less)\n"
                + "   - Blank line\n"
                + "   - Detailed explanation (if necessary), wrapped at 72 characters\n"
                + "3. The commit message should:\n"
                + "   - Be clear and descriptive\n"
                + "   - Use the imperative mood in the subject line (e.g., \"Add feature\" not \"Added feature\")\n"
                + "   - Explain what and why, not how\n"
                + "   - Reference relevant issue numbers if applicable\n"
                + "4. Avoid:\n"
                + "   - Generic messages like \"Bug fix\" or \"Update file.txt\"\n"
                + "   - Mentioning obvious details that can be seen in the diff\n"
                + "\n"
                + "Output:\n"
                + "- Provide only the commit message, without any additional explanation or commentary.\n"
                + "\n"
                + "Output Structure:\n"
                + "<type>[optional scope]: <description>\n"
                + "[optional body]\n"
                + "Example:\n"
                + "   feat(api): add endpoint for user authentication\n"
                + "Possible scopes (examples, infer from diff context):\n"
                + "- api: app API-related code\n"
                + "- ui: user interface changes\n"
                + "- db: database-related changes\n"
                + "- etc.\n"
                + "Possible types:\n"
                + "   - fix, use this if you think the code fixed something\n"
                + "   - feat, use this if you think the code creates a new feature\n"
                + "   - perf, use this if you think the code makes performance improvements\n"
                + "   - docs, use this if you think the code does anything related to documentation\n"
                + "   - refactor, use this if you think that the change is simple a refactor but the functionality is the same\n"
                + "   - test, use this if this change is related to testing code (.spec, .test, etc)\n"
                + "   - chore, use this for code related to maintenance tasks, build processes, or other non-user-facing changes. It typically includes tasks that don't directly impact the functionality but are necessary for the project's development and maintenance.\n"
                + "   - ci, use this if this change is for CI related stuff\n"
                + "   - revert, use this if im reverting something\n"
                + "\n"
                + "Note: The whole result should be given in {language} and the final result must not contain ‘```’\n";
    }

    private static String getPrompt3() {
        return "Generate a concise yet detailed git commit message using the following format and information:\n"
                + "\n"
                + "<type>(<scope>): <subject>\n"
                + "\n"
                + "<body>\n"
                + "\n"
                + "<footer>\n"
                + "\n"
                + "Use the following placeholders in your analysis:\n"
                + "- diff begin ：\n"
                + "{diff}\n"
                + "- diff end.\n"
                + "\n"
                + "Guidelines:\n"
                + "\n"
                + "1. <type>: Commit type (required)\n"
                + "   - Use standard types: feat, fix, docs, style, refactor, perf, test, chore\n"
                + "\n"
                + "2. <scope>: Area of impact (required)\n"
                + "   - Briefly mention the specific component or module affected\n"
                + "\n"
                + "3. <subject>: Short description (required)\n"
                + "   - Summarize the main change in one sentence (max 50 characters)\n"
                + "   - Use the imperative mood, e.g., \"add\" not \"added\" or \"adds\"\n"
                + "   - Don't capitalize the first letter\n"
                + "   - No period at the end\n"
                + "\n"
                + "4. <body>: Detailed description (required)\n"
                + "   - Explain the motivation for the change\n"
                + "   - Describe the key modifications (max 3 bullet points)\n"
                + "   - Mention any important technical details\n"
                + "   - Use the imperative mood\n"
                + "\n"
                + "5. <footer>: (optional)\n"
                + "   - Note any breaking changes\n"
                + "   - Reference related issues or PRs\n"
                + "\n"
                + "Example:\n"
                + "\n"
                + "feat(user-auth): implement two-factor authentication\n"
                + "\n"
                + "• Add QR code generation for 2FA setup\n"
                + "• Integrate Google Authenticator API\n"
                + "• Update user settings for 2FA options\n"
                + "\n"
                + "Notes:\n"
                + "- Keep the entire message under 300 characters\n"
                + "- Focus on what and why, not how\n"
                + "- Summarize diff to highlight key changes; don't include raw diff output\n"
                + "\n"
                + "Note: The whole result should be given in {language} and the final result must not contain ‘```’\n";
    }
}
