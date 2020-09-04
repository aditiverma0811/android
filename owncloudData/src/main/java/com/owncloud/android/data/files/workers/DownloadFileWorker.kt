/**
 * ownCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.data.files.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.data.ext.showNotificationWithProgress
import com.owncloud.android.data.files.datasources.LocalFileDataSource
import com.owncloud.android.data.files.storage.FileStorageUtils
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation
import org.koin.core.KoinComponent
import org.koin.core.inject
import timber.log.Timber
import java.io.File

class DownloadFileWorker(
    appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent, OnDatatransferProgressListener {

    val client: OwnCloudClient by inject()
    lateinit var downloadRemoteFileOperation: DownloadRemoteFileOperation

    private var fileId: Long = -1
    lateinit var accountName: String
    lateinit var remotePath: String
    private var storagePath: String? = ""

    override suspend fun doWork(): Result {

        accountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT) as String
        remotePath = workerParameters.inputData.getString(KEY_PARAM_REMOTE_PATH) as String
        storagePath = workerParameters.inputData.getString(KEY_PARAM_STORAGE_PATH)
        fileId = workerParameters.inputData.getLong(KEY_PARAM_FILE_ID, -1)

        downloadRemoteFileOperation = DownloadRemoteFileOperation(
            remotePath,
            FileStorageUtils.getTemporalPath(accountName)
        )

        downloadRemoteFileOperation.addDatatransferProgressListener(this)

        return try {
            val result = downloadFile()
            if (result.isSuccess) {
                saveDownloadedFile()
                Result.success()
            } else {
                throw result.exception
            }
        } catch (throwable: Throwable) {
            // clean up and log
            Result.failure()
        }

    }

    private fun downloadFile(): RemoteOperationResult<Any> {
        /// download will be performed to a temporal file, then moved to the final location
        val tmpFile = File(temporalPath)

        var result = downloadRemoteFileOperation.execute(client)

        if (result.isSuccess) {
            if (FileStorageUtils.getUsableSpace() < tmpFile.length()) {
                Timber.w("Not enough space to copy %s", tmpFile.absolutePath)
            }

            val newFile = File(savePathForFile)
            Timber.d("Save path: %s", newFile.absolutePath)
            val parent: File? = newFile.parentFile
            val created = parent?.mkdirs()
            parent?.let {
                Timber.d("Creation of parent folder ${it.absolutePath} succeeded: $created")
                Timber.d("Parent folder ${it.absolutePath} is directory: ${it.isDirectory} exists: ${it.exists()}")
            }
            val moved = tmpFile.renameTo(newFile)
            Timber.d("New file ${newFile.absolutePath} is directory: ${newFile.isDirectory} and exists: ${newFile.exists()}")
            if (!moved) {
                result = RemoteOperationResult(RemoteOperationResult.ResultCode.LOCAL_STORAGE_NOT_MOVED)
            }

        }
        return result
    }

    /**
     * Updates the OC File after a successful download.
     */
    private fun saveDownloadedFile() {
        val localFileDataSource: LocalFileDataSource by inject()

        // TODO: Handle this kind of problems in a better way
        val ocFile = localFileDataSource.getFile(fileId) ?: return

        ocFile.apply {
            needsToUpdateThumbnail = true
            modifiedTimestamp = downloadRemoteFileOperation.modificationTimestamp
            etag = downloadRemoteFileOperation.etag
            storagePath = savePathForFile
            length = (File(savePathForFile).length())
        }
        localFileDataSource.saveFile(ocFile)

        //mStorageManager.triggerMediaScan(file.getStoragePath())
        //mStorageManager.saveConflict(file, null)
    }

    private val temporalPath
        get() = temporalFolder + remotePath

    private val temporalFolder
        get() = FileStorageUtils.getTemporalPath(accountName)

    private val savePathForFile: String
        get() =
            // re-downloads should be done over the original file
            storagePath.takeUnless { it.isNullOrBlank() }
                ?: FileStorageUtils.getDefaultSavePathFor(accountName, remotePath)

    override fun onTransferProgress(
        progressRate: Long,
        totalTransferredSoFar: Long,
        totalToTransfer: Long,
        filePath: String
    ) {
        showNotificationWithProgress(
            maxValue = totalToTransfer.toInt(),
            progress = totalTransferredSoFar.toInt(),
            notificationChannelName = "DOWNLOAD",
            notificationId = "123"
        )
    }

    companion object {
        const val KEY_PARAM_ACCOUNT = "KEY_PARAM_ACCOUNT"
        const val KEY_PARAM_REMOTE_PATH = "KEY_PARAM_REMOTE_PATH"
        const val KEY_PARAM_STORAGE_PATH = "KEY_PARAM_STORAGE_PATH"
        const val KEY_PARAM_FILE_ID = "KEY_PARAM_FILE_ID"
    }

}
