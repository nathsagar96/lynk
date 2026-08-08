package ly.lynk.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpAnonymizerTest {

    @Test
    void shouldTruncateIpv4ToPrefix() {
        assertThat(IpAnonymizer.anonymize("192.168.1.100")).isEqualTo("192.168.1.0");
        assertThat(IpAnonymizer.anonymize("10.0.0.1")).isEqualTo("10.0.0.0");
        assertThat(IpAnonymizer.anonymize("255.255.255.255")).isEqualTo("255.255.255.0");
    }

    @Test
    void shouldTruncateIpv6ToPrefix() {
        assertThat(IpAnonymizer.anonymize("2001:0db8:85a3:0000:0000:0000:0000:0001"))
                .isEqualTo("2001:0db8:85a3::");
        assertThat(IpAnonymizer.anonymize("fe80:0000:0000:0000:0000:0000:0000:0001"))
                .isEqualTo("fe80:0000:0000::");
    }

    @Test
    void shouldHandleShorthandIpv6() {
        assertThat(IpAnonymizer.anonymize("2001:db8:85a3::1")).isEqualTo("2001:0db8:85a3::");
    }

    @Test
    void shouldHandleCompressedIpv6AtStart() {
        assertThat(IpAnonymizer.anonymize("::abcd:ef01")).isEqualTo("0000:0000:0000::");
    }

    @Test
    void shouldHandleCompressedIpv6InMiddle() {
        assertThat(IpAnonymizer.anonymize("2001:db8::dead:beef")).isEqualTo("2001:0db8:0000::");
    }

    @Test
    void shouldHandleCompressedIpv6AtEnd() {
        assertThat(IpAnonymizer.anonymize("2001:db8:85a3::")).isEqualTo("2001:0db8:85a3::");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(IpAnonymizer.anonymize(null)).isNull();
    }

    @Test
    void shouldReturnBlankForBlankInput() {
        assertThat(IpAnonymizer.anonymize("  ")).isEqualTo("  ");
    }
}
