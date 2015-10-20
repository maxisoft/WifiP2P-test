package android.dristributed.wifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    WifiP2pManager.Channel mChannel;
    WifiP2pManager mManager;
    CallBackInterface mCallBack;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, CallBackInterface callBack) {
        mChannel = channel;
        mManager = manager;
        mCallBack = callBack;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            mCallBack.setIsWifiP2pEnabled(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // The peer list has changed!  We should probably do something about
            // that.

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            // Connection state changed!  We should probably do something about
            // that.

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            mCallBack.thisDeviceChanged(device);

            /*if (mManager == null) {
                Log.w(String.valueOf(WiFiDirectBroadcastReceiver.class), "no wifip2p manager");
                return;
            }


            mCallBack.thisDeviceChanged(device);
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo == null) Log.w(String.valueOf(WiFiDirectBroadcastReceiver.class), "no networkInfo");
            if (device != null || (networkInfo != null && networkInfo.isConnected())) {

                // We are connected with the other device, request connection
                // info to find group owner IP

                mManager.requestConnectionInfo(mChannel, mCallBack);
            }*/
        }

    }

    public interface CallBackInterface extends WifiP2pManager.ConnectionInfoListener{
        void setIsWifiP2pEnabled(boolean enabled);
        void thisDeviceChanged(WifiP2pDevice device);
    }
}
