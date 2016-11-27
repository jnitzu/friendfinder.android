package fi.joni.lehtinen.friendfinder;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseProvider extends ContentProvider {

    public enum LOGIN {
        ID("_id"),
        EMAIL("email"),
        HASH("hash");

        public final String SQL_NAME;

        LOGIN(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_LOGIN + "." + SQL_NAME;
        }
    }

    public enum FRIEND {
        ID("_id"),
        FIRSTNAME("firstname"),
        LASTNAME("lastname");

        public final String SQL_NAME;

        FRIEND(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_FRIEND + "." + SQL_NAME;
        }
    }

    public enum LOCATION {
        ID("_id"),
        FRIEND_ID("friend_id"),
        LATITUDE("latitude"),
        LONGITUDE("longitude"),
        ACCURACY("accuracy"),
        TIME_RECORDED("time_recorded");

        public final String SQL_NAME;

        LOCATION(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_LOCATION + "." + SQL_NAME;
        }
    }

    public enum CIRCLE {
        ID("_id"),
        NAME("name");

        public final String SQL_NAME;

        CIRCLE(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_CIRCLE + "." + SQL_NAME;
        }
    }

    public enum LAST_CIRCLE {
        CIRCLE_ID("circle_id");

        public final String SQL_NAME;

        LAST_CIRCLE(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_LAST_CIRCLE + "." + SQL_NAME;
        }
    }

    public enum CIRCLE_MEMBERS {
        ID("_id"),
        CIRCLE_ID("circle_id"),
        FRIEND_ID("friend_id");

        public final String SQL_NAME;

        CIRCLE_MEMBERS(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_CIRCLE_MEMBERS + "." + SQL_NAME;
        }
    }

    public enum JOIN_REQUEST {
        ID("_id"),
        CIRCLE_ID("circle_id"),
        NAME("name");

        public final String SQL_NAME;

        JOIN_REQUEST(String name){
            SQL_NAME = name;
        }

        @Override
        public String toString() {
            return SQL_NAME;
        }

        public String fullName() {
            return TABLE_NAME_JOIN_REQUEST + "." + SQL_NAME;
        }
    }

    /**
     * Database specific constant declarations
     */
    private SQLiteDatabase db;
    static final String DATABASE_NAME = "friendfinder";

    // REAL TABLES
    static final String TABLE_NAME_LOGIN = "login";
    static final String TABLE_NAME_FRIEND = "friend";
    static final String TABLE_NAME_CIRCLE = "circle";
    static final String TABLE_NAME_CIRCLE_MEMBERS = "circle_members";
    static final String TABLE_NAME_LOCATION = "location";
    static final String TABLE_NAME_JOIN_REQUEST = "join_request";
    static final String TABLE_NAME_LAST_CIRCLE = "last_circle";

    // FAKE TABLES
    static final String TABLE_NAME_DEBUG = "debug";
    static final String TABLE_NAME_ALL = "all";
    static final int DATABASE_VERSION = 9;

    /**
     * ContentProvider specific constant declarations
     */
    public static final String AUTHORITY = "fi.joni.lehtinen.friendfinder.provider";
    public static final String URL = "content://" + AUTHORITY + "/";
    public static final Uri CONTENT_URI = Uri.parse(URL);
    public static final Uri CONTENT_URI_DEBUG = Uri.parse(URL + TABLE_NAME_DEBUG);
    public static final Uri CONTENT_URI_ALL = Uri.parse(URL + TABLE_NAME_ALL);
    public static final Uri CONTENT_URI_LOGIN = Uri.parse(URL + TABLE_NAME_LOGIN);
    public static final Uri CONTENT_URI_FRIEND = Uri.parse(URL + TABLE_NAME_FRIEND);
    public static final Uri CONTENT_URI_LOCATION = Uri.parse(URL + TABLE_NAME_LOCATION);
    public static final Uri CONTENT_URI_CIRCLE = Uri.parse(URL + TABLE_NAME_CIRCLE);
    public static final Uri CONTENT_URI_CIRCLE_LAST = Uri.parse(URL + TABLE_NAME_LAST_CIRCLE);
    public static final Uri CONTENT_URI_CIRCLE_MEMBERS = Uri.parse(URL + TABLE_NAME_CIRCLE_MEMBERS);
    public static final Uri CONTENT_URI_JOIN_REQUEST = Uri.parse(URL + TABLE_NAME_JOIN_REQUEST);
    public static final Uri CONTENT_URI_CIRCLE_LOCATION = Uri.parse(URL + TABLE_NAME_CIRCLE_MEMBERS + "/" + TABLE_NAME_LOCATION);


    private static final int _DEBUG = 1000;
    private static final int _ALL = 0;
    private static final int _LOGIN = 3;
    private static final int _FRIEND = 4;
    private static final int _FRIEND_ID = 5;
    private static final int _CIRCLE = 6;
    private static final int _CIRCLE_ID = 7;
    private static final int _CIRCLE_MEMBERS = 8;
    private static final int _CIRCLE_MEMBERS_ID = 9;
    private static final int _LOCATION = 10;
    private static final int _LOCATION_ID = 11;
    private static final int _JOIN_REQUEST = 12;
    private static final int _JOIN_REQUEST_ID = 13;
    private static final int _CIRCLE_LOCATION = 14;
    private static final int _CIRCLE_LAST = 15;

    private static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_DEBUG, _DEBUG);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_ALL, _ALL);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_LOGIN, _LOGIN);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_FRIEND, _FRIEND);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_FRIEND + "/#", _FRIEND_ID);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_CIRCLE, _CIRCLE);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_CIRCLE + "/#", _CIRCLE_ID);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_LAST_CIRCLE, _CIRCLE_LAST);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_CIRCLE_MEMBERS, _CIRCLE_MEMBERS);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_CIRCLE_MEMBERS + "/#", _CIRCLE_MEMBERS_ID);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_LOCATION, _LOCATION);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_LOCATION + "/#", _LOCATION_ID);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_JOIN_REQUEST, _JOIN_REQUEST);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_JOIN_REQUEST + "/#", _JOIN_REQUEST_ID);
        uriMatcher.addURI( AUTHORITY, TABLE_NAME_CIRCLE_MEMBERS + "/" + TABLE_NAME_LOCATION, _CIRCLE_LOCATION);
    }

    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db){

            // Enable foreign keys
            db.execSQL("PRAGMA foreign_keys = ON;");

            db.execSQL("CREATE TABLE " + TABLE_NAME_LOGIN + " (" +
                    LOGIN.ID + " INTEGER, " +
                    LOGIN.EMAIL + " TEXT NOT NULL, " +
                    LOGIN.HASH + " BLOB NOT NULL, " +
                    "PRIMARY KEY(" + LOGIN.ID + ") " +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_FRIEND + " (" +
                    FRIEND.ID + " INTEGER, " +
                    FRIEND.FIRSTNAME + " TEXT NOT NULL, " +
                    FRIEND.LASTNAME + " TEXT NOT NULL, " +
                    "PRIMARY KEY(" + FRIEND.ID + ") " +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_LOCATION + " (" +
                    LOCATION.ID + " INTEGER, " +
                    LOCATION.FRIEND_ID + " INTEGER NOT NULL, " +
                    LOCATION.LATITUDE + " REAL NOT NULL, " +
                    LOCATION.LONGITUDE + " REAL NOT NULL, " +
                    LOCATION.ACCURACY + " REAL NOT NULL, " +
                    LOCATION.TIME_RECORDED + " INTEGER NOT NULL, " +
                    "PRIMARY KEY(" + LOCATION.ID + "), " +
                    "FOREIGN KEY(" + LOCATION.FRIEND_ID + ") REFERENCES " + TABLE_NAME_FRIEND + "(" + FRIEND.ID + ") ON DELETE CASCADE" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_CIRCLE + " (" +
                    CIRCLE.ID + " INTEGER, " +
                    CIRCLE.NAME + " TEXT NOT NULL, " +
                    "PRIMARY KEY(" + CIRCLE.ID + ") " +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_CIRCLE_MEMBERS + " (" +
                    CIRCLE_MEMBERS.ID + " INTEGER, " +
                    CIRCLE_MEMBERS.CIRCLE_ID + " INTEGER NOT NULL, " +
                    CIRCLE_MEMBERS.FRIEND_ID + " INTEGER NOT NULL, " +
                    "PRIMARY KEY(" + CIRCLE_MEMBERS.ID + "), " +
                    "FOREIGN KEY(" + CIRCLE_MEMBERS.CIRCLE_ID + ") REFERENCES " + TABLE_NAME_CIRCLE + "(" + CIRCLE.ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY(" + CIRCLE_MEMBERS.FRIEND_ID + ") REFERENCES " + TABLE_NAME_FRIEND + "(" + FRIEND.ID + ") ON DELETE CASCADE" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_JOIN_REQUEST + " (" +
                    JOIN_REQUEST.ID + " INTEGER, " +
                    JOIN_REQUEST.CIRCLE_ID + " INTEGER NOT NULL, " +
                    JOIN_REQUEST.NAME + " TEXT NOT NULL, " +
                    "PRIMARY KEY(" + JOIN_REQUEST.ID + ") " +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_NAME_LAST_CIRCLE + " (" +
                    LAST_CIRCLE.CIRCLE_ID + " INTEGER );");

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_JOIN_REQUEST);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_LAST_CIRCLE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CIRCLE_MEMBERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CIRCLE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_LOCATION);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_FRIEND);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_LOGIN);
            onCreate(db);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);

            // Enable foreign keys
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    @Override
    public boolean onCreate() {
        DatabaseHelper dbHelper = new DatabaseHelper(getContext());

        /**
         * Create a write able database which will trigger its
         * creation if it doesn't already exist.
         */
        db = dbHelper.getWritableDatabase();
        return db != null;
    }

    @Override
    public int delete( Uri uri, String selection, String[] selectionArgs ) {
        int count = 0;
        List<Uri> notify = new ArrayList<>();

        switch (uriMatcher.match(uri)){

            case _CIRCLE_ID:
                count = db.delete( TABLE_NAME_CIRCLE, CIRCLE.ID +  " = " + uri.getPathSegments().get(1), null);
                notify.add( CONTENT_URI_CIRCLE_MEMBERS );
                break;
            case _CIRCLE:
                count = db.delete( TABLE_NAME_CIRCLE, selection, selectionArgs);
                notify.add( CONTENT_URI_CIRCLE_MEMBERS );
                break;
            case _FRIEND_ID:
                count = db.delete( TABLE_NAME_FRIEND, FRIEND.ID +  " = " + uri.getPathSegments().get(1), null);
                notify.add( CONTENT_URI_CIRCLE_MEMBERS );
                break;
            case _FRIEND:
                count = db.delete( TABLE_NAME_FRIEND, selection, selectionArgs);
                notify.add( CONTENT_URI_CIRCLE_MEMBERS );
                break;
            case _JOIN_REQUEST_ID:
                count = db.delete( TABLE_NAME_JOIN_REQUEST, JOIN_REQUEST.ID +  " = " + uri.getPathSegments().get(1), null);
                break;
            case _JOIN_REQUEST:
                count = db.delete( TABLE_NAME_JOIN_REQUEST, selection, selectionArgs);
                break;
            case _CIRCLE_MEMBERS:
                count = db.delete( TABLE_NAME_CIRCLE_MEMBERS, selection, selectionArgs);
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _ALL:
                count += db.delete( TABLE_NAME_JOIN_REQUEST, null, null);
                count += db.delete( TABLE_NAME_CIRCLE_MEMBERS, null, null);
                count += db.delete( TABLE_NAME_CIRCLE, null, null);
                count += db.delete( TABLE_NAME_LOCATION, null, null);
                count += db.delete( TABLE_NAME_FRIEND, null, null);
                count += db.delete( TABLE_NAME_LOGIN, null, null);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        for( Uri u : notify ) {
            getContext().getContentResolver().notifyChange(u, null);
        }

        return count;
    }

    @Override
    public String getType( Uri uri ) {
        switch (uriMatcher.match(uri)){

            case _LOGIN:
                return "vnd.android.cursor.item/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_LOGIN;
            case _FRIEND:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_FRIEND;
            case _FRIEND_ID:
                return "vnd.android.cursor.item/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_FRIEND;
            case _CIRCLE:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_CIRCLE;
            case _CIRCLE_ID:
                return "vnd.android.cursor.item/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_CIRCLE;
            case _CIRCLE_MEMBERS:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_CIRCLE_MEMBERS;
            case _CIRCLE_MEMBERS_ID:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_CIRCLE_MEMBERS;
            case _LOCATION:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_LOCATION;
            case _LOCATION_ID:
                return "vnd.android.cursor.dir/vnd.fi.joni.lehtinen.friendfinder." + TABLE_NAME_LOCATION;

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }


    @Override
    public Uri insert( Uri uri, ContentValues values ) {
        long rowID;
        String table_name;
        List<Uri> notify = new ArrayList<>();

        switch (uriMatcher.match(uri)){

            case _LOGIN:
                table_name = TABLE_NAME_LOGIN;
                break;
            case _FRIEND:
                table_name = TABLE_NAME_FRIEND;
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _CIRCLE:
                table_name = TABLE_NAME_CIRCLE;
                break;
            case _CIRCLE_MEMBERS:
                table_name = TABLE_NAME_CIRCLE_MEMBERS;
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _LOCATION:
                table_name = TABLE_NAME_LOCATION;
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _JOIN_REQUEST:
                table_name = TABLE_NAME_JOIN_REQUEST;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        rowID = db.insert(table_name, "", values);

        if ( rowID != -1 ) {
            Uri _uri = ContentUris.withAppendedId(uri, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);

            for( Uri u : notify ) {
                getContext().getContentResolver().notifyChange(u, null);
            }

            return _uri;
        }

        throw new SQLException("Failed to add a record into " + uri);
    }


    @Override
    public Cursor query( Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder ) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        Cursor c = null;
        switch (uriMatcher.match(uri)){

            case _LOGIN:
                qb.setTables(TABLE_NAME_LOGIN);
                break;
            case _FRIEND_ID:
                qb.appendWhere( FRIEND.ID + "=" + uri.getPathSegments().get(1));
            case _FRIEND:
                qb.setTables( TABLE_NAME_FRIEND );
                break;
            case _CIRCLE_ID:
                qb.appendWhere(CIRCLE.ID + "=" + uri.getPathSegments().get(1));
            case _CIRCLE:
                qb.setTables( TABLE_NAME_CIRCLE );
                break;
            case _CIRCLE_MEMBERS_ID:
                qb.appendWhere( CIRCLE_MEMBERS.ID + "=" + uri.getPathSegments().get(1));
            case _CIRCLE_MEMBERS:
                qb.setTables(
                    TABLE_NAME_CIRCLE_MEMBERS +
                    " JOIN " +
                    TABLE_NAME_FRIEND +
                    " ON "+ CIRCLE_MEMBERS.FRIEND_ID.fullName() + "=" + FRIEND.ID.fullName());
                break;
            case _LOCATION_ID:
                qb.appendWhere( LOCATION.ID + "=" + uri.getPathSegments().get(1));
            case _LOCATION:
                qb.setTables( TABLE_NAME_LOCATION );
                break;
            case _JOIN_REQUEST_ID:
                qb.appendWhere( JOIN_REQUEST.ID + "=" + uri.getPathSegments().get(1));
            case _JOIN_REQUEST:
                qb.setTables( TABLE_NAME_JOIN_REQUEST );
                break;
            case _CIRCLE_LOCATION:
                qb.setTables(
                        TABLE_NAME_CIRCLE_MEMBERS +
                                " JOIN " +
                                TABLE_NAME_LOCATION +
                                " ON "+ CIRCLE_MEMBERS.FRIEND_ID.fullName() + "=" + LOCATION.FRIEND_ID.fullName() +
                                " JOIN " +
                                TABLE_NAME_FRIEND +
                                " ON "+ CIRCLE_MEMBERS.FRIEND_ID.fullName() + "=" + FRIEND.ID.fullName());

                c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

                if( c == null || c.getCount() == 0 ) {
                    qb.setTables(
                        TABLE_NAME_FRIEND +
                                " JOIN " +
                                TABLE_NAME_LOCATION +
                                " ON "+ FRIEND.ID.fullName() + "=" + LOCATION.FRIEND_ID.fullName());
                    c = qb.query(db, projection, null, null, null, null, null);
                }
                break;
            case _CIRCLE_LAST:
                Cursor last = db.rawQuery( "SELECT * FROM " + TABLE_NAME_CIRCLE + " WHERE " + CIRCLE.ID + " IN ( SELECT * FROM " + TABLE_NAME_LAST_CIRCLE + " )" , null );
                if ( last != null && last.moveToFirst() && last.getCount() != 0 ){
                    return last;
                } else {
                    if( last != null )
                        last.close();
                    return db.rawQuery( "SELECT * FROM " + TABLE_NAME_CIRCLE + " LIMIT 1", null );
                }
            case _DEBUG:
                c = db.query(TABLE_NAME_LOGIN,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- LOGIN TABLE ---------------------");
                while(c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getString(1));
                }
                c.close();
                c = db.query(TABLE_NAME_FRIEND,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- FRIEND TABLE ---------------------");
                while (c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getString(1) + " : " + c.getString(2));
                }
                c.close();
                c = db.query(TABLE_NAME_CIRCLE,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- CIRCLE TABLE ---------------------");
                while(c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getString(1));
                }
                c.close();
                c = db.query(TABLE_NAME_CIRCLE_MEMBERS,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- CIRCLE MEMBERS TABLE ---------------------");
                while(c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getInt(1) + " : " + c.getInt(2));
                }
                c.close();
                c = db.query(TABLE_NAME_LOCATION,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- LOCATION TABLE ---------------------");
                while(c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getInt(1) + " : " + c.getDouble(2) + " : " + c.getDouble(3) + " : " + c.getDouble(4) + " : " + c.getLong(5));
                }
                c.close();
                c = db.query(TABLE_NAME_JOIN_REQUEST,null,null,null,null,null,null);
                Log.d("DEBUG_TAG", "----------------- JOIN REQUEST TABLE ---------------------");
                while(c.moveToNext()){
                    Log.d("DEBUG_TAG",c.getInt(0) + " : " + c.getInt(1) + " : " + c.getString(2));
                }
                c.close();
                return null;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        if( c == null )
            c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        /**
         * register to watch a content URI for changes
         */
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update( Uri uri, ContentValues values, String selection, String[] selectionArgs ) {
        int count = 0;
        String tableName;
        List<Uri> notify = new ArrayList<>();

        switch (uriMatcher.match(uri)){

            case _LOGIN:
                tableName = TABLE_NAME_LOGIN;
                break;
            case _FRIEND:
                tableName = TABLE_NAME_FRIEND;
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _LOCATION:
                tableName = TABLE_NAME_LOCATION;
                notify.add( CONTENT_URI_CIRCLE_LOCATION );
                break;
            case _CIRCLE_LAST:
                tableName = TABLE_NAME_LAST_CIRCLE;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        count = db.update(tableName, values, selection, selectionArgs);

        if(count > 0){
            getContext().getContentResolver().notifyChange(uri, null);

            for( Uri u : notify ) {
                getContext().getContentResolver().notifyChange(u, null);
            }
        }

        return count;
    }

    @NonNull
    @Override
    public ContentProviderResult[] applyBatch( ArrayList<ContentProviderOperation> operations ) throws OperationApplicationException {

        ContentProviderResult[] result = new ContentProviderResult[operations.size()];

        db.beginTransaction();
        try {
            for (int i = 0; i < operations.size(); i++) {
                result[i] = operations.get( i ).apply(this, result, i);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return result;
    }
}
