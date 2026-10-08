package com.mediassist.platform.documentchunk.infrastructure.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultTextChunkingServiceTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\n\t"})
    void shouldReturnNoChunksForMissingText(String text) {
        assertThat(chunker(240, 60).splitIntoChunks(text)).isEmpty();
    }

    @Test
    void shouldKeepShortTextAndItsInternalFormatting() {
        String text = "Record A - 11 June 2026\nA condition was recorded.\n\nA review was planned.";

        assertThat(chunker(240, 60).splitIntoChunks("  " + text + "\n"))
            .containsExactly(text);
    }

    @Test
    void shouldPreferACompleteParagraphOverFillingTheChunkWithPartOfTheNextParagraph() {
        String first = sentence("First", 10) + " " + sentence("Second", 10);
        String second = sentence("Third", 10) + " " + sentence("Fourth", 10);

        assertThat(chunker(240, 0).splitIntoChunks(first + "\n\n" + second))
            .containsExactly(first, second);
    }

    @Test
    void shouldNotSplitAParagraphThatFitsAloneJustToFillThePreviousChunk() {
        String first = sentence("Small", 30);
        String second = sentence("Next", 85) + " " + sentence("End", 85);

        assertThat(chunker(1000, 0).splitIntoChunks(first + "\n\n" + second))
            .containsExactly(first, second);
    }

    @Test
    void shouldSplitAtSentencesWhenNoParagraphBreakIsAvailable() {
        String first = sentence("First", 17);
        String second = sentence("Second", 17);
        String third = sentence("Third", 17);

        assertThat(chunker(240, 0).splitIntoChunks(first + " " + second + " " + third))
            .containsExactly(first + " " + second, third);
    }

    @Test
    void shouldNotTreatEveryWrappedPdfLineAsAParagraph() {
        String first = "First " + "word ".repeat(18) + "\n" + "word ".repeat(12) + "recorded.";
        String second = sentence("Second", 17);

        assertThat(chunker(240, 0).splitIntoChunks(first + " " + second))
            .containsExactly(first, second);
    }

    @Test
    void shouldKeepAnUnpunctuatedHeadingWithItsOpeningSentenceWhenTheyFit() {
        String first = sentence("Previous", 28);
        String next = "Record A - Clinician letter, 11 June 2026\n" + sentence("Assessment", 12);

        assertThat(chunker(240, 0).splitIntoChunks(first + "\n\n" + next))
            .containsExactly(first, next);
    }

    @Test
    void shouldKeepASeparateHeadingParagraphWithItsOpeningText() {
        String first = sentence("Previous", 28);
        String next = "Record A - Clinician letter, 11 June 2026\n\n" + sentence("Assessment", 12);

        assertThat(chunker(240, 0).splitIntoChunks(first + "\n\n" + next))
            .containsExactly(first, next);
    }

    @Test
    void shouldNotSeparateACommonDoctorTitleFromItsName() {
        String first = sentence("Previous", 28);
        String next = "Dr. Adams " + "reviewed ".repeat(12) + "the records.";

        assertThat(chunker(240, 0).splitIntoChunks(first + " " + next))
            .containsExactly(first, next);
    }

    @Test
    void shouldOverlapAWholeSentenceRatherThanStartInItsMiddle() {
        String first = sentence("First", 17);
        String second = sentence("Second", 17);
        String third = sentence("Third", 17);
        String fourth = sentence("Fourth", 17);

        assertThat(chunker(240, 60).splitIntoChunks(String.join(" ", first, second, third, fourth)))
            .containsExactly(first + " " + second, second + " " + third, third + " " + fourth);
    }

    @Test
    void shouldDropOverlapRatherThanSplitANewSentenceThatFitsByItself() {
        String first = sentence("First", 17);
        String second = sentence("Second", 17);
        String third = sentence("Third", 39);

        assertThat(chunker(240, 60).splitIntoChunks(first + " " + second + " " + third))
            .containsExactly(first + " " + second, third);
    }

    @Test
    void shouldFallBackToWholeWordsWhenASentenceExceedsTheLimit() {
        String text = IntStream.range(0, 90).mapToObj(i -> "word" + i).collect(Collectors.joining(" ")) + ".";

        List<String> chunks = chunker(150, 0).splitIntoChunks(text);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(150));
        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }

    @Test
    void shouldAlignWordOverlapWhenSplittingAnOversizedSentence() {
        String text = IntStream.range(0, 90).mapToObj(i -> "word" + i).collect(Collectors.joining(" ")) + ".";

        List<String> chunks = chunker(150, 30).splitIntoChunks(text);

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.length()).isLessThanOrEqualTo(150);
            assertThat(chunk).matches("word\\d+.*");
        });
        for (String word : text.split(" ")) {
            assertThat(chunks).anySatisfy(chunk -> assertThat(List.of(chunk.split(" "))).contains(word));
        }
        assertThat(chunks).hasSizeLessThan(10);
    }

    @Test
    void shouldSplitAnOversizedTokenWithoutLosingCharacters() {
        String text = "x".repeat(355);
        List<String> chunks = chunker(100, 0).splitIntoChunks(text);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
        assertThat(String.join("", chunks)).isEqualTo(text);
    }

    @Test
    void shouldNotSplitAUtf16SurrogatePairAtTheHardLimit() {
        String text = "x".repeat(100) + "\uD83D\uDE00" + "y".repeat(110);
        List<String> chunks = chunker(101, 0).splitIntoChunks(text);

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.length()).isLessThanOrEqualTo(101);
            assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
            assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
        });
        assertThat(String.join("", chunks)).isEqualTo(text);
    }

    @Test
    @Timeout(2)
    void shouldKeepMakingProgressEvenWithAnOverlapNearTheChunkSize() {
        List<String> sentences = IntStream.range(0, 30).mapToObj(i -> sentence("Record" + i, 10)).toList();

        List<String> chunks = chunker(150, 149).splitIntoChunks(String.join(" ", sentences));

        assertThat(chunks).hasSizeLessThanOrEqualTo(sentences.size());
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(150));
        for (String sentence : sentences) {
            assertThat(chunks).anySatisfy(chunk -> assertThat(chunk).contains(sentence));
        }
    }

    @Test
    void shouldHandleWindowsParagraphSeparatorsAndPageBreaks() {
        String first = sentence("First", 10) + " " + sentence("Second", 10);
        String second = sentence("Third", 10) + " " + sentence("Fourth", 10);
        String third = sentence("Fifth", 10) + " " + sentence("Sixth", 10);

        assertThat(chunker(240, 0).splitIntoChunks(first + "\r\n\t\r\n" + second + "\f" + third))
            .containsExactly(first, second, third);
    }

    @Test
    @Timeout(10)
    void shouldCoverTheEntireTextInOrderAcrossDifferentSizesAndOverlaps() {
        Random random = new Random(42);
        StringBuilder source = new StringBuilder();
        int wordIndex = 0;
        for (int sentence = 0; sentence < 50; sentence++) {
            source.append("Record").append(sentence).append(' ');
            int words = 1 + random.nextInt(80);
            for (int word = 0; word < words; word++) {
                source.append("word").append(wordIndex++).append(' ');
            }
            source.append("recorded").append(sentence).append('.').append(sentence % 3 == 0 ? "\n\n" : " ");
        }
        String text = source.toString().strip();

        for (int size : new int[]{100, 240, 1000}) {
            for (int overlap : new int[]{0, size / 4, size - 1}) {
                List<String> chunks = chunker(size, overlap).splitIntoChunks(text);
                int coveredEnd = 0;
                for (String chunk : chunks) {
                    assertThat(chunk.length()).isLessThanOrEqualTo(size);
                    int start = text.indexOf(chunk);
                    assertThat(start).isNotNegative();
                    if (start > coveredEnd) {
                        assertThat(text.substring(coveredEnd, start)).isBlank();
                    }
                    int end = start + chunk.length();
                    assertThat(end).as("New coverage for size %s, overlap %s, chunk %s", size, overlap, chunk)
                        .isGreaterThan(coveredEnd);
                    coveredEnd = end;
                }
                assertThat(text.substring(coveredEnd)).isBlank();
            }
        }
    }

    private DefaultTextChunkingService chunker(int size, int overlap) {
        DocumentChunkingProperties properties = new DocumentChunkingProperties();
        properties.setMaxChunkSize(size);
        properties.setOverlapSize(overlap);
        return new DefaultTextChunkingService(properties);
    }

    private String sentence(String label, int words) {
        return label + " " + "word ".repeat(words) + "recorded.";
    }
}
