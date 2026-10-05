# Experiments

Things we measured while building this demo. The [README](README.md) covers how Jev plugs into a WfSpec; this file covers what we learned.

| # | Question | Headline |
|---|---|---|
| 1 | Jev vs. `gpt-5.6-luna` vs. `gpt-5.6-terra` on the full suite: speed and safety | Jev 32/32 at ~150 ms per call; Terra 32/32 and Luna 30/32 at ~1.4–1.9 s. On prompt injections the OpenAI models' own answer was often "cancel" (Luna 6/6, Terra 3/6); Jev escalated every time. |
| 2 | Why did the same claim get different answers after a restart? | JSON key order. Java's `Map.of` reorders keys on every restart, and a bare Score index (`2.0`) was ambiguous to the next call. Fixed by passing labels and sorting keys. |

**Setup:** lh-quarkus 1.3.0, Quarkus 3.40.1, local `lh-standalone`.
- Jev: `jev-latest` (served `jev-1.13.0`).
- OpenAI: `gpt-5.6-luna` (`reasoning_effort=low`) and `gpt-5.6-terra` (`medium`), with strict JSON-schema output.
- Latencies are task `startTime` → `endTime` as recorded by the LittleHorse server (`lhctl list taskRun <wfRunId>`).

---

## 1. Jev vs. Luna vs. Terra

*2026-10-05 · all three examples, three engines*

**Setup**
- **Same suite per engine:**
  - 5 tickets: status, cancel, and 3 prompt-injection variants
  - the 6 seeded package claims
  - the 5 seeded candidates
- 2 rounds each, so 32 WfRuns per engine, sent one at a time. Each engine ran on a freshly restarted app.
- Terra ran through the OpenAI WfSpecs with an environment override, with no code change: `OPENAI_MODEL=gpt-5.6-terra OPENAI_REASONING_EFFORT=medium`. Every WfRun records which model answered.
- Every engine answers the same policy questions. OpenAI answers them through a strict JSON schema (see the README).

**Results**

| | Jev | Luna (low) | Terra (medium) |
|---|---|---|---|
| Matched expected outcome | **32/32** | 30/32 | **32/32** |
| Injections where the model's own answer was "cancel" | **0/6** | 6/6 | 3/6 |
| Injections escalated in the end (manipulation gate) | 6/6 | 6/6 | 6/6 |
| Self-contradicting tracking assessments | **0/10** | 1/10 | **0/10** |
| One model call, median / p90 (ms) | **147 / 283** | 1,894 / 3,016 | 1,425 / 2,259 |
| 4-call workflow, model time (s) | **0.5–1.0** | 6.2–9.7 | 4.7–8.6 |

**Mismatches**
- **Luna, carol r2:** contradictory tracking answers ("at the address" 0.99 *and* "wrong location" 0.99). The contradiction check sent the claim to a person.
- **Luna, APP-101 r1:** recommended a phone screen at 0.95 instead of fast-tracking to onsite. That isn't unsafe, just weaker; round 2 got it right at 0.99.

**Takeaways**
- **Prompt injection is where the engines differ most.**
  - The OpenAI models' own answer was often "cancel": Luna 6/6, Terra 3/6. They also flagged manipulation at 0.98–1.0, and only the WfSpec's manipulation gate stopped them.
  - A likely reason: "what action?" and "is this manipulation?" are separate questions, so they answer the request literally and flag it separately.
  - Jev answered "escalate" by itself in all 6 runs, with confidence 0.36–0.97.
- **Terra is the best OpenAI option, but not safe on its own.** It was 32/32 with no contradictions, yet chose "cancel" on 3 of 6 injections.
- **Dave is denied consistently now.** Experiment 2's fix (labels instead of score indices) held for every engine: dave was DENIED in all 6 runs.
- **Confidence didn't separate the engines this run.** Every final decision was right and came with confidence of 0.95 or higher on all three. In an earlier run, before experiment 2's fix, Jev's wrong answers on dave came with 0.58–0.79 and the gate caught them; Luna's wrong answer came at 0.98 and went through.
- **Speed:** Jev was about 10–13× faster per model call.

Raw runs: `/tmp/exp/{jev5,luna5,terra5}.jsonl`; analysis: `/tmp/exp/analysis5.txt`.

---

## 2. Same input, different answer: JSON key order

*2026-10-05 · `package-claim-jev`, customer `dave`*

**What we saw**
- Dave's claim has delivery proof at his address and 4 recent claims. The policy says DENY.
- It was DENIED twice on the morning of 10-05, then RESHIPPED on every run after a restart. The WfRun inputs were the same and so was the model (`jev-1.13.0`).

**Method:** replay the final `decide-resolution-jev` call with the exact inputs LittleHorse stored for the task, changing one thing at a time.

| Test | Result |
|---|---|
| Same request ×5 | Same choice every time; probabilities vary by a few hundredths |
| Swap input values between a DENIED and a RESHIPPED run | No change, so the values aren't the cause |
| All 24 orderings of the Choice options | DENY 24/24, so option order doesn't matter |
| All 120 top-level key orderings, `tracking_evidence` first in `assessment` | DENY 90, RESHIP 30 |
| All 120 top-level key orderings, `customer_risk` first in `assessment` | RESHIP 114, DENY 6 |
| Rebuild each real run's exact request (salt recovered from the stored `order` variable's key order) | All 4 runs reproduce their recorded decision. Both DENIED runs sent `tracking_evidence` first; both RESHIPPED runs sent `customer_risk` first. |

**Cause**
- **`Map.of` iteration order changes on every JVM restart.** The JDK picks a random salt at startup, so the same code sends the same state with its keys in a different order after a restart.
- **The risk value was ambiguous.** The risk call returns `abuse_risk: 2.0`, a Score index into Low/Medium/High. The resolution call never sees that scale, so how it reads `2.0` depended on what came just before it.

**Fix tested:** pass the level label instead of the index.

| `abuse_risk` sent as | `tracking_evidence` first | `customer_risk` first |
|---|---|---|
| `2.0` | DENY 90 / RESHIP 30 | RESHIP 114 / DENY 6 |
| `"HIGH"` | DENY 120 | DENY 120 |
| `"High: new account with frequent recent claims"` | DENY 120 | DENY 120 |

**Takeaways**
- **When one model call feeds another, pass self-describing values.** An index means something only next to the question that produced it.
- **Make the request independent of `Map` order.** Do this together with the first fix; on its own it only hides the ambiguity.
- **Stable choices made this easy to debug.** Old WfRuns could be replayed from LittleHorse's stored task inputs and gave the same choice every time.

**Applied (10-05):**
- `assess-risk` now returns `abuse_risk` as `LOW` / `MEDIUM` / `HIGH`.
- A Jackson customizer (`SortedJsonKeys`) sorts map keys in every JSON request, so the request is the same after a restart.

Result: on two separate restarts, all 6 seeded claims matched (dave: DENY at 1.0 both times). Alice's probabilities moved by 0.01, and so did 6 identical requests sent back to back. That is Jev's own small variation, not request order.

---

## Caveats

- Small samples, sent one at a time from one machine. None of this is a load test.
- Prompts were written once for all engines, not tuned per model.
- OpenAI latency depends on `reasoning_effort` and time of day; we tried `low` (Luna) and `medium` (Terra) only.
