package android.dristributed.wifip2p.gameproto;

import java.util.UUID;

public class UuidRegister implements CommBaseInterface {
    private final UUID uuid;

    public UuidRegister(UUID uuid) {
        this.uuid = uuid;
    }
}
