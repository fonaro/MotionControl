package il.liranfunaro.motion;

import il.liranfunaro.motion.client.HostStatus;
import il.liranfunaro.motion.client.MotionHostClient;
import il.liranfunaro.motion.exceptions.HostNotExistException;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

public class HostListAdapter extends BaseExpandableListAdapter {
	private final Context context;
	private final Activity itsActivity;
	private final int myAppWidgetId;
	private final boolean isForWidget;
	
	protected HostPreferences[] hosts;
	protected MotionHostClient[] hostsClient;
	
	public HostListAdapter(Activity activity, int myAppWidgetId) {
		this.itsActivity = activity;
		this.context = itsActivity.getApplicationContext();
		this.myAppWidgetId = myAppWidgetId;
		this.isForWidget = myAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
		
		updateHosts(false);
	}
	
	public void updateHosts() {
		updateHosts(true);
	}
	
	public void updateHosts(boolean notify) {
		Set<HostPreferences> hostsSet = new TreeSet<HostPreferences>();
		
		Set<String> hostsUUID = HostPreferences.getHostsList(itsActivity);
		for (String uuid : hostsUUID) {
			try {
				hostsSet.add(new HostPreferences(context, uuid, false));
			} catch (HostNotExistException e) {
				Log.e(getClass().getSimpleName(), "Missing Host", e);
			}
		}
		
		int hostCount = hostsSet.size();
		
		this.hosts = hostsSet.toArray(new HostPreferences[hostCount]);
		this.hostsClient = new MotionHostClient[hostCount];
		
		if(notify) {
			notifyDataSetChanged();
		}
	}

	@Override
	public Object getChild(int groupPosition, int childPosition) {
		if(hostsClient[groupPosition] == null) {
			return null;
		}

		switch(hostsClient[groupPosition].getHostStatus()) {
		case AVAILIBLE:
			return hostsClient[groupPosition].getCamera(childPosition);
		default:
			return null;
		}
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return getCombinedChildId(groupPosition, childPosition);
	}
	
	@Override
	public View getChildView(final int groupPosition, final int childPosition,
			boolean isLastChild, View convertView, ViewGroup parent) {
		HostStatus hostStatus = hostsClient[groupPosition].getHostStatus();
		ArrayList<String> availibleCamera = hostsClient[groupPosition].getAvalibleCameras();
		
		String cameraNumber = availibleCamera == null ? null : availibleCamera.get(childPosition); 
		
		ChildState state;
		String message = null;
		
		switch (hostStatus) {
		case UNAUTHORIZED:
		case UNAVALIBLE:
			state = ChildState.ERROR;
			message = hostStatus.getUserMessage();
			break;
		default:
			if(cameraNumber != null) {
				state = ChildState.READY;
			} else {
				state = ChildState.LOADING;
			}
		}
		
		return getChildView(groupPosition, childPosition, state, message,
				cameraNumber, convertView, parent);
	}
	
	public static enum ChildState {
		LOADING, READY, ERROR
	}
	
	public View getChildView(final int groupPosition, int childPosition,
			ChildState state, String message, final String cameraNumber,
			View convertView, ViewGroup parent) {
		if (convertView == null) {
			LayoutInflater inflater = itsActivity.getLayoutInflater();
			convertView = inflater.inflate(R.layout.camera, null);
			convertView.setClickable(true);
		}
		
		TextView textView = (TextView) convertView.findViewById(R.id.cameraNumber);
		ImageButton refreshBtn = (ImageButton) convertView.findViewById(R.id.refreshCamera);
		ImageButton settingsBtn = (ImageButton) convertView.findViewById(R.id.cameraConfiguration);
		
		switch (state) {
		case ERROR:
			textView.setText(message);
			refreshBtn.setVisibility(View.VISIBLE);
			settingsBtn.setVisibility(View.INVISIBLE);
			
			refreshBtn.setOnClickListener(new OnHostRefreshListner(groupPosition));
			refreshBtn.setImageResource(R.drawable.ic_action_refresh);
			
			settingsBtn.setOnClickListener(null);
			
			convertView.setOnClickListener(null);
			break;
		case LOADING:
			textView.setText("Loading...");
			refreshBtn.setVisibility(View.INVISIBLE);
			settingsBtn.setVisibility(View.INVISIBLE);
			
			refreshBtn.setOnClickListener(null);
			settingsBtn.setOnClickListener(null);
			
			convertView.setOnClickListener(null);
			break;
		case READY:
			textView.setText("Camera " + cameraNumber);
			refreshBtn.setVisibility(View.VISIBLE);
			settingsBtn.setVisibility(View.VISIBLE);
			
			refreshBtn.setOnClickListener(null);
			refreshBtn.setImageResource(R.drawable.ic_action_camera);
			settingsBtn.setOnClickListener(new OnCameraSettingsListner(groupPosition, cameraNumber));
			
			convertView.setOnClickListener(isForWidget ?
					new OnWidgetSelectCameraListner(groupPosition, cameraNumber) : 
					new OnMainSelectCameraListner(groupPosition, cameraNumber));
		}
		
		return convertView;
	}

	@Override
	public int getChildrenCount(int groupPosition) {
		if(hostsClient[groupPosition] == null) {
			hostsClient[groupPosition] = 
					new MotionHostClient(hosts[groupPosition], GeneralPreferences.getConnectionTimeout(context));
		}
		
		ArrayList<String> availibleCamera = hostsClient[groupPosition].getAvalibleCameras();
		if(availibleCamera != null) {
			return availibleCamera.size();
		}
		
		HostStatus hostStatus = hostsClient[groupPosition].getHostStatus();
		
		switch(hostStatus) {
		case UNAUTHORIZED:
		case UNAVALIBLE:
			break;
		case AVAILIBLE:
		case UNKNOWN:
		default:
			hostsClient[groupPosition].fetchAvailibleCamerasAsync(new Runnable() {
				
				@Override
				public void run() {
					itsActivity.runOnUiThread(new Runnable(){
					    public void run(){
					        notifyDataSetChanged();
					    }
					});
				}
			});
			break;
		}
		
		return 1;
	}
	
	@Override
	public Object getGroup(int groupPosition) {
		return hosts[groupPosition];
	}

	@Override
	public int getGroupCount() {
		return hosts.length;
	}

	@Override
	public long getGroupId(int groupPosition) {
		return groupPosition;
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
		if (convertView == null) {
            LayoutInflater infalInflater = itsActivity.getLayoutInflater();
            convertView = infalInflater.inflate(R.layout.host, null);
        }
		
		final HostPreferences host = hosts[groupPosition];
		
		ImageButton editHostButton = (ImageButton) convertView.findViewById(R.id.editHost);
		TextView hostNameView = (TextView) convertView.findViewById(R.id.hostName);
		TextView hostUrl = (TextView) convertView.findViewById(R.id.hostUrl);
		TextView hostUsername = (TextView) convertView.findViewById(R.id.hostUsername);
		
        editHostButton.setOnClickListener(new OnEditHostListner(groupPosition));
        hostNameView.setText(host.getName());
		hostUrl.setText(host.getExternalHost().getHost());
		hostUsername.setText(host.getUsername());
        
        return convertView;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition) {
		ArrayList<String> availibleCamera = hostsClient[groupPosition].getAvalibleCameras();
		return availibleCamera != null;
	}
	
	public class OnHostRefreshListner implements OnClickListener {
		
		private final int groupPosition;
		
		public OnHostRefreshListner(int groupPosition) {
			this.groupPosition = groupPosition;
		}
		
		@Override
		public void onClick(View v) {
			hostsClient[groupPosition] = null;
			notifyDataSetChanged();
		}
	}
	
	public class OnCameraSettingsListner implements OnClickListener {
		
		private final int groupPosition;
		private final String cameraNumber;
		
		public OnCameraSettingsListner(int groupPosition, String cameraNumber) {
			this.groupPosition = groupPosition;
			this.cameraNumber = cameraNumber;
		}
		
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(context, CameraConfigurationActivity.class);
			GenericCameraActivity.setIntentParameters(intent, hosts[groupPosition].getUUID(), cameraNumber);
			itsActivity.startActivity(intent);
		}
	}
	
	public class OnWidgetSelectCameraListner implements OnClickListener {
		private final int groupPosition;
		private final String cameraNumber;
		
		public OnWidgetSelectCameraListner(int groupPosition, String cameraNumber) {
			this.groupPosition = groupPosition;
			this.cameraNumber = cameraNumber;
		}
		
		@Override
		public void onClick(View v) {
			MotionWidgetProvider.initWidget(context, myAppWidgetId, hosts[groupPosition].getUUID().toString(), cameraNumber);
			
			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
					myAppWidgetId);
			itsActivity.setResult(Activity.RESULT_OK, resultValue);
			itsActivity.finish();
		}
	}
	
	public class OnMainSelectCameraListner implements OnClickListener {
		
		private final int groupPosition;
		private final String cameraNumber;
		
		public OnMainSelectCameraListner(int groupPosition, String cameraNumber) {
			this.groupPosition = groupPosition;
			this.cameraNumber = cameraNumber;
		}
		
		@Override
		public void onClick(View v) {
			Intent intent = new Intent(context, LiveCameraActivity.class);
			GenericCameraActivity.setIntentParameters(intent, hosts[groupPosition].getUUID(), cameraNumber);
			itsActivity.startActivity(intent);
		}
	}
	
	public class OnEditHostListner implements OnClickListener {
		
		private final int groupPosition;
		
		public OnEditHostListner(int groupPosition) {
			this.groupPosition = groupPosition;
		}
		
        public void onClick(View v) {
        	hosts[groupPosition].edit(itsActivity);
        }
    }
}
