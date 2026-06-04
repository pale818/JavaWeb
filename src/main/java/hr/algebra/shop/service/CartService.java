package hr.algebra.shop.service;

import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@SessionScope
@Getter
public class CartService {


    private final Map<Long, Integer> items = new HashMap<>();

    public void addItem(Long productId, Integer quantity) {
        items.put(productId, items.getOrDefault(productId, 0) + quantity);
    }

    public void updateQuantity(Long productId, Integer quantity) {
        if (quantity <= 0) {
            removeItem(productId);
        } else {
            items.put(productId, quantity);
        }
    }

    public void removeItem(Long productId) {
        items.remove(productId);
    }

    public void clear() {
        items.clear();
    }

    public Map<Long, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public Integer getTotalItemsCount() {
        return items.values().stream().reduce(0, Integer::sum);
    }
}
