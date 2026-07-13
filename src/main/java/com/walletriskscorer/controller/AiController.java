package com.walletriskscorer.controller;

import com.walletriskscorer.dto.ChatDto;
import com.walletriskscorer.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(originPatterns = "*") // Allows requests from the React frontend
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatDto.Response> chat(@RequestBody ChatDto.Request request) {
        String reply = aiService.getChatResponse(request.getWalletAddress(), request.getChain() != null ? request.getChain() : "eth", request.getMessage());
        return ResponseEntity.ok(ChatDto.Response.builder().reply(reply).build());
    }
}
