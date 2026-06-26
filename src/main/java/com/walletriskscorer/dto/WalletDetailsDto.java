package com.walletriskscorer.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WalletDetailsDto {

    private Stats stats;
    private List<Interaction> recentInteractions;
    private List<SignalFlag> signalFlags;

    @Data
    @Builder
    public static class Stats {
        private String walletAge;
        private String totalTxs;
        private int contractsHit;
        private String fundSource;
        private String tokenSpread;
        private int riskScore;
        private String riskLevel;
        private int riskContacts;
    }

    @Data
    @Builder
    public static class Interaction {
        private String name;
        private String address;
        private String type;
        private String status;
        private String value; // e.g. "1.24 ETH"
        private boolean isPositive; // For styling green/red
        private String timeAgo;
        private String hash;
    }

    @Data
    @Builder
    public static class SignalFlag {
        private String label;
        private String status;
        private boolean isClear; // true for green check, false for red X
    }
}
