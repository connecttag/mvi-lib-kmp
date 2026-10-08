package org.connecttag.lib.mvi.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

fun CreationExtras.requireSavedStateHandle(): SavedStateHandle = createSavedStateHandle()
