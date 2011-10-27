package org.hogeika.android.app.Contacts.plugin.gmail;

import org.hogeika.android.app.Contacts.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

public class GMailLogoutActivity extends Activity {
	public static final String EXTRA_ACCOUNT_NAME = "account_name";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		final String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
		if(accountName == null){
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
		setContentView(R.layout.gmail_logout);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(accountName);
		builder.setPositiveButton("Logout", new OnClickListener() {		
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SharedPreferences pref = getSharedPreferences(GMailManager.PREF, Activity.MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.remove(GMailManager.PREF_GMAIL_OAUTH_TOKEN + "." + accountName);
				editor.remove(GMailManager.PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + accountName);
				editor.commit();
				setResult(RESULT_OK);
				finish();
			}
		});
		builder.setNegativeButton("Cancel", new OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(this);
		dialog.show();
	}
}
