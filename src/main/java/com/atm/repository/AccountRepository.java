package com.atm.repository;

import com.atm.model.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class AccountRepository {

    @Autowired
    private DataSource dataSource;

    public Optional<Account> findByCardNumber(String cardNumber) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE card_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Account> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Account findByIdForUpdate(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                throw new SQLException("Account not found: " + id);
            }
        }
    }

    public void updateBalance(Connection conn, Long id, java.math.BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ?, updated_at = NOW() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void updatePin(Connection conn, Long id, String newPinHash) throws SQLException {
        String sql = "UPDATE accounts SET pin_hash = ?, updated_at = NOW() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPinHash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public DataSource getDataSource() { return dataSource; }

    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getLong("id"));
        a.setCardNumber(rs.getString("card_number"));
        a.setOwnerName(rs.getString("owner_name"));
        a.setPinHash(rs.getString("pin_hash"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setLocked(rs.getBoolean("is_locked"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) a.setCreatedAt(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) a.setUpdatedAt(ua.toLocalDateTime());
        return a;
    }
}
