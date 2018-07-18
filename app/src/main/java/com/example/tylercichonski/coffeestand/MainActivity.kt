package com.example.tylercichonski.coffeestand

import android.app.Activity
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v7.app.AlertDialog
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.tylercichonski.coffeestand.R.id.*
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.sdk.pos.ChargeRequest
import com.squareup.sdk.pos.CurrencyCode
import com.squareup.sdk.pos.PosClient
import com.squareup.sdk.pos.PosSdk
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity() {
    // Firebase instance variables
  // Firebase instance variables

    private val APPLICATION_ID = "sq0idp-AWZpT2Eg011bOSisdxzKeA"
    private val CHARGE_REQUEST_CODE = 1
    companion object {
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String
        var valveController: Boolean = false
    }

    lateinit var db: DocumentReference
    lateinit var fs: DocumentReference
    lateinit var locations: CollectionReference
    //constants for Firestore Document Data Fields
    val AMOUNT_IN_KEG ="amountInKeg"
    val AMOUNT_DISPENSED = "amount dispensed"
    val MAC_ADDRESS = "macAddress"
    val LOCATION = "location"
    //variables to hold Firestore Data Fields
    var amountInKeg: Any? = 0
    lateinit var macAddress: String
    //Hashmap used to upload back to Firestore
    val items = HashMap<String,Any>()
    val transactionItems = HashMap<String,Any>()
    lateinit var context: Context
    var transaction = Transaction("","","","","", Timestamp.now())
    private var posClient: PosClient? = null
    private val TAG = MainActivity::class.java!!.getSimpleName()
    var location = Location("","","","","")
    var coffee2="none"
    var cancelRequest = false




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)




                var mFirebaseAuth = FirebaseAuth.getInstance()
                var mFirebaseUser = mFirebaseAuth.currentUser
                if (mFirebaseUser == null) {
                    // Not signed in, launch the Sign In activity
                    startActivity(Intent(this, SignInActivity::class.java))
                    finish()
                    return
                } else {
                    transaction.location = mFirebaseUser.displayName.toString()
                    location.businessName = mFirebaseUser.displayName.toString()

                }

        fs = FirebaseFirestore.getInstance().document("Locations/${transaction.location}")
        db = FirebaseFirestore.getInstance().document("Locations/Home")//pull firstore data
        db.get().addOnSuccessListener(OnSuccessListener <DocumentSnapshot>{ documentSnapshot -> //set variables to firestore data
            amountInKeg = documentSnapshot.get(AMOUNT_IN_KEG)
            transaction.location = mFirebaseUser.displayName.toString()
            macAddress= documentSnapshot.get(MAC_ADDRESS) as String
            m_address = macAddress
            ConnectToDevice(this).execute()
        })





        posClient = PosSdk.createClient(this, APPLICATION_ID)

        setup(amountDispensedTextView,costTextView,startCoffeeButton)
        unpaidTextView.visibility = View.INVISIBLE
        payForCoffeeButton.visibility = View.INVISIBLE

        payForCoffeeButton.setOnClickListener {
                sendCommand("4")
                payForCoffeeButton.visibility= View.INVISIBLE
                stopValveButton()
        }
        //control_led_disconnect.setOnClickListener { ConnectToDevice(this).execute() }

    }

    fun View.onClick(action: suspend (View) -> Unit) {
        // launch one actor
        val eventActor = actor<View>(UI, capacity = Channel.CONFLATED) {
            for (event in channel) action(event)
        }
        // install a listener to activate this actor
        setOnClickListener {
            eventActor.offer(it)
        }
    }


    fun setup(hello: TextView,cost:TextView, button: Button){
        var coffee = "none"
        var coffee2="none"
        var storedCoffee = "0"
        transaction.timeStamp = Timestamp.now()
        var locations = fs.collection("Transactions")
        locations.add(transaction).addOnSuccessListener{
            transaction.transactionID = it.id}
        var costPerOunce : Double= 0.25
        var job: Job = launch(UI) {

            delay(4000)
            while (isActive) {
                var coffee = "${coffeeMeter()}"
                delay(100)
                var coffee2 = "${coffeeMeter()}"
                if (coffee2 > coffee) {
                    amountDispensedTextView.text = coffee2
                    var costDouble = coffee2.toDouble() * costPerOunce
                    costTextView.text = costDouble.toString()
                    transaction.coffeeDispensed = coffee2
                    transaction.cost = costDouble.toString()
                    if (transaction.status == "Started") {
                        if (transaction.coffeeDispensed > "0") {
                            transaction.status = "Coffee Poured"
                            payForCoffeeButton.visibility = View.VISIBLE
                        }
                    }

                }
            }
        }
        button.onClick {
            var command = sendCommand("3")
            coffee = "${coffeeMeter()}"
            startCoffeeButton.visibility = View.INVISIBLE
            transaction.status = "Started"

        }
    }

    suspend fun coffeeMeter() : String= withContext(CommonPool) {
        var buf = ByteArray(1024)
        try {
            var coffeeScanner: Scanner = Scanner(m_bluetoothSocket!!.inputStream).useDelimiter("&")
            "${coffeeScanner.next()}"
        } catch (e: IOException) {
            "exepection"
        }
    }


     fun stopValveButton() {
        var coffeeDispensed = transaction.coffeeDispensed
        var coffeeCost = transaction.cost.toDouble()//*100
        var coffeeCostInt = coffeeCost.toInt()
        var request: ChargeRequest = ChargeRequest.Builder(coffeeCostInt,CurrencyCode.USD).build()
        try{
            val intent: Intent = posClient?.createChargeIntent(request)!!
            startActivityForResult(intent,CHARGE_REQUEST_CODE)

        }catch (e: IOException) {
            }
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
        items.put(LOCATION, transaction.location)
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

    private fun <T : View> findView(@IdRes id: Int): T {

        return findViewById<View>(id) as T
    }




    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHARGE_REQUEST_CODE) {
            if (data == null) {
                // This can happen if Square Point of Sale was uninstalled or crashed while we're waiting for a
                // result.
                Toast.makeText(this,"No Result from Square Point of Sale",Toast.LENGTH_SHORT).show()
                return
            }
            if (resultCode == Activity.RESULT_OK) {
                val success = posClient?.parseChargeSuccess(data)!!
                onTransactionSuccess(success)
            } else {
                val error = posClient?.parseChargeError(data)!!
                onTransactionError(error)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }




    private fun onTransactionSuccess(successResult: ChargeRequest.Success) {
        transaction.status = "Paid"
        fs.collection("Transactions").document(transaction.transactionID).set(transaction)
        val message = Html.fromHtml("<b><font color='#00aa00'>Success</font></b><br><br>"
                + "<b>Client RealTransaction Id</b><br>"
                + successResult.clientTransactionId
                + "<br><br><b>Server RealTransaction Id</b><br>"
                + successResult.serverTransactionId
                + "<br><br><b>Request Metadata</b><br>"
                + successResult.requestMetadata)
        showResult(message)
        Log.d(TAG, message.toString())
    }



     fun onTransactionError(errorResult: ChargeRequest.Error) {

        startCoffeeButton.visibility = View.INVISIBLE
        payForCoffeeButton.visibility = View.VISIBLE
        transaction.status = "Unpaid"
        unpaidTextView.visibility = View.VISIBLE
         var transactionInstance = fs.collection("Transactions").document(transaction.transactionID).set(transaction)
         //setup(cancelRequest)
         //this.recreate()
         //startNewTransaction()





//        val message = Html.fromHtml("<b><font color='#aa0000'>Error</font></b><br><br>"
//                + "<b>Error Key</b><br>"
//                + errorResult.code
//                + "<br><br><b>Error Description</b><br>"
//                + errorResult.debugDescription
//                + "<br><br><b>Request Metadata</b><br>"
//                + errorResult.requestMetadata)
//        showResult(message)
        Log.d(TAG, message.toString())
    }



    private fun isBlank(s: String?): Boolean {
        return s == null || s.trim { it <= ' ' }.isEmpty()
    }




    private fun showResult(message: CharSequence) {
        AlertDialog.Builder(this).setTitle("Coffee")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    fun startNewTransaction(){
        transaction = Transaction("","","","","", Timestamp.now())
        unpaidTextView.visibility = View.INVISIBLE
        payForCoffeeButton.visibility = View.INVISIBLE
        startCoffeeButton.visibility= View.VISIBLE





    }


}



