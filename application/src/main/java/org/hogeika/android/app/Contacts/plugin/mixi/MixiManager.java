package org.hogeika.android.app.Contacts.plugin.mixi;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import jp.mixi.android.sdk.CallbackListener;
import jp.mixi.android.sdk.Config;
import jp.mixi.android.sdk.ErrorInfo;
import jp.mixi.android.sdk.MixiContainer;
import jp.mixi.android.sdk.MixiContainerFactory;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.TimeLineManager.Listener;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
//import android.provider.BaseColumns;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;
import android.widget.Toast;

public class MixiManager implements Manager {
	private static final String SOURCE_TYPE_OUTBOX = "@outbox";
	private static final String SOURCE_TYPE_INBOX = "@inbox";
	private static final String SOURCE_TYPE_VOICE_COMMENT = "@voice-comment";
	private static final String SOURCE_TYPE_VOICE = "@voice";
	private static final String TAG = "MixManager";
	public static final String MANAGER_NAME = "mixi.jp";
	public static final String PREF = "mixi_setting";
	public static final String PREF_MIXI_ME_ID = "me_id";
	
//	private interface VoiceColumns extends BaseColumns {
//		static final String TABLE_NAME = "voice_table";
//		
//		static final String VOICE_ID = "voice_id";
//		static final String CREATED = "created";
//		static final String USER = "user";
//		static final String REPLY_COUNT = "reply_count";
//	}

//	private class VoiceDB extends SQLiteOpenHelper {
//		private static final int VERSION = 1;
//		private static final String NAME = "mixi_voice.db";
//		
//		public VoiceDB(Context context){
//			super(context, NAME, null, VERSION);
//		}
//		
//		@Override
//		public void onCreate(SQLiteDatabase db){
//			db.execSQL("CREATE TABLE " + VoiceColumns.TABLE_NAME + " (" +
//					VoiceColumns._ID + " integer primary key autoincrement," +
//					VoiceColumns.VOICE_ID + " text not null," +
//					VoiceColumns.CREATED + " bigint not null," +
//					VoiceColumns.USER + " text not null," +
//					VoiceColumns.REPLY_COUNT + " integer not null" +
//					");");
//		}
//
//		@Override
//		public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
//			// TODO Auto-generated method stub
//			
//		}
//	}
	
	private static final int REQUEST_LOGIN_MIXI = 1;
	private static final int REQUEST_LOGOUT_MIXI = 2;
	private int mRequestCode = REQUEST_LOGIN_MIXI;
	private int mRevokeCode = REQUEST_LOGOUT_MIXI;

	private final Context mContext;
	private final ContentResolver mContentResolver;
	private Drawable mIcon;
	private Drawable mVoiceIcon;
	private Drawable mMessageIcon;
	private final Handler mHandler;
	private final TimeLineManager mTimeLineManager;

	private MixiContainer mContainer;
	public MixiManager(Context context, TimeLineManager timeLineManager){
		mContext = context;
		mContentResolver = context.getContentResolver();
		mHandler = new Handler(context.getMainLooper());
		mTimeLineManager = timeLineManager;

		AccountManager accountManager = AccountManager.get(context);
		AuthenticatorDescription[] accountTypes = accountManager.getAuthenticatorTypes();
		for(AuthenticatorDescription description : accountTypes){
            if (description.type.equals("jp.mixi.authenticator.MixiAccountType")) {
				String packageName = description.packageName;
				PackageManager pm = context.getPackageManager();

            	if (description.iconId != 0) {
					mIcon = pm.getDrawable(packageName, description.iconId, null);
					if (mIcon == null) {
						throw new IllegalArgumentException("IconID provided, but drawable not found");
					}
				} else {
					mIcon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
				}
            }
		}
		if(mIcon == null){ // mixi account not found.
			return;
		}
		mVoiceIcon = createIcon((BitmapDrawable) mIcon, "Voice", Color.WHITE);
		mMessageIcon = createIcon((BitmapDrawable) mIcon, "Message", Color.RED);

		Resources res = context.getResources();
		Config config = new Config();
		config.selector = Config.GRAPH_API;
		config.clientId = res.getString(R.string.mixi_client_id);

		mContainer = MixiContainerFactory.getContainer(config);
		
		timeLineManager.addManager(this);
	}
	
	@Override
	public String getName() {
		return MANAGER_NAME;
	}
	
	@Override
	public void init(ContextWrapper activity){
		if(mIcon == null){ // mixi account is not found.
			return;
		}
		mContainer.init(activity);
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		mMeID = pref.getString(PREF_MIXI_ME_ID, null);
	}
	

	public void setRequestCode(int request, int revoke){
		mRequestCode = request;
		mRevokeCode = revoke;
	}

	@Override
	public void login(final Activity activity, final Account account){
		if(checkAccount(account)) return;
		mContainer.authorize(activity, new String[]{/*"mixi_apps", "r_profile",*/ "r_profile", "r_message", "r_voice", "w_diary", "r_updates"}, mRequestCode, new CallbackListener() {
			
			@Override
			public void onFatal(ErrorInfo e) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void onError(ErrorInfo e) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void onComplete(Bundle values) {
				final ProgressDialog progress = new ProgressDialog(activity);
				progress.setCancelable(false);
				progress.show();
				getMeIDAsync(new GetMeIDCallback(){
					@Override
					public void onComplete(String id) {
						Toast.makeText(activity, "Logged in!", Toast.LENGTH_SHORT);
						progress.dismiss();
					}

					@Override
					public void onError() {
						Toast.makeText(activity, "Error!", Toast.LENGTH_LONG);
						logout(activity, account);
						progress.dismiss();
					}
				});
			}
			
			@Override
			public void onCancel() {
				// TODO Auto-generated method stub
			}
		});		
	}
	
	private interface GetMeIDCallback {
		void onComplete(String id);
		void onError();
	}
	
	private CountDownLatch mMeIDLatch = null;
	private String mMeID = null;
	
	private synchronized void getMeIDAsync(final GetMeIDCallback callback){
		if(!mContainer.isAuthorized()) {
			callback.onError();
			return;
		}
		if(mMeID != null){
			callback.onComplete(mMeID);
			return;
		}
		if(mMeIDLatch != null){
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						mMeIDLatch.await();
					} catch (InterruptedException e) {
					}
					if(mMeID != null){
						callback.onComplete(mMeID);
					}else{
						callback.onError();
					}
				}
			});
			return;
		}
		mMeIDLatch = new CountDownLatch(1);
		mContainer.send("/people/@me/@self", new CallbackListener() {

			@Override
			public void onComplete(Bundle values) {
				try {
					JSONObject obj = new JSONObject(values.getString("response"));
					String id = obj.getJSONObject("entry").getString("id");
					SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
					Editor editor = pref.edit();
					editor.clear();
					editor.putString(PREF_MIXI_ME_ID, id);
					editor.commit();
					mMeID = id;
				} catch (JSONException e) {
					e.printStackTrace();
				}
				if(mMeID != null){
					callback.onComplete(mMeID);
				}else{
					callback.onError();
				}
				mMeIDLatch.countDown();
			}

			private void clear(){
				SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.clear();
				editor.commit();
				mMeID = null;
				callback.onError();
				mMeIDLatch.countDown();
			}
			
			@Override
			public void onCancel() {
				clear();
			}

			@Override
			public void onFatal(ErrorInfo e) {
				clear();
			}

			@Override
			public void onError(ErrorInfo e) {
				clear();
			}
		});		
	}
	
	@Override
	public void close(Activity activity){
		mContainer.close(activity);
	}

	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		mContainer.authorizeCallback(requestCode, resultCode, data);
	}

	@Override
	public boolean checkAccount(Account account) {
		// Assume there is only one mixi account on system
		return (mContainer.isAuthorized() && mMeID != null);
	}

	@Override
	public void logout(Activity activity, Account account) {
		mContainer.logout(activity, mRevokeCode, new CallbackListener() {
			
			@Override
			public void onFatal(ErrorInfo e) {
				clearAccount();
			}
			
			@Override
			public void onError(ErrorInfo e) {
				clearAccount();
			}
			
			@Override
			public void onComplete(Bundle values) {
				clearAccount();
			}
			
			@Override
			public void onCancel() {
				// TODO Auto-generated method stub				
			}

			private void clearAccount(){
				SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.clear();
				editor.commit();
				mMeID = null;
			}
		});
	}

	private static final Map<String, String> INBOX_PARAMS;
	private static final Map<String, String> OUTBOX_PARAMS;
	private static final Map<String, String> VOICE_COMMENT_PARAMS;
	private static final Map<String, String> UPDATES_PARAMS;
	static {
		INBOX_PARAMS = new HashMap<String, String>();
		INBOX_PARAMS.put("fields", "id,title,sender.id,timeSent,body");
		OUTBOX_PARAMS = new HashMap<String, String>();
		OUTBOX_PARAMS.put("fields", "id,title,recipient.id,timeSent,body");
		VOICE_COMMENT_PARAMS = new HashMap<String, String>();
		VOICE_COMMENT_PARAMS.put("trim_user", "1");
		VOICE_COMMENT_PARAMS.put("attach_photo",  "1");
		UPDATES_PARAMS = new HashMap<String, String>();
		UPDATES_PARAMS.put("fields", "voice,diary");
	}
	private static final SimpleDateFormat VOICE_DATE_FORMAT = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);;

	private abstract class AbstractSyncTask implements Runnable {
		private final CountDownLatch mLatch;
		private final String mPath;
		private final Map<String, String> mParam;

		private AbstractSyncTask(CountDownLatch latch, String path, Map<String, String> param) {
			super();
			this.mLatch = latch;
			this.mPath = path;
			this.mParam = param;
		}

		abstract void onComplete(Bundle values);
		
		@Override
		public void run() {
			mContainer.send(mPath, mParam, new CallbackListener() {
				@Override
				public void onComplete(Bundle values) {
					try{
						AbstractSyncTask.this.onComplete(values);
					}finally{
						mLatch.countDown();
					}
				}
				
				@Override
				public void onFatal(ErrorInfo e) {
					mLatch.countDown();
				}
				
				@Override
				public void onError(ErrorInfo e) {
					mLatch.countDown();
				}
				
				@Override
				public void onCancel() {
					mLatch.countDown();
				}
			});
		}
		
		
	}
	@Override
	public void sync(int type) {
		if(!checkAccount(null)){ // TODO
			return;
		}
		if(type == SYNC_TYPE_LIGHT){
			return;
		}
		mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_START, this, type, mMeID, 0, 0);
		final Map<String, Long> userMap = new HashMap<String, Long>();
		Cursor c = mContentResolver.query(RawContactsEntity.CONTENT_URI, new String[]{RawContactsEntity._ID, RawContactsEntity.DATA1}, RawContactsEntity.MIMETYPE + "='vnd.android.cursor.item/vnd.jp.mixi.profile'", null, null);
		while(c.moveToNext()){
			long rawContactId = c.getLong(0);
			String profile = c.getString(1);
			userMap.put(profile, rawContactId);
		}
		syncMessageInbox(userMap);
		syncMessageOutbox(userMap);
		syncVoice(userMap);
		syncFriendsVoice(userMap);
		syncUpdates(userMap);
		mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_END, this, type, mMeID, 0, 0);
	}

	private void syncMessageInbox(final Map<String, Long> userMap) {
		final CountDownLatch latch = new CountDownLatch(1);
		mHandler.post(new AbstractSyncTask(latch, "/messages/@me/@inbox", INBOX_PARAMS) {
			@Override
			void onComplete(Bundle values) {
				try {
					JSONObject obj = new JSONObject(values.getString("response"));
					JSONArray array = obj.getJSONArray("entry");
					for(int i = 0; i < array.length(); i++){
						JSONObject msg = array.getJSONObject(i);
						String id = msg.getString("id");
						String title = msg.getString("title");
						Log.d(TAG, "title = " + title);
						FastDateFormat fastDateFormat = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT;
						long date = org.apache.commons.lang.time.DateUtils.parseDate(msg.getString("timeSent"),new String[]{fastDateFormat.getPattern()}).getTime();
						String sender = msg.getJSONObject("sender").getString("id");
						Log.d(TAG, "sender = " + sender);
						String body = msg.getString("body");
						if(userMap.containsKey(sender)){
							mTimeLineManager.addTimeLineItem(MixiManager.this, date, userMap.get(sender), "dummy" , SOURCE_TYPE_INBOX, id, TimeLineItem.DIRECTION_INCOMING, title, body);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				} 
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}

	private void syncMessageOutbox(final Map<String, Long> userMap) {
		final CountDownLatch latch = new CountDownLatch(1);
		mHandler.post(new AbstractSyncTask(latch, "/messages/@me/@outbox", OUTBOX_PARAMS) {
			@Override
			void onComplete(Bundle values) {
				try {
					JSONObject obj = new JSONObject(values.getString("response"));
					JSONArray array = obj.getJSONArray("entry");
					for(int i = 0; i < array.length(); i++){
						JSONObject msg = array.getJSONObject(i);
						String id = msg.getString("id");
						String title = msg.getString("title");
						Log.d(TAG, "title = " + title);
						FastDateFormat fastDateFormat = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT;
						long date = org.apache.commons.lang.time.DateUtils.parseDate(msg.getString("timeSent"),new String[]{fastDateFormat.getPattern()}).getTime();
						String recipient = msg.getJSONObject("recipient").getString("id");
						Log.d(TAG, "recipient = " + recipient);
						String body = msg.getString("body");
						if(userMap.containsKey(recipient)){
							mTimeLineManager.addTimeLineItem(MixiManager.this, date, userMap.get(recipient), "dummy" , SOURCE_TYPE_OUTBOX, id, TimeLineItem.DIRECTION_OUTGOING, title, body);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}
	

	private void syncVoice(final Map<String, Long> userMap) {
		final CountDownLatch latch = new CountDownLatch(1);
		final Set<JSONObject> voices = new HashSet<JSONObject>();
		Map<String,String> voice_params = new HashMap<String, String>();
		voice_params.put("trim_user", "1");
		voice_params.put("attach_photo",  "1");
		// TODO "since_id"

		mHandler.post(new AbstractSyncTask(latch, "/voice/statuses/@me/user_timeline", voice_params) {
			@Override
			void onComplete(Bundle values) {
				try {
					JSONArray array = new JSONArray(values.getString("response"));
					for(int i = 0; i < array.length(); i++){
						JSONObject voice = array.getJSONObject(i);
						String voice_id = voice.getString("id");
						long date = VOICE_DATE_FORMAT.parse(voice.getString("created_at")).getTime();
						int reply_count = voice.getInt("reply_count");
						Log.d(TAG, "voice_id = " + voice_id + " created_at = " + date + " reply_count = " + reply_count);
						if(reply_count > 0){ // TODO
							voices.add(voice);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
		if(!voices.isEmpty()){
			for(JSONObject voice : voices){
				syncVoiceCommentOne(userMap, voice);
			}
		}
	}

	private void syncVoiceCommentOne(final Map<String, Long> userMap, final JSONObject voice) {
		String voice_id = "";
		try{
			voice_id = voice.getString("id");
		} catch (JSONException e1) {
			// assume always success
			return;
		}
		final CountDownLatch latch = new CountDownLatch(1);
		mHandler.post(new AbstractSyncTask(latch, "/voice/replies/show/" +  voice_id, VOICE_COMMENT_PARAMS) {
			@Override
			void onComplete(Bundle values) {
				try {
					Set<Long> users = new HashSet<Long>();
					JSONArray array = new JSONArray(values.getString("response"));
					for(int i = 0; i < array.length(); i++){
						JSONObject comment = array.getJSONObject(i);
						String user = comment.getJSONObject("user").getString("id");
						if(!user.equals(mMeID) && userMap.containsKey(user)){
							users.add(userMap.get(user));
						}
					}
					if(users.isEmpty()){
						return;
					}
//					{
//						long date = VOICE_DATE_FORMAT.parse(voice.getString("created_at")).getTime();
//						String voice_id = voice.getString("id");
//						String text = voice.getString("text");
//						mTimeLineManager.addTimeLineItem(MixiManager.this, date, users, "dummy" , SOURCE_TYPE_VOICE, voice_id, TimeLineItem.DIRECTION_OUTGOING, text, "");
//					}					
					for(int i = 0; i < array.length(); i++){
						JSONObject comment = array.getJSONObject(i);
						String comment_id = comment.getString("id");
						long date = VOICE_DATE_FORMAT.parse(comment.getString("created_at")).getTime();
						String text = comment.getString("text");
						String user = comment.getJSONObject("user").getString("id");
						Log.d(TAG, "comment_id = " + comment_id + " created_at = " + date + " user = " + user + " text = " + text);
						if(userMap.containsKey(user)){
							mTimeLineManager.addTimeLineItem(MixiManager.this, date, userMap.get(user), "dummy" , SOURCE_TYPE_VOICE_COMMENT, comment_id, TimeLineItem.DIRECTION_INCOMING, "コメント", text);
						}else if(user.equals(mMeID)){
							mTimeLineManager.addTimeLineItem(MixiManager.this, date, users, "dummy" , SOURCE_TYPE_VOICE_COMMENT, comment_id, TimeLineItem.DIRECTION_OUTGOING, "コメント", text);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}

	private void syncFriendsVoice(final Map<String, Long> userMap) {
		final CountDownLatch latch = new CountDownLatch(1);
		final Set<JSONObject> voices = new HashSet<JSONObject>();
		Map<String,String> voice_params = new HashMap<String, String>();
		voice_params.put("trim_user", "1");
		voice_params.put("attach_photo",  "1");
		// TODO "since_id"
		
		mHandler.post(new AbstractSyncTask(latch, "/voice/statuses/friends_timeline/", voice_params) {
			
			@Override
			void onComplete(Bundle values) {
				try {
					JSONArray array = new JSONArray(values.getString("response"));
					for(int i = 0; i < array.length(); i++){
						JSONObject voice = array.getJSONObject(i);
						String voice_id = voice.getString("id");
						long date = VOICE_DATE_FORMAT.parse(voice.getString("created_at")).getTime();
						int reply_count = voice.getInt("reply_count");
						Log.d(TAG, "voice_id = " + voice_id + " created_at = " + date + " reply_count = " + reply_count);
						String user = voice.getJSONObject("user").getString("id");
						if(! userMap.containsKey(user)){
							continue; // Ugh!
						}
						if(reply_count > 0){ // TODO
							voices.add(voice);
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
		if(!voices.isEmpty()){
			for(JSONObject voice : voices){
				syncFriendsVoiceCommentOne(userMap, voice);
			}
		}
	}

	private void syncFriendsVoiceCommentOne(final Map<String, Long> userMap, final JSONObject voice) {
		String voice_id = "";
		try {
			voice_id = voice.getString("id");
		} catch (JSONException e1) {
			// assume always success
			return;
		}
		final CountDownLatch latch = new CountDownLatch(1);
		mHandler.post(new AbstractSyncTask(latch, "/voice/replies/show/" +  voice_id, VOICE_COMMENT_PARAMS) {
			@Override
			void onComplete(Bundle values) {
				try {
					long friend_raw_id = userMap.get(voice.getJSONObject("user").getString("id")); // assume always success
					JSONArray array = new JSONArray(values.getString("response"));
//					boolean flag = false;
					for(int i = 0; i < array.length(); i++){
						JSONObject comment = array.getJSONObject(i);
						String user = comment.getJSONObject("user").getString("id");
						if(user.equals(mMeID)){
//							flag = true;
							String comment_id = comment.getString("id");
							String text = comment.getString("text");
							long date = VOICE_DATE_FORMAT.parse(comment.getString("created_at")).getTime();
							Log.d(TAG, "comment_id = " + comment_id + " created_at = " + date + " user = " + user + " text = " + text);
							mTimeLineManager.addTimeLineItem(MixiManager.this, date, friend_raw_id, "dummy" , SOURCE_TYPE_VOICE_COMMENT, comment_id, TimeLineItem.DIRECTION_OUTGOING, "コメント", text);
						}
					}
//					if(flag){
//						long date = VOICE_DATE_FORMAT.parse(voice.getString("created_at")).getTime();
//						String text = voice.getString("text");
//						String id = voice.getString("id");
//						mTimeLineManager.addTimeLineItem(MixiManager.this, date, friend_raw_id, "dummy" , SOURCE_TYPE_VOICE, id, TimeLineItem.DIRECTION_INCOMING, text, "");
//					}					
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}
	
	private void syncUpdates(final Map<String, Long> userMap) {
		final CountDownLatch latch = new CountDownLatch(1);
		mHandler.post(new AbstractSyncTask(latch, "/updates/@me/@friends", UPDATES_PARAMS) {
			@Override
			void onComplete(Bundle values) {
				try {
					JSONObject response = new JSONObject(values.getString("response"));
					JSONArray items = response.getJSONArray("items");
					for(int i = 0; i < items.length(); i++){
						JSONObject item = items.getJSONObject(i);

						JSONObject actor = item.getJSONObject("actor");
						String actorId = actor.getString("id");
						String sender = actorId.split("=")[1]; // Ugh!
						if(!userMap.containsKey(sender)){
							continue;
						}
						
						String link = item.getString("link");
						String id = item.getString("id");
						
						JSONObject object = item.getJSONObject("object");
						String type = object.getString("objectType");
						FastDateFormat fastDateFormat = DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT;
						long date = org.apache.commons.lang.time.DateUtils.parseDate(object.getString("postedTime"),new String[]{fastDateFormat.getPattern()}).getTime();
						String body = null;
						if(type.equals("status")){
							body = object.getString("content");
						}
						if(type.equals("article")){
							body = object.getString("displayName");
						}
						if(body == null){
							continue;
						}
						Log.d(TAG, "type = " + type + " posted = " + date);
						mTimeLineManager.addActivityStreamItem(MixiManager.this, date, userMap.get(sender), "dummy", type, id, body, link);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
		}
	}

	@Override
	public Drawable getIconDrawable(String sourceType) {
		if(sourceType.equals(SOURCE_TYPE_VOICE) || sourceType.equals(SOURCE_TYPE_VOICE_COMMENT)){
			return mVoiceIcon;
		}
		if(sourceType.equals(SOURCE_TYPE_INBOX) || sourceType.equals(SOURCE_TYPE_OUTBOX)){
			return mMessageIcon;
		}
		return mIcon;
	}

	@Override
	public Intent getIntent(long rawContactId, String sourceAccount, String sourceType, String originalId) {
		if(sourceType.equals(SOURCE_TYPE_VOICE)){
			String tmp[] = originalId.split("-",2);
			String user_id = tmp[0];
			String post_time = tmp[1];
			Uri target_uri = Uri.parse("http://mixi.jp/view_voice.pl?post_time=" + post_time + "#mainArea");
			Uri uri = Uri.parse("http://mixi.jp/redirect_with_owner_id.pl?b=" + Uri.encode(target_uri.toString()) + "&k=owner_id&v=" + Uri.encode(user_id));
			
			Intent intent = new Intent(Intent.ACTION_VIEW,uri);
			return intent;
		}
		if(sourceType.equals(SOURCE_TYPE_VOICE_COMMENT)){
			String tmp[] = originalId.split("-",4);
			String user_id = tmp[0];
			String post_time = tmp[1];
			Uri target_uri = Uri.parse("http://mixi.jp/view_voice.pl?post_time=" + post_time + "#mainArea");
			Uri uri = Uri.parse("http://mixi.jp/redirect_with_owner_id.pl?b=" + Uri.encode(target_uri.toString()) + "&k=owner_id&v=" + Uri.encode(user_id));
			
			Intent intent = new Intent(Intent.ACTION_VIEW,uri);
			return intent;
		}
		if(sourceType.equals(SOURCE_TYPE_INBOX)){
			Uri uri = Uri.parse("http://mixi.jp/view_message.pl?box=inbox&id=" + originalId + "#mainArea");
			
			Intent intent = new Intent(Intent.ACTION_VIEW,uri);
			return intent;
		}
		if(sourceType.equals(SOURCE_TYPE_OUTBOX)){
			Uri uri = Uri.parse("http://mixi.jp/view_message.pl?box=outbox&id=" + originalId + "#mainArea");
			
			Intent intent = new Intent(Intent.ACTION_VIEW,uri);
			return intent;
		}
		return null;
	}

	private Drawable createIcon(BitmapDrawable orginal, String text, int color){
		Bitmap bitmap = orginal.getBitmap().copy(Bitmap.Config.RGB_565, true);
		Canvas canvas = new Canvas(bitmap);
		float h = canvas.getHeight();
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(color);
		paint.setTextSize(h / 3);
		paint.setFakeBoldText(true);
		canvas.drawText(text, 0, h, paint);
		return new BitmapDrawable(bitmap);
	}

	@Override
	public String getActionText(String sourceType) {
		if(sourceType.equals("status")){
			return "つぶやき";
		}
		if(sourceType.equals("article")){
			return "日記";
		}
		return null;
	}

	@Override
	public int getActiveAccountCount() {
		if(checkAccount(null)){
			return 1;
		}
		return 0;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub		
	}
}
