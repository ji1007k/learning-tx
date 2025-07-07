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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class BasicTxTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

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

}