package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import org.apache.commons.lang.StringUtils;
import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.QuickContact;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class ContactSessionActivity extends Activity {
	private static final int DIALOG_PROGRESS = 2;
	public static final String EXTRA_CONTACT_LOOKUP_URI = "contact_lookup_uri";
	
	private class SessionDataAdapter extends ArrayAdapter<TimeLineItem> {

		public SessionDataAdapter(Context context, List<TimeLineItem> data) {
			super(context, R.layout.listitem_contact_session_incoming, data);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TimeLineItem data = getItem(position);
			int direction = data.getDirection();
			// Inflate a view template
			if (convertView == null) {
				LayoutInflater layoutInflater = getLayoutInflater();
				if(data.getDirection() == TimeLineItem.DIRECTION_OUTGOING){
					convertView = layoutInflater.inflate(R.layout.listitem_contact_session_outgoing, parent, false);
				}else{
					convertView = layoutInflater.inflate(R.layout.listitem_contact_session_incoming, parent, false);
				}
			} else {
				if(convertView.getId() == R.id.Layout_outgoing && direction != TimeLineItem.DIRECTION_OUTGOING){
					LayoutInflater layoutInflater = getLayoutInflater();
					convertView = layoutInflater.inflate(R.layout.listitem_contact_session_incoming, parent, false);
				}
				else if(convertView.getId() == R.id.Layout_incoming && direction == TimeLineItem.DIRECTION_OUTGOING){
					LayoutInflater layoutInflater = getLayoutInflater();
					convertView = layoutInflater.inflate(R.layout.listitem_contact_session_outgoing, parent, false);
				}
			}
			TextView title = (TextView) convertView.findViewById(R.id.TextView_title);
			TextView body = (TextView) convertView.findViewById(R.id.TextView_body);
			ImageView typeIcon = (ImageView) convertView.findViewById(R.id.ImageView_typeIcon);
			ImageView directionIcon = (ImageView) convertView.findViewById(R.id.ImageView_directionIcon);
			TextView timestamp = (TextView) convertView.findViewById(R.id.TextView_timeStamp);
			
			title.setText(data.getTitle());
			body.setText(data.getSummary());
			typeIcon.setImageDrawable(data.getIconDrawable());
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

			return convertView;
		}				
	}
	
	private ContactsApplication mApplication;
	private ArrayList<TimeLineItem> mList;
	private SessionDataAdapter mAdapter;
	private ListView mListView;
	private int mFirstVisiblePosition = 0;
	private int mFirstChildTop = 0;

	private Uri mLookupUri;
	private final TimeLineManager.Listener mListener = new TimeLineManager.Listener() {
		@Override
		public void onUpdate() {
			updateTimeLine(false);
		}
	};
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.contact_session);
		
		mApplication = (ContactsApplication)getApplication();
		
		Intent intent = getIntent();
		mLookupUri = intent.getParcelableExtra(EXTRA_CONTACT_LOOKUP_URI);
		
		mList = new ArrayList<TimeLineItem>();
		mAdapter = new SessionDataAdapter(this, mList);
		mListView = (ListView)findViewById(R.id.ListView_contactSession);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				TimeLineItem data = mAdapter.getItem(position);
				Intent intent = data.getIntent();
				if(intent != null){
					startActivity(intent);
				}else{
					String message = data.getSummary();
					if(StringUtils.isEmpty(message)){
						message = data.getTitle();
					}
					AlertDialog dialog = new AlertDialog.Builder(ContactSessionActivity.this)
						.setMessage(message)
						.setCancelable(true)
						.setNegativeButton("close", null)
						.create();
					dialog.show();
				}
			}
		});
		mFirstVisiblePosition = 0;
		mFirstChildTop = 0;

		QuickContactBadge badge = (QuickContactBadge)findViewById(R.id.QuickContactBadge_contact);
		badge.assignContactUri(mLookupUri);
		badge.setMode(QuickContact.MODE_SMALL);
		InputStream is = Contacts.openContactPhotoInputStream(getContentResolver(), Contacts.lookupContact(getContentResolver(), mLookupUri));
		if(is != null){
			Bitmap icon = BitmapFactory.decodeStream(is);
			badge.setImageBitmap(icon);
		}
		
		TextView contactName = (TextView)findViewById(R.id.TextView_contactName);
		Cursor c = managedQuery(mLookupUri, new String[]{Contacts.DISPLAY_NAME}, null, null, null);
		c.moveToFirst();
		String displayName = c.getString(0);
		contactName.setText(displayName);
	}

	@Override
	protected void onDestroy() {
		mApplication.getTimeLineManager().removeListener(mListener);
		super.onDestroy();
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
		if(showDialog){
			showDialog(DIALOG_PROGRESS);
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				SortedSet<TimeLineItem> timeLine = mApplication.getTimeLineManager().getTimeLine(mLookupUri);
				final List<TimeLineItem> tmpList = new ArrayList<TimeLineItem>();
				for(TimeLineItem item : timeLine){
					tmpList.add(item);
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
