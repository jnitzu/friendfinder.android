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
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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

public class RegistrationFragment extends Fragment implements ServiceConnection {

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private OnLoginListener mOnLoginListener;

    private EditText mFirstName;
    private EditText mLastName;
    private EditText mEmail;
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private Button mRegisterButton;
    private View mProgressView;
    private View mRegistrationView;

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {

        View view = inflater.inflate( R.layout.fragment_registration, container, false );

        mFirstName = (EditText) view.findViewById( R.id.registration_first_name );
        mLastName = (EditText) view.findViewById( R.id.registration_last_name );
        mEmail = (EditText) view.findViewById( R.id.registration_email );
        mPassword = (EditText) view.findViewById( R.id.registration_pw_1 );
        mPasswordConfirm = (EditText) view.findViewById( R.id.registration_pw_2 );
        mRegisterButton = (Button) view.findViewById( R.id.registration_send_button );
        mProgressView = view.findViewById( R.id.registration_progress );
        mRegistrationView = view.findViewById( R.id.registration_form );

        mEmail.setOnFocusChangeListener( new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange( View v, boolean hasFocus ) {
                if(!hasFocus){
                    checkEmailTaken();
                }
            }
        } );

        mPasswordConfirm.setOnEditorActionListener( new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction( TextView textView, int id, KeyEvent keyEvent ) {
                if( id == R.id.register || id == EditorInfo.IME_ACTION_DONE ) {

                    // Hide soft keyboard
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService( Context.INPUT_METHOD_SERVICE );
                    imm.hideSoftInputFromWindow( mPasswordConfirm.getWindowToken(), 0 );

                    attemptRegistration();
                    return true;
                }
                return false;
            }

        } );

        mRegisterButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                attemptRegistration();
            }
        } );

        mRegisterButton.setEnabled( false );

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

    private void showProgress( final boolean show ) {
        int shortAnimTime = getResources().getInteger( android.R.integer.config_shortAnimTime );

        mRegistrationView.animate().setDuration( shortAnimTime ).alpha( show ? 0.5f : 1 ).setListener( new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd( Animator animation ) {

                // Disable registration interactive objects
                mFirstName.setEnabled( !show );
                mLastName.setEnabled( !show );
                mEmail.setEnabled( !show );
                mPassword.setEnabled( !show );
                mPasswordConfirm.setEnabled( !show );
                mRegisterButton.setEnabled( !show );
            }
        } );

        mProgressView.animate().setDuration( shortAnimTime ).alpha( show ? 1 : 0 ).setListener( new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd( Animator animation ) {
                mProgressView.setVisibility( show ? View.VISIBLE : View.GONE );
            }
        } );
    }

    private void checkEmailTaken(){
        String email = mEmail.getText().toString();
        mBinder.transmit( ConnectionProtocol.Protocols.EMAIL_TAKEN, new Login( email, "", null ), new RequestResultCallbacks() {

            @Override
            public void OnRequestResult( Reply reply ) {
                if(reply == null){
                    return;
                }

                switch( reply.mReplyCode ){
                    case EMAIL_TAKEN:
                        if(reply.getMessage( 0 )[0] != (byte)0){
                            RegistrationFragment.this.mEmail.setError( getString( R.string.error_email_taken ) );
                            RegistrationFragment.this.mEmail.requestFocus();
                        }
                        break;
                    default:
                }
            }

        });
    }

    private void attemptRegistration() {

        Validator mValidator = new Validator();

        // Reset errors.
        mFirstName.setError( null );
        mLastName.setError( null );
        mEmail.setError( null );
        mPassword.setError( null );
        mPasswordConfirm.setError( null );

        // Store values at the time of the login attempt.
        String firstName = mFirstName.getText().toString();
        String lastName = mLastName.getText().toString();
        String email = mEmail.getText().toString();
        String password = mPassword.getText().toString();
        String passwordConfirm = mPasswordConfirm.getText().toString();


        if( TextUtils.isEmpty( firstName ) ) {
            mFirstName.setError( getString( R.string.error_field_required ) );
            mFirstName.requestFocus();
            return;
        }

        if( TextUtils.isEmpty( lastName ) ) {
            mLastName.setError( getString( R.string.error_field_required ) );
            mLastName.requestFocus();
            return;
        }

        if( TextUtils.isEmpty( email ) ) {
            mEmail.setError( getString( R.string.error_field_required ) );
            mEmail.requestFocus();
            return;
        } else if( !mValidator.isEmailValid( email ) ) {
            mEmail.setError( getString( R.string.error_invalid_email ) );
            mEmail.requestFocus();
            return;
        }

        if( TextUtils.isEmpty( password ) ) {
            mPassword.setError( getString( R.string.error_field_required ) );
            mPassword.requestFocus();
            return;
        } else if( !mValidator.isPasswordValid( password ) ) {
            mPassword.setError( getString( R.string.error_invalid_password ) );
            mPassword.requestFocus();
            return;
        } else if( !password.equals( passwordConfirm ) ) {
            mPasswordConfirm.setError( getString( R.string.error_password_no_match ) );
            mPasswordConfirm.requestFocus();
            return;
        }

        showProgress( true );

        mBinder.transmit( ConnectionProtocol.Protocols.REGISTER, new Register( email, firstName, lastName, password ), new RequestResultCallbacks(){

            @Override
            public void OnRequestResult( Reply reply ) {

                showProgress( false );

                if(reply == null){

                    return;
                }

                switch( reply.mReplyCode ){
                    case REGISTERATION_SUCCESSFUL:

                        List<byte[]> messages = reply.getMessages();

                        Log.d( "UtilityERROR", "byte[] length: " + messages.get( 0 ).length );

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
                    case EMAIL_TAKEN:
                        RegistrationFragment.this.mEmail.setError( getString( R.string.error_email_taken ) );
                        RegistrationFragment.this.mEmail.requestFocus();
                        break;
                    default:
                }
            }

        });
    }

    @Override
    public void onServiceConnected( ComponentName componentName, IBinder iBinder ) {
        mBinder = ( ServerConnectionService.ServerConnectionBinder ) iBinder;

        mRegisterButton.setEnabled( true );
    }

    @Override
    public void onServiceDisconnected( ComponentName componentName ) {
        mRegisterButton.setEnabled( false );
    }
}
