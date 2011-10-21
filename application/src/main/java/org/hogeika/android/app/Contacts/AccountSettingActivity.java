package org.hogeika.android.app.Contacts;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.hogeika.android.app.Contacts.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class AccountSettingActivity extends Activity {
	private static final int DIALOG_PROGRESS = 4;
	
	private static final String[] KNOWN_ACCOUNT_TYPES = {
		"com.google",
		/*"com.facebook.auth.login",*/
		"com.twitter.android.auth.login",
		"jp.mixi.authenticator.MixiAccountType",
	};

	private ContactsApplication mApplication;
	
	private List<AccountData> mAccounts;
	private AccountAdapter mAdapter;
	OnAccountsUpdateListener mListener;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.account_setting);

		mApplication = (ContactsApplication)getApplication();
		
		mAccounts = new ArrayList<AccountData>();
		final ListView listView = (ListView)findViewById(R.id.ListView_accounts);
		mAdapter = new AccountAdapter(this, mAccounts);
		listView.setAdapter(mAdapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
				showDialog(DIALOG_PROGRESS);
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						AccountData selected = mAccounts.get(position);
						if(selected.isActive()){
							mApplication.logout(AccountSettingActivity.this, selected.getAccount());
						}else{
							mApplication.login(AccountSettingActivity.this, selected.getAccount());
						}
					}
				}, 100);
			}
		});
		
		mListener = new OnAccountsUpdateListener() {
			@Override
			public void onAccountsUpdated(Account[] accounts) {
				mAdapter.clear();
		        for (int i = 0; i < accounts.length; i++) {
		            if(ArrayUtils.contains(KNOWN_ACCOUNT_TYPES, accounts[i].type)){
			            AccountData data = new AccountData(accounts[i]);
			            mAdapter.add(data);
		            }
		        }
		        // Update the account spinner
		        mAdapter.notifyDataSetChanged();				
			}

		};
	}
	
	

//	@Override
//	protected void onResume() {
//		super.onResume();
//		AccountManager am = AccountManager.get(this);
//		am.addOnAccountsUpdatedListener(mListener, null, true);
//	}
//
	@Override
	protected void onPause() {
		super.onPause();
//		AccountManager am = AccountManager.get(this);
//		am.removeOnAccountsUpdatedListener(mListener);
		try{
			dismissDialog(DIALOG_PROGRESS);
		}catch(IllegalArgumentException e){
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		case DIALOG_PROGRESS:
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setCancelable(false);
			return dialog;
		}
		return super.onCreateDialog(id);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		AccountManager am = AccountManager.get(this);
		if(hasFocus){
			am.addOnAccountsUpdatedListener(mListener, null, true);
		}else{
			am.removeOnAccountsUpdatedListener(mListener);
		}
	}



	private class AccountData {
		private Account mAccount;
		private CharSequence mTypeLabel;
		private Drawable mIcon;
		private boolean mIsActive;

		/**
		 * @param name The name of the account. This is usually the user's email address or
		 *        username.
		 * @param description The description for this account. This will be dictated by the
		 *        type of account returned, and can be obtained from the system AccountManager.
		 */
		public AccountData(Account account) {
			mAccount = account;
			AuthenticatorDescription description = getAuthenticatorDescription(account.type);
			if (description != null) {
				String packageName = description.packageName;
				PackageManager pm = getPackageManager();

				if (description.labelId != 0) {
					mTypeLabel = pm.getText(packageName, description.labelId, null);
					if (mTypeLabel == null) {
						throw new IllegalArgumentException("LabelID provided, but label not found");
					}
				} else {
					mTypeLabel = "";
				}

				if (description.iconId != 0) {
					mIcon = pm.getDrawable(packageName, description.iconId, null);
					if (mIcon == null) {
						throw new IllegalArgumentException("IconID provided, but drawable not " +
						"found");
					}
				} else {
					mIcon = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
				}

				mIsActive = mApplication.checkAccount(account);
			}
		}
		
	    private AuthenticatorDescription getAuthenticatorDescription(String type) {
	        AuthenticatorDescription[] accountTypes = AccountManager.get(AccountSettingActivity.this).getAuthenticatorTypes();
	        for (int i = 0; i < accountTypes.length; i++) {
	            if (accountTypes[i].type.equals(type)) {
	                return accountTypes[i];
	            }
	        }
	        // No match found
	        throw new RuntimeException("Unable to find matching authenticator");
	    }

	    public Account getAccount(){
	    	return mAccount;
	    }
		public String getName() {
			return mAccount.name;
		}

		public CharSequence getTypeLabel() {
			return mTypeLabel;
		}

		public Drawable getIcon() {
			return mIcon;
		}

		public String toString() {
			return getName();
		}

		public boolean isActive(){
			return mIsActive;
		}
	}

	/**
	 * Custom adapter used to display account icons and descriptions in the account spinner.
	 */
	private class AccountAdapter extends ArrayAdapter<AccountData> {
		public AccountAdapter(Context context, List<AccountData> accountData) {
			super(context, R.layout.listitem_account, accountData);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			// Inflate a view template
			if (convertView == null) {
				LayoutInflater layoutInflater = getLayoutInflater();
				convertView = layoutInflater.inflate(R.layout.listitem_account, parent, false);
			}
			TextView accountName = (TextView) convertView.findViewById(R.id.TextView_acoountName);
			TextView accountType = (TextView) convertView.findViewById(R.id.TextView_accountType);
			ImageView accountIcon = (ImageView) convertView.findViewById(R.id.ImageView_accountIcon);
			CheckBox accountActive = (CheckBox) convertView.findViewById(R.id.CheckBox_accountActive);

			// Populate template
			AccountData data = getItem(position);
			accountName.setText(data.getName());
			accountType.setText(data.getTypeLabel());
			Drawable icon = data.getIcon();
			if (icon == null) {
				icon = getResources().getDrawable(android.R.drawable.ic_menu_search);
			}
			accountIcon.setImageDrawable(icon);
			accountActive.setChecked(data.isActive());

			return convertView;
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mApplication.authorizeCallback(requestCode, resultCode, data);
	}
}
