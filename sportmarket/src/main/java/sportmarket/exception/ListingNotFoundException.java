package sportmarket.exception;

public class ListingNotFoundException extends RuntimeException {
    public ListingNotFoundException(Long id) {
        super("Listing with ID " + id + " was not found.");
    }
}
