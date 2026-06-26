package com.walletriskscorer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnsProfileDto {

    private String address;       // resolved 0x address
    private String ensName;       // e.g. vitalik.eth
    private String displayName;   // ENS name if found, else truncated 0x
    private String avatarUrl;     // avatar image URL or null
    private String description;   // ENS text record description or null
    private String twitter;       // ENS text record twitter or null
    private boolean resolved;     // true if ENS name was found

    public EnsProfileDto(String address) {
        this.address = address != null ? address.toLowerCase() : null;
        this.ensName = null;
        this.displayName = truncate(this.address);
        this.avatarUrl = null;
        this.resolved = false;
    }

    public EnsProfileDto(String address, String ensName, String avatarUrl, String description, String twitter) {
        this.address = address != null ? address.toLowerCase() : null;
        this.ensName = ensName;
        this.displayName = ensName != null ? ensName : truncate(this.address);
        this.avatarUrl = avatarUrl;
        this.description = description;
        this.twitter = twitter;
        this.resolved = ensName != null;
    }

    private String truncate(String addr) {
        if (addr == null || addr.length() < 10) return addr;
        return addr.substring(0, 6) + "..." + addr.substring(addr.length() - 4);
    }
}
