package fi.joni.lehtinen.friendfinder.circle;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.OnCircleChangeListener;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.RequestResultCallbacks;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;
import fi.joni.lehtinen.friendfinder.authentication.Validator;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Utility;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;

public class CreateCircleFragment extends Fragment implements ServiceConnection {

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private OnCircleChangeListener mOnCircleChangeListener;

    private EditText mName;
    private Button mCreateButton;
    private View mProgressView;
    private View mFormView;

    private long mUserID;

    public void setUserID( long id ) { mUserID = id; }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {

        View view = inflater.inflate( R.layout.fragment_create_circle, container, false );

        mName = (EditText)view.findViewById( R.id.create_circle_name_edit_text );
        mCreateButton = (Button)view.findViewById( R.id.create_circle_button );
        mProgressView = view.findViewById(R.id.create_circle_progress);
        mFormView = view.findViewById(R.id.create_circle_form);

        mName.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.ime_add_friend || id == EditorInfo.IME_ACTION_DONE) {
                    addFriend();
                    return true;
                }
                return false;
            }

        });

        mCreateButton.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                addFriend();
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
            mOnCircleChangeListener = (OnCircleChangeListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()  + " must implement OnCircleChangeListener");
        }
    }


    private void addFriend(){

        Validator mValidator = new Validator();

        mName.setError(null);

        final String name = mName.getText().toString();


        if ( TextUtils.isEmpty(name)) {
            mName.setError(getString(R.string.error_field_required));
            mName.requestFocus();
            return;
        } else if (!mValidator.isCircleNameValid(name)) {
            mName.setError(getString(R.string.error_invalid_email));
            mName.requestFocus();
            return;
        }

        showProgress(true);

        mBinder.transmit( ConnectionProtocol.Protocols.CREATE_CIRCLE, new Circle( 0, name ), new RequestResultCallbacks(){

            @Override
            public void OnRequestResult( Reply reply ) {

                showProgress( false );

                if(reply == null){

                    return;
                }

                switch( reply.mReplyCode ){
                    case CIRCLE_CREATE_SUCCESSFUL:

                        List<byte[]> messages = reply.getMessages();

                        long group_id = Utility.byteArrayToLong( messages.get( 0 ) );

                        mOnCircleChangeListener.changeCircle( new Circle( group_id, name ) );

                        ArrayList<ContentProviderOperation> batch = new ArrayList<>();

                        ContentValues circleContent = new ContentValues();
                        circleContent.put( DatabaseProvider.CIRCLE.ID.toString(), group_id );
                        circleContent.put( DatabaseProvider.CIRCLE.NAME.toString(), name );

                        ContentValues circleMemberContent = new ContentValues();
                        circleMemberContent.put( DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID.toString(), group_id );
                        circleMemberContent.put( DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID.toString(), mUserID );

                        batch.add( ContentProviderOperation
                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE )
                            .withValues( circleContent )
                            .build()
                        );

                        batch.add( ContentProviderOperation
                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS )
                            .withValues( circleMemberContent )
                            .build()
                        );

                        try {
                            getActivity().getContentResolver().applyBatch( DatabaseProvider.AUTHORITY, batch );
                        } catch( RemoteException | OperationApplicationException e ){

                        }

                        getFragmentManager().popBackStack();

                        break;
                    default:
                }
            }

        });
    }
    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mFormView.animate().setDuration(shortAnimTime).alpha(show ? 0.5f : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

                // Disable login interactive objects
                mName.setEnabled(!show);
                mCreateButton.setEnabled(!show);
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

        mCreateButton.setEnabled( true );
    }

    @Override
    public void onServiceDisconnected( ComponentName componentName ) {
        mCreateButton.setEnabled( false );
    }


}
