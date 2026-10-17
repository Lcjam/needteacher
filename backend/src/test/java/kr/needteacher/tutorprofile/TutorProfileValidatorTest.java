package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.needteacher.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class TutorProfileValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private TutorProfileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TutorProfileValidator();
    }

    private ObjectNode validCreateNode() {
        ObjectNode node = JSON.createObjectNode();
        node.put("displayName", "김선생");
        node.put("university", "경북대학교");
        node.put("department", "수학교육과");
        node.put("introduction", "수학 전공자입니다.");
        node.putArray("subjectCodes").add("MATH").add("SCIENCE");
        node.putArray("gradeCodes").add("MIDDLE_1").add("MIDDLE_2");
        node.putArray("dongCodes").add("BEOMEO_1").add("BEOMEO_2");

        ObjectNode tuition = node.putObject("tuition");
        tuition.put("amountWon", 50000);
        tuition.put("unit", "PER_SESSION");
        tuition.put("sessionMinutes", 60);

        node.put("declarationType", "REPORTED");
        node.put("registrationNumber", "12345");
        return node;
    }

    @Test
    @DisplayName("유효한 생성 요청 노드는 통과한다")
    void validCreatePasses() {
        assertThatCode(() -> validator.validateCreate(validCreateNode())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("root 노드가 null이거나 객체가 아니면 예외가 발생한다")
    void rootNodeInvalid() {
        assertThatThrownBy(() -> validator.validateCreate(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validateCreate(JSON.createArrayNode()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("highSchool 필드가 포함되어 있으면 예외가 발생한다")
    void highSchoolRejected() {
        ObjectNode node = validCreateNode();
        node.put("highSchool", "대구고");
        assertThatThrownBy(() -> validator.validateCreate(node))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertField(ve, "highSchool");
                });
    }

    @Test
    @DisplayName("displayName 유효성 검증 실패 케이스들")
    void displayNameValidation() {
        ObjectNode node = validCreateNode();
        node.remove("displayName");
        assertThrowsField(node, "displayName");

        node = validCreateNode();
        node.put("displayName", 123);
        assertThrowsField(node, "displayName");

        node = validCreateNode();
        node.put("displayName", "");
        assertThrowsField(node, "displayName");

        node = validCreateNode();
        node.put("displayName", "a".repeat(21));
        assertThrowsField(node, "displayName");

        ObjectNode validNode = validCreateNode();
        validNode.put("displayName", "a".repeat(20));
        assertThatCode(() -> validator.validateCreate(validNode)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("university 및 department 유효성 검증 실패 케이스들")
    void universityAndDepartmentValidation() {
        ObjectNode node = validCreateNode();
        node.remove("university");
        assertThrowsField(node, "university");

        node = validCreateNode();
        node.put("university", "");
        assertThrowsField(node, "university");

        node = validCreateNode();
        node.put("university", "U".repeat(51));
        assertThrowsField(node, "university");

        node = validCreateNode();
        node.remove("department");
        assertThrowsField(node, "department");

        node = validCreateNode();
        node.put("department", "");
        assertThrowsField(node, "department");

        node = validCreateNode();
        node.put("department", "D".repeat(51));
        assertThrowsField(node, "department");
    }

    @Test
    @DisplayName("introduction 유효성 검증")
    void introductionValidation() {
        ObjectNode nullIntroNode = validCreateNode();
        nullIntroNode.putNull("introduction");
        assertThatCode(() -> validator.validateCreate(nullIntroNode)).doesNotThrowAnyException();

        ObjectNode badIntroType = validCreateNode();
        badIntroType.put("introduction", 123);
        assertThrowsField(badIntroType, "introduction");

        ObjectNode longIntroNode = validCreateNode();
        longIntroNode.put("introduction", "i".repeat(1001));
        assertThrowsField(longIntroNode, "introduction");

        ObjectNode maxIntroNode = validCreateNode();
        maxIntroNode.put("introduction", "i".repeat(1000));
        assertThatCode(() -> validator.validateCreate(maxIntroNode)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("subjectCodes 유효성 검증")
    void subjectCodesValidation() {
        ObjectNode node = validCreateNode();
        node.remove("subjectCodes");
        assertThrowsField(node, "subjectCodes");

        node = validCreateNode();
        node.putArray("subjectCodes");
        assertThrowsField(node, "subjectCodes");

        node = validCreateNode();
        ArrayNode arr = node.putArray("subjectCodes");
        arr.add("MATH").add("ENGLISH").add("SCIENCE").add("SOCIAL").add("ESSAY").add("CODING");
        assertThrowsField(node, "subjectCodes");

        node = validCreateNode();
        arr = node.putArray("subjectCodes");
        arr.add("MATH").add("MATH");
        assertThrowsField(node, "subjectCodes");

        node = validCreateNode();
        arr = node.putArray("subjectCodes");
        arr.add("INVALID_CODE");
        assertThrowsField(node, "subjectCodes");

        node = validCreateNode();
        arr = node.putArray("subjectCodes");
        arr.add(123);
        assertThrowsField(node, "subjectCodes");
    }

    @Test
    @DisplayName("gradeCodes 유효성 검증")
    void gradeCodesValidation() {
        ObjectNode node = validCreateNode();
        node.remove("gradeCodes");
        assertThrowsField(node, "gradeCodes");

        node = validCreateNode();
        node.putArray("gradeCodes");
        assertThrowsField(node, "gradeCodes");

        node = validCreateNode();
        ArrayNode arr = node.putArray("gradeCodes");
        arr.add("MIDDLE_1").add("MIDDLE_1");
        assertThrowsField(node, "gradeCodes");

        node = validCreateNode();
        arr = node.putArray("gradeCodes");
        arr.add("INVALID_GRADE");
        assertThrowsField(node, "gradeCodes");

        node = validCreateNode();
        arr = node.putArray("gradeCodes");
        arr.add(123);
        assertThrowsField(node, "gradeCodes");
    }

    @Test
    @DisplayName("dongCodes 유효성 검증")
    void dongCodesValidation() {
        ObjectNode node = validCreateNode();
        node.remove("dongCodes");
        assertThrowsField(node, "dongCodes");

        node = validCreateNode();
        node.putArray("dongCodes");
        assertThrowsField(node, "dongCodes");

        node = validCreateNode();
        ArrayNode arr = node.putArray("dongCodes");
        arr.add("BEOMEO_1").add("BEOMEO_1");
        assertThrowsField(node, "dongCodes");

        node = validCreateNode();
        arr = node.putArray("dongCodes");
        arr.add("INVALID_DONG");
        assertThrowsField(node, "dongCodes");

        node = validCreateNode();
        arr = node.putArray("dongCodes");
        arr.add(123);
        assertThrowsField(node, "dongCodes");
    }

    @Test
    @DisplayName("tuition 유효성 검증")
    void tuitionValidation() {
        ObjectNode node = validCreateNode();
        node.remove("tuition");
        assertThrowsField(node, "tuition");

        node = validCreateNode();
        node.put("tuition", "string");
        assertThrowsField(node, "tuition");

        node = validCreateNode();
        ObjectNode t = (ObjectNode) node.path("tuition");
        t.remove("amountWon");
        assertThrowsField(node, "tuition.amountWon");

        t.put("amountWon", 0);
        assertThrowsField(node, "tuition.amountWon");

        t.put("amountWon", 10000001);
        assertThrowsField(node, "tuition.amountWon");

        t.put("amountWon", 50000);
        t.remove("unit");
        assertThrowsField(node, "tuition.unit");

        t.put("unit", "INVALID");
        assertThrowsField(node, "tuition.unit");

        t.put("unit", "PER_SESSION");
        t.remove("sessionMinutes");
        assertThrowsField(node, "tuition.sessionMinutes");

        t.put("sessionMinutes", 9);
        assertThrowsField(node, "tuition.sessionMinutes");

        t.put("sessionMinutes", 601);
        assertThrowsField(node, "tuition.sessionMinutes");

        t.put("sessionMinutes", 60);
        t.put("sessionsPerMonth", 4);
        assertThrowsField(node, "tuition.sessionsPerMonth");

        t.put("unit", "PER_MONTH");
        t.remove("sessionsPerMonth");
        assertThrowsField(node, "tuition.sessionsPerMonth");

        t.put("sessionsPerMonth", 0);
        assertThrowsField(node, "tuition.sessionsPerMonth");

        t.put("sessionsPerMonth", 32);
        assertThrowsField(node, "tuition.sessionsPerMonth");

        t.put("sessionsPerMonth", 4);
        ObjectNode exemptValidNode = validCreateNode();
        ObjectNode exemptTuition = (ObjectNode) exemptValidNode.path("tuition");
        exemptTuition.put("unit", "PER_MONTH");
        exemptTuition.put("sessionsPerMonth", 4);
        exemptValidNode.put("declarationType", "ENROLLED_EXEMPT");
        exemptValidNode.remove("registrationNumber");
        assertThatCode(() -> validator.validateCreate(exemptValidNode)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("declarationType 및 registrationNumber 유효성 검증")
    void declarationValidation() {
        ObjectNode node = validCreateNode();
        node.remove("declarationType");
        assertThrowsField(node, "declarationType");

        node = validCreateNode();
        node.put("declarationType", "UNKNOWN");
        assertThrowsField(node, "declarationType");

        node = validCreateNode();
        node.put("declarationType", "REPORTED");
        node.remove("registrationNumber");
        assertThrowsField(node, "registrationNumber");

        node.put("registrationNumber", "");
        assertThrowsField(node, "registrationNumber");

        node.put("registrationNumber", "R".repeat(31));
        assertThrowsField(node, "registrationNumber");

        ObjectNode maxRegNode = validCreateNode();
        maxRegNode.put("declarationType", "REPORTED");
        maxRegNode.put("registrationNumber", "R".repeat(30));
        assertThatCode(() -> validator.validateCreate(maxRegNode)).doesNotThrowAnyException();

        ObjectNode exemptWithReg = validCreateNode();
        exemptWithReg.put("declarationType", "ENROLLED_EXEMPT");
        exemptWithReg.put("registrationNumber", "123");
        assertThrowsField(exemptWithReg, "registrationNumber");

        ObjectNode exemptWithoutReg = validCreateNode();
        exemptWithoutReg.put("declarationType", "ENROLLED_EXEMPT");
        exemptWithoutReg.remove("registrationNumber");
        assertThatCode(() -> validator.validateCreate(exemptWithoutReg)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Jackson 강제 변환 방지: 문자열 필드에 객체, 숫자, boolean 등이 들어오면 검증 실패한다")
    void nonStringTypesRejected() {
        ObjectNode node1 = validCreateNode();
        node1.set("displayName", JSON.createObjectNode());
        assertThrowsField(node1, "displayName");

        ObjectNode node2 = validCreateNode();
        node2.set("displayName", JSON.createArrayNode());
        assertThrowsField(node2, "displayName");

        ObjectNode node3 = validCreateNode();
        node3.put("displayName", true);
        assertThrowsField(node3, "displayName");

        ObjectNode node4 = validCreateNode();
        node4.set("university", JSON.createObjectNode());
        assertThrowsField(node4, "university");

        ObjectNode node5 = validCreateNode();
        node5.set("department", JSON.createObjectNode());
        assertThrowsField(node5, "department");

        ObjectNode node6 = validCreateNode();
        node6.set("introduction", JSON.createObjectNode());
        assertThrowsField(node6, "introduction");

        ObjectNode node7 = validCreateNode();
        node7.put("introduction", 12345);
        assertThrowsField(node7, "introduction");

        ObjectNode node8 = validCreateNode();
        node8.set("declarationType", JSON.createObjectNode());
        assertThrowsField(node8, "declarationType");

        ObjectNode node9 = validCreateNode();
        node9.put("declarationType", 123);
        assertThrowsField(node9, "declarationType");

        ObjectNode node10 = validCreateNode();
        node10.set("registrationNumber", JSON.createObjectNode());
        assertThrowsField(node10, "registrationNumber");

        ObjectNode node11 = validCreateNode();
        node11.put("registrationNumber", 123);
        assertThrowsField(node11, "registrationNumber");
    }

    @Test
    @DisplayName("Jackson 강제 변환 방지: 숫자 필드에 소수점(1.5), 지수/큰 수(1e12), 문자열, 객체 등이 들어오면 검증 실패한다")
    void nonIntTypesRejected() {
        ObjectNode node1 = validCreateNode();
        node1.withObject("/tuition").put("amountWon", 1.5);
        assertThrowsField(node1, "tuition.amountWon");

        ObjectNode node2 = validCreateNode();
        node2.withObject("/tuition").put("amountWon", 1e12);
        assertThrowsField(node2, "tuition.amountWon");

        ObjectNode node3 = validCreateNode();
        node3.withObject("/tuition").put("amountWon", "50000");
        assertThrowsField(node3, "tuition.amountWon");

        ObjectNode node4 = validCreateNode();
        node4.withObject("/tuition").set("amountWon", JSON.createObjectNode());
        assertThrowsField(node4, "tuition.amountWon");

        ObjectNode node5 = validCreateNode();
        node5.withObject("/tuition").put("sessionMinutes", 60.5);
        assertThrowsField(node5, "tuition.sessionMinutes");

        ObjectNode node6 = validCreateNode();
        node6.withObject("/tuition").put("sessionMinutes", "60");
        assertThrowsField(node6, "tuition.sessionMinutes");

        ObjectNode node7 = validCreateNode();
        node7.withObject("/tuition").set("sessionMinutes", JSON.createObjectNode());
        assertThrowsField(node7, "tuition.sessionMinutes");

        ObjectNode node8 = validCreateNode();
        node8.withObject("/tuition").put("unit", "PER_MONTH");
        node8.withObject("/tuition").put("sessionsPerMonth", 4.5);
        assertThrowsField(node8, "tuition.sessionsPerMonth");

        ObjectNode node9 = validCreateNode();
        node9.withObject("/tuition").put("unit", "PER_MONTH");
        node9.withObject("/tuition").put("sessionsPerMonth", "4");
        assertThrowsField(node9, "tuition.sessionsPerMonth");

        ObjectNode node10 = validCreateNode();
        node10.withObject("/tuition").put("unit", "PER_MONTH");
        node10.withObject("/tuition").set("sessionsPerMonth", JSON.createObjectNode());
        assertThrowsField(node10, "tuition.sessionsPerMonth");
    }

    @Test
    @DisplayName("Jackson 강제 변환 방지: 배열 필드나 원소에 객체, 숫자 등이 들어오면 검증 실패한다")
    void arrayAndElementTypesRejected() {
        ObjectNode node1 = validCreateNode();
        node1.set("subjectCodes", JSON.createObjectNode());
        assertThrowsField(node1, "subjectCodes");

        ObjectNode node2 = validCreateNode();
        node2.putArray("subjectCodes").addObject();
        assertThrowsField(node2, "subjectCodes");

        ObjectNode node3 = validCreateNode();
        node3.putArray("subjectCodes").add(123);
        assertThrowsField(node3, "subjectCodes");

        ObjectNode node4 = validCreateNode();
        node4.putArray("gradeCodes").addObject();
        assertThrowsField(node4, "gradeCodes");

        ObjectNode node5 = validCreateNode();
        node5.putArray("gradeCodes").add(123);
        assertThrowsField(node5, "gradeCodes");

        ObjectNode node6 = validCreateNode();
        node6.putArray("dongCodes").addObject();
        assertThrowsField(node6, "dongCodes");

        ObjectNode node7 = validCreateNode();
        node7.putArray("dongCodes").add(123);
        assertThrowsField(node7, "dongCodes");

        ObjectNode node8 = validCreateNode();
        node8.put("tuition", 12345);
        assertThrowsField(node8, "tuition");

        ObjectNode node9 = validCreateNode();
        node9.set("tuition", JSON.createArrayNode());
        assertThrowsField(node9, "tuition");

        ObjectNode node10 = validCreateNode();
        node10.withObject("/tuition").set("unit", JSON.createObjectNode());
        assertThrowsField(node10, "tuition.unit");
    }

    private void assertThrowsField(ObjectNode node, String field) {
        assertThatThrownBy(() -> validator.validateCreate(node))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertField((ValidationException) e, field));
    }

    private void assertField(ValidationException ve, String field) {
        boolean present = ve.getFieldErrors().stream().anyMatch(f -> field.equals(f.field()));
        if (!present) {
            throw new AssertionError("Expected field error " + field + " but got " + ve.getFieldErrors());
        }
    }
}
