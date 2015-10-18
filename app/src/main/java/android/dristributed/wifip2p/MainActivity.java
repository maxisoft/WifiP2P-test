package android.dristributed.wifip2p;

import android.content.Context;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final int SERVER_PORT = 3000;
    final List<String> mPeerList = new ArrayList<String>();
    WifiP2pManager.Channel mChannel;
    WifiP2pManager mManager;
    ;
    ListView mPeerListView;
    ArrayAdapter<String> mAdapter;
    private volatile boolean discoverServiceRunning;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                discoverService();
            }
        });
        mPeerListView = (ListView) findViewById(R.id.peer_list);

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mPeerList);
        mPeerListView.setAdapter(mAdapter);
        mPeerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                final String item = (String) parent.getItemAtPosition(position);
                connect(item);
            }
        });


        //create mChannel
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
    }

    private boolean addPeer(String peerAddress) {
        synchronized (mPeerList) {
            int index = Collections.binarySearch(mPeerList, peerAddress);
            if (index < 0) {
                mAdapter.insert(peerAddress, -index - 1);
                mAdapter.notifyDataSetChanged();
                return true;
            }
            return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mManager != null) {
            mManager.cancelConnect(mChannel, null);
            mManager.stopPeerDiscovery(mChannel, null);
            mManager.clearLocalServices(mChannel, null);
            mManager.clearServiceRequests(mChannel, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerService();
        setupDiscoverService();
        discoverService();
    }

    private void registerService() {
        WifiP2pUpnpServiceInfo serviceInfo = WifiP2pUpnpServiceInfo.newInstance(
                UUID.randomUUID().toString(),
                "urn:schemas-upnp-org:device:MediaServer:1", //TODO
                Collections.singletonList("urn:schemas-upnp-org:service:ContentDirectory:1")); //TODO

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
                addPeer(srcDevice.deviceAddress);
            }
        });
        //WifiP2pServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        WifiP2pUpnpServiceRequest serviceRequest = WifiP2pUpnpServiceRequest.newInstance();
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
}
