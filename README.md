# Jev × LittleHorse: fast, typed AI decisions inside durable workflows

## TL;DR

- **Speed:** a Jev call takes **~100 ms**; `gpt-5.6-luna` takes **~1–2.5 s**. A four-call workflow
  finishes in **~1 s with Jev vs. 7–10 s with OpenAI**.
- **Integrability:** Jev returns typed answers (choice, probabilities, confidence) that drop straight
  into workflow variables and `if` conditions. There's no prompt-and-parse step.
- **Safety:** OpenAI contradicted itself ("delivered at the address: 0.99" *and* "wrong
  location: 0.99"), and it reports 0.99 self-reported confidence even when wrong. Jev's confidence
  was low on the genuinely borderline case. See [EXPERIMENTS.md](EXPERIMENTS.md).
- **Guardrails belong in the workflow:** order ownership checks, contradiction checks, and confidence
  gates sit outside the model, so prompt injection can't spend money.

## What is [Jev](https://docs.typesafe.ai/introduction/quickstart)?

- TypeSafe's first **System One** model: fast judgments instead of generated text... inspired by Daniel Kahneman's: *Thinking Fast and Slow*.
- You send `state` (any JSON) plus named `questions`, and get typed `answers` back.
- All questions in one request run **in parallel**: 19 questions cost about the same time as 1.
- Answers are always one of your options, with calibrated probabilities.

| Question type | Use it for | Returns |
|---|---|---|
| **Choice** | pick one of N options | `choice`, `probabilities`, `confidence` |
| **Score** | position on ordered levels | `score` (can fall between levels), `probabilities`, `confidence` |
| **Noul** | is this true? | `noul` = probability of yes (0–1) |

## How Jev plugs into a WfSpec

Every model call follows the same four-step shape (examples from `package-claim`):

**1. Questions and thresholds are constants in the example's `policy/`**, one file per workflow, so they can be reviewed and tuned in one place.

```java
public static final double MIN_CLAIM_CONFIDENCE = 0.7;
public static final Map<String, Question> CLAIM_QUESTIONS = Map.of("claim_type", Question.choice(
        "What problem with `order` is the customer reporting in `customer_email`?",
        Map.of("MISSING_PACKAGE", "...", "DAMAGED_OR_WRONG_ITEM", "...", "OTHER", "...")));
```

**2. A decision task makes one model call and returns a flat `Map`**, which LittleHorse stores as `JSON_OBJ`. The task's inputs become Jev's `state`.

```java
@LHTaskMethod(CLASSIFY_CLAIM + JEV)
public Map<String, Object> classifyClaimJev(String emailBody, Map<String, Object> order, WorkerContext ctx) {
    var r = models.ask(JEV, ctx, Map.of("customer_email", emailBody, "order", order), CLAIM_QUESTIONS);
    var claim = r.get("claim_type");
    return r.toResult("type", claim.choice(), "confidence", claim.confidence(), "probabilities", claim.probabilities());
}
```

**3. The WfSpec stores the answer and branches on it with `jsonPath`.** No parsing happens anywhere.

```java
claim.assign(valid.execute(CLASSIFY_CLAIM + engine, emailBody, order).timeout(60).withRetries(2));
valid.doIf(claim.jsonPath("$.confidence").isLessThan(MIN_CLAIM_CONFIDENCE), t -> escalate(t, ...))
     .doElseIf(claim.jsonPath("$.type").isEqualTo("DAMAGED_OR_WRONG_ITEM"), t -> t.execute(SEND_RETURN_LABEL, userId, orderId))
     .doElseIf(claim.jsonPath("$.type").isEqualTo("MISSING_PACKAGE"), t -> { ... });
```

**4. Answers become inputs to later steps**, which only works because they're typed values:
- *A task argument:* `t.execute(FETCH_ROLE, triage.jsonPath("$.track"))` fetches the role Jev chose.
- *The next model call's state:* `decide-resolution` takes the `evidence` and `risk` variables from earlier calls as inputs.
- *A child workflow name:* `wf.runWf(childWf, inputs)`, where `childWf` is Jev's choice from an allowlist (dispatch).

## Patterns demonstrated

| Pattern | What it means | Where |
|---|---|---|
| Decision worker | Model returns a decision; the workflow executes it | all workflows |
| Intent routing | A Choice picks the handler or path | ticket triage, screening role track |
| Workflow dispatch | A Choice picks which **child workflow** to start, from an allowlist | `dispatch-support-ticket` |
| Speculative fan-out | Ask every question you *might* need in one call; code uses what applies | screening call 1 (9 questions) |
| Composite scoring | Score dimensions separately; weight them in code | screening `compute-fit-score` |
| Answer → next question | A second call only when the first answer unlocks new data | package claim, screening |
| Confidence-gated routing | Riskier actions need more confidence; low confidence goes to a human | refunds (0.8), onsite interviews (0.85) |
| Deterministic guardrails | Rules the model can't override | order ownership, contradiction check, name and email removed from resumes, declines need a second, separate review |

## The workflows

| Workflow | Story | Model calls |
|---|---|---|
| `handle-support-ticket-{jev,openai}` | Support email → cancel and refund / send order info / escalate | 1 |
| `dispatch-support-ticket` | Same tickets, but Jev picks a child workflow: `refund-order`, `send-order-status`, `issue-return-label`, `escalate-to-helpdesk` | 1 |
| `package-claim-{jev,openai}` | "Where's my package?", using a carrier API (HTTP) and CRM data | up to 4 |
| `screen-candidate-{jev,openai}` | Resume screening with name and email removed, using ATS and employment-verification data; uses all 3 question types; Jev completes the recruiter-review user task | up to 4 |

**Package claim** (`package-claim-*`)
1. **Classify the claim** (Choice): missing package / damaged or wrong item (emails a return label) / other.
2. Call the carrier API, then **assess the tracking** (4 Nouls): delivered? proof at the address? wrong location? contradicts the customer?
3. If both "proof at address" and "wrong location" are high → human. Not delivered → email an ETA. Order under $50 → refund.
4. Call the CRM, then **assess risk** (Score for abuse risk, Noul for pressure tactics).
5. **Decide** (Choice) against a written policy → refund / reship / deny. Confidence below 0.8 → human.

**Candidate screening** (`screen-candidate-*`)
1. **Triage** in one call: role track (Choice), seniority (Score), spam (Noul), remote-only (Noul), 5 skill dimensions (Score).
2. Fetch the chosen role → compute a **composite fit score** in code using that role's weights.
3. **Check requirements:** one Noul per must-have and nice-to-have of *that* role.
4. Fetch employment verification → **verify the work history** (Nouls, plus a Choice for the discrepancy type).
5. **Recommend** using every earlier answer: onsite if confidence > 0.85, otherwise phone screen or recruiter review. A decline always needs the recruiter review, which a Jev "recruiter" completes with its own call.

## Jev vs. OpenAI

Both engines run identical workflows, questions, and guardrails; only the decision task differs.
Method, raw numbers and caveats are in [EXPERIMENTS.md](EXPERIMENTS.md).

| | Jev (`jev-1.13`) | `gpt-5.6-luna` (low) | `gpt-5.6-terra` (medium) |
|---|---|---|---|
| One model call, median | **~150 ms** | ~1.9 s | ~1.4 s |
| 4-call workflow, model time (range) | **0.5–1.0 s** | 6.2–9.7 s | 4.7–8.6 s |
| Matched expected outcome (32 runs) | **32** | 30 | **32** |
| Model chose "cancel" on an injection | **0/6** | 6/6 | 3/6 |
| Self-contradicting tracking answers | **0/10** | 1/10 | **0/10** |

Numbers are from the latest run, experiment 5. Experiment 4 also showed Jev's confidence dropping when it was wrong while OpenAI stayed at 0.93 or higher; see [EXPERIMENTS.md](EXPERIMENTS.md).

Highlights:
- **Prompt injection:** both OpenAI models often chose to cancel the order; only the manipulation check in the WfSpec stopped it. Jev escalated on its own every time.
- **Self-contradiction:** both OpenAI models sometimes said "delivered at the address" *and* "wrong location" at 0.99. Terra did it on a package that was never delivered.
- **Key order flipped a Jev decision:** a bare Score index passed to the next call was ambiguous. Pass labels, not indices.
- **Switch the OpenAI twin to another model** without code changes: `OPENAI_MODEL=gpt-5.6-terra OPENAI_REASONING_EFFORT=medium ./gradlew quarkusDev`.

### How the OpenAI twin gets structured output

[`OpenAiModel`](src/main/java/io/littlehorse/common/llm/OpenAiModel.java) answers the same policy questions as Jev, using OpenAI's strict JSON-schema output (`response_format: json_schema`, `strict: true`):

1. **Each question becomes a required schema field, typed by question type.**
   - Choice → `{choice: enum[option keys], confidence}`
   - Noul → `{noul}`
   - Score → `{score: integer, confidence}`

   The enum means OpenAI can only answer with one of the policy's option keys, the same allowlist Jev gives.
2. **The reply is mapped into the shared `ModelResponse`.** Decision workers, `jsonPath` branches and gates are identical for both engines.

What strict mode does **not** give you:
- **Calibrated confidence.** OpenAI's confidence is self-reported. It stayed at 0.93 or above, so confidence gates rarely fire for it.
- **Probabilities or fractional scores.** `probabilities` is left out, and Score is a whole-number index.
- **Range checks.** Values meant to be 0–1 aren't enforced.
- **Correct answers.** The answer always has the right shape, but it can still contradict itself. The contradiction check in the WfSpec exists for this.
- **Special handling of refusals.** A refusal or empty reply fails to parse, so the task fails and `withRetries(2)` tries again.

**Option: LittleHorse Structs.** Decision tasks here return a `Map`, which LittleHorse stores as `JSON_OBJ`, so nothing checks the answer's shape once it leaves the worker. With Structs:
- A decision task returns an `@LHStructDef` class instead of a `Map`, e.g. `ClaimResolution { decision, confidence }`.
- The WfSpec declares the variable with `wf.declareStruct("resolution", ClaimResolution.class)`.
- lh-quarkus registers the StructDef, and LittleHorse type-checks every answer against it, from either engine.
- The answer's schema then lives in one place, in LittleHorse as well as in the worker.

This demo doesn't use Structs yet.

## Run it

```shell script
docker run --name littlehorse -d -p 2023:2023 -p 8080:8080 \
  ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:latest
# .env (or export): TYPESAFE_API_KEY=...  OPENAI_API_KEY=...
./gradlew quarkusDev        # registers the WfSpecs, runs the task workers and two HTTP services (carrier, helpdesk) · dashboard on :8080
```

Start any workflow with `lhctl run <wfSpec> <var> <value> ...`. Swap `-jev` for `-openai` to run the OpenAI twin.

```shell script
# Support ticket triage
lhctl run handle-support-ticket-jev user-id alice email-body "Please cancel ORD-1002 and refund me."
# Workflow dispatch (Jev only)
lhctl run dispatch-support-ticket user-id alice email-body "ORD-1001 arrived broken, I want to send it back."
# Package claim
lhctl run package-claim-jev user-id carol email-body "ORD-3001 says delivered but nothing is here."
# Candidate screening (APP-101, APP-103 … APP-106)
lhctl run screen-candidate-jev application-id APP-101

# Then: watch it in the dashboard (localhost:8080) or
lhctl get wfRun <wfRunId>
lhctl get variable <wfRunId> 0 outcome     # `status` / `decision` for support tickets
```

`lhctl` must point at the local server. If your default config uses TLS, add `--configFile <file with LHC_API_PROTOCOL=PLAINTEXT>`.

**Seeded scenarios**

| Package claims | Expected | Candidates | Expected |
|---|---|---|---|
| `alice` ORD-1001, photo on porch | reship | APP-101 senior backend | onsite |
| `carol` ORD-3001, locker 2.4 km away | refund | APP-103 inflated job title | recruiter review |
| `dave` ORD-4001, signed for, 4 recent claims | deny | APP-104 frontend dev, no open role fits | recruiter review |
| `erin` ORD-5001, in transit | ETA email | APP-105 spam | closed |
| `frank` ORD-6001, broken | return label | APP-106 wants remote only | recruiter review |
| `gina` ORD-7001, $19 | auto-refund | | |


## Links

- [Decision Workers (LittleHorse blog)](https://littlehorse.io/blog/decision-workers)
- [TypeSafe docs](https://docs.typesafe.ai): [primitives](https://docs.typesafe.ai/primitives), [patterns](https://docs.typesafe.ai/patterns), [confidence](https://docs.typesafe.ai/confidence)
- [lh-quarkus](https://github.com/littlehorse-enterprises/lh-quarkus)
- TypeSafe agent skill: [.agents/skills/typesafe-ai](.agents/skills/typesafe-ai/SKILL.md)
