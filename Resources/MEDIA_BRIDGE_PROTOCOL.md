# Music bridge: phone ↔ cluster

The cluster (`live.nesh.music.cluster`) gets now-playing data from the rider's phone over two
Bluetooth paths and merges them:

1. **AVRCP (standard, always on).** The phone's OS sends title/artist/album/duration/position/
   play state for whatever app is playing (Spotify, YT Music, …). The cluster's AOSP build must
   have the A2DP Sink and AVRCP Controller profiles enabled
   (`bluetooth.profile.a2dp.sink.enabled=true`, `bluetooth.profile.avrcp.controller.enabled=true`).
   No phone app is required.
2. **SPP companion (music-phone app, optional).** Adds what AVRCP can't carry reliably: album
   art, the queue ("up next"), the source app name and extra tags. Described below.

When both are available for the same track the cluster uses the SPP fields and takes play state
and position from AVRCP. Cluster commands go over SPP when it's connected, otherwise AVRCP.

## Setup

Pair the phone with the cluster. In music-phone choose the cluster from the paired devices,
enable notification access (required by Android to read other apps' media sessions), allow
Bluetooth access, and tap **Start sending**. The bridge restarts on boot and when the phone
connects to the cluster, until the user taps **Stop sending**.

## Transport

- The cluster is the RFCOMM server. The phone connects as client.
- Service UUID: `00001101-0000-1000-8000-00805F9B34FB`. Service name: `NL Music Bridge`.
- Every frame is a 4-byte big-endian payload length followed by that many bytes of UTF-8 JSON.
  Receivers reject frames larger than 1 MB (cluster) / 64 KB (phone).
- Unknown JSON fields must be ignored, and unknown `type`s skipped, so either side can add
  optional fields without breaking the other.

## Phone → cluster: `MEDIA_STATE`

A complete state frame is sent right after connecting, on every metadata/queue/playback change,
and once per second while playing.

```json
{
  "type": "MEDIA_STATE",
  "title": "Blinding Lights",
  "artist": "The Weeknd",
  "album": "After Hours",
  "playbackState": "PLAYING",
  "duration": 200040,
  "position": 84000,
  "albumArt": "<base64 JPEG, only when the art changes>",
  "albumArtId": "3f2a91c0",
  "queue": [{"id": "42", "title": "Save Your Tears", "artist": "The Weeknd", "album": "After Hours", "duration": 0}],
  "currentTrackId": "41",
  "sourcePackage": "com.spotify.music",
  "sourceAppName": "Spotify",
  "albumArtist": "The Weeknd",
  "genre": "Pop",
  "trackNumber": 9,
  "trackCount": 14,
  "year": 2020,
  "playbackSpeed": 1.0
}
```

| Field | Type | Notes |
|---|---|---|
| `type` | string | Always `MEDIA_STATE`. |
| `title`, `artist`, `album` | string | Empty string if unknown. An empty `title` means nothing is playing. |
| `playbackState` | string | `PLAYING`, `PAUSED`, `BUFFERING`, `STOPPED` or `NONE`. |
| `duration`, `position` | long, ms | `position` is sampled when the frame is built. |
| `albumArt` | string? | Plain base64 JPEG (≤ 320 px edge, ≤ 100 KB before base64). Only sent when the cluster doesn't have this `albumArtId` yet on the current connection. |
| `albumArtId` | string? | Content hash of the art. If `albumArt` is absent but the id matches the cluster's cached art, keep showing it. `null` means no art. |
| `queue` | array? | At most 100 entries, `null` if the app exposes no queue. |
| `currentTrackId` | string? | Id of the playing entry in `queue`. |
| `sourcePackage`, `sourceAppName` | string? | Which app is playing. *(optional, added in v1.0)* |
| `albumArtist`, `genre` | string? | *(optional)* |
| `trackNumber`, `trackCount`, `year` | long? | Omitted when unknown. *(optional)* |
| `playbackSpeed` | float? | Omitted when 1.0. *(optional)* |

A sender that doesn't use `albumArtId` sends `albumArt` in every frame. That still works; it just
uses more bandwidth.

## Cluster → phone: `COMMAND`

```json
{"type": "COMMAND", "command": "SEEK", "position": 45000}
```

`command` is one of `PLAY`, `PAUSE`, `NEXT`, `PREVIOUS`, `SEEK`. `position` (ms) is only sent for
`SEEK`. The phone runs the command on the active app's MediaController and ignores commands that
app doesn't support.
