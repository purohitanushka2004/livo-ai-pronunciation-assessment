package com.livo.pronunciation.dto;

public class WordResult {

    private String word;
    private double startTime;
    private double endTime;
    private double confidence;   // 0.0 - 1.0, raw recognizer confidence
    private int wordScore;       // 0 - 100, normalized pronunciation score
    private String status;       // "good" | "unclear" | "mispronounced" | "omitted" | "extra"
    private String note;         // human-readable explanation
    private String expectedWord; // reference-mode only: the word the script expected here
    private String errorType;    // reference-mode only: "substitution" | "omission" | "insertion"

    public WordResult() {}

    public WordResult(String word, double startTime, double endTime, double confidence,
                       int wordScore, String status, String note) {
        this(word, startTime, endTime, confidence, wordScore, status, note, null, null);
    }

    public WordResult(String word, double startTime, double endTime, double confidence,
                       int wordScore, String status, String note, String expectedWord, String errorType) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
        this.confidence = confidence;
        this.wordScore = wordScore;
        this.status = status;
        this.note = note;
        this.expectedWord = expectedWord;
        this.errorType = errorType;
    }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public double getStartTime() { return startTime; }
    public void setStartTime(double startTime) { this.startTime = startTime; }

    public double getEndTime() { return endTime; }
    public void setEndTime(double endTime) { this.endTime = endTime; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public int getWordScore() { return wordScore; }
    public void setWordScore(int wordScore) { this.wordScore = wordScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getExpectedWord() { return expectedWord; }
    public void setExpectedWord(String expectedWord) { this.expectedWord = expectedWord; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
}
