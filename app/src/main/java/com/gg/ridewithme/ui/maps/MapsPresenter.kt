package com.gg.ridewithme.ui.maps

import com.gg.ridewithme.data.network.NetworkService
import com.mindorks.ridesharing.simulator.WebSocket
import com.mindorks.ridesharing.simulator.WebSocketListener


class MapsPresenter(private val networkService: NetworkService) : WebSocketListener {

    private var view: MapsView? = null
    private lateinit var webSocket: WebSocket

    fun onAttach(view: MapsView?) {
        this.view = view
        webSocket = networkService.createWebSocket(this)
        webSocket.connect()
    }

    fun onDetach() {
        webSocket.disconnect()
        view = null
    }

    override fun onConnect() {

    }

    override fun onMessage(data: String) {

    }

    override fun onDisconnect() {
    }

    override fun onError(error: String) {
    }
}