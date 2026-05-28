package com.dasigconnect.backend.config;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ANALYTICS_SUMMARY_CACHE = "analyticsSummary";

    @Bean
    public CacheManager cacheManager() {
        return new TtlCacheManager(List.of(
                new TtlCache(ANALYTICS_SUMMARY_CACHE, Duration.ofSeconds(60))));
    }

    private static final class TtlCacheManager implements CacheManager {
        private final Map<String, Cache> caches;

        private TtlCacheManager(List<Cache> caches) {
            this.caches = caches.stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Cache::getName, cache -> cache));
        }

        @Override
        public Cache getCache(String name) {
            return caches.get(name);
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }
    }

    private static final class TtlCache extends AbstractValueAdaptingCache {
        private final String name;
        private final long ttlMillis;
        private final ConcurrentHashMap<Object, Entry> store = new ConcurrentHashMap<>();

        private TtlCache(String name, Duration ttl) {
            super(false);
            this.name = name;
            this.ttlMillis = ttl.toMillis();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return store;
        }

        @Override
        protected Object lookup(Object key) {
            Entry entry = store.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.expiresAtMillis() <= System.currentTimeMillis()) {
                store.remove(key, entry);
                return null;
            }
            return entry.value();
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            Object existing = lookup(key);
            if (existing != null) {
                @SuppressWarnings("unchecked")
                T value = (T) fromStoreValue(existing);
                return value;
            }
            try {
                T loaded = valueLoader.call();
                put(key, loaded);
                return loaded;
            } catch (Exception ex) {
                throw new ValueRetrievalException(key, valueLoader, ex);
            }
        }

        @Override
        public void put(Object key, Object value) {
            store.put(key, new Entry(toStoreValue(value), System.currentTimeMillis() + ttlMillis));
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            Entry fresh = new Entry(toStoreValue(value), System.currentTimeMillis() + ttlMillis);
            Entry existing = store.putIfAbsent(key, fresh);
            return existing == null ? null : toValueWrapper(existing.value());
        }

        @Override
        public void evict(Object key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        private record Entry(Object value, long expiresAtMillis) {}
    }
}
