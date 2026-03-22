package com.atm.service;

import com.atm.cache.RedisCache;
import com.atm.model.Account;
import com.atm.model.Transaction;
import com.atm.repository.AccountRepository;
import com.atm.repository.TransactionRepository;
import com.atm.util.AppException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private RedisCache redis;
    @Autowired private DataSource dataSource;

    // Get account info
    public Account getAccount(Long accountId) throws SQLException {
        return accountRepo.findById(accountId)
            .orElseThrow(() -> new AppException.NotFoundException("Tài khoản không tồn tại"));
    }

    // DEPOSIT
    public BigDecimal deposit(Long accountId, BigDecimal amount) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            throw new AppException.BadRequestException("Số tiền nạp tối thiểu là 10,000 VNĐ");
        }
        if (amount.compareTo(BigDecimal.valueOf(50_000_000)) > 0) {
            throw new AppException.BadRequestException("Số tiền nạp tối đa là 50,000,000 VNĐ/lần");
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = accountRepo.findByIdForUpdate(conn, accountId);
                BigDecimal newBalance = account.getBalance().add(amount);

                accountRepo.updateBalance(conn, accountId, newBalance);
                txRepo.insert(conn, accountId, "DEPOSIT", amount, newBalance, "SUCCESS",
                    "Nạp tiền thành công");

                conn.commit();
                log.info("Deposit success: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
                return newBalance;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public BigDecimal withdraw(Long accountId, BigDecimal amount) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.valueOf(50_000)) < 0) {
            throw new AppException.BadRequestException("Số tiền rút tối thiểu là 50,000 VNĐ");
        }
        if (amount.compareTo(BigDecimal.valueOf(20_000_000)) > 0) {
            throw new AppException.BadRequestException("Số tiền rút tối đa là 20,000,000 VNĐ/lần");
        }

        String lockKey = "lock:withdraw:" + accountId;
        boolean locked = redis.tryLock(lockKey, 10);
        if (!locked) {
            throw new AppException.ConflictException(
                "Tài khoản đang xử lý giao dịch khác. Vui lòng thử lại.");
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Account account = null;
            try {
                account = accountRepo.findByIdForUpdate(conn, accountId);

                if (account.isLocked()) {
                    throw new AppException.ForbiddenException("Tài khoản đã bị khóa");
                }

                if (account.getBalance().compareTo(amount) < 0) {
                    throw new AppException.BadRequestException(
                        "Số dư không đủ. Số dư hiện tại: " +
                        String.format("%,.0f", account.getBalance()) + " VNĐ");
                }

                BigDecimal newBalance = account.getBalance().subtract(amount);

                accountRepo.updateBalance(conn, accountId, newBalance);
                txRepo.insert(conn, accountId, "WITHDRAW", amount, newBalance, "SUCCESS",
                    "Rút tiền thành công");

                conn.commit();
                log.info("Withdraw success: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
                return newBalance;

            } catch (AppException e) {
                try {
                    conn.rollback();
                    if (account != null) {
                        logFailedTx(accountId, "WITHDRAW", amount,
                            account.getBalance(), e.getMessage());
                    }
                } catch (Exception ignored) {}
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw new AppException.InternalException("Lỗi hệ thống: " + e.getMessage());
            }
        } finally {
            redis.releaseLock(lockKey);
        }
    }

    // Change PIN
    public void changePin(Long accountId, String currentPin, String newPin) throws SQLException {
        if (newPin == null || !newPin.matches("\\d{4,6}")) {
            throw new AppException.BadRequestException("PIN mới phải là 4-6 chữ số");
        }

        Account account = accountRepo.findById(accountId)
            .orElseThrow(() -> new AppException.NotFoundException("Tài khoản không tồn tại"));

        if (!BCrypt.checkpw(currentPin, account.getPinHash())) {
            throw new AppException.UnauthorizedException("PIN hiện tại không đúng");
        }

        if (BCrypt.checkpw(newPin, account.getPinHash())) {
            throw new AppException.BadRequestException("PIN mới không được trùng PIN cũ");
        }

        String newHash = BCrypt.hashpw(newPin, BCrypt.gensalt(10));

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                accountRepo.updatePin(conn, accountId, newHash);
                conn.commit();
                log.info("PIN changed: accountId={}", accountId);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Transaction History
    public List<Transaction> getHistory(Long accountId, int limit) throws SQLException {
        return txRepo.findByAccountId(accountId, Math.min(limit, 100));
    }

    // Monthly Report
    public TransactionRepository.MonthlyStats getMonthlyReport(Long accountId, String yearMonth) throws SQLException {
        if (!yearMonth.matches("\\d{4}-\\d{2}")) {
            throw new AppException.BadRequestException("Định dạng tháng phải là YYYY-MM");
        }
        return txRepo.getMonthlyStats(accountId, yearMonth);
    }

    // Internal helper: log failed transaction
    private void logFailedTx(Long accountId, String type, BigDecimal amount,
                              BigDecimal currentBalance, String note) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            txRepo.insert(conn, accountId, type, amount, currentBalance, "FAILED", note);
        } catch (Exception e) {
            log.error("Failed to log failed transaction: {}", e.getMessage());
        }
    }
}
