package org.hogeika.android.app.Contacts;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public interface Manager {
	public static int SYNC_TYPE_LIGHT = 1;
	public static int SYNC_TYPE_HEAVY = 2;
	
	public String getName();

	public void authorizeCallback(int requestCode, int resultCode, Intent data);

	public void close(Activity activity);

	public void login(Activity activity, Account account);

	public boolean checkAccount(Account account);
	
	public void init(ContextWrapper activity);

	public void logout(Activity activity, Account account);

	public void sync(int type);
	
	public Drawable getIconDrawable(String sourceType);
	
	public Intent getIntent(long rawContactId, String sourceAccount, String sourceType, String originalId);

	public Intent getIntent(String sourceAccount, String sourceType, String originalId);

	public String getActionText(String sourceType);

	public int getActiveAccountCount();
}
