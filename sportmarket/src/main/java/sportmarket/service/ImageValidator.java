package sportmarket.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import sportmarket.exception.ImageTooLargeException;
import sportmarket.exception.InvalidImageException;

@Component
public class ImageValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    public record ValidatedImage(byte[] content, String contentType, String extension) {}

    public ValidatedImage validate(MultipartFile file) {
        if (file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_BYTES) throw new ImageTooLargeException();
        try (var stream = file.getInputStream()) {
            byte[] source = stream.readNBytes(MAX_BYTES + 1);
            if (source.length > MAX_BYTES) throw new ImageTooLargeException();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png") && !format.equals("webp")) throw invalid();
                    validateContainer(source, format);
                    reader.setInput(input, false, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 6000 || height > 6000 || (long) width * height > MAX_PIXELS) {
                        throw new InvalidImageException("Image dimensions must be at most 6000 per side and 16 megapixels.");
                    }
                    AtomicBoolean warning = new AtomicBoolean();
                    reader.addIIOReadWarningListener((ignored, message) -> warning.set(true));
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null || warning.get()) throw invalid();
                    String output = format.equals("jpeg") ? "jpeg" : "png";
                    var bytes = new ByteArrayOutputStream();
                    try (var bounded = new FilterOutputStream(bytes) {
                        private int written;
                        @Override public void write(int value) throws IOException {
                            if (++written > MAX_BYTES) throw new LimitException(); out.write(value);
                        }
                        @Override public void write(byte[] values, int offset, int length) throws IOException {
                            if ((long) written + length > MAX_BYTES) throw new LimitException();
                            written += length; out.write(values, offset, length);
                        }
                    }) {
                        if (!ImageIO.write(decoded, output, bounded)) throw invalid();
                    } finally { decoded.flush(); }
                    return new ValidatedImage(bytes.toByteArray(), "image/" + output, output.equals("jpeg") ? "jpg" : "png");
                } finally { reader.dispose(); }
            }
        } catch (IOException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof LimitException) throw new ImageTooLargeException();
            }
            throw invalid();
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) { throw invalid(); }
    }
    private static InvalidImageException invalid() {
        return new InvalidImageException("Upload a valid, uncorrupted JPEG, PNG, or static WebP image.");
    }
    private static class LimitException extends IOException {}
    private static void validateContainer(byte[] data, String format) {
        if (format.equals("jpeg")) {
            if (data.length < 4 || (data[0] & 255) != 255 || (data[1] & 255) != 216
                    || (data[data.length - 2] & 255) != 255 || (data[data.length - 1] & 255) != 217) throw invalid();
        } else if (format.equals("png")) {
            int offset = 8; boolean ended = false;
            while (offset + 12 <= data.length) {
                int length = ByteBuffer.wrap(data, offset, 4).getInt();
                if (length < 0 || (long) offset + 12 + length > data.length) throw invalid();
                CRC32 crc = new CRC32(); crc.update(data, offset + 4, length + 4);
                long expected = Integer.toUnsignedLong(ByteBuffer.wrap(data, offset + 8 + length, 4).getInt());
                if (crc.getValue() != expected) throw invalid();
                String type = new String(data, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if (type.equals("acTL")) throw invalid(); // Static images only; do not silently drop animation.
                offset += length + 12;
                if (type.equals("IEND")) { ended = length == 0 && offset == data.length; break; }
            }
            if (!ended) throw invalid();
        } else {
            if (data.length < 20 || Integer.toUnsignedLong(ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8 != data.length) throw invalid();
            int offset = 12;
            while (offset + 8 <= data.length) {
                String type = new String(data, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
                long length = Integer.toUnsignedLong(ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
                if (type.equals("ANIM") || type.equals("ANMF") || offset + 8L + length + (length % 2) > data.length) throw invalid();
                offset += (int) (8 + length + length % 2);
            }
            if (offset != data.length) throw invalid();
        }
    }
}
