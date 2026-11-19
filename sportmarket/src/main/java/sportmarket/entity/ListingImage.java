package sportmarket.entity;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "listing_images")
public class ListingImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;
    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;
    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected ListingImage() {}
    public ListingImage(Listing listing, String storageKey, String contentType, int displayOrder) {
        this.listing = listing; this.storageKey = storageKey;
        this.contentType = contentType; this.displayOrder = displayOrder;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Listing getListing() { return listing; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
