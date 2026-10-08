package com.proudvocab.android.ui.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

@Suppress("UNCHECKED_CAST")
inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: (CreationExtras) -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(extras) as T
}

@Suppress("UNCHECKED_CAST")
inline fun <reified VM : ViewModel> simpleFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
