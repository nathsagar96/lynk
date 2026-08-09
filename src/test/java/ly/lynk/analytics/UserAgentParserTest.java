package ly.lynk.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentParserTest {

    @Test
    void testParseBrowser() {
        String chrome =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        String firefox = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0";
        String safari =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";

        assertThat(UserAgentParser.parseBrowser(chrome)).isEqualTo("Chrome");
        assertThat(UserAgentParser.parseBrowser(firefox)).isEqualTo("Firefox");
        assertThat(UserAgentParser.parseBrowser(safari)).isEqualTo("Safari");
        assertThat(UserAgentParser.parseBrowser(null)).isEqualTo("Unknown");
    }

    @Test
    void testParseOs() {
        String mac = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
        String win = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        String ios = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";
        String android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36";

        assertThat(UserAgentParser.parseOs(mac)).isEqualTo("macOS");
        assertThat(UserAgentParser.parseOs(win)).isEqualTo("Windows");
        assertThat(UserAgentParser.parseOs(ios)).isEqualTo("iOS");
        assertThat(UserAgentParser.parseOs(android)).isEqualTo("Android");
        assertThat(UserAgentParser.parseOs(null)).isEqualTo("Unknown");
    }
}
