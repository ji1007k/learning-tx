package com.test.learningtx.service;

import com.test.learningtx.entity.Account;
import com.test.learningtx.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
//    private final LogService logService;

    @Transactional  // 메서드 전체가 하나의 트랜잭션
    public void transfer(Long fromId, Long toId, Long amount) {
        log.info("=== 계좌 이체 시작: {} -> {}, 금액: {} ===", fromId, toId, amount);

        Account fromAccount = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("출금 계좌를 찾을 수 없음"));
        
        Account toAccount = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없음"));

        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

//        logService.logTransaction("TRANSFER",
//                String.format("Transfer %d from %s to %s", amount, fromAccount, toAccount));

        log.info("=== 계좌 이체 완료 ===");
        

    }
}
