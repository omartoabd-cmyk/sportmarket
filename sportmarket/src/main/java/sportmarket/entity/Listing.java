package sportmarket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "listings")
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_condition", nullable = false, length = 20)
    private ListingCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Listing() {
    }

    public Listing(String title, String description, BigDecimal price, ListingCondition condition,
                   ListingStatus status, Category category, User seller) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.condition = condition;
        this.status = status;
        this.category = category;
        this.seller = seller;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public ListingCondition getCondition() { return condition; }
    public ListingStatus getStatus() { return status; }
    public Category getCategory() { return category; }
    public User getSeller() { return seller; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Updates only editable details; seller, creation time, and status stay unchanged. */
    public void updateDetails(String title, String description, BigDecimal price,
                              ListingCondition condition, Category category) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.condition = condition;
        this.category = category;
    }

    public void changeStatus(ListingStatus status) {
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
