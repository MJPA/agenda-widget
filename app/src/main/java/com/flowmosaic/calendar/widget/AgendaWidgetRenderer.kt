package com.flowmosaic.calendar.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.flowmosaic.calendar.R
import com.flowmosaic.calendar.activity.PermissionsActivity
import com.flowmosaic.calendar.prefs.AgendaWidgetPrefs
import com.flowmosaic.calendar.remoteviews.EventsWidgetService

object AgendaWidgetRenderer {

    internal fun renderPermissionRequestView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.permission_request_widget)
        val appName = context.getString(R.string.app_name)
        val tapToSetupString = context.getString(R.string.tap_to_set_up, appName)
        views.setTextViewText(R.id.permission_request_text, tapToSetupString)

        val intent = Intent(context, PermissionsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.permission_request_text, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    internal fun renderCalendarWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        showProgress: Boolean = false
    ) {
        val intent = Intent(context, EventsWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        val prefs = AgendaWidgetPrefs(context)
        val layoutId =
            if (prefs.getAlignBottom(widgetId.toString())) R.layout.agenda_widget_bottom_aligned else R.layout.agenda_widget

        val views = RemoteViews(context.packageName, layoutId).apply {
            setRemoteAdapter(R.id.events_list_view, intent)
            setEmptyView(R.id.events_list_view, R.id.empty_view)

            setPendingIntentTemplate(
                R.id.events_list_view,
                createWidgetActionPendingIntent(context, CLICK_ACTION, widgetId)
            )
            setOnClickPendingIntent(
                R.id.refresh_button,
                createWidgetActionPendingIntent(context, UPDATE_ACTION, widgetId)
            )
            setOnClickPendingIntent(
                R.id.add_button,
                createWidgetActionPendingIntent(context, CREATE_ACTION, widgetId)
            )
            setOnClickPendingIntent(
                R.id.empty_view,
                createWidgetActionPendingIntent(context, CLICK_ACTION, widgetId)
            )

            setupEmptyView(context, widgetId, prefs)
            setWidgetBackground(widgetId, prefs)
            setActionButtonsVisibility(widgetId, prefs)
            showOrHideProgress(showProgress)
        }

        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.events_list_view)
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun createWidgetActionPendingIntent(
        context: Context,
        action: String,
        widgetId: Int
    ): PendingIntent {
        Intent(context, AgendaWidget::class.java).run {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            val requestCode = 0
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, requestCode, this, flags)
        }
    }

    private fun RemoteViews.setWidgetBackground(widgetId: Int, prefs: AgendaWidgetPrefs) {
        val backgroundColor = 0x000000
        val opacity = prefs.getOpacity(widgetId.toString())
        val color = ColorUtils.setAlphaComponent(backgroundColor, (opacity * 255).toInt())
        setInt(R.id.main_view, "setBackgroundColor", color)
    }

    @SuppressLint("RtlHardcoded")
    private fun RemoteViews.setActionButtonsVisibility(widgetId: Int, prefs: AgendaWidgetPrefs) {
        val actionButtonsVisible =
            if (prefs.getShowActionButtons(widgetId.toString())) View.VISIBLE else View.GONE
        setViewVisibility(R.id.widget_action_buttons, actionButtonsVisible)

        val buttonsAlignment =
            when (prefs.getTextAlignment(widgetId.toString())) {
                AgendaWidgetPrefs.TextAlignment.LEFT -> Gravity.RIGHT
                AgendaWidgetPrefs.TextAlignment.CENTER -> Gravity.RIGHT
                AgendaWidgetPrefs.TextAlignment.RIGHT -> Gravity.LEFT
            }
        setInt(R.id.widget_action_buttons, "setGravity", buttonsAlignment)

        val textColor = prefs.getTextColor(widgetId.toString()).toArgb()
        setInt(R.id.refresh_button, "setColorFilter", textColor)
        setInt(R.id.add_button, "setColorFilter", textColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(
                R.id.refresh_spinner, "setIndeterminateTintList",
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_enabled),
                    ), intArrayOf(
                        textColor,
                    )
                )
            )
        }
    }

    private fun RemoteViews.setupEmptyView(
        context: Context,
        widgetId: Int,
        prefs: AgendaWidgetPrefs
    ) {
        val noUpcomingEventsText = if (prefs.getShowNoUpcomingEventsText(
                widgetId.toString()
            )
        ) context.getString(R.string.no_upcoming_events) else ""
        val textAlignment =
            when (prefs.getTextAlignment(widgetId.toString())) {
                AgendaWidgetPrefs.TextAlignment.LEFT -> Gravity.START
                AgendaWidgetPrefs.TextAlignment.CENTER -> Gravity.CENTER
                AgendaWidgetPrefs.TextAlignment.RIGHT -> Gravity.END
            }
        setTextViewText(R.id.empty_view, noUpcomingEventsText)
        setInt(R.id.empty_view, "setGravity", textAlignment)
        setTextColor(
            R.id.empty_view,
            prefs.getTextColor(widgetId.toString()).toArgb()
        )
    }

    private fun RemoteViews.showOrHideProgress(showProgress: Boolean) {
        if (showProgress) {
            setViewVisibility(R.id.refresh_button, View.GONE)
            setViewVisibility(R.id.refresh_spinner, View.VISIBLE)
        } else {
            setViewVisibility(R.id.refresh_button, View.VISIBLE)
            setViewVisibility(R.id.refresh_spinner, View.GONE)
        }
    }

}