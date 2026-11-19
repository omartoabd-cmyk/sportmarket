package sportmarket;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import sportmarket.dto.ListingImageResponse;
import sportmarket.entity.*;
import sportmarket.repository.*;
import sportmarket.service.*;
import sportmarket.exception.*;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:image-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingImageTests {
    private static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory(Path.of("target"), "image-test-"); } catch (Exception exception) { throw new ExceptionInInitializerError(exception); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) { registry.add("app.images.directory", () -> ROOT.toAbsolutePath().toString()); }
    @Autowired MockMvc mvc;
    @Autowired ListingRepository listings;
    @Autowired ListingImageRepository images;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ListingImageService service;
    @Autowired ListingService listingService;
    @Autowired ImageStorageService storage;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;
    @LocalServerPort int port;
    private User owner;
    private User other;
    private Listing listing;
    private Listing anotherListing;
    private String token;
    private String otherToken;

    @BeforeEach void setUp() {
        owner = users.saveAndFlush(new User("Image Owner", UUID.randomUUID() + "@example.test", "test-hash"));
        other = users.saveAndFlush(new User("Other Owner", UUID.randomUUID() + "@example.test", "test-hash"));
        Category category = categories.findAllByOrderByNameAsc().getFirst();
        listing = listings.saveAndFlush(new Listing("Image test gear", "Description", new BigDecimal("10.00"), ListingCondition.GOOD, ListingStatus.AVAILABLE, category, owner));
        anotherListing = listings.saveAndFlush(new Listing("Other gear", "Description", new BigDecimal("10.00"), ListingCondition.GOOD, ListingStatus.AVAILABLE, category, other));
        token = tokens.issueToken(owner.getId().toString()).accessToken();
        otherToken = tokens.issueToken(other.getId().toString()).accessToken();
    }
    @AfterEach void cleanUp() {
        if (listings.existsById(listing.getId())) listingService.deleteListing(listing.getId(), owner.getId().toString());
        if (listings.existsById(anotherListing.getId())) listingService.deleteListing(anotherListing.getId(), other.getId().toString());
        users.deleteById(owner.getId()); users.deleteById(other.getId());
    }
    @Test void uploadsPngAndServesPublicMetadataBytesAndSecurityHeaders() throws Exception {
        var image = upload("png");
        var entity = images.findById(image.id()).orElseThrow();
        assertThat(Files.exists(ROOT.resolve(entity.getStorageKey()))).isTrue();
        assertThat(entity.getStorageKey()).matches("[a-f0-9-]{36}\\.png");
        mvc.perform(get("/api/listings/{id}/images", listing.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(image.id())).andExpect(jsonPath("$[0].storageKey").doesNotExist());
        mvc.perform(get(image.url())).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(content().bytes(storage.read(entity.getStorageKey())));
        mvc.perform(get("/api/listings/{id}", listing.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(image.id()));
        mvc.perform(get("/api/listings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + listing.getId() + ")].images[0].id").value(org.hamcrest.Matchers.hasItem(image.id().intValue())));
    }
    @Test void acceptsJpegAndWebpByActualContentRatherThanDeclaredTypeOrFilename() throws Exception {
        var jpeg = upload("jpeg");
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        byte[] webp = getClass().getResourceAsStream("/images/valid.webp").readAllBytes();
        var result = mvc.perform(multipart("/api/listings/{id}/images", listing.getId())
                        .file(new MockMultipartFile("file", "not-trusted.exe", "application/octet-stream", webp))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/png")).andReturn();
        var image = json.readValue(result.getResponse().getContentAsString(), ListingImageResponse.class);
        byte[] content = storage.read(images.findById(image.id()).orElseThrow().getStorageKey());
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(content))).isNotNull();
    }
    @Test void rejectsInvalidCorruptAndUnsafeImages() throws Exception {
        byte[] png = bytes("png");
        byte[] corrupt = png.clone(); corrupt[corrupt.length - 1] ^= 1;
        for (byte[] bad : List.of(new byte[0], "<svg onload='alert(1)'/>".getBytes(), "not an image".getBytes(),
                Arrays.copyOf(png, png.length / 2), corrupt, bytes("gif"), Arrays.copyOf(bytes("jpeg"), 25))) {
            mvc.perform(multipart("/api/listings/{id}/images", listing.getId())
                            .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", bad)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        assertThat(images.findByListingIdOrderByDisplayOrderAsc(listing.getId())).isEmpty();
    }
    @Test void rejectsOversizedFileInServiceAndRealMultipartRequest() throws Exception {
        byte[] oversized = new byte[ImageValidator.MAX_BYTES + 1];
        mvc.perform(multipart("/api/listings/{id}/images", listing.getId())
                        .file(new MockMultipartFile("file", "big.png", "image/png", oversized)).header("Authorization", "Bearer " + token))
                .andExpect(status().is(413)).andExpect(jsonPath("$.status").value(413));
        String boundary = "SportMarketTestBoundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());
        body.write(oversized); body.write(("\r\n--" + boundary + "--\r\n").getBytes());
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/listings/" + listing.getId() + "/images"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(json.readTree(response.body()).get("status").asInt()).isEqualTo(413);
    }
    @Test void rejectsSixthImageAndReusesDeletedSlot() throws Exception {
        var first = upload("png");
        for (int i = 1; i < 5; i++) upload("png");
        mvc.perform(multipart("/api/listings/{id}/images", listing.getId()).file(file("png")).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/listings/{id}/images/{imageId}", listing.getId(), first.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(upload("png").displayOrder()).isZero();
    }
    @Test void nonOwnerCannotUploadOrDeleteAndAnonymousCannotUpload() throws Exception {
        mvc.perform(multipart("/api/listings/{id}/images", listing.getId()).file(file("png")))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/listings/{id}/images", listing.getId()).file(file("png")).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        var image = upload("png");
        mvc.perform(delete("/api/listings/{id}/images/{imageId}", listing.getId(), image.id()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        assertThat(images.existsById(image.id())).isTrue();
    }
    @Test void deletesMetadataAndFileAndListingDeletionCleansItsImages() throws Exception {
        var image = upload("png");
        String key = images.findById(image.id()).orElseThrow().getStorageKey();
        mvc.perform(delete("/api/listings/{id}/images/{imageId}", listing.getId(), image.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(images.existsById(image.id())).isFalse(); assertThat(Files.exists(ROOT.resolve(key))).isFalse();
        mvc.perform(get(image.url())).andExpect(status().isNotFound());
        var another = upload("png");
        String anotherKey = images.findById(another.id()).orElseThrow().getStorageKey();
        listingService.deleteListing(listing.getId(), owner.getId().toString());
        assertThat(images.existsById(another.id())).isFalse(); assertThat(Files.exists(ROOT.resolve(anotherKey))).isFalse();
    }
    @Test void rejectsImageFromDifferentListingAndMissingResources() throws Exception {
        var image = upload("png");
        mvc.perform(delete("/api/listings/{id}/images/{imageId}", anotherListing.getId(), image.id()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/listings/9223372036854775807/images")).andExpect(status().isNotFound());
        mvc.perform(get("/api/listing-images/9223372036854775807/content")).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/listings/{id}/images", listing.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
    @Test void untrustedNamesCannotTraverseStorageAndInternalKeysAreRestricted() throws Exception {
        var result = mvc.perform(multipart("/api/listings/{id}/images", listing.getId())
                        .file(new MockMultipartFile("file", "../../outside.png", "image/png", bytes("png"))).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        var image = json.readValue(result.getResponse().getContentAsString(), ListingImageResponse.class);
        assertThat(images.findById(image.id()).orElseThrow().getStorageKey()).doesNotContain("..", "/", "outside");
        for (String key : List.of("../outside.png", "..\\outside.png", "/tmp/outside.png", "C:\\Windows\\win.ini", "https://example.test/x.png")) {
            assertThatThrownBy(() -> storage.read(key)).isInstanceOf(ImageStorageException.class);
            assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(ImageStorageException.class);
        }
    }
    @Test void rollbackRemovesNewFileAndRetainsOldFileForRolledBackDelete() throws Exception {
        var template = new TransactionTemplate(transactionManager);
        var image = upload("png");
        String retained = images.findById(image.id()).orElseThrow().getStorageKey();
        template.executeWithoutResult(status -> { service.delete(listing.getId(), image.id(), owner.getId().toString()); status.setRollbackOnly(); });
        assertThat(Files.exists(ROOT.resolve(retained))).isTrue(); assertThat(images.existsById(image.id())).isTrue();
        final String[] rolledBackKey = new String[1];
        template.executeWithoutResult(status -> {
            try {
                var created = service.upload(listing.getId(), owner.getId().toString(), file("png"));
                rolledBackKey[0] = images.findById(created.id()).orElseThrow().getStorageKey(); status.setRollbackOnly();
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        assertThat(Files.exists(ROOT.resolve(rolledBackKey[0]))).isFalse();
    }
    @Test void concurrentUploadsCannotExceedFiveImages() throws Exception {
        for (int i = 0; i < 4; i++) upload("png");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> task = () -> { start.await(); try { service.upload(listing.getId(), owner.getId().toString(), file("png")); return true; }
            catch (InvalidImageException exception) { return false; } };
        try {
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(images.findByListingIdOrderByDisplayOrderAsc(listing.getId())).hasSize(5);
        } finally { pool.shutdownNow(); }
    }
    private ListingImageResponse upload(String format) throws Exception {
        var result = mvc.perform(multipart("/api/listings/{id}/images", listing.getId()).file(file(format)).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), ListingImageResponse.class);
    }
    private MockMultipartFile file(String format) throws Exception { return new MockMultipartFile("file", "photo." + format, "image/" + format, bytes(format)); }
    private byte[] bytes(String format) throws Exception {
        var image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0x339955);
        var output = new ByteArrayOutputStream(); ImageIO.write(image, format, output); return output.toByteArray();
    }
}
