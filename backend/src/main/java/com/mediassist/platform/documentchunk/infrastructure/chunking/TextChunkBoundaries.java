package com.mediassist.platform.documentchunk.infrastructure.chunking;

import java.text.BreakIterator;
import java.util.Collections;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record TextChunkBoundaries(NavigableSet<Integer> paragraphEnds, NavigableSet<Integer> sentenceEnds) {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("(?:\\r?\\n[\\t ]*){2,}|\\f");
    private static final Pattern TITLE_ABBREVIATION = Pattern.compile("(?i)\\b(?:dr|mr|mrs|ms|prof|sr|jr)\\.\\s*$");

    static TextChunkBoundaries findIn(String text) {
        NavigableSet<Integer> paragraphEnds = new TreeSet<>();
        NavigableSet<Integer> headingEnds = new TreeSet<>();
        Matcher paragraphs = PARAGRAPH_SEPARATOR.matcher(text);
        int paragraphStart = 0;
        while (paragraphs.find()) {
            String paragraph = text.substring(paragraphStart, paragraphs.start()).strip();
            if (looksLikeHeading(paragraph)) {
                headingEnds.add(paragraphs.end());
            } else {
                paragraphEnds.add(paragraphs.end());
            }
            paragraphStart = paragraphs.end();
        }

        NavigableSet<Integer> sentenceEnds = new TreeSet<>();
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(text);
        for (int end = sentences.first(); end != BreakIterator.DONE; end = sentences.next()) {
            String precedingText = text.substring(Math.max(0, end - 16), end);
            if (end == text.length() || (!headingEnds.contains(end) && !TITLE_ABBREVIATION.matcher(precedingText).find())) {
                sentenceEnds.add(end);
            }
        }
        sentenceEnds.addAll(paragraphEnds);

        return new TextChunkBoundaries(
            Collections.unmodifiableNavigableSet(paragraphEnds),
            Collections.unmodifiableNavigableSet(sentenceEnds)
        );
    }

    private static boolean looksLikeHeading(String paragraph) {
        return !paragraph.isEmpty() && paragraph.length() <= 120 && !paragraph.contains("\n")
            && !paragraph.endsWith(".") && !paragraph.endsWith("!") && !paragraph.endsWith("?");
    }
}
