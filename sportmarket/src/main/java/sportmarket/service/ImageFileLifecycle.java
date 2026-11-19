package sportmarket.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Coordinates filesystem side effects with database transactions; no file is deleted on rollback. */
@Component
public class ImageFileLifecycle {
    private static final Logger log = LoggerFactory.getLogger(ImageFileLifecycle.class);
    private final ImageStorageService storage;
    public ImageFileLifecycle(ImageStorageService storage) { this.storage = storage; }
    public void deleteOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteSafely(key);
            }
        });
    }
    public void deleteAfterCommit(List<String> keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { keys.forEach(ImageFileLifecycle.this::deleteSafely); }
        });
    }
    private void deleteSafely(String key) {
        try { storage.delete(key); }
        catch (RuntimeException exception) { log.error("Image file cleanup failed for storage key {}; orphan cleanup is required.", key); }
    }
}
