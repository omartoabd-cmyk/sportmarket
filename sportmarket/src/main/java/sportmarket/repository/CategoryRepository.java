package sportmarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sportmarket.entity.Category;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();
}
