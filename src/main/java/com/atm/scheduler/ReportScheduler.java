package com.atm.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    @Autowired private DataSource dataSource;

    @Value("${report.scheduler.hour:2}")
    private int scheduledHour;

    @PostConstruct
    public void start() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "report-scheduler");
                    t.setDaemon(true);
                    return t;
                });

        long initialDelay = computeDelaySeconds(scheduledHour);
        log.info("Report scheduler started. First run in {} minutes", initialDelay / 60);

        scheduler.scheduleAtFixedRate(
                this::refreshMonthlyReport,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
    }

    public void refreshMonthlyReport() {
        log.info("Running monthly report refresh...");
        String sql = """
            INSERT INTO report_monthly
                (account_id, ym, total_deposit, total_withdraw, tx_count)
            SELECT
                account_id,
                DATE_FORMAT(created_at, '%Y-%m')                                       AS ym,
                COALESCE(SUM(CASE WHEN type='DEPOSIT'  THEN amount ELSE 0 END), 0)     AS total_deposit,
                COALESCE(SUM(CASE WHEN type='WITHDRAW' THEN amount ELSE 0 END), 0)     AS total_withdraw,
                COUNT(*)                                                                AS tx_count
            FROM transactions
            WHERE status = 'SUCCESS'
              AND created_at >= DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-01')
            GROUP BY account_id, ym
            ON DUPLICATE KEY UPDATE
                total_deposit  = VALUES(total_deposit),
                total_withdraw = VALUES(total_withdraw),
                tx_count       = VALUES(tx_count),
                updated_at     = NOW()
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int rows = ps.executeUpdate();
            log.info("Report refresh complete. Rows upserted: {}", rows);
        } catch (Exception e) {
            log.error("Report refresh failed", e);
        }
    }

    private long computeDelaySeconds(int targetHour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(LocalTime.of(targetHour, 0));
        if (!next.isAfter(now)) next = next.plusDays(1);
        return ChronoUnit.SECONDS.between(now, next);
    }
}