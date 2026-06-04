package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.model.Order;
import hr.algebra.shop.model.OrderItem;
import hr.algebra.shop.model.Product;
import hr.algebra.shop.model.User;
import hr.algebra.shop.repository.UserRepository;
import hr.algebra.shop.service.CartService;
import hr.algebra.shop.service.OrderService;
import hr.algebra.shop.service.PayPalService;
import hr.algebra.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutMvcController {

    private static final String ORDER_ATTR = "order";
    private static final String ORDER_CONFIRMATION_VIEW = "order-confirmation";

    private final CartService cartService;
    private final ProductService productService;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final PayPalService payPalService;

    @GetMapping
    public String showCheckout(Model model) {
        Map<Product, Integer> cartProducts = buildCartProducts();
        model.addAttribute("cartProducts", cartProducts);
        model.addAttribute("total", calculateTotal(cartProducts));
        return "checkout";
    }

    @PostMapping
    public String placeOrder(@RequestParam String paymentMethod, Authentication auth, Model model) {
        if ("PAYPAL".equals(paymentMethod)) {
            BigDecimal total = calculateTotal(buildCartProducts());
            String approvalUrl = payPalService.createOrder(total);
            return "redirect:" + approvalUrl;
        }
        Order saved = buildAndSaveOrder(auth.getName(), "CASH");
        model.addAttribute(ORDER_ATTR, saved);
        return ORDER_CONFIRMATION_VIEW;
    }

    /** PayPal redirects here after the user approves payment. */
    @GetMapping("/paypal/return")
    public String paypalReturn(@RequestParam String token, Authentication auth, Model model) {
        payPalService.captureOrder(token);
        Order saved = buildAndSaveOrder(auth.getName(), "PAYPAL");
        model.addAttribute(ORDER_ATTR, saved);
        return ORDER_CONFIRMATION_VIEW;
    }

    /** PayPal redirects here if the user cancels. */
    @GetMapping("/paypal/cancel")
    public String paypalCancel() {
        return "redirect:/checkout";
    }

    @GetMapping("/confirmation/{id}")
    public String showConfirmation(@PathVariable Long id, Authentication auth, Model model) {
        Order order = orderService.findOrderById(id)
                .filter(o -> o.getUser().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        model.addAttribute(ORDER_ATTR, order);
        return ORDER_CONFIRMATION_VIEW;
    }

    private Map<Product, Integer> buildCartProducts() {
        Map<Product, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : cartService.getItems().entrySet()) {
            result.put(productService.getProductById(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private BigDecimal calculateTotal(Map<Product, Integer> cartProducts) {
        return cartProducts.entrySet().stream()
                .map(e -> e.getKey().getPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Order buildAndSaveOrder(String username, String paymentMethod) {
        User user = userRepository.findByUsername(username).orElseThrow();
        List<OrderItem> items = new ArrayList<>();

        Order order = Order.builder()
                .user(user)
                .createdAt(LocalDateTime.now())
                .paymentMethod(paymentMethod)
                .totalAmount(BigDecimal.ZERO)
                .items(items)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cartService.getItems().entrySet()) {
            Product p = productService.getProductById(entry.getKey());
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
            items.add(OrderItem.builder()
                    .order(order)
                    .product(p)
                    .quantity(entry.getValue())
                    .priceAtPurchase(p.getPrice())
                    .build());
        }

        order.setTotalAmount(total);
        Order saved = orderService.createOrder(order);
        cartService.clear();
        return saved;
    }
}
