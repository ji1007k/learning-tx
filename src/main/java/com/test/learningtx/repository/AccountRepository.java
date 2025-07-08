package com.test.learningtx.repository;

import com.test.learningtx.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // PESSIMISTIC LOCK(비관적 락): 충돌이 일어날거라 가정하고 락을 거는 방식
    //  - 데이터 조회 시 즉시 락
    //  - 다른 트랜잭션은 대기 필요
    //  - 트랜잭션 완료 후 락 해제
    // PESSIMISTIC_WRITE(쓰기 락): 이 데이터를 수정할 예정이니 다른 사람은 건드리지 마라
    // 비관적 락: 화장실 들어가면서 문 잠그기
    // 낙관적 락: 화장실에 사람 없겠지~ 하고 들어갔다가 사람 있으면 나중에 다시 오기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a From Account a WHERE a.id = :id
    """)
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    Optional<Account> findByName(String name);

    List<Account> findByBalanceBetween(Long minBalance, Long maxBalance);
}
