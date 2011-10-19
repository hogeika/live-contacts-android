package org.hogeika.android.app.Contacts;

import org.hogeika.android.app.Contacts.ContactsApplication.InitializeCallback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, Intent intent) {
    	int count = intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 1);
    	Log.d("AlarmReceiver", "catch Alarm Event (" + count + ")");
    	final ContactsApplication application = (ContactsApplication) context.getApplicationContext();
    	application.initializeAsync(new InitializeCallback() {
			@Override
			public void onComplete() {
				ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo ni = cm.getActiveNetworkInfo();
				if(ni == null){
					return;
				}
				switch(ni.getState()){
				case CONNECTED:
					Log.d("AlarmReceiver", "NetworkInfo.State = CONNECTED");
					application.getTimeLineManager().sync(Manager.SYNC_TYPE_HEAVY);
					break;
				case CONNECTING:
					Log.d("AlarmReceiver", "NetworkInfo.State = CONNECTING");
					try {
						Thread.sleep(10 * 1000); // Ugh! wait 10sec;
					} catch (InterruptedException e) {
					} 
					application.getTimeLineManager().sync(Manager.SYNC_TYPE_HEAVY);
					break;
				case SUSPENDED:
					Log.d("AlarmReceiver", "NetworkInfo.State = SUSPENDED");
					break;
				case DISCONNECTED:
					Log.d("AlarmReceiver", "NetworkInfo.State = DISCONNECTED");
					break;
				case UNKNOWN:
					Log.d("AlarmReceiver", "NetworkInfo.State = UNKNOWN");
					break;
				}
			}
		});
	}
}
