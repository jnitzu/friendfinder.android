package fi.joni.lehtinen.friendfinder.circle;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.OnCircleChangeListener;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.RequestResultCallbacks;
import fi.joni.lehtinen.friendfinder.ServerConnectionService;
import fi.joni.lehtinen.friendfinder.connectionprotocol.ConnectionProtocol;
import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.CircleMember;

public class ManageCircleFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>, ServiceConnection {

    private ServerConnectionService.ServerConnectionBinder mBinder;
    private CircleMemberCursorAdapter mAdapter;
    private OnCircleChangeListener mOnCircleChangeListener;

    private Button mAddFriendButton;
    private Button mRemoveCurrentUserButton;
    private Button mDeleteCircleButton;
    private ListView mListView;
    private View mHeader;
    private View mFoorer;

    private long mUserID;
    private Circle mCircle;

    public void setUserID( long id ){ mUserID = id; }
    public void setCircle( Circle circle ){ mCircle = circle; }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
        final View view = inflater.inflate( R.layout.fragment_manage_circle, container, false );

        mListView = (ListView)view.findViewById( android.R.id.list );

        mHeader = inflater.inflate( R.layout.fragment_manage_circle_header, mListView, false );
        mFoorer = inflater.inflate( R.layout.fragment_manage_circle_footer, mListView, false );

        mListView.addHeaderView( mHeader );
        mListView.addFooterView( mFoorer );

        mAddFriendButton = (Button)mHeader.findViewById( R.id.add_friend_button );

        mRemoveCurrentUserButton = (Button)mFoorer.findViewById( R.id.remove_current_user );
        mDeleteCircleButton = (Button)mFoorer.findViewById( R.id.delete_circle );

        mListView.setDivider( null );

        mAddFriendButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {

                AddFriendFragment mAddFriendFragment = new AddFriendFragment();
                mAddFriendFragment.setGroupID( mCircle.mID );
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mAddFriendFragment)
                        .addToBackStack(null)
                        .commit();
            }
        } );

        mRemoveCurrentUserButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                mBinder.transmit( ConnectionProtocol.Protocols.REMOVE_CIRCLE_MEMBER , new CircleMember( mCircle.mID, mUserID ), new RequestResultCallbacks() {
                    @Override
                    public void OnRequestResult( Reply reply ) {

                    if(reply == null){
                        return;
                    }

                    switch( reply.mReplyCode ){
                        case REMOVE_FRIEND_SUCCESSFUL:
                            String selection = DatabaseProvider.CIRCLE.ID + " = " + mCircle.mID;

                            getActivity().getContentResolver().delete( DatabaseProvider.CONTENT_URI_CIRCLE, selection, null );

                            mOnCircleChangeListener.removeCircle( mCircle );
                            getFragmentManager().popBackStack();
                            break;
                        default:
                            Snackbar.make(view, "Server error while trying to leave circle.", Snackbar.LENGTH_LONG);
                    }
                    }
                } );
            }
        } );

        mDeleteCircleButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                mBinder.transmit( ConnectionProtocol.Protocols.DELETE_CIRCLE , mCircle, new RequestResultCallbacks() {
                    @Override
                    public void OnRequestResult( Reply reply ) {
                    if(reply == null){
                        return;
                    }

                    switch( reply.mReplyCode ){
                        case CIRCLE_DELETE_SUCCESSFUL:
                            String selection = DatabaseProvider.CIRCLE.ID + " = " + mCircle.mID;

                            getActivity().getContentResolver().delete( DatabaseProvider.CONTENT_URI_CIRCLE, selection, null );

                            mOnCircleChangeListener.removeCircle( mCircle );
                            getFragmentManager().popBackStack();
                            break;
                        default:
                            Snackbar.make(view, "Server error while trying to delete circle.", Snackbar.LENGTH_LONG);
                    }
                    }
                } );
            }
        } );

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );

        mAdapter = new CircleMemberCursorAdapter(getActivity(), R.layout.list_item_manage_circle, null, new String[]{}, new int[]{}, 0);

        setListAdapter(mAdapter);

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
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mOnCircleChangeListener = (OnCircleChangeListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()  + " must implement OnCircleChangeListener");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader( int id, Bundle args) {
        String selection = DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID + " = ? AND " + DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID + " <> ?";
        String[] selectionArgs = new String[]{ mCircle.mID+"", mUserID+"" };

        return new CursorLoader(getActivity(), DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS, null, selection, selectionArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    private class CircleMemberCursorAdapter extends SimpleCursorAdapter {

        public CircleMemberCursorAdapter( Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            final TextView mName = (TextView)view.findViewById(R.id.list_item_circle_name);
            ImageView mRemoveMember = (ImageView)view.findViewById(R.id.list_item_circle_remove_member);

            final Cursor c = getCursor();
            c.moveToPosition( position );

            final long id = c.getLong( c.getColumnIndex( DatabaseProvider.FRIEND.ID.toString() ) );
            String firstname = c.getString( c.getColumnIndex( DatabaseProvider.FRIEND.FIRSTNAME.toString() ) );
            String lastname = c.getString( c.getColumnIndex( DatabaseProvider.FRIEND.LASTNAME.toString() ) );

            mName.setText( firstname + " " + lastname );

            mRemoveMember.setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    mBinder.transmit( ConnectionProtocol.Protocols.REMOVE_CIRCLE_MEMBER , new CircleMember( mCircle.mID, id ), new RequestResultCallbacks() {
                        @Override
                        public void OnRequestResult( Reply reply ) {

                            if(reply == null){
                                return;
                            }

                            switch( reply.mReplyCode ){
                                case REMOVE_FRIEND_SUCCESSFUL:
                                    String selection = DatabaseProvider.CIRCLE_MEMBERS.CIRCLE_ID + " = " + mCircle.mID + " AND " + DatabaseProvider.CIRCLE_MEMBERS.FRIEND_ID + " = " + id;

                                    getActivity().getContentResolver().delete( DatabaseProvider.CONTENT_URI_CIRCLE_MEMBERS, selection, null );
                                    mOnCircleChangeListener.removeCircleMember( id );
                                    break;
                                default:
                                    Snackbar.make(parent.getRootView(), "Server error while trying to remove " + mName.getText(), Snackbar.LENGTH_LONG);
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
