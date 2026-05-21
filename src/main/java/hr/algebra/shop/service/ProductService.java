package hr.algebra.shop.service;
import hr.algebra.shop.model.Product;
import java.util.List;
public interface ProductService {
    List<Product> getAllProducts();
    Product getProductById(Long id);
    List<Product> getProductsByCategory(Long categoryId);
    List<Product> searchByName(String name);
    List<Product> searchByNameInCategory(String name, Long categoryId);
    Product save(Product product);
    void deleteById(Long id);
}
