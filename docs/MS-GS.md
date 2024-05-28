# Masterserver <-> Game Server

MS (Masterserver) and GS (Game Server) communicate in purely WebSocket.

All JSON **MUST** be encoded in **UTF-8**.

GS **MUST** send a *GET* HTTPS request to `/ws` of MS to init the WebSocket connection.

MS **WILL** wait for a registration request message right after a connection is accepted, and GS **MUST** send one to proceed.

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

Then MS **WILL** send back a registration response message, and the `metadata.id` field **WILL** be the same as in request.

---
Example of a registration response message:
```json
{
    "metadata": {
        "type": 2,
        "id": <INT64>,
    },

    "success": <BOOL>,
    "id": <INT64 WHEN success = true>,
    "error": {
        "type": <INT32>,
        "msg": <STRING>
    } <WHEN success = false>
}
```
---
Errors:
| Type | Desc |
| --- | --- |
| 1 | Invalid registration token |
---

If the `success` field is `true` then MS **WILL** list the server on the public list.

In this state, MS **WILL** send WebSocket `Ping` control frame every a few seconds and GS **MUST** send back WebSocket `Pong` control frame with a server presence response attached.

---
Example of a server presence response message:
```json
{
    "name": <STRING>,
    "desc": <STRING>,
    "port": <UINT16>,
    "map": <STRING>,
    "playlist": <STRING>,
    "curPlayers": <INT32>,
    "maxPlayers": <INT32>,
    "password": <STRING OPTIONAL>,
    "gameState": <INT32>
}
```
---

MS **WILL** also send player join request messages to GS and GS **MUST** send back player join response messages which **MUST** has the same `metadata.id` as in the request.

---
Example of a player join request message:
```json
{
    "metadata": {
        "type": 3,
        "id": <INT64>,
    },
    
    "sessionToken": <STRING>,
    "username": <STRING>,
    "clantag": <STRING>,
    "conv": <UINT32>
}
```
---
Example of a player join response message:
```json
{
    "metadata": {
        "type": 4,
        "id": <INT64>,
    },
    
    "success": <BOOL>,
    "error": {
        "type": <INT32>,
        "msg": <STRING>
    } <WHEN success = false>
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