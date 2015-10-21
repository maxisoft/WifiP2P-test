package android.dristributed.wifip2p;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Server implements Runnable{
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private ServerCallBack callBack;
    private volatile boolean stop = false;
    public Server(ServerCallBack callBack){
        setCallBack(callBack);
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public Server(){
        this(null);
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
                int readyChannels  = selector.select();
                if(readyChannels == 0) continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while(keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isAcceptable()) {

                        // Accept the new client connection
                        SocketChannel client = serverSocket.accept();
                        client.configureBlocking(false);
                        if (callBack != null){
                            callBack.onClientAccepted(client);
                        }


                        // Add the new connection to the selector
                        client.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {

                        // Read the data from client
                        SocketChannel client = (SocketChannel) key.channel();
                    }
                    keyIterator.remove();
                }
            }

        } catch (IOException e) {
        }
    }

    public void stop(){
        this.stop = true;
    }

    public void setCallBack(ServerCallBack callBack) {
        this.callBack = callBack;
    }

    interface ServerCallBack {
        void onClientAccepted(SocketChannel client);
    }
}
