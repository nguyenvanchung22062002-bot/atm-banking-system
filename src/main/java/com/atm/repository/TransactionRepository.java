package com.atm.repository;

import com.atm.model.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TransactionRepository {

    @Autowired
    private DataSource dataSource;

    public void insert(Connection conn, Long accountId, String type,
                       BigDecimal amount, BigDecimal balanceAfter,
                       String status, String note) throws SQLException {
        String sql = """
            INSERT INTO transactions
                (account_id, type, amount, balance_after, status, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setString(2, type);
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, balanceAfter);
            ps.setString(5, status);
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    public List<Transaction> findByAccountId(Long accountId, int limit) throws SQLException {
        String sql = """
            SELECT * FROM transactions
            WHERE account_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public MonthlyStats getMonthlyStats(Long accountId, String ym) throws SQLException {
        String sql = """
            SELECT
                COUNT(*)                                                            AS tx_count,
                COALESCE(SUM(CASE WHEN type='DEPOSIT'  THEN amount ELSE 0 END), 0) AS total_deposit,
                COALESCE(SUM(CASE WHEN type='WITHDRAW' THEN amount ELSE 0 END), 0) AS total_withdraw
            FROM transactions
            WHERE account_id = ?
              AND status = 'SUCCESS'
              AND DATE_FORMAT(created_at, '%Y-%m') = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setString(2, ym);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MonthlyStats(
                            rs.getInt("tx_count"),
                            rs.getBigDecimal("total_deposit"),
                            rs.getBigDecimal("total_withdraw")
                    );
                }
            }
        }
        return new MonthlyStats(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Transaction map(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getLong("id"));
        t.setAccountId(rs.getLong("account_id"));
        t.setType(rs.getString("type"));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setBalanceAfter(rs.getBigDecimal("balance_after"));
        t.setStatus(rs.getString("status"));
        t.setNote(rs.getString("note"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
        return t;
    }

    public record MonthlyStats(int txCount, BigDecimal totalDeposit, BigDecimal totalWithdraw) {}
}