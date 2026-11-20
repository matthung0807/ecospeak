package com.matthung.ecospeak.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AudioCache {
    private static final Pattern MANAGED =
            Pattern.compile(
                    "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(wav|tmp)");
    private final Path root;
    private final int maxBytes;

    public AudioCache(AudioProperties config) {
        root = config.getDirectory().toAbsolutePath().normalize();
        maxBytes = config.getMaxOutputBytes();
    }

    public Path root() {
        return root;
    }

    private void directory() throws IOException {
        for (Path part = root; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new IOException("Unsafe audio directory");
            }
        }
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid audio directory");
        }
    }

    private Path file(UUID id, String extension) throws IOException {
        directory();
        Path path = root.resolve(id.toString() + extension);
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Unsafe audio file");
        }
        return path;
    }

    public boolean ready(UUID id) throws IOException {
        Path path = file(id, ".wav");
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && Files.size(path) > 44
                && Files.size(path) <= maxBytes;
    }

    public byte[] read(UUID id) throws IOException {
        if (!ready(id)) {
            throw new AudioFailure(404, "audio_not_found");
        }
        try (var stream = Files.newInputStream(file(id, ".wav"), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IOException("Audio exceeds limit");
            }
            return bytes;
        }
    }

    public void publish(UUID id, byte[] wav) throws IOException {
        Path temporary = file(id, ".tmp");
        try {
            try (var stream =
                    Files.newOutputStream(
                            temporary,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)) {
                stream.write(wav);
            }
            Files.move(
                    temporary,
                    file(id, ".wav"),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void remove(UUID id) throws IOException {
        Files.deleteIfExists(file(id, ".wav"));
        Files.deleteIfExists(file(id, ".tmp"));
    }

    public void reconcile(Set<UUID> retained) throws IOException {
        directory();
        IOException failure = null;
        try (var paths = Files.newDirectoryStream(root)) {
            for (Path path : paths) {
                var match = MANAGED.matcher(path.getFileName().toString());
                if (!match.matches()) {
                    continue;
                }
                if ("tmp".equals(match.group(2))
                        || !retained.contains(UUID.fromString(match.group(1)))) {
                    // Delete only the managed entry, never traverse links or directories.
                    try {
                        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("Unexpected cache directory");
                        }
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        failure = error;
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
