package com.livo.pronunciation.dto;

import java.util.List;

public class AnalysisResponse {

    private String transcript;
    private int overallScore;         // 0 - 100
    private double durationSeconds;
    private double speakingRateWpm;
    private int fluencyScore;         // 0 - 100 (pacing / pausing)
    private int clarityScore;         // 0 - 100 (avg word confidence)
    private List<WordResult> words;
    private List<String> summary;     // short human-readable takeaways
    private String mode;              // "free" | "reference"
    private String referenceText;     // echoed back when mode == "reference"

    public AnalysisResponse() {}

    public AnalysisResponse(String transcript, int overallScore, double durationSeconds,
                             double speakingRateWpm, int fluencyScore, int clarityScore,
                             List<WordResult> words, List<String> summary) {
        this(transcript, overallScore, durationSeconds, speakingRateWpm, fluencyScore, clarityScore,
                words, summary, "free", null);
    }

    public AnalysisResponse(String transcript, int overallScore, double durationSeconds,
                             double speakingRateWpm, int fluencyScore, int clarityScore,
                             List<WordResult> words, List<String> summary,
                             String mode, String referenceText) {
        this.transcript = transcript;
        this.overallScore = overallScore;
        this.durationSeconds = durationSeconds;
        this.speakingRateWpm = speakingRateWpm;
        this.fluencyScore = fluencyScore;
        this.clarityScore = clarityScore;
        this.words = words;
        this.summary = summary;
        this.mode = mode;
        this.referenceText = referenceText;
    }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public double getSpeakingRateWpm() { return speakingRateWpm; }
    public void setSpeakingRateWpm(double speakingRateWpm) { this.speakingRateWpm = speakingRateWpm; }

    public int getFluencyScore() { return fluencyScore; }
    public void setFluencyScore(int fluencyScore) { this.fluencyScore = fluencyScore; }

    public int getClarityScore() { return clarityScore; }
    public void setClarityScore(int clarityScore) { this.clarityScore = clarityScore; }

    public List<WordResult> getWords() { return words; }
    public void setWords(List<WordResult> words) { this.words = words; }

    public List<String> getSummary() { return summary; }
    public void setSummary(List<String> summary) { this.summary = summary; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getReferenceText() { return referenceText; }
    public void setReferenceText(String referenceText) { this.referenceText = referenceText; }
}
