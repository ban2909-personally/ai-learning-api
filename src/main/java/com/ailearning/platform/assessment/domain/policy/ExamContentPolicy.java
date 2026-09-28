package com.ailearning.platform.assessment.domain.policy;

import com.ailearning.platform.assessment.domain.enumtype.PracticeSkill;
import com.ailearning.platform.assessment.domain.enumtype.QuestionKind;
import com.ailearning.platform.assessment.domain.model.PracticeExam;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.Locale;

public final class ExamContentPolicy {
    private ExamContentPolicy() {}

    public static void validate(PracticeExam exam, boolean readyForPublication) {
        text(exam.title(), 180, true);
        text(exam.description(), 1000, false);
        require(exam.description() != null, "Mô tả không được null.");
        require(
                exam.durationMinutes() >= 1 && exam.durationMinutes() <= 240,
                "Thời lượng đề phải từ 1 đến 240 phút.");
        require(exam.sections().size() <= 20, "Đề không được có quá 20 phần.");
        require(
                exam.sections().stream().mapToInt(s -> s.questions().size()).sum() <= 250,
                "Đề không được có quá 250 câu hỏi.");
        if (readyForPublication) require(!exam.sections().isEmpty(), "Đề cần ít nhất một phần.");
        exam.sections().forEach(section -> validateSection(section, readyForPublication));
    }

    private static void validateSection(PracticeExam.Section section, boolean ready) {
        PracticeSkill skill = PracticeSkill.valueOf(section.skill());
        text(section.title(), 180, ready);
        require(section.title() != null, "Tiêu đề phần không được null.");
        text(section.passage(), 20000, false);
        text(section.audioText(), 20000, ready && skill == PracticeSkill.LISTENING);
        if (ready) require(!section.questions().isEmpty(), "Mỗi phần cần ít nhất một câu hỏi.");
        section.questions().forEach(question -> validateQuestion(skill, question, ready));
    }

    private static void validateQuestion(
            PracticeSkill skill, PracticeExam.Question question, boolean ready) {
        QuestionKind kind = QuestionKind.valueOf(question.kind());
        require(
                (skill == PracticeSkill.WRITING) == (kind == QuestionKind.WRITING),
                "Loại câu hỏi không phù hợp với kỹ năng.");
        text(question.prompt(), 10000, ready);
        require(
                question.prompt() != null && question.explanation() != null,
                "Câu hỏi và giải thích không được null.");
        text(question.explanation(), 5000, ready && kind != QuestionKind.WRITING);
        require(question.options().size() <= 10, "Mỗi câu hỏi có tối đa 10 lựa chọn.");
        question.options()
                .forEach(option -> text(option, 500, ready && kind == QuestionKind.CHOICE));
        if (kind == QuestionKind.WRITING) {
            require(
                    question.correctAnswer() == null && question.options().isEmpty(),
                    "Câu Writing không được có đáp án trắc nghiệm.");
            return;
        }
        text(question.correctAnswer(), 500, ready);
        if (kind == QuestionKind.TEXT) {
            require(question.options().isEmpty(), "Câu điền đáp án không được có lựa chọn.");
        } else if (ready) {
            long distinctOptions =
                    question.options().stream()
                            .map(ExamContentPolicy::normalizeOption)
                            .distinct()
                            .count();
            require(
                    question.options().size() >= 2
                            && distinctOptions == question.options().size()
                            && question.options().contains(question.correctAnswer()),
                    "Câu trắc nghiệm cần các lựa chọn khác nhau và đáp án thuộc lựa chọn.");
        }
    }

    private static String normalizeOption(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void text(String value, int max, boolean required) {
        require(
                (value == null || value.length() <= max)
                        && (!required || value != null && !value.isBlank()),
                "Nội dung trống hoặc vượt giới hạn cho phép.");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new BusinessException("invalid_exam_content", ErrorType.BAD_REQUEST, message);
    }
}
