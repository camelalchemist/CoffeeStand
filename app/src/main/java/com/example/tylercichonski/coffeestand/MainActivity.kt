package com.example.tylercichonski.coffeestand

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.gms.common.server.converter.StringToIntConverter
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    //bluetooth data
    companion object {
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String
    }



    lateinit var db: DocumentReference
    //constants for Firestore Document Data Fields
    val AMOUNT_IN_KEG ="amountInKeg"
    val AMOUNT_DISPENSED = "amount dispensed"
    val MAC_ADDRESS = "macAddress"
    val LOCATION = "location"
    //variables to hold Firestore Data Fields
    var amountInKeg: Any? = 0
    lateinit var location: String
    lateinit var macAddress: String
    var amountdispensed: Int = 0
    //Hashmap used to upload back to Firestore
    val items = HashMap<String,Any>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance().document("Locations/Home")//pull firstore data
        db.get().addOnSuccessListener(OnSuccessListener <DocumentSnapshot>{ documentSnapshot -> //set variables to firestore data
            amountInKeg = documentSnapshot.get(AMOUNT_IN_KEG)
            location = documentSnapshot.get(LOCATION) as String
            macAddress= documentSnapshot.get(MAC_ADDRESS) as String
            m_address = macAddress
            ConnectToDevice(this).execute()

            control_led_on.setOnClickListener { sendCommand("3") }
            control_led_off.setOnClickListener { sendCommand("4") }
            //control_led_disconnect.setOnClickListener { disconnect() }




        })

    }




    //onclickhandler for btton. This will be replaced by the data output by the arduino
    fun amountDispensedButtonClicked(view: View){
        var amountDispensed = editAmountDispensed.text.toString()
        var dblAmountDispensed = amountDispensed.toDouble()
        var stringAmountInKeg = amountInKeg.toString()
        var dblAmountInKeg = stringAmountInKeg.toDouble()
        amountInKeg = dblAmountInKeg.minus(dblAmountDispensed)
        println(amountInKeg as Double)
        items.put(AMOUNT_IN_KEG, amountInKeg as Double) //load data into hashmap so it can be sent back to firestore
        items.put(LOCATION, location)
        items.put(MAC_ADDRESS, macAddress)
        db.set(items)

    }
    private fun sendCommand(input: String) {
        if (m_bluetoothSocket != null) {
            try{
                m_bluetoothSocket!!.outputStream.write(input.toByteArray())
            } catch(e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun disconnect() {
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context

        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                if (m_bluetoothSocket == null || !m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
            } else {
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }
}



