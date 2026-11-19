package sportmarket.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import sportmarket.dto.UserResponse;
import sportmarket.dto.ListingResponse;
import sportmarket.service.ListingService;
import java.util.List;
import sportmarket.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    private final ListingService listingService;

    public UserController(UserService service, ListingService listingService) {
        this.service = service;
        this.listingService = listingService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return service.getCurrentUser(jwt.getSubject());
    }

    @GetMapping("/me/listings")
    public List<ListingResponse> getMyListings(@AuthenticationPrincipal Jwt jwt) {
        return listingService.getMyListings(jwt.getSubject());
    }
}
