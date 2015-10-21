package android.dristributed.wifip2p.gameproto;

import java.util.Objects;
import java.util.UUID;

/**
 * Created by maxime on 21/10/2015.
 */
public interface Comm {
    boolean send(UUID target, CommBaseInterface object);
    Object recv(); //blocking
}
