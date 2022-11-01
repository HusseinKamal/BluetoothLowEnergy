package com.hussein.bluetoothlowenergy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*


@SuppressLint("Registered")
@Suppress("DEPRECATION", "NAME_SHADOWING")
class GPSHelper(activityCLS: Activity,mListen:OnLocationEnableListener): Activity(){
    private var googleApiClient: GoogleApiClient? = null
    private var activity=activityCLS
    private var listener=mListen
    private val LOCATION_REQUEST=4
    interface OnLocationEnableListener{
        fun onLocationEnabled(isEnable: Boolean)
    }
    init {
        //If Android is more than Q then it should enable location to use BLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            enableLoc()
        }
    }

    private fun enableLoc() {
        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(activity)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(bundle: Bundle?) {}
                    override fun onConnectionSuspended(i: Int) {
                        googleApiClient!!.connect()
                    }
                })
                .addOnConnectionFailedListener {
                }.build()
            googleApiClient!!.connect()
            val locationRequest = LocationRequest.create()
            locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            locationRequest.interval = 30 * 1000
            locationRequest.fastestInterval = 5 * 1000
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
            builder.setAlwaysShow(true)
            val result: PendingResult<LocationSettingsResult> =
                LocationServices.SettingsApi.checkLocationSettings(
                    googleApiClient!!, builder.build()
                )
            result.setResultCallback { result ->
                val status: Status = result.status
                when (status.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        status.startResolutionForResult(activity, LOCATION_REQUEST)

                        //                                finish();
                    } catch (e: SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            LOCATION_REQUEST ->
                listener.onLocationEnabled(isLocationEnabled(activity))
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(isLocationEnabled(activity))
        {
            listener.onLocationEnabled(true)
        }
    }
    companion object{
    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(LOCATION_SERVICE) as LocationManager
            val gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            gps_enabled

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    }
}