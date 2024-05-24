# Masterserver <-> Game Server

MS (Masterserver) and GS (Game Server) communicate in purely WebSocket.

All JSON **MUST** be encoded in **UTF-8**.

GS **MUST** send a *GET* HTTPS request to `/ws` of MS to init the WebSocket connection.

After establishing, MS **SHALL** go through the three states below in order:

1. `REGISTERING`
2. `NORMAL`
3. `CLOSED`

MS **SHALL** go into `REGISTERING` right after a connection is accepted, and GS **MUST** send a registration request message to proceed.

---
Example of a registration request message:
```json
{
    "msgType": 1,
    "msgId": <UINT32>,

    "name": <STRING>,
    "desc": <STRING>,
    "port": <UINT16>,
    "map": <STRING>,
    "playlist": <STRING>,
    "maxPlayers": <UINT32>,
    "password": <STRING OPTIONAL>,
    "regToken": <STRING>
}
```
---

Then MS **SHALL** send back a registration response message, and the `msgId` field **SHALL** be the same as in request method.

---
Example of a registration response message:
```json
{
    "msgType": 2,
    "msgId": <UINT32>,

    "success": <BOOL>,
    "id": <UINT32 WHEN success = true>,
    "error": <STRING WHEN success = false>
}
```
---

If the `success` field is `true` then MS **SHALL** go into `NORMAL` state.

In this state, MS **SHALL** send WebSocket `Ping` control frame with empty data attached every a few seconds and GS **MUST** send back WebSocket `Pong` control frame with a server presence response.

---
Example of a server presence response message:
```json
{
    "name": <STRING>,
    "desc": <STRING>,
    "port": <UINT16>,
    "map": <STRING>,
    "playlist": <STRING>,
    "curPlayers": <UINT32>,
    "maxPlayers": <UINT32>,
    "password": <STRING OPTIONAL>,
    "state": <UINT32>
}
```
---

MS **CAN** also send a player join request message to GS and GS **MUST** send back a player join response message which **MUST** has the same `msgId` as in the request.

---
Example of a player join request message:
```json
{
    "msgType": 3,
    "msgId": <UINT32>,
    
    "sessionToken": <STRING>,
    "username": <STRING>,
    "clantag": <STRING>
}
```
---
Example of a player join response message:
```json
{
    "msgType": 4,
    "msgId": <UINT32>,
    
    "conv": <UINT32>
}
```
---

After not receiving `Pong` for a while, MS **SHALL** send `Close` WebSocket control frame, go into `CLOSED` state and the server will be removed from the server list.