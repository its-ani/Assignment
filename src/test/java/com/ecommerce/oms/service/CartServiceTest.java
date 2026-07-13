package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.AddCartItemRequest;
import com.ecommerce.oms.dto.CartResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.*;
import com.ecommerce.oms.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(UUID userId, String email, UserRole role) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);

        User userEntity = User.builder()
                .id(userId)
                .email(email)
                .role(role)
                .passwordHash("hash")
                .name("Test User")
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(userEntity);

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testGetCartImplicitCreation() {
        UUID customerId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        User customer = User.builder().id(customerId).email("customer@test.com").role(UserRole.CUSTOMER).build();
        Cart newCart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).items(new ArrayList<>()).build();

        when(cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

        CartResponse response = cartService.getCart(customerId);

        assertNotNull(response);
        assertEquals(CartStatus.ACTIVE, response.getStatus());
        assertEquals(0, response.getItemCount());
        assertEquals(0, response.getSubtotal().compareTo(BigDecimal.ZERO));

        verify(cartRepository).findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE);
        verify(userRepository).findById(customerId);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void testAddItemNewProduct() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        Product product = Product.builder().id(productId).name("iPhone").price(new BigDecimal("999.00")).active(true).build();
        User customer = User.builder().id(customerId).email("customer@test.com").role(UserRole.CUSTOMER).build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).items(new ArrayList<>()).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Stub stock levels
        InventoryItem stock = InventoryItem.builder().quantityOnHand(10).quantityReserved(2).build();
        when(inventoryItemRepository.findByProductId(productId)).thenReturn(Collections.singletonList(stock));

        AddCartItemRequest request = AddCartItemRequest.builder().productId(productId).quantity(3).build();
        CartResponse response = cartService.addItem(customerId, request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(3, response.getItemCount());
        assertEquals(new BigDecimal("2997.00"), response.getSubtotal());
        assertNull(response.getItems().get(0).getAvailabilityWarning());
    }

    @Test
    void testAddItemRepeatIncrementsQuantity() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        Product product = Product.builder().id(productId).name("iPhone").price(new BigDecimal("999.00")).active(true).build();
        User customer = User.builder().id(customerId).email("customer@test.com").role(UserRole.CUSTOMER).build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).items(new ArrayList<>()).build();
        CartItem existingItem = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(existingItem);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Stub stock levels
        InventoryItem stock = InventoryItem.builder().quantityOnHand(10).quantityReserved(2).build();
        when(inventoryItemRepository.findByProductId(productId)).thenReturn(Collections.singletonList(stock));

        AddCartItemRequest request = AddCartItemRequest.builder().productId(productId).quantity(3).build();
        CartResponse response = cartService.addItem(customerId, request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItemCount()); // 2 + 3
        assertEquals(new BigDecimal("4995.00"), response.getSubtotal());
    }

    @Test
    void testAddItemInactiveProductThrowsException() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        Product product = Product.builder().id(productId).name("iPhone").price(new BigDecimal("999.00")).active(false).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        AddCartItemRequest request = AddCartItemRequest.builder().productId(productId).quantity(1).build();

        assertThrows(ResourceNotFoundException.class, () -> cartService.addItem(customerId, request));
    }

    @Test
    void testUpdateItemQuantityToZeroRemovesItem() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        Product product = Product.builder().id(productId).name("iPhone").price(new BigDecimal("999.00")).active(true).build();
        User customer = User.builder().id(customerId).email("customer@test.com").role(UserRole.CUSTOMER).build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).items(new ArrayList<>()).build();
        CartItem existingItem = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(existingItem);

        when(cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.updateItemQuantity(customerId, productId, 0);

        assertNotNull(response);
        assertEquals(0, response.getItems().size());
        assertEquals(0, response.getItemCount());
        assertEquals(0, response.getSubtotal().compareTo(BigDecimal.ZERO));
    }

    @Test
    void testSoftStockWarningTriggersOnLowStock() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        Product product = Product.builder().id(productId).name("iPhone").price(new BigDecimal("999.00")).active(true).build();
        User customer = User.builder().id(customerId).email("customer@test.com").role(UserRole.CUSTOMER).build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).items(new ArrayList<>()).build();
        CartItem existingItem = CartItem.builder().id(UUID.randomUUID()).cart(cart).product(product).quantity(5).build();
        cart.getItems().add(existingItem);

        when(cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));

        // Available stock is only 3
        InventoryItem stock = InventoryItem.builder().quantityOnHand(5).quantityReserved(2).build();
        when(inventoryItemRepository.findByProductId(productId)).thenReturn(Collections.singletonList(stock));

        CartResponse response = cartService.getCart(customerId);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("Only 3 available in stock", response.getItems().get(0).getAvailabilityWarning());
    }

    @Test
    void testOwnershipMismatchThrowsException() {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        mockSecurityContext(customerId, "customer@test.com", UserRole.CUSTOMER);

        assertThrows(AccessDeniedException.class, () -> cartService.getCart(otherCustomerId));
    }
}
