package it.niedermann.owncloud.notes.shared.util;

import androidx.annotation.NonNull;

import com.nextcloud.android.sso.api.ParsedResponse;

import java.util.HashMap;
import java.util.Map;

public class ResponseUtil {
    /*
     * Move headers key to lowercase
     */
    @NonNull
    public static <T> Map<String, String> getLowerCaseHeaders(Map<String, String> headers) {
        final var cleanedHeaders = new HashMap<String, String>();

        if (headers != null) {
            headers.forEach((k, v) -> cleanedHeaders.put(k.toLowerCase(), v));
        }
        return cleanedHeaders;
    }

    /*
     * Get response headers and move headers key to lowercase
     */
    @NonNull
    public static <T> Map<String, String> getLowerCaseHeaders(ParsedResponse<T> fetchResponse) {
        return getLowerCaseHeaders(fetchResponse.getHeaders());
    }
}
