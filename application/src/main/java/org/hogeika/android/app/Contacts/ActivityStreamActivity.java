package org.hogeika.android.app.Contacts;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import org.hogeika.android.app.Contacts.TimeLineManager.ActivityStreamItem;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineUser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class ActivityStreamActivity extends AbstractTimeLiveViewActivity<ActivityStreamItem> {
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
			QuickContactBadge contactIcon = (QuickContactBadge) convertView.findViewById(R.id.QuickContactBadge_contactIcon);
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
			contactIcon.assignContactUri(data.getUser().getContactLookupUri());
			contactIcon.setMode(QuickContact.MODE_MEDIUM);
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);
		
		ArrayList<ActivityStreamItem> mList = new ArrayList<ActivityStreamItem>();
		final ContactAdapter mAdapter = new ContactAdapter(this, mList);
		ListView mListView = (ListView)findViewById(R.id.ListView_activityStream);
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
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				ActivityStreamItem data = mAdapter.getItem(position);
				ContactsContract.QuickContact.showQuickContact(getApplicationContext(), view, data.getUser().getContactLookupUri(), ContactsContract.QuickContact.MODE_MEDIUM, null);
				return true;
			}
		});
		setListView(mListView, mAdapter, mList);
	}

	@Override
	protected void onTimeLineUpdated(TimeLineManager manager, List<ActivityStreamItem> tmpList) {
		List<ActivityStreamItem> stream = manager.getActivityStream();
		tmpList.addAll(stream);
	}
}
