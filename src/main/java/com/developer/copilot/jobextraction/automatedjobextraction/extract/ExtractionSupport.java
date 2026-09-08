package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

public final class ExtractionSupport {

    private ExtractionSupport() {
    }

    public static String firstText(Document document, String cssQuery) {
        if (document == null || cssQuery == null) {
            return null;
        }
        Element el = document.selectFirst(cssQuery);
        return cleanText(el == null ? null : el.text());
    }

    public static String joinedText(Document document, String cssQuery) {
        if (document == null || cssQuery == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Element el : document.select(cssQuery)) {
            String text = cleanText(el.text());
            if (text != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(text);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static List<String> listItems(Element root, String cssQuery) {
        List<String> items = new ArrayList<>();
        if (root == null || cssQuery == null) {
            return items;
        }
        for (Element el : root.select(cssQuery)) {
            String text = cleanText(el.text());
            if (text != null) {
                items.add(text);
            }
        }
        return items;
    }

    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.clean(html, Safelist.none());
        return cleanText(Jsoup.parse(cleaned).text());
    }

    public static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    public static boolean hostContains(String host, String fragment) {
        return host != null && host.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    public static String meta(Document document, String cssQuery, String attr) {
        if (document == null) {
            return null;
        }
        Element el = document.selectFirst(cssQuery);
        if (el == null) {
            return null;
        }
        return cleanText(el.attr(attr));
    }
}
