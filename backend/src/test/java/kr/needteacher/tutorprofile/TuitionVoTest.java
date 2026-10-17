package kr.needteacher.tutorprofile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TuitionVoTest {

    @Test
    @DisplayName("기본 생성자 및 getter 동작 확인")
    void constructorAndGetters() {
        TuitionVo vo = new TuitionVo(50000, TuitionUnit.PER_SESSION, 60, null);
        assertThat(vo.getAmountWon()).isEqualTo(50000);
        assertThat(vo.getUnit()).isEqualTo(TuitionUnit.PER_SESSION);
        assertThat(vo.getSessionMinutes()).isEqualTo(60);
        assertThat(vo.getSessionsPerMonth()).isNull();

        TuitionVo defaultVo = new TuitionVo();
        assertThat(defaultVo.getAmountWon()).isNull();
        assertThat(defaultVo.getUnit()).isNull();
        assertThat(defaultVo.getSessionMinutes()).isNull();
        assertThat(defaultVo.getSessionsPerMonth()).isNull();
    }

    @Test
    @DisplayName("equals & hashCode 동등성 및 차이 분기 검증")
    void equalsAndHashCode() {
        TuitionVo base = new TuitionVo(400000, TuitionUnit.PER_MONTH, 90, 8);
        TuitionVo same = new TuitionVo(400000, TuitionUnit.PER_MONTH, 90, 8);
        TuitionVo diffAmount = new TuitionVo(500000, TuitionUnit.PER_MONTH, 90, 8);
        TuitionVo diffUnit = new TuitionVo(400000, TuitionUnit.PER_SESSION, 90, 8);
        TuitionVo diffMinutes = new TuitionVo(400000, TuitionUnit.PER_MONTH, 60, 8);
        TuitionVo diffSessions = new TuitionVo(400000, TuitionUnit.PER_MONTH, 90, 10);
        TuitionVo nullSessions1 = new TuitionVo(400000, TuitionUnit.PER_SESSION, 90, null);
        TuitionVo nullSessions2 = new TuitionVo(400000, TuitionUnit.PER_SESSION, 90, null);

        // 동일성
        assertThat(base.equals(base)).isTrue();
        // 동등성
        assertThat(base).isEqualTo(same);
        assertThat(base.hashCode()).isEqualTo(same.hashCode());
        assertThat(nullSessions1).isEqualTo(nullSessions2);
        assertThat(nullSessions1.hashCode()).isEqualTo(nullSessions2.hashCode());

        // null 및 다른 클래스
        assertThat(base.equals(null)).isFalse();
        assertThat(base.equals("not a vo")).isFalse();

        // 각 필드 불일치
        assertThat(base).isNotEqualTo(diffAmount);
        assertThat(base).isNotEqualTo(diffUnit);
        assertThat(base).isNotEqualTo(diffMinutes);
        assertThat(base).isNotEqualTo(diffSessions);
        assertThat(base).isNotEqualTo(nullSessions1);
        assertThat(nullSessions1).isNotEqualTo(base);
    }
}
