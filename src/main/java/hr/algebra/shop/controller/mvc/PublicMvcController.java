package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.service.CategoryService;
import hr.algebra.shop.service.ProductService;
import hr.algebra.shop.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PublicMvcController {

    private static final String CART_COUNT = "cartCount";
    private static final String CATEGORIES = "categories";

    private final CategoryService categoryService;
    private final ProductService productService;
    private final CartService cartService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute(CATEGORIES, categoryService.getAllCategories());
        model.addAttribute("featuredProducts", productService.getAllProducts());
        model.addAttribute(CART_COUNT, cartService.getTotalItemsCount());
        return "home";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute(CATEGORIES, categoryService.getAllCategories());
        model.addAttribute(CART_COUNT, cartService.getTotalItemsCount());
        return CATEGORIES;
    }

    @GetMapping("/categories/{id}/products")
    public String productsByCategory(@PathVariable Long id, Model model) {
        model.addAttribute("category", categoryService.getCategoryById(id));
        model.addAttribute("products", productService.getProductsByCategory(id));
        model.addAttribute(CART_COUNT, cartService.getTotalItemsCount());
        return "products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProductById(id));
        model.addAttribute(CART_COUNT, cartService.getTotalItemsCount());
        return "product-detail";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
