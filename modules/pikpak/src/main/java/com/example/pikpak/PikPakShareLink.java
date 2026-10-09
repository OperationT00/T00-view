package com.example.pikpak;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import static com.example.pikpak.PikPakException.Code.INVALID_INPUT;

/** Parsing only; constructing a link never performs a network request. */
public record PikPakShareLink(String shareId) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    public PikPakShareLink {
        if (shareId == null || !ID.matcher(shareId).matches())
            throw new PikPakException(INVALID_INPUT, "分享标识不合法");
    }
    public static PikPakShareLink parse(String input) {
        if (input == null || input.length() > 4096 || input.codePoints().anyMatch(Character::isISOControl))
            throw new PikPakException(INVALID_INPUT, "请输入有效的 PikPak 分享链接");
        try {
            URI uri = new URI(input.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"mypikpak.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawFragment() != null)
                throw new URISyntaxException("", "Unsupported share URL");
            String path = uri.getRawPath();
            if (path == null || !path.matches("/s/[A-Za-z0-9_-]{1,128}/?"))
                throw new URISyntaxException("", "Unsupported share path");
            // Only known presentation options can be discarded; never silently discard a password.
            String query = uri.getRawQuery();
            if (query != null && !query.isEmpty()) {
                for (String field : query.split("&", -1)) {
                    if (!field.matches("view=(list|grid|flow)|order=(name|mod|size)_(asc|desc)"))
                        throw new URISyntaxException("", "Unsupported share parameters");
                }
            }
            return new PikPakShareLink(path.substring(3).replaceFirst("/$", ""));
        } catch (URISyntaxException e) {
            throw new PikPakException(INVALID_INPUT, "仅支持标准 HTTPS PikPak 分享链接；提取码请单独填写");
        }
    }
    public URI uri() { return URI.create("https://mypikpak.com/s/" + shareId); }
}
