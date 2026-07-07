package com.walletriskscorer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "api_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCache {

    @Id
    @Column(name = "cache_key", nullable = false)
    private String cacheKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_response", columnDefinition = "jsonb", nullable = false)
    private String jsonResponse;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
