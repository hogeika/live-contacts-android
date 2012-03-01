package org.hogeika.android.app.Contacts;

import java.text.DateFormat;

import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineCursor;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
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

public class RecentSessionActivity extends AbstractTimeLiveViewActivity {
	private class RecentContactAdapter extends ResourceCursorAdapter {
		public RecentContactAdapter(Context context, TimeLineCursor c) {
			super(context, R.layout.listitem_recent_contact, c);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			TimeLineCursor c = (TimeLineCursor) cursor;

			TextView contactName = (TextView) view.findViewById(R.id.TextView_contactName);
			TextView body = (TextView) view.findViewById(R.id.TextView_body);
			QuickContactBadge contactIcon = (QuickContactBadge) view.findViewById(R.id.QuickContactBadge_contactIcon);
			ImageView typeIcon = (ImageView) view.findViewById(R.id.ImageView_typeIcon);
			ImageView directionIcon = (ImageView) view.findViewById(R.id.ImageView_directionIcon);
			TextView timestamp = (TextView) view.findViewById(R.id.TextView_timeStamp);
			TextView title = (TextView) view.findViewById(R.id.TextView_title);
			
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
			switch(c.getDirection()){
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
			timestamp.setText(DateUtils.formatSameDayTime(c.getTimeStamp(), System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT));
			title.setText(c.getTitle());
			
			view.setTag(c.getContactLookupUri());
		}
		
	}

	private TimeLineCursor mCursor;
	private RecentContactAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recent_session);
		
		mCursor = getTimeLineManager().getRecentContactsCursor();
		mAdapter = new RecentContactAdapter(this, mCursor);
		ListView mListView = (ListView)findViewById(R.id.ListView_recentSession);
		mListView.setAdapter(mAdapter);
		
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent intent = new Intent(getApplicationContext(), ContactSessionActivity.class);
				intent.putExtra(ContactSessionActivity.EXTRA_CONTACT_LOOKUP_URI, (Uri)view.getTag());
				startActivity(intent);
			}
		});
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				ContactsContract.QuickContact.showQuickContact(getApplicationContext(), view, (Uri)view.getTag(), ContactsContract.QuickContact.MODE_MEDIUM, null);
				return true;
			}
		});
	}

	@Override
	protected void onDestroy() {
		mCursor.close();
		super.onDestroy();
	}

	@Override
	protected void onTimeLineUpdated() {
		mCursor.close();
		mCursor = getTimeLineManager().getRecentContactsCursor();
		mAdapter.changeCursor(mCursor);
	}

}
