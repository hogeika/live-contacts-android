package org.hogeika.android.app.Contacts.plugin.gmail;

import javax.security.auth.callback.Callback;

public class NameCallback implements Callback {
	private String name = null;

	public NameCallback(String string) {
		// nothing to do 
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
