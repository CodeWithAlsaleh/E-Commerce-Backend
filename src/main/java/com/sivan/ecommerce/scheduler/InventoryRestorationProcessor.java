package com.sivan.ecommerce.scheduler;

import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.outbox.Status;
import com.sivan.ecommerce.repository.order.OrderItemRepository;
import com.sivan.ecommerce.repository.outbox.InventoryOutboxRepository;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class InventoryRestorationProcessor {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryOutboxRepository inventoryOutboxRepository;

    public InventoryRestorationProcessor(ProductRepository productRepository,
                                         OrderItemRepository orderItemRepository,
                                         InventoryOutboxRepository inventoryOutboxRepository) {

        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryOutboxRepository = inventoryOutboxRepository;
    }

    // @Transactional(propagation = Propagation.REQUIRES_NEW) "Read about this"
    @Transactional
    public void processTask(InventoryOutbox task) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(task.getOrder().getId());

        for (OrderItem orderItem : orderItems)
            productRepository.restoreStock(orderItem.getProduct().getId(), orderItem.getQuantity());

        task.setStatus(Status.COMPLETED);
        inventoryOutboxRepository.save(task);
    }
}
