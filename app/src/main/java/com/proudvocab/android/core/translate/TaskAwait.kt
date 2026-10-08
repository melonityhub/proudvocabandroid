package com.proudvocab.android.core.translate

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bridges a Google Play services `Task` and a coroutine. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener(OnSuccessListener<T> { result ->
        if (cont.isActive) cont.resumeWith(Result.success(result))
    })
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

/** Same as [await] but for `Task<Void>` results. */
suspend fun Task<Void>.awaitCompletion(): Unit = suspendCancellableCoroutine { cont ->
    addOnSuccessListener(OnSuccessListener<Void> {
        if (cont.isActive) cont.resumeWith(Result.success(Unit))
    })
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
