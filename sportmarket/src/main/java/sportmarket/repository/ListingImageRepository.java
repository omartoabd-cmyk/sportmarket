package sportmarket.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sportmarket.entity.ListingImage;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
    List<ListingImage> findByListingIdOrderByDisplayOrderAsc(Long listingId);
    List<ListingImage> findByListingIdInOrderByDisplayOrderAsc(Collection<Long> listingIds);
    Optional<ListingImage> findByIdAndListingId(Long id, Long listingId);
}
