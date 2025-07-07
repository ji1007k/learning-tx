package com.test.learningtx.repository;

import com.test.learningtx.entity.TxLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TxLogRepository extends JpaRepository<TxLog, Long> {
}
