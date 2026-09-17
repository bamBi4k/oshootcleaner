package com.example.oshootcleaner

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Bridges the classic AppWidgetProvider system to the Glance composable
 * widget defined in CleanerWidget.kt. This is the class the manifest and
 * cleaner_widget_info.xml point to.
 */
class CleanerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CleanerWidget()
}
