package com.sivan.ecommerce.scheduler;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.outbox.Status;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.repository.order.OrderItemRepository;
import com.sivan.ecommerce.repository.outbox.InventoryOutboxRepository;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryRestorationProcessor}.
 * Only the processor logic is tested — repositories are mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryRestorationProcessor")
class InventoryRestorationProcessorTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryOutboxRepository inventoryOutboxRepository;

    @InjectMocks
    private InventoryRestorationProcessor inventoryRestorationProcessor;

    // ============= shouldRestoreStockForMultipleOrderItemsAndCompleteTask =============

    @Test
    @DisplayName("Should restore stock for each order item, mark task COMPLETED, and save it")
    void shouldRestoreStockForMultipleOrderItemsAndCompleteTask() {

        // Arrange — Order
        Order order = new Order();
        UUID orderId = UUID.randomUUID();
        EntityTestUtil.setId(order, orderId);

        // Arrange — Outbox task
        InventoryOutbox task = new InventoryOutbox(order);
        EntityTestUtil.setId(task, UUID.randomUUID());

        // Arrange — Product A (quantity 3)
        Product productA = new Product("Wireless Mouse", "Ergonomic wireless mouse", 100, 2999, "USD", "http://img.com/mouse.jpg");
        UUID productAId = UUID.randomUUID();
        EntityTestUtil.setId(productA, productAId);

        // Arrange — Product B (quantity 5)
        Product productB = new Product("Mechanical Keyboard", "Cherry MX switches", 50, 8999, "USD", "http://img.com/keyboard.jpg");
        UUID productBId = UUID.randomUUID();
        EntityTestUtil.setId(productB, productBId);

        // Arrange — Order Items
        OrderItem itemA = new OrderItem(order, productA, 3, 2999);
        OrderItem itemB = new OrderItem(order, productB, 5, 8999);

        when(orderItemRepository.findByOrderId(orderId))
                .thenReturn(List.of(itemA, itemB));

        // Act
        inventoryRestorationProcessor.processTask(task);

        // Assert — restoreStock called with correct productId and quantity for each item
        verify(productRepository).restoreStock(productAId, 3);
        verify(productRepository).restoreStock(productBId, 5);
        verify(productRepository, times(2)).restoreStock(any(UUID.class), anyInt());

        // Assert — task status changed to COMPLETED
        assertEquals(Status.COMPLETED, task.getStatus());

        // Assert — updated task was saved
        verify(inventoryOutboxRepository).save(task);
    }

    // ============= shouldHandleOrderWithEmptyItemsList =============

    @Test
    @DisplayName("Should skip restoreStock when order has no items, but still mark task COMPLETED and save it")
    void shouldHandleOrderWithEmptyItemsList() {

        // Arrange — Order
        Order order = new Order();
        UUID orderId = UUID.randomUUID();
        EntityTestUtil.setId(order, orderId);

        // Arrange — Outbox task
        InventoryOutbox task = new InventoryOutbox(order);
        EntityTestUtil.setId(task, UUID.randomUUID());

        when(orderItemRepository.findByOrderId(orderId))
                .thenReturn(Collections.emptyList());

        // Act
        inventoryRestorationProcessor.processTask(task);

        // Assert — restoreStock is NEVER called (no items to restore)
        verifyNoInteractions(productRepository);

        // Assert — task is still marked as COMPLETED (prevents infinite retry)
        assertEquals(Status.COMPLETED, task.getStatus());

        // Assert — updated task was saved
        verify(inventoryOutboxRepository).save(task);
    }
}
