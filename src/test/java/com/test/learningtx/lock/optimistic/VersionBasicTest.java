package com.test.learningtx.lock.optimistic;

import com.test.learningtx.entity.OptimisticAccount;
import com.test.learningtx.repository.OptimisticAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
public class VersionBasicTest {

    @Autowired
    private OptimisticAccountRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("[1] @Version 기본 동작 테스트")
    @Transactional
    void testVersionBasicBehavior() {
        OptimisticAccount account = OptimisticAccount.builder()
                .name("테스트 사용자")
                .balance(BigDecimal.valueOf(1000))
                .build();

        System.out.printf("저장 전: %s%n", account);

        OptimisticAccount saved = repository.saveAndFlush(account);

        // version이 0으로 시작하는지 확인
        assertEquals(0L, saved.getVersion(), "처음 저장 시 version은 0이어야 합니다");

        // 3. 첫 번째 수정
        saved.deposit(BigDecimal.valueOf(500));
        OptimisticAccount updated1 = repository.saveAndFlush(saved);
        System.out.printf("첫 번째 수정 후: %s%n", updated1);

        // version이 1로 증가했는지 확인
        assertEquals(1L, updated1.getVersion(), "첫 번째 수정 후 version은 1이어야 합니다");
        assertEquals(BigDecimal.valueOf(1500), updated1.getBalance());

        // 4. 두 번째 수정
        updated1.withdraw(BigDecimal.valueOf(200));
        OptimisticAccount updated2 = repository.saveAndFlush(updated1);
        System.out.printf("두 번째 수정 후: %s%n", updated2);

        // version이 2로 증가했는지 확인
        assertEquals(2L, updated2.getVersion(), "두 번째 수정 후 version은 2여야 합니다");
        assertEquals(BigDecimal.valueOf(1300), updated2.getBalance());
    }

    /**
     * 테스트 2: 낙관적 락 충돌 시뮬레이션
     *
     * 👤 사용자A: 계좌 조회 (잔액: 1000, version: 5)
     * 👤 사용자B: 계좌 조회 (잔액: 1000, version: 5)
     *
     * 👤 사용자A: 100원 출금 처리
     * 💾 UPDATE ... SET version=6 WHERE version=5 ✅ 성공!
     *
     * 👤 사용자B: 200원 출금 처리
     * 💾 UPDATE ... SET version=6 WHERE version=5 ❌ 실패!
     * 🚨 OptimisticLockingFailureException 발생!
     */
    @Test
    void testOptimisticLockException() {
        System.out.println("=== 낙관적 락 충돌 테스트 ===");

        // 1. 계좌 생성
        OptimisticAccount account = OptimisticAccount.builder()
                .name("충돌 테스트")
                .balance(BigDecimal.valueOf(1000))
                .build();
        account = repository.save(account);

        System.out.printf("초기 계좌: %s%n", account);

        // 2. 두 개의 트랜잭션에서 동일한 엔티티 조회
        OptimisticAccount account1 = repository.findById(account.getId()).orElseThrow();
        OptimisticAccount account2 = repository.findById(account.getId()).orElseThrow();

        System.out.printf("첫 번째 조회: %s%n", account1);
        System.out.printf("두 번째 조회: %s%n", account2);

        // 둘 다 같은 version을 가지고 있는지 확인
        assertEquals(account1.getVersion(), account2.getVersion());

        // 3. 첫 번째 트랜잭션에서 수정 및 저장 (성공)
        account1.deposit(BigDecimal.valueOf(100));
        OptimisticAccount saved1 = repository.save(account1);
        System.out.printf("첫 번째 수정 성공: %s%n", saved1);

        // version이 증가했는지 확인
        assertEquals(1L, saved1.getVersion());

        // 4. 두 번째 트랜잭션에서 수정 시도 (실패 예상)
        account2.withdraw(BigDecimal.valueOf(200));

        System.out.println("두 번째 수정 시도... OptimisticLockingFailureException 예상");

        assertThrows(OptimisticLockingFailureException.class, () -> {
            repository.save(account2);
        }, "version 충돌 시 OptimisticLockingFailureException이 발생해야 합니다");

        System.out.println("예상대로 OptimisticLockingFailureException 발생!");

        // 5. 최종 상태 확인
        OptimisticAccount finalAccount = repository.findById(account.getId()).orElseThrow();
        System.out.printf("최종 계좌 상태: %s%n", finalAccount);

        // 첫 번째 트랜잭션의 변경사항만 반영되어야 함
        assertEquals(BigDecimal.valueOf(1100), finalAccount.getBalance());
        assertEquals(1L, finalAccount.getVersion());
    }

    /**
     * 테스트 3: SQL 로그 확인용 테스트
     */
    @Test
    @Transactional
    void testSqlLogs() {
        System.out.println("=== SQL 로그 확인 테스트 ===");

        OptimisticAccount account = OptimisticAccount.builder()
                .name("SQL 테스트")
                .balance(BigDecimal.valueOf(1000))
                .build();

        // save()만 사용하면 Hibernate는 실제 DB에 쿼리를 보내지 않고 영속성 컨텍스트에만 저장
        // flush()까지 호출해서 바로 DB에 반영
        System.out.println("INSERT SQL 확인:");
        repository.saveAndFlush(account);

        // save() 후 1차 캐시(영속성 컨텍스트)에 이미 있어서 Hibernate가 SELECT를 안함.
        // 영속성 컨텍스트 clear로 1차 캐시 비워야 SELECT 다시 실행함
        entityManager.clear();

        System.out.println("SELECT SQL 확인:");
        OptimisticAccount found = repository.findById(account.getId()).orElseThrow();

        // 낙관적 락(Optimistic Locking)을 쓸 경우, version 필드가 바뀌어야 UPDATE가 발생
        System.out.println("UPDATE SQL 확인 (version WHERE 조건 주목!):");
        found.deposit(BigDecimal.valueOf(100));
        repository.saveAndFlush(found);

        System.out.println("테스트 완료 - 콘솔에서 SQL 로그를 확인해보세요!");
    }


}
