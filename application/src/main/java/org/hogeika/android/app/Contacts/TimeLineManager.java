package org.hogeika.android.app.Contacts;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
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
				db.execSQL("DROP VIEW IF EXISTS contacts_timeline;");
				db.execSQL("CREATE VIEW IF NOT EXISTS contacts_timeline AS SELECT *,message.* FROM timeline INNER JOIN message ON timeline.message_id=message._ID;");
			}
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
		db.execSQL("CREATE VIEW contacts_timeline AS SELECT *,message.* FROM timeline INNER JOIN message ON timeline.message_id=message._ID;");
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

	private SQLiteOpenHelper mDBHelper;
	private SQLiteDatabase mDB;
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
		mDB = mDBHelper.getWritableDatabase();
		mContentRsolver = context.getContentResolver();
		mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		mHandler = new Handler(mContext.getMainLooper());
		purgeDB(); // purge old data
	}

	private void purgeDB(){
		long currentTime = System.currentTimeMillis();
		long diff = RETENTION_PERIOD * 24 * 60 * 60 * 1000L;
		String whereClause = TimeStampColumns.TIMESTAMP + " < ?";
		String[] whereArgs = new String[]{Long.toString(currentTime - diff)};
		
		Cursor messages = mDB.query(MessageColumns.TABLE_NAME, new String[]{MessageColumns._ID}, whereClause, whereArgs, null, null, null);
		if(messages.moveToFirst()){
			int idColumn = messages.getColumnIndex(MessageColumns._ID);
			do{
				long msg_id = messages.getLong(idColumn);
				mDB.delete(TimeLineColumns.TABLE_NAME, TimeLineColumns.MESSAGE_ID + "=?", new String[]{Long.toString(msg_id)});
			}while(messages.moveToNext());
		}
		messages.close();
		int count = 0;
		count += mDB.delete(MessageColumns.TABLE_NAME, MessageColumns.TIMESTAMP + " < ?", whereArgs);
		count += mDB.delete(ActivityStreamColumns.TABLE_NAME, whereClause, whereArgs);
		Log.d(TAG, "Delete " + count + " rows.");
	}

	private void clearDB(){
		dropTables(mDB);
		createTables(mDB);
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
		long getTimeStamp();
		int getDirection();
		String getSummary();
		String getTitle();
		Drawable getTypeIcon();
		Intent getIntent();
	}
	
	private class TimeLineCursorImpl extends AbstractDelegateCursor implements TimeLineCursor {
		private final int mRawContactIdColum;
		private final int mTimestampColumn;
		private final int mSourceColumn;
		private final int mSourceAccountColumn;
		private final int mSourceTypeColumn;
		private final int mOriginalIdColumn;
		private final int mDirectionColumn;
		private final int mTitleColumn;
		private final int mSummaryColumn;

		private TimeLineItem mCurrentTimeLineItem = null;

		protected TimeLineCursorImpl(Cursor cursor) {
			super(cursor);
			mRawContactIdColum = mCursor.getColumnIndex(TimeLineColumns.RAW_CONTACT_ID);
			mTimestampColumn = mCursor.getColumnIndex(MessageColumns.TIMESTAMP);
			mSourceColumn = mCursor.getColumnIndex(MessageColumns.SOURCE);
			mSourceAccountColumn =mCursor.getColumnIndex(MessageColumns.SOURCE_ACCOUNT);
			mSourceTypeColumn = mCursor.getColumnIndex(MessageColumns.SOURCE_TYPE);
			mOriginalIdColumn = mCursor.getColumnIndex(MessageColumns.ORIGINAL_ID);
			mDirectionColumn = mCursor.getColumnIndex(MessageColumns.DIRECTION);
			mTitleColumn = mCursor.getColumnIndex(MessageColumns.TITLE);
			mSummaryColumn = mCursor.getColumnIndex(MessageColumns.SUMMARY);
		}

		@Override
		protected void invalidateCache() {
			mCurrentTimeLineItem = null;
		}
		
		private TimeLineItem getTimeLineItem(){
			if(mCurrentTimeLineItem != null) return mCurrentTimeLineItem;
			long rawContactId = mCursor.getLong(mRawContactIdColum);
			Set<TimeLineUser> users = new HashSet<TimeLineUser>();
			users.add(newTimeLineUser(rawContactId));
			long timeStamp = mCursor.getLong(mTimestampColumn);
			String source = mCursor.getString(mSourceColumn);
			String sourceAccount = mCursor.getString(mSourceAccountColumn);
			String sourceType = mCursor.getString(mSourceTypeColumn);
			String originalId = mCursor.getString(mOriginalIdColumn);
			int direction = mCursor.getInt(mDirectionColumn);
			String title = mCursor.getString(mTitleColumn);
			String summary = mCursor.getString(mSummaryColumn);
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
	}
	
	public synchronized TimeLineCursor getTimeLineCursor(Uri contactLookupUri){
		String lookupKey  = contactLookupUri.getPathSegments().get(2);
//		Cursor cursor = db.query("contacts_timeline", null, TimeLineColumns.LOOKUP_KEY + "=?", new String[]{lookupKey}, null, null, MessageColumns.TIMESTAMP + " DESC");
		Cursor cursor = mDB.rawQuery("SELECT * FROM contacts_timeline WHERE lookup=? ORDER BY time_stamp DESC", new String[]{lookupKey});
		return new TimeLineCursorImpl(cursor);
	}
	
	public interface RecentContactsCursor extends TimeLineCursor {
		Bitmap getIcon();
		Uri getContactLookupUri();
		String getDisplayName();
	}
	
	private class RecentContactsCursorImpl extends TimeLineCursorImpl implements RecentContactsCursor {
		private final int mRawContactIdColumn;
		
		private TimeLineUser mCurrentTimeLineUser = null;
		
		public RecentContactsCursorImpl(Cursor cursor) {
			super(cursor);
			mRawContactIdColumn = mCursor.getColumnIndex(TimeLineColumns.RAW_CONTACT_ID);
		}
		
		@Override
		protected void invalidateCache(){
			super.invalidateCache();
			mCurrentTimeLineUser = null;
		}
		
		private TimeLineUser getTimeLineUser() {
			if(mCurrentTimeLineUser != null) return mCurrentTimeLineUser;
			long rawContactId = mCursor.getLong(mRawContactIdColumn);
			mCurrentTimeLineUser = newTimeLineUser(rawContactId);
			return mCurrentTimeLineUser;
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
	
	public synchronized RecentContactsCursor getRecentContactsCursor(){
		Cursor cursor = mDB.rawQuery("SELECT * FROM contacts_timeline as x WHERE time_stamp=(SELECT MAX(time_stamp) FROM contacts_timeline WHERE lookup=x.lookup) ORDER BY time_stamp DESC", null);
		return new RecentContactsCursorImpl(cursor);
	}
	
	protected synchronized boolean addItem(TimeLineItemImpl item){
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
		Cursor c = mDB.query(MessageColumns.TABLE_NAME, new String[]{MessageColumns._ID}, MessageColumns.ORIGINAL_ID + "=?", new String[]{item.mOriginalId},null,null,null);
		try{
			if(c.getCount() > 0){
				//			c.moveToFirst();
				//			msg_id = c.getLong(0);
				//			db.update(MessageColumns.TABLE_NAME, values, MessageColumns.ORIGINAL_ID + " = ?", new String[]{item.mOriginalId});
				return true;
			}else{
				msg_id = mDB.insert(MessageColumns.TABLE_NAME, null, values);
			}
			if(msg_id == -1){
				return false;
			}
			mDB.delete(TimeLineColumns.TABLE_NAME, TimeLineColumns.MESSAGE_ID + "=?", new String[]{Long.toString(msg_id)});
			for(TimeLineUser user : item.getUsers()){
				values.clear();
				values.put(TimeLineColumns.LOOKUP_KEY, user.getContactLookupKey());
				values.put(TimeLineColumns.RAW_CONTACT_ID, user.getRawContactId());
				values.put(TimeLineColumns.MESSAGE_ID, msg_id);
				mDB.insert(TimeLineColumns.TABLE_NAME, null, values);
			}		
		}finally{
			c.close();
		}
		return true;
	}
	
	public synchronized boolean addActivityStreamItem(Manager source, long timeStamp, long rawContactId, String sourceAccount, String sourceType, String originalId, String summary, String url){
		TimeLineUser user = newTimeLineUser(rawContactId);
		String sourceName = source.getName();
		
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

		long msg_id = -1;
		Cursor c = mDB.query(ActivityStreamColumns.TABLE_NAME, new String[]{ActivityStreamColumns._ID}, ActivityStreamColumns.ORIGINAL_ID + "=? and " +  ActivityStreamColumns.SOURCE + "=?", new String[]{originalId, sourceName},null,null,null);
		try {
			if(c.getCount() > 0){
				c.moveToFirst();
				msg_id = c.getLong(0);
				mDB.update(ActivityStreamColumns.TABLE_NAME, values, ActivityStreamColumns._ID + " = ?", new String[]{Long.toString(msg_id)});
				return true;
			}else{
				msg_id = mDB.insert(ActivityStreamColumns.TABLE_NAME, null, values);
			}
			if(msg_id == -1){
				return false;
			}
		}finally{
			c.close();
		}
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
				for(Manager manager : mManagerMap.values()){
					manager.clear();
				}
				sync(Manager.SYNC_TYPE_LIGHT);
				if(listener != null){
					listener.onComplete();
				}
				notifyOnUpdate();
			}
		}).start();
	}
	
	protected synchronized void internalClear(){
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
		if(contactLookupUri == null){
			return null;
		}
		Cursor c = mContentRsolver.query(contactLookupUri, new String[]{Contacts.LOOKUP_KEY}, null, null, null);
		if(!c.moveToFirst()){
			return null;
		}
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
