package org.hogeika.android.app.Contacts;

import org.hogeika.android.app.Contacts.TimeLineManager.ClearListener;
import org.hogeika.android.app.Contacts.TimeLineManager.PurgeListenr;
import org.hogeika.android.app.Contacts.TimeLineManager.ReloadListener;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class SettingActivity extends PreferenceActivity implements OnPreferenceChangeListener {
	private ContactsApplication mApplication;
	private TimeLineManager mTimeLineManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApplication = (ContactsApplication) getApplication();
		mTimeLineManager = mApplication.getTimeLineManager();
		boolean isSyncing = mTimeLineManager.isSyncing();
		
		addPreferencesFromResource(R.xml.setting);

		PreferenceScreen ps = getPreferenceScreen();
		int count = ps.getPreferenceCount();
		for(int i = 0; i < count; i++){
			Preference pref = ps.getPreference(i);
			if(pref instanceof EditTextPreference){
				EditTextPreference edit = (EditTextPreference)pref;
				edit.setSummary(edit.getText());
			}
			if(pref instanceof ListPreference){
				ListPreference list = (ListPreference)pref;
				list.setSummary(list.getEntry());
			}
			pref.setOnPreferenceChangeListener(this);
		}
		Preference p = findPreference("account_setting");
		Intent intent = new Intent(this, AccountSettingActivity.class);
		p.setIntent(intent);
		
		p = findPreference("help");
		String url = getResources().getString(R.string.help_url);
		intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		p.setIntent(intent);
		
		p = findPreference("reload");
		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				final ProgressDialog progressDialog = new ProgressDialog(SettingActivity.this);
				progressDialog.setCancelable(false);
				progressDialog.setMessage("Loading..");
				progressDialog.show();
				mTimeLineManager.reload(new ReloadListener() {
					@Override
					public void onComplete() {
						progressDialog.dismiss();
						finish();
					}
				});
				return true;
			}
		});
		
		p = findPreference("clear");
		if(isSyncing){
			p.setEnabled(false);
		}
		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
				builder.setTitle("Clear Database?");
				builder.setMessage("Are you really clear TimeLine Database ?");
				builder.setPositiveButton("OK(Clear)", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						final ProgressDialog progressDialog = new ProgressDialog(SettingActivity.this);
						progressDialog.setCancelable(false);
						progressDialog.setMessage("Loading..");
						progressDialog.show();
						mTimeLineManager.clear(new ClearListener() {
							@Override
							public void onComplete() {
								progressDialog.dismiss();
								finish();
							}
						});
					}
				});
				builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				builder.create().show();
				return true;
			}
		});

		p = findPreference("purge");
		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				final ProgressDialog progressDialog = new ProgressDialog(SettingActivity.this);
				progressDialog.setCancelable(false);
				progressDialog.setMessage("Purging..");
				progressDialog.show();
				mTimeLineManager.purge(new PurgeListenr() {
					@Override
					public void onComplete() {
						progressDialog.dismiss();
						finish();
					}
				});
				return true;
			}
		});

//		p = findPreference("quit");
//		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
//			@Override
//			public boolean onPreferenceClick(Preference preference) {
//				moveTaskToBack(true);
//				Process.killProcess(Process.myPid());
//				return false;
//			}
//		});
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if(preference instanceof EditTextPreference){
			EditTextPreference edit = (EditTextPreference)preference;
			edit.setSummary((String)newValue);
		}
		if(preference instanceof ListPreference){
			ListPreference list = (ListPreference)preference;
			list.setSummary(list.getEntries()[list.findIndexOfValue((String)newValue)]);
		}
		if(preference.hasKey() && preference.getKey().equals("sync_period")){
			int sync_period = Integer.parseInt((String)newValue);
			if(sync_period > 0){
				mTimeLineManager.startTimer(sync_period);
			} else{
				mTimeLineManager.stopTimer();
			}
		}
		return true;
	}

}
