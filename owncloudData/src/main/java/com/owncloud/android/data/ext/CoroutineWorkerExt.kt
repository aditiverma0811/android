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

package com.owncloud.android.data.ext

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import com.owncloud.android.data.R

fun CoroutineWorker.showNotificationWithProgress(
    progress: Int,
    maxValue: Int,
    notificationId: String,
    notificationChannelName: String
) {

    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationChannel = NotificationChannel(
            notificationId,
            notificationChannelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(notificationChannel)
    }

    val notificationBuilder = NotificationCompat.Builder(
        applicationContext, notificationId
    ).setContentTitle("Descargando")
        .setSmallIcon(R.drawable.ic_android_black_24dp)


    if (progress == maxValue) {
        notificationBuilder.setContentText("Completo")
            .setTimeoutAfter(1_000)
    } else {
        notificationBuilder.setContentText("Progreso")
            .setProgress(maxValue, progress, false)

    }
    notificationManager.notify(notificationId.toInt(), notificationBuilder.build())
}
