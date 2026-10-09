package com.example.mediavault.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Old writes are available only in isolated compatibility tests, not in normal deployments. */
@Component
public class LegacyWritePolicy {
    private final boolean enabled;
    public LegacyWritePolicy(@Value("${vault.legacy-writes-enabled:false}") boolean enabled) { this.enabled = enabled; }
    public void requireEnabled() {
        if (!enabled) throw new ApiException(HttpStatus.CONFLICT,"LEGACY_READ_ONLY","旧标签接口已只读，请通过六维管理更新收藏");
    }
}
