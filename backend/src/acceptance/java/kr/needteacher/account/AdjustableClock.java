package kr.needteacher.account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class AdjustableClock extends Clock {

    private final AtomicReference<Instant> time;
    private final ZoneId zone;

    public AdjustableClock() {
        this(new AtomicReference<>(Instant.EPOCH), ZoneOffset.UTC);
    }

    private AdjustableClock(AtomicReference<Instant> time, ZoneId zone) {
        this.time = time;
        this.zone = zone;
    }

    public void set(Instant instant) {
        time.set(instant);
    }

    public void advanceSeconds(long seconds) {
        time.updateAndGet(value -> value.plusSeconds(seconds));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new AdjustableClock(time, newZone);
    }

    @Override
    public Instant instant() {
        return time.get();
    }
}
