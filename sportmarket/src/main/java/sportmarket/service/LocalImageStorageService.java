package sportmarket.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sportmarket.exception.ImageNotFoundException;
import sportmarket.exception.ImageStorageException;

@Service
public class LocalImageStorageService implements ImageStorageService {
    private final Path root;
    public LocalImageStorageService(@Value("${app.images.directory}") String directory) {
        try {
            Path configured = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(configured);
            if (Files.isSymbolicLink(configured)) throw new ImageStorageException();
            root = configured.toRealPath();
        } catch (IOException exception) { throw new ImageStorageException(); }
    }
    private Path resolve(String key) {
        if (key == null || !key.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)")) {
            throw new ImageStorageException();
        }
        Path path = root.resolve(key).normalize();
        if (!root.equals(path.getParent()) || Files.isSymbolicLink(path)) throw new ImageStorageException();
        return path;
    }
    @Override public String store(byte[] content, String extension) {
        if (!(extension.equals("jpg") || extension.equals("png"))) throw new ImageStorageException();
        String key = UUID.randomUUID() + "." + extension;
        Path path = resolve(key);
        try {
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            return key;
        } catch (FileAlreadyExistsException exception) {
            // Never remove an existing file if CREATE_NEW rejected a key collision.
            throw new ImageStorageException();
        } catch (IOException exception) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Report the storage failure below. */ }
            throw new ImageStorageException();
        }
    }
    @Override public byte[] read(String key) {
        Path path = resolve(key);
        try (var input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(ImageValidator.MAX_BYTES + 1);
            if (content.length > ImageValidator.MAX_BYTES) throw new ImageStorageException();
            return content;
        } catch (NoSuchFileException exception) { throw new ImageNotFoundException(); }
        catch (IOException exception) { throw new ImageStorageException(); }
    }
    @Override public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException exception) { throw new ImageStorageException(); }
    }
}
