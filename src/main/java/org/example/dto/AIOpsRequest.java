package org.example.dto;

import lombok.Data;

/**
 * Reproduction diagnosis request.
 *
 * The class name is kept for compatibility with the existing /api/ai_ops path.
 */
@Data
public class AIOpsRequest {

    /**
     * Natural language diagnosis goal, for example:
     * "accuracy is much lower than the paper result".
     */
    private String userRequest;

    /**
     * Experiment run id, for example: repro-run-001.
     */
    private String runId;

    /**
     * Main symptom, for example: low_accuracy, loss_plateau, cuda_oom, nan_loss.
     */
    private String symptom;

    /**
     * Metric to focus on, for example: accuracy, f1, loss, auc.
     */
    private String targetMetric;

    /**
     * Optional current observed metric value.
     */
    private String currentMetric;

    /**
     * Optional diagnosis focus, for example: config, data_split, hyperparameter, runtime_error.
     */
    private String focus;
}
