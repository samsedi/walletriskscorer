package com.walletriskscorer.repository;

import com.walletriskscorer.entity.ApiCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiCacheRepository extends JpaRepository<ApiCache, String> {
}
