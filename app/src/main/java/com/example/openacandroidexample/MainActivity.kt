package com.example.openacandroidexample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.openacandroidexample.ui.theme.OpenACAndroidExampleTheme

class MainActivity : ComponentActivity() {

    private val vm: ProofViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenACAndroidExampleTheme {
                LaunchedEffect(Unit) {
                    intent?.data?.let { uri -> if (!vm.handleOidcRedirect(uri)) vm.handleCallback(uri) }
                }

                DisposableEffect(vm) {
                    val listener = { newIntent: Intent ->
                        newIntent.data?.let { uri -> if (!vm.handleOidcRedirect(uri)) vm.handleCallback(uri) }
                        Unit
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                ZkIdComponent(vm = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.cancelPendingSignIn()
    }
}
