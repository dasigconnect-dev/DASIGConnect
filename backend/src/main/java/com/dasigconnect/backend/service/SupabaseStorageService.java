package com.dasigconnect.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucket;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupabaseStorageService(
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey,
            @Value("${app.supabase.storage-bucket:dasigconnect-media}") String bucket) {
        this.supabaseUrl = supabaseUrl.replaceAll("/$", "");
        this.serviceRoleKey = serviceRoleKey;
        this.bucket = bucket;
    }

    public boolean isConfigured() {
        return !supabaseUrl.isBlank() && !serviceRoleKey.isBlank();
    }

    public String createSignedUploadUrl(String objectPath) {
        if (!isConfigured()) {
            throw new IllegalStateException("Supabase storage is not configured.");
        }
        try {
            String endpoint = supabaseUrl + "/storage/v1/object/upload/sign/" + bucket + "/" + objectPath;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"upsert\":false}"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Supabase signed URL request failed {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("Supabase Storage returned " + response.statusCode());
            }

            JsonNode node = objectMapper.readTree(response.body());
            String relativeUrl = node.get("url").asText();
            return supabaseUrl + "/storage/v1" + relativeUrl;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create signed upload URL: " + ex.getMessage(), ex);
        }
    }

    public String getPublicUrl(String objectPath) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    public boolean deletePublicObject(String publicUrl) {
        if (!isConfigured()) {
            log.warn("Supabase storage is not configured; skipping object purge.");
            return false;
        }
        String objectPath = objectPathFromPublicUrl(publicUrl);
        if (objectPath == null || objectPath.isBlank()) {
            log.warn("Could not derive Supabase object path from URL; skipping object purge.");
            return false;
        }
        try {
            String endpoint = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            if (response.statusCode() == 404) {
                log.info("Supabase object already missing during purge: {}", objectPath);
                return true;
            }
            log.warn("Supabase object purge failed {} for {}: {}", response.statusCode(), objectPath, response.body());
            return false;
        } catch (Exception ex) {
            log.warn("Failed to purge Supabase object {}: {}", objectPath, ex.getMessage());
            return false;
        }
    }

    private String objectPathFromPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String marker = "/storage/v1/object/public/" + bucket + "/";
        int index = publicUrl.indexOf(marker);
        if (index < 0) {
            return null;
        }
        return publicUrl.substring(index + marker.length());
    }
}
