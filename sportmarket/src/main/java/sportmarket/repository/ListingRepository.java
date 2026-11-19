package sportmarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sportmarket.entity.Listing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select listing from Listing listing where listing.id = :id")
    Optional<Listing> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"category", "seller"})
    Page<Listing> findAll(Specification<Listing> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "seller"})
    List<Listing> findBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId);

    @Override
    @EntityGraph(attributePaths = {"category", "seller"})
    Optional<Listing> findById(Long id);
}
