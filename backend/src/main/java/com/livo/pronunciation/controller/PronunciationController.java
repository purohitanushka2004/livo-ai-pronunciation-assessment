package com.livo.pronunciation.controller;

import com.livo.pronunciation.dto.AnalysisResponse;
import com.livo.pronunciation.service.AudioProcessingService;
import com.livo.pronunciation.service.PronunciationScoringService;
import com.livo.pronunciation.service.SpeechRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/pronunciation")
public class PronunciationController {

    private static final Logger log = LoggerFactory.getLogger(PronunciationController.class);

    private final AudioProcessingService audioProcessingService;
    private final SpeechRecognitionService speechRecognitionService;
    private final PronunciationScoringService pronunciationScoringService;

    public PronunciationController(AudioProcessingService audioProcessingService,
                                    SpeechRecognitionService speechRecognitionService,
                                    PronunciationScoringService pronunciationScoringService) {
        this.audioProcessingService = audioProcessingService;
        this.speechRecognitionService = speechRecognitionService;
        this.pronunciationScoringService = pronunciationScoringService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Accepts an audio file (30-45s of English speech), transcribes it,
     * scores pronunciation, and returns word-level highlights.
     *
     * No consent flag is required server-side because uploading itself is
     * the consent action; the frontend shows a consent notice before the
     * file picker is enabled (see DPDP notes in ARCHITECTURE.md).
     */
    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public ResponseEntity<AnalysisResponse> analyze(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "referenceText", required = false) String referenceText) throws Exception {
        long t0 = System.currentTimeMillis();
        Path rawTemp = null;
        Path wavTemp = null;
        try {
            rawTemp = audioProcessingService.saveTempFile(audio);

            AudioProcessingService.ConversionResult conversion = audioProcessingService.convertToWav16kMono(rawTemp);
            wavTemp = conversion.wavFile();
            long tConvert = System.currentTimeMillis();

            // Duration comes straight from ffmpeg's own log in the common case
            // (see AudioProcessingService) - only fall back to a second
            // ffprobe process if that parse ever fails.
            double duration = conversion.durationSeconds() != null
                    ? conversion.durationSeconds()
                    : audioProcessingService.getDurationSeconds(wavTemp);
            audioProcessingService.validateDuration(duration);

            SpeechRecognitionService.TranscriptionResult transcription = speechRecognitionService.transcribe(wavTemp);
            long tTranscribe = System.currentTimeMillis();

            AnalysisResponse response = pronunciationScoringService.score(transcription, duration, referenceText);

            long tTotal = System.currentTimeMillis() - t0;
            log.info("analyze() timing ms: convert={} transcribe={} total={}",
                    tConvert - t0, tTranscribe - tConvert, tTotal);

            return ResponseEntity.ok(response);
        } finally {
            // Guaranteed deletion regardless of success/failure - see DPDP policy.
            audioProcessingService.cleanup(rawTemp, wavTemp);
        }
    }
}
