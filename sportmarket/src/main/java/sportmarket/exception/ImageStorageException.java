package sportmarket.exception;

public class ImageStorageException extends RuntimeException {
    public ImageStorageException() { super("Image storage is temporarily unavailable."); }
}
