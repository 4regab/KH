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
-> {page,pageSize,totalCount,data:[...]}  (pages do not overlap; verified p1/p2 overlap=0)

### Verified catalog mapping (source: kisskh Explore page chunk 780.f011ddf6618b7a1e.js `chips`
### array, cross-checked 2026-10-18 by sampling DramaList/Drama/{id} for each filter; see probe/categories*.txt)
- type (site labels): 0 All, 1 TVSeries, 2 Movie, 3 Anime, 4 Hollywood
  - type=1 detail.type == "TVSeries"; type=2 detail.type == "Movie"; type=3 "Anime"; type=4 "Hollywood"
  - Hollywood (4) = US content, mixed series and movies; only country 6 has results.
  - Movies are NOT always 1 episode (Philippine/Japanese movies have 4-8 parts). Use type, never episode count.
- country (site labels, site order): 0 All Regions, 2 South Korea, 1 Chinese, 6 United States, 5 Thailand,
  8 Philippine, 3 Japanese, 4 Hong Kong, 7 Taiwan
  - detail.country strings: China, South Korea, Japan, Hong Kong, Thailand, United States, Taiwan, Philippines
  - country=9 returns a few results via API but is NOT offered by the site -> do not expose.
- status: 0 All, 1 Ongoing, 2 Completed, 3 Upcoming
- order (site labels): 1 Popular, 2 Last Update, 3 Release Date. There is NO rating sort.
- Verified non-empty combos (totalCount): series KR 2075, CN 2980, JP 557, TH 949, TW 121, PH 119, HK 18, US 0;
  movies KR 613, PH 318, JP 259, CN 191, TH 118, TW 31, HK 13, US 2; anime JP 2115, CN 408, KR 21, US 3.
  Empty combos exist (e.g. series+US = 0): probe with pageSize=1 and hide/disable empty tabs.
- TopRating / MostView / LastUpdate / Upcoming / Animate endpoints are global and accept no type/country
  filter. They must never be labelled as series-, movie- or country-specific.
- Old mapping lines below ("order 2 newest release, 3 last updated", country 9) are superseded by this block.

Legacy notes:
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
