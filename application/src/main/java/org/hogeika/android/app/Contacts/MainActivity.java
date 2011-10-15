package org.hogeika.android.app.Contacts;


import org.hogeika.android.app.Contacts.R;
import org.hogeika.android.app.Contacts.ContactsApplication.InitializeCallback;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class MainActivity extends TabActivity {

    private static final String SAVED_STATE_CURRENT_TAB = "currentTab";
	private static String TAG = "ContactFlow";
	private static final int DIALOG_PROGRESS = 1;
	private static final int DIALOG_ALERT_INIT = 2;
	private static final int DIALOG_ALERT_ACCOUNT = 3;
	
    
    private ContactsApplication mApplication;
	private long mLastLightSyncTime = 0;
	private long mLastHeavySyncTime = 0;
	
    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in onSaveInstanceState(Bundle). <b>Note: Otherwise it is null.</b>
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.main);

        // InitializeCallbck is call after onRestoreInstanceState()
        String tag;
        if(savedInstanceState == null){ // only first onCreate()
        	tag = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("start_tab", "contacts");
        } else{
    		tag = savedInstanceState.getString(SAVED_STATE_CURRENT_TAB);
        }
        final String currentTab = tag;
        
		mApplication = (ContactsApplication)getApplication();
		showDialog(DIALOG_PROGRESS);
		mApplication.initializeAsync(new InitializeCallback(){
			@Override
			public void onComplete() {
				runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
				        TabHost tabHost = getTabHost();
				        TabSpec spec;
				        Intent intent;
				        Resources res = getResources();
				        
				        intent = new Intent(MainActivity.this, RecentSessionActivity.class);
				        spec = tabHost.newTabSpec("contacts").setIndicator("Contacts",res.getDrawable(R.drawable.ic_tab_contacts)).setContent(intent);
				        tabHost.addTab(spec);

				        intent = new Intent(MainActivity.this, ActivityStreamActivity.class);
				        spec = tabHost.newTabSpec("activity").setIndicator("Activity",res.getDrawable(R.drawable.ic_tab_activity)).setContent(intent);
				        tabHost.addTab(spec);
				        
			        	tabHost.setCurrentTabByTag(currentTab);

			        	try{
			        		dismissDialog(DIALOG_PROGRESS);
						}catch(IllegalArgumentException e){
						}
			        	
						TimeLineManager timeLineManager = mApplication.getTimeLineManager();
						if(timeLineManager.getManagerCount()<=1){
							showDialog(DIALOG_ALERT_INIT);
							return;
						}
						if(timeLineManager.getActiveAccountCount() == 0){
							showDialog(DIALOG_ALERT_ACCOUNT);
							return;
						}
					}
				});
			}
		});
		
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		mApplication.initializeAsync(new InitializeCallback() {
			@Override
			public void onComplete() {
				syncTimeLine(Manager.SYNC_TYPE_LIGHT);
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(SAVED_STATE_CURRENT_TAB, getTabHost().getCurrentTabTag());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if(mApplication.getTimeLineManager().isSyncing()){
			menu.findItem(R.id.menuitem_sync).setEnabled(false);
		}else{
			menu.findItem(R.id.menuitem_sync).setEnabled(true);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuitem_sync:
			syncTimeLine(Manager.SYNC_TYPE_HEAVY);
			return true;
		case R.id.menuitem_setting:
			Intent intent = new Intent(this, SettingActivity.class);
			startActivity(intent);
			return true;
			
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder;
		switch(id){
		case DIALOG_PROGRESS:
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setCancelable(false);
			dialog.setMessage("Loading..");
			return dialog;
			
		case DIALOG_ALERT_INIT:
			builder = new AlertDialog.Builder(MainActivity.this);
			builder.setMessage("Contact Flow を利用するには、Twitter 公式アプリ、または Mixi 公式アプリのどちらか、または（できれば）両方がインストールされアカウントが設定されている必要があります。\n詳しくはHelpを参照してください。");
			builder.setPositiveButton("Help(Web)", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String url = getResources().getString(R.string.help_url);
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});
			builder.setNegativeButton("Cancel", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				}
			});
			return builder.create();
			
		case DIALOG_ALERT_ACCOUNT:
			builder = new AlertDialog.Builder(MainActivity.this);
			builder.setMessage("連携するアカウントを設定してください。メニューボタンを押して「Setting」を選び「Account Settings」からも設定できます。\n設定後、メニューから「sync」を実行してください。");
			builder.setPositiveButton("設定する", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(MainActivity.this, AccountSettingActivity.class);
					startActivity(intent);
				}
			});
			builder.setNeutralButton("Help(Web)", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String url = getResources().getString(R.string.help_url);
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});
			builder.setNegativeButton("Cancel", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					
				}
			});
			return builder.create();
		}
		return super.onCreateDialog(id);
	}
	
	private void syncTimeLine(int type){
		long now = SystemClock.elapsedRealtime();
		if(type == Manager.SYNC_TYPE_LIGHT){
			if(now > mLastLightSyncTime + 30 * 1000){
				mLastLightSyncTime = now;
				mApplication.getTimeLineManager().sync(type);
			}
		}
		else if(type == Manager.SYNC_TYPE_HEAVY){
			if(now > mLastHeavySyncTime + 60 * 1000){
				mLastLightSyncTime = now;
				mLastHeavySyncTime = now;
				mApplication.getTimeLineManager().sync(type);
			}
		}
	}
}

