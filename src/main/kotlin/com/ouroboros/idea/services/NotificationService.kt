package com.ouroboros.idea.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NotificationService(private val project: Project) {

    private val notificationGroup = NotificationGroupManager.getInstance()
        .getNotificationGroup("TideStack")

    fun showInfo(message: String) {
        notificationGroup.createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    fun showSuccess(message: String) {
        notificationGroup.createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    fun showWarning(message: String) {
        notificationGroup.createNotification(message, NotificationType.WARNING)
            .notify(project)
    }

    fun showError(message: String) {
        notificationGroup.createNotification(message, NotificationType.ERROR)
            .notify(project)
    }
}
