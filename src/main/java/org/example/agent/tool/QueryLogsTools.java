package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Experiment log query tool.
 *
 * This keeps the original "log query" role from AIOps, but reads evidence from
 * uploaded training and reproduction documents instead of returning mock logs.
 */
@Component
public class QueryLogsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryLogsTools.class);

    public static final String TOOL_QUERY_EXPERIMENT_LOGS = "queryExperimentLogs";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VectorSearchService vectorSearchService;

    @Tool(description = "Search uploaded experiment logs for paper reproduction diagnosis. " +
            "Use this tool to inspect training logs, evaluation output, runtime errors, CUDA/OOM, NaN, " +
            "dataset records, seed, checkpoint policy, early stopping, command history, and warnings.")
    public String queryExperimentLogs(
            @ToolParam(description = "Log query or symptom, for example: command, epoch 19, OOM, NaN, dataset, seed, checkpoint, class 0 recall")
            String query,
            @ToolParam(description = "Maximum number of document chunks to return, default 6, max 12")
            Integer limit) {
        try {
            int topK = (limit == null || limit <= 0) ? 6 : Math.min(limit, 12);
            String safeQuery = query == null ? "" : query.trim();
            String searchQuery = """
                    training_log reproduction logs command epoch loss accuracy macro F1 class-level evaluation output
                    dataset seed checkpoint early stopping runtime warning OOM NaN CUDA %s
                    """.formatted(safeQuery);

            logger.info("Querying uploaded experiment log documents, query: {}, topK: {}", safeQuery, topK);
            List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilarDocuments(searchQuery, topK);

            LogSearchOutput output = new LogSearchOutput();
            output.setSuccess(!results.isEmpty());
            output.setRunId("repro-run-001");
            output.setQuery(safeQuery);
            output.setTotal(results.size());
            output.setMessage(results.isEmpty()
                    ? "No uploaded log evidence matched the query. Upload or re-upload training_log.md."
                    : "Log evidence returned from uploaded reproduction documents.");
            output.setResults(results);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("Failed to query uploaded experiment logs", e);
            return "{\"success\":false,\"message\":\"Failed to query uploaded experiment logs: " + e.getMessage() + "\"}";
        }
    }

    @Data
    public static class LogSearchOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("run_id")
        private String runId;

        @JsonProperty("query")
        private String query;

        @JsonProperty("total")
        private int total;

        @JsonProperty("message")
        private String message;

        @JsonProperty("results")
        private List<VectorSearchService.SearchResult> results;
    }
}
