package com.sivan.ecommerce.repository.outbox;

import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.outbox.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryOutboxRepository extends JpaRepository<InventoryOutbox, UUID> {

    /*
     *   1. Grab a Pessimistic Write Lock (Equivalent to FOR UPDATE)
     *      @Lock(LockModeType.PESSIMISTIC_WRITE)
     *
     *   2. Set the timeout to -2 (Hibernate's internal code for SKIP LOCKED)
     *      @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
     *
     *   I don't want to use these now, cuz I'm not scaling to multiple servers
     * */
    @Query(value = """
            SELECT io
            FROM InventoryOutbox io
            WHERE io.status = :status
            """)
    List<InventoryOutbox> findTopByStatus(@Param("status") Status status, Pageable pageable);
}
