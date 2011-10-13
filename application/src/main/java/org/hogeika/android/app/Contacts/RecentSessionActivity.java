package org.hogeika.android.app.Contacts;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineUser;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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

public class RecentSessionActivity extends Activity {
	private static final int DIALOG_PROGRESS = 2;

	ContactsApplication mApplication;

	private class ContactData {
		private final TimeLineItem mItem;
		private final TimeLineUser mUser;

		public ContactData(TimeLineItem item, TimeLineUser user) {
			super();
			mItem = item;
			mUser = user;
		}
		private Bitmap getIcon() {
			return mUser.getBitmapIcon();
		}
		private String getDisplayName() {
			return mUser.getDisplayName();
		}
		private Drawable getTypeIcon(){
			return mItem.getIconDrawable();
		}
		private String getTitle(){
			return mItem.getTitle();
		}
		private String getSummary() {
			return mItem.getSummary();
		}
		private Uri getContactLookupUri() {
			return mUser.getContactLookupUri();
		}
		private int getDirection(){
			return mItem.getDirection();
		}
		private long getTimeStamp(){
			return mItem.getTimeStamp();
		}
	}

	private class ContactAdapter extends ArrayAdapter<ContactData>{

		public ContactAdapter(Context context, List<ContactData> objects) {
			super(context, R.layout.listitem_recent_contact, objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// Inflate a view template
			if (convertView == null) {
				LayoutInflater layoutInflater = getLayoutInflater();
				convertView = layoutInflater.inflate(R.layout.listitem_recent_contact, parent, false);
			}
			TextView contactName = (TextView) convertView.findViewById(R.id.TextView_contactName);
			TextView body = (TextView) convertView.findViewById(R.id.TextView_body);
			ImageView contactIcon = (ImageView) convertView.findViewById(R.id.ImageView_contactIcon);
			ImageView typeIcon = (ImageView) convertView.findViewById(R.id.ImageView_typeIcon);
			ImageView directionIcon = (ImageView) convertView.findViewById(R.id.ImageView_directionIcon);
			TextView timestamp = (TextView) convertView.findViewById(R.id.TextView_timeStamp);
			TextView title = (TextView) convertView.findViewById(R.id.TextView_title);
			
			ContactData data = getItem(position);
			contactName.setText(data.getDisplayName());
			body.setText(data.getSummary());
			Bitmap icon = data.getIcon();
			if(icon != null){
				contactIcon.setImageBitmap(icon);
			}else{
				contactIcon.setImageResource(R.drawable.ic_contact_picture);
			}
			typeIcon.setImageDrawable(data.getTypeIcon());
			switch(data.getDirection()){
			case TimeLineItem.DIRECTION_INCOMING:
				directionIcon.setImageResource(android.R.drawable.sym_call_incoming);
				break;
			case TimeLineItem.DIRECTION_OUTGOING:
				directionIcon.setImageResource(android.R.drawable.sym_call_outgoing);
				break;
			case TimeLineItem.DIRECTION_MISSED:
				directionIcon.setImageResource(android.R.drawable.sym_call_missed);
				break;
			default:
				directionIcon.setImageResource(android.R.drawable.sym_action_chat);
				break;
			}
			timestamp.setText(DateUtils.formatSameDayTime(data.getTimeStamp(), System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT));
			title.setText(data.getTitle());
			return convertView;
		}		
	}
	
	private final TimeLineManager.Listener mListener = new TimeLineManager.Listener() {
		@Override
		public void onUpdate() {
			updateTimeLine(false);
		}
	};

	private ArrayList<ContactData> mList;
	private ContactAdapter mAdapter;
	private ListView mListView;
	private int mFirstVisiblePosition = 0;
	private int mFirstChildTop = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recent_session);
		
		mApplication = (ContactsApplication)getApplication();
		
		mList = new ArrayList<ContactData>();
		mAdapter = new ContactAdapter(this, mList);
		mListView = (ListView)findViewById(R.id.ListView_recentSession);
		mListView.setAdapter(mAdapter);
		
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				ContactData data = mAdapter.getItem(position);
				Intent intent = new Intent(getApplicationContext(), ContactSessionActivity.class);
				intent.putExtra(ContactSessionActivity.EXTRA_CONTACT_LOOKUP_URI, data.getContactLookupUri());
				startActivity(intent);
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
				SortedMap<TimeLineUser, SortedSet<TimeLineItem>> contactsTiimeline = manager.getRecentContacts();
				final List<ContactData> tmpList = new ArrayList<ContactData>();
				for(TimeLineUser user : contactsTiimeline.keySet()){
					SortedSet<TimeLineItem> timeLine = contactsTiimeline.get(user);
					if(timeLine == null){
						continue;
					}
					TimeLineItem item = timeLine.first();
					ContactData data = new ContactData(item, user);
					tmpList.add(data);
				}
				runOnUiThread(new Runnable() {				
					@Override
					public void run() {
						mList.clear();		
						mList.addAll(tmpList);
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
