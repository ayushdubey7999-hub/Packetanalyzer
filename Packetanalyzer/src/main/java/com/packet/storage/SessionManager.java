package com.packet.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.packet.model.CaptureStatistics;
import com.packet.model.PacketInfo;
import com.packet.model.PacketSnapshot;
import com.packet.model.SessionSource;
import com.packet.model.SessionStatisticsSnapshot;
import com.packet.util.AppInfo;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Saves and loads analyzer sessions as JSON files.
 *
 * <p>Serialization is kept separate from the UI layer. Packet rows are streamed in batches so large
 * sessions do not require holding the entire JSON document in memory.
 */
public final class SessionManager {

    public static final String FILE_EXTENSION = "lpas";
    public static final String FORMAT_ID = "lan-packet-analyzer-session";
    public static final int FORMAT_VERSION = 1;
    public static final int LOAD_BATCH_SIZE = 250;

    private static final Gson GSON = new GsonBuilder().create();

    public record SaveRequest(
            List<PacketInfo> packets,
            CaptureStatistics statistics,
            String sessionMode,
            SessionSource source) {}

    public record LoadedSession(
            int formatVersion,
            String savedAt,
            String sessionMode,
            SessionStatisticsSnapshot statistics,
            SessionSource source,
            int packetCount) {}

    public record LoadProgress(String message, int packetCount) {}

    /**
     * Writes all packet data and statistics to a JSON session file.
     */
    public void save(Path destination, SaveRequest request) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(request, "request");
        List<PacketInfo> packets = request.packets() != null ? request.packets() : List.of();
        SessionStatisticsSnapshot stats =
                request.statistics() != null
                        ? SessionStatisticsSnapshot.from(request.statistics())
                        : new SessionStatisticsSnapshot();

        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8);
                JsonWriter json = new JsonWriter(writer)) {
            json.setIndent("  ");
            json.beginObject();
            json.name("formatVersion").value(FORMAT_VERSION);
            json.name("format").value(FORMAT_ID);
            json.name("appVersion").value(AppInfo.VERSION);
            json.name("savedAt").value(Instant.now().toString());
            json.name("sessionMode").value(request.sessionMode() != null ? request.sessionMode() : "");

            json.name("statistics");
            GSON.toJson(stats, SessionStatisticsSnapshot.class, json);

            if (request.source() != null) {
                json.name("source");
                GSON.toJson(request.source(), SessionSource.class, json);
            }

            json.name("packets");
            json.beginArray();
            for (PacketInfo packet : packets) {
                GSON.toJson(PacketSnapshot.from(packet), PacketSnapshot.class, json);
            }
            json.endArray();
            json.endObject();
        }
    }

    /**
     * Loads a session file on a background thread, delivering packet batches to the consumer.
     */
    public Thread loadAsync(
            Path file,
            Consumer<LoadProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer,
            Consumer<LoadedSession> completeConsumer,
            Consumer<Throwable> errorConsumer) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(progressConsumer, "progressConsumer");
        Objects.requireNonNull(batchConsumer, "batchConsumer");
        Objects.requireNonNull(completeConsumer, "completeConsumer");
        Objects.requireNonNull(errorConsumer, "errorConsumer");

        Thread thread =
                new Thread(
                        () -> {
                            try {
                                LoadedSession session = load(file, progressConsumer, batchConsumer);
                                completeConsumer.accept(session);
                            } catch (Throwable t) {
                                errorConsumer.accept(t);
                            }
                        },
                        "session-load");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private LoadedSession load(
            Path file,
            Consumer<LoadProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws SessionLoadException, IOException {
        if (!Files.isRegularFile(file)) {
            throw new SessionLoadException("Session file not found: " + file);
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                JsonReader json = new JsonReader(reader)) {
            return parseSession(json, progressConsumer, batchConsumer);
        } catch (JsonSyntaxException e) {
            throw new SessionLoadException("Session file contains invalid JSON.", e);
        } catch (IOException e) {
            throw new SessionLoadException("Could not read session file.", e);
        }
    }

    private LoadedSession parseSession(
            JsonReader json,
            Consumer<LoadProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws IOException, SessionLoadException {
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            throw new SessionLoadException("Session file is not a valid JSON object.");
        }

        int formatVersion = -1;
        String format = null;
        String savedAt = null;
        String sessionMode = null;
        SessionStatisticsSnapshot statistics = new SessionStatisticsSnapshot();
        SessionSource source = null;
        int packetCount = 0;
        List<PacketInfo> batch = new ArrayList<>(LOAD_BATCH_SIZE);

        json.beginObject();
        while (json.hasNext()) {
            String name = json.nextName();
            switch (name) {
                case "formatVersion" -> formatVersion = json.nextInt();
                case "format" -> format = json.nextString();
                case "savedAt" -> savedAt = json.nextString();
                case "sessionMode" -> sessionMode = json.nextString();
                case "statistics" -> statistics = GSON.fromJson(json, SessionStatisticsSnapshot.class);
                case "source" -> source = GSON.fromJson(json, SessionSource.class);
                case "packets" ->
                        packetCount = readPackets(json, batch, progressConsumer, batchConsumer);
                default -> json.skipValue();
            }
        }
        json.endObject();

        flushBatch(batch, packetCount, progressConsumer, batchConsumer);

        validateHeader(formatVersion, format);

        if (statistics == null) {
            statistics = new SessionStatisticsSnapshot();
        }

        return new LoadedSession(
                formatVersion,
                savedAt,
                sessionMode,
                statistics,
                source,
                packetCount);
    }

    private int readPackets(
            JsonReader json,
            List<PacketInfo> batch,
            Consumer<LoadProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer)
            throws IOException, SessionLoadException {
        if (json.peek() != JsonToken.BEGIN_ARRAY) {
            throw new SessionLoadException("Session file is missing a packets array.");
        }

        json.beginArray();
        int count = 0;
        while (json.hasNext()) {
            PacketSnapshot snapshot = GSON.fromJson(json, PacketSnapshot.class);
            if (snapshot == null) {
                throw new SessionLoadException("Encountered a null packet entry in the session file.");
            }
            PacketInfo packet = snapshot.toPacketInfo();
            batch.add(packet);
            count++;

            if (batch.size() >= LOAD_BATCH_SIZE) {
                flushBatch(batch, count, progressConsumer, batchConsumer);
            }
        }
        json.endArray();

        if (count == 0) {
            progressConsumer.accept(new LoadProgress("No packets in session", 0));
        }
        return count;
    }

    private void flushBatch(
            List<PacketInfo> batch,
            int packetCount,
            Consumer<LoadProgress> progressConsumer,
            Consumer<List<PacketInfo>> batchConsumer) {
        if (batch.isEmpty()) {
            return;
        }
        progressConsumer.accept(new LoadProgress("Loading session…", packetCount));
        batchConsumer.accept(List.copyOf(batch));
        batch.clear();
    }

    private static void validateHeader(int formatVersion, String format) throws SessionLoadException {
        if (!FORMAT_ID.equals(format)) {
            throw new SessionLoadException(
                    "Unrecognized session format. Expected \"" + FORMAT_ID + "\" but found \""
                            + format
                            + "\".");
        }
        if (formatVersion > FORMAT_VERSION) {
            throw new SessionLoadException(
                    "Session file version "
                            + formatVersion
                            + " is newer than this application supports ("
                            + FORMAT_VERSION
                            + ").");
        }
        if (formatVersion < 1) {
            throw new SessionLoadException("Session file version is not supported.");
        }
    }

    /** Synchronous load for tests or small sessions. */
    public LoadedSession loadSync(Path file) throws SessionLoadException, IOException {
        return load(file, progress -> {}, batch -> {});
    }
}
