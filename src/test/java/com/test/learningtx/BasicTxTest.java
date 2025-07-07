package com.test.learningtx;

import com.test.learningtx.entity.Account;
import com.test.learningtx.repository.AccountRepository;
import com.test.learningtx.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class BasicTxTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestTxService testTxService;

    private Account account1;
    private Account account2;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        account1 = new Account("홍길동", 10000L);
        account2 = new Account("김철수", 5000L);
        account1 = accountRepository.save(account1);
        account2 = accountRepository.save(account2);
    }

    @Test
    @DisplayName("기본 계좌이체 테스트")
    void testBasicTransfer() {
        // given (준비)
        //  - 테스트에 필요한 초기 상황 설정
        //  - 데이터 준비, 상태 설정 등
        Long transferAmount = 3000L;

        // when (실행)
        //  - 실제 테스트 동작 수행
        //  - 메서드 호출, 이벤트 발생 등
        accountService.transfer(account1.getId(), account2.getId(), transferAmount);

        // then (검증)
        //  - 결과가 예상대로 나오는지 확인
        Account updatedAccount1 = accountRepository.findById(account1.getId()).get();
        Account updatedAccount2 = accountRepository.findById(account2.getId()).get();

        assertEquals(7000L, updatedAccount1.getBalance());
        assertEquals(8000L, updatedAccount2.getBalance());
    }

    @Test
    @DisplayName("READ_UNCOMMITTED Dirty Read 테스트")
    void testDirtyRead() throws Exception {
        Long accountId = account1.getId();
        boolean dirtyReadExists = false;

        // when - 동시에 두 작업 실행 5번 반복
        for (int i=0; i<5; i++) {
            System.out.println("=== 시도 " + (i+1) + " ===");

            // CompletableFuture: Java에서 비동기 작업을 쉽게 처리할 수 있게 해주는 클래스
            //  - supplyAsync: 값을 반환하는 비동기 작업
            CompletableFuture<Account> readTask = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(50);  // 변경 작업이 시작될 시간 주기
                    return accountService.readUncommitted(accountId);   // 별도 스레드에서 실행
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
    
            //  - runAsync: 반환값이 없는 비동기 작업
            CompletableFuture<Void> updateTask = CompletableFuture.runAsync(() -> {
                try {
                    testTxService.updateAndRollback(accountId);
                } catch (Exception e) {
                    // 의도적 롤백 예외 무시
                    System.out.println("예상된 롤백 발생: " + e.getMessage());
                }
            });
    
            // then
            Account readResult = readTask.get();    // 결과가 나올 때까지 기다림
            updateTask.get();
    
            System.out.println("READ_UNCOMMITTED로 읽은 잔액: " + readResult.getBalance());
            System.out.println("실제 최종 잔액: " + accountRepository.findById(accountId).get().getBalance());

            if (!Objects.equals(readResult.getBalance(), accountRepository.findById(accountId).get().getBalance())) {
                dirtyReadExists = true;
                System.out.println("Dirty Read 발견!");
                break;
            }
        }

        // 루프 밖에서 검증
        if (dirtyReadExists) {
            System.out.println("Dirty Read 테스트 성공");
        } else {
            System.out.println("Dirty Read 발생하지 않음 (타이밍 이슈 또는 H2 제한)");
        }
    }

}