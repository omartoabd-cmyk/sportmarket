package sportmarket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.entity.*;
import sportmarket.repository.*;
import sportmarket.service.TokenService;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ListingCrudTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ListingRepository listings;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired EntityManager entityManager;

    private User owner;
    private User other;
    private Category category;
    private Listing listing;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(new User("Owner", "owner@example.test", "test-only-hash"));
        other = users.saveAndFlush(new User("Other Seller", "other@example.test", "test-only-hash"));
        category = categories.findAllByOrderByNameAsc().getFirst();
        listing = listings.saveAndFlush(new Listing("Baseball glove", "Used glove", new BigDecimal("25.00"),
                ListingCondition.GOOD, ListingStatus.AVAILABLE, category, owner));
        ownerToken = tokens.issueToken(owner.getId().toString()).accessToken();
        otherToken = tokens.issueToken(other.getId().toString()).accessToken();
        entityManager.clear();
    }

    @Test
    void createsListingWithJwtSellerAvailableStatusAndLocation() throws Exception {
        Map<String, Object> body = validRequest();
        // Extra client fields cannot be bound to the seller, status, or ID.
        body.put("sellerId", other.getId());
        body.put("seller", Map.of("id", other.getId()));
        body.put("status", "SOLD");
        MvcResult result = mvc.perform(post("/api/listings").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seller.id").value(owner.getId()))
                .andExpect(jsonPath("$.seller.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.category.id").value(category.getId()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/listings/" + id);
        entityManager.clear();
        Listing saved = listings.findById(id).orElseThrow();
        assertThat(saved.getSeller().getId()).isEqualTo(owner.getId());
        assertThat(saved.getPrice()).isEqualByComparingTo("49.99");
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
        mvc.perform(get("/api/listings/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tennis racket"));
    }

    @Test
    void requiresAuthenticationForEveryNewRoute() throws Exception {
        mvc.perform(post("/api/listings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/listings/{id}", listing.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/listings/{id}", listing.getId())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/listings/{id}/status", listing.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/listings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/listings").header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidCreationAndFullUpdateFieldsWithoutChangingDatabase() throws Exception {
        List<Map<String, Object>> invalidRequests = new java.util.ArrayList<>();
        invalidRequests.add(Map.of());
        for (var entry : Map.<String, Object>ofEntries(
                Map.entry("title", " "), Map.entry("description", " "),
                Map.entry("price", BigDecimal.ZERO), Map.entry("categoryId", 0L),
                Map.entry("condition", "BROKEN")).entrySet()) {
            var body = validRequest(); body.put(entry.getKey(), entry.getValue()); invalidRequests.add(body);
        }
        for (Object price : List.of(new BigDecimal("-1"), new BigDecimal("0.001"), new BigDecimal("10000000000"))) {
            var body = validRequest(); body.put("price", price); invalidRequests.add(body);
        }
        for (String field : List.of("title", "description", "price", "categoryId", "condition")) {
            var body = validRequest(); body.put(field, null); invalidRequests.add(body);
        }
        var longTitle = validRequest(); longTitle.put("title", "x".repeat(256)); invalidRequests.add(longTitle);
        var longDescription = validRequest(); longDescription.put("description", "x".repeat(10001)); invalidRequests.add(longDescription);
        var numericCondition = validRequest(); numericCondition.put("condition", 0); invalidRequests.add(numericCondition);
        for (var body : invalidRequests) {
            String request = json.writeValueAsString(body);
            mvc.perform(post("/api/listings").header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
            mvc.perform(put("/api/listings/{id}", listing.getId()).header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        entityManager.clear();
        assertThat(listings.count()).isEqualTo(1);
        assertThat(listings.findById(listing.getId()).orElseThrow().getTitle()).isEqualTo("Baseball glove");
    }

    @Test
    void rejectsUnknownCategoryOnCreateAndUpdate() throws Exception {
        var body = validRequest(); body.put("categoryId", Long.MAX_VALUE);
        mvc.perform(post("/api/listings").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Category not found"));
        mvc.perform(put("/api/listings/{id}", listing.getId()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        entityManager.clear();
        assertThat(listings.count()).isEqualTo(1);
        assertThat(listings.findById(listing.getId()).orElseThrow().getTitle()).isEqualTo("Baseball glove");
    }

    @Test
    void ownerUpdatesDetailsAndPreservesSellerCreationTimeAndStatus() throws Exception {
        Category newCategory = categories.findAllByOrderByNameAsc().getLast();
        Listing saved = listings.findById(listing.getId()).orElseThrow();
        saved.changeStatus(ListingStatus.SOLD);
        listings.flush();
        Instant createdAt = saved.getCreatedAt();
        Instant previousUpdatedAt = saved.getUpdatedAt();
        entityManager.clear();
        var body = validRequest(); body.put("categoryId", newCategory.getId());
        body.put("sellerId", other.getId()); body.put("status", "AVAILABLE");
        MvcResult result = mvc.perform(put("/api/listings/{id}", listing.getId())
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tennis racket"))
                .andExpect(jsonPath("$.description").value("Lightly used racket"))
                .andExpect(jsonPath("$.condition").value("LIKE_NEW"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.category.id").value(newCategory.getId()))
                .andExpect(jsonPath("$.seller.id").value(owner.getId()))
                .andExpect(jsonPath("$.status").value("SOLD")).andReturn();
        entityManager.clear();
        Listing updated = listings.findById(listing.getId()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(Instant.parse(json.readTree(result.getResponse().getContentAsString())
                .get("updatedAt").asString())).isCloseTo(updated.getUpdatedAt(),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(updated.getSeller().getId()).isEqualTo(owner.getId());
        assertThat(updated.getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(updated.getPrice()).isEqualByComparingTo("49.99");
    }

    @Test
    void nonOwnerCannotUpdateDeleteOrChangeStatus() throws Exception {
        mvc.perform(put("/api/listings/{id}", listing.getId()).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(delete("/api/listings/{id}", listing.getId()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(patch("/api/listings/{id}/status", listing.getId()).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        entityManager.clear();
        Listing unchanged = listings.findById(listing.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Baseball glove");
        assertThat(unchanged.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void ownerCanDeleteListing() throws Exception {
        mvc.perform(delete("/api/listings/{id}", listing.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        listings.flush(); entityManager.clear();
        assertThat(listings.findById(listing.getId())).isEmpty();
        mvc.perform(get("/api/listings/{id}", listing.getId())).andExpect(status().isNotFound());
    }

    @Test
    void missingListingReturns404ForEveryMutation() throws Exception {
        mvc.perform(put("/api/listings/{id}", Long.MAX_VALUE).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Listing not found"));
        mvc.perform(delete("/api/listings/{id}", Long.MAX_VALUE).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(patch("/api/listings/{id}/status", Long.MAX_VALUE).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void ownerCanMarkSoldAndAvailableAndPublicReadsReflectStatus() throws Exception {
        mvc.perform(patch("/api/listings/{id}/status", listing.getId()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SOLD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD"));
        entityManager.clear();
        assertThat(listings.findById(listing.getId()).orElseThrow().getStatus()).isEqualTo(ListingStatus.SOLD);
        mvc.perform(get("/api/listings")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/listings/{id}", listing.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
        mvc.perform(patch("/api/listings/{id}/status", listing.getId()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(get("/api/listings")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void rejectsInvalidStatusValuesWithoutChangingListing() throws Exception {
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":\"PENDING\"}",
                "{\"status\":0}", "{\"status\":\"0\"}", "{\"status\":\"sold\"}")) {
            mvc.perform(patch("/api/listings/{id}/status", listing.getId()).header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        entityManager.clear();
        assertThat(listings.findById(listing.getId()).orElseThrow().getStatus()).isEqualTo(ListingStatus.AVAILABLE);
    }

    @Test
    void myListingsIncludesOnlyOwnersAvailableAndSoldListingsNewestFirst() throws Exception {
        Listing sold = listings.saveAndFlush(new Listing("Sold item", "Description", new BigDecimal("10.00"),
                ListingCondition.FAIR, ListingStatus.SOLD, category, owner));
        listings.saveAndFlush(new Listing("Other item", "Description", new BigDecimal("15.00"),
                ListingCondition.NEW, ListingStatus.AVAILABLE, category, other));
        mvc.perform(get("/api/users/me/listings").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(sold.getId()))
                .andExpect(jsonPath("$[1].id").value(listing.getId()))
                .andExpect(jsonPath("$[*].status", contains("SOLD", "AVAILABLE")))
                .andExpect(jsonPath("$[*].seller.id", everyItem(is(owner.getId().intValue()))));
        mvc.perform(get("/api/users/me/listings").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Other item"));
    }

    @Test
    void myListingsReturnsEmptyArrayForUserWithoutListings() throws Exception {
        mvc.perform(get("/api/users/me/listings").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void deletedAccountCannotCreateListing() throws Exception {
        users.deleteById(other.getId()); users.flush();
        mvc.perform(post("/api/listings").header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsSupportsAllListingWriteMethodsAndUnlistedRoutesStayDenied() throws Exception {
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            mvc.perform(options("/api/listings/1").header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", method)
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
        }
        mvc.perform(patch("/api/listings/{id}", listing.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/categories/1").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", "Tennis racket");
        request.put("description", "Lightly used racket");
        request.put("price", new BigDecimal("49.99"));
        request.put("categoryId", category.getId());
        request.put("condition", "LIKE_NEW");
        return request;
    }

    private String bearer(String token) { return "Bearer " + token; }
}
