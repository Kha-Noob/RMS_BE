package web.restaurant.swp.modules.review.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class ProfanityFilterService {

    private final Set<String> rawBlacklistedWords = new HashSet<>();
    private final Set<String> normalizedBlacklistedWords = new HashSet<>();
    private final File storageFile = new File("uploads/profanity_words.txt");

    @PostConstruct
    public void init() {
        loadWordsFromFile();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        // 1. Remove accents (normalize Vietnamese)
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // 2. Remove all non-alphanumeric except spaces
        normalized = normalized.replaceAll("[^a-zA-Z0-9\\s]", "");
        
        // 3. Collapse multiple spaces and trim
        return normalized.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    public synchronized void loadWordsFromFile() {
        if (!storageFile.exists()) {
            storageFile.getParentFile().mkdirs();
            try {
                // Seed with standard Vietnamese/English bad words/abbreviations as defaults
                Set<String> defaultWords = Set.of("vcl", "dm", "dkm", "cl", "cac", "fuck", "bitch", "shit");
                Files.write(storageFile.toPath(), defaultWords, StandardCharsets.UTF_8);
                log.info("Created default profanity words file");
            } catch (IOException e) {
                log.error("Failed to create default profanity words file", e);
            }
        }

        try {
            rawBlacklistedWords.clear();
            normalizedBlacklistedWords.clear();
            Files.readAllLines(storageFile.toPath(), StandardCharsets.UTF_8).forEach(line -> {
                String rawWord = line.trim();
                if (!rawWord.isEmpty()) {
                    rawBlacklistedWords.add(rawWord);
                    String normalized = normalizeText(rawWord);
                    if (!normalized.isEmpty()) {
                        normalizedBlacklistedWords.add(normalized);
                    }
                }
            });
            log.info("Loaded {} raw and {} normalized blacklisted profanity words", rawBlacklistedWords.size(), normalizedBlacklistedWords.size());
        } catch (IOException e) {
            log.error("Failed to load profanity words from file", e);
        }
    }

    public synchronized void importWords(String content) throws IOException {
        storageFile.getParentFile().mkdirs();
        Files.writeString(storageFile.toPath(), content, StandardCharsets.UTF_8);
        loadWordsFromFile();
    }

    public boolean hasProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String normalizedText = normalizeText(text);
        String strippedText = normalizedText.replaceAll("\\s+", "");

        // Split normalizedText into tokens for short word matching
        String[] tokens = normalizedText.split(" ");
        Set<String> tokenSet = new HashSet<>(java.util.Arrays.asList(tokens));

        for (String pattern : normalizedBlacklistedWords) {
            // If the pattern has spaces
            if (pattern.contains(" ")) {
                if (normalizedText.contains(pattern)) {
                    return true;
                }
                String strippedPattern = pattern.replaceAll("\\s+", "");
                if (strippedText.contains(strippedPattern)) {
                    return true;
                }
            } else {
                // If it is a short word (length <= 3), match only as a whole word token to avoid false positives (e.g. cl in client, dm in admin)
                if (pattern.length() <= 3) {
                    if (tokenSet.contains(pattern)) {
                        return true;
                    }
                } else {
                    // For longer words (length > 3), match as a substring of stripped text or normalized text
                    if (strippedText.contains(pattern)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public synchronized Set<String> getBlacklistedWords() {
        return new HashSet<>(rawBlacklistedWords);
    }
}
