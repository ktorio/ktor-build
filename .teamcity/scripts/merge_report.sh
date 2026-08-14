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
            projectFlaky: $DV.projectFlaky, projectFailed: $DV.projectFailed },
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

# --- HTML report (report tabs render HTML; embed the markdown text, escaped) ---
{
  printf '<!doctype html><meta charset="utf-8"><title>Ktor Flaky Tests</title>'
  printf '<style>body{font:14px/1.55 -apple-system,Segoe UI,Roboto,sans-serif;margin:2rem;color:#222}pre{white-space:pre-wrap;word-break:break-word}</style>'
  printf '<pre>'
  printf '%s' "$MD" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g'
  printf '</pre>'
} > "$OUT_DIR/flaky-report.html"

# consolidated JSON to stdout for the orchestrator
printf '%s\n' "$CONSOLIDATED"
