package org.hogeika.android.app.Contacts.plugin.local;

import org.hogeika.android.app.Contacts.ContactsApplication;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.ContactsApplication.InitializeCallback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
    	Log.d("SMSReceiver", "catch SMS_RECEIVED");
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
						manager.syncIncomingSMS();
						tm.notifyOnUpdate();
					}
				});
			}
		});
	}
}
