package org.hogeika.android.app.Contacts;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import org.hogeika.android.app.Contacts.TimeLineManager.ActivityStreamItem;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineUser;

import android.app.Activity;
import android.app.Dialog;
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
	private static final int DIALOG_PROGRESS = 3;

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
	private ListView mListView;
	private int mFirstVisiblePosition = 0;
	private int mFirstChildTop = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);

		mApplication = (ContactsApplication)getApplication();
		
		mList = new ArrayList<ActivityStreamItem>();
		mAdapter = new ContactAdapter(this, mList);
		mListView = (ListView)findViewById(R.id.ListView_activityStream);
		mListView.setAdapter(mAdapter);
		
		mListView.setOnItemClickListener(new OnItemClickListener() {
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
		mFirstVisiblePosition = 0;
		mFirstChildTop = 0;
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateTimeLine(true);
		mApplication.getTimeLineManager().addListener(mListener);
	}

	@Override
	protected void onPause() {
		mApplication.getTimeLineManager().removeListener(mListener);
		super.onPause();
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mFirstVisiblePosition = savedInstanceState.getInt("FVP");
		mFirstChildTop = savedInstanceState.getInt("FCT");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("FVP", mListView.getFirstVisiblePosition());
		if(mListView.getChildCount()>0){
			outState.putInt("FCT", mListView.getChildAt(0).getTop());
		}else{
			outState.putInt("FCT", 0);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		case DIALOG_PROGRESS:
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setCancelable(false);
			return dialog;
		}
		return super.onCreateDialog(id);
	}
	
	private boolean mIsUpdating = false;
	private synchronized void updateTimeLine(final boolean showDialog) {
		if(mIsUpdating){
			return;
		}
		mIsUpdating = true;
		if(showDialog){
			showDialog(DIALOG_PROGRESS);
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
						if(mFirstVisiblePosition > 0 || mFirstChildTop > 0){
							mListView.setSelectionFromTop(mFirstVisiblePosition, mFirstChildTop);
						}
						mFirstVisiblePosition = 0;
						mFirstChildTop = 0;
						if(showDialog){
							try {
								dismissDialog(DIALOG_PROGRESS);
							}catch(IllegalArgumentException e){
							}
						}
						mIsUpdating = false;
					}
				});
			}
		}).start();
	}

}
