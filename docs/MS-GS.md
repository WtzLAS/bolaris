# Masterserver <-> Game Server

All JSON **MUST** be encoded in **UTF-8**.

GS **MUST** send a *GET* HTTPS request to `/ws` of MS to init the WebSocket connection.

In the body of the request GS **MUST** include a valid registration request JSON.

---
Example of a registration request JSON:
```json
{
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

Then MS **SHALL** send back a HTTP response with details below.

If the status code is 101 then WebSocket connection is established.

If the status code is 400 then the registration has failed and MS **SHALL** include the following registration error JSON in the body.

---
Example of a registration error JSON:
```json
{
    "type": <UINT32>,
    "desc": <STRING>
}
```
---

After the connection is established., MS **SHALL** send WebSocket `Ping` control frame with empty data attached every a few seconds and GS **MUST** send back WebSocket `Pong` control frame with a server presence response.

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

MS **CAN** also send a player join request message to GS.

---
Example of a player join request message:
```json
{
    "msgType": 1,

    "username": <STRING>,
    "clantag": <STRING>,
    "conv": <UINT32>
}
```
---

After not receiving `Pong` for a while, MS **SHALL** send `Close` WebSocket control frame, close the TCP connection and the server will be removed from the server list.