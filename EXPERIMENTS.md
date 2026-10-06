# Experiments

## 1. Jev vs. OpenAI: speed and safety

**Setup**
- Three models answer the same policy questions inside the same workflows:
  - `jev`
  - `gpt-5.6-luna` (reasoning: low)
  - `gpt-5.6-terra` (reasoning: medium)
- Each experiment ran the full suite twice, 32 workflow runs in total:
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


**Takeaways**
- **Luna, one package claim:** said the package was left "at the address" *and* "at the wrong location", both at 0.99. The workflow's contradiction check sent it to a "human" for review.
- **Luna, one candidate:** recommended a phone screen instead of an onsite interview. Weaker, not unsafe.
- **Prompt injection is the biggest difference.**
  - Asked how support should handle the email, the OpenAI models often answered "cancel the order" (Luna 6/6, Terra 3/6).
  - They did flag the email as manipulation, so the workflow's manipulation check escalated it. Without that check, the order would have been cancelled.
  - Jev answered "escalate" on its own every time.
- **Keep guardrails in the workflow.** Every unsafe OpenAI answer was stopped by a deterministic check in the WfSpec, not by the model.


## 2. JSON key order can change Jev's answer

**What happened**
- Workflow: `package-claim` with `engine=jev`. Task: `decide-resolution`, the final call that picks refund, reship or deny.
- Its input includes `abuse_risk` from the earlier `assess-risk` task, along with the `tracking_evidence` and `customer_risk` keys in `assessment`.
- With identical inputs, the app denied the claim in one run and reshipped it after a restart.

**Example**
- Dave's package claim has signed proof of delivery at his address and 4 recent claims, so the policy says deny.

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
- **Pass words, not codes, from one model call to the next.**
  - `assess-risk` returned `2.0`. That means "HIGH" only if you know it's a position on a Low/Medium/High scale.
  - `decide-resolution` never saw that scale, so it had to guess what `2.0` meant.
  - Sending `"HIGH"` removed the guess: dave was denied every time.
- **Sorting the keys alone would have made things worse.**
  - Sorting makes every request the same, but "the same" doesn't mean "correct".
  - In alphabetical order `customer_risk` comes first, and that's the order where `2.0` led to "reship".
  - With sorting only, dave's claim would have been reshipped on every run.
- **We use both fixes:** labels make the answer right, and sorting keeps it the same after a restart.