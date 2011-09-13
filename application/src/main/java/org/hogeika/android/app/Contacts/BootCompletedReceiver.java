package org.hogeika.android.app.Contacts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
    	Log.d("BootCompletedReciver", "catch BOOT_COMPLETED");
    	final ContactsApplication application = (ContactsApplication) context.getApplicationContext();
    	application.initializeAsync(null);
	}
}
