package com.example.myapplication

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.myapplication.classes.PreprocessedTable
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.utils.ImageVectorizer
import com.example.myapplication.utils.SocketHandler
import com.example.myapplication.utils.WiFiDirectBroadcastReceiver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


class MainActivity : AppCompatActivity(), ImageVectorizer.ImageVectorizerListener {
    private var _activityMainBinding: ActivityMainBinding? = null

    private val activityMainBinding
        get() = _activityMainBinding!!

    private lateinit var imageVectorizer: ImageVectorizer
    private lateinit var preprocessedTable: PreprocessedTable

    private val intentFilter = IntentFilter()
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var manager: WifiP2pManager
    private lateinit var receiver: WiFiDirectBroadcastReceiver

    val PORT_USED = 8889

    var custom_peers: ArrayList<CustomDevice> = ArrayList()
    var serverClass: ServerClass? = null
    var clientClass: ClientClass? = null
    var connectionStatus: TextView? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ),
                0
            )
            Log.d(TAG, "PERMISSION")
        }

        initializeImageVectorizer()

        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        activityMainBinding.startConnect.setOnClickListener {
            checkLocationEnabled()
            startSearching()
        }

        connectionStatus = activityMainBinding.connectionStatus
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) Log.d(TAG, grantResults.contentToString())
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    // ImageVectorizer listener functions
    override fun onTableReady(preprocessedTable: PreprocessedTable) {
        this.preprocessedTable = preprocessedTable
    }

    private fun initializeImageVectorizer() {
        imageVectorizer = ImageVectorizer(this, this)
    }

    // P2P
    fun checkLocationEnabled() {
        val lm = this@MainActivity.getSystemService(LOCATION_SERVICE) as LocationManager
        var gps_enabled = false
        var network_enabled = false

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) { }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) { }

        if (!gps_enabled && !network_enabled) {
            // notify user
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.gps_network_not_enabled_title)
                .setMessage(R.string.gps_network_not_enabled)
                .setPositiveButton(R.string.open_location_settings) { _, _ ->
                        this@MainActivity.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                .setNegativeButton(R.string.Cancel, null)
                .show()
        }
    }

    private fun startSearching() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                connectionStatus?.text = "Discovery Started"
            }

            override fun onFailure(reason: Int) {
                connectionStatus?.text = "Discovery start Failed, ${reason}"
            }
        })
    }

    var peerListListener =
        PeerListListener { peersList ->
            Log.d("DEVICE_NAME", "Listener called " + peersList.deviceList.size)
            if (peersList.deviceList.isNotEmpty()) {
                // first make a list of all devices already present
                val deviceAlreadyPresent: ArrayList<CustomDevice> = ArrayList()
                for (device in peersList.deviceList) {
                    val idx: Int = checkPeersListByName(device.deviceName)
                    Log.d(TAG, idx.toString())
                    if (idx != -1) {
                        // device already in list
                        deviceAlreadyPresent.add(custom_peers[idx])
                    }
                }
                if (deviceAlreadyPresent.size == peersList.deviceList.size) {
                    // all discovered devices already present
                    return@PeerListListener
                }

                // clear previous views
                clearAllDeviceIcons()

                // this will remove all devices no longer in range
                custom_peers.clear()
                // add all devices in range
                custom_peers.addAll(deviceAlreadyPresent)

                // add all already present devices to the view
                for (d in deviceAlreadyPresent) {
                    if (d.iconView?.parent != null)
                        (d.iconView?.parent as ViewGroup).removeView(d.iconView)

                    activityMainBinding.scannedDevices.addView(d.iconView)
                }
                for (device in peersList.deviceList) {
                    if (checkPeersListByName(device.deviceName) == -1) {
                        // device not already present
                        val tmpDevice: View = createNewDevice(device.deviceName)
                        activityMainBinding.scannedDevices.addView(tmpDevice)
                        foundDevice(tmpDevice)
                        val tmpDeviceObj = CustomDevice()
                        tmpDeviceObj.deviceName = device.deviceName
                        tmpDeviceObj.id = tmpDevice.id
                        tmpDeviceObj.device = device
                        tmpDeviceObj.iconView = tmpDevice
                        custom_peers.add(tmpDeviceObj)
                    }
                }
            } else {
                Toast.makeText(applicationContext, "No Peers Found", Toast.LENGTH_SHORT).show()
            }
        }

    var connectionInfoListener =
        ConnectionInfoListener { info ->
            val groupOwnerAddress = info.groupOwnerAddress
            if (info.groupFormed && info.isGroupOwner) {
                connectionStatus?.text = "HOST"
                serverClass = ServerClass()
                serverClass!!.start()
            } else if (info.groupFormed) {
                connectionStatus?.text = "CLIENT"
                clientClass = ClientClass(groupOwnerAddress)
                clientClass!!.start()
            }
        }

    private fun getIndexFromIdPeerList(id: Int): Int {
        for (d in custom_peers) {
            if (d.id == id) {
                return custom_peers.indexOf(d)
            }
        }
        return -1
    }

    fun clearAllDeviceIcons() {
        if (custom_peers.isNotEmpty()) {
            for (d in custom_peers) {
                Log.d("clearAllDeviceIcons", d.iconView.toString())
                activityMainBinding.scannedDevices.removeView(d.iconView)
            }
        }
    }

    fun createNewDevice(deviceName: String): View {
        val device1: View = LayoutInflater.from(this).inflate(R.layout.device_icon, null)
        val txtDevice1 = device1.findViewById<TextView>(R.id.deviceText)
        val deviceId: Int = System.currentTimeMillis().toInt() + device_count++
        txtDevice1.text = deviceName
        device1.id = deviceId
        device1.setOnClickListener {
            Log.d(TAG, "onClick ${it.id}")
            val idx = getIndexFromIdPeerList(it.id)
            val device = custom_peers[idx].device!!
            val config = WifiP2pConfig()
            config.deviceAddress = device.deviceAddress

            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        applicationContext,
                        "Connected to " + device.deviceName,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        applicationContext,
                        "Error in connecting to " + device.deviceName,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
        device1.visibility = View.INVISIBLE
        return device1
    }

    private fun foundDevice(foundDevice: View) {
        val animatorSet = AnimatorSet()
        animatorSet.duration = 400
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        val animatorList = ArrayList<Animator>()
        val scaleXAnimator = ObjectAnimator.ofFloat(foundDevice, "ScaleX", 0f, 1.2f, 1f)
        animatorList.add(scaleXAnimator)
        val scaleYAnimator = ObjectAnimator.ofFloat(foundDevice, "ScaleY", 0f, 1.2f, 1f)
        animatorList.add(scaleYAnimator)
        animatorSet.playTogether(animatorList)
        foundDevice.visibility = View.VISIBLE
        animatorSet.start()
    }

    private fun checkPeersListByName(deviceName: String): Int {
        for (d in custom_peers) {
            if (d.deviceName == deviceName) {
                return custom_peers.indexOf(d)
            }
        }
        return -1
    }

    inner class ServerClass : Thread() {
        private var socket: Socket? = null
        private var serverSocket: ServerSocket? = null

        override fun run() {
            try {
                serverSocket = ServerSocket(PORT_USED)
                socket = serverSocket!!.accept()
                SocketHandler.socket = socket

                val intent = Intent(applicationContext, MomentMeldActivity::class.java)
                intent.putExtra("preprocessedTable", Json.encodeToString(preprocessedTable))
                intent.putExtra("role", HOST)
                startActivity(intent)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    inner class ClientClass internal constructor(address: InetAddress) : Thread() {
        private var socket: Socket = Socket()
        private var hostAddress = address.hostAddress

        override fun run() {
            try {
                socket.connect(InetSocketAddress(hostAddress, PORT_USED), 500)
                SocketHandler.socket = socket

                val intent = Intent(applicationContext, MomentMeldActivity::class.java)
                intent.putExtra("preprocessedTable", Json.encodeToString(preprocessedTable))
                intent.putExtra("role", CLIENT)
                startActivity(intent)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val PREPROCESSED_FILE_NAME = "preprocessedTable.json"
        private var device_count = 0
        const val HOST = "HOST"
        const val CLIENT = "CLIENT"
    }
}

class CustomDevice {
    var id = 0
    var deviceName: String? = null
    var device: WifiP2pDevice? = null
    var iconView: View? = null
}