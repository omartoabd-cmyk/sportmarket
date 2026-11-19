package sportmarket.dto;

import java.math.BigDecimal;
import java.time.Instant;
import sportmarket.entity.ListingCondition;
import sportmarket.entity.ListingStatus;

public record ListingResponse(Long id,
     String title,
      String description,
       BigDecimal price,
        ListingCondition condition,
         ListingStatus status,
          CategoryResponse category,
        SellerResponse seller,
         Instant createdAt,
          Instant updatedAt, java.util.List<ListingImageResponse> images) {
    public record SellerResponse(Long id, String fullName) {}
}
