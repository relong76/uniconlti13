package net.unicon.lti.service.app;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.unicon.lti.model.LaunchRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Completed launches by their nonce, which the API token the app gets carries. Kept for as long
 * as that token lasts; after that the user launches again from the LMS.
 */
@Component
public class LaunchStore {

    static final Duration TIME_TO_LIVE = Duration.ofHours(1);

    private static final long MAXIMUM_SIZE = 10_000;

    private final Cache<String, LaunchRecord> launches = CacheBuilder.newBuilder()
        .expireAfterWrite(TIME_TO_LIVE)
        .maximumSize(MAXIMUM_SIZE)
        .build();

    public void put(String nonce, LaunchRecord launch) {
        launches.put(nonce, launch);
    }

    public Optional<LaunchRecord> get(String nonce) {
        return nonce == null ? Optional.empty() : Optional.ofNullable(launches.getIfPresent(nonce));
    }

}
