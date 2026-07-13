package com.walletriskscorer.controller;

import com.walletriskscorer.dto.EnsProfileDto;
import com.walletriskscorer.service.EnsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ens")
@CrossOrigin(originPatterns = "*")
public class EnsController {

    private final EnsService ensService;

    public EnsController(EnsService ensService) {
        this.ensService = ensService;
    }

    /**
     * GET /api/ens/resolve?address=0xd8dA6BF...
     * Also accepts ENS names: ?address=vitalik.eth
     */
    @GetMapping("/resolve")
    public ResponseEntity<EnsProfileDto> resolve(
            @RequestParam String address,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        EnsProfileDto profile = ensService.resolveAddress(address, refresh);
        return ResponseEntity.ok(profile);
    }
}
