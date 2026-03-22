package com.atm.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Configuration
@ComponentScan("com.atm")
@PropertySource("classpath:application.properties")
public class AppConfig {

    @Value("${db.url}")         private String dbUrl;
    @Value("${db.username}")    private String dbUser;
    @Value("${db.password}")    private String dbPass;
    @Value("${db.pool.size:10}") private int poolSize;

    @Value("${redis.host:localhost}") private String redisHost;
    @Value("${redis.port:6379}")      private int redisPort;
    @Value("${redis.password:}")      private String redisPass;

    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dbUrl);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPass);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("ATM-HikariPool");
        return new HikariDataSource(cfg);
    }

    @Bean(destroyMethod = "close")
    public JedisPool jedisPool() {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(50);
        cfg.setMaxIdle(10);
        cfg.setMinIdle(5);
        cfg.setTestOnBorrow(true);
        cfg.setTestOnReturn(true);
        cfg.setTestWhileIdle(true);

        if (redisPass != null && !redisPass.isEmpty()) {
            return new JedisPool(cfg, redisHost, redisPort, 2000, redisPass);
        }
        return new JedisPool(cfg, redisHost, redisPort, 2000);
    }

    @Bean
    public Properties appProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            props.load(is);
        }
        return props;
    }
}
