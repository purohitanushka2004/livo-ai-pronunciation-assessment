package com.livo.pronunciation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the Vosk offline speech recognizer.
 *
 * Why Vosk instead of a paid API (e.g. SpeechSuper, Google Cloud Speech,
 * Azure Pronunciation Assessment): it is free, open-source (Apache 2.0),
 * runs fully offline/on-server, needs no API key, and gives us word-level
 * timestamps + confidence, which is exactly the signal we need to flag
 * unclear/mispronounced words. The trade-off is accuracy: Vosk's small
 * English model is not phoneme-level like SpeechSuper's pronunciation
 * assessment engine. This is documented as a known limitation in
 * ARCHITECTURE.md, with SpeechSuper/Azure listed as the production upgrade
 * path once a paid key is available.
 */
@Service
public class SpeechRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${vosk.model.path:./models/vosk-model-small-en-us-0.15}")
    private String modelPath;

    private Model model;

    @PostConstruct
    public void init() throws IOException {
        log.info("Loading Vosk model from {}", modelPath);
        this.model = new Model(modelPath);
        log.info("Vosk model loaded.");
    }

    @PreDestroy
    public void close() {
        if (model != null) {
            model.close();
        }
    }

    public record RecognizedWord(String word, double start, double end, double confidence) {}

    public record TranscriptionResult(String transcript, List<RecognizedWord> words) {}

    public TranscriptionResult transcribe(Path wavFile) throws IOException {
        List<RecognizedWord> words = new ArrayList<>();
        StringBuilder transcriptBuilder = new StringBuilder();

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile.toFile());
             Recognizer recognizer = new Recognizer(model, 16000)) {

            recognizer.setWords(true);
            recognizer.setMaxAlternatives(0);

            // Larger read buffer (16KB vs the original 4KB) means far fewer
            // native acceptWaveForm() calls for a 30-45s clip, cutting
            // JNI/syscall overhead - a direct latency win in the hottest
            // part of the request.
            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = ais.read(buffer)) >= 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    consumeResult(recognizer.getResult(), words, transcriptBuilder);
                }
            }
            consumeResult(recognizer.getFinalResult(), words, transcriptBuilder);

        } catch (Exception e) {
            throw new IOException("Speech recognition failed: " + e.getMessage(), e);
        }

        return new TranscriptionResult(transcriptBuilder.toString().trim(), words);
    }

    private void consumeResult(String json, List<RecognizedWord> words, StringBuilder transcriptBuilder) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode wordsNode = root.get("result");
            if (wordsNode != null && wordsNode.isArray()) {
                for (JsonNode w : wordsNode) {
                    String word = w.get("word").asText();
                    double start = w.get("start").asDouble();
                    double end = w.get("end").asDouble();
                    // Vosk's "conf" field is a per-word recognition confidence (0-1).
                    // Some model/recognizer combos omit it; default to a neutral 0.85.
                    double confidence = w.has("conf") ? w.get("conf").asDouble() : 0.85;
                    words.add(new RecognizedWord(word, start, end, confidence));
                }
            }
            JsonNode text = root.get("text");
            if (text != null && !text.asText().isBlank()) {
                if (transcriptBuilder.length() > 0) transcriptBuilder.append(" ");
                transcriptBuilder.append(text.asText());
            }
        } catch (Exception e) {
            log.warn("Failed to parse Vosk result chunk: {}", e.getMessage());
        }
    }
}
