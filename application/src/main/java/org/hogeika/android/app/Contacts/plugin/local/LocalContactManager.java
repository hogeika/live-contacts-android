package org.hogeika.android.app.Contacts.plugin.local;

import java.util.HashMap;
import java.util.Map;

import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContactsEntity;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

public class LocalContactManager implements Manager {
	public static final String MANAGER_NAME = "localhost";

	private final ContentResolver mContentResolver;
	private final String mLocalNumber;
	private final Resources mResources;
	private final Drawable mIcon;
	private final Drawable mPhoneIcon;
	private final Drawable mSMSIcon;
	private final TimeLineManager mTimeLineManager;
	private final BroadcastReceiver mReceiver;
	
	public LocalContactManager(Context context, TimeLineManager timelineManager){
		mContentResolver = context.getContentResolver();
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		String localNumber = tm.getLine1Number();
		if(localNumber != null){
			mLocalNumber = localNumber;
		}else{
			mLocalNumber = "0"; // Ugh!
		}
		Resources resources = context.getResources();
		mResources = resources;
		mTimeLineManager = timelineManager;
		Drawable icon = null;
		AuthenticatorDescription[] accountTypes = AccountManager.get(context).getAuthenticatorTypes();
		for(AuthenticatorDescription description : accountTypes){
            if (description.type.equals("com.google")) {
				String packageName = description.packageName;
				PackageManager pm = context.getPackageManager();

            	if (description.iconId != 0) {
            		icon = pm.getDrawable(packageName, description.iconId, null);
					if (icon == null) {
						throw new IllegalArgumentException("IconID provided, but drawable not found");
					}
				} else {
					icon = resources.getDrawable(android.R.drawable.sym_def_app_icon);
				}
            }
		}// TODO not found.
		mIcon = icon;
		mPhoneIcon = resources.getDrawable(R.drawable.icon_phone);
		mSMSIcon = resources.getDrawable(R.drawable.icon_sms);
		
		mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				final Map<String, Long> userMap = new HashMap<String, Long>();
				final Map<String, String> typeMap = new HashMap<String, String>();
				readUserMap(userMap, null);
				syncSMS(TimeLineItem.DIRECTION_INCOMING, userMap, typeMap);
			}
		};
		context.registerReceiver(mReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));
		timelineManager.addManager(this);
	}
	@Override
	public String getName() {
		return MANAGER_NAME;
	}
	
	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
	}

	@Override
	public void close(Activity activity) {
	}

	@Override
	public void login(Activity activity, Account account) {
	}

	@Override
	public boolean checkAccount(Account account) {
		return true;
	}

	@Override
	public void init(ContextWrapper context) {
	}

	@Override
	public void logout(Activity activity, Account account) {
	}

	@Override
	public void sync(int type) {
		final Map<String, Long> userMap = new HashMap<String, Long>();
		final Map<String, String> typeMap = new HashMap<String, String>();
		readUserMap(userMap, typeMap);
		syncCallLog(userMap, typeMap);
		syncSMS(TimeLineItem.DIRECTION_INCOMING, userMap, typeMap);
		syncSMS(TimeLineItem.DIRECTION_OUTGOING, userMap, typeMap);
	}
	
	private void readUserMap(final Map<String, Long> userMap, final Map<String, String> typeMap) {
		Cursor c = mContentResolver.query(RawContactsEntity.CONTENT_URI, new String[]{RawContactsEntity._ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL}, RawContactsEntity.MIMETYPE + "='vnd.android.cursor.item/phone_v2'", null, null);
		while(c.moveToNext()){
			long rawContactId = c.getLong(0);
			String number = c.getString(1);
			number = PhoneNumberUtils.stripSeparators(number);
			int type = c.getInt(2);
			String label = c.getString(3);
			if(userMap != null){
				userMap.put(number, rawContactId);
			}
			if(typeMap != null){
				typeMap.put(number, Phone.getTypeLabel(mResources, type, label).toString());
			}
		}
	}
	
	private String getOriginalId(String prefix, long timestamp, String from, String to){
		return prefix + "/" + Long.toString(timestamp) + "/" + from + "/" + to;
	}
	
	public void syncCallLog(){
		final Map<String, Long> userMap = new HashMap<String, Long>();
		final Map<String, String> typeMap = new HashMap<String, String>();
		readUserMap(userMap, typeMap);
		syncCallLog(userMap, typeMap);
	}
	
	private void syncCallLog(Map<String, Long> userMap, Map<String, String> typeMap) {
		String[] projection = new String[]{
				Calls.TYPE,
				Calls.DATE,
				Calls.NUMBER,
		};
		Cursor cursor = mContentResolver.query(Calls.CONTENT_URI, projection, null, null, null);
		while(cursor.moveToNext()){
			int type = cursor.getInt(cursor.getColumnIndex(Calls.TYPE));
			long date = cursor.getLong(cursor.getColumnIndex(Calls.DATE));
			String number = cursor.getString(cursor.getColumnIndex(Calls.NUMBER));
			if(number.startsWith("-")){
				continue;
			}
			number = PhoneNumberUtils.stripSeparators(number);
			Log.d("CallLog", "type = " + type + " date = " + date + " number " + number);
			int direction = TimeLineItem.DIRECTION_INCOMING; // TODO
			if(userMap.containsKey(number)){
				String originalId;
				switch(type){
				case Calls.INCOMING_TYPE:
					direction = TimeLineItem.DIRECTION_INCOMING;
					originalId = getOriginalId("tel", date, number, mLocalNumber);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-in", originalId, direction, typeMap.get(number), "Tel:" + number);
					break;
				case Calls.OUTGOING_TYPE:
					direction = TimeLineItem.DIRECTION_OUTGOING;
					originalId = getOriginalId("tel", date, mLocalNumber, number);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-out", originalId, direction, typeMap.get(number), "Tel:" + number);
					break;
				case Calls.MISSED_TYPE:
					direction = TimeLineItem.DIRECTION_MISSED;
					originalId = getOriginalId("tel", date, number, mLocalNumber);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-miss", originalId, direction, typeMap.get(number), "Tel:" + number);
					break;			
				}
			}
		}
	}
	
	public void syncIncomingSMS(){
		final Map<String, Long> userMap = new HashMap<String, Long>();
		final Map<String, String> typeMap = new HashMap<String, String>();
		readUserMap(userMap, typeMap);
		syncSMS(TimeLineItem.DIRECTION_INCOMING, userMap, typeMap);
	}

	private void syncSMS(int direction, Map<String, Long> userMap, Map<String, String> typeMap) {
		String box = (direction == TimeLineItem.DIRECTION_INCOMING) ? "inbox" : "sent";
		Uri uri = Uri.parse("content://sms/" + box);
		String[] projection = new String[]{
			"date",
			"body",
			"address",
		};
		Cursor cursor = mContentResolver.query(uri, projection, null, null, null);
		while(cursor.moveToNext()){
			long date = cursor.getLong(0);
			String body = cursor.getString(1);
			String number = cursor.getString(2);
			number = PhoneNumberUtils.stripSeparators(number);
			if(userMap.containsKey(number)){
				String originalId;
				if(direction == TimeLineItem.DIRECTION_INCOMING){
					originalId = getOriginalId("sms", date, number, mLocalNumber);
				}else{
					// OUTGOING
					originalId = getOriginalId("sms", date, mLocalNumber, number);
				}
				mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "sms-" + box, originalId, direction, typeMap.get(number), body);
			}
		}	
	}

	@Override
	public Drawable getIconDrawable(String sourceType) {
		if(sourceType.startsWith("tel-")){
			return mPhoneIcon;
		}
		if(sourceType.startsWith("sms-")){
			return mSMSIcon;
		}
		return mIcon;
	}

	@Override
	public Intent getIntent(long rawContactId, String sourceAccount, String sourceType, String originalId) {
		String tmp[] = originalId.split("/", 4);
		String from = tmp[2];
		String to = tmp[3];
		if(sourceType.equals("tel-in") || sourceType.equals("tel-miss")){
			Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + from));
			return intent;
		}
		if(sourceType.equals("tel-out")){
			Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + to));
			return intent;
		}
		if(sourceType.startsWith("sms-inbox")){
			Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + from));
			return intent;
		}
		if(sourceType.startsWith("sms-sent")){
			Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + to));
			return intent;
		}
		return null;
	}
	@Override
	public String getActionText(String sourceType) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public int getActiveAccountCount() {
		return 0;
	}
	@Override
	public void clear() {
		// TODO Auto-generated method stub	
	}
}
