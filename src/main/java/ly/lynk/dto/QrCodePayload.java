package ly.lynk.dto;

import java.time.Duration;

/**
 * QR code payload containing raw PNG image bytes and recommended cache duration.
 *
 * @param imageBytes PNG image bytes
 * @param cacheDuration recommended HTTP cache duration
 */
public record QrCodePayload(byte[] imageBytes, Duration cacheDuration) {}
