/* Copyright 2010 Google Inc.
 * Copyright 2011 Happy.Wide.Grove@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hogeika.android.app.Contacts.plugin.gmail;

import java.security.Provider;
import java.security.Security;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.URLName;

import com.sun.mail.imap.IMAPSSLStore;


/**
 * Performs XOAUTH authentication.
 *
 * <p>Before using this class, you must call {@code initialize} to install the
 * XOAUTH SASL provider.
 */
public class XoauthAuthenticator {
  public static final class XoauthProvider extends Provider {
    /**
	 * 
	 */
	private static final long serialVersionUID = -3099468546556621534L;

	public XoauthProvider() {
      super("Google Xoauth Provider", 1.0,
            "Provides the Xoauth experimental SASL Mechanism");
      put("SaslClientFactory.XOAUTH",
          "org.hogeika.android.app.Contacts.plugin.gmail.XoauthSaslClientFactory");
    }
  }

  /**
   * Installs the XOAUTH SASL provider. This must be called exactly once before
   * calling other methods on this class.
   */
  public static void initialize() {
    Security.addProvider(new XoauthProvider());
  }

  /**
   * Connects and authenticates to an IMAP server with XOAUTH. You must have
   * called {@code initialize}.
   *
   * @param host Hostname of the imap server, for example {@code
   *     imap.googlemail.com}.
   * @param port Port of the imap server, for example 993.
   * @param userEmail Email address of the user to authenticate, for example
   *     {@code xoauth@gmail.com}.
   * @param oauthToken The user's OAuth token.
   * @param oauthTokenSecret The user's OAuth token secret.
   * @param debug Whether to enable debug logging on the IMAP connection.
   *
   * @return An authenticated IMAPSSLStore that can be used for IMAP operations.
   */
  public static IMAPSSLStore connectToImap(String host,
                                           int port,
                                           String userEmail,
                                           String oauthToken,
                                           String oauthTokenSecret,
                                           boolean debug) throws Exception {
    Properties props = new Properties();
    props.put("mail.imaps.sasl.enable", "true");
    props.put("mail.imaps.sasl.mechanisms", "XOAUTH");
    props.put(XoauthSaslClientFactory.OAUTH_TOKEN_PROP,
              oauthToken);
    props.put(XoauthSaslClientFactory.OAUTH_TOKEN_SECRET_PROP,
              oauthTokenSecret);
    props.put(XoauthSaslClientFactory.CONSUMER_KEY_PROP,
              "anonymous");
    props.put(XoauthSaslClientFactory.CONSUMER_SECRET_PROP,
              "anonymous");
    Session session = Session.getInstance(props);
    session.setDebug(debug);

    final URLName unusedUrlName = null;
    IMAPSSLStore store = new IMAPSSLStore(session, unusedUrlName);
    final String emptyPassword = "";
    store.connect(host, port, userEmail, emptyPassword);
    return store;
  }
}
