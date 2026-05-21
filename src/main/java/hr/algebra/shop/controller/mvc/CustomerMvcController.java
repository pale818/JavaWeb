package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.repository.UserRepository;
import hr.algebra.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class CustomerMvcController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @GetMapping("/orders/my")
    public String myOrders(Authentication auth, Model model) {
        Long userId = userRepository.findByUsername(auth.getName()).orElseThrow().getId();
        model.addAttribute("orders", orderService.getOrdersByUser(userId));
        return "my-orders";
    }
}