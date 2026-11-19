package sportmarket;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ListingSearchTests {
    @Autowired MockMvc mvc;
    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    private Category soccer;
    private Category tennis;
    private User seller;
    private Long boots;
    private Long ball;
    private Long racket;
    private Long jersey;
    private Long special;

    @BeforeEach
    void setUp() {
        seller = users.saveAndFlush(new User("Search Seller", "search@example.test", "test-only-hash"));
        List<Category> all = categories.findAllByOrderByNameAsc();
        soccer = all.stream().filter(item -> item.getName().equals("Soccer")).findFirst().orElseThrow();
        tennis = all.stream().filter(item -> item.getName().equals("Tennis")).findFirst().orElseThrow();
        boots = add("Nike boots", soccer, ListingCondition.GOOD, "50.00", ListingStatus.AVAILABLE, 1);
        ball = add("NIKE ball", soccer, ListingCondition.LIKE_NEW, "20.00", ListingStatus.AVAILABLE, 2);
        racket = add("Tennis racket", tennis, ListingCondition.GOOD, "100.00", ListingStatus.AVAILABLE, 3);
        jersey = add("nike jersey", soccer, ListingCondition.GOOD, "50.00", ListingStatus.AVAILABLE, 4);
        special = add("100%_Ready! kit", soccer, ListingCondition.FAIR, "10.00", ListingStatus.AVAILABLE, 5);
        add("Nike sold boots", soccer, ListingCondition.GOOD, "5.00", ListingStatus.SOLD, 6);
    }

    @Test
    void publicDefaultsReturnOnlyAvailableListingsAndStableMetadata() throws Exception {
        mvc.perform(get("/api/listings"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[*].id", contains(special.intValue(), jersey.intValue(), racket.intValue(), ball.intValue(), boots.intValue())))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"nike", "NIKE", "NiKe", "  nike  "})
    void matchesTitleCaseInsensitively(String search) throws Exception {
        mvc.perform(get("/api/listings").param("search", search))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id", contains(jersey.intValue(), ball.intValue(), boots.intValue())));
    }

    @Test
    void wildcardCharactersAreLiteralAndDescriptionIsNotSearched() throws Exception {
        mvc.perform(get("/api/listings").param("search", "%_Ready!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(special))
                .andExpect(jsonPath("$.totalElements").value(1));
        // Every description mentions this word; none of the titles does.
        mvc.perform(get("/api/listings").param("search", "description-only"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersCategoryConditionAndInclusivePriceBounds() throws Exception {
        mvc.perform(get("/api/listings").param("categoryId", tennis.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(racket));
        mvc.perform(get("/api/listings").param("condition", "GOOD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/listings").param("minPrice", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/listings").param("maxPrice", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/listings").param("minPrice", "50").param("maxPrice", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void allFiltersCombineBeforePaginationAndSorting() throws Exception {
        mvc.perform(get("/api/listings").param("search", "NIKE").param("categoryId", soccer.getId().toString())
                        .param("condition", "GOOD").param("minPrice", "25").param("maxPrice", "60")
                        .param("sort", "price_asc").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(jersey));
    }

    @Test
    void supportsFourSortOptionsWithIdTies() throws Exception {
        mvc.perform(get("/api/listings").param("sort", "oldest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(boots.intValue(), ball.intValue(), racket.intValue(), jersey.intValue(), special.intValue())));
        mvc.perform(get("/api/listings").param("sort", "price_asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(special.intValue(), ball.intValue(), boots.intValue(), jersey.intValue(), racket.intValue())));
        mvc.perform(get("/api/listings").param("sort", "price_desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(racket.intValue(), jersey.intValue(), boots.intValue(), ball.intValue(), special.intValue())));
        jdbc.update("UPDATE listings SET created_at = ?", Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC));
        mvc.perform(get("/api/listings").param("sort", "newest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(special.intValue(), jersey.intValue(), racket.intValue(), ball.intValue(), boots.intValue())));
        mvc.perform(get("/api/listings").param("sort", "oldest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", contains(boots.intValue(), ball.intValue(), racket.intValue(), jersey.intValue(), special.intValue())));
    }

    @Test
    void paginatesFirstMiddleLastAndOutOfRangePages() throws Exception {
        mvc.perform(get("/api/listings").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/api/listings").param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/api/listings").param("size", "2").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/listings").param("size", "2").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.page").value(3));
        mvc.perform(get("/api/listings").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void noMatchesReturnsEmptyPageAndBlankSearchIsOptional() throws Exception {
        mvc.perform(get("/api/listings").param("search", "no-such-equipment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get("/api/listings").param("search", "  ").param("minPrice", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,51", "size,-1", "size,abc",
            "minPrice,-0.01", "maxPrice,-1", "minPrice,no", "maxPrice,NaN", "categoryId,-1", "categoryId,0",
            "categoryId,abc", "categoryId,9223372036854775807", "condition,INVALID", "condition,good", "condition,0",
            "sort,title", "sort,price", "sort,NEWEST"})
    void rejectsInvalidFiltersWithStructured400(String key, String value) throws Exception {
        mvc.perform(get("/api/listings").param(key, value))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsReversedPriceRangeLongSearchAndOffsetOverflow() throws Exception {
        mvc.perform(get("/api/listings").param("minPrice", "100").param("maxPrice", "10"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Minimum price must not exceed maximum price."));
        mvc.perform(get("/api/listings").param("search", "x".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/listings").param("page", "2147483647").param("size", "50"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/listings").param("sort", "createdAt,desc"))
                .andExpect(status().isBadRequest());
    }

    private Long add(String title, Category category, ListingCondition condition, String price, ListingStatus status, int day) {
        Listing listing = listings.saveAndFlush(new Listing(title, "description-only", new BigDecimal(price), condition, status, category, seller));
        jdbc.update("UPDATE listings SET created_at = ? WHERE id = ?", Instant.parse("2026-01-0" + day + "T00:00:00Z").atOffset(ZoneOffset.UTC), listing.getId());
        return listing.getId();
    }
}
