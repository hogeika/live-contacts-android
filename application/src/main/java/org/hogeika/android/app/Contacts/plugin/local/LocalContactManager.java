package org.hogeika.android.app.Contacts.plugin.local;

import java.util.HashMap;
import java.util.Map;

import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContactsEntity;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

public class LocalContactManager implements Manager {
	private static final Uri SMS_URI = Uri.parse("content://sms/");

	public static final String MANAGER_NAME = "localhost";

	public static final String PREF = "local_seting";
	public static final String PREF_LOCAL_CALLLOG_LAST_CHECK_TIME = "calllog_last_check_time";
	public static final String PREF_LOCAL_SMS_LAST_CHECK_TIME = "sms_last_check_time";
	
	private final Context mContext;
	private final ContentResolver mContentResolver;
	private final Handler mHandler;
	private final String mLocalNumber;
	private final Resources mResources;
	private final Drawable mIcon;
	private final Drawable mPhoneIcon;
	private final Drawable mSMSIcon;
	private final TimeLineManager mTimeLineManager;
//	private final BroadcastReceiver mReceiver;
	
	public LocalContactManager(Context context, TimeLineManager timelineManager){
		mContext = context;
		mContentResolver = context.getContentResolver();
		mHandler = new Handler(mContext.getMainLooper());

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
		
		mContentResolver.registerContentObserver(Calls.CONTENT_URI, true, new ContentObserver(mHandler) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				syncCallLog(SYNC_TYPE_LIGHT);
				mTimeLineManager.notifyOnUpdate();
			}
		});
		mContentResolver.registerContentObserver(SMS_URI, true, new ContentObserver(mHandler) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				syncSMS(SYNC_TYPE_LIGHT);
				mTimeLineManager.notifyOnUpdate();
			}
		});
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
		syncCallLog(type, userMap, typeMap);
		syncSMS(type, userMap, typeMap);
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
	
	private void syncCallLog(int type){
		final Map<String, Long> userMap = new HashMap<String, Long>();
		final Map<String, String> typeMap = new HashMap<String, String>();
		readUserMap(userMap, typeMap);
		syncCallLog(type, userMap, typeMap);
	}
	
	private void syncCallLog(int type, Map<String, Long> userMap, Map<String, String> typeMap){
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		long lastCheckTime = 0;
		if(type == SYNC_TYPE_LIGHT ){
			lastCheckTime = pref.getLong(PREF_LOCAL_CALLLOG_LAST_CHECK_TIME, 0);
		}
		long now = System.currentTimeMillis() - 60 * 1000;

		syncCallLog(userMap, typeMap, lastCheckTime);
		
		Editor editor = pref.edit();
		editor.putLong(PREF_LOCAL_CALLLOG_LAST_CHECK_TIME, now);
		editor.commit();
	}
	
	private void syncCallLog(Map<String, Long> userMap, Map<String, String> typeMap, long lastCheckTime) {
		String[] projection = new String[]{
				Calls.TYPE,
				Calls.DATE,
				Calls.DURATION,
				Calls.NUMBER,
		};
		Cursor cursor = mContentResolver.query(Calls.CONTENT_URI, projection, Calls.DATE + "> ?",  new String[]{Long.toString(lastCheckTime)}, null);
		int typeIndex = cursor.getColumnIndex(Calls.TYPE);
		int dateIndex = cursor.getColumnIndex(Calls.DATE);
		int durationIndex = cursor.getColumnIndex(Calls.DURATION);
		int numberIndex = cursor.getColumnIndex(Calls.NUMBER);
		while(cursor.moveToNext()){
			int type = cursor.getInt(typeIndex);
			long date = cursor.getLong(dateIndex);
			int duration = cursor.getInt(durationIndex);
			String number = cursor.getString(numberIndex);
			if(number.startsWith("-")){
				continue;
			}
			number = PhoneNumberUtils.stripSeparators(number);
			Log.d("CallLog", "type = " + type + " date = " + date + " number " + number);
			int direction = TimeLineItem.DIRECTION_INCOMING; // TODO
			if(userMap.containsKey(number)){
				String originalId;
				String title = typeMap.get(number) + "(" + DateUtils.formatElapsedTime(duration) + ")";
				switch(type){
				case Calls.INCOMING_TYPE:
					direction = TimeLineItem.DIRECTION_INCOMING;
					originalId = getOriginalId("tel", date, number, mLocalNumber);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-in", originalId, direction, title, "Tel:" + number);
					break;
				case Calls.OUTGOING_TYPE:
					direction = TimeLineItem.DIRECTION_OUTGOING;
					originalId = getOriginalId("tel", date, mLocalNumber, number);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-out", originalId, direction, title, "Tel:" + number);
					break;
				case Calls.MISSED_TYPE:
					direction = TimeLineItem.DIRECTION_MISSED;
					originalId = getOriginalId("tel", date, number, mLocalNumber);
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(number), mLocalNumber, "tel-miss", originalId, direction, title, "Tel:" + number);
					break;			
				}
			}
		}
	}
	
	private void syncSMS(int type){
		final Map<String, Long> userMap = new HashMap<String, Long>();
		final Map<String, String> typeMap = new HashMap<String, String>();
		readUserMap(userMap, typeMap);
		syncSMS(type,  userMap, typeMap);
	}

	private void syncSMS(int type, Map<String, Long> userMap, Map<String, String> typeMap){
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		long lastCheckTime = 0;
		if(type == SYNC_TYPE_LIGHT ){
			lastCheckTime = pref.getLong(PREF_LOCAL_SMS_LAST_CHECK_TIME, 0);
		}
		long now = System.currentTimeMillis() - 60 * 1000;
		
		syncSMS(TimeLineItem.DIRECTION_INCOMING, userMap, typeMap, lastCheckTime);
		syncSMS(TimeLineItem.DIRECTION_OUTGOING, userMap, typeMap, lastCheckTime);
		
		Editor editor = pref.edit();
		editor.putLong(PREF_LOCAL_SMS_LAST_CHECK_TIME, now);
		editor.commit();
	}
	
	private void syncSMS(int direction, Map<String, Long> userMap, Map<String, String> typeMap, long lastCheckTime) {
		String box = (direction == TimeLineItem.DIRECTION_INCOMING) ? "inbox" : "sent";
		Uri uri = Uri.parse(SMS_URI + box);
		String[] projection = new String[]{
			"date",
			"body",
			"address",
		};
		Cursor cursor = mContentResolver.query(uri, projection, "date > ?",  new String[]{Long.toString(lastCheckTime)}, null);
//		Cursor cursor = mContentResolver.query(uri, projection, null, null, null);
		while(cursor.moveToNext()){
			long date = cursor.getLong(0);
			String body = cursor.getString(1);
			String number = cursor.getString(2);
			number = PhoneNumberUtils.stripSeparators(number);
			if(userMap.containsKey(number)){
				String originalId;
				if(direction == TimeLineItem.DIRECTION_INCOMING){
					originalId = getOriginalId("sms", date, number, mLocalNumber);
//					Log.d("SMS inbox","tel:" + number + " date:" + date);
				}else{
					// OUTGOING
					originalId = getOriginalId("sms", date, mLocalNumber, number);
//					Log.d("SMS sent","tel:" + number + " date:" + date);
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
		return 1;
	}
	@Override
	public void clear() {
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		Editor editor = pref.edit();
		editor.putLong(PREF_LOCAL_CALLLOG_LAST_CHECK_TIME, 0);
		editor.commit();
	}
}
