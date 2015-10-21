package android.dristributed.wifip2p.gameproto;

import java.util.UUID;

public interface Comm {
    boolean send(UUID target, CommBaseInterface object);

    Object recv() throws InterruptedException; //blocking
}
