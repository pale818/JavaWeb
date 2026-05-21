package hr.algebra.shop.service;

import hr.algebra.shop.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderService {
    Order createOrder(Order order);
    Optional<Order> findOrderById(Long id);
    List<Order> getOrdersByUser(Long userId);
    List<Order> getAllOrders();
    List<Order> searchOrders(String username, String from, String to);
}