package hr.algebra.shop.service;
import hr.algebra.shop.model.Category;
import java.util.List;
public interface CategoryService {
    List<Category> getAllCategories();
    Category getCategoryById(Long id);
    Category save(Category category);
    void deleteById(Long id);
}
