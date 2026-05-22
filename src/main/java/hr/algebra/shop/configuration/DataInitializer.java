package hr.algebra.shop.configuration;

import hr.algebra.shop.model.Category;
import hr.algebra.shop.model.Product;
import hr.algebra.shop.model.Role;
import hr.algebra.shop.model.User;
import hr.algebra.shop.repository.CategoryRepository;
import hr.algebra.shop.repository.ProductRepository;
import hr.algebra.shop.repository.RoleRepository;
import hr.algebra.shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-password}")
    private String defaultPassword;

    @Override
    public void run(ApplicationArguments args) {
        seedRolesAndUsers();
        seedCategoriesAndProducts();
    }

    private void seedRolesAndUsers() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_CUSTOMER").build()));

        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@shop.com")
                    .password(passwordEncoder.encode(defaultPassword))
                    .roles(Set.of(adminRole))
                    .build());
        }

        if (userRepository.findByUsername("customer").isEmpty()) {
            userRepository.save(User.builder()
                    .username("customer")
                    .email("customer@shop.com")
                    .password(passwordEncoder.encode(defaultPassword))
                    .roles(Set.of(customerRole))
                    .build());
        }
    }

    private void seedCategoriesAndProducts() {
        if (categoryRepository.count() > 0) {
            return;
        }

        Category electronics = categoryRepository.save(Category.builder()
                .name("Electronics").description("Gadgets and devices").build());
        Category books = categoryRepository.save(Category.builder()
                .name("Books").description("Fiction and non-fiction").build());
        Category clothing = categoryRepository.save(Category.builder()
                .name("Clothing").description("Apparel and accessories").build());

        productRepository.save(Product.builder()
                .name("Smartphone").description("Latest model with 5G")
                .price(new BigDecimal("699.99")).stockQuantity(50).category(electronics).build());
        productRepository.save(Product.builder()
                .name("Laptop").description("High performance for gaming")
                .price(new BigDecimal("1200.00")).stockQuantity(20).category(electronics).build());
        productRepository.save(Product.builder()
                .name("Java Programming").description("Learn Spring Boot 3")
                .price(new BigDecimal("45.50")).stockQuantity(100).category(books).build());
        productRepository.save(Product.builder()
                .name("T-Shirt").description("100% Cotton")
                .price(new BigDecimal("19.99")).stockQuantity(200).category(clothing).build());
    }
}
