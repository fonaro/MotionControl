package il.liranfunaro.motion;

import il.liranfunaro.motion.client.CameraStatus;
import il.liranfunaro.motion.client.MotionCameraClient;
import il.liranfunaro.motion.exceptions.HostNotExistException;

import java.util.Calendar;

import uk.me.malcolmlandon.motion.MotionWidgetOldConfigure;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

public class MotionWidget extends AppWidgetProvider {
	public static final String PREFS_NAME = MotionWidget.class.toString();

	public static final String PREF_WIDGET_HOST_UUID = "UUID_OF_WIDGET_";
	public static final String PREF_WIDGET_HOST_CAMERA = "CAMERA_OF_WIDGET_";

	public static String ACTION_WIDGET_STATUS = "ActionWidgetStatus";
	public static String ACTION_WIDGET_START = "ActionWidgetStart";
	public static String ACTION_WIDGET_PAUSE = "ActionWidgetPause";
	public static String ACTION_WIDGET_SNAPSHOT = "ActionWidgetSnapshot";
	public static String ACTION_WIDGET_LIVE_STREAM = "ActionWidgetLiveStream";
	public static String STATUS_TEXT_FORMAT = "%s #%s: %s";
	
	protected static class ActionHandler {
		final private Context context;
		final private AppWidgetManager appWidgetManager;
		final private int appWidgetId;
		final private String action;
		final private RemoteViews remoteViews;
		
		public ActionHandler(Context context,
				AppWidgetManager appWidgetManager, int appWidgetId) {
			this(context, appWidgetManager, appWidgetId, null);
		}
		
		public ActionHandler(Context context,
				AppWidgetManager appWidgetManager, int appWidgetId,
				String action) {
			super();
			this.context = context;
			this.appWidgetManager = appWidgetManager;
			this.appWidgetId = appWidgetId;
			this.action = action;
			remoteViews = new RemoteViews(context.getPackageName(),	R.layout.widget);
		}
		
		protected Intent createActionIntent(String action) {
			Intent intent = new Intent(context, MotionWidget.class);
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
		
		public void updateWidget() {
			setButtonAction(ACTION_WIDGET_STATUS, R.id.button_status);
			setButtonAction(ACTION_WIDGET_START, R.id.button_start);
			setButtonAction(ACTION_WIDGET_PAUSE, R.id.button_pause);
			setButtonAction(ACTION_WIDGET_SNAPSHOT, R.id.button_snapshot);
			setButtonAction(ACTION_WIDGET_LIVE_STREAM, R.id.button_livestream);

			appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		for (int id : appWidgetIds) {
			new ActionHandler(context, appWidgetManager, id).updateWidget();
		}
	}
	
	public static void updateWidget(Context context, int appWidgetId) {
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		new ActionHandler(context, appWidgetManager, appWidgetId).updateWidget();
	}
	
	protected int getAppWidgetId(Bundle extras) {
		int mAppWidgetId = -1;
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		return mAppWidgetId;
	}

	static SharedPreferences getSharedPreferences(Context context) {
		return context.getSharedPreferences(PREFS_NAME, 0);
	}

	static String getWidgetUUID(Context context, int appWidgetId) {
		SharedPreferences prefs = getSharedPreferences(context);
		return getWidgetUUID(prefs, appWidgetId);
	}

	static String getWidgetUUID(SharedPreferences prefs, int appWidgetId) {
		return prefs.getString(PREF_WIDGET_HOST_UUID + appWidgetId, "");
	}

	static HostPreferences getWidgetHostPreferences(Context context,
			int appWidgetId) throws HostNotExistException {
		String uuid = getWidgetUUID(context, appWidgetId);
		return new HostPreferences(context, uuid, false);
	}

	static HostPreferences getWidgetHostPreferences(Context context,
			SharedPreferences prefs, int appWidgetId) throws HostNotExistException {
		String uuid = getWidgetUUID(prefs, appWidgetId);
		if(uuid != null && !uuid.isEmpty()) {
			return new HostPreferences(context, uuid, false);
		} else {
			MotionWidgetOldConfigure old = new MotionWidgetOldConfigure(context, appWidgetId);
			return old.migratePreferences();
		}
	}

	static String getWidgetCamera(Context context, int appWidgetId) {
		SharedPreferences prefs = getSharedPreferences(context);
		return getWidgetCamera(prefs, appWidgetId);
	}

	static String getWidgetCamera(SharedPreferences prefs, int appWidgetId) {
		return prefs.getString(PREF_WIDGET_HOST_CAMERA + appWidgetId, "");
	}

	public static void setWidgetPreferences(Context context, int appWidgetId,
			String hostUUID, String camera) {
		SharedPreferences prefs = getSharedPreferences(context);
		setWidgetPreferences(prefs, appWidgetId, hostUUID, camera);
	}

	public static void setWidgetPreferences(SharedPreferences prefs,
			int appWidgetId, String hostUUID, String camera) {
		Editor edit = prefs.edit();
		setWidgetPreferences(edit, appWidgetId, hostUUID, camera);
		edit.commit();
	}

	public static void setWidgetPreferences(Editor edit, int appWidgetId,
			String hostUUID, String camera) {
		edit.putString(PREF_WIDGET_HOST_UUID + appWidgetId, hostUUID);
		edit.putString(PREF_WIDGET_HOST_CAMERA + appWidgetId, camera);
	}
	
	public static void removeWidgetPreferences(Context context, int appWidgetId) {
		SharedPreferences prefs = getSharedPreferences(context);
		removeWidgetPreferences(prefs, appWidgetId);
	}
	
	public static void removeWidgetPreferences(SharedPreferences prefs,
			int appWidgetId) {
		Editor edit = prefs.edit();
		removeWidgetPreferences(edit, appWidgetId);
		edit.commit();
	}
	
	public static void removeWidgetPreferences(Editor edit, int appWidgetId) {
		edit.remove(PREF_WIDGET_HOST_UUID + appWidgetId);
		edit.remove(PREF_WIDGET_HOST_CAMERA + appWidgetId);
	}

	public static MotionCameraClient getWidgetCameraClient(Context context,
			int appWidgetId) throws HostNotExistException {
		SharedPreferences prefs = getSharedPreferences(context);
		HostPreferences host = getWidgetHostPreferences(context, prefs,
				appWidgetId);
		String camera = getWidgetCamera(prefs, appWidgetId);

		return new MotionCameraClient(host, camera,
				GeneralPreferences.getConnectionTimeout(context));
	}
	
	public static MotionCameraClient getWidgetCameraClient(Context context, SharedPreferences prefs,
			HostPreferences host, int appWidgetId) {
		String camera = getWidgetCamera(prefs, appWidgetId);

		return new MotionCameraClient(host, camera,
				GeneralPreferences.getConnectionTimeout(context));
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		int appWidgetId = getAppWidgetId(intent.getExtras());
		String action = intent.getAction();
		
		Toast.makeText(context, action, Toast.LENGTH_SHORT).show();
		
		if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            removeWidgetPreferences(context, appWidgetId);
        } else {
//        	SharedPreferences prefs = getSharedPreferences(context);
//        	try {
//				HostPreferences host = getWidgetHostPreferences(context, prefs, appWidgetId);
//				doAsyncAction(context, action, host, appWidgetId);
//			} catch (HostNotExistException e) {
//				removeWidgetPreferences(context, appWidgetId);
//			}
        	widgetAction(context, appWidgetId, action);
        }
		
		super.onReceive(context, intent);
	}
	
	public static void widgetAction(Context context, int widgetId, String action) {
		try {
			HostPreferences host = getWidgetHostPreferences(context,
					widgetId);
			doAsyncAction(context, action, host, widgetId);
		} catch (HostNotExistException e) {
			RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
					R.layout.widget);
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
			updateWidget(appWidgetManager, remoteViews, false,
					"Host does not exist", "error", widgetId);
		}
	}
	
	public static String getStatusText(HostPreferences host,
			MotionCameraClient camera, CameraStatus status) {
		String stautsText = status == null ? "UNAVAILABLE" : status.toString();
		return String.format(STATUS_TEXT_FORMAT, host.getName(),
				camera.getCameraNumber(), stautsText);
	}

	public static void doAsyncAction(final Context context, final String action,
			final HostPreferences host,
			final int appWidgetId) {
		final RemoteViews rv = new RemoteViews(context.getPackageName(),
				R.layout.widget);
		final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
		
		SharedPreferences prefs = getSharedPreferences(context);
		final MotionCameraClient camera = getWidgetCameraClient(context, prefs, host, appWidgetId);

		rv.setViewVisibility(R.id.lastUpdate, View.GONE);
		rv.setViewVisibility(R.id.updatingIndicator, View.VISIBLE);
		//rv.setTextViewText(R.id.lastUpdate, "loading...");
		mgr.updateAppWidget(appWidgetId, rv);

		new Thread(new Runnable() {

			@Override
			public void run() {
				CameraStatus status = null;

				try {
					status = doAction(context, host, camera, action);
				} finally {
					updateWidget(context, mgr, rv, true, getStatusText(host, camera, status), appWidgetId);
				}

			}
		}).start();
	}
	
	public static void enableWidget(RemoteViews remoteViews, boolean enabled) {
		int visibility = enabled ? View.VISIBLE : View.GONE;
		
		remoteViews.setViewVisibility(R.id.button_status, visibility);
		remoteViews.setViewVisibility(R.id.button_start, visibility);
		remoteViews.setViewVisibility(R.id.button_pause, visibility);
		remoteViews.setViewVisibility(R.id.button_snapshot, visibility);
	}
	
	public static void updateWidget(Context context, AppWidgetManager appWidgetManager, 
			RemoteViews remoteViews, boolean enabled ,String statusText, int appWidgetId) {
		String time = DateFormat.getTimeFormat(context).format(
				Calendar.getInstance().getTime());
		updateWidget(appWidgetManager, remoteViews, enabled, statusText, 
				"last update: " + time, appWidgetId);
	}
	
	public static void updateWidget(AppWidgetManager appWidgetManager, 
			RemoteViews remoteViews, boolean enabled ,String statusText, String lastUpdateText, int appWidgetId) {
		remoteViews.setTextViewText(R.id.status,statusText);
		remoteViews.setViewVisibility(R.id.updatingIndicator, View.GONE);
		remoteViews.setViewVisibility(R.id.lastUpdate, View.VISIBLE);
		remoteViews.setTextViewText(R.id.lastUpdate, lastUpdateText);
		enableWidget(remoteViews, enabled);
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	public static CameraStatus doAction(Context context, HostPreferences host, MotionCameraClient camera, String action) {
		if (action.equals(ACTION_WIDGET_STATUS)) {
			return camera.getStatus();
		} else if (action.equals(ACTION_WIDGET_START)) {
			return camera.startDetection();
		} else if (action.equals(ACTION_WIDGET_PAUSE)) {
			return camera.pauseDetection();
		} else if (action.equals(ACTION_WIDGET_SNAPSHOT)) {
			camera.snapshot();
			return camera.getStatus();
		} else if(action.equals(ACTION_WIDGET_LIVE_STREAM)) {
			Intent intent = new Intent(context, MjpegActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			GenericCameraActivity.setIntentParameters(intent, host.getUUID(), camera.getCameraNumber());
			context.startActivity(intent);
			return camera.getStatus();
		}

		return camera.getStatus();
	}
}
