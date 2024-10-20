package de.leximon.telephone.util


import de.leximon.telephone.isDev
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

lateinit var mongoClient: CoroutineClient
lateinit var database: CoroutineDatabase

fun initDatabase(connectionString: String) {
    KMongo.createClient(connectionString).coroutine.let {
        mongoClient = it
        database = it.getDatabase(if (isDev) "telephone_test" else "telephone")
    }
}