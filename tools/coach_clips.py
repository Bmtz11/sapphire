"""Catalog of recorded coach clips (Nike-style). Generates the ElevenLabs scripts and
coach_clips.json (order matters: the recordings are split by the long pauses)."""
import json, os

ONES = ["صفر","واحد","اثنين","ثلاثة","أربعة","خمسة","ستة","سبعة","ثمانية","تسعة","عشرة",
        "احدعش","اثنعش","ثلطعش","اربعطعش","خمسطعش","سطعش","سبعطعش","ثمنطعش","تسعطعش"]
TENS = {20:"عشرين",30:"ثلاثين",40:"أربعين",50:"خمسين",60:"ستين",70:"سبعين",80:"ثمانين",90:"تسعين"}
def num(n):
    if n < 20: return ONES[n]
    t, o = n // 10 * 10, n % 10
    return TENS[t] if o == 0 else f"{ONES[o]} و{TENS[t]}"
HUND = {1:"مية",2:"ميتين",3:"ثلاثمية",4:"أربعمية",5:"خمسمية",6:"ستمية",7:"سبعمية",8:"ثمنمية",9:"تسعمية"}

clips = [
 ("start_walk","بدأنا المشي. أول ما تبدأ تجري بنتبه لك."),
 ("start_run","بدأنا الجري. بعطيك وتيرتك كل كيلو."),
 ("start_football","بدأت المباراة. بالتوفيق."),
 ("start_intervals","بدأنا الجري المخصص."),
 ("start_hike","بدأنا الهايكنج. بحسب لك الصعود والنزول."),
 ("start_treadmill","بدأنا التمرين الداخلي. المسافة من خطواتك."),
 ("start_cycling","بدأنا ركوب الدراجة."),
 ("mode_run","بديت الجري. خلك على نفس مريح."),
 ("mode_walk","رجعت للمشي."),
 ("you_ran","جريت"),
 ("in","في"),
 ("last_km","الكيلو الأخير"),
 ("last_500","آخر خمسمية متر"),
 ("pace_now","وتيرتك الحين"),
 ("goal_half","وصلت نص الهدف. باقي"),
 ("goal_reached","مبروك! حققت هدف"),
 ("hr_is","نبضك"),
 ("zone_is","أنت في المنطقة"),
 ("slow_down","خفف شوي."),
 ("time_half","مر نص الوقت. باقي"),
 ("time_left","باقي دقيقة وحدة. شد حيلك."),
 ("time_reached","خلص الوقت! قطعت"),
 ("warmup","إحماء"),
 ("run","جري"),
 ("walk","مشي"),
 ("cooldown","تهدئة"),
 ("you_have","عندك"),
 ("stages","مراحل."),
 ("then","بعدها"),
 ("intervals_done","خلصت التمرين المخصص. عاش! تقدر تكمل أو تنهي."),
 ("paused","إيقاف مؤقت."),
 ("resumed","كملنا. يلا."),
 ("no_workout","ما فيه تمرين شغال."),
 ("status_run","أنت تجري."),
 ("status_walk","أنت تمشي."),
 ("distance","المسافة"),
 ("pace","الوتيرة"),
 ("per_km","للكيلو."),
 ("heart","النبض"),
 ("finished","انتهى التمرين. المسافة"),
 ("burned","حرقت تقريباً"),
 ("kcal","سعرة."),
 ("climbed","صعدت"),
 ("did","سويت"),
 ("sprints","سبرنت."),
 ("top_speed","أعلى سرعة"),
 ("kmh","كيلو بالساعة."),
 ("of_it_run","منها جري"),
 ("meter","متر"),
 ("km","كيلو"),
 ("point","فاصلة"),
 ("min1","دقيقة"),
 ("min1_and","دقيقة و"),
 ("min2","دقيقتين"),
 ("min2_and","دقيقتين و"),
 ("mins","دقائق"),
 ("mins_and","دقائق و"),
 ("sec","ثانية"),
 ("hour","ساعة"),
 ("hour_and","ساعة و"),
 ("sample","يلا نبدأ. خذ نفس عميق، وخلك على وتيرة مريحة."),
]
clips += [(f"n{i}", num(i)) for i in range(100)]
clips += [(f"h{i}", HUND[i]) for i in HUND] + [(f"h{i}_and", HUND[i] + " و") for i in HUND]

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    json.dump([c[0] for c in clips], open(os.path.join(here, "coach_clips.json"), "w"), indent=0)
    out = os.environ.get("OUT", here)
    size = 30
    parts = [clips[i:i + size] for i in range(0, len(clips), size)]
    for k, part in enumerate(parts, 1):
        txt = '\n<break time="2.0s" />\n'.join(t for _, t in part)
        open(os.path.join(out, f"part{k}.txt"), "w", encoding="utf-8").write(txt + "\n")
    print(len(clips), "clips in", len(parts), "parts")
