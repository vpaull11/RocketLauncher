package com.rocketlauncher.presentation.navigation;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Обход {@link NoSuchMethodError} на API &lt; 33: Kotlin/стабы иногда связывают вызов с
 * {@code URLEncoder.encode(String, Charset)} / {@code URLDecoder.decode(String, Charset)},
 * которых нет в ART до Android 13. В Java вызовы со строкой {@code "UTF-8"} однозначны.
 */
public final class UrlFormEncode {
    private UrlFormEncode() {}

    public static String utf8(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8", e);
        }
    }

    public static String decodeUtf8(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8", e);
        }
    }
}
