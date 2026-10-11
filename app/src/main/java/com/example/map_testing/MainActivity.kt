package com.example.map_testing // Ensure this matches your package name

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationApi.NavigatorListener
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator

class MainActivity : AppCompatActivity() {

    private lateinit var navView: NavigationView
    private var mNavigator: Navigator? = null

    // 1. Declare event listener variables
    private var arrivalListener: Navigator.ArrivalListener? = null
    private var routeChangedListener: Navigator.RouteChangedListener? = null

    private val SPLASH_SCREEN_DELAY_MILLIS = 1000L

    private val permissionsLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissionResults ->
        if (permissionResults.getOrDefault(permission.ACCESS_FINE_LOCATION, false)) {
            onLocationPermissionGranted()
        } else {
            showToast("Location permission is required for navigation.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navView = findViewById(R.id.navigation_view)
        navView.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigation_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(permission.ACCESS_FINE_LOCATION, permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.any { !checkPermissionGranted(it) }) {
            permissionsLauncher.launch(permissions)
        } else {
            Handler(Looper.getMainLooper()).postDelayed(
                { onLocationPermissionGranted() },
                SPLASH_SCREEN_DELAY_MILLIS
            )
        }
    }

    private fun checkPermissionGranted(permissionToCheck: String): Boolean =
        ContextCompat.checkSelfPermission(this, permissionToCheck) == PackageManager.PERMISSION_GRANTED

    private fun onLocationPermissionGranted() {
        initializeNavigationApi()
    }

    @SuppressLint("MissingPermission")
    private fun initializeNavigationApi() {
        NavigationApi.getNavigator(
            this,
            object : NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    mNavigator = navigator

                    // 4. Configure task removal behavior
                    navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)

                    // 5. Follow my location with tilted perspective
                    navView.getMapAsync { googleMap ->
                        googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED)
                    }

                    // 3. Register navigation event listeners
                    registerNavigationListeners()
                }

                override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                    when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> {
                            showToast(
                                "Error loading Navigation API: Your API key is " +
                                        "invalid or not authorized to use Navigation."
                            )
                        }
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> {
                            showToast(
                                "Error loading Navigation API: User did not " +
                                        "accept the Navigation Terms of Use."
                            )
                        }
                        else -> showToast("Error loading Navigation API: $errorCode")
                    }
                }
            }
        )
    }

    // 2. Register event listeners for arrival and route change
    private fun registerNavigationListeners() {
        withNavigatorAsync { navigator ->
            arrivalListener =
                Navigator.ArrivalListener {
                    showToast("User has arrived at the destination!")
                    mNavigator?.clearDestinations()
                }
            navigator.addArrivalListener(arrivalListener)

            routeChangedListener =
                Navigator.RouteChangedListener {
                    showToast("onRouteChanged: the driver's route changed")
                }
            navigator.addRouteChangedListener(routeChangedListener)
        }
    }

    /** Helper to safely run tasks requiring the Navigator instance */
    private fun withNavigatorAsync(callback: (Navigator) -> Unit) {
        mNavigator?.let(callback)
    }

    private fun showToast(errorMessage: String) {
        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
    }

    // 6. Forward Activity Lifecycle calls to NavigationView & clean up
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        navView.onSaveInstanceState(savedInstanceState)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        navView.onTrimMemory(level)
    }

    override fun onStart() {
        super.onStart()
        navView.onStart()
    }

    override fun onResume() {
        super.onResume()
        navView.onResume()
    }

    override fun onPause() {
        navView.onPause()
        super.onPause()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        navView.onConfigurationChanged(configuration)
    }

    override fun onStop() {
        navView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        navView.onDestroy()
        withNavigatorAsync { navigator ->
            // Unregister event listeners to avoid memory leaks
            if (arrivalListener != null) {
                navigator.removeArrivalListener(arrivalListener)
            }
            if (routeChangedListener != null) {
                navigator.removeRouteChangedListener(routeChangedListener)
            }

            navigator.simulator?.unsetUserLocation()
            navigator.cleanup()
        }
        super.onDestroy()
    }
}