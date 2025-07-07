package com.test.learningtx.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tx_logs")
@Getter @Setter
@NoArgsConstructor
public class TxLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public TxLog(String operation, String details) {
        this.operation = operation;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}
