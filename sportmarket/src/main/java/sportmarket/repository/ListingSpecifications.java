package sportmarket.repository;

import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import sportmarket.dto.ListingSearchRequest;
import sportmarket.entity.Listing;
import sportmarket.entity.ListingStatus;

public final class ListingSpecifications {
    private ListingSpecifications() {}

    public static Specification<Listing> matching(ListingSearchRequest filters) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("status"), ListingStatus.AVAILABLE));
            if (filters.search() != null && !filters.search().isBlank()) {
                // Treat SQL wildcard characters as literal user input, not operators.
                String term = filters.search().strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(builder.like(builder.lower(root.get("title")), "%" + term + "%", '!'));
            }
            if (filters.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), filters.categoryId()));
            }
            if (filters.condition() != null) {
                predicates.add(builder.equal(root.get("condition"), filters.condition()));
            }
            if (filters.minPrice() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), filters.minPrice()));
            }
            if (filters.maxPrice() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), filters.maxPrice()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
