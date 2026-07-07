package com.walletriskscorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletriskscorer.dto.EnsProfileDto;
import com.walletriskscorer.entity.ApiCache;
import com.walletriskscorer.repository.ApiCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class EnsService {

    private static final Logger log = LoggerFactory.getLogger(EnsService.class);

    // ensdata.net — free, no API key required
    private static final String ENSDATA_BASE = "https://ensdata.net/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiCacheRepository apiCacheRepository;

    public EnsService(ApiCacheRepository apiCacheRepository) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiCacheRepository = apiCacheRepository;
    }

    /**
     * Resolves either:
     *   - A raw 0x address  → looks up ENS name + avatar
     *   - An ENS name       → resolves to address + avatar
     *
     * ensdata.net accepts both formats at the same endpoint.
     * e.g. https://ensdata.net/vitalik.eth
     *      https://ensdata.net/0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045
     */
    public EnsProfileDto resolveAddress(String input, boolean refresh) {
        String cleanInput = input.trim();
        String cacheKey = "ensProfile:" + cleanInput.toLowerCase();

        ApiCache cache = refresh ? null : apiCacheRepository.findById(cacheKey).orElse(null);
        if (cache != null && cache.getUpdatedAt().isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) {
            try {
                return objectMapper.readValue(cache.getJsonResponse(), EnsProfileDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse cache for {}", cacheKey, e);
            }
        }

        EnsProfileDto resultDto;

        try {
            URI uri = URI.create(ENSDATA_BASE + cleanInput);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            // ensdata.net returns 404 if no ENS found
            if (response.statusCode() == 404 || response.body() == null
                    || response.body().isBlank()) {
                log.info("No ENS found for: {}", cleanInput);
                resultDto = buildFallback(cleanInput);
            } else if (response.statusCode() != 200) {
                log.warn("ensdata.net returned {} for {}", response.statusCode(), cleanInput);
                resultDto = buildFallback(cleanInput);
            } else {
                resultDto = parseEnsDataResponse(cleanInput, response.body());
            }

        } catch (Exception e) {
            log.error("ENS resolution failed for {}: {}", cleanInput, e.getMessage());
            resultDto = buildFallback(cleanInput);
        }

        try {
            apiCacheRepository.save(ApiCache.builder()
                    .cacheKey(cacheKey)
                    .jsonResponse(objectMapper.writeValueAsString(resultDto))
                    .updatedAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save cache for {}", cacheKey, e);
        }

        return resultDto;
    }

    // ---------- private helpers ----------

    private EnsProfileDto parseEnsDataResponse(String input, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // ensdata.net response fields:
            // address, ens, avatar, cover, description, twitter, github, url

            String address  = getTextField(root, "address");
            String ensName  = getTextField(root, "ens");
            String avatar   = getTextField(root, "avatar");
            String desc     = getTextField(root, "description");
            String twitter  = getTextField(root, "twitter");

            // If address is missing but we sent a 0x, use the input
            if (address == null || address.isBlank()) {
                address = input.startsWith("0x") ? input : null;
            }

            // If ENS name came through but no address, use input
            if (address == null) {
                address = input;
            }

            EnsProfileDto dto = new EnsProfileDto(address, ensName, avatar, desc, twitter);

            log.info("ENS resolved: {} → {} (avatar: {})",
                    input, ensName != null ? ensName : "none", avatar != null ? "yes" : "no");

            return dto;

        } catch (Exception e) {
            log.error("Failed to parse ensdata.net response: {}", e.getMessage());
            return buildFallback(input);
        }
    }

    /**
     * Fallback when ENS not found or API fails.
     * Returns a profile with just the truncated address.
     */
    private EnsProfileDto buildFallback(String input) {
        String cleanInput = input.trim().toLowerCase();
        EnsProfileDto dto = new EnsProfileDto(input);
        
        // Known Off-Chain Entities Dictionary (for wallets without ENS)
        if (cleanInput.equals("0x94845333028b1204fbe14e1278fd4adde46b22ce")) {
            dto.setDisplayName("Donald Trump");
            dto.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=Trump&backgroundColor=ffdfbf");
        } else if (input.endsWith(".eth") || input.contains(".")) {
            dto.setEnsName(input);
            dto.setDisplayName(input);
        }
        
        return dto;
    }

    private String getTextField(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || val.asText().isBlank()) return null;
        return val.asText().trim();
    }
}
