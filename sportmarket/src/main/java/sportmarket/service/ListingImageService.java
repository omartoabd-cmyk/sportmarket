package sportmarket.service;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sportmarket.dto.ListingImageResponse;
import sportmarket.entity.Listing;
import sportmarket.entity.ListingImage;
import sportmarket.exception.*;
import sportmarket.repository.ListingImageRepository;
import sportmarket.repository.ListingRepository;

@Service
@Transactional(readOnly = true)
public class ListingImageService {
    private final ListingRepository listings;
    private final ListingImageRepository images;
    private final ImageStorageService storage;
    private final ImageValidator validator;
    private final ImageFileLifecycle lifecycle;
    public ListingImageService(ListingRepository listings, ListingImageRepository images, ImageStorageService storage,
                               ImageValidator validator, ImageFileLifecycle lifecycle) {
        this.listings = listings; this.images = images; this.storage = storage; this.validator = validator; this.lifecycle = lifecycle;
    }
    @Transactional
    public ListingImageResponse upload(Long listingId, String subject, MultipartFile file) {
        Listing listing = owned(listingId, subject);
        var existing = images.findByListingIdOrderByDisplayOrderAsc(listingId);
        if (existing.size() >= 5) throw new InvalidImageException("A listing can have at most 5 images.");
        var decoded = validator.validate(file);
        int order = 0;
        var slots = existing.stream().map(ListingImage::getDisplayOrder).toList();
        while (slots.contains(order)) order++;
        String key = storage.store(decoded.content(), decoded.extension());
        lifecycle.deleteOnRollback(key);
        var image = images.saveAndFlush(new ListingImage(listing, key, decoded.contentType(), order));
        return ListingImageResponse.from(image);
    }
    public List<ListingImageResponse> getImages(Long listingId) {
        if (!listings.existsById(listingId)) throw new ListingNotFoundException(listingId);
        return images.findByListingIdOrderByDisplayOrderAsc(listingId).stream().map(ListingImageResponse::from).toList();
    }
    public record Content(byte[] bytes, String contentType) {}
    public Content getContent(Long imageId) {
        var image = images.findById(imageId).orElseThrow(ImageNotFoundException::new);
        return new Content(storage.read(image.getStorageKey()), image.getContentType());
    }
    @Transactional
    public void delete(Long listingId, Long imageId, String subject) {
        owned(listingId, subject);
        var image = images.findByIdAndListingId(imageId, listingId).orElseThrow(ImageNotFoundException::new);
        images.delete(image);
        lifecycle.deleteAfterCommit(List.of(image.getStorageKey()));
    }
    private Listing owned(Long id, String subject) {
        Listing listing = listings.findLockedById(id).orElseThrow(() -> new ListingNotFoundException(id));
        if (!listing.getSeller().getId().toString().equals(subject)) throw new AccessDeniedException("You do not own this listing.");
        return listing;
    }
}
