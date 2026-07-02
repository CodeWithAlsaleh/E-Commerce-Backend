package com.sivan.ecommerce.scheduler;

import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.outbox.Status;
import com.sivan.ecommerce.repository.outbox.InventoryOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryRestorationScheduler {

    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryRestorationProcessor inventoryRestorationProcessor;

    public InventoryRestorationScheduler(InventoryOutboxRepository inventoryOutboxRepository,
                                         InventoryRestorationProcessor inventoryRestorationProcessor) {

        this.inventoryOutboxRepository = inventoryOutboxRepository;
        this.inventoryRestorationProcessor = inventoryRestorationProcessor;
    }

    @Scheduled(fixedDelay = 10000)
    public void work() {

        /*
         *   I'm not going to implement POISON PILL "retry count"
         *   problem rn, but it deserves to read about
         * */

        List<InventoryOutbox> tasks = inventoryOutboxRepository.findTopByStatus(Status.PENDING,
                PageRequest.of(0, 50));

        for (InventoryOutbox task : tasks) {
            try {
                inventoryRestorationProcessor.processTask(task);
            } catch (Exception e) {
                System.err.println("Failed to process outbox task " + task.getId() + ": " + e.getMessage());
            }
        }
    }
}
