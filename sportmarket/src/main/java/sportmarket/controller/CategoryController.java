package sportmarket.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import sportmarket.dto.CategoryResponse;
import sportmarket.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryResponse> getCategories() {
        return service.getCategories();
    }
}
