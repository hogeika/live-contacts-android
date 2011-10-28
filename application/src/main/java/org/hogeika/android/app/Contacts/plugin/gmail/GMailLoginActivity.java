package org.hogeika.android.app.Contacts.plugin.gmail;

import java.io.IOException;

import org.hogeika.android.app.Contacts.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
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

	public static final String RESULT_ACCOUNT_NAME = "acount_name";
	public static final String RESULT_OAUTH_TOKEN = "oauth_token";
	public static final String RESULT_OAUTH_TOKEN_SECRET = "oauth_token_secret";

	private static final String SCOPE = "https://mail.google.com/";
	private static final String CONSUMER_SECRET = "anonymous";
	private static final String CONSUMER_KEY = "anonymous";	
	private static final String CALLBACK_URL = "myapp://oauth";

	private static final int DIALOG_PROGRESS = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		final String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME); // don't use now
		if(accountName == null){
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
		
		setContentView(R.layout.twitter_login);
		new Handler().post(new Runnable() {
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
				showDialog(DIALOG_PROGRESS);
				try {
					// TODO in background.
					tempCredentiaals = temporaryToken.execute();
					Log.d("GMailLoginActivity", "token = " + tempCredentiaals.token);
					Log.d("GMailLoginActivity", "tokenSecret = " + tempCredentiaals.tokenSecret);
					signer.tokenSharedSecret = tempCredentiaals.tokenSecret;
				} catch (IOException e) {
					e.printStackTrace();
					setResult(RESULT_CANCELED);
					finish();
					return;
				}finally{
					safeDismissDialog(DIALOG_PROGRESS);
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
					@Override
					public void onPageStarted(WebView view, String url, Bitmap favicon) {
						super.onPageStarted(view, url, favicon);
						showDialog(DIALOG_PROGRESS);
					}

					@Override
					public void onPageFinished(WebView view, String url) {
						super.onPageFinished(view, url);
						safeDismissDialog(DIALOG_PROGRESS);
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
							showDialog(DIALOG_PROGRESS);
							try {
								// TODO in background.
								credentials = accessToken.execute();
							} catch (IOException e) {
								e.printStackTrace();
								setResult(RESULT_CANCELED);
								finish();
								return;
							}finally{
								safeDismissDialog(DIALOG_PROGRESS);
							}
							// Ugh! Can't check login account
							Log.d("GMailLoginActivity", "access_token = " + credentials.token);
							Log.d("GMailLoginActivity", "access_tokenSecret = " + credentials.tokenSecret);

							Intent result = new Intent();
							result.putExtra(RESULT_ACCOUNT_NAME, accountName);
							result.putExtra(RESULT_OAUTH_TOKEN, credentials.token);
							result.putExtra(RESULT_OAUTH_TOKEN_SECRET, credentials.tokenSecret);
							setResult(RESULT_OK, result);
							finish();
						}
					}
				});
				webView.loadUrl(url);
			}
		});
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		case DIALOG_PROGRESS:
			return new ProgressDialog(this);
		}
		return super.onCreateDialog(id);
	}
	
	private void safeDismissDialog(int id){
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					dismissDialog(DIALOG_PROGRESS);
				}catch(IllegalArgumentException e){
				}
			}
		});
	}
}
