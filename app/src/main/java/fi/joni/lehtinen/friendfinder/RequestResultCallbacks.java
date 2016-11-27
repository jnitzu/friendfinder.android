package fi.joni.lehtinen.friendfinder;

import fi.joni.lehtinen.friendfinder.connectionprotocol.Reply;

public interface RequestResultCallbacks {

    void OnRequestResult(Reply reply);

}

