package com.walletriskscorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletriskscorer.dto.WalletDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final MoralisService moralisService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiService(MoralisService moralisService) {
        this.moralisService = moralisService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String getChatResponse(String walletAddress, String chain, String message) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            return "Hello! I am your AI Risk Assistant. I see you are asking about wallet " + walletAddress + 
                   ". (To enable real AI analysis, please add your Gemini API key to the backend configuration).";
        }

        try {
            // Retrieve wallet context
            WalletDetailsDto details = moralisService.getWalletDetails(walletAddress, chain, false);
            
            String context = "You are an expert Web3 security analyst. You are analyzing the wallet address: " + walletAddress + " on chain " + chain + ".\n" +
                             "Here is the local risk profile data for this wallet:\n" +
                             "Risk Score: " + details.getStats().getRiskScore() + "/100 (" + details.getStats().getRiskLevel() + ").\n" +
                             "Total Transactions: " + details.getStats().getTotalTxs() + ".\n" +
                             "Risk Contacts: " + details.getStats().getRiskContacts() + ".\n\n" +
                             "IMPORTANT INSTRUCTION: You have access to Google Search. If the user's question requires information not provided in the risk profile above (such as token balances, wallet ownership, notable transactions, or recent news), you MUST search the web to find the answer.\n\n" +
                             "Please answer the user's question concisely. Keep the response under 3 sentences if possible.\n\n" +
                             "User Question: " + message;

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", context)
                ))
            ));
            
            // Enable Google Search Grounding so the AI can search the web for information about the wallet
            requestBody.put("tools", List.of(
                Map.of("googleSearch", new HashMap<>())
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String availableModels = "Could not fetch models";
            try {
                String modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey;
                ResponseEntity<String> modelsRes = restTemplate.getForEntity(modelsUrl, String.class);
                availableModels = modelsRes.getBody();
            } catch (Exception ex) {
                // Ignore
            }
            log.error("Gemini API error: {}. Available models: {}", e.getResponseBodyAsString(), availableModels, e);
            return "API Error: " + e.getResponseBodyAsString() + " | Available Models snippet: " + 
                   (availableModels.length() > 500 ? availableModels.substring(0, 500) + "..." : availableModels);
        } catch (Exception e) {
            log.error("Error communicating with Gemini API", e);
            return "System Error: " + e.getMessage();
        }
    }
}
