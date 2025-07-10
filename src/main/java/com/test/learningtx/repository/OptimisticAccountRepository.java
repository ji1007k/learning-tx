package com.test.learningtx.repository;

import com.test.learningtx.entity.OptimisticAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 낙관적 락 계좌 레포지토리 - 기본 버전
 *
 * @Version이 있는 엔티티는 별도 설정 없이도
 * 자동으로 낙관적 락 적용!
 */
@Repository
public interface OptimisticAccountRepository extends JpaRepository<OptimisticAccount, Long> {
    /**
     * 기본 조회 - @Version에 의한 자동 낙관적 락 적용
     *
     * 동작:
     * 1. SELECT id, name, balance, version FROM optimistic_accounts WHERE id = ?
     * 2. version 값도 함께 엔티티에 저장됨
     */
//    Optional<OptimisticAccount> findById(Long id);    // 기본적으로 자동 생성됨

    /**
     * 기본 저장 - @Version에 의한 자동 version 관리
     *
     * 신규 저장 시:
     * - INSERT ... version = 0
     *
     * 수정 시:
     * - UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?
     */
//    OptimisticAccount save(OptimisticAccount account);    // 기본적으로 자동 생성됨

    /**
     * 이름으로 계좌 찾기
     */
    Optional<OptimisticAccount> findByName(String name);
}
