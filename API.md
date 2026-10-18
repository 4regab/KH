# kisskh.co API map (verified 2026-10-18)

Base: https://kisskh.co/api/  — plain JSON, no auth. Send Referer: https://kisskh.co/

## Home rows (array of {id,title,thumbnail,episodesCount,label})
- DramaList/Show                     hero carousel (6, id/title/thumbnail only)
- DramaList/LastUpdate?ispc=true     latest updates
- DramaList/MostView?ispc=true&c=1   most viewed (c = 1..n tab)
- DramaList/TopRating?ispc=true
- DramaList/MostSearch?ispc=true
- DramaList/Upcoming?ispc=true       label like " Coming Oct 23"
- DramaList/Animate?ispc=true        anime

## Paging grid
DramaList/List?page=N&pageSize=40&type=T&sub=0&country=C&status=S&order=O
-> {page,pageSize,totalCount,data:[...]}
- type:    0 all, 1 TV series, 2 movie, 3 anime, 4 Hollywood
- country: 0 all, 1 China, 2 Korea, 3 Japan, 4 Hong Kong, 5 Thailand, 6 US, 7 Taiwan, 8 Philippines, 9 Indonesia
- status:  0 all, 1 ongoing, 2 completed, 3 upcoming
- order:   1 popular, 2 newest release, 3 last updated

## Search
DramaList/Search?q=TEXT&type=0  -> array

## Detail
DramaList/Drama/{id}?isq=false
-> {id,title,description,thumbnail,type,country,status,releaseDate,episodesCount,trailer,episodes:[{id,number,sub}]}
episodes are newest-first.

## Stream (needs kkey)
DramaList/Episode/{epId}.png?err=false&ts=&time=&kkey=K
-> {Video, Type, ThirdParty, ...}. Type 1 = HLS m3u8 (plays with no headers). Type 2 = third-party/countdown, not playable.

## Subtitles (needs kkey)
Sub/{epId}?kkey=K -> [{src,label,land,default}]
File extension decides decryption (AES-CBC, PKCS7, base64 per cue line, key/iv as UTF-8):
- .srt / .vtt  plain
- .txt   key 8056483646328763  iv 6852612370185273
- .txt1  key AmSmZVcH93UQUezi  iv ReBKWW8cqdjPEnF6
- other  key sWODXX04QRTkHdlZ  iv 8pwhapJeC4hrS9hO

## kkey
Produced by global _0x54b991 in https://kisskh.co/common.js:
  _0x54b991(epId, null, "2.8.10", GUID, 4830201, "kisskh" x6)
GUID: video 62f176f3bb1b5b8e70e39932ad34a0c7, subs VgV52sWhwvBSf8BsM3BRY9weWiiCbtGp
Deterministic per epId (no time input). The app runs common.js itself in a JS engine,
so if the site rotates the cipher, the app picks up the new common.js automatically.

Found with REA analyze-javascript-application (webpack graph) + lazy chunks 502/876.
