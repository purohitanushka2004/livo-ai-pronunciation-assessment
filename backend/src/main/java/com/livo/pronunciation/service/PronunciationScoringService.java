package com.livo.pronunciation.service;

import com.livo.pronunciation.dto.AnalysisResponse;
import com.livo.pronunciation.dto.WordResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns raw STT output (words, timestamps, per-word confidence) into a
 * pronunciation score and a list of flagged words.
 *
 * Scoring model (documented in ARCHITECTURE.md):
 *
 *  clarityScore   = average of per-word recognizer confidence, scaled 0-100.
 *                   Rationale: a speech recognizer trained on native English
 *                   struggles most on words that are mispronounced, slurred,
 *                   or acoustically unclear - low confidence is a reasonable
 *                   proxy for "a listener would also have trouble here".
 *
 *  fluencyScore   = penalizes speaking rate outside a natural conversational
 *                   band (110-170 wpm) and unnaturally long in-word pauses.
 *
 *  overallScore   = 0.7 * clarityScore + 0.3 * fluencyScore
 *
 *  Per word:
 *    confidence >= 0.80        -> "good"
 *    0.55 <= confidence < 0.80 -> "unclear"   ("said indistinctly / hard to make out")
 *    confidence < 0.55         -> "mispronounced" ("recognizer could not confidently match this to an English word")
 *
 * This is a heuristic, not a phoneme-level analysis. It is a deliberate
 * scope trade-off explained in ARCHITECTURE.md - see "Trade-offs" section.
 */
@Service
public class PronunciationScoringService {

    private static final double GOOD_THRESHOLD = 0.80;
    private static final double UNCLEAR_THRESHOLD = 0.55;

    private static final double IDEAL_MIN_WPM = 110;
    private static final double IDEAL_MAX_WPM = 170;

    public AnalysisResponse score(SpeechRecognitionService.TranscriptionResult stt, double durationSeconds) {
        return score(stt, durationSeconds, null);
    }

    /**
     * @param referenceText optional "read aloud" target sentence. When present,
     *                      recognized words are aligned against it word-by-word
     *                      so we can flag exactly which expected words were
     *                      substituted (mispronounced as a different word),
     *                      omitted (skipped entirely), or which extra words
     *                      were inserted - instead of relying purely on
     *                      recognizer confidence as a proxy. This is the
     *                      direct answer to "correctly identify which
     *                      specific words were mispronounced": confidence
     *                      alone can't tell you a word was *wrong*, only that
     *                      it was *unclear*.
     */
    public AnalysisResponse score(SpeechRecognitionService.TranscriptionResult stt, double durationSeconds,
                                   String referenceText) {
        List<SpeechRecognitionService.RecognizedWord> recognized = stt.words();

        if (referenceText != null && !referenceText.isBlank() && !recognized.isEmpty()) {
            return scoreAgainstReference(stt, durationSeconds, referenceText);
        }

        if (recognized.isEmpty()) {
            return new AnalysisResponse(
                    "",
                    0,
                    durationSeconds,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of("No speech was clearly detected in this recording. " +
                            "Try recording in a quieter environment and speaking closer to the microphone.")
            );
        }

        List<WordResult> wordResults = new ArrayList<>();
        double confidenceSum = 0;
        int flaggedCount = 0;

        for (SpeechRecognitionService.RecognizedWord w : recognized) {
            double conf = clamp(w.confidence(), 0, 1);
            confidenceSum += conf;

            String status;
            String note;
            if (conf >= GOOD_THRESHOLD) {
                status = "good";
                note = "Clear pronunciation.";
            } else if (conf >= UNCLEAR_THRESHOLD) {
                status = "unclear";
                note = "Said indistinctly - the sound was hard to make out clearly.";
                flaggedCount++;
            } else {
                status = "mispronounced";
                note = "Likely mispronounced or unclear enough that it didn't confidently match the expected English sound.";
                flaggedCount++;
            }

            int wordScore = (int) Math.round(conf * 100);
            wordResults.add(new WordResult(w.word(), w.start(), w.end(), conf, wordScore, status, note));
        }

        double clarityScore = (confidenceSum / recognized.size()) * 100.0;

        double speakingRateWpm = (recognized.size() / durationSeconds) * 60.0;
        double fluencyScore = scoreFluency(speakingRateWpm, recognized);

        int overallScore = (int) Math.round(0.7 * clarityScore + 0.3 * fluencyScore);
        overallScore = (int) clamp(overallScore, 0, 100);

        List<String> summary = buildSummary(overallScore, clarityScore, fluencyScore, speakingRateWpm, flaggedCount, recognized.size());

        return new AnalysisResponse(
                stt.transcript(),
                overallScore,
                round1(durationSeconds),
                round1(speakingRateWpm),
                (int) clamp(fluencyScore, 0, 100),
                (int) clamp(clarityScore, 0, 100),
                wordResults,
                summary
        );
    }

    private double scoreFluency(double wpm, List<SpeechRecognitionService.RecognizedWord> words) {
        double rateScore;
        if (wpm >= IDEAL_MIN_WPM && wpm <= IDEAL_MAX_WPM) {
            rateScore = 100;
        } else if (wpm < IDEAL_MIN_WPM) {
            double deficit = IDEAL_MIN_WPM - wpm;
            rateScore = Math.max(30, 100 - deficit * 1.5);
        } else {
            double excess = wpm - IDEAL_MAX_WPM;
            rateScore = Math.max(30, 100 - excess * 1.2);
        }

        // Penalize unnaturally long gaps between consecutive words (hesitation/filler pauses).
        int longPauseCount = 0;
        for (int i = 1; i < words.size(); i++) {
            double gap = words.get(i).start() - words.get(i - 1).end();
            if (gap > 0.8) {
                longPauseCount++;
            }
        }
        double pausePenalty = Math.min(30, longPauseCount * 6);

        return Math.max(0, rateScore - pausePenalty);
    }

    private List<String> buildSummary(int overallScore, double clarityScore, double fluencyScore,
                                       double wpm, int flaggedCount, int totalWords) {
        List<String> summary = new ArrayList<>();

        if (overallScore >= 85) {
            summary.add("Strong overall pronunciation - most words were recognized with high confidence.");
        } else if (overallScore >= 65) {
            summary.add("Generally understandable pronunciation with some words that could be clearer.");
        } else {
            summary.add("Several words were difficult to recognize clearly - focus on the highlighted words below.");
        }

        if (flaggedCount > 0) {
            summary.add(flaggedCount + " of " + totalWords + " word(s) were flagged as unclear or mispronounced.");
        }

        if (wpm < IDEAL_MIN_WPM) {
            summary.add(String.format("Speaking pace was slow (%.0f words/min) - aim for a more natural conversational pace.", wpm));
        } else if (wpm > IDEAL_MAX_WPM) {
            summary.add(String.format("Speaking pace was fast (%.0f words/min) - slowing down slightly may improve clarity.", wpm));
        }

        return summary;
    }

    // ---------------------------------------------------------------------
    // Reference-text ("read aloud") scoring: aligns what was actually said
    // against what the learner was asked to read, so mispronunciations are
    // identified by comparing words directly rather than only by recognizer
    // confidence.
    // ---------------------------------------------------------------------

    private static final Pattern NON_WORD = Pattern.compile("[^a-z']+");

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.toLowerCase().split("\\s+")) {
            String cleaned = NON_WORD.matcher(raw).replaceAll("");
            if (!cleaned.isBlank()) tokens.add(cleaned);
        }
        return tokens;
    }

    /** Alignment operation produced by the edit-distance backtrace. */
    private record AlignOp(String type, String expected, String said,
                            SpeechRecognitionService.RecognizedWord recognizedWord) {}

    private AnalysisResponse scoreAgainstReference(SpeechRecognitionService.TranscriptionResult stt,
                                                    double durationSeconds, String referenceText) {
        List<SpeechRecognitionService.RecognizedWord> recognized = stt.words();
        List<String> refTokens = tokenize(referenceText);
        List<String> saidTokens = new ArrayList<>();
        for (SpeechRecognitionService.RecognizedWord w : recognized) {
            saidTokens.add(NON_WORD.matcher(w.word().toLowerCase()).replaceAll(""));
        }

        List<AlignOp> ops = alignWords(refTokens, saidTokens, recognized);

        List<WordResult> wordResults = new ArrayList<>();
        double correctnessSum = 0;
        int refWordCount = 0;
        int substitutions = 0, omissions = 0, insertions = 0;

        for (AlignOp op : ops) {
            switch (op.type()) {
                case "match" -> {
                    double conf = clamp(op.recognizedWord().confidence(), 0, 1);
                    String status;
                    String note;
                    double correctness;
                    if (conf >= GOOD_THRESHOLD) {
                        status = "good"; note = "Clear and matched the expected word.";
                        correctness = 100;
                    } else if (conf >= UNCLEAR_THRESHOLD) {
                        status = "unclear"; note = "Right word, but said indistinctly.";
                        correctness = 70;
                    } else {
                        status = "unclear"; note = "Right word, but very quiet or unclear.";
                        correctness = 40;
                    }
                    int wordScore = (int) Math.round(conf * 100);
                    wordResults.add(new WordResult(op.said(), op.recognizedWord().start(),
                            op.recognizedWord().end(), conf, wordScore, status, note, null, null));
                    correctnessSum += correctness;
                    refWordCount++;
                }
                case "substitution" -> {
                    double conf = clamp(op.recognizedWord().confidence(), 0, 1);
                    int wordScore = (int) Math.round(conf * 100);
                    wordResults.add(new WordResult(op.said(), op.recognizedWord().start(),
                            op.recognizedWord().end(), conf, wordScore, "mispronounced",
                            "Expected \"" + op.expected() + "\" here, but it sounded like \"" + op.said() + "\".",
                            op.expected(), "substitution"));
                    correctnessSum += 20;
                    refWordCount++;
                    substitutions++;
                }
                case "omission" -> {
                    wordResults.add(new WordResult(op.expected(), -1, -1, 0, 0, "omitted",
                            "This word from the target sentence wasn't detected in the recording.",
                            op.expected(), "omission"));
                    correctnessSum += 0;
                    refWordCount++;
                    omissions++;
                }
                case "insertion" -> {
                    double conf = clamp(op.recognizedWord().confidence(), 0, 1);
                    int wordScore = (int) Math.round(conf * 100);
                    wordResults.add(new WordResult(op.said(), op.recognizedWord().start(),
                            op.recognizedWord().end(), conf, wordScore, "extra",
                            "This word wasn't part of the target sentence.", null, "insertion"));
                    insertions++;
                }
                default -> { }
            }
        }

        double clarityScore = refWordCount > 0 ? correctnessSum / refWordCount : 0;
        double speakingRateWpm = recognized.isEmpty() ? 0 : (recognized.size() / durationSeconds) * 60.0;
        double fluencyScore = recognized.isEmpty() ? 0 : scoreFluency(speakingRateWpm, recognized);

        int overallScore = (int) clamp(Math.round(0.7 * clarityScore + 0.3 * fluencyScore), 0, 100);
        int flaggedCount = substitutions + omissions;

        List<String> summary = new ArrayList<>();
        if (overallScore >= 85) {
            summary.add("Strong reading - almost every word matched the target sentence clearly.");
        } else if (overallScore >= 65) {
            summary.add("Mostly on target, with a few words that didn't match the script.");
        } else {
            summary.add("Several words didn't match the target sentence - see the practice list below.");
        }
        if (flaggedCount > 0) {
            summary.add(flaggedCount + " of " + refTokens.size() + " target word(s) were mispronounced or skipped"
                    + (substitutions > 0 ? " (" + substitutions + " said as a different word" : "")
                    + (substitutions > 0 && omissions > 0 ? ", " : "")
                    + (omissions > 0 ? omissions + " skipped entirely" : "")
                    + (substitutions > 0 || omissions > 0 ? ")" : "") + ".");
        }
        if (insertions > 0) {
            summary.add(insertions + " extra word(s) were said that weren't in the target sentence.");
        }
        if (speakingRateWpm > 0 && speakingRateWpm < IDEAL_MIN_WPM) {
            summary.add(String.format("Speaking pace was slow (%.0f words/min).", speakingRateWpm));
        } else if (speakingRateWpm > IDEAL_MAX_WPM) {
            summary.add(String.format("Speaking pace was fast (%.0f words/min).", speakingRateWpm));
        }

        return new AnalysisResponse(
                stt.transcript(),
                overallScore,
                round1(durationSeconds),
                round1(speakingRateWpm),
                (int) clamp(fluencyScore, 0, 100),
                (int) clamp(clarityScore, 0, 100),
                wordResults,
                summary,
                "reference",
                referenceText
        );
    }

    /**
     * Classic Wagner-Fischer edit-distance alignment between the reference
     * script and what the recognizer heard, at word granularity (substitution
     * cost 1, insertion cost 1, deletion cost 1). The backtrace gives us,
     * for every expected word, whether it was matched, substituted for a
     * different word, or skipped outright - plus any extra words the
     * speaker added.
     */
    private List<AlignOp> alignWords(List<String> ref, List<String> said,
                                      List<SpeechRecognitionService.RecognizedWord> recognizedWords) {
        int n = ref.size();
        int m = said.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (ref.get(i - 1).equals(said.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        List<AlignOp> ops = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref.get(i - 1).equals(said.get(j - 1))) {
                ops.add(new AlignOp("match", ref.get(i - 1), said.get(j - 1), recognizedWords.get(j - 1)));
                i--; j--;
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                ops.add(new AlignOp("substitution", ref.get(i - 1), said.get(j - 1), recognizedWords.get(j - 1)));
                i--; j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                ops.add(new AlignOp("omission", ref.get(i - 1), null, null));
                i--;
            } else {
                ops.add(new AlignOp("insertion", null, said.get(j - 1), recognizedWords.get(j - 1)));
                j--;
            }
        }
        java.util.Collections.reverse(ops);
        return ops;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
