package sportmarket.exception;

public class ImageTooLargeException extends RuntimeException {
    public ImageTooLargeException() { super("Image exceeds the 5 MB limit."); }
}
