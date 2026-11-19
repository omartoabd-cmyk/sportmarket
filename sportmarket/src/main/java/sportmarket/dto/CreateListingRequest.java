package sportmarket.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sportmarket.entity.ListingCondition;

public record CreateListingRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Positive Long categoryId,
        @NotNull ListingCondition condition) {}
