package com.sivan.ecommerce.scheduler;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.outbox.Status;
import com.sivan.ecommerce.repository.outbox.InventoryOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryRestorationScheduler}.
 * Only the scheduler logic is tested — the repository and processor are mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRestorationScheduler")
class InventoryRestorationSchedulerTest {

    @Mock
    private InventoryOutboxRepository inventoryOutboxRepository;

    @Mock
    private InventoryRestorationProcessor inventoryRestorationProcessor;

    @InjectMocks
    private InventoryRestorationScheduler inventoryRestorationScheduler;

    // ========================== Helper ==========================

    private InventoryOutbox createOutboxTask() {
        Order order = new Order();
        EntityTestUtil.setId(order, UUID.randomUUID());

        InventoryOutbox task = new InventoryOutbox(order);
        EntityTestUtil.setId(task, UUID.randomUUID());
        return task;
    }

    // =================== shouldProcessAllTasksSuccessfully ===================

    @Test
    @DisplayName("Should poll PENDING tasks with PageRequest(0, 50) and invoke processor for each task")
    void shouldProcessAllTasksSuccessfully() {

        // Arrange
        InventoryOutbox task1 = createOutboxTask();
        InventoryOutbox task2 = createOutboxTask();
        InventoryOutbox task3 = createOutboxTask();

        when(inventoryOutboxRepository.findTopByStatus(Status.PENDING, PageRequest.of(0, 50)))
                .thenReturn(List.of(task1, task2, task3));

        // Act
        inventoryRestorationScheduler.work();

        // Assert — repository was called with the exact page constraint
        verify(inventoryOutboxRepository).findTopByStatus(Status.PENDING, PageRequest.of(0, 50));

        // Assert — processor was invoked exactly 3 times, once per task
        verify(inventoryRestorationProcessor).processTask(task1);
        verify(inventoryRestorationProcessor).processTask(task2);
        verify(inventoryRestorationProcessor).processTask(task3);
        verify(inventoryRestorationProcessor, times(3)).processTask(any(InventoryOutbox.class));
    }

    // ============= shouldSurviveExceptionsAndContinueLoop =============

    @Test
    @DisplayName("Should survive a RuntimeException on task #2 and still process task #3 (firewall edge case)")
    void shouldSurviveExceptionsAndContinueLoop() {

        // Arrange
        InventoryOutbox task1 = createOutboxTask();
        InventoryOutbox task2 = createOutboxTask();
        InventoryOutbox task3 = createOutboxTask();

        when(inventoryOutboxRepository.findTopByStatus(Status.PENDING, PageRequest.of(0, 50)))
                .thenReturn(List.of(task1, task2, task3));

        // Simulate a database crash on task #2
        doNothing().when(inventoryRestorationProcessor).processTask(task1);
        doThrow(new RuntimeException("Simulated DB failure"))
                .when(inventoryRestorationProcessor).processTask(task2);
        doNothing().when(inventoryRestorationProcessor).processTask(task3);

        // Act
        inventoryRestorationScheduler.work();

        // Assert — all 3 tasks were attempted despite the exception on #2
        verify(inventoryRestorationProcessor).processTask(task1);
        verify(inventoryRestorationProcessor).processTask(task2);
        verify(inventoryRestorationProcessor).processTask(task3);
        verify(inventoryRestorationProcessor, times(3)).processTask(any(InventoryOutbox.class));
    }
}
