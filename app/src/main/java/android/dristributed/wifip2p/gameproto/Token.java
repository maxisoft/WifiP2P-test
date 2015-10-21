package android.dristributed.wifip2p.gameproto;

import java.util.concurrent.atomic.AtomicInteger;

public class Token implements CommBaseInterface {
    private final AtomicInteger clock = new AtomicInteger(0);

    public int getClock() {
        return clock.get();
    }

    public int incrClock() {
        return clock.incrementAndGet();
    }
}
