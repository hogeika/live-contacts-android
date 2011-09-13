package org.hogeika.android.app.Contacts;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.TimeLineManager.ActivityStreamItem;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineUser;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ActivityStreamActivity extends Activity {
	ContactsApplication mApplication;

	private class ContactAdapter extends ArrayAdapter<ActivityStreamItem>{

		public ContactAdapter(Context context, List<ActivityStreamItem> objects) {
			super(context, R.layout.listitem_recent_contact, objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// Inflate a view template
			if (convertView == null) {
				LayoutInflater layoutInflater = getLayoutInflater();
				convertView = layoutInflater.inflate(R.layout.listitem_activity_stream, parent, false);
			}
			TextView contactName = (TextView) convertView.findViewById(R.id.TextView_contactName);
			TextView body = (TextView) convertView.findViewById(R.id.TextView_body);
			ImageView contactIcon = (ImageView) convertView.findViewById(R.id.ImageView_contactIcon);
			ImageView typeIcon = (ImageView) convertView.findViewById(R.id.ImageView_typeIcon);
			TextView timestamp = (TextView) convertView.findViewById(R.id.TextView_timeStamp);
			TextView action = (TextView) convertView.findViewById(R.id.TextView_action);
			
			ActivityStreamItem data = getItem(position);
			TimeLineUser user = data.getUser();
			contactName.setText(user.getDisplayName());
			body.setText(data.getSummary());
			Bitmap icon = user.getBitmapIcon();
			if(icon != null){
				contactIcon.setImageBitmap(icon);
			}else{
				contactIcon.setImageResource(R.drawable.ic_contact_picture);
			}
			typeIcon.setImageDrawable(data.getIconDrawable());
			timestamp.setText(DateUtils.formatSameDayTime(data.getTimeStamp(), System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT));
			String actionText = data.getActionText();
			if(actionText != null){
				action.setText(data.getActionText());
			}else{
				action.setText("");
			}
			return convertView;
		}		
	}
	
	private final TimeLineManager.Listener mListener = new TimeLineManager.Listener() {
		@Override
		public void onUpdate() {
			updateTimeLine(false);
		}
	};

	private ArrayList<ActivityStreamItem> mList;
	private ContactAdapter mAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);

		mApplication = (ContactsApplication)getApplication();
		
		mList = new ArrayList<ActivityStreamItem>();
		mAdapter = new ContactAdapter(this, mList);
		ListView listView = (ListView)findViewById(R.id.ListView_activityStream);
		listView.setAdapter(mAdapter);
		
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				ActivityStreamItem data = mAdapter.getItem(position);
				Intent intent = data.getIntent();
				if(intent != null){
					startActivity(intent);
				}
			}
		});
		updateTimeLine(true);
		mApplication.getTimeLineManager().addListener(mListener);
	}

	@Override
	protected void onDestroy() {
		mApplication.getTimeLineManager().removeListener(mListener);
		super.onDestroy();
	}
	
	private boolean mIsUpdating = false;
	private ProgressDialog mProgressDialog = null;
	private synchronized void updateTimeLine(final boolean showDialog) {
		if(mIsUpdating){
			return;
		}
		if(showDialog){
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setCancelable(false);
			mProgressDialog.show();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				TimeLineManager manager = mApplication.getTimeLineManager();
				final List<ActivityStreamItem> stream = manager.getActivityStream();
				runOnUiThread(new Runnable() {				
					@Override
					public void run() {
						mList.clear();		
						mList.addAll(stream);
						mAdapter.notifyDataSetChanged();
						if(showDialog){
							mProgressDialog.dismiss();
							mProgressDialog =null;
						}
						mIsUpdating = false;
					}
				});
			}
		}).start();
	}

}
