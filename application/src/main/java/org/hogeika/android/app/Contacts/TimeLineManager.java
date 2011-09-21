package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class TimeLineManager {
	private static final String TAG = "TimeLineManager";

	private interface TimeStampColumns {
		static final String TIMESTAMP = "time_stamp";
	}
	
	private interface TimeLineColumns extends BaseColumns {
		static final String TABLE_NAME = "timeline";
		
		static final String LOOKUP_KEY = "lookup";
		static final String RAW_CONTACT_ID = "raw_contact_id";
		static final String MESSAGE_ID = "message_id";
	}
	private interface MessageColumns extends BaseColumns , TimeStampColumns  {
		static final String TABLE_NAME = "message";
		
		static final String SOURCE = "source";
		static final String SOURCE_ACCOUNT = "source_account";
		static final String SOURCE_TYPE = "source_type";
		static final String ORIGINAL_ID = "original_id";
		static final String DIRECTION = "direction";
		static final String TITLE = "title";
		static final String SUMMARY = "summary";
	}
	private interface ActivityStreamColumns extends BaseColumns , TimeStampColumns  {
		static final String TABLE_NAME = "activity_stream";
		
		static final String LOOKUP_KEY = "lookup";
		static final String RAW_CONTACT_ID = "raw_contact_id";
		static final String SOURCE = "source";
		static final String SOURCE_ACCOUNT = "source_account";
		static final String SOURCE_TYPE = "source_type";
		static final String ORIGINAL_ID = "original_id";
		static final String SUMMARY = "summary";
		static final String URL = "url";
	}
	private class TimeLineDB extends SQLiteOpenHelper {
		
		private static final int VERSION = 1;
		private static final String NAME = "timeline.db";

		public TimeLineDB(Context context) {
			super(context, NAME, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			createTables(db);
		}

		@Override
		public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
			// TODO Auto-generated method stub
			
		}
	}

	private static final int RETENTION_PERIOD = 90;

	private void createTables(SQLiteDatabase db){
		db.execSQL("CREATE TABLE " + TimeLineColumns.TABLE_NAME + " (" +
				TimeLineColumns._ID + "  integer primary key autoincrement," +
				TimeLineColumns.RAW_CONTACT_ID + " bigint not null," +
				TimeLineColumns.LOOKUP_KEY + " text not null," +
				TimeLineColumns.MESSAGE_ID + " integer not null" +
				");");
		db.execSQL("CREATE TABLE " + MessageColumns.TABLE_NAME + " (" +
				MessageColumns._ID + "  integer primary key autoincrement," +
				MessageColumns.TIMESTAMP + " bigint not null," +
				MessageColumns.SOURCE + " text not null," +
				MessageColumns.SOURCE_ACCOUNT + " text not null," +
				MessageColumns.SOURCE_TYPE + " text not null," +
				MessageColumns.ORIGINAL_ID + " text not null unique," +
				MessageColumns.DIRECTION + " int not null," +
				MessageColumns.TITLE + " text," +
				MessageColumns.SUMMARY + " text" +
				");");
		db.execSQL("CREATE TABLE " + ActivityStreamColumns.TABLE_NAME + " (" +
				ActivityStreamColumns._ID + "  integer primary key autoincrement," +
				ActivityStreamColumns.RAW_CONTACT_ID + " bigint not null," +
				ActivityStreamColumns.LOOKUP_KEY + " text not null," +
				ActivityStreamColumns.TIMESTAMP + " bigint not null," +
				ActivityStreamColumns.SOURCE + " text not null," +
				ActivityStreamColumns.SOURCE_ACCOUNT + " text not null," +
				ActivityStreamColumns.SOURCE_TYPE + " text not null," +
				ActivityStreamColumns.ORIGINAL_ID + " text not null unique," +
				ActivityStreamColumns.SUMMARY + " text," +
				ActivityStreamColumns.URL + " text" +
				");");
	}
	
	private void dropTables(SQLiteDatabase db){
		db.execSQL("drop table " + MessageColumns.TABLE_NAME + ";");
		db.execSQL("drop table " + TimeLineColumns.TABLE_NAME + ";");
		db.execSQL("drop table " + ActivityStreamColumns.TABLE_NAME + ";");
	}
	
	private static TimeLineManager mInstance = null;
	public static TimeLineManager getInstance(Context context){
		if(mInstance == null){
			mInstance = new TimeLineManager(context);
		}
		return mInstance;
	}

	private Context mContext;
	private ContentResolver mContentRsolver;
	private Comparator<TimeLineItem> mTimeLineComparator;
	private Map<TimeLineUser, SortedSet<TimeLineItem>> mContactsTimeLine = new HashMap<TimeLineUser, SortedSet<TimeLineItem>>();
	private SortedSet<ActivityStreamItem> mActivityStream = new TreeSet<ActivityStreamItem>(new Comparator<ActivityStreamItem>() {
		@Override
		public int compare(ActivityStreamItem o1, ActivityStreamItem o2) {
			long time1 = o1.getTimeStamp();
			long time2 = o2.getTimeStamp();
			if(time1 < time2) {
				return 1; 
			}
			if(time1 > time2) {
				return -1;
			}
			return o1.getUser().compareTo(o2.getUser());
		}
	});
	private SQLiteOpenHelper mDBHelper;
	private NotificationManager mNotificationManager;
	private Map<String, Manager> mManagerMap = new HashMap<String, Manager>();
	private Handler mHandler;
	
	public int getManagerCount(){
		return mManagerMap.size();
	}
	public int getActiveAccountCount(){
		int count = 0;
		for(Manager manager : mManagerMap.values()){
			count += manager.getActiveAccountCount();
		}
		return count;
	}
	
	protected TimeLineManager(Context context){
		mContext = context;
		mDBHelper = new TimeLineDB(context);
		mContentRsolver = context.getContentResolver();
		mTimeLineComparator = new Comparator<TimeLineItem>() {
			@Override
			public int compare(TimeLineItem o1, TimeLineItem o2) {
				long time1 = o1.getTimeStamp();
				long time2 = o2.getTimeStamp();
				if(time1 < time2) {
					return 1; 
				}
				if(time1 > time2) {
					return -1;
				}
				return o1.hashCode() - o2.hashCode(); // ugh!
			}
		};
		mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		mHandler = new Handler(mContext.getMainLooper());
		purgeDB(); // purge old data
		loadDB();
	}

	private void loadDB() {
		SQLiteDatabase db = mDBHelper.getReadableDatabase();
		Cursor messages = db.query(MessageColumns.TABLE_NAME, null, null, null, null, null, null);
		if(messages.moveToFirst()){
			int idColumn = messages.getColumnIndex(MessageColumns._ID);
			int timestampColumn = messages.getColumnIndex(MessageColumns.TIMESTAMP);
			int sourceColumn = messages.getColumnIndex(MessageColumns.SOURCE);
			int sourceAccountColumn = messages.getColumnIndex(MessageColumns.SOURCE_ACCOUNT);
			int sourceTypeColumn = messages.getColumnIndex(MessageColumns.SOURCE_TYPE);
			int originalIdColumn = messages.getColumnIndex(MessageColumns.ORIGINAL_ID);
			int directionColumn = messages.getColumnIndex(MessageColumns.DIRECTION);
			int titleColumn = messages.getColumnIndex(MessageColumns.TITLE);
			int summaryColumn = messages.getColumnIndex(MessageColumns.SUMMARY);
			do{
				long msg_id = messages.getLong(idColumn);
				long timeStamp = messages.getLong(timestampColumn);
				String source = messages.getString(sourceColumn);
				String sourceAccount = messages.getString(sourceAccountColumn);
				String sourceType = messages.getString(sourceTypeColumn);
				String originalId = messages.getString(originalIdColumn);
				int direction = messages.getInt(directionColumn);
				String title = messages.getString(titleColumn);
				String summary = messages.getString(summaryColumn);
				
				Cursor timeline = db.query(TimeLineColumns.TABLE_NAME, new String[]{TimeLineColumns.RAW_CONTACT_ID}, TimeLineColumns.MESSAGE_ID + "=" + msg_id, null, null, null,null);
				if(timeline.moveToFirst()){
					Set<TimeLineUser> users = new HashSet<TimeLineUser>();
					int rawContactIdColumn = timeline.getColumnIndex(TimeLineColumns.RAW_CONTACT_ID);
					do {
						long rawContactId = timeline.getLong(rawContactIdColumn);
						TimeLineUser user = newTimeLineUser(rawContactId);
						users.add(user);
					}while(timeline.moveToNext());
					TimeLineItemImpl item = new TimeLineItemImpl(source, timeStamp, users, sourceAccount, sourceType, originalId, direction, title, summary);
					internalAddItem(item);
				}
				timeline.close();
			}while(messages.moveToNext());
		}
		messages.close();

		Cursor activity = db.query(ActivityStreamColumns.TABLE_NAME, null, null, null, null, null, null);
		if(activity.moveToFirst()){
			int rawContactIdColumn = activity.getColumnIndex(ActivityStreamColumns.RAW_CONTACT_ID);
			int timestampColumn = activity.getColumnIndex(ActivityStreamColumns.TIMESTAMP);
			int sourceColumn = activity.getColumnIndex(ActivityStreamColumns.SOURCE);
			int sourceAccountColumn = activity.getColumnIndex(ActivityStreamColumns.SOURCE_ACCOUNT);
			int sourceTypeColumn = activity.getColumnIndex(ActivityStreamColumns.SOURCE_TYPE);
			int originalIdColumn = activity.getColumnIndex(ActivityStreamColumns.ORIGINAL_ID);
			int summaryColumn = activity.getColumnIndex(ActivityStreamColumns.SUMMARY);
			int urlColumn = activity.getColumnIndex(ActivityStreamColumns.URL);
			do{
				long rawContactId = activity.getLong(rawContactIdColumn);
				long timeStamp = activity.getLong(timestampColumn);
				String source = activity.getString(sourceColumn);
				String sourceAccount = activity.getString(sourceAccountColumn);
				String sourceType = activity.getString(sourceTypeColumn);
				String originalId = activity.getString(originalIdColumn);
				String summary = activity.getString(summaryColumn);
				String url = activity.getString(urlColumn);
				
				TimeLineUser user = newTimeLineUser(rawContactId);
				ActivityStreamItemImpl item = new ActivityStreamItemImpl(source, timeStamp, user, sourceAccount, sourceType, originalId, summary, url);
				internalAddActivityStreamItem(item);
			}while(activity.moveToNext());
		}
		activity.close();
		db.close();
	}
	
	private void purgeDB(){
		long currentTime = System.currentTimeMillis();
		long diff = RETENTION_PERIOD * 24 * 60 * 60 * 1000L;
		String whereClause = TimeStampColumns.TIMESTAMP + " < ?";
		String[] whereArgs = new String[]{Long.toString(currentTime - diff)};
		
		SQLiteDatabase db = mDBHelper.getReadableDatabase();
		Cursor messages = db.query(MessageColumns.TABLE_NAME, new String[]{MessageColumns._ID}, whereClause, whereArgs, null, null, null);
		if(messages.moveToFirst()){
			int idColumn = messages.getColumnIndex(MessageColumns._ID);
			do{
				long msg_id = messages.getLong(idColumn);
				db.delete(TimeLineColumns.TABLE_NAME, TimeLineColumns.MESSAGE_ID + "=?", new String[]{Long.toString(msg_id)});
			}while(messages.moveToNext());
		}
		messages.close();
		int count = 0;
		count += db.delete(MessageColumns.TABLE_NAME, MessageColumns.TIMESTAMP + " < ?", whereArgs);
		count += db.delete(ActivityStreamColumns.TABLE_NAME, whereClause, whereArgs);
		Log.d(TAG, "Delete " + count + " rows.");
		db.close();
	}

	private void clearDB(){
		SQLiteDatabase db = mDBHelper.getReadableDatabase();
		dropTables(db);
		createTables(db);
	}
	
	public void addManager(Manager manager){
		mManagerMap.put(manager.getName(), manager);
	}
	
	public Manager getManager(String name){
		return mManagerMap.get(name);
	}
	
	public static interface TimeLineItem {

		public static final int DIRECTION_INCOMING = 0;
		public static final int DIRECTION_OUTGOING = 1;
		public static final int DIRECTION_MISSED = 2;

		public long getTimeStamp();
		public Intent getIntent(TimeLineUser user);
		public Intent getIntent();
		public Drawable getIconDrawable();
		public Manager getSource();
		public Set<TimeLineUser> getUsers();
		public String getTitle();
		public String getSummary();
		public int getDirection();
	}
	
	public static interface TimeLineUser extends Comparable<TimeLineUser>{
		public String getContactLookupKey();
		public long getRawContactId();
		public Uri getContactLookupUri();
		public String getDisplayName();
		public Bitmap getBitmapIcon();
	}
	
	protected class TimeLineUserImpl implements TimeLineUser {
		private final String mLookupKey;
		private final Uri mLookupUri;
		private final long mRawContactId;
		private String mDisplayName = null;
		private Bitmap mBitmapIcon = null;

		private TimeLineUserImpl(String lookupKey){
			this(0, lookupKey, null);
		}

		private TimeLineUserImpl(long rawContactId, String lookupKey, Uri lookupUri) {
			super();
			this.mLookupKey = lookupKey;
			this.mLookupUri = lookupUri;
			this.mRawContactId = rawContactId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((mLookupKey == null) ? 0 : mLookupKey.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimeLineUserImpl other = (TimeLineUserImpl) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (mLookupKey == null) {
				if (other.mLookupKey != null)
					return false;
			} else if (!mLookupKey.equals(other.mLookupKey))
				return false;
			return true;
		}

		@Override
		public String getContactLookupKey() {
			return mLookupKey;
		}

		@Override
		public long getRawContactId() {
			return mRawContactId;
		}

		@Override
		public Uri getContactLookupUri() {
			return mLookupUri;
		}

		private TimeLineManager getOuterType() {
			return TimeLineManager.this;
		}

		@Override
		public int compareTo(TimeLineUser o) {
			return this.getContactLookupKey().compareTo(o.getContactLookupKey());
		}

		@Override
		public String getDisplayName() {
			if(mDisplayName != null){
				return mDisplayName;
			}
			Cursor c = mContentRsolver.query(mLookupUri, new String[]{Contacts.DISPLAY_NAME}, null, null, null);
			c.moveToFirst();
			mDisplayName = c.getString(0);
			return mDisplayName;
		}

		@Override
		public Bitmap getBitmapIcon() {
			if(mBitmapIcon != null){
				return mBitmapIcon;
			}
			InputStream is = Contacts.openContactPhotoInputStream(mContentRsolver, Contacts.lookupContact(mContentRsolver, mLookupUri));
			if(is != null){
				mBitmapIcon = BitmapFactory.decodeStream(is);
			}
			return mBitmapIcon;
		}
		
	}
	
	protected class TimeLineItemImpl implements TimeLineItem {
		private final String mSourceName;
		private final long mTimeStamp;
		private final Set<TimeLineUser> mUsers;
		private final String mSourceAccount;
		private final String mSourceType;
		private final String mOriginalId;
		private final int mDirection;
		private final String mTitle;
		private final String mSummary;
		
		private TimeLineItemImpl(String sourceName, long timeStamp, Set<TimeLineUser> users, String sourceAccount, String sourceType, String originalId, int direction, String title, String summary) {
			super();
			this.mSourceName = sourceName;
			this.mTimeStamp = timeStamp;
			this.mUsers = users;
			this.mSourceAccount = sourceAccount;
			this.mSourceType = sourceType;
			this.mOriginalId = originalId;
			this.mDirection = direction;
			this.mTitle = title;
			this.mSummary = summary;
		}
		
		@Override
		public Manager getSource(){
			return mManagerMap.get(mSourceName);
		}

		/* (non-Javadoc)
		 * @see org.hogeika.android.app.ContactFlow.TimeLineItem#getTimeStamp()
		 */
		@Override
		public long getTimeStamp(){
			return mTimeStamp;
		}
		/* (non-Javadoc)
		 * @see org.hogeika.android.app.ContactFlow.TimeLineItem#getTitle()
		 */
		@Override
		public String getTitle() {
			return mTitle;
		}
		/* (non-Javadoc)
		 * @see org.hogeika.android.app.ContactFlow.TimeLineItem#getSummary()
		 */
		@Override
		public String getSummary(){
			return mSummary;
		}
		/* (non-Javadoc)
		 * @see org.hogeika.android.app.ContactFlow.TimeLineItem#getDirection()
		 */
		@Override
		public int getDirection(){
			return mDirection;
		}

		@Override
		public Set<TimeLineUser> getUsers() {
			return mUsers;
		}
		
		@Override
		public Intent getIntent(TimeLineUser user){
			return getIntent();
		}

		@Override
		public Intent getIntent(){
			Intent intent = getSource().getIntent(mSourceAccount, mSourceType, mOriginalId);
			return intent;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((mOriginalId == null) ? 0 : mOriginalId.hashCode());
			result = prime * result
					+ ((mSourceName == null) ? 0 : mSourceName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimeLineItemImpl other = (TimeLineItemImpl) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (mOriginalId == null) {
				if (other.mOriginalId != null)
					return false;
			} else if (!mOriginalId.equals(other.mOriginalId))
				return false;
			if (mSourceName == null) {
				if (other.mSourceName != null)
					return false;
			} else if (!mSourceName.equals(other.mSourceName))
				return false;
			return true;
		}

		private TimeLineManager getOuterType() {
			return TimeLineManager.this;
		}

		@Override
		public Drawable getIconDrawable() {
			return getSource().getIconDrawable(mSourceType);
		}

	}

	public static interface ActivityStreamItem {
		public long getTimeStamp();
		public Intent getIntent();
		public Manager getSource();
		public String getSourceType();
		public TimeLineUser getUser();
		public String getSummary();
		public String getURLString();
		public Drawable getIconDrawable();
		public String getActionText();
	}
	
	private class ActivityStreamItemImpl implements ActivityStreamItem {
		private final TimeLineUser mUser;
		private final String mSourceName;
		private final long mTimeStamp;
		private final String mSourceAccount;
		private final String mSourceType;
		private final String mOriginalId;
		private final String mSummary;	
		private final String mURL;
		
		private ActivityStreamItemImpl(String sourceName, long timeStamp, TimeLineUser user, 
				String sourceAccount, String sourceType,
				String originalId, String summary, String url) {
			super();
			this.mUser = user;
			this.mSourceName = sourceName;
			this.mTimeStamp = timeStamp;
			this.mSourceAccount = sourceAccount;
			this.mSourceType = sourceType;
			this.mOriginalId = originalId;
			this.mSummary = summary;
			this.mURL = url;
		}

		@Override
		public long getTimeStamp() {
			return mTimeStamp;
		}

		@Override
		public Intent getIntent() {
			if(mURL != null){
				Uri uri = Uri.parse(mURL);
				Intent intent = new Intent(Intent.ACTION_VIEW,uri);
				return intent;
			}
			Intent intent = getSource().getIntent(mSourceAccount, mSourceType, mOriginalId);
			return intent;
		}

		@Override
		public Manager getSource() {
			return mManagerMap.get(mSourceName);
		}

		@Override
		public String getSourceType() {
			return mSourceType;
		}

		@Override
		public TimeLineUser getUser() {
			return mUser;
		}

		@Override
		public String getSummary() {
			return mSummary;
		}

		@Override
		public String getURLString() {
			return mURL;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((mOriginalId == null) ? 0 : mOriginalId.hashCode());
			result = prime * result
					+ ((mSourceName == null) ? 0 : mSourceName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ActivityStreamItemImpl other = (ActivityStreamItemImpl) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (mOriginalId == null) {
				if (other.mOriginalId != null)
					return false;
			} else if (!mOriginalId.equals(other.mOriginalId))
				return false;
			if (mSourceName == null) {
				if (other.mSourceName != null)
					return false;
			} else if (!mSourceName.equals(other.mSourceName))
				return false;
			return true;
		}

		private TimeLineManager getOuterType() {
			return TimeLineManager.this;
		}

		@Override
		public Drawable getIconDrawable() {
			return getSource().getIconDrawable(mSourceType);
		}

		@Override
		public String getActionText() {
			return getSource().getActionText(mSourceType);
		}
	}
	
	public boolean addTimeLineItem(Manager source, long timeStamp, long rawContactId, String sourceAccount, String sourceType, String originalId, int direction, String title, String summary){
		Set<TimeLineUser> users = new HashSet<TimeLineUser>();
		TimeLineUser user = newTimeLineUser(rawContactId);
		users.add(user);
		TimeLineItemImpl item = new TimeLineItemImpl(source.getName(), timeStamp, users, sourceAccount, sourceType, originalId, direction, title, summary);
		return addItem(item);
	}

	public boolean addTimeLineItem(Manager source, long timeStamp, Set<Long> rawContactIds, String sourceAccount, String sourceType, String originalId, int direction, String title, String summary){
		Set<TimeLineUser> users = new HashSet<TimeLineUser>();
		for(long rawContactId : rawContactIds){
			TimeLineUser user = newTimeLineUser(rawContactId);
			users.add(user);
		}
		TimeLineItemImpl item = new TimeLineItemImpl(source.getName(), timeStamp, users, sourceAccount, sourceType, originalId, direction, title, summary);
		return addItem(item);
	}
	
	public List<TimeLineItem> getTimeLine(){
		return null;
	}
	
	public synchronized SortedSet<TimeLineItem> getTimeLine(Uri contactLookupUri){
		String lookupKey  = contactLookupUri.getPathSegments().get(2);
		SortedSet<TimeLineItem> result = mContactsTimeLine.get(new TimeLineUserImpl(lookupKey));
		return result;
	}
	
	public synchronized SortedMap<TimeLineUser, SortedSet<TimeLineItem>> getRecentContacts(){
		SortedMap<TimeLineUser, SortedSet<TimeLineItem>> tmp = new TreeMap<TimeLineUser, SortedSet<TimeLineItem>>(new Comparator<TimeLineUser>() {
			@Override
			public int compare(TimeLineUser o1, TimeLineUser o2) {
				long time1 = mContactsTimeLine.get(o1).first().getTimeStamp();
				long time2 = mContactsTimeLine.get(o2).first().getTimeStamp();
				if(time1 < time2){
					return 1;
				}
				if(time1 > time2){
					return -1;
				}
				return o1.compareTo(o2);
			}
		});
		for(TimeLineUser user : mContactsTimeLine.keySet()){
			// Clone TimeLine
			tmp.put(user, new TreeSet<TimeLineManager.TimeLineItem>(mContactsTimeLine.get(user)));
		}
		return tmp;
	}
	
	protected synchronized void internalAddItem(TimeLineItem item){
		for(TimeLineUser user : item.getUsers()){
			SortedSet<TimeLineItem> set;
			if(mContactsTimeLine.containsKey(user)){
				set = mContactsTimeLine.get(user);
			}else{
				set = new TreeSet<TimeLineItem>(mTimeLineComparator);
				mContactsTimeLine.put(user, set);
			}
			set.add(item);
		}		
	}
	
	protected synchronized boolean addItem(TimeLineItemImpl item){
		internalAddItem(item);
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(MessageColumns.TIMESTAMP, item.getTimeStamp());
		values.put(MessageColumns.SOURCE, item.getSource().getName());
		values.put(MessageColumns.SOURCE_ACCOUNT, item.mSourceAccount);
		values.put(MessageColumns.SOURCE_TYPE, item.mSourceType);
		values.put(MessageColumns.ORIGINAL_ID, item.mOriginalId);
		values.put(MessageColumns.DIRECTION, item.getDirection());
		values.put(MessageColumns.TITLE, item.getTitle());
		values.put(MessageColumns.SUMMARY, item.getSummary());

		long msg_id = -1;
		Cursor c = db.query(MessageColumns.TABLE_NAME, new String[]{MessageColumns._ID}, MessageColumns.ORIGINAL_ID + "=?", new String[]{item.mOriginalId},null,null,null);
		try{
			if(c.getCount() > 0){
				//			c.moveToFirst();
				//			msg_id = c.getLong(0);
				//			db.update(MessageColumns.TABLE_NAME, values, MessageColumns.ORIGINAL_ID + " = ?", new String[]{item.mOriginalId});
				return true;
			}else{
				msg_id = db.insert(MessageColumns.TABLE_NAME, null, values);
			}
			if(msg_id == -1){
				return false;
			}
			db.delete(TimeLineColumns.TABLE_NAME, TimeLineColumns.MESSAGE_ID + "=?", new String[]{Long.toString(msg_id)});
			for(TimeLineUser user : item.getUsers()){
				values.clear();
				values.put(TimeLineColumns.LOOKUP_KEY, user.getContactLookupKey());
				values.put(TimeLineColumns.RAW_CONTACT_ID, user.getRawContactId());
				values.put(TimeLineColumns.MESSAGE_ID, msg_id);
				db.insert(TimeLineColumns.TABLE_NAME, null, values);
			}		
		}finally{
			c.close();
		}
		db.close();
		return true;
	}
	
	protected synchronized void internalAddActivityStreamItem(ActivityStreamItem item){
		mActivityStream.add(item);
	}
	
	public synchronized boolean addActivityStreamItem(Manager source, long timeStamp, long rawContactId, String sourceAccount, String sourceType, String originalId, String summary, String url){
		TimeLineUser user = newTimeLineUser(rawContactId);
		ActivityStreamItemImpl item = new ActivityStreamItemImpl(source.getName(), timeStamp, user, sourceAccount, sourceType, originalId, summary, url);
		internalAddActivityStreamItem(item);
		
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		try{
			ContentValues values = new ContentValues();
			values.put(ActivityStreamColumns.RAW_CONTACT_ID, user.getRawContactId());
			values.put(ActivityStreamColumns.LOOKUP_KEY, user.getContactLookupKey());
			values.put(ActivityStreamColumns.TIMESTAMP, item.getTimeStamp());
			values.put(ActivityStreamColumns.SOURCE, item.mSourceName);
			values.put(ActivityStreamColumns.SOURCE_ACCOUNT, item.mSourceAccount);
			values.put(ActivityStreamColumns.SOURCE_TYPE, item.mSourceType);
			values.put(ActivityStreamColumns.ORIGINAL_ID, item.mOriginalId);
			values.put(ActivityStreamColumns.SUMMARY, item.getSummary());
			values.put(ActivityStreamColumns.URL, item.getURLString());

			long msg_id = -1;
			Cursor c = db.query(ActivityStreamColumns.TABLE_NAME, new String[]{ActivityStreamColumns._ID}, ActivityStreamColumns.ORIGINAL_ID + "=? and " +  ActivityStreamColumns.SOURCE + "=?", new String[]{item.mOriginalId, item.mSourceName},null,null,null);
			try {
				if(c.getCount() > 0){
					c.moveToFirst();
					msg_id = c.getLong(0);
					db.update(ActivityStreamColumns.TABLE_NAME, values, ActivityStreamColumns._ID + " = ?", new String[]{Long.toString(msg_id)});
					return true;
				}else{
					msg_id = db.insert(ActivityStreamColumns.TABLE_NAME, null, values);
				}
				if(msg_id == -1){
					return false;
				}
			}finally{
				c.close();
			}
		}finally{
			db.close();
		}
		return true;
	}

	public synchronized List<ActivityStreamItem> getActivityStream(){
		List<ActivityStreamItem> result = new ArrayList<ActivityStreamItem>(mActivityStream);
		return result;
	}

	
	public interface Listener {
		public void onUpdate();
	}
	
	private final Set<Listener> mListeners = new HashSet<Listener>();
	
	public void addListener(Listener listener){
		mListeners.add(listener);
	}
	
	public void removeListener(Listener listener){
		mListeners.remove(listener);
	}
	
	public void notifyOnUpdate(){
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				for(Listener listener : mListeners){
					listener.onUpdate();
				}
			}
		});
	}
	
	private static final int NOTIFICATION_ID_SYNC = 1;
	private boolean mIsSyncing = false;
	public synchronized void sync(final int type){
		if(mIsSyncing){
			return;
		}
		mIsSyncing = true;
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(type == Manager.SYNC_TYPE_HEAVY){
					Notification notification = new Notification(android.R.drawable.stat_notify_sync, "Start sync.", System.currentTimeMillis());
					Intent intent = new Intent(mContext, MainActivity.class);
					PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0);
					notification.setLatestEventInfo(mContext, "ContactFlow", "syncing..", pi);
					notification.flags |= Notification.FLAG_ONGOING_EVENT;
					mNotificationManager.notify(NOTIFICATION_ID_SYNC, notification);
				}
				for(Manager manager : mManagerMap.values()){
					manager.sync(type);
				}
				if(type == Manager.SYNC_TYPE_HEAVY){
					mNotificationManager.cancel(NOTIFICATION_ID_SYNC);
				}
				mIsSyncing = false;
				notifyOnUpdate();
			}
		}).start();
	}


	public boolean isSyncing(){
		return mIsSyncing;
	}
	
	public static interface ReloadListener {
		public void onComplete();
	}
	
	public synchronized void reload(final ReloadListener listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				internalClear();
				loadDB();
				if(listener != null){
					listener.onComplete();
				}
				notifyOnUpdate();
			}
		}).start();
	}
	
	public static interface PurgeListenr {
		public void onComplete();
	}
	
	public synchronized void purge(final PurgeListenr listener){
		new Thread(new Runnable() {
			public void run() {
				internalClear();
				purgeDB();
				loadDB();
				if(listener != null){
					listener.onComplete();
				}
				notifyOnUpdate();
			}
		}).start();
	}
	
	public static interface ClearListener {
		public void onComplete();
	}

	public synchronized void clear(final ClearListener listener){
		new Thread(new Runnable() {
			@Override
			public void run() {
				internalClear();
				clearDB();
				sync(Manager.SYNC_TYPE_LIGHT);
				if(listener != null){
					listener.onComplete();
				}
				notifyOnUpdate();
			}
		}).start();
	}
	
	protected synchronized void internalClear(){
		mContactsTimeLine.clear();
		mActivityStream.clear();
		mRawContactIdCache.clear();
		mLookupUriCache.clear();
	}
	
	private Map<Long, TimeLineUser> mRawContactIdCache = new HashMap<Long, TimeLineUser>();
	private Map<String, TimeLineUser> mLookupUriCache = new HashMap<String, TimeLineUser>();
	
	public synchronized TimeLineUser newTimeLineUser(long rawContactId){
		if(mRawContactIdCache.containsKey(rawContactId)){
			return mRawContactIdCache.get(rawContactId);
		}
		Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
		Uri contactLookupUri = RawContacts.getContactLookupUri(mContentRsolver, rawContactUri);
		Cursor c = mContentRsolver.query(contactLookupUri, new String[]{Contacts.LOOKUP_KEY}, null, null, null);
		c.moveToFirst();
		String lookupKey = c.getString(0);
		c.close();
		if(mLookupUriCache.containsKey(lookupKey)){
			TimeLineUser user = mLookupUriCache.get(lookupKey);
			mRawContactIdCache.put(rawContactId, user);
			return user;
		}
		TimeLineUser newUser = new TimeLineUserImpl(rawContactId, lookupKey, contactLookupUri);
		mRawContactIdCache.put(rawContactId, newUser);
		mLookupUriCache.put(lookupKey, newUser);
		return newUser;
	}

	private PendingIntent getAlarmIntent(){
		Intent alarmIntent = new Intent(mContext, AlarmReceiver.class);
		PendingIntent operation = PendingIntent.getBroadcast(mContext, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);		
		return operation;
	}
	
	public void startTimer(long period){
		Log.d(TAG,"startTimer(" + period +")");
		PendingIntent operation = getAlarmIntent();
		AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
		alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 30 * 1000, period, operation);
	}
	
	public void stopTimer(){
		Log.d(TAG,"stopTimer()");
		PendingIntent operation = getAlarmIntent();
		AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(operation);
	}
	

}
