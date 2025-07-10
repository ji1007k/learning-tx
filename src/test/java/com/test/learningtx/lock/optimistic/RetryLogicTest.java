package com.test.learningtx.lock.optimistic;

import com.test.learningtx.entity.OptimisticAccount;
import com.test.learningtx.repository.OptimisticAccountRepository;
import com.test.learningtx.service.OptimisticLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class RetryLogicTest {

    @Autowired
    private OptimisticAccountRepository repository;

    @Autowired
    private OptimisticLockService optimisticService;

    private OptimisticAccount testAccount;
    private final Executor executor = Executors.newFixedThreadPool(10);

    @BeforeEach
    void setup() {
        testAccount = OptimisticAccount.builder()
                .name("재시도 테스트")
                .balance(BigDecimal.valueOf(10000))
                .build();

        testAccount = repository.saveAndFlush(testAccount);

        System.out.printf("테스트 계좌 생성: %s%n", testAccount);
    }

    @Test
    void testWithoutRetry_ShouldFail() {
        int threadCnt = 5;
        BigDecimal withdrawAmount = BigDecimal.valueOf(100);

        List<CompletableFuture<String>> tasks = new ArrayList<>();
        for (int i=0; i<threadCnt; i++) {
            int idx = i;

            CompletableFuture<String> withdrawTask = CompletableFuture.supplyAsync(() -> {
                try {
                    System.out.printf("스레드 %d 시작 (재시도x)%n", idx);
                    optimisticService.withdrawNoRetry(testAccount.getId(), withdrawAmount);
                    System.out.printf("스레드 %d 성공%n", idx);
                    return "success";
                } catch (OptimisticLockingFailureException e) {
                    System.out.printf("스레드 %d OptimisticLock 실패%n", idx);
                    return "OPTIMISTIC_FAILURE";
                } catch (Exception e) {
                    System.out.printf("스레드 %d 기타 오류. %s%n", idx, e.getMessage());
                    return "OTHER_FAILURE";
                }

            }, executor);

            tasks.add(withdrawTask);
        }

        // 모든 Future 완료 대기 및 결과 수집
        List<String> results = tasks.stream().map(CompletableFuture::join).toList();

        // 결과 분석
        long successCount = results.stream().filter("SUCCESS"::equals).count();
        long optimisticFailures = results.stream().filter("OPTIMISTIC_FAILURE"::equals).count();
        long otherFailures = results.stream().filter("OTHER_FAILURE"::equals).count();

        System.out.printf("\n=== 결과 분석 ===%n");
        System.out.printf("성공: %d건%n", successCount);
        System.out.printf("낙관적 락 실패: %d건%n", optimisticFailures);
        System.out.printf("기타 실패: %d건%n", otherFailures);

        // 동시 접근에서 낙관적 락 실패가 발생했을 것으로 예상
        assertTrue(optimisticFailures > 0, "재시도 없이는 낙관적 락 실패가 발생해야 합니다");
    }

    @Test
    void testWithRetry_ShouldSucceed() {
        int threadCnt = 5;
        BigDecimal withdrawAmount = BigDecimal.valueOf(100);

        List<CompletableFuture<Boolean>> tasks = new ArrayList<>();
        for (int i=0; i<threadCnt; i++) {
            int idx = i;

            CompletableFuture<Boolean> withdrawTask = CompletableFuture.supplyAsync(() -> {
                try {
                    // 🔑 핵심 1: 스레드별로 다른 시작 시간
                    /*낙관적 락의 한계:
                    동시성이 높으면 → 충돌 많음 → 재시도 많음 → 성능 저하
                    동시성을 줄이면 → 충돌 적음 → 하지만 순차 실행과 비슷*/
                    int staggerDelay = idx * 500; // 0ms, 500ms, 1000ms, 1500ms, 2000ms
                    if (staggerDelay > 0) {
                        Thread.sleep(staggerDelay);
                        System.out.printf("스레드 %d: %dms 지연 후 시작%n", idx, staggerDelay);
                    } else {
                        System.out.printf("스레드 %d: 즉시 시작%n", idx);
                    }
                    System.out.printf("스레드 %d 시작 (재시도 o)%n", idx);
                    optimisticService.wirhdrawWithRetry(testAccount.getId(), withdrawAmount);
                    System.out.printf("스레드 %d 성공%n", idx);
                    return true;
                } catch (Exception e) {
                    System.out.printf("스레드 %d 최종 실패. %s%n", idx, e.getMessage());
                    return false;
                }

            }, executor);

            tasks.add(withdrawTask);
        }

        // 모든 Future 완료 대기 및 결과 수집
        List<Boolean> results = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        // 결과 분석
        long successCount = results.stream().filter(Boolean::booleanValue).count();
        long failureCount = results.stream().filter(result -> !result).count();

        System.out.printf("\n=== 결과 분석 ===%n");
        System.out.printf("성공: %d건, 실패: %d건%n", successCount, failureCount);

        // 재시도 덕분에 모든 거래가 성공했을 것으로 예상
        assertEquals(threadCnt, successCount, "재시도 로직으로 모든 거래가 성공해야 합니다");
        assertEquals(0, failureCount, "재시도 로직으로 실패가 없어야 합니다");

        // 최종 잔액 확인
        OptimisticAccount finalAccount = repository.findById(testAccount.getId()).orElseThrow();
        BigDecimal expectedBalance = BigDecimal.valueOf(10000 - (100 * threadCnt));
        assertEquals(expectedBalance, finalAccount.getBalance());

        System.out.printf("최종 계좌 상태: %s%n", finalAccount);
    }


}
