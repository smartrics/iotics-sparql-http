package smartrics.iotics.sparqlhttp;

import com.google.gson.Gson;

public record ErrorMessage(String message) {

    public static String toJson(String message) {
        return new ErrorMessage(message).toJsonString();
    }

    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
