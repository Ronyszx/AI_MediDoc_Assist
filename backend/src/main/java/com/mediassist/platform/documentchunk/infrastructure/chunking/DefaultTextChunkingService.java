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
        TextChunkBoundaries boundaries = TextChunkBoundaries.findIn(normalizedText);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int previousEnd = 0;

        while (start < normalizedText.length()) {
            int targetEnd = Math.min(start + properties.getMaxChunkSize(), normalizedText.length());
            int end = findChunkEnd(normalizedText, boundaries, start, targetEnd, previousEnd);
            String chunk = normalizedText.substring(start, end).strip();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= normalizedText.length()) {
                break;
            }

            start = nextChunkStart(normalizedText, boundaries, start, end);
            previousEnd = end;
        }

        return chunks;
    }

    private int findChunkEnd(String text, TextChunkBoundaries boundaries, int start, int targetEnd, int previousEnd) {
        if (targetEnd >= text.length()) {
            return text.length();
        }

        int newContentStart = skipWhitespace(text, previousEnd);
        Integer paragraphEnd = boundaries.paragraphEnds().floor(targetEnd);
        if (paragraphEnd != null && paragraphEnd > start && paragraphEnd > newContentStart) {
            return paragraphEnd;
        }

        Integer sentenceEnd = boundaries.sentenceEnds().floor(targetEnd);
        if (sentenceEnd != null && sentenceEnd > start && sentenceEnd > newContentStart) {
            return sentenceEnd;
        }

        // A sentence longer than the size limit must fall back to a word boundary.
        for (int index = targetEnd; index > Math.max(start, newContentStart); index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }

        if (Character.isHighSurrogate(text.charAt(targetEnd - 1)) && Character.isLowSurrogate(text.charAt(targetEnd))) {
            return targetEnd - 1;
        }

        return targetEnd;
    }

    private int nextChunkStart(String text, TextChunkBoundaries boundaries, int previousStart, int previousEnd) {
        if (properties.getOverlapSize() == 0) {
            return skipWhitespace(text, previousEnd);
        }

        int desiredStart = Math.max(previousStart + 1, previousEnd - properties.getOverlapSize());
        int nextStart;
        if (boundaries.sentenceEnds().contains(previousEnd) || boundaries.paragraphEnds().contains(previousEnd)) {
            nextStart = findSentenceOverlapStart(boundaries, previousStart, previousEnd, desiredStart);
            Integer nextSentenceEnd = boundaries.sentenceEnds().higher(previousEnd);
            if (nextSentenceEnd != null && nextSentenceEnd - nextStart > properties.getMaxChunkSize()
                && nextSentenceEnd - previousEnd <= properties.getMaxChunkSize()) {
                nextStart = previousEnd;
            }
        } else {
            nextStart = findWordOverlapStart(text, desiredStart, previousEnd);
        }

        if (!hasRoomForNextWord(text, nextStart, previousEnd)) {
            nextStart = previousEnd;
        }

        return skipWhitespace(text, nextStart);
    }

    private int findSentenceOverlapStart(TextChunkBoundaries boundaries, int previousStart, int previousEnd, int desiredStart) {
        int overlapBudget = Math.max(properties.getOverlapSize(), properties.getMaxChunkSize() / 2);
        Integer before = boundaries.sentenceEnds().floor(desiredStart);
        if (before != null && before > previousStart && previousEnd - before <= overlapBudget) {
            return before;
        }

        Integer after = boundaries.sentenceEnds().ceiling(desiredStart);
        return after != null && after < previousEnd ? after : previousEnd;
    }

    private int findWordOverlapStart(String text, int desiredStart, int previousEnd) {
        int start = desiredStart;
        if (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            while (start < previousEnd && !Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return start;
    }

    private boolean hasRoomForNextWord(String text, int start, int previousEnd) {
        int limit = Math.min(text.length(), start + properties.getMaxChunkSize());
        int wordEnd = skipWhitespace(text, previousEnd);
        while (wordEnd < text.length() && wordEnd <= limit && !Character.isWhitespace(text.charAt(wordEnd))) {
            wordEnd++;
        }
        return wordEnd <= limit;
    }

    private int skipWhitespace(String text, int start) {
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }
}
