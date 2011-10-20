package org.hogeika.android.app.Contacts.plugin.twitter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;

import twitter4j.DirectMessage;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UserMentionEntity;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;

public class TwitterManager implements Manager {
	public static final String MANAGER_NAME = "twitter.com";
	
	public static final String PREF = "twitter_setting";
	public static final String PREF_TWITTER_OAUTH_TOKEN = "oauth_token";
	public static final String PREF_TWITTER_OAUTH_TOEKN_SECRET = "oauth_token_secret";

	private static final int REQUEST_LOGIN_TWITTER = 1;
	private int mRequestCode = REQUEST_LOGIN_TWITTER;

	private final ContentResolver mContentResolver;
	private final TimeLineManager mTimeLineManager;
	
	private Drawable mIcon;
	private Drawable mDMIcon;
	private TwitterFactory mFactory = null;
	private Map<String, Twitter> mTwitterMap = new HashMap<String, Twitter>();

	private Twitter mTmpTwitter = null;
	private RequestToken mTmpRequestToken = null;
	private Activity mTmpActivity = null;
	
	public TwitterManager(Context context, TimeLineManager timeLineManager){
		mContentResolver = context.getContentResolver();
		mTimeLineManager = timeLineManager;
		
		AuthenticatorDescription[] accountTypes = AccountManager.get(context).getAuthenticatorTypes();
		for(AuthenticatorDescription description : accountTypes){
            if (description.type.equals("com.twitter.android.auth.login")) {
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
		if(mIcon == null){ // Twitter account not found.
			return;
		}
		mDMIcon = createIcon((BitmapDrawable) mIcon, "DM", Color.RED);
		
		Resources res = context.getResources();
		ConfigurationBuilder conf = new ConfigurationBuilder();
		conf.setOAuthConsumerKey(res.getString(R.string.twitter_oauth_consumer_key));
		conf.setOAuthConsumerSecret(res.getString(R.string.twitter_oauth_consumer_secret));
		mFactory = new TwitterFactory(conf.build());
		
		timeLineManager.addManager(this);
	}

	@Override
	public void init(ContextWrapper context){
		if(mIcon == null){ // Twitter account not found.
			return;
		}
		AccountManager am = AccountManager.get(context);
		Account accounts[] = am.getAccountsByType("com.twitter.android.auth.login");

		SharedPreferences pref = context.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		mTwitterMap.clear();
		for(Account account : accounts){
			String oauthToken = pref.getString(PREF_TWITTER_OAUTH_TOKEN + "." + account.name, "");
			String oauthTokenSecret = pref.getString(PREF_TWITTER_OAUTH_TOEKN_SECRET + "." + account.name, "");
			if(oauthToken.length()>0 && oauthTokenSecret.length()>0){
				AccessToken accessToken = new AccessToken(oauthToken, oauthTokenSecret);
				Twitter twitter = mFactory.getInstance(accessToken);
				mTwitterMap.put(account.name, twitter);
			}	
		}
		// refresh preference
		Editor editor = pref.edit();
		editor.clear();
		for(Entry<String, Twitter> set : mTwitterMap.entrySet()){
			String name = set.getKey();
			Twitter twitter = set.getValue();
			try {
				AccessToken accessToken = twitter.getOAuthAccessToken();
				editor.putString(PREF_TWITTER_OAUTH_TOKEN + "." + name, accessToken.getToken());
				editor.putString(PREF_TWITTER_OAUTH_TOEKN_SECRET+ "." + name, accessToken.getTokenSecret());
			} catch (TwitterException e) {
				e.printStackTrace();
			}
		}
		editor.commit();
	}

	public void setRequestCode(int code){
		mRequestCode = code;
	}
	
	@Override
	public String getName() {
		return MANAGER_NAME;
	}
	
	@Override
	public void login(Activity activity, Account account){
		if(mTwitterMap.containsKey(account.name)){
			return;
		}
		if(mTmpActivity != null){
			return;
		}

		try {
			mTmpActivity = activity;
			mTmpTwitter = mFactory.getInstance();
			mTmpRequestToken = mTmpTwitter.getOAuthRequestToken(TwitterLoginActivity.CALLBACK_URL);
			Intent intent = new Intent(mTmpActivity, TwitterLoginActivity.class);
			intent.putExtra(TwitterLoginActivity.EXTRA_AUTH_URL, mTmpRequestToken.getAuthorizationURL());
			mTmpActivity.startActivityForResult(intent, mRequestCode);
		} catch (TwitterException e) {
			e.printStackTrace();
			mTmpActivity = null;
			mTmpTwitter = null;
			mTmpRequestToken = null;
		}
	}
	
	@Override
	public void close(Activity activity){
	}
	
	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		if(mTmpTwitter != null && requestCode == mRequestCode){
			if(resultCode == Activity.RESULT_OK){
				String oauthVerifier = data.getStringExtra(TwitterLoginActivity.RESULT_OAUTH_VERIFIER);
				String oauthToken = data.getStringExtra(TwitterLoginActivity.RESULT_OAUTH_TOKEN);

				try {
					AccessToken accessToken = mTmpTwitter.getOAuthAccessToken(mTmpRequestToken, oauthVerifier);
					Log.d("Twitter", "oauth_token = " + accessToken.getToken());
					Log.d("Twitter", "oauth_token_secret = " + accessToken.getTokenSecret());
					if(!mTmpRequestToken.getToken().equals(oauthToken)){
						Log.d("Twitter", "Ugh!");
						return;
					}
					String accountName = mTmpTwitter.getScreenName();
					Log.d("TwitterManager", "Login as " + accountName);
					mTwitterMap.put(accountName, mTmpTwitter);

					SharedPreferences pref = mTmpActivity.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
					Editor editor = pref.edit();
					editor.putString(PREF_TWITTER_OAUTH_TOKEN + "." + accountName, accessToken.getToken());
					editor.putString(PREF_TWITTER_OAUTH_TOEKN_SECRET + "." + accountName, accessToken.getTokenSecret());
					editor.commit();

					//				dumpDM(mTwitter);
				} catch (TwitterException e) {
					e.printStackTrace();
				} finally {
					mTmpRequestToken = null;
					mTmpTwitter = null;
					mTmpActivity = null;
				}
			}else{
				mTmpRequestToken = null;
				mTmpTwitter = null;
				mTmpActivity = null;
			}
		}
	}

	@Override
	public boolean checkAccount(Account account) {
		return mTwitterMap.containsKey(account.name);
	}

	@Override
	public void logout(Activity activity, Account account) {
		Intent intent = new Intent(activity, TwitterLogoutActivity.class);
		activity.startActivity(intent);
		
		mTwitterMap.remove(account.name);
		
		SharedPreferences pref = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		Editor editor = pref.edit();
		editor.remove(PREF_TWITTER_OAUTH_TOKEN + "." + account.name);
		editor.remove(PREF_TWITTER_OAUTH_TOEKN_SECRET + "." + account.name);
		editor.commit();
	}
	
	@SuppressWarnings("unused")
	private void dumpDM(Twitter twitter){
		try {
			ResponseList<DirectMessage> list = twitter.getDirectMessages();
			for(DirectMessage dm : list){
				String body = dm.getText();
				Log.d("TwitterManager", "DM incoming :" + body);
			}
			list = twitter.getSentDirectMessages();
			for(DirectMessage dm : list){
				String body = dm.getText();
				Log.d("TwitterManager", "DM outgoing:" + body);
			}
		} catch (TwitterException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public void sync(int type) {
		if(type == SYNC_TYPE_LIGHT){
			return;
		}
		for(Twitter twitter : mTwitterMap.values()){
			final Map<Long, Long> userMap = new HashMap<Long, Long>();
			Cursor c = mContentResolver.query(RawContactsEntity.CONTENT_URI, new String[]{RawContactsEntity._ID, RawContactsEntity.DATA1}, RawContactsEntity.MIMETYPE + "='vnd.android.cursor.item/vnd.twitter.profile'", null, null);
			while(c.moveToNext()){
				long rawContactId = c.getLong(0);
				long userId = Long.parseLong(c.getString(1));
				userMap.put(userId, rawContactId);
			}
			syncDM(twitter, userMap);
			syncMentions(twitter, userMap);
			syncHomeTimeline(twitter, userMap);
		}
	}
	private void syncDM(Twitter twitter, final Map<Long, Long> userMap) {
		try{
			String account = Long.toString(twitter.getId());
			ResponseList<DirectMessage> list = twitter.getDirectMessages();
			for(DirectMessage dm : list){
				String id = Long.toString(dm.getId());
				long sender = dm.getSenderId();
				long date = dm.getCreatedAt().getTime();
				String body = dm.getText();
				String userName = dm.getSenderScreenName();
				Log.d("FlowActivity", "DM incoming :" + body);
				if(userMap.containsKey(sender)){
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(sender), account, "direct_meesages", id + "-" + userName + "-" + sender, TimeLineItem.DIRECTION_INCOMING, "", body);
				}
			}
			list = twitter.getSentDirectMessages();
			for(DirectMessage dm : list){
				String id = Long.toString(dm.getId());
				long recipient = dm.getRecipientId();
				long date = dm.getCreatedAt().getTime();
				String body = dm.getText();
				String userName = dm.getRecipientScreenName();
				Log.d("FlowActivity", "DM outgoing:" + body);
				if(userMap.containsKey(recipient)){
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(recipient), account, "direct_meesages/sent", id + "-" + userName + "-" + recipient, TimeLineItem.DIRECTION_OUTGOING, "", body);
				}
			}
		}catch(TwitterException e){
			e.printStackTrace();
		}
	}
		
	private void syncMentions(Twitter twitter, final Map<Long, Long> userMap) {
		
		try {
			String account = Long.toString(twitter.getId());
			ResponseList<Status> list = twitter.getMentions();
			for(Status status : list){
				String id = Long.toString(status.getId());
				long sender = status.getUser().getId();
				long date = status.getCreatedAt().getTime();
				String body = status.getText();
				String summary = body.replaceFirst("^(@\\S+\\s)*", "");
				Log.d("FlowActivity", "Tweet <<:" + body);
				if(userMap.containsKey(sender)){
					mTimeLineManager.addTimeLineItem(this, date, userMap.get(sender), account, "statuses/mentions", id, TimeLineItem.DIRECTION_INCOMING, "", summary);
				}
			}
			list = twitter.getUserTimeline();
			for(Status status : list){
				UserMentionEntity[] menters = status.getUserMentionEntities();
				String id = Long.toString(status.getId());
				long date = status.getCreatedAt().getTime();
				String body = status.getText();
				String summary = body.replaceFirst("^(@\\S+\\s)*", "");
				Log.d("FlowActivity", "Tweet >>:" + body);
				Set<Long> users = new HashSet<Long>();
				for(UserMentionEntity user : menters){
					long recpient = user.getId();
					if(userMap.containsKey(recpient)){
						users.add(userMap.get(recpient));
					}
				}
				if(users.size()>0){
					mTimeLineManager.addTimeLineItem(this, date, users, account, "statuses/mentions/sent", id, TimeLineItem.DIRECTION_OUTGOING, "", summary);
				}
			}
		} catch (TwitterException e) {
			e.printStackTrace();
		}		
	}

	private void syncHomeTimeline(Twitter twitter, final Map<Long, Long> userMap) {
		try{
			String account = Long.toString(twitter.getId());
			ResponseList<Status> list = twitter.getHomeTimeline();
			for(Status status : list){
				String id = Long.toString(status.getId());
				long sender = status.getUser().getId();
				long date = status.getCreatedAt().getTime();
				String body = status.getText();
				String sourceType = "tweet";
				if(status.isRetweet()){
					sourceType = "RT";
					Log.d("FlowActivity", "Tweet(RT) :" + body);
				}else{
					Log.d("FlowActivity", "Tweet :" + body);
				}
				if(userMap.containsKey(sender)){
					mTimeLineManager.addActivityStreamItem(this, date, userMap.get(sender), account, sourceType, id, body, null);
				}
			}
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public Drawable getIconDrawable(String sourceType) {
		if(sourceType.startsWith("direct_meesages")){
			return mDMIcon;
		}
		return mIcon;
	}

	@Override
	public Intent getIntent(long rawContactId, String sourceAccount, String sourceType, String originalId) {
		return getIntent(sourceAccount, sourceType, originalId);
	}

	@Override
	public Intent getIntent(String sourceAccount, String sourceType, String originalId) {
		if(sourceType.startsWith("direct_meesages")){
			String tmp[] = originalId.split("-");
//			String tweet = tmp[0];
			String userName = tmp[1];
			String userId = tmp[2];
			Uri uri = Uri.parse("https://mobile.twitter.com/#!/messages/" + userName + "/" + userId);
			
			Intent intent = new Intent(Intent.ACTION_VIEW,uri);
			return intent;
		}
		if(sourceType.startsWith("statuses/")){
			Intent intent = new Intent();
			intent.setClassName("com.twitter.android", "com.twitter.android.TweetActivity");
			intent.setData(Uri.parse("content://com.twitter.android.provider.TwitterProvider/status_groups_view/id/" + originalId + "?ownerId=" + sourceAccount));
			return intent;
		}
		if(sourceType.startsWith("tweet")){
			Intent intent = new Intent();
			intent.setClassName("com.twitter.android", "com.twitter.android.TweetActivity");
			intent.setData(Uri.parse("content://com.twitter.android.provider.TwitterProvider/status_groups_view/id/" + originalId + "?ownerId=" + sourceAccount));
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
		paint.setTextSize(h / 2);
		paint.setFakeBoldText(true);
		canvas.drawText(text, 0, h, paint);
		return new BitmapDrawable(bitmap);
	}

	@Override
	public String getActionText(String sourceType) {
		if(sourceType.equals("tweet")){
			return "つぶやき";
		}
		if(sourceType.equals("RT")){
			return "リツイート";
		}
		return null;
	}

	@Override
	public int getActiveAccountCount() {
		return mTwitterMap.size();
	}

	
}
