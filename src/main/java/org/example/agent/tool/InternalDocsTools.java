package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reproduction document search tool.
 * It reuses the existing vector knowledge base to search uploaded papers,
 * reproduction guides, experiment configs, notes, and related research documents.
 */
@Component
public class InternalDocsTools {

    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);

    public static final String TOOL_QUERY_PAPER_DOCS = "queryPaperDocs";

    private final VectorSearchService vectorSearchService;

    @Value("${rag.top-k:3}")
    private int topK = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Tool(description = "Search uploaded paper documents, reproduction guides, experiment configs, research notes, and related papers. " +
            "Use this tool when you need evidence from the target paper, reproduction setup, or uploaded research materials. " +
            "The result contains relevant chunks, similarity scores, and metadata.")
    public String queryPaperDocs(
            @ToolParam(description = "Search query, for example: paper target result, method, experiments, dataset, baseline, hyperparameters, reproduction guide, config")
            String query) {

        try {
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(query, topK);

            if (searchResults.isEmpty()) {
                return "{\"status\":\"no_results\",\"message\":\"No relevant paper chunks found in the knowledge base.\"}";
            }

            return objectMapper.writeValueAsString(searchResults);

        } catch (Exception e) {
            logger.error("[Tool Error] queryPaperDocs failed", e);
            return String.format("{\"status\":\"error\",\"message\":\"Failed to query paper docs: %s\"}",
                    e.getMessage());
        }
    }
}
