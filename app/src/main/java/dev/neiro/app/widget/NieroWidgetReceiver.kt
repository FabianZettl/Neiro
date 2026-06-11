package dev.neiro.app.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NieroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: NieroWidget = NieroWidget()
}
