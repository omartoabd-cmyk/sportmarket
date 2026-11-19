package sportmarket.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import sportmarket.dto.ListingSearchRequest;
import sportmarket.dto.ListingPageResponse;
import sportmarket.repository.ListingSpecifications;
import sportmarket.exception.InvalidListingFilterException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import sportmarket.dto.CreateListingRequest;
import sportmarket.dto.UpdateListingRequest;
import sportmarket.dto.UpdateListingStatusRequest;
import sportmarket.entity.Category;
import sportmarket.exception.CategoryNotFoundException;
import sportmarket.repository.CategoryRepository;
import sportmarket.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.dto.CategoryResponse;
import sportmarket.dto.ListingResponse;
import sportmarket.entity.Listing;
import sportmarket.entity.ListingStatus;
import sportmarket.exception.ListingNotFoundException;
import sportmarket.repository.ListingRepository;

@Service
@Transactional(readOnly = true)
public class ListingService {
    private final ListingRepository listingRepository;
    private final sportmarket.repository.ListingImageRepository imageRepository;
    private final ImageFileLifecycle imageFiles;

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public ListingService(ListingRepository listingRepository, CategoryRepository categoryRepository,
                          UserRepository userRepository, sportmarket.repository.ListingImageRepository imageRepository,
                          ImageFileLifecycle imageFiles) {
        this.listingRepository = listingRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.imageRepository = imageRepository;
        this.imageFiles = imageFiles;
    }

    public ListingPageResponse getAvailableListings(ListingSearchRequest filters) {
        if (filters.minPrice() != null && filters.maxPrice() != null
                && filters.minPrice().compareTo(filters.maxPrice()) > 0) {
            throw new InvalidListingFilterException("Minimum price must not exceed maximum price.");
        }
        if (filters.categoryId() != null && !categoryRepository.existsById(filters.categoryId())) {
            throw new InvalidListingFilterException("The selected category does not exist.");
        }
        if ((long) filters.page() * filters.size() > Integer.MAX_VALUE) {
            throw new InvalidListingFilterException("The requested page exceeds the supported offset.");
        }
        // Map public sort names to fixed entity properties. Never pass raw input to Sort.
        Sort sort = switch (filters.sort()) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt", "id");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price", "id");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price", "id");
            default -> throw new InvalidListingFilterException("Unsupported listing sort option.");
        };
        var result = listingRepository.findAll(ListingSpecifications.matching(filters),
                PageRequest.of(filters.page(), filters.size(), sort));
        return new ListingPageResponse(toResponses(result.getContent()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    public ListingResponse getListingById(Long id) {
        return toResponse(listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException(id)));
    }

    @Transactional
    public ListingResponse createListing(CreateListingRequest request, String subject) {
        Long userId = authenticatedUserId(subject);
        var seller = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication."));
        Category category = findCategory(request.categoryId());
        Listing listing = new Listing(request.title(), request.description(), request.price(),
                request.condition(), ListingStatus.AVAILABLE, category, seller);
        return toResponse(listingRepository.saveAndFlush(listing));
    }

    @Transactional
    public ListingResponse updateListing(Long id, UpdateListingRequest request, String subject) {
        Listing listing = findOwnedListing(id, subject);
        Category category = findCategory(request.categoryId());
        listing.updateDetails(request.title(), request.description(), request.price(), request.condition(), category);
        // The entity is managed: dirty checking writes its changes. Flush runs
        // @PreUpdate before we map updatedAt into the response.
        listingRepository.flush();
        return toResponse(listing);
    }

    @Transactional
    public void deleteListing(Long id, String subject) {
        Listing listing = findOwnedListing(id, subject);
        var images = imageRepository.findByListingIdOrderByDisplayOrderAsc(id);
        var keys = images.stream().map(sportmarket.entity.ListingImage::getStorageKey).toList();
        // Remove managed children first; Hibernate must not retain references to a deleted parent.
        imageRepository.deleteAll(images);
        imageRepository.flush();
        listingRepository.delete(listing); // The FK cascade is also a database-level safety net.
        imageFiles.deleteAfterCommit(keys);
    }

    @Transactional
    public ListingResponse updateListingStatus(Long id, UpdateListingStatusRequest request, String subject) {
        Listing listing = findOwnedListing(id, subject);
        listing.changeStatus(request.status());
        listingRepository.flush();
        return toResponse(listing);
    }

    public List<ListingResponse> getMyListings(String subject) {
        return toResponses(listingRepository.findBySellerIdOrderByCreatedAtDescIdDesc(authenticatedUserId(subject)));
    }

    private Listing findOwnedListing(Long id, String subject) {
        Long userId = authenticatedUserId(subject);
        Listing listing = listingRepository.findLockedById(id)
                .orElseThrow(() -> new ListingNotFoundException(id));
        if (!listing.getSeller().getId().equals(userId)) {
            throw new AccessDeniedException("You do not own this listing.");
        }
        return listing;
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private Long authenticatedUserId(String subject) {
        try {
            long id = Long.parseLong(subject);
            if (id > 0) { return id; }
        } catch (NumberFormatException ignored) {
            // The JWT decoder normally rejects invalid subjects before the service is called.
        }
        throw new BadCredentialsException("Invalid authentication.");
    }

    private List<ListingResponse> toResponses(List<Listing> listings) {
        if (listings.isEmpty()) return List.of();
        var grouped = imageRepository.findByListingIdInOrderByDisplayOrderAsc(listings.stream().map(Listing::getId).toList())
                .stream().collect(java.util.stream.Collectors.groupingBy(image -> image.getListing().getId()));
        return listings.stream().map(listing -> toResponse(listing, grouped.getOrDefault(listing.getId(), List.of())))
                .toList();
    }

    private ListingResponse toResponse(Listing listing) {
        return toResponse(listing, imageRepository.findByListingIdOrderByDisplayOrderAsc(listing.getId()));
    }

    private ListingResponse toResponse(Listing listing, List<sportmarket.entity.ListingImage> images) {
        return new ListingResponse(listing.getId(), listing.getTitle(), listing.getDescription(),
                listing.getPrice(), listing.getCondition(), listing.getStatus(),
                new CategoryResponse(listing.getCategory().getId(), listing.getCategory().getName()),
                new ListingResponse.SellerResponse(listing.getSeller().getId(), listing.getSeller().getFullName()),
                listing.getCreatedAt(), listing.getUpdatedAt(),
                images.stream().map(sportmarket.dto.ListingImageResponse::from).toList());
    }
}
