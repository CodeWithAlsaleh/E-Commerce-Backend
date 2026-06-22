package com.sivan.ecommerce.service.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.mapper.cart.CartItemMapper;
import com.sivan.ecommerce.repository.cart.CartItemRepository;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartItemServiceImpl implements CartItemService {

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;

    @Autowired
    public CartItemServiceImpl(ProductRepository productRepository, CartItemRepository cartItemRepository, CustomerRepository customerRepository) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public CartItemResponseDTO createCartItem(CartItemRequestDTO cartItemRequestDTO) {
        /*
         *  NOTE:
         *      productRepository.findById() primes Hibernate's L1 Cache (Persistence Context) at the start
         *      of this transaction. So when CartItemMapper later calls cartItem.getProduct().getTitle()
         *      the lazy proxy resolves instantly from memory instead of firing an extra SQL network query.
         * */
        Optional<Product> product = productRepository.findById(cartItemRequestDTO.productId());

        if (product.isEmpty() || !product.get().isActive())
            throw new ProductNotFoundException("Product not found");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Customer customer = customerRepository.findByEmailWithCart(email)
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found"));

        CartItem cartItem = new CartItem(0);

        Optional<CartItem> curCartItem = cartItemRepository.findCartItem(product.get().getId(), customer.getCart().getId());

        if (curCartItem.isPresent())
            cartItem = curCartItem.get(); // Managed entity
        else {
            // Only link relationships if it's a brand-new entity
            cartItem.setProduct(product.get());
            customer.getCart().addCartItem(cartItem);
        }

        cartItem.setQuantity(cartItem.getQuantity() + cartItemRequestDTO.quantity());

        if (cartItem.getQuantity() > product.get().getQuantity())
            throw new InsufficientStockException("Requested quantity is not available in stock");

        return CartItemMapper.mapCartItemToCartItemResponse(cartItemRepository.save(cartItem));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItemResponseDTO> getCartItems() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Customer customer = customerRepository.findByEmailWithCart(email)
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found"));

        return cartItemRepository.findAllByCartIdWithProduct(customer.getCart().getId())
                .stream().map(CartItemMapper::mapCartItemToCartItemResponse).toList();
    }
}
