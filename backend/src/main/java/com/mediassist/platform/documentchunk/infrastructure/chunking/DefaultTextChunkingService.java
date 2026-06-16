package com.mediassist.platform.documentchunk.infrastructure.chunking;

import com.mediassist.platform.documentchunk.application.TextChunkingService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultTextChunkingService implements TextChunkingService {

    private final DocumentChunkingProperties properties;

    public DefaultTextChunkingService(DocumentChunkingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<String> splitIntoChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = text.strip();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalizedText.length()) {
            int targetEnd = Math.min(start + properties.getMaxChunkSize(), normalizedText.length());
            int end = findChunkEnd(normalizedText, start, targetEnd);
            String chunk = normalizedText.substring(start, end).strip();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= normalizedText.length()) {
                break;
            }

            start = nextChunkStart(normalizedText, start, end);
        }

        return chunks;
    }

    private int findChunkEnd(String text, int start, int targetEnd) {
        if (targetEnd >= text.length()) {
            return text.length();
        }

        int minimumEnd = start + Math.max(1, properties.getMaxChunkSize() / 2);
        for (int index = targetEnd; index > minimumEnd; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }

        return targetEnd;
    }

    private int nextChunkStart(String text, int previousStart, int previousEnd) {
        int nextStart = Math.max(0, previousEnd - properties.getOverlapSize());
        while (nextStart < text.length() && Character.isWhitespace(text.charAt(nextStart))) {
            nextStart++;
        }

        if (nextStart <= previousStart) {
            return previousEnd;
        }

        return nextStart;
    }
}
