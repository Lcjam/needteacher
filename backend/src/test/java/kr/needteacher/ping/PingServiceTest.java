package kr.needteacher.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingServiceTest {

    @Test
    void returnsPong() {
        assertEquals("pong", new PingService().ping());
    }
}
