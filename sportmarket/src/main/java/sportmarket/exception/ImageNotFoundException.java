package sportmarket.exception;

public class ImageNotFoundException extends RuntimeException {
    public ImageNotFoundException() { super("Image was not found."); }
}
