package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "submission_media_contexts")
public class SubmissionMediaContext {

    @Id
    @Column(name = "submission_id")
    private UUID submissionId;
    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;
    @Column(name = "context_version", nullable = false)
    private long contextVersion;
    @Column(name = "asset_set_hash", nullable = false, length = 64)
    private String assetSetHash;
    @Column(name = "ready_asset_count", nullable = false)
    private int readyAssetCount;
    @Column(name = "processing_asset_count", nullable = false)
    private int processingAssetCount;
    @Column(name = "failed_asset_count", nullable = false)
    private int failedAssetCount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "observed_scenes", columnDefinition = "jsonb")
    private String observedScenes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "observed_objects", columnDefinition = "jsonb")
    private String observedObjects;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "observed_activities", columnDefinition = "jsonb")
    private String observedActivities;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "people_count_ranges", columnDefinition = "jsonb")
    private String peopleCountRanges;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "equipment_signals", columnDefinition = "jsonb")
    private String equipmentSignals;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "recognition_signals", columnDefinition = "jsonb")
    private String recognitionSignals;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "ocr_text", columnDefinition = "jsonb")
    private String ocrText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "event_hypotheses", columnDefinition = "jsonb")
    private String eventHypotheses;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "temporal_signals", columnDefinition = "jsonb")
    private String temporalSignals;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "quality_signals", columnDefinition = "jsonb")
    private String qualitySignals;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "composition_signals", columnDefinition = "jsonb")
    private String compositionSignals;
    @Column(name = "context_text", nullable = false, columnDefinition = "text")
    private String contextText;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public long getContextVersion() { return contextVersion; }
    public void setContextVersion(long contextVersion) { this.contextVersion = contextVersion; }
    public String getAssetSetHash() { return assetSetHash; }
    public void setAssetSetHash(String assetSetHash) { this.assetSetHash = assetSetHash; }
    public int getReadyAssetCount() { return readyAssetCount; }
    public void setReadyAssetCount(int value) { this.readyAssetCount = value; }
    public int getProcessingAssetCount() { return processingAssetCount; }
    public void setProcessingAssetCount(int value) { this.processingAssetCount = value; }
    public int getFailedAssetCount() { return failedAssetCount; }
    public void setFailedAssetCount(int value) { this.failedAssetCount = value; }
    public String getObservedScenes() { return observedScenes; }
    public void setObservedScenes(String value) { this.observedScenes = value; }
    public String getObservedObjects() { return observedObjects; }
    public void setObservedObjects(String value) { this.observedObjects = value; }
    public String getObservedActivities() { return observedActivities; }
    public void setObservedActivities(String value) { this.observedActivities = value; }
    public String getPeopleCountRanges() { return peopleCountRanges; }
    public void setPeopleCountRanges(String value) { this.peopleCountRanges = value; }
    public String getEquipmentSignals() { return equipmentSignals; }
    public void setEquipmentSignals(String value) { this.equipmentSignals = value; }
    public String getRecognitionSignals() { return recognitionSignals; }
    public void setRecognitionSignals(String value) { this.recognitionSignals = value; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String value) { this.ocrText = value; }
    public String getEventHypotheses() { return eventHypotheses; }
    public void setEventHypotheses(String value) { this.eventHypotheses = value; }
    public String getTemporalSignals() { return temporalSignals; }
    public void setTemporalSignals(String value) { this.temporalSignals = value; }
    public String getQualitySignals() { return qualitySignals; }
    public void setQualitySignals(String value) { this.qualitySignals = value; }
    public String getCompositionSignals() { return compositionSignals; }
    public void setCompositionSignals(String value) { this.compositionSignals = value; }
    public String getContextText() { return contextText; }
    public void setContextText(String value) { this.contextText = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String value) { this.modelVersion = value; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant value) { this.generatedAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}
