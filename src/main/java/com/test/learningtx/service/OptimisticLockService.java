package com.test.learningtx.service;

import com.test.learningtx.entity.OptimisticAccount;
import com.test.learningtx.repository.OptimisticAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

/**
 * 재시도 전략
 * - 1. 즉시 재시도
 * - [적용됨] 2. 백오프 재시도 (권장)
 *     > 즉시 재시도x. 잠시 대기 후 재시도
 * - 3. Spring Retry (자동)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticLockService {

    private final OptimisticAccountRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 재시도 로직이 있는 출금 처리
     * 낙관적 락은 재시도 시 동시성이 높을수록 충돌 확률 증가
     */
    @Transactional
    public void wirhdrawWithRetry(Long accountId, BigDecimal amount) {
        int maxRetries = 5;  // 최대 5번 시도
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                attempt++;
                log.info("🔄 출금 시도 {}/{}: 계좌={}, 금액={}", attempt, maxRetries, accountId, amount);

                // 1. 계좌 조회 (version 포함)
                OptimisticAccount account = repository.findById(accountId)
                        .orElseThrow(() -> new IllegalArgumentException("계좌 없음: " + accountId));

                entityManager.refresh(account);  // 강제로 최신 DB 데이터 조회 -> 1차 캐싱으로 인한 재시도 실패 방지
                log.info("📖 조회된 계좌: 잔액={}, version={}", account.getBalance(), account.getVersion());

                // 2. 비즈니스 로직 실행
                account.withdraw(amount);

                // 3. 저장 (여기서 OptimisticLockingFailureException 발생 가능)
                OptimisticAccount saved = repository.saveAndFlush(account);

                log.info("✅ 출금 성공! 최종 잔액={}, version={}", saved.getBalance(), saved.getVersion());
                return; // 성공하면 메서드 종료

            } catch (OptimisticLockingFailureException e) {
                log.warn("⚠️ 낙관적 락 충돌 발생! 시도 {}/{}", attempt, maxRetries);

                if (attempt >= maxRetries) {
                    log.error("❌ 최대 재시도 횟수 초과! 출금 실패");
                    throw new RuntimeException("출금 처리 실패: 너무 많은 동시 접근", e);
                }

                // 백오프 전략: 점진적으로 대기 시간 증가
                // 시간이 짧을 수록 동시성 높음 -> 충돌 발생
                // 🔑 핵심 2: 재시도할 때도 랜덤 지연으로 분산
                try {
                    int retryDelay = 100 + new Random().nextInt(300); // 100~399ms 랜덤
                    System.out.printf("⏰ [%s] %dms 랜덤 대기%n", Thread.currentThread().getName(), retryDelay);
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("대기 중 인터럽트: " + Thread.currentThread().getName(), ie);
                }
            }
        }
    }

    /**
     * 재시도 없는 출금 (비교용)
     */
    @Transactional
    public void withdrawNoRetry(Long accountId, BigDecimal amount) {
        log.info("🚫 재시도 없는 출금: 계좌={}, 금액={}", accountId, amount);

        OptimisticAccount account = repository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌 없음: " + accountId));

        account.withdraw(amount);
        repository.save(account); // 실패하면 그냥 예외 발생

        log.info("✅ 출금 성공 (재시도 없음)");
    }
}
