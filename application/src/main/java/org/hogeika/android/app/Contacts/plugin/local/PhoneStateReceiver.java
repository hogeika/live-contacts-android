package org.hogeika.android.app.Contacts.plugin.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.hogeika.android.app.Contacts.ContactsApplication;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.ContactsApplication.InitializeCallback;

public class PhoneStateReceiver extends BroadcastReceiver{
	@Override
	public void onReceive(Context context, Intent intent) {
	    final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
	    if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
	    	Log.d("PhoneStateReceiver", "catch EXTRA_STATE_IDLE");
	    	final ContactsApplication application = (ContactsApplication) context.getApplicationContext();
	    	try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
			}
	    	application.initializeAsync(new InitializeCallback() {
				@Override
				public void onComplete() {
			    	final TimeLineManager tm = application.getTimeLineManager();
					final LocalContactManager manager = (LocalContactManager) tm.getManager(LocalContactManager.MANAGER_NAME);
			    	application.getExecutor().execute(new Runnable() {
						@Override
						public void run() {
							manager.syncCallLog();
							tm.notifyOnUpdate();
						}
					});
				}
			});
	    }
	}
}
