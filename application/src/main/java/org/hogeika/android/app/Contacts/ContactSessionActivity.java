package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.text.DateFormat;

import org.apache.commons.lang.StringUtils;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineCursor;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import android.app.AlertDialog;
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
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class ContactSessionActivity extends AbstractTimeLiveViewActivity {
	public static final String EXTRA_CONTACT_LOOKUP_URI = "contact_lookup_uri";
	
	private class ContactSessionAdapter extends CursorAdapter {
		private Context mContext;
		private LayoutInflater mInflater;

		public ContactSessionAdapter(Context context, TimeLineCursor cursor) {
			super(context, cursor);
			mContext = context;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			TimeLineCursor c = (TimeLineCursor)cursor;
			int direction = c.getDirection();
			View view;
			if(direction == TimeLineItem.DIRECTION_OUTGOING){
				view = mInflater.inflate(R.layout.listitem_contact_session_outgoing, parent, false);
			}else{
				view = mInflater.inflate(R.layout.listitem_contact_session_incoming, parent, false);
			}
			return view;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TimeLineCursor cursor = (TimeLineCursor) getItem(position);

			if (convertView == null) {
				convertView = newView(mContext, cursor, parent);
			} else {
				int direction = cursor.getDirection();
				if(convertView.getId() == R.id.Layout_outgoing && direction != TimeLineItem.DIRECTION_OUTGOING){
					convertView = mInflater.inflate(R.layout.listitem_contact_session_incoming, parent, false);
				}
				else if(convertView.getId() == R.id.Layout_incoming && direction == TimeLineItem.DIRECTION_OUTGOING){
					convertView = mInflater.inflate(R.layout.listitem_contact_session_outgoing, parent, false);
				}
			}
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
				TimeLineCursor c = (TimeLineCursor) mAdapter.getItem(position);

				Intent intent = c.getIntent();
				if(intent != null){
					startActivity(intent);
				}else{
					String message = c.getSummary();
					if(StringUtils.isEmpty(message)){
						message = c.getTitle();
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
