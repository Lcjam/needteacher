package kr.needteacher.tutorprofile;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TuitionDto(Integer amountWon, TuitionUnit unit, Integer sessionMinutes, Integer sessionsPerMonth) {

    public static TuitionDto from(TuitionVo vo) {
        if (vo == null) {
            return null;
        }
        return new TuitionDto(vo.getAmountWon(), vo.getUnit(), vo.getSessionMinutes(), vo.getSessionsPerMonth());
    }
}
