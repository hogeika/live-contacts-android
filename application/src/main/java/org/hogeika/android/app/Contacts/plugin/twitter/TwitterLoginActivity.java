package org.hogeika.android.app.Contacts.plugin.twitter;

import org.hogeika.android.app.Contacts.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class TwitterLoginActivity extends Activity {
	public static final String EXTRA_AUTH_URL = "auth_url";
	public static final String RESULT_OAUTH_TOKEN = "oauth_token";
	public static final String RESULT_OAUTH_VERIFIER = "oauth_verifier";
	public static final String CALLBACK_URL = "myapp://oauth";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		final String authUrl = intent.getStringExtra(EXTRA_AUTH_URL);
		if(authUrl == null){
			finishActivity(RESULT_CANCELED);
			return;
		}
		setContentView(R.layout.twitter_login);
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				final WebView webView = (WebView) findViewById(R.id.WebView_twitter_login);
				WebSettings webSettings = webView.getSettings();
				webSettings.setJavaScriptEnabled(true);
				webView.setWebViewClient(new WebViewClient(){
					private ProgressDialog progressDialog = new ProgressDialog(TwitterLoginActivity.this);

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
							String oauthToken = uri.getQueryParameter("oauth_token");
							String oauthVerifier = uri.getQueryParameter("oauth_verifier");

							Intent result = new Intent();
							result.putExtra(RESULT_OAUTH_TOKEN, oauthToken);
							result.putExtra(RESULT_OAUTH_VERIFIER, oauthVerifier);
							setResult(RESULT_OK, result);
							finish();
						}

					}
				});
				webView.loadUrl(authUrl);
			}
		}, 500);;
	}
}
