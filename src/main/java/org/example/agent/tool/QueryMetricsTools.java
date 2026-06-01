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
 * Experiment metric query tool.
 *
 * This keeps the original "metrics" role from AIOps, but reads metric evidence
 * from the uploaded reproduction documents instead of returning mock values.
 */
@Component
public class QueryMetricsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryMetricsTools.class);

    public static final String TOOL_QUERY_EXPERIMENT_METRICS = "queryExperimentMetrics";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VectorSearchService vectorSearchService;

    @Tool(description = "Search uploaded metric evidence for paper reproduction diagnosis. " +
            "Use this tool to inspect paper target results, current reproduction metrics, accuracy, precision, recall, " +
            "macro F1, weighted F1, class-level results, metric gaps, and missing metrics.")
    public String queryExperimentMetrics(
            @ToolParam(description = "Metric query or symptom, for example: target result, current accuracy, macro F1, class 0 recall, gap analysis")
            String query) {
        try {
            String safeQuery = query == null ? "" : query.trim();
            String searchQuery = """
                    metrics_log paper target results current reproduction results accuracy precision recall macro F1 weighted F1
                    class-level results gap analysis missing metrics diagnosis boundaries %s
                    """.formatted(safeQuery);

            logger.info("Querying uploaded experiment metric documents, query: {}", safeQuery);
            List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilarDocuments(searchQuery, 6);

            MetricSearchOutput output = new MetricSearchOutput();
            output.setSuccess(!results.isEmpty());
            output.setRunId("repro-run-001");
            output.setQuery(safeQuery);
            output.setMessage(results.isEmpty()
                    ? "No uploaded metric evidence matched the query. Upload or re-upload metrics_log.md."
                    : "Metric evidence returned from uploaded reproduction documents.");
            output.setResults(results);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("Failed to query uploaded experiment metrics", e);
            return "{\"success\":false,\"message\":\"Failed to query uploaded experiment metrics: " + e.getMessage() + "\"}";
        }
    }

    @Data
    public static class MetricSearchOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("run_id")
        private String runId;

        @JsonProperty("query")
        private String query;

        @JsonProperty("message")
        private String message;

        @JsonProperty("results")
        private List<VectorSearchService.SearchResult> results;
    }
}
