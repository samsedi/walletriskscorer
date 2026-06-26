package com.walletriskscorer.controller;

import com.walletriskscorer.dto.EnsProfileDto;
import com.walletriskscorer.service.EnsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ens")
@CrossOrigin(origins = "*")
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
    public ResponseEntity<EnsProfileDto> resolve(@RequestParam String address) {
        EnsProfileDto profile = ensService.resolveAddress(address);
        return ResponseEntity.ok(profile);
    }
}
