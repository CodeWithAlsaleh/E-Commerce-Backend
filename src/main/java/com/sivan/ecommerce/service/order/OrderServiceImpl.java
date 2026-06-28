package com.sivan.ecommerce.service.order;

import com.sivan.ecommerce.dto.order.OrderFilterDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.mapper.order.OrderMapper;
import com.sivan.ecommerce.repository.cart.CartItemRepository;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.order.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CartItemRepository cartItemRepository;

    private static final Set<String> ALLOWED_SORTS = Set.of("totalPrice", "createdAt");

    public OrderServiceImpl(OrderRepository orderRepository,
                            CustomerRepository customerRepository,
                            CartItemRepository cartItemRepository) {

        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    @Transactional
    public OrderResponseDTO placeOrder(String idempotencyKey, OrderRequestDTO orderRequestDTO) {
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent())
            return OrderMapper.mapOrderToOrderResponse(existingOrder.get());

        Customer customer = getCurrentCustomer();
        List<CartItem> cartItems = getCartItems(customer);

        validateCartIsNotEmpty(cartItems);
        validateCartInventory(cartItems); // TODO: fix the error response structure

        Order order = buildOrder(customer, cartItems, idempotencyKey, orderRequestDTO);

        try {
            /*
             *   Flush here will force hibernate to run interceptors and synchronize timestamps.
             *
             *   Also:
             *       Force Hibernate to execute queued SQL now instead of waiting until transaction commit.
             *       This ensures database exceptions are thrown here, where they can be caught and mapped
             *       to meaningful business exceptions.
             * */
            Order savedOrder = orderRepository.saveAndFlush(order);

            // TODO: This will hit the DB with len(cartItems) delete queries "Fix it later"
            cartItemRepository.deleteAll(cartItems);

            return OrderMapper.mapOrderToOrderResponse(savedOrder);
        } catch (DataIntegrityViolationException exception) {
            // The exact-millisecond race condition happened.
            // Throw a conflict so the frontend can automatically retry (which will hit Step 1 safely).
            throw new ResourceConflictException("Order is currently processing. Please refresh");
        } catch (ObjectOptimisticLockingFailureException exception) {
            // Use Spring's ObjectOptimisticLockingFailureException, not Hibernate's OptimisticEntityLockException
            throw new ResourceConflictException("Inventory was updated by another user. Please try again");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponseDTO> getOrders(OrderFilterDTO orderFilterDTO, Pageable pageable) {
        Customer customer = getCurrentCustomer();

        for (Sort.Order order : pageable.getSort()) {
            // Throwing InvalidDataException tells Spring "The client sent bad data"
            if (!ALLOWED_SORTS.contains(order.getProperty()))
                throw new InvalidDataException("Sorting by '" + order.getProperty() + "' is not allowed");
        }

        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by("createdAt").descending()
            );
        }

        return orderRepository.findByFilters(customer.getId(), orderFilterDTO.status(), pageable);
    }

    private Customer getCurrentCustomer() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return customerRepository.findByEmailWithCart(email.toLowerCase())
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found"));
    }

    private List<CartItem> getCartItems(Customer customer) {
        return cartItemRepository.findAllByCartIdWithProduct(customer.getCart().getId());
    }

    private void validateCartIsNotEmpty(List<CartItem> cartItems) {
        if (cartItems.isEmpty())
            throw new InvalidDataException("Cannot place an order with an empty cart");
    }

    private void validateCartInventory(List<CartItem> cartItems) {
        List<String> outOfStockErrors = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            if (!product.isActive())
                outOfStockErrors.add(product.getTitle() + " is no longer available.");
            else if (cartItem.getQuantity() > product.getQuantity())
                outOfStockErrors.add(product.getTitle() + " has insufficient stock. Only " + product.getQuantity() + " left");
        }

        // If we collected ANY errors, throw them all at once!
        if (!outOfStockErrors.isEmpty()) {
            // String.join will combine the list into a single sentence separated by ","
            throw new InsufficientStockException(String.join(",", outOfStockErrors));
        }
    }

    private Order buildOrder(Customer customer,
                             List<CartItem> cartItems,
                             String idempotencyKey,
                             OrderRequestDTO orderRequestDTO) {

        long totalPrice = 0;
        Order order = new Order(customer, Status.PENDING, 0, orderRequestDTO.shippingAddress());

        for (CartItem cartItem : cartItems) {
            totalPrice += cartItem.calculateItemTotal();

            cartItem.getProduct().setQuantity(cartItem.getProduct().getQuantity() - cartItem.getQuantity());

            order.addOrderItem(new OrderItem(order, cartItem.getProduct(), cartItem.getQuantity(), cartItem.getProduct().getPrice()));
        }

        order.setTotalPrice(totalPrice);
        order.setIdempotencyKey(idempotencyKey);

        return order;
    }
}
