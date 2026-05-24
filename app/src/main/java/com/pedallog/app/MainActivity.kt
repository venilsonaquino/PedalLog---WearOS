package com.pedallog.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import com.pedallog.app.modules.tracking.domain.valueobjects.GpsSignal
import com.pedallog.app.modules.tracking.presentation.ui.components.GpsWarningType
import com.pedallog.app.modules.tracking.presentation.ui.screens.RideScreen
import com.pedallog.app.modules.tracking.presentation.viewmodels.RideViewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity principal do PedalLog.
 * Atua de forma limpa apenas como âncora do Compose, observando o ciclo de vida
 * de baixa energia (Ambient Mode) e lógicas obrigatórias de permissão.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RideViewModel by viewModels()
    private var isAmbient by mutableStateOf(false)
    private var ambientUpdateTrigger by mutableStateOf(0)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
        }

        override fun onExitAmbient() {
            isAmbient = false
        }

        override fun onUpdateAmbient() {
            ambientUpdateTrigger++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        setContent {
            val context = this
            val navController = rememberSwipeDismissableNavController()
            
            var gpsWarningType by remember { mutableStateOf<GpsWarningType?>(null) }
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {}

            val burnInOffset = remember(isAmbient, ambientUpdateTrigger) {
                if (isAmbient) {
                    val minutes = System.currentTimeMillis() / 60000
                    val shiftX = (((minutes % 5) - 2) * 2).toInt()
                    val shiftY = ((((minutes / 5) % 5) - 2) * 2).toInt()
                    Modifier.offset(shiftX.dp, shiftY.dp)
                } else Modifier
            }

            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "ride"
            ) {
                composable("ride") {
                    RideScreen(
                        viewModel = viewModel,
                        isAmbient = isAmbient,
                        burnInOffset = burnInOffset,
                        gpsWarningType = gpsWarningType,
                        onDismissWarning = { gpsWarningType = null },
                        onConfirmWarning = {
                            gpsWarningType = null
                            viewModel.startRide()
                        },
                        onSettingsWarning = {
                            gpsWarningType = null
                            context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        onStartClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            } else {
                                val signal = viewModel.gpsSignal.value
                                when {
                                    signal == GpsSignal.DISABLED -> gpsWarningType = GpsWarningType.DISABLED
                                    signal == GpsSignal.WEAK || signal == GpsSignal.ACQUIRING -> gpsWarningType = GpsWarningType.WEAK
                                    else -> viewModel.startRide()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
