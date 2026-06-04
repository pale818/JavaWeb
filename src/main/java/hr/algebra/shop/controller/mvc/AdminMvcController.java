package hr.algebra.shop.controller.mvc;

import hr.algebra.shop.dto.CategoryForm;
import hr.algebra.shop.dto.ProductForm;
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

    private static final String CATEGORIES_ATTR = "categories";
    private static final String CATEGORIES_LIST_VIEW = "admin/categories/list";
    private static final String CATEGORIES_FORM_VIEW = "admin/categories/form";
    private static final String REDIRECT_CATEGORIES = "redirect:/admin/categories";
    private static final String PRODUCTS_LIST_VIEW = "admin/products/list";
    private static final String PRODUCTS_FORM_VIEW = "admin/products/form";
    private static final String REDIRECT_PRODUCTS = "redirect:/admin/products";
    private static final String SUCCESS_ATTR = "success";

    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final LoginHistoryRepository loginHistoryRepository;

    // ── Categories ──────────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute(CATEGORIES_ATTR, categoryService.getAllCategories());
        return CATEGORIES_LIST_VIEW;
    }

    @GetMapping("/categories/new")
    public String newCategoryForm(Model model) {
        model.addAttribute("category", new CategoryForm());
        return CATEGORIES_FORM_VIEW;
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        Category cat = categoryService.getCategoryById(id);
        CategoryForm form = new CategoryForm();
        form.setId(cat.getId());
        form.setName(cat.getName());
        form.setDescription(cat.getDescription());
        model.addAttribute("category", form);
        return CATEGORIES_FORM_VIEW;
    }

    @PostMapping("/categories/save")
    public String saveCategory(@Valid @ModelAttribute("category") CategoryForm form,
                               BindingResult result, RedirectAttributes attrs) {
        if (result.hasErrors()) return CATEGORIES_FORM_VIEW;
        Category category = Category.builder()
                .id(form.getId())
                .name(form.getName())
                .description(form.getDescription())
                .build();
        categoryService.save(category);
        attrs.addFlashAttribute(SUCCESS_ATTR, "Category saved successfully.");
        return REDIRECT_CATEGORIES;
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes attrs) {
        categoryService.deleteById(id);
        attrs.addFlashAttribute(SUCCESS_ATTR, "Category deleted.");
        return REDIRECT_CATEGORIES;
    }

    // ── Products ─────────────────────────────────────────────────────────────────

    @GetMapping("/products")
    public String listProducts(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return PRODUCTS_LIST_VIEW;
    }

    @GetMapping("/products/new")
    public String newProductForm(Model model) {
        model.addAttribute("product", new ProductForm());
        model.addAttribute(CATEGORIES_ATTR, categoryService.getAllCategories());
        return PRODUCTS_FORM_VIEW;
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model) {
        Product p = productService.getProductById(id);
        ProductForm form = new ProductForm();
        form.setId(p.getId());
        form.setName(p.getName());
        form.setDescription(p.getDescription());
        form.setPrice(p.getPrice());
        form.setStockQuantity(p.getStockQuantity());
        form.setCategoryId(p.getCategory().getId());
        model.addAttribute("product", form);
        model.addAttribute(CATEGORIES_ATTR, categoryService.getAllCategories());
        return PRODUCTS_FORM_VIEW;
    }

    @PostMapping("/products/save")
    public String saveProduct(@Valid @ModelAttribute("product") ProductForm form,
                              BindingResult result, Model model, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            model.addAttribute(CATEGORIES_ATTR, categoryService.getAllCategories());
            return PRODUCTS_FORM_VIEW;
        }
        Category category = categoryService.getCategoryById(form.getCategoryId());
        Product product = Product.builder()
                .id(form.getId())
                .name(form.getName())
                .description(form.getDescription())
                .price(form.getPrice())
                .stockQuantity(form.getStockQuantity())
                .category(category)
                .build();
        productService.save(product);
        attrs.addFlashAttribute(SUCCESS_ATTR, "Product saved successfully.");
        return REDIRECT_PRODUCTS;
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes attrs) {
        productService.deleteById(id);
        attrs.addFlashAttribute(SUCCESS_ATTR, "Product deleted.");
        return REDIRECT_PRODUCTS;
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
