package kr.needteacher.tutorprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class TuitionVo {

    @Column(name = "amount_won", nullable = false)
    private Integer amountWon;

    @Enumerated(EnumType.STRING)
    @Column(name = "tuition_unit", nullable = false, length = 20)
    private TuitionUnit unit;

    @Column(name = "session_minutes", nullable = false)
    private Integer sessionMinutes;

    @Column(name = "sessions_per_month")
    private Integer sessionsPerMonth;

    protected TuitionVo() {}

    public TuitionVo(Integer amountWon, TuitionUnit unit, Integer sessionMinutes, Integer sessionsPerMonth) {
        this.amountWon = amountWon;
        this.unit = unit;
        this.sessionMinutes = sessionMinutes;
        this.sessionsPerMonth = sessionsPerMonth;
    }

    public Integer getAmountWon() {
        return amountWon;
    }

    public TuitionUnit getUnit() {
        return unit;
    }

    public Integer getSessionMinutes() {
        return sessionMinutes;
    }

    public Integer getSessionsPerMonth() {
        return sessionsPerMonth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TuitionVo tuitionVo = (TuitionVo) o;
        return java.util.Objects.equals(amountWon, tuitionVo.amountWon)
                && unit == tuitionVo.unit
                && java.util.Objects.equals(sessionMinutes, tuitionVo.sessionMinutes)
                && java.util.Objects.equals(sessionsPerMonth, tuitionVo.sessionsPerMonth);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(amountWon, unit, sessionMinutes, sessionsPerMonth);
    }
}
