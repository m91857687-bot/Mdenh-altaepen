package com.example.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.admin.ui.AdminAppScreen
import com.example.admin.ui.theme.AdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val adminViewModel: AdminViewModel = viewModel(
                factory = AdminViewModel.factory(
                    context = applicationContext,
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
                )
            )
            AdminTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdminAppScreen(viewModel = adminViewModel)
                }
            }
        }
    }
}
