package com.interviewcopilot.business.interview.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interviewcopilot.business.interview.application.InterviewApplicationService;
import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.domain.ReportStatus;
import com.interviewcopilot.business.interview.repository.InterviewSessionPage;
import com.interviewcopilot.business.security.CurrentUserPrincipal;
import com.interviewcopilot.business.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewController {
    private final InterviewApplicationService interviews;

    public InterviewController(InterviewApplicationService interviews) {
        this.interviews = interviews;
    }

    @PostMapping
    public ResponseEntity<InterviewResponse> create(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @Valid @RequestBody CreateInterviewRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        InterviewApplicationService.CreateResult result = interviews.create(
                principal.id(),
                new InterviewApplicationService.CreateCommand(
                        request.targetPosition(), request.skills(), request.difficulty(), request.questionCount()),
                idempotencyKey);
        return ResponseEntity.status(result.replay() ? 200 : 201)
                .body(new InterviewResponse(toInterview(result.session()), RequestIds.resolve(servletRequest), Instant.now()));
    }

    @GetMapping
    public InterviewPageResponse list(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) InterviewStatus status,
            HttpServletRequest servletRequest
    ) {
        InterviewSessionPage result = interviews.list(principal.id(), Optional.ofNullable(status), page, pageSize);
        List<InterviewListItem> items = result.content().stream().map(InterviewController::toListItem).toList();
        return new InterviewPageResponse(
                new InterviewPage(items, result.page() + 1, result.size(), result.totalElements()),
                RequestIds.resolve(servletRequest), Instant.now());
    }

    @GetMapping("/{interviewId}")
    public InterviewDetailResponse get(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            HttpServletRequest servletRequest
    ) {
        return new InterviewDetailResponse(
                toDetail(interviews.get(principal.id(), interviewId)),
                RequestIds.resolve(servletRequest), Instant.now());
    }

    @PostMapping("/{interviewId}/start")
    public ResponseEntity<StartInterviewResponse> start(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        InterviewApplicationService.StartResult result = interviews.start(
                principal.id(), interviewId, idempotencyKey, requestId);
        InterviewQuestion question = result.question();
        return ResponseEntity.ok(new StartInterviewResponse(
                new StartInterviewData(
                        new StartedInterview(result.session().id(), InterviewStatus.IN_PROGRESS, question.number()),
                        new PublicQuestion(question.id(), question.number(), question.text(), question.topic(),
                                question.difficulty(), question.type())),
                requestId, Instant.now()));
    }

    @PostMapping("/{interviewId}/answers")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        InterviewApplicationService.SubmitResult result = interviews.submitAnswer(principal.id(), interviewId,
                new InterviewApplicationService.SubmitCommand(request.questionId(), request.answerContent()), idempotencyKey, requestId);
        InterviewSession session = result.session();
        InterviewQuestion answered = result.answeredQuestion();
        PublicEvaluation evaluation = answered.answer().orElseThrow().evaluation().map(InterviewController::toEvaluation).orElseThrow();
        SubmitAnswerData data = session.status() == InterviewStatus.COMPLETED
                ? new CompletedAnswerResult(new AnswerReceipt(answered.answer().orElseThrow().id(), answered.id(), answered.answer().orElseThrow().submittedAt()), evaluation, null, session.status(), ReportStatus.PENDING)
                : new InProgressAnswerResult(new AnswerReceipt(answered.answer().orElseThrow().id(), answered.id(), answered.answer().orElseThrow().submittedAt()), evaluation,
                session.currentQuestion().map(InterviewController::toPublicQuestion).orElseThrow(), session.status());
        return ResponseEntity.status(result.replay() ? 200 : 201).body(new SubmitAnswerResponse(data, requestId, Instant.now()));
    }

    @GetMapping("/{interviewId}/current-question")
    public CurrentQuestionResponse currentQuestion(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        InterviewSession session = interviews.currentQuestion(principal.id(), interviewId);
        CurrentQuestionData data;
        if (session.status() == InterviewStatus.COMPLETED) {
            data = new CompletedCurrentQuestionData(session.id(), session.status(), null,
                    session.configuration().questionCount(), null, null);
        } else {
            InterviewQuestion question = session.currentQuestion().orElseThrow();
            data = new InProgressCurrentQuestionData(session.id(), session.status(), question.number(),
                    session.configuration().questionCount(), toCurrentQuestion(question), questionProgressStatus(question));
        }
        return new CurrentQuestionResponse(data, requestId, Instant.now());
    }

    @PostMapping("/{interviewId}/cancel")
    public ResponseEntity<CancelInterviewResponse> cancel(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        InterviewApplicationService.CancelResult result = interviews.cancel(principal.id(), interviewId, idempotencyKey);
        InterviewSession session = result.session();
        return ResponseEntity.ok(new CancelInterviewResponse(
                new CancelInterviewData(session.id(), session.status(), session.cancelledAt().orElseThrow()),
                requestId, Instant.now()));
    }

    @GetMapping("/{interviewId}/report")
    public ReportResponse report(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        return new ReportResponse(toReport(interviews.getReport(principal.id(), interviewId)), requestId, Instant.now());
    }

    @PostMapping("/{interviewId}/report/retry")
    public ResponseEntity<ReportResponse> retryReport(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        String requestId = RequestIds.resolve(servletRequest);
        InterviewApplicationService.ReportResult result = interviews.retryReport(
                principal.id(), interviewId, idempotencyKey, requestId);
        return ResponseEntity.ok(new ReportResponse(toReport(result.report()), requestId, Instant.now()));
    }

    private static PublicEvaluation toEvaluation(com.interviewcopilot.business.interview.domain.AnswerEvaluation e) {
        return new PublicEvaluation(e.accuracy(), e.completeness(), e.depth(), e.clarity(), e.answerOverallScore(), e.strengths(), e.missingPoints(), e.feedback());
    }

    private static PublicQuestion toPublicQuestion(InterviewQuestion q) {
        return new PublicQuestion(q.id(), q.number(), q.text(), q.topic(), q.difficulty(), q.type());
    }

    private static CurrentQuestion toCurrentQuestion(InterviewQuestion q) {
        return new CurrentQuestion(q.id(), q.text(), q.topic(), q.difficulty(), q.type());
    }

    private static QuestionProgressStatus questionProgressStatus(InterviewQuestion q) {
        return q.isEvaluated() ? QuestionProgressStatus.EVALUATED
                : q.answer().isPresent() ? QuestionProgressStatus.EVALUATION_PENDING
                : QuestionProgressStatus.WAITING_FOR_ANSWER;
    }

    private static ReportData toReport(InterviewReport report) {
        var metrics = report.metrics();
        var summary = report.summary().orElse(null);
        return new ReportData(report.sessionId(), report.status(), metrics.interviewOverallScore(), metrics.questionCount(),
                metrics.completedQuestionCount(), metrics.averageAccuracy(), metrics.averageCompleteness(), metrics.averageDepth(),
                metrics.averageClarity(), metrics.durationSeconds(), summary == null ? null : summary.strengthSummary(),
                summary == null ? null : summary.weaknessSummary(), summary == null ? null : summary.improvementSuggestions(),
                summary == null ? null : summary.overallComment(), report.publishedAt().orElse(null));
    }

    private static InterviewView toInterview(InterviewSession session) {
        return new InterviewView(session.id(), session.configuration().targetPosition(), session.configuration().skills(),
                session.configuration().difficulty(), session.configuration().questionCount(), session.status(),
                session.currentQuestionNumber().orElse(null), session.interviewOverallScore().orElse(null), session.createdAt());
    }

    private static InterviewListItem toListItem(InterviewSession session) {
        return new InterviewListItem(session.id(), session.configuration().targetPosition(), session.configuration().difficulty(),
                session.configuration().questionCount(), session.status(), session.interviewOverallScore().orElse(null),
                session.createdAt(), session.completedAt().orElse(null));
    }

    private static InterviewDetail toDetail(InterviewSession session) {
        int completed = (int) session.questions().stream().filter(InterviewQuestion::isEvaluated).count();
        List<QuestionProgress> questions = session.questions().stream()
                .map(question -> new QuestionProgress(question.id(), question.number(),
                        question.isEvaluated() ? QuestionProgressStatus.EVALUATED
                                : question.answer().isPresent() ? QuestionProgressStatus.EVALUATION_PENDING
                                : QuestionProgressStatus.WAITING_FOR_ANSWER))
                .toList();
        return new InterviewDetail(session.id(), session.configuration().targetPosition(), session.configuration().skills(),
                session.configuration().difficulty(), session.configuration().questionCount(), session.status(),
                session.currentQuestionNumber().orElse(null), session.interviewOverallScore().orElse(null),
                new InterviewProgress(completed, session.configuration().questionCount()), questions, session.createdAt(),
                session.startedAt().orElse(null), session.completedAt().orElse(null));
    }

    public record CreateInterviewRequest(
            @JsonProperty("target_position") @NotBlank @Size(max = 100) String targetPosition,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 50) String> skills,
            com.interviewcopilot.business.interview.domain.Difficulty difficulty,
            @JsonProperty("question_count") Integer questionCount
    ) {
    }

    public record InterviewResponse(InterviewView data, @JsonProperty("request_id") String requestId, Instant timestamp) {
    }

    public record InterviewView(UUID id, @JsonProperty("target_position") String targetPosition, List<String> skills,
                                com.interviewcopilot.business.interview.domain.Difficulty difficulty,
                                @JsonProperty("question_count") int questionCount, InterviewStatus status,
                                @JsonProperty("current_question_number") Integer currentQuestionNumber,
                                @JsonProperty("interview_overall_score") BigDecimal interviewOverallScore,
                                @JsonProperty("created_at") Instant createdAt) {
    }

    public record InterviewListItem(UUID id, @JsonProperty("target_position") String targetPosition,
                                    com.interviewcopilot.business.interview.domain.Difficulty difficulty,
                                    @JsonProperty("question_count") int questionCount, InterviewStatus status,
                                    @JsonProperty("interview_overall_score") BigDecimal interviewOverallScore,
                                    @JsonProperty("created_at") Instant createdAt,
                                    @JsonProperty("completed_at") Instant completedAt) {
    }

    public record InterviewPage(List<InterviewListItem> items, int page, @JsonProperty("page_size") int pageSize, long total) {
    }

    public record InterviewPageResponse(InterviewPage data, @JsonProperty("request_id") String requestId, Instant timestamp) {
    }

    public record InterviewProgress(@JsonProperty("completed_question_count") int completedQuestionCount,
                                    @JsonProperty("total_question_count") int totalQuestionCount) {
    }

    public enum QuestionProgressStatus { WAITING_FOR_ANSWER, EVALUATION_PENDING, EVALUATED }

    public record QuestionProgress(UUID id, @JsonProperty("question_number") int questionNumber,
                                   QuestionProgressStatus status) {
    }

    public record InterviewDetail(UUID id, @JsonProperty("target_position") String targetPosition, List<String> skills,
                                  com.interviewcopilot.business.interview.domain.Difficulty difficulty,
                                  @JsonProperty("question_count") int questionCount, InterviewStatus status,
                                  @JsonProperty("current_question_number") Integer currentQuestionNumber,
                                  @JsonProperty("interview_overall_score") BigDecimal interviewOverallScore,
                                  InterviewProgress progress, List<QuestionProgress> questions,
                                  @JsonProperty("created_at") Instant createdAt, @JsonProperty("started_at") Instant startedAt,
                                  @JsonProperty("completed_at") Instant completedAt) {
    }

    public record InterviewDetailResponse(InterviewDetail data, @JsonProperty("request_id") String requestId, Instant timestamp) {
    }

    public record StartedInterview(UUID id, InterviewStatus status,
                                   @JsonProperty("current_question_number") int currentQuestionNumber) {
    }

    public record PublicQuestion(UUID id, @JsonProperty("question_number") int questionNumber, String question,
                                 String topic, Difficulty difficulty,
                                 @JsonProperty("question_type") com.interviewcopilot.business.interview.domain.QuestionType questionType) {
    }

    public record StartInterviewData(StartedInterview interview,
                                     @JsonProperty("current_question") PublicQuestion currentQuestion) {
    }

    public record StartInterviewResponse(StartInterviewData data, @JsonProperty("request_id") String requestId,
                                         Instant timestamp) {
    }

    public record SubmitAnswerRequest(@JsonProperty("question_id") UUID questionId,
                                      @JsonProperty("answer_content") @NotBlank @Size(max = 8000) String answerContent) {}
    public record AnswerReceipt(UUID id, @JsonProperty("question_id") UUID questionId,
                                @JsonProperty("submitted_at") Instant submittedAt) {}
    public record PublicEvaluation(BigDecimal accuracy, BigDecimal completeness, BigDecimal depth, BigDecimal clarity,
                                   @JsonProperty("answer_overall_score") BigDecimal answerOverallScore,
                                   List<String> strengths, @JsonProperty("missing_points") List<String> missingPoints, String feedback) {}
    public sealed interface SubmitAnswerData permits InProgressAnswerResult, CompletedAnswerResult {}
    public record InProgressAnswerResult(AnswerReceipt answer, PublicEvaluation evaluation,
                                          @JsonProperty("next_question") PublicQuestion nextQuestion,
                                          @JsonProperty("interview_status") InterviewStatus interviewStatus) implements SubmitAnswerData {}
    public record CompletedAnswerResult(AnswerReceipt answer, PublicEvaluation evaluation,
                                        @JsonProperty("next_question") PublicQuestion nextQuestion,
                                        @JsonProperty("interview_status") InterviewStatus interviewStatus,
                                        @JsonProperty("report_status") ReportStatus reportStatus) implements SubmitAnswerData {}
    public record SubmitAnswerResponse(SubmitAnswerData data, @JsonProperty("request_id") String requestId, Instant timestamp) {}

    public sealed interface CurrentQuestionData permits InProgressCurrentQuestionData, CompletedCurrentQuestionData {}
    public record InProgressCurrentQuestionData(UUID interviewId, @JsonProperty("interview_status") InterviewStatus interviewStatus,
                                                @JsonProperty("question_number") int questionNumber,
                                                @JsonProperty("total_question_count") int totalQuestionCount,
                                                CurrentQuestion question,
                                                @JsonProperty("answer_status") QuestionProgressStatus answerStatus) implements CurrentQuestionData {}
    public record CompletedCurrentQuestionData(UUID interviewId, @JsonProperty("interview_status") InterviewStatus interviewStatus,
                                               @JsonProperty("question_number") Integer questionNumber,
                                               @JsonProperty("total_question_count") int totalQuestionCount,
                                               CurrentQuestion question,
                                               @JsonProperty("answer_status") QuestionProgressStatus answerStatus) implements CurrentQuestionData {}
    public record CurrentQuestion(UUID id, String question, String topic, Difficulty difficulty,
                                  @JsonProperty("question_type") com.interviewcopilot.business.interview.domain.QuestionType questionType) {}
    public record CurrentQuestionResponse(CurrentQuestionData data, @JsonProperty("request_id") String requestId, Instant timestamp) {}
    public record CancelInterviewData(UUID id, InterviewStatus status, @JsonProperty("cancelled_at") Instant cancelledAt) {}
    public record CancelInterviewResponse(CancelInterviewData data, @JsonProperty("request_id") String requestId, Instant timestamp) {}
    public record ReportData(@JsonProperty("interview_id") UUID interviewId,
                             @JsonProperty("report_status") ReportStatus reportStatus,
                             @JsonProperty("interview_overall_score") BigDecimal interviewOverallScore,
                             @JsonProperty("question_count") int questionCount,
                             @JsonProperty("completed_question_count") int completedQuestionCount,
                             @JsonProperty("average_accuracy") BigDecimal averageAccuracy,
                             @JsonProperty("average_completeness") BigDecimal averageCompleteness,
                             @JsonProperty("average_depth") BigDecimal averageDepth,
                             @JsonProperty("average_clarity") BigDecimal averageClarity,
                             @JsonProperty("duration_seconds") long durationSeconds,
                             @JsonProperty("strength_summary") String strengthSummary,
                             @JsonProperty("weakness_summary") String weaknessSummary,
                             @JsonProperty("improvement_suggestions") List<String> improvementSuggestions,
                             @JsonProperty("overall_comment") String overallComment,
                             @JsonProperty("published_at") Instant publishedAt) {}
    public record ReportResponse(ReportData data, @JsonProperty("request_id") String requestId, Instant timestamp) {}
}
