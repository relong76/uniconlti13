package net.unicon.lti.service.app;

import net.unicon.lti.model.LaunchRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LaunchStoreTest {

    private final LaunchStore launchStore = new LaunchStore();

    @Test
    public void testKeepsALaunchByItsNonce() {
        LaunchRecord launch = new LaunchRecord("id-token", "client-1", Map.of("sub", "user-1"), null, Instant.now());

        launchStore.put("nonce-1", launch);

        assertSame(launch, launchStore.get("nonce-1").orElseThrow());
        assertTrue(launchStore.get("nonce-2").isEmpty());
        assertTrue(launchStore.get(null).isEmpty());
    }

    @Test
    public void testKeepsLaunchesAsLongAsTheApiToken() {
        // APIJWTServiceImpl issues API tokens for 3600 seconds
        assertEquals(Duration.ofHours(1), LaunchStore.TIME_TO_LIVE);
    }

}
