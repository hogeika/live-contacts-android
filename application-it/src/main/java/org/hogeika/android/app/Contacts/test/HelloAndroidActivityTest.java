package org.hogeika.android.app.Contacts.test;

import android.test.ActivityInstrumentationTestCase2;

import org.hogeika.android.app.Contacts.*;

public class HelloAndroidActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    public HelloAndroidActivityTest() {
        super(MainActivity.class);
    }

    public void testActivity() {
        MainActivity activity = getActivity();
        assertNotNull(activity);
    }
}

