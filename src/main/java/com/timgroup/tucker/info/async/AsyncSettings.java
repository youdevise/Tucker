package com.timgroup.tucker.info.async;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class AsyncSettings {
    final Clock clock;
    final Duration repeatInterval;
    final StatusUpdated statusUpdateHook;
    final Duration stalenessLimit;

    private AsyncSettings(Clock clock, Duration repeatInterval, StatusUpdated statusUpdateHook, Duration stalenessLimit) {
        this.clock = clock;
        this.repeatInterval = repeatInterval;
        this.statusUpdateHook = statusUpdateHook;
        this.stalenessLimit = stalenessLimit;
    }

    public static AsyncSettings settings() {
        return new AsyncSettings(Clock.systemDefaultZone(), Duration.ofSeconds(30), StatusUpdated.NOOP, Duration.ofMinutes(5));
    }

    public AsyncSettings withClock(@SuppressWarnings("hiding") Clock clock) {
        return new AsyncSettings(clock, repeatInterval, statusUpdateHook, stalenessLimit);
    }

    public AsyncSettings withRepeatSchedule(long time, TimeUnit units) {
        return new AsyncSettings(clock, Duration.ofNanos(units.toNanos(time)), statusUpdateHook, stalenessLimit);
    }

    public AsyncSettings withRepeatSchedule(Duration interval) {
        return new AsyncSettings(clock, interval, statusUpdateHook, stalenessLimit);
    }

    public AsyncSettings withUpdateHook(StatusUpdated statusUpdated) {
        return new AsyncSettings(clock, repeatInterval, statusUpdated, stalenessLimit);
    }

    public AsyncSettings withStalenessLimit(long time, TimeUnit units) {
        return new AsyncSettings(clock, repeatInterval, statusUpdateHook, Duration.ofNanos(units.toNanos(time)));
    }

    public AsyncSettings withStalenessLimit(Duration duration) {
        return new AsyncSettings(clock, repeatInterval, statusUpdateHook, duration);
    }
}
