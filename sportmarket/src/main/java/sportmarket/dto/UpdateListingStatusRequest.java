package sportmarket.dto;

import jakarta.validation.constraints.NotNull;
import sportmarket.entity.ListingStatus;

public record UpdateListingStatusRequest(@NotNull ListingStatus status) {}
