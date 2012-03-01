package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
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
	private interface TimeLineColumns extends BaseColumns , TimeStampColumns {
		static final String TABLE_NAME = "timeline2";
		
		static final String LOOKUP_KEY = "lookup";
		static final String RAW_CONTACT_ID = "raw_contact_id";
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
		
		private static final int VERSION = 2;
		private static final String NAME = "timeline.db";

		public TimeLineDB(Context context) {
			super(context, NAME, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			createTables(db);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
			if(oldVer <= 1){
				// ToDo
				db.execSQL("DROP VIEW IF EXISTS contacts_timeline;");
				db.execSQL("CREATE VIEW IF NOT EXISTS contacts_timeline AS SELECT *,message.* FROM timeline INNER JOIN message ON timeline.message_id=message._ID;");
			}
		}
	}

	private static final int RETENTION_PERIOD = 30;

	private void createTables(SQLiteDatabase db){
		db.execSQL("CREATE TABLE IF NOT EXISTS " + TimeLineColumns.TABLE_NAME + " (" +
				TimeLineColumns._ID + "  integer primary key autoincrement," +
				TimeLineColumns.LOOKUP_KEY + " text not null," +
				TimeLineColumns.RAW_CONTACT_ID + " bigint not null," +
				TimeLineColumns.TIMESTAMP + " bigint not null," +
				TimeLineColumns.SOURCE + " text not null," +
				TimeLineColumns.SOURCE_ACCOUNT + " text not null," +
				TimeLineColumns.SOURCE_TYPE + " text not null," +
				TimeLineColumns.ORIGINAL_ID + " text not null unique," +
				TimeLineColumns.DIRECTION + " int not null," +
				TimeLineColumns.TITLE + " text," +
				TimeLineColumns.SUMMARY + " text" +
				");");
		db.execSQL("CREATE TABLE IF NOT EXISTS " + ActivityStreamColumns.TABLE_NAME + " (" +
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
		db.execSQL("drop table if exists " + TimeLineColumns.TABLE_NAME + ";");
		db.execSQL("drop table if exists " + ActivityStreamColumns.TABLE_NAME + ";");
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

	private SQLiteOpenHelper mDBHelper;
	private SQLiteDatabase mDB;
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
		mDB = mDBHelper.getWritableDatabase();
		mContentRsolver = context.getContentResolver();
		mHandler = new Handler(mContext.getMainLooper());
		
		purgeDB(); // purge old data
		updateLookupKey();
		mContentRsolver.registerContentObserver(RawContacts.CONTENT_URI, true, new ContentObserver(mHandler) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				updateLookupKey();
				notifyOnUpdate();
			}
			
		});
	}
	
	private Map<Long, TimeLineUser> mRawContactIdCache = new HashMap<Long, TimeLineUser>();
	
	public synchronized TimeLineUser newTimeLineUser(long rawContactId){
		if(mRawContactIdCache.containsKey(rawContactId)){
			return mRawContactIdCache.get(rawContactId);
		}
		Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
		Uri contactLookupUri = RawContacts.getContactLookupUri(mContentRsolver, rawContactUri);
		if(contactLookupUri == null){
			return null;
		}
		String lookupKey  = contactLookupUri.getPathSegments().get(2);
		TimeLineUser newUser = new TimeLineUserImpl(rawContactId, lookupKey, contactLookupUri);
		mRawContactIdCache.put(rawContactId, newUser);
		return newUser;
	}


	private synchronized void updateLookupKey(){
		mRawContactIdCache.clear();

		int count = 0;
		ContentValues values = new ContentValues();
		Cursor cursor = mDB.query(TimeLineColumns.TABLE_NAME, new String[]{TimeLineColumns.RAW_CONTACT_ID}, null, null, TimeLineColumns.RAW_CONTACT_ID, null, null);
		if(cursor.moveToFirst()){
			do {
				long rawContactId = cursor.getLong(0);
				TimeLineUser user = newTimeLineUser(rawContactId);
				String lookupKey = user.getContactLookupKey();
				
				values.clear();
				values.put(TimeLineColumns.LOOKUP_KEY, lookupKey);
				count += mDB.update(TimeLineColumns.TABLE_NAME, values, TimeLineColumns.RAW_CONTACT_ID + "=? AND " + TimeLineColumns.LOOKUP_KEY + "<>?", new String[]{Long.toString(rawContactId), lookupKey});
			}while(cursor.moveToNext());
		}
		cursor.close();
		
		cursor = mDB.query(ActivityStreamColumns.TABLE_NAME, new String[]{ActivityStreamColumns.RAW_CONTACT_ID}, null, null, ActivityStreamColumns.RAW_CONTACT_ID, null, null);
		if(cursor.moveToFirst()){
			do {
				long rawContactId = cursor.getLong(0);
				TimeLineUser user = newTimeLineUser(rawContactId);
				String lookupKey = user.getContactLookupKey();
				
				values.clear();
				values.put(ActivityStreamColumns.LOOKUP_KEY, lookupKey);
				count += mDB.update(ActivityStreamColumns.TABLE_NAME, values, ActivityStreamColumns.RAW_CONTACT_ID + "=? AND " + ActivityStreamColumns.LOOKUP_KEY + "<>?", new String[]{Long.toString(rawContactId),lookupKey});
			}while(cursor.moveToNext());
		}
		cursor.close();
		
		if(count > 0){
			isUpdated = true;
		}
	}

	private void purgeDB(){
		long currentTime = System.currentTimeMillis();
		long diff = RETENTION_PERIOD * 24 * 60 * 60 * 1000L;
		String whereClause = TimeStampColumns.TIMESTAMP + " < ?";
		String[] whereArgs = new String[]{Long.toString(currentTime - diff)};
		
		int count = 0;
		count += mDB.delete(TimeLineColumns.TABLE_NAME, whereClause, whereArgs);
		count += mDB.delete(ActivityStreamColumns.TABLE_NAME, whereClause, whereArgs);
		Log.d(TAG, "Delete " + count + " rows.");
	}

	private void clearDB(){
		dropTables(mDB);
		createTables(mDB);
		mRawContactIdCache.clear();
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
			Intent intent = getSource().getIntent(user.getRawContactId(), mSourceAccount, mSourceType, mOriginalId);
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
			Intent intent = getSource().getIntent(mUser.getRawContactId(), mSourceAccount, mSourceType, mOriginalId);
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
	
	private abstract class AbstractDelegateCursor implements Cursor {
		protected final Cursor mCursor;
		
		protected AbstractDelegateCursor(Cursor cursor){
			this.mCursor = cursor;
		}
		
		protected abstract void invalidateCache();
		
		// Delegate methods
		public void close() {
			mCursor.close();
			invalidateCache();
		}

		public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
			mCursor.copyStringToBuffer(columnIndex, buffer);
		}

		public void deactivate() {
			mCursor.deactivate();
			invalidateCache();
		}

		public byte[] getBlob(int columnIndex) {
			return mCursor.getBlob(columnIndex);
		}

		public int getColumnCount() {
			return mCursor.getColumnCount();
		}

		public int getColumnIndex(String columnName) {
			return mCursor.getColumnIndex(columnName);
		}

		public int getColumnIndexOrThrow(String columnName)
				throws IllegalArgumentException {
			return mCursor.getColumnIndexOrThrow(columnName);
		}

		public String getColumnName(int columnIndex) {
			return mCursor.getColumnName(columnIndex);
		}

		public String[] getColumnNames() {
			return mCursor.getColumnNames();
		}

		public int getCount() {
			return mCursor.getCount();
		}

		public double getDouble(int columnIndex) {
			return mCursor.getDouble(columnIndex);
		}

		public Bundle getExtras() {
			return mCursor.getExtras();
		}

		public float getFloat(int columnIndex) {
			return mCursor.getFloat(columnIndex);
		}

		public int getInt(int columnIndex) {
			return mCursor.getInt(columnIndex);
		}

		public long getLong(int columnIndex) {
			return mCursor.getLong(columnIndex);
		}

		public int getPosition() {
			return mCursor.getPosition();
		}

		public short getShort(int columnIndex) {
			return mCursor.getShort(columnIndex);
		}

		public String getString(int columnIndex) {
			return mCursor.getString(columnIndex);
		}

		public boolean getWantsAllOnMoveCalls() {
			return mCursor.getWantsAllOnMoveCalls();
		}

		public boolean isAfterLast() {
			return mCursor.isAfterLast();
		}

		public boolean isBeforeFirst() {
			return mCursor.isBeforeFirst();
		}

		public boolean isClosed() {
			return mCursor.isClosed();
		}

		public boolean isFirst() {
			return mCursor.isFirst();
		}

		public boolean isLast() {
			return mCursor.isLast();
		}

		public boolean isNull(int columnIndex) {
			return mCursor.isNull(columnIndex);
		}

		public boolean move(int offset) {
			invalidateCache();
			return mCursor.move(offset);
		}

		public boolean moveToFirst() {
			invalidateCache();
			return mCursor.moveToFirst();
		}

		public boolean moveToLast() {
			invalidateCache();
			return mCursor.moveToLast();
		}

		public boolean moveToNext() {
			invalidateCache();
			return mCursor.moveToNext();
		}

		public boolean moveToPosition(int position) {
			invalidateCache();
			return mCursor.moveToPosition(position);
		}

		public boolean moveToPrevious() {
			invalidateCache();
			return mCursor.moveToPrevious();
		}

		public void registerContentObserver(ContentObserver observer) {
			mCursor.registerContentObserver(observer);
		}

		public void registerDataSetObserver(DataSetObserver observer) {
			mCursor.registerDataSetObserver(observer);
		}

		public boolean requery() {
			invalidateCache();
			return mCursor.requery();
		}

		public Bundle respond(Bundle extras) {
			return mCursor.respond(extras);
		}

		public void setNotificationUri(ContentResolver cr, Uri uri) {
			mCursor.setNotificationUri(cr, uri);
		}

		public void unregisterContentObserver(ContentObserver observer) {
			mCursor.unregisterContentObserver(observer);
		}

		public void unregisterDataSetObserver(DataSetObserver observer) {
			mCursor.unregisterDataSetObserver(observer);
		}		
	}
	
	public interface TimeLineCursor extends Cursor {
		Bitmap getIcon();
		Uri getContactLookupUri();
		String getDisplayName();
		
		long getTimeStamp();
		int getDirection();
		String getSummary();
		String getTitle();
		Drawable getTypeIcon();
		Intent getIntent();
	}
	
	class TimeLineCursorImpl extends AbstractDelegateCursor implements TimeLineCursor {
		private final int mRawContactIdColumn;
		private final int mTimestampColumn;

		int sourceColumn;
		int sourceAccountColumn;
		int sourceTypeColumn;
		int originalIdColumn;
		int directionColumn;
		int titleColumn;
		int summaryColumn;

		private TimeLineItem mCurrentTimeLineItem = null;
		private TimeLineUser mCurrentTimeLineUser = null;

		protected TimeLineCursorImpl(Cursor cursor) {
			super(cursor);
			mRawContactIdColumn = mCursor.getColumnIndex(TimeLineColumns.RAW_CONTACT_ID);
			mTimestampColumn = mCursor.getColumnIndex(TimeLineColumns.TIMESTAMP);

			sourceColumn = mCursor.getColumnIndex(TimeLineColumns.SOURCE);
			sourceAccountColumn =mCursor.getColumnIndex(TimeLineColumns.SOURCE_ACCOUNT);
			sourceTypeColumn = mCursor.getColumnIndex(TimeLineColumns.SOURCE_TYPE);
			originalIdColumn = mCursor.getColumnIndex(TimeLineColumns.ORIGINAL_ID);
			directionColumn = mCursor.getColumnIndex(TimeLineColumns.DIRECTION);
			titleColumn = mCursor.getColumnIndex(TimeLineColumns.TITLE);
			summaryColumn = mCursor.getColumnIndex(TimeLineColumns.SUMMARY);
		}

		@Override
		protected void invalidateCache() {
			mCurrentTimeLineUser = null;
			mCurrentTimeLineItem = null;
		}

		private TimeLineUser getTimeLineUser() {
			if(mCurrentTimeLineUser != null) return mCurrentTimeLineUser;
			long rawContactId = mCursor.getLong(mRawContactIdColumn);
			mCurrentTimeLineUser = newTimeLineUser(rawContactId);
			return mCurrentTimeLineUser;
		}

		private TimeLineItem getTimeLineItem(){
			if(mCurrentTimeLineItem != null) return mCurrentTimeLineItem;
			TimeLineUser user = getTimeLineUser();		
			Set<TimeLineUser> users = new HashSet<TimeLineUser>();
			users.add(user);
			
			long timeStamp = mCursor.getLong(mTimestampColumn);
			String source = mCursor.getString(sourceColumn);
			String sourceAccount = mCursor.getString(sourceAccountColumn);
			String sourceType = mCursor.getString(sourceTypeColumn);
			String originalId = mCursor.getString(originalIdColumn);
			int direction = mCursor.getInt(directionColumn);
			String title = mCursor.getString(titleColumn);
			String summary = mCursor.getString(summaryColumn);
			mCurrentTimeLineItem = new TimeLineItemImpl(source, timeStamp, users, sourceAccount, sourceType, originalId, direction, title, summary);
			return mCurrentTimeLineItem;
		}

		@Override
		public long getTimeStamp() {
			TimeLineItem item = getTimeLineItem();
			return item.getTimeStamp();
		}


		@Override
		public int getDirection() {
			TimeLineItem item = getTimeLineItem();
			return item.getDirection();
		}

		@Override
		public String getSummary() {
			TimeLineItem item = getTimeLineItem();
			return item.getSummary();
		}


		@Override
		public String getTitle() {
			TimeLineItem item = getTimeLineItem();
			return item.getTitle();
		}


		@Override
		public Drawable getTypeIcon() {
			TimeLineItem item = getTimeLineItem();
			return item.getIconDrawable();
		}

		@Override
		public Intent getIntent() {
			TimeLineItem item = getTimeLineItem();
			TimeLineUser user = item.getUsers().toArray(new TimeLineUser[]{})[0]; // TODO
			return item.getIntent(user);
		}


		@Override
		public Bitmap getIcon() {
			TimeLineUser user = getTimeLineUser();
			if(user==null) return null;
			return user.getBitmapIcon();
		}

		@Override
		public Uri getContactLookupUri() {
			TimeLineUser user = getTimeLineUser();
			if(user==null) return null; // TODO Ugh!
			return user.getContactLookupUri();
		}

		@Override
		public String getDisplayName() {
			TimeLineUser user = getTimeLineUser();
			if(user==null) return "Unknown"; // TODO Ugh!
			return user.getDisplayName();
		}
	}
	
	public synchronized TimeLineCursor getTimeLineCursor(Uri contactLookupUri){
		String lookupKey  = contactLookupUri.getPathSegments().get(2);
		Cursor cursor = mDB.query(TimeLineColumns.TABLE_NAME, null, TimeLineColumns.LOOKUP_KEY + "=?", new String[]{lookupKey}, null, null, TimeLineColumns.TIMESTAMP + " DESC");
		return new TimeLineCursorImpl(cursor);
	}
	
	public synchronized TimeLineCursor getRecentContactsCursor(){
		Cursor cursor = mDB.query(TimeLineColumns.TABLE_NAME + " AS x", null, TimeLineColumns.TIMESTAMP + "=(SELECT MAX(" + TimeLineColumns.TIMESTAMP +") FROM " + TimeLineColumns.TABLE_NAME + " WHERE x." + TimeLineColumns.LOOKUP_KEY + "=" + TimeLineColumns.LOOKUP_KEY + ")", null, null, null, TimeLineColumns.TIMESTAMP + " DESC");
		return new TimeLineCursorImpl(cursor);
	}
	
	protected synchronized boolean addItem(TimeLineItemImpl item){
		long currentTime = System.currentTimeMillis();
		long diff = RETENTION_PERIOD * 24 * 60 * 60 * 1000L;
		if(item.getTimeStamp() < (currentTime - diff)) return false;
		
		String originalId = item.mOriginalId;
		String sourceName = item.getSource().getName();
		mDB.delete(TimeLineColumns.TABLE_NAME, TimeLineColumns.ORIGINAL_ID + "=? and " +  TimeLineColumns.SOURCE + "=?", new String[]{originalId, sourceName});
		
		ContentValues values = new ContentValues();
		values.put(TimeLineColumns.SOURCE, sourceName);
		values.put(TimeLineColumns.SOURCE_ACCOUNT, item.mSourceAccount);
		values.put(TimeLineColumns.SOURCE_TYPE, item.mSourceType);
		values.put(TimeLineColumns.ORIGINAL_ID, originalId);
		values.put(TimeLineColumns.DIRECTION, item.getDirection());
		values.put(TimeLineColumns.TITLE, item.getTitle());
		values.put(TimeLineColumns.SUMMARY, item.getSummary());

		boolean flag = true;
		for(TimeLineUser user : item.getUsers()){
			values.put(TimeLineColumns.LOOKUP_KEY, user.getContactLookupKey());
			values.put(TimeLineColumns.RAW_CONTACT_ID, user.getRawContactId());
			values.put(TimeLineColumns.TIMESTAMP, item.getTimeStamp());
			long id = mDB.insert(TimeLineColumns.TABLE_NAME, null, values);
			if(id == -1){
				flag = false;
			}
		}
		isUpdated = true;
		return flag;
	}
	
	public synchronized boolean addActivityStreamItem(Manager source, long timeStamp, long rawContactId, String sourceAccount, String sourceType, String originalId, String summary, String url){
		TimeLineUser user = newTimeLineUser(rawContactId);
		String sourceName = source.getName();
		
		mDB.delete(ActivityStreamColumns.TABLE_NAME, ActivityStreamColumns.ORIGINAL_ID + "=? and " +  ActivityStreamColumns.SOURCE + "=?", new String[]{originalId, sourceName});

		ContentValues values = new ContentValues();
		values.put(ActivityStreamColumns.RAW_CONTACT_ID, user.getRawContactId());
		values.put(ActivityStreamColumns.LOOKUP_KEY, user.getContactLookupKey());
		values.put(ActivityStreamColumns.TIMESTAMP, timeStamp);
		values.put(ActivityStreamColumns.SOURCE, sourceName);
		values.put(ActivityStreamColumns.SOURCE_ACCOUNT, sourceAccount);
		values.put(ActivityStreamColumns.SOURCE_TYPE, sourceType);
		values.put(ActivityStreamColumns.ORIGINAL_ID, originalId);
		values.put(ActivityStreamColumns.SUMMARY, summary);
		values.put(ActivityStreamColumns.URL, url);

		long id = mDB.insert(ActivityStreamColumns.TABLE_NAME, null, values);
		if(id == -1){
			return false;
		}
		isUpdated = true;
		return true;
	}

	public interface ActivityStreamCursor extends Cursor {
		public Bitmap getIcon();
		public Uri getContactLookupUri();
		public String getDisplayName();
		
		public long getTimeStamp();
		public Intent getIntent();
		public Manager getSource();
		public String getSourceType();
		public String getSummary();
		public String getURLString();
		public Drawable getTypeIcon();
		public String getActionText();		
	}

	private class ActivityStreamCursorImpl extends AbstractDelegateCursor implements ActivityStreamCursor {
		private final int mRawContactIdColumn;
		private final int mTimestampColumn;
		private final int mSourceColumn;
		private final int mSourceAccountColumn;
		private final int mSourceTypeColumn;
		private final int mOriginalIdColumn;
		private final int mSummaryColumn;
		private final int mUrlColumn;
		
		protected ActivityStreamCursorImpl(Cursor cursor) {
			super(cursor);
			mRawContactIdColumn = cursor.getColumnIndex(ActivityStreamColumns.RAW_CONTACT_ID);
			mTimestampColumn = cursor.getColumnIndex(ActivityStreamColumns.TIMESTAMP);
			mSourceColumn = cursor.getColumnIndex(ActivityStreamColumns.SOURCE);
			mSourceAccountColumn = cursor.getColumnIndex(ActivityStreamColumns.SOURCE_ACCOUNT);
			mSourceTypeColumn = cursor.getColumnIndex(ActivityStreamColumns.SOURCE_TYPE);
			mOriginalIdColumn = cursor.getColumnIndex(ActivityStreamColumns.ORIGINAL_ID);
			mSummaryColumn = cursor.getColumnIndex(ActivityStreamColumns.SUMMARY);
			mUrlColumn = cursor.getColumnIndex(ActivityStreamColumns.URL);
		}

		private ActivityStreamItem mCurrentItem = null;
		
		@Override
		protected void invalidateCache() {
			mCurrentItem = null;
		}
		
		private ActivityStreamItem getCurentItem(){
			if(mCurrentItem != null) return mCurrentItem;
			long rawContactId = mCursor.getLong(mRawContactIdColumn);
			long timeStamp = mCursor.getLong(mTimestampColumn);
			String source = mCursor.getString(mSourceColumn);
			String sourceAccount = mCursor.getString(mSourceAccountColumn);
			String sourceType = mCursor.getString(mSourceTypeColumn);
			String originalId = mCursor.getString(mOriginalIdColumn);
			String summary = mCursor.getString(mSummaryColumn);
			String url = mCursor.getString(mUrlColumn);
			
			TimeLineUser user = newTimeLineUser(rawContactId);
			mCurrentItem = new ActivityStreamItemImpl(source, timeStamp, user, sourceAccount, sourceType, originalId, summary, url);
			return mCurrentItem;
		}

		@Override
		public long getTimeStamp() {
			ActivityStreamItem item = getCurentItem();
			return item.getTimeStamp();
		}

		@Override
		public Intent getIntent() {
			ActivityStreamItem item = getCurentItem();
			return item.getIntent();
		}

		@Override
		public Manager getSource() {
			ActivityStreamItem item = getCurentItem();
			return item.getSource();
		}

		@Override
		public String getSourceType() {
			ActivityStreamItem item = getCurentItem();
			return item.getSourceType();
		}

		@Override
		public String getSummary() {
			ActivityStreamItem item = getCurentItem();
			return item.getSummary();
		}

		@Override
		public String getURLString() {
			ActivityStreamItem item = getCurentItem();
			return item.getURLString();
		}

		@Override
		public Drawable getTypeIcon() {
			ActivityStreamItem item = getCurentItem();
			return item.getIconDrawable();
		}

		@Override
		public String getActionText() {
			ActivityStreamItem item = getCurentItem();
			return item.getActionText();
		}

		@Override
		public Bitmap getIcon() {
			ActivityStreamItem item = getCurentItem();
			TimeLineUser user = item.getUser();
			if(user == null) return null;
			return user.getBitmapIcon();
		}

		@Override
		public String getDisplayName() {
			ActivityStreamItem item = getCurentItem();
			TimeLineUser user = item.getUser();
			if(user == null) return "Unknown";
			return user.getDisplayName();
		}

		@Override
		public Uri getContactLookupUri() {
			ActivityStreamItem item = getCurentItem();
			TimeLineUser user = item.getUser();
			if(user == null) return null;
			return user.getContactLookupUri();
		}
	}
	
	public synchronized ActivityStreamCursor getActivityStreamCursor(){
		Cursor cursor = mDB.query(ActivityStreamColumns.TABLE_NAME, null, null, null, null, null, ActivityStreamColumns.TIMESTAMP + " DESC");
		return new ActivityStreamCursorImpl(cursor);
	}
	
	public interface Listener {
		public static final int SYNC_START = 1;
		public static final int SYNC_PROGRESS = 2;
		public static final int SYNC_END = 3;
		public static final int SYNC_ERROR = 4;
		
		public void onSyncStateChange(int state, Manager manager, int type, String accountName, int count, int total);
		public void onUpdate();
	}
	
	private final Set<Listener> mListeners = new HashSet<Listener>();
	
	public void addListener(Listener listener){
		mListeners.add(listener);
	}
	
	public void removeListener(Listener listener){
		mListeners.remove(listener);
	}
	
	private boolean isUpdated = true;
	public void notifyOnUpdate(){
		if(!isUpdated){
			return;
		}
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				for(Listener listener : mListeners){
					listener.onUpdate();
				}
				isUpdated = false;
			}
		});
	}
	
	public void notifyOnSyncStateChange(final int state, final Manager manager, final int type, final String accountName, final int count, final int total){
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				for(Listener listener : mListeners){
					listener.onSyncStateChange(state, manager, type, accountName, count, total);
				}
			}
		});
	}
	
	private boolean mIsSyncing = false;
	public synchronized void sync(final int type){
		if(mIsSyncing){
			return;
		}
		mIsSyncing = true;
		new Thread(new Runnable() {
			@Override
			public void run() {
				notifyOnSyncStateChange(Listener.SYNC_START, null, type, null, 0, 0);
				for(Manager manager : mManagerMap.values()){
					if(manager.getActiveAccountCount() > 0){
						notifyOnSyncStateChange(Listener.SYNC_START, manager, type, null, 0, 0);
						try{
							manager.sync(type);
						}catch(Throwable e){
							Log.e("TimeLineManager", "Error occor while syncing.");
							Log.e("TimeLineManager", e.toString());
							notifyOnSyncStateChange(Listener.SYNC_ERROR, manager, type, null, 0, 0);
						}
						notifyOnSyncStateChange(Listener.SYNC_END, manager, type, null, 0, 0);
						notifyOnUpdate(); // Ugh! check update
					}
				}
				mIsSyncing = false;
				notifyOnSyncStateChange(Listener.SYNC_END, null, type, null, 0, 0);
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
				purgeDB();
				isUpdated = true;
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
				clearDB();
				for(Manager manager : mManagerMap.values()){
					manager.clear();
				}
				isUpdated = true;
				sync(Manager.SYNC_TYPE_LIGHT);
				if(listener != null){
					listener.onComplete();
				}
				notifyOnUpdate();
			}
		}).start();
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
