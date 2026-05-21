package hr.algebra.shop.controller.rest;

import hr.algebra.shop.dto.ProductDTO;
import hr.algebra.shop.model.Product;
import hr.algebra.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;

    @GetMapping
    public List<ProductDTO> getAll() {
        return productService.getAllProducts().stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable Long id) {
        return toDTO(productService.getProductById(id));
    }

    @GetMapping("/search")
    public List<ProductDTO> search(@RequestParam String name,
                                   @RequestParam(required = false) Long categoryId) {
        List<Product> results = categoryId != null
                ? productService.searchByNameInCategory(name, categoryId)
                : productService.searchByName(name);
        return results.stream().map(this::toDTO).toList();
    }

    private ProductDTO toDTO(Product p) {
        return new ProductDTO(p.getId(), p.getName(), p.getDescription(),
                p.getPrice(), p.getStockQuantity(), p.getCategory().getName());
    }
}