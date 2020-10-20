package com.nil.googlemaps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback
//    , LocationListener, GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener
{

    private var mMap: GoogleMap? = null

    lateinit var mapView: MapView

    private val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"

    private val DEFAULT_ZOOM = 15f

    lateinit var tvCurrentAddress: TextView

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    var end_latitude = 0.0

    var end_longitude = 0.0

    var origin: MarkerOptions? = null

    var destination:MarkerOptions? = null

    var latitude = 0.0

    var longitude = 0.0

    override fun onMapReady(googleMap: GoogleMap?) {
        mapView.onResume()
        mMap = googleMap

        askPermissionLocation()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mMap!!.setMyLocationEnabled(true)
//        mMap!!.setOnCameraMoveListener(this)
//        mMap!!.setOnCameraMoveStartedListener(this)
//        mMap!!.setOnCameraIdleListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById<MapView>(R.id.map1)

        tvCurrentAddress = findViewById<TextView>(R.id.tvAdd)

        askPermissionLocation()

        var mapViewBundle: Bundle? = null
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY)
        }

        mapView.onCreate(mapViewBundle)
        mapView.getMapAsync(this)

        B_search.setOnClickListener {
            searchArea()
        }

        B_clear.setOnClickListener {
            mapView.onCreate(mapViewBundle)
            mapView.getMapAsync(this)
        }
    }

    private fun searchArea() {
        val tf_location =
            findViewById<View>(R.id.TF_location) as EditText

        val location = tf_location.text.toString()

        var addressList: List<Address>? = null

        val markerOptions = MarkerOptions()
        if (location != "") {
            val geocoder = Geocoder(applicationContext)
            try {
                addressList = geocoder.getFromLocationName(location, 5)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (addressList != null) {
                for (i in addressList.indices) {
                    val myAddress = addressList[i]
                    val latLng =
                        LatLng(myAddress.latitude, myAddress.longitude)
                    markerOptions.position(latLng)
                    mMap!!.addMarker(markerOptions)
                    end_latitude = myAddress.latitude
                    end_longitude = myAddress.longitude

                    mMap!!.animateCamera(CameraUpdateFactory.newLatLng(latLng))

                    val mo = MarkerOptions()
                    mo.title("Distance")

                    val results = FloatArray(10)
                    Location.distanceBetween(
                        latitude,
                        longitude,
                        end_latitude,
                        end_longitude,
                        results
                    )

                    val s =
                        String.format("%.1f", results[0] / 1000)

                    //Setting marker to draw route between these two points
                    origin = MarkerOptions().position(LatLng(latitude, longitude))
                        .title("HSR Layout").snippet("origin")
                    destination =
                        MarkerOptions().position(LatLng(end_latitude, end_longitude))
                            .title(tf_location.text.toString())
                            .snippet("Distance = $s KM")
                    mMap!!.addMarker(destination)
                    mMap!!.addMarker(origin)

                    Toast.makeText(
                        this@MainActivity,
                        "Distance = $s KM",
                        Toast.LENGTH_SHORT
                    ).show()

                    tvCurrentAddress!!.setText("Distance = $s KM")


                    // Getting URL to the Google Directions API
                    val url: String =
                        getDirectionsUrl(origin!!.getPosition(), destination!!.getPosition())!!

                    val downloadTask: DownloadTask = DownloadTask()

                    // Start downloading json data from Google Directions API
                    downloadTask.execute(url)

                }
            }
        }
    }


    inner class DownloadTask :
        AsyncTask<String?, Void?, String>() {

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            val parserTask = ParserTask()
            parserTask.execute(result)
        }

        override fun doInBackground(vararg url: String?): String {
            var data = ""
            try {
                data = downloadUrl(url[0].toString()).toString()
            } catch (e: java.lang.Exception) {
                Log.d("Background Task", e.toString())
            }
            return data
        }
    }



    /**
     * A class to parse the JSON format
     */
    inner class ParserTask :
        AsyncTask<String?, Int?, List<List<HashMap<String, String>>>?>() {
        // Parsing the data in non-ui thread
        override fun doInBackground(vararg jsonData: String?): List<List<HashMap<String, String>>>? {
            val jObject: JSONObject
            var routes: List<List<HashMap<String, String>>>? =
                null
            try {
                jObject = JSONObject(jsonData[0])
                val parser = DataParser()
                routes = parser.parse(jObject)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return routes
        }

        override fun onPostExecute(result: List<List<HashMap<String, String>>>?) {
            val points = ArrayList<LatLng?>()
            val lineOptions = PolylineOptions()
            for (i in result!!.indices) {
                val path =
                    result[i]
                for (j in path.indices) {
                    val point = path[j]
                    val lat = point["lat"]!!.toDouble()
                    val lng = point["lng"]!!.toDouble()
                    val position = LatLng(lat, lng)
                    points.add(position)
                }
                lineOptions.addAll(points)
                lineOptions.width(8f)
                lineOptions.color(Color.RED)
                lineOptions.geodesic(true)
            }

// Drawing polyline in the Google Map for the i-th route
            if (points.size != 0)
                mMap!!.addPolyline(lineOptions)
        }

    }




    /**
     * A method to download json data from url
     */
    @Throws(IOException::class)
    private fun downloadUrl(strUrl: String): String? {
        var data = ""
        var iStream: InputStream? = null
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(strUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.connect()
            iStream = urlConnection!!.inputStream
            val br =
                BufferedReader(InputStreamReader(iStream))
            val sb = StringBuffer()
            var line: String? = ""
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            data = sb.toString()
            br.close()
        } catch (e: java.lang.Exception) {
            Log.d("Exception", e.toString())
        } finally {
            iStream!!.close()
            urlConnection!!.disconnect()
        }
        return data
    }

    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String? {

        // Origin of route
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude

        // Destination of route
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude

        //setting transportation mode
        val mode = "mode=driving"

        // Building the parameters to the web service
        val parameters = "$str_origin&$str_dest&$mode"

        // Output format
        val output = "json"

        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters&key=AIzaSyCASsBISqXNR4imAynxnIw8Fa68Cv1c8cY"
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        askPermissionLocation()
        var mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
        if (mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle)
        }

        mapView.onSaveInstanceState(mapViewBundle)
    }

    private fun askPermissionLocation() {
        askPermission(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) {

            getCurrentLocation()
//            mapView.getMapAsync(this@MainActivity)

        }.onDeclined { e ->
            if (e.hasDenied()) {
                //the list of denied permissions
                e.denied.forEach {
                }

                AlertDialog.Builder(this)
                    .setMessage("Please accept our permissions.. Otherwise you will not be able to use some of our Important Features.")
                    .setPositiveButton("yes") { _, _ ->
                        e.askAgain()
                    } //ask again
                    .setNegativeButton("no") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            if (e.hasForeverDenied()) {
                //the list of forever denied permissions, user has check 'never ask again'
                e.foreverDenied.forEach {
                }
                // you need to open setting manually if you really need it
                e.goToSettings();
            }
        }
    }

    private fun getCurrentLocation() {
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this@MainActivity)

        try {
            @SuppressLint("MissingPermission")
            val location =
                fusedLocationProviderClient!!.getLastLocation()

            location.addOnCompleteListener(object : OnCompleteListener<Location> {
                override fun onComplete(loc: Task<Location>) {
                    if (loc.isSuccessful) {
                        val currentLocation = loc.result as Location?
                        if (currentLocation != null) {
                            moveCamera(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                DEFAULT_ZOOM
                            )

                            latitude = currentLocation.latitude
                            longitude = currentLocation.longitude

                        }
                    } else {
                        askPermissionLocation()
                    }
                }
            })
        } catch (se: Exception) {
            Log.e("TAG", "Security Exception")
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float) {
        mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

//    override fun onLocationChanged(location: Location?) {
//        val geocoder = Geocoder(this, Locale.getDefault())
//        var addresses: List<Address>? = null
//        try {
//            addresses = geocoder.getFromLocation(location!!.latitude, location.longitude, 1)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//        setAddress(addresses!![0])
//    }
//
//    private fun setAddress(addresses: Address) {
//        if (addresses != null) {
//
//            if (addresses.getAddressLine(0) != null) {
//                tvCurrentAddress!!.setText(addresses.getAddressLine(0))
//            }
//            if (addresses.getAddressLine(1) != null) {
//                tvCurrentAddress!!.setText(
//                    tvCurrentAddress.getText().toString() + addresses.getAddressLine(1)
//                )
//            }
//        }
//    }
//
//    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {
//
//    }
//
//    override fun onProviderEnabled(p0: String?) {
//
//    }
//
//    override fun onProviderDisabled(p0: String?) {
//
//    }
//
//    override fun onCameraMove() {
//
//    }
//
//    override fun onCameraMoveStarted(p0: Int) {
//
//    }
//
//    override fun onCameraIdle() {
//        var addresses: List<Address>? = null
//        val geocoder = Geocoder(this, Locale.getDefault())
//        try {
//            addresses = geocoder.getFromLocation( mMap!!.getCameraPosition().target.latitude, mMap!!.getCameraPosition().target.longitude, 1)
//
//            setAddress(addresses!![0])
//
//        } catch (e: IndexOutOfBoundsException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }

}