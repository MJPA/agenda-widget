package com.flowmosaic.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.flowmosaic.calendar.analytics.AgendaWidgetLogger
import com.flowmosaic.calendar.data.CalendarPermissionsChecker.hasCalendarPermission
import com.flowmosaic.calendar.prefs.AgendaWidgetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


const val UPDATE_ACTION = "com.flowmosaic.calendar.broadcast.ACTION_UPDATE_WIDGET"
const val CREATE_ACTION = "com.flowmosaic.calendar.broadcast.ACTION_CREATE_EVENT"
const val CLICK_ACTION = "com.flowmosaic.calendar.widget.CLICK_ACTION"

const val EXTRA_START_TIME = "com.flowmosaic.calendar.START_TIME"
const val EXTRA_END_TIME = "com.flowmosaic.calendar.END_TIME"
const val EXTRA_EVENT_ID = "com.flowmosaic.calendar.EVENT_ID"

/**
 * Implementation of App Widget functionality.
 */
class AgendaWidget : AppWidgetProvider() {

    private var loggerInstance: AgendaWidgetLogger? = null

    private fun getLogger(context: Context): AgendaWidgetLogger {
        if (loggerInstance == null) {
            loggerInstance = AgendaWidgetLogger(context)
        }
        return loggerInstance!!
    }

    fun forceWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, AgendaWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        onUpdate(context, appWidgetManager, widgetIds)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = AgendaWidgetPrefs(context)
        // I want this to finish before going ahead with other instructions in the method
        CoroutineScope(Dispatchers.Main).launch {
            prefs.initSelectedCalendars(context)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
        if (prefs.getShouldLogWidgetActivityEvent()) {
            getLogger(context).logWidgetLifecycleEvent(
                AgendaWidgetLogger.WidgetStatus.ACTIVE, mapOf(
                    "number_of_widgets" to appWidgetIds.size.toString(),
                )
            )
            prefs.setWidgetActivityEventLastLoggedTimestamp()
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        getLogger(context).logWidgetLifecycleEvent(AgendaWidgetLogger.WidgetStatus.ENABLED)
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        getLogger(context).logWidgetLifecycleEvent(
            AgendaWidgetLogger.WidgetStatus.DISABLED
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        getLogger(context).logWidgetLifecycleEvent(AgendaWidgetLogger.WidgetStatus.DELETED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UPDATE_ACTION -> {
                handleClickWidgetRefresh(context, intent)
            }
            CREATE_ACTION -> {
                handleClickWidgetCreate(context)
            }
            CLICK_ACTION -> {
                handleClickWidgetAgendaItem(context, intent)
            }
        }

        super.onReceive(context, intent)
    }

    private fun handleClickWidgetRefresh(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId,
                showProgress = true
            )
            val delayMillis = 300L
            Handler(Looper.getMainLooper()).postDelayed({
                updateWidget(
                    context,
                    AppWidgetManager.getInstance(context),
                    widgetId,
                    showProgress = false
                )
            }, delayMillis)
        }

        getLogger(context).logActionButtonEvent(
            AgendaWidgetLogger.ActionButton.REFRESH
        )
    }

    private fun handleClickWidgetCreate(context: Context) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        getLogger(context).logActionButtonEvent(
            AgendaWidgetLogger.ActionButton.ADD_EVENT
        )
    }

    private fun handleClickWidgetAgendaItem(context: Context, intent: Intent) {
        val startTime: Long = intent.getLongExtra(EXTRA_START_TIME, 0)
        val endTime: Long = intent.getLongExtra(EXTRA_END_TIME, 0)
        val eventId: Long = intent.getLongExtra(EXTRA_EVENT_ID, 0)
        val builder: Uri.Builder = CalendarContract.CONTENT_URI.buildUpon()

        when {
            eventId > 0 -> {
                getLogger(context).logSelectItemEvent(
                    AgendaWidgetLogger.WidgetItemName.EVENT
                )
                builder.appendPath("events")
                ContentUris.appendId(builder, eventId)
            }

            startTime > 0 -> {
                getLogger(context).logSelectItemEvent(
                    AgendaWidgetLogger.WidgetItemName.DATE
                )
                builder.appendPath("time")
                ContentUris.appendId(builder, startTime)
            }

            else -> {
                builder.appendPath("time")
                ContentUris.appendId(builder, System.currentTimeMillis())
            }
        }

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)

        try {
            context.startActivity(viewIntent)
        } catch (error: Error) {
            getLogger(context).logException(mapOf("error" to error.stackTrace.toString(), "location" to "handleClickWidgetAgendaItem"))
        }
    }



    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        showProgress: Boolean = false
    ) {
        if (hasCalendarPermission(context)) {
            AgendaWidgetRenderer.renderCalendarWidget(
                context,
                appWidgetManager,
                widgetId,
                showProgress
            )
        } else {
            AgendaWidgetRenderer.renderPermissionRequestView(context, appWidgetManager, widgetId)
        }
    }

}
