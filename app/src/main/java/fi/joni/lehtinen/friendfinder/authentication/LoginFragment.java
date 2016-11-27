package fi.joni.lehtinen.friendfinder.authentication;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.List;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.MainActivity;
import fi.joni.lehtinen.friendfinder.OnLoginListener;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.RequestResultCallbacks;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Utility;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Login;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Register;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.User;


public class LoginFragment extends Fragment implements ServiceConnection {

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private OnLoginListener mOnLoginListener;

    private EditText mEmail;
    private EditText mPassword;
    private TextView mForgottenPassword;
    private Button mLoginButton;
    private View mProgressView;
    private View mLoginView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mEmail = (EditText) view.findViewById(R.id.login_email);
        mPassword = (EditText) view.findViewById(R.id.login_password);
        mLoginButton = (Button) view.findViewById(R.id.login_button);
        mForgottenPassword = (TextView) view.findViewById(R.id.password_forgotten);
        mProgressView = view.findViewById(R.id.login_progress);
        mLoginView = view.findViewById(R.id.login_form);

        mPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_ACTION_DONE) {
                    attemptLogin();
                    return true;
                }
                return false;
            }

        });

        mLoginButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                attemptLogin();
            }

        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent myIntent = new Intent( getActivity(), ServerConnectionService.class );
        getActivity().bindService( myIntent, this, 0 );
    }

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unbindService( this );
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mOnLoginListener = (OnLoginListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()  + " must implement OnLoginListener");
        }
    }

    private void attemptLogin() {

        Validator mValidator = new Validator();

        // Reset errors.
        mEmail.setError(null);
        mPassword.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmail.getText().toString();
        String password = mPassword.getText().toString();


        if (TextUtils.isEmpty(email)) {
            mEmail.setError(getString(R.string.error_field_required));
            mEmail.requestFocus();
            return;
        } else if (!mValidator.isEmailValid(email)) {
            mEmail.setError(getString(R.string.error_invalid_email));
            mEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)){
            mPassword.setError(getString(R.string.error_field_required));
            mPassword.requestFocus();
            return;
        } else if (!mValidator.isPasswordValid(password)) {
            mPassword.setError(getString(R.string.error_invalid_password));
            mPassword.requestFocus();
            return;
        }

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);

        mBinder.transmit( ConnectionProtocol.Protocols.LOGIN, new Login( email, password, null), new RequestResultCallbacks(){

            @Override
            public void OnRequestResult( Reply reply ) {

                showProgress( false );

                if(reply == null){

                    return;
                }

                switch( reply.mReplyCode ){
                    case LOGIN_SUCCESSFUL:

                        List<byte[]> messages = reply.getMessages();

                        long id = Utility.byteArrayToLong( messages.get( 0 ) );

                        ContentValues loginContent = new ContentValues();
                        loginContent.put( DatabaseProvider.LOGIN.ID.toString(), id );
                        loginContent.put( DatabaseProvider.LOGIN.EMAIL.toString(), new String(messages.get( 3 ), StandardCharsets.UTF_8 ));
                        loginContent.put( DatabaseProvider.LOGIN.HASH.toString(), Utility.passwordDecode( new String(messages.get( 4 ), StandardCharsets.UTF_8) ) );

                        ContentValues friendContent = new ContentValues();
                        friendContent.put( DatabaseProvider.FRIEND.ID.toString(), id );
                        friendContent.put( DatabaseProvider.FRIEND.FIRSTNAME.toString(), new String(messages.get( 1 ), StandardCharsets.UTF_8 ));
                        friendContent.put( DatabaseProvider.FRIEND.LASTNAME.toString(), new String(messages.get( 2 ), StandardCharsets.UTF_8 ));

                        Cursor c = getActivity().getContentResolver().query( DatabaseProvider.CONTENT_URI_LOGIN, null, null, null, null );

                        if( c != null && c.getCount() != 0 ){
                            getActivity().getContentResolver().update( DatabaseProvider.CONTENT_URI_LOGIN, loginContent, DatabaseProvider.LOGIN.ID + " = " + id, null );
                            getActivity().getContentResolver().update( DatabaseProvider.CONTENT_URI_FRIEND, friendContent, DatabaseProvider.FRIEND.ID + " = " + id, null );
                        } else {
                            getActivity().getContentResolver().insert( DatabaseProvider.CONTENT_URI_LOGIN, loginContent );
                            getActivity().getContentResolver().insert( DatabaseProvider.CONTENT_URI_FRIEND, friendContent );
                        }

                        if( c != null )
                            c.close();

                        mBinder.login(id);
                        mBinder.startRequests();

                        mOnLoginListener.onLogin( id );
                        break;

                    // This case shouldn't happen now that we check if email exists before hand
                    case CREDENTIAL_ERROR_EMAIL:
                        LoginFragment.this.mEmail.setError(getString(R.string.error_incorrect_email));
                        LoginFragment.this.mEmail.requestFocus();
                        break;
                    case CREDENTIAL_ERROR_PASSWORD:
                        LoginFragment.this.mPassword.setError(getString(R.string.error_incorrect_password));
                        LoginFragment.this.mPassword.requestFocus();
                        break;
                    default:
                }
            }

        });
    }

    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginView.animate().setDuration(shortAnimTime).alpha(show ? 0.5f : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

                // Disable login interactive objects
                mEmail.setEnabled(!show);
                mPassword.setEnabled(!show);
                mLoginButton.setEnabled(!show);
                mForgottenPassword.setEnabled(!show);
            }
        });

        mProgressView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }


    @Override
    public void onServiceConnected( ComponentName componentName, IBinder iBinder ) {
        mBinder = ( ServerConnectionService.ServerConnectionBinder ) iBinder;

        mLoginButton.setEnabled( true );
    }

    @Override
    public void onServiceDisconnected( ComponentName componentName ) {
        mLoginButton.setEnabled( false );
    }
}
