package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.model.Category;
import hr.algebra.shop.model.Product;
import hr.algebra.shop.repository.LoginHistoryRepository;
import hr.algebra.shop.service.CategoryService;
import hr.algebra.shop.service.OrderService;
import hr.algebra.shop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMvcController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final LoginHistoryRepository loginHistoryRepository;

    // ── Categories ──────────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/categories/list";
    }

    @GetMapping("/categories/new")
    public String newCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "admin/categories/form";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        model.addAttribute("category", categoryService.getCategoryById(id));
        return "admin/categories/form";
    }

    @PostMapping("/categories/save")
    public String saveCategory(@Valid @ModelAttribute Category category, BindingResult result,
                               RedirectAttributes attrs) {
        if (result.hasErrors()) return "admin/categories/form";
        categoryService.save(category);
        attrs.addFlashAttribute("success", "Category saved successfully.");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes attrs) {
        categoryService.deleteById(id);
        attrs.addFlashAttribute("success", "Category deleted.");
        return "redirect:/admin/categories";
    }

    // ── Products ─────────────────────────────────────────────────────────────────

    @GetMapping("/products")
    public String listProducts(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "admin/products/list";
    }

    @GetMapping("/products/new")
    public String newProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/products/form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getProductById(id));
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/products/form";
    }

    @PostMapping("/products/save")
    public String saveProduct(@Valid @ModelAttribute Product product, BindingResult result,
                              Model model, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategories());
            return "admin/products/form";
        }
        productService.save(product);
        attrs.addFlashAttribute("success", "Product saved successfully.");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes attrs) {
        productService.deleteById(id);
        attrs.addFlashAttribute("success", "Product deleted.");
        return "redirect:/admin/products";
    }

    // ── Login History ─────────────────────────────────────────────────────────────

    @GetMapping("/login-history")
    public String loginHistory(Model model) {
        model.addAttribute("logins", loginHistoryRepository.findAll());
        return "admin/login-history";
    }

    // ── All Orders ────────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public String allOrders(@RequestParam(required = false) String username,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            Model model) {
        model.addAttribute("orders", orderService.searchOrders(username, from, to));
        model.addAttribute("username", username);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/orders";
    }
}