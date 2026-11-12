package no.trafondart.common;

import no.trafondart.config.AppProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production is browser → Vercel → Render → app, so TRUSTED_PROXY_HOPS=2: Vercel puts the browser's address in
 * X-Forwarded-For and Render appends Vercel's. Anything further left was written by the client and is a lie.
 */
class ClientIpResolverTest {

    private static ClientIpResolver resolver(int hops) {
        return new ClientIpResolver(new AppProperties(null, null, null, new AppProperties.Security(hops), null, null, null));
    }

    private static MockHttpServletRequest request(String remote, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void withoutTrustedProxiesTheHeaderIsIgnored() {
        var a = resolver(0).resolveAddress(request("192.0.2.1", "1.2.3.4"));
        assertThat(a.client()).isEqualTo("192.0.2.1");
        assertThat(a.edge()).isEqualTo("192.0.2.1");
    }

    @ParameterizedTest(name = "[{index}] {1} → client {2}, edge {3}")
    @CsvSource(delimiter = '|', value = {
            // normal path through Vercel and Render
            "10.1.1.1 | 198.51.100.7, 76.76.21.9          | 198.51.100.7 | 76.76.21.9",
            // the client tried to spoof by prepending addresses: ignored
            "10.1.1.1 | 6.6.6.6, 198.51.100.7, 76.76.21.9 | 198.51.100.7 | 76.76.21.9",
            "10.1.1.1 | a, b, c, d, 198.51.100.7, 76.76.21.9 | 198.51.100.7 | 76.76.21.9",
            // someone called Render directly: only one entry, written by Render itself
            "10.1.1.1 | 203.0.113.5                       | 203.0.113.5  | 203.0.113.5",
            // spaces and empty entries are tolerated
            "10.1.1.1 | ' 198.51.100.7 ,76.76.21.9 '      | 198.51.100.7 | 76.76.21.9",
    })
    void countsTrustedHopsFromTheRight(String remote, String xff, String client, String edge) {
        var a = resolver(2).resolveAddress(request(remote, xff));
        assertThat(a.client()).isEqualTo(client);
        assertThat(a.edge()).isEqualTo(edge);
    }

    @Test
    void missingOrBlankHeaderFallsBackToTheSocketAddress() {
        assertThat(resolver(2).resolveAddress(request("10.1.1.1", null)).client()).isEqualTo("10.1.1.1");
        assertThat(resolver(2).resolveAddress(request("10.1.1.1", "  ")).client()).isEqualTo("10.1.1.1");
        assertThat(resolver(2).resolveAddress(request("10.1.1.1", " , ,")).client()).isEqualTo("10.1.1.1");
    }

    @Test
    void absurdlyLongHeadersAreCappedNotTrusted() {
        String junk = "1.1.1.1,".repeat(5000) + "198.51.100.7, 76.76.21.9";
        var a = resolver(2).resolveAddress(request("10.1.1.1", junk));
        assertThat(a.client()).isEqualTo("198.51.100.7");
        assertThat(a.client().length()).isLessThan(64);
    }

    @Test
    void anEntryThatIsNotAnAddressIsReplacedByTheEdge() {
        // keys must stay short and boring – never let a client choose an arbitrary rate-limit key
        var a = resolver(2).resolveAddress(request("10.1.1.1", "x".repeat(300) + ", 76.76.21.9"));
        assertThat(a.client()).isEqualTo("76.76.21.9");
    }
}
