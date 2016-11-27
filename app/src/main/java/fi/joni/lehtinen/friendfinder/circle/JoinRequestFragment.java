package fi.joni.lehtinen.friendfinder.circle;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.RequestResultCallbacks;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;

public class JoinRequestFragment extends ListFragment  implements LoaderManager.LoaderCallbacks<Cursor>, ServiceConnection {

    private static final String TAG = "JoinRequestFragment";

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private JoinRequestCursorAdapter mAdapter;
    private long mUserID;

    public void setUserID( long id ) { mUserID = id; }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );


        String[] dataColumns = { DatabaseProvider.JOIN_REQUEST.NAME.toString() };

        int[] viewIDs = { R.id.list_item_circle_name };

        mAdapter = new JoinRequestCursorAdapter(getActivity(), R.layout.list_item_join_request, null, dataColumns, viewIDs, 0);

        setEmptyText(getString(R.string.no_join_requests));
        setListAdapter(mAdapter);
        setListShown(false);

        getListView().setDivider( null );
        getListView().setPadding( 0,15,0,0 );

        getLoaderManager().initLoader(0, null, this);
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
    public Loader<Cursor> onCreateLoader( int id, Bundle args) {
        return new CursorLoader(getActivity(), DatabaseProvider.CONTENT_URI_JOIN_REQUEST, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);

        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    private class JoinRequestCursorAdapter extends SimpleCursorAdapter {

        public JoinRequestCursorAdapter( Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public View getView( final int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            ImageView mConfirm = (ImageView)view.findViewById(R.id.list_item_circle_join_confirm);
            ImageView mDecline = (ImageView)view.findViewById(R.id.list_item_circle_join_decline);

            mConfirm.setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    final Cursor c = getCursor();
                    c.moveToPosition( position );

                    final Circle mCircle = new Circle( c.getLong( 1 ), c.getString( 2 ) );

                    mBinder.transmit( ConnectionProtocol.Protocols.CONFIRM_JOIN_REQUEST, mCircle, new RequestResultCallbacks() {
                        @Override
                        public void OnRequestResult( Reply reply ) {
                            switch( reply.mReplyCode ){
                                case JOIN_REQUEST_CONFIRMED_SUCCESSFULLY:
                                    Uri uri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_JOIN_REQUEST, c.getLong( 0 ) );

                                    ArrayList<ContentProviderOperation> batch = new ArrayList<>();

                                    batch.add( ContentProviderOperation.newDelete( uri ).build() );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE )
                                            .withValue( DatabaseProvider.CIRCLE.ID.toString(), mCircle.mID )
                                            .withValue( DatabaseProvider.CIRCLE.NAME.toString(), mCircle.mName )
                                            .build()
                                    );

                                    batch.add( ContentProviderOperation
                                            .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS )
                                            .withValue( DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID.toString(), mCircle.mID )
                                            .withValue( DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID.toString(), mUserID )
                                            .build()
                                    );

                                    for( byte[] data : reply.getMessages() ){
                                        String[] parts = new String( data, StandardCharsets.UTF_8 ).split( "," );

                                        Cursor c = getActivity().getContentResolver().query( DatabaseProvider.CONTENT_URI_FRIEND, null, DatabaseProvider.FRIEND.ID.toString() + " = " + Long.parseLong( parts[0] ), null, null );

                                        if ( c == null || c.getCount() == 0 ){
                                            batch.add( ContentProviderOperation
                                                    .newInsert( DatabaseProvider.CONTENT_URI_FRIEND )
                                                    .withValue( DatabaseProvider.FRIEND.ID.toString(), Long.parseLong( parts[0] ) )
                                                    .withValue( DatabaseProvider.FRIEND.FIRSTNAME.toString(), parts[1] )
                                                    .withValue( DatabaseProvider.FRIEND.LASTNAME.toString(), parts[2] )
                                                    .build()
                                            );
                                        }

                                        if( c != null ) c.close();

                                        batch.add( ContentProviderOperation
                                                .newInsert( DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS )
                                                .withValue( DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID.toString(), mCircle.mID )
                                                .withValue( DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID.toString(), Long.parseLong( parts[0] ) )
                                                .build()
                                        );
                                    }

                                    try {
                                        getActivity().getContentResolver().applyBatch( DatabaseProvider.AUTHORITY, batch );
                                    } catch( RemoteException | OperationApplicationException e ){
                                        Log.e( TAG, Log.getStackTraceString( e ));
                                    }


                                    break;
                                case JOIN_REQUEST_ERROR:
                                default:

                            }

                        }
                    } );

                }
            } );

            mDecline.setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    final Cursor c = getCursor();
                    c.moveToPosition( position );

                    mBinder.transmit( ConnectionProtocol.Protocols.DECLINE_JOIN_REQUEST, new Circle( c.getLong( 1 ), null ), new RequestResultCallbacks() {
                        @Override
                        public void OnRequestResult( Reply reply ) {
                            switch( reply.mReplyCode ) {
                                case JOIN_REQUEST_DECLINED_SUCCESSFULLY:
                                    Uri uri = ContentUris.withAppendedId( DatabaseProvider.CONTENT_URI_JOIN_REQUEST, c.getLong( 0 ) );
                                    getActivity().getContentResolver().delete( uri, null, null );
                                    break;
                                case JOIN_REQUEST_ERROR:
                                default:
                            }
                        }
                    } );
                }
            } );

            return view;
        }
    }

    @Override
    public void onServiceConnected( ComponentName name, IBinder iBinder ) {
        mBinder = ( ServerConnectionService.ServerConnectionBinder ) iBinder;
    }

    @Override
    public void onServiceDisconnected( ComponentName name ) {

    }
}
