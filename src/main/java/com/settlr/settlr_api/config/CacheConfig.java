package com.settlr.settlr_api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@SuppressWarnings("null")
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new CaffeineCache("expense-categories",
                        Caffeine.newBuilder()
                                .maximumSize(500)
                                .expireAfterWrite(24, TimeUnit.HOURS)
                                .build()),
                new CaffeineCache("user-insights",
                        Caffeine.newBuilder()
                                .maximumSize(100)
                                .expireAfterWrite(1, TimeUnit.HOURS)
                                .build()),
                new CaffeineCache("trip-summaries",
                        Caffeine.newBuilder()
                                .maximumSize(200)
                                .expireAfterWrite(2, TimeUnit.HOURS)
                                .build())
        ));
        return cacheManager;
    }
}
