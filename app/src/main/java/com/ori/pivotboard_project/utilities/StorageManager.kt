package com.ori.pivotboard_project.utilities

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage
import java.util.UUID

/** The only class that talks to Firebase Storage. Chart screenshots go through here. */
class StorageManager private constructor(context: Context) {

    private val root: StorageReference = Firebase.storage.reference

    /**
     * Uploads [imageUri] under `images/<uid>_<uuid>` and hands back the public download URL.
     *
     * [onProgress] reports 0..100 so the Create Post screen can show a determinate bar.
     * [onComplete] receives a null url when the upload failed - callers must handle that
     * rather than assume success.
     */
    fun uploadImage(
        imageUri: Uri,
        uid: String,
        onProgress: ((percent: Int) -> Unit)? = null,
        onComplete: (downloadUrl: String?) -> Unit
    ) {
        val imageRef = root.child("${Constants.STORAGE.IMAGES_DIR}/${uid}_${UUID.randomUUID()}")

        imageRef.putFile(imageUri)
            .addOnProgressListener { snapshot ->
                if (snapshot.totalByteCount > 0) {
                    val percent = (100 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                    onProgress?.invoke(percent)
                }
            }
            .continueWithTask { task ->
                // Surface an upload failure here instead of silently asking for a download url.
                if (!task.isSuccessful) task.exception?.let { throw it }
                imageRef.downloadUrl
            }
            .addOnCompleteListener { task ->
                // Covers both the rethrown upload error and a downloadUrl failure.
                if (!task.isSuccessful) task.exception?.let { logStorageFailure("upload", it) }
                onComplete(if (task.isSuccessful) task.result?.toString() else null)
            }
    }

    /**
     * [StorageException] buries the useful part: `errorCode` distinguishes a rules rejection
     * from a missing bucket, and `httpResultCode` gives the raw status (403 vs 404). Without
     * this the caller only sees a null url.
     */
    private fun logStorageFailure(label: String, e: Exception) {
        val storageException = e as? StorageException
        Log.e(
            TAG,
            "$label FAILED" +
                " | errorCode=${storageException?.errorCode ?: "(not a StorageException)"}" +
                " | httpResult=${storageException?.httpResultCode ?: "-"}" +
                " | type=${e.javaClass.name}" +
                " | message=${e.message}" +
                " | cause=${e.cause}",
            e
        )
    }

    fun deleteImage(downloadUrl: String, onComplete: ((success: Boolean) -> Unit)? = null) {
        if (downloadUrl.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        Firebase.storage.getReferenceFromUrl(downloadUrl)
            .delete()
            .addOnCompleteListener { onComplete?.invoke(it.isSuccessful) }
    }

    companion object {
        private const val TAG = "PivotBoardStorage"

        @Volatile
        private var instance: StorageManager? = null

        fun init(context: Context): StorageManager =
            instance ?: synchronized(this) {
                instance ?: StorageManager(context).also { instance = it }
            }

        fun getInstance(): StorageManager = instance
            ?: throw IllegalStateException("StorageManager must be initialized by calling init(context) before use.")
    }
}
