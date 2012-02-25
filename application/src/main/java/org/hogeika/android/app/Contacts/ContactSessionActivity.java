package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.text.DateFormat;

import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineCursor;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class ContactSessionActivity extends AbstractTimeLiveViewActivity {
	public static final String EXTRA_CONTACT_LOOKUP_URI = "contact_lookup_uri";
	
	private class ContactSessionAdapter extends ResourceCursorAdapter {

		public ContactSessionAdapter(Context context, TimeLineCursor cursor) {
			super(context, R.layout.listitem_contact_session_incoming, cursor);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// TODO Auto-generated method stub
			return super.getView(position, convertView, parent);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			TimeLineCursor c = (TimeLineCursor) cursor;
			
			TextView title = (TextView) view.findViewById(R.id.TextView_title);
			TextView body = (TextView) view.findViewById(R.id.TextView_body);
			ImageView typeIcon = (ImageView) view.findViewById(R.id.ImageView_typeIcon);
			ImageView directionIcon = (ImageView) view.findViewById(R.id.ImageView_directionIcon);
			TextView timestamp = (TextView) view.findViewById(R.id.TextView_timeStamp);
			
			title.setText(c.getTitle());
			body.setText(c.getSummary());
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
		}
		
	}
	
	private Uri mLookupUri;
	private TimeLineCursor mCursor;
	private ContactSessionAdapter mAdapter;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.contact_session);
		
		Intent intent = getIntent();
		mLookupUri = intent.getParcelableExtra(EXTRA_CONTACT_LOOKUP_URI);
		
		mCursor = getTimeLineManager().getTimeLineCursor(mLookupUri);
		mAdapter = new ContactSessionAdapter(this, mCursor);
		ListView mListView = (ListView)findViewById(R.id.ListView_contactSession);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
//				TimeLineItem data = mAdapter.getItem(position);
//				TimeLineUser targetUser = null;
//				for(TimeLineUser tmp : data.getUsers()){
//					if(tmp.getContactLookupUri().equals(mLookupUri)){
//						targetUser = tmp;
//						break;
//					}
//				}
//				Intent intent = data.getIntent(targetUser);
//				if(intent != null){
//					startActivity(intent);
//				}else{
//					String message = data.getSummary();
//					if(StringUtils.isEmpty(message)){
//						message = data.getTitle();
//					}
//					AlertDialog dialog = new AlertDialog.Builder(ContactSessionActivity.this)
//						.setMessage(message)
//						.setCancelable(true)
//						.setNegativeButton("close", null)
//						.create();
//					dialog.show();
//				}
			}
		});

		QuickContactBadge badge = (QuickContactBadge)findViewById(R.id.QuickContactBadge_contact);
		badge.assignContactUri(mLookupUri);
		badge.setMode(QuickContact.MODE_MEDIUM);
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
	protected void onTimeLineUpdated() {
		mCursor.close();
		mCursor = getTimeLineManager().getTimeLineCursor(mLookupUri);
		mAdapter.changeCursor(mCursor);
	}

}
