package smartrics.iotics.sparqlhttp;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTokenTest {
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss", Locale.ENGLISH);

    @Test
    void testValidTimestamp() {
        // Create a timestamp that is definitely in the future
        String futureTime = LocalDateTime.now(ZoneOffset.UTC).plusDays(1).format(formatter);
        SimpleToken token = newSimpleToken(futureTime);

        assertTrue(token.isValid(), "Token with future expiry should be valid");
    }

    @Test
    void testInvalidTimestamp() {
        String pastTime = LocalDateTime.now(ZoneOffset.UTC).minusDays(1).format(formatter);
        SimpleToken token = newSimpleToken(pastTime);
        assertFalse(token.isValid(), "Token with past expiry should be invalid");
    }

    @Test
    void testInvalidData() {
        String futureTime = LocalDateTime.now(ZoneOffset.UTC).plusDays(1).format(formatter);
        assertFalse(new SimpleToken(new SimpleToken.Payload("agent", "userDID", futureTime)).isValid(), "Token with null agentId should be invalid");
        assertFalse(new SimpleToken(new SimpleToken.Payload(null, "userDID", futureTime)).isValid(), "Token with null agentDID should be invalid");
        assertFalse(new SimpleToken(new SimpleToken.Payload("agent#id", null, futureTime)).isValid(), "Token with null userDID should be invalid");
    }


    @NotNull
    private static SimpleToken newSimpleToken(String pastTime) {
        SimpleToken.Payload payload = new SimpleToken.Payload("agent#id", "userDID", pastTime);
        return new SimpleToken(payload);
    }
}