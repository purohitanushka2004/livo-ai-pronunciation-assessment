package com.livo.pronunciation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Livo AI Pronunciation Scoring service.
 *
 * DPDP note: this application never persists uploaded audio to disk beyond a
 * short-lived temp file that is deleted immediately after processing (see
 * {@link com.livo.pronunciation.service.AudioProcessingService}). No audio
 * or transcript is written to a database. See ARCHITECTURE.md for the full
 * data-handling policy.
 */
@SpringBootApplication
@EnableScheduling
public class PronunciationApplication {
    public static void main(String[] args) {
        SpringApplication.run(PronunciationApplication.class, args);
    }
}
