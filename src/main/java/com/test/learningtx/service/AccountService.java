package com.test.learningtx.service;

import com.test.learningtx.entity.Account;
import com.test.learningtx.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

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

        log.info("=== 계좌 이체 완료 ===");
    }

    /**
     * 1. READ_UNCOMMITTED: 가장 낮은 격리 레벨
     *  - Dirty Read 가능 (커밋x 데이터 읽기)
     *  - 성능은 가장 좋지만 데이터 일관성 문제 발생 가능
     *  - A 트랜잭션이 데이터 변경 (아직 커밋 X) -> B 트랜잭션이 A가 변경한 데이터 읽음 -> A 트랜잭션 롤백 -> B가 읽은 데이터는 유령 데이터가 됨
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public Account readUncommitted(Long accountId) {
        log.info("=== READ_UNCOMMITTED로 계좌 조회: {} ===", accountId);
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없음"));
    }

    /**
     * 2. READ_COMMMITTED
     *  - 커밋된 데이터만 읽기 가능
     *  - Dirty Read 방지
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)    // H2 기본 설정
    public Account readCommitted(Long accountId) {
        log.info("=== READ_COMMITTED로 계좌 조회: {} ===", accountId);
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없음"));
    }

    /**
     * 3. READ_COMMITTED
     *  - Dirty Read 방지
     *  - Non-Repeatable Read 방지
     *  - 동일 트랜잭션 내에서 항상 같은 값 읽기 보장
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account readRepeatableRead(Long accountId) {
        log.info("=== REPEATABLE_READ로 계좌 조회: {} ===", accountId);
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없음"));
    }
}
