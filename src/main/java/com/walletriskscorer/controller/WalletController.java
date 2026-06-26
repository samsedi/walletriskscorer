package com.walletriskscorer.controller;

import com.walletriskscorer.dto.ActivityChartDto;
import com.walletriskscorer.service.MoralisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*") // Allows React app on port 3000/5173 to call this backend
public class WalletController {

    private final MoralisService moralisService;
    private final com.walletriskscorer.service.EnsService ensService;

    public WalletController(MoralisService moralisService, com.walletriskscorer.service.EnsService ensService) {
        this.moralisService = moralisService;
        this.ensService = ensService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ActivityChartDto>> getTransactionActivity(
            @RequestParam String address,
            @RequestParam(defaultValue = "W") String timeframe,
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "eth") String chain
    ) {
        if (address == null || address.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String hexAddress = ensService.resolveAddress(address).getAddress();
        if (hexAddress == null || !hexAddress.startsWith("0x")) {
            return ResponseEntity.badRequest().build();
        }
        
        List<ActivityChartDto> activity = moralisService.getTransactionActivity(hexAddress, timeframe, year, chain);
        return ResponseEntity.ok(activity);
    }

    @GetMapping("/details")
    public ResponseEntity<com.walletriskscorer.dto.WalletDetailsDto> getWalletDetails(
            @RequestParam String address,
            @RequestParam(defaultValue = "eth") String chain
    ) {
        if (address == null || address.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String hexAddress = ensService.resolveAddress(address).getAddress();
        if (hexAddress == null || !hexAddress.startsWith("0x")) {
            return ResponseEntity.badRequest().build();
        }
        
        com.walletriskscorer.dto.WalletDetailsDto details = moralisService.getWalletDetails(hexAddress, chain);
        return ResponseEntity.ok(details);
    }
}
