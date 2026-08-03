package ly.lynk.click;

import java.time.Instant;

public record ClickEvent(String shortcode, String ipAddress, String userAgent, String referer, Instant clickedAt) {}
