package il.liranfunaro.motion;

import il.liranfunaro.motion.client.MotionCameraClient;
import il.liranfunaro.motion.exceptions.HostNotExistException;
import uk.me.malcolmlandon.motion.MotionWidgetOldConfigure;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

public class MotionWidgetProvider extends AppWidgetProvider {
	public static final String PREFS_NAME = MotionWidgetProvider.class.toString();

	public static final String PREF_WIDGET_HOST_UUID = "UUID_OF_WIDGET_";
	public static final String PREF_WIDGET_HOST_CAMERA = "CAMERA_OF_WIDGET_";

	public static String ACTION_WIDGET_STATUS = "ActionWidgetStatus";
	public static String ACTION_WIDGET_START = "ActionWidgetStart";
	public static String ACTION_WIDGET_PAUSE = "ActionWidgetPause";
	public static String ACTION_WIDGET_SNAPSHOT = "ActionWidgetSnapshot";
	public static String ACTION_WIDGET_LIVE_STREAM = "ActionWidgetLiveStream";
	public static String STATUS_TEXT_FORMAT = "%s (%s): %s";
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		for (int id : appWidgetIds) {
			new MotionWidgetActionHandler(context, appWidgetManager, id).updateWidget();
		}
	}
	
	public static void updateWidget(Context context, int appWidgetId) {
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		new MotionWidgetActionHandler(context, appWidgetManager, appWidgetId).updateWidget();
	}
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		int appWidgetId = getAppWidgetId(intent.getExtras());
		String action = intent.getAction();
		
		if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            removeWidgetPreferences(context, appWidgetId);
        } else {
        	widgetAction(context, appWidgetId, action);
        }
		
		super.onReceive(context, intent);
	}
	
	public static void widgetAction(Context context, int appWidgetId, String action) {
		new MotionWidgetActionHandler(context, appWidgetId, action).applyWidgetAction();
	}
	
	protected int getAppWidgetId(Bundle extras) {
		int mAppWidgetId = -1;
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		return mAppWidgetId;
	}
	
	public static void initWidget(Context context, int appWidgetId,
			String hostUUID, String camera) {
		setWidgetPreferences(context, appWidgetId, hostUUID, camera);
		updateWidget(context, appWidgetId);
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
}
