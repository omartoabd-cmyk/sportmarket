package sportmarket.dto;

import java.util.List;

/** Stable API contract, independent of Spring Data's Page serialization. */
public record ListingPageResponse(List<ListingResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {}
