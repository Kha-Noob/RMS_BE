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

    private final Set<String> blacklistedWords = new HashSet<>();
    private final File storageFile = new File("uploads/profanity_words.txt");

    @PostConstruct
    public void init() {
        loadWordsFromFile();
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
            blacklistedWords.clear();
            Files.readAllLines(storageFile.toPath(), StandardCharsets.UTF_8).forEach(line -> {
                String word = line.trim().toLowerCase();
                if (!word.isEmpty()) {
                    blacklistedWords.add(word);
                }
            });
            log.info("Loaded {} blacklisted profanity words", blacklistedWords.size());
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
        String normalizedText = text.toLowerCase();
        for (String word : blacklistedWords) {
            if (normalizedText.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Set<String> getBlacklistedWords() {
        return new HashSet<>(blacklistedWords);
    }
}
