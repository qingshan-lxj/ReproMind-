package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Chat service for paper reproduction and experiment diagnosis.
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired
    private QueryLogsTools queryLogsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.35, 3000, 0.85);
    }

    public String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        systemPromptBuilder.append("你是 ReproPilot，一个论文复现与实验诊断助手。")
                .append("你擅长阅读论文实验设置、对比复现配置、分析训练日志、检查实验指标，并定位复现失败原因。\n");
        systemPromptBuilder.append("当用户询问论文方法、目标结果、实验设置、数据集、baseline、超参数或复现指南时，优先使用 queryPaperDocs。\n");
        systemPromptBuilder.append("当用户询问 loss、accuracy、F1、AUC、GPU 显存、训练耗时、收敛情况或结果差距时，使用 queryExperimentMetrics。\n");
        systemPromptBuilder.append("当用户询问报错、训练中断、CUDA OOM、数据划分、seed、配置告警或训练日志时，使用 queryExperimentLogs。\n");
        systemPromptBuilder.append("回答必须基于工具返回的论文片段、指标和日志证据，不能编造论文结果、实验配置或日志内容。\n");
        systemPromptBuilder.append("如果证据不足，要明确说明缺少哪些材料，例如 target_paper、reproduction_guide、experiment_config、training_log 或 metrics_log。\n");
        systemPromptBuilder.append("诊断类回答要给出：现象、证据、可能根因、验证方法和下一步实验建议。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，可以使用 getCurrentDateTime 工具。\n\n");

        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上规则和对话历史，回答用户的新问题。");

        return systemPromptBuilder.toString();
    }

    public Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
    }

    public ToolCallback[] getToolCallbacks() {
        // ReproPilot only needs local method tools. Keeping unrelated MCP tools
        // enabled can distract the agent with stale AIOps/cloud-log actions.
        return new ToolCallback[0];
    }

    public void logAvailableTools() {
        logger.info("External MCP tools disabled for ReproPilot; using local reproduction diagnosis tools.");
    }

    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("repro_pilot_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("Executing ReproPilot.call()");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReproPilot chat completed, answer length: {}", answer.length());
        return answer;
    }
}
