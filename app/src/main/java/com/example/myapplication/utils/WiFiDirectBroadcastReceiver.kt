package com.example.myapplication.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.example.myapplication.MainActivity


/**
 * A BroadcastReceiver that notifies of important Wi-Fi p2p events.
 */
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {
    private val TAG = "WiFiDirectBroadcastReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    activity.connectionStatus?.text = "WIFI ON"
                else
                    activity.connectionStatus?.text = "WIFI OFF"
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // Call WifiP2pManager.requestPeers() to get a list of current peers+
                manager.requestPeers(channel, activity.peerListListener)
                Log.e("DEVICE_NAME", "WIFI P2P peers changed called")
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                if (manager == null) {
                    return
                }

                val networkInfo: NetworkInfo =
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                if (networkInfo.isConnected) {
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                } else {
                    activity.connectionStatus?.text = "Device Disconnected"
                    activity.clearAllDeviceIcons()
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
            }
        }
    }
}
