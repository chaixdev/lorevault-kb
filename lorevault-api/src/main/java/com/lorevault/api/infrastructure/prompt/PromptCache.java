package com.lorevault.api.infrastructure.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Simple in-memory cache for prompt templates with TTL support.
 * Provides basic caching functionality without external dependencies.
 */
@Component
public class PromptCache {

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public PromptCache() {
        this(300); // 5 minutes default TTL
    }

    public PromptCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Get from cache or load using the provided supplier.
     * 
     * @param key the cache key
     * @param loader supplier to load the value if not cached or expired
     * @return the cached or newly loaded PromptTemplate
     */
    public PromptTemplate getOrLoad(String key, Supplier<PromptTemplate> loader) {
        CacheEntry entry = cache.get(key);
        
        if (entry != null && !isExpired(entry)) {
            return entry.value;
        }
        
        // Load new value
        PromptTemplate value = loader.get();
        cache.put(key, new CacheEntry(value, Instant.now().getEpochSecond()));
        return value;
    }

    private boolean isExpired(CacheEntry entry) {
        return ttlSeconds > 0 && (Instant.now().getEpochSecond() - entry.timestamp) > ttlSeconds;
    }

    /**
     * Clear all cached entries.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get the current cache size.
     * 
     * @return number of cached entries
     */
    public int size() {
        return cache.size();
    }

    private record CacheEntry(PromptTemplate value, long timestamp) {}
}