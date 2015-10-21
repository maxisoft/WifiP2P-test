package android.dristributed.wifip2p;

import android.dristributed.wifip2p.gameproto.Comm;
import android.dristributed.wifip2p.gameproto.CommBaseInterface;
import android.dristributed.wifip2p.gameproto.UuidRegister;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CommBase implements Runnable, Comm {
    private final UUID myUuid;
    private final ByteBuffer buffer;
    private final BlockingQueue<RecvObjWrapper> receivedQ;
    private final Map<UUID, BlockingQueue<CommBaseInterface>> sendQs;
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private ServerCallBack callBack;
    private volatile boolean stop = false;
    private volatile boolean stopped = false;

    public CommBase(ServerCallBack callBack){
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        buffer = ByteBuffer.allocate(1024 * 100); //~100Kb
        receivedQ = new LinkedBlockingQueue<>();
        sendQs = new WeakHashMap<>(); //avoid memory leak
        setCallBack(callBack);
        myUuid = UUID.randomUUID();
    }

    public CommBase(){
        this(null);
    }


    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }

    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }

    @Override
    public void run() {
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(4000));
            serverSocket.configureBlocking(false);
            int ops = serverSocket.validOps();
            SelectionKey selectKey = serverSocket.register(selector, ops);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (!stop){
                int readyChannels = selector.select(100);
                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while(keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isAcceptable()) {

                        // Accept the new client connection
                        SocketChannel client = serverSocket.accept();
                        if (callBack != null){
                            callBack.onClientAccepted(client);
                        }
                        // Add the new connection to the selector
                        addSocketChannel(client);
                    } else if (key.isReadable()) {
                        // Read the data from client
                        SocketChannel client = (SocketChannel) key.channel();
                        ClientState state = (ClientState) key.attachment();
                        try {
                            int readed = client.read(buffer);
                            if (readed > 0) {
                                CommBaseInterface objReaded = (CommBaseInterface) deserialize(buffer.array());
                                if (objReaded instanceof UuidRegister) {
                                    state.uuid = ((UuidRegister) objReaded).getUuid();
                                    if (callBack != null) {
                                        callBack.onUuidRegister((UuidRegister) objReaded, client);
                                    }
                                }
                                Log.i(CommBase.class.getSimpleName(), "recv: " + objReaded);
                                if (!receivedQ.offer(new RecvObjWrapper(objReaded, state.uuid))) {
                                    //TODO error (should never append 'cause queue is capped at MAX_INT)
                                }
                                //TODO more error check in order to handle bad message serialisation
                            } else {
                                client.close();
                            }

                        } catch (ClosedChannelException e) {
                            e.printStackTrace();
                            client.close();
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                        } finally {
                            buffer.clear();
                        }

                    } else if (key.isWritable()) {
                        // send the data to the client
                        SocketChannel client = (SocketChannel) key.channel();
                        ClientState state = (ClientState) key.attachment();
                        if (!state.myUuidSent) {
                            Log.i(CommBase.class.getSimpleName(), "sending UuidRegister(" + getMyUuid() + ")");
                            client.write(ByteBuffer.wrap(serialize(new UuidRegister(getMyUuid()))));
                            //TODO writted == buffer.position()
                            state.myUuidSent = true;
                        } else if (state.uuid != null) {
                            BlockingQueue<CommBaseInterface> q = sendQs.get(state.uuid);
                            if (q != null) {
                                CommBaseInterface obj = q.poll();
                                if (obj != null) {
                                    try {
                                        Log.i(CommBase.class.getSimpleName(), "sending obj");
                                        int writted = client.write(ByteBuffer.wrap(serialize(obj)));
                                        //TODO writted == buffer.position()
                                    } catch (ClosedChannelException e) {
                                        client.close();
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    keyIterator.remove();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        stopped = true;
        Log.w(CommBase.class.getSimpleName(), "stopped");
    }

    public void stop(){
        this.stop = true;
    }

    public boolean isStopping() {
        return this.stop && !this.stopped;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public UUID getMyUuid() {
        return myUuid;
    }

    public SelectionKey addSocketChannel(SocketChannel channel) throws IOException {
        return addSocketChannel(channel, channel.validOps());
    }

    public SelectionKey addSocketChannel(SocketChannel channel, int interestSet) throws IOException {
        channel.configureBlocking(false);
        SelectionKey ret = channel.register(selector, interestSet, new ClientState());
        selector.wakeup();
        Log.i(CommBase.class.getSimpleName(), "added socket channel " + channel.socket().getInetAddress());
        return ret;
    }

    public void setCallBack(ServerCallBack callBack) {
        this.callBack = callBack;
    }

    @Override
    public boolean send(UUID target, CommBaseInterface object) {
        BlockingQueue<CommBaseInterface> q;
        synchronized (sendQs) {
            q = sendQs.get(target);
            if (q == null) { //create and put a new queue in sendQs
                q = new LinkedBlockingQueue<>();
                sendQs.put(target, q);
            }
        }
        return q.offer(object); //non blocking
    }

    @Override
    public Object recv() throws InterruptedException {
        return receivedQ.take(); //blocking op
    }


    interface ServerCallBack {
        void onClientAccepted(SocketChannel client);

        void onUuidRegister(UuidRegister uuidRegister, SocketChannel client);
    }

    /**
     * Every received Object must be wrapped to this class.
     */
    public static class RecvObjWrapper {
        private final CommBaseInterface data;
        private final UUID source;

        RecvObjWrapper(CommBaseInterface data, UUID source) {
            this.data = data;
            this.source = source;
        }

        public CommBaseInterface getData() {
            return data;
        }

        public UUID getSource() {
            return source;
        }
    }

    /**
     * Hold client information between selector's select calls.
     */
    static class ClientState {
        private UUID uuid;
        private boolean myUuidSent;
    }
}
