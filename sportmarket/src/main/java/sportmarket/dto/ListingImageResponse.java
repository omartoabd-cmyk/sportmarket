package sportmarket.dto;

import java.time.Instant;
import sportmarket.entity.ListingImage;

public record ListingImageResponse(Long id, Long listingId, String contentType,
        int displayOrder, Instant createdAt, String url) {
    public static ListingImageResponse from(ListingImage image) {
        return new ListingImageResponse(image.getId(), image.getListing().getId(), image.getContentType(),
                image.getDisplayOrder(), image.getCreatedAt(), "/api/listing-images/" + image.getId() + "/content");
    }
}
