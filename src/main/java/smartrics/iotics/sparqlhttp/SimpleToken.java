package smartrics.iotics.sparqlhttp;

import com.google.gson.Gson;
import smartrics.iotics.identity.experimental.JWT;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record SimpleToken(Payload payload) {

    public static SimpleToken parse(String token) {
        JWT jwt = JWT.parse(token);
        Gson gson = new Gson();
        String string = jwt.toNiceString();
        return gson.fromJson(string, SimpleToken.class);
    }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss", Locale.ENGLISH);

    public String userDID() {
        return payload.sub();
    }

    public String agentDID() {
        if (payload == null || payload.iss == null) {
            return null;
        }
        return payload.iss().split("#")[0];
    }

    public String agentId() {
        if (payload == null || payload.iss == null) {
            return null;
        }
        String[] split = payload.iss().split("#");
        if (split.length == 2)
            return split[1];
        return null;
    }

    public ZonedDateTime expiryTimestampUTC() {
        LocalDateTime localDateTime = LocalDateTime.parse(payload().exp(), formatter);
        return localDateTime.atOffset(ZoneOffset.UTC).toZonedDateTime();
    }

    public boolean isValid() {
        return userDID() != null &&
                agentId() != null &&
                agentDID() != null &&
                expiryTimestampUTC().isAfter(ZonedDateTime.now(ZoneOffset.UTC));
    }

    public record Payload(String iss, String sub, String exp) {
    }
}
