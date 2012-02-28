package org.hogeika.android.app.Contacts;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;

public abstract class AbstractTimeLiveViewActivity extends Activity {
	private static final int DIALOG_PROGRESS = 3;

	private ContactsApplication mApplication;
	
	private final TimeLineManager.Listener mListener = new TimeLineManager.Listener() {
		@Override
		public void onUpdate() {
			onTimeLineUpdated();
		}

		@Override
		public void onSyncStateChange(int state, Manager manager, int type, String accountName, int count, int total) {
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApplication = (ContactsApplication)getApplication();
	}

	protected abstract void onTimeLineUpdated();
	
	@Override
	protected void onResume() {
		super.onResume();
		mApplication.getTimeLineManager().addListener(mListener);
	}

	@Override
	protected void onPause() {
		mApplication.getTimeLineManager().removeListener(mListener);
		super.onPause();
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		case DIALOG_PROGRESS:
			ProgressDialog dialog = new ProgressDialog(this);
			dialog.setCancelable(true);
			dialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					finish();
				}
			});
			return dialog;
		}
		return super.onCreateDialog(id);
	}
	
	protected TimeLineManager getTimeLineManager(){
		return mApplication.getTimeLineManager();
	}
}
