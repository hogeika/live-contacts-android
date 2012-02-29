package org.hogeika.android.app.Contacts.plugin.gmail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang.StringUtils;
import org.hogeika.android.app.Contacts.Manager;
import org.hogeika.android.app.Contacts.TimeLineManager;
import org.hogeika.android.app.Contacts.TimeLineManager.Listener;
import org.hogeika.android.app.Contacts.TimeLineManager.TimeLineItem;
import org.hogeika.android.app.Contacts.plugin.twitter.TwitterLoginActivity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPSSLStore;

public class GMailManager implements Manager {
	private static final String ELIMINATION_REGEXP = "(?m)^.+$(\r?\n^(>|＞).*$)+\r?\n?\\Z";

	private static final int MAX_MESSAGES = 200;

	private static final String TAG = "GMailManager";

	public static final String MANAGER_NAME = "gmail";

	public static final String PREF = "gmail_setting";
	public static final String PREF_GMAIL_OAUTH_TOKEN = "oauth_token";
	public static final String PREF_GMAIL_OAUTH_TOKEN_SECRET = "oauth_token_secret";
	public static final String PREF_GMAIL_LATEST_UID_INBOX = "latest_uid_inbox";
	public static final String PREF_GMAIL_LATEST_UID_OUTBOX = "latest_uid_outbox";

	private class AccountInfo {
		private final String mToken;
		private final String mTokenSecret;
		private long mInboxLatestUID = -1;
		private long mOutboxLatestUID = -1;
		public AccountInfo(String token, String tokenSecret) {
			super();
			this.mToken = token;
			this.mTokenSecret = tokenSecret;
		}
		private String getToken() {
			return mToken;
		}
		private String getTokenSecret() {
			return mTokenSecret;
		}
		private long getInboxLatestUID() {
			return mInboxLatestUID;
		}
		private void setInboxLatestUID(long uid) {
			this.mInboxLatestUID = uid;
		}
		private long getOutboxLatestUID() {
			return mOutboxLatestUID;
		}
		private void setOutboxLatestUID(long uid) {
			this.mOutboxLatestUID = uid;
		}
		
	}
	
	private final Context mContext;
	private final ContentResolver mContentResolver;
	private Drawable mIcon = null;
	private final TimeLineManager mTimeLineManager;
	private int mRequestCodeLogin = 1;
	private Map<String, AccountInfo> mAuthMap = new HashMap<String, AccountInfo>();
	
	public GMailManager(Context context, TimeLineManager manager) {
		this.mContext = context;
		mContentResolver = context.getContentResolver();
		this.mTimeLineManager = manager;
		
		PackageManager pm = context.getPackageManager();
		try {
			mIcon = pm.getApplicationIcon("com.google.android.gm");
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	if(mIcon == null){
			mIcon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
    	}
		manager.addManager(this);
	}

	public void setRequestCode(int code) {
		mRequestCodeLogin = code;
	}


	@Override
	public String getName() {
		return MANAGER_NAME;
	}
	
  	@Override
	public void init(ContextWrapper context) {
		refreshAccountInfo();
		XoauthAuthenticator.initialize();
	}

	private void refreshAccountInfo() {
		AccountManager am = AccountManager.get(mContext);
		Account accounts[] = am.getAccountsByType("com.google");
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		mAuthMap.clear();
		for(Account account : accounts){
			String oauthToken = pref.getString(PREF_GMAIL_OAUTH_TOKEN + "." + account.name, "");
			String oauthTokenSecret = pref.getString(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + account.name, "");
			long inboxUid = pref.getLong(PREF_GMAIL_LATEST_UID_INBOX + "." + account.name, -1);
			long outboxUid = pref.getLong(PREF_GMAIL_LATEST_UID_OUTBOX + "." + account.name, -1);
			if(oauthToken.length()>0 && oauthTokenSecret.length()>0){
				AccountInfo info = new AccountInfo(oauthToken, oauthTokenSecret);
				info.setInboxLatestUID(inboxUid);
				info.setOutboxLatestUID(outboxUid);
				mAuthMap.put(account.name, info);
			}
		}
		// refresh preference
		Editor editor = pref.edit();
		editor.clear();
		for(Entry<String, AccountInfo> entry : mAuthMap.entrySet()){
			String name = entry.getKey();
			AccountInfo info = entry.getValue();
			editor.putString(PREF_GMAIL_OAUTH_TOKEN + "." + name, info.getToken());
			editor.putString(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + name, info.getTokenSecret());
			editor.putLong(PREF_GMAIL_LATEST_UID_INBOX+ "." + name, info.getInboxLatestUID());
			editor.putLong(PREF_GMAIL_LATEST_UID_OUTBOX+ "." + name, info.getOutboxLatestUID());
		}
		editor.commit();
	}
	

	@Override
	public boolean checkAccount(Account account) {
		return mAuthMap.containsKey(account.name);
	}


	@Override
	public void authorizeCallback(int requestCode, int resultCode, Intent data) {
		if(requestCode == mRequestCodeLogin && resultCode == Activity.RESULT_OK){
			String accountName = data.getStringExtra(TwitterLoginActivity.RESULT_ACCOUNT_NAME);
			String oauthToken = data.getStringExtra(TwitterLoginActivity.RESULT_OAUTH_TOKEN);
			String oauthTokenSecret = data.getStringExtra(TwitterLoginActivity.RESULT_OAUTH_TOKEN_SECRET);

			if(oauthToken.length()>0 && oauthTokenSecret.length()>0){
					SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.putString(PREF_GMAIL_OAUTH_TOKEN + "." + accountName, oauthToken);
				editor.putString(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + accountName, oauthTokenSecret);
				editor.commit();

				refreshAccountInfo();
			}
		}
	}

	@Override
	public void close(Activity activity) {
		// TODO Auto-generated method stub

	}

	@Override
	public void login(Activity activity, Account account) {
		Intent intent = new Intent(activity, GMailLoginActivity.class);
		intent.putExtra(GMailLoginActivity.EXTRA_ACCOUNT_NAME, account.name);
		activity.startActivityForResult(intent, mRequestCodeLogin);
	}

	@Override
	public void logout(Activity activity, Account account) {
		final String accountName = account.name;
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(accountName);
		builder.setPositiveButton("Logout", new OnClickListener() {		
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
				Editor editor = pref.edit();
				editor.remove(PREF_GMAIL_OAUTH_TOKEN + "." + accountName);
				editor.remove(PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + accountName);
				editor.commit();
				
				refreshAccountInfo();
			}
		});
		builder.setNegativeButton("Cancel", null);
		AlertDialog dialog = builder.create();
		dialog.setOwnerActivity(activity);
		dialog.show();
	}

	@Override
	public void sync(int type) {
		if(type == SYNC_TYPE_LIGHT){
			return;
		}
		Map<String, Long> userMap = new HashMap<String, Long>();
		Cursor c = mContentResolver.query(RawContactsEntity.CONTENT_URI, new String[]{RawContactsEntity._ID, RawContactsEntity.DATA1}, RawContactsEntity.MIMETYPE + "=?", new String[]{ CommonDataKinds.Email.CONTENT_ITEM_TYPE}, null);
		while(c.moveToNext()){
			long rawContactId = c.getLong(0);
			String address = c.getString(1);
			userMap.put(address, rawContactId);
		}		
		for(String name : mAuthMap.keySet()){
			AccountInfo info = mAuthMap.get(name);
			mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_START, this, type, name, 0, 0);
			syncOne(name, info, userMap);
			SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
			Editor editor = pref.edit();
			editor.putLong(PREF_GMAIL_LATEST_UID_INBOX+ "." + name, info.getInboxLatestUID());
			editor.putLong(PREF_GMAIL_LATEST_UID_OUTBOX+ "." + name, info.getOutboxLatestUID());
			editor.commit();
			mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_END, this, type, name, 0, 0);
		}
	}
	
	private Message[] getLastMessages(IMAPFolder folder, long lastUID) {
		Message[] messages = new Message[]{};
		try {
			if(lastUID >= 0){
				messages = folder.getMessagesByUID(lastUID, UIDFolder.LASTUID);
				if(messages.length > MAX_MESSAGES){
					Message[] tmp = Arrays.copyOfRange(messages, messages.length - MAX_MESSAGES, messages.length);
					messages = tmp;
				}
			}else{
				int end = folder.getMessageCount();
				int start = end - MAX_MESSAGES + 1;
				if(start < 1) start = 1;
				messages = folder.getMessages(start,end);
			}
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		return messages;
	}

	private void syncOne(String name, AccountInfo info,final Map<String, Long> userMap){
		try {
			IMAPSSLStore store = XoauthAuthenticator.connectToImap(
					"imap.googlemail.com",
			        993,
			        name,
			        info.getToken(),
			        info.getTokenSecret(),
			        true);
			
			IMAPFolder folder = (IMAPFolder) store.getFolder("[Gmail]/All Mail");
			if(folder.exists()){
				Message[] messages;
				folder.open(Folder.READ_ONLY);
				Log.d(TAG, "messageCount = " + folder.getMessageCount());
				long lastUID = info.getInboxLatestUID();
				messages = getLastMessages(folder, lastUID);
				int count = 0;
				for(Message tmp : messages){
					if(!(tmp instanceof IMAPMessage)) continue; // Ugh!
					IMAPMessage message = (IMAPMessage)tmp;
					Address froms[] = message.getFrom();
					if(froms == null) continue;
					Set<Long> fromSet = new HashSet<Long>();
					for(InternetAddress address : (InternetAddress[])froms){
						Log.d(TAG, "From = " + address.getAddress());
						String from = address.getAddress();
						if(userMap.containsKey(from)){
							fromSet.add(userMap.get(from));
						}
					}
					if(!fromSet.isEmpty()){
						String subject = StringUtils.trimToEmpty(message.getSubject());
						String msgId = message.getMessageID();
						String body = getTextBody(message);
						long timeStamp = message.getSentDate().getTime();
						Log.d(TAG, "Subject = " + subject);
						Log.d(TAG, "Date = " + timeStamp);
						Log.d(TAG, "msgId = " + msgId);
						Log.d(TAG, "body = " + body);
						mTimeLineManager.addTimeLineItem(this, timeStamp, fromSet, name, "email", msgId, TimeLineItem.DIRECTION_INCOMING, subject, body);
					}
					mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_PROGRESS, this, SYNC_TYPE_HEAVY, name + "/inbox", ++count, messages.length);
				}
				info.setInboxLatestUID(folder.getUIDNext());
				folder.close(false);
			}
			folder = (IMAPFolder) store.getFolder("[Gmail]/Sent Mail");
			if(folder.exists()){
				Message[] messages;
				folder.open(Folder.READ_ONLY);
				Log.d(TAG, "messageCount = " + folder.getMessageCount());
				long lastUID = info.getOutboxLatestUID();
				messages = getLastMessages(folder, lastUID);	
				
				int count = 0;
				for(Message tmp : messages){
					if(!(tmp instanceof IMAPMessage)) continue; // Ugh!
					IMAPMessage message = (IMAPMessage)tmp;
					Address[] recipients = message.getRecipients(RecipientType.TO);
					if(recipients == null) continue;
					Set<Long> toSet = new HashSet<Long>();
					for(InternetAddress address : (InternetAddress[])recipients){
						Log.d(TAG, "To = " + address.getAddress());
						String from = address.getAddress();
						if(userMap.containsKey(from)){
							toSet.add(userMap.get(from));
						}
					}
					if(!toSet.isEmpty()){
						String subject = StringUtils.trimToEmpty(message.getSubject());
						String msgId = message.getMessageID();
						String body = getTextBody(message);
						long timeStamp = message.getSentDate().getTime();
						Log.d(TAG, "Subject = " + subject);
						Log.d(TAG, "Date = " + timeStamp);
						Log.d(TAG, "msgId = " + msgId);
						Log.d(TAG, "body = " + body);
						mTimeLineManager.addTimeLineItem(this, timeStamp, toSet, name, "email", msgId, TimeLineItem.DIRECTION_OUTGOING, subject, body);
					}
					mTimeLineManager.notifyOnSyncStateChange(Listener.SYNC_PROGRESS, this, SYNC_TYPE_HEAVY, name + "/outbox", ++count, messages.length);
				}
				info.setOutboxLatestUID(folder.getUIDNext());
				folder.close(false);
			}
			store.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String shapingBody(String body) {
		return body.replaceAll(ELIMINATION_REGEXP, "\n");
	}

	private String getTextBody(IMAPMessage message) {
		String body = "";
		try {
			Object content = message.getContent();
			if(content instanceof String){
				body = (String)content;
			}
			else if(content instanceof MimeMultipart){
				MimeMultipart multipart = (MimeMultipart)content;
				int count = multipart.getCount();
				for(int i = 0; i < count; i++){
					MimeBodyPart part = (MimeBodyPart)multipart.getBodyPart(i);
					if(part.isMimeType("text/*") && StringUtils.isEmpty(part.getDisposition())){
						body = (String)part.getContent();
					}
				}
				if(body.length() == 0){
					body = multipart.getPreamble();
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return shapingBody(body);
	}

	@Override
	public Drawable getIconDrawable(String sourceType) {
		return mIcon;
	}

	@Override
	public Intent getIntent(long rawContactId, String sourceAccount, String sourceType, String originalId) {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEARCH);
		intent.setClassName("com.google.android.gm", "com.google.android.gm.ConversationListActivity");
		intent.putExtra(SearchManager.QUERY, "rfc822msgid:" + originalId);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}

	@Override
	public String getActionText(String sourceType) {
		return "GMail";
	}

	@Override
	public int getActiveAccountCount() {
		return mAuthMap.size();
	}

	@Override
	public void clear() {
		SharedPreferences pref = mContext.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
		Editor editor = pref.edit();
		for(Entry<String, AccountInfo> entry : mAuthMap.entrySet()){
			String name = entry.getKey();
			AccountInfo info = entry.getValue();
			info.setInboxLatestUID(-1);
			info.setOutboxLatestUID(-1);
			editor.putLong(PREF_GMAIL_LATEST_UID_INBOX+ "." + name, -1);
			editor.putLong(PREF_GMAIL_LATEST_UID_OUTBOX+ "." + name, -1);
		}
		editor.commit();
	}
}
