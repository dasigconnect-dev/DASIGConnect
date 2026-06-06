package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-4.12 Phase 7D: upsert an asset's rights/consent record. {@code rightsBasis} is validated
 * against the controlled vocabulary in the service.
 */
public class MediaAssetRightsUpdateRequestDto {

    @Size(max = 300)
    private String rightsHolder;

    @NotBlank
    @Size(max = 20)
    private String rightsBasis;

    @Size(max = 2000)
    private String licenseUri;

    @Size(max = 2000)
    private String consentReference;

    private LocalDate clearanceDate;

    private LocalDate expiresAt;

    private List<@Size(max = 60) String> permittedChannels;

    @Size(max = 4000)
    private String restrictions;

    @Size(max = 2000)
    private String supportingDocumentUrl;

    public String getRightsHolder() { return rightsHolder; }
    public void setRightsHolder(String rightsHolder) { this.rightsHolder = rightsHolder; }
    public String getRightsBasis() { return rightsBasis; }
    public void setRightsBasis(String rightsBasis) { this.rightsBasis = rightsBasis; }
    public String getLicenseUri() { return licenseUri; }
    public void setLicenseUri(String licenseUri) { this.licenseUri = licenseUri; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public LocalDate getClearanceDate() { return clearanceDate; }
    public void setClearanceDate(LocalDate clearanceDate) { this.clearanceDate = clearanceDate; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public List<String> getPermittedChannels() { return permittedChannels; }
    public void setPermittedChannels(List<String> permittedChannels) { this.permittedChannels = permittedChannels; }
    public String getRestrictions() { return restrictions; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }
    public String getSupportingDocumentUrl() { return supportingDocumentUrl; }
    public void setSupportingDocumentUrl(String supportingDocumentUrl) { this.supportingDocumentUrl = supportingDocumentUrl; }
}
