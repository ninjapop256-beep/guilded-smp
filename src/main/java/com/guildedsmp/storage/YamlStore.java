package com.guildedsmp.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves one YAML data file safely.
 * <ul>
 *   <li>Writes go to a temp file first and are then moved over the original (atomic where supported).</li>
 *   <li>A corrupt file is renamed to {@code *.corrupt-<timestamp>} and never overwritten.</li>
 *   <li>Serialization happens on the calling (main) thread; only the disk write is async.</li>
 * </ul>
 */
public final class YamlStore {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File file;
    private final IoExecutor io;
    private final Logger logger;

    public YamlStore(File file, IoExecutor io, Logger logger) {
        this.file = file;
        this.io = io;
        this.logger = logger;
    }

    public File file() {
        return file;
    }

    public YamlConfiguration load() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.isFile()) {
            return yaml;
        }
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            File quarantine = new File(file.getParentFile(),
                    file.getName() + ".corrupt-" + LocalDateTime.now().format(STAMP));
            boolean moved = file.renameTo(quarantine);
            logger.log(Level.SEVERE, "Could not read " + file.getName() + " (" + e.getMessage() + "). "
                    + (moved ? "The unreadable file was kept as " + quarantine.getName() + "."
                    : "The file could not be moved aside; it will not be overwritten until fixed."), e);
            if (!moved) {
                return null;
            }
            return new YamlConfiguration();
        }
    }

    /** Serializes now and writes on the IO thread. */
    public void saveAsync(YamlConfiguration yaml) {
        String data = yaml.saveToString();
        io.submit(() -> write(data));
    }

    /** Serializes and writes immediately (used during shutdown). */
    public void saveSync(YamlConfiguration yaml) {
        write(yaml.saveToString());
    }

    private void write(String data) {
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save " + file.getName() + "; the previous copy was left untouched.", e);
        }
    }
}
