#!/bin/bash
# Merge the flaky-test sources into one consolidated report.
#
# Inputs : <teamcity.json> <develocity.json> [quarantine.json] [output-dir]
#   teamcity.json  = collect_teamcity.sh output  {revision, attempts, flaky:[{name,target,targetDetail}]}
#   develocity.json= collect_develocity.sh output {available, windowDays, projectFlaky, projectFailed, classes:[...]}
#   quarantine.json= collect_quarantine.sh output {available, buildType, runs, tests:[...], counts:{...}}
#                    Optional, so a caller passing only the two original sources still works.
# Outputs: writes flaky-report.{json,md,html} into output-dir (default: cwd) and
#          echoes the consolidated JSON to stdout for the orchestrator.
set -euo pipefail

TC_FILE=$1
DV_FILE=$2
# The third argument is the quarantine file when it is a readable file, otherwise the output dir.
if [ -n "${3:-}" ] && [ -f "${3:-}" ]; then
  QR_FILE=$3
  OUT_DIR=${4:-.}
else
  QR_FILE=""
  OUT_DIR=${3:-.}
fi

# Ktor source repo, used to link classes and the revision in the HTML report.
KTOR_REPO_URL="${KTOR_REPO_URL:-https://github.com/ktorio/ktor}"
KTOR_REPO_URL="${KTOR_REPO_URL%/}"
KTOR_REPO_SLUG="${KTOR_REPO_SLUG:-$(printf '%s' "$KTOR_REPO_URL" | sed -E 's#^https?://[^/]+/##')}"
# TeamCity / Develocity bases for the per-row deep links (provided by the notifier via env).
TC_SERVER_URL="${TC_SERVER_URL:-https://ktor.teamcity.com}"
TC_SERVER_URL="${TC_SERVER_URL%/}"
WATCHED_BUILD_TYPE="${WATCHED_BUILD_TYPE:-Ktor_KtorCore_All}"
DV_DASHBOARD_URL="${DV_DASHBOARD_URL:-https://ge.jetbrains.com}"
DV_DASHBOARD_URL="${DV_DASHBOARD_URL%/}"
# YouTrack, for the per-class "find/file issue" links (C2).
YOUTRACK_URL="${YOUTRACK_URL:-https://youtrack.jetbrains.com}"
YOUTRACK_URL="${YOUTRACK_URL%/}"
YOUTRACK_PROJECT="${YOUTRACK_PROJECT:-KTOR}"

QR_INPUT=$(mktemp)
trap 'rm -f "$QR_INPUT"' EXIT
if [ -n "$QR_FILE" ]; then
  cat "$QR_FILE" > "$QR_INPUT"
else
  jq -n '{available: false, buildType: "", runs: 0, tests: [],
          counts: {stillFlaky: 0, alwaysFailing: 0, stableCandidate: 0}}' > "$QR_INPUT"
fi

CONSOLIDATED=$(jq -n --slurpfile tc "$TC_FILE" --slurpfile dv "$DV_FILE" --slurpfile qr "$QR_INPUT" '
  def stripVariants: sub("(\\[[^\\]]*\\])+$"; "");
  def classOf: stripVariants | split(".") | (if length > 1 then .[:-1] | join(".") else .[0] end);
  def infer:
    . as $c
    | if   ($c|test("\\.engine\\.(curl|darwin|winhttp)\\.")) then ["native"]
      elif ($c|test("\\.engine\\.js\\."))                    then ["js"]
      elif ($c|test("\\.engine\\.android\\."))               then ["jvm"]
      elif ($c|test("\\.server\\.(netty|jetty|tomcat)\\.|\\.engine\\.(okhttp|apache5?|java)\\.")) then ["jvm"]
      elif ($c|test("Jvm"))                                  then ["jvm"]
      elif ($c|test("Nix$"))                                 then ["native"]
      else ["multiplatform"] end;

  ($tc[0] // {revision:"", attempts:0, flaky:[]}) as $TC
  | ($dv[0] // {available:false, windowDays:0, projectFlaky:0, projectFailed:0, classes:[]}) as $DV
  | ($TC.flaky // []) as $tcf
  | ( $tcf | map({class:(.name|classOf), target}) | group_by(.class)
           | map({key:.[0].class, value:(map(.target)|unique)}) | from_entries ) as $tcmap
  | ( $DV.classes | map(.class) | unique ) as $dvClasses
  | ( $tcf
      | map(. + {class:(.name|classOf)})
      | map(. + {chronic: (.class | IN($dvClasses[]))}) ) as $thisRun
  | ( $DV.classes
      | sort_by(-.flaky)
      | map(. + { targets: ($tcmap[.class] // (.class|infer)),
                  targetSource: (if $tcmap[.class] then "teamcity" else "inferred" end),
                  alsoThisRun: (($tcmap[.class]) != null) }) ) as $chronic
  | ($qr[0] // {available:false, buildType:"", runs:0, tests:[],
                counts:{stillFlaky:0, alwaysFailing:0, stableCandidate:0}}) as $QR
  | {
      revision: $TC.revision,
      attempts: $TC.attempts,
      dv: { available: $DV.available, windowDays: $DV.windowDays,
            projectFlaky: $DV.projectFlaky, projectFailed: $DV.projectFailed,
            projectTrend: ($DV.projectTrend // []), trendDays: ($DV.trendDays // []) },
      quarantine: $QR,
      thisRun: $thisRun,
      byTargetCounts: ( $thisRun | group_by(.target) | map({key:.[0].target, value:length}) | from_entries ),
      chronic: $chronic,
      overlap: ( [ $thisRun[] | select(.chronic) | .class ] | unique ),
      overlapCount: ( [ $thisRun[] | select(.chronic) | .class ] | unique | length )
    }
')

mkdir -p "$OUT_DIR"
printf '%s\n' "$CONSOLIDATED" > "$OUT_DIR/flaky-report.json"

# --- Markdown report ---
MD=$(printf '%s' "$CONSOLIDATED" | jq -r '
  def tag(e): "[" + e.target + (if (e.targetDetail // "") != "" then ":" + e.targetDetail else "" end) + "]";
  "# Ktor — consolidated flaky tests\n"
  + "\n**Build All Core** · revision `" + ((.revision // "") | .[0:12]) + "` · attempts: " + (.attempts|tostring) + "\n"
  + (if .dv.available
       then "\nDevelocity: last " + (.dv.windowDays|tostring) + "d · project flaky: " + (.dv.projectFlaky|tostring) + " · failed: " + (.dv.projectFailed|tostring) + "\n"
       else "\n_Develocity source unavailable (no access key or API error)._\n" end)
  + "\n## This run — TeamCity retry-diff (" + (.thisRun|length|tostring) + ")\n\n"
  + "By target: " + ((.byTargetCounts | to_entries | map(.key + ": " + (.value|tostring)) | join(", ")) // "—") + "\n\n"
  + ((( [ .thisRun | sort_by(.target, .name)[] | "- " + tag(.) + " " + .name + (if .chronic then "  ⬅ also chronic (28d)" else "" end) ] | join("\n")) ) // "_none_")
  + "\n\n## Chronic — Develocity 28d, top flaky classes\n\n"
  + (if (.chronic|length) > 0
       then "| flaky | failed | class | target(s) | src | this run |\n|--:|--:|---|---|---|:-:|\n"
            + ( [ .chronic[0:25][] | "| " + (.flaky|tostring) + " | " + (.failed|tostring) + " | `" + .class + "` | " + (.targets|join(", ")) + " | " + .targetSource + " | " + (if .alsoThisRun then "✅" else "" end) + " |" ] | join("\n") )
       else "_none_" end)
  + "\n\n## Overlap — flaked this run AND chronic (" + (.overlapCount|tostring) + ")\n\n"
  + (if .overlapCount > 0 then ([ .overlap[] | "- `" + . + "`" ] | join("\n")) else "_none_" end)
  + "\n\n## Quarantined — @Flaky / _flaky, from the scheduled flaky-test build\n\n"
  + (if .quarantine.available | not
       then "_No data: the `"
            + (if (.quarantine.buildType // "") == "" then "flaky-test" else .quarantine.buildType end)
            + "` build has no finished runs yet._\n"
       else "Last " + (.quarantine.runs|tostring) + " run(s) · still flaky: "
            + (.quarantine.counts.stillFlaky|tostring) + " · always failing: "
            + (.quarantine.counts.alwaysFailing|tostring) + " · ready to un-quarantine: "
            + (.quarantine.counts.stableCandidate|tostring) + "\n\n"
            + (if (.quarantine.tests|length) > 0
                 then "| verdict | pass | fail | target | test |\n|---|--:|--:|---|---|\n"
                      + ( [ .quarantine.tests[] | "| " + .verdict + " | " + (.passed|tostring) + " | "
                            + (.failed|tostring) + " | " + tag(.) + " | " + .name + " |" ] | join("\n") )
                      + "\n\n_`always-failing` is not flakiness — fix it or `@Ignore` it. "
                      + "`stable-candidate` has stopped flipping and can leave quarantine._"
                 else "_none_" end)
       end)
  + "\n"
')
printf '%s\n' "$MD" > "$OUT_DIR/flaky-report.md"

# --- HTML report (report tabs render HTML; build a styled dashboard from the JSON) ---
HTML_HEAD='<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ktor Flaky Tests</title><style>
*{box-sizing:border-box}
:root{color-scheme:light dark;
 --page:#f9f9f7;--surface:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--muted:#898781;
 --grid:#e1e0d9;--border:rgba(11,11,11,.10);--accent:#2a78d6;
 --good:#0a840a;--warn:#8a5d08;--crit:#c23636;
 --good-bg:rgba(12,163,12,.12);--warn-bg:rgba(250,178,25,.18);--crit-bg:rgba(208,59,59,.12);
 --t-jvm:#2a78d6;--t-native:#eb6834;--t-js:#eda100;--t-wasm:#1baf7a;--t-mp:#4a3aa7;--t-android:#e87ba4}
@media (prefers-color-scheme:dark){:root{
 --page:#0d0d0d;--surface:#1a1a19;--ink:#fff;--ink2:#c3c2b7;--muted:#898781;
 --grid:#2c2c2a;--border:rgba(255,255,255,.10);--accent:#3987e5;
 --good:#3ad13a;--warn:#fab219;--crit:#e66767;
 --good-bg:rgba(58,209,58,.14);--warn-bg:rgba(250,178,25,.16);--crit-bg:rgba(230,103,103,.16);
 --t-jvm:#3987e5;--t-native:#d95926;--t-js:#c98500;--t-wasm:#199e70;--t-mp:#9085e9;--t-android:#d55181}}
body{margin:0 auto;max-width:1100px;background:var(--page);color:var(--ink);padding:28px;
 font:14px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
h1{font-size:22px;font-weight:650;margin:0 0 4px}
.sub{color:var(--ink2);font-size:13px;margin-bottom:24px}
.sub code{background:var(--surface);border:1px solid var(--border);border-radius:5px;padding:1px 6px;font-size:12px}
h2{font-size:15px;font-weight:620;margin:32px 0 14px;padding-bottom:8px;border-bottom:1px solid var(--grid);
 display:flex;align-items:baseline;gap:8px}
h2 .n{color:var(--muted);font-weight:500;font-size:13px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px}
.tile{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px 18px}
.tile .k{color:var(--ink2);font-size:12.5px;margin-bottom:6px}
.tile .v{font-size:30px;font-weight:640;line-height:1}
.tile .d{font-size:12px;color:var(--muted);margin-top:6px}
.tile.alert .v{color:var(--crit)}
.tile.ok .v{color:var(--good)}
.badge{display:inline-flex;align-items:center;gap:5px;font-size:12px;font-weight:520;
 padding:2px 9px;border-radius:999px;white-space:nowrap}
.badge .dot{width:8px;height:8px;border-radius:50%;background:var(--muted);flex:none}
.tgt{background:var(--surface);border:1px solid var(--border);color:var(--ink2)}
.tgt-jvm .dot{background:var(--t-jvm)}.tgt-native .dot{background:var(--t-native)}
.tgt-js .dot{background:var(--t-js)}.tgt-wasmjs .dot{background:var(--t-wasm)}
.tgt-multiplatform .dot{background:var(--t-mp)}.tgt-android .dot{background:var(--t-android)}
.st-good{background:var(--good-bg);color:var(--good)}
.st-warn{background:var(--warn-bg);color:var(--warn)}
.st-crit{background:var(--crit-bg);color:var(--crit)}
.byt{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:14px}
.byt .badge b{font-weight:640;margin-left:2px}
table{width:100%;border-collapse:collapse;font-size:13px}
th{text-align:left;color:var(--muted);font-weight:550;font-size:11.5px;text-transform:uppercase;
 letter-spacing:.03em;padding:0 12px 8px;border-bottom:1px solid var(--grid)}
th.num,td.num{text-align:right;font-variant-numeric:tabular-nums}
td{padding:9px 12px;border-bottom:1px solid var(--grid);vertical-align:top}
tr:last-child td{border-bottom:0}
tbody tr:hover{background:var(--surface)}
code.cls,.name{font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;word-break:break-all}
code.cls{color:var(--ink)}
.empty{color:var(--muted);font-style:italic;padding:8px 0}
.note{color:var(--muted);font-size:12px;margin-top:12px}
.tag-list{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:4px}
a{color:var(--accent);text-decoration:none}
a:hover{text-decoration:underline}
a.cls{font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;word-break:break-all}
.toolbar{margin:6px 0 22px}
.filter{width:100%;max-width:380px;padding:8px 12px;border:1px solid var(--border);border-radius:8px;
 background:var(--surface);color:var(--ink);font-size:13px}
.legend{margin:0 0 18px;background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:0 14px}
.legend summary{cursor:pointer;color:var(--ink2);font-size:13px;font-weight:550;padding:11px 0;list-style:none}
.legend summary::-webkit-details-marker{display:none}
.legend summary::before{content:"▸ ";color:var(--muted)}
.legend[open] summary::before{content:"▾ "}
.legend ul{margin:0 0 12px;padding-left:18px;color:var(--ink2);font-size:12.5px;line-height:1.75}
h2.prio{color:var(--crit);border-bottom-color:var(--crit-bg)}
table.srt th{cursor:pointer;user-select:none;white-space:nowrap}
table.srt th:hover{color:var(--ink2)}
table.srt th::after{content:"";opacity:.35;font-size:9px}
table.srt th.asc::after{content:" ▲";opacity:.7}
table.srt th.desc::after{content:" ▼";opacity:.7}
td .meths{display:flex;flex-direction:column;gap:2px}
.chip{display:inline-flex;align-items:center;font-size:11px;font-weight:520;padding:1px 7px;border-radius:999px;
 border:1px solid var(--border);background:var(--surface);color:var(--accent);white-space:nowrap;margin-left:6px;text-decoration:none}
.chip:hover{text-decoration:underline}
.muted{color:var(--muted)}
.small{font-size:11px}
.trend{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:14px 16px;margin-bottom:8px}
.trend-ctrl{display:flex;align-items:center;gap:10px;margin-bottom:10px;font-size:13px;color:var(--ink2);flex-wrap:wrap}
.trend-ctrl select{background:var(--page);color:var(--ink);border:1px solid var(--border);border-radius:7px;padding:5px 8px;font-size:13px}
.trend-svg{width:100%;height:160px;display:block}
.trend-svg .line{fill:none;stroke:var(--accent);stroke-width:2;stroke-linejoin:round;stroke-linecap:round}
.trend-svg .area{fill:var(--accent);opacity:.10;stroke:none}
.trend-svg .grid{stroke:var(--grid);stroke-width:1}
.trend-svg .ax{fill:var(--muted);font-size:10px}
</style></head><body>'

BODY=$(printf '%s' "$CONSOLIDATED" | jq -r \
  --arg repo "$KTOR_REPO_URL" --arg slug "$KTOR_REPO_SLUG" \
  --arg tc "$TC_SERVER_URL" --arg watched "$WATCHED_BUILD_TYPE" --arg ge "$DV_DASHBOARD_URL" \
  --arg yt "$YOUTRACK_URL" --arg ytproj "$YOUTRACK_PROJECT" '
  def esc: tostring|gsub("&";"&"+"amp;")|gsub("<";"&"+"lt;")|gsub(">";"&"+"gt;")|gsub("\"";"&"+"quot;");
  def attrEsc: tostring|gsub("&";"&"+"amp;")|gsub("\"";"&"+"quot;");
  # Derive the class of a test name (strip variant suffixes like [jvm], drop the method segment).
  def classOf: tostring|sub("(\\[[^\\]]*\\])+$";"")|split(".")|(if length>1 then .[:-1]|join(".") else .[0] end);
  def ghUrl(cls): $repo + "/search?type=code&q=" + ("repo:" + $slug + " " + ((cls)|split(".")|last) | @uri);
  def clsLink(cls): "<a class=\"cls\" href=\""+(ghUrl(cls)|attrEsc)+"\" title=\"Search on GitHub\">"+((cls)|esc)+"</a>";
  # Develocity 28d history for a class, and the TeamCity failing-build tests tab (B3 deep links).
  def geChip(cls): "<a class=\"chip\" href=\""+($ge+"/scans/tests?search.rootProjectNames=ktor&tests.container="+((cls)|@uri)|attrEsc)+"\" title=\"Develocity 28d history\">DC</a>";
  def tcChip(id): (if (id//"")=="" then "" else "<a class=\"chip\" href=\""+($tc+"/buildConfiguration/"+$watched+"/"+(id|tostring)+"?buildTab=tests"|attrEsc)+"\" title=\"Failing build on TeamCity\">▶ build</a>" end);
  # YouTrack: find open issues mentioning the class, and file a new one prefilled (C2).
  def ytChips(cls):
    "<a class=\"chip\" href=\""+($yt+"/issues?q="+("project: "+$ytproj+" #Unresolved "+((cls)|split(".")|last)|@uri)|attrEsc)+"\" title=\"Open "+($ytproj|esc)+" issues mentioning this test\">YT</a>"
    + "<a class=\"chip\" href=\""+($yt+"/newIssue?project="+$ytproj+"&summary="+("Flaky test: "+((cls)|split(".")|last)|@uri)|attrEsc)+"\" title=\"File a new "+($ytproj|esc)+" issue\">＋</a>";
  # Flaky rate = flaky / total, colored by severity; "—" when total is unknown (B1).
  def rateCell(f;t): ((if (t//0)>0 then ((f*1000/t)|round)/10 else null end)) as $r
    | (if $r==null then "<span class=\"badge tgt\">—</span>"
       else (if $r<2 then "st-good" elif $r<=5 then "st-warn" else "st-crit" end) as $sev
         | "<span class=\"badge "+$sev+"\">"+($r|tostring)+"%</span>" end);
  def tbadge(t;d):
    "<span class=\"badge tgt tgt-"+(t|ascii_downcase)+"\"><span class=\"dot\"></span>"+(t|esc)
    +(if (d//"")!="" then ":"+(d|esc) else "" end)+"</span>";
  def vb(v):
    (if v=="stable-candidate" then ["good","✓"]
     elif v=="always-failing" then ["crit","✗"]
     else ["warn","⚠"] end) as $s
    | "<span class=\"badge st-"+$s[0]+"\">"+$s[1]+" "+(v|esc)+"</span>";

  (.revision // "") as $rev
  | "<h1>Ktor · Flaky Tests</h1>"
  + "<div class=\"sub\">Build All Core · revision "
    + (if $rev != "" then "<a href=\""+($repo+"/commit/"+$rev|attrEsc)+"\"><code>"+($rev[0:12]|esc)+"</code></a>" else "<code>unknown</code>" end)
    + " · " + (.attempts|tostring)+" retry attempt(s)"
    + (if .dv.available then " · Develocity last "+(.dv.windowDays|tostring)+"d" else " · Develocity unavailable" end)
    + "</div>"

  + "<details class=\"legend\"><summary>How to read &amp; act on this report</summary><ul>"
  + "<li><b>Flaky</b> — a test whose result changed across retry attempts (failed, then passed). <b>Failed</b> — failed on every attempt.</li>"
  + "<li><b>Fix these first</b> — flaked in this run <i>and</i> chronically flaky over the last 28d: most likely a real, recurring problem, not a one-off.</li>"
  + "<li><b>Quarantine verdicts</b> — <span class=\"badge st-warn\">⚠ still-flaky</span> keep watching · <span class=\"badge st-crit\">✗ always-failing</span> not flakiness, fix it or <code>@Ignore</code> · <span class=\"badge st-good\">✓ stable-candidate</span> stopped flipping, ready to un-quarantine.</li>"
  + "<li>Class names link to a GitHub code search in the Ktor repo. To quarantine or harden a test, use <code>@Flaky</code> / the <code>_flaky</code> source set, <code>DeterministicFailureGuard</code> and <code>assertEventually</code>.</li>"
  + "</ul></details>"

  + "<div class=\"tiles\">"
  + "<div class=\"tile"+(if (.thisRun|length)>0 then " alert" else " ok" end)+"\"><div class=\"k\">Flaky this run</div><div class=\"v\">"+(.thisRun|length|tostring)+"</div><div class=\"d\">TeamCity retry-diff</div></div>"
  + "<div class=\"tile"+(if .overlapCount>0 then " alert" else "" end)+"\"><div class=\"k\">Fix first</div><div class=\"v\">"+(.overlapCount|tostring)+"</div><div class=\"d\">this run & chronic 28d</div></div>"
  + "<div class=\"tile\"><div class=\"k\">Develocity 28d</div><div class=\"v\">"+(if .dv.available then (.dv.projectFlaky|tostring) else "—" end)+"</div><div class=\"d\">flaky · "+(if .dv.available then (.dv.projectFailed|tostring) else "—" end)+" failed</div></div>"
  + "<div class=\"tile\"><div class=\"k\">Quarantine</div><div class=\"v\">"+(if .quarantine.available then (.quarantine.counts.stillFlaky|tostring) else "—" end)+"</div><div class=\"d\">still flaky</div></div>"
  + "</div>"

  + "<div class=\"toolbar\"><input id=\"flt\" class=\"filter\" type=\"search\" placeholder=\"Filter by test or class…\" autocomplete=\"off\"></div>"

  # --- Fix these first: flaked this run AND chronic (highest signal) — moved to the top ---
  + "<h2 class=\"prio\">Fix these first <span class=\"n\">flaked this run & chronic 28d · "+(.overlapCount|tostring)+"</span></h2>"
  + ( (.chronic | map(select(.alsoThisRun))) as $fix
      | if ($fix|length)==0
        then "<div class=\"empty\">Nothing urgent — no test flaked this run that is also chronically flaky.</div>"
        else "<table class=\"srt\"><thead><tr><th>Class</th><th>Target(s)</th><th class=\"num\">Rate</th><th class=\"num\">28d flaky</th><th class=\"num\">28d failed</th></tr></thead><tbody>"
          + ( [ $fix[] |
              "<tr><td>"+clsLink(.class)+geChip(.class)+ytChips(.class)+"</td>"
              +"<td>"+((.targets|map(tbadge(.;"")))|join(" "))+"</td>"
              +"<td class=\"num\">"+rateCell(.flaky;.total)+"</td>"
              +"<td class=\"num\">"+(.flaky|tostring)+"</td>"
              +"<td class=\"num\">"+(.failed|tostring)+"</td></tr>" ] | join("") )
          + "</tbody></table>" end )

  # --- This run, grouped by class so related failures cluster ---
  + "<h2>This run <span class=\"n\">TeamCity retry-diff · "+(.thisRun|length|tostring)+"</span></h2>"
  + (if (.thisRun|length)==0 then "<div class=\"empty\">No flaky tests detected in this run.</div>"
     else
       ( "<div class=\"byt\">"
         + ( .byTargetCounts | to_entries | sort_by(-.value) | map(
              "<span class=\"badge tgt tgt-"+(.key|ascii_downcase)+"\"><span class=\"dot\"></span>"
              +(.key|esc)+" <b>"+(.value|tostring)+"</b></span>") | join("") )
         + "</div>" )
       + "<table class=\"srt\"><thead><tr><th>Class</th><th>Target(s)</th><th class=\"num\">Tests</th><th>Failing test(s)</th><th>Chronic</th></tr></thead><tbody>"
       + ( [ .thisRun | group_by(.class) | sort_by(.[0].class)[] |
             (.[0].class) as $cls
             | ( [ .[].failBuildId // empty ] | map(select(. != "")) | (.[0] // "") ) as $fbid
             | "<tr><td>"+clsLink($cls)+geChip($cls)+tcChip($fbid)+ytChips($cls)+"</td>"
               +"<td>"+( [ .[] | {t:.target,d:(.targetDetail//"")} ] | unique | map(tbadge(.t;.d)) | join(" ") )+"</td>"
               +"<td class=\"num\">"+(length|tostring)+"</td>"
               +"<td class=\"name\"><div class=\"meths\">"
                 +( [ .[] | (if (.name|startswith($cls+".")) then .name[(($cls|length)+1):] else .name end) | esc ] | join("</div><div>") )
               +"</div></td>"
               +"<td>"+(if (any(.[]; .chronic)) then "<span class=\"badge st-warn\">⚠ 28d</span>" else "" end)+"</td></tr>" ] | join("") )
       + "</tbody></table>"
     end)

  # Standalone flakiness-trend block: a line chart with a class selector (defaults to Overall).
  # Data is embedded as JSON; the inline script redraws the SVG on selection change.
  + (if (.dv.available and (((.dv.projectTrend // []) | length) > 0))
     then "<h2>Flakiness trend <span class=\"n\">Develocity 28d · filter by class</span></h2>"
       + "<div class=\"trend\"><div class=\"trend-ctrl\"><label for=\"trendSel\">Series</label>"
       + "<select id=\"trendSel\"><option value=\"__overall__\">Overall (all flaky classes)</option>"
       + ( [ .chronic[] | "<option value=\""+(.class|attrEsc)+"\">"+(.class|split(".")|last|esc)+"</option>" ] | join("") )
       + "</select><span id=\"trendCap\" class=\"muted small\"></span></div>"
       + "<div id=\"trendChart\"></div>"
       + "<script id=\"trend-data\" type=\"application/json\">"
       + ( { days: (.dv.trendDays // []), overall: (.dv.projectTrend // []),
             classes: ([ .chronic[] | {key:.class, value:(.trend // [])} ] | from_entries),
             lastFlaky: ([ .chronic[] | {key:.class, value:(.lastFlakyMs // null)} ] | from_entries) } | tojson )
       + "</script></div>"
     else "" end)

  + "<h2>Chronic <span class=\"n\">Develocity 28d · top "+([(.chronic|length),25]|min|tostring)+" of "+(.chronic|length|tostring)+"</span></h2>"
  + (if (.chronic|length)==0 then "<div class=\"empty\">none</div>"
     else "<table class=\"srt\"><thead><tr><th class=\"num\">Flaky</th><th class=\"num\">Failed</th><th class=\"num\">Rate</th><th>Class</th><th>Target(s)</th><th>This run</th></tr></thead><tbody>"
       + ( [ .chronic[0:25][] |
           "<tr><td class=\"num\">"+(.flaky|tostring)+"</td><td class=\"num\">"+(.failed|tostring)+"</td>"
           +"<td class=\"num\">"+rateCell(.flaky;.total)+"</td>"
           +"<td>"+clsLink(.class)+geChip(.class)+ytChips(.class)+"</td>"
           # Show targets only when observed in this run (targetSource == teamcity); never guess.
           +"<td>"+(if .targetSource == "teamcity" then ((.targets|map(tbadge(.;"")))|join(" ")) else "<span class=\"muted\">—</span>" end)+"</td>"
           +"<td>"+(if .alsoThisRun then "<span class=\"badge st-crit\">● yes</span>" else "" end)+"</td></tr>" ] | join("") )
       + "</tbody></table>" end)

  + "<h2>Quarantined <span class=\"n\">@Flaky / _flaky</span></h2>"
  + (if (.quarantine.available|not)
     then "<div class=\"empty\">No data: the <code>"+((if (.quarantine.buildType//"")=="" then "flaky-test" else .quarantine.buildType end)|esc)+"</code> build has no finished runs yet.</div>"
     else "<div class=\"tag-list\"><span class=\"badge st-warn\">⚠ "+(.quarantine.counts.stillFlaky|tostring)+" still flaky</span>"
       +"<span class=\"badge st-crit\">✗ "+(.quarantine.counts.alwaysFailing|tostring)+" always failing</span>"
       +"<span class=\"badge st-good\">✓ "+(.quarantine.counts.stableCandidate|tostring)+" ready to un-quarantine</span>"
       +"<span class=\"badge tgt\">last "+(.quarantine.runs|tostring)+" run(s)</span></div>"
       +(if (.quarantine.tests|length)>0
         then "<table class=\"srt\"><thead><tr><th>Verdict</th><th class=\"num\">Pass</th><th class=\"num\">Fail</th><th>Target</th><th>Test</th></tr></thead><tbody>"
           +([.quarantine.tests[]|"<tr><td>"+vb(.verdict)+"</td><td class=\"num\">"+(.passed|tostring)+"</td><td class=\"num\">"+(.failed|tostring)+"</td><td>"+tbadge(.target;.targetDetail)+"</td><td class=\"name\"><a href=\""+(ghUrl(.name|classOf)|attrEsc)+"\">"+(.name|esc)+"</a>"+geChip(.name|classOf)+ytChips(.name|classOf)+"</td></tr>"]|join(""))
           +"</tbody></table>"
           +"<div class=\"note\"><b>always-failing</b> is not flakiness — fix it or <code>@Ignore</code> it. <b>stable-candidate</b> has stopped flipping and can leave quarantine.</div>"
         else "<div class=\"empty\">none</div>" end)
     end)
')

# Inline, dependency-free interactivity: type-to-filter across all tables and click-to-sort columns.
HTML_SCRIPT='<script>
(function(){
  var flt=document.getElementById("flt");
  function rows(){return document.querySelectorAll("table.srt tbody tr");}
  if(flt)flt.addEventListener("input",function(){
    var q=this.value.toLowerCase();
    rows().forEach(function(tr){tr.style.display=tr.textContent.toLowerCase().indexOf(q)>=0?"":"none";});
  });
  document.querySelectorAll("table.srt").forEach(function(tbl){
    var ths=tbl.tHead?tbl.tHead.rows[0].cells:[];
    Array.prototype.forEach.call(ths,function(th,idx){
      th.addEventListener("click",function(){
        var body=tbl.tBodies[0], trs=Array.prototype.slice.call(body.rows);
        var asc=!(th.classList.contains("asc"));
        Array.prototype.forEach.call(ths,function(o){o.classList.remove("asc","desc");});
        th.classList.add(asc?"asc":"desc");
        trs.sort(function(a,b){
          var x=(a.cells[idx]||{}).textContent||"", y=(b.cells[idx]||{}).textContent||"";
          x=x.trim();y=y.trim();
          var nx=parseFloat(x.replace(/[^0-9.\-]/g,"")), ny=parseFloat(y.replace(/[^0-9.\-]/g,""));
          var num=/[0-9]/.test(x)&&/[0-9]/.test(y)&&!isNaN(nx)&&!isNaN(ny);
          var c=num?(nx-ny):x.localeCompare(y);
          return asc?c:-c;
        });
        trs.forEach(function(r){body.appendChild(r);});
      });
    });
  });

  // Flakiness-trend chart: redraws the selected series (overall or one class) as an SVG line.
  (function(){
    var el=document.getElementById("trend-data"); if(!el) return;
    var data; try{ data=JSON.parse(el.textContent); }catch(e){ return; }
    var sel=document.getElementById("trendSel"),
        chart=document.getElementById("trendChart"),
        cap=document.getElementById("trendCap");
    function fmt(ms){ var d=new Date(ms); return d.toLocaleDateString(undefined,{month:"short",day:"numeric"}); }
    function series(k){ return k==="__overall__" ? (data.overall||[]) : ((data.classes||{})[k]||[]); }
    function draw(k){
      var s=series(k), days=data.days||[], n=s.length;
      var W=720,H=160,pL=30,pR=10,pT=12,pB=24, iw=W-pL-pR, ih=H-pT-pB;
      var mx=Math.max.apply(null,s.concat([1]));
      function x(i){ return pL + (n<=1?0:i*iw/(n-1)); }
      function y(v){ return pT + ih - (v*ih/mx); }
      var pts=s.map(function(v,i){ return x(i).toFixed(1)+","+y(v).toFixed(1); }).join(" ");
      var svg="<svg viewBox=\"0 0 "+W+" "+H+"\" preserveAspectRatio=\"none\" class=\"trend-svg\">"
        + "<line class=\"grid\" x1=\""+pL+"\" y1=\""+pT+"\" x2=\""+pL+"\" y2=\""+(pT+ih)+"\"/>"
        + "<line class=\"grid\" x1=\""+pL+"\" y1=\""+(pT+ih)+"\" x2=\""+(W-pR)+"\" y2=\""+(pT+ih)+"\"/>"
        + "<text class=\"ax\" x=\""+(pL-5)+"\" y=\""+(pT+5)+"\" text-anchor=\"end\">"+mx+"</text>"
        + "<text class=\"ax\" x=\""+(pL-5)+"\" y=\""+(pT+ih)+"\" text-anchor=\"end\">0</text>";
      if(n>1) svg+="<polygon class=\"area\" points=\""+pL+","+(pT+ih)+" "+pts+" "+x(n-1)+","+(pT+ih)+"\"/>";
      svg+="<polyline class=\"line\" points=\""+pts+"\"/>";
      [0,Math.floor((n-1)/2),n-1].forEach(function(i){
        if(i>=0&&i<n&&days[i]) svg+="<text class=\"ax\" x=\""+x(i)+"\" y=\""+(H-7)+"\" text-anchor=\"middle\">"+fmt(days[i])+"</text>";
      });
      chart.innerHTML=svg+"</svg>";
      var total=s.reduce(function(a,b){return a+b;},0), lf=(data.lastFlaky||{})[k];
      if(cap) cap.textContent=(k==="__overall__"?"All flaky classes":k.split(".").pop())
        +" · "+total+" flaky runs / 28d · peak "+mx+"/day"+(lf?" · last "+fmt(lf):"");
    }
    if(sel) sel.addEventListener("change",function(){ draw(this.value); });
    draw("__overall__");
  })();
})();
</script>'

{ printf '%s' "$HTML_HEAD"; printf '%s' "$BODY"; printf '%s' "$HTML_SCRIPT"; printf '</body></html>'; } > "$OUT_DIR/flaky-report.html"

# consolidated JSON to stdout for the orchestrator
printf '%s\n' "$CONSOLIDATED"
