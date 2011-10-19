package org.hogeika.android.app.Contacts;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hogeika.android.app.Contacts.plugin.local.LocalContactManager;
import org.hogeika.android.app.Contacts.plugin.mixi.MixiManager;
import org.hogeika.android.app.Contacts.plugin.twitter.TwitterManager;

import android.accounts.Account;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class ContactsApplication extends Application {
	public static final int REQUEST_LOGIN_MIXI = 1;
	public static final int REQUEST_LOGOUT_MIXI = 2;
	public static final int REQUEST_LOGIN_TWITTER = 3;

	
	private TimeLineManager mTimeLineManager = null;
	/*package*/ LocalContactManager mLocalContactManager = null;
	private TwitterManager mTwitterManager = null;
	private MixiManager mMixiManager = null;
	
	private ExecutorService mExecutor;
	
	@Override
	public void onCreate() {
		Log.d("ContactFlowApplication", "onCreate()");
		super.onCreate();
		mExecutor = Executors.newSingleThreadExecutor();
		initializeAsync(null);
	}

	@Override
	public void onTerminate() {
		Log.d("ContactFlowApplication", "onTerminate()");
		mExecutor.shutdown();
		super.onTerminate();
	}

	public interface InitializeCallback{
		public void onComplete();
	}
	
	private CountDownLatch mInitializeLatch = null;
	
	public synchronized void initializeAsync(final InitializeCallback callback){
		if(mInitializeLatch != null){
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						mInitializeLatch.await();
					} catch (InterruptedException e) {
					}
					if(callback != null){
						callback.onComplete();
					}
				}
			}).start();
			return;
		}
		mInitializeLatch = new CountDownLatch(1);
		new Thread(new Runnable() {
			@Override
			public void run() {
				mTimeLineManager = TimeLineManager.getInstance(ContactsApplication.this);
				
				mLocalContactManager = new LocalContactManager(ContactsApplication.this, mTimeLineManager);
				
				mTwitterManager = new TwitterManager(ContactsApplication.this, mTimeLineManager);
				mTwitterManager.setRequestCode(REQUEST_LOGIN_TWITTER);

				mMixiManager = new MixiManager(ContactsApplication.this, mTimeLineManager);
				mMixiManager.setRequestCode(REQUEST_LOGIN_MIXI, REQUEST_LOGOUT_MIXI);
				
				mLocalContactManager.init(ContactsApplication.this);
				mMixiManager.init(ContactsApplication.this);
				mTwitterManager.init(ContactsApplication.this);
				
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ContactsApplication.this);
				int sync_period = Integer.parseInt(prefs.getString("sync_period", "0"));
				if(sync_period > 0){
					mTimeLineManager.startTimer(sync_period);
				}
				if(callback != null){
					callback.onComplete();
				}
				mInitializeLatch.countDown();
			}
		}).start();
	}
	
	public boolean isInitialized(){
		return mInitializeLatch != null && mInitializeLatch.getCount() == 0;
	}
	
	public TimeLineManager getTimeLineManager(){
		return mTimeLineManager;
	}
	
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_LOGIN_TWITTER){
			mTwitterManager.authorizeCallback(requestCode, resultCode, data);
		}
		if(requestCode == REQUEST_LOGIN_MIXI || requestCode == REQUEST_LOGOUT_MIXI){
			mMixiManager.authorizeCallback(requestCode, resultCode, data);
		}
	}
	
	public boolean checkAccount(Account account) {
		Manager manager = getManager(account.type);
		if(manager == null) return false;
		return manager.checkAccount(account);
	}
	
	private Manager getManager(String accountType){
		if("com.google".equals(accountType)){
			return mLocalContactManager;
		}
		if("com.twitter.android.auth.login".equals(accountType)){
			return mTwitterManager;
		}
		if("jp.mixi.authenticator.MixiAccountType".equals(accountType)){
			return mMixiManager;
		}
		return null;
	}

	public void logout(Activity activity, Account account) {
		Manager manager = getManager(account.type);
		if(manager == null) return;
		manager.logout(activity, account);
	}

	public void login(Activity activity, Account account) {
		Manager manager = getManager(account.type);
		if(manager == null) return;
		manager.login(activity, account);
	}

	public Executor getExecutor(){
		return mExecutor;
	}
}
