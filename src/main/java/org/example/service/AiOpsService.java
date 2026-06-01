package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.dto.AIOpsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Multi-agent paper reproduction diagnosis service.
 *
 * The class name is kept to minimize controller changes. Its behavior is now
 * aligned with the original AIOps pattern: metrics + logs + docs -> root cause.
 */
@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired
    private QueryLogsTools queryLogsTools;

    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks)
            throws GraphRunnerException {
        return executeAiOpsAnalysis(chatModel, toolCallbacks, null);
    }

    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks,
                                                       AIOpsRequest request)
            throws GraphRunnerException {
        logger.info("Starting multi-agent paper reproduction diagnosis workflow");

        ReactAgent plannerAgent = buildPlannerAgent(chatModel);
        ReactAgent executorAgent = buildExecutorAgent(chatModel);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("repro_diagnosis_supervisor")
                .description("Coordinates repro_diagnosis_planner and repro_diagnosis_executor")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = buildTaskPrompt(request);

        logger.info("Invoking reproduction diagnosis supervisor");
        return supervisorAgent.invoke(taskPrompt);
    }

    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("Extracting final reproduction diagnosis report");

        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("Extracted final report from planner_plan, length: {}", reportText.length());
            return Optional.of(reportText);
        }

        Optional<String> plannerTextOutput = state.value("planner_plan")
                .map(Object::toString)
                .filter(text -> !text.isBlank());

        if (plannerTextOutput.isPresent()) {
            String reportText = plannerTextOutput.get();
            logger.info("Extracted text report from planner_plan fallback, length: {}", reportText.length());
            return Optional.of(reportText);
        }

        logger.warn("Unable to extract final report from planner_plan");
        return Optional.empty();
    }

    private String buildTaskPrompt(AIOpsRequest request) {
        String userRequest = valueOrDefault(request == null ? null : request.getUserRequest(), "自动诊断当前论文复现实验为什么没有达到论文报告结果");
        String runId = valueOrDefault(request == null ? null : request.getRunId(), "repro-run-001");
        String symptom = valueOrDefault(request == null ? null : request.getSymptom(), "low_accuracy_or_result_gap");
        String targetMetric = valueOrDefault(request == null ? null : request.getTargetMetric(), "accuracy");
        String currentMetric = valueOrDefault(request == null ? null : request.getCurrentMetric(), "unknown");
        String focus = valueOrDefault(request == null ? null : request.getFocus(), "paper_config_metrics_logs");

        return String.format("""
                你正在执行一项论文复现实验诊断任务。请围绕已上传的目标论文、复现指南、实验配置、
                训练日志和指标记录，按照“规划 -> 执行 -> 再规划 -> 最终报告”的闭环完成诊断。

                用户诊断目标：%s
                实验 Run ID：%s
                主要症状：%s
                关注指标：%s
                当前观测值：%s
                诊断关注点：%s

                目标：
                1. 检索目标论文的任务、数据集、baseline、指标、超参数和报告结果。
                2. 查询当前复现实验的指标，确认 loss、accuracy、F1、显存、收敛和结果差距。
                3. 查询训练日志和错误日志，查找配置差异、数据划分、seed、OOM、NaN、早停等证据。
                4. 对比论文目标结果和当前实验结果，定位可能根因。
                5. 输出《论文复现实验诊断报告》。

                严格要求：
                - 必须基于 queryPaperDocs、queryExperimentMetrics、queryExperimentLogs 返回的真实证据。
                - 不允许编造论文结果、实验指标、日志错误或配置项。
                - 如果材料不足，最终报告必须明确列出缺失材料和无法判断项。
                """, userRequest, runId, symptom, targetMetric, currentMetric, focus);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel) {
        return ReactAgent.builder()
                .name("repro_diagnosis_planner")
                .description("Plans paper reproduction diagnosis steps and writes the final diagnosis report")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(new ToolCallback[0])
                .outputKey("planner_plan")
                .build();
    }

    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel) {
        return ReactAgent.builder()
                .name("repro_diagnosis_executor")
                .description("Executes one reproduction diagnosis step planned by the planner")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(new ToolCallback[0])
                .outputKey("executor_feedback")
                .build();
    }

    private Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
    }

    private String buildPlannerPrompt() {
        return """
                你是 repro_diagnosis_planner，同时承担 Replanner 角色。

                你的职责：
                1. 读取当前任务 {input} 和 Executor 的最近反馈 {executor_feedback}。
                2. 将论文复现实验诊断拆成可执行步骤，每一步只要求 Executor 完成一个明确任务。
                3. 执行阶段输出 JSON，必须包含：
                   - decision: PLAN | EXECUTE | FINISH
                   - step: 当前步骤描述
                   - tool: 预期使用的工具，必须是 queryPaperDocs、queryExperimentMetrics 或 queryExperimentLogs 之一
                   - query: 具体检索词或诊断症状
                   - context: 已知证据和下一步意图
                4. 推荐诊断顺序：
                   - 查论文目标结果、实验设置、数据集、baseline、指标和超参数
                   - 查当前实验指标，确认结果差距、收敛、显存和训练耗时
                   - 查训练日志，确认配置差异、数据划分、seed、OOM、NaN、早停等证据
                   - 汇总证据，判断可能根因和下一轮实验计划
                5. 如果连续查不到关键材料，不要编造，转入 FINISH 并说明缺失材料。

                FINISH 时必须直接输出 Markdown，不要输出 JSON、分隔线、emoji 或重复标题。
                排版必须规范：
                - 每个标题独占一行，标题前后保留空行。
                - 每个列表项独占一行，避免把多个结论挤在同一段。
                - 表格前后保留空行，表格列数必须和表头一致。
                - 每个小节最多 3-5 条高价值结论，优先写证据充分的内容。
                - 不要输出“正在分析”“以下是报告”等过程性文本。

                最终报告模板：

                # 论文复现实验诊断报告

                ## 1. 总体结论
                - 复现状态：
                - 主要差距：
                - 最可能根因：
                - 证据充分性：

                ## 2. 论文目标与当前结果对比
                | 项目 | 论文目标/设置 | 当前实验 | 差距或风险 |
                |---|---|---|---|
                | 数据集 |  |  |  |
                | Baseline |  |  |  |
                | 指标 |  |  |  |
                | 超参数 |  |  |  |
                | 最终结果 |  |  |  |

                ## 3. 指标异常分析
                - Loss 收敛：
                - 验证/测试指标：
                - 显存与耗时：
                - 其他异常信号：

                ## 4. 日志证据
                - 配置差异：
                - 数据划分/seed：
                - 运行时错误：
                - 训练过程告警：

                ## 5. 可能根因排序
                1.
                2.
                3.

                ## 6. 修复建议

                ## 7. 下一轮实验计划

                ## 8. 缺失材料或无法判断项

                所有判断必须能回到工具证据；没有证据时写“材料不足，无法判断”。
                """;
    }

    private String buildExecutorPrompt() {
        return """
                你是 repro_diagnosis_executor。

                你的职责：
                1. 读取 Planner 最新输出 {planner_plan}，只执行其中的第一个明确步骤。
                2. 根据 planner 指定的 tool 调用：
                   - queryPaperDocs：查论文、复现指南、实验配置、相关文档
                   - queryExperimentMetrics：查 loss、accuracy、F1、显存、训练耗时、目标结果差距
                   - queryExperimentLogs：查训练日志、错误日志、配置告警、数据划分、seed、OOM、NaN
                3. 不要直接生成最终诊断报告，只返回结构化执行反馈。
                4. 不允许编造检索不到的论文内容、指标、日志或配置。

                返回 JSON：
                {
                  "status": "SUCCESS | NO_RESULTS | FAILED",
                  "executedStep": "实际执行的步骤",
                  "tool": "实际调用的工具",
                  "query": "实际使用的检索词或症状",
                  "summary": "证据摘要",
                  "evidence": ["关键证据"],
                  "risks": ["当前证据暴露的风险或不足"],
                  "nextHint": "建议 Planner 下一步检查的方向"
                }
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是 repro_diagnosis_supervisor，负责调度 repro_diagnosis_planner 和 repro_diagnosis_executor。

                调度规则：
                1. 需要拆解任务、决定下一步或生成最终报告时，调用 repro_diagnosis_planner。
                2. 当 planner 输出 decision=EXECUTE 时，调用 repro_diagnosis_executor 执行该步骤。
                3. 根据 executor_feedback 再调用 planner，直到 planner 输出 decision=FINISH。
                4. FINISH 后，最终输出必须是完整 Markdown《论文复现实验诊断报告》。
                5. 如果关键材料缺失或工具查不到结果，必须让 planner 在最终报告中如实说明。

                只允许在 planner、executor 和 FINISH 之间做选择。
                """;
    }
}
