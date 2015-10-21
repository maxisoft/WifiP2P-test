package android.dristributed.wifip2p;

import android.content.Context;
import android.content.IntentFilter;
import android.dristributed.wifip2p.gameproto.GameComm;
import android.dristributed.wifip2p.gameproto.UuidRegister;
import android.dristributed.wifip2p.model.Const;
import android.dristributed.wifip2p.model.Game;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements WiFiDirectBroadcastReceiver.CallBackInterface,
        WifiP2pManager.ConnectionInfoListener,
        CommBase.ServerCallBack,
        GameComm.CallBack
{
    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager.Channel mChannel;
    WifiP2pManager mManager;
    CommBase mCommBase;
    Thread mServerThread;
    private volatile boolean discoverServiceRunning;
    private WiFiDirectBroadcastReceiver receiver;
    LinearLayout mMainLayout;
    volatile GraphicBoard mGraphicBoard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Discovering services", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                discoverService();
            }
        });

        mMainLayout = (LinearLayout) findViewById(R.id.main_rel_layout);
        mMainLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float percent = 1.f * event.getX() / v.getWidth();
                    Log.i("P4", "percent: " + percent);
                    mGraphicBoard.onTouchDown(percent);
                    return true;
                }
                return false;
            }
        });


        //create mChannel
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
    }

    public void drawGame() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMainLayout.removeAllViews();
                mMainLayout.addView(mGraphicBoard.createBoardView());
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpnpServices();
        unregisterReceiver(receiver);
        mCommBase.stop();
        //TODO
        try{
            mServerThread.join(100);
        } catch (InterruptedException e) {
            mServerThread.interrupt();
        } finally {
            mServerThread = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(receiver, intentFilter);

        registerService();
        setupDiscoverService();
        discoverService();
        if (mServerThread == null){
            mCommBase = new CommBase(this);
            mServerThread = new Thread(mCommBase);
            mServerThread.start();
        }
        mGraphicBoard = new GraphicBoard();
        drawGame();
    }

    private void stopUpnpServices() {
        if (mManager != null) {
            mManager.cancelConnect(mChannel, null);
            mManager.stopPeerDiscovery(mChannel, null);
            mManager.clearLocalServices(mChannel, null);
            mManager.clearServiceRequests(mChannel, null);
        }
    }

    private void registerService() {
        WifiP2pUpnpServiceInfo serviceInfo = WifiP2pUpnpServiceInfo.newInstance(
                UUID.randomUUID().toString(),
                "urn:schemas-upnp-org:device:GameServer:1", //TODO
                Collections.singletonList("urn:schemas-upnp-org:service:P4:1")); //TODO

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i("WifiP2p", "service added with success");
            }

            @Override
            public void onFailure(int arg0) {
                Log.w("WifiP2p", "failure with err code " + arg0);
            }
        });
    }

    private void setupDiscoverService() {
        mManager.setUpnpServiceResponseListener(mChannel, new WifiP2pManager.UpnpServiceResponseListener() {
            @Override
            public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) {
                Log.i("WifiP2p - UpnP", "services " + uniqueServiceNames);
            }
        });
        //WifiP2pServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        WifiP2pUpnpServiceRequest serviceRequest = WifiP2pUpnpServiceRequest.newInstance("urn:schemas-upnp-org:service:P4:1");
        mManager.addServiceRequest(mChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.i("WifiP2p", "request service ok");
                    }

                    @Override
                    public void onFailure(int code) {
                        Log.w("WifiP2p - setupService", "failure with err code " + code);
                    }
                });
    }

    boolean discoverService() {
        //setupDiscoverService();
        if (discoverServiceRunning) {
            return false;
        }
        discoverServiceRunning = true;
        mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.i("WifiP2p", "discover service ok");
                discoverServiceRunning = false;
            }

            @Override
            public void onFailure(int code) {
                Log.w("WifiP2p discover", "failure with err code " + code);
                discoverServiceRunning = false;
            }
        });
        return true;
    }

    public void connect(String deviceAddress) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void setIsWifiP2pEnabled(boolean enabled) {

    }

    @Override
    public void thisDeviceChanged(WifiP2pDevice device) {
        mManager.requestConnectionInfo(mChannel, this);
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        // InetAddress from WifiP2pInfo struct.
        if (info.groupOwnerAddress == null){
            return;// TODO DISCONNECTED
        }
        try {
            final InetAddress groupOwnerAddress = InetAddress.getByName(info.groupOwnerAddress.getHostAddress());
            Snackbar.make(this.findViewById(android.R.id.content), String.valueOf(groupOwnerAddress), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // After the group negotiation, we can determine the group owner.
            if (info.groupFormed && info.isGroupOwner) {
                // Do whatever tasks are specific to the group owner.
                // One common case is creating a server thread and accepting
                // incoming connections.
            } else if (info.groupFormed) {
                // The other device acts as the client. In this case,
                // you'll want to create a client thread that connects to the group
                // owner.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        InetSocketAddress hostAddress = new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), 4000);
                        try {
                            SocketChannel client = SocketChannel.open(hostAddress);
                            Snackbar.make(MainActivity.this.findViewById(android.R.id.content), "connection ok", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                            mCommBase.addSocketChannel(client);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClientAccepted(SocketChannel client) {
        Log.i("server", "accepted client " + client.socket().getInetAddress());
        Snackbar.make(MainActivity.this.findViewById(android.R.id.content), "accepted client " + client.socket().getInetAddress(), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    @Override
    public void onUuidRegister(UuidRegister uuidRegister, SocketChannel client) {
        Snackbar.make(MainActivity.this.findViewById(android.R.id.content), "bind client " + client.socket().getInetAddress() + " " + uuidRegister.getUuid(), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }


    public static int pixelToDip(int pixelValue, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pixelValue, context.getResources().getDisplayMetrics());
    }

    @Override
    public void onGameMove(byte row, byte player) {
        drawGame();
    }

    class GraphicBoard {
        private final Game game;
        private final GameComm gameComm;

        public GraphicBoard(){
            gameComm = new GameComm(mCommBase, 2, MainActivity.this);
            game = gameComm.getGame();
            new Thread(gameComm).start();//TODO
        }

        public void onTouchDown(float percent) {
            byte row = (byte) Math.floor(Const.ROW_NBR * percent);
            Log.i("P4", "row: " + row);
            if (gameComm.isTokenOwner() && game.getGameLogic().dropDisc(row, (byte) gameComm.myPlayerIndex())){
                drawGame();
                gameComm.notifyGameMove(row);
            }
        }

        public View createBoardView() {

            int width = pixelToDip(750, MainActivity.this);
            int height = pixelToDip(648, MainActivity.this);

            // Create a bitmap with the dimensions we defined above, and with a 16-bit pixel format. We'll
            // get a little more in depth with pixel formats in a later post.
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // Create a paint object for us to draw with, and set our drawing color to red.


            // Create a new canvas to draw on, and link it to the bitmap that we created above. Any drawing
            // operations performed on the canvas will have an immediate effect on the pixel data of the
            // bitmap.
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.parseColor("#3f51b5"));

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setAntiAlias(true);

            for (int i = 0; i < Const.ROW_NBR; i++) {
                for (int j = 0; j < Const.LINE_NBR; j++) {
                    int xmargin = i + 1;
                    int ymargin = j + 1;
                    int xcenter = i * 90 + xmargin * 15 + 45;
                    int ycenter = j * 90 + ymargin * 15 + 45;
                    byte discValue = game.getBoard().getCellAt(j, i);
                    paint.setColor(discValueToColor(discValue));
                    canvas.drawCircle(pixelToDip(xcenter, MainActivity.this), pixelToDip(ycenter, MainActivity.this), pixelToDip(43, MainActivity.this), paint);
                }
            }

            // In order to display this image in our activity, we need to create a new ImageView that we
            // can display.
            ImageView imageView = new ImageView(MainActivity.this);

            // Set this ImageView's bitmap to the one we have drawn to.
            imageView.setImageBitmap(bitmap);

            // Create a simple layout and add our image view to it.
            RelativeLayout layout = new RelativeLayout(MainActivity.this);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.CENTER_IN_PARENT);
            layout.addView(imageView, params);
            //layout.setBackgroundColor(Color.BLACK);

            return layout;
        }

        public int discValueToColor(byte discValue) {
            switch (discValue) {
                case Const.EMPTY_CELL:
                    return Color.BLACK;
                case 0:
                    return Color.parseColor("#ffeb3b"); //YELLOW
                case 1:
                    return Color.parseColor("#f44336"); //RED
                case 2:
                    return Color.parseColor("#4caf50"); //GREEN
            }
            return Color.WHITE;
        }

    }
}
