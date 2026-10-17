package kr.needteacher.tutorprofile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.needteacher.common.FieldErrorDetail;
import kr.needteacher.common.ValidationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TutorProfileValidator {

    public void validateCreate(JsonNode root) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new ValidationException("요청 본문이 올바르지 않습니다.", List.of());
        }

        if (root.has("highSchool")) {
            errors.add(new FieldErrorDetail("highSchool", "출신 고등학교 정보는 수집하지 않습니다."));
        }

        validateDisplayName(root.path("displayName"), errors);
        validateUniversity(root.path("university"), errors);
        validateDepartment(root.path("department"), errors);
        validateIntroduction(root.get("introduction"), errors);
        validateSubjectCodes(root.path("subjectCodes"), errors);
        validateGradeCodes(root.path("gradeCodes"), errors);
        validateDongCodes(root.path("dongCodes"), errors);

        JsonNode tuitionNode = root.path("tuition");
        validateTuition(tuitionNode, errors);

        validateDeclaration(root.path("declarationType"), root.get("registrationNumber"), errors);

        if (!errors.isEmpty()) {
            throw new ValidationException("입력값 검증에 실패했습니다.", errors);
        }
    }

    private void validateDisplayName(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isString()) {
            errors.add(new FieldErrorDetail("displayName", "활동명은 1~20자여야 합니다."));
            return;
        }
        String text = node.asString();
        if (text.isEmpty() || text.length() > 20) {
            errors.add(new FieldErrorDetail("displayName", "활동명은 1~20자여야 합니다."));
        }
    }

    private void validateUniversity(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isString()) {
            errors.add(new FieldErrorDetail("university", "대학교명은 1~50자여야 합니다."));
            return;
        }
        String text = node.asString();
        if (text.isEmpty() || text.length() > 50) {
            errors.add(new FieldErrorDetail("university", "대학교명은 1~50자여야 합니다."));
        }
    }

    private void validateDepartment(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isString()) {
            errors.add(new FieldErrorDetail("department", "학과명은 1~50자여야 합니다."));
            return;
        }
        String text = node.asString();
        if (text.isEmpty() || text.length() > 50) {
            errors.add(new FieldErrorDetail("department", "학과명은 1~50자여야 합니다."));
        }
    }

    private void validateIntroduction(JsonNode node, List<FieldErrorDetail> errors) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isString()) {
            errors.add(new FieldErrorDetail("introduction", "소개글은 문자열이어야 합니다."));
            return;
        }
        String text = node.asString();
        if (text.length() > 1000) {
            errors.add(new FieldErrorDetail("introduction", "소개글은 최대 1000자까지 작성할 수 있습니다."));
        }
    }

    private void validateSubjectCodes(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isArray()) {
            errors.add(new FieldErrorDetail("subjectCodes", "과목은 1~5개 선택해야 합니다."));
            return;
        }
        if (node.size() < 1 || node.size() > 5) {
            errors.add(new FieldErrorDetail("subjectCodes", "과목은 1~5개 선택해야 합니다."));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                errors.add(new FieldErrorDetail("subjectCodes", "올바르지 않은 과목 코드입니다."));
                return;
            }
            String code = item.asString();
            if (!seen.add(code)) {
                errors.add(new FieldErrorDetail("subjectCodes", "과목 코드가 중복되었습니다: " + code));
                return;
            }
            try {
                SubjectCode.valueOf(code);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldErrorDetail("subjectCodes", "알 수 없는 과목 코드입니다: " + code));
                return;
            }
        }
    }

    private void validateGradeCodes(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isArray()) {
            errors.add(new FieldErrorDetail("gradeCodes", "대상 학년은 최소 1개 이상 선택해야 합니다."));
            return;
        }
        if (node.size() < 1) {
            errors.add(new FieldErrorDetail("gradeCodes", "대상 학년은 최소 1개 이상 선택해야 합니다."));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                errors.add(new FieldErrorDetail("gradeCodes", "올바르지 않은 학년 코드입니다."));
                return;
            }
            String code = item.asString();
            if (!seen.add(code)) {
                errors.add(new FieldErrorDetail("gradeCodes", "학년 코드가 중복되었습니다: " + code));
                return;
            }
            try {
                GradeCode.valueOf(code);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldErrorDetail("gradeCodes", "알 수 없는 학년 코드입니다: " + code));
                return;
            }
        }
    }

    private void validateDongCodes(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isArray()) {
            errors.add(new FieldErrorDetail("dongCodes", "희망 지역은 최소 1개 이상 선택해야 합니다."));
            return;
        }
        if (node.size() < 1) {
            errors.add(new FieldErrorDetail("dongCodes", "희망 지역은 최소 1개 이상 선택해야 합니다."));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                errors.add(new FieldErrorDetail("dongCodes", "올바르지 않은 지역 코드입니다."));
                return;
            }
            String code = item.asString();
            if (!seen.add(code)) {
                errors.add(new FieldErrorDetail("dongCodes", "지역 코드가 중복되었습니다: " + code));
                return;
            }
            try {
                DongCode.valueOf(code);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldErrorDetail("dongCodes", "알 수 없는 지역 코드입니다: " + code));
                return;
            }
        }
    }

    private void validateTuition(JsonNode node, List<FieldErrorDetail> errors) {
        if (node.isMissingNode() || !node.isObject()) {
            errors.add(new FieldErrorDetail("tuition", "과외비 정보는 필수입니다."));
            return;
        }

        JsonNode amountNode = node.path("amountWon");
        if (amountNode.isMissingNode() || !amountNode.isInt()) {
            errors.add(new FieldErrorDetail("tuition.amountWon", "과외비 금액을 입력해야 합니다."));
        } else {
            int amount = amountNode.asInt();
            if (amount < 1 || amount > 10000000) {
                errors.add(new FieldErrorDetail("tuition.amountWon", "과외비 금액은 1원 이상 10,000,000원 이하이어야 합니다."));
            }
        }

        JsonNode unitNode = node.path("unit");
        TuitionUnit unit = null;
        if (unitNode.isMissingNode() || !unitNode.isString()) {
            errors.add(new FieldErrorDetail("tuition.unit", "과외비 단위를 선택해야 합니다."));
        } else {
            try {
                unit = TuitionUnit.valueOf(unitNode.asString());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldErrorDetail("tuition.unit", "알 수 없는 과외비 단위입니다."));
            }
        }

        JsonNode minutesNode = node.path("sessionMinutes");
        if (minutesNode.isMissingNode() || !minutesNode.isInt()) {
            errors.add(new FieldErrorDetail("tuition.sessionMinutes", "1회 수업 시간은 필수입니다."));
        } else {
            int minutes = minutesNode.asInt();
            if (minutes < 10 || minutes > 600) {
                errors.add(new FieldErrorDetail("tuition.sessionMinutes", "1회 수업 시간은 10~600분이어야 합니다."));
            }
        }

        JsonNode sessionsNode = node.get("sessionsPerMonth");
        if (unit == TuitionUnit.PER_SESSION) {
            if (sessionsNode != null && !sessionsNode.isNull() && !sessionsNode.isMissingNode()) {
                errors.add(new FieldErrorDetail("tuition.sessionsPerMonth", "회당 과외비는 월 수업 횟수를 입력할 수 없습니다."));
            }
        } else if (unit == TuitionUnit.PER_MONTH) {
            if (sessionsNode == null || sessionsNode.isNull() || !sessionsNode.isInt()) {
                errors.add(new FieldErrorDetail("tuition.sessionsPerMonth", "월별 과외비는 월 수업 횟수가 필수입니다."));
            } else {
                int sessions = sessionsNode.asInt();
                if (sessions < 1 || sessions > 31) {
                    errors.add(new FieldErrorDetail("tuition.sessionsPerMonth", "월별 과외비는 월 1~31회 수업 횟수를 입력해야 합니다."));
                }
            }
        }
    }

    private void validateDeclaration(JsonNode typeNode, JsonNode regNumberNode, List<FieldErrorDetail> errors) {
        if (typeNode.isMissingNode() || !typeNode.isString()) {
            errors.add(new FieldErrorDetail("declarationType", "신고 유형은 필수입니다."));
            return;
        }
        DeclarationType type;
        try {
            type = DeclarationType.valueOf(typeNode.asString());
        } catch (IllegalArgumentException e) {
            errors.add(new FieldErrorDetail("declarationType", "알 수 없는 신고 유형입니다."));
            return;
        }

        if (type == DeclarationType.REPORTED) {
            if (regNumberNode == null || regNumberNode.isNull() || !regNumberNode.isString()) {
                errors.add(new FieldErrorDetail("registrationNumber", "신고번호는 필수입니다."));
            } else {
                String reg = regNumberNode.asString();
                if (reg.isEmpty() || reg.length() > 30) {
                    errors.add(new FieldErrorDetail("registrationNumber", "신고번호는 1~30자여야 합니다."));
                }
            }
        } else if (type == DeclarationType.ENROLLED_EXEMPT) {
            if (regNumberNode != null && !regNumberNode.isNull() && !regNumberNode.isMissingNode()) {
                errors.add(new FieldErrorDetail("registrationNumber", "재학생 면제는 신고번호를 입력할 수 없습니다."));
            }
        }
    }
}
