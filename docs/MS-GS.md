# Masterserver <-> Game Server

MS (Masterserver) and GS (Game Server) communicate in purely WebSocket.

All JSON **MUST** be encoded in **UTF-8**.

GS **MUST** send a *GET* HTTPS request to `/v2/server/ws` of MS to init the WebSocket connection.

GS **MUST** send registration requests regularly to avoid the removal.

---
Example of a registration request message:
```json
{
    "metadata": {
        "type": 1,
        "id": <INT64>,
    },

    "info": {
        "name": <STRING>,
        "desc": <STRING>,
        "port": <UINT16>,
        "map": <STRING>,
        "playlist": <STRING>,
        "curPlayers": <INT32>,
        "maxPlayers": <INT32>,
        "password": <STRING OPTIONAL>,
        "gameState": <INT32>,
    },

    "regToken": <STRING>
}
```
---

Then MS **WILL** send back a general response message with no extra fields, and the `metadata.id` field **WILL** be the same as in request.

---
Example of a general response message:
```json
{
    "metadata": {
        "type": <NEGATIVE OF THE REQUEST>,
        "id": <INT64>,
    },

    "success": <BOOL>,
    "error": {
        "type": <INT32>,
        "msg": <STRING>
    } <WHEN success = false>,

    <OTHER FIELD SPECIFIED IN SPEC>
}
```
---
Errors:
| Type | Desc |
| --- | --- |
| 1 | Invalid registration token |
---

If the `success` field is `true` then MS **WILL** list the server on the public list.

MS **WILL** also send player join request messages to GS and GS **MUST** send back general response messages with no extra fields which **MUST** has the same `metadata.id` as in the request.

---
Example of a player join request message:
```json
{
    "metadata": {
        "type": 2,
        "id": <INT64>,
    },
    
    "sessionToken": <STRING>,
    "username": <STRING>,
    "clantag": <STRING>,
    "conv": <UINT32>
}
```
---

After not receiving `Pong` for a while, MS **WILL** send `Close` WebSocket control frame, close the connection and the server will be removed from the server list.

The connection **MUST** be immediately closed if
1. received non-UTF-8 text frame
2. received malformed JSON
3. received a JSON message without valid metadata
4. received a JSON message with valid metadata but wrong schema

Any message with unknown `metadata.id` messages **SHALL** be ignored.