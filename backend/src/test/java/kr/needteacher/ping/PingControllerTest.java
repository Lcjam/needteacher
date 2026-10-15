package kr.needteacher.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingControllerTest {

    @Test
    void returnsWhatServiceAnswers() {
        assertEquals("pong", new PingController(new PingService()).ping());
    }
}
