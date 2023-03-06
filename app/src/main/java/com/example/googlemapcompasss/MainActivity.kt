package com.example.googlemapcompasss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import com.example.googlemapcompasss.PermissionUtils.PermissionDeniedDialog.Companion.newInstance
import com.example.googlemapcompasss.PermissionUtils.isPermissionGranted
import com.example.googlemapcompasss.databinding.AboutAlertDialogViewBinding
import com.example.googlemapcompasss.databinding.ActivityMainBinding
import com.example.googlemapcompasss.databinding.SensorAlertDialogViewBinding
import com.example.googlemapcompasss.model.Azimuth
import com.example.googlemapcompasss.model.DisplayRotation
import com.example.googlemapcompasss.model.RotationVector
import com.example.googlemapcompasss.model.SensorAccuracy
import com.example.googlemapcompasss.preference.PreferenceStore
import com.example.googlemapcompasss.util.MathUtils
import com.example.googlemapcompasss.view.ObservableSensorAccuracy
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * This demo shows how GMS Location can be used to check for changes to the users location.  The
 * "My Location" button uses GMS Location to set the blue dot representing the users location.
 * Permission for [Manifest.permission.ACCESS_FINE_LOCATION] and [Manifest.permission.ACCESS_COARSE_LOCATION]
 * are requested at run time. If either permission is not granted, the Activity is finished with an error message.
 */



const val OPTION_INSTRUMENTED_TEST = "INSTRUMENTED_TEST"

private const val TAG = "MainActivity"


class MainActivity : AppCompatActivity(),
    OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnMapReadyCallback,
    OnRequestPermissionsResultCallback , SensorEventListener {
    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * [.onRequestPermissionsResult].
     */
    private var permissionDenied = false
    private lateinit var map: GoogleMap


    private val observableSensorAccuracy = ObservableSensorAccuracy(SensorAccuracy.NO_CONTACT)

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var preferenceStore: PreferenceStore

    private var optionsMenu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
//        startActivity(Intent(this, CompassActivity::class.java))

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        initPreferenceStore()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.setOnMyLocationButtonClickListener(this)
        googleMap.setOnMyLocationClickListener(this)
        enableMyLocation()
    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {

        // [START maps_check_location_permission]
        // 1. Check if permissions are granted, if so, enable the my location layer
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            return
        }

        // 2. If if a permission rationale dialog should be shown
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            PermissionUtils.RationaleDialog.newInstance(
                LOCATION_PERMISSION_REQUEST_CODE, true
            ).show(supportFragmentManager, "dialog")
            return
        }

        // 3. Otherwise, request permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
        // [END maps_check_location_permission]
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT)
            .show()
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    override fun onMyLocationClick(location: Location) {
        Toast.makeText(this, "Current location:\n$location", Toast.LENGTH_LONG)
            .show()
    }

    // [START maps_check_location_permission_result]
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
            return
        }

        if (isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation()
        } else {
            // Permission was denied. Display an error message
            // [START_EXCLUDE]
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true
            // [END_EXCLUDE]
        }
    }

    // [END maps_check_location_permission_result]
    override fun onResumeFragments() {
        super.onResumeFragments()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError()
            permissionDenied = false
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private fun showMissingPermissionError() {
        newInstance(true).show(supportFragmentManager, "dialog")
    }

    companion object {
        /**
         * Request code for location permission request.
         *
         * @see .onRequestPermissionsResult
         */
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private fun initPreferenceStore() {
        preferenceStore = PreferenceStore(this)
        preferenceStore.screenOrientationLocked.observe(this) { setScreenRotationMode(it) }
        preferenceStore.nightMode.observe(this) { setNightMode(it) }
        Log.d(TAG, "Initialized preference store")
    }

    override fun onResume() {
        super.onResume()

        if (isInstrumentedTest()) {
            Log.i(TAG, "Skipping registration of sensor listener")
        } else {
            registerSensorListener()
        }

        Log.i(TAG, "Started compass")
    }

    private fun registerSensorListener() {
        val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            Log.w(TAG, "Rotation vector sensor not available")
            showSensorErrorDialog()
            return
        }

        val success = sensorManager.registerListener(this, rotationVectorSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        if (!success) {
            Log.w(TAG, "Could not enable rotation vector sensor")
            showSensorErrorDialog()
            return
        }
    }

    private fun showSensorErrorDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.sensor_error_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show()
    }

    private fun isInstrumentedTest() = intent.extras?.getBoolean(OPTION_INSTRUMENTED_TEST) ?: false

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        Log.i(TAG, "Stopped compass")
    }

    override fun onDestroy() {
        super.onDestroy()
        closePreferenceStore()
    }

    private fun closePreferenceStore() {
        preferenceStore.close()
        Log.d(TAG, "Closed preference store")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        optionsMenu = menu
        updateSensorStatusIcon()
        updateScreenRotationIcon()
        updateNightModeIcon()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sensor_status -> {
                showSensorStatusPopup()
                true
            }
            R.id.action_screen_rotation -> {
                toggleRotationScreenLocked()
                true
            }
            R.id.action_night_mode -> {
                toggleNightMode()
                true
            }
            R.id.action_about -> {
                showAboutPopup()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSensorStatusPopup() {
        val alertDialogBuilder = MaterialAlertDialogBuilder(this)
        val dialogContextInflater = LayoutInflater.from(alertDialogBuilder.context)

        val dialogBinding = SensorAlertDialogViewBinding.inflate(dialogContextInflater, null, false)
//        dialogBinding.sensorAccuracy = observableSensorAccuracy

        alertDialogBuilder
            .setTitle(R.string.sensor_status)
            .setView(dialogBinding.root)
            .setNeutralButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun toggleRotationScreenLocked() {
        preferenceStore.screenOrientationLocked.value?.let {
            preferenceStore.screenOrientationLocked.value = it.not()
        }
    }

    private fun toggleNightMode() {
        preferenceStore.nightMode.value?.let {
            when (it) {
                AppCompatDelegate.MODE_NIGHT_NO -> preferenceStore.nightMode.value =
                    AppCompatDelegate.MODE_NIGHT_YES
                AppCompatDelegate.MODE_NIGHT_YES -> preferenceStore.nightMode.value =
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> preferenceStore.nightMode.value = AppCompatDelegate.MODE_NIGHT_NO
            }
        }
    }

    private fun showAboutPopup() {
        val alertDialogBuilder = MaterialAlertDialogBuilder(this)
        val dialogContextInflater = LayoutInflater.from(alertDialogBuilder.context)

        val dialogBinding = AboutAlertDialogViewBinding.inflate(dialogContextInflater, null, false)
//        dialogBinding.version = BuildConfig.VERSION_NAME
        dialogBinding.copyrightText.movementMethod = LinkMovementMethod.getInstance()
        dialogBinding.licenseText.movementMethod = LinkMovementMethod.getInstance()
        dialogBinding.sourceCodeText.movementMethod = LinkMovementMethod.getInstance()

        alertDialogBuilder
            .setTitle(R.string.app_name)
            .setView(dialogBinding.root)
            .setNeutralButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    //Called when the accuracy of the registered sensor has changed. Unlike onSensorChanged(), this is only called when this accuracy value changes.
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        when (sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> setSensorAccuracy(accuracy)
            else -> Log.w(TAG, "Unexpected accuracy changed event of type ${sensor.type}")
        }
    }

    private fun setSensorAccuracy(accuracy: Int) {
        Log.v(TAG, "Sensor accuracy value $accuracy")
        val sensorAccuracy = adaptSensorAccuracy(accuracy)
        setSensorAccuracy(sensorAccuracy)
    }

    internal fun setSensorAccuracy(sensorAccuracy: SensorAccuracy) {
        observableSensorAccuracy.set(sensorAccuracy)
        updateSensorStatusIcon()
    }

    private fun adaptSensorAccuracy(accuracy: Int): SensorAccuracy {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> SensorAccuracy.NO_CONTACT
            SensorManager.SENSOR_STATUS_UNRELIABLE -> SensorAccuracy.UNRELIABLE
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.LOW
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.HIGH
            else -> {
                Log.w(TAG, "Encountered unexpected sensor accuracy value '$accuracy'")
                SensorAccuracy.NO_CONTACT
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> updateCompass(event)
            else -> Log.w(TAG, "Unexpected sensor changed event of type ${event.sensor.type}")
        }
    }

    private fun updateCompass(event: SensorEvent) {
        val rotationVector = RotationVector(event.values[0], event.values[1], event.values[2])
        val displayRotation = getDisplayRotation()
        val azimuth = MathUtils.calculateAzimuth(rotationVector, displayRotation)
        setAzimuth(azimuth)
    }

    private fun getDisplayRotation(): DisplayRotation {
        return when (getDisplayCompat()?.rotation) {
            Surface.ROTATION_90 -> DisplayRotation.ROTATION_90
            Surface.ROTATION_180 -> DisplayRotation.ROTATION_180
            Surface.ROTATION_270 -> DisplayRotation.ROTATION_270
            else -> DisplayRotation.ROTATION_0
        }
    }

    private fun getDisplayCompat(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
    }

    internal fun setAzimuth(azimuth: Azimuth) {
        binding.contentMain.compass.setAzimuth(azimuth)
        Log.v(TAG, "Azimuth $azimuth")
    }

    private fun setScreenRotationMode(screenOrientationLocked: Boolean) {
        if (screenOrientationLocked) {
            setScreenRotationMode(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
        } else {
            setScreenRotationMode(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    private fun setScreenRotationMode(screenOrientation: Int) {
        Log.d(TAG, "Setting requested orientation to value $screenOrientation")
        requestedOrientation = screenOrientation
        updateScreenRotationIcon()
    }

    private fun setNightMode(@AppCompatDelegate.NightMode mode: Int) {
        Log.d(TAG, "Setting night mode to value $mode")
        AppCompatDelegate.setDefaultNightMode(mode)
        updateNightModeIcon()
    }

    private fun updateSensorStatusIcon() {
        val sensorAccuracy = observableSensorAccuracy.get() ?: SensorAccuracy.NO_CONTACT

        optionsMenu
            ?.findItem(R.id.action_sensor_status)
            ?.setIcon(sensorAccuracy.iconResourceId)
    }

    private fun updateScreenRotationIcon() {
        optionsMenu
            ?.findItem(R.id.action_screen_rotation)
            ?.setIcon(getScreenRotationIcon())
    }

    @DrawableRes
    private fun getScreenRotationIcon(): Int {
        return when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> R.drawable.ic_screen_rotation
            else -> R.drawable.ic_screen_rotation_lock
        }
    }

    private fun updateNightModeIcon() {
        optionsMenu
            ?.findItem(R.id.action_night_mode)
            ?.setIcon(getNightModeIcon())
    }

    @DrawableRes
    private fun getNightModeIcon(): Int {
        return when (preferenceStore.nightMode.value) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.drawable.ic_light_mode
            AppCompatDelegate.MODE_NIGHT_YES -> R.drawable.ic_dark_mode
            else -> R.drawable.ic_auto_mode
        }
    }
}