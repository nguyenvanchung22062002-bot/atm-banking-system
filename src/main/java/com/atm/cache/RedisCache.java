package com.atm.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

    @Autowired
    private JedisPool pool;

    public boolean tryLock(String key, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            String result = j.set(key, "1", SetParams.setParams().nx().ex(ttlSeconds));
            return "OK".equals(result);
        } catch (Exception e) {
            log.error("Redis tryLock failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public void releaseLock(String key) {
        try (Jedis j = pool.getResource()) {
            j.del(key);
        } catch (Exception e) {
            log.error("Redis releaseLock failed: {}", e.getMessage());
        }
    }

    // Session
    public void setSession(String token, String accountId, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            j.setex("session:" + token, ttlSeconds, accountId);
        } catch (Exception e) {
            log.error("Redis setSession failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public String getSession(String token) {
        try (Jedis j = pool.getResource()) {
            return j.get("session:" + token);
        } catch (Exception e) {
            log.error("Redis getSession failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public void deleteSession(String token) {
        try (Jedis j = pool.getResource()) {
            j.del("session:" + token);
        } catch (Exception e) {
            log.error("Redis deleteSession failed: {}", e.getMessage());
        }
    }

    public int incrementAttempts(String cardNumber, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            String key = "attempts:" + cardNumber;
            long count = j.incr(key);
            if (count == 1) j.expire(key, ttlSeconds);
            return (int) count;
        } catch (Exception e) {
            log.error("Redis incrementAttempts failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public void resetAttempts(String cardNumber) {
        try (Jedis j = pool.getResource()) {
            j.del("attempts:" + cardNumber);
        } catch (Exception e) {
            log.error("Redis resetAttempts failed: {}", e.getMessage());
        }
    }

    public void set(String key, String value, int ttlSeconds) {
        try (Jedis j = pool.getResource()) {
            j.setex(key, ttlSeconds, value);
        } catch (Exception e) {
            log.error("Redis set failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public String get(String key) {
        try (Jedis j = pool.getResource()) {
            return j.get(key);
        } catch (Exception e) {
            log.error("Redis get failed: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable");
        }
    }

    public void delete(String key) {
        try (Jedis j = pool.getResource()) {
            j.del(key);
        } catch (Exception e) {
            log.error("Redis delete failed: {}", e.getMessage());
        }
    }
}