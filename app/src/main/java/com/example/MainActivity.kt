package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.explorer.CityExplorerScreen
import com.example.explorer.CityExplorerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val explorerViewModel: CityExplorerViewModel = viewModel(
                factory = CityExplorerViewModel.factory(
                    context = applicationContext,
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
                )
            )
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CityExplorerScreen(viewModel = explorerViewModel)
                }
            }
        }
    }
}
