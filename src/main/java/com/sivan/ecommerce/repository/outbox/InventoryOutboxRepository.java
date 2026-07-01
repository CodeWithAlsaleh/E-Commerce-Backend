package com.sivan.ecommerce.repository.outbox;

import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryOutboxRepository extends JpaRepository<InventoryOutbox, UUID> {
}
