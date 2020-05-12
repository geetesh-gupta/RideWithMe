package com.gg.ridewithme.ui.maps

import com.google.android.gms.maps.model.LatLng

interface MapsView {
    fun showNearbyCabs(latLngList: List<LatLng>)

    fun informCabBooked()

    fun showPath(pickUpPath: List<LatLng>)
}