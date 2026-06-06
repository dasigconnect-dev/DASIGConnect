package com.dasigconnect.backend.model.dto.media;

/**
 * UC-4.12 Phase 7G: one portable inventory record, Dublin Core-aligned plus preservation fields.
 * Deliberately excludes {@code storage_url} and any signed/private URL or credential — an export
 * must be safe to hand out. Field groups: {@code dc*} = Dublin Core; {@code pres*} = preservation.
 */
public record MediaExportRecordDto(
        String dcIdentifier,
        String dcTitle,
        String dcCreatorUploader,
        String dcCreatorRightsHolder,
        String dcDate,
        String dcSubjectTags,
        String dcSubjectCategory,
        String dcRightsBasis,
        String dcRightsVisibility,
        String dcRightsExpires,
        String dcRightsState,
        String dcRelationParents,
        String dcRelationChildren,
        String dcFormatType,
        long dcFormatSizeBytes,
        String presFixitySha256,
        String presFixityStatus,
        String presCuratedAt,
        String presClassificationModel,
        String presLifecycleStatus) {

    /** CSV header in the same field order as this record, for the documented Dublin Core mapping. */
    public static final String[] CSV_HEADERS = {
        "dc_identifier", "dc_title", "dc_creator_uploader", "dc_creator_rights_holder", "dc_date",
        "dc_subject_tags", "dc_subject_category", "dc_rights_basis", "dc_rights_visibility",
        "dc_rights_expires", "dc_rights_state", "dc_relation_parents", "dc_relation_children",
        "dc_format_type", "dc_format_size_bytes", "pres_fixity_sha256", "pres_fixity_status",
        "pres_curated_at", "pres_classification_model", "pres_lifecycle_status"
    };

    public String[] toRow() {
        return new String[] {
            dcIdentifier, dcTitle, dcCreatorUploader, dcCreatorRightsHolder, dcDate,
            dcSubjectTags, dcSubjectCategory, dcRightsBasis, dcRightsVisibility, dcRightsExpires,
            dcRightsState, dcRelationParents, dcRelationChildren, dcFormatType,
            String.valueOf(dcFormatSizeBytes), presFixitySha256, presFixityStatus,
            presCuratedAt, presClassificationModel, presLifecycleStatus
        };
    }
}
