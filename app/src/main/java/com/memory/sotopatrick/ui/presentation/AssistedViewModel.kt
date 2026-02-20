package com.memory.sotopatrick.ui.presentation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memory.sotopatrick.di.ViewModelFactoryProvider
import dagger.hilt.android.EntryPointAccessors

@Composable
inline fun <reified T : ViewModel, F> assistedViewModel(
    crossinline create: (F) -> T
): T {
    val factoryProvider = EntryPointAccessors.fromActivity(
        LocalContext.current as Activity,
        ViewModelFactoryProvider::class.java
    )

    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                val factory = factoryProvider.getGameSetupViewModelFactory() as F
                return create(factory) as VM
            }
        }
    )
}