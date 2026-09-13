package com.jarves.stark

import android.content.Context

class PublishingClient(private val context: Context) {
    suspend fun publish(assetId:String, platforms:List<String>, title:String, description:String):String =
        ContentFactoryClient(context).publish(assetId, title, description, platforms)
}
