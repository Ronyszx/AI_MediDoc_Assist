package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentqa.domain.DocumentEvidenceAssessment;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceRating;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DocumentEvidenceSelectionService {
    private static final int MIN_SUPPORTING_RELEVANCE = 2;
    private final DocumentEvidenceExtractor extractor;
    private final DocumentEvidenceScorer scorer;
    private final DocumentEvidenceSettings settings;

    public DocumentEvidenceSelectionService(DocumentEvidenceExtractor extractor, DocumentEvidenceScorer scorer,
                                            DocumentEvidenceSettings settings) {
        this.extractor = extractor;
        this.scorer = scorer;
        this.settings = settings;
    }

    public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages) {
        if (!settings.isBatchSelectionEnabled()) {
            return extractor.selectEvidence(question, passages);
        }
        if (passages.isEmpty()) {
            throw new DocumentEvidenceException("No evidence passages available for selection");
        }

        List<DocumentEvidenceAssessment> assessments = new ArrayList<>();
        for (int start = 0; start < passages.size(); start += settings.getSelectionBatchSize()) {
            int end = Math.min(start + settings.getSelectionBatchSize(), passages.size());
            List<DocumentEvidenceItem> batch = List.copyOf(passages.subList(start, end));
            DocumentEvidenceAssessment assessment = scorer.assessEvidence(question, batch);
            List<DocumentEvidenceRating> orderedRatings = validateAndOrderRatings(assessment, batch);
            if (!assessments.isEmpty() && !Objects.equals(assessment.modelName(), assessments.getFirst().modelName())) {
                throw new DocumentEvidenceException("Evidence model changed between selection batches");
            }
            assessments.add(new DocumentEvidenceAssessment(assessment.modelName(), orderedRatings));
        }
        return mergeAssessments(assessments);
    }

    private List<DocumentEvidenceRating> validateAndOrderRatings(DocumentEvidenceAssessment assessment,
                                                                List<DocumentEvidenceItem> batch) {
        Set<String> allowed = new HashSet<>();
        batch.forEach(item -> allowed.add(item.passageId()));
        Map<String, DocumentEvidenceRating> rated = new LinkedHashMap<>();
        if (assessment.ratings().size() != batch.size()) {
            throw new DocumentEvidenceException("Evidence ratings must cover every supplied passage");
        }
        for (DocumentEvidenceRating rating : assessment.ratings()) {
            if (!allowed.contains(rating.passageId()) || rated.putIfAbsent(rating.passageId(), rating) != null
                || rating.relevance() < 0 || rating.relevance() > 3) {
                throw new DocumentEvidenceException("Evidence rating is outside its batch, repeated or invalid");
            }
        }
        return batch.stream().map(item -> rated.get(item.passageId())).toList();
    }

    private DocumentEvidenceSelection mergeAssessments(List<DocumentEvidenceAssessment> assessments) {
        Set<String> merged = new LinkedHashSet<>();
        // Relevance grades are ordinal selection hints, not clinical confidence scores.
        for (int grade = 3; grade >= MIN_SUPPORTING_RELEVANCE; grade--) {
            int currentGrade = grade;
            var nominees = assessments.stream().map(assessment -> assessment.ratings().stream()
                .filter(rating -> rating.relevance() == currentGrade).map(DocumentEvidenceRating::passageId).toList()).toList();
            int longest = nominees.stream().mapToInt(List::size).max().orElse(0);
            for (int rank = 0; rank < longest; rank++) {
                for (List<String> batch : nominees) {
                    if (rank < batch.size()) {
                        merged.add(batch.get(rank));
                    }
                }
            }
        }
        boolean limited = merged.size() > settings.getMaxItems();
        return new DocumentEvidenceSelection(assessments.getFirst().modelName(),
            merged.stream().limit(settings.getMaxItems()).toList(), limited);
    }
}
