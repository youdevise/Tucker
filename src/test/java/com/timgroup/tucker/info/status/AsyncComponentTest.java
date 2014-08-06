package com.timgroup.tucker.info.status;

import static com.timgroup.tucker.info.Status.OK;
import static com.timgroup.tucker.info.Status.WARNING;
import static java.util.Calendar.JULY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.timgroup.tucker.info.Component;
import com.timgroup.tucker.info.Report;
import com.timgroup.tucker.info.status.AsyncComponent.Clock;
import com.timgroup.tucker.info.status.AsyncComponent.Consumer;

public class AsyncComponentTest {

    @Test
    public void returnsIdAndLabelOfWrappedComponent() {
        AsyncComponent asyncComponent = AsyncComponent.wrapping(healthyWellBehavedComponent());
        assertEquals("my-test-component-id", asyncComponent.getId());
        assertEquals("My Test Component Label", asyncComponent.getLabel());
    }
    
    @Test
    public void returnsPendingReportForWrappedComponentThatHasNotReturnedYet() {
        AsyncComponent asyncComponent = AsyncComponent.wrapping(healthyWellBehavedComponent());

        Report report = asyncComponent.getReport();

        assertEquals(report.getStatus(), WARNING);
        assertEquals(report.getValue(), "Pending");
    }
    
    private Component healthyWellBehavedComponent() {
        return new Component("my-test-component-id", "My Test Component Label") {
            @Override public Report getReport() {
                return new Report(OK, "It's all good.");
            }
        };
    };

    @Test
    public void returnsWarningStatusWhenReportHasNeverBeenReturnedWithinTimeThreshold() {
        Date initialisation = minutesAfterInitialisation(0);
        Date sixMinutesLater = minutesAfterInitialisation(6);
        
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(initialisation, sixMinutesLater);

        TestingSemaphore invoked = new TestingSemaphore();
        AsyncComponent asyncComponent = AsyncComponent.wrapping(neverReturnsComponent(invoked),
                AsyncComponent.settings()
                .withClock(clock)
                .withRepeatSchedule(1, NANOSECONDS)
                .withStalenessLimit(4, MINUTES));
        
        schedule(asyncComponent);
        
        invoked.waitFor("Component to be invoked");
        Report report = asyncComponent.getReport();

        assertEquals(WARNING, report.getStatus());
        assertThat(
            report.getValue().toString(),
            containsString("Last run at 2014-07-12T01:00:00 (over 4 minutes ago): Pending"));
    }

    private void schedule(AsyncComponent asyncComponent) {
        AsyncComponentScheduler scheduler = new AsyncComponentScheduler(Arrays.asList(asyncComponent));
        scheduler.start();
    }
    
    private Component neverReturnsComponent(final TestingSemaphore invoked) {
        return new Component("my-never-returning-component-id", "My Never Returning Component") {
            @Override public Report getReport() {
                try {
                    invoked.completed();
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                throw new IllegalStateException("Should never have completed");
            }
        };
    }

    @Test
    public void returnsWarningStatusForStaleReport() throws Exception {
        Date initialised = minutesAfterInitialisation(0);
        Date oneMinuteLater = minutesAfterInitialisation(1);
        Date threeMinutesLater = minutesAfterInitialisation(3);
        Date tenMinutesLater = minutesAfterInitialisation(10);

        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(initialised, oneMinuteLater, oneMinuteLater, threeMinutesLater, threeMinutesLater, tenMinutesLater);

        final TestingSemaphore componentUpdated = new TestingSemaphore();
        final TestingSemaphore reportAsserted = new TestingSemaphore();
        Consumer statusUpdated = new Consumer() {
            @Override public void apply(Report report) {
                componentUpdated.completed();
                reportAsserted.waitFor("assertion to be checked");
            }
            
        };
        AsyncComponent asyncComponent = AsyncComponent.wrapping(nthCallNeverReturns(2),
                AsyncComponent.settings()
                .withClock(clock)
                .withRepeatSchedule(1, NANOSECONDS)
                .withUpdateHook(statusUpdated));
        
        schedule(asyncComponent);

        componentUpdated.waitFor("Component to be invoked");
        assertEquals(new Report(OK, "Everything's fine"), asyncComponent.getReport());
        reportAsserted.completed();

        componentUpdated.waitFor("Component to be invoked");
        assertEquals(new Report(OK, "Everything's fine"), asyncComponent.getReport());
        reportAsserted.completed();

        Report report = asyncComponent.getReport();

        assertEquals(
                new Report(WARNING, "Last run at 2014-07-12T01:03:00 (over 5 minutes ago): Everything's fine"),
                report);

    }
    
    private Component nthCallNeverReturns(final int callsThatWillReturnQuickly) {
        return new Component("my-eventually-never-returns-component-id", "My Eventually Never Returns Component") {
            private final Semaphore quickReturnSemaphore = new Semaphore(callsThatWillReturnQuickly);

            @Override public Report getReport() {
                try {
                    quickReturnSemaphore.acquire();
                    return new Report(OK, "Everything's fine");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Test
    public void returnsReportCreatedByWrappedComponent() {
        final TestingSemaphore componentInvoked = new TestingSemaphore();
        Consumer onUpdate = new Consumer() {
            @Override public void apply(Report report) {
                componentInvoked.completed();
            }
        };
        AsyncComponent asyncComponent = AsyncComponent.wrapping(fastComponent(),
                AsyncComponent.settings().withRepeatSchedule(1, NANOSECONDS).withUpdateHook(onUpdate));
        schedule(asyncComponent);
        
        componentInvoked.waitFor("Component to be invoked");
        
        assertEquals(
            new Report(OK, "Quickly returned"),
            asyncComponent.getReport());
    }
    
    private Component fastComponent() {
        return new Component("my-fast-component-id", "My Fast Component") {
            @Override public Report getReport() {
                return new Report(OK, "Quickly returned");
            }
        };
    }
    
    @Test
    public void reschedulesUpdateAfterComponentThrowsException() {
        final TestingSemaphore componentInvoked = new TestingSemaphore();
        final TestingSemaphore assertionSemaphore = new TestingSemaphore();
        Consumer onUpdate = new Consumer() {
            @Override public void apply(Report report) {
                componentInvoked.completed();
                assertionSemaphore.waitFor("assertion to be checked");
            }
        };

        AsyncComponent asyncComponent = AsyncComponent.wrapping(initiallyThrowsExceptionComponent(),
                AsyncComponent.settings().withRepeatSchedule(1, NANOSECONDS).withUpdateHook(onUpdate));
        
        schedule(asyncComponent);
        
        componentInvoked.waitFor("Component to be invoked");
        Report report = asyncComponent.getReport();
        
        assertEquals(WARNING, report.getStatus());
        assertThat(report.getException(), is(instanceOf(IllegalStateException.class)));
        assertionSemaphore.completed();
        
        componentInvoked.waitFor("Component to be invoked");
        assertEquals(new Report(OK, "Recovered"), asyncComponent.getReport());
    }
    
    private Component initiallyThrowsExceptionComponent() {
        return new Component("my-fast-component-id", "My Fast Component") {
            private AtomicInteger timesCalled = new AtomicInteger(0);

            @Override public Report getReport() {
                if (timesCalled.getAndIncrement() == 0) {
                    throw new IllegalStateException("Thrown by component");
                }
                return new Report(OK, "Recovered");
            }
        };
    }

    @Test
    public void reschedulesUpdateAfterUpdateHookThrowsException() {
        final TestingSemaphore componentInvoked = new TestingSemaphore();
        Consumer onUpdate = new Consumer() {
            private final AtomicInteger timesCalled = new AtomicInteger(0);
            @Override public void apply(Report report) {
                componentInvoked.completed();
                if (timesCalled.getAndIncrement() == 0) {
                    throw new IllegalArgumentException("Thrown by update hook");
                }
            }
        };

        AsyncComponent asyncComponent = AsyncComponent.wrapping(
                healthyWellBehavedComponent(),
                AsyncComponent.settings().withRepeatSchedule(1, NANOSECONDS).withUpdateHook(onUpdate));
        
        schedule(asyncComponent);
        
        componentInvoked.waitFor("Component to be invoked");
        assertEquals(new Report(OK, "It's all good."), asyncComponent.getReport());
        
        componentInvoked.waitFor("Component to be invoked");
        assertEquals(new Report(OK, "It's all good."), asyncComponent.getReport());
    }
    
    private Date minutesAfterInitialisation(int minutes) {
        Calendar calender = Calendar.getInstance();
        calender.setTimeZone(TimeZone.getTimeZone("UTC"));
        calender.set(2014, JULY, 12, 1, minutes, 0);
        return calender.getTime();
    }
    
    @Test
    public void doesNotRescheduleWhenAnErrorIsThrownDuringUpdate() {
        final TestingSemaphore componentInvoked = new TestingSemaphore();

        AsyncComponent asyncComponent = AsyncComponent.wrapping(
                throwsErrorComponent(componentInvoked),
                AsyncComponent.settings().withRepeatSchedule(1, NANOSECONDS));
        
        schedule(asyncComponent);
        
        componentInvoked.waitFor("Component to be invoked");
        
        assertFalse(componentInvoked.completedAgainIn(10, MILLISECONDS));
    }
    
    private Component throwsErrorComponent(final TestingSemaphore componentInvoked) {
        return new Component("my-fast-component-id", "My Fast Component") {
            @Override public Report getReport() {
                componentInvoked.completed();
                throw new NoSuchMethodError("Unrecoverable error from component");
            }
        };
    }
}
