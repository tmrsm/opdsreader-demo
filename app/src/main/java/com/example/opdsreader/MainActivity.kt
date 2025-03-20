package com.example.opdsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.example.opdsreader.di.NetworkModule
import com.example.opdsreader.ui.main.MainScreen
import com.example.opdsreader.ui.main.MainViewModel
import com.example.opdsreader.ui.theme.OpdsreaderTheme

@OptIn(coil.annotation.ExperimentalCoilApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = NetworkModule.provideOpdsRepository()

        setContent {
            OpdsreaderTheme {
                val viewModel = remember { MainViewModel(repository) }
                MainScreen(
                    viewModel = viewModel,
                    onEntryClick = { entry ->
                        viewModel.handleEntryClick(entry)
                    }
                )
            }
        }
    }
}