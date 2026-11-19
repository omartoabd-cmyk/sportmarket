package sportmarket;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.entity.*;
import sportmarket.repository.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SportmarketApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ListingRepository listings;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;

    private Long availableId;
    private Long soldId;

    @BeforeEach
    void setUp() {
        User seller = users.save(new User("Alex Seller", "alex@example.test", "test-only-hash"));
        Category category = categories.findAllByOrderByNameAsc().stream()
                .filter(value -> value.getName().equals("Soccer")).findFirst().orElseThrow();
        availableId = listings.saveAndFlush(new Listing("Soccer ball", "Lightly used ball",
                new BigDecimal("24.99"), ListingCondition.GOOD, ListingStatus.AVAILABLE,
                category, seller)).getId();
        soldId = listings.saveAndFlush(new Listing("Sold boots", "Used boots",
                new BigDecimal("45.00"), ListingCondition.FAIR, ListingStatus.SOLD,
                category, seller)).getId();
    }

    @Test
    void retrievesOnlyAvailableListingsWithoutPrivateSellerFields() throws Exception {
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(availableId))
                .andExpect(jsonPath("$.content[0].price").value(24.99))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].seller.fullName").value("Alex Seller"))
                .andExpect(jsonPath("$.content[0].seller.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].seller.email").doesNotExist());
    }

    @Test
    void retrievesListingById() throws Exception {
        mvc.perform(get("/api/listings/{id}", availableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(availableId))
                .andExpect(jsonPath("$.title").value("Soccer ball"))
                .andExpect(jsonPath("$.category.name").value("Soccer"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void soldListingCanStillBeReadById() throws Exception {
        mvc.perform(get("/api/listings/{id}", soldId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
    }

    @Test
    void returnsStructured404ForMissingListing() throws Exception {
        mvc.perform(get("/api/listings/9223372036854775807"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Listing not found"))
                .andExpect(jsonPath("$.detail").value("Listing with ID 9223372036854775807 was not found."))
                .andExpect(jsonPath("$.instance").value("/api/listings/9223372036854775807"));
    }

    @Test
    void returnsStructured400ForInvalidId() throws Exception {
        mvc.perform(get("/api/listings/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void retrievesSeededCategoriesInAlphabeticalOrder() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].name", contains("Baseball", "Basketball", "Fitness",
                        "Hockey", "Other", "Soccer", "Tennis")));
    }

    @Test
    void returnsEmptyPageWhenNoAvailableListingsExist() throws Exception {
        listings.deleteAll();
        listings.flush();
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
