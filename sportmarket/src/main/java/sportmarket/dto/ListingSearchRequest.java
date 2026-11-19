package sportmarket.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sportmarket.entity.ListingCondition;

/** Optional query parameters for the public marketplace, not a JSON request body. */
public record ListingSearchRequest(
        @Size(max = 100) String search,
        @Positive Long categoryId,
        ListingCondition condition,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        @Pattern(regexp = "newest|oldest|price_asc|price_desc", message = "must be newest, oldest, price_asc, or price_desc") String sort,
        @Min(0) Integer page,
        @Min(1) @Max(50) Integer size) {
    public ListingSearchRequest {
        sort = sort == null ? "newest" : sort;
        page = page == null ? 0 : page;
        size = size == null ? 12 : size;
    }
}
