package fi.joni.lehtinen.friendfinder;


import fi.joni.lehtinen.friendfinder.connectionprotocol.dto.Circle;

public interface OnCircleChangeListener {

    void changeCircle( Circle circle );
    void removeCircle( Circle circle );
    void removeCircleMember( long userId );
}
