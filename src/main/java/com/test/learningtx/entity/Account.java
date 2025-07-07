package com.test.learningtx.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long balance;

    @Version    // TODO 이게 뭐지?
    private Long version;


    public Account(String name, Long balance) {
        this.name = name;
        this.balance = balance;
    }

    // 입금
    public void deposit(Long amount) {
        this.balance += amount;
    }

    // 출금
    public void withdraw(Long amount) {
        if (this.balance < amount) {
            throw new IllegalArgumentException("잔액 부족");
        }
        this.balance -= amount;
    }
}
