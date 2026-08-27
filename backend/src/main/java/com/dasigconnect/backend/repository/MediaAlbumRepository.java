package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaAlbum;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAlbumRepository extends JpaRepository<MediaAlbum, UUID> {

    @Query("SELECT a FROM MediaAlbum a WHERE a.institution.id = :institutionId ORDER BY LOWER(a.name)")
    List<MediaAlbum> findByInstitutionIdOrderByName(@Param("institutionId") UUID institutionId);

    @Query("SELECT a FROM MediaAlbum a WHERE a.institution.id IN :institutionIds ORDER BY LOWER(a.name)")
    List<MediaAlbum> findByInstitutionIdInOrderByName(@Param("institutionIds") java.util.Collection<UUID> institutionIds);

    @Query("""
            SELECT a FROM MediaAlbum a
            WHERE a.institution.id = :institutionId
              AND LOWER(a.name) = LOWER(:name)
            """)
    Optional<MediaAlbum> findByInstitutionIdAndNameIgnoreCase(
            @Param("institutionId") UUID institutionId,
            @Param("name") String name);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    /** Name lookup scoped to a parent folder — {@code parentAlbumId} null means the institution root. */
    @Query("""
            SELECT a FROM MediaAlbum a
            WHERE a.institution.id = :institutionId
              AND LOWER(a.name) = LOWER(:name)
              AND ((:parentAlbumId IS NULL AND a.parentAlbum IS NULL)
                   OR a.parentAlbum.id = :parentAlbumId)
            """)
    Optional<MediaAlbum> findByParentAndNameIgnoreCase(
            @Param("institutionId") UUID institutionId,
            @Param("parentAlbumId") UUID parentAlbumId,
            @Param("name") String name);

    long countByParentAlbumId(UUID parentAlbumId);

    /** [parentAlbumId, childCount] pairs for every album in the institution that has children. */
    @Query("""
            SELECT a.parentAlbum.id, COUNT(a)
            FROM MediaAlbum a
            WHERE a.institution.id = :institutionId AND a.parentAlbum IS NOT NULL
            GROUP BY a.parentAlbum.id
            """)
    List<Object[]> countChildAlbumsByParent(@Param("institutionId") UUID institutionId);

    /** Same as {@link #countChildAlbumsByParent} but across every institution (admin network view). */
    @Query("""
            SELECT a.parentAlbum.id, COUNT(a)
            FROM MediaAlbum a
            WHERE a.parentAlbum IS NOT NULL
            GROUP BY a.parentAlbum.id
            """)
    List<Object[]> countChildAlbumsByParentAllInstitutions();

    /**
     * All descendant album ids of {@code albumId} (excludes the album itself),
     * via a recursive walk of parent_album_id. Used for cycle guards and
     * subtree emptiness checks.
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM media_albums WHERE parent_album_id = :albumId
                UNION ALL
                SELECT m.id FROM media_albums m
                JOIN subtree s ON m.parent_album_id = s.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<UUID> findDescendantIds(@Param("albumId") UUID albumId);

    /** Re-home a set of albums to another institution (used when a folder is moved cross-institution). */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE media_albums SET institution_id = :institutionId WHERE id IN :ids", nativeQuery = true)
    void rehomeAlbums(@Param("institutionId") UUID institutionId, @Param("ids") java.util.Collection<UUID> ids);
}
