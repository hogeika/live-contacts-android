package org.hogeika.android.app.Contacts;

import java.text.DateFormat;

import org.hogeika.android.app.Contacts.TimeLineManager.ActivityStreamCursor;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class ActivityStreamActivity extends AbstractTimeLiveViewActivity {
	private class ActivityStreamAdapter extends ResourceCursorAdapter {

		public ActivityStreamAdapter(Context context, ActivityStreamCursor cursor) {
			super(context,R.layout.listitem_activity_stream, cursor);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ActivityStreamCursor c = (ActivityStreamCursor) cursor;
			
			TextView contactName = (TextView) view.findViewById(R.id.TextView_contactName);
			TextView body = (TextView) view.findViewById(R.id.TextView_body);
			QuickContactBadge contactIcon = (QuickContactBadge) view.findViewById(R.id.QuickContactBadge_contactIcon);
			ImageView typeIcon = (ImageView) view.findViewById(R.id.ImageView_typeIcon);
			TextView timestamp = (TextView) view.findViewById(R.id.TextView_timeStamp);
			TextView action = (TextView) view.findViewById(R.id.TextView_action);
			
			contactName.setText(c.getDisplayName());
			body.setText(c.getSummary());
			Bitmap icon = c.getIcon();
			if(icon != null){
				contactIcon.setImageBitmap(icon);
			}else{
				contactIcon.setImageResource(R.drawable.ic_contact_picture);
			}
			contactIcon.assignContactUri(c.getContactLookupUri());
			contactIcon.setMode(QuickContact.MODE_MEDIUM);
			typeIcon.setImageDrawable(c.getTypeIcon());
			timestamp.setText(DateUtils.formatSameDayTime(c.getTimeStamp(), System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT));
			String actionText = c.getActionText();
			if(actionText != null){
				action.setText(actionText);
			}else{
				action.setText("");
			}
		}
	}
	
	private ActivityStreamCursor mCursor;
	private ActivityStreamAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);
		
		mCursor = getTimeLineManager().getActivityStreamCursor();
		mAdapter = new ActivityStreamAdapter(this, mCursor);
		ListView mListView = (ListView)findViewById(R.id.ListView_activityStream);
		mListView.setAdapter(mAdapter);
		
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				ActivityStreamCursor c = (ActivityStreamCursor)mAdapter.getItem(position);
				Intent intent = c.getIntent();
				if(intent != null){
					startActivity(intent);
				}
			}
		});
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				ActivityStreamCursor c = (ActivityStreamCursor)mAdapter.getItem(position);
				ContactsContract.QuickContact.showQuickContact(getApplicationContext(), view, c.getContactLookupUri(), ContactsContract.QuickContact.MODE_MEDIUM, null);
				return true;
			}
		});
	}

	@Override
	protected void onTimeLineUpdated() {
		mCursor.close();
		mCursor = getTimeLineManager().getActivityStreamCursor();
		mAdapter.changeCursor(mCursor);
	}
}
