package com.example.tylercichonski.coffeestand

import com.google.firebase.Timestamp
import java.util.*

class Transaction constructor(var coffeeDispensed:String,
                              var cost: String,
                              var status: String,
                              var location: String,
                              var transactionID:String,
                              var timeStamp: Timestamp

                             )