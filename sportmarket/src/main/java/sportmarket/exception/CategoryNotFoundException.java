package sportmarket.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category with ID " + id + " was not found.");
    }
}
