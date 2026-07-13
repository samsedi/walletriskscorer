package com.walletriskscorer.controller;

import com.walletriskscorer.entity.ApiKey;
import com.walletriskscorer.repository.ApiKeyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/admin/keys")
@CrossOrigin(originPatterns = "*")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    // Generate a new API key for a client
    @PostMapping("/generate")
    public ResponseEntity<ApiKey> generateKey(@RequestBody Map<String, String> request) {
        String clientName = request.get("clientName");
        
        if (clientName == null || clientName.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Generate a secure, unique key with a prefix
        String rawKey = "wr_" + UUID.randomUUID().toString().replace("-", "");

        ApiKey newKey = new ApiKey();
        newKey.setKeyValue(rawKey);
        newKey.setClientName(clientName);
        newKey.setActive(true);
        newKey.setCreatedAt(LocalDateTime.now());

        ApiKey saved = apiKeyRepository.save(newKey);
        return ResponseEntity.ok(saved);
    }

    // List all API keys
    @GetMapping
    public ResponseEntity<List<ApiKey>> listKeys() {
        return ResponseEntity.ok(apiKeyRepository.findAll());
    }
    
    // Revoke an API key
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable Long id) {
        apiKeyRepository.findById(id).ifPresent(key -> {
            key.setActive(false);
            apiKeyRepository.save(key);
        });
        return ResponseEntity.ok().build();
    }
}
