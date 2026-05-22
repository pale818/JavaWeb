package hr.algebra.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryForm {

    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
