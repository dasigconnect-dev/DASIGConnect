package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class MediaAssetSearchRequestDto {

    @NotBlank
    @Size(max = 500)
    private String query;

    private UUID institutionId;
    private String scope;
    private String mediaType;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer pageSize;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
