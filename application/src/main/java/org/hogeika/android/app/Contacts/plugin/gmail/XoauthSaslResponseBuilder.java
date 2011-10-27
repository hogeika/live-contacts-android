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

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.escape.PercentEscaper;


/**
 * Builds the XOAUTH SASL client response. This class has no internal state.
 */
public class XoauthSaslResponseBuilder {
	/**
	 * Builds an XOAUTH SASL client response.
	 *
	 * @param userEmail The email address of the user, for example
	 *     "xoauth@gmail.com".
	 * @param protocol The XoauthProtocol for which to generate an authentication
	 *     string.
	 * @param tokenAndTokenSecret The OAuth token and token_secret.
	 * @param consumer The OAuth consumer that is trying to authenticate.
	 *
	 * @return A byte array containing the auth string suitable for being returned
	 * from {@code SaslClient.evaluateChallenge}. It needs to be base64-encoded
	 * before actually being sent over the network.
	 * @throws GeneralSecurityException 
	 */
	public byte[] buildResponse(String userEmail,
			XoauthProtocol protocol,
			String oauthToken,
			String oauthTokenSecret,
			String consumerKey,
			String consumerSecert)
	throws IOException, URISyntaxException, GeneralSecurityException {
		OAuthHmacSigner signer = new OAuthHmacSigner();
		signer.clientSharedSecret = consumerSecert;
		signer.tokenSharedSecret = oauthTokenSecret;
		String encodedUrl = "https://mail.google.com/mail/b/" + userEmail + "/" + protocol.getName() + "/";
		OAuthParameters authorizer = new OAuthParameters();
		authorizer.consumerKey = consumerKey;
		authorizer.computeNonce();
		authorizer.signer = signer;
		authorizer.computeTimestamp();
		authorizer.token = oauthToken;
		authorizer.version = "1.0";
		authorizer.computeSignature("GET", new GenericUrl(encodedUrl));
		
	    StringBuilder buf = new StringBuilder("GET ");
	    buf.append(encodedUrl).append(' ');
	    appendParameter(buf, "oauth_consumer_key", authorizer.consumerKey);
	    appendParameter(buf, "oauth_nonce", authorizer.nonce);
	    appendParameter(buf, "oauth_signature", authorizer.signature);
	    appendParameter(buf, "oauth_signature_method", authorizer.signatureMethod);
	    appendParameter(buf, "oauth_timestamp", authorizer.timestamp);
	    appendParameter(buf, "oauth_token", authorizer.token);
	    appendParameter(buf, "oauth_version", authorizer.version);
	    // hack: we have to remove the extra ',' at the end
	    String authString = buf.substring(0, buf.length() - 1);
		return authString.getBytes();
	}

	private static final PercentEscaper ESCAPER = new PercentEscaper("-_.~", false);
	private static String escape(String value) {
		return ESCAPER.escape(value);
	}
	private void appendParameter(StringBuilder buf, String name, String value) {
		if (value != null) {
			buf.append(escape(name)).append("=\"").append(escape(value)).append("\",");
		}
	}
}