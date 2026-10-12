package com.lchat.app.util

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object Db {

    private const val DATABASE_URL =
        "https://l-chat-a7c10-default-rtdb.europe-west1.firebasedatabase.app"

    fun ref(path: String): DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).getReference(path)
}
