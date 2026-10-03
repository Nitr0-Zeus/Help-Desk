package com.helpdesk.common.time;

import java.time.Instant;

public interface TimeProvider {
    Instant now();
}
