package fi.joni.lehtinen.friendfinder.circle;


import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import fi.joni.lehtinen.friendfinder.DatabaseProvider;
import fi.joni.lehtinen.friendfinder.OnCircleChangeListener;
import fi.joni.lehtinen.friendfinder.R;
import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;

public class ChooseCircleFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private CircleCursorAdapter mAdapter;
    private OnCircleChangeListener mOnCircleChangeListener;

    private long mUserID;

    public void setUserID( long id ) { mUserID = id; }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );


        String[] dataColumns = { DatabaseProvider.CIRCLE.NAME.toString() };

        int[] viewIDs = { R.id.list_item_circle_name };

        mAdapter = new CircleCursorAdapter(getActivity(), R.layout.list_item_circle, null, dataColumns, viewIDs, 0);

        setEmptyText(getString(R.string.not_member_of_circle));
        setListAdapter(mAdapter);
        setListShown(false);

        getListView().setDivider( null );
        getListView().setPadding( 0,15,0,0 );

        getLoaderManager().initLoader(0, null, this);
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
        return new CursorLoader(getActivity(), DatabaseProvider.CONTENT_URI_CIRCLE, null, null, null, null);
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

    private class CircleCursorAdapter extends SimpleCursorAdapter {

        public CircleCursorAdapter( Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            final TextView mCircleName = (TextView)view.findViewById(R.id.list_item_circle_name);
            ImageView mCircleSettings = (ImageView)view.findViewById(R.id.list_item_circle_settings);

            Cursor c = getCursor();
            c.moveToPosition( position );

            long group_id = c.getLong( c.getColumnIndex( DatabaseProvider.CIRCLE.ID.toString() ) );
            final Circle mCircle = new Circle( group_id, mCircleName.getText().toString() );

            mCircleName.setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    mOnCircleChangeListener.changeCircle( mCircle );
                    getFragmentManager().popBackStack();
                }
            } );

            mCircleSettings.setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    ManageCircleFragment mManageCircleFragment = new ManageCircleFragment();
                    mManageCircleFragment.setUserID( mUserID );
                    mManageCircleFragment.setCircle( mCircle );
                    getFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, mManageCircleFragment)
                            .addToBackStack(null)
                            .commit();
                }
            } );

            return view;
        }
    }
}
