package com.gg.ridewithme.ui.maps

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import com.gg.ridewithme.R
import com.gg.ridewithme.data.network.NetworkService
import com.gg.ridewithme.utils.AnimationUtils
import com.gg.ridewithme.utils.MapUtils
import com.gg.ridewithme.utils.PermissionUtils
import com.gg.ridewithme.utils.ViewUtils
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.android.synthetic.main.activity_maps.*


class MapsActivity : AppCompatActivity(), MapsView, OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }

    private lateinit var googleMap: GoogleMap
    private lateinit var presenter: MapsPresenter

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private val nearbyCabMarkerList = arrayListOf<Marker>()

    private var pickUpLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null


    private var greyPolyLine: Polyline? = null
    private var blackPolyline: Polyline? = null

    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setUpClickListener()
    }

    private fun setUpClickListener() {
        pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!, dropLatLng!!)
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun moveCamera(latLng: LatLng?) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng?) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }

    private fun setCurrentLocationAsPickUp() {
        pickUpLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    private fun enableMyLocationOnMap() {
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
        googleMap.isMyLocationEnabled = true
    }

    private fun setupLocationsListener() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (currentLatLng == null) {
                    for (location in locationResult.locations) {
                        if (currentLatLng == null) {
                            currentLatLng = LatLng(location.latitude, location.longitude)
                            setCurrentLocationAsPickUp()
                            enableMyLocationOnMap()
                            moveCamera(currentLatLng)
                            animateCamera(currentLatLng)
                            presenter.requestNearbyCabs(currentLatLng!!)
                        }
                    }
                }
                // Few more things we can do here:
                // For example: Update the location of user on server
            }
        }

        fusedLocationProviderClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private fun checkAndShowRequestButton() {
        if (pickUpLatLng !== null && dropLatLng !== null) {
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when {
            PermissionUtils.isAccessFineLocationGranted(this) -> {
                when {
                    PermissionUtils.isLocationEnabled(this) -> {
                        setupLocationsListener()
                    }
                    else -> {
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                }
            }
            else -> {
                PermissionUtils.requestAccessFineLocationPermission(
                    this,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            setupLocationsListener()
                        }
                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_permission_not_granted),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(TAG, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Place Selection Canceled")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDetach()
    }

    override fun showNearbyCabs(latLngList: List<LatLng>) {
        nearbyCabMarkerList.clear()
        for (latLng in latLngList) {
            val nearbyCabMarker = addCarMarkerAndGet(latLng)
            nearbyCabMarkerList.add(nearbyCabMarker)
        }
    }

    override fun informCabBooked() {
        nearbyCabMarkerList.forEach { it.remove() }
        nearbyCabMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latLngList: List<LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latLngList) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))

        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll(latLngList)
        greyPolyLine = googleMap.addPolyline(polylineOptions)

        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.width(5f)
        blackPolylineOptions.color(Color.BLACK)
        blackPolyline = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size - 1])
        destinationMarker?.setAnchor(0.5f, 0.5f)

        val polylineAnimator = AnimationUtils.polyLineAnimator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size * (percentValue / 100.0f)).toInt()
            blackPolyline?.points = greyPolyLine?.points!!.subList(0, index)
        }
        polylineAnimator.start()
    }
}