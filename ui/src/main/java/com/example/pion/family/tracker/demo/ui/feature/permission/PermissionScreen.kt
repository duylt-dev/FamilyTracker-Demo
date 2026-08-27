package com.example.pion.family.tracker.demo.ui.feature.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pion.family.tracker.demo.ui.R
import com.example.pion.family.tracker.demo.ui.core.mvi.CollectEffects
import com.example.pion.family.tracker.demo.ui.permission.PermissionStep
import com.example.pion.family.tracker.demo.ui.permission.rememberLocationPermissionFlow
import org.koin.androidx.compose.koinViewModel

/**
 * US-01→US-05, phase-04 Implementation Step 5. Stateful — biết Koin, `LocationPermissionFlow` và
 * `CollectEffects`. [PermissionScreen] bên dưới chỉ nhận state + onIntent (MVI doc §4).
 */
@Composable
fun PermissionRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionFlow = rememberLocationPermissionFlow(
        currentStep = state.step,
        onPermissionResolved = { type, granted ->
            viewModel.onIntent(PermissionIntent.PermissionResolved(type, granted))
        },
        onScreenResumed = { viewModel.onIntent(PermissionIntent.ScreenResumed) },
    )

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PermissionEffect.RequestNotifications -> permissionFlow.requestNotifications()
            PermissionEffect.RequestFineLocation -> permissionFlow.requestFineLocation()
            PermissionEffect.OpenAppSettings -> permissionFlow.openAppSettings()
            PermissionEffect.GoToMap -> onFinished()
        }
    }

    PermissionScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
private fun PermissionScreen(
    state: PermissionState,
    onIntent: (PermissionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titleRes, descriptionRes) = when (state.step) {
        PermissionStep.NOTIFICATIONS -> R.string.permission_notifications_title to R.string.permission_notifications_description
        PermissionStep.FINE_LOCATION -> R.string.permission_fine_location_title to R.string.permission_fine_location_description
        PermissionStep.BACKGROUND_LOCATION -> R.string.permission_background_location_title to R.string.permission_background_location_description
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onIntent(PermissionIntent.Continue) }) {
                Text(stringResource(R.string.action_continue))
            }
            TextButton(onClick = { onIntent(PermissionIntent.Skip) }) {
                Text(stringResource(R.string.action_skip))
            }
        }
    }
}
