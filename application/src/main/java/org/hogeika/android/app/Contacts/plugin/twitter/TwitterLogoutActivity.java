package org.hogeika.android.app.Contacts.plugin.twitter;

import org.hogeika.android.app.Contacts.R;

import android.app.Activity;
import android.os.Bundle;

public class TwitterLogoutActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.twitter_logout);
		finish();
	}
}
