package org.hogeika.android.app.Contacts.plugin.gmail;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

public class GMailManager implements Manager {
	public static final String MANAGER_NAME = "gmail";

	public static final String PREF = "gmail_setting";
	public static final String PREF_GMAIL_OAUTH_TOKEN = "oauth_token";

	private final Context mContext;
	private final TimeLineManager mTimeLineManager;
	private int mRequestCode = 1;
	private Map<String, String> mAuthMap = new HashMap<String, String>();
	
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
		AccountManager am = AccountManager.get(context);
		Account accounts[] = am.getAccountsByType("com.google");
		for(Account account : accounts){
			Bundle bundle = null;
			try {
				bundle = am.getAuthToken(account, "mail", false, null, null).getResult();
			} catch (OperationCanceledException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
			if(authToken != null){
				Log.d("GMailManager", "authToken = " + authToken);
				mAuthMap.put(account.name, authToken);
			}
		}
	}
	
	@Override
	public boolean checkAccount(Account account) {
		return mAuthMap.containsKey(account.name);
	}


	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void close(Activity activity) {
		// TODO Auto-generated method stub

	}

	@Override
	public void login(Activity activity, Account account) {
		AccountManager am = AccountManager.get(activity);
		Bundle bundle = null;
		try {
			bundle = am.getAuthToken(account, "mail", null, activity, null, null).getResult();
		} catch (OperationCanceledException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AuthenticatorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String authToken = "";
		if(bundle.containsKey(AccountManager.KEY_INTENT)){
            Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
            int flags = intent.getFlags();
            flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
            intent.setFlags(flags);
            activity.startActivityForResult(intent, mRequestCode);
		}else{
			authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
		}
		Log.d("GMailManager", "authToken = " + authToken);
	}

	@Override
	public void logout(Activity activity, Account account) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sync(int type) {
		for(String name : mAuthMap.keySet()){
			String authToken = mAuthMap.get(name);
			syncOne(name, authToken);
		}
	}
	
	private void syncOne(String name, String authToken){
		
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
		// TODO Auto-generated method stub
		return 0;
	}

}
