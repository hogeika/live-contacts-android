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
		ListView listView = (ListView)findViewById(R.id.ListView_contactSession);
		listView.setAdapter(mAdapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
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
