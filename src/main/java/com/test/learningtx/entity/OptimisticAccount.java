package com.test.learningtx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "optimistic_accounts")
@Getter @Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class OptimisticAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal balance;

    /**
     * 🔓 낙관적 락의 핵심: @Version
     *
     * 특징:
     * 1. 엔티티가 저장될 때 0으로 시작
     * 2. 엔티티가 수정될 때마다 자동으로 +1 증가
     * 3. UPDATE 시 WHERE 조건에 자동으로 version 조건 추가
     * 4. version이 다르면 OptimisticLockException 발생
     */
    @Version
    private Long version;

    /**
     * 입금 처리
     */
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다.");
        }
        this.balance = this.balance.add(amount);
        // version은 JPA가 자동으로 처리! 우리가 건드릴 필요 없음
    }

    /**
     * 출금 처리
     */
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다.");
        }

        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액 부족! 현재 잔액: " + balance);
        }

        this.balance = this.balance.subtract(amount);
        // version은 save() 할 때 자동으로 증가
    }

    @Override
    public String toString() {
        return String.format("Account{id=%d, name='%s', balance=%s, version=%d}",
                id, name, balance, version);
    }
}
