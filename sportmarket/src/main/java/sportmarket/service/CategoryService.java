package sportmarket.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.dto.CategoryResponse;
import sportmarket.repository.CategoryRepository;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName())).toList();
    }
}
