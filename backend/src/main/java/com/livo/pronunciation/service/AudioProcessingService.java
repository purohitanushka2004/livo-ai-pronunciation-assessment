package com.livo.pronunciation.service;

import com.livo.pronunciation.exception.InvalidAudioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles everything that touches a file on disk. Nothing here persists
 * beyond the lifetime of a single request:
 *
 *  1. The raw upload is written to a per-request temp file.
 *  2. ffmpeg converts it to 16kHz mono PCM WAV (what Vosk expects).
 *  3. Duration is checked against the 30-45s window using ffprobe.
 *  4. Both temp files are deleted in a finally block by the caller
 *     (see PronunciationController), regardless of success or failure.
 *
 * DPDP: no audio bytes are logged, and no copy is written anywhere outside
 * the OS temp directory, which is cleared on deletion.
 */
@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);
    private static final double MIN_DURATION_SECONDS = 30.0;
    private static final double MAX_DURATION_SECONDS = 45.0;
    // small grace window so a 29.6s or 45.4s clip isn't rejected for rounding
    private static final double GRACE_SECONDS = 1.0;

    public Path saveTempFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioException("No audio file was received.");
        }
        String suffix = extensionOf(file.getOriginalFilename());
        Path temp = Files.createTempFile("livo-upload-", suffix);
        file.transferTo(temp);
        return temp;
    }

    private static final Pattern FFMPEG_DURATION_PATTERN =
            Pattern.compile("Duration:\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    /** Result of a single ffmpeg pass: the converted WAV, plus duration if we
     *  could parse it straight out of ffmpeg's own log (saving a second
     *  ffprobe process spawn on the common path — see ARCHITECTURE.md
     *  "Latency" notes). durationSeconds is null if parsing failed, in which
     *  case the caller should fall back to {@link #getDurationSeconds}. */
    public record ConversionResult(Path wavFile, Double durationSeconds) {}

    public ConversionResult convertToWav16kMono(Path input) throws IOException, InterruptedException {
        Path output = Files.createTempFile("livo-converted-", ".wav");
        List<String> command = List.of(
                "ffmpeg", "-y",
                "-i", input.toString(),
                "-ac", "1",
                "-ar", "16000",
                "-sample_fmt", "s16",
                output.toString()
        );
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        // ffmpeg writes its progress/metadata log (including the source
        // "Duration: HH:MM:SS.xx" line) to this combined stream. Reading it
        // here means we get duration for free from the conversion we were
        // already doing, instead of spawning a second ffprobe process just
        // to ask the same question - a real, measurable latency win since
        // process spawn overhead dominates for short 30-45s clips.
        StringBuilder logBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logBuilder.append(line).append('\n');
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new InvalidAudioException("Audio conversion timed out.");
        }
        if (process.exitValue() != 0) {
            throw new InvalidAudioException(
                    "Could not read this audio file. Please upload a valid WAV, MP3, M4A, or OGG file.");
        }

        Double duration = parseDurationFromFfmpegLog(logBuilder.toString());
        return new ConversionResult(output, duration);
    }

    private Double parseDurationFromFfmpegLog(String ffmpegLog) {
        Matcher m = FFMPEG_DURATION_PATTERN.matcher(ffmpegLog);
        if (!m.find()) return null;
        try {
            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));
            int seconds = Integer.parseInt(m.group(3));
            int centis = Integer.parseInt(m.group(4));
            return hours * 3600.0 + minutes * 60.0 + seconds + centis / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public double getDurationSeconds(Path wavFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                wavFile.toString()
        );
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String out;
        try (var reader = process.inputReader()) {
            out = reader.readLine();
        }
        process.waitFor(10, TimeUnit.SECONDS);
        if (out == null) {
            throw new InvalidAudioException("Could not determine audio duration.");
        }
        try {
            return Double.parseDouble(out.trim());
        } catch (NumberFormatException e) {
            throw new InvalidAudioException("Could not determine audio duration.");
        }
    }

    public void validateDuration(double durationSeconds) {
        if (durationSeconds < MIN_DURATION_SECONDS - GRACE_SECONDS) {
            throw new InvalidAudioException(String.format(
                    "Recording is too short (%.1fs). Please upload 30-45 seconds of speech.", durationSeconds));
        }
        if (durationSeconds > MAX_DURATION_SECONDS + GRACE_SECONDS) {
            throw new InvalidAudioException(String.format(
                    "Recording is too long (%.1fs). Please upload 30-45 seconds of speech.", durationSeconds));
        }
    }

    public void cleanup(Path... paths) {
        for (Path p : paths) {
            if (p == null) continue;
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Failed to delete temp file {}: {}", p, e.getMessage());
            }
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return ".tmp";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
