package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.model.Product;
import hr.algebra.shop.service.CartService;
import hr.algebra.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartMvcController {

    private static final String REDIRECT_CART = "redirect:/cart";

    private final CartService cartService;
    private final ProductService productService;

    @GetMapping
    public String viewCart(Model model) {
        Map<Long, Integer> items = cartService.getItems();
        List<CartItemDTO> cartItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Product product = productService.getProductById(entry.getKey());
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));
            cartItems.add(new CartItemDTO(product, entry.getValue(), subtotal));
            total = total.add(subtotal);
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("total", total);
        model.addAttribute("cartCount", cartService.getTotalItemsCount());
        return "cart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId, @RequestParam Integer quantity) {
        cartService.addItem(productId, quantity);
        return REDIRECT_CART;
    }

    @PostMapping("/update")
    public String updateQuantity(@RequestParam Long productId, @RequestParam Integer quantity) {
        cartService.updateQuantity(productId, quantity);
        return REDIRECT_CART;
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long productId) {
        cartService.removeItem(productId);
        return REDIRECT_CART;
    }

    @PostMapping("/clear")
    public String clearCart() {
        cartService.clear();
        return REDIRECT_CART;
    }

    // Inner DTO for view display
    @lombok.AllArgsConstructor
    @lombok.Getter
    public static class CartItemDTO {
        private Product product;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
