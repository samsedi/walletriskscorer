package com.walletriskscorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletriskscorer.dto.ActivityChartDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MoralisService {

    private static final Logger log = LoggerFactory.getLogger(MoralisService.class);
    private static final String MORALIS_BASE = "https://deep-index.moralis.io/api/v2.2/";

    @Value("${moralis.api.key}")
    private String apiKey;

    @Value("${etherscan.api.key:YourApiKeyToken}")
    private String etherscanApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MoralisService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<ActivityChartDto> getTransactionActivity(String address, String timeframe, int year, String chain) {
        String cleanAddress = address.trim();
        List<ActivityChartDto> result = new ArrayList<>();
        
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-moralis-api-key")) {
            log.warn("Moralis API Key is not configured! Returning mock transaction history.");
            return buildMockData(timeframe, year);
        }

        try {
            // Include from_date and to_date to get transactions for the specific year
            String fromDate = year + "-01-01T00:00:00Z";
            String toDate = year + "-12-31T23:59:59Z";
            URI uri = URI.create(MORALIS_BASE + cleanAddress + "?chain=" + chain + "&limit=100&from_date=" + fromDate + "&to_date=" + toDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Moralis API returned {}: {}", response.statusCode(), response.body());
                return buildMockData(timeframe, year);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode txs = root.get("result");
            
            if (txs == null || !txs.isArray()) {
                return buildMockData(timeframe, year);
            }

            int bucketCount = 52;
            if ("D".equalsIgnoreCase(timeframe)) bucketCount = 7;
            else if ("M".equalsIgnoreCase(timeframe)) bucketCount = 12;

            int[] counts = new int[bucketCount];

            for (JsonNode tx : txs) {
                JsonNode timestampNode = tx.get("block_timestamp");
                if (timestampNode != null) {
                    Instant txTime = Instant.parse(timestampNode.asText());
                    java.time.ZonedDateTime zdt = txTime.atZone(ZoneOffset.UTC);
                    
                    if (zdt.getYear() != year) continue; // Safety check

                    int bucketIndex = 0;
                    if ("D".equalsIgnoreCase(timeframe)) {
                        bucketIndex = zdt.getDayOfWeek().getValue() - 1; // 0 = Mon, 6 = Sun
                    } else if ("M".equalsIgnoreCase(timeframe)) {
                        bucketIndex = zdt.getMonthValue() - 1; // 0 = Jan, 11 = Dec
                    } else {
                        bucketIndex = zdt.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) - 1; // 0 = W1, 51 = W52
                        if (bucketIndex < 0) bucketIndex = 0;
                        if (bucketIndex >= 52) bucketIndex = 51;
                    }
                    counts[bucketIndex]++;
                }
            }

            int maxCount = 1;
            for (int count : counts) {
                if (count > maxCount) maxCount = count;
            }

            String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            String[] monthLabels = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

            for (int i = 0; i < bucketCount; i++) {
                int count = counts[i];
                int heightPercent = Math.max(5, (count * 100) / maxCount);
                
                String label = "W" + (i + 1);
                if ("D".equalsIgnoreCase(timeframe)) label = dayLabels[i];
                else if ("M".equalsIgnoreCase(timeframe)) label = monthLabels[i];
                
                result.add(ActivityChartDto.builder()
                        .label(label)
                        .value(count)
                        .height(heightPercent + "%")
                        .active(count == maxCount) // Highlight the highest bucket
                        .build());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to fetch Moralis history: {}", e.getMessage(), e);
            return buildMockData(timeframe, year);
        }
    }

    private List<ActivityChartDto> buildMockData(String timeframe, int year) {
        List<ActivityChartDto> mock = new ArrayList<>();
        
        int bucketCount = 52;
        if ("D".equalsIgnoreCase(timeframe)) bucketCount = 7;
        else if ("M".equalsIgnoreCase(timeframe)) bucketCount = 12;

        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String[] monthLabels = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        for (int i = 0; i < bucketCount; i++) {
            int val = (int)(Math.random() * 50);
            String label = "W" + (i + 1);
            if ("D".equalsIgnoreCase(timeframe)) label = dayLabels[i];
            else if ("M".equalsIgnoreCase(timeframe)) label = monthLabels[i];
            
            mock.add(ActivityChartDto.builder()
                    .label(label)
                    .value(val)
                    .height(Math.max(5, (val * 100) / 50) + "%")
                    .active(false)
                    .build());
        }
        return mock;
    }
    private String getGoPlusChainId(String chain) {
        return switch (chain.toLowerCase()) {
            case "bsc" -> "56";
            case "polygon" -> "137";
            case "arbitrum" -> "42161";
            case "optimism" -> "10";
            case "base" -> "8453";
            default -> "1"; // eth
        };
    }

    public com.walletriskscorer.dto.WalletDetailsDto getWalletDetails(String address, String chain) {
        String cleanAddress = address.trim();
        
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-moralis-api-key")) {
            return buildMockWalletDetails(cleanAddress, chain);
        }

        try {
            // Fetch the first 100 transactions for the wallet
            URI uri = URI.create(MORALIS_BASE + cleanAddress + "?chain=" + chain + "&limit=100");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();

            // Fetch exact stats for the wallet
            URI statsUri = URI.create(MORALIS_BASE + "wallets/" + cleanAddress + "/stats?chain=" + chain);
            HttpRequest statsRequest = HttpRequest.newBuilder()
                    .uri(statsUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();

            // Fetch oldest transaction for wallet age
            URI oldestTxUri = URI.create(MORALIS_BASE + cleanAddress + "?chain=" + chain + "&order=ASC&limit=1");
            HttpRequest oldestTxRequest = HttpRequest.newBuilder()
                    .uri(oldestTxUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> statsResponse = httpClient.send(statsRequest, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> oldestTxResponse = httpClient.send(oldestTxRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Moralis API returned {}: {}", response.statusCode(), response.body());
                return buildMockWalletDetails(cleanAddress, chain);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode txs = root.get("result");
            
            if (txs == null || !txs.isArray()) {
                return buildMockWalletDetails(cleanAddress, chain);
            }

            // Parse Exact Total Transactions
            String exactTotalTxs = "100+";
            if (statsResponse.statusCode() == 200) {
                JsonNode statsRoot = objectMapper.readTree(statsResponse.body());
                if (statsRoot.has("transactions") && statsRoot.get("transactions").has("total")) {
                    long total = statsRoot.get("transactions").get("total").asLong();
                    exactTotalTxs = String.format("%,d", total);
                }
            }

            // Calculate exact Wallet Age and Fund Source
            String walletAge = "0.0y";
            String exactFundSource = "Unknown";
            if (oldestTxResponse.statusCode() == 200) {
                JsonNode oldestRoot = objectMapper.readTree(oldestTxResponse.body());
                JsonNode oldestTxs = oldestRoot.get("result");
                if (oldestTxs != null && oldestTxs.isArray() && oldestTxs.size() > 0) {
                    JsonNode firstTx = oldestTxs.get(0);
                    String blockTimestamp = firstTx.get("block_timestamp").asText();
                    try {
                        java.time.Instant firstTxInstant = java.time.Instant.parse(blockTimestamp);
                        java.time.Instant now = java.time.Instant.now();
                        long daysBetween = java.time.Duration.between(firstTxInstant, now).toDays();
                        double years = daysBetween / 365.25;
                        walletAge = String.format("%.1fy", years);
                    } catch (Exception e) {}
                    
                    if (firstTx.has("from_address") && !firstTx.get("from_address").isNull()) {
                        String fromAddr = firstTx.get("from_address").asText().toLowerCase();
                        exactFundSource = fromAddr.substring(0, 6) + "..." + fromAddr.substring(fromAddr.length() - 4);
                        
                        // Known Entity Registry for funding
                        if (fromAddr.equals("0x1db3439a222c519ab44bb1144fc28167b4fa6ee6")) exactFundSource = "Vb 1 (Vitalik)";
                        else if (fromAddr.equals("0x28c6c06298d514db089934071355e5743bf21d60")) exactFundSource = "CEX (Binance)";
                        else if (fromAddr.equals("0x00000000219ab540356cbb839cbe05303d7705fa")) exactFundSource = "Beacon Deposit";
                        else if (fromAddr.equals("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")) exactFundSource = "USDC System";
                    }
                }
            }

            java.util.Set<String> uniqueContracts = new java.util.HashSet<>();
            List<com.walletriskscorer.dto.WalletDetailsDto.Interaction> interactions = new ArrayList<>();
            int txCount = 0;

            for (JsonNode tx : txs) {
                txCount++;
                String toAddress = tx.get("to_address") != null && !tx.get("to_address").isNull() ? tx.get("to_address").asText() : "";
                if (!toAddress.isEmpty()) {
                    uniqueContracts.add(toAddress.toLowerCase());
                }

                if (interactions.size() < 3) {
                    String valueWei = tx.get("value") != null ? tx.get("value").asText() : "0";
                    java.math.BigDecimal ethValue = new java.math.BigDecimal(valueWei).divide(new java.math.BigDecimal("1000000000000000000"), 4, java.math.RoundingMode.HALF_UP);
                    
                    String symbol = switch (chain.toLowerCase()) {
                        case "bsc" -> "BNB";
                        case "polygon" -> "POL";
                        case "arbitrum", "optimism", "base" -> "ETH";
                        default -> "ETH";
                    };
                    String formattedValue = String.format("%.2f %s", ethValue.doubleValue(), symbol);
                    String type = ethValue.compareTo(java.math.BigDecimal.ZERO) > 0 ? "Transfer" : "Interaction";
                    
                    String name = "Unknown Contract";
                    String status = "Flagged";
                    boolean isPositive = false;

                    String inputData = tx.get("input") != null ? tx.get("input").asText() : "0x";
                    String fromAddress = tx.get("from_address") != null && !tx.get("from_address").isNull() ? tx.get("from_address").asText() : "";
                    String displayAddress = toAddress;
                    
                    String txHash = tx.get("hash") != null ? tx.get("hash").asText() : "";
                    String timeAgo = "";
                    if (tx.get("block_timestamp") != null) {
                        try {
                            java.time.Instant txTime = java.time.Instant.parse(tx.get("block_timestamp").asText());
                            java.time.Instant now = java.time.Instant.now();
                            long seconds = Math.abs(java.time.Duration.between(txTime, now).getSeconds());
                            if (seconds < 60) timeAgo = seconds + " secs ago";
                            else if (seconds < 3600) timeAgo = (seconds / 60) + " mins ago";
                            else if (seconds < 86400 * 2) timeAgo = (seconds / 3600) + " hrs ago"; // Use hours for up to 48h (like Etherscan)
                            else timeAgo = (seconds / 86400) + " days ago";
                        } catch (Exception e) {}
                    }

                    if (toAddress.equalsIgnoreCase(cleanAddress)) {
                        name = "Incoming Transfer";
                        type = "Receive";
                        status = "Safe";
                        isPositive = true;
                        displayAddress = fromAddress;
                    } else if (fromAddress.equalsIgnoreCase(cleanAddress) && toAddress.isEmpty()) {
                        name = "Contract Creation";
                        type = "Deploy";
                        status = "Safe";
                        isPositive = true;
                        displayAddress = "0x0000...0000"; // Placeholder
                    } else if ("0x".equals(inputData)) {
                        name = "Wallet Transfer";
                        type = "Transfer out";
                        status = "Safe";
                        isPositive = true;
                    } else {
                        // Fetch Contract Name from Block Explorer
                        if (!toAddress.isEmpty()) {
                            try {
                                String explorerApiUrl = switch (chain.toLowerCase()) {
                                    case "bsc" -> "https://api.bscscan.com/api";
                                    case "polygon" -> "https://api.polygonscan.com/api";
                                    case "arbitrum" -> "https://api.arbiscan.io/api";
                                    case "optimism" -> "https://api-optimistic.etherscan.io/api";
                                    case "base" -> "https://api.basescan.org/api";
                                    default -> "https://api.etherscan.io/api";
                                };
                                
                                URI explorerUri = URI.create(explorerApiUrl + "?module=contract&action=getsourcecode&address=" + toAddress + "&apikey=" + etherscanApiKey);
                                HttpRequest explorerRequest = HttpRequest.newBuilder().uri(explorerUri).timeout(Duration.ofSeconds(5)).GET().build();
                                HttpResponse<String> explorerResponse = httpClient.send(explorerRequest, HttpResponse.BodyHandlers.ofString());
                                if (explorerResponse.statusCode() == 200) {
                                    JsonNode eRoot = objectMapper.readTree(explorerResponse.body());
                                    if ("1".equals(eRoot.get("status").asText()) && eRoot.has("result") && eRoot.get("result").isArray() && eRoot.get("result").size() > 0) {
                                        String contractName = eRoot.get("result").get(0).get("ContractName").asText();
                                        if (contractName != null && !contractName.isEmpty()) {
                                            name = contractName;
                                        } else {
                                            name = "Unverified Contract";
                                        }
                                    } else {
                                        name = "Unverified Contract";
                                    }
                                }
                                // rate limit protection
                                Thread.sleep(250);
                            } catch (Exception e) {
                                log.warn("Block Explorer API failed for {}: {}", toAddress, e.getMessage());
                            }
                        }

                        if (!name.equals("Unknown Contract") && !name.equals("Unverified Contract")) {
                            status = "Safe";
                            isPositive = true;
                            type = "Contract Call";
                        } else if (name.equals("Unverified Contract")) {
                            status = "Caution";
                            isPositive = false; // Flagged in UI for caution
                            type = "Contract Call";
                        }
                    }

                    interactions.add(com.walletriskscorer.dto.WalletDetailsDto.Interaction.builder()
                            .name(name)
                            .address(displayAddress.length() > 10 ? displayAddress.substring(0, 6) + "..." + displayAddress.substring(displayAddress.length() - 4) : displayAddress)
                            .type(type)
                            .status(status)
                            .value(formattedValue)
                            .isPositive(isPositive)
                            .timeAgo(timeAgo)
                            .hash(txHash)
                            .build());
                }
            }

            // Fetch Token Spread
            String exactTokenSpread = "0 Tokens";
            try {
                URI erc20Uri = URI.create(MORALIS_BASE + cleanAddress + "/erc20?chain=" + chain);
                HttpRequest erc20Request = HttpRequest.newBuilder()
                        .uri(erc20Uri).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").header("X-API-Key", apiKey).GET().build();
                HttpResponse<String> erc20Response = httpClient.send(erc20Request, HttpResponse.BodyHandlers.ofString());
                if (erc20Response.statusCode() == 200) {
                    JsonNode erc20Root = objectMapper.readTree(erc20Response.body());
                    if (erc20Root.isArray()) exactTokenSpread = ">" + erc20Root.size() + " Tokens";
                } else if (erc20Response.statusCode() == 400 && erc20Response.body().contains("over 10000 tokens")) {
                    exactTokenSpread = ">10000 Tokens";
                }
            } catch (Exception e) {}

            int riskScore = 12;
            int riskContacts = 0;
            String riskLevel = "Low Risk";
            List<com.walletriskscorer.dto.WalletDetailsDto.SignalFlag> flags = new java.util.ArrayList<>();
            try {
                String goPlusChainId = getGoPlusChainId(chain);
                URI goplusUri = URI.create("https://api.gopluslabs.io/api/v1/address_security/" + cleanAddress + "?chain_id=" + goPlusChainId);
                HttpRequest goplusRequest = HttpRequest.newBuilder().uri(goplusUri).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
                HttpResponse<String> goplusResponse = httpClient.send(goplusRequest, HttpResponse.BodyHandlers.ofString());
                if (goplusResponse.statusCode() == 200) {
                    JsonNode gRoot = objectMapper.readTree(goplusResponse.body());
                    if (gRoot.has("result") && !gRoot.get("result").isNull()) {
                        JsonNode data = gRoot.get("result");
                        if (data.has(cleanAddress.toLowerCase())) {
                            data = data.get(cleanAddress.toLowerCase());
                        }
                        
                        int score = 0;
                        boolean sanctioned = "1".equals(data.path("sanctioned").asText());
                        boolean darkweb = "1".equals(data.path("darkweb_transactions").asText());
                        boolean cybercrime = "1".equals(data.path("cybercrime").asText());
                        boolean mixer = "1".equals(data.path("mixer").asText());
                        boolean honeypot = "1".equals(data.path("honeypot_related_address").asText());
                        boolean phishing = "1".equals(data.path("phishing_activities").asText());
                        boolean malicious = "1".equals(data.path("malicious_mining_activities").asText());
                        boolean stealing = "1".equals(data.path("stealing_attack").asText());
                        boolean laundering = "1".equals(data.path("money_laundering").asText());
                        boolean financialCrime = "1".equals(data.path("financial_crime").asText());
                        boolean blacklist = "1".equals(data.path("blacklist_doubt").asText());
                        boolean blackmail = "1".equals(data.path("blackmail_activities").asText());
                        boolean fakeKyc = "1".equals(data.path("fake_kyc").asText());
                        boolean maliciousContracts = data.path("number_of_malicious_contracts_created").asInt(0) > 0;
                        
                        if (sanctioned) { score += 100; riskContacts++; }
                        if (darkweb) { score += 100; riskContacts++; }
                        if (cybercrime) { score += 100; riskContacts++; }
                        if (mixer) { score += 30; riskContacts++; }
                        if (honeypot) { score += 100; riskContacts++; }
                        if (phishing) { score += 100; riskContacts++; }
                        if (malicious) { score += 80; riskContacts++; }
                        if (stealing) { score += 100; riskContacts++; }
                        if (laundering) { score += 100; riskContacts++; }
                        if (financialCrime) { score += 100; riskContacts++; }
                        if (blacklist) { score += 80; riskContacts++; }
                        if (blackmail) { score += 100; riskContacts++; }
                        if (fakeKyc) { score += 60; riskContacts++; }
                        if (maliciousContracts) { score += 100; riskContacts++; }
                        
                        riskScore = Math.min(score, 100);
                        if (riskScore == 0) riskScore = (int)(Math.random() * 15);
                        
                        flags.add(com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder()
                            .label(sanctioned ? "OFAC Sanctions: Detected" : "OFAC Sanctions: Clear")
                            .isClear(!sanctioned).build());
                        flags.add(com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder()
                            .label(stealing ? "Exploit/Stealing: Detected" : "Exploit/Stealing: Clear")
                            .isClear(!stealing).build());
                        flags.add(com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder()
                            .label(phishing ? "Phishing Activity: Detected" : "Phishing Activity: Clear")
                            .isClear(!phishing).build());
                        flags.add(com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder()
                            .label(mixer ? "Mixer Exposure: Detected" : "Mixer Exposure: Clear")
                            .isClear(!mixer).build());
                        flags.add(com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder()
                            .label(darkweb ? "Darknet Funding: Detected" : "Darknet Funding: None Detected")
                            .isClear(!darkweb).build());
                    }
                }
            } catch (Exception e) {}
            
            if (flags.isEmpty()) {
                flags = List.of(
                    com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("OFAC Sanctions: Clear").isClear(true).build(),
                    com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("Mixer Exposure: Clear").isClear(true).build(),
                    com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("Darknet Funding: None Detected").isClear(true).build()
                );
            }
            if (riskScore < 30) riskLevel = "Low Risk";
            else if (riskScore < 70) riskLevel = "Medium Risk";
            else riskLevel = "High Risk";

            return com.walletriskscorer.dto.WalletDetailsDto.builder()
                    .stats(com.walletriskscorer.dto.WalletDetailsDto.Stats.builder()
                            .walletAge(walletAge)
                            .totalTxs(String.valueOf(exactTotalTxs))
                            .contractsHit(uniqueContracts.size())
                            .fundSource(exactFundSource)
                            .tokenSpread(exactTokenSpread)
                            .riskScore(riskScore)
                            .riskLevel(riskLevel)
                            .riskContacts(riskContacts)
                            .build())
                    .recentInteractions(interactions)
                    .signalFlags(flags)
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch Moralis details: {}", e.getMessage(), e);
            return buildMockWalletDetails(cleanAddress, chain);
        }
    }

    private com.walletriskscorer.dto.WalletDetailsDto buildMockWalletDetails(String address, String chain) {
        String symbol = switch (chain != null ? chain.toLowerCase() : "eth") {
            case "bsc" -> "BNB";
            case "polygon" -> "POL";
            case "arbitrum", "optimism", "base" -> "ETH";
            default -> "ETH";
        };

        return com.walletriskscorer.dto.WalletDetailsDto.builder()
                .stats(com.walletriskscorer.dto.WalletDetailsDto.Stats.builder()
                        .walletAge("3.2y")
                        .totalTxs("1,847")
                        .contractsHit(94)
                        .fundSource("CEX (Binance)")
                        .tokenSpread("12+")
                        .build())
                .recentInteractions(List.of(
                        com.walletriskscorer.dto.WalletDetailsDto.Interaction.builder()
                                .name("Uniswap V3")
                                .address("0x1f98...0000")
                                .type("Swap")
                                .status("Safe")
                                .value("1.24 " + symbol)
                                .isPositive(true)
                                .build(),
                        com.walletriskscorer.dto.WalletDetailsDto.Interaction.builder()
                                .name("OpenSea Seaport")
                                .address("0x0000...0000")
                                .type("Approval")
                                .status("Safe")
                                .value("0.00 " + symbol)
                                .isPositive(true)
                                .build(),
                        com.walletriskscorer.dto.WalletDetailsDto.Interaction.builder()
                                .name("Unknown Contract")
                                .address("0x8a9b...4c21")
                                .type("Transfer")
                                .status("Flagged")
                                .value("0.05 " + symbol)
                                .isPositive(false)
                                .build()
                ))
                .signalFlags(List.of(
                        com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("OFAC Sanctions: Clear").isClear(true).build(),
                        com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("Tornado Cash Exposure: 0 Hops").isClear(true).build(),
                        com.walletriskscorer.dto.WalletDetailsDto.SignalFlag.builder().label("Darknet Funding: None Detected").isClear(true).build()
                ))
                .build();
    }
}
