package org.hogeika.android.app.Contacts;

import org.hogeika.android.app.Contacts.ContactsApplication.InitializeCallback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
    	int count = intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 1);
    	Log.d("AlarmReceiver", "catch Alarm Event (" + count + ")");
//    	if(count == 1) return;
    	final ContactsApplication application = (ContactsApplication) context.getApplicationContext();
    	application.initializeAsync(new InitializeCallback() {
			@Override
			public void onComplete() {
				application.getTimeLineManager().sync(Manager.SYNC_TYPE_HEAVY);
			}
		});
	}

}
