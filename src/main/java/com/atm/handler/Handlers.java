package com.atm.handler;

import com.atm.service.AccountService;
import com.atm.service.AuthService;
import com.atm.util.AppException;
import com.atm.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// Login
@Component
class LoginHandler extends BaseHandler {

    @Autowired private AuthService authService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "POST")) return;

        record LoginReq(String cardNumber, String pin) {}
        LoginReq req = JsonUtil.parse(ex, LoginReq.class);

        if (req == null || req.cardNumber() == null || req.pin() == null) {
            throw new AppException.BadRequestException("cardNumber và pin không được để trống");
        }

        AuthService.LoginResult result = authService.login(req.cardNumber().trim(), req.pin());
        JsonUtil.sendSuccess(ex, result);
    }
}

// Logout
@Component
class LogoutHandler extends BaseHandler {

    @Autowired private AuthService authService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "POST")) return;
        String token = AuthService.extractToken(ex);
        if (token != null) authService.logout(token);
        JsonUtil.sendSuccess(ex, Map.of("message", "Đăng xuất thành công"));
    }
}

//Get Account Info
@Component
class AccountInfoHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "GET")) return;
        Long accountId = authenticate(ex, authService);
        var account = accountService.getAccount(accountId);

        var safe = Map.of(
            "ownerName",  account.getOwnerName(),
            "cardNumber", maskCard(account.getCardNumber()),
            "balance",    account.getBalance()
        );
        JsonUtil.sendSuccess(ex, safe);
    }

    private String maskCard(String card) {
        if (card == null || card.length() < 8) return card;
        return "**** **** **** " + card.substring(card.length() - 4);
    }
}


// Deposit
@Component
class DepositHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "POST")) return;
        Long accountId = authenticate(ex, authService);

        record AmountReq(BigDecimal amount) {}
        AmountReq req = JsonUtil.parse(ex, AmountReq.class);
        if (req == null || req.amount() == null) {
            throw new AppException.BadRequestException("Thiếu trường amount");
        }

        BigDecimal newBalance = accountService.deposit(accountId, req.amount());
        JsonUtil.sendSuccess(ex, Map.of(
            "message",    "Nạp tiền thành công",
            "amount",     req.amount(),
            "newBalance", newBalance
        ));
    }
}


// Withdraw
@Component
class WithdrawHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "POST")) return;
        Long accountId = authenticate(ex, authService);

        record AmountReq(BigDecimal amount) {}
        AmountReq req = JsonUtil.parse(ex, AmountReq.class);
        if (req == null || req.amount() == null) {
            throw new AppException.BadRequestException("Thiếu trường amount");
        }

        BigDecimal newBalance = accountService.withdraw(accountId, req.amount());
        JsonUtil.sendSuccess(ex, Map.of(
            "message",    "Rút tiền thành công",
            "amount",     req.amount(),
            "newBalance", newBalance
        ));
    }
}

// Change PIN
@Component
class ChangePinHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "PUT")) return;
        Long accountId = authenticate(ex, authService);

        record ChangePinReq(String currentPin, String newPin) {}
        ChangePinReq req = JsonUtil.parse(ex, ChangePinReq.class);
        if (req == null || req.currentPin() == null || req.newPin() == null) {
            throw new AppException.BadRequestException("Thiếu currentPin hoặc newPin");
        }

        accountService.changePin(accountId, req.currentPin(), req.newPin());
        JsonUtil.sendSuccess(ex, Map.of("message", "Đổi PIN thành công"));
    }
}


//Transaction History
@Component
class HistoryHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "GET")) return;
        Long accountId = authenticate(ex, authService);

        int limit = 20;
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "limit".equals(kv[0])) {
                    try { limit = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {}
                }
            }
        }

        var history = accountService.getHistory(accountId, limit);
        JsonUtil.sendSuccess(ex, history);
    }
}

// Monthly Report
@Component
class ReportHandler extends BaseHandler {

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;

    @Override
    protected void doHandle(HttpExchange ex) throws Exception {
        if (!requireMethod(ex, "GET")) return;
        Long accountId = authenticate(ex, authService);

        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "month".equals(kv[0])) {
                    month = kv[1];
                }
            }
        }

        var stats = accountService.getMonthlyReport(accountId, month);
        JsonUtil.sendSuccess(ex, Map.of(
            "month",         month,
            "txCount",       stats.txCount(),
            "totalDeposit",  stats.totalDeposit(),
            "totalWithdraw", stats.totalWithdraw(),
            "netFlow",       stats.totalDeposit().subtract(stats.totalWithdraw())
        ));
    }
}


@Component
public class Handlers {

    @Autowired LoginHandler loginHandler;
    @Autowired LogoutHandler logoutHandler;
    @Autowired AccountInfoHandler accountInfoHandler;
    @Autowired DepositHandler depositHandler;
    @Autowired WithdrawHandler withdrawHandler;
    @Autowired ChangePinHandler changePinHandler;
    @Autowired HistoryHandler historyHandler;
    @Autowired ReportHandler reportHandler;

    public LoginHandler login()        { return loginHandler; }
    public LogoutHandler logout()      { return logoutHandler; }
    public AccountInfoHandler info()   { return accountInfoHandler; }
    public DepositHandler deposit()    { return depositHandler; }
    public WithdrawHandler withdraw()  { return withdrawHandler; }
    public ChangePinHandler changePin(){ return changePinHandler; }
    public HistoryHandler history()    { return historyHandler; }
    public ReportHandler report()      { return reportHandler; }
}
