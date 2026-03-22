package com.atm.service;

import com.atm.cache.RedisCache;
import com.atm.model.Account;
import com.atm.repository.AccountRepository;
import com.atm.util.AppException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired private AccountRepository accountRepo;
    @Autowired private RedisCache redis;

    @Value("${login.max.attempts:3}")   private int maxAttempts;
    @Value("${login.lock.seconds:300}") private int lockSeconds;
    @Value("${session.ttl.seconds:1800}") private int sessionTtl;

    private static final SecureRandom RNG = new SecureRandom();

    public record LoginResult(String token, String ownerName, String cardNumber) {}

    public LoginResult login(String cardNumber, String rawPin) throws SQLException {
        int attempts = redis.incrementAttempts(cardNumber, lockSeconds);
        if (attempts > maxAttempts) {
            throw new AppException.ForbiddenException(
                "Tài khoản tạm khóa do nhập sai PIN quá " + maxAttempts + " lần. Vui lòng thử lại sau.");
        }

        // Load account
        Account account = accountRepo.findByCardNumber(cardNumber)
            .orElseThrow(() -> new AppException.NotFoundException("Số thẻ không tồn tại"));

        if (account.isLocked()) {
            throw new AppException.ForbiddenException("Tài khoản đã bị khóa. Vui lòng liên hệ ngân hàng.");
        }

        // Verify PIN with jBCrypt
        if (!BCrypt.checkpw(rawPin, account.getPinHash())) {
            int remaining = maxAttempts - attempts;
            String msg = remaining > 0
                ? "PIN không đúng. Còn " + remaining + " lần thử."
                : "PIN không đúng. Tài khoản đã bị tạm khóa.";
            throw new AppException.UnauthorizedException(msg);
        }

        // Success – reset attempts, create session
        redis.resetAttempts(cardNumber);

        String token = generateToken();
        redis.setSession(token, String.valueOf(account.getId()), sessionTtl);

        log.info("Login success: card={}", cardNumber);
        return new LoginResult(token, account.getOwnerName(), cardNumber);
    }

    public void logout(String token) {
        redis.deleteSession(token);
    }

    // Resolve session token → accountId
    public Long resolveSession(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException.UnauthorizedException("Vui lòng đăng nhập");
        }
        String value = redis.getSession(token);
        if (value == null) {
            throw new AppException.UnauthorizedException("Phiên đăng nhập hết hạn");
        }
        return Long.parseLong(value);
    }

    // Extract Bearer token from Authorization header
    public static String extractToken(com.sun.net.httpserver.HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
