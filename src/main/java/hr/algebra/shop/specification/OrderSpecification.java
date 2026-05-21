package hr.algebra.shop.specification;

import hr.algebra.shop.model.Order;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> hasUsername(String username) {
        return (root, query, cb) -> {
            if (username == null || username.isBlank()) return null;
            return cb.like(cb.lower(root.get("user").get("username")), "%" + username.toLowerCase() + "%");
        };
    }

    public static Specification<Order> createdAfter(String from) {
        return (root, query, cb) -> {
            if (from == null || from.isBlank()) return null;
            return cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDate.parse(from).atStartOfDay());
        };
    }

    public static Specification<Order> createdBefore(String to) {
        return (root, query, cb) -> {
            if (to == null || to.isBlank()) return null;
            return cb.lessThanOrEqualTo(root.get("createdAt"), LocalDate.parse(to).atTime(23, 59, 59));
        };
    }
}