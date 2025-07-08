package com.test.learningtx.service;

import com.test.learningtx.entity.Account;
import com.test.learningtx.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없음: " + accountId));
    }

    @Transactional  // 메서드 전체가 하나의 트랜잭션
    public void transfer(Long fromId, Long toId, Long amount) {
        log.info("=== 계좌 이체 시작: {} -> {}, 금액: {} ===", fromId, toId, amount);

        Account fromAccount = getAccountById(fromId);
        
        Account toAccount = getAccountById(toId);

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
        return getAccountById(accountId);
    }

    /**
     * 2. READ_COMMMITTED
     *  - 커밋된 데이터만 읽기 가능
     *  - Dirty Read 방지
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)    // H2 기본 설정
    public Account readCommitted(Long accountId) {
        log.info("=== READ_COMMITTED로 계좌 조회: {} ===", accountId);
        return getAccountById(accountId);
    }

    /**
     * 3. REPEATABLE_READ
     *  - Dirty Read 방지
     *  - Non-Repeatable Read 방지
     *  - Non-Repeatable Read: 동일 트랜잭션 내에서 동일한 행을 두 번 조회했을 때, 다른 트랜잭션의 수정(UPDATE)으로 인해 다른 값이 나타나는 현상
     *  - 동일 트랜잭션 내에서 항상 같은 값 읽기 보장
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<Account> readTwiceInSameTransaction(Long accountId) {
        log.info("=== 트랜잭션A 시작 ===");
        log.info("=== REPEATABLE_READ로 계좌 조회: {} ===", accountId);
        
        // 첫 번째 읽기
        Account first = getAccountById(accountId);

        // 중간에 잠깐 대기 (다른 트랜잭션이 수정할 시간)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 두 번째 읽기 (같은 트랜잭션 내에서)
        Account second = getAccountById(accountId);

        log.info("=== 트랜잭션A 종료 ===");
        return List.of(first, second);
    }

    /**
     *  READ_COMMITTED 에서 Phantom Read 발생 테스트
     * - Phantom Read: 동일 트랜잭션 내에서 같은 범위 쿼리를 두 번 실행했을 때 새로운 행이 추가로 조회됨
     * - 삽입(INSERT)으로 인한 차이
     * - 마치 유령(Phantom)처럼 갑자기 나타나는 행들
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<List<Account>> getAccountsByBalanceRangeReadCommitted(Long minBalance, Long maxBalance) {
        log.info("=== REPEATABLE_READ로 잔액 범위 조회: {} ~ {} ===", minBalance, maxBalance);
        
        List<Account> firstList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("첫 번째 조회 완료: {}건, 시간: {}", firstList.size(), System.currentTimeMillis());

        try {
            Thread.sleep(2000); // 다른 트랜잭션이 삽입할 시간동안 대기
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<Account> secondList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("두 번째 조회 완료: {}건, 시간: {}", secondList.size(), System.currentTimeMillis());

        return List.of(firstList, secondList);
    }

    /**
     *  REPEATABLE_READ 에서 Phantom Read 발생 테스트용
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<List<Account>> getAccountsByBalanceRangeRepeatableRead(Long minBalance, Long maxBalance) {
        log.info("=== REPEATABLE_READ로 잔액 범위 조회: {} ~ {} ===", minBalance, maxBalance);

        List<Account> firstList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("첫 번째 조회 완료: {}건, 시간: {}", firstList.size(), System.currentTimeMillis());

        try {
            Thread.sleep(2000); // 다른 트랜잭션이 삽입할 시간동안 대기
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<Account> secondList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("두 번째 조회 완료: {}건, 시간: {}", secondList.size(), System.currentTimeMillis());

        return List.of(firstList, secondList);
    }

    /**
     * 4. SERIALIZABLE: 가장 엄격한 격리레벨
     *  - 모든 이상현상 방지
     *  - Dirty Read 방지
     *  - Non-Repeatable Read 방지
     *  - Phantom Read 방지
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Account readSerializable(Long accountId) {
        log.info("=== SERIALIZABLE로 계좌 조회: {} ===", accountId);
        return getAccountById(accountId);
    }

    /**
     * SERIALIZABLE로 잔액 범위 조회 (Phantom Read 방지)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<List<Account>> getAccountsByBalanceRangeSerializable(Long minBalance, Long maxBalance) {
        log.info("=== SERIALIZABLE로 잔액 범위 조회: {} ~ {} ===", minBalance, maxBalance);

        List<Account> firstList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("첫 번째 조회 완료: {}건, 시간: {}", firstList.size(), System.currentTimeMillis());

        try {
            Thread.sleep(2000); // 다른 트랜잭션이 삽입할 시간동안 대기
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<Account> secondList = accountRepository.findByBalanceBetween(minBalance, maxBalance);
        log.info("두 번째 조회 완료: {}건, 시간: {}", secondList.size(), System.currentTimeMillis());

        return List.of(firstList, secondList);
    }
}
