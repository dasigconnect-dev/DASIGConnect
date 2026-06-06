package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAssetRelation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRelationRepository extends JpaRepository<MediaAssetRelation, UUID> {

    /** Relations where the asset is the parent (its versions / derivatives). */
    @Query("""
        SELECT r FROM MediaAssetRelation r
        WHERE r.parentAssetId = :assetId AND r.institutionId = :institutionId
        ORDER BY r.createdAt DESC
        """)
    List<MediaAssetRelation> findByParent(
            @Param("assetId") UUID assetId, @Param("institutionId") UUID institutionId);

    /** Relations where the asset is the child (what it is a version / derivative of). */
    @Query("""
        SELECT r FROM MediaAssetRelation r
        WHERE r.childAssetId = :assetId AND r.institutionId = :institutionId
        ORDER BY r.createdAt DESC
        """)
    List<MediaAssetRelation> findByChild(
            @Param("assetId") UUID assetId, @Param("institutionId") UUID institutionId);

    boolean existsByParentAssetIdAndChildAssetIdAndRelationType(
            UUID parentAssetId, UUID childAssetId, String relationType);

    /** Forward edge targets (children) of a node — used for bounded cycle detection. */
    @Query("SELECT r.childAssetId FROM MediaAssetRelation r WHERE r.parentAssetId = :parentId")
    List<UUID> findChildIds(@Param("parentId") UUID parentId);

    /** All lineage edges in scope (null institution = network) for the Phase 7G export. */
    @Query("SELECT r FROM MediaAssetRelation r WHERE (:institutionId IS NULL OR r.institutionId = :institutionId)")
    List<MediaAssetRelation> findForExport(@Param("institutionId") UUID institutionId);
}
