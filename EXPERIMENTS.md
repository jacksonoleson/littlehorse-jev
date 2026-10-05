# Experiments

What we measured while building this demo. Timings come from the LittleHorse server (task start → end).

## 1. Jev vs. OpenAI: speed and safety

**Setup**
- Three engines answer the same policy questions inside the same workflows:
  - Jev
  - `gpt-5.6-luna` (reasoning: low)
  - `gpt-5.6-terra` (reasoning: medium)
- Each engine ran the full suite twice, 32 workflow runs in total:
  - 5 support tickets: an order-status question, a cancel request, and 3 prompt injections
  - the 6 seeded package claims
  - the 5 seeded candidates

**Results**

| | Jev | Luna | Terra |
|---|---|---|---|
| Matched the expected outcome | **32/32** | 30/32 | **32/32** |
| Model's own answer to a prompt injection was "cancel the order" | **0/6** | 6/6 | 3/6 |
| Prompt injections stopped in the end (by the workflow's manipulation check) | 6/6 | 6/6 | 6/6 |
| Tracking answers that contradicted themselves | **0/10** | 1/10 | **0/10** |
| One model call, median | **~150 ms** | ~1.9 s | ~1.4 s |
| 4-call workflow, total model time | **0.5–1.0 s** | 6.2–9.7 s | 4.7–8.6 s |

**Where an engine got it wrong**
- **Luna, one package claim:** said the package was left "at the address" *and* "at the wrong location", both at 0.99. The workflow's contradiction check sent it to a person.
- **Luna, one candidate:** recommended a phone screen instead of an onsite interview. Weaker, not unsafe.

**Takeaways**
- **Prompt injection is the biggest difference.**
  - Asked how support should handle the email, the OpenAI models often answered "cancel the order" (Luna 6/6, Terra 3/6).
  - They did flag the email as manipulation, so the workflow's manipulation check escalated it. Without that check, the order would have been cancelled.
  - Jev answered "escalate" on its own every time.
- **Terra is the strongest OpenAI option, but not safe on its own.** Every outcome was right, yet it chose "cancel" on half the injections.
- **Keep guardrails in the workflow.** Every unsafe OpenAI answer was stopped by a deterministic check in the WfSpec, not by the model.
- **Jev is ~10× faster per call.** A 4-call workflow takes about 1 s instead of 5–10 s.


## 2. JSON key order can change Jev's answer

**What happened**
- Dave's package claim has signed proof of delivery at his address and 4 recent claims, so the policy says deny.
- With identical inputs, the app denied the claim in one run and reshipped it after a restart.

**How we found the cause**
- We replayed the final decision call with the exact inputs LittleHorse had stored for each run, changing one thing at a time:

| Test | Result |
|---|---|
| Same request, sent repeatedly | Same answer every time |
| Shuffle the order of the answer options | Always deny, so not the cause |
| Swap input values between a deny run and a reship run | No change, so not the cause |
| Change the order of keys inside `assessment` | Deny or reship, depending on which key comes first |
| Rebuild each real run's exact request | Reproduces every recorded answer |

**Cause**
- **Java's `Map.of` picks a different key order on each restart**, so the same state reached Jev with its keys in a different order.
- **The risk value was ambiguous.** The risk step returned `abuse_risk: 2.0`, an index into Low/Medium/High. The final decision never sees that scale, so how it reads `2.0` depends on what comes just before it.

**Fix**

Each cell is out of 120 replays:

| `abuse_risk` sent as | `tracking_evidence` first | `customer_risk` first |
|---|---|---|
| `2.0` | deny 90 / reship 30 | reship 113 / deny 7 |
| `"HIGH"` | deny 120 | deny 120 |

- **Pass the level name** (`LOW` / `MEDIUM` / `HIGH`) instead of the index. This is what fixes the answer.
- **Sort JSON keys in every request** (`SortedJsonKeys`), so a restart can't change what Jev receives.

**Takeaways**
- **When one model call feeds another, pass self-describing values.** An index only means something next to the question that produced it.
- **Sorting keys alone isn't enough.** Sorted order puts `customer_risk` first, and with the bare index that reships dave every time.
- **Replaying stored task inputs makes model behavior debuggable.** LittleHorse keeps every task's inputs, so any past decision can be re-sent exactly.