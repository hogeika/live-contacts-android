package org.hogeika.android.app.Contacts;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public abstract class AbstractTimeLiveViewActivity<T> extends Activity {
	private static final String SAVE_STATE_FCT = "FCT";
	private static final String SAVE_STATE_FVP = "FVP";

	private static final int DIALOG_PROGRESS = 3;

	private ContactsApplication mApplication;
	private ListView mListView;
	private List<T> mList;
	private ArrayAdapter<T> mAdapter;
	private int mFirstVisiblePosition = 0;
	private int mFirstChildTop = 0;
	private long mLastPauseTime = 0;
	
	private final TimeLineManager.Listener mListener = new TimeLineManager.Listener() {
		@Override
		public void onUpdate() {
			updateTimeLine(false);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApplication = (ContactsApplication)getApplication();
		mFirstVisiblePosition = 0;
		mFirstChildTop = 0;
		mLastPauseTime = 0;
	}

	protected void setListView(ListView view,ArrayAdapter<T> adaptor, List<T> list){
		mListView = view;
		mAdapter = adaptor;
		mList = list;
	}
	
	protected abstract void onTimeLineUpdated(TimeLineManager manager, List<T> tmpList);
	
	@Override
	protected void onResume() {
		super.onResume();
		long now = SystemClock.elapsedRealtime();
		if(now > mLastPauseTime + 15 * 1000){
			updateTimeLine(true);
		}
		mApplication.getTimeLineManager().addListener(mListener);
	}

	@Override
	protected void onPause() {
		mApplication.getTimeLineManager().removeListener(mListener);
		mLastPauseTime = SystemClock.elapsedRealtime();
		super.onPause();
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mFirstVisiblePosition = savedInstanceState.getInt(SAVE_STATE_FVP);
		mFirstChildTop = savedInstanceState.getInt(SAVE_STATE_FCT);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(SAVE_STATE_FVP, mListView.getFirstVisiblePosition());
		if(mListView.getChildCount()>0){
			outState.putInt(SAVE_STATE_FCT, mListView.getChildAt(0).getTop());
		}else{
			outState.putInt(SAVE_STATE_FCT, 0);
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

	private boolean mIsUpdating = false;
	protected synchronized void updateTimeLine(final boolean showDialog) {
		if(mIsUpdating){
			return;
		}
		mIsUpdating = true;
		if(showDialog){
			showDialog(DIALOG_PROGRESS);
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				TimeLineManager manager = mApplication.getTimeLineManager();
				final List<T> tmpList = new ArrayList<T>();
				onTimeLineUpdated(manager, tmpList);
				runOnUiThread(new Runnable() {				
					@Override
					public void run() {
						mList.clear();
						mList.addAll(tmpList);
						mAdapter.notifyDataSetChanged();
						if(mFirstVisiblePosition > 0 || mFirstChildTop > 0){
							mListView.setSelectionFromTop(mFirstVisiblePosition, mFirstChildTop);
						}
						mFirstVisiblePosition = 0;
						mFirstChildTop = 0;
						if(showDialog){
							try {
								dismissDialog(DIALOG_PROGRESS);
							}catch(IllegalArgumentException e){
							}
						}
						mIsUpdating = false;
					}
				});
			}
		}).start();
	}
}
