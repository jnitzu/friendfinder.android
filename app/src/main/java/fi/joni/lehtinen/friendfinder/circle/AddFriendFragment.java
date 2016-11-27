package fi.joni.lehtinen.friendfinder.circle;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.RequestResultCallbacks;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;
import fi.joni.lehtinen.friendfinder.authentication.Validator;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.CircleMember;

public class AddFriendFragment extends Fragment implements ServiceConnection {

    private ServerConnectionService.ServerConnectionBinder mBinder;

    private EditText mEmail;
    private Button mAddFriendButton;
    private View mProgressView;
    private View mFormView;

    private long mGroupID;

    public void setGroupID( long id ) { mGroupID = id; }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
        View view = inflater.inflate( R.layout.fragment_add_friend, container, false );

        mEmail = (EditText)view.findViewById( R.id.add_friend_edit_text );
        mAddFriendButton = (Button)view.findViewById( R.id.add_friend_button );
        mProgressView = view.findViewById(R.id.add_friend_progress);
        mFormView = view.findViewById(R.id.add_friend_form);

        mEmail.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.ime_add_friend || id == EditorInfo.IME_ACTION_DONE) {
                    addFriend();
                    return true;
                }
                return false;
            }

        });

        mAddFriendButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                addFriend();
            }

        });

        return view;
    }

    private void addFriend(){

        Validator mValidator = new Validator();

        mEmail.setError(null);

        String email = mEmail.getText().toString();


        if ( TextUtils.isEmpty(email)) {
            mEmail.setError(getString(R.string.error_field_required));
            mEmail.requestFocus();
            return;
        } else if (!mValidator.isEmailValid(email)) {
            mEmail.setError(getString(R.string.error_invalid_email));
            mEmail.requestFocus();
            return;
        }

        showProgress(true);

        mBinder.transmit( ConnectionProtocol.Protocols.ADD_CIRCLE_MEMBER, new CircleMember( mGroupID, email ), new RequestResultCallbacks(){

            @Override
            public void OnRequestResult( Reply reply ) {

                showProgress( false );

                if(reply == null){
                    return;
                }

                switch( reply.mReplyCode ){
                    case ADD_FRIEND_SUCCESSFUL:
                        Snackbar.make(mFormView, "Friend invited to circle.", Snackbar.LENGTH_LONG);
                        getFragmentManager().popBackStack();
                        break;

                    // This case shouldn't happen now that we check if email exists before hand
                    case FRIEND_NOT_FOUND:
                        AddFriendFragment.this.mEmail.setError(getString(R.string.error_incorrect_email));
                        AddFriendFragment.this.mEmail.requestFocus();
                        break;
                    default:
                        Snackbar.make(mFormView, "Server error while trying to add a friend.", Snackbar.LENGTH_LONG);
                }
            }

        });
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

    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mFormView.animate().setDuration(shortAnimTime).alpha(show ? 0.5f : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

                // Disable login interactive objects
                mEmail.setEnabled(!show);
                mAddFriendButton.setEnabled(!show);
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

        mAddFriendButton.setEnabled( true );
    }

    @Override
    public void onServiceDisconnected( ComponentName componentName ) {
        mAddFriendButton.setEnabled( false );
    }

}
