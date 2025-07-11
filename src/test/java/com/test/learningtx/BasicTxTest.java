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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
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
        account1 = new Account("홍길동", BigDecimal.valueOf(10000L));
        account2 = new Account("김철수", BigDecimal.valueOf(5000L));
        account1 = accountRepository.save(account1);
        account2 = accountRepository.save(account2);
    }

    @Test
    @DisplayName("[1] 기본 계좌이체 테스트")
    void testBasicTransfer() {
        // given (준비)
        //  - 테스트에 필요한 초기 상황 설정
        //  - 데이터 준비, 상태 설정 등
        BigDecimal transferAmount = BigDecimal.valueOf(3000L);

        // when (실행)
        //  - 실제 테스트 동작 수행
        //  - 메서드 호출, 이벤트 발생 등
        accountService.transfer(account1.getId(), account2.getId(), transferAmount);

        // then (검증)
        //  - 결과가 예상대로 나오는지 확인
        Account updatedAccount1 = accountRepository.findById(account1.getId()).get();
        Account updatedAccount2 = accountRepository.findById(account2.getId()).get();

        assertEquals(BigDecimal.valueOf(7000L), updatedAccount1.getBalance());
        assertEquals(BigDecimal.valueOf(8000L), updatedAccount2.getBalance());
    }

    @Test
    @DisplayName("[2] READ_UNCOMMITTED: Dirty Read 테스트")
    void testDirtyRead() throws Exception {
        Long accountId = account1.getId();
        boolean dirtyReadExists = false;

        // 10번 반복할 동안 Dirty Read 발생여부 확인
        // when - 동시에 두 작업 실행
        for (int i=0; i<10; i++) {
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
                    testTxService.updateBalanceAndRollback(accountId, BigDecimal.valueOf(5000L));
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
            System.out.println("Dirty Read 테스트 종료");
        } else {
            System.out.println("Dirty Read 발생하지 않음 (타이밍 이슈 또는 H2 제한)");
        }
    }

    @Test
    @DisplayName("[3-1] READ_COMMITTED: 기본 테스트")
    void testReadCommitted() {
        // given
        Account account = new Account("READ_COMMITTED_TEST", BigDecimal.valueOf(1000L));
        accountRepository.saveAndFlush(account);

        Account result = accountService.readCommitted(account.getId());

        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualTo(BigDecimal.valueOf(1000L));
        assertThat(result.getName()).isEqualTo("READ_COMMITTED_TEST");
    }

    @Test
    @DisplayName("[3-2] READ_COMMITTED: Dirty Read 방지 테스트")
    void testReadCommittedPreventsDirtyRead() throws Exception {
        // given
        Account account = new Account("DIRTY_READ_TEST", BigDecimal.valueOf(1000L));
        accountRepository.saveAndFlush(account);

        // 10번 반복할 동안 Dirty Read 발생여부 확인
        for (int i=0; i<10; i++) {
            System.out.println("=== 시도 " + (i+1) + " ===");

            // when - 별도 스레드에서 READ_COMMITTED로 읽기
            CompletableFuture<Account> readFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(500); // 수정 트랜잭션이 시작된 후 읽기
                    return accountService.readCommitted(account.getId());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // 메인 스레드에서 데이터 수정 후 롤백
            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    testTxService.updateBalanceAndRollback(account.getId(), BigDecimal.valueOf(5000L));
                } catch (Exception e) {
                    // 롤백 예외는 예상된 동작
                }
            });

            // 모든 작업 완료 대기
            //  - get: 타임아웃 지정 가능
            CompletableFuture.allOf(readFuture, writeFuture).get(5, TimeUnit.SECONDS);

            // then - uncommitted 데이터(5000)가 아닌 원래 값(1000)을 읽어야 함
            Account result = readFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getBalance()).isEqualTo(BigDecimal.valueOf(1000L)); // Dirty Read 방지
        }

        System.out.println("테스트 종료: Dirty Read 발생하지 않음");
    }

    /**
     * supplyAsync // 리턴값 있는 비동기 (새 스레드에서 시작)
     * runAsync // 리턴값 없는 비동기 (새 스레드에서 시작)
     * thenRun // 리턴값 없는 체이닝 (이전 작업 완료 후 실행)
     * thenApply // 리턴값 있는 체이닝 (이전 작업 완료 후 실행)
     */
    @Test
    @DisplayName("[3-3] READ_COMMITTED: Non-Repeatable Read 발생 테스트")
    void testReadCommittedNonRepeatableRead() throws ExecutionException, InterruptedException, TimeoutException {
        Account account = new Account("NON_REPEATABLE_TEST", BigDecimal.valueOf(2000L));
        accountRepository.saveAndFlush(account);

        CompletableFuture<Account> firstRead = CompletableFuture.supplyAsync(() -> {
            return accountService.readCommitted(account.getId());
        });

        // 첫 번째 읽기 후에 수정
        CompletableFuture<Void> updateTask = firstRead.thenRun(() -> {
            testTxService.updateBalanceAndCommit(account.getId(), BigDecimal.valueOf(3000L));
        });

        // 수정 후 두번째 읽기
        CompletableFuture<Account> secondRead = updateTask.thenApply(v -> {
            return accountService.readCommitted(account.getId());
        });

        // then - 트랜잭션A 안에서의 첫 번째와 두 번째 읽기 값이 다름 (Non-Repeatable Read)
        Account first = firstRead.get();
        Account second = secondRead.get(5, TimeUnit.SECONDS);
        
        assertThat(first.getBalance()).isEqualTo(BigDecimal.valueOf(2000L));    // 원래 값
        assertThat(second.getBalance()).isEqualTo(BigDecimal.valueOf(3000L));   // 수정된 값

        System.out.println("Non Repeatable Read 발생!");
    }

    @Test
    @DisplayName("[3-4] READ_COMMITTED: Phantom Read 발생 테스트")
    void testReadCommittedPhantomRead() throws ExecutionException, InterruptedException, TimeoutException {
        Account account1 = new Account("PHANTOM_TEST_1", BigDecimal.valueOf(1500L));
        Account account2 = new Account("PHANTOM_TEST_2", BigDecimal.valueOf(2500L));
        accountRepository.saveAndFlush(account1);
        accountRepository.saveAndFlush(account2);

        boolean phantomReadExists = false;
        
        for (int i=0; i<10; i++) {
            System.out.println("=== 시도 " + (i+1) + " ===");

            // READ_COMMITTED로 범위 조회
            CompletableFuture<List<List<Account>>> firstQuery = CompletableFuture.supplyAsync(() -> {
                return accountService.getAccountsByBalanceRangeReadCommitted(
                        BigDecimal.valueOf(1000L),
                        BigDecimal.valueOf(3000L));
            });

            // 첫 번째 조회 후 새로운 데이터 삽입 (트랜잭션B)
            CompletableFuture<Void> insertTask = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // 첫번째 조회 후 삽입
                    testTxService.insertAccountAndCommit("PHANTOM_NEW", BigDecimal.valueOf(2000L));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // 모든 작업이 종료될 때까지 대기
            CompletableFuture.allOf(firstQuery, insertTask).get(5, TimeUnit.SECONDS);

            // Then: 첫 번째와 두 번째 조회 결과가 다를 수 있음 (Phantom Read)
            List<List<Account>> firstQueryResult = firstQuery.get();
            List<Account> firstList = firstQueryResult.get(0);
            List<Account> secondList = firstQueryResult.get(1);

            System.out.println("첫 번째 조회 결과 수: " + firstList.size());
            System.out.println("두 번째 조회 결과 수: " + secondList.size());

            if (firstList.size() != secondList.size()) {
                System.out.println("Phantom Read 발생!");
                phantomReadExists = true;
                break;
            }
        }
        
        // Phantom Read 발생 확인
        assertThat(phantomReadExists).isTrue();
    }

    @Test
    @DisplayName("[4-1] REPEATABLE_READ: Non-Repeatable Read 방지 테스트")
    void testRepeatableReadPreventsNonRepeatableRead() throws ExecutionException, InterruptedException, TimeoutException {
        Account account = new Account("NON_REPEATABLE_TEST", BigDecimal.valueOf(2000L));
        accountRepository.saveAndFlush(account);

        // 읽기 (트랜잭션A)
        CompletableFuture<List<Account>> readTask = CompletableFuture.supplyAsync(() -> {
            return accountService.readTwiceInSameTransaction(account.getId());
        });

        // 새 트랜잭션B에서 수정
        CompletableFuture<Void> updateTask = CompletableFuture.runAsync(() -> {
            testTxService.updateBalanceAndCommit(account.getId(), BigDecimal.valueOf(5000L));
        });

        // 모든 작업 완료 대기
        CompletableFuture.allOf(readTask, updateTask).get(5, TimeUnit.SECONDS);
        
        // 첫번째와 두번째 읽기 결과가 동일한지 확인
        List<Account> accounts = readTask.get();

        assertThat(accounts.get(0).getBalance()).isEqualTo(BigDecimal.valueOf(2000L));      // 원래 값
        assertThat(accounts.get(1).getBalance()).isEqualTo(BigDecimal.valueOf(2000L));      // 같은 값 유지!

        System.out.println("REPEATABLE_READ: Non-Repeatable Read 방지 성공!");
    }

    // 이론: REPEATABLE_READ에서도 Phantom Read 발생
    // 실제: 많은 DB(MySQL InnoDB, H2 등)가 REPEATABLE_READ에서도 Phantom Read를 방지
    @Test
    @DisplayName("[4-2] REPEATABLE_READ: Phantom Read 방지 테스트")
    void testRepeatableReadPreventsPhantomRead() throws ExecutionException, InterruptedException, TimeoutException {
        Account account1 = new Account("PHANTOM_TEST_1", BigDecimal.valueOf(1500L));
        Account account2 = new Account("PHANTOM_TEST_2", BigDecimal.valueOf(2500L));
        accountRepository.saveAndFlush(account1);
        accountRepository.saveAndFlush(account2);

        boolean phantomReadExists = false;
        
        for (int i=0; i<5; i++) {
            System.out.println("=== 시도 " + (i+1) + " ===");

            // REPEATABLE_READ로 범위 조회
            CompletableFuture<List<List<Account>>> firstQuery = CompletableFuture.supplyAsync(() -> {
                return accountService.getAccountsByBalanceRangeRepeatableRead(
                        BigDecimal.valueOf(1000L),
                        BigDecimal.valueOf(3000L));
            });

            // 첫 번째 조회 후 새로운 데이터 삽입 (트랜잭션B)
            CompletableFuture<Void> insertTask = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // 첫번째 조회 후 삽입
                    testTxService.insertAccountAndCommit("PHANTOM_NEW", BigDecimal.valueOf(2000L));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // 모든 작업이 종료될 때까지 대기
            CompletableFuture.allOf(firstQuery, insertTask).get(5, TimeUnit.SECONDS);

            // Then: 첫 번째와 두 번째 조회 결과가 다를 수 있음 (Phantom Read)
            List<List<Account>> firstQueryResult = firstQuery.get();
            List<Account> firstList = firstQueryResult.get(0);
            List<Account> secondList = firstQueryResult.get(1);

            System.out.println("첫 번째 조회 결과 수: " + firstList.size());
            System.out.println("두 번째 조회 결과 수: " + secondList.size());

            if (firstList.size() != secondList.size()) {
                System.out.println("Phantom Read 발생!");
                phantomReadExists = true;
                break;
            }
        }

        // 실제 많은 DB(MySQL InnoDB, H2 등)가 REPEATABLE_READ에서도 Phantom Read를 방지해 보다 안전하게 구현되어있음
        System.out.println("Phantom Read 미발생");
        assertThat(phantomReadExists).isFalse();
    }

    @Test
    @DisplayName("[5-1] SERIALIZABLE: 기본 테스트")
    void testSerializable() {
        Account account = new Account("SERIALIZABLE_TEST", BigDecimal.valueOf(3000L));
        accountRepository.saveAndFlush(account);

        Account result = accountService.readSerializable(account.getId());

        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualTo(BigDecimal.valueOf(3000L));
        assertThat(result.getName()).isEqualTo("SERIALIZABLE_TEST");
    }

    @Test
    @DisplayName("[5-2] SERIALIZABLE: Phantom Read 방지 테스트")
    void testSerializablePreventsPhantomRead() throws ExecutionException, InterruptedException, TimeoutException {
        Account account1 = new Account("PHANTOM_TEST_1", BigDecimal.valueOf(1500L));
        Account account2 = new Account("PHANTOM_TEST_2", BigDecimal.valueOf(2500L));
        accountRepository.saveAndFlush(account1);
        accountRepository.saveAndFlush(account2);

        boolean phantomReadExists = false;

        for (int i=0; i<5; i++) {
            System.out.println("=== 시도 " + (i+1) + " ===");

            // SERIALIZABLE 범위 조회
            CompletableFuture<List<List<Account>>> firstQuery = CompletableFuture.supplyAsync(() -> {
                return accountService.getAccountsByBalanceRangeSerializable(
                        BigDecimal.valueOf(1000L),
                        BigDecimal.valueOf(3000L));
            });

            // 첫 번째 조회 후 새로운 데이터 삽입 (트랜잭션B)
            CompletableFuture<Void> insertTask = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000); // 첫번째 조회 후 삽입
                    testTxService.insertAccountAndCommit("PHANTOM_NEW", BigDecimal.valueOf(2000L));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // 모든 작업이 종료될 때까지 대기
            CompletableFuture.allOf(firstQuery, insertTask).get(5, TimeUnit.SECONDS);

            // Then: 첫 번째와 두 번째 조회 결과가 다를 수 있음 (Phantom Read)
            List<List<Account>> firstQueryResult = firstQuery.get();
            List<Account> firstList = firstQueryResult.get(0);
            List<Account> secondList = firstQueryResult.get(1);

            System.out.println("첫 번째 조회 결과 수: " + firstList.size());
            System.out.println("두 번째 조회 결과 수: " + secondList.size());

            if (firstList.size() != secondList.size()) {
                System.out.println("Phantom Read 발생!");
                phantomReadExists = true;
                break;
            }
        }

        // 실제 많은 DB(MySQL InnoDB, H2 등)가 REPEATABLE_READ에서도 Phantom Read를 방지해 보다 안전하게 구현되어있음
        System.out.println("Phantom Read 미발생");
        assertThat(phantomReadExists).isFalse();
    }



}