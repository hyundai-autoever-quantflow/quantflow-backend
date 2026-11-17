package org.example.quantflowbackend.domain.news.service;


import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

@Component
public class NewsTextCleaner {
    private static final Pattern HIGHLIGHT_TAG =
            Pattern.compile("</?b\\s*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE =
            Pattern.compile("[\\s\\p{Z}]+");

    public String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String withoutTags = HIGHLIGHT_TAG.matcher(text)
                .replaceAll("");

        String decoded = HtmlUtils.htmlUnescape(withoutTags);

        return WHITESPACE.matcher(decoded)
                .replaceAll(" ")
                .strip();
    }
}
