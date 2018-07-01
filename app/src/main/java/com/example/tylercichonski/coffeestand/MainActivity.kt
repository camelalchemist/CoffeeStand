package com.example.tylercichonski.coffeestand

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.google.android.gms.common.server.converter.StringToIntConverter
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

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
}



