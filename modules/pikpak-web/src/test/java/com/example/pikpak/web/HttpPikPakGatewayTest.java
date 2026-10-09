package com.example.pikpak.web;

import com.example.pikpak.PikPakException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class HttpPikPakGatewayTest {
    @Test void proxyConfigurationIsExplicitAndDoesNotLeakSecrets(){
        assertEquals(7897, HttpPikPakGateway.proxyAddress("http://127.0.0.1:7897").getPort());
        for(String value : new String[]{"http://localhost", "socks5://localhost:7897", "http://user:secret@localhost:7897", "http://localhost:7897/path", "http://localhost:7897?token=secret"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> HttpPikPakGateway.proxyAddress(value));
            assertFalse(error.getMessage().contains("secret"));
        }
    }
    @Test void onlyKnownMediaHostShapesAreAccepted(){
        assertNotNull(HttpPikPakGateway.mediaUri("https://dl-a10b-1234.mypikpak.com/path?token=secret"));
        for(String url:new String[]{"http://dl-a1.mypikpak.com/path","https://mypikpak.com.evil.test/file","https://127.0.0.1/a","https://user@dl-a1.mypikpak.com/x","https://dl-a1.mypikpak.com:444/x"})
            assertThrows(IllegalArgumentException.class,()->HttpPikPakGateway.mediaUri(url));
    }
    @Test void accessErrorsAreNotReturnedAsEmptySuccess(){
        var json=JsonMapper.builder().build();
        for(String state:new String[]{"PASS_CODE_EMPTY","PASS_CODE_ERROR","PROHIBITED","DELETED"})
            assertThrows(PikPakException.class,()->HttpPikPakGateway.check(json.readTree("{\"share_status\":\""+state+"\"}")));
        assertDoesNotThrow(()->HttpPikPakGateway.check(json.readTree("{\"share_status\":\"OK\"}")));
    }
    @Test void errorsNeverExposeRemoteDescriptions(){
        var node=JsonMapper.builder().build().readTree("{\"error_code\":10,\"error_description\":\"secret-token\"}");
        var error=assertThrows(PikPakException.class,()->HttpPikPakGateway.check(node));
        assertFalse(error.getMessage().contains("secret-token"));
    }
}
