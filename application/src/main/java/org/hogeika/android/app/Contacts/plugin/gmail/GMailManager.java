package org.hogeika.android.app.Contacts.plugin.gmail;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.Folder;

import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.plugin.twitter.TwitterLogoutActivity;

import com.sun.mail.imap.IMAPSSLStore;

import twitter4j.Twitter;
import twitter4j.auth.AccessToken;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class GMailManager implements Manager {
	public static final String MANAGER_NAME = "gmail";

	public static final String PREF = "gmail_setting";
	public static final String PREF_GMAIL_OAUTH_TOKEN = "oauth_token";
	public static final String PREF_GMAIL_OAUTH_TOKEN_SECRET = "oauth_token_secret";

	private class AuthInfo {
		private final String mToken;
		private final String mTokenSecret;
		public AuthInfo(String token, String tokenSecret) {
			super();
			this.mToken = token;
			this.mTokenSecret = tokenSecret;
		}
		private String getToken() {
			return mToken;
		}
		private String getTokenSecret() {
			return mTokenSecret;
		}
		
	}
	
	private final Context mContext;
	private final TimeLineManager mTimeLineManager;
	private int mRequestCode = 1;
	private Map<String, AuthInfo> mAuthMap = new HashMap<String, AuthInfo>();
	
	public GMailManager(Context context, TimeLineManager manager) {
		this.mContext = context;
		this.mTimeLineManager = manager;
		manager.addManager(this);
	}

	public void setRequestCode(int code) {
		mRequestCode = code;
	}


	@Override
	public String getName() {
		return MANAGER_NAME;
	}
	
  	@Override
	public void init(ContextWrapper context) {
		refreshAccountInfo();
		XoauthAuthenticator.initialize();
	}

	private void refreshAccountInfo() {
		AccountManager am = AccountManager.get(mContext);
		Account accounts[] = am.getAccountsByType("com.google");
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		mAuthMap.clear();
		for(Account account : accounts){
			String oauthToken = pref.getString(PREF_GMAIL_OAUTH_TOKEN + "." + account.name, "");
			String oauthTokenSecret = pref.getString(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + account.name, "");
			if(oauthToken.length()>0 && oauthTokenSecret.length()>0){
				AuthInfo info = new AuthInfo(oauthToken, oauthTokenSecret);
				mAuthMap.put(account.name, info);
			}
		}
		// refresh preference
		Editor editor = pref.edit();
		editor.clear();
		for(Entry<String, AuthInfo> entry : mAuthMap.entrySet()){
			String name = entry.getKey();
			AuthInfo info = entry.getValue();
			editor.putString(PREF_GMAIL_OAUTH_TOKEN + "." + name, info.getToken());
			editor.putString(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + name, info.getTokenSecret());
		}
		editor.commit();
	}
	

	@Override
	public boolean checkAccount(Account account) {
		return mAuthMap.containsKey(account.name);
	}


	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		if(requestCode == mRequestCode){
			refreshAccountInfo();
		}
	}

	@Override
	public void close(Activity activity) {
		// TODO Auto-generated method stub

	}

	@Override
	public void login(Activity activity, Account account) {
		Intent intent = new Intent(activity, GMailLoginActivity.class);
		intent.putExtra(GMailLoginActivity.EXTRA_ACCOUNT_NAME, account.name);
		activity.startActivityForResult(intent, mRequestCode);
	}

	@Override
	public void logout(Activity activity, Account account) {
		Intent intent = new Intent(activity, GMailLogoutActivity.class);
		intent.putExtra(GMailLogoutActivity.EXTRA_ACCOUNT_NAME, account.name);
		activity.startActivityForResult(intent, mRequestCode);
	}

	@Override
	public void sync(int type) {
		for(String name : mAuthMap.keySet()){
			AuthInfo info = mAuthMap.get(name);
			syncOne(name, info.getToken(), info.getTokenSecret());
		}
	}
	
	private void syncOne(String name, String authToken, String authTokenSecret){
		try {
			IMAPSSLStore imapSslStore = XoauthAuthenticator.connectToImap(
					"imap.googlemail.com",
			        993,
			        name,
			        authToken,
			        authTokenSecret,
			        true);
			Folder folder = imapSslStore.getDefaultFolder();
			Log.d("GMailManager", "fullName = " + folder.getFullName());
			Log.d("GMailManager", "messageCount = " + folder.getMessageCount());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Drawable getIconDrawable(String sourceType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Intent getIntent(long rawContactId, String sourceAccount,
			String sourceType, String originalId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Intent getIntent(String sourceAccount, String sourceType,
			String originalId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getActionText(String sourceType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getActiveAccountCount() {
		return mAuthMap.size();
	}

}
