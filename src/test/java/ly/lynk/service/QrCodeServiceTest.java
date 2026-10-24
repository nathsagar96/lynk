package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import ly.lynk.exception.QrCodeGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    @DisplayName("generates valid PNG bytes that decode back to source content")
    void generatesValidPngAndDecodesContent() throws Exception {
        String content = "https://lynk.ly/my-cool-link";
        byte[] pngBytes = qrCodeService.generatePng(content, 256);

        assertThat(pngBytes).isNotEmpty();
        // PNG magic bytes
        assertThat(pngBytes[0]).isEqualTo((byte) 0x89);
        assertThat(pngBytes[1]).isEqualTo((byte) 0x50);
        assertThat(pngBytes[2]).isEqualTo((byte) 0x4E);
        assertThat(pngBytes[3]).isEqualTo((byte) 0x47);

        // Decode the generated image back to string using ZXing
        var bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertThat(bufferedImage).isNotNull();
        assertThat(bufferedImage.getWidth()).isEqualTo(256);
        assertThat(bufferedImage.getHeight()).isEqualTo(256);

        var luminanceSource = new BufferedImageLuminanceSource(bufferedImage);
        var bitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
        Result decodedResult = new QRCodeReader().decode(bitmap);

        assertThat(decodedResult.getText()).isEqualTo(content);
    }

    @Test
    @DisplayName("fails with QrCodeGenerationException if dimension is negative or invalid")
    void throwsOnInvalidDimensions() {
        assertThatThrownBy(() -> qrCodeService.generatePng("https://lynk.ly", -10))
                .isInstanceOf(QrCodeGenerationException.class)
                .hasMessageContaining("Failed to generate QR code");
    }
}
