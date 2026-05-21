package com.mintynex.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * NEW — src/main/java/com/mintynex/storage/SupabaseStorageService.java
 *
 * Reusable helper to upload files to Supabase Storage via REST API.
 * Requires in application.properties:
 *   supabase.url=https://mwqidsomofhsbsrhddlk.supabase.co
 *   supabase.service-role-key=YOUR_SERVICE_ROLE_KEY
 */
@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Uploads a file to a Supabase Storage bucket.
     *
     * @param fileBytes   raw bytes of the file
     * @param bucket      bucket name (e.g. "post-media", "card-images", "profile-banners")
     * @param filename    filename to store (e.g. "abc123.jpg") — caller should make it unique
     * @param contentType MIME type (e.g. "image/jpeg", "video/mp4")
     * @return public URL of the uploaded file
     */
    public String upload(byte[] fileBytes, String bucket, String filename, String contentType) {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.parseMediaType(contentType));
        // upsert=true so re-uploads overwrite instead of erroring
        headers.set("x-upsert", "true");

        HttpEntity<byte[]> entity = new HttpEntity<>(fileBytes, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl, HttpMethod.POST, entity, String.class);
            log.debug("Supabase upload response: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Supabase upload failed for bucket={} file={}: {}", bucket, filename, e.getMessage());
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }

        // Return the public URL
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filename;
    }

    /**
     * Generates a unique filename with original extension preserved.
     * e.g. "photo.jpg" → "a1b2c3d4-e5f6.jpg"
     */
    public static String uniqueFilename(String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
