# ReproMind

ReproMind 是一个面向论文复现和实验诊断场景的智能助手项目。项目后端基于 Spring Boot 构建，集成 DashScope 大模型、Spring AI Alibaba Agent Framework 和 Milvus 向量数据库，支持文档上传、向量检索、RAG 问答、工具调用、多 Agent 协作诊断和 SSE 流式输出。

简单来说，用户可以上传论文、复现指南、训练日志、指标记录等文件，系统会将文档切分并写入 Milvus 向量库。之后用户提问或触发自动诊断时，Agent 会调用本地工具检索相关证据，再结合大模型生成回答或诊断报告。

## 主要功能

- 文档上传：支持上传 `txt`、`md` 文件，并自动创建向量索引。
- RAG 问答：基于上传文档进行检索增强问答。
- 流式对话：通过 SSE 实时返回模型输出。
- 工具调用：Agent 可自动调用论文检索、指标检索、日志检索等工具。
- 多 Agent 诊断：通过 `SupervisorAgent` 调度 Planner 和 Executor，完成论文复现实验诊断。
- 会话管理：支持多轮对话历史保存和清空。
- Web 页面：提供简单的前端交互页面。

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring AI
- Spring AI Alibaba Agent Framework
- Alibaba Cloud DashScope
- Milvus
- Maven
- HTML / CSS / JavaScript

## 项目结构

```text
src/main/java/org/example
|-- controller
|   |-- ChatController.java          # 聊天、流式聊天、多 Agent 诊断接口
|   |-- FileUploadController.java    # 文件上传接口
|   `-- MilvusCheckController.java   # Milvus 健康检查
|-- service
|   |-- ChatService.java             # 普通 ReactAgent 对话逻辑
|   |-- AiOpsService.java            # 多 Agent 编排诊断逻辑
|   |-- VectorIndexService.java      # 文档向量化与入库
|   |-- VectorSearchService.java     # 向量检索
|   |-- VectorEmbeddingService.java  # DashScope Embedding 调用
|   `-- DocumentChunkService.java    # 文档切分
|-- agent/tool
|   |-- InternalDocsTools.java       # 论文和复现文档检索工具
|   |-- QueryMetricsTools.java       # 实验指标检索工具
|   |-- QueryLogsTools.java          # 训练日志检索工具
|   `-- DateTimeTools.java           # 时间工具
`-- config                           # 配置类
```

## 核心流程

普通问答流程：

```text
用户问题 -> ChatController -> ChatService -> ReactAgent -> 工具调用/向量检索 -> 模型回答
```

多 Agent 诊断流程：

```text
用户触发诊断
-> SupervisorAgent
-> PlannerAgent 规划诊断步骤
-> ExecutorAgent 调用工具检索证据
-> PlannerAgent 根据反馈继续规划或生成最终报告
```

Planner 和 Executor 之间通过框架中的 `OverAllState` 共享状态通信，主要字段包括 `planner_plan` 和 `executor_feedback`。

## 工具调用说明

项目使用 Spring AI 的 `@Tool` 注解把 Java 方法注册为 Agent 可调用工具：

- `queryPaperDocs`：检索论文、复现指南、实验配置等文档。
- `queryExperimentMetrics`：检索 accuracy、F1、loss 等指标信息。
- `queryExperimentLogs`：检索训练日志、报错、OOM、NaN、seed 等信息。
- `getCurrentDateTime`：获取当前时间。

这些工具底层会调用 `VectorSearchService`，将查询文本向量化后到 Milvus 中检索相关文档片段，再把结果返回给 Agent。

## 环境要求

- JDK 17
- Maven
- Docker / Docker Compose
- DashScope API Key

## 配置说明

请通过环境变量配置 DashScope API Key，不要把真实 Key 写入代码或提交到 GitHub。

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY="your-api-key"
```

Bash：

```bash
export DASHSCOPE_API_KEY=your-api-key
```

应用默认端口：

```text
http://localhost:9900
```

Milvus 默认地址：

```yaml
milvus:
  host: localhost
  port: 19530
```

## 启动方式

```text
1. 启动：

make init
```

2. 打开页面：

```text
http://localhost:9900
```

## 主要接口

### 上传文件

```http
POST /api/upload
```

```text
file: 上传的 txt 或 md 文件
```

### 普通问答

```http
POST /api/chat
```

请求示例：

```json
{
  "Id": "session-001",
  "Question": "分析一下当前复现实验的指标差距"
}
```

### 流式问答

```http
POST /api/chat_stream
```

返回类型：

```text
text/event-stream
```

### 多 Agent 自动诊断

```http
POST /api/ai_ops
```

请求示例：

```json
{
  "userRequest": "诊断为什么当前复现实验没有达到论文结果",
  "runId": "repro-run-001",
  "symptom": "low_accuracy_or_result_gap",
  "targetMetric": "accuracy",
  "currentMetric": "unknown",
  "focus": "paper_config_metrics_logs"
}
```

### 会话管理

```http
POST /api/chat/clear
GET  /api/chat/session/{sessionId}
```

### Milvus 健康检查

```http
GET /milvus/health
```

## License

MIT
