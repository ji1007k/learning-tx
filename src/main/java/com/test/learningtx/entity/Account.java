package com.test.learningtx.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal balance;

    // 낙관적 락을 위한 Version.
    @Version
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Account(String name, BigDecimal balance) {
        this.name = name;
        this.balance = balance;
    }

    @PrePersist
    protected void onCreate() {
        createAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 입금
    public void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다.");
        }
        this.balance = this.balance.add(amount);
    }

    // 출금
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다.");
        }
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재 잔액: " + balance);
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * 입금 (Long 타입도 지원)
     */
    public void deposit(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다.");
        }
        deposit(new BigDecimal(amount));
    }

    /**
     * 출금 (Long 타입도 지원)
     */
    public void withdraw(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다.");
        }
        withdraw(new BigDecimal(amount));
    }

}
