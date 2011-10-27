package org.hogeika.android.app.Contacts.plugin.gmail;

import java.io.IOException;

import org.hogeika.android.app.Contacts.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.api.client.auth.oauth.OAuthAuthorizeTemporaryTokenUrl;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.googleapis.auth.oauth.GoogleOAuthGetAccessToken;
import com.google.api.client.googleapis.auth.oauth.GoogleOAuthGetTemporaryToken;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;

public class GMailLoginActivity extends Activity {
	public static final String EXTRA_ACCOUNT_NAME = "account_name";

	private static final String SCOPE = "https://mail.google.com/";
	private static final String CONSUMER_SECRET = "anonymous";
	private static final String CONSUMER_KEY = "anonymous";	
	private static final String CALLBACK_URL = "myapp://oauth";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		final String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
		if(accountName == null){
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
		
		setContentView(R.layout.twitter_login);
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				String appName = getResources().getString(R.string.app_name);
				final HttpTransport transport = new ApacheHttpTransport();
				final OAuthHmacSigner signer = new OAuthHmacSigner();
				signer.clientSharedSecret = CONSUMER_SECRET;
				GoogleOAuthGetTemporaryToken temporaryToken = new GoogleOAuthGetTemporaryToken();
				temporaryToken.transport = transport;
				temporaryToken.signer = signer;
				temporaryToken.consumerKey = CONSUMER_KEY;
				temporaryToken.scope = SCOPE;
				temporaryToken.displayName = appName;
				temporaryToken.callback = CALLBACK_URL;
				
				OAuthCredentialsResponse tempCredentiaals = null;
				try {
					tempCredentiaals = temporaryToken.execute();
					Log.d("GMailLoginActivity", "token = " + tempCredentiaals.token);
					Log.d("GMailLoginActivity", "tokenSecret = " + tempCredentiaals.tokenSecret);
					signer.tokenSharedSecret = tempCredentiaals.tokenSecret;
				} catch (IOException e) {
					e.printStackTrace();
					setResult(RESULT_CANCELED);
					finish();
					return;
				}
				
				OAuthAuthorizeTemporaryTokenUrl authUrl = new OAuthAuthorizeTemporaryTokenUrl("https://www.google.com/accounts/OAuthAuthorizeToken");
				authUrl.temporaryToken = tempCredentiaals.token;
				
				final String url = authUrl.build();
				
				final WebView webView = (WebView) findViewById(R.id.WebView_twitter_login);
				CookieManager cm = CookieManager.getInstance();
				cm.removeAllCookie();
				WebSettings webSettings = webView.getSettings();
				webSettings.setJavaScriptEnabled(true);
				webView.setWebViewClient(new WebViewClient(){
					private ProgressDialog progressDialog = new ProgressDialog(GMailLoginActivity.this);

					@Override
					public void onPageStarted(WebView view, String url, Bitmap favicon) {
						super.onPageStarted(view, url, favicon);
						progressDialog.show();
					}

					@Override
					public void onPageFinished(WebView view, String url) {
						super.onPageFinished(view, url);
						if(progressDialog.isShowing()){
							progressDialog.dismiss();
						}
						if(url != null && url.startsWith(CALLBACK_URL)){
							Uri uri = Uri.parse(url);
							String requestToken = uri.getQueryParameter("oauth_token");
							String verifier = uri.getQueryParameter("oauth_verifier");
							
							Log.d("GMailLoginActivity", uri.getQuery());
							
							GoogleOAuthGetAccessToken accessToken = new GoogleOAuthGetAccessToken();
							accessToken.transport = transport;
							accessToken.temporaryToken = requestToken;
							accessToken.signer = signer;
							accessToken.consumerKey = CONSUMER_KEY;
							accessToken.verifier = verifier;							

							OAuthCredentialsResponse credentials;
							try {
								credentials = accessToken.execute();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								return;
							}
							Log.d("GMailLoginActivity", "access_token = " + credentials.token);
							Log.d("GMailLoginActivity", "access_tokenSecret = " + credentials.tokenSecret);

							SharedPreferences pref = getSharedPreferences(GMailManager.PREF, Activity.MODE_PRIVATE);
							Editor editor = pref.edit();
							editor.putString(GMailManager.PREF_GMAIL_OAUTH_TOKEN + "." + accountName, credentials.token);
							editor.putString(GMailManager.PREF_GMAIL_OAUTH_TOKEN_SECRET + "." + accountName, credentials.tokenSecret);
							editor.commit();

							Intent result = new Intent();
							setResult(RESULT_OK, result);
							finish();
						}
					}
				});
				webView.loadUrl(url);
			}
		}, 500);;
	}
}
