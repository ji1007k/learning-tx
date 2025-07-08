package com.test.learningtx;

import com.test.learningtx.entity.Account;
import com.test.learningtx.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestTxService {

    @Autowired
    private AccountRepository accountRepository;

    @Transactional
    void updateBalanceAndRollback(Long accountId, Long newBalance) {
        System.out.println("=== updateAndRollback 시작 ===");

        Account beforeAccount = accountRepository.findById(accountId).get();
        System.out.println("변경 전 잔액: " + beforeAccount.getBalance());

        beforeAccount.setBalance(newBalance);
        accountRepository.saveAndFlush(beforeAccount);  // JPA 1차 캐시 우회하여 DB에 즉시 반영 (아직 커밋 안됨, 롤백 가능)
        System.out.println("변경 후 잔액: " + beforeAccount.getBalance());

        try {
            Thread.sleep(500);  // READ_UNCOMMITTED가 읽을 시간 주기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("롤백 시작!");
        throw new RuntimeException("의도적 롤백");
    }

    @Transactional
    public void updateBalanceAndCommit(Long accountId, long newBanlance) {
        System.out.println("=== 트랜잭션B 시작 ===");
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없음"));

        account.setBalance(newBanlance);
        accountRepository.saveAndFlush(account);

        // 트랜잭션 끝나면 자동 커밋됨
        System.out.println("=== 트랜잭션B 종료 ===");
    }
}
