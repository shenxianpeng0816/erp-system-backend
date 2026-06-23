package com.erp.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Accepts common API / browser datetime strings:
 * {@code yyyy-MM-dd HH:mm:ss}, {@code yyyy-MM-dd'T'HH:mm:ss}, {@code yyyy-MM-dd'T'HH:mm}, {@code yyyy-MM-dd}.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter SPACE_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = normalize(text.trim());
        if (s.length() == 10) {
            return LocalDate.parse(s, ISO_DATE).atStartOfDay();
        }
        if (s.length() == 16) {
            s = s + ":00";
        }
        try {
            return LocalDateTime.parse(s, SPACE_SECONDS);
        } catch (DateTimeParseException ex) {
            throw ctxt.weirdStringException(
                    text,
                    LocalDateTime.class,
                    "Expected yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss, or yyyy-MM-dd");
        }
    }

    static String normalize(String raw) {
        String s = raw;
        int plus = s.indexOf('+');
        if (plus > 0) {
            s = s.substring(0, plus);
        }
        int z = s.indexOf('Z');
        if (z > 0) {
            s = s.substring(0, z);
        }
        if (s.length() > 19 && s.charAt(19) == '.') {
            s = s.substring(0, 19);
        }
        return s.replace('T', ' ');
    }
}
