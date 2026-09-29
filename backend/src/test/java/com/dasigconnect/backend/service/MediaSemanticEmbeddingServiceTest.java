package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.entity.AssetTag;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaSemanticEmbeddingServiceTest {

    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final AssetTagRepository tags = mock(AssetTagRepository.class);
    private final MediaAssetEmbeddingRepository embeddings = mock(MediaAssetEmbeddingRepository.class);
    private final VoyageAIClient voyage = mock(VoyageAIClient.class);
    private final MediaSemanticEmbeddingService service =
            new MediaSemanticEmbeddingService(assets, tags, embeddings, voyage);

    @Test
    void generateOrReuse_buildsDocumentFromTrustedMetadataOnly() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setDisplayTitle("Innovation Summit");
        asset.setFileName("IMG_hackathon-day1.jpg");
        asset.setAssetType("event-photo");
        asset.setTemporalClassification("evergreen");
        MediaAlbum album = new MediaAlbum();
        album.setName("Student Events");
        asset.setMediaAlbum(album);
        AssetTag manual = tag("hackathon", "manual");
        AssetTag generated = tag("ignored-ai-tag", "ai_generated");
        when(voyage.modelName()).thenReturn("voyage-4-lite");
        when(embeddings.existsCurrentEmbedding(
                assetId, MediaAssetEmbeddingType.SEMANTIC, "voyage-4-lite")).thenReturn(false);
        when(assets.findActiveWithAlbumById(assetId)).thenReturn(Optional.of(asset));
        when(tags.findByMediaAssetIdOrderByCreatedAtAsc(assetId))
                .thenReturn(List.of(manual, generated));
        when(voyage.embedDocument(anyString())).thenReturn("[0.2]");

        assertThat(service.generateOrReuse(assetId)).isTrue();

        var input = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(voyage).embedDocument(input.capture());
        assertThat(input.getValue())
                .contains("title: Innovation Summit")
                .contains("filename: IMG hackathon day1")
                .contains("album: Student Events")
                .contains("tags: hackathon")
                .contains("format: event-photo")
                .contains("temporal: evergreen")
                .doesNotContain("ignored-ai-tag");
        verify(embeddings).upsert(
                assetId, MediaAssetEmbeddingType.SEMANTIC, "[0.2]", "voyage-4-lite");
        verify(assets).updateEmbedding(assetId, "[0.2]", "voyage-4-lite");
    }

    @Test
    void generateOrReuse_currentModelSkipsProviderAndDatabaseWrite() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setEmbeddingModel("voyage-4-lite");
        when(voyage.modelName()).thenReturn("voyage-4-lite");
        when(embeddings.existsCurrentEmbedding(
                assetId, MediaAssetEmbeddingType.SEMANTIC, "voyage-4-lite")).thenReturn(true);
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));

        assertThat(service.generateOrReuse(assetId)).isTrue();

        verify(voyage, never()).embedDocument(anyString());
        verify(assets, never()).findActiveWithAlbumById(assetId);
    }

    @Test
    void generateOrReuse_repairsLegacyColumnWithoutCallingVoyageAgain() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setEmbeddingModel("old-model");
        when(voyage.modelName()).thenReturn("voyage-4-lite");
        when(embeddings.existsCurrentEmbedding(
                assetId, MediaAssetEmbeddingType.SEMANTIC, "voyage-4-lite")).thenReturn(true);
        when(assets.findActiveById(assetId)).thenReturn(Optional.of(asset));
        when(embeddings.findEmbedding(assetId, MediaAssetEmbeddingType.SEMANTIC))
                .thenReturn(Optional.of("[0.4]"));

        assertThat(service.generateOrReuse(assetId)).isTrue();

        verify(voyage, never()).embedDocument(anyString());
        verify(assets).updateEmbedding(assetId, "[0.4]", "voyage-4-lite");
    }

    private static AssetTag tag(String label, String source) {
        AssetTag tag = new AssetTag();
        tag.setLabel(label);
        tag.setSource(source);
        return tag;
    }
}
