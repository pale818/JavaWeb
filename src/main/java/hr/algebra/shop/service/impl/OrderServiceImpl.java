package hr.algebra.shop.service.impl;

import hr.algebra.shop.model.Order;
import hr.algebra.shop.repository.OrderRepository;
import hr.algebra.shop.service.OrderService;
import hr.algebra.shop.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    @Override
    public Optional<Order> findOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Override
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }



    @Override
    public List<Order> searchOrders(String username, String from, String to) {
        Specification<Order> spec = Specification
                .where(OrderSpecification.hasUsername(username))
                .and(OrderSpecification.createdAfter(from))
                .and(OrderSpecification.createdBefore(to));
        return orderRepository.findAll(spec);
    }
}