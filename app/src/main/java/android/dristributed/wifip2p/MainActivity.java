package android.dristributed.wifip2p;

import android.content.Context;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private WifiP2pDnsSdServiceInfo mServiceInfo;
    private Map<String, String> mDnsRecord = new HashMap<>();


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

                String count = mDnsRecord.get("count");
                if (count != null) {
                    mDnsRecord.put("count", String.valueOf(Long.parseLong(count) + 1));
                } else {
                    mDnsRecord.put("count", String.valueOf(1));
                }

                Snackbar.make(view, "Dns record updated " + mDnsRecord.get("count"), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                registerService();
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
        if (mServiceInfo != null) {
            mManager.removeLocalService(mChannel, mServiceInfo, null);
        }

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance("_test" + UUID.randomUUID(), "_wifip2p._hack", mDnsRecord);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        mManager.addLocalService(mChannel, mServiceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i("DNS-SD", "service added !");
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            }
        });

    }

    private void setupDiscoverService() {
        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(
                    String fullDomain, Map record, WifiP2pDevice device) {
                Log.i("DNS-SD", "DnsSdTxtRecord available -" + record.toString());
                Toast.makeText(MainActivity.this, "DnsSdTxtRecord available -" + record.toString(), Toast.LENGTH_LONG).show();
                addPeer(device.deviceAddress);
            }
        };
        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                WifiP2pDevice resourceType) {
            }
        };

        mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);
        WifiP2pServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(mChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // Success!
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    }
                });

    }

    boolean discoverService() {
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
