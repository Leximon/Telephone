package de.leximon.telephone.util

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo

lateinit var mongoClient: MongoClient
lateinit var database: MongoDatabase

fun initDatabase(connectionString: String) {
    KMongo.createClient(connectionString).let {
        mongoClient = it
        database = it.getDatabase("telephone")
    }
}