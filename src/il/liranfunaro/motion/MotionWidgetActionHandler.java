package il.liranfunaro.motion;

import il.liranfunaro.motion.client.CameraStatus;
import il.liranfunaro.motion.client.MotionCameraClient;
import il.liranfunaro.motion.exceptions.HostNotExistException;

import java.util.Calendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.RemoteViews;

class MotionWidgetActionHandler {
	final private Context context;
	final private AppWidgetManager appWidgetManager;
	final private int appWidgetId;
	final private String action;
	final private RemoteViews remoteViews;
	
	private HostPreferences host = null;
	private MotionCameraClient camera = null;
	
	public MotionWidgetActionHandler(Context context,
			AppWidgetManager appWidgetManager, int appWidgetId) {
		this(context, appWidgetManager, appWidgetId, MotionWidgetProvider.ACTION_WIDGET_STATUS);
	}
	
	public MotionWidgetActionHandler(Context context,int appWidgetId, String action) {
		this(context, AppWidgetManager.getInstance(context), appWidgetId, action);
	}
	
	public MotionWidgetActionHandler(Context context,
			AppWidgetManager appWidgetManager, int appWidgetId,
			String action) {
		super();
		this.context = context;
		this.appWidgetManager = appWidgetManager;
		this.appWidgetId = appWidgetId;
		this.action = action;
		remoteViews = new RemoteViews(context.getPackageName(),	R.layout.widget);
	}
	
	protected synchronized void updateChangesToWidget() {
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}
	
	protected Intent createActionIntent(String action) {
		Intent intent = new Intent(context, MotionWidgetProvider.class);
		intent.setAction(action);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		return intent;
	}
	
	protected PendingIntent createActionPendingIntent(String action) {
		Intent intent = createActionIntent(action);
		return PendingIntent.getBroadcast(
				context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
	}
	
	protected void setButtonAction(String action, int viewId) {
		PendingIntent pendingIntent = createActionPendingIntent(action);
		remoteViews.setOnClickPendingIntent(viewId, pendingIntent);
	}
	
	protected synchronized void updateActions() {
		setButtonAction(MotionWidgetProvider.ACTION_WIDGET_STATUS, R.id.button_status);
		setButtonAction(MotionWidgetProvider.ACTION_WIDGET_START, R.id.button_start);
		setButtonAction(MotionWidgetProvider.ACTION_WIDGET_PAUSE, R.id.button_pause);
		setButtonAction(MotionWidgetProvider.ACTION_WIDGET_SNAPSHOT, R.id.button_snapshot);
		setButtonAction(MotionWidgetProvider.ACTION_WIDGET_LIVE_STREAM, R.id.button_livestream);
		
		updateChangesToWidget();
	}
	
	public void updateWidget() {
		applyWidgetAction();
		updateActions();
	}
	
	protected void updateWidget(boolean enabled, boolean loading, String statusText) {
		String time = DateFormat.getTimeFormat(context).format(
				Calendar.getInstance().getTime());
		updateWidget(enabled, loading, statusText, "updated: " + time);
	}
	
	protected synchronized void updateWidget(boolean enabled, boolean loading, String statusText, String lastUpdateText) {
		remoteViews.setTextViewText(R.id.status,statusText);
		remoteViews.setTextViewText(R.id.lastUpdate, lastUpdateText);
		
		remoteViews.setViewVisibility(R.id.updatingIndicator, loading ? View.VISIBLE : View.GONE);
		remoteViews.setViewVisibility(R.id.lastUpdate, loading ? View.GONE : View.VISIBLE);
		
		enableWidget(enabled);
		updateChangesToWidget();
	}
	
	protected synchronized void enableWidget(boolean enabled) {
		int visibility = enabled ? View.VISIBLE : View.GONE;
		
		remoteViews.setViewVisibility(R.id.button_status, visibility);
		remoteViews.setViewVisibility(R.id.button_start, visibility);
		remoteViews.setViewVisibility(R.id.button_pause, visibility);
		remoteViews.setViewVisibility(R.id.button_snapshot, visibility);
	}
	
	public void applyWidgetAction() {
		if(action == null) {
			return;
		}
		
		try {
			SharedPreferences prefs = MotionWidgetProvider.getSharedPreferences(context);
			host = MotionWidgetProvider.getWidgetHostPreferences(context, prefs, appWidgetId);
			camera = MotionWidgetProvider.getWidgetCameraClient(context, prefs, host, appWidgetId);
			
			doAsyncAction();
		} catch (HostNotExistException e) {
			updateWidget(false, false, "Host does not exist", "error");
		}
	}
	
	protected void doAsyncAction() {
		updateWidget(true, true, getStatusText(CameraStatus.LOADING));

		new Thread(new Runnable() {

			@Override
			public void run() {
				CameraStatus status = null;

				try {
					status = doAction();
				} finally {
					updateWidget(true, false, getStatusText(status));
				}

			}
		}).start();
	}
	
	protected CameraStatus doAction() {
		if (action.equals(MotionWidgetProvider.ACTION_WIDGET_STATUS)) {
			return camera.getStatus();
		} else if (action.equals(MotionWidgetProvider.ACTION_WIDGET_START)) {
			return camera.startDetection();
		} else if (action.equals(MotionWidgetProvider.ACTION_WIDGET_PAUSE)) {
			return camera.pauseDetection();
		} else if (action.equals(MotionWidgetProvider.ACTION_WIDGET_SNAPSHOT)) {
			camera.snapshot();
			return camera.getStatus();
		} else if(action.equals(MotionWidgetProvider.ACTION_WIDGET_LIVE_STREAM)) {
			Intent intent = new Intent(context, LiveCameraActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			GenericCameraActivity.setIntentParameters(intent, host.getUUID(), camera.getCameraNumber());
			context.startActivity(intent);
			return camera.getStatus();
		}

		return camera.getStatus();
	}
	
	protected String getStatusText(CameraStatus status) {
		String stautsText = status == null ? "unavailable" : status.toString();
		return String.format(MotionWidgetProvider.STATUS_TEXT_FORMAT, host != null ? host.getName() : "unkown",
				camera != null ? camera.getCameraNumber() : "?", stautsText);
	}
}