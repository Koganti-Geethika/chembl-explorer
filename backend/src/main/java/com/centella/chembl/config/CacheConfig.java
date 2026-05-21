package com.centella.chembl.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine.
 *
 * Strategy: Cache ChEMBL API responses for 10 minutes.
 * ChEMBL data is relatively static (scientific database), so a 10-min TTL
 * avoids hammering the public API while staying reasonably fresh.
 *
 * Cache is keyed by: targetId + all filter parameters.
 * Max 100 entries to avoid memory bloat.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("activities");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(100)
                .recordStats()
        );
        return manager;
    }
}
