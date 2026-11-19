package sportmarket.controller;

import sportmarket.dto.ListingPageResponse;
import sportmarket.dto.ListingSearchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import sportmarket.dto.CreateListingRequest;
import sportmarket.dto.UpdateListingRequest;
import sportmarket.dto.UpdateListingStatusRequest;
import org.springframework.web.bind.annotation.*;
import sportmarket.dto.ListingResponse;
import sportmarket.service.ListingService;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping
    public ListingPageResponse getListings(@Valid @ModelAttribute ListingSearchRequest filters) {
        return service.getAvailableListings(filters);
    }

    @GetMapping("/{id}")
    public ListingResponse getListing(@PathVariable Long id) {
        return service.getListingById(id);
    }

    @PostMapping
    public ResponseEntity<ListingResponse> createListing(@Valid @RequestBody CreateListingRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ListingResponse response = service.createListing(request, jwt.getSubject());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ListingResponse updateListing(@PathVariable Long id, @Valid @RequestBody UpdateListingRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.updateListing(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteListing(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.deleteListing(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ListingResponse updateListingStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateListingStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.updateListingStatus(id, request, jwt.getSubject());
    }
}
