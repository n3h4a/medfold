# Demo video narration

[`demo-video.mp4`](demo-video.mp4) is 79 seconds, captioned, with no sound. A voice-over in your
own voice makes it much stronger for LinkedIn or a portfolio. This script is timed to the video:
read it at a relaxed pace (about 2.5 words per second) and each line fits its scene.

| Time | On screen | Say |
|---|---|---|
| 0:00 | Title card | "This is Medfold. It turns a folder of paper medical reports into one private record you can search." |
| 0:04 | Log in | "I'm logging in as a demo patient. All the data here is synthetic." |
| 0:08 | Overview | "The overview shows what's in the record, and which latest results sit outside the range printed on their own report: things worth asking a doctor about." |
| 0:16 | Upload | "Now I'll upload a new thyroid report, as a PDF. A phone photo works too." |
| 0:18 | Processing | "The upload returns straight away. In the background, Medfold reads the text layer, or runs OCR on scans, and then a language model turns the text into structured data." |
| 0:26 | Document detail | "A couple of seconds later it's organised: the type, the date, the doctor, and every value with its reference range." |
| 0:31 | Results table | "TSH is above the printed range. That flag is plain arithmetic in the backend. The AI is never allowed to judge or advise." |
| 0:36 | Timeline | "The timeline orders documents by the date of the record, not the upload. I can filter by type." |
| 0:44 | HbA1c trend | "And here's one test from four different reports, lined up over time, with units converted when labs disagree." |
| 0:50 | Search | "Search works from anywhere. Typing 'metformin' finds the prescription it's written on…" |
| 0:56 | Prescription | "…and opens it." |
| 0:59 | Visit summary | "Before an appointment, the visit summary puts the latest results, previous values and current medicines on one printable page." |
| 1:07 | Dark mode | "There's a dark mode, and it all works on a phone." |
| 1:10 | Log out | "Logging out revokes the session on the server. Medfold is Spring Boot, React, FastAPI and PostgreSQL, with over 470 automated tests. The code is on GitHub." |
| 1:14 | End card | (silence, or "Thanks for watching.") |

## Adding your voice

Record the narration on your phone or laptop (any format), then combine it with the video:

```bash
ffmpeg -i demo/demo-video.mp4 -i narration.m4a -map 0:v -map 1:a -c:v copy -c:a aac -shortest demo/demo-video-narrated.mp4
```

If you speak a little faster or slower than the video, that's fine: the captions carry the
details, so the voice only has to stay roughly in step.

## Re-recording the video

The video is generated, not hand-edited, so it can be re-recorded after UI changes:

```bash
scripts/start-dev.sh                     # in one terminal
scripts/seed-demo-data.sh                # once
xvfb-run -a node demo/src/record-demo.cjs   # or plain `node` on a desktop
```

It writes `demo/demo-video.mp4` and `demo/quick-demo.gif`, and prints the time each caption
appears, for updating the table above.
