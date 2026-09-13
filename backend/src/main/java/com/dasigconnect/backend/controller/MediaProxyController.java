package com.dasigconnect.backend.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.service.MediaStorageService;

/**
 * Streams media bytes from R2 server-side, so the browser/AI clients never talk
 * to R2 directly. See {@link MediaStorageService}'s class javadoc for why —
 * short version: R2's public dev URL is not stable and previously broke every
 * media preview in the app at once when it stopped resolving.
 *
 * Deliberately unauthenticated (no {@code @PreAuthorize}, matches Spring
 * Security's global {@code permitAll} here) — media was always effectively
 * public via the R2 URL it replaces, and plain {@code <img src>} tags can't
 * attach an Authorization header anyway.
 */
@RestController
@RequestMapping(MediaStorageService.MEDIA_PROXY_PATH)
public class MediaProxyController {

    private final MediaStorageService mediaStorageService;

    public MediaProxyController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @GetMapping("/{*objectPath}")
    public ResponseEntity<byte[]> serve(@PathVariable String objectPath) {
        String key = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        try {
            MediaStorageService.StoredObject object = mediaStorageService.downloadObject(key);
            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(object.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
            if (object.contentLength() != null) {
                response.contentLength(object.contentLength());
            }
            return response.body(object.content());
        } catch (MediaStorageService.MediaObjectNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
