package sportmarket.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sportmarket.dto.ListingImageResponse;
import sportmarket.service.ListingImageService;

@RestController
public class ListingImageController {
    private final ListingImageService service;
    public ListingImageController(ListingImageService service) { this.service = service; }
    @PostMapping(value = "/api/listings/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingImageResponse> upload(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
                                                       @RequestPart("file") MultipartFile file) {
        var image = service.upload(id, jwt.getSubject(), file);
        return ResponseEntity.created(URI.create(image.url())).body(image);
    }
    @GetMapping("/api/listings/{id}/images")
    public List<ListingImageResponse> images(@PathVariable Long id) { return service.getImages(id); }
    @GetMapping("/api/listing-images/{imageId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long imageId) {
        var content = service.getContent(imageId);
        String extension = content.contentType().equals("image/jpeg") ? "jpg" : "png";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("image-" + imageId + "." + extension).build().toString())
                .body(content.bytes());
    }
    @DeleteMapping("/api/listings/{listingId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long listingId, @PathVariable Long imageId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(listingId, imageId, jwt.getSubject());
    }
}
