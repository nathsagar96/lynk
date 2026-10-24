package ly.lynk.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import ly.lynk.exception.QrCodeGenerationException;
import org.springframework.stereotype.Service;

/** Service responsible for generating QR code images. */
@Service
public class QrCodeService {

    private static final Map<EncodeHintType, Object> HINTS = Map.of(
            EncodeHintType.CHARACTER_SET,
            StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION,
            ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN,
            1);

    /**
     * Generates a PNG QR code image encoding the given content.
     *
     * @param content the text or URL to encode
     * @param size dimension in pixels (width and height)
     * @return byte array containing the PNG image
     * @throws QrCodeGenerationException if generation or image encoding fails
     */
    public byte[] generatePng(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, HINTS);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new QrCodeGenerationException("Failed to generate QR code", e);
        }
    }
}
