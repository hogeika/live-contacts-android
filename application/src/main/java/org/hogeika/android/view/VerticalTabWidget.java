package org.hogeika.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TabWidget;

public class VerticalTabWidget extends TabWidget {
	public VerticalTabWidget(Context context) {
		this(context, null);
	} 
	
	public VerticalTabWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOrientation(LinearLayout.VERTICAL);
	}
	
	@Override
	public void addView(View child) {
		// Ugh!
		final LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
		lp.setMargins(0, 0, 0, 0);
		child.setLayoutParams(lp);
		super.addView(child);
	}
}
